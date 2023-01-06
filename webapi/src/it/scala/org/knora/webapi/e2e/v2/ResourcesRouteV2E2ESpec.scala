/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.MediaRange
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import org.xmlunit.diff.Diff
import spray.json.JsValue
import spray.json.JsonParser
import java.net.URLEncoder
import java.nio.file.Paths
import java.time.Instant
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.duration._
import dsp.errors.AssertionException

import org.knora.webapi._
import org.knora.webapi.e2e.ClientTestDataCollector
import org.knora.webapi.e2e.InstanceChecker
import org.knora.webapi.e2e.TestDataFileContent
import org.knora.webapi.e2e.TestDataFilePath
import org.knora.webapi.e2e.v2.ResponseCheckerV2._
import org.knora.webapi.http.directives.DSPApiDirectives
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util._
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.v2.OntologiesRouteV2
import org.knora.webapi.sharedtestdata.SharedOntologyTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util._

/**
 * Tests the API v2 resources route.
 */
class ResourcesRouteV2E2ESpec extends E2ESpec {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(
    appConfig.defaultTimeoutAsDuration
  )

  private val anythingUserEmail             = SharedTestDataADM.anythingUser1.email
  private val password                      = SharedTestDataADM.testPass
  private var aThingLastModificationDate    = Instant.now
  private val hamletResourceIri             = new MutableTestIri
  private val aThingIri                     = "http://rdfh.ch/0001/a-thing"
  private val aThingIriEncoded              = URLEncoder.encode(aThingIri, "UTF-8")
  private val aThingWithHistoryIri          = "http://rdfh.ch/0001/thing-with-history"
  private val aThingWithHistoryIriEncoded   = URLEncoder.encode(aThingWithHistoryIri, "UTF-8")
  private val reiseInsHeiligeLandIriEncoded = URLEncoder.encode("http://rdfh.ch/0803/2a6221216701", "UTF-8")

  // If true, writes all API responses to test data files. If false, compares the API responses to the existing test data files.
  private val writeTestDataFiles = false

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(
      path = "test_data/ontologies/anything-onto.ttl",
      name = "http://www.knora.org/ontology/0001/anything"
    ),
    RdfDataObject(
      path = "test_data/ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest"
    ),
    RdfDataObject(
      path = "test_data/ontologies/sequences-onto.ttl",
      name = "http://www.knora.org/ontology/0001/sequences"
    ),
    RdfDataObject(
      path = "test_data/all_data/sequences-data.ttl",
      name = "http://www.knora.org/data/0001/sequences"
    )
  )

  private val instanceChecker: InstanceChecker = InstanceChecker.getJsonLDChecker

  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("v2", "resources")

  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(appConfig)

  private def collectClientTestData(fileName: String, fileContent: String, fileExtension: String = "json"): Unit =
    clientTestDataCollector.addFile(
      TestDataFileContent(
        filePath = TestDataFilePath(
          directoryPath = clientTestDataPath,
          filename = fileName,
          fileExtension = fileExtension
        ),
        text = fileContent
      )
    )

  private def successResponse(message: String): String =
    s"""{
       |  "knora-api:result" : "$message",
       |  "@context" : {
       |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
       |  }
       |}""".stripMargin

  private def updateResourceMetadataResponse(
    newLastModificationDate: Instant,
    maybeNewLabel: String,
    resourceIri: String,
    maybeNewPermissions: String
  ): String =
    s"""{
       |    "knora-api:lastModificationDate": {
       |        "@value": "$newLastModificationDate",
       |        "@type": "xsd:dateTimeStamp"
       |    },
       |    "rdfs:label": "$maybeNewLabel",
       |    "knora-api:resourceIri": "$resourceIri",
       |    "knora-api:hasPermissions": "$maybeNewPermissions",
       |    "knora-api:resourceClassIri": "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing",
       |    "@context": {
       |        "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
       |        "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
       |        "xsd": "http://www.w3.org/2001/XMLSchema#",
       |        "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
       |    }
       |}""".stripMargin

  "The resources v2 endpoint" should {
    "perform a resource request for the book 'Reise ins Heilige Land' using the complex schema in JSON-LD" in {
      val request                = Get(s"$baseApiUrl/v2/resources/$reiseInsHeiligeLandIriEncoded")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)

      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"),
          writeTestDataFiles
        )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/v2#book".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the complex schema in Turtle" in {
      // Test correct handling of q values in the Accept header.
      val acceptHeader: Accept = Accept(
        MediaRange.One(RdfMediaTypes.`application/ld+json`, 0.5f),
        MediaRange.One(RdfMediaTypes.`text/turtle`, 0.8f),
        MediaRange.One(RdfMediaTypes.`application/rdf+xml`, 0.2f)
      )

      val request = Get(s"$baseApiUrl/v2/resources/$reiseInsHeiligeLandIriEncoded")
        .addHeader(acceptHeader)
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerTurtle = readOrWriteTextFile(
        responseAsString,
        Paths.get("..", "test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"),
        writeTestDataFiles
      )
      assert(parseTurtle(responseAsString) == parseTurtle(expectedAnswerTurtle))
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the complex schema in RDF/XML" in {
      val request = Get(s"$baseApiUrl/v2/resources/$reiseInsHeiligeLandIriEncoded")
        .addHeader(Accept(RdfMediaTypes.`application/rdf+xml`))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerRdfXml = readOrWriteTextFile(
        responseAsString,
        Paths.get("..", "test_data/resourcesR2RV2/BookReiseInsHeiligeLand.rdf"),
        writeTestDataFiles
      )
      assert(parseRdfXml(responseAsString) == parseRdfXml(expectedAnswerRdfXml))
    }

    "perform a resource preview request for the book 'Reise ins Heilige Land' using the complex schema" in {
      val request                = Get(s"$baseApiUrl/v2/resourcespreview/$reiseInsHeiligeLandIriEncoded")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/BookReiseInsHeiligeLandPreview.jsonld"),
          writeTestDataFiles
        )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a resource preview request for a Thing resource using the complex schema" in {
      val request                = Get(s"$baseApiUrl/v2/resourcespreview/$aThingIriEncoded")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/AThing.jsonld"),
          writeTestDataFiles
        )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      collectClientTestData("resource-preview", responseAsString)
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema (specified by an HTTP header) in JSON-LD" in {
      val request = Get(s"$baseApiUrl/v2/resources/$reiseInsHeiligeLandIriEncoded")
        .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.jsonld"),
          writeTestDataFiles
        )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema in Turtle" in {
      val request = Get(s"$baseApiUrl/v2/resources/$reiseInsHeiligeLandIriEncoded")
        .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
        .addHeader(Accept(RdfMediaTypes.`text/turtle`))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerTurtle =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.ttl"),
          writeTestDataFiles
        )
      assert(parseTurtle(responseAsString) == parseTurtle(expectedAnswerTurtle))
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema in RDF/XML" in {
      val request = Get(s"$baseApiUrl/v2/resources/$reiseInsHeiligeLandIriEncoded")
        .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
        .addHeader(Accept(RdfMediaTypes.`application/rdf+xml`))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerRdfXml =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.rdf"),
          writeTestDataFiles
        )
      assert(parseRdfXml(responseAsString) == parseRdfXml(expectedAnswerRdfXml))
    }

    "perform a resource preview request for the book 'Reise ins Heilige Land' using the simple schema (specified by an HTTP header)" in {
      val request = Get(s"$baseApiUrl/v2/resourcespreview/$reiseInsHeiligeLandIriEncoded")
        .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimplePreview.jsonld"),
          writeTestDataFiles
        )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema (specified by a URL parameter)" in {
      val request = Get(
        s"$baseApiUrl/v2/resources/$reiseInsHeiligeLandIriEncoded?${RouteUtilV2.SCHEMA_PARAM}=${RouteUtilV2.SIMPLE_SCHEMA_NAME}"
      )
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.jsonld"),
          writeTestDataFiles
        )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a resource request for the first page of the book '[Das] Narrenschiff (lat.)' using the complex schema" in {
      val iri                    = URLEncoder.encode("http://rdfh.ch/0803/7bbb8e59b703", "UTF-8")
      val request                = Get(s"$baseApiUrl/v2/resources/$iri")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(
        responseAsString,
        Paths.get("..", "test_data/resourcesR2RV2/NarrenschiffFirstPage.jsonld"),
        writeTestDataFiles
      )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a full resource request for a resource with a BCE date property" in {
      val iri                    = URLEncoder.encode("http://rdfh.ch/0001/thing_with_BCE_date", "UTF-8")
      val request                = Get(s"$baseApiUrl/v2/resources/$iri")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(
        responseAsString,
        Paths.get("..", "test_data/resourcesR2RV2/ThingWithBCEDate.jsonld"),
        writeTestDataFiles
      )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with a date property that represents a period going from BCE to CE" in {
      val iri                    = URLEncoder.encode("http://rdfh.ch/0001/thing_with_BCE_date2", "UTF-8")
      val request                = Get(s"$baseApiUrl/v2/resources/$iri")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(
        responseAsString,
        Paths.get("..", "test_data/resourcesR2RV2/ThingWithBCEDate2.jsonld"),
        writeTestDataFiles
      )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with a list value" in {
      val iri                    = URLEncoder.encode("http://rdfh.ch/0001/thing_with_list_value", "UTF-8")
      val request                = Get(s"$baseApiUrl/v2/resources/$iri")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(
        responseAsString,
        Paths.get("..", "test_data/resourcesR2RV2/ThingWithListValue.jsonld"),
        writeTestDataFiles
      )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with a list value (in the simple schema)" in {
      val iri = URLEncoder.encode("http://rdfh.ch/0001/thing_with_list_value", "UTF-8")
      val request = Get(s"$baseApiUrl/v2/resources/$iri")
        .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/ThingWithListValueSimple.jsonld"),
          writeTestDataFiles
        )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with a link (in the complex schema)" in {
      val iri                    = URLEncoder.encode("http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ", "UTF-8")
      val request                = Get(s"$baseApiUrl/v2/resources/$iri")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(
        responseAsString,
        Paths.get("..", "test_data/resourcesR2RV2/ThingWithLinkComplex.jsonld"),
        writeTestDataFiles
      )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with a link (in the simple schema)" in {
      val iri = URLEncoder.encode("http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ", "UTF-8")
      val request = Get(s"$baseApiUrl/v2/resources/$iri")
        .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(
        responseAsString,
        Paths.get("..", "test_data/resourcesR2RV2/ThingWithLinkSimple.jsonld"),
        writeTestDataFiles
      )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with a Text language (in the complex schema)" in {
      val iri                    = URLEncoder.encode("http://rdfh.ch/0001/a-thing-with-text-valuesLanguage", "UTF-8")
      val request                = Get(s"$baseApiUrl/v2/resources/$iri")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/ThingWithTextLangComplex.jsonld"),
          writeTestDataFiles
        )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with a Text language (in the simple schema)" in {
      val iri = URLEncoder.encode("http://rdfh.ch/0001/a-thing-with-text-valuesLanguage", "UTF-8")
      val request = Get(s"$baseApiUrl/v2/resources/$iri")
        .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/ThingWithTextLangSimple.jsonld"),
          writeTestDataFiles
        )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with values of different types" in {
      val iri                    = URLEncoder.encode("http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw", "UTF-8")
      val request                = Get(s"$baseApiUrl/v2/resources/$iri")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/Testding.jsonld"),
          writeTestDataFiles
        )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      collectClientTestData("testding", responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a Thing resource with a link to a ThingPicture resource" in {
      val iri                    = URLEncoder.encode("http://rdfh.ch/0001/a-thing-with-picture", "UTF-8")
      val request                = Get(s"$baseApiUrl/v2/resources/$iri")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(
        responseAsString,
        Paths.get("..", "test_data/resourcesR2RV2/ThingWithPicture.jsonld"),
        writeTestDataFiles
      )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      collectClientTestData("thing-with-picture", responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request with a link to a resource that the user doesn't have permission to see" in {
      val iri                    = URLEncoder.encode("http://rdfh.ch/0001/0JhgKcqoRIeRRG6ownArSw", "UTF-8")
      val request                = Get(s"$baseApiUrl/v2/resources/$iri")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/ThingWithOneHiddenResource.jsonld"),
          writeTestDataFiles
        )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request with a link to a resource that is marked as deleted" in {
      val iri                    = URLEncoder.encode("http://rdfh.ch/0001/l8f8FVEiSCeq9A1p8gBR-A", "UTF-8")
      val request                = Get(s"$baseApiUrl/v2/resources/$iri")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/ThingWithOneDeletedResource.jsonld"),
          writeTestDataFiles
        )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a past version of a resource, using a URL-encoded xsd:dateTimeStamp" in {
      val timestamp              = URLEncoder.encode("2019-02-12T08:05:10.351Z", "UTF-8")
      val request                = Get(s"$baseApiUrl/v2/resources/$aThingWithHistoryIriEncoded?version=$timestamp")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/ThingWithVersionHistory.jsonld"),
          writeTestDataFiles
        )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a full resource request for a past version of a resource, using a Knora ARK timestamp" in {
      val timestamp              = URLEncoder.encode("20190212T080510351Z", "UTF-8")
      val request                = Get(s"$baseApiUrl/v2/resources/$aThingWithHistoryIriEncoded?version=$timestamp")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/ThingWithVersionHistory.jsonld"),
          writeTestDataFiles
        )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "return the complete version history of a resource" in {
      val request                = Get(s"$baseApiUrl/v2/resources/history/$aThingWithHistoryIriEncoded")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/CompleteVersionHistory.jsonld"),
          writeTestDataFiles
        )
      compareJSONLDForResourceHistoryResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "return the version history of a resource within a date range" in {
      val startDate = URLEncoder.encode(Instant.parse("2019-02-08T15:05:11Z").toString, "UTF-8")
      val endDate   = URLEncoder.encode(Instant.parse("2019-02-13T09:05:10Z").toString, "UTF-8")
      val request =
        Get(s"$baseApiUrl/v2/resources/history/$aThingWithHistoryIriEncoded?startDate=$startDate&endDate=$endDate")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(
        responseAsString,
        Paths.get("..", "test_data/resourcesR2RV2/PartialVersionHistory.jsonld"),
        writeTestDataFiles
      )
      compareJSONLDForResourceHistoryResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "return each of the versions of a resource listed in its version history" in {
      val historyRequest                = Get(s"$baseApiUrl/v2/resources/history/$aThingWithHistoryIriEncoded")
      val historyResponse: HttpResponse = singleAwaitingRequest(historyRequest)
      val historyResponseAsString       = responseToString(historyResponse)
      assert(historyResponse.status == StatusCodes.OK, historyResponseAsString)
      val jsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(historyResponseAsString)
      val entries: JsonLDArray           = jsonLDDocument.requireArray("@graph")

      for (entry: JsonLDValue <- entries.value) {
        entry match {
          case jsonLDObject: JsonLDObject =>
            val versionDate: Instant = jsonLDObject.requireDatatypeValueInObject(
              key = OntologyConstants.KnoraApiV2Complex.VersionDate,
              expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
              validationFun = stringFormatter.xsdDateTimeStampToInstant
            )

            val arkTimestamp                  = stringFormatter.formatArkTimestamp(versionDate)
            val versionRequest                = Get(s"$baseApiUrl/v2/resources/$aThingWithHistoryIriEncoded?version=$arkTimestamp")
            val versionResponse: HttpResponse = singleAwaitingRequest(versionRequest)
            val versionResponseAsString       = responseToString(versionResponse)
            assert(versionResponse.status == StatusCodes.OK, versionResponseAsString)
            val expectedAnswerJSONLD =
              readOrWriteTextFile(
                versionResponseAsString,
                Paths.get("..", s"test_data/resourcesR2RV2/ThingWithVersionHistory$arkTimestamp.jsonld"),
                writeTestDataFiles
              )
            compareJSONLDForResourcesResponse(
              expectedJSONLD = expectedAnswerJSONLD,
              receivedJSONLD = versionResponseAsString
            )

          case other => throw AssertionException(s"Expected JsonLDObject, got $other")
        }
      }
    }

    "return all history events for a given resource" in {
      val resourceIri = URLEncoder.encode("http://rdfh.ch/0001/a-thing-picture", "UTF-8")
      val resourceHistoryRequest = Get(s"$baseApiUrl/v2/resources/resourceHistoryEvents/$resourceIri")
        .addCredentials(BasicHttpCredentials(SharedTestDataADM.anythingAdminUser.email, password))
      val resourceHistoryResponse: HttpResponse = singleAwaitingRequest(resourceHistoryRequest)
      val historyResponseAsString               = responseToString(resourceHistoryResponse)
      assert(resourceHistoryResponse.status == StatusCodes.OK, historyResponseAsString)
    }

    "return entire resource and value history events for a given project" in {
      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/0001", "UTF-8")
      val projectHistoryRequest = Get(s"$baseApiUrl/v2/resources/projectHistoryEvents/$projectIri")
        .addCredentials(BasicHttpCredentials(SharedTestDataADM.anythingAdminUser.email, password))
      val projectHistoryResponse: HttpResponse = singleAwaitingRequest(projectHistoryRequest)
      val historyResponseAsString              = responseToString(projectHistoryResponse)
      assert(projectHistoryResponse.status == StatusCodes.OK, historyResponseAsString)
    }

    "return a graph of resources reachable via links from/to a given resource" in {
      val request =
        Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?direction=both")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)

      collectClientTestData("resource-graph", responseAsString)

      assert(response.status == StatusCodes.OK, responseAsString)
      val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)

      val expectedAnswerJSONLD = readOrWriteTextFile(
        responseAsString,
        Paths.get("..", "test_data/resourcesR2RV2/ThingGraphBoth.jsonld"),
        writeTestDataFiles
      )
      val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)

      assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
    }

    "return a graph of resources reachable via links from a given resource" in {
      val request =
        Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?direction=outbound")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)

      val expectedAnswerJSONLD = readOrWriteTextFile(
        responseAsString,
        Paths.get("..", "test_data/resourcesR2RV2/ThingGraphOutbound.jsonld"),
        writeTestDataFiles
      )
      val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)

      assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
    }

    "return a graph of resources reachable via links to a given resource" in {
      val request =
        Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?direction=inbound")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)

      val expectedAnswerJSONLD = readOrWriteTextFile(
        responseAsString,
        Paths.get("..", "test_data/resourcesR2RV2/ThingGraphInbound.jsonld"),
        writeTestDataFiles
      )
      val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)

      assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
    }

    "return a graph of resources reachable via links to/from a given resource, excluding a specified property" in {
      val request = Get(
        s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?direction=both&excludeProperty=${URLEncoder
            .encode("http://0.0.0.0:3333/ontology/0001/anything/v2#isPartOfOtherThing", "UTF-8")}"
      )
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)

      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/ThingGraphBothWithExcludedProp.jsonld"),
          writeTestDataFiles
        )
      val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)

      assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
    }

    "return a graph of resources reachable via links from a given resource, specifying search depth" in {
      val request =
        Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?direction=both&depth=2")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)

      val expectedAnswerJSONLD =
        readOrWriteTextFile(
          responseAsString,
          Paths.get("..", "test_data/resourcesR2RV2/ThingGraphBothWithDepth.jsonld"),
          writeTestDataFiles
        )
      val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)

      assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
    }

    "not accept a graph request with an invalid direction" in {
      val request =
        Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?direction=foo")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.BadRequest, responseAsString)
    }

    "not accept a graph request with an invalid depth (< 1)" in {
      val request                = Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?depth=0")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.BadRequest, responseAsString)
    }

    "not accept a graph request with an invalid depth (> max)" in {
      val request = Get(
        s"$baseApiUrl/v2/graph/${URLEncoder
            .encode("http://rdfh.ch/0001/start", "UTF-8")}?depth=${appConfig.v2.graphRoute.maxGraphBreadth + 1}"
      )
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.BadRequest, responseAsString)
    }

    "not accept a graph request with an invalid property to exclude" in {
      val request =
        Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/0001/start", "UTF-8")}?excludeProperty=foo")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.BadRequest, responseAsString)
    }

    "return resources from a project" in {
      val resourceClass   = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book", "UTF-8")
      val orderByProperty = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#title", "UTF-8")
      val request =
        Get(s"$baseApiUrl/v2/resources?resourceClass=$resourceClass&orderByProperty=$orderByProperty&page=0")
          .addHeader(new ProjectHeader(SharedTestDataADM.incunabulaProject.id)) ~> addCredentials(
          BasicHttpCredentials(SharedTestDataADM.incunabulaProjectAdminUser.email, password)
        )
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString       = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(
        responseAsString,
        Paths.get("..", "test_data/resourcesR2RV2/BooksFromIncunabula.jsonld"),
        writeTestDataFiles
      )
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "create a resource with values" in {
      val createResourceWithValues: String =
        """{
          |  "@type" : "anything:Thing",
          |  "anything:hasBoolean" : {
          |    "@type" : "knora-api:BooleanValue",
          |    "knora-api:booleanValueAsBoolean" : true
          |  },
          |  "anything:hasColor" : {
          |    "@type" : "knora-api:ColorValue",
          |    "knora-api:colorValueAsColor" : "#ff3333"
          |  },
          |  "anything:hasDate" : {
          |    "@type" : "knora-api:DateValue",
          |    "knora-api:dateValueHasCalendar" : "GREGORIAN",
          |    "knora-api:dateValueHasEndEra" : "CE",
          |    "knora-api:dateValueHasEndYear" : 1489,
          |    "knora-api:dateValueHasStartEra" : "CE",
          |    "knora-api:dateValueHasStartYear" : 1489
          |  },
          |  "anything:hasDecimal" : {
          |    "@type" : "knora-api:DecimalValue",
          |    "knora-api:decimalValueAsDecimal" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "100000000000000.000000000000001"
          |    }
          |  },
          |  "anything:hasGeometry" : {
          |    "@type" : "knora-api:GeomValue",
          |    "knora-api:geometryValueAsGeometry" : "{\"status\":\"active\",\"lineColor\":\"#ff3333\",\"lineWidth\":2,\"points\":[{\"x\":0.08098591549295775,\"y\":0.16741071428571427},{\"x\":0.7394366197183099,\"y\":0.7299107142857143}],\"type\":\"rectangle\",\"original_index\":0}"
          |  },
          |  "anything:hasGeoname" : {
          |    "@type" : "knora-api:GeonameValue",
          |    "knora-api:geonameValueAsGeonameCode" : "2661604"
          |  },
          |  "anything:hasInteger" : [ {
          |    "@type" : "knora-api:IntValue",
          |    "knora-api:hasPermissions" : "CR knora-admin:Creator|V http://rdfh.ch/groups/0001/thing-searcher",
          |    "knora-api:intValueAsInt" : 5,
          |    "knora-api:valueHasComment" : "this is the number five"
          |  }, {
          |    "@type" : "knora-api:IntValue",
          |    "knora-api:intValueAsInt" : 6
          |  } ],
          |  "anything:hasInterval" : {
          |    "@type" : "knora-api:IntervalValue",
          |    "knora-api:intervalValueHasEnd" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "3.4"
          |    },
          |    "knora-api:intervalValueHasStart" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "1.2"
          |    }
          |  },
          |  "anything:hasTimeStamp" : {
          |    "@type" : "knora-api:TimeValue",
          |    "knora-api:timeValueAsTimeStamp" : {
          |      "@type" : "xsd:dateTimeStamp",
          |      "@value" : "2020-01-24T08:47:10.307068Z"
          |    }
          |  },
          |  "anything:hasListItem" : {
          |    "@type" : "knora-api:ListValue",
          |    "knora-api:listValueAsListNode" : {
          |      "@id" : "http://rdfh.ch/lists/0001/treeList03"
          |    }
          |  },
          |  "anything:hasOtherThingValue" : {
          |    "@type" : "knora-api:LinkValue",
          |    "knora-api:linkValueHasTargetIri" : {
          |      "@id" : "http://rdfh.ch/0001/a-thing"
          |    }
          |  },
          |  "anything:hasRichtext" : {
          |    "@type" : "knora-api:TextValue",
          |    "knora-api:textValueAsXml" : "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text><p><strong>this is</strong> text</p> with standoff</text>",
          |    "knora-api:textValueHasMapping" : {
          |      "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping"
          |    }
          |  },
          |  "anything:hasText" : {
          |    "@type" : "knora-api:TextValue",
          |    "knora-api:valueAsString" : "this is text without standoff"
          |  },
          |  "anything:hasUri" : {
          |    "@type" : "knora-api:UriValue",
          |    "knora-api:uriValueAsUri" : {
          |      "@type" : "xsd:anyURI",
          |      "@value" : "https://www.knora.org"
          |    }
          |  },
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "rdfs:label" : "test thing",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
          |  }
          |}""".stripMargin

      collectClientTestData("create-resource-with-values-request", createResourceWithValues)

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, createResourceWithValues)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)

      // Request the newly created resource in the complex schema, and check that it matches the ontology.
      val resourceComplexGetRequest = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}"
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val resourceComplexGetResponse: HttpResponse = singleAwaitingRequest(resourceComplexGetRequest)
      val resourceComplexGetResponseAsString       = responseToString(resourceComplexGetResponse)

      instanceChecker.check(
        instanceResponse = resourceComplexGetResponseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )

      // Request the newly created resource in the simple schema, and check that it matches the ontology.
      val resourceSimpleGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}")
        .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)
      )
      val resourceSimpleGetResponse: HttpResponse = singleAwaitingRequest(resourceSimpleGetRequest)
      val resourceSimpleGetResponseAsString       = responseToString(resourceSimpleGetResponse)

      instanceChecker.check(
        instanceResponse = resourceSimpleGetResponseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )

      // Check that the text value with standoff is correct in the simple schema.
      val resourceSimpleAsJsonLD: JsonLDDocument = JsonLDUtil.parseJsonLD(resourceSimpleGetResponseAsString)
      val text: String =
        resourceSimpleAsJsonLD.body.requireString("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasRichtext")
      assert(text == "this is text with standoff")
    }

    "create a resource and a property with references to an external ontology (FOAF)" in {
      val createResourceWithRefToFoaf: String =
        """{
          |  "@type" : "freetest:FreeTestSubClassOfFoafPerson",
          |  "freetest:hasFoafName" : {
          |    "@type" : "knora-api:TextValue",
          |    "knora-api:valueAsString" : "this is a foaf name"
          |  },
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "rdfs:label" : "Test foaf Person",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "freetest" : "http://0.0.0.0:3333/ontology/0001/freetest/v2#"
          |  }
          |}""".stripMargin

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, createResourceWithRefToFoaf)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)

      // Request the newly created resource in the complex schema, and check that it matches the ontology.
      val resourceComplexGetRequest = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}"
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val resourceComplexGetResponse: HttpResponse = singleAwaitingRequest(resourceComplexGetRequest)
      val resourceComplexGetResponseAsString       = responseToString(resourceComplexGetResponse)

      instanceChecker.check(
        instanceResponse = resourceComplexGetResponseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/freetest/v2#FreeTestSubClassOfFoafPerson".toSmartIri,
        knoraRouteGet = doGetRequest
      )

      // Request the newly created resource in the simple schema, and check that it matches the ontology.
      val resourceSimpleGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}")
        .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)
      )
      val resourceSimpleGetResponse: HttpResponse = singleAwaitingRequest(resourceSimpleGetRequest)
      val resourceSimpleGetResponseAsString       = responseToString(resourceSimpleGetResponse)

      instanceChecker.check(
        instanceResponse = resourceSimpleGetResponseAsString,
        expectedClassIri =
          "http://0.0.0.0:3333/ontology/0001/freetest/simple/v2#FreeTestSubClassOfFoafPerson".toSmartIri,
        knoraRouteGet = doGetRequest
      )

      // Check that the value is correct in the simple schema.
      val resourceSimpleAsJsonLD: JsonLDDocument = JsonLDUtil.parseJsonLD(resourceSimpleGetResponseAsString)
      val foafName: String =
        resourceSimpleAsJsonLD.body.requireString("http://0.0.0.0:3333/ontology/0001/freetest/simple/v2#hasFoafName")
      assert(foafName == "this is a foaf name")
    }

    "create a resource whose label contains a Unicode escape and quotation marks" in {
      val jsonLDEntity: String =
        FileUtil.readTextFile(Paths.get("..", "test_data/resourcesR2RV2/ThingWithUnicodeEscape.jsonld"))
      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)
    }

    "create a resource with a custom creation date" in {
      val creationDate: Instant = SharedTestDataADM.customResourceCreationDate

      val jsonLDEntity: String =
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true
           |  },
           |  "rdfs:label" : "test thing",
           |  "knora-api:creationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$creationDate"
           |  },
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      collectClientTestData("create-resource-with-custom-creation-date", jsonLDEntity)

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)

      val savedCreationDate: Instant = responseJsonDoc.body.requireDatatypeValueInObject(
        key = OntologyConstants.KnoraApiV2Complex.CreationDate,
        expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
        validationFun = stringFormatter.xsdDateTimeStampToInstant
      )

      assert(savedCreationDate == creationDate)
    }

    def createResourceWithCustomIRI(iri: IRI): String =
      s"""{
         |  "@id" : "$iri",
         |  "@type" : "anything:Thing",
         |  "knora-api:attachedToProject" : {
         |    "@id" : "http://rdfh.ch/projects/0001"
         |  },
         |  "anything:hasBoolean" : {
         |    "@type" : "knora-api:BooleanValue",
         |    "knora-api:booleanValueAsBoolean" : true
         |  },
         |  "rdfs:label" : "test thing",
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
         |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
         |  }
         |}""".stripMargin

    "create a resource with a custom IRI" in {
      val customIRI: IRI = SharedTestDataADM.customResourceIRI
      val jsonLDEntity   = createResourceWithCustomIRI(customIRI)

      collectClientTestData("create-resource-with-custom-IRI-request", jsonLDEntity)

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri == customIRI)
    }

    "not create a resource with an invalid custom IRI" in {
      val customIRI: IRI = "http://rdfh.ch/invalid-resource-IRI"
      val jsonLDEntity   = createResourceWithCustomIRI(customIRI)
      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest, response.toString)
    }

    "not create a resource with a custom IRI containing the wrong project code" in {
      val customIRI: IRI = "http://rdfh.ch/0803/a-thing-with-IRI"
      val jsonLDEntity   = createResourceWithCustomIRI(customIRI)
      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest, response.toString)
    }

    "return a DuplicateValueException during resource creation when the supplied resource IRI is not unique" in {
      // duplicate resource IRI

      val params =
        s"""{
           |  "@id" : "$aThingIri",
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true
           |  },
           |  "rdfs:label" : "test thing with duplicate iri",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val request =
        Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password)
        )
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest, response.toString)

      val errorMessage: String = Await.result(Unmarshal(response.entity).to[String], 1.second)
      val invalidIri: Boolean =
        errorMessage.contains(s"IRI: 'http://rdfh.ch/0001/a-thing' already exists, try another one.")
      invalidIri should be(true)
    }

    def createResourceWithCustomValueIRI(valueIRI: IRI): String =
      s"""{
         |  "@type" : "anything:Thing",
         |  "knora-api:attachedToProject" : {
         |    "@id" : "http://rdfh.ch/projects/0001"
         |  },
         |  "anything:hasBoolean" : {
         |    "@id" : "$valueIRI",
         |    "@type" : "knora-api:BooleanValue",
         |    "knora-api:booleanValueAsBoolean" : true
         |  },
         |  "rdfs:label" : "test thing with value IRI",
         |  "@context" : {
         |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
         |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
         |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
         |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
         |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
         |  }
         |}""".stripMargin

    "create a resource with random IRI and a custom value IRI" in {
      val customValueIRI: IRI = SharedTestDataADM.customValueIRI
      val jsonLDEntity        = createResourceWithCustomValueIRI(customValueIRI)

      collectClientTestData("create-resource-with-custom-value-IRI-request", jsonLDEntity)

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseAsJsonLD(request)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)

      // Request the newly created resource.
      val resourceGetRequest = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}"
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))

      // Get the value from the response.
      val resourceGetResponseAsJsonLD = getResponseAsJsonLD(resourceGetRequest)
      val valueIri: IRI = resourceGetResponseAsJsonLD.body
        .requireObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
        .requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(valueIri == customValueIRI)
    }

    "create a resource with random resource IRI and custom value UUIDs" in {
      val customValueUUID = SharedTestDataADM.customValueUUID

      val jsonLDEntity =
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true,
           |    "knora-api:valueHasUUID" : "$customValueUUID"
           |  },
           |  "rdfs:label" : "test thing",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           | }
           |}""".stripMargin

      collectClientTestData("create-resource-with-custom-value-UUID-request", jsonLDEntity)

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseAsJsonLD(request)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)

      // Request the newly created resource.
      val resourceGetRequest = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}"
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))

      // Get the value from the response.
      val resourceGetResponseAsJsonLD = getResponseAsJsonLD(resourceGetRequest)
      val valueUUID = resourceGetResponseAsJsonLD.body
        .requireObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
        .requireString(OntologyConstants.KnoraApiV2Complex.ValueHasUUID)
      assert(valueUUID == customValueUUID)

    }

    "create a resource with random resource IRI and custom value creation date" in {
      val creationDate: Instant = SharedTestDataADM.customValueCreationDate

      val jsonLDEntity =
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : false,
           |    "knora-api:valueCreationDate" : {
           |        "@type" : "xsd:dateTimeStamp",
           |        "@value" : "$creationDate"
           |    }
           |  },
           |  "rdfs:label" : "test thing with value has creation date",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           | }
           |}""".stripMargin

      collectClientTestData("create-resource-with-custom-value-creationDate-request", jsonLDEntity)

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseAsJsonLD(request)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)

      // Request the newly created resource.
      val resourceGetRequest = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}"
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val resourceGetResponseAsString = getResponseAsString(resourceGetRequest)

      // Get the value from the response.
      val resourceGetResponseAsJsonLD = JsonLDUtil.parseJsonLD(resourceGetResponseAsString)
      val savedCreationDate: Instant = resourceGetResponseAsJsonLD.body
        .requireObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
        .requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.ValueCreationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )
      assert(savedCreationDate == creationDate)

    }

    "create a resource with custom resource IRI, creation date, and a value with custom value IRI and UUID" in {
      val customResourceIRI: IRI      = SharedTestDataADM.customResourceIRI_resourceWithValues
      val customCreationDate: Instant = Instant.parse("2019-01-09T15:45:54.502951Z")
      val customValueIRI: IRI         = SharedTestDataADM.customValueIRI_withResourceIriAndValueIRIAndValueUUID
      val customValueUUID             = SharedTestDataADM.customValueUUID

      val jsonLDEntity =
        s"""{
           |   "@id" : "$customResourceIRI",
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@id": "$customValueIRI",
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true,
           |    "knora-api:valueHasUUID" : "$customValueUUID"
           |  },
           |  "rdfs:label" : "test thing",
           |  "knora-api:creationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$customCreationDate"
           |  },
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           | }
           |}""".stripMargin

      collectClientTestData(
        "create-resource-with-custom-resourceIRI-creationDate-ValueIri-ValueUUID-request",
        jsonLDEntity
      )

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseAsJsonLD(request)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri == customResourceIRI)

      // Request the newly created resource.
      val resourceGetRequest = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}"
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val resourceGetResponseAsString = getResponseAsString(resourceGetRequest)

      // Get the value from the response.
      val resourceGetResponseAsJsonLD = JsonLDUtil.parseJsonLD(resourceGetResponseAsString)
      val valueIri: IRI = resourceGetResponseAsJsonLD.body
        .requireObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
        .requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(valueIri == customValueIRI)

      val valueUUID = resourceGetResponseAsJsonLD.body
        .requireObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
        .requireString(OntologyConstants.KnoraApiV2Complex.ValueHasUUID)
      assert(valueUUID == customValueUUID)

      val savedCreationDate: Instant = responseJsonDoc.body.requireDatatypeValueInObject(
        key = OntologyConstants.KnoraApiV2Complex.CreationDate,
        expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
        validationFun = stringFormatter.xsdDateTimeStampToInstant
      )

      assert(savedCreationDate == customCreationDate)

      // when no custom creation date is given to the value, it should have the same creation date as the resource
      val savedValueCreationDate: Instant = resourceGetResponseAsJsonLD.body
        .requireObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasBoolean")
        .requireDatatypeValueInObject(
          key = OntologyConstants.KnoraApiV2Complex.ValueCreationDate,
          expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
          validationFun = stringFormatter.xsdDateTimeStampToInstant
        )
      assert(savedValueCreationDate == customCreationDate)

    }

    "create a resource as another user" in {
      val jsonLDEntity =
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true
           |  },
           |  "rdfs:label" : "test thing",
           |  "knora-api:attachedToUser" : {
           |    "@id" : "${SharedTestDataADM.anythingUser1.id}"
           |  },
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      collectClientTestData("create-resource-as-user", jsonLDEntity)

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.anythingAdminUser.email, password))
      val responseJsonDoc: JsonLDDocument = getResponseAsJsonLD(request)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)
      val savedAttachedToUser: IRI =
        responseJsonDoc.body.requireIriInObject(
          OntologyConstants.KnoraApiV2Complex.AttachedToUser,
          stringFormatter.validateAndEscapeIri
        )
      assert(savedAttachedToUser == SharedTestDataADM.anythingUser1.id)
    }

    "not create a resource as another user if the requesting user is an ordinary user" in {
      val jsonLDEntity =
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "anything:hasBoolean" : {
           |    "@type" : "knora-api:BooleanValue",
           |    "knora-api:booleanValueAsBoolean" : true
           |  },
           |  "rdfs:label" : "test thing",
           |  "knora-api:attachedToUser" : {
           |    "@id" : "${SharedTestDataADM.anythingUser1.id}"
           |  },
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.anythingUser2.email, password))
      val response = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.Forbidden, "should be forbidden")
    }

    "create a resource containing escaped text" in {
      val jsonLDEntity =
        FileUtil.readTextFile(Paths.get("..", "test_data/resourcesR2RV2/CreateResourceWithEscape.jsonld"))
      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val responseJsonDoc: JsonLDDocument = getResponseAsJsonLD(request)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)
    }

    "update the metadata of a resource" in {
      val newLabel            = "test thing with modified label"
      val newPermissions      = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:ProjectMember"
      val newModificationDate = Instant.now.plus(java.time.Duration.ofDays(1))

      val jsonLDEntity =
        s"""|{
            |  "@id" : "$aThingIri",
            |  "@type" : "anything:Thing",
            |  "rdfs:label" : "$newLabel",
            |  "knora-api:hasPermissions" : "$newPermissions",
            |  "knora-api:newModificationDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "$newModificationDate"
            |  },
            |  "@context" : {
            |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin

      collectClientTestData("update-resource-metadata-request", jsonLDEntity)

      val updateRequest = Put(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val updateResponseAsString: String = getResponseAsString(updateRequest)
      assert(
        JsonParser(updateResponseAsString) == JsonParser(
          updateResourceMetadataResponse(
            resourceIri = aThingIri,
            maybeNewLabel = newLabel,
            newLastModificationDate = newModificationDate,
            maybeNewPermissions = newPermissions
          )
        )
      )

      collectClientTestData("update-resource-metadata-response", updateResponseAsString)

      val previewRequest = Get(
        s"$baseApiUrl/v2/resourcespreview/$aThingIriEncoded"
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))

      val previewJsonLD        = getResponseAsJsonLD(previewRequest)
      val updatedLabel: String = previewJsonLD.requireString(OntologyConstants.Rdfs.Label)
      assert(updatedLabel == newLabel)
      val updatedPermissions: String = previewJsonLD.requireString(OntologyConstants.KnoraApiV2Complex.HasPermissions)
      assert(
        PermissionUtilADM.parsePermissions(updatedPermissions) == PermissionUtilADM.parsePermissions(newPermissions)
      )

      val lastModificationDate: Instant = previewJsonLD.requireDatatypeValueInObject(
        key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
        expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
        validationFun = stringFormatter.xsdDateTimeStampToInstant
      )

      assert(lastModificationDate == newModificationDate)
      aThingLastModificationDate = newModificationDate
    }

    "update the metadata of a resource that has a last modification date" in {
      val newLabel            = "test thing with modified label again"
      val newPermissions      = "CR knora-admin:ProjectMember|V knora-admin:ProjectMember"
      val newModificationDate = Instant.now.plus(java.time.Duration.ofDays(1))

      val jsonLDEntity =
        s"""|{
            |  "@id" : "$aThingIri",
            |  "@type" : "anything:Thing",
            |  "rdfs:label" : "$newLabel",
            |  "knora-api:hasPermissions" : "$newPermissions",
            |  "knora-api:lastModificationDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "$aThingLastModificationDate"
            |  },
            |  "knora-api:newModificationDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "$newModificationDate"
            |  },
            |  "@context" : {
            |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin

      collectClientTestData("update-resource-metadata-request-with-last-mod-date", jsonLDEntity)

      val updateRequest = Put(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val updateResponse: HttpResponse   = singleAwaitingRequest(updateRequest)
      val updateResponseAsString: String = responseToString(updateResponse)
      assert(updateResponse.status == StatusCodes.OK, updateResponseAsString)
      assert(
        JsonParser(updateResponseAsString) == JsonParser(
          updateResourceMetadataResponse(
            resourceIri = aThingIri,
            maybeNewLabel = newLabel,
            newLastModificationDate = newModificationDate,
            maybeNewPermissions = newPermissions
          )
        )
      )

      collectClientTestData("update-resource-metadata-response-with-last-mod-date", updateResponseAsString)

      val previewRequest = Get(
        s"$baseApiUrl/v2/resourcespreview/$aThingIriEncoded"
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val previewResponse: HttpResponse = singleAwaitingRequest(previewRequest)
      val previewResponseAsString       = responseToString(previewResponse)
      assert(previewResponse.status == StatusCodes.OK, previewResponseAsString)

      val previewJsonLD        = JsonLDUtil.parseJsonLD(previewResponseAsString)
      val updatedLabel: String = previewJsonLD.requireString(OntologyConstants.Rdfs.Label)
      assert(updatedLabel == newLabel)
      val updatedPermissions: String = previewJsonLD.requireString(OntologyConstants.KnoraApiV2Complex.HasPermissions)
      assert(
        PermissionUtilADM.parsePermissions(updatedPermissions) == PermissionUtilADM.parsePermissions(newPermissions)
      )

      val lastModificationDate: Instant = previewJsonLD.requireDatatypeValueInObject(
        key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
        expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
        validationFun = stringFormatter.xsdDateTimeStampToInstant
      )

      assert(lastModificationDate == newModificationDate)
      aThingLastModificationDate = newModificationDate
    }

    "mark a resource as deleted" in {
      val jsonLDEntity =
        s"""|{
            |  "@id" : "$aThingIri",
            |  "@type" : "anything:Thing",
            |  "knora-api:lastModificationDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "$aThingLastModificationDate"
            |  },
            |  "knora-api:deleteComment" : "This resource is too boring.",
            |  "@context" : {
            |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin

      collectClientTestData("delete-resource-request", jsonLDEntity)

      val updateRequest = Post(
        s"$baseApiUrl/v2/resources/delete",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val updateResponse: HttpResponse   = singleAwaitingRequest(updateRequest)
      val updateResponseAsString: String = responseToString(updateResponse)
      assert(updateResponse.status == StatusCodes.OK, updateResponseAsString)
      assert(JsonParser(updateResponseAsString) == JsonParser(successResponse("Resource marked as deleted")))

      collectClientTestData("delete-resource-response", updateResponseAsString)

      val previewRequest = Get(
        s"$baseApiUrl/v2/resourcespreview/$aThingIriEncoded"
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val previewResponse: HttpResponse = singleAwaitingRequest(previewRequest)
      previewResponse.status should equal(StatusCodes.OK)

      val previewResponseAsString = responseToString(previewResponse)
      val previewJsonLD           = JsonLDUtil.parseJsonLD(previewResponseAsString)
      val responseIsDeleted       = previewJsonLD.requireBoolean(OntologyConstants.KnoraApiV2Complex.IsDeleted)
      responseIsDeleted should equal(true)
      val responseType = previewJsonLD.requireString("@type")
      responseType should equal(OntologyConstants.KnoraApiV2Complex.DeletedResource)

      collectClientTestData("deleted-resource-preview-response", previewResponseAsString)
    }

    "mark a resource as deleted, supplying a custom delete date" in {
      val resourceIri = "http://rdfh.ch/0001/5IEswyQFQp2bxXDrOyEfEA"
      val deleteDate  = Instant.now

      val jsonLDEntity =
        s"""|{
            |  "@id" : "$resourceIri",
            |  "@type" : "anything:Thing",
            |  "knora-api:deleteComment" : "This resource is too boring.",
            |  "knora-api:deleteDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "$deleteDate"
            |  },
            |  "@context" : {
            |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin

      collectClientTestData("delete-resource-with-custom-delete-date-request", jsonLDEntity)

      val updateRequest = Post(
        s"$baseApiUrl/v2/resources/delete",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.superUser.email, password))
      val updateResponse: HttpResponse   = singleAwaitingRequest(updateRequest)
      val updateResponseAsString: String = responseToString(updateResponse)
      assert(updateResponse.status == StatusCodes.OK, updateResponseAsString)
      assert(JsonParser(updateResponseAsString) == JsonParser(successResponse("Resource marked as deleted")))

      val previewRequest = Get(
        s"$baseApiUrl/v2/resourcespreview/${URLEncoder.encode(resourceIri, "UTF-8")}"
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val previewResponse: HttpResponse = singleAwaitingRequest(previewRequest)
      val previewResponseAsString       = responseToString(previewResponse)
      previewResponse.status should equal(StatusCodes.OK)

      val previewJsonLD     = JsonLDUtil.parseJsonLD(previewResponseAsString)
      val responseIsDeleted = previewJsonLD.requireBoolean(OntologyConstants.KnoraApiV2Complex.IsDeleted)
      responseIsDeleted should equal(true)
      val responseType = previewJsonLD.requireString("@type")
      responseType should equal(OntologyConstants.KnoraApiV2Complex.DeletedResource)
      val responseDeleteDate = previewJsonLD
        .requireObject(OntologyConstants.KnoraApiV2Complex.DeleteDate)
        .requireString("@value")
      responseDeleteDate should equal(deleteDate.toString)
    }

    "create a resource with a large text containing a lot of markup (32849 words, 6738 standoff tags)" ignore { // uses too much memory for GitHub CI
      // Create a resource containing the text of Hamlet.

      val hamletXml = FileUtil.readTextFile(Paths.get("..", "test_data/resourcesR2RV2/hamlet.xml"))

      val jsonLDEntity =
        s"""{
           |  "@type" : "anything:Thing",
           |  "anything:hasRichtext" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:textValueAsXml" : ${stringFormatter.toJsonEncodedString(hamletXml)},
           |    "knora-api:textValueHasMapping" : {
           |      "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping"
           |    }
           |  },
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "rdfs:label" : "test thing",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val resourceCreateRequest = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val resourceCreateResponseAsJsonLD: JsonLDDocument = getResponseAsJsonLD(resourceCreateRequest)
      val resourceIri: IRI =
        resourceCreateResponseAsJsonLD.body.requireStringWithValidation(
          JsonLDKeywords.ID,
          stringFormatter.validateAndEscapeIri
        )
      assert(resourceIri.toSmartIri.isKnoraDataIri)
      hamletResourceIri.set(resourceIri)
    }

    "read the large text and its markup as XML, and check that it matches the original XML" ignore { // depends on previous test
      val hamletXml = FileUtil.readTextFile(Paths.get("..", "test_data/resourcesR2RV2/hamlet.xml"))

      // Request the newly created resource.
      val resourceGetRequest = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode(hamletResourceIri.get, "UTF-8")}"
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val resourceGetResponseAsString = getResponseAsString(resourceGetRequest)

      // Check that the response matches the ontology.
      instanceChecker.check(
        instanceResponse = resourceGetResponseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )

      // Get the XML from the response.
      val resourceGetResponseAsJsonLD = JsonLDUtil.parseJsonLD(resourceGetResponseAsString)
      val xmlFromResponse: String = resourceGetResponseAsJsonLD.body
        .requireObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext")
        .requireString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml)

      // Compare it to the original XML.
      val xmlDiff: Diff =
        DiffBuilder.compare(Input.fromString(hamletXml)).withTest(Input.fromString(xmlFromResponse)).build()
      xmlDiff.hasDifferences should be(false)
    }

    "read the large text without its markup, and get the markup separately as pages of standoff" ignore { // depends on previous test
      // Get the resource without markup.
      val resourceGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(hamletResourceIri.get, "UTF-8")}")
        .addHeader(new MarkupHeader(RouteUtilV2.MARKUP_STANDOFF)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password)
      )
      val resourceGetResponse: HttpResponse = singleAwaitingRequest(resourceGetRequest)
      val resourceGetResponseAsString       = responseToString(resourceGetResponse)

      // Check that the response matches the ontology.
      instanceChecker.check(
        instanceResponse = resourceGetResponseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )

      // Get the standoff markup separately.
      val resourceGetResponseAsJsonLD = JsonLDUtil.parseJsonLD(resourceGetResponseAsString)
      val textValue: JsonLDObject =
        resourceGetResponseAsJsonLD.body.requireObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext")
      val maybeTextValueAsXml: Option[String] =
        textValue.maybeString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml)
      assert(maybeTextValueAsXml.isEmpty)
      val textValueIri: IRI =
        textValue.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)

      val resourceIriEncoded: IRI  = URLEncoder.encode(hamletResourceIri.get, "UTF-8")
      val textValueIriEncoded: IRI = URLEncoder.encode(textValueIri, "UTF-8")

      val standoffBuffer: ArrayBuffer[JsonLDObject] = ArrayBuffer.empty
      var offset: Int                               = 0
      var hasMoreStandoff: Boolean                  = true

      while (hasMoreStandoff) {
        // Get a page of standoff.

        val standoffGetRequest = Get(
          s"$baseApiUrl/v2/standoff/$resourceIriEncoded/$textValueIriEncoded/$offset"
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
        val standoffGetResponse: HttpResponse         = singleAwaitingRequest(standoffGetRequest)
        val standoffGetResponseAsJsonLD: JsonLDObject = responseToJsonLDDocument(standoffGetResponse).body

        val standoff: Seq[JsonLDValue] =
          standoffGetResponseAsJsonLD.maybeArray(JsonLDKeywords.GRAPH).map(_.value).getOrElse(Seq.empty)

        val standoffAsJsonLDObjects: Seq[JsonLDObject] = standoff.map {
          case jsonLDObject: JsonLDObject => jsonLDObject
          case other                      => throw AssertionException(s"Expected JsonLDObject, got $other")
        }

        standoffBuffer.appendAll(standoffAsJsonLDObjects)

        standoffGetResponseAsJsonLD.maybeInt(OntologyConstants.KnoraApiV2Complex.NextStandoffStartIndex) match {
          case Some(nextOffset) => offset = nextOffset
          case None             => hasMoreStandoff = false
        }
      }

      assert(standoffBuffer.length == 6738)

      // Check the standoff tags to make sure they match the ontology.

      for (jsonLDObject <- standoffBuffer) {
        val docForValidation = JsonLDDocument(body = jsonLDObject).toCompactString()

        instanceChecker.check(
          instanceResponse = docForValidation,
          expectedClassIri =
            jsonLDObject.requireStringWithValidation(JsonLDKeywords.TYPE, stringFormatter.toSmartIriWithErr),
          knoraRouteGet = doGetRequest
        )
      }
    }

    "erase a resource" in {
      val resourceLastModificationDate = Instant.parse("2019-02-13T09:05:10Z")

      val jsonLDEntity =
        s"""|{
            |  "@id" : "$aThingWithHistoryIri",
            |  "@type" : "anything:Thing",
            |  "knora-api:lastModificationDate" : {
            |    "@type" : "xsd:dateTimeStamp",
            |    "@value" : "$resourceLastModificationDate"
            |  },
            |  "@context" : {
            |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
            |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
            |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
            |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
            |  }
            |}""".stripMargin

      collectClientTestData("erase-resource-request", jsonLDEntity)

      val updateRequest = Post(
        s"$baseApiUrl/v2/resources/erase",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.anythingAdminUser.email, password))
      val updateResponse: HttpResponse = singleAwaitingRequest(updateRequest)
      val updateResponseAsString       = responseToString(updateResponse)
      assert(updateResponse.status == StatusCodes.OK, updateResponseAsString)

      val previewRequest = Get(
        s"$baseApiUrl/v2/resourcespreview/$aThingWithHistoryIriEncoded"
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val previewResponse: HttpResponse = singleAwaitingRequest(previewRequest)
      val previewResponseAsString       = responseToString(previewResponse)
      assert(previewResponse.status == StatusCodes.NotFound, previewResponseAsString)
    }

    "create a resource containing a text value with a standoff link" in {
      val jsonLDEntity =
        """{
          |  "@type": "anything:Thing",
          |  "anything:hasText": {
          |    "@type": "knora-api:TextValue",
          |    "knora-api:textValueAsXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text>\n   This text links to another <a class=\"salsah-link\" href=\"http://rdfh.ch/0001/another-thing\">resource</a>.\n</text>",
          |    "knora-api:textValueHasMapping": {
          |      "@id": "http://rdfh.ch/standoff/mappings/StandardMapping"
          |    }
          |  },
          |  "knora-api:attachedToProject": {
          |    "@id": "http://rdfh.ch/projects/0001"
          |  },
          |  "rdfs:label": "obj_inst1",
          |  "@context": {
          |    "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
          |    "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
          |    "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
          |  }
          |}""".stripMargin

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)

      // Request the newly created resource in the complex schema, and check that it matches the ontology.
      val resourceComplexGetRequest = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}"
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val resourceComplexGetResponse: HttpResponse = singleAwaitingRequest(resourceComplexGetRequest)
      val resourceComplexGetResponseAsString       = responseToString(resourceComplexGetResponse)

      instanceChecker.check(
        instanceResponse = resourceComplexGetResponseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )

      // Check that it has the property knora-api:hasStandoffLinkToValue.
      val resourceJsonLDDoc = JsonLDUtil.parseJsonLD(resourceComplexGetResponseAsString)
      assert(resourceJsonLDDoc.body.value.contains(OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue))
    }

    "create a resource containing a text value with multiple standoff links" in {
      val jsonLDEntity =
        """{
          |  "@type": "anything:Thing",
          |  "anything:hasText": {
          |  "@type": "knora-api:TextValue",
          |  "knora-api:textValueAsXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text>\n   This text links to another <a class=\"salsah-link\" href=\"http://rdfh.ch/0001/another-thing\">thing</a> and a <a class=\"salsah-link\" href=\"http://rdfh.ch/0001/a-blue-thing\">blue thing</a>.\n</text>",
          |   "knora-api:textValueHasMapping": {
          |                  "@id": "http://rdfh.ch/standoff/mappings/StandardMapping"
          |            	}
          |            },
          |   "knora-api:attachedToProject": {
          |                  "@id": "http://rdfh.ch/projects/0001"
          |                  },
          |   "rdfs:label": "thing_with_mutiple_standoffLinks",
          |   "@context": {
          |                "anything": "http://0.0.0.0:3333/ontology/0001/anything/v2#",
          |                "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |                "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
          |                "knora-api": "http://api.knora.org/ontology/knora-api/v2#"
          |                }
          | }""".stripMargin

      val request = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)

      // Request the newly created resource in the complex schema, and check that it matches the ontology.
      val resourceComplexGetRequest = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}"
      ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password))
      val resourceComplexGetResponse: HttpResponse = singleAwaitingRequest(resourceComplexGetRequest)
      val resourceComplexGetResponseAsString       = responseToString(resourceComplexGetResponse)

      // Check that it has multiple property knora-api:hasStandoffLinkToValue.
      val resourceJsonLDDoc: JsonLDDocument = JsonLDUtil.parseJsonLD(resourceComplexGetResponseAsString)
      val numberOfStandofHasLinkValue = resourceJsonLDDoc.body
        .value(OntologyConstants.KnoraApiV2Complex.HasStandoffLinkToValue)
        .asInstanceOf[JsonLDArray]
        .value
        .size
      assert(numberOfStandofHasLinkValue == 2)
    }

    "return a IIIF manifest for the pages of a book" in {
      val resourceIri            = "http://rdfh.ch/0001/thing-with-pages"
      val request                = Get(s"$baseApiUrl/v2/resources/iiifmanifest/${URLEncoder.encode(resourceIri, "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseStr: String    = responseToString(response)
      assert(response.status == StatusCodes.OK, responseStr)
      val responseJson: JsValue = JsonParser(responseStr)

      val expectedJson: JsValue = JsonParser(
        readOrWriteTextFile(
          responseStr,
          Paths.get("..", "test_data/resourcesR2RV2/IIIFManifest.jsonld"),
          writeTestDataFiles
        )
      )

      assert(responseJson == expectedJson)
    }

    "correctly update the ontology cache when adding a resource, so that the resource can afterwards be found by gravsearch" in {
      val freetestLastModDate: Instant = Instant.parse("2012-12-12T12:12:12.12Z")
      DSPApiDirectives.handleErrors(system, appConfig)(new OntologiesRouteV2(routeData, runtime).makeRoute)
      val auth = BasicHttpCredentials(SharedTestDataADM.anythingAdminUser.email, SharedTestDataADM.testPass)

      // create a new resource class and add a property with cardinality to it
      val createResourceClass =
        s"""{
           |  "@id" : "${SharedOntologyTestDataADM.FREETEST_ONTOLOGY_IRI_LocalHost}",
           |  "@type" : "owl:Ontology",
           |  "knora-api:lastModificationDate" : {
           |    "@type" : "xsd:dateTimeStamp",
           |    "@value" : "$freetestLastModDate"
           |  },
           |  "@graph" : [ {
           |    "@id" : "freetest:NewClass",
           |    "@type" : "owl:Class",
           |    "rdfs:label" : {
           |      "@language" : "en",
           |      "@value" : "New resource class"
           |    },
           |    "rdfs:subClassOf" : [
           |            {
           |               "@id": "knora-api:Resource"
           |            },
           |      {
           |        "@type": "http://www.w3.org/2002/07/owl#Restriction",
           |        "owl:maxCardinality": 1,
           |        "owl:onProperty": {
           |          "@id": "freetest:hasName"
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
           |    "freetest" : "http://0.0.0.0:3333/ontology/0001/freetest/v2#"
           |  }
           |}""".stripMargin

      val createResourceClassRequest = Post(
        s"$baseApiUrl/v2/ontologies/classes",
        HttpEntity(RdfMediaTypes.`application/ld+json`, createResourceClass)
      ) ~> addCredentials(auth)
      val createResourceClassResponse: HttpResponse = singleAwaitingRequest(createResourceClassRequest)

      assert(createResourceClassResponse.status == StatusCodes.OK, createResourceClassResponse.toString)

      // create an instance of the class
      val createResourceWithValues: String =
        """{
          |  "@type" : "freetest:NewClass",
          |  "freetest:hasName" : {
          |    "@type" : "knora-api:TextValue",
          |    "knora-api:valueAsString" : "The new text value"
          |  },
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "rdfs:label" : "test resource instance",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "freetest" : "http://0.0.0.0:3333/ontology/0001/freetest/v2#"
          |  }
          |}""".stripMargin

      val resourceRequest = Post(
        s"$baseApiUrl/v2/resources",
        HttpEntity(RdfMediaTypes.`application/ld+json`, createResourceWithValues)
      ) ~> addCredentials(auth)
      val resourceResponse: HttpResponse = singleAwaitingRequest(resourceRequest)

      assert(resourceResponse.status == StatusCodes.OK, resourceResponse.toString)
      val resourceIri: IRI =
        responseToJsonLDDocument(resourceResponse).body
          .requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)

      // get resource back
      val resourceComplexGetRequest = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}"
      ) ~> addCredentials(auth)
      val resourceComplexGetResponse: HttpResponse = singleAwaitingRequest(resourceComplexGetRequest)

      val valueObject = responseToJsonLDDocument(resourceComplexGetResponse).body
        .requireObject("http://0.0.0.0:3333/ontology/0001/freetest/v2#hasName")
      val valueIri: IRI      = valueObject.requireString("@id")
      val valueAsString: IRI = valueObject.requireString("http://api.knora.org/ontology/knora-api/v2#valueAsString")

      assert(valueAsString == "The new text value")

      // try to edit the value which requires the class to be properly cached
      val editValue =
        s"""{
           |  "@id" : "$resourceIri",
           |  "@type" : "freetest:NewClass",
           |  "freetest:hasName" : {
           |    "@id" : "$valueIri",
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:valueAsString" : "changed value"
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "freetest" : "http://0.0.0.0:3333/ontology/0001/freetest/v2#"
           |  }
           |}""".stripMargin

      val editValueRequest =
        Put(baseApiUrl + "/v2/values", HttpEntity(RdfMediaTypes.`application/ld+json`, editValue)) ~> addCredentials(
          auth
        )
      val editValueResponse: HttpResponse = singleAwaitingRequest(editValueRequest)
      assert(editValueResponse.status == StatusCodes.OK, responseToString(editValueResponse))
    }

    "correctly load and request resources that have a isSequenceOf relation to a video resource" in {
      val cred   = BasicHttpCredentials(anythingUserEmail, password)
      val valUrl = s"$baseApiUrl/v2/values"
      val resUrl = s"$baseApiUrl/v2/resources"

      // get the video resource
      val videoResourceIri = URLEncoder.encode("http://rdfh.ch/0001/video-01", "UTF-8")
      val videoGetRequest  = Get(s"$resUrl/$videoResourceIri") ~> addCredentials(cred)
      val videoResponse    = singleAwaitingRequest(videoGetRequest)
      assert(videoResponse.status == StatusCodes.OK)

      // get the sequence reource pointing to the video resource
      val sequenceResourceIri = URLEncoder.encode("http://rdfh.ch/0001/video-sequence-01", "UTF-8")
      val sequenceGetRequest  = Get(s"$resUrl/$sequenceResourceIri") ~> addCredentials(cred)
      val sequenceResponse    = singleAwaitingRequest(sequenceGetRequest)
      assert(sequenceResponse.status == StatusCodes.OK)

      // get the isSequenceOfValue property on the sequence resource
      val sequenceOfUuid     = "6CKp1AmZT1SRHYeSOUaJjA"
      val sequenceOfRequest  = Get(s"$valUrl/$sequenceResourceIri/$sequenceOfUuid") ~> addCredentials(cred)
      val sequenceOfResponse = singleAwaitingRequest(sequenceOfRequest)
      assert(sequenceOfResponse.status == StatusCodes.OK)

      // get the hasSequenceBounds property on the sequence resource
      val sequenceBoundsUuid     = "vEDim4wvSfGnhSvX6fXcaA"
      val sequenceBoundsRequest  = Get(s"$valUrl/$sequenceResourceIri/$sequenceBoundsUuid") ~> addCredentials(cred)
      val sequenceBoundsResponse = singleAwaitingRequest(sequenceBoundsRequest)
      assert(sequenceBoundsResponse.status == StatusCodes.OK)
    }

    "correctly create and request additional resources that have a isSequenceOf relation to a video resource" in {
      val cred   = BasicHttpCredentials(anythingUserEmail, password)
      val valUrl = s"$baseApiUrl/v2/values"
      val resUrl = s"$baseApiUrl/v2/resources"

      // create another sequence of the video resource
      val createSequenceJson: String =
        """{
          |  "@type" : "sequences:VideoSequence",
          |  "knora-api:isSequenceOfValue" : {
          |    "@type" : "knora-api:LinkValue",
          |    "knora-api:linkValueHasTargetIri" : {
          |      "@id" : "http://rdfh.ch/0001/video-01"
          |    }
          |  },
          |  "knora-api:hasSequenceBounds" : {
          |    "@type" : "knora-api:IntervalValue",
          |    "knora-api:intervalValueHasEnd" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "3.4"
          |    },
          |    "knora-api:intervalValueHasStart" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "1.2"
          |    }
          |  },
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "rdfs:label" : "second sequence",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "sequences" : "http://0.0.0.0:3333/ontology/0001/sequences/v2#"
          |  }
          |}""".stripMargin

      val createSequenceRequest =
        Post(resUrl, HttpEntity(RdfMediaTypes.`application/ld+json`, createSequenceJson)) ~> addCredentials(cred)
      val createSequenceResponse = singleAwaitingRequest(createSequenceRequest)
      assert(createSequenceResponse.status == StatusCodes.OK, createSequenceResponse.toString)
      val createSequenceResponseBody = responseToJsonLDDocument(createSequenceResponse).body
      val sequenceResourceIri        = URLEncoder.encode(createSequenceResponseBody.requireString(JsonLDKeywords.ID), "UTF-8")

      // get the newly created sequence resource
      val sequenceGetRequest = Get(s"$resUrl/$sequenceResourceIri") ~> addCredentials(cred)
      val sequenceResponse   = singleAwaitingRequest(sequenceGetRequest)
      assert(sequenceResponse.status == StatusCodes.OK)
      val getSequenceResponseBody = responseToJsonLDDocument(sequenceResponse).body
      val sequenceOfUuid = getSequenceResponseBody
        .requireObject(OntologyConstants.KnoraApiV2Complex.IsSequenceOfValue)
        .requireString(OntologyConstants.KnoraApiV2Complex.ValueHasUUID)
      val sequenceBoundsUuid = getSequenceResponseBody
        .requireObject(OntologyConstants.KnoraApiV2Complex.HasSequenceBounds)
        .requireString(OntologyConstants.KnoraApiV2Complex.ValueHasUUID)

      // get the isSequenceOfValue property on the sequence resource
      val sequenceOfRequest  = Get(s"$valUrl/$sequenceResourceIri/$sequenceOfUuid") ~> addCredentials(cred)
      val sequenceOfResponse = singleAwaitingRequest(sequenceOfRequest)
      assert(sequenceOfResponse.status == StatusCodes.OK)

      // get the hasSequenceBounds property on the sequence resource
      val sequenceBoundsRequest  = Get(s"$valUrl/$sequenceResourceIri/$sequenceBoundsUuid") ~> addCredentials(cred)
      val sequenceBoundsResponse = singleAwaitingRequest(sequenceBoundsRequest)
      assert(sequenceBoundsResponse.status == StatusCodes.OK)
    }

    "correctly create and request resources that have a isSequenceOf-subproperty relation to an audio resource" in {
      val cred   = BasicHttpCredentials(anythingUserEmail, password)
      val valUrl = s"$baseApiUrl/v2/values"
      val resUrl = s"$baseApiUrl/v2/resources"

      // create another sequence of the video resource
      val createSequenceJson: String =
        """{
          |  "@type" : "sequences:AudioSequence",
          |  "sequences:isAnnotatedSequenceOfAudioValue" : {
          |    "@type" : "knora-api:LinkValue",
          |    "knora-api:linkValueHasTargetIri" : {
          |      "@id" : "http://rdfh.ch/0001/audio-01"
          |    }
          |  },
          |  "sequences:hasCustomSequenceBounds" : {
          |    "@type" : "knora-api:IntervalValue",
          |    "knora-api:intervalValueHasEnd" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "14.2"
          |    },
          |    "knora-api:intervalValueHasStart" : {
          |      "@type" : "xsd:decimal",
          |      "@value" : "9.9"
          |    }
          |  },
          |  "knora-api:attachedToProject" : {
          |    "@id" : "http://rdfh.ch/projects/0001"
          |  },
          |  "rdfs:label" : "custom audio sequence",
          |  "@context" : {
          |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
          |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
          |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
          |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
          |    "sequences" : "http://0.0.0.0:3333/ontology/0001/sequences/v2#"
          |  }
          |}""".stripMargin

      val createSequenceRequest =
        Post(resUrl, HttpEntity(RdfMediaTypes.`application/ld+json`, createSequenceJson)) ~> addCredentials(cred)
      val createSequenceResponse = singleAwaitingRequest(createSequenceRequest)
      assert(createSequenceResponse.status == StatusCodes.OK, createSequenceResponse.toString)
      val createSequenceResponseBody = responseToJsonLDDocument(createSequenceResponse).body
      val sequenceResourceIri        = URLEncoder.encode(createSequenceResponseBody.requireString(JsonLDKeywords.ID), "UTF-8")

      // get the newly created sequence reource
      val sequenceGetRequest = Get(s"$resUrl/$sequenceResourceIri") ~> addCredentials(cred)
      val sequenceResponse   = singleAwaitingRequest(sequenceGetRequest)
      assert(sequenceResponse.status == StatusCodes.OK)
      val getSequenceResponseBody = responseToJsonLDDocument(sequenceResponse).body
      val sequenceOfUuid = getSequenceResponseBody
        .requireObject("http://0.0.0.0:3333/ontology/0001/sequences/v2#isAnnotatedSequenceOfAudioValue")
        .requireString(OntologyConstants.KnoraApiV2Complex.ValueHasUUID)
      val sequenceBoundsUuid = getSequenceResponseBody
        .requireObject("http://0.0.0.0:3333/ontology/0001/sequences/v2#hasCustomSequenceBounds")
        .requireString(OntologyConstants.KnoraApiV2Complex.ValueHasUUID)

      // get the isSequenceOfValue property on the sequence resource
      val sequenceOfRequest  = Get(s"$valUrl/$sequenceResourceIri/$sequenceOfUuid") ~> addCredentials(cred)
      val sequenceOfResponse = singleAwaitingRequest(sequenceOfRequest)
      assert(sequenceOfResponse.status == StatusCodes.OK)

      // get the hasSequenceBounds property on the sequence resource
      val sequenceBoundsRequest  = Get(s"$valUrl/$sequenceResourceIri/$sequenceBoundsUuid") ~> addCredentials(cred)
      val sequenceBoundsResponse = singleAwaitingRequest(sequenceBoundsRequest)
      assert(sequenceBoundsResponse.status == StatusCodes.OK)
    }
  }
}

object ResourcesRouteV2E2ESpec {
  val config: Config = ConfigFactory.parseString("""akka.loglevel = "DEBUG"
                                                   |akka.stdout-loglevel = "DEBUG"
                                                   |app.triplestore.profile-queries = false
        """.stripMargin)
}
