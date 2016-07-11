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
import org.knora.webapi.messages.v1.responder.resourcemessages.PropsGetForRegionV1
import org.knora.webapi.messages.v1.responder.resourcemessages.ResourceV1JsonProtocol._
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.routing.v1.ResourcesRouteV1
import org.knora.webapi.store._
import spray.http.MediaTypes._
import spray.http.{HttpEntity, _}
import spray.json._

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

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(15).second)

    val user = "root"
    val password = "test"

    val rdfDataObjects = List(
        RdfDataObject(path = "../knora-ontologies/knora-base.ttl", name = "http://www.knora.org/ontology/knora-base"),
        RdfDataObject(path = "../knora-ontologies/knora-dc.ttl", name = "http://www.knora.org/ontology/dc"),
        RdfDataObject(path = "../knora-ontologies/salsah-gui.ttl", name = "http://www.knora.org/ontology/salsah-gui"),
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/images-demo-onto.ttl", name = "http://www.knora.org/ontology/images"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images"),
        RdfDataObject(path = "_test_data/ontologies/anything-onto.ttl", name = "http://www.knora.org/ontology/anything"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")
    )

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 300.seconds)
    }

    "The Resources Endpoint" should {
        "provide a HTML representation of the resource properties " in {
            /* Incunabula resources*/

            /* A Book without a preview image */
            Get("/v1/resources.html/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?noresedit=true&reqtype=properties") ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
                assert(responseAs[String] contains "Phyiscal description")
                assert(responseAs[String] contains "Location")
                assert(responseAs[String] contains "Publication location")
                assert(responseAs[String] contains "URI")
                assert(responseAs[String] contains "Title")
                assert(responseAs[String] contains "Datum der Herausgabe")
                assert(responseAs[String] contains "Citation/reference")
                assert(responseAs[String] contains "Publisher")
            }

            /* A Page with a preview image */
            Get("/v1/resources.html/http%3A%2F%2Fdata.knora.org%2Fde6c38ce3401?noresedit=true&reqtype=properties") ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
                assert(responseAs[String] contains "preview")
                assert(responseAs[String] contains "Ursprünglicher Dateiname")
                assert(responseAs[String] contains "Page identifier")
            }
        }

        "get the regions of a page when doing a context query with resinfo set to true" in {

            Get("/v1/resources/http%3A%2F%2Fdata.knora.org%2F9d626dc76c03?resinfo=true&reqtype=context") ~> resourcesPath ~> check {

                val response: Map[String, JsValue] = responseAs[String].parseJson.asJsObject.fields

                val resourceContext = response("resource_context").asJsObject.fields

                val resinfo: Map[String, JsValue] = resourceContext("resinfo").asJsObject.fields

                val regions = resinfo.get("regions") match {
                    case Some(JsArray(regionsVector)) =>
                        val regions: Vector[PropsGetForRegionV1] = regionsVector.map(_.convertTo[PropsGetForRegionV1])

                        val region1 = regions.filter {
                            region => region.res_id == "http://data.knora.org/021ec18f1735"
                        }

                        val region2 = regions.filter {
                            region => region.res_id == "http://data.knora.org/b6b64a62b006"
                        }

                        assert(region1.length == 1, "No region found with Iri 'http://data.knora.org/021ec18f1735'")

                        assert(region2.length == 1, "No region found with Iri 'http://data.knora.org/b6b64a62b006'")

                    case None => assert(false, "No regions given, but 2 were expected")
                    case _ => assert(false, "No valid regions given")

                }

                assert(status == StatusCodes.OK, response.toString)
            }
        }

        "create a resource of type images:person" in {

            val params =
            """

              {
              	"restype_id": "http://www.knora.org/ontology/images#person",
              	"label": "Testperson",
              	"project_id": "http://data.knora.org/projects/images",
              	"properties": {
              		"http://www.knora.org/ontology/images#lastname": [{"richtext_value":{"textattr":"{}","resource_reference" :[],"utf8str":"Testname"}}],
                    "http://www.knora.org/ontology/images#firstname": [{"richtext_value":{"textattr":"{}","resource_reference" :[],"utf8str":"Name"}}]
              	}
              }


            """

            Post("/v1/resources", HttpEntity(`application/json`, params)) ~> addCredentials(BasicHttpCredentials(user, password)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

            }

        }

        "create a resource of type anything:Thing" in {
            val params =
            """
              {
              	"restype_id": "http://www.knora.org/ontology/anything#Thing",
              	"label": "A thing",
              	"project_id": "http://data.knora.org/projects/anything",
              	"properties": {
              		"http://www.knora.org/ontology/anything#hasText": [{"richtext_value":{"textattr":"{}","resource_reference" :[],"utf8str":"Test text"}}],
                    "http://www.knora.org/ontology/anything#hasInteger": [{"int_value":12345}],
                    "http://www.knora.org/ontology/anything#hasDecimal": [{"decimal_value":5.6}],
                    "http://www.knora.org/ontology/anything#hasUri": [{"uri_value":"http://dhlab.unibas.ch"}],
                    "http://www.knora.org/ontology/anything#hasDate": [{"date_value":"JULIAN:1291-08-01:1291-08-01"}],
                    "http://www.knora.org/ontology/anything#hasColor": [{"color_value":"#4169E1"}],
                    "http://www.knora.org/ontology/anything#hasListItem": [{"hlist_value":"http://data.knora.org/anything/treeList10"}],
                    "http://www.knora.org/ontology/anything#hasInterval": [{"interval_value": [1000000000000000.0000000000000001, 1000000000000000.0000000000000002]}]
              	}
              }
            """

            // TODO: these properties have been commented out in the thing test ontology because of compatibility with the GUI
            // "http://www.knora.org/ontology/anything#hasGeoname": [{"geoname_value": "2661602"}]
            // "http://www.knora.org/ontology/anything#hasTime": [{"time_value":1.2345}],
            //  "http://www.knora.org/ontology/anything#hasBoolean": [{"boolean_value":true}],
            // "http://www.knora.org/ontology/anything#hasGeometry": [{"geom_value":"{\"status\":\"active\",\"lineColor\":\"#ff3333\",\"lineWidth\":2,\"points\":[{\"x\":0.5516074450084602,\"y\":0.4444444444444444},{\"x\":0.2791878172588832,\"y\":0.5}],\"type\":\"rectangle\",\"original_index\":0}"}],

            Post("/v1/resources", HttpEntity(`application/json`, params)) ~> addCredentials(BasicHttpCredentials(user, password)) ~> resourcesPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

            }
        }

    }

}
