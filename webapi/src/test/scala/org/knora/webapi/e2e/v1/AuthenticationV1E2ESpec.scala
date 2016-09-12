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
import org.knora.webapi.messages.v1.responder.ontologymessages.LoadOntologiesRequest
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.routing.v1.{AuthenticateRouteV1, ResourcesRouteV1}
import org.knora.webapi.store._
import org.knora.webapi.routing.Authenticator.KNORA_AUTHENTICATION_COOKIE_NAME
import spray.http.HttpHeaders.{Cookie, `Set-Cookie`}
import spray.http._
import spray.httpx.RequestBuilding
import spray.httpx.unmarshalling._
import spray.httpx.SprayJsonSupport._
import spray.json.DefaultJsonProtocol

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Represents a response Knora returns when communicating with the 'v1/session' route during the 'login' operation.
  * @param status is the returned status code.
  * @param message is the returned message.
  * @param sid is the returned session id.
  */
case class SessionResponse(status: Int, message: String, sid: String)

/**
  * A spray-json protocol used for turning the JSON responses from the 'login' operation during communication with the
  * 'v1/session' route into a case classes for easier testing.
  */
object JsonSessionResponseProtocol extends DefaultJsonProtocol {
    implicit val SessionResponseFormat = jsonFormat3(SessionResponse)
}

/**
  * End-to-end test specification for testing authentication using [[AuthenticateRouteV1]]. This specification uses the
  * Spray Testkit as documented here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class AuthenticationV1E2ESpec extends E2ESpec with RequestBuilding {

    override def testConfigSource =
        """
         # akka.loglevel = "DEBUG"
         # akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    import JsonSessionResponseProtocol._

    private val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val authenticatePath = AuthenticateRouteV1.knoraApiPath(system, settings, log)
    private val resourcesPath = ResourcesRouteV1.knoraApiPath(system, settings, log)

    private val incunabulaUser = UserProfileV1(
        projects = Vector("http://data.knora.org/projects/77275339"),
        groups = Nil,
        userData = UserDataV1(
            email = Some("test@test.ch"),
            lastname = Some("Test"),
            firstname = Some("User"),
            username = Some("testuser"),
            token = None,
            user_id = Some("http://data.knora.org/users/b83acc5f05"),
            lang = "de"
        )
    )

    implicit private val timeout: Timeout = 300.seconds

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(5).second)

    private val rdfDataObjects = List(
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
        Await.result(responderManager ? LoadOntologiesRequest(incunabulaUser), 10.seconds)
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
        var sid = ""
        "succeed with 'login' and correct username / correct password " in {
            /* Correct username and correct password */
            Get("/v1/session?login&username=root&password=test") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
                /* store session */
                sid = responseAs[SessionResponse].sid
                assert(header[`Set-Cookie`] === Some(`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, content = sid))))
            }
        }

        "succeed with authentication when using correct session id in cookie" in {
            // authenticate by calling '/v2/session' without parameters but by providing session id in cookie from earlier login
            Get("/v1/session") ~> Cookie(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, sid)) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
            }
        }

        "succeed with 'logout' when providing the session cookie " in {
            // do logout with stored session id
            Get("/v1/session?logout") ~> Cookie(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, sid)) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
                assert(header[`Set-Cookie`] === Some(`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, "deleted", expires = Some(DateTime(1970, 1, 1, 0, 0, 0))))))
            }
        }

        "fail with authentication when providing the session cookie after logout" in {
            Get("/v1/session") ~> Cookie(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, sid)) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }

        "fail with 'login' and correct username / wrong password " in {
            /* Correct username and wrong password */
            Get("/v1/session?login&username=root&password=wrong") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }

        "fail with 'login' and wrong username " in {
            /* wrong username */
            Get("/v1/session?login&username=wrong&password=test") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }

        "fail with authentication when using wrong session id in cookie " in {
            Get("/v1/session") ~> Cookie(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, "123456")) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }
    }

    "The Session Route ('v1/session') with credentials supplied via Basic Auth " should {
        "succeed with 'login' and correct username / correct password " in {
            /* Correct username and correct password */
            Get("/v1/session?login") ~> addCredentials(BasicHttpCredentials("root", "test")) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.OK)
            }
        }

        "fail with 'login' and correct username / wrong password " in {
            /* Correct username and wrong password */
            Get("/v1/session?login") ~> addCredentials(BasicHttpCredentials("root", "wrong")) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
            }
        }

        "fail with 'login' and wrong username " in {
            /* wrong username */
            Get("/v1/session?logint") ~> addCredentials(BasicHttpCredentials("root", "test")) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                assert(status === StatusCodes.Unauthorized)
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
                // assert(status === StatusCodes.OK)
                assert(responseAs[String] contains "\"password\":null")
                assert(responseAs[String] contains "\"token\":null")
            }
        }
    }
}
