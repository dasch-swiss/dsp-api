package org.knora.webapi.e2e.v2

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi._
import org.knora.webapi.e2e.{ClientTestDataCollector, TestDataFileContent, TestDataFilePath}
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.v2.responder.ontologymessages.{InputOntologyV2, TestResponseParsingModeV2}
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.routing.v2.{OntologiesRouteV2, ResourcesRouteV2}
import org.knora.webapi.sharedtestdata.{SharedOntologyTestDataADM, SharedTestDataADM}
import org.knora.webapi.util._

import java.net.URLEncoder
import java.nio.file.{Files, Path, Paths}
import java.time.Instant
import scala.concurrent.ExecutionContextExecutor

object tempE2eSpec {
  private val anythingUserProfile = SharedTestDataADM.anythingAdminUser
  private val anythingUsername    = anythingUserProfile.email

  private val superUserProfile = SharedTestDataADM.superUser
  private val superUsername    = superUserProfile.email

  private val password = SharedTestDataADM.testPass
}

/**
 * End-to-end test specification for API v2 ontology routes.
 */
class tempE2eSpec extends R2RSpec {

  import tempE2eSpec._
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
    RdfDataObject(path = "test_data/all_data/freetest-data.ttl", name = "http://www.knora.org/data/0001/freetest")
  )

  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  private val ontologiesPath                            = DSPApiDirectives.handleErrors(system)(new OntologiesRouteV2(routeData).knoraApiPath)
  private val resourcesPath                             = DSPApiDirectives.handleErrors(system)(new ResourcesRouteV2(routeData).knoraApiPath)

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  // If true, the existing expected response files are overwritten with the HTTP GET responses from the server.
  // If false, the responses from the server are compared to the contents fo the expected response files.
  // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
  private val writeTestDataFiles = false
  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("v2", "ontologies")
  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(settings)
  // URL-encoded IRIs for use as URL segments in HTTP GET tests.
  private val anythingProjectSegment   = URLEncoder.encode(SharedTestDataADM.ANYTHING_PROJECT_IRI, "UTF-8")
  private val incunabulaProjectSegment = URLEncoder.encode(SharedTestDataADM.INCUNABULA_PROJECT_IRI, "UTF-8")
  private val beolProjectSegment       = URLEncoder.encode(SharedTestDataADM.BEOL_PROJECT_IRI, "UTF-8")
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
  private val fooIri                       = new MutableTestIri
  private val barIri                       = new MutableTestIri
  private val uselessIri                   = new MutableTestIri
  private var fooLastModDate: Instant      = Instant.now
  private var barLastModDate: Instant      = Instant.now
  private var anythingLastModDate: Instant = Instant.parse("2017-12-19T15:23:42.166Z")
  private var freetestLastModDate: Instant = Instant.parse("2012-12-12T12:12:12.12Z")
  private var uselessLastModDate: Instant  = Instant.now

  override def testConfigSource: String =
    """
      |# akka.loglevel = "DEBUG"
      |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

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

    /**
     * Writes the expected response file.
     *
     * @param responseStr the contents of the file to be written.
     * @param mediaType   the media type of the response.
     */
    def writeFile(responseStr: String, mediaType: MediaType.NonBinary): Unit =
      if (!disableWrite) {
        // Per default only read access is allowed in the bazel sandbox.
        // This workaround allows to save test output.
        val testOutputDir = Paths.get(sys.env("TEST_UNDECLARED_OUTPUTS_DIR"))
        val file          = makeFile(mediaType)
        val newOutputFile = testOutputDir.resolve(file)
        Files.createDirectories(newOutputFile.getParent)
        FileUtil.writeTextFile(newOutputFile, responseStr)
      }

    def makeFile(mediaType: MediaType.NonBinary): Path = {
      val fileSuffix = mediaType.fileExtensions.head
      Paths.get(s"test_data/ontologyR2RV2/$fileBasename.$fileSuffix")
    }

    /**
     * If `maybeClientTestDataBasename` is defined, stores the response string in [[org.knora.webapi.e2e.ClientTestDataCollector]].
     */
    def storeClientTestData(responseStr: String): Unit =
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

    /**
     * Reads the expected response file.
     *
     * @param mediaType the media type of the response.
     * @return the contents of the file.
     */
    def readFile(mediaType: MediaType.NonBinary): String =
      FileUtil.readTextFile(makeFile(mediaType))
  }

  "The Ontologies v2 Endpoint" should {

    "create a class anything:Book with no properties" in {
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
           |    "@id" : "anything:Book",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "book"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "Represents a book"
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

    "create a class anything:Page with no properties" in {
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
           |    "@id" : "anything:Page",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "Page"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "Represents a page"
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

    "create a property anything:hasPage with knora-api:objectType anything:Book" in {

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
           |    "@id" : "anything:hasPage",
           |    "@type" : "owl:ObjectProperty",
           |    "knora-api:subjectType" : {
           |      "@id" : "anything:Book"
           |    },
           |    "knora-api:objectType" : {
           |      "@id" : "anything:Page"
           |    },
           |    "rdfs:comment" : {
           |      "@language" : "en",
           |      "@value" : "A book has a page."
           |    },
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "Has page"
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

    "add a cardinality for the property anything:hasPage to the class anything:Book" in {
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
           |    "@id" : "anything:Book",
           |    "@type" : "owl:Class",
           |    "rdfs:subClassOf" : {
           |      "@type": "owl:Restriction",
           |      "owl:minCardinality" : 1,
           |      "owl:onProperty" : {
           |        "@id" : "anything:hasPage"
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
        classes = paramsAsInput.classes.map { case (classIri, classDef) =>
          val hasPageValueCardinality =
            "http://0.0.0.0:3333/ontology/0001/anything/v2#hasPageValue".toSmartIri ->
              classDef.directCardinalities("http://0.0.0.0:3333/ontology/0001/anything/v2#hasPage".toSmartIri)

          classIri -> classDef.copy(
            directCardinalities = classDef.directCardinalities + hasPageValueCardinality
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

    "should have added isEditable in newly added link property value" in {
      val url = URLEncoder.encode(s"${SharedOntologyTestDataADM.ANYTHING_ONTOLOGY_IRI_LocalHost}", "UTF-8")
      Get(
        s"/v2/ontologies/allentities/${url}"
      ) ~> ontologiesPath ~> check {
        val responseStr: String = responseAs[String]
        assert(status == StatusCodes.OK, response.toString)
        val responseJsonDoc = JsonLDUtil.parseJsonLD(responseStr)

        val graph = responseJsonDoc.body.requireArray("@graph").value //.head.asInstanceOf[JsonLDObject]

        val hasPageValue = graph
          .filter(
            _.asInstanceOf[JsonLDObject]
              .value("@id")
              .asInstanceOf[JsonLDString]
              .value == "http://0.0.0.0:3333/ontology/0001/anything/v2#hasPageValue"
          )
          .head
          .asInstanceOf[JsonLDObject]

        val iris = hasPageValue.value.keySet

        iris.size should equal(10)

        iris should contain allOf (
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
      }
    }

  }
}
