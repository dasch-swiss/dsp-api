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
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.*

import pekko.http.scaladsl.model.*
import pekko.http.scaladsl.model.headers.Accept
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.messages.util.rdf.JsonLD

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
    disableWrite: Boolean = false, // TODO: remove this parameter
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
  //
  // The following were redundant because they were each two routs to the same ontology, checked against the same file:
  //
  //   HttpGetTest(
  //     urlPath = "/ontology/knora-api/v2",
  //     fileBasename = "knoraApiOntologyWithValueObjects",
  //     disableWrite = true,
  //   ),
  // -> same as `s"/v2/ontologies/allentities/${urlEncodeIri(KnoraApiV2Complex.KnoraApiOntologyIri)}"`
  //   HttpGetTest(
  //     urlPath = "/ontology/knora-api/simple/v2",
  //     fileBasename = "knoraApiOntologySimple",
  //     disableWrite = true,
  //   ),
  // -> same as `s"/v2/ontologies/allentities/${urlEncodeIri(KnoraApiV2Simple.KnoraApiOntologyIri)}"`

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
        s"""|The response did not equal the approved data.
            |
            |Response:
            |
            |${responseStr}
            |
            |----------------------------
            |
            |Approved data:
            |
            |${httpGetTest.readFile(mediaType)}
            |""".stripMargin,
      )
    }
    // if (writeTestDataFiles) httpGetTest.writeFile(responseStr, mediaType)
    // else assert(JsonParser(responseStr) == JsonParser(httpGetTest.readFile(mediaType)))
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
        s"""|The response did not equal the approved data.
            |
            |Response:
            |
            |${responseStr}
            |
            |----------------------------
            |
            |Approved data:
            |
            |${httpGetTest.readFile(mediaType)}
            |""".stripMargin,
      )
    }
  }
  // LATER: use jena directly, with `isIsomorphicWith` (note that we only have one graph here)
  // LATER: in failure case, write the response to a file for approval

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
        maybeClientTestDataBasename = Some("knora-api-ontology"),
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
        maybeClientTestDataBasename = Some("get-ontologies-project-anything-response"),
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
        maybeClientTestDataBasename = Some("anything-ontology"),
      )

    private val anythingThingWithAllLanguages =
      HttpGetTest(
        urlPath =
          s"/v2/ontologies/classes/${urlEncodeIri(SharedOntologyTestDataADM.ANYTHING_THING_RESOURCE_CLASS_LocalHost)}?allLanguages=true",
        fileBasename = "anythingThingWithAllLanguages",
        maybeClientTestDataBasename = Some("get-class-anything-thing-with-allLanguages-response"),
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
      // TODO: class segments of project ontologies
      // TODO: property segments of project ontologies
    )
  }

  // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  // If true, the existing expected response files are overwritten with the HTTP GET responses from the server.
  // If false, the responses from the server are compared to the contents fo the expected response files.
  // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  private val writeTestDataFiles = true

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
