/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.e2e.v1

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.util.Timeout
import ch.megard.akka.http.cors.CorsRejection
import org.knora.webapi.LiveActorMaker
import org.knora.webapi.e2e.E2ESpec
import org.knora.webapi.http.CORSSupport
import org.knora.webapi.http.CORSSupport.CORS
import org.knora.webapi.messages.v1.store.triplestoremessages.RdfDataObject
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.routing.v1.{ResourcesRouteV1, StoreRouteV1}
import org.knora.webapi.store._

import scala.concurrent.duration._

/**
  * End-to-end test specification for testing [[StoreRouteV1]]. This specification uses the
  * Spray Testkit as documented here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class CORSSupportV1E2ESpec extends E2ESpec with RequestBuilding {

    override def testConfigSource =
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    /* Start a live ResponderManager */
    private val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)

    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula")
    )

    private val rdfDataObjectsJsonList =
        """
            [
                {"path": "../knora-ontologies/knora-base.ttl", "name": "http://www.knora.org/ontology/knora-base"},
                {"path": "../knora-ontologies/knora-dc.ttl", "name": "http://www.knora.org/ontology/dc"},
                {"path": "../knora-ontologies/salsah-gui.ttl", "name": "http://www.knora.org/ontology/salsah-gui"},
                {"path": "_test_data/ontologies/incunabula-onto.ttl", "name": "http://www.knora.org/ontology/incunabula"},
                {"path": "_test_data/all_data/incunabula-data.ttl", "name": "http://www.knora.org/data/incunabula"}
            ]
        """

    /* get the path of the route we want to test */
    private val resourcesRoute = ResourcesRouteV1.knoraApiPath(system, settings, log)

    /* set the timeout for the route test */
    implicit private val timeout: Timeout = 180.seconds
    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(180).second)

    val exampleOrigin = HttpOrigin("http://example.com")
    val corsSettings = CORSSupport.corsSettings

    "A Route with enabled CORS support " should {

        "accept valid pre-flight requests" in {

            Options() ~> Origin(exampleOrigin) ~> `Access-Control-Request-Method`(GET) ~> {
                CORS(resourcesRoute)
            } ~> check {
                responseAs[String] shouldBe empty
                status shouldBe StatusCodes.OK
                response.headers should contain theSameElementsAs Seq(
                    `Access-Control-Allow-Origin`(exampleOrigin),
                    `Access-Control-Allow-Methods`(corsSettings.allowedMethods),
                    //`Access-Control-Allow-Headers`("Origin, X-Requested-With, Content-Type, Accept, Authorization"),
                    `Access-Control-Max-Age`(1800),
                    `Access-Control-Allow-Credentials`(true)
                )
            }
        }

        "reject pre-flight requests with invalid method" in {

            val invalidMethod = PATCH
            Options() ~> Origin(exampleOrigin) ~> `Access-Control-Request-Method`(invalidMethod) ~> {
                CORS(resourcesRoute)
            } ~> check {
                rejection shouldBe CorsRejection(None, Some(invalidMethod), None)
            }
        }

    }
}
