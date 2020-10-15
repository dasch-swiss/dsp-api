package org.knora.webapi.e2e.v2

import java.io.File
import java.net.URLEncoder
import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Accept, BasicHttpCredentials}
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.eclipse.rdf4j.model.Model
import org.knora.webapi._
import org.knora.webapi.e2e.{ClientTestDataCollector, TestDataFileContent, TestDataFilePath}
import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util._
import org.knora.webapi.messages.v2.responder.ontologymessages.{InputOntologyV2, TestResponseParsingModeV2}
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.routing.v2.OntologiesRouteV2
import org.knora.webapi.sharedtestdata.{SharedOntologyTestDataADM, SharedTestDataADM}
import org.knora.webapi.util._
import spray.json._

import scala.concurrent.ExecutionContextExecutor

object OntologyV2R2RSpec {
    private val anythingUserProfile = SharedTestDataADM.anythingAdminUser
    private val anythingUsername = anythingUserProfile.email

    private val superUserProfile = SharedTestDataADM.superUser
    private val superUsername = superUserProfile.email

    private val password = SharedTestDataADM.testPass
}

/**
 * End-to-end test specification for API v2 ontology routes.
 */
class OntologyV2R2RSpec extends R2RSpec {

    import OntologyV2R2RSpec._

    override def testConfigSource: String =
        """
          |# akka.loglevel = "DEBUG"
          |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    private val ontologiesPath = new OntologiesRouteV2(routeData).knoraApiPath

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    // If true, the existing expected response files are overwritten with the HTTP GET responses from the server.
    // If false, the responses from the server are compared to the contents fo the expected response files.
    private val writeTestDataFiles = false

    override lazy val rdfDataObjects = List(
        RdfDataObject(path = "test_data/ontologies/example-box.ttl", name = "http://www.knora.org/ontology/shared/example-box"),
        RdfDataObject(path = "test_data/ontologies/minimal-onto.ttl", name = "http://www.knora.org/ontology/0001/minimal")
    )

    // Directory path for generated client test data
    private val clientTestDataPath: Seq[String] = Seq("v2", "ontologies")

    // Collects client test data
    private val clientTestDataCollector = new ClientTestDataCollector(settings)

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
    private case class HttpGetTest(urlPath: String,
                                   fileBasename: String,
                                   maybeClientTestDataBasename: Option[String] = None,
                                   disableWrite: Boolean = false) {
        def makeFile(mediaType: MediaType.NonBinary): File = {
            val fileSuffix = mediaType.fileExtensions.head
            new File(s"test_data/ontologyR2RV2/$fileBasename.$fileSuffix")
        }

        /**
         * Writes the expected response file.
         *
         * @param responseStr the contents of the file to be written.
         * @param mediaType   the media type of the response.
         */
        def writeFile(responseStr: String, mediaType: MediaType.NonBinary): Unit = {
            if (!disableWrite) {
                // Per default only read access is allowed in the bazel sandbox.
                // This workaround allows to save test output.
                val testOutputDir = sys.env("TEST_UNDECLARED_OUTPUTS_DIR")
                val file = makeFile(mediaType)
                val newOutputFile = new File(testOutputDir, file.getPath)
                newOutputFile.getParentFile.mkdirs()
                FileUtil.writeTextFile(newOutputFile, responseStr)
            }
        }

        /**
         * If `maybeClientTestDataBasename` is defined, stores the response string in [[org.knora.webapi.e2e.ClientTestDataCollector]].
         */
        def storeClientTestData(responseStr: String): Unit = {
            maybeClientTestDataBasename match {
                case Some(clientTestDataBasename) =>
                    clientTestDataCollector.addFile(
                        TestDataFileContent(
                            filePath = TestDataFilePath(
                                directoryPath = clientTestDataPath,
                                filename = clientTestDataBasename,
                                fileExtension = "json"
                            ),
                            text = responseStr
                        )
                    )

                case None => ()
            }
        }

        /**
         * Reads the expected response file.
         *
         * @param mediaType the media type of the response.
         * @return the contents of the file.
         */
        def readFile(mediaType: MediaType.NonBinary): String = {
            FileUtil.readTextFile(makeFile(mediaType))
        }
    }

    // URL-encoded IRIs for use as URL segments in HTTP GET tests.
    private val anythingProjectSegment = URLEncoder.encode(SharedTestDataADM.ANYTHING_PROJECT_IRI, "UTF-8")
    private val incunabulaProjectSegment = URLEncoder.encode(SharedTestDataADM.INCUNABULA_PROJECT_IRI, "UTF-8")
    private val beolProjectSegment = URLEncoder.encode(SharedTestDataADM.BEOL_PROJECT_IRI, "UTF-8")
    private val knoraApiSimpleOntologySegment = URLEncoder.encode(OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri, "UTF-8")
    private val knoraApiWithValueObjectsOntologySegment = URLEncoder.encode(OntologyConstants.KnoraApiV2Complex.KnoraApiOntologyIri, "UTF-8")
    private val incunabulaOntologySimpleSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2", "UTF-8")
    private val incunabulaOntologyWithValueObjectsSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2", "UTF-8")
    private val knoraApiDateSegment = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2#Date", "UTF-8")
    private val knoraApiDateValueSegment = URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2#DateValue", "UTF-8")
    private val knoraApiSimpleHasColorSegment = URLEncoder.encode("http://api.knora.org/ontology/knora-api/simple/v2#hasColor", "UTF-8")
    private val knoraApiWithValueObjectsHasColorSegment = URLEncoder.encode("http://api.knora.org/ontology/knora-api/v2#hasColor", "UTF-8")
    private val incunabulaSimplePubdateSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate", "UTF-8")
    private val incunabulaWithValueObjectsPubDateSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#pubdate", "UTF-8")
    private val incunabulaWithValueObjectsPageSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#page", "UTF-8")
    private val incunabulaWithValueObjectsBookSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book", "UTF-8")
    private val boxOntologyWithValueObjectsSegment = URLEncoder.encode("http://api.knora.org/ontology/shared/example-box/v2", "UTF-8")
    private val minimalOntologyWithValueObjects = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/minimal/v2", "UTF-8")
    private val anythingOntologyWithValueObjects = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2", "UTF-8")
    private val anythingThingWithAllLanguages = URLEncoder.encode(SharedOntologyTestDataADM.ANYTHING_THING_RESOURCE_CLASS_LocalHost, "UTF-8")
    private val imagesBild = URLEncoder.encode(SharedOntologyTestDataADM.IMAGES_BILD_RESOURCE_CLASS_LocalHost, "UTF-8")
    private val incunabulaBook = URLEncoder.encode(SharedOntologyTestDataADM.INCUNABULA_BOOK_RESOURCE_CLASS_LocalHost, "UTF-8")
    private val incunabulaPage = URLEncoder.encode(SharedOntologyTestDataADM.INCUNABULA_PAGE_RESOURCE_CLASS_LocalHost, "UTF-8")
    private val anythingHasListItem = URLEncoder.encode(SharedOntologyTestDataADM.ANYTHING_HasListItem_PROPERTY_LocalHost, "UTF-8")
    private val anythingHasDate = URLEncoder.encode(SharedOntologyTestDataADM.ANYTHING_HasDate_PROPERTY_LocalHost, "UTF-8")
    private val imagesTitel = URLEncoder.encode(SharedOntologyTestDataADM.IMAGES_TITEL_PROPERTY_LocalHost, "UTF-8")
    private val incunabulaPartOf = URLEncoder.encode(SharedOntologyTestDataADM.INCUNABULA_PartOf_Property_LocalHost, "UTF-8")

    // The URLs and expected response files for each HTTP GET test.
    private val httpGetTests = Seq(
        HttpGetTest(urlPath = "/v2/ontologies/metadata", fileBasename = "allOntologyMetadata", maybeClientTestDataBasename = Some("all-ontology-metadata-response")),
        HttpGetTest(urlPath = s"/v2/ontologies/metadata/$anythingProjectSegment", fileBasename = "anythingOntologyMetadata", maybeClientTestDataBasename = Some("get-ontologies-project-anything-response")),
        HttpGetTest(urlPath = s"/v2/ontologies/metadata/$incunabulaProjectSegment", fileBasename = "incunabulaOntologyMetadata", maybeClientTestDataBasename = Some("get-ontologies-project-incunabula-response")),
        HttpGetTest(urlPath = s"/v2/ontologies/metadata/$beolProjectSegment", fileBasename = "beolOntologyMetadata", maybeClientTestDataBasename = Some("get-ontologies-project-beol-response")),
        HttpGetTest(urlPath = s"/v2/ontologies/allentities/$knoraApiSimpleOntologySegment", fileBasename = "knoraApiOntologySimple"),
        HttpGetTest(urlPath = "/ontology/knora-api/simple/v2", fileBasename = "knoraApiOntologySimple", disableWrite = true),
        HttpGetTest(urlPath = s"/v2/ontologies/allentities/$knoraApiWithValueObjectsOntologySegment", fileBasename = "knoraApiOntologyWithValueObjects", maybeClientTestDataBasename = Some("knora-api-ontology")),
        HttpGetTest(urlPath = "/ontology/knora-api/v2", fileBasename = "knoraApiOntologyWithValueObjects", disableWrite = true),
        HttpGetTest(urlPath = "/ontology/salsah-gui/v2", fileBasename = "salsahGuiOntology"),
        HttpGetTest(urlPath = "/ontology/standoff/v2", fileBasename = "standoffOntologyWithValueObjects"),
        HttpGetTest(urlPath = s"/v2/ontologies/allentities/$incunabulaOntologySimpleSegment", fileBasename = "incunabulaOntologySimple"),
        HttpGetTest(urlPath = s"/v2/ontologies/allentities/$incunabulaOntologyWithValueObjectsSegment", fileBasename = "incunabulaOntologyWithValueObjects", maybeClientTestDataBasename = Some("incunabula-ontology")),
        HttpGetTest(urlPath = s"/v2/ontologies/classes/$knoraApiDateSegment", fileBasename = "knoraApiDate"),
        HttpGetTest(urlPath = s"/v2/ontologies/classes/$knoraApiDateValueSegment", fileBasename = "knoraApiDateValue"),
        HttpGetTest(urlPath = s"/v2/ontologies/properties/$knoraApiSimpleHasColorSegment", fileBasename = "knoraApiSimpleHasColor"),
        HttpGetTest(urlPath = s"/v2/ontologies/properties/$knoraApiWithValueObjectsHasColorSegment", fileBasename = "knoraApiWithValueObjectsHasColor"),
        HttpGetTest(urlPath = s"/v2/ontologies/properties/$incunabulaSimplePubdateSegment", fileBasename = "incunabulaSimplePubDate"),
        HttpGetTest(urlPath = s"/v2/ontologies/properties/$incunabulaWithValueObjectsPubDateSegment", fileBasename = "incunabulaWithValueObjectsPubDate"),
        HttpGetTest(urlPath = s"/v2/ontologies/classes/$incunabulaWithValueObjectsPageSegment/$incunabulaWithValueObjectsBookSegment", fileBasename = "incunabulaPageAndBookWithValueObjects"),
        HttpGetTest(urlPath = s"/v2/ontologies/allentities/$boxOntologyWithValueObjectsSegment", fileBasename = "boxOntologyWithValueObjects"),
        HttpGetTest(urlPath = s"/v2/ontologies/allentities/$minimalOntologyWithValueObjects", fileBasename = "minimalOntologyWithValueObjects", maybeClientTestDataBasename = Some("minimal-ontology")),
        HttpGetTest(urlPath = s"/v2/ontologies/allentities/$anythingOntologyWithValueObjects", fileBasename = "anythingOntologyWithValueObjects", maybeClientTestDataBasename = Some("anything-ontology")),
        HttpGetTest(urlPath = s"/v2/ontologies/classes/$anythingThingWithAllLanguages?allLanguages=true", fileBasename = "anythingThingWithAllLanguages", maybeClientTestDataBasename = Some("get-class-anything-thing-with-allLanguages-response")),
        HttpGetTest(urlPath = s"/v2/ontologies/classes/$imagesBild", fileBasename = "imagesBild", maybeClientTestDataBasename = Some("get-class-image-bild-response")),
        HttpGetTest(urlPath = s"/v2/ontologies/classes/$incunabulaBook", fileBasename = "incunabulaBook", maybeClientTestDataBasename = Some("get-class-incunabula-book-response")),
        HttpGetTest(urlPath = s"/v2/ontologies/classes/$incunabulaPage", fileBasename = "incunabulaPage", maybeClientTestDataBasename = Some("get-class-incunabula-page-response")),
        HttpGetTest(urlPath = s"/v2/ontologies/properties/$anythingHasListItem", fileBasename = "anythingHasListItem", maybeClientTestDataBasename = Some("get-property-listValue-response")),
        HttpGetTest(urlPath = s"/v2/ontologies/properties/$anythingHasDate", fileBasename = "anythingHasDate", maybeClientTestDataBasename = Some("get-property-DateValue-response")),
        HttpGetTest(urlPath = s"/v2/ontologies/properties/$imagesTitel", fileBasename = "imagesTitel", maybeClientTestDataBasename = Some("get-property-textValue-response")),
        HttpGetTest(urlPath = s"/v2/ontologies/properties/$incunabulaPartOf", fileBasename = "incunabulaPartOf", maybeClientTestDataBasename = Some("get-property-linkvalue-response"))
    )

    // The media types that will be used in HTTP Accept headers in HTTP GET tests.
    private val mediaTypesForGetTests = Seq(
        RdfMediaTypes.`application/ld+json`,
        RdfMediaTypes.`text/turtle`,
        RdfMediaTypes.`application/rdf+xml`
    )

    private val fooIri = new MutableTestIri
    private var fooLastModDate: Instant = Instant.now

    private var anythingLastModDate: Instant = Instant.parse("2017-12-19T15:23:42.166Z")

    private val uselessIri = new MutableTestIri
    private var uselessLastModDate: Instant = Instant.now

    private def getPropertyIrisFromResourceClassResponse(responseJsonDoc: JsonLDDocument): Set[SmartIri] = {
        val classDef = responseJsonDoc.body.requireArray("@graph").value.head.asInstanceOf[JsonLDObject]

        classDef.value(OntologyConstants.Rdfs.SubClassOf).asInstanceOf[JsonLDArray].value.collect {
            case obj: JsonLDObject if !obj.isIri => obj.requireIriInObject(OntologyConstants.Owl.OnProperty, stringFormatter.toSmartIriWithErr)
        }.toSet
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
                                    val existingFile: File = httpGetTest.makeFile(mediaType)

                                    if (existingFile.exists()) {
                                        val parsedResponse: Model = parseRdfXml(responseStr)
                                        val parsedExistingFile: Model = parseRdfXml(httpGetTest.readFile(mediaType))

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

        "create an empty ontology called 'foo' with a project code" in {
            val label = "The foo ontology"

            val params =
                s"""{
                   |    "knora-api:ontologyName": "foo",
                   |    "knora-api:attachedToProject": {
                   |      "@id": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}"
                   |    },
                   |    "rdfs:label": "$label",
                   |    "@context": {
                   |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                   |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
                   |    }
                   |}""".stripMargin

            clientTestDataCollector.addFile(
                TestDataFileContent(
                    filePath = TestDataFilePath(
                        directoryPath = clientTestDataPath,
                        filename = "create-empty-foo-ontology-request",
                        fileExtension = "json"
                    ),
                    text = params
                )
            )

            Post("/v2/ontologies", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                val responseStr = responseAs[String]
                assert(status == StatusCodes.OK, responseStr)
                val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
                val metadata = responseJsonDoc.body
                val ontologyIri = metadata.value("@id").asInstanceOf[JsonLDString].value
                assert(ontologyIri == "http://0.0.0.0:3333/ontology/0001/foo/v2")
                fooIri.set(ontologyIri)
                assert(metadata.value(OntologyConstants.Rdfs.Label) == JsonLDString(label))

                val lastModDate = metadata.requireDatatypeValueInObject(
                    key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
                    expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                    validationFun = stringFormatter.xsdDateTimeStampToInstant
                )

                fooLastModDate = lastModDate

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "create-empty-foo-ontology-response",
                            fileExtension = "json"
                        ),
                        text = responseStr
                    )
                )
            }
        }

        "create an empty ontology called 'bar' with a comment" in {
            val label = "The bar ontology"
            val comment = "some comment"

            val params =
                s"""{
                   |    "knora-api:ontologyName": "bar",
                   |    "knora-api:attachedToProject": {
                   |      "@id": "${SharedTestDataADM.ANYTHING_PROJECT_IRI}"
                   |    },
                   |    "rdfs:label": "$label",
                   |    "rdfs:comment": "$comment",
                   |    "@context": {
                   |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
                   |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
                   |    }
                   |}""".stripMargin

            clientTestDataCollector.addFile(
                TestDataFileContent(
                    filePath = TestDataFilePath(
                        directoryPath = clientTestDataPath,
                        filename = "create-ontology-with-comment-request",
                        fileExtension = "json"
                    ),
                    text = params
                )
            )

            Post("/v2/ontologies", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                val responseStr = responseAs[String]
                assert(status == StatusCodes.OK, responseStr)
                val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)
                val metadata = responseJsonDoc.body
                val ontologyIri = metadata.value("@id").asInstanceOf[JsonLDString].value
                assert(ontologyIri == "http://0.0.0.0:3333/ontology/0001/bar/v2")
                assert(metadata.value(OntologyConstants.Rdfs.Comment) == JsonLDString(comment))

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "create-ontology-with-comment-response",
                            fileExtension = "json"
                        ),
                        text = responseStr
                    )
                )
            }
        }

        "change the metadata of 'foo'" in {
            val newLabel = "The modified foo ontology"
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

            clientTestDataCollector.addFile(
                TestDataFileContent(
                    filePath = TestDataFilePath(
                        directoryPath = clientTestDataPath,
                        filename = "update-ontology-metadata-request",
                        fileExtension = "json"
                    ),
                    text = params
                )
            )

            Put("/v2/ontologies/metadata", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                val metadata = responseJsonDoc.body
                val ontologyIri = metadata.value("@id").asInstanceOf[JsonLDString].value
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

        "delete the 'foo' ontology" in {
            val fooIriEncoded = URLEncoder.encode(fooIri.get, "UTF-8")
            val lastModificationDate = URLEncoder.encode(fooLastModDate.toString, "UTF-8")

            Delete(s"/v2/ontologies/$fooIriEncoded?lastModificationDate=$lastModificationDate") ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                val responseStr = responseAs[String]
                assert(status == StatusCodes.OK, responseStr)

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "delete-ontology-response",
                            fileExtension = "json"
                        ),
                        text = responseStr
                    )
                )
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

            clientTestDataCollector.addFile(
                TestDataFileContent(
                    filePath = TestDataFilePath(
                        directoryPath = clientTestDataPath,
                        filename = "create-value-property-request",
                        fileExtension = "json"
                    ),
                    text = params
                )
            )

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                val responseStr = responseAs[String]
                assert(status == StatusCodes.OK, responseStr)
                val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.properties should ===(paramsAsInput.properties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "create-value-property-response",
                            fileExtension = "json"
                        ),
                        text = responseStr
                    )
                )
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

            clientTestDataCollector.addFile(
                TestDataFileContent(
                    filePath = TestDataFilePath(
                        directoryPath = clientTestDataPath,
                        filename = "change-property-label-request",
                        fileExtension = "json"
                    ),
                    text = params
                )
            )

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects.toSet should ===(paramsAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects.toSet)

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

            clientTestDataCollector.addFile(
                TestDataFileContent(
                    filePath = TestDataFilePath(
                        directoryPath = clientTestDataPath,
                        filename = "change-property-comment-request",
                        fileExtension = "json"
                    ),
                    text = params
                )
            )

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects.toSet should ===(paramsAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects.toSet)

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

            Put("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(paramsAsInput.properties.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects)

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

            clientTestDataCollector.addFile(
                TestDataFileContent(
                    filePath = TestDataFilePath(
                        directoryPath = clientTestDataPath,
                        filename = "create-class-with-cardinalities-request",
                        fileExtension = "json"
                    ),
                    text = params
                )
            )

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes should ===(paramsAsInput.classes)

                // Check that cardinalities were inherited from anything:Thing.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain("http://0.0.0.0:3333/ontology/0001/anything/v2#hasDecimal".toSmartIri)

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

            clientTestDataCollector.addFile(
                TestDataFileContent(
                    filePath = TestDataFilePath(
                        directoryPath = clientTestDataPath,
                        filename = "create-class-without-cardinalities-request",
                        fileExtension = "json"
                    ),
                    text = params
                )
            )

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                val responseStr = responseAs[String]
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes should ===(paramsAsInput.classes)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain("http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "create-class-without-cardinalities-response",
                            fileExtension = "json"
                        ),
                        text = responseStr
                    )
                )
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

            clientTestDataCollector.addFile(
                TestDataFileContent(
                    filePath = TestDataFilePath(
                        directoryPath = clientTestDataPath,
                        filename = "change-class-label-request",
                        fileExtension = "json"
                    ),
                    text = params
                )
            )

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes.head._2.predicates(OntologyConstants.Rdfs.Label.toSmartIri).objects should ===(paramsAsInput.classes.head._2.predicates.head._2.objects)

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

            clientTestDataCollector.addFile(
                TestDataFileContent(
                    filePath = TestDataFilePath(
                        directoryPath = clientTestDataPath,
                        filename = "change-class-comment-request",
                        fileExtension = "json"
                    ),
                    text = params
                )
            )

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/classes", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes.head._2.predicates(OntologyConstants.Rdfs.Comment.toSmartIri).objects should ===(paramsAsInput.classes.head._2.predicates.head._2.objects)

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

            clientTestDataCollector.addFile(
                TestDataFileContent(
                    filePath = TestDataFilePath(
                        directoryPath = clientTestDataPath,
                        filename = "create-link-property-request",
                        fileExtension = "json"
                    ),
                    text = params
                )
            )

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                val responseStr = responseAs[String]
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.properties should ===(paramsAsInput.properties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "create-link-property-response",
                            fileExtension = "json"
                        ),
                        text = responseStr
                    )
                )
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

            clientTestDataCollector.addFile(
                TestDataFileContent(
                    filePath = TestDataFilePath(
                        directoryPath = clientTestDataPath,
                        filename = "add-cardinalities-to-class-nothing-request",
                        fileExtension = "json"
                    ),
                    text = params
                )
            )

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            val paramsWithAddedLinkValueCardinality = paramsAsInput.copy(
                classes = paramsAsInput.classes.map {
                    case (classIri, classDef) =>
                        val hasOtherNothingValueCardinality = "http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothingValue".toSmartIri ->
                            classDef.directCardinalities("http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothing".toSmartIri)

                        classIri -> classDef.copy(
                            directCardinalities = classDef.directCardinalities + hasOtherNothingValueCardinality
                        )
                }
            )

            Post("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                val responseStr = responseAs[String]
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes.head._2.directCardinalities should ===(paramsWithAddedLinkValueCardinality.classes.head._2.directCardinalities)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain("http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate

                clientTestDataCollector.addFile(
                    TestDataFileContent(
                        filePath = TestDataFilePath(
                            directoryPath = clientTestDataPath,
                            filename = "add-cardinalities-to-class-nothing-response",
                            fileExtension = "json"
                        ),
                        text = responseStr
                    )
                )
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

            clientTestDataCollector.addFile(
                TestDataFileContent(
                    filePath = TestDataFilePath(
                        directoryPath = clientTestDataPath,
                        filename = "remove-property-cardinality-request",
                        fileExtension = "json"
                    ),
                    text = params
                )
            )

            Put("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes.head._2.directCardinalities.isEmpty should ===(true)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain("http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "delete the property anything:hasOtherNothing" in {
            val propertySegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasOtherNothing", "UTF-8")
            val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

            Delete(s"/v2/ontologies/properties/$propertySegment?lastModificationDate=$lastModificationDate") ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                responseJsonDoc.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)

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

            Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
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

            Post("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes.head._2.directCardinalities should ===(paramsAsInput.classes.head._2.directCardinalities)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain("http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri)

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

            Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.properties should ===(paramsAsInput.properties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
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

            clientTestDataCollector.addFile(
                TestDataFileContent(
                    filePath = TestDataFilePath(
                        directoryPath = clientTestDataPath,
                        filename = "replace-class-cardinalities-request",
                        fileExtension = "json"
                    ),
                    text = params
                )
            )

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(params)).unescape

            Put("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes.head._2.directCardinalities should ===(paramsAsInput.classes.head._2.directCardinalities)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain("http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "delete the property anything:hasNothingness" in {
            val propertySegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasNothingness", "UTF-8")
            val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

            Delete(s"/v2/ontologies/properties/$propertySegment?lastModificationDate=$lastModificationDate") ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                responseJsonDoc.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)

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

            clientTestDataCollector.addFile(
                TestDataFileContent(
                    filePath = TestDataFilePath(
                        directoryPath = clientTestDataPath,
                        filename = "remove-class-cardinalities-request",
                        fileExtension = "json"
                    ),
                    text = params
                )
            )

            Put("/v2/ontologies/cardinalities", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.classes.head._2.directCardinalities.isEmpty should ===(true)

                // Check that cardinalities were inherited from knora-api:Resource.
                getPropertyIrisFromResourceClassResponse(responseJsonDoc) should contain("http://api.knora.org/ontology/knora-api/v2#attachedToUser".toSmartIri)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "delete the property anything:hasEmptiness" in {
            val propertySegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#hasEmptiness", "UTF-8")
            val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

            Delete(s"/v2/ontologies/properties/$propertySegment?lastModificationDate=$lastModificationDate") ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                responseJsonDoc.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)

                val newAnythingLastModDate = responseJsonDoc.requireDatatypeValueInObject(
                    key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
                    expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
                    validationFun = stringFormatter.xsdDateTimeStampToInstant
                )

                assert(newAnythingLastModDate.isAfter(anythingLastModDate))
                anythingLastModDate = newAnythingLastModDate
            }
        }

        "delete the class anything:Nothing" in {
            val classSegment = URLEncoder.encode("http://0.0.0.0:3333/ontology/0001/anything/v2#Nothing", "UTF-8")
            val lastModificationDate = URLEncoder.encode(anythingLastModDate.toString, "UTF-8")

            Delete(s"/v2/ontologies/classes/$classSegment?lastModificationDate=$lastModificationDate") ~> addCredentials(BasicHttpCredentials(anythingUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                responseJsonDoc.requireStringWithValidation("@id", stringFormatter.toSmartIriWithErr) should ===("http://0.0.0.0:3333/ontology/0001/anything/v2".toSmartIri)

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
                   |}
                """.stripMargin

            Post("/v2/ontologies", HttpEntity(RdfMediaTypes.`application/ld+json`, createOntologyJson)) ~> addCredentials(BasicHttpCredentials(superUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)
                val metadata = responseJsonDoc.body
                val ontologyIri = metadata.value("@id").asInstanceOf[JsonLDString].value
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
                   |}
            """.stripMargin

            // Convert the submitted JSON-LD to an InputOntologyV2, without SPARQL-escaping, so we can compare it to the response.
            val paramsAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(JsonLDUtil.parseJsonLD(createPropertyJson)).unescape

            Post("/v2/ontologies/properties", HttpEntity(RdfMediaTypes.`application/ld+json`, createPropertyJson)) ~> addCredentials(BasicHttpCredentials(superUsername, password)) ~> ontologiesPath ~> check {
                assert(status == StatusCodes.OK, response.toString)
                val responseJsonDoc = responseToJsonLDDocument(response)

                // Convert the response to an InputOntologyV2 and compare the relevant part of it to the request.
                val responseAsInput: InputOntologyV2 = InputOntologyV2.fromJsonLD(responseJsonDoc, parsingMode = TestResponseParsingModeV2).unescape
                responseAsInput.properties should ===(paramsAsInput.properties)

                // Check that the ontology's last modification date was updated.
                val newAnythingLastModDate = responseAsInput.ontologyMetadata.lastModificationDate.get
                assert(newAnythingLastModDate.isAfter(uselessLastModDate))
                uselessLastModDate = newAnythingLastModDate
            }
        }
    }
}
