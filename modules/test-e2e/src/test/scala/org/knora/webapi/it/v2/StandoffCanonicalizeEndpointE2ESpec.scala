/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.it.v2

import org.junit.runner.RunWith
import sttp.client4.*
import sttp.model.MediaType
import sttp.model.StatusCode
import zio.*
import zio.json.ast.*
import zio.test.*

import java.nio.file.Paths

import dsp.valueobjects.Iri
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.*
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.OntologyConstants.KnoraBase as KB
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.common.ResourceIri
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.FileUtil

/**
 * E2E tests for the experimental `POST /v2/standoff/canonicalize` endpoint.
 *
 * Beyond wiring (200, `application/xml`, 400 on bad input) these assert the two properties the endpoint
 * exists to provide for client-side change detection of rich text:
 *   - idempotency: `canonicalize(canonicalize(x)) == canonicalize(x)`, and
 *   - equivalence with a stored value: `canonicalize(x) == textValueAsXml` after `x` is stored.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class StandoffCanonicalizeEndpointE2ESpec extends E2EZSpec {

  private val freetestOntologyIRI = "http://0.0.0.0:3333/ontology/0001/freetest/v2#"
  private val canonicalizeUri     = uri"/v2/standoff/canonicalize"
  private val xmlMediaType        = MediaType("application", "xml")
  private val standardHtmlPath    = "test_data/test_route/texts/StandardHTML.xml"

  override val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/anything"),
    RdfDataObject(
      path = "test_data/project_ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest",
    ),
    RdfDataObject(path = "test_data/project_data/freetest-data.ttl", name = "http://www.knora.org/data/0001/freetest"),
  )

  private val validationFun: (String, => Nothing) => String = (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)

  private def canonicalize(xml: String): RIO[TestApiClient, String] =
    TestApiClient.postString(canonicalizeUri, xml, xmlMediaType, anythingUser1).flatMap(_.assert200)

  private def createResourceWithTextValue(xmlContent: String): RIO[TestApiClient, JsonLDDocument] = {
    val jsonLDEntity = Json.Obj(
      ("@type", Json.Str("freetest:FreeTest")),
      ("knora-api:attachedToProject", Json.Obj("@id", Json.Str("http://rdfh.ch/projects/0001"))),
      ("rdfs:label", Json.Str("canonicalize-endpoint-test")),
      (
        "freetest:hasText",
        Json.Obj(
          ("@type", Json.Str("knora-api:TextValue")),
          ("knora-api:textValueAsXml", Json.Str(xmlContent)),
          ("knora-api:textValueHasMapping", Json.Obj("@id", Json.Str(KB.StandardMapping))),
        ),
      ),
      (
        "@context",
        Json.Obj(
          ("freetest", Json.Str(freetestOntologyIRI)),
          ("rdfs", Json.Str("http://www.w3.org/2000/01/rdf-schema#")),
          ("knora-api", Json.Str("http://api.knora.org/ontology/knora-api/v2#")),
        ),
      ),
    )
    for {
      response <- TestApiClient.postJsonLd(uri"/v2/resources", jsonLDEntity.toString(), anythingUser1).orDie
      jsonLd   <- response.assert200.mapAttempt(JsonLDUtil.parseJsonLD).orDie
    } yield jsonLd
  }

  private def readTextValueAsXml(resourceIri: ResourceIri): RIO[TestApiClient, String] =
    for {
      doc   <- TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri", anythingUser1).flatMap(_.assert200)
      value <- ZIO.fromEither(doc.body.getRequiredObject(s"${freetestOntologyIRI}hasText")).mapError(new Exception(_))
      xml   <- ZIO.fromEither(value.getRequiredString(KA.TextValueAsXml)).mapError(new Exception(_))
    } yield xml

  override val e2eSpec: Spec[env, Any] = suite("POST /v2/standoff/canonicalize (experimental)")(
    test("normalises standard-mapping XML and is idempotent") {
      for {
        input <- ZIO.attempt(FileUtil.readTextFile(Paths.get(standardHtmlPath)))
        pass1 <- canonicalize(input)
        pass2 <- canonicalize(pass1)
      } yield assertTrue(pass1 == pass2)
    },
    test("responds 200 with an application/xml body") {
      for {
        input    <- ZIO.attempt(FileUtil.readTextFile(Paths.get(standardHtmlPath)))
        response <- TestApiClient.postString(canonicalizeUri, input, xmlMediaType, anythingUser1)
        body     <- response.assert200
      } yield assertTrue(
        response.code == StatusCode.Ok,
        response.contentType.exists(_.contains("xml")),
        body.startsWith("<?xml"),
      )
    },
    test("its output equals the textValueAsXml of a stored value") {
      for {
        input      <- ZIO.attempt(FileUtil.readTextFile(Paths.get(standardHtmlPath)))
        canonical  <- canonicalize(input)
        doc        <- createResourceWithTextValue(input)
        resourceIri = doc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
        storedXml  <- readTextValueAsXml(ResourceIri.unsafeFrom(resourceIri))
      } yield assertTrue(canonical == storedXml)
    },
    test("rejects malformed XML with 400") {
      for {
        response <- TestApiClient.postString(canonicalizeUri, "<p>unclosed", xmlMediaType, anythingUser1)
      } yield assertTrue(response.code == StatusCode.BadRequest)
    },
    test("rejects unauthenticated requests") {
      for {
        input    <- ZIO.attempt(FileUtil.readTextFile(Paths.get(standardHtmlPath)))
        response <- TestApiClient.postString(canonicalizeUri, input, xmlMediaType)
      } yield assertTrue(response.code == StatusCode.Forbidden)
    },
    test("does not resolve external XML entities (XXE)") {
      // A DOCTYPE with an external SYSTEM entity must be rejected outright, never resolved/inlined.
      val xxe =
        """<?xml version="1.0"?><!DOCTYPE r [<!ENTITY x SYSTEM "file:///etc/passwd">]>""" +
          """<text documentType="html"><p>&x;</p></text>"""
      for {
        response <- TestApiClient.postString(canonicalizeUri, xxe, xmlMediaType, anythingUser1)
      } yield assertTrue(response.code == StatusCode.BadRequest)
    },
  )
}
