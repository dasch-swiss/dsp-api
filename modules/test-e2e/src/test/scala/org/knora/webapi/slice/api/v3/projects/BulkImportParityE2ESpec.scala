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
import org.apache.jena.rdf.model.Statement
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
 * Guards against drift between the v3 bulk data-import and the v2 single-resource create: both write
 * paths must produce the same graph for one project. The test imports the shared fixture set through
 * both paths into the same project data graph and compares the two graphs: equal triple count,
 * identical resource-IRI set, and RDF isomorphism after normalizing minted value/standoff IRIs,
 * UUIDs, and creation timestamps.
 *
 * `hasPermissions` is included in the compare: the bulk import honors a payload `hasPermissions` and
 * resolves class/property DOAPs per entity, matching the create path. `lastModificationDate` is
 * stripped, since only the two-step create paths write it.
 *
 * Nothing else is stripped: `hasTextValueType` is written by all three write paths, `valueHasOrder`
 * is carried explicitly by every fixture value (so neither path synthesizes one), and `pageCount` is
 * persisted by neither (the live SipiService reports no page count, and the fake mirrors that).
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

  // richtext_standoff_refcount links `id_empty` from two text values, so its standoff-link LinkValue
  // must carry valueHasRefCount = 2 on both paths (the counter aggregates across text values).
  private val refCountResourceIri = "http://rdfh.ch/9999/f0khY71NRBqJ7noQj-YHpQ"
  private val refCountTargetIri   = "http://rdfh.ch/9999/ylMWInTAQxqQcPc51UqwPQ"

  private val dataTtl = RdfDataObject(initGraphBase + "data.ttl", projectDataGraph)

  override def rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(initGraphBase + "admin.ttl", "http://www.knora.org/data/admin"),
    RdfDataObject(initGraphBase + "permissions.ttl", "http://www.knora.org/data/permissions"),
    RdfDataObject(initGraphBase + "onto.ttl", "http://www.knora.org/ontology/9999/onto"),
    RdfDataObject(initGraphBase + "in-built.ttl", "http://www.knora.org/ontology/9999/in-built"),
    RdfDataObject(initGraphBase + "second-onto.ttl", "http://www.knora.org/ontology/9999/second-onto"),
    dataTtl,
  )

  // Tier 0: created before the dependent tiers. Most carry no resource dependency; where one exists it
  // is satisfied by list order — `richtext_all_standoff` has a standoff link to `id_empty`, which is
  // listed first. `richtext_recursive_standoff_link` is a Tier-0 resource but needs a two-step create
  // (self-referencing standoff link), so it is handled separately below.
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
    "richtext_standoff_refcount",
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
    test("both paths produce isomorphic graphs") {
      for {
        tester <- ZIO
                    .serviceWithZIO[UserService](_.findUserByIri(UserIri.unsafeFrom(testerUserIri)))
                    .someOrFail(new RuntimeException(s"on-behalf-of user $testerUserIri not found"))
        // Run A: bulk import as root on behalf of the project user, then dump the project data graph.
        importId <- triggerBulkImport
        _        <- ZIO.addFinalizer(deleteImportTask(importId).ignore)
        status   <- pollImportUntilDone(importId)
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
        val normA = normalize(graphA)
        val normB = normalize(graphB)
        val iso   = normA.isIsomorphicWith(normB)

        val resourcesA = resourceIris(graphA)
        val resourcesB = resourceIris(graphB)

        val creationDateA = creationDate(graphA, migrationCreationIri)
        val creationDateB = creationDate(graphB, migrationCreationIri)

        val refCountA = standoffLinkRefCount(graphA, refCountResourceIri, refCountTargetIri)
        val refCountB = standoffLinkRefCount(graphB, refCountResourceIri, refCountTargetIri)

        val diff =
          if (iso) ""
          else
            predicateDelta(normA, normB) + "\n" + objectDelta(normA, normB) +
              "\n\n-- raw canonical N-Triples diff (over-reports on a non-isomorphic pair; read the two deltas above first) --\n" +
              canonicalDiff(normA, normB)

        // Proof the two-step creates ran: only they write lastModificationDate; the bulk path never does.
        val twoStepCreatesRan = assertTrue(
          hasLastModification(graphB, audioSegmentIri),
          hasLastModification(graphB, richtextIri),
          !hasLastModification(graphA, audioSegmentIri),
        )
        // Equal triple count on the normalized models (lastModificationDate stripped, hasPermissions compared).
        val tripleCountsMatch = assertTrue(normA.size == normB.size)
        // Identical resource-IRI set.
        val resourceSetsMatch = assertTrue(resourcesA == resourcesB)
        // Custom creation date survives on both sides (sentinelled inside the isomorphism compare).
        val creationDateSurvives = assertTrue(
          creationDateA.contains(migrationCreationDate),
          creationDateA == creationDateB,
        )
        // RDF isomorphism, hasPermissions included.
        val graphsAreIsomorphic = assertTrue(iso)
        // The standoff-link LinkValue counter aggregates across text values: richtext_standoff_refcount
        // has two text values linking one target, so its refcount is 2 on both paths.
        val standoffRefCountsMatch = assertTrue(refCountA.contains(2), refCountB.contains(2))

        (twoStepCreatesRan &&
          tripleCountsMatch &&
          resourceSetsMatch &&
          creationDateSurvives &&
          graphsAreIsomorphic &&
          standoffRefCountsMatch).label(
          s"""|counts: A=${normA.size} B=${normB.size}
              |standoff-link refCount A=$refCountA B=$refCountB
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

  private def pollImportOnce(importId: DataTaskId): ZIO[TestApiClient, Throwable, DataTaskStatusResponse] =
    TestApiClient
      .getJson[DataTaskStatusResponse](uri"/v3/projects/$projectIri/data-imports/${importId.value}", rootUser)
      .flatMap(r => ZIO.fromEither(r.body).mapError(new RuntimeException(_)))

  // Polls until the task leaves InProgress, sleeping between attempts. A transport/deserialization
  // failure from pollImportOnce propagates immediately instead of being retried, so a genuine error
  // fails the test fast rather than being masked by up to a minute of pointless polling.
  private def pollImportUntilDone(importId: DataTaskId): ZIO[TestApiClient, Throwable, DataTaskStatusResponse] = {
    def loop(remainingAttempts: Int): ZIO[TestApiClient, Throwable, DataTaskStatusResponse] =
      pollImportOnce(importId).flatMap { status =>
        status.status match {
          case DataTaskStatus.Completed | DataTaskStatus.Failed   => ZIO.succeed(status)
          case DataTaskStatus.InProgress if remainingAttempts > 0 =>
            ZIO.sleep(500.millis) *> loop(remainingAttempts - 1)
          case DataTaskStatus.InProgress =>
            ZIO.fail(new RuntimeException(s"bulk import $importId did not finish within the polling budget"))
        }
      }
    loop(remainingAttempts = 120)
  }

  // 2xx = deleted, 404 = already gone (the reset path or a prior finalizer removed it). Any other
  // status — notably 409 while the import is still in progress — means the per-JVM import-task mutex may
  // still be held, which would block every later bulk-import spec in this shared JVM. Surface it as a
  // warning so a stuck import shows in the test log instead of silently leaking the slot.
  private def deleteImportTask(importId: DataTaskId): ZIO[TestApiClient, Throwable, Unit] =
    TestApiClient
      .deleteJson[Json](uri"/v3/projects/$projectIri/data-imports/${importId.value}", rootUser)
      .flatMap(resp =>
        ZIO
          .logWarning(
            s"import-task delete for $importId returned ${resp.code}; the per-JVM import slot may still be held",
          )
          .when(!resp.code.isSuccess && resp.code != StatusCode.NotFound)
          .unit,
      )

  // --- Reset -----------------------------------------------------------------------------------

  private def resetProjectGraph(importId: DataTaskId): ZIO[TriplestoreService & TestApiClient, Throwable, Unit] =
    deleteImportTask(importId) *>
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
      javaFile <- ZIO.attemptBlocking {
                    val f = java.nio.file.Files.createTempFile("bulk-import-parity-", ".nq")
                    f.toFile.deleteOnExit()
                    f
                  }
      file   = Path.fromJava(javaFile)
      _     <- ZIO.serviceWithZIO[TriplestoreService](_.downloadGraph(InternalIri(projectDataGraph), file, NQuads))
      model <- DatasetOps.from(file, Lang.NQUADS).map(toModel)
    } yield model

  private def toModel(ds: org.apache.jena.query.Dataset): Model = {
    val model = ModelFactory.createDefaultModel()
    model.add(ds.getDefaultModel)
    ds.listNames().asScala.foreach(name => model.add(ds.getNamedModel(name)))
    model
  }

  // --- Comparison helpers ----------------------------------------------------------------------

  // Objects of these predicates are replaced by one constant sentinel so minted UUIDs and timestamps
  // do not defeat the compare. A Jena Model has set semantics, so two triples on the same sentinelled
  // predicate for one subject would collapse into one; the ontology's maxCount 1 and the import's SHACL
  // check prevent that, so it is a latent blind spot, not an active gap.
  private val sentinelPredicates =
    Set(kb + "valueHasUUID", kb + "standoffTagHasUUID", kb + "creationDate", kb + "valueCreationDate")
  private val lastModProp        = kb + "lastModificationDate"
  private val standoffTagPattern =
    """^http://rdfh\.ch/[0-9A-Fa-f]{4}/[A-Za-z0-9_-]+/values/[A-Za-z0-9_-]+/standoff/\d+$""".r

  private def isMintedNode(uri: String): Boolean =
    ValueIri.from(uri).isRight || standoffTagPattern.matches(uri)

  /**
   * Returns a copy of the model with minted value/standoff IRIs replaced by blank nodes, UUIDs and
   * creation timestamps replaced by a sentinel, and lastModificationDate stripped. hasPermissions is
   * kept and compared. Every occurrence of a given minted IRI maps to the same blank node, so
   * shared-identity edges (LinkValue subject/object, standoff parents, previousValue, the segment
   * cross-link) survive.
   */
  private def normalize(model: Model): Model = {
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
      val strip = p == lastModProp
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

  // Shared by creationDate and standoffLinkRefCount, which both need only the first (and, given the
  // ontology's maxCount 1, only) object of a single-valued property.
  private def firstObjectOf(model: Model, subject: Resource, predicateUri: String): Option[RDFNode] =
    model.listObjectsOfProperty(subject, model.createProperty(predicateUri)).asScala.toList.headOption

  private def creationDate(model: Model, resourceIri: String): Option[String] =
    firstObjectOf(model, model.createResource(resourceIri), kb + "creationDate")
      .map(_.asLiteral().getLexicalForm)

  // The standoff-link LinkValue reifies one (resource, target) pair. Find it by its rdf:subject/object,
  // then read valueHasRefCount — the number of the resource's text values that link the target.
  private def standoffLinkRefCount(model: Model, resourceIri: String, targetIri: String): Option[Int] = {
    val rdf               = "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
    val hasStandoffLinkTo = model.createResource(kb + "hasStandoffLinkTo")
    val target            = model.createResource(targetIri)
    model
      .listResourcesWithProperty(model.createProperty(rdf + "subject"), model.createResource(resourceIri))
      .asScala
      .filter(lv => model.contains(lv, model.createProperty(rdf + "predicate"), hasStandoffLinkTo))
      .filter(lv => model.contains(lv, model.createProperty(rdf + "object"), target))
      .flatMap(lv => firstObjectOf(model, lv, kb + "valueHasRefCount"))
      .toList
      .headOption
      .map(_.asLiteral().getInt)
  }

  private def canonicalNTriples(model: Model): String = {
    val out = new ByteArrayOutputStream()
    RDFDataMgr.write(out, model, RDFFormat.NTRIPLES_C14N)
    out.toString(StandardCharsets.UTF_8)
  }

  // Statements grouped by predicate URI — the shared traversal behind the two per-predicate deltas.
  private def byPredicate(m: Model): Map[String, List[Statement]] =
    m.listStatements().asScala.toList.groupBy(_.getPredicate.getURI)

  // Blank-node-insensitive delta: which predicates occur a different number of times in A vs B. On a
  // non-isomorphic pair the canonical line diff over-reports (blank labels get relabelled), so this
  // per-predicate count is the reliable signal for triaging a structural divergence.
  private def predicateDelta(a: Model, b: Model): String = {
    val ha   = byPredicate(a).view.mapValues(_.size).toMap
    val hb   = byPredicate(b).view.mapValues(_.size).toMap
    val rows = (ha.keySet ++ hb.keySet).toList.sorted.flatMap { p =>
      val ca = ha.getOrElse(p, 0)
      val cb = hb.getOrElse(p, 0)
      if (ca != cb) Some(s"  $p: A=$ca B=$cb") else None
    }
    if (rows.isEmpty) "per-predicate counts identical (divergence is value-level, not structural)"
    else "per-predicate count differences (A=bulk, B=create):\n" + rows.mkString("\n")
  }

  // Per-predicate diff of NON-blank objects (literals + IRIs). Blank objects render identically, so
  // this ignores blank-node wiring and isolates value-level differences (a differing literal or IRI).
  private def objectDelta(a: Model, b: Model): String = {
    def render(n: RDFNode): String =
      if (n.isAnon) "_:_"
      else if (n.isLiteral) s""""${n.asLiteral.getLexicalForm}"^^${n.asLiteral.getDatatypeURI}"""
      else n.toString
    def objects(m: Model): Map[String, List[String]] =
      byPredicate(m).view.mapValues(_.map(st => render(st.getObject)).sorted).toMap
    val ha   = objects(a)
    val hb   = objects(b)
    val rows = (ha.keySet ++ hb.keySet).toList.sorted.flatMap { p =>
      val oa = ha.getOrElse(p, Nil)
      val ob = hb.getOrElse(p, Nil)
      if (oa == ob) None
      else
        Some(
          s"  $p:\n    A-only: ${(oa diff ob).take(6).mkString(", ")}\n    B-only: ${(ob diff oa).take(6).mkString(", ")}",
        )
    }
    if (rows.isEmpty) "per-predicate non-blank objects identical (difference is blank-node wiring only)"
    else "per-predicate non-blank object differences (A=bulk, B=create):\n" + rows.mkString("\n")
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
