/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.e2e.v2

import java.net.URLEncoder
import java.nio.file.Paths
import java.time.Instant

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.{Accept, BasicHttpCredentials}
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, MediaRange, StatusCodes}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi._
import org.knora.webapi.e2e.v2.ResponseCheckerV2._
import org.knora.webapi.e2e.{ClientTestDataCollector, InstanceChecker, TestDataFileContent, TestDataFilePath}
import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util._
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.{OntologyConstants, StringFormatter}
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util._
import org.xmlunit.builder.{DiffBuilder, Input}
import org.xmlunit.diff.Diff
import spray.json.{JsValue, JsonParser}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}

/**
  * Tests the API v2 resources route.
  */
class ResourcesRouteV2E2ESpec extends E2ESpec(ResourcesRouteV2E2ESpec.config) {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  implicit def default(implicit system: ActorSystem): RouteTestTimeout =
    RouteTestTimeout(settings.triplestoreUpdateTimeout)

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  private val anythingUserEmail = SharedTestDataADM.anythingUser1.email
  private val password = SharedTestDataADM.testPass
  private var aThingLastModificationDate = Instant.now
  private val hamletResourceIri = new MutableTestIri

  // If true, writes all API responses to test data files. If false, compares the API responses to the existing test data files.
  private val writeTestDataFiles = false

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  private val instanceChecker: InstanceChecker = InstanceChecker.getJsonLDChecker

  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("v2", "resources")

  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(settings)

  private def successResponse(message: String): String =
    s"""{
           |  "knora-api:result" : "$message",
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#"
           |  }
           |}""".stripMargin

  "The resources v2 endpoint" should {
    "perform a resource request for the book 'Reise ins Heilige Land' using the complex schema in JSON-LD" in {
      val resourceIri = "http://rdfh.ch/resources/w1JN2mMZam7F1_eiyvz6pw"
      val resourceIriEncoded = URLEncoder.encode(resourceIri, "UTF-8")
      val request = Get(s"$baseApiUrl/v2/resources/$resourceIriEncoded")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)

      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"),
                            writeTestDataFiles)
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
        MediaRange.One(RdfMediaTypes.`application/ld+json`, 0.5F),
        MediaRange.One(RdfMediaTypes.`text/turtle`, 0.8F),
        MediaRange.One(RdfMediaTypes.`application/rdf+xml`, 0.2F)
      )

      val request = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/w1JN2mMZam7F1_eiyvz6pw", "UTF-8")}")
        .addHeader(acceptHeader)
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerTurtle = readOrWriteTextFile(responseAsString,
                                                     Paths.get("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"),
                                                     writeTestDataFiles)
      assert(parseTurtle(responseAsString) == parseTurtle(expectedAnswerTurtle))
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the complex schema in RDF/XML" in {
      val request = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/w1JN2mMZam7F1_eiyvz6pw", "UTF-8")}")
        .addHeader(Accept(RdfMediaTypes.`application/rdf+xml`))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerRdfXml = readOrWriteTextFile(responseAsString,
                                                     Paths.get("test_data/resourcesR2RV2/BookReiseInsHeiligeLand.rdf"),
                                                     writeTestDataFiles)
      assert(parseRdfXml(responseAsString) == parseRdfXml(expectedAnswerRdfXml))
    }

    "perform a resource preview request for the book 'Reise ins Heilige Land' using the complex schema" in {
      val request =
        Get(
          s"$baseApiUrl/v2/resourcespreview/${URLEncoder.encode("http://rdfh.ch/resources/w1JN2mMZam7F1_eiyvz6pw", "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/BookReiseInsHeiligeLandPreview.jsonld"),
                            writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a resource preview request for a Thing resource using the complex schema" in {
      val request = Get(
        s"$baseApiUrl/v2/resourcespreview/${URLEncoder.encode("http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA", "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString, Paths.get("test_data/resourcesR2RV2/AThing.jsonld"), writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "resource-preview",
            fileExtension = "json"
          ),
          text = responseAsString
        )
      )
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema (specified by an HTTP header) in JSON-LD" in {
      val request = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/w1JN2mMZam7F1_eiyvz6pw", "UTF-8")}")
        .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.jsonld"),
                            writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema in Turtle" in {
      val request = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/w1JN2mMZam7F1_eiyvz6pw", "UTF-8")}")
        .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
        .addHeader(Accept(RdfMediaTypes.`text/turtle`))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerTurtle =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.ttl"),
                            writeTestDataFiles)
      assert(parseTurtle(responseAsString) == parseTurtle(expectedAnswerTurtle))
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema in RDF/XML" in {
      val request = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/w1JN2mMZam7F1_eiyvz6pw", "UTF-8")}")
        .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
        .addHeader(Accept(RdfMediaTypes.`application/rdf+xml`))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerRdfXml =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.rdf"),
                            writeTestDataFiles)
      assert(parseRdfXml(responseAsString) == parseRdfXml(expectedAnswerRdfXml))
    }

    "perform a resource preview request for the book 'Reise ins Heilige Land' using the simple schema (specified by an HTTP header)" in {
      val request =
        Get(
          s"$baseApiUrl/v2/resourcespreview/${URLEncoder.encode("http://rdfh.ch/resources/w1JN2mMZam7F1_eiyvz6pw", "UTF-8")}")
          .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimplePreview.jsonld"),
                            writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema (specified by a URL parameter)" in {
      val request = Get(s"$baseApiUrl/v2/resources/${URLEncoder
        .encode("http://rdfh.ch/resources/w1JN2mMZam7F1_eiyvz6pw", "UTF-8")}?${RouteUtilV2.SCHEMA_PARAM}=${RouteUtilV2.SIMPLE_SCHEMA_NAME}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.jsonld"),
                            writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a resource request for the first page of the book '[Das] Narrenschiff (lat.)' using the complex schema" in {
      val request = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/O_wI0t9iQYy4cUG6ex_UAg", "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString,
                                                     Paths.get("test_data/resourcesR2RV2/NarrenschiffFirstPage.jsonld"),
                                                     writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a full resource request for a resource with a BCE date property" in {
      val request =
        Get(
          s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/Oc-2o65l9ozmUY8jPLdu6Q", "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString,
                                                     Paths.get("test_data/resourcesR2RV2/ThingWithBCEDate.jsonld"),
                                                     writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with a date property that represents a period going from BCE to CE" in {
      val request =
        Get(
          s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/qpW9FkcgiCEC_AabCrYDYQ", "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString,
                                                     Paths.get("test_data/resourcesR2RV2/ThingWithBCEDate2.jsonld"),
                                                     writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with a list value" in {
      val request =
        Get(
          s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/QhkalmdntjSkYOLsDZ9lHg", "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString,
                                                     Paths.get("test_data/resourcesR2RV2/ThingWithListValue.jsonld"),
                                                     writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with a list value (in the simple schema)" in {
      val request =
        Get(
          s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/QhkalmdntjSkYOLsDZ9lHg", "UTF-8")}")
          .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/ThingWithListValueSimple.jsonld"),
                            writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with a link (in the complex schema)" in {
      val request =
        Get(
          s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/0C-0L1kORryKzJAJxxRyRQ", "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString,
                                                     Paths.get("test_data/resourcesR2RV2/ThingWithLinkComplex.jsonld"),
                                                     writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with a link (in the simple schema)" in {
      val request =
        Get(
          s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/0C-0L1kORryKzJAJxxRyRQ", "UTF-8")}")
          .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString,
                                                     Paths.get("test_data/resourcesR2RV2/ThingWithLinkSimple.jsonld"),
                                                     writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with a Text language (in the complex schema)" in {
      val request = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/mniT9VYSIrfwKupam8Vzng", "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/ThingWithTextLangComplex.jsonld"),
                            writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with a Text language (in the simple schema)" in {
      val request = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/mniT9VYSIrfwKupam8Vzng", "UTF-8")}")
        .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/ThingWithTextLangSimple.jsonld"),
                            writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a resource with values of different types" in {
      val request =
        Get(
          s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/H6gBWUuJSuuO-CilHV8kQw", "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString, Paths.get("test_data/resourcesR2RV2/Testding.jsonld"), writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "testding",
            fileExtension = "json"
          ),
          text = responseAsString
        )
      )

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a Thing resource with a link to a ThingPicture resource" in {
      val request =
        Get(
          s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/Cq3JSR3jXFXgyDClrnxOUA", "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString,
                                                     Paths.get("test_data/resourcesR2RV2/ThingWithPicture.jsonld"),
                                                     writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "thing-with-picture",
            fileExtension = "json"
          ),
          text = responseAsString
        )
      )

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request with a link to a resource that the user doesn't have permission to see" in {
      val request =
        Get(
          s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/0JhgKcqoRIeRRG6ownArSw", "UTF-8")}")

      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/ThingWithOneHiddenResource.jsonld"),
                            writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request with a link to a resource that is marked as deleted" in {
      val request =
        Get(
          s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/l8f8FVEiSCeq9A1p8gBR-A", "UTF-8")}")

      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/ThingWithOneDeletedResource.jsonld"),
                            writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)

      // Check that the resource corresponds to the ontology.
      instanceChecker.check(
        instanceResponse = responseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )
    }

    "perform a full resource request for a past version of a resource, using a URL-encoded xsd:dateTimeStamp" in {
      val request = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw", "UTF-8")}?version=${URLEncoder
          .encode("2019-02-12T08:05:10.351Z", "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/ThingWithVersionHistory.jsonld"),
                            writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "perform a full resource request for a past version of a resource, using a Knora ARK timestamp" in {
      val request = Get(
        s"$baseApiUrl/v2/resources/${URLEncoder.encode("http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw", "UTF-8")}?version=${URLEncoder
          .encode("20190212T080510351Z", "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/ThingWithVersionHistory.jsonld"),
                            writeTestDataFiles)
      compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "return the complete version history of a resource" in {
      val request =
        Get(
          s"$baseApiUrl/v2/resources/history/${URLEncoder.encode("http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw", "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/CompleteVersionHistory.jsonld"),
                            writeTestDataFiles)
      compareJSONLDForResourceHistoryResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "return the version history of a resource within a date range" in {
      val resourceIri = URLEncoder.encode("http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw", "UTF-8")
      val startDate = URLEncoder.encode(Instant.parse("2019-02-08T15:05:11Z").toString, "UTF-8")
      val endDate = URLEncoder.encode(Instant.parse("2019-02-13T09:05:10Z").toString, "UTF-8")
      val request = Get(s"$baseApiUrl/v2/resources/history/$resourceIri?startDate=$startDate&endDate=$endDate")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString,
                                                     Paths.get("test_data/resourcesR2RV2/PartialVersionHistory.jsonld"),
                                                     writeTestDataFiles)
      compareJSONLDForResourceHistoryResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAsString)
    }

    "return each of the versions of a resource listed in its version history" in {
      val resourceIri = URLEncoder.encode("http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw", "UTF-8")
      val historyRequest = Get(s"$baseApiUrl/v2/resources/history/$resourceIri")
      val historyResponse: HttpResponse = singleAwaitingRequest(historyRequest)
      val historyResponseAsString = responseToString(historyResponse)
      assert(historyResponse.status == StatusCodes.OK, historyResponseAsString)
      val jsonLDDocument: JsonLDDocument = JsonLDUtil.parseJsonLD(historyResponseAsString)
      val entries: JsonLDArray = jsonLDDocument.requireArray("@graph")

      for (entry: JsonLDValue <- entries.value) {
        entry match {
          case jsonLDObject: JsonLDObject =>
            val versionDate: Instant = jsonLDObject.requireDatatypeValueInObject(
              key = OntologyConstants.KnoraApiV2Complex.VersionDate,
              expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
              validationFun = stringFormatter.xsdDateTimeStampToInstant
            )

            val arkTimestamp = stringFormatter.formatArkTimestamp(versionDate)
            val versionRequest = Get(s"$baseApiUrl/v2/resources/$resourceIri?version=$arkTimestamp")
            val versionResponse: HttpResponse = singleAwaitingRequest(versionRequest)
            val versionResponseAsString = responseToString(versionResponse)
            assert(versionResponse.status == StatusCodes.OK, versionResponseAsString)
            val expectedAnswerJSONLD =
              readOrWriteTextFile(versionResponseAsString,
                                  Paths.get(s"test_data/resourcesR2RV2/ThingWithVersionHistory$arkTimestamp.jsonld"),
                                  writeTestDataFiles)
            compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD,
                                              receivedJSONLD = versionResponseAsString)

          case other => throw AssertionException(s"Expected JsonLDObject, got $other")
        }
      }
    }

    "return all history events for a given resource" in {
      val resourceIri = URLEncoder.encode("http://rdfh.ch/resources/h2TEa725XPK7CfG15Bk0bA", "UTF-8")
      val resourceHistoryRequest = Get(s"$baseApiUrl/v2/resources/resourceHistoryEvents/$resourceIri")
        .addCredentials(BasicHttpCredentials(SharedTestDataADM.anythingAdminUser.email, password))
      val resourceHistoryResponse: HttpResponse = singleAwaitingRequest(resourceHistoryRequest)
      val historyResponseAsString = responseToString(resourceHistoryResponse)
      assert(resourceHistoryResponse.status == StatusCodes.OK, historyResponseAsString)
    }

    "return entire resource and value history events for a given project" in {
      val projectIri = URLEncoder.encode("http://rdfh.ch/projects/0001", "UTF-8")
      val projectHistoryRequest = Get(s"$baseApiUrl/v2/resources/projectHistoryEvents/$projectIri")
        .addCredentials(BasicHttpCredentials(SharedTestDataADM.anythingAdminUser.email, password))
      val projectHistoryResponse: HttpResponse = singleAwaitingRequest(projectHistoryRequest)
      val historyResponseAsString = responseToString(projectHistoryResponse)
      assert(projectHistoryResponse.status == StatusCodes.OK, historyResponseAsString)
    }

    "return a graph of resources reachable via links from/to a given resource" in {
      val request =
        Get(
          s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw", "UTF-8")}?direction=both")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "resource-graph",
            fileExtension = "json"
          ),
          text = responseAsString
        )
      )

      assert(response.status == StatusCodes.OK, responseAsString)
      val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)

      val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString,
                                                     Paths.get("test_data/resourcesR2RV2/ThingGraphBoth.jsonld"),
                                                     writeTestDataFiles)
      val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)

      assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
    }

    "return a graph of resources reachable via links from a given resource" in {
      val request =
        Get(
          s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw", "UTF-8")}?direction=outbound")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)

      val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString,
                                                     Paths.get("test_data/resourcesR2RV2/ThingGraphOutbound.jsonld"),
                                                     writeTestDataFiles)
      val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)

      assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
    }

    "return a graph of resources reachable via links to a given resource" in {
      val request =
        Get(
          s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw", "UTF-8")}?direction=inbound")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)

      val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString,
                                                     Paths.get("test_data/resourcesR2RV2/ThingGraphInbound.jsonld"),
                                                     writeTestDataFiles)
      val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)

      assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
    }

    "return a graph of resources reachable via links to/from a given resource, excluding a specified property" in {
      val request = Get(
        s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw", "UTF-8")}?direction=both&excludeProperty=${URLEncoder
          .encode("http://0.0.0.0:3333/ontology/0001/anything/v2#isPartOfOtherThing", "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)

      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/ThingGraphBothWithExcludedProp.jsonld"),
                            writeTestDataFiles)
      val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)

      assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
    }

    "return a graph of resources reachable via links from a given resource, specifying search depth" in {
      val request =
        Get(
          s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw", "UTF-8")}?direction=both&depth=2")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val parsedReceivedJsonLD = JsonLDUtil.parseJsonLD(responseAsString)

      val expectedAnswerJSONLD =
        readOrWriteTextFile(responseAsString,
                            Paths.get("test_data/resourcesR2RV2/ThingGraphBothWithDepth.jsonld"),
                            writeTestDataFiles)
      val parsedExpectedJsonLD = JsonLDUtil.parseJsonLD(expectedAnswerJSONLD)

      assert(parsedReceivedJsonLD == parsedExpectedJsonLD)
    }

    "not accept a graph request with an invalid direction" in {
      val request =
        Get(
          s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw", "UTF-8")}?direction=foo")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.BadRequest, responseAsString)
    }

    "not accept a graph request with an invalid depth (< 1)" in {
      val request = Get(
        s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw", "UTF-8")}?depth=0")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.BadRequest, responseAsString)
    }

    "not accept a graph request with an invalid depth (> max)" in {
      val request = Get(s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw",
                                                                   "UTF-8")}?depth=${settings.maxGraphBreadth + 1}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.BadRequest, responseAsString)
    }

    "not accept a graph request with an invalid property to exclude" in {
      val request =
        Get(
          s"$baseApiUrl/v2/graph/${URLEncoder.encode("http://rdfh.ch/resources/gKGfzQuqCIvrXmagQI-Xuw", "UTF-8")}?excludeProperty=foo")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.BadRequest, responseAsString)
    }

    "return resources from a project" in {
      val resourceClass = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#book", "UTF-8")
      val orderByProperty = URLEncoder.encode("http://0.0.0.0:3333/ontology/0803/incunabula/v2#title", "UTF-8")
      val request = Get(
        s"$baseApiUrl/v2/resources?resourceClass=$resourceClass&orderByProperty=$orderByProperty&page=0")
        .addHeader(new ProjectHeader(SharedTestDataADM.incunabulaProject.id)) ~> addCredentials(
        BasicHttpCredentials(SharedTestDataADM.incunabulaProjectAdminUser.email, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.OK, responseAsString)
      val expectedAnswerJSONLD = readOrWriteTextFile(responseAsString,
                                                     Paths.get("test_data/resourcesR2RV2/BooksFromIncunabula.jsonld"),
                                                     writeTestDataFiles)
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
                  |      "@id" : "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA"
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-resource-with-values-request",
            fileExtension = "json"
          ),
          text = createResourceWithValues
        )
      )

      val request = Post(s"$baseApiUrl/v2/resources",
                         HttpEntity(RdfMediaTypes.`application/ld+json`, createResourceWithValues)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)

      // Request the newly created resource in the complex schema, and check that it matches the ontology.
      val resourceComplexGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val resourceComplexGetResponse: HttpResponse = singleAwaitingRequest(resourceComplexGetRequest)
      val resourceComplexGetResponseAsString = responseToString(resourceComplexGetResponse)

      instanceChecker.check(
        instanceResponse = resourceComplexGetResponseAsString,
        expectedClassIri = "http://0.0.0.0:3333/ontology/0001/anything/v2#Thing".toSmartIri,
        knoraRouteGet = doGetRequest
      )

      // Request the newly created resource in the simple schema, and check that it matches the ontology.
      val resourceSimpleGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}")
        .addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val resourceSimpleGetResponse: HttpResponse = singleAwaitingRequest(resourceSimpleGetRequest)
      val resourceSimpleGetResponseAsString = responseToString(resourceSimpleGetResponse)

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

    "create a resource whose label contains a Unicode escape and quotation marks" in {
      val jsonLDEntity: String =
        FileUtil.readTextFile(Paths.get("test_data/resourcesR2RV2/ThingWithUnicodeEscape.jsonld"))
      val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-resource-with-custom-creation-date",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
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

    def createResourceWithCustomIRI(iri: IRI): String = {
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
    }

    "create a resource with a custom IRI" in {
      val customIRI: IRI = SharedTestDataADM.customResourceIRI
      val jsonLDEntity = createResourceWithCustomIRI(customIRI)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-resource-with-custom-IRI-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri == customIRI)
    }

    "not create a resource with an invalid custom IRI" in {
      val customIRI: IRI = "http://rdfh.ch/resources/invalid-resource-IRI"
      val jsonLDEntity = createResourceWithCustomIRI(customIRI)
      val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      println(responseToString(response))
      assert(response.status == StatusCodes.BadRequest, response.toString)
    }

    "return a DuplicateValueException during resource creation when the supplied resource IRI is not unique" in {

      // duplicate resource IRI
      val params =
        s"""{
                   |  "@id" : "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA",
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

      val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, params)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.BadRequest, response.toString)

      val errorMessage: String = Await.result(Unmarshal(response.entity).to[String], 1.second)
      val invalidIri: Boolean =
        errorMessage.contains(
          s"IRI: 'http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA' already exists, try another one.")
      invalidIri should be(true)
    }

    def createResourceWithCustomValueIRI(valueIRI: IRI): String = {
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
    }

    "create a resource with random IRI and a custom value IRI" in {
      val customValueIRI: IRI = SharedTestDataADM.customValueIRI
      val jsonLDEntity = createResourceWithCustomValueIRI(customValueIRI)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-resource-with-custom-value-IRI-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)

      // Request the newly created resource.
      val resourceGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val resourceGetResponse: HttpResponse =
        singleAwaitingRequest(resourceGetRequest, duration = settings.triplestoreUpdateTimeout)
      val resourceGetResponseAsString = responseToString(resourceGetResponse)

      // Get the value from the response.
      val resourceGetResponseAsJsonLD = JsonLDUtil.parseJsonLD(resourceGetResponseAsString)
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-resource-with-custom-value-UUID-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)

      // Request the newly created resource.
      val resourceGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val resourceGetResponse: HttpResponse =
        singleAwaitingRequest(resourceGetRequest, duration = settings.triplestoreUpdateTimeout)
      val resourceGetResponseAsString = responseToString(resourceGetResponse)

      // Get the value from the response.
      val resourceGetResponseAsJsonLD = JsonLDUtil.parseJsonLD(resourceGetResponseAsString)
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-resource-with-custom-value-creationDate-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)

      // Request the newly created resource.
      val resourceGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val resourceGetResponse: HttpResponse =
        singleAwaitingRequest(resourceGetRequest, duration = settings.triplestoreUpdateTimeout)
      val resourceGetResponseAsString = responseToString(resourceGetResponse)

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
      val customResourceIRI: IRI = SharedTestDataADM.customResourceIRI_resourceWithValues
      val customCreationDate: Instant = Instant.parse("2019-01-09T15:45:54.502951Z")
      val customValueIRI: IRI = SharedTestDataADM.customValueIRI_withResourceIriAndValueIRIAndValueUUID
      val customValueUUID = SharedTestDataADM.customValueUUID

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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-resource-with-custom-resourceIRI-creationDate-ValueIri-ValueUUID-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)

      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri == customResourceIRI)

      // Request the newly created resource.
      val resourceGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val resourceGetResponse: HttpResponse =
        singleAwaitingRequest(resourceGetRequest, duration = settings.triplestoreUpdateTimeout)
      val resourceGetResponseAsString = responseToString(resourceGetResponse)

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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "create-resource-as-user",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(SharedTestDataADM.anythingAdminUser.email, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)
      val savedAttachedToUser: IRI =
        responseJsonDoc.body.requireIriInObject(OntologyConstants.KnoraApiV2Complex.AttachedToUser,
                                                stringFormatter.validateAndEscapeIri)
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

      val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(SharedTestDataADM.anythingUser2.email, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseAsString = responseToString(response)
      assert(response.status == StatusCodes.Forbidden, responseAsString)
    }

    "create a resource containing escaped text" in {
      val jsonLDEntity = FileUtil.readTextFile(Paths.get("test_data/resourcesR2RV2/CreateResourceWithEscape.jsonld"))
      val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)
    }

    "update the metadata of a resource" in {
      val resourceIri = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA"
      val newLabel = "test thing with modified label"
      val newPermissions = "CR knora-admin:Creator|M knora-admin:ProjectMember|V knora-admin:ProjectMember"
      val newModificationDate = Instant.now.plus(java.time.Duration.ofDays(1))

      val jsonLDEntity =
        s"""|{
                    |  "@id" : "$resourceIri",
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-resource-metadata-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val updateRequest = Put(s"$baseApiUrl/v2/resources",
                              HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val updateResponse: HttpResponse = singleAwaitingRequest(updateRequest)
      val updateResponseAsString: String = responseToString(updateResponse)
      assert(updateResponse.status == StatusCodes.OK, updateResponseAsString)
      assert(JsonParser(updateResponseAsString) == JsonParser(successResponse("Resource metadata updated")))

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-resource-metadata-response",
            fileExtension = "json"
          ),
          text = updateResponseAsString
        )
      )

      val previewRequest = Get(s"$baseApiUrl/v2/resourcespreview/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val previewResponse: HttpResponse = singleAwaitingRequest(previewRequest)
      val previewResponseAsString = responseToString(previewResponse)
      assert(previewResponse.status == StatusCodes.OK, previewResponseAsString)

      val previewJsonLD = JsonLDUtil.parseJsonLD(previewResponseAsString)
      val updatedLabel: String = previewJsonLD.requireString(OntologyConstants.Rdfs.Label)
      assert(updatedLabel == newLabel)
      val updatedPermissions: String = previewJsonLD.requireString(OntologyConstants.KnoraApiV2Complex.HasPermissions)
      assert(
        PermissionUtilADM.parsePermissions(updatedPermissions) == PermissionUtilADM.parsePermissions(newPermissions))

      val lastModificationDate: Instant = previewJsonLD.requireDatatypeValueInObject(
        key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
        expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
        validationFun = stringFormatter.xsdDateTimeStampToInstant
      )

      assert(lastModificationDate == newModificationDate)
      aThingLastModificationDate = newModificationDate
    }

    "update the metadata of a resource that has a last modification date" in {
      val resourceIri = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA"
      val newLabel = "test thing with modified label again"
      val newPermissions = "CR knora-admin:ProjectMember|V knora-admin:ProjectMember"
      val newModificationDate = Instant.now.plus(java.time.Duration.ofDays(1))

      val jsonLDEntity =
        s"""|{
                    |  "@id" : "$resourceIri",
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "update-resource-metadata-request-with-last-mod-date",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val updateRequest = Put(s"$baseApiUrl/v2/resources",
                              HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val updateResponse: HttpResponse = singleAwaitingRequest(updateRequest)
      val updateResponseAsString: String = responseToString(updateResponse)
      assert(updateResponse.status == StatusCodes.OK, updateResponseAsString)
      assert(JsonParser(updateResponseAsString) == JsonParser(successResponse("Resource metadata updated")))

      val previewRequest = Get(s"$baseApiUrl/v2/resourcespreview/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val previewResponse: HttpResponse = singleAwaitingRequest(previewRequest)
      val previewResponseAsString = responseToString(previewResponse)
      assert(previewResponse.status == StatusCodes.OK, previewResponseAsString)

      val previewJsonLD = JsonLDUtil.parseJsonLD(previewResponseAsString)
      val updatedLabel: String = previewJsonLD.requireString(OntologyConstants.Rdfs.Label)
      assert(updatedLabel == newLabel)
      val updatedPermissions: String = previewJsonLD.requireString(OntologyConstants.KnoraApiV2Complex.HasPermissions)
      assert(
        PermissionUtilADM.parsePermissions(updatedPermissions) == PermissionUtilADM.parsePermissions(newPermissions))

      val lastModificationDate: Instant = previewJsonLD.requireDatatypeValueInObject(
        key = OntologyConstants.KnoraApiV2Complex.LastModificationDate,
        expectedDatatype = OntologyConstants.Xsd.DateTimeStamp.toSmartIri,
        validationFun = stringFormatter.xsdDateTimeStampToInstant
      )

      assert(lastModificationDate == newModificationDate)
      aThingLastModificationDate = newModificationDate
    }

    "mark a resource as deleted" in {
      val resourceIri = "http://rdfh.ch/resources/SHnkVt4X2LHAM2nNZVwkoA"

      val jsonLDEntity =
        s"""|{
                    |  "@id" : "$resourceIri",
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "delete-resource-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val updateRequest = Post(s"$baseApiUrl/v2/resources/delete",
                               HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val updateResponse: HttpResponse = singleAwaitingRequest(updateRequest)
      val updateResponseAsString: String = responseToString(updateResponse)
      assert(updateResponse.status == StatusCodes.OK, updateResponseAsString)
      assert(JsonParser(updateResponseAsString) == JsonParser(successResponse("Resource marked as deleted")))

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "delete-resource-response",
            fileExtension = "json"
          ),
          text = updateResponseAsString
        )
      )

      val previewRequest = Get(s"$baseApiUrl/v2/resourcespreview/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val previewResponse: HttpResponse = singleAwaitingRequest(previewRequest)
      val previewResponseAsString = responseToString(previewResponse)
      assert(previewResponse.status == StatusCodes.NotFound, previewResponseAsString)
    }

    "mark a resource as deleted, supplying a custom delete date" in {
      val resourceIri = "http://rdfh.ch/resources/5IEswyQFQp2bxXDrOyEfEA"
      val deleteDate = Instant.now

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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "delete-resource-with-custom-delete-date-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val updateRequest = Post(s"$baseApiUrl/v2/resources/delete",
                               HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(SharedTestDataADM.superUser.email, password))
      val updateResponse: HttpResponse = singleAwaitingRequest(updateRequest)
      val updateResponseAsString: String = responseToString(updateResponse)
      assert(updateResponse.status == StatusCodes.OK, updateResponseAsString)
      assert(JsonParser(updateResponseAsString) == JsonParser(successResponse("Resource marked as deleted")))

      val previewRequest = Get(s"$baseApiUrl/v2/resourcespreview/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val previewResponse: HttpResponse = singleAwaitingRequest(previewRequest)
      val previewResponseAsString = responseToString(previewResponse)
      assert(previewResponse.status == StatusCodes.NotFound, previewResponseAsString)
    }

    "create a resource with a large text containing a lot of markup (32849 words, 6738 standoff tags)" ignore { // uses too much memory for GitHub CI
      // Create a resource containing the text of Hamlet.

      val hamletXml = FileUtil.readTextFile(Paths.get("test_data/resourcesR2RV2/hamlet.xml"))

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

      val resourceCreateRequest = Post(s"$baseApiUrl/v2/resources",
                                       HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val resourceCreateResponse: HttpResponse =
        singleAwaitingRequest(request = resourceCreateRequest, duration = settings.triplestoreUpdateTimeout)
      assert(resourceCreateResponse.status == StatusCodes.OK, resourceCreateResponse.toString)

      val resourceCreateResponseAsJsonLD: JsonLDDocument = responseToJsonLDDocument(resourceCreateResponse)
      val resourceIri: IRI =
        resourceCreateResponseAsJsonLD.body.requireStringWithValidation(JsonLDKeywords.ID,
                                                                        stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)
      hamletResourceIri.set(resourceIri)
    }

    "read the large text and its markup as XML, and check that it matches the original XML" ignore { // depends on previous test
      val hamletXml = FileUtil.readTextFile(Paths.get("test_data/resourcesR2RV2/hamlet.xml"))

      // Request the newly created resource.
      val resourceGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(hamletResourceIri.get, "UTF-8")}") ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val resourceGetResponse: HttpResponse =
        singleAwaitingRequest(resourceGetRequest, duration = settings.triplestoreUpdateTimeout)
      val resourceGetResponseAsString = responseToString(resourceGetResponse)

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
        BasicHttpCredentials(anythingUserEmail, password))
      val resourceGetResponse: HttpResponse = singleAwaitingRequest(resourceGetRequest)
      val resourceGetResponseAsString = responseToString(resourceGetResponse)

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

      val resourceIriEncoded: IRI = URLEncoder.encode(hamletResourceIri.get, "UTF-8")
      val textValueIriEncoded: IRI = URLEncoder.encode(textValueIri, "UTF-8")

      val standoffBuffer: ArrayBuffer[JsonLDObject] = ArrayBuffer.empty
      var offset: Int = 0
      var hasMoreStandoff: Boolean = true

      while (hasMoreStandoff) {
        // Get a page of standoff.

        val standoffGetRequest = Get(s"$baseApiUrl/v2/standoff/$resourceIriEncoded/$textValueIriEncoded/$offset") ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password))
        val standoffGetResponse: HttpResponse = singleAwaitingRequest(standoffGetRequest)
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
      val resourceIri = "http://rdfh.ch/resources/0xfMLw0jVhmBIoAxuTbVxw"
      val resourceLastModificationDate = Instant.parse("2019-02-13T09:05:10Z")

      val jsonLDEntity =
        s"""|{
                    |  "@id" : "$resourceIri",
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

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "erase-resource-request",
            fileExtension = "json"
          ),
          text = jsonLDEntity
        )
      )

      val updateRequest = Post(s"$baseApiUrl/v2/resources/erase",
                               HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(SharedTestDataADM.anythingAdminUser.email, password))
      val updateResponse: HttpResponse = singleAwaitingRequest(updateRequest)
      val updateResponseAsString = responseToString(updateResponse)
      assert(updateResponse.status == StatusCodes.OK, updateResponseAsString)

      val previewRequest = Get(s"$baseApiUrl/v2/resourcespreview/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val previewResponse: HttpResponse = singleAwaitingRequest(previewRequest)
      val previewResponseAsString = responseToString(previewResponse)
      assert(previewResponse.status == StatusCodes.NotFound, previewResponseAsString)
    }

    "create a resource containing a text value with a standoff link" in {
      val jsonLDEntity =
        """{
                  |  "@type": "anything:Thing",
                  |  "anything:hasText": {
                  |    "@type": "knora-api:TextValue",
                  |    "knora-api:textValueAsXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text>\n   This text links to another <a class=\"salsah-link\" href=\"http://rdfh.ch/resources/4ExjVniQehNpL3hQsU3Dgw\">resource</a>.\n</text>",
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

      val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)

      // Request the newly created resource in the complex schema, and check that it matches the ontology.
      val resourceComplexGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val resourceComplexGetResponse: HttpResponse = singleAwaitingRequest(resourceComplexGetRequest)
      val resourceComplexGetResponseAsString = responseToString(resourceComplexGetResponse)

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
          |  "knora-api:textValueAsXml": "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<text>\n   This text links to another <a class=\"salsah-link\" href=\"http://rdfh.ch/resources/4ExjVniQehNpL3hQsU3Dgw\">thing</a> and a <a class=\"salsah-link\" href=\"http://rdfh.ch/resources/QUYp92LBXyOlCTz7CRs24g\">blue thing</a>.\n</text>",
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

      val request = Post(s"$baseApiUrl/v2/resources", HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity)) ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK, response.toString)
      val responseJsonDoc: JsonLDDocument = responseToJsonLDDocument(response)
      val resourceIri: IRI =
        responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, stringFormatter.validateAndEscapeIri)
      assert(resourceIri.toSmartIri.isKnoraDataIri)

      // Request the newly created resource in the complex schema, and check that it matches the ontology.
      val resourceComplexGetRequest = Get(s"$baseApiUrl/v2/resources/${URLEncoder.encode(resourceIri, "UTF-8")}") ~> addCredentials(
        BasicHttpCredentials(anythingUserEmail, password))
      val resourceComplexGetResponse: HttpResponse = singleAwaitingRequest(resourceComplexGetRequest)
      val resourceComplexGetResponseAsString = responseToString(resourceComplexGetResponse)

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
      val resourceIri = "http://rdfh.ch/resources/ZQF1NaDlRt6HgNZh-mXjAw"
      val request = Get(s"$baseApiUrl/v2/resources/iiifmanifest/${URLEncoder.encode(resourceIri, "UTF-8")}")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseStr: String = responseToString(response)
      assert(response.status == StatusCodes.OK, responseStr)
      val responseJson: JsValue = JsonParser(responseStr)

      val expectedJson: JsValue = JsonParser(
        readOrWriteTextFile(responseStr, Paths.get("test_data/resourcesR2RV2/IIIFManifest.jsonld"), writeTestDataFiles))

      assert(responseJson == expectedJson)
    }
  }
}

object ResourcesRouteV2E2ESpec {
  val config: Config = ConfigFactory.parseString("""akka.loglevel = "DEBUG"
          |akka.stdout-loglevel = "DEBUG"
          |app.triplestore.profile-queries = false
        """.stripMargin)
}
