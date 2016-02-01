/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.e2e.v1

import akka.actor.{ActorSystem, Props}
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.LiveActorMaker
import org.knora.webapi.e2e.E2ESpec
import org.knora.webapi.messages.v1respondermessages.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.routing.v1.ResourcesRouteV1
import org.knora.webapi.store._
import spray.http.StatusCodes

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * End-to-end test specification for the resources endpoint. This specification uses the Spray Testkit as documented
  * here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class ResourcesV1E2ESpec extends E2ESpec {

    override def testConfigSource =
        """
         # akka.loglevel = "DEBUG"
         # akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val resourcesPath = ResourcesRouteV1.rapierPath(system, settings, log)

    implicit val timeout: Timeout = 300.seconds

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(5).second)

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/images-demo-onto.ttl", name = "http://www.knora.org/ontology/images"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images")
    )

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 300.seconds)
    }

    "The Resources Endpoint" should {
        "provide a HTML representation of the resource properties " in {
            /* Dokubib resource */
            /* Dokubib data is commented out for now because it takes too long to load.
                       Get("/v1/resources.html/http%3A%2F%2Fdata.knora.org%2F0871f7678dbf?noresedit=true&reqtype=properties") ~> resourcesPath ~> check {
                           //log.debug("==>> " + responseAs[String])
                           assert(status === StatusCodes.OK)
                           assert(responseAs[String] contains("preview"))
                           assert(responseAs[String] contains("Title"))
                           assert(responseAs[String] contains("Season"))
                           assert(responseAs[String] contains("Picture"))
                           assert(responseAs[String] contains("Description"))
                       } */

            /* Incunabula resource*/
            Get("/v1/resources.html/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?noresedit=true&reqtype=properties") ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
                assert(responseAs[String] contains ("preview"))
                assert(responseAs[String] contains ("Phyiscal description"))
                assert(responseAs[String] contains ("Location"))
                assert(responseAs[String] contains ("Publication location"))
                assert(responseAs[String] contains ("URI"))
                assert(responseAs[String] contains ("Title"))
                assert(responseAs[String] contains ("Datum der Herausgabe"))
                assert(responseAs[String] contains ("Citation/reference"))
                assert(responseAs[String] contains ("Publisher"))
            }
        }

    }

}
