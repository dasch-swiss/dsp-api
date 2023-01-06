/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout
import spray.json._

import java.net.URLEncoder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Instant
import scala.concurrent.ExecutionContextExecutor

import dsp.constants.SalsahGui
import dsp.errors.AssertionException
import dsp.schema.domain.Cardinality._
import dsp.valueobjects.LangString
import dsp.valueobjects.LanguageCode
import org.knora.webapi._
import org.knora.webapi.e2e.ClientTestDataCollector
import org.knora.webapi.e2e.TestDataFileContent
import org.knora.webapi.e2e.TestDataFilePath
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.v2.responder.ontologymessages.InputOntologyV2
import org.knora.webapi.messages.v2.responder.ontologymessages.TestResponseParsingModeV2
import org.knora.webapi.models._
import org.knora.webapi.routing.v2.OntologiesRouteV2
import org.knora.webapi.routing.v2.ResourcesRouteV2
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util._

object OntologyV2R2RSpec {
  private val anythingUserProfile = SharedTestDataADM.anythingAdminUser
  private val anythingUsername    = anythingUserProfile.email

  private val superUserProfile = SharedTestDataADM.superUser
  private val superUsername    = superUserProfile.email

  private val password = SharedTestDataADM.testPass
}

/**
 * End-to-end test specification for API v2 ontology routes.
 */
class OntologyV2R2RSpec extends R2RSpec {

  import OntologyV2R2RSpec._

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private val ontologiesPath =
    DSPApiDirectives.handleErrors(system, appConfig)(new OntologiesRouteV2(routeData, null).makeRoute)
  private val resourcesPath =
    DSPApiDirectives.handleErrors(system, appConfig)(new ResourcesRouteV2(routeData, null).makeRoute)

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(
    appConfig.defaultTimeoutAsDuration
  )

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  // If true, the existing expected response files are overwritten with the HTTP GET responses from the server.
  // If false, the responses from the server are compared to the contents fo the expected response files.
  // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  private val writeTestDataFiles = false

  override lazy val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/ontologies/example-box.ttl",
      name = "http://www.knora.org/ontology/shared/example-box"
    ),
    RdfDataObject(path = "test_data/ontologies/minimal-onto.ttl", name = "http://www.knora.org/ontology/0001/minimal"),
    RdfDataObject(
      path = "test_data/ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest"
    ),
    RdfDataObject(path = "test_data/all_data/freetest-data.ttl", name = "http://www.knora.org/data/0001/freetest"),
    RdfDataObject(
      path = "test_data/ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything"
    ),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("v2", "ontologies")

  // an instance of ClientTestDataCollector
  private val clientTestDataCollector = new ClientTestDataCollector(appConfig)

  // Collects client test data
  // TODO: redefine below method somewhere else if can be reused over other test files
  private def CollectClientTestData(fileName: String, fileContent: String): Unit =
    clientTestDataCollector.addFile(
      TestDataFileContent(
        filePath = TestDataFilePath(
          directoryPath = clientTestDataPath,
          filename = fileName,
          fileExtension = "json"
        ),
        text = fileContent
      )
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
    disableWrite: Boolean = false
  ) {
    def makeFile(mediaType: MediaType.NonBinary): Path = {
      val fileSuffix = mediaType.fileExtensions.head
      Paths.get("..", "test_data", "ontologyR2RV2", s"$fileBasename.$fileSuffix")
    }

    /**
     * Writes the expected response file.
     *
     * @param responseStr the contents of the file to be written.
     * @param mediaType   the media type of the response.
     */
    def writeFile(responseStr: String, mediaType: MediaType.NonBinary): Unit =
      if (!disableWrite) {
        val fileSuffix    = mediaType.fileExtensions.head
        val newOutputFile = Paths.get("..", "test_data", "ontologyR2RV2", s"$fileBasename.$fileSuffix")

        Files.createDirectories(newOutputFile.getParent)
        FileUtil.writeTextFile(newOutputFile, responseStr)
      }

    /**
     * If `maybeClientTestDataBasename` is defined, stores the response string in [[org.knora.webapi.e2e.ClientTestDataCollector]].
     */
    def storeClientTestData(responseStr: String): Unit =
      maybeClientTestDataBasename match {
        case Some(clientTestDataBasename) =>
          CollectClientTestData(clientTestDataBasename, responseStr)

        case None => ()
      }

    /**
     * Reads the expected response file.
     *
     * @param mediaType the media type of the response.
     * @return the contents of the file.
     */
    def readFile(mediaType: MediaType.NonBinary): String =
      FileUtil.readTextFile(makeFile(mediaType))
  }

  // URL-encoded IRIs for use as URL segments in HTTP GET tests.
  private val anythingProjectSegment   = URLEncoder.encode(SharedTestDataADM.anythingProjectIri, "UTF-8")
  private val incunabulaProjectSegment = URLEncoder.encode(SharedTestDataADM.incunabulaProjectIri, "UTF-8")
  private val beolProjectSegment       = URLEncoder.encode(SharedTestDataADM.beolProjectIri, "UTF-8")
  private val knoraApiSimpleOntologySegment =
    URLEncoder.encode(OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri, "UTF-8")
  private val knoraApiWithValueObjectsOntologySegment =
    URLEncoder.encode(OntologyConstants.KnoraApiV2Complex.KnoraApiOntologyIri, "UTF-8")
  private val incunabulaOntologySimpleSegment =
    URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2", "UTF-8")
  private val incunabulaOntologyWithValueObjectsSegment =
    URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2", "UTF-8")
  private val knoraApiDateSegment = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2#Date", "UTF-8")
  private val knoraApiDateValueSegment =
    URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2#DateValue", "UTF-8")
  private val knoraApiSimpleHasColorSegment =
    URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2#hasColor", "UTF-8")
  private val knoraApiWithValueObjectsHasColorSegment =
    URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2#hasColor", "UTF-8")
  private val incunabulaSimplePubdateSegment =
    URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate", "UTF-8")
  private val incunabulaWithValueObjectsPubDateSegment =
    URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#pubdate", "UTF-8")
  private val incunabulaWithValueObjectsPageSegment =
    URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#page", "UTF-8")
  private val incunabulaWithValueObjectsBookSegment =
    URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book", "UTF-8")
  private val boxOntologyWithValueObjectsSegment =
    URLEncoder.encode("http://api.knora.org/ontology/shared/example-box/v2", "UTF-8")
  private val minimalOntologyWithValueObjects =
    URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/minimal/v2", "UTF-8")
  private val anythingOntologyWithValueObjects =
    URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2", "UTF-8")
  private val anythingThingWithAllLanguages =
    URLEncoder.encode(SharedOntologyTestDataADM.ANYTHING_THING_RESOURCE_CLASS_LocalHost, "UTF-8")
  private val imagesBild = URLEncoder.encode(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS_LocalHost, "UTF-8")
  private val incunabulaBook =
    URLEncoder.encode(SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS_LocalHost, "UTF-8")
  private val incunabulaPage =
    URLEncoder.encode(SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS_LocalHost, "UTF-8")
  private val anythingHasListItem =
    URLEncoder.encode(SharedOntologyTestDataADM.ANYTHING_HasListItem_PROPERTY_LocalHost, "UTF-8")
  private val anythingHasDate =
    URLEncoder.encode(SharedOntologyTestDataADM.ANYTHING_HasDate_PROPERTY_LocalHost, "UTF-8")
  private val imagesTitel = URLEncoder.encode(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY_LocalHost, "UTF-8")
  private val incunabulaPartOf =
    URLEncoder.encode(SharedOntologyTestDataADM.INCUNABULA_PartOf_Property_LocalHost, "UTF-8")

  private val anythingOntoLocalhostIri = SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost
  private val hasOtherNothingIri       = anythingOntoLocalhostIri + "#hasOtherNothing"
  private val hasOtherNothingValueIri  = anythingOntoLocalhostIri + "#hasOtherNothingValue"

  // The URLs and expected response files for each HTTP GET test.
  private val httpGetTests = Seq(
    HttpGetTest(
      urlPath = "/v2/ontologies/metadata",
      fileBasename = "allOntologyMetadata",
      maybeClientTestDataBasename = Some("all-ontology-metadata-response")
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/metadata/$anythingProjectSegment",
      fileBasename = "anythingOntologyMetadata",
      maybeClientTestDataBasename = Some("get-ontologies-project-anything-response")
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/metadata/$incunabulaProjectSegment",
      fileBasename = "incunabulaOntologyMetadata",
      maybeClientTestDataBasename = Some("get-ontologies-project-incunabula-response")
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/metadata/$beolProjectSegment",
      fileBasename = "beolOntologyMetadata",
      maybeClientTestDataBasename = Some("get-ontologies-project-beol-response")
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/allentities/$knoraApiSimpleOntologySegment",
      fileBasename = "knoraApiOntologySimple"
    ),
    HttpGetTest(
      urlPath = "/ontology/knora-api/simple/v2",
      fileBasename = "knoraApiOntologySimple",
      disableWrite = true
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/allentities/$knoraApiWithValueObjectsOntologySegment",
      fileBasename = "knoraApiOntologyWithValueObjects",
      maybeClientTestDataBasename = Some("knora-api-ontology")
    ),
    HttpGetTest(
      urlPath = "/ontology/knora-api/v2",
      fileBasename = "knoraApiOntologyWithValueObjects",
      disableWrite = true
    ),
    HttpGetTest(urlPath = "/ontology/salsah-gui/v2", fileBasename = "salsahGuiOntology"),
    HttpGetTest(urlPath = "/ontology/standoff/v2", fileBasename = "standoffOntologyWithValueObjects"),
    HttpGetTest(
      urlPath = s"/v2/ontologies/allentities/$incunabulaOntologySimpleSegment",
      fileBasename = "incunabulaOntologySimple"
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/allentities/$incunabulaOntologyWithValueObjectsSegment",
      fileBasename = "incunabulaOntologyWithValueObjects",
      maybeClientTestDataBasename = Some("incunabula-ontology")
    ),
    HttpGetTest(urlPath = s"/v2/ontologies/classes/$knoraApiDateSegment", fileBasename = "knoraApiDate"),
    HttpGetTest(urlPath = s"/v2/ontologies/classes/$knoraApiDateValueSegment", fileBasename = "knoraApiDateValue"),
    HttpGetTest(
      urlPath = s"/v2/ontologies/properties/$knoraApiSimpleHasColorSegment",
      fileBasename = "knoraApiSimpleHasColor"
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/properties/$knoraApiWithValueObjectsHasColorSegment",
      fileBasename = "knoraApiWithValueObjectsHasColor"
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/properties/$incunabulaSimplePubdateSegment",
      fileBasename = "incunabulaSimplePubDate"
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/properties/$incunabulaWithValueObjectsPubDateSegment",
      fileBasename = "incunabulaWithValueObjectsPubDate"
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/classes/$incunabulaWithValueObjectsPageSegment/$incunabulaWithValueObjectsBookSegment",
      fileBasename = "incunabulaPageAndBookWithValueObjects"
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/allentities/$boxOntologyWithValueObjectsSegment",
      fileBasename = "boxOntologyWithValueObjects"
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/allentities/$minimalOntologyWithValueObjects",
      fileBasename = "minimalOntologyWithValueObjects",
      maybeClientTestDataBasename = Some("minimal-ontology")
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/allentities/$anythingOntologyWithValueObjects",
      fileBasename = "anythingOntologyWithValueObjects",
      maybeClientTestDataBasename = Some("anything-ontology")
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/classes/$anythingThingWithAllLanguages?allLanguages=true",
      fileBasename = "anythingThingWithAllLanguages",
      maybeClientTestDataBasename = Some("get-class-anything-thing-with-allLanguages-response")
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/classes/$imagesBild",
      fileBasename = "imagesBild",
      maybeClientTestDataBasename = Some("get-class-image-bild-response")
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/classes/$incunabulaBook",
      fileBasename = "incunabulaBook",
      maybeClientTestDataBasename = Some("get-class-incunabula-book-response")
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/classes/$incunabulaPage",
      fileBasename = "incunabulaPage",
      maybeClientTestDataBasename = Some("get-class-incunabula-page-response")
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/properties/$anythingHasListItem",
      fileBasename = "anythingHasListItem",
      maybeClientTestDataBasename = Some("get-property-listValue-response")
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/properties/$anythingHasDate",
      fileBasename = "anythingHasDate",
      maybeClientTestDataBasename = Some("get-property-DateValue-response")
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/properties/$imagesTitel",
      fileBasename = "imagesTitel",
      maybeClientTestDataBasename = Some("get-property-textValue-response")
    ),
    HttpGetTest(
      urlPath = s"/v2/ontologies/properties/$incunabulaPartOf",
      fileBasename = "incunabulaPartOf",
      maybeClientTestDataBasename = Some("get-property-linkvalue-response")
    )
  )

  // The media types that will be used in HTTP Accept headers in HTTP GET tests.
  private val mediaTypesForGetTests = Seq(
    RdfMediaTypes.`application/ld+json`,
    RdfMediaTypes.`text/turtle`,
    RdfMediaTypes.`application/rdf+xml`
  )

  private val fooIri                  = new MutableTestIri
  private val barIri                  = new MutableTestIri
  private var fooLastModDate: Instant = Instant.now
  private var barLastModDate: Instant = Instant.now

  private var anythingLastModDate: Instant = Instant.parse("2017-12-19T15:23:42.166Z")
  private var freetestLastModDate: Instant = Instant.parse("2012-12-12T12:12:12.12Z")

  private val uselessIri                  = new MutableTestIri
  private var uselessLastModDate: Instant = Instant.now

  private def getPropertyIrisFromResourceClassResponse(responseJsonDoc: JsonLDDocument): Set[SmartIri] = {
    val classDef = responseJsonDoc.body.requireArray("@graph").value.head.asInstanceOf[JsonLDObject]

    classDef
      .value(OntologyConstants.Rdfs.SubClassOf)
      .asInstanceOf[JsonLDArray]
      .value
      .collect {
        case obj: JsonLDObject if !obj.isIri =>
          obj.requireIriInObject(OntologyConstants.Owl.OnProperty, stringFormatter.toSmartIriWithErr)
      }
      .toSet
  }

  "The Ontologies v2 Endpoint" should {

    "serve ontology data in different formats" in {
      // Iterate over the HTTP GET tests.
      for (httpGetTest <- httpGetTests) {

        // Do each test with each media type.
        for (mediaType <- mediaTypesForGetTests) {

          Get(httpGetTest.urlPath).addHeader(Accept(mediaType)) ~> ontologiesPath ~> check {
            val responseStr: String = responseAs[String]
            assert(response.status == StatusCodes.OK, responseStr)

            // Are we writing expected response files?
            if (writeTestDataFiles) {
              // Yes. But only write RDF/XML files if they're semantically different from the ones that we already
              // have, to avoid writing new files into Git that differ only in their blank node IDs.

              mediaType match {
                case RdfMediaTypes.`application/rdf+xml` =>
                  val existingFile: Path = httpGetTest.makeFile(mediaType)

                  if (Files.exists(existingFile)) {
                    val parsedResponse: RdfModel     = parseRdfXml(responseStr)
                    val parsedExistingFile: RdfModel = parseRdfXml(httpGetTest.readFile(mediaType))

                    if (parsedResponse != parsedExistingFile) {
                      httpGetTest.writeFile(responseStr, mediaType)
                    }
                  } else {
                    httpGetTest.writeFile(responseStr, mediaType)
                  }

                case _ =>
                  httpGetTest.writeFile(responseStr, mediaType)
              }
            } else {
              // No. Compare the received response with the expected response.
              mediaType match {
                case RdfMediaTypes.`application/ld+json` =>
                  assert(JsonParser(responseStr) == JsonParser(httpGetTest.readFile(mediaType)))

                case RdfMediaTypes.`text/turtle` =>
                  assert(parseTurtle(responseStr) == parseTurtle(httpGetTest.readFile(mediaType)))

                case RdfMediaTypes.`application/rdf+xml` =>
                  assert(parseRdfXml(responseStr) == parseRdfXml(httpGetTest.readFile(mediaType)))

                case _ => throw AssertionException(s"Unsupported media type for test: $mediaType")
              }
            }

            // If necessary, store the JSON-LD response as client test data.
            if (mediaType == RdfMediaTypes.`application/ld+json`) {
              httpGetTest.storeClientTestData(responseStr)
            }
          }
        }
      }
    }

    "not allow the user to request the knora-base ontology" in {
      Get(
        "/v2/ontologies/allentities/http%3A%2F%2Fapi.knora.org%2Fontology%2Fknora-base%2Fv2"
      ) ~> ontologiesPath ~> check {
        val responseStr: String = responseAs[String]
        assert(response.status == StatusCodes.BadRequest, responseStr)
      }
    }

    "not allow the user to request the knora-admin ontology" in {
      Get(
        "/v2/ontologies/allentities/http%3A%2F%2Fapi.knora.org%2Fontology%2Fknora-admin%2Fv2"
      ) ~> ontologiesPath ~> check {
        val responseStr: String = responseAs[String]
        assert(response.status == StatusCodes.BadRequest, responseStr)
      }
    }

    "create an empty ontology called 'foo' with a project code" in {
      val label = "The foo ontology"

      val params =
        s"""{
           |    "knora-api:ontologyName": "foo",
           |    "knora-api:attachedToProject": {
           |      "@id": "${SharedTestDataADM.anythingProjectIri}"
           |    },
           |    "rdfs:label": "$label",
           |    "@context": {
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
           |    }
           |}""".stripMargin

      CollectClientTestData("create-empty-foo-ontology-request", params)

      Post("/v2/ontologies", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        val metadata        = responseJsonDoc.body
        val ontologyIri     = metadata.value("@id").asInstanceOf[JsonLDString].value
        assert(ontologyIri == "http://0.0.0.0:3333/ontology/0001/foo/v2")
        fooIri.set(ontologyIri)
        assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString(label))

        val lastModDate = metadata.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        fooLastModDate = lastModDate

        CollectClientTestData("create-empty-foo-ontology-response", responseStr)
      }
    }

    "create an empty ontology called 'bar' with a comment" in {
      val label   = "The bar ontology"
      val comment = "some comment"

      val params =
        s"""{
           |    "knora-api:ontologyName": "bar",
           |    "knora-api:attachedToProject": {
           |      "@id": "${SharedTestDataADM.anythingProjectIri}"
           |    },
           |    "rdfs:label": "$label",
           |    "rdfs:comment": "$comment",
           |    "@context": {
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
           |    }
           |}""".stripMargin

      CollectClientTestData("create-ontology-with-comment-request", params)

      Post("/v2/ontologies", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        val metadata        = responseJsonDoc.body
        val ontologyIri     = metadata.value("@id").asInstanceOf[JsonLDString].value
        assert(ontologyIri == "http://0.0.0.0:3333/ontology/0001/bar/v2")
        assert(
          metadata.value(OntologyConstants.Rdfs.Comment) == JsonLDString(
            stringFormatter.fromSparqlEncodedString(comment)
          )
        )
        barIri.set(ontologyIri)
        val lastModDate = metadata.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )
        barLastModDate = lastModDate

        CollectClientTestData("create-ontology-with-comment-response", responseStr)
      }
    }

    "create an empty ontology called 'test' with a comment that has a special character" in {
      val label   = "The test ontology"
      val comment = "some \\\"test\\\" comment"

      val params =
        s"""{
           |    "knora-api:ontologyName": "test",
           |    "knora-api:attachedToProject": {
           |      "@id": "${SharedTestDataADM.anythingProjectIri}"
           |    },
           |    "rdfs:label": "$label",
           |    "rdfs:comment": "$comment",
           |    "@context": {
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
           |    }
           |}""".stripMargin

      Post("/v2/ontologies", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        val metadata        = responseJsonDoc.body
        val ontologyIri     = metadata.value("@id").asInstanceOf[JsonLDString].value
        assert(ontologyIri == "http://0.0.0.0:3333/ontology/0001/test/v2")
        assert(
          metadata.value(OntologyConstants.Rdfs.Comment) == JsonLDString(
            stringFormatter.fromSparqlEncodedString(comment)
          )
        )
      }
    }

    "change the metadata of 'foo'" in {
      val newLabel   = "The modified foo ontology"
      val newComment = "new comment"

      val params =
        s"""{
           |  "@id": "${fooIri.get}",
           |  "rdfs:label": "$newLabel",
           |  "rdfs:comment": "$newComment",
           |  "knora-api:lastModificationDate": {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$fooLastModDate"
           |  },
           |  "@context": {
           |    "xsd" :  "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
           |  }
           |}""".stripMargin

      CollectClientTestData("update-ontology-metadata-request", params)

      Put("/v2/ontologies/metadata", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        val metadata        = responseJsonDoc.body
        val ontologyIri     = metadata.value("@id").asInstanceOf[JsonLDString].value
        assert(ontologyIri == fooIri.get)
        assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString(newLabel))
        assert(metadata.value(OntologyConstants.Rdfs.Comment) == JsonLDString(newComment))

        val lastModDate = metadata.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        assert(lastModDate.isAfter(fooLastModDate))
        fooLastModDate = lastModDate
      }
    }

    "change the metadata of 'bar' ontology giving a comment containing a special character" in {
      val newComment = "a \\\"new\\\" comment"

      val params =
        s"""{
           |  "@id": "${barIri.get}",
           |  "rdfs:comment": "$newComment",
           |  "knora-api:lastModificationDate": {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$barLastModDate"
           |  },
           |  "@context": {
           |    "xsd" :  "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
           |  }
           |}""".stripMargin

      Put("/v2/ontologies/metadata", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        val metadata        = responseJsonDoc.body
        val ontologyIri     = metadata.value("@id").asInstanceOf[JsonLDString].value
        assert(ontologyIri == barIri.get)
        assert(
          metadata.value(OntologyConstants.Rdfs.Comment) == JsonLDString(
            stringFormatter.fromSparqlEncodedString(newComment)
          )
        )

        val lastModDate = metadata.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        assert(lastModDate.isAfter(barLastModDate))
        barLastModDate = lastModDate
      }
    }

    "delete the comment from 'foo'" in {
      val fooIriEncoded        = URLEncoder.encode(fooIri.get, "UTF-8")
      val lastModificationDate = URLEncoder.encode(fooLastModDate.toString, "UTF-8")

      Delete(s"/v2/ontologies/comment/$fooIriEncoded?lastModificationDate=$lastModificationDate") ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        val metadata        = responseJsonDoc.body
        val ontologyIri     = metadata.value("@id").asInstanceOf[JsonLDString].value
        assert(ontologyIri == fooIri.get)
        assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString("The modified foo ontology"))
        assert(!metadata.value.contains(OntologyConstants.Rdfs.Comment))

        val lastModDate = metadata.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        assert(lastModDate.isAfter(fooLastModDate))
        fooLastModDate = lastModDate
      }
    }

    "determine that an ontology can be deleted" in {
      val fooIriEncoded = URLEncoder.encode(fooIri.get, "UTF-8")

      Get(s"/v2/ontologies/candeleteontology/$fooIriEncoded") ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]

        CollectClientTestData("can-do-response", responseStr)

        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        assert(responseJsonDoc.body.value(OntologyConstants.KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
      }
    }

    "delete the 'foo' ontology" in {
      val fooIriEncoded        = URLEncoder.encode(fooIri.get, "UTF-8")
      val lastModificationDate = URLEncoder.encode(fooLastModDate.toString, "UTF-8")

      Delete(s"/v2/ontologies/$fooIriEncoded?lastModificationDate=$lastModificationDate") ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)

        CollectClientTestData("delete-ontology-response", responseStr)
      }
    }

    "create a property anything:hasName as a subproperty of knora-api:hasValue and schema:name" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |      "@id" : "anything:hasName",
           |      "@type" : "owl:ObjectProperty",
           |      "knora-api:subjectType" : {
           |        "@id" : "anything:Thing"
           |      },
           |      "knora-api:objectType" : {
           |        "@id" : "knora-api:TextValue"
           |      },
           |      "rdfs:comment" : [ {
           |        "@language" : "en",
           |        "@value" : "The name of a Thing"
           |      }, {
           |        "@language" : "de",
           |        "@value" : "Der Name eines Dinges"
           |      } ],
           |      "rdfs:label" : [ {
           |        "@language" : "en",
           |        "@value" : "has name"
           |      }, {
           |        "@language" : "de",
           |        "@value" : "hat Namen"
           |      } ],
           |      "rdfs:subPropertyOf" : [ {
           |        "@id" : "knora-api:hasValue"
           |      }, {
           |        "@id" : "http://schema.org/name"
           |      } ],
           |      "salsah-gui:guiElement" : {
           |        "@id" : "salsah-gui:SimpleText"
           |      },
           |      "salsah-gui:guiAttribute" : [ "size=80", "maxlength=100" ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      CollectClientTestData("create-value-property-request", params)

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.properties should ===(paramsAsInput.properties)

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate

        CollectClientTestData("create-value-property-response", responseStr)
      }
    }

    "change the rdfs:label of a property" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty",
           |    "rdfs:label" : [ {
           |      "@language" : "en",
           |      "@value" : "has name"
           |    }, {
           |      "@language" : "fr",
           |      "@value" : "a nom"
           |    }, {
           |      "@language" : "de",
           |      "@value" : "hat Namen"
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      CollectClientTestData("change-property-label-request", params)

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects.toSet should ===(
          paramsAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects.toSet
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the rdfs:comment of a property" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty",
           |    "rdfs:comment" : [ {
           |      "@language" : "en",
           |      "@value" : "The name of a Thing"
           |    }, {
           |      "@language" : "fr",
           |      "@value" : "Le nom d'une chose"
           |    }, {
           |      "@language" : "de",
           |      "@value" : "Der Name eines Dinges"
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      CollectClientTestData("change-property-comment-request", params)

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.properties.head._2
          .predicates(OntologyConstants.Rdfs.Comment.toSmartIri)
          .objects
          .toSet should ===(
          paramsAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects.toSet
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "add an rdfs:comment to a link property that has no rdfs:comment" in {
      val params =
        s"""{
           |    "@id": "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |    "@type": "owl:Ontology",
           |    "knora-api:lastModificationDate": {
           |        "@type": "xsd:dateTimeStamp",
           |        "@value": "$anythingLastModDate"
           |    },
           |    "@graph": [
           |        {
           |            "@id": "anything:hasBlueThing",
           |            "@type": "owl:ObjectProperty",
           |            "rdfs:comment": [
           |                {
           |                    "@language": "en",
           |                    "@value": "asdas asd as dasdasdas"
           |                }
           |            ]
           |        }
           |    ],
           |    "@context": {
           |        "anything": "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#",
           |        "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
           |        "owl": "http://www.w3.org/2002/07/owl#",
           |        "xsd": "http://www.w3.org/2001/XMLSchema#",
           |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
           |        "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#"
           |    }
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(
          paramsAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the rdfs:comment of a property" in {
      val propertySegment =
        URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/freetest/v2#hasPropertyWithComment2", "UTF-8")
      val lastModificationDate = URLEncoder.encode(freetestLastModDate.toString, "UTF-8")

      Delete(
        s"/v2/ontologies/properties/comment/$propertySegment?lastModificationDate=$lastModificationDate"
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        val newFreetestLastModDate = responseJsonDoc.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate

        val expectedResponse: String =
          s"""{
             |   "knora-api:lastModificationDate": {
             |       "@value": "${newFreetestLastModDate}",
             |       "@type": "xsd:dateTimeStamp"
             |   },
             |   "rdfs:label": "freetest",
             |   "@graph": [
             |      {
             |         "rdfs:label": [
             |            {
             |               "@value": "Property mit einem Kommentar 2",
             |               "@language": "de"
             |            },
             |            {
             |               "@value": "Property with a comment 2",
             |               "@language": "en"
             |            }
             |         ],
             |         "rdfs:subPropertyOf": {
             |            "@id": "knora-api:hasValue"
             |         },
             |         "@type": "owl:ObjectProperty",
             |         "knora-api:objectType": {
             |            "@id": "knora-api:TextValue"
             |         },
             |         "salsah-gui:guiElement": {
             |            "@id": "salsah-gui:SimpleText"
             |         },
             |         "@id": "freetest:hasPropertyWithComment2"
             |      }
             |   ],
             |   "knora-api:attachedToProject": {
             |      "@id": "http://rdfh.ch/projects/0001"
             |   },
             |   "@type": "owl:Ontology",
             |   "@id": "http://0.0.0.0:3333/ontology/0001/freetest/v2",
             |   "@context": {
             |      "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |      "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
             |      "freetest": "http://0.0.0.0:3333/ontology/0001/freetest/v2#",
             |      "owl": "http://www.w3.org/2002/07/owl#",
             |      "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#",
             |      "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
             |      "xsd": "http://www.w3.org/2001/XMLSchema#"
             |   }
             |}""".stripMargin

        val expectedResponseToCompare: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(expectedResponse)).unescape

        val responseFromJsonLD: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

        responseFromJsonLD.properties.head._2.predicates.toSet should ===(
          expectedResponseToCompare.properties.head._2.predicates.toSet
        )

        CollectClientTestData("delete-property-comment-response", responseStr)
      }
    }

    "delete the rdfs:comment of a class" in {
      val classSegment: String =
        URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/freetest/v2#BookWithComment2", "UTF-8")
      val lastModificationDate = URLEncoder.encode(freetestLastModDate.toString, "UTF-8")

      Delete(
        s"/v2/ontologies/classes/comment/$classSegment?lastModificationDate=$lastModificationDate"
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(responseStr)
        val newFreetestLastModDate = responseJsonDoc.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        assert(newFreetestLastModDate.isAfter(freetestLastModDate))
        freetestLastModDate = newFreetestLastModDate

        val expectedResponse: String =
          s"""{
             |   "knora-api:lastModificationDate": {
             |       "@value": "${newFreetestLastModDate}",
             |       "@type": "xsd:dateTimeStamp"
             |   },
             |   "rdfs:label": "freetest",
             |   "@graph": [
             |      {
             |         "rdfs:label": [
             |            {
             |               "@value": "Buch 2 mit Kommentar",
             |               "@language": "de"
             |            },
             |            {
             |               "@value": "Book 2 with comment",
             |               "@language": "en"
             |            }
             |         ],
             |         "rdfs:subClassOf": [
             |            {
             |               "@id": "knora-api:Resource"
             |            }
             |         ],
             |         "@type": "owl:Class",
             |         "@id": "freetest:BookWithComment2"
             |      }
             |   ],
             |   "knora-api:attachedToProject": {
             |      "@id": "http://rdfh.ch/projects/0001"
             |   },
             |   "@type": "owl:Ontology",
             |   "@id": "http://0.0.0.0:3333/ontology/0001/freetest/v2",
             |   "@context": {
             |      "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
             |      "knora-api": "http://api.knora.org/ontology/knora-api/v2#",
             |      "freetest": "http://0.0.0.0:3333/ontology/0001/freetest/v2#",
             |      "owl": "http://www.w3.org/2002/07/owl#",
             |      "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#",
             |      "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
             |      "xsd": "http://www.w3.org/2001/XMLSchema#"
             |   }
             |}""".stripMargin

        val expectedResponseToCompare: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(expectedResponse)).unescape

        val responseFromJsonLD: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

        responseFromJsonLD.classes.head._2.predicates.toSet should ===(
          expectedResponseToCompare.classes.head._2.predicates.toSet
        )

        CollectClientTestData("delete-class-comment-response", responseStr)
      }
    }

    "change the salsah-gui:guiElement and salsah-gui:guiAttribute of a property" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty",
           |    "salsah-gui:guiElement" : {
           |      "@id" : "salsah-gui:Textarea"
           |    },
           |    "salsah-gui:guiAttribute" : [ "cols=80", "rows=24" ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      CollectClientTestData("change-property-guielement-request", params)

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put(
        "/v2/ontologies/properties/guielement",
        HttpEntity(RdfMediaTypes.`application/ld+json`, params)
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

        responseAsInput.properties.head._2
          .predicates(SalsahGui.External.GuiElementProp.toSmartIri)
          .objects
          .toSet should ===(
          paramsAsInput.properties.head._2
            .predicates(SalsahGui.External.GuiElementProp.toSmartIri)
            .objects
            .toSet
        )

        responseAsInput.properties.head._2
          .predicates(SalsahGui.External.GuiAttribute.toSmartIri)
          .objects
          .toSet should ===(
          paramsAsInput.properties.head._2
            .predicates(SalsahGui.External.GuiAttribute.toSmartIri)
            .objects
            .toSet
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "not change the salsah-gui:guiElement and salsah-gui:guiAttribute of a property if their combination is invalid" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty",
           |    "salsah-gui:guiElement" : {
           |      "@id" : "salsah-gui:List"
           |    },
           |    "salsah-gui:guiAttribute" : [ "cols=80", "rows=24" ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      CollectClientTestData("not-change-property-guielement-request", params)

      // FIXME: Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      // val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put(
        "/v2/ontologies/properties/guielement",
        HttpEntity(RdfMediaTypes.`application/ld+json`, params)
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.BadRequest, responseStr)

        CollectClientTestData("not-change-property-guielement-response", responseStr)
      }
    }

    "remove the salsah-gui:guiElement and salsah-gui:guiAttribute from a property" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasName",
           |    "@type" : "owl:ObjectProperty"
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      CollectClientTestData("remove-property-guielement-request", params)

      Put(
        "/v2/ontologies/properties/guielement",
        HttpEntity(RdfMediaTypes.`application/ld+json`, params)
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

        assert(
          !responseAsInput.properties.head._2.predicates
            .contains(SalsahGui.External.GuiElementProp.toSmartIri)
        )

        assert(
          !responseAsInput.properties.head._2.predicates
            .contains(SalsahGui.External.GuiAttribute.toSmartIri)
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a class anything:WildThing that is a subclass of anything:Thing, with a direct cardinality for anything:hasName" in {

      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:WildThing",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "wild thing"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "A thing that is wild"
           |    },
           |    "rdfs:subClassOf" : [
           |      {
           |        "@id": "anything:Thing"
           |      },
           |      {
           |        "@type": "http://www.w3.org/2002/07/owl#Restriction",
           |        "owl:maxCardinality": 1,
           |        "owl:onProperty": {
           |          "@id": "anything:hasName"
           |        },
           |        "salsah-gui:guiOrder": 1
           |      }
           |    ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      CollectClientTestData("create-class-with-cardinalities-request", params)

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes should ===(paramsAsInput.classes)

        // Check that cardinalities were inherited from anything:Thing.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a class anything:Nothing with no properties" in {
      val params =
        s"""
           |{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "nothing"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "Represents nothing"
           |    },
           |    "rdfs:subClassOf" : {
           |      "@id" : "knora-api:Resource"
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}
           """.stripMargin

      CollectClientTestData("create-class-without-cardinalities-request", params)

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes should ===(paramsAsInput.classes)

        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate

        CollectClientTestData("create-class-without-cardinalities-response", responseStr)
      }
    }

    "change the labels of a class" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : [ {
           |      "@language" : "en",
           |      "@value" : "nothing"
           |    }, {
           |      "@language" : "fr",
           |      "@value" : "rien"
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      CollectClientTestData("change-class-label-request", params)

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects should ===(
          paramsAsInput.classes.head._2.predicates.head._2.objects
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the comments of a class" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:comment" : [ {
           |      "@language" : "en",
           |      "@value" : "Represents nothing"
           |    }, {
           |      "@language" : "fr",
           |      "@value" : "ne représente rien"
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      CollectClientTestData("change-class-comment-request", params)

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(
          paramsAsInput.classes.head._2.predicates.head._2.objects
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a property anything:hasOtherNothing with knora-api:objectType anything:Nothing" in {

      val params =
        s"""
           |{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasOtherNothing",
           |    "@type" : "owl:ObjectProperty",
           |    "knora-api:subjectType" : {
           |      "@id" : "anything:Nothing"
           |    },
           |    "knora-api:objectType" : {
           |      "@id" : "anything:Nothing"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "Refers to the other Nothing of a Nothing"
           |    },
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "has nothingness"
           |    },
           |    "rdfs:subPropertyOf" : {
           |      "@id" : "knora-api:hasLinkTo"
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}
                """.stripMargin

      CollectClientTestData("create-link-property-request", params)

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.properties should ===(paramsAsInput.properties)

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate

        CollectClientTestData("create-link-property-response", responseStr)
      }
    }

    "add a cardinality for the property anything:hasOtherNothing to the class anything:Nothing" in {
      val params =
        s"""
           |{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:hasOtherNothing"
           |      }
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}
                """.stripMargin

      CollectClientTestData("add-cardinalities-to-class-nothing-request", params)

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      val paramsWithAddedLinkValueCardinality = paramsAsInput.copy(
        classes = paramsAsInput.classes.map { case (classIri, classDef) =>
          val hasOtherNothingValueCardinality =
            "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothingValue".toSmartIri ->
              classDef.directCardinalities("http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothing".toSmartIri)

          classIri -> classDef.copy(
            directCardinalities = classDef.directCardinalities + hasOtherNothingValueCardinality
          )
        }
      )

      Post("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.directCardinalities should ===(
          paramsWithAddedLinkValueCardinality.classes.head._2.directCardinalities
        )

        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate

        CollectClientTestData("add-cardinalities-to-class-nothing-response", responseStr)
      }
    }

    "add all IRIs to newly created link value property again" in {
      val url = URLEncoder.encode(s"${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}", "UTF-8")
      Get(
        s"/v2/ontologies/allentities/${url}"
      ) ~> ontologiesPath ~> check {
        val responseStr: String = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        val graph = responseJsonDoc.body.requireArray("@graph").value

        val hasOtherNothingValue = graph
          .filter(
            _.asInstanceOf[JsonLDObject]
              .value("@id")
              .asInstanceOf[JsonLDString]
              .value == "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothingValue"
          )
          .head
          .asInstanceOf[JsonLDObject]

        val iris = hasOtherNothingValue.value.keySet

        val expectedIris = Set(
          OntologyConstants.Rdfs.Comment,
          OntologyConstants.Rdfs.Label,
          OntologyConstants.Rdfs.SubPropertyOf,
          OntologyConstants.KnoraApiV2Complex.IsEditable,
          OntologyConstants.KnoraApiV2Complex.IsResourceProperty,
          OntologyConstants.KnoraApiV2Complex.IsLinkValueProperty,
          OntologyConstants.KnoraApiV2Complex.ObjectType,
          OntologyConstants.KnoraApiV2Complex.SubjectType,
          "@id",
          "@type"
        )

        iris should equal(expectedIris)

        val isEditable = hasOtherNothingValue.requireBoolean(OntologyConstants.KnoraApiV2Complex.IsEditable)
        isEditable shouldBe (true)
      }
    }

    "update the property label and check if the property is still correctly marked as `isEditable: true`" in {
      // update label and comment of a property
      val newLabel = "updated label"
      val params =
        s"""
           |{
           |  "@id" : "$anythingOntoLocalhostIri",
           |  "@type": "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph": [
           |    {
           |      "@id": "$hasOtherNothingIri",
           |      "@type" : "owl:ObjectProperty",
           |      "rdfs:label": {
           |        "@language": "en",
           |        "@value": "$newLabel"
           |      }
           |    }
           |  ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
           |  }
           |}""".stripMargin

      Put("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]

        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc    = JsonLDUtil.parseJsonLD(responseStr)
        val updatedOntologyIri = responseJsonDoc.body.value("@id").asInstanceOf[JsonLDString].value
        assert(updatedOntologyIri == anythingOntoLocalhostIri)
        val graph               = responseJsonDoc.body.requireArray("@graph").value
        val property            = graph.head.asInstanceOf[JsonLDObject]
        val returnedPropertyIri = property.requireString("@id")
        returnedPropertyIri should equal(hasOtherNothingIri)
        val returnedLabel = property.requireObject(OntologyConstants.Rdfs.Label).requireString("@value")
        returnedLabel should equal(newLabel)
        val lastModDate = responseJsonDoc.body.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        assert(lastModDate.isAfter(fooLastModDate))
        anythingLastModDate = lastModDate

        // load back the ontology to verify that the updated property still is editable
        val encodedIri = URLEncoder.encode(s"${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}", "UTF-8")
        Get(
          s"/v2/ontologies/allentities/${encodedIri}"
        ) ~> ontologiesPath ~> check {
          val responseStr: String = responseAs[String]
          assert(status == StatusCodes.OK, response.toString)
          val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

          val graph           = responseJsonDoc.body.requireArray("@graph").value.map(_.asInstanceOf[JsonLDObject])
          val nothingValue    = graph.filter(_.requireString("@id") == hasOtherNothingValueIri).head
          val isEditableMaybe = nothingValue.maybeBoolean(OntologyConstants.KnoraApiV2Complex.IsEditable)
          isEditableMaybe should equal(Some(true))
        }
      }
    }

    "update the property comment and check if the property is still correctly marked as `isEditable: true`" in {
      // update label and comment of a property
      val newComment = "updated comment"

      val params =
        s"""
           |{
           |  "@id" : "$anythingOntoLocalhostIri",
           |  "@type": "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph": [
           |    {
           |      "@id": "$hasOtherNothingIri",
           |      "@type" : "owl:ObjectProperty",
           |      "rdfs:comment": {
           |        "@language": "en",
           |        "@value": "$newComment"
           |      }
           |    }
           |  ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
           |  }
           |}""".stripMargin

      Put("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]

        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc    = JsonLDUtil.parseJsonLD(responseStr)
        val updatedOntologyIri = responseJsonDoc.body.value("@id").asInstanceOf[JsonLDString].value
        assert(updatedOntologyIri == anythingOntoLocalhostIri)
        val graph               = responseJsonDoc.body.requireArray("@graph").value
        val property            = graph.head.asInstanceOf[JsonLDObject]
        val returnedPropertyIri = property.requireString("@id")
        returnedPropertyIri should equal(hasOtherNothingIri)
        val returnedComment = property.requireObject(OntologyConstants.Rdfs.Comment).requireString("@value")
        returnedComment should equal(newComment)
        val lastModDate = responseJsonDoc.body.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        assert(lastModDate.isAfter(fooLastModDate))
        anythingLastModDate = lastModDate

        // load back the ontology to verify that the updated property still is editable
        val encodedIri = URLEncoder.encode(s"${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}", "UTF-8")
        Get(
          s"/v2/ontologies/allentities/${encodedIri}"
        ) ~> ontologiesPath ~> check {
          val responseStr: String = responseAs[String]
          assert(status == StatusCodes.OK, response.toString)
          val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

          val graph           = responseJsonDoc.body.requireArray("@graph").value.map(_.asInstanceOf[JsonLDObject])
          val nothingValue    = graph.filter(_.requireString("@id") == hasOtherNothingValueIri).head
          val isEditableMaybe = nothingValue.maybeBoolean(OntologyConstants.KnoraApiV2Complex.IsEditable)
          isEditableMaybe should equal(Some(true))
        }
      }
    }

    "delete the property comment and check if the property is still correctly marked as `isEditable: true`" in {
      // update label and comment of a property
      val propertyIriEncoded = URLEncoder.encode(hasOtherNothingIri, "UTF-8")

      Delete(
        s"/v2/ontologies/properties/comment/$propertyIriEncoded?lastModificationDate=$anythingLastModDate"
      ) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]

        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc    = JsonLDUtil.parseJsonLD(responseStr)
        val updatedOntologyIri = responseJsonDoc.body.value("@id").asInstanceOf[JsonLDString].value
        assert(updatedOntologyIri == anythingOntoLocalhostIri)
        val graph               = responseJsonDoc.body.requireArray("@graph").value
        val property            = graph.head.asInstanceOf[JsonLDObject]
        val returnedPropertyIri = property.requireString("@id")
        returnedPropertyIri should equal(hasOtherNothingIri)
        assert(property.value.get(OntologyConstants.Rdfs.Comment) == None)
        val lastModDate = responseJsonDoc.body.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        assert(lastModDate.isAfter(fooLastModDate))
        anythingLastModDate = lastModDate

        // load back the ontology to verify that the updated property still is editable
        val encodedIri = URLEncoder.encode(s"${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}", "UTF-8")
        Get(
          s"/v2/ontologies/allentities/${encodedIri}"
        ) ~> ontologiesPath ~> check {
          val responseStr: String = responseAs[String]
          assert(status == StatusCodes.OK, response.toString)
          val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

          val graph           = responseJsonDoc.body.requireArray("@graph").value.map(_.asInstanceOf[JsonLDObject])
          val nothingValue    = graph.filter(_.requireString("@id") == hasOtherNothingValueIri).head
          val isEditableMaybe = nothingValue.maybeBoolean(OntologyConstants.KnoraApiV2Complex.IsEditable)
          isEditableMaybe should equal(Some(true))
        }
      }
    }

    "update the property guiElement and check if the property is still correctly marked as `isEditable: true`" in {
      // update label and comment of a property

      val params =
        s"""
           |{
           |  "@id" : "$anythingOntoLocalhostIri",
           |  "@type": "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph": [
           |    {
           |      "@id": "$hasOtherNothingIri",
           |      "@type" : "owl:ObjectProperty",
           |      "salsah-gui:guiElement": {
           |        "@id": "salsah-gui:Searchbox"
           |      }
           |    }
           |  ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui": "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#"
           |  }
           |}""".stripMargin

      Put(
        "/v2/ontologies/properties/guielement",
        HttpEntity(RdfMediaTypes.`application/ld+json`, params)
      ) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]

        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc    = JsonLDUtil.parseJsonLD(responseStr)
        val updatedOntologyIri = responseJsonDoc.body.value("@id").asInstanceOf[JsonLDString].value
        assert(updatedOntologyIri == anythingOntoLocalhostIri)
        val graph               = responseJsonDoc.body.requireArray("@graph").value
        val property            = graph.head.asInstanceOf[JsonLDObject]
        val returnedPropertyIri = property.requireString("@id")
        returnedPropertyIri should equal(hasOtherNothingIri)
        val lastModDate = responseJsonDoc.body.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        assert(lastModDate.isAfter(fooLastModDate))
        anythingLastModDate = lastModDate

        // load back the ontology to verify that the updated property still is editable
        val encodedIri = URLEncoder.encode(s"${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}", "UTF-8")
        Get(
          s"/v2/ontologies/allentities/${encodedIri}"
        ) ~> ontologiesPath ~> check {
          val responseStr: String = responseAs[String]
          assert(status == StatusCodes.OK, response.toString)
          val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

          val graph           = responseJsonDoc.body.requireArray("@graph").value.map(_.asInstanceOf[JsonLDObject])
          val nothingValue    = graph.filter(_.requireString("@id") == hasOtherNothingValueIri).head
          val isEditableMaybe = nothingValue.maybeBoolean(OntologyConstants.KnoraApiV2Complex.IsEditable)
          isEditableMaybe should equal(Some(true))
        }
      }
    }

    "remove the cardinality for the property anything:hasOtherNothing from the class anything:Nothing" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class"
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      CollectClientTestData("remove-property-cardinality-request", params)

      Put("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.directCardinalities.isEmpty should ===(true)

        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "determine that a property can be deleted" in {
      val propertySegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothing", "UTF-8")

      Get(s"/v2/ontologies/candeleteproperty/$propertySegment") ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        assert(responseJsonDoc.body.value(OntologyConstants.KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
      }
    }

    "delete the property anything:hasOtherNothing" in {
      val propertySegment      = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothing", "UTF-8")
      val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

      Delete(
        s"/v2/ontologies/properties/$propertySegment?lastModificationDate=$lastModificationDate"
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        responseJsonDoc.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===(
          "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri
        )

        val newAnythingLastModDate = responseJsonDoc.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a property anything:hasNothingness with knora-api:subjectType anything:Nothing" in {
      val params =
        s"""{
           |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasNothingness",
           |    "@type" : "owl:ObjectProperty",
           |    "knora-api:subjectType" : {
           |      "@id" : "anything:Nothing"
           |    },
           |    "knora-api:objectType" : {
           |      "@id" : "knora-api:BooleanValue"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "Indicates whether a Nothing has nothingness"
           |    },
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "has nothingness"
           |    },
           |    "rdfs:subPropertyOf" : {
           |      "@id" : "knora-api:hasValue"
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.properties should ===(paramsAsInput.properties)

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "add a cardinality for the property anything:hasNothingness to the class anything:Nothing" in {
      val params =
        s"""{
           |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:hasNothingness"
           |      }
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.directCardinalities should ===(
          paramsAsInput.classes.head._2.directCardinalities
        )

        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "change the GUI order of the cardinality on anything:hasNothingness in the class anything:Nothing" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality": 1,
           |      "owl:onProperty": {
           |        "@id" : "anything:hasNothingness"
           |      },
           |      "salsah-gui:guiOrder": 2
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      CollectClientTestData("change-gui-order-request", params)

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put("/v2/ontologies/guiorder", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.directCardinalities should ===(
          paramsAsInput.classes.head._2.directCardinalities
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a property anything:hasEmptiness with knora-api:subjectType anything:Nothing" in {
      val params =
        s"""{
           |  "@id" : "http://0.0.0.0:3333/ontology/0001/anything/v2",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:hasEmptiness",
           |    "@type" : "owl:ObjectProperty",
           |    "knora-api:subjectType" : {
           |      "@id" : "anything:Nothing"
           |    },
           |    "knora-api:objectType" : {
           |      "@id" : "knora-api:BooleanValue"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "Indicates whether a Nothing has emptiness"
           |    },
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "has emptiness"
           |    },
           |    "rdfs:subPropertyOf" : {
           |      "@id" : "knora-api:hasValue"
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.properties should ===(paramsAsInput.properties)

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "determine that a class's cardinalities can be changed" in {
      val classSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#Nothing", "UTF-8")

      Get(s"/v2/ontologies/canreplacecardinalities/$classSegment") ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        assert(responseJsonDoc.body.value(OntologyConstants.KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
      }
    }

    "change the cardinalities of the class anything:Nothing, replacing anything:hasNothingness with anything:hasEmptiness" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality": 1,
           |      "owl:onProperty": {
           |        "@id" : "anything:hasEmptiness"
           |      }
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      CollectClientTestData("replace-class-cardinalities-request", params)

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

      Put("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.directCardinalities should ===(
          paramsAsInput.classes.head._2.directCardinalities
        )

        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the property anything:hasNothingness" in {
      val propertySegment      = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasNothingness", "UTF-8")
      val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

      Delete(
        s"/v2/ontologies/properties/$propertySegment?lastModificationDate=$lastModificationDate"
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        responseJsonDoc.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===(
          "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri
        )

        val newAnythingLastModDate = responseJsonDoc.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "remove all cardinalities from the class anything:Nothing" in {
      val params =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:Nothing",
           |    "@type" : "owl:Class"
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      CollectClientTestData("remove-class-cardinalities-request", params)

      Put("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.classes.head._2.directCardinalities.isEmpty should ===(true)

        // Check that cardinalities were inherited from knora-api:Resource.
        getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain(
          "http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri
        )

        // Check that the ontology's last modification date was updated.
        val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "delete the property anything:hasEmptiness" in {
      val propertySegment      = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasEmptiness", "UTF-8")
      val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

      Delete(
        s"/v2/ontologies/properties/$propertySegment?lastModificationDate=$lastModificationDate"
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        responseJsonDoc.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===(
          "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri
        )

        val newAnythingLastModDate = responseJsonDoc.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "determine that a class can be deleted" in {
      val classSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#Nothing", "UTF-8")

      Get(s"/v2/ontologies/candeleteclass/$classSegment") ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        assert(responseJsonDoc.body.value(OntologyConstants.KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
      }
    }

    "delete the class anything:Nothing" in {
      val classSegment         = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#Nothing", "UTF-8")
      val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

      Delete(s"/v2/ontologies/classes/$classSegment?lastModificationDate=$lastModificationDate") ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        responseJsonDoc.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===(
          "http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri
        )

        val newAnythingLastModDate = responseJsonDoc.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        assert(newAnythingLastModDate.isAfter(anythingLastModDate))
        anythingLastModDate = newAnythingLastModDate
      }
    }

    "create a shared ontology and put a property in it" in {
      val label = "The useless ontology"

      val createOntologyJson =
        s"""
           |{
           |    "knora-api:ontologyName": "useless",
           |    "knora-api:attachedToProject": {
           |      "@id": "${OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject}"
           |    },
           |    "knora-api:isShared": true,
           |    "rdfs:label": "$label",
           |    "@context": {
           |        "rdfs": "${OntologyConstants.Rdfs.RdfsPrefixExpansion}",
           |        "knora-api": "${OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion}"
           |    }
           |}""".stripMargin

      Post("/v2/ontologies", HttpEntity(RdfMediaTypes.`application/ld+json`, createOntologyJson)) ~> addCredentials(
        BasicHttpCredentials(superUsername, password)
      ) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)
        val metadata        = responseJsonDoc.body
        val ontologyIri     = metadata.value("@id").asInstanceOf[JsonLDString].value
        assert(ontologyIri == "http://api.knora.org/ontology/shared/useless/v2")
        uselessIri.set(ontologyIri)
        assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString(label))

        val lastModDate = metadata.requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )

        uselessLastModDate = lastModDate
      }

      val createPropertyJson =
        s"""
           |{
           |  "@id" : "${uselessIri.get}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$uselessLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "useless:hasSharedName",
           |    "@type" : "owl:ObjectProperty",
           |    "knora-api:objectType" : {
           |      "@id" : "knora-api:TextValue"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "Represents a name"
           |    },
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "has shared name"
           |    },
           |    "rdfs:subPropertyOf" : {
           |      "@id" : "knora-api:hasValue"
           |    }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "useless" : "${uselessIri.get}#"
           |  }
           |}""".stripMargin

      // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
      val paramsAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(createPropertyJson)).unescape

      Post(
        "/v2/ontologies/properties",
        HttpEntity(RdfMediaTypes.`application/ld+json`, createPropertyJson)
      ) ~> addCredentials(BasicHttpCredentials(superUsername, password)) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        responseAsInput.properties should ===(paramsAsInput.properties)

        // Check that the ontology's last modification date was updated.
        val newLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
        assert(newLastModDate.isAfter(uselessLastModDate))
        uselessLastModDate = newLastModDate
      }
    }

    "create a class with several cardinalities, then remove one of the cardinalities" in {
      // Create a class with no cardinalities.

      val createClassRequestJson =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:TestClass",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "test class"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "A test class"
           |    },
           |    "rdfs:subClassOf" : [
           |      {
           |        "@id": "knora-api:Resource"
           |      }
           |    ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      Post(
        "/v2/ontologies/classes",
        HttpEntity(RdfMediaTypes.`application/ld+json`, createClassRequestJson)
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = responseToJsonLDDocument(response)

        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

        anythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
      }

      // Create a text property.

      val createTestTextPropRequestJson =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |      "@id" : "anything:testTextProp",
           |      "@type" : "owl:ObjectProperty",
           |      "knora-api:subjectType" : {
           |        "@id" : "anything:TestClass"
           |      },
           |      "knora-api:objectType" : {
           |        "@id" : "knora-api:TextValue"
           |      },
           |      "rdfs:comment" : {
           |        "@language" : "en",
           |        "@value" : "A test text property"
           |      },
           |      "rdfs:label" : {
           |        "@language" : "en",
           |        "@value" : "test text property"
           |      },
           |      "rdfs:subPropertyOf" : {
           |        "@id" : "knora-api:hasValue"
           |      }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      Post(
        "/v2/ontologies/properties",
        HttpEntity(RdfMediaTypes.`application/ld+json`, createTestTextPropRequestJson)
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        anythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get

      }

      // Create an integer property.

      val createTestIntegerPropRequestJson =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |      "@id" : "anything:testIntProp",
           |      "@type" : "owl:ObjectProperty",
           |      "knora-api:subjectType" : {
           |        "@id" : "anything:TestClass"
           |      },
           |      "knora-api:objectType" : {
           |        "@id" : "knora-api:IntValue"
           |      },
           |      "rdfs:comment" : {
           |        "@language" : "en",
           |        "@value" : "A test int property"
           |      },
           |      "rdfs:label" : {
           |        "@language" : "en",
           |        "@value" : "test int property"
           |      },
           |      "rdfs:subPropertyOf" : {
           |        "@id" : "knora-api:hasValue"
           |      }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      Post(
        "/v2/ontologies/properties",
        HttpEntity(RdfMediaTypes.`application/ld+json`, createTestIntegerPropRequestJson)
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        anythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
      }

      // Create a link property.

      val createTestLinkPropRequestJson =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |      "@id" : "anything:testLinkProp",
           |      "@type" : "owl:ObjectProperty",
           |      "knora-api:subjectType" : {
           |        "@id" : "anything:TestClass"
           |      },
           |      "knora-api:objectType" : {
           |        "@id" : "anything:TestClass"
           |      },
           |      "rdfs:comment" : {
           |        "@language" : "en",
           |        "@value" : "A test link property"
           |      },
           |      "rdfs:label" : {
           |        "@language" : "en",
           |        "@value" : "test link property"
           |      },
           |      "rdfs:subPropertyOf" : {
           |        "@id" : "knora-api:hasLinkTo"
           |      }
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      Post(
        "/v2/ontologies/properties",
        HttpEntity(RdfMediaTypes.`application/ld+json`, createTestLinkPropRequestJson)
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, responseStr)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        anythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
      }

      // Add cardinalities to the class.

      val addCardinalitiesRequestJson =
        s"""
           |{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:TestClass",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : [ {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:testTextProp"
           |      }
           |    },
           |    {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:testIntProp"
           |      }
           |    },
           |    {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:testLinkProp"
           |      }
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      Post(
        "/v2/ontologies/cardinalities",
        HttpEntity(RdfMediaTypes.`application/ld+json`, addCardinalitiesRequestJson)
      ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        anythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
      }

      // Remove the link value cardinality from to the class.
      val params =
        s"""
           |{
           |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$anythingLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "anything:TestClass",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : [ {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:testTextProp"
           |      }
           |    },
           |    {
           |      "@type": "owl:Restriction",
           |      "owl:maxCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:testIntProp"
           |      }
           |    } ]
           |  } ],
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "owl" : "http://www.w3.org/2002/07/owl#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
           |  }
           |}""".stripMargin

      Put("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUsername, password)
      ) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        val responseAsInput: InputOntologyV2 =
          InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
        anythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
      }
    }
  }

  "create a class with two cardinalities, use one in data, and allow only removal of the cardinality for the property not used in data" in {
    // Create a class with no cardinalities.

    val label = LangString.make(LanguageCode.en, "A Blue Free Test class").fold(e => throw e.head, v => v)
    val comment = Some(
      LangString
        .make(LanguageCode.en, "A Blue Free Test class used for testing cardinalities")
        .fold(e => throw e.head, v => v)
    )
    val createClassRequestJson = CreateClassRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "BlueFreeTestClass",
        label = label,
        comment = comment
      )
      .value

    Post(
      "/v2/ontologies/classes",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createClassRequestJson)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      assert(status == StatusCodes.OK, response.toString)
      val responseJsonDoc = responseToJsonLDDocument(response)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // Create a text property.
    val label1 = LangString.make(LanguageCode.en, "blue test text property").fold(e => throw e.head, v => v)
    val comment1 = Some(
      LangString
        .make(LanguageCode.en, "A blue test text property")
        .fold(e => throw e.head, v => v)
    )
    val createTestTextPropRequestJson =
      CreatePropertyRequest
        .make(
          ontologyName = "freetest",
          lastModificationDate = freetestLastModDate,
          propertyName = "hasBlueTestTextProp",
          subjectClassName = Some("BlueFreeTestClass"),
          propertyType = PropertyValueType.TextValue,
          label = label1,
          comment = comment1
        )
        .value

    Post(
      "/v2/ontologies/properties",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createTestTextPropRequestJson)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get

    }

    // Create an integer property.
    val label2 = LangString.make(LanguageCode.en, "blue test integer property").fold(e => throw e.head, v => v)
    val comment2 = Some(
      LangString
        .make(LanguageCode.en, "A blue test integer property")
        .fold(e => throw e.head, v => v)
    )
    val createTestIntegerPropRequestJson = CreatePropertyRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        propertyName = "hasBlueTestIntProp",
        subjectClassName = Some("BlueFreeTestClass"),
        propertyType = PropertyValueType.IntValue,
        label = label2,
        comment = comment2
      )
      .value

    Post(
      "/v2/ontologies/properties",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createTestIntegerPropRequestJson)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // Add cardinalities to the class.

    val addCardinalitiesRequestJson = AddCardinalitiesRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "BlueFreeTestClass",
        restrictions = List(
          Restriction(
            CardinalityRestriction.MaxCardinalityOne,
            onProperty = Property(ontology = "freetest", property = "hasBlueTestTextProp")
          ),
          Restriction(
            CardinalityRestriction.MaxCardinalityOne,
            onProperty = Property(ontology = "freetest", property = "hasBlueTestIntProp")
          )
        )
      )
      .value

    Post(
      "/v2/ontologies/cardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, addCardinalitiesRequestJson)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // Create a resource of #BlueTestClass using only #hasBlueTestIntProp

    val createResourceWithValues: String =
      s"""{
         |  "@type" : "freetest:BlueFreeTestClass",
         |  "freetest:hasBlueTestIntProp" : {
         |    "@type" : "knora-api:IntValue",
         |    "knora-api:hasPermissions" : "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher",
         |    "knora-api:intValueAsInt" : 5,
         |    "knora-api:valueHasComment" : "this is the number five"
         |  },
         |  "knora-api:attachedToProject" : {
         |    "@id" : "http://rdfh.ch/projects/0001"
         |  },
         |  "rdfs:label" : "my blue test class thing instance",
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
         |    "freetest" : "${SharedOntologyTestDataADM.FREETEST_ONTOLOGY_IRI_LocalHost}#"
         |  }
         |}""".stripMargin

    Post(
      "/v2/resources",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createResourceWithValues)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> resourcesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)
    }

    // payload to test cardinality can't be deleted
    val cardinalityCantBeDeletedPayload = AddCardinalitiesRequest.make(
      ontologyName = "freetest",
      lastModificationDate = freetestLastModDate,
      className = "FreeTest",
      restrictions = List(
        Restriction(
          CardinalityRestriction.MinCardinalityOne,
          onProperty = Property(ontology = "freetest", property = "hasText")
        )
      )
    )

    CollectClientTestData("candeletecardinalities-false-request", cardinalityCantBeDeletedPayload.value)

    // Expect cardinality can't be deleted - endpoint should return CanDo response with value false
    Post(
      "/v2/ontologies/candeletecardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, cardinalityCantBeDeletedPayload.value)
    ) ~>
      addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        assert(
          !responseJsonDoc.body
            .value(OntologyConstants.KnoraApiV2Complex.CanDo)
            .asInstanceOf[JsonLDBoolean]
            .value
        )

        CollectClientTestData("candeletecardinalities-false-response", responseStr)
      }

    // Prepare the JsonLD payload to check if a cardinality can be deleted and then to also actually delete it.
    val params =
      s"""
         |{
         |  "@id" : "${SharedOntologyTestDataADM.FREETEST_ONTOLOGY_IRI_LocalHost}",
         |  "@type" : "owl:Ontology",
         |  "knora-api:lastModificationDate" : {
         |    "@type" : "xsd:dateTimeStamp",
         |    "@value" : "$freetestLastModDate"
         |  },
         |  "@graph" : [ {
         |    "@id" : "freetest:BlueFreeTestClass",
         |    "@type" : "owl:Class",
         |    "rdfs:subClassOf" :  {
         |      "@type": "owl:Restriction",
         |      "owl:maxCardinality" : 1,
         |      "owl:onProperty" : {
         |        "@id" : "freetest:hasBlueTestTextProp"
         |      }
         |    }
         |  } ],
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "owl" : "http://www.w3.org/2002/07/owl#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
         |    "freetest" : "${SharedOntologyTestDataADM.FREETEST_ONTOLOGY_IRI_LocalHost}#"
         |  }
         |}""".stripMargin

    CollectClientTestData("candeletecardinalities-true-request", params)

    // Successfully check if the cardinality can be deleted
    Post(
      "/v2/ontologies/candeletecardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, params)
    ) ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password)
    ) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, response.toString)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
      assert(responseJsonDoc.body.value(OntologyConstants.KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)

      CollectClientTestData("candeletecardinalities-true-response", responseStr)
    }

    // Successfully remove the (unused) text value cardinality from the class.
    Patch("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password)
    ) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, response.toString)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }
  }

  "create two classes with the same property, use one in data, and allow removal of the cardinality for the property not used in data" in {
    // Create TestClassOne with no cardinalities.
    val label = LangString.make(LanguageCode.en, "Test class number one").fold(e => throw e.head, v => v)
    val comment = Some(
      LangString
        .make(LanguageCode.en, "A test class used for testing cardinalities")
        .fold(e => throw e.head, v => v)
    )
    val createClassRequestJsonOne = CreateClassRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "TestClassOne",
        label = label,
        comment = comment
      )
      .value

    Post(
      "/v2/ontologies/classes",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createClassRequestJsonOne)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      assert(status == StatusCodes.OK, response.toString)
      val responseJsonDoc = responseToJsonLDDocument(response)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // Create TestClassTwo with no cardinalities
    val label1 = LangString.make(LanguageCode.en, "Test class number two").fold(e => throw e.head, v => v)
    val comment1 = Some(
      LangString
        .make(LanguageCode.en, "A test class used for testing cardinalities")
        .fold(e => throw e.head, v => v)
    )
    val createClassRequestJsonTwo = CreateClassRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "TestClassTwo",
        label = label1,
        comment = comment1
      )
      .value

    Post(
      "/v2/ontologies/classes",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createClassRequestJsonTwo)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      assert(status == StatusCodes.OK, response.toString)
      val responseJsonDoc = responseToJsonLDDocument(response)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // Create a text property hasTestTextProp.
    val label2 = LangString.make(LanguageCode.en, "Test int property").fold(e => throw e.head, v => v)
    val comment2 = Some(
      LangString
        .make(LanguageCode.en, "A test int property")
        .fold(e => throw e.head, v => v)
    )
    val createPropRequestJson = CreatePropertyRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        propertyName = "hasIntProp",
        subjectClassName = None,
        propertyType = PropertyValueType.IntValue,
        label = label2,
        comment = comment2
      )
      .value

    Post(
      "/v2/ontologies/properties",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createPropRequestJson)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get

    }

    // Add cardinality hasIntProp to TestClassOne.
    val addCardinalitiesRequestJsonOne = AddCardinalitiesRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "TestClassOne",
        restrictions = List(
          Restriction(
            CardinalityRestriction.MaxCardinalityOne,
            onProperty = Property(ontology = "freetest", property = "hasIntProp")
          )
        )
      )
      .value

    Post(
      "/v2/ontologies/cardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, addCardinalitiesRequestJsonOne)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // Add cardinality hasIntProp to TestClassTwo.
    val addCardinalitiesRequestJsonTwo = AddCardinalitiesRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "TestClassTwo",
        restrictions = List(
          Restriction(
            CardinalityRestriction.MaxCardinalityOne,
            onProperty = Property(ontology = "freetest", property = "hasIntProp")
          )
        )
      )
      .value

    Post(
      "/v2/ontologies/cardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, addCardinalitiesRequestJsonTwo)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // Create a resource of #TestClassOne using #hasIntProp
    val createResourceWithValues: String =
      s"""{
         |  "@type" : "freetest:TestClassOne",
         |  "freetest:hasIntProp" : {
         |    "@type" : "knora-api:IntValue",
         |    "knora-api:hasPermissions" : "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher",
         |    "knora-api:intValueAsInt" : 5,
         |    "knora-api:valueHasComment" : "this is the number five"
         |  },
         |  "knora-api:attachedToProject" : {
         |    "@id" : "http://rdfh.ch/projects/0001"
         |  },
         |  "rdfs:label" : "test class instance",
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
         |    "freetest" : "${SharedOntologyTestDataADM.FREETEST_ONTOLOGY_IRI_LocalHost}#"
         |  }
         |}""".stripMargin

    Post(
      "/v2/resources",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createResourceWithValues)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> resourcesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)
    }

    // payload to ask if cardinality can be removed from TestClassTwo
    val cardinalityCanBeDeletedPayload = AddCardinalitiesRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "TestClassTwo",
        restrictions = List(
          Restriction(
            CardinalityRestriction.MaxCardinalityOne,
            onProperty = Property(ontology = "freetest", property = "hasIntProp")
          )
        )
      )
      .value

    CollectClientTestData(
      "candeletecardinalities-true-if-not-used-in-this-class-request",
      cardinalityCanBeDeletedPayload
    )

    // Expect cardinality can be deleted from TestClassTwo - CanDo response should return true
    Post(
      "/v2/ontologies/candeletecardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, cardinalityCanBeDeletedPayload)
    ) ~>
      addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
        val responseStr = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
        assert(
          responseJsonDoc.body
            .value(OntologyConstants.KnoraApiV2Complex.CanDo)
            .asInstanceOf[JsonLDBoolean]
            .value
        )

        CollectClientTestData("candeletecardinalities-true-if-not-used-in-this-class-response", responseStr)
      }
  }

  "verify that link-property can not be deleted" in {

    // payload representing a link-property to test that cardinality can't be deleted
    val cardinalityOnLinkPropertyWhichCantBeDeletedPayload = AddCardinalitiesRequest.make(
      ontologyName = "anything",
      lastModificationDate = anythingLastModDate,
      className = "Thing",
      restrictions = List(
        Restriction(
          CardinalityRestriction.MinCardinalityZero,
          onProperty = Property(ontology = "anything", property = "isPartOfOtherThing")
        )
      )
    )

    Post(
      "/v2/ontologies/candeletecardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, cardinalityOnLinkPropertyWhichCantBeDeletedPayload.value)
    ) ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password)
    ) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, response.toString)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
      assert(!responseJsonDoc.body.value(OntologyConstants.KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
    }
  }

  "verify that a class's cardinalities cannot be changed" in {
    val classSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing", "UTF-8")

    Get(s"/v2/ontologies/canreplacecardinalities/$classSegment") ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password)
    ) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
      assert(!responseJsonDoc.body.value(OntologyConstants.KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
    }
  }

  "determine that a class cannot be deleted" in {
    val thingIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#Thing", "UTF-8")

    Get(s"/v2/ontologies/candeleteclass/$thingIri") ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password)
    ) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
      assert(!responseJsonDoc.body.value(OntologyConstants.KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
    }
  }

  "determine that a property cannot be deleted" in {
    val propertyIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasInteger", "UTF-8")

    Get(s"/v2/ontologies/candeleteproperty/$propertyIri") ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password)
    ) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
      assert(!responseJsonDoc.body.value(OntologyConstants.KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
    }
  }

  "determine that an ontology cannot be deleted" in {
    val ontologyIri = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2", "UTF-8")

    Get(s"/v2/ontologies/candeleteontology/$ontologyIri") ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password)
    ) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
      assert(!responseJsonDoc.body.value(OntologyConstants.KnoraApiV2Complex.CanDo).asInstanceOf[JsonLDBoolean].value)
    }
  }

  "create a class w/o comment" in {
    val label = LangString.make(LanguageCode.en, "Test label").fold(e => throw e.head, v => v)
    val request = CreateClassRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "testClass",
        label = label,
        comment = None
      )
      .value

    Post(
      "/v2/ontologies/classes",
      HttpEntity(RdfMediaTypes.`application/ld+json`, request)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      assert(status == StatusCodes.OK, response.toString)

      val responseJsonDoc = responseToJsonLDDocument(response)
      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }
  }

  "create a property w/o comment" in {
    val label = LangString.make(LanguageCode.en, "Test label").fold(e => throw e.head, v => v)
    val request = CreatePropertyRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        propertyName = "testProperty",
        subjectClassName = None,
        propertyType = PropertyValueType.IntValue,
        label = label,
        comment = None
      )
      .value

    Post(
      "/v2/ontologies/properties",
      HttpEntity(RdfMediaTypes.`application/ld+json`, request)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {

      val response = responseAs[String]
      assert(status == StatusCodes.OK, response)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(response)
      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }
  }

  "create a class that is a sequence of a video resource" in {

    val videoResourceIri             = "http://0.0.0.0:3333/ontology/0001/freetest/v2#VideoResource".toSmartIri
    val videoSequenceIri             = "http://0.0.0.0:3333/ontology/0001/freetest/v2#VideoSequence".toSmartIri
    val isSequenceOfVideoPropertyIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#isSequenceOfVideo".toSmartIri

    // create VideoResource class
    val createVideoClassRequest = CreateClassRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "VideoResource",
        subClassOf = Some("knora-api:MovingImageRepresentation")
      )
      .value

    Post(
      "/v2/ontologies/classes",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createVideoClassRequest)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      assert(status == StatusCodes.OK, response.toString)

      val responseJsonDoc = responseToJsonLDDocument(response)
      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      assert(responseAsInput.classes.keySet.contains(videoResourceIri))
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // create VideoSequence class
    val createSequenceClassRequest = CreateClassRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "VideoSequence"
      )
      .value

    Post(
      "/v2/ontologies/classes",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createSequenceClassRequest)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      assert(status == StatusCodes.OK, response.toString)

      val responseJsonDoc = responseToJsonLDDocument(response)
      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      assert(responseAsInput.classes.keySet.contains(videoSequenceIri))
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // create isSequenceOfVideo property
    val sequenceOfPropertyRequest = CreatePropertyRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        propertyName = "isSequenceOfVideo",
        subjectClassName = None,
        propertyType = PropertyValueType.Resource,
        subPropertyOf = Some("knora-api:isSequenceOf")
      )
      .value

    Post(
      "/v2/ontologies/properties",
      HttpEntity(RdfMediaTypes.`application/ld+json`, sequenceOfPropertyRequest)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {

      val response = responseAs[String]
      assert(status == StatusCodes.OK, response)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(response)
      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // add cardinality to class
    val addCardinalitiesRequestJson = AddCardinalitiesRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "VideoSequence",
        restrictions = List(
          Restriction(
            CardinalityRestriction.CardinalityOne,
            onProperty = Property(ontology = "freetest", property = "isSequenceOfVideo")
          ),
          Restriction(
            CardinalityRestriction.CardinalityOne,
            onProperty = Property(ontology = "knora-api", property = "hasSequenceBounds")
          )
        )
      )
      .value

    Post(
      "/v2/ontologies/cardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, addCardinalitiesRequestJson)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // check the ontology to see if all worked as it should
    val url = URLEncoder.encode(s"http://0.0.0.0:3333/ontology/0001/freetest/v2", "UTF-8")
    Get(
      s"/v2/ontologies/allentities/${url}"
    ) ~> ontologiesPath ~> check {
      val responseStr: String = responseAs[String]
      assert(status == StatusCodes.OK, response.toString)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

      assert(responseAsInput.classes.keySet.contains(videoResourceIri))
      assert(responseAsInput.classes.keySet.contains(videoSequenceIri))
      val videoSequenceCardinalities = responseAsInput.classes
        .getOrElse(videoSequenceIri, throw new AssertionError(s"Class $videoSequenceIri not found"))
        .directCardinalities
      assert(videoSequenceCardinalities.keySet.contains(isSequenceOfVideoPropertyIri))
      val cardinality = videoSequenceCardinalities.get(isSequenceOfVideoPropertyIri).get.cardinality
      assert(cardinality == MustHaveOne)
    }

  }

  "create a class that is a sequence of an audio resource" in {

    val audioResourceIri             = "http://0.0.0.0:3333/ontology/0001/freetest/v2#AudioResource".toSmartIri
    val audioSequenceIri             = "http://0.0.0.0:3333/ontology/0001/freetest/v2#AudioSequence".toSmartIri
    val isSequenceOfAudioPropertyIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#isSequenceOfAudio".toSmartIri

    // create AudioResource class
    val createAudioClassRequest = CreateClassRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "AudioResource",
        subClassOf = Some("knora-api:AudioRepresentation")
      )
      .value

    Post(
      "/v2/ontologies/classes",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createAudioClassRequest)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      assert(status == StatusCodes.OK, response.toString)

      val responseJsonDoc = responseToJsonLDDocument(response)
      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      assert(responseAsInput.classes.keySet.contains(audioResourceIri))
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // create AudioSequence class
    val createSequenceClassRequest = CreateClassRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "AudioSequence"
      )
      .value

    Post(
      "/v2/ontologies/classes",
      HttpEntity(RdfMediaTypes.`application/ld+json`, createSequenceClassRequest)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      assert(status == StatusCodes.OK, response.toString)

      val responseJsonDoc = responseToJsonLDDocument(response)
      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      assert(responseAsInput.classes.keySet.contains(audioSequenceIri))
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // create isSequenceOfAudio property
    val sequenceOfPropertyRequest = CreatePropertyRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        propertyName = "isSequenceOfAudio",
        subjectClassName = None,
        propertyType = PropertyValueType.Resource,
        subPropertyOf = Some("knora-api:isSequenceOf")
      )
      .value

    Post(
      "/v2/ontologies/properties",
      HttpEntity(RdfMediaTypes.`application/ld+json`, sequenceOfPropertyRequest)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {

      val response = responseAs[String]
      assert(status == StatusCodes.OK, response)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(response)
      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // add cardinality to class
    val addCardinalitiesRequestJson = AddCardinalitiesRequest
      .make(
        ontologyName = "freetest",
        lastModificationDate = freetestLastModDate,
        className = "AudioSequence",
        restrictions = List(
          Restriction(
            CardinalityRestriction.CardinalityOne,
            onProperty = Property(ontology = "freetest", property = "isSequenceOfAudio")
          ),
          Restriction(
            CardinalityRestriction.CardinalityOne,
            onProperty = Property(ontology = "knora-api", property = "hasSequenceBounds")
          )
        )
      )
      .value

    Post(
      "/v2/ontologies/cardinalities",
      HttpEntity(RdfMediaTypes.`application/ld+json`, addCardinalitiesRequestJson)
    ) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
      val responseStr = responseAs[String]
      assert(status == StatusCodes.OK, responseStr)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
      freetestLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
    }

    // check the ontology to see if all worked as it should
    val url = URLEncoder.encode(s"http://0.0.0.0:3333/ontology/0001/freetest/v2", "UTF-8")
    Get(
      s"/v2/ontologies/allentities/${url}"
    ) ~> ontologiesPath ~> check {
      val responseStr: String = responseAs[String]
      assert(status == StatusCodes.OK, response.toString)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

      val responseAsInput: InputOntologyV2 =
        InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape

      assert(responseAsInput.classes.keySet.contains(audioResourceIri))
      assert(responseAsInput.classes.keySet.contains(audioSequenceIri))
      val audioSequenceCardinalities = responseAsInput.classes
        .getOrElse(audioSequenceIri, throw new AssertionError(s"Class $audioSequenceIri not found"))
        .directCardinalities
      assert(audioSequenceCardinalities.keySet.contains(isSequenceOfAudioPropertyIri))
      val cardinality = audioSequenceCardinalities.get(isSequenceOfAudioPropertyIri).get.cardinality
      assert(cardinality == MustHaveOne)
    }
  }

  "return isSequenceOf and isPartOf properties from knora-base marked as isEditable" in {
    val requestUrl = s"/v2/ontologies/allentities/$knoraApiWithValueObjectsOntologySegment"
    Get(requestUrl) ~> ontologiesPath ~> check {
      val responseStr: String = responseAs[String]
      assert(status == StatusCodes.OK, response.toString)
      val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
      val graph           = responseJsonDoc.body.requireArray(JsonLDKeywords.GRAPH).value.map(_.asInstanceOf[JsonLDObject])

      val isSequenceOfIsEditable = graph
        .find(_.requireString(JsonLDKeywords.ID) == OntologyConstants.KnoraApiV2Complex.IsSequenceOf)
        .fold(false)(_.requireBoolean(OntologyConstants.KnoraApiV2Complex.IsEditable))
      val isSequenceOfValueIsEditable = graph
        .find(_.requireString(JsonLDKeywords.ID) == OntologyConstants.KnoraApiV2Complex.IsSequenceOfValue)
        .fold(false)(_.requireBoolean(OntologyConstants.KnoraApiV2Complex.IsEditable))
      val hasSequenceBoundsIsEditable = graph
        .find(_.requireString(JsonLDKeywords.ID) == OntologyConstants.KnoraApiV2Complex.HasSequenceBounds)
        .fold(false)(_.requireBoolean(OntologyConstants.KnoraApiV2Complex.IsEditable))
      val isPartOfIsEditable = graph
        .find(_.requireString(JsonLDKeywords.ID) == OntologyConstants.KnoraApiV2Complex.IsPartOf)
        .fold(false)(_.requireBoolean(OntologyConstants.KnoraApiV2Complex.IsEditable))
      val isPartOfValueIsEditable = graph
        .find(_.requireString(JsonLDKeywords.ID) == OntologyConstants.KnoraApiV2Complex.IsPartOfValue)
        .fold(false)(_.requireBoolean(OntologyConstants.KnoraApiV2Complex.IsEditable))
      val seqnumIsEditable = graph
        .find(_.requireString(JsonLDKeywords.ID) == OntologyConstants.KnoraApiV2Complex.Seqnum)
        .fold(false)(_.requireBoolean(OntologyConstants.KnoraApiV2Complex.IsEditable))

      assert(isSequenceOfIsEditable)
      assert(isSequenceOfValueIsEditable)
      assert(hasSequenceBoundsIsEditable)
      assert(isPartOfIsEditable)
      assert(isPartOfValueIsEditable)
      assert(seqnumIsEditable)

    }
  }

  "not create a property with invalid gui attribute" in {
    val params =
      s"""{
         |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
         |  "@type" : "owl:Ontology",
         |  "knora-api:lastModificationDate" : {
         |    "@type" : "xsd:dateTimeStamp",
         |    "@value" : "$anythingLastModDate"
         |  },
         |  "@graph" : [ {
         |      "@id" : "anything:hasPropertyWithWrongGuiAttribute",
         |      "@type" : "owl:ObjectProperty",
         |      "knora-api:subjectType" : {
         |        "@id" : "anything:Thing"
         |      },
         |      "knora-api:objectType" : {
         |        "@id" : "knora-api:TextValue"
         |      } ,
         |      "rdfs:label" : [ {
         |        "@language" : "en",
         |        "@value" : "has wrong GUI attribute"
         |      } ],
         |      "rdfs:subPropertyOf" : [ {
         |        "@id" : "knora-api:hasValue"
         |      } ],
         |      "salsah-gui:guiElement" : {
         |        "@id" : "salsah-gui:SimpleText"
         |      },
         |      "salsah-gui:guiAttribute" : [ "size=80", "maxilength=100" ]
         |  } ],
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
         |    "owl" : "http://www.w3.org/2002/07/owl#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
         |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
         |  }
         |}""".stripMargin

    Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password)
    ) ~> ontologiesPath ~> check {

      val responseStr: String = responseAs[String]
      assert(response.status == StatusCodes.BadRequest, responseStr)

    }
  }

  "not create a property with invalid gui attribute value" in {
    val params =
      s"""{
         |  "@id" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}",
         |  "@type" : "owl:Ontology",
         |  "knora-api:lastModificationDate" : {
         |    "@type" : "xsd:dateTimeStamp",
         |    "@value" : "$anythingLastModDate"
         |  },
         |  "@graph" : [ {
         |      "@id" : "anything:hasPropertyWithWrongGuiAttribute",
         |      "@type" : "owl:ObjectProperty",
         |      "knora-api:subjectType" : {
         |        "@id" : "anything:Thing"
         |      },
         |      "knora-api:objectType" : {
         |        "@id" : "knora-api:TextValue"
         |      } ,
         |      "rdfs:label" : [ {
         |        "@language" : "en",
         |        "@value" : "has wrong GUI attribute"
         |      } ],
         |      "rdfs:subPropertyOf" : [ {
         |        "@id" : "knora-api:hasValue"
         |      } ],
         |      "salsah-gui:guiElement" : {
         |        "@id" : "salsah-gui:SimpleText"
         |      },
         |      "salsah-gui:guiAttribute" : [ "size=80", "maxlength=100.7" ]
         |  } ],
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "salsah-gui" : "http://api.knora.org/ontology/salsah-gui/v2#",
         |    "owl" : "http://www.w3.org/2002/07/owl#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
         |    "anything" : "${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}#"
         |  }
         |}""".stripMargin

    Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
      BasicHttpCredentials(anythingUsername, password)
    ) ~> ontologiesPath ~> check {

      val responseStr: String = responseAs[String]
      assert(response.status == StatusCodes.BadRequest, responseStr)

    }
  }
}
