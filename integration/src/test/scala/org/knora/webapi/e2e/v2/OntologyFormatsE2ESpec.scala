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
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.*

import pekko.http.scaladsl.model.*
import pekko.http.scaladsl.model.headers.Accept
import org.knora.webapi.messages.OntologyConstants.KnoraApiV2Simple

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
    maybeClientTestDataBasename: Option[String] = None,
    disableWrite: Boolean = false,
  ) {
    private def makeFile(suffix: String): Path =
      Paths.get("..", "test_data", "generated_test_data", "ontologyR2RV2", s"$fileBasename.$suffix")

    /**
     * Writes the expected response file.
     *
     * @param responseStr the contents of the file to be written.
     * @param mediaType   the media type of the response.
     */
    def writeFile(responseStr: String, mediaType: MediaType.NonBinary): Unit =
      if (!disableWrite) {
        val newOutputFile = makeFile(mediaType.fileExtensions.head)

        Files.createDirectories(newOutputFile.getParent)
        FileUtil.writeTextFile(newOutputFile, responseStr)
        ()
      }

    /**
     * If `maybeClientTestDataBasename` is defined, stores the response string in [[org.knora.webapi.e2e.ClientTestDataCollector]].
     */
    def storeClientTestData(responseStr: String): Unit =
      maybeClientTestDataBasename match {
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

  // URL-encoded IRIs for use as URL segments in HTTP GET tests.
  // private val incunabulaProjectSegment = URLEncoder.encode(SharedTestDataADM.incunabulaProjectIri, "UTF-8")
  // private val beolProjectSegment       = URLEncoder.encode(SharedTestDataADM.beolProjectIri, "UTF-8")
  //
  //
  // private val knoraApiWithValueObjectsOntologySegment =
  //   URLEncoder.encode(KnoraApiV2Complex.KnoraApiOntologyIri, "UTF-8")
  // private val incunabulaOntologySimpleSegment =
  //   URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2", "UTF-8")
  // private val incunabulaOntologyWithValueObjectsSegment =
  //   URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2", "UTF-8")
  // private val knoraApiDateSegment = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2#Date", "UTF-8")
  // private val knoraApiDateValueSegment =
  //   URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2#DateValue", "UTF-8")
  // private val knoraApiSimpleHasColorSegment =
  //   URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2#hasColor", "UTF-8")
  // private val knoraApiWithValueObjectsHasColorSegment =
  //   URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2#hasColor", "UTF-8")
  // private val incunabulaSimplePubdateSegment =
  //   URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate", "UTF-8")
  // private val incunabulaWithValueObjectsPubDateSegment =
  //   URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#pubdate", "UTF-8")
  // private val incunabulaWithValueObjectsPageSegment =
  //   URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#page", "UTF-8")
  // private val incunabulaWithValueObjectsBookSegment =
  //   URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book", "UTF-8")
  // private val boxOntologyWithValueObjectsSegment =
  //   URLEncoder.encode("http://api.knora.org/ontology/shared/example-box/v2", "UTF-8")
  // private val minimalOntologyWithValueObjects =
  //   URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/minimal/v2", "UTF-8")
  // private val anythingOntologyWithValueObjects =
  //   URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2", "UTF-8")
  // private val anythingThingWithAllLanguages =
  //   URLEncoder.encode(SharedOntologyTestDataADM.ANYTHING_THING_RESOURCE_CLASS_LocalHost, "UTF-8")
  // private val imagesBild = URLEncoder.encode(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS_LocalHost, "UTF-8")
  // private val incunabulaBook =
  //   URLEncoder.encode(SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS_LocalHost, "UTF-8")
  // private val incunabulaPage =
  //   URLEncoder.encode(SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS_LocalHost, "UTF-8")
  // private val anythingHasListItem =
  //   URLEncoder.encode(SharedOntologyTestDataADM.ANYTHING_HasListItem_PROPERTY_LocalHost, "UTF-8")
  // private val anythingHasDate =
  //   URLEncoder.encode(SharedOntologyTestDataADM.ANYTHING_HasDate_PROPERTY_LocalHost, "UTF-8")
  // private val imagesTitel = URLEncoder.encode(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY_LocalHost, "UTF-8")
  // private val incunabulaPartOf =
  //   URLEncoder.encode(SharedOntologyTestDataADM.INCUNABULA_PartOf_Property_LocalHost, "UTF-8")

  // The URLs and expected response files for each HTTP GET test.
  // private val httpGetTests = Seq(
  //
  //
  //
  //
  //   HttpGetTest(
  //     urlPath = "/ontology/knora-api/simple/v2",
  //     fileBasename = "knoraApiOntologySimple",
  //     disableWrite = true,
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/allentities/$knoraApiWithValueObjectsOntologySegment",
  //     fileBasename = "knoraApiOntologyWithValueObjects",
  //     maybeClientTestDataBasename = Some("knora-api-ontology"),
  //   ),
  //   HttpGetTest(
  //     urlPath = "/ontology/knora-api/v2",
  //     fileBasename = "knoraApiOntologyWithValueObjects",
  //     disableWrite = true,
  //   ),
  //   HttpGetTest(urlPath = "/ontology/salsah-gui/v2", fileBasename = "salsahGuiOntology"),
  //   HttpGetTest(urlPath = "/ontology/standoff/v2", fileBasename = "standoffOntologyWithValueObjects"),
  //   HttpGetTest(urlPath = s"/v2/ontologies/classes/$knoraApiDateSegment", fileBasename = "knoraApiDate"),
  //   HttpGetTest(urlPath = s"/v2/ontologies/classes/$knoraApiDateValueSegment", fileBasename = "knoraApiDateValue"),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/properties/$knoraApiSimpleHasColorSegment",
  //     fileBasename = "knoraApiSimpleHasColor",
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/properties/$knoraApiWithValueObjectsHasColorSegment",
  //     fileBasename = "knoraApiWithValueObjectsHasColor",
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/properties/$incunabulaSimplePubdateSegment",
  //     fileBasename = "incunabulaSimplePubDate",
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/properties/$incunabulaWithValueObjectsPubDateSegment",
  //     fileBasename = "incunabulaWithValueObjectsPubDate",
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/classes/$incunabulaWithValueObjectsPageSegment/$incunabulaWithValueObjectsBookSegment",
  //     fileBasename = "incunabulaPageAndBookWithValueObjects",
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/allentities/$boxOntologyWithValueObjectsSegment",
  //     fileBasename = "boxOntologyWithValueObjects",
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/allentities/$minimalOntologyWithValueObjects",
  //     fileBasename = "minimalOntologyWithValueObjects",
  //     maybeClientTestDataBasename = Some("minimal-ontology"),
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/allentities/$anythingOntologyWithValueObjects",
  //     fileBasename = "anythingOntologyWithValueObjects",
  //     maybeClientTestDataBasename = Some("anything-ontology"),
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/classes/$anythingThingWithAllLanguages?allLanguages=true",
  //     fileBasename = "anythingThingWithAllLanguages",
  //     maybeClientTestDataBasename = Some("get-class-anything-thing-with-allLanguages-response"),
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/classes/$imagesBild",
  //     fileBasename = "imagesBild",
  //     maybeClientTestDataBasename = Some("get-class-image-bild-response"),
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/classes/$incunabulaBook",
  //     fileBasename = "incunabulaBook",
  //     maybeClientTestDataBasename = Some("get-class-incunabula-book-response"),
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/classes/$incunabulaPage",
  //     fileBasename = "incunabulaPage",
  //     maybeClientTestDataBasename = Some("get-class-incunabula-page-response"),
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/properties/$anythingHasListItem",
  //     fileBasename = "anythingHasListItem",
  //     maybeClientTestDataBasename = Some("get-property-listValue-response"),
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/properties/$anythingHasDate",
  //     fileBasename = "anythingHasDate",
  //     maybeClientTestDataBasename = Some("get-property-DateValue-response"),
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/properties/$imagesTitel",
  //     fileBasename = "imagesTitel",
  //     maybeClientTestDataBasename = Some("get-property-textValue-response"),
  //   ),
  //   HttpGetTest(
  //     urlPath = s"/v2/ontologies/properties/$incunabulaPartOf",
  //     fileBasename = "incunabulaPartOf",
  //     maybeClientTestDataBasename = Some("get-property-linkvalue-response"),
  //   ),
  // )

  // TODO: remove before merging!
  // The following test cases have been removed:
  //
  // HttpGetTest(
  //   urlPath = "/v2/ontologies/metadata",
  //   fileBasename = "allOntologyMetadata",
  //   maybeClientTestDataBasename = Some("all-ontology-metadata-response"),
  // ),
  // HttpGetTest(
  //   urlPath = s"/v2/ontologies/metadata/$incunabulaProjectSegment",
  //   fileBasename = "incunabulaOntologyMetadata",
  //   maybeClientTestDataBasename = Some("get-ontologies-project-incunabula-response"),
  // ),
  // HttpGetTest(
  //   urlPath = s"/v2/ontologies/metadata/$beolProjectSegment",
  //   fileBasename = "beolOntologyMetadata",
  //   maybeClientTestDataBasename = Some("get-ontologies-project-beol-response"),
  // ),
  // HttpGetTest(
  //   urlPath = s"/v2/ontologies/allentities/$incunabulaOntologySimpleSegment",
  //   fileBasename = "incunabulaOntologySimple",
  // ),
  // HttpGetTest(
  //   urlPath = s"/v2/ontologies/allentities/$incunabulaOntologyWithValueObjectsSegment",
  //   fileBasename = "incunabulaOntologyWithValueObjects",
  //   maybeClientTestDataBasename = Some("incunabula-ontology"),
  // ),

  private def urlEncodeIri(iri: IRI): String =
    URLEncoder.encode(iri, "UTF-8")

  private def checkJsonLdTestCase(httpGetTest: HttpGetTest) = {
    val mediaType   = RdfMediaTypes.`application/ld+json`
    val responseStr = getResponse(httpGetTest, mediaType)
    if (writeTestDataFiles) httpGetTest.writeFile(responseStr, mediaType)
    else assert(JsonParser(responseStr) == JsonParser(httpGetTest.readFile(mediaType)))
    httpGetTest.storeClientTestData(responseStr)
  }

  private def checkTurleTestCase(httpGetTest: HttpGetTest) = {
    val mediaType   = RdfMediaTypes.`text/turtle`
    val responseStr = getResponse(httpGetTest, mediaType)
    if (writeTestDataFiles) httpGetTest.writeFile(responseStr, mediaType)
    else assert(parseTurtle(responseStr) == parseTurtle(httpGetTest.readFile(mediaType)))
  }

  private def checkRdfXmlTestCase(httpGetTest: HttpGetTest) = {
    val mediaType   = RdfMediaTypes.`application/rdf+xml`
    val responseStr = getResponse(httpGetTest, mediaType)
    if (writeTestDataFiles) {
      val parsedResponse: RdfModel     = parseRdfXml(responseStr)
      val parsedExistingFile: RdfModel = parseRdfXml(httpGetTest.readFile(mediaType))
      if (parsedResponse != parsedExistingFile) httpGetTest.writeFile(responseStr, mediaType)
    } else assert(parseRdfXml(responseStr) == parseRdfXml(httpGetTest.readFile(mediaType)))
  }

  private def getResponse(
    httpGetTest: HttpGetTest,
    mediaType: MediaType.NonBinary,
  ) = {
    val request     = Get(s"$baseApiUrl${httpGetTest.urlPath}").addHeader(Accept(mediaType))
    val response    = singleAwaitingRequest(request)
    val responseStr = responseToString(response)
    assert(response.status == StatusCodes.OK, responseStr)
    responseStr
  }

  private object TestCases {
    private val anythingOntologyMetadata: HttpGetTest = HttpGetTest(
      urlPath = s"/v2/ontologies/metadata/${urlEncodeIri(SharedTestDataADM.anythingProjectIri)}",
      fileBasename = "anythingOntologyMetadata",
      maybeClientTestDataBasename = Some("get-ontologies-project-anything-response"),
    )

    private val knoraApiOntologySimple = HttpGetTest(
      urlPath = s"/v2/ontologies/allentities/${urlEncodeIri(KnoraApiV2Simple.KnoraApiOntologyIri)}",
      fileBasename = "knoraApiOntologySimple",
    )

    val testCases = Seq(
      anythingOntologyMetadata,
      knoraApiOntologySimple,
    )
  }

  // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  // If true, the existing expected response files are overwritten with the HTTP GET responses from the server.
  // If false, the responses from the server are compared to the contents fo the expected response files.
  // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  private val writeTestDataFiles = false

  "The Ontologies v2 Endpoint" should {
    "serve the ontology in JSON-LD, turtle and RDF-XML" in {
      forEvery(TestCases.testCases) { testCase =>
        checkJsonLdTestCase(testCase)
        checkTurleTestCase(testCase)
        checkRdfXmlTestCase(testCase)
      }
    }
    "serve the knora-api ontology on two separate endpoints" in {
      // TODO: implement
    }
  }
}
