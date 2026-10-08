/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import org.junit.runner.RunWith
import sttp.client4.*
import sttp.model.StatusCode
import zio.*
import zio.json.ast.Json
import zio.test.*

import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.util.standoff.StandoffTagUtilV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.slice.standoff.service.StandoffMappingService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.testservices.RequestsUpdates.RequestUpdate
import org.knora.webapi.testservices.RequestsUpdates.addVersionQueryParam
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

/**
 * End-to-end proof of `POST /admin/maintenance/backfill-value-has-xml`.
 *
 * The spec removes every `valueHasXml` of project 0001 and the backfill restores it.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class MaintenanceBackfillValueHasXmlE2ESpec extends E2EZSpec {

  private val projectIri   = "http://rdfh.ch/projects/0001"
  private val dataGraph    = "http://www.knora.org/data/0001/anything"
  private val anythingOnto = "http://0.0.0.0:3333/ontology/0001/anything/v2#"
  private val hasTextIri   = s"${anythingOnto}hasText"
  private val kb           = "http://www.knora.org/ontology/knora-base#"

  private val customMappingIri =
    StandoffMappingIri.unsafeFrom(s"$projectIri/mappings/BackfillHTMLMapping")

  private val pollSchedule = Schedule.spaced(500.millis) && Schedule.recurs(120)

  private val backfillUri = uri"/admin/maintenance/backfill-value-has-xml"

  private val plainXml =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<text documentType="html"><p>first <strong>version</strong> of the text</p></text>""".stripMargin

  private val editedXml =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<text documentType="html"><p>second <em>version</em> of the text</p></text>""".stripMargin

  private val deletedXml =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<text documentType="html"><p>a value that is deleted <strong>afterwards</strong></p></text>""".stripMargin

  private val customMappingXml =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<text documentType="html"><p>This an <span class="event" data-description="an &quot;event&quot;" data-date="GREGORIAN:2017-01-27 CE">event</span>.</p></text>""".stripMargin

  private val internalReferenceXml =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<text documentType="html"><p>This <strong id="link_id">strong value</strong> is linked by this <a class="internal-link" href="#link_id">link</a></p></text>""".stripMargin

  private val mutualReferencesXml =
    """<?xml version="1.0" encoding="UTF-8"?>
      |<text documentType="html">
      |  <p>ref to note <a class="internal-link" href="#_note1" id="_ref-note1">[1]</a></p>
      |  <p><a class="internal-link" href="#_ref-note1" id="_note1">[1]</a> note 1</p>
      |</text>""".stripMargin

  private val manyTagsXml =
    s"""<?xml version="1.0" encoding="UTF-8"?>
       |<text documentType="html">${(1 to 120).map(i => s"<p>paragraph <em>$i</em></p>").mkString}</text>""".stripMargin

  /** The value that carries the XML in the text property of the resource, and its history. */
  private final case class Fixture(
    historyResource: String,
    historyV1: String,
    historyV1Created: String,
    historyV2: String,
    deletedValue: String,
    customResource: String,
    customValue: String,
    internalResource: String,
    internalValue: String,
    manyResource: String,
    manyValue: String,
  )

  private val fixture = new AtomicReference[Fixture]()

  private def textValueJson(xml: String, mapping: StandoffMappingIri, valueIri: Option[String]): Json.Obj = {
    val id = valueIri.map(iri => Chunk("@id" -> Json.Str(iri))).getOrElse(Chunk.empty)
    Json.Obj(
      Chunk[(String, Json)]("@type" -> Json.Str("knora-api:TextValue")) ++ id ++ Chunk(
        "knora-api:textValueAsXml"      -> Json.Str(xml),
        "knora-api:textValueHasMapping" -> Json.Obj("@id" -> Json.Str(mapping.value)),
      ),
    )
  }

  private val context = Json.Obj(
    "knora-api" -> Json.Str("http://api.knora.org/ontology/knora-api/v2#"),
    "rdfs"      -> Json.Str("http://www.w3.org/2000/01/rdf-schema#"),
    "anything"  -> Json.Str(anythingOnto),
  )

  private def createResourceWithText(xml: String, mapping: StandoffMappingIri): RIO[TestApiClient, String] = {
    val body = Json.Obj(
      "@type"                       -> Json.Str("anything:Thing"),
      "knora-api:attachedToProject" -> Json.Obj("@id" -> Json.Str(projectIri)),
      "rdfs:label"                  -> Json.Str("backfill-value-has-xml-e2e"),
      "anything:hasText"            -> textValueJson(xml, mapping, None),
      "@context"                    -> context,
    )
    for {
      doc <- TestApiClient.postJsonLdDocument(uri"/v2/resources", body.toString, anythingUser1).flatMap(_.assert200)
      iri <- ZIO.fromEither(doc.body.getRequiredString(JsonLDKeywords.ID)).mapError(new RuntimeException(_))
    } yield iri
  }

  private def updateText(
    resourceIri: String,
    valueIri: String,
    xml: String,
    mapping: StandoffMappingIri,
  ): RIO[TestApiClient, Unit] = {
    val body = Json.Obj(
      "@id"              -> Json.Str(resourceIri),
      "@type"            -> Json.Str("anything:Thing"),
      "anything:hasText" -> textValueJson(xml, mapping, Some(valueIri)),
      "@context"         -> context,
    )
    TestApiClient.putJsonLd(uri"/v2/values", body.toString, anythingUser1).flatMap(_.assert200).unit
  }

  private def deleteText(resourceIri: String, valueIri: String): RIO[TestApiClient, Unit] = {
    val body = Json.Obj(
      "@id"              -> Json.Str(resourceIri),
      "@type"            -> Json.Str("anything:Thing"),
      "anything:hasText" -> Json.Obj(
        "@id"                     -> Json.Str(valueIri),
        "@type"                   -> Json.Str("knora-api:TextValue"),
        "knora-api:deleteComment" -> Json.Str("deleted by the backfill e2e spec"),
      ),
      "@context" -> context,
    )
    TestApiClient.postJsonLd(uri"/v2/values/delete", body.toString, anythingUser1).flatMap(_.assert200).unit
  }

  private def createCustomMapping: RIO[TestApiClient, Unit] = {
    val params =
      s"""{
         |  "knora-api:mappingHasName": "BackfillHTMLMapping",
         |  "knora-api:attachedToProject": { "@id": "$projectIri" },
         |  "rdfs:label": "mapping of the valueHasXml backfill e2e spec",
         |  "@context": {
         |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
         |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
         |  }
         |}""".stripMargin
    val body = Seq(
      multipart("json", params).contentType("application/json"),
      multipartFile("xml", Paths.get("test_data/test_route/texts/mappingForHTML.xml")).contentType("text/xml(UTF-8)"),
    )
    TestApiClient.postMultiPart[Json](uri"/v2/mapping", body, anythingUser1).flatMap(_.assert200).unit
  }

  private def ts[A](f: TriplestoreService => Task[A]): ZIO[TriplestoreService, Throwable, A] =
    ZIO.serviceWithZIO[TriplestoreService](f)

  private def currentValueOf(resourceIri: String): RIO[TriplestoreService, String] =
    ts(
      _.query(
        Select(
          s"SELECT ?v WHERE { GRAPH <$dataGraph> { <$resourceIri> <http://www.knora.org/ontology/0001/anything#hasText> ?v } }",
        ),
      ),
    ).map(_.getFirstOrThrow("v"))

  private def selectFirst(query: String, variable: String): RIO[TriplestoreService, Option[String]] =
    ts(_.query(Select(query))).map(_.getFirst(variable))

  private def creationDateOf(valueIri: String): RIO[TriplestoreService, String] =
    selectFirst(
      s"SELECT ?d WHERE { GRAPH <$dataGraph> { <$valueIri> <${kb}valueCreationDate> ?d } }",
      "d",
    ).someOrFail(new RuntimeException(s"$valueIri has no creation date"))

  private def previousVersionOf(valueIri: String): RIO[TriplestoreService, String] =
    selectFirst(
      s"SELECT ?p WHERE { GRAPH <$dataGraph> { <$valueIri> <${kb}previousValue> ?p } }",
      "p",
    ).someOrFail(new RuntimeException(s"$valueIri has no previous version"))

  private def storedXml(valueIri: String): RIO[TriplestoreService, Option[String]] =
    selectFirst(s"SELECT ?xml WHERE { GRAPH <$dataGraph> { <$valueIri> <${kb}valueHasXml> ?xml } }", "xml")

  private val removeAllStoredXml: RIO[TriplestoreService, Unit] =
    ts(_.query(Update(s"DELETE WHERE { GRAPH <$dataGraph> { ?v <${kb}valueHasXml> ?xml } }")))

  private def removeStoredXml(valueIri: String): RIO[TriplestoreService, Unit] =
    ts(_.query(Update(s"DELETE WHERE { GRAPH <$dataGraph> { <$valueIri> <${kb}valueHasXml> ?xml } }")))

  /** The gate of `ValueHasXmlBackfillQuery.selectCandidates`, minus the given values. */
  private def candidateLeft(except: Seq[String] = Seq.empty): RIO[TriplestoreService, Boolean] = {
    val excluded = if (except.isEmpty) "" else s"FILTER(?v NOT IN (${except.map(i => s"<$i>").mkString(", ")}))"
    ts(
      _.query(
        Ask(s"""ASK {
               |  GRAPH <$dataGraph> {
               |    ?v a <${kb}TextValue> ; <${kb}valueHasMapping> ?m .
               |    FILTER EXISTS { ?v <${kb}valueHasStandoff> ?n }
               |    FILTER NOT EXISTS { ?v <${kb}valueHasXml> ?x }
               |    $excluded
               |  }
               |}""".stripMargin),
      ),
    )
  }

  private val graphSnapshot: RIO[TriplestoreService, Set[(String, String, String)]] =
    ts(_.query(Select(s"SELECT ?s ?p ?o WHERE { GRAPH <$dataGraph> { ?s ?p ?o } }"))).map(
      _.results.bindings.map(row => (row.getRequired("s"), row.getRequired("p"), row.getRequired("o"))).toSet,
    )

  private def postBackfill(user: User = rootUser): RIO[TestApiClient, StatusCode] =
    TestApiClient.postJson[Json](backfillUri, user).map(_.code)

  private final case class StillRunning() extends RuntimeException("a backfill is still running")

  /**
   * Waits until no run is active. The POST that observes this starts one more run; with no candidate left it
   * only selects and ends, and it changes no triple.
   */
  private val awaitBackfillIdle: RIO[TestApiClient, Unit] =
    postBackfill().flatMap {
      case StatusCode.Accepted => ZIO.unit
      case StatusCode.Conflict => ZIO.fail(StillRunning())
      case other               => ZIO.die(new IllegalStateException(s"unexpected status $other"))
    }
      .retry(pollSchedule)

  private def awaitNoCandidate(except: Seq[String] = Seq.empty): RIO[TriplestoreService, Unit] =
    candidateLeft(except)
      .filterOrFail(left => !left)(StillRunning())
      .retry(pollSchedule)
      .unit

  /** Runs a backfill to completion: waits for a free instance, starts a run and waits for it. */
  private def runBackfillToCompletion(except: Seq[String] = Seq.empty): RIO[TriplestoreService & TestApiClient, Unit] =
    awaitBackfillIdle *> awaitNoCandidate(except) *> awaitBackfillIdle

  private def servedXml(resourceIri: String, version: Option[String]): RIO[TestApiClient, String] = {
    val update = version.fold[RequestUpdate[JsonLDDocument]](identity)(addVersionQueryParam(_))
    for {
      doc   <- TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri", rootUser, update).flatMap(_.assert200)
      value <- ZIO.fromEither(doc.body.getRequiredObject(hasTextIri)).mapError(new RuntimeException(_))
      xml   <- ZIO.fromEither(value.getRequiredString(KA.TextValueAsXml)).mapError(new RuntimeException(_))
    } yield xml
  }

  private def canonical(xml: String, mappingIri: StandoffMappingIri): RIO[StandoffMappingService, String] =
    ZIO
      .serviceWithZIO[StandoffMappingService](_.getMappingV2(mappingIri))
      .flatMap(mapping => ZIO.attempt(StandoffTagUtilV2.canonicalize(xml, mapping)))

  /** The stored literal and the served XML are the same document after canonicalization, and neither is empty. */
  private def storedEqualsServed(
    resourceIri: String,
    valueIri: String,
    mapping: StandoffMappingIri,
    version: Option[String] = None,
  ): RIO[TriplestoreService & TestApiClient & StandoffMappingService, TestResult] =
    for {
      stored      <- storedXml(valueIri).someOrFail(new RuntimeException(s"$valueIri has no valueHasXml"))
      served      <- servedXml(resourceIri, version)
      storedCanon <- canonical(stored, mapping)
      servedCanon <- canonical(served, mapping)
    } yield assertTrue(stored.nonEmpty, storedCanon == servedCanon)

  private val failingValue    = "http://rdfh.ch/0001/backfill-e2e/values/failing"
  private val renderableValue = "http://rdfh.ch/0001/backfill-e2e/values/renderable"

  private val insertFailingAndRenderableValues: RIO[TriplestoreService, Unit] =
    ts(
      _.query(
        Update(s"""PREFIX kb: <$kb>
                  |PREFIX standoff: <http://www.knora.org/ontology/standoff#>
                  |INSERT DATA {
                  |  GRAPH <$dataGraph> {
                  |    <$failingValue> a kb:TextValue ;
                  |      kb:isDeleted false ;
                  |      kb:valueHasString "unmapped" ;
                  |      kb:valueHasMapping <http://rdfh.ch/standoff/mappings/StandardMapping> ;
                  |      kb:valueHasStandoff <$failingValue/standoff/0> .
                  |    <$failingValue/standoff/0> a standoff:StandoffNonexistentTag ;
                  |      kb:standoffTagHasStart 0 ;
                  |      kb:standoffTagHasEnd 8 ;
                  |      kb:standoffTagHasStartIndex 0 ;
                  |      kb:standoffTagHasUUID "6d1b7b5e-9c1c-4f0e-8a27-5b9e5f4d2a01" .
                  |    <$renderableValue> a kb:TextValue ;
                  |      kb:isDeleted false ;
                  |      kb:valueHasString "renderable" ;
                  |      kb:valueHasMapping <http://rdfh.ch/standoff/mappings/StandardMapping> ;
                  |      kb:valueHasStandoff <$renderableValue/standoff/0> .
                  |    <$renderableValue/standoff/0> a standoff:StandoffRootTag ;
                  |      standoff:standoffRootTagHasDocumentType "html" ;
                  |      kb:standoffTagHasStart 0 ;
                  |      kb:standoffTagHasEnd 10 ;
                  |      kb:standoffTagHasStartIndex 0 ;
                  |      kb:standoffTagHasUUID "6d1b7b5e-9c1c-4f0e-8a27-5b9e5f4d2a02" .
                  |  }
                  |}""".stripMargin),
      ),
    )

  private val deleteFailingAndRenderableValues: RIO[TriplestoreService, Unit] =
    ts(
      _.query(
        Update(
          Seq(failingValue, renderableValue)
            .flatMap(v => Seq(v, s"$v/standoff/0"))
            .map(subject => s"DELETE WHERE { GRAPH <$dataGraph> { <$subject> ?p ?o } }")
            .mkString(";\n"),
        ),
      ),
    )

  override val e2eSpec: Spec[env, Any] = suite("POST /admin/maintenance/backfill-value-has-xml")(
    test("returns 403 when the authenticated user is not a SystemAdmin") {
      postBackfill(normalUser).map(code => assertTrue(code == StatusCode.Forbidden))
    },
    test("set up formatted values of every kind and remove all stored XML of the project") {
      for {
        _ <- createCustomMapping

        historyResource  <- createResourceWithText(plainXml, StandoffMappingIri.StandardMapping)
        historyV1        <- currentValueOf(historyResource)
        historyV1Created <- creationDateOf(historyV1)
        _                <- ZIO.sleep(10.millis)
        _                <- updateText(historyResource, historyV1, editedXml, StandoffMappingIri.StandardMapping)
        historyV2        <- currentValueOf(historyResource)

        deletedResource <- createResourceWithText(deletedXml, StandoffMappingIri.StandardMapping)
        deletedValue    <- currentValueOf(deletedResource)
        _               <- deleteText(deletedResource, deletedValue)

        customResource <- createResourceWithText(customMappingXml, customMappingIri)
        customValue    <- currentValueOf(customResource)

        internalResource <- createResourceWithText(internalReferenceXml, StandoffMappingIri.StandardMapping)
        internalValue    <- currentValueOf(internalResource)

        manyResource <- createResourceWithText(manyTagsXml, StandoffMappingIri.StandardMapping)
        manyValue    <- currentValueOf(manyResource)

        _ = fixture.set(
              Fixture(
                historyResource,
                historyV1,
                historyV1Created,
                historyV2,
                deletedValue,
                customResource,
                customValue,
                internalResource,
                internalValue,
                manyResource,
                manyValue,
              ),
            )
        _             <- removeAllStoredXml
        candidates    <- candidateLeft()
        deletedStored <- storedXml(deletedValue)
        v1Previous    <- previousVersionOf(historyV2)
      } yield assertTrue(candidates, deletedStored.isEmpty, v1Previous == historyV1)
    },
    test("starts the backfill with 202 and gives every candidate its XML") {
      for {
        code <- postBackfill()
        _    <- awaitNoCandidate()
        _    <- awaitBackfillIdle
      } yield assertTrue(code == StatusCode.Accepted)
    },
    test("stored XML equals served XML for the current version") {
      val f = fixture.get
      storedEqualsServed(f.historyResource, f.historyV2, StandoffMappingIri.StandardMapping)
    },
    test("stored XML equals served XML for a historical version") {
      val f = fixture.get
      storedEqualsServed(
        f.historyResource,
        f.historyV1,
        StandoffMappingIri.StandardMapping,
        Some(f.historyV1Created),
      )
    },
    test("the deleted value version carries valueHasXml") {
      val f = fixture.get
      storedXml(f.deletedValue).map(xml => assertTrue(xml.exists(_.nonEmpty)))
    },
    test("stored XML equals served XML for a value with a custom mapping") {
      val f = fixture.get
      storedEqualsServed(f.customResource, f.customValue, customMappingIri)
    },
    test("stored XML equals served XML for a value with internal references") {
      val f = fixture.get
      storedEqualsServed(f.internalResource, f.internalValue, StandoffMappingIri.StandardMapping)
    },
    test("stored XML of mutually referencing tags equals the submitted XML and the served XML") {
      for {
        resource       <- createResourceWithText(mutualReferencesXml, StandoffMappingIri.StandardMapping)
        value          <- currentValueOf(resource)
        _              <- removeStoredXml(value)
        _              <- runBackfillToCompletion()
        stored         <- storedXml(value).someOrFail(new RuntimeException("no valueHasXml"))
        storedCan      <- canonical(stored, StandoffMappingIri.StandardMapping)
        inputCan       <- canonical(mutualReferencesXml, StandoffMappingIri.StandardMapping)
        storedEqServed <- storedEqualsServed(resource, value, StandoffMappingIri.StandardMapping)
      } yield assertTrue(storedCan == inputCan) && storedEqServed
    },
    test("stored XML equals served XML for a value with at least 100 standoff tags") {
      val f = fixture.get
      for {
        result <- storedEqualsServed(f.manyResource, f.manyValue, StandoffMappingIri.StandardMapping)
        tags   <-
          selectFirst(
            s"SELECT (COUNT(?n) AS ?count) WHERE { GRAPH <$dataGraph> { <${f.manyValue}> <${kb}valueHasStandoff> ?n } }",
            "count",
          )
      } yield result && assertTrue(tags.exists(_.toInt >= 100))
    },
    test("a re-run leaves the project graph unchanged") {
      for {
        before <- graphSnapshot
        _      <- runBackfillToCompletion()
        after  <- graphSnapshot
      } yield assertTrue(before.nonEmpty, before == after)
    },
    test("returns 409 while a run is active") {
      val f = fixture.get
      for {
        _      <- removeStoredXml(f.customValue)
        _      <- awaitBackfillIdle
        second <- postBackfill()
        _      <- awaitNoCandidate()
        _      <- awaitBackfillIdle
        stored <- storedXml(f.customValue)
      } yield assertTrue(second == StatusCode.Conflict, stored.isDefined)
    },
    test("a value that cannot be rendered stays without XML while all other values gain it") {
      val body = for {
        _          <- insertFailingAndRenderableValues
        _          <- runBackfillToCompletion(except = Seq(failingValue))
        failing    <- storedXml(failingValue)
        renderable <- storedXml(renderableValue)
        onlyFailed <- candidateLeft(except = Seq(failingValue))
        failedLeft <- candidateLeft()
      } yield assertTrue(failing.isEmpty, renderable.exists(_.nonEmpty), !onlyFailed, failedLeft)
      val cleanup = deleteFailingAndRenderableValues *> awaitBackfillIdle
      body
        .ensuring(cleanup.orDieWith(e => new IllegalStateException(s"backfill E2E cleanup failed: $e", e)))
        .zipWith(candidateLeft())((result, left) => result && assertTrue(!left))
    },
  )
}
