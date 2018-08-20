/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import java.io.File
import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.model.MediaRange
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi._
import org.knora.webapi.e2e.v2.ResponseCheckerR2RV2.compareJSONLDForResourcesResponse
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.v2.ResourcesRouteV2
import org.knora.webapi.util.FileUtil

import scala.concurrent.ExecutionContextExecutor

/**
  * Tests the API v2 resources route.
  */
class ResourcesRouteV2R2RSpec extends R2RSpec {

    override def testConfigSource: String =
        """
          |# akka.loglevel = "DEBUG"
          |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    private val resourcesPath = ResourcesRouteV2.knoraApiPath(system, settings, log)

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    private val anythingUser = SharedTestDataADM.anythingUser1

    private val password = "test"

    // If true, writes all API responses to test data files. If false, compares the API responses to the existing test data files.
    private val writeTestDataFiles = false

    /**
      * Reads or writes a test data file.
      *
      * @param responseAsString the API response received from Knora.
      * @param file             the file in which the expected API response is stored.
      * @param writeFile        if `true`, writes the response to the file and returns it, otherwise returns the current contents of the file.
      * @return the expected response.
      */
    private def readOrWriteTextFile(responseAsString: String, file: File, writeFile: Boolean = false): String = {
        if (writeFile) {
            FileUtil.writeTextFile(file, responseAsString)
            responseAsString
        } else {
            FileUtil.readTextFile(file)
        }
    }

    override lazy val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")

    )

    "The resources v2 endpoint" should {

        "perform a resource request for the book 'Reise ins Heilige Land' using the complex schema in JSON-LD" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/2a6221216701", "UTF-8")}") ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a resource request for the book 'Reise ins Heilige Land' using the complex schema in Turtle" in {

            // Test correct handling of q values in the Accept header.
            val acceptHeader: Accept = Accept(
                MediaRange.One(RdfMediaTypes.`application/ld+json`, 0.5F),
                MediaRange.One(RdfMediaTypes.`text/turtle`, 0.8F),
                MediaRange.One(RdfMediaTypes.`application/rdf+xml`, 0.2F)
            )

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/2a6221216701", "UTF-8")}").addHeader(acceptHeader) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerTurtle = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLand.ttl"), writeTestDataFiles)

                assert(parseTurtle(responseAs[String]) == parseTurtle(expectedAnswerTurtle))

            }
        }

        "perform a resource request for the book 'Reise ins Heilige Land' using the complex schema in RDF/XML" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/2a6221216701", "UTF-8")}").addHeader(Accept(RdfMediaTypes.`application/rdf+xml`)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerRdfXml = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLand.rdf"), writeTestDataFiles)

                assert(parseRdfXml(responseAs[String]) == parseRdfXml(expectedAnswerRdfXml))

            }
        }

        "perform a resource preview request for the book 'Reise ins Heilige Land' using the complex schema" in {

            Get(s"/v2/resourcespreview/${URLEncoder.encode("http://rdfh.ch/2a6221216701", "UTF-8")}") ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                // println(responseAs[String])

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLandPreview.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema (specified by an HTTP header) in JSON-LD" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/2a6221216701", "UTF-8")}").addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema in Turtle" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/2a6221216701", "UTF-8")}").
                addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)).
                addHeader(Accept(RdfMediaTypes.`text/turtle`)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerTurtle = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.ttl"), writeTestDataFiles)

                assert(parseTurtle(responseAs[String]) == parseTurtle(expectedAnswerTurtle))
            }
        }

        "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema in RDF/XML" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/2a6221216701", "UTF-8")}").
                addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)).
                addHeader(Accept(RdfMediaTypes.`application/rdf+xml`)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerRdfXml = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.rdf"), writeTestDataFiles)

                assert(parseRdfXml(responseAs[String]) == parseRdfXml(expectedAnswerRdfXml))

            }
        }


        "perform a resource preview request for the book 'Reise ins Heilige Land' using the simple schema (specified by an HTTP header)" in {

            Get(s"/v2/resourcespreview/${URLEncoder.encode("http://rdfh.ch/2a6221216701", "UTF-8")}").addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLandSimplePreview.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])
            }
        }

        "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema (specified by a URL parameter)" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/2a6221216701", "UTF-8")}?${RouteUtilV2.SCHEMA_PARAM}=${RouteUtilV2.SIMPLE_SCHEMA_NAME}") ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a full resource request for a resource with a BCE date property" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/thing_with_BCE_date", "UTF-8")}") ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/ThingWithBCEDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform a full resource request for a resource with a date property that represents a period going from BCE to CE" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/thing_with_BCE_date2", "UTF-8")}") ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/ThingWithBCEDate2.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform a full resource request for a resource with a list value" ignore { // disabled because the language in which the label is returned is not deterministic

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/thing_with_list_value", "UTF-8")}") ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/ThingWithListValue.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform a full resource request for a resource with a link (in the complex schema)" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ", "UTF-8")}") ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/ThingWithLinkComplex.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a full resource request for a resource with a link (in the simple schema)" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ", "UTF-8")}").addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/ThingWithLinkSimple.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a full resource request for a resource with a Text language (in the complex schema)" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/a-thing-with-text-valuesLanguage", "UTF-8")}") ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/ThingWithTextLangComplex.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }


        "perform a full resource request for a resource with a Text language (in the simple schema)" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/a-thing-with-text-valuesLanguage", "UTF-8")}").addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/resourcesR2RV2/ThingWithTextLangSimple.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }
    }

}