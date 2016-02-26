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
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.LiveActorMaker
import org.knora.webapi.e2e.E2ESpec
import org.knora.webapi.messages.v1respondermessages.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.routing.v1.{ResourcesRouteV1, AuthenticateRouteV1}
import org.knora.webapi.store._

import spray.http.{BasicHttpCredentials, StatusCodes}
import spray.httpx.RequestBuilding

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * End-to-end test specification for testing authentication using [[AuthenticateRouteV1]]. This specification uses the
  * Spray Testkit as documented here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class AuthenticationV1E2ESpec extends E2ESpec with RequestBuilding {

    override def testConfigSource =
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    val authenticatePath = AuthenticateRouteV1.rapierPath(system, settings, log)
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

    "The Authentication Route ('v1/authenticate') with credentials supplied via URL parameters " should {
        "succeed with authentication and correct username / correct password " in {
            /* Correct username and password */
            Get("/v1/authenticate?username=root&password=test") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
            }
        }
        "fail with authentication and correct username / wrong password " in {
            /* Correct username / wrong password */
            Get("/v1/authenticate?username=root&password=wrong") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }
    }
    "The Authentication Route ('v1/authenticate') with credentials supplied via Basic Auth " should {
        "succeed with authentication and correct username / correct password " in {
            /* Correct username / correct password */
            Get("/v1/authenticate") ~> addCredentials(BasicHttpCredentials("root", "test")) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
            }
        }
        "fail with authentication and correct username / wrong password " in {
            /* Correct username / wrong password */
            Get("/v1/authenticate") ~> addCredentials(BasicHttpCredentials("root", "wrong")) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }
    }

    "The Session Route ('v1/session') with credentials supplied via URL parameters " should {
        "succeed with 'login' and correct username / correct password " in {
            /* Correct username and password */
            Get("/v1/session?login&username=root&password=test") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
            }
        }
        "fail with 'login' and correct username / wrong password " in {

        }
        "fail with 'login' and wrong username " in {

        }
        "succed with authentication using correct session id " in {
            // do login and store session id
            // authenticate by calling '/v2/session' without parameters but by providing session id in cookie
        }
        "succeed with 'logout' when providing the session cookie " in {
            // do login and store session id
            // do logout with stored session id
        }
    }

    "The Session Route ('v1/session') with credentials supplied via Basic Auth " should {
        "succeed with 'login' and correct username / correct password " in {
            /* Correct username and password */
            Get("/v1/session?login") ~> addCredentials(BasicHttpCredentials("root", "test")) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
            }
        }
    }

    "The Resources Route using the Authenticator trait " should {
        "succeed with authentication using URL parameters and correct username / correct password " in {
            /* Correct username / correct password */
            Get("/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?username=root&password=test") ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
            }
        }
        "fail with authentication using URL parameters and correct username / wrong password " in {
            /* Correct username / wrong password */
            Get("/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?username=root&password=wrong") ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }
        "succeed with authentication using HTTP Basic Auth headers and correct username / correct password " in {
            /* Correct username / correct password */
            Get("/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a") ~> addCredentials(BasicHttpCredentials("root", "test")) ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
            }
        }
        "fail with authentication using HTTP Basic Auth headers and correct username / wrong password " in {
            /* Correct username / wrong password */
            Get("/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a") ~> addCredentials(BasicHttpCredentials("root", "wrong")) ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }
        "not return sensitive information (token, password) in the response " in {
            Get("/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?username=root&password=test") ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
                assert(responseAs[String] contains "\"password\":null")
                assert(responseAs[String] contains "\"token\":null")
            }
        }
    }
}
