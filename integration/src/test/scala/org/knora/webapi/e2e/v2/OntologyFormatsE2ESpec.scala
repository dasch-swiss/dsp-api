/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.knora.webapi.e2e.v2

import org.apache.pekko
import org.scalatest.Inspectors.forEvery
import spray.json.*

import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.knora.webapi.*
import org.knora.webapi.e2e.ClientTestDataCollector
import org.knora.webapi.e2e.TestDataFileContent
import org.knora.webapi.e2e.TestDataFilePath
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Complex
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Simple
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.*

import pekko.http.scaladsl.model.*
import pekko.http.scaladsl.model.headers.Accept

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
   * @param maybeClientTestDataBasename the basename of the client test data file, if any, to be collected by
   *                                    [[org.knora.webapi.e2e.ClientTestDataCollector]].
   * @param disableWrite                if true, this [[HttpGetTest]] will not write the expected response file when `writeFile` is called.
   *                                    This is useful if two tests share the same file.
   */
  private case class HttpGetTest(
    urlPath: String,
    fileBasename: String,
    clientTestDataBasename: Option[String] = None,
  ) {
    private def makeFile(suffix: String): Path =
      Paths.get("..", "test_data", "generated_test_data", "ontologyR2RV2", s"$fileBasename.$suffix")

    /**
     * Writes the expected response file.
     *
     * @param responseStr the contents of the file to be written.
     * @param mediaType   the media type of the response.
     */
    def writeFile(responseStr: String, mediaType: MediaType.NonBinary): Unit = {
      val newOutputFile = makeFile(mediaType.fileExtensions.head)

      Files.createDirectories(newOutputFile.getParent)
      FileUtil.writeTextFile(newOutputFile, responseStr)
      ()
    }

    /**
     * If `maybeClientTestDataBasename` is defined, stores the response string in [[org.knora.webapi.e2e.ClientTestDataCollector]].
     */
    def storeClientTestData(responseStr: String): Unit =
      clientTestDataBasename match {
        case Some(clientTestDataBasename) => CollectClientTestData(clientTestDataBasename, responseStr)
        case None                         => ()
      }

    /**
     * Reads the expected response file.
     *
     * @param mediaType the media type of the response.
     * @return the contents of the file.
     */
    def readFile(mediaType: MediaType.NonBinary): String =
      FileUtil.readTextFile(makeFile(mediaType.fileExtensions.head))

    def fileExists(mediaType: MediaType.NonBinary): Boolean =
      Files.exists(makeFile(mediaType.fileExtensions.head))

    def writeReceived(responseStr: String, mediaType: MediaType.NonBinary): Unit = {
      val newOutputFile = makeFile(s"received.${mediaType.fileExtensions.head}")

      Files.createDirectories(newOutputFile.getParent)
      FileUtil.writeTextFile(newOutputFile, responseStr)
      ()
    }
  }

  private val clientTestDataPath: Seq[String] = Seq("v2", "ontologies")
  private val clientTestDataCollector         = new ClientTestDataCollector(appConfig)

  private def CollectClientTestData(fileName: String, fileContent: String): Unit =
    clientTestDataCollector.addFile(
      TestDataFileContent(
        filePath = TestDataFilePath(
          directoryPath = clientTestDataPath,
          filename = fileName,
          fileExtension = "json",
        ),
        text = fileContent,
      ),
    )

  private def urlEncodeIri(iri: IRI): String =
    URLEncoder.encode(iri, "UTF-8")

  private def checkJsonLdTestCase(httpGetTest: HttpGetTest) = {
    val mediaType   = RdfMediaTypes.`application/ld+json`
    val responseStr = getResponse(httpGetTest.urlPath, mediaType)
    if (!httpGetTest.fileExists(mediaType)) {
      if (writeTestDataFiles) httpGetTest.writeReceived(responseStr, mediaType)
      throw new AssertionError(s"No approved data available in file ${httpGetTest.fileBasename}")
    }
    if (JsonParser(responseStr) != JsonParser(httpGetTest.readFile(mediaType))) {
      if (writeTestDataFiles) httpGetTest.writeReceived(responseStr, mediaType)
      throw new AssertionError(
        s"""|
            |The response did not equal the approved data.
            |
            |Response:
            |
            |$responseStr
            |
            |
            |${"=" * 120}
            |
            |
            |Approved data:
            |
            |${httpGetTest.readFile(mediaType)}
            |
            |""".stripMargin,
      )
    }
    httpGetTest.storeClientTestData(responseStr)
  }

  private def checkTurleTestCase(httpGetTest: HttpGetTest) = {
    val mediaType   = RdfMediaTypes.`text/turtle`
    val responseStr = getResponse(httpGetTest.urlPath, mediaType)
    if (!httpGetTest.fileExists(mediaType)) {
      if (writeTestDataFiles) httpGetTest.writeReceived(responseStr, mediaType)
      throw new AssertionError(s"No approved data available in file ${httpGetTest.fileBasename}")
    }
    if (parseTurtle(responseStr) != parseTurtle(httpGetTest.readFile(mediaType))) {
      if (writeTestDataFiles) httpGetTest.writeReceived(responseStr, mediaType)
      throw new AssertionError(
        s"""|
            |The response did not equal the approved data.
            |
            |Response:
            |
            |$responseStr
            |
            |
            |${"=" * 120}
            |
            |
            |Approved data:
            |
            |${httpGetTest.readFile(mediaType)}
            |
            |""".stripMargin,
      )
    }
  }

  private def checkRdfXmlTestCase(httpGetTest: HttpGetTest) = {
    val mediaType   = RdfMediaTypes.`application/rdf+xml`
    val responseStr = getResponse(httpGetTest.urlPath, mediaType)
    // RDF XML can be compared agains the persisted turtle file, so does not need to br written to a file.
    if (writeTestDataFiles) ()
    else assert(parseRdfXml(responseStr) == parseTurtle(httpGetTest.readFile(RdfMediaTypes.`text/turtle`)))
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
        clientTestDataBasename = Some("knora-api-ontology"),
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
        clientTestDataBasename = Some("get-ontologies-project-anything-response"),
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
        clientTestDataBasename = Some("anything-ontology"),
      )

    private val anythingThingWithAllLanguages =
      HttpGetTest(
        urlPath =
          s"/v2/ontologies/classes/${urlEncodeIri(SharedOntologyTestDataADM.ANYTHING_THING_RESOURCE_CLASS_LocalHost)}?allLanguages=true",
        fileBasename = "anythingThingWithAllLanguages",
        clientTestDataBasename = Some("get-class-anything-thing-with-allLanguages-response"),
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
        clientTestDataBasename = Some("get-property-listValue-response"),
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

  // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  // If true, the existing expected response files are overwritten with the HTTP GET responses from the server.
  // If false, the responses from the server are compared to the contents fo the expected response files.
  // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  private val writeTestDataFiles = false

  "The Ontologies v2 Endpoint" should {
    "serve the ontologies in JSON-LD, turtle and RDF-XML" in {
      forEvery(TestCases.testCases) { testCase =>
        checkTurleTestCase(testCase)
        checkJsonLdTestCase(testCase)
        checkRdfXmlTestCase(testCase)
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
