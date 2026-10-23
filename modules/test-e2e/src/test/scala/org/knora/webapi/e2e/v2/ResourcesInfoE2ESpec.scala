/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.junit.runner.RunWith
import sttp.client4.UriContext
import sttp.model.Uri
import zio.*
import zio.test.*

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.anythingProjectIri
import org.knora.webapi.sharedtestdata.SharedTestDataADM.anythingUser1
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.api.v2.ApiV2
import org.knora.webapi.slice.api.v2.resources.ResourcesBatchRequest
import org.knora.webapi.slice.api.v2.resources.info.ListResponseDto
import org.knora.webapi.slice.api.v2.resources.info.ResourceInfoDto
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
import org.knora.webapi.testservices.TestApiClient

/**
 * E2E tests for `GET /v2/resources/info` against the real triplestore: project-wide and single-class listings,
 * the at-or-after `cutoff` on the effective date (`lastModificationDate`, else `creationDate`), `asOf`, and the
 * 400 cases. The mutation test creates, changes and soft-deletes its own resource so shared data stays untouched.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ResourcesInfoE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(
      path = "test_data/project_ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything",
    ),
  )

  private val thingClass     = SharedOntologyTestDataADM.ANYTHING_THING_RESOURCE_CLASS_LocalHost
  private val blueThingClass = SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost + "#BlueThing"
  private val aBlueThingIri  = "http://rdfh.ch/0001/a-blue-thing"

  private val jsonLdContext =
    """"@context" : {
      |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
      |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
      |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
      |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
      |  }""".stripMargin

  private def infoUri(resourceClass: Option[String] = None, cutoff: Option[String] = None): Uri =
    uri"/v2/resources/info?resourceClass=$resourceClass&cutoff=$cutoff"

  private def listInfo(uri: Uri, project: ProjectIri = anythingProjectIri) =
    TestApiClient
      .getJson[ListResponseDto](uri, _.header(ApiV2.Headers.xKnoraAcceptProject, project.value))
      .flatMap(_.assert200)

  private def listInfoExpecting400(uri: Uri, project: Option[ProjectIri] = Some(anythingProjectIri)) =
    TestApiClient
      .getAsString(uri, r => project.fold(r)(p => r.header(ApiV2.Headers.xKnoraAcceptProject, p.value)))
      .flatMap(_.assert400)

  private def entryOf(resourceIri: String)(list: ListResponseDto): Task[ResourceInfoDto] =
    ZIO
      .fromOption(list.resources.find(_.resourceIri == resourceIri))
      .orElseFail(Exception(s"$resourceIri is not listed"))

  private def createThing: ZIO[TestApiClient, Throwable, String] = {
    val payload =
      s"""{
         |  "@type" : "anything:Thing",
         |  "knora-api:attachedToProject" : { "@id" : "${anythingProjectIri.value}" },
         |  "rdfs:label" : "a thing for the resources info spec",
         |  $jsonLdContext
         |}""".stripMargin
    TestApiClient
      .postJsonLdDocument(uri"/v2/resources", payload, anythingUser1)
      .flatMap(_.assert200)
      .flatMap(response => ZIO.fromEither(response.body.getRequiredString(JsonLDKeywords.ID)).mapError(Exception(_)))
  }

  private def addIntValue(resourceIri: String) = {
    val payload =
      s"""{
         |  "@id" : "$resourceIri",
         |  "@type" : "anything:Thing",
         |  "anything:hasInteger" : { "@type" : "knora-api:IntValue", "knora-api:intValueAsInt" : 42 },
         |  $jsonLdContext
         |}""".stripMargin
    TestApiClient.postJsonLdDocument(uri"/v2/values", payload, anythingUser1).flatMap(_.assert200)
  }

  private def deleteThing(resourceIri: String, lastModificationDate: Instant) = {
    val payload =
      s"""{
         |  "@id" : "$resourceIri",
         |  "@type" : "anything:Thing",
         |  "knora-api:lastModificationDate" : { "@type" : "xsd:dateTimeStamp", "@value" : "$lastModificationDate" },
         |  $jsonLdContext
         |}""".stripMargin
    TestApiClient.postJsonLd(uri"/v2/resources/delete", payload, anythingUser1).flatMap(_.assert200)
  }

  private val listings = suite("listing")(
    test("a project-wide call lists every class with its resourceClass; a class call lists exactly that class") {
      for {
        all    <- listInfo(infoUri())
        things <- listInfo(infoUri(resourceClass = Some(thingClass)))
        allIris = all.resources.map(_.resourceIri).toSet
      } yield assertTrue(
        all.resources.map(_.resourceClass).distinct.size > 1,
        // Complex-schema external class IRIs; 0001 also holds a knora-api:Region.
        all.resources.forall(r => r.resourceClass.contains("/v2#") && !r.resourceClass.contains("/simple/v2#")),
        all.count == all.resources.size,
        things.resources.nonEmpty,
        things.resources.forall(_.resourceClass == thingClass),
        things.resources.map(_.resourceIri).toSet.subsetOf(allIris),
        all.count > things.count,
      )
    },
    test("a class call does not match subclasses: the BlueThing instance is only in the project-wide listing") {
      for {
        all    <- listInfo(infoUri())
        things <- listInfo(infoUri(resourceClass = Some(thingClass)))
      } yield assertTrue(
        all.resources.exists(r => r.resourceIri == aBlueThingIri && r.resourceClass == blueThingClass),
        !things.resources.exists(_.resourceIri == aBlueThingIri),
      )
    },
    test("a cutoff in the future gives an empty list") {
      for {
        now    <- Clock.instant
        future <- listInfo(infoUri(cutoff = Some(now.plusSeconds(86400).toString)))
      } yield assertTrue(future.resources.isEmpty, future.count == 0)
    },
    test("asOf is the server time of the request") {
      for {
        before <- Clock.instant
        list   <- listInfo(infoUri())
        after  <- Clock.instant
      } yield assertTrue(!list.asOf.isBefore(before), !list.asOf.isAfter(after))
    },
    test("a cutoff with a URL-encoded +02:00 offset is applied as that offset") {
      for {
        all       <- listInfo(infoUri())
        newest     = all.resources.maxBy(_.lastModificationDate)
        plusTwo    = newest.lastModificationDate.atOffset(ZoneOffset.ofHours(2))
        withOffset = plusTwo.format(ISO_OFFSET_DATE_TIME)
        offsetUri  = infoUri(cutoff = Some(withOffset))
        atOffset  <- listInfo(offsetUri)
        // The same wall-clock time read as UTC lies two hours later.
        sameWallClockUtc = plusTwo.withOffsetSameLocal(ZoneOffset.UTC).format(ISO_OFFSET_DATE_TIME)
        asUtc           <- listInfo(infoUri(cutoff = Some(sameWallClockUtc)))
      } yield assertTrue(
        withOffset.endsWith("+02:00"),
        offsetUri.toString.contains("%2B02:00"),
        atOffset.resources.exists(_.resourceIri == newest.resourceIri),
        !asUtc.resources.exists(_.resourceIri == newest.resourceIri),
      )
    },
    test("an unknown project and an unknown but well-formed class both give an empty list") {
      val unknownClass = SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost + "#NoSuchClass"
      for {
        unknownProject <- listInfo(infoUri(), ProjectIri.unsafeFrom("http://rdfh.ch/projects/9999"))
        noSuchClass    <- listInfo(infoUri(resourceClass = Some(unknownClass)))
      } yield assertTrue(unknownProject.resources.isEmpty, noSuchClass.resources.isEmpty)
    },
  )

  private val changes = suite("cutoff on a changing resource")(
    test("a created, changed and soft-deleted resource is listed by a cutoff at or before each write") {
      for {
        resourceIri <- createThing
        created     <- listInfo(infoUri(resourceClass = Some(thingClass))).flatMap(entryOf(resourceIri))
        createdAt    = created.lastModificationDate
        atCreation  <- listInfo(infoUri(cutoff = Some(createdAt.toString)))
        justAfter   <- listInfo(infoUri(cutoff = Some(createdAt.plusMillis(1).toString)))
        _           <- addIntValue(resourceIri)
        changed     <- listInfo(infoUri(cutoff = Some(createdAt.toString))).flatMap(entryOf(resourceIri))
        _           <- deleteThing(resourceIri, changed.lastModificationDate)
        deleted     <-
          listInfo(infoUri(cutoff = Some(changed.lastModificationDate.toString))).flatMap(entryOf(resourceIri))
        batch <-
          TestApiClient
            .postJsonReceiveString(uri"/v2/resources/batch", ResourcesBatchRequest(List(resourceIri)), anythingUser1)
            .flatMap(_.assert200)
        tombstone = JsonLDUtil.parseJsonLD(batch).body
      } yield assertTrue(
        created.resourceClass == thingClass,
        createdAt == created.creationDate,
        !created.isDeleted,
        atCreation.resources.exists(_.resourceIri == resourceIri),
        !justAfter.resources.exists(_.resourceIri == resourceIri),
        changed.lastModificationDate.isAfter(createdAt),
        changed.creationDate == created.creationDate,
        deleted.isDeleted,
        deleted.deleteDate.contains(deleted.lastModificationDate),
        deleted.lastModificationDate.isAfter(changed.lastModificationDate),
        tombstone.getRequiredString(JsonLDKeywords.TYPE) == Right(KnoraApiV2Complex.DeletedResource),
        tombstone.getRequiredBoolean(KnoraApiV2Complex.IsDeleted) == Right(true),
        tombstone.getRequiredString(JsonLDKeywords.ID) == Right(resourceIri),
      )
    },
  )

  private val badRequests = suite("400 Bad Request")(
    test("for a cutoff without an offset") {
      listInfoExpecting400(infoUri(cutoff = Some("2026-10-23T08:00:00"))).as(assertCompletes)
    },
    test("for a date-only cutoff") {
      listInfoExpecting400(infoUri(cutoff = Some("2026-10-23"))).as(assertCompletes)
    },
    test("for a malformed resourceClass") {
      listInfoExpecting400(infoUri(resourceClass = Some("not an iri"))).as(assertCompletes)
    },
    test("for a missing X-Knora-Accept-Project header") {
      listInfoExpecting400(infoUri(), project = None).as(assertCompletes)
    },
  )

  override val e2eSpec = suite("ResourcesInfoE2ESpec")(listings, changes, badRequests)
}
