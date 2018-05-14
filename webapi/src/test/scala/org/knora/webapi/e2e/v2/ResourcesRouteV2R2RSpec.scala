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

import akka.actor.{ActorSystem, Props}
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.e2e.v2.ResponseCheckerR2RV2.compareJSONLDForResourcesResponse
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.v2.ResourcesRouteV2
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.util.FileUtil
import org.knora.webapi.{KnoraSystemInstances, LiveActorMaker, R2RSpec, SharedTestDataADM}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor}

/**
  * End-to-end test specification for the search endpoint. This specification uses the Spray Testkit as documented
  * here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class ResourcesRouteV2R2RSpec extends R2RSpec {

    override def testConfigSource: String =
        """
          |# akka.loglevel = "DEBUG"
          |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val resourcesPath = ResourcesRouteV2.knoraApiPath(system, settings, log)

    implicit private val timeout: Timeout = settings.defaultRestoreTimeout

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(15).second)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    private val anythingUser = SharedTestDataADM.anythingUser1

    private val password = "test"

    private val rdfDataObjects = List(

        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")

    )

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 360.seconds)
        Await.result(responderManager ? LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser), 30.seconds)
    }

    "The resources v2 endpoint" should {
        "perform a resource request for the book 'Reise ins Heilige Land' using the complex schema" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/2a6221216701", "UTF-8")}") ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLand.jsonld"))

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema (specified by an HTTP header)" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/2a6221216701", "UTF-8")}").addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.jsonld"))

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a resource request for the book 'Reise ins Heilige Land' using the simple schema (specified by a URL parameter)" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/2a6221216701", "UTF-8")}?${RouteUtilV2.SCHEMA_PARAM}=${RouteUtilV2.SIMPLE_SCHEMA_NAME}") ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/resourcesR2RV2/BookReiseInsHeiligeLandSimple.jsonld"))

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a full resource request for a resource with a BCE date property" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/thing_with_BCE_date", "UTF-8")}") ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/resourcesR2RV2/ThingWithBCEDate.jsonld"))

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform a full resource request for a resource with a date property that represents a period going from BCE to CE" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/thing_with_BCE_date2", "UTF-8")}") ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/resourcesR2RV2/ThingWithBCEDate2.jsonld"))

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform a full resource request for a resource with a list value" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/thing_with_list_value", "UTF-8")}") ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/resourcesR2RV2/ThingWithListValue.jsonld"))

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform a full resource request for a resource with a link (in the complex schema)" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ", "UTF-8")}") ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/resourcesR2RV2/ThingWithLinkComplex.jsonld"))

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a full resource request for a resource with a link (in the simple schema)" in {

            Get(s"/v2/resources/${URLEncoder.encode("http://rdfh.ch/0001/0C-0L1kORryKzJAJxxRyRQ", "UTF-8")}").addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/resourcesR2RV2/ThingWithLinkSimple.jsonld"))

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }
    }

}