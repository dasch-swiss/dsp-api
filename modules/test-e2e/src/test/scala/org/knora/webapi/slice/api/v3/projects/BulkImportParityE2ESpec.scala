/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import org.apache.jena.rdf.model.AnonId
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import org.junit.runner.RunWith
import sttp.client4.*
import sttp.model.*
import zio.*
import zio.json.*
import zio.json.ast.Json
import zio.nio.file.Path
import zio.test.*

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.NQuads
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.`export`.domain.DataTaskId
import org.knora.webapi.slice.`export`.domain.DataTaskStatus
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.slice.common.ValueIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.common.jena.DatasetOps
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.testservices.TestApiClient

/**
 * Guards against drift between the two write paths. The v3 bulk data-import and the v2
 * single-resource create must produce the same graph for one project. The test imports the shared
 * fixture set through both paths into the same project data graph and compares the two graphs: equal
 * triple count, identical resource-IRI set, and RDF isomorphism after normalizing minted
 * value/standoff IRIs, UUIDs, and creation timestamps. `hasPermissions` is excluded from the compare
 * (the two paths assign permissions differently), and `lastModificationDate` is stripped (only the
 * two-step create paths write it).
 *
 * Status: this test does not pass yet. It surfaced three independent defects that make the bulk
 * import fail its own SHACL validation (`data-shapes.ttl`), so there is no imported graph to compare.
 * Each fix site carries a `TODO(DEV-7149)` pointer:
 *   1. Standoff-link LinkValues are attached to the built-in SystemUser (`OntologyTransformer`),
 *      which the import shape `AttachedToUserNotBuiltInShape` rejects — any text with a resource
 *      standoff-link fails (richtext_all_standoff, richtext_recursive_standoff_link).
 *   2. `addResourceMetadata` adds a second `creationDate` over a payload-supplied one, and a payload
 *      `xsd:dateTimeStamp` fails the `xsd:dateTime` shape (migration_creation_date).
 *   3. `addResourceMetadata` adds a second `hasPermissions` over a payload-supplied one, breaking
 *      maxCount 1 (resource_permissions).
 */
@RunWith(classOf[DspZTestJUnitRunner])
class BulkImportParityE2ESpec extends E2EZSpec {

  override protected def sipiServiceLayer = BulkImportParityFakeSipiService.layer

  private val fixtureBase   = "test_data/bulk-import-parity/"
  private val initGraphBase = fixtureBase + "initial-db-graphs/"

  private val projectIri       = "http://rdfh.ch/projects/Rt2eOA19Q16vMYb_cIkw2g"
  private val projectDataGraph = "http://www.knora.org/data/9999/core-validation"
  private val onBehalfOf       = "testerKnownUser"
  private val testerUserIri    = "http://rdfh.ch/users/quvChlufRtiUcF0VvtHjjA"

  private val jsonLdType   = MediaType.unsafeApply("application", "ld+json")
  private val resourcesUri = uri"/v2/resources"
  private val valuesUri    = uri"/v2/values"

  private val onto     = "http://0.0.0.0:3333/ontology/9999/onto/v2#"
  private val knoraApi = "http://api.knora.org/ontology/knora-api/v2#"
  private val kb       = "http://www.knora.org/ontology/knora-base#"

  private val testRichtextProp   = onto + "testRichtext"
  private val relatesToValueProp = knoraApi + "relatesToValue"

  private val audioSegmentIri       = "http://rdfh.ch/9999/c9J1D6fiTwebS6ibbfVDWA"
  private val richtextIri           = "http://rdfh.ch/9999/msw8injcR6yPKdzYDGwYdw"
  private val migrationCreationIri  = "http://rdfh.ch/9999/1ayv8UcVR3Gk31kCJ2PSxQ"
  private val migrationCreationDate = "2019-01-09T15:45:54.502951Z"

  private val dataTtl = RdfDataObject(initGraphBase + "data.ttl", projectDataGraph)

  override def rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(initGraphBase + "admin.ttl", "http://www.knora.org/data/admin"),
    RdfDataObject(initGraphBase + "permissions.ttl", "http://www.knora.org/data/permissions"),
    RdfDataObject(initGraphBase + "onto.ttl", "http://www.knora.org/ontology/9999/onto"),
    RdfDataObject(initGraphBase + "in-built.ttl", "http://www.knora.org/ontology/9999/in-built"),
    RdfDataObject(initGraphBase + "second-onto.ttl", "http://www.knora.org/ontology/9999/second-onto"),
    dataTtl,
  )

  // Tier 0: no resource dependencies. `richtext_recursive_standoff_link` is a Tier-0 resource but
  // needs a two-step create (self-referencing standoff link), so it is handled separately below.
  private val tier0 = List(
    "id_empty",
    "all_dates",
    "all_scalar_values",
    "migration_iri",
    "migration_creation_date",
    "migration_ark",
    "comment_on_value",
    "same_comment_on_two_values",
    "value_order",
    "value_permissions",
    "resource_permissions",
    "private_resource",
    "private_property_resource",
    "richtext_all_standoff",
    "second_onto_class",
    "image_still",
    "image_still_svg",
    "iiif_image",
    "video_repr",
    "audio_repr",
    "document_repr",
    "archive_repr",
    "text_repr",
    "bitstream_permissions",
    "restricted_image",
    "target_empty_1",
    "target_empty_2",
  )
  private val tier1          = List("region", "img_seqnum_direct", "in_built_link_props", "link_obj")
  private val tier2          = List("onto_link_props")
  private val plainResources = tier0 ++ tier1 ++ tier2

  override val e2eSpec: Spec[env, Any] = suite("Bulk import vs single-resource create parity")(
    test("both paths produce isomorphic graphs (permissions excluded)") {
      for {
        tester <- ZIO
                    .serviceWithZIO[UserService](_.findUserByIri(UserIri.unsafeFrom(testerUserIri)))
                    .someOrFail(new RuntimeException(s"on-behalf-of user $testerUserIri not found"))
        // Run A: bulk import as root on behalf of the project user, then dump the project data graph.
        importId <- triggerBulkImport
        _        <- ZIO.addFinalizer(deleteImportTask(importId).ignore)
        status   <- pollImportUntilDone(importId).retry(Schedule.spaced(500.millis) && Schedule.recurs(120))
        _        <- ZIO
               .fail(
                 new RuntimeException(
                   s"bulk import did not complete: ${status.status} ${status.errorMessage.getOrElse("")}",
                 ),
               )
               .unless(status.status == DataTaskStatus.Completed)
        graphA <- dumpProjectGraph
        // Reset: delete the import task (frees the per-JVM mutex), then restore the lists-only data graph.
        _      <- resetProjectGraph(importId)
        _      <- runSingleCreates(tester)
        graphB <- dumpProjectGraph
      } yield {
        val normA = normalize(graphA, includePermissions = false)
        val normB = normalize(graphB, includePermissions = false)
        val iso   = normA.isIsomorphicWith(normB)

        val resourcesA = resourceIris(graphA)
        val resourcesB = resourceIris(graphB)

        val creationDateA = creationDate(graphA, migrationCreationIri)
        val creationDateB = creationDate(graphB, migrationCreationIri)

        val diff = if (iso) "" else canonicalDiff(normA, normB)

        assertTrue(
          // Proof the two-step creates ran: only they write lastModificationDate; the bulk path never does.
          hasLastModification(graphB, audioSegmentIri),
          hasLastModification(graphB, richtextIri),
          !hasLastModification(graphA, audioSegmentIri),
          // Equal triple count on the normalized models (lastModificationDate + hasPermissions stripped).
          normA.size == normB.size,
          // Identical resource-IRI set.
          resourcesA == resourcesB,
          // Custom creation date survives on both sides (sentinelled inside the isomorphism compare).
          creationDateA.contains(migrationCreationDate),
          creationDateA == creationDateB,
          // RDF isomorphism, permissions excluded.
          iso,
        ).label(
          s"""|counts: A=${normA.size} B=${normB.size}
              |resources only in A: ${(resourcesA -- resourcesB).toList.sorted.mkString(", ")}
              |resources only in B: ${(resourcesB -- resourcesA).toList.sorted.mkString(", ")}
              |$diff""".stripMargin,
        )
      }
    },
  )

  // --- Run A: bulk import ----------------------------------------------------------------------

  private def triggerBulkImport: ZIO[TestApiClient, Throwable, DataTaskId] =
    for {
      payload <- readFixture("bulk-import/data.jsonld")
      resp    <- TestApiClient.postBinary[DataTaskStatusResponse](
                uri"/v3/projects/$projectIri/data-imports?onBehalfOfUser=$onBehalfOf",
                payload.getBytes(StandardCharsets.UTF_8),
                jsonLdType,
                rootUser,
              )
      status <- ZIO.fromEither(resp.body).mapError(new RuntimeException(_))
      _      <- ZIO
             .fail(new RuntimeException(s"bulk import trigger failed: ${resp.code}"))
             .unless(resp.code == StatusCode.Accepted)
    } yield status.id

  private def pollImportUntilDone(importId: DataTaskId): ZIO[TestApiClient, Throwable, DataTaskStatusResponse] =
    TestApiClient
      .getJson[DataTaskStatusResponse](uri"/v3/projects/$projectIri/data-imports/${importId.value}", rootUser)
      .flatMap(r => ZIO.fromEither(r.body).mapError(new RuntimeException(_)))
      .flatMap { status =>
        status.status match {
          case DataTaskStatus.Completed => ZIO.succeed(status)
          case DataTaskStatus.Failed    => ZIO.succeed(status)
          case _                        => ZIO.fail(new RuntimeException("import still in progress"))
        }
      }

  private def deleteImportTask(importId: DataTaskId): ZIO[TestApiClient, Throwable, Response[Either[String, Json]]] =
    TestApiClient.deleteJson[Json](uri"/v3/projects/$projectIri/data-imports/${importId.value}", rootUser)

  // --- Reset -----------------------------------------------------------------------------------

  private def resetProjectGraph(importId: DataTaskId): ZIO[TriplestoreService & TestApiClient, Throwable, Unit] =
    deleteImportTask(importId).unit *>
      ZIO.serviceWithZIO[TriplestoreService] { ts =>
        ts.dropGraphByIri(InternalIri(projectDataGraph)) *>
          ts.insertDataIntoTriplestore(List(dataTtl), prependDefaults = false)
      }

  // --- Run B: single-resource creates ----------------------------------------------------------

  private def runSingleCreates(user: User): ZIO[TestApiClient, Throwable, Unit] =
    for {
      _ <- ZIO.foreachDiscard(plainResources)(name =>
             readFixture(s"single-resources/$name.json").flatMap(create(resourcesUri, _, user)),
           )
      _ <- richtextTwoStep(user)
      _ <- segmentCycle(user)
    } yield ()

  // The standoff link targets the resource's own IRI, which must exist before the value is inserted.
  // Create the resource with zero values, then add the richtext value.
  private def richtextTwoStep(user: User): ZIO[TestApiClient, Throwable, Unit] =
    for {
      body  <- readFixture("single-resources/richtext_recursive_standoff_link.json")
      empty <- dropProperty(body, testRichtextProp)
      _     <- create(resourcesUri, empty, user)
      value <- valuePayload(body, testRichtextProp)
      _     <- create(valuesUri, value, user)
    } yield ()

  // audio_segment and video_segment reference each other via relatesToValue. Create audio_segment
  // without the back-link, then video_segment (its relatesToValue -> audio_segment resolves), then
  // add audio_segment's relatesToValue -> video_segment.
  private def segmentCycle(user: User): ZIO[TestApiClient, Throwable, Unit] =
    for {
      audio      <- readFixture("single-resources/audio_segment.json")
      video      <- readFixture("single-resources/video_segment.json")
      audioNoRel <- dropProperty(audio, relatesToValueProp)
      _          <- create(resourcesUri, audioNoRel, user)
      _          <- create(resourcesUri, video, user)
      audioRel   <- valuePayload(audio, relatesToValueProp)
      _          <- create(valuesUri, audioRel, user)
    } yield ()

  private def create(uri: Uri, body: String, user: User): ZIO[TestApiClient, Throwable, Unit] =
    TestApiClient
      .postJsonLd(uri, body, user)
      .flatMap(resp =>
        ZIO
          .fail(new RuntimeException(s"POST $uri failed (${resp.code}): ${resp.body.merge}"))
          .unless(resp.code.isSuccess)
          .unit,
      )

  private def dropProperty(jsonStr: String, prop: String): Task[String] =
    parseObject(jsonStr).map(fields => Json.Obj(fields.filterNot(_._1 == prop)).toJson)

  private def valuePayload(jsonStr: String, prop: String): Task[String] =
    parseObject(jsonStr).flatMap { fields =>
      val kept = fields.filter(f => f._1 == "@id" || f._1 == "@type" || f._1 == prop)
      ZIO
        .fail(new RuntimeException(s"payload is missing @id, @type or $prop"))
        .when(kept.map(_._1).toSet != Set("@id", "@type", prop))
        .as(Json.Obj(kept).toJson)
    }

  private def parseObject(jsonStr: String): Task[Chunk[(String, Json)]] =
    ZIO.fromEither(jsonStr.fromJson[Json]).mapError(new RuntimeException(_)).flatMap {
      case Json.Obj(fields) => ZIO.succeed(fields)
      case other            => ZIO.fail(new RuntimeException(s"expected a JSON object, got: ${other.toJson.take(80)}"))
    }

  // --- Graph dump ------------------------------------------------------------------------------

  private def dumpProjectGraph: ZIO[TriplestoreService & Scope, Throwable, Model] =
    for {
      javaFile <- ZIO.attemptBlocking(java.nio.file.Files.createTempFile("bulk-import-parity-", ".nq"))
      file      = Path.fromJava(javaFile)
      _        <- ZIO.serviceWithZIO[TriplestoreService](_.downloadGraph(InternalIri(projectDataGraph), file, NQuads))
      model    <- DatasetOps.from(file, Lang.NQUADS).map(toModel)
    } yield model

  private def toModel(ds: org.apache.jena.query.Dataset): Model = {
    val model = ModelFactory.createDefaultModel()
    model.add(ds.getDefaultModel)
    ds.listNames().asScala.foreach(name => model.add(ds.getNamedModel(name)))
    model
  }

  // --- Comparison helpers ----------------------------------------------------------------------

  private val sentinelPredicates =
    Set(kb + "valueHasUUID", kb + "standoffTagHasUUID", kb + "creationDate", kb + "valueCreationDate")
  private val lastModProp        = kb + "lastModificationDate"
  private val hasPermissionsProp = kb + "hasPermissions"
  private val standoffTagPattern =
    """^http://rdfh\.ch/[0-9A-Fa-f]{4}/[A-Za-z0-9_-]+/values/[A-Za-z0-9_-]+/standoff/\d+$""".r

  private def isMintedNode(uri: String): Boolean =
    ValueIri.from(uri).isRight || standoffTagPattern.matches(uri)

  /**
   * Returns a copy of the model with minted value/standoff IRIs replaced by blank nodes, UUIDs and
   * creation timestamps replaced by a sentinel, lastModificationDate stripped, and (unless
   * `includePermissions`) hasPermissions stripped. Every occurrence of a given minted IRI maps to
   * the same blank node, so shared-identity edges (LinkValue subject/object, standoff parents,
   * previousValue, the segment cross-link) survive.
   */
  private def normalize(model: Model, includePermissions: Boolean): Model = {
    val minted: Set[String] =
      model
        .listStatements()
        .asScala
        .flatMap(st => List[RDFNode](st.getSubject, st.getObject))
        .collect { case n if n.isURIResource && isMintedNode(n.asResource.getURI) => n.asResource.getURI }
        .toSet
    val blankIds: Map[String, AnonId] = minted.iterator.map(uri => uri -> AnonId.create()).toMap

    val out      = ModelFactory.createDefaultModel()
    val sentinel = out.createLiteral("__normalized__")
    model.listStatements().asScala.foreach { st =>
      val p     = st.getPredicate.getURI
      val strip = p == lastModProp || (p == hasPermissionsProp && !includePermissions)
      if (!strip) {
        val subj: Resource =
          if (st.getSubject.isURIResource)
            blankIds.get(st.getSubject.getURI).fold(st.getSubject)(id => out.createResource(id))
          else st.getSubject
        val obj: RDFNode =
          if (sentinelPredicates.contains(p)) sentinel
          else if (st.getObject.isURIResource)
            blankIds.get(st.getObject.asResource.getURI).fold(st.getObject)(id => out.createResource(id))
          else st.getObject
        out.add(subj, st.getPredicate, obj)
        ()
      }
    }
    out
  }

  private def resourceIris(model: Model): Set[String] =
    model
      .listSubjects()
      .asScala
      .collect { case s if s.isURIResource && ResourceIri.from(s.getURI).isRight => s.getURI }
      .toSet

  private def hasLastModification(model: Model, resourceIri: String): Boolean =
    model.contains(model.createResource(resourceIri), model.createProperty(lastModProp))

  private def creationDate(model: Model, resourceIri: String): Option[String] =
    model
      .listObjectsOfProperty(model.createResource(resourceIri), model.createProperty(kb + "creationDate"))
      .asScala
      .toList
      .headOption
      .map(node => node.asLiteral().getLexicalForm)

  private def canonicalNTriples(model: Model): String = {
    val out = new ByteArrayOutputStream()
    RDFDataMgr.write(out, model, RDFFormat.NTRIPLES_C14N)
    out.toString(StandardCharsets.UTF_8)
  }

  private def canonicalDiff(a: Model, b: Model): String = {
    val linesA  = canonicalNTriples(a).linesIterator.toSet
    val linesB  = canonicalNTriples(b).linesIterator.toSet
    val onlyInA = (linesA -- linesB).toList.sorted.take(40)
    val onlyInB = (linesB -- linesA).toList.sorted.take(40)
    s"""|only in A (bulk), up to 40 lines:
        |${onlyInA.mkString("\n")}
        |only in B (create), up to 40 lines:
        |${onlyInB.mkString("\n")}""".stripMargin
  }

  private def readFixture(relPath: String): Task[String] =
    ZIO.attemptBlocking(scala.io.Source.fromResource(fixtureBase + relPath).mkString)
}
