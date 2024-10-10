/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.headers.Accept
import org.scalatest.Inspectors.forEvery
import spray.json.*

import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.knora.webapi.*
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Simple
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.RdfFormatUtil
import org.knora.webapi.messages.util.rdf.Turtle
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.*

class OntologyFormatsE2ESpec extends E2ESpec {

  override lazy val rdfDataObjects: List[RdfDataObject] =
    List(
      RdfDataObject("test_data/project_ontologies/freetest-onto.ttl", "http://www.knora.org/ontology/0001/freetest"),
      RdfDataObject("test_data/project_ontologies/minimal-onto.ttl", "http://www.knora.org/ontology/0001/minimal"),
    )

  /**
   * Represents an HTTP GET test that requests ontology information.
   *
   * @param urlPath                     the URL path to be used in the request.
   * @param fileBasename                the basename of the test data file containing the expected response.
   */
  private case class HttpGetTest(urlPath: IRI, fileBasename: IRI, persistTtl: Boolean = false) {
    private def makeFile(fileEnding: String = "jsonld"): Path =
      Paths.get("..", "test_data", "generated_test_data", "ontologyR2RV2", s"$fileBasename.$fileEnding")

    private def storeAsTtl(): Unit = {
      val jsonStr = readFile()
      val model   = parseJsonLd(jsonStr)
      val ttlStr  = RdfFormatUtil.format(model, Turtle)
      val newFile = makeFile("ttl")
      Files.createDirectories(newFile.getParent)
      val _ = FileUtil.writeTextFile(newFile, ttlStr)
      ()
    }

    /**
     * Reads the expected response file.
     *
     * @return the contents of the file.
     */
    def readFile(): String =
      FileUtil.readTextFile(makeFile())

    def fileExists: Boolean =
      Files.exists(makeFile())

    def writeReceived(responseStr: String): Unit = {
      val newOutputFile = makeFile()
      Files.createDirectories(newOutputFile.getParent)
      FileUtil.writeTextFile(newOutputFile, responseStr)
      if (persistTtl) storeAsTtl()
      else ()
    }
  }

  private def urlEncodeIri(iri: IRI): String =
    URLEncoder.encode(iri, "UTF-8")

  private def checkTestCase(httpGetTest: HttpGetTest) = {
    val responseJsonLd = getResponse(httpGetTest.urlPath, RdfMediaTypes.`application/ld+json`)
    val responseTtl    = getResponse(httpGetTest.urlPath, RdfMediaTypes.`text/turtle`)
    val responseRdfXml = getResponse(httpGetTest.urlPath, RdfMediaTypes.`application/rdf+xml`)

    if (!httpGetTest.fileExists) {
      httpGetTest.writeReceived(responseJsonLd)
      throw new AssertionError(s"No approved data available in file ${httpGetTest.fileBasename}")
    }

    val approvedJsonLd = httpGetTest.readFile()
    if (JsonParser(responseJsonLd) != JsonParser(approvedJsonLd)) {
      httpGetTest.writeReceived(responseJsonLd)
      throw new AssertionError(
        s"""|
            |The response did not equal the approved data.
            |
            |Response:
            |
            |$responseJsonLd
            |
            |
            |${"=" * 120}
            |
            |
            |Approved data:
            |
            |$approvedJsonLd
            |
            |""".stripMargin,
      )
    }

    assert(parseTurtle(responseTtl) == parseJsonLd(responseJsonLd))
    assert(parseRdfXml(responseRdfXml) == parseJsonLd(responseJsonLd))
  }

  private def getResponse(url: String, mediaType: MediaType.NonBinary) = {
    val request     = Get(s"$baseApiUrl$url").addHeader(Accept(mediaType))
    val response    = singleAwaitingRequest(request)
    val responseStr = responseToString(response)
    assert(response.status == StatusCodes.OK, responseStr)
    responseStr
  }

  private object TestCases {
    private val knoraApiOntologySimple =
      HttpGetTest(
        urlPath = s"/v2/ontologies/allentities/${urlEncodeIri(KnoraApiV2Simple.KnoraApiOntologyIri)}",
        fileBasename = "knoraApiOntologySimple",
      )

    private val knoraApiOntologyComplex =
      HttpGetTest(
        urlPath = s"/v2/ontologies/allentities/${urlEncodeIri(KnoraApiV2Complex.KnoraApiOntologyIri)}",
        fileBasename = "knoraApiOntologyWithValueObjects",
      )

    private val salsahGuiOntology =
      HttpGetTest(urlPath = "/ontology/salsah-gui/v2", fileBasename = "salsahGuiOntology")

    private val standoffOntology =
      HttpGetTest(urlPath = "/ontology/standoff/v2", fileBasename = "standoffOntologyWithValueObjects")

    private val knoraApiDateSegmentSimple =
      HttpGetTest(
        urlPath = s"/v2/ontologies/classes/${urlEncodeIri(KnoraApiV2Simple.Date)}",
        fileBasename = "knoraApiDate",
      )

    private val knoraApiDateSegmentComplex =
      HttpGetTest(
        urlPath = s"/v2/ontologies/classes/${urlEncodeIri(KnoraApiV2Complex.DateValue)}",
        fileBasename = "knoraApiDateValue",
      )

    private val knoraApiHasColorSegmentSimple =
      HttpGetTest(
        urlPath = s"/v2/ontologies/properties/${urlEncodeIri(KnoraApiV2Simple.HasColor)}",
        fileBasename = "knoraApiSimpleHasColor",
      )

    private val knoraApiHasColorSegmentComplex =
      HttpGetTest(
        urlPath = s"/v2/ontologies/properties/${urlEncodeIri(KnoraApiV2Complex.HasColor)}",
        fileBasename = "knoraApiWithValueObjectsHasColor",
      )

    private val anythingOntologyMetadata =
      HttpGetTest(
        urlPath = s"/v2/ontologies/metadata/${urlEncodeIri(SharedTestDataADM.anythingProjectIri)}",
        fileBasename = "anythingOntologyMetadata",
      )

    private val anythingOntologySimple =
      HttpGetTest(
        urlPath =
          s"/v2/ontologies/allentities/${urlEncodeIri(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost_SIMPLE)}",
        fileBasename = "anythingOntologySimple",
      )

    private val anythingOntologyComplex =
      HttpGetTest(
        urlPath =
          s"/v2/ontologies/allentities/${urlEncodeIri(SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost)}",
        fileBasename = "anythingOntologyWithValueObjects",
        persistTtl = true,
      )

    private val anythingThingWithAllLanguages =
      HttpGetTest(
        urlPath =
          s"/v2/ontologies/classes/${urlEncodeIri(SharedOntologyTestDataADM.ANYTHING_THING_RESOURCE_CLASS_LocalHost)}?allLanguages=true",
        fileBasename = "anythingThingWithAllLanguages",
      )

    private val anythingOntologyThingSimple =
      HttpGetTest(
        urlPath =
          s"/v2/ontologies/classes/${urlEncodeIri(SharedOntologyTestDataADM.ANYTHING_THING_RESOURCE_CLASS_LocalHost_SIMPLE)}",
        fileBasename = "anythingThingSimple",
      )

    private val anythingOntologyThingComplex =
      HttpGetTest(
        urlPath =
          s"/v2/ontologies/classes/${urlEncodeIri(SharedOntologyTestDataADM.ANYTHING_THING_RESOURCE_CLASS_LocalHost)}",
        fileBasename = "anythingThing",
      )

    private val anythingOntologyHasListItemSimple =
      HttpGetTest(
        urlPath =
          s"/v2/ontologies/properties/${urlEncodeIri(SharedOntologyTestDataADM.ANYTHING_HasListItem_PROPERTY_LocalHost_SIMPLE)}",
        fileBasename = "anythingHasListItemSimple",
      )

    private val anythingOntologyHasListItemComplex =
      HttpGetTest(
        urlPath =
          s"/v2/ontologies/properties/${urlEncodeIri(SharedOntologyTestDataADM.ANYTHING_HasListItem_PROPERTY_LocalHost)}",
        fileBasename = "anythingHasListItem",
      )

    val testCases = Seq(
      // built-in ontologies
      knoraApiOntologySimple,
      knoraApiOntologyComplex,
      salsahGuiOntology,
      standoffOntology,
      // class segments of built-in ontologies
      knoraApiDateSegmentSimple,
      knoraApiDateSegmentComplex,
      // property segments of built-in ontologies
      knoraApiHasColorSegmentSimple,
      knoraApiHasColorSegmentComplex,
      // project ontologies
      anythingOntologyMetadata,
      anythingOntologySimple,
      anythingOntologyComplex,
      anythingThingWithAllLanguages,
      // class segments of project ontologies
      anythingOntologyThingSimple,
      anythingOntologyThingComplex,
      // property segments of project ontologies
      anythingOntologyHasListItemSimple,
      anythingOntologyHasListItemComplex,
    )
  }

  "The Ontologies v2 Endpoint" should {
    "serve the ontologies in JSON-LD, turtle and RDF-XML" in {
      forEvery(TestCases.testCases) { testCase =>
        checkTestCase(testCase)
      }
    }

    "serve the knora-api ontology in the simple schema on two separate endpoints" in {
      val ontologyAllEntitiesResponseJson = getResponse(
        s"/v2/ontologies/allentities/${urlEncodeIri(KnoraApiV2Simple.KnoraApiOntologyIri)}",
        RdfMediaTypes.`application/ld+json`,
      )
      val knoraApiResponseJson = getResponse(s"/ontology/knora-api/simple/v2", RdfMediaTypes.`application/ld+json`)
      assert(JsonParser(ontologyAllEntitiesResponseJson) == JsonParser(knoraApiResponseJson))

      val ontologyAllEntitiesResponseTurtle = getResponse(
        s"/v2/ontologies/allentities/${urlEncodeIri(KnoraApiV2Simple.KnoraApiOntologyIri)}",
        RdfMediaTypes.`text/turtle`,
      )
      val knoraApiResponseTurtle = getResponse(s"/ontology/knora-api/simple/v2", RdfMediaTypes.`text/turtle`)
      assert(parseTurtle(ontologyAllEntitiesResponseTurtle) == parseTurtle(knoraApiResponseTurtle))

      val ontologyAllEntitiesResponseRdfXml = getResponse(
        s"/v2/ontologies/allentities/${urlEncodeIri(KnoraApiV2Simple.KnoraApiOntologyIri)}",
        RdfMediaTypes.`application/rdf+xml`,
      )
      val knoraApiResponseRdfXml = getResponse(s"/ontology/knora-api/simple/v2", RdfMediaTypes.`application/rdf+xml`)
      assert(parseRdfXml(ontologyAllEntitiesResponseRdfXml) == parseRdfXml(knoraApiResponseRdfXml))
    }

    "serve the knora-api in the complex schema on two separate endpoints" in {
      val ontologyAllEntitiesResponseJson = getResponse(
        s"/v2/ontologies/allentities/${urlEncodeIri(KnoraApiV2Complex.KnoraApiOntologyIri)}",
        RdfMediaTypes.`application/ld+json`,
      )
      val knoraApiResponseJson = getResponse(s"/ontology/knora-api/v2", RdfMediaTypes.`application/ld+json`)
      assert(JsonParser(ontologyAllEntitiesResponseJson) == JsonParser(knoraApiResponseJson))

      val ontologyAllEntitiesResponseTurtle = getResponse(
        s"/v2/ontologies/allentities/${urlEncodeIri(KnoraApiV2Complex.KnoraApiOntologyIri)}",
        RdfMediaTypes.`text/turtle`,
      )
      val knoraApiResponseTurtle = getResponse(s"/ontology/knora-api/v2", RdfMediaTypes.`text/turtle`)
      assert(parseTurtle(ontologyAllEntitiesResponseTurtle) == parseTurtle(knoraApiResponseTurtle))

      val ontologyAllEntitiesResponseRdfXml = getResponse(
        s"/v2/ontologies/allentities/${urlEncodeIri(KnoraApiV2Complex.KnoraApiOntologyIri)}",
        RdfMediaTypes.`application/rdf+xml`,
      )
      val knoraApiResponseRdfXml = getResponse(s"/ontology/knora-api/v2", RdfMediaTypes.`application/rdf+xml`)
      assert(parseRdfXml(ontologyAllEntitiesResponseRdfXml) == parseRdfXml(knoraApiResponseRdfXml))
    }
  }
}
