/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.values

import org.junit.runner.RunWith
import sttp.client4.*
import zio.*
import zio.json.ast.Json
import zio.test.*

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2ez.ValueHasXmlTestSupport.*
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex as KA
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.models.filemodels.FileType
import org.knora.webapi.models.filemodels.UploadFileRequest
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.common.StandoffMappingIri
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.testservices.TestDspIngestClient

/**
 * A formatted text value written through `POST /v2/values` and `PUT /v2/values` stores `kb:valueHasXml`, byte-identical
 * to the `textValueAsXml` the API serves. Unformatted text stores none.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class ValueHasXmlOnValueWriteE2ESpec extends E2EZSpec {

  private val standard = StandoffMappingIri.StandardMapping

  private def createFormattedValue(resourceIri: String, xml: String, mapping: StandoffMappingIri) =
    createValue(resourceIri, textValueJson(xml, mapping, None))

  /** `PUT /v2/values` with an arbitrary text value; the IRI of the new version is read from the triplestore. */
  private def updateValue(resourceIri: String, value: Json.Obj): RIO[TestApiClient & TriplestoreService, String] = {
    val body = Json.Obj(
      "@id"              -> Json.Str(resourceIri),
      "@type"            -> Json.Str("anything:Thing"),
      "anything:hasText" -> value,
      "@context"         -> context,
    )
    TestApiClient.putJsonLd(uri"/v2/values", body.toString, anythingUser1).flatMap(_.assert200) *>
      currentValueOf(resourceIri)
  }

  /** Creates a bare resource and one formatted value on it through `POST /v2/values`; returns both IRIs. */
  private def resourceWithCreatedValue(xml: String, mapping: StandoffMappingIri = standard) =
    for {
      resource <- createThing()
      value    <- createFormattedValue(resource, xml, mapping)
    } yield (resource, value)

  private def freshMappingName(prefix: String): UIO[String] =
    Random.nextUUID.map(id => s"$prefix${id.toString.take(8)}")

  /** Uploads the XSL file, registers it as an XSLTransformation resource and returns its IRI. */
  private def createXslTransformation: RIO[TestApiClient & TestDspIngestClient, String] =
    for {
      uploaded <- ZIO.serviceWithZIO[TestDspIngestClient](
                    _.uploadFile(Paths.get("test_data/test_route/texts/freetestCustomMappingTransformation.xsl")),
                  )
      body = UploadFileRequest
               .make(fileType = FileType.TextFile, internalFilename = uploaded.internalFilename)
               .toJsonLd(className = Some("XSLTransformation"))
      doc <- TestApiClient.postJsonLdDocument(uri"/v2/resources", body, anythingUser1).flatMap(_.assert200)
      iri <- ZIO.fromEither(doc.body.getRequiredString(JsonLDKeywords.ID)).mapError(new RuntimeException(_))
    } yield iri

  /** Registers a mapping whose default XSL transformation is `xslIri`, derived from the freetest fixture. */
  private def createMappingWithTransformation(
    mappingName: String,
    xslIri: String,
  ): RIO[TestApiClient, StandoffMappingIri] =
    for {
      template <-
        ZIO.attempt(
          Files.readString(Paths.get("test_data/test_route/texts/freetestCustomMappingWithTransformation.xml")),
        )
      file <- ZIO.attempt(Files.createTempFile("mapping-with-xsl", ".xml"))
      _    <- ZIO.attempt(Files.writeString(file, template.replace("http://rdfh.ch/0001/xYSnl8dmTw2RM6KQGVqNDA", xslIri)))
      iri  <- createCustomMapping(mappingName, file: Path).ensuring(ZIO.attempt(Files.deleteIfExists(file)).ignore)
    } yield iri

  private def servedHtml(resourceIri: String): RIO[TestApiClient, Option[String]] =
    for {
      doc   <- TestApiClient.getJsonLdDocument(uri"/v2/resources/$resourceIri", rootUser).flatMap(_.assert200)
      value <- ZIO.fromEither(doc.body.getRequiredObject(hasTextIri)).mapError(new RuntimeException(_))
      html  <- ZIO.fromEither(value.getString(KA.TextValueAsHtml)).mapError(new RuntimeException(_))
    } yield html

  private val paragraphsXml = xmlDoc(
    "<p>1st: alpha, beta;</p><p>2nd: <strong>gamma</strong> (delta)</p><p>3rd: <em>epsilon</em> &amp; zeta?</p>",
  )

  private val escapingXml = xmlDoc(
    """<p>She said "hi" and 'bye' \ back\\slash &amp; 🦉 owl, 🦉🦉 flock</p><p>C:\dir\\file "q" 'a' &amp;amp;</p>""",
  )

  private def largeXml(offset: Int) =
    xmlDoc((1 to 3000).map(i => s"<p>line ${i + offset}: <em>${i * 7 + offset}</em></p>").mkString)

  override val e2eSpec: Spec[env, Any] = suite("valueHasXml written by POST and PUT /v2/values")(
    test("create stores valueHasXml byte-identical to the served textValueAsXml") {
      for {
        (resource, value) <- resourceWithCreatedValue(xmlDoc("<p>one <strong>2</strong> three: <em>4</em>.</p>"))
        result            <- storedByteIdenticalToServed(resource, value)
      } yield result
    },
    test("an edit stores the new version's own valueHasXml and the previous version keeps its own") {
      for {
        (resource, v1) <- resourceWithCreatedValue(xmlDoc("<p>v1 <strong>first</strong> (1)</p>"))
        v1Created      <- creationDateOf(v1)
        _              <- ZIO.sleep(10.millis)
        _              <- updateText(resource, v1, xmlDoc("<p>v2 <em>second</em> [2], longer</p>"), standard)
        v2             <- currentValueOf(resource)
        v1Stored       <- storedXml(v1)
        v2Stored       <- storedXml(v2)
        newVersion     <- storedByteIdenticalToServed(resource, v2)
        oldVersion     <- storedByteIdenticalToServed(resource, v1, Some(v1Created))
      } yield newVersion && oldVersion && assertTrue(
        v1 != v2,
        v1Stored.isDefined,
        v2Stored.isDefined,
        v1Stored != v2Stored,
      )
    },
    test("an edit into unformatted text leaves the new version without valueHasXml and the previous one with its own") {
      for {
        (resource, v1) <- resourceWithCreatedValue(xmlDoc("<p>to be <em>flattened</em> soon</p>"))
        v1Created      <- creationDateOf(v1)
        _              <- ZIO.sleep(10.millis)
        v2             <- updateValue(resource, plainTextJson("now plain: 1, 2, 3", Some(v1)))
        v1Stored       <- storedXml(v1)
        v2Stored       <- storedXml(v2)
        oldVersion     <- storedByteIdenticalToServed(resource, v1, Some(v1Created))
      } yield oldVersion && assertTrue(v1 != v2, v1Stored.isDefined, v2Stored.isEmpty)
    },
    test("a custom mapping stores valueHasXml byte-identical to the served textValueAsXml") {
      for {
        mappingName <- freshMappingName("OnWriteCustom")
        mapping     <- createCustomMapping(mappingName)
        xml          =
          xmlDoc(
            """<p>A <span class="event" data-description="an &quot;event&quot;" data-date="GREGORIAN:2017-01-27 CE">happening</span> on 27.1.</p>""",
          )
        (resource, value) <- resourceWithCreatedValue(xml, mapping)
        result            <- storedByteIdenticalToServed(resource, value)
      } yield result
    },
    test("with an XSL transformation valueHasXml equals the served textValueAsXml, not the served textValueAsHtml") {
      for {
        mappingName <- freshMappingName("OnWriteXsl")
        xslIri      <- createXslTransformation
        mapping     <- createMappingWithTransformation(mappingName, xslIri)
        xml          =
          """<?xml version="1.0" encoding="UTF-8"?>
            |<text><section>sample no. 9 of <italic>standoff</italic>, rendered twice</section></text>""".stripMargin
        (resource, value) <- resourceWithCreatedValue(xml, mapping)
        html              <- servedHtml(resource)
        servedAsXml       <- servedXml(resource, None)
        stored            <- storedXml(value)
        identical         <- storedByteIdenticalToServed(resource, value)
      } yield identical && assertTrue(
        html.exists(_.nonEmpty),
        html.exists(_ != servedAsXml),
        stored.isDefined,
        stored != html,
      )
    },
    test("a multi-paragraph value stores valueHasXml byte-identical to the served textValueAsXml") {
      for {
        (resource, value) <- resourceWithCreatedValue(paragraphsXml)
        result            <- storedByteIdenticalToServed(resource, value)
      } yield result
    },
    test("quotes, backslashes, ampersands and astral-plane characters survive the stored literal") {
      for {
        (resource, value) <- resourceWithCreatedValue(escapingXml)
        stored            <- storedXml(value)
        result            <- storedByteIdenticalToServed(resource, value)
      } yield result && assertTrue(
        stored.exists(s => s.contains("\uD83E\uDD89") && s.contains("\\\\slash") && s.contains("&amp;")),
      )
    },
    test("a document of 6000 standoff tags is created and edited with valueHasXml identical to the served XML") {
      for {
        started        <- Clock.nanoTime
        (resource, v1) <- resourceWithCreatedValue(largeXml(0))
        created        <- storedByteIdenticalToServed(resource, v1)
        createdAt      <- Clock.nanoTime
        _              <- updateText(resource, v1, largeXml(100000), standard)
        v2             <- currentValueOf(resource)
        edited         <- storedByteIdenticalToServed(resource, v2)
        finished       <- Clock.nanoTime
        tags           <- selectFirst(
                  s"SELECT (COUNT(?n) AS ?count) WHERE { GRAPH <$dataGraph> { <$v2> <${kb}valueHasStandoff> ?n } }",
                  "count",
                )
        _ <-
          ZIO.logInfo(
            s"large document: create ${(createdAt - started) / 1000000} ms, edit ${(finished - createdAt) / 1000000} ms",
          )
      } yield created && edited && assertTrue(v1 != v2, tags.exists(_.toInt >= 6000))
    },
    test("an unformatted text value has no valueHasXml while a formatted sibling has one") {
      for {
        resource    <- createThing()
        plain       <- createValue(resource, plainTextJson("just text; 2 + 2 = 4"))
        formatted   <- createFormattedValue(resource, xmlDoc("<p>sibling <em>5</em></p>"), standard)
        plainXml    <- storedXml(plain)
        siblingXml  <- storedXml(formatted)
        plainString <- valueHasString(plain)
      } yield assertTrue(plainXml.isEmpty, siblingXml.exists(_.nonEmpty), plainString.contains("just text; 2 + 2 = 4"))
    },
  )
}
