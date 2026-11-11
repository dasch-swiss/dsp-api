/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.values

import org.junit.runner.RunWith
import sttp.client4.*
import sttp.model.StatusCode
import zio.*
import zio.json.ast.Json
import zio.test.*

import java.nio.file.Files
import java.nio.file.Paths

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2ez.ValueHasXmlTestSupport.*
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

/**
 * The BEOL `cell` tag carries five attributes. The rendering of tag attributes must give the same document whether it
 * runs on write, on read or in the backfill, so the stored `valueHasXml` and the served `textValueAsXml` are
 * canonically equal in each case.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ValueHasXmlFiveAttributeTagE2ESpec extends E2EZSpec {

  private val beolOnto            = "http://0.0.0.0:3333/ontology/0801/beol/v2#"
  private val beolHasText         = s"${beolOnto}hasText"
  private val beolDataGraph       = "http://www.knora.org/data/0801/beol"
  private val beolHasTextInternal = "http://www.knora.org/ontology/0801/beol#hasText"

  private val mappingName = "FiveAttributeBeolTei"
  private val mapping     = StandoffMappingIri.unsafeFrom(s"${beolProjectIri.value}/mappings/$mappingName")

  private val xml = xmlDoc(
    """<table><row><cell width="50" align="center" valign="top" rowspan="2" colspan="3">five attributes</cell></row></table>""",
  )

  private val pollSchedule = Schedule.spaced(500.millis) && Schedule.recurs(120)

  private val backfillUri = uri"/admin/maintenance/backfill-value-has-xml"

  /** The backfill endpoint answered 409: another backfill is active. Retried. */
  private case object StillRunning extends RuntimeException("a backfill is still running")

  /** The backfill has not restored the value's XML yet. Retried. */
  private case object NotRestoredYet extends RuntimeException("the value has no XML yet")

  private final case class UnexpectedStatus(code: StatusCode)
      extends RuntimeException(s"unexpected backfill status $code")

  /** The BEOL TEI mapping without its placeholder `defaultXSLTransformation`, which is not a valid IRI. */
  private val createBeolMapping: RIO[TestApiClient, StandoffMappingIri] =
    ZIO.acquireReleaseWith(ZIO.attempt(Files.createTempFile("beol-tei-mapping", ".xml")))(file =>
      ZIO.attempt(Files.deleteIfExists(file)).ignore,
    ) { file =>
      for {
        original  <- ZIO.attempt(Files.readString(Paths.get("test_data/test_route/texts/beol/BEOLTEIMapping.xml")))
        withoutXsl = original.replaceAll("(?s)<defaultXSLTransformation>.*?</defaultXSLTransformation>", "")
        _         <- ZIO
               .fail(new RuntimeException("The BEOL mapping still names a default XSL transformation"))
               .when(withoutXsl.contains("defaultXSLTransformation"))
        _   <- ZIO.attempt(Files.writeString(file, withoutXsl))
        iri <- createCustomMapping(mappingName, file, beolProjectIri.value, beolUser)
      } yield iri
    }

  private def createLetter: RIO[TestApiClient, String] = {
    val body = Json.Obj(
      "@type"                       -> Json.Str("beol:letter"),
      "knora-api:attachedToProject" -> Json.Obj("@id" -> Json.Str(beolProjectIri.value)),
      "rdfs:label"                  -> Json.Str("five-attribute-tag-e2e"),
      "beol:title"                  -> plainTextJson("five-attribute-tag-e2e"),
      "beol:hasText"                -> textValueJson(xml, mapping, None),
      "@context"                    -> Json.Obj(
        "knora-api" -> Json.Str("http://api.knora.org/ontology/knora-api/v2#"),
        "rdfs"      -> Json.Str("http://www.w3.org/2000/01/rdf-schema#"),
        "beol"      -> Json.Str(beolOnto),
      ),
    )
    TestApiClient
      .postJsonLdDocument(uri"/v2/resources", body.toString, beolUser)
      .flatMap(_.assert200)
      .flatMap(doc => ZIO.fromEither(doc.body.getRequiredString(JsonLDKeywords.ID)).mapError(new RuntimeException(_)))
  }

  private def valueOf(resourceIri: String): RIO[TriplestoreService, String] =
    select(s"SELECT ?v WHERE { GRAPH <$beolDataGraph> { <$resourceIri> <$beolHasTextInternal> ?v } }")
      .map(_.getFirstOrThrow("v"))

  private def removeStoredXml(valueIri: String): RIO[TriplestoreService, Unit] =
    update(s"DELETE WHERE { GRAPH <$beolDataGraph> { <$valueIri> <${kb}valueHasXml> ?xml } }")

  private val cellAttributes = Seq("width", "align", "valign", "rowspan", "colspan")

  private def cellKeepsAllAttributes(stored: String): TestResult = {
    val cell = "<cell\\b[^>]*>".r.findFirstIn(stored).getOrElse("")
    assertTrue(cellAttributes.forall(attr => cell.matches(s"""(?s).*\\s$attr=".*""")))
  }

  /** Starts a backfill as soon as none is active, then waits until the value has its XML again. */
  private def backfillUntilStored(valueIri: String): RIO[TriplestoreService & TestApiClient, String] =
    TestApiClient
      .postJson[Json](backfillUri, rootUser)
      .map(_.code)
      .flatMap {
        case StatusCode.Accepted => ZIO.unit
        case StatusCode.Conflict => ZIO.fail(StillRunning)
        case other               => ZIO.fail(UnexpectedStatus(other))
      }
      .retry(pollSchedule && Schedule.recurWhile[Throwable](_ == StillRunning)) *>
      storedXmlIn(valueIri, beolDataGraph)
        .someOrFail(NotRestoredYet)
        .retry(pollSchedule && Schedule.recurWhile[Throwable](_ == NotRestoredYet))

  private def canonicallyEqual(resource: String, value: String) =
    storedCanonicallyEqualsServed(resource, value, mapping, graph = beolDataGraph, textProperty = beolHasText)

  override val e2eSpec: Spec[env, Any] = suite("valueHasXml of a tag with five attributes")(
    test("write and read: the stored XML equals the served XML, canonically, and keeps all five attributes") {
      for {
        resource <- createLetter
        value    <- valueOf(resource)
        stored   <- storedXmlIn(value, beolDataGraph).someOrFail(new RuntimeException("no valueHasXml"))
        equal    <- canonicallyEqual(resource, value)
      } yield equal && cellKeepsAllAttributes(stored)
    },
    test("backfill: the restored XML equals the served XML, canonically, and keeps all five attributes") {
      for {
        resource <- createLetter
        value    <- valueOf(resource)
        _        <- removeStoredXml(value)
        removed  <- storedXmlIn(value, beolDataGraph)
        restored <- backfillUntilStored(value)
        equal    <- canonicallyEqual(resource, value)
      } yield equal && assertTrue(removed.isEmpty) && cellKeepsAllAttributes(restored)
    },
  ) @@ TestAspect.beforeAll(createBeolMapping) @@ TestAspect.sequential
}
