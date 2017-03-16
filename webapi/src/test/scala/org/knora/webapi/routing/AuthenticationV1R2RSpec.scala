/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.routing

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.model.headers.{BasicHttpCredentials, Cookie, HttpCookie, `Set-Cookie`}
import akka.http.scaladsl.model.{DateTime, StatusCodes}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.messages.v1.responder.ontologymessages.LoadOntologiesRequest
import org.knora.webapi.messages.v1.responder.sessionmessages.{SessionJsonProtocol, SessionResponse}
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.responders._
import org.knora.webapi.responders.v1.ResponderManagerV1
import org.knora.webapi.routing.Authenticator.KNORA_AUTHENTICATION_COOKIE_NAME
import org.knora.webapi.routing.v1.{AuthenticateRouteV1, ResourcesRouteV1}
import org.knora.webapi.store._
import org.knora.webapi.{LiveActorMaker, R2RSpec, SharedAdminTestData}

import scala.concurrent.Await
import scala.concurrent.duration._


/**
  * Route-to-Responder (R2R) test specification for testing authentication using [[AuthenticateRouteV1]]. This
  * specification uses the Spray Testkit as documented here: http://spray.io/documentation/1.2.2/spray-testkit/
  *
  * This spec tests the 'v1/authentication' and 'v1/session' route.
  */
class AuthenticationV1R2RSpec extends R2RSpec with SessionJsonProtocol {

    override def testConfigSource =
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    private val responderManager = system.actorOf(Props(new ResponderManagerV1 with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val authenticatePath = AuthenticateRouteV1.knoraApiPath(system, settings, log)
    private val resourcesPath = ResourcesRouteV1.knoraApiPath(system, settings, log)

    private implicit val timeout: Timeout = 300.seconds

    private implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds)

    private val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images")
    )

    private val rootEmail = SharedAdminTestData.rootUser.userData.email.get
    private val rootEmailEnc = java.net.URLEncoder.encode(rootEmail, "utf-8")
    private val inactiveUser = java.net.URLEncoder.encode(SharedAdminTestData.inactiveUser.userData.email.get, "utf-8")
    private val wrongEmail = "wrong@example.com"
    private val wrongEmailEnc = java.net.URLEncoder.encode(wrongEmail, "utf-8")
    private val testPass = java.net.URLEncoder.encode("test", "utf-8")
    private val wrongPass = java.net.URLEncoder.encode("wrong", "utf-8")

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 300.seconds)
        Await.result(responderManager ? LoadOntologiesRequest(SharedAdminTestData.rootUser), 10.seconds)
    }
    "The Authentication Route ('v1/authenticate') when accessed with credentials supplied via URL parameters " should {

        "succeed with authentication and correct email / correct password " in {
            /* Correct email and password */
            Get(s"/v1/authenticate?email=$rootEmailEnc&password=$testPass") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.OK)
            }
        }

        "fail with authentication and correct email / wrong password " in {
            /* Correct username / wrong password */
            Get(s"/v1/authenticate?email=$rootEmailEnc&password=$wrongPass") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.Unauthorized)
            }
        }

        /* not supported in this branch */
        "fail with authentication if the user is set as 'not active' " in {
            /* User not active */
            Get(s"/v1/authenticate?email=$inactiveUser&password=$testPass") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.Unauthorized)
            }
        }
    }
    "The Authentication Route ('v1/authenticate') when accessed with credentials supplied via Basic Auth " should {

        "succeed with authentication and correct email / correct password " in {
            /* Correct username / correct password */
            Get("/v1/authenticate") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass)) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal (StatusCodes.OK)
            }
        }

        "fail with authentication and correct email / wrong password " in {
            /* Correct username / wrong password */
            Get("/v1/authenticate") ~> addCredentials(BasicHttpCredentials(rootEmail, wrongPass)) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.Unauthorized)
            }
        }

    }
    "The Session Route ('v1/session') when accessed with credentials supplied via URL parameters " should {
        var sid: String = ""

        "succeed with 'login' and correct email / correct password " in {
            /* Correct username and correct password */
            Get(s"/v1/session?login&email=$rootEmailEnc&password=$testPass") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.OK)
                /* store session */
                sid = Await.result(Unmarshal(response.entity).to[SessionResponse], 1.seconds).sid
                header[`Set-Cookie`] should equal(Some(`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, sid))))
            }
        }

        "succeed with authentication when using correct session id in cookie" in {
            // authenticate by calling '/v2/session' without parameters but by providing session id in cookie from earlier login
            Get("/v1/session") ~> Cookie(KNORA_AUTHENTICATION_COOKIE_NAME, sid) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.OK)
            }
        }

        "succeed with 'logout' when providing the session cookie " in {
            // do logout with stored session id
            Get("/v1/session?logout") ~> Cookie(KNORA_AUTHENTICATION_COOKIE_NAME, sid) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.OK)
                header[`Set-Cookie`] should equal(Some(`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, "deleted", expires = Some(DateTime(1970, 1, 1, 0, 0, 0))))))
            }
        }

        "fail with authentication when providing the session cookie after logout" in {
            Get("/v1/session") ~> Cookie(KNORA_AUTHENTICATION_COOKIE_NAME, sid) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.Unauthorized)
            }
        }

        "fail with 'login' and correct username / wrong password " in {
            /* Correct username and wrong password */
            Get(s"/v1/session?login&email=$rootEmailEnc&password=$wrongPass") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.Unauthorized)
            }
        }

        "fail with 'login' and wrong username " in {
            /* wrong username */
            Get(s"/v1/session?login&email=$wrongEmailEnc&password=$testPass") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.Unauthorized)
            }
        }

        "fail with authentication when using wrong session id in cookie " in {
            Get("/v1/session") ~> Cookie(KNORA_AUTHENTICATION_COOKIE_NAME, "123456") ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.Unauthorized)
            }
        }

    }

    "The Session Route ('v1/session') when accessed with credentials supplied via Basic Auth " should {

        "succeed with 'login' and correct email / correct password " in {
            /* Correct email and correct password */
            Get("/v1/session?login") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass)) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.OK)
            }
        }

        "fail with 'login' and correct email / wrong password " in {
            /* Correct email and wrong password */
            Get("/v1/session?login") ~> addCredentials(BasicHttpCredentials(rootEmail, wrongPass)) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.Unauthorized)
            }
        }

        "fail with 'login' and wrong email " in {
            /* wrong email */
            Get("/v1/session?login") ~> addCredentials(BasicHttpCredentials(wrongEmail, testPass)) ~> authenticatePath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.Unauthorized)
            }
        }

    }

    "The Resources Route using the Authenticator trait when accessed " should {

        "succeed with authentication using URL parameters and correct email / correct password " in {
            /* Correct username / correct password */
            Get(s"/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?email=$rootEmailEnc&password=$testPass") ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.OK)
            }
        }

        "fail with authentication using URL parameters and correct email / wrong password " in {
            /* Correct username / wrong password */
            Get(s"/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?email=$rootEmailEnc&password=$wrongPass") ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.Unauthorized)
            }
        }

        "succeed with authentication using HTTP Basic Auth headers and correct email / correct password " in {
            /* Correct username / correct password */
            Get("/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass)) ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.OK)
            }
        }

        "fail with authentication using HTTP Basic Auth headers and correct email / wrong password " in {
            /* Correct username / wrong password */
            Get("/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a") ~> addCredentials(BasicHttpCredentials(rootEmail, wrongPass)) ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                status should equal(StatusCodes.Unauthorized)
            }
        }

        "not return sensitive information (token, password) in the response " in {
            Get(s"/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?email=$rootEmailEnc&password=$testPass") ~> resourcesPath ~> check {
                //log.debug("==>> " + responseAs[String])
                // assert(status === StatusCodes.OK)
                assert(!responseAs[String].contains("\"password\""))
                assert(!responseAs[String].contains("\"token\""))
            }
        }

    }

}
