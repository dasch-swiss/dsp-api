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

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Set-Cookie`, _}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.ConfigFactory
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.v1.responder.sessionmessages.{SessionJsonProtocol, SessionResponse}
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.routing.Authenticator.KNORA_AUTHENTICATION_COOKIE_NAME
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._


object AuthenticationV1E2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing authentication.
  *
  * This spec tests the 'v1/authentication' and 'v1/session' route.
  */
class AuthenticationV1E2ESpec extends E2ESpec(AuthenticationV1E2ESpec.config) with SessionJsonProtocol with TriplestoreJsonProtocol {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds)

    private val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/ontologies/incunabula-onto.ttl", name = "http://www.knora.org/ontology/incunabula"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/ontologies/images-demo-onto.ttl", name = "http://www.knora.org/ontology/images"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images")
    )

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "The Authentication Route ('v1/authenticate') with credentials supplied via URL parameters" should {

        "succeed with authentication and correct username / correct password" in {
            /* Correct username and password */
            val request = Get(baseApiUrl + "/v1/authenticate?username=root&password=test")
            val response: HttpResponse = singleAwaitingRequest(request)
            log.debug(s"response: ${response.toString}")
            assert(response.status === StatusCodes.OK)
        }

        "fail with authentication and correct username / wrong password" in {
            /* Correct username / wrong password */
            val request = Get(baseApiUrl + "/v1/authenticate?username=root&password=wrong")
            val response: HttpResponse = singleAwaitingRequest(request)
            log.debug(s"response: ${response.toString}")
            assert(response.status === StatusCodes.Unauthorized)
        }
        "fail with authentication if the user is set as 'not active' " in {
            /* User not active */
            val request = Get(baseApiUrl + "/v1/authenticate?username=inactiveuser&password=test")
            val response: HttpResponse = singleAwaitingRequest(request)
            log.debug(s"response: ${response.toString}")
            assert(response.status === StatusCodes.Unauthorized)
        }
    }

    "The Authentication Route ('v1/authenticate') with credentials supplied via Basic Auth" should {

        "succeed with authentication and correct username / correct password" in {
            /* Correct username / correct password */
            val request = Get(baseApiUrl + "/v1/authenticate") ~> addCredentials(BasicHttpCredentials("root", "test"))
            val response = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK)
        }

        "fail with authentication and correct username / wrong password" in {
            /* Correct username / wrong password */
            val request = Get(baseApiUrl + "/v1/authenticate") ~> addCredentials(BasicHttpCredentials("root", "wrong"))
            val response = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.Unauthorized)
        }
    }

    "The Session Route ('v1/session') with credentials supplied via URL parameters" should {
        var sid = ""
        "succeed with 'login' and correct username / correct password" in {
            /* Correct username and correct password */
            val request = Get(baseApiUrl + "/v1/session?login&username=root&password=test")
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK)

            log.debug(response.toString)

            val sr: SessionResponse = Await.result(Unmarshal(response.entity).to[SessionResponse], 1.seconds)
            sid = sr.sid

            assert(response.headers.contains(`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, value = sid))))
        }

        "succeed with authentication when using correct session id in cookie" in {
            // authenticate by calling '/v1/session' without parameters but by providing session id in cookie from earlier login
            val request = Get(baseApiUrl + "/v1/session") ~> Cookie(KNORA_AUTHENTICATION_COOKIE_NAME, sid)
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.OK)

        }

        "succeed with 'logout' when providing the session cookie" in {
            // do logout with stored session id
            val request = Get(baseApiUrl + "/v1/session?logout") ~> Cookie(KNORA_AUTHENTICATION_COOKIE_NAME, sid)
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.OK)
            assert(response.headers.contains(`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, "deleted", expires = Some(DateTime(1970, 1, 1, 0, 0, 0))))))
        }

        "fail with authentication when providing the session cookie after logout" in {
            val request = Get(baseApiUrl + "/v1/session") ~> Cookie(KNORA_AUTHENTICATION_COOKIE_NAME, sid)
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }

        "fail with 'login' and correct username / wrong password" in {
            /* Correct username and wrong password */
            val request = Get(baseApiUrl + "/v1/session?login&username=root&password=wrong")
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }

        "fail with 'login' and wrong username" in {
            /* wrong username */
            val request = Get(baseApiUrl + "/v1/session?login&username=userwrong&password=test")
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.NotFound)
        }

        "fail with authentication when using wrong session id in cookie" in {
            val request = Get(baseApiUrl + "/v1/session") ~> Cookie(KNORA_AUTHENTICATION_COOKIE_NAME, "123456")
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }
    }

    "The Session Route ('v1/session') with credentials supplied via Basic Auth" should {
        "succeed with 'login' and correct username / correct password" in {
            /* Correct username and correct password */
            val request = Get(baseApiUrl + "/v1/session?login") ~> addCredentials(BasicHttpCredentials("root", "test"))
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.OK)
        }

       "fail with 'login' and correct username / wrong password " in {
           /* Correct username and wrong password */
           val request = Get(baseApiUrl + "/v1/session?login") ~> addCredentials(BasicHttpCredentials("root", "wrong"))
           val response = singleAwaitingRequest(request)
           //log.debug("==>> " + responseAs[String])
           assert(response.status === StatusCodes.Unauthorized)
       }

        "fail with 'login' and wrong username " in {
            /* wrong username */
            val request = Get(baseApiUrl + "/v1/session?login") ~> addCredentials(BasicHttpCredentials("wrong", "test"))
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.NotFound)
        }
    }

   "The Resources Route using the Authenticator trait " should {
       "succeed with authentication using URL parameters and correct username / correct password " in {
           /* Correct username / correct password */
           val request = Get(baseApiUrl + "/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?username=root&password=test")
               val response = singleAwaitingRequest(request)
               //log.debug("==>> " + responseAs[String])
               assert(response.status === StatusCodes.OK)
       }

       "fail with authentication using URL parameters and correct username / wrong password " in {
           /* Correct username / wrong password */
           val request = Get(baseApiUrl + "/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?username=root&password=wrong")

               val response = singleAwaitingRequest(request)
               //log.debug("==>> " + responseAs[String])
               assert(response.status === StatusCodes.Unauthorized)
       }

       "succeed with authentication using HTTP Basic Auth headers and correct username / correct password " in {
           /* Correct username / correct password */
           val request = Get(baseApiUrl + "/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a") ~> addCredentials(BasicHttpCredentials("root", "test"))
               val response = singleAwaitingRequest(request)
               //log.debug("==>> " + responseAs[String])
               assert(response.status === StatusCodes.OK)
       }

       "fail with authentication using HTTP Basic Auth headers and correct username / wrong password " in {
           /* Correct username / wrong password */
           val request = Get(baseApiUrl + "/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a") ~> addCredentials(BasicHttpCredentials("root", "wrong"))
               val response = singleAwaitingRequest(request)
               //log.debug("==>> " + responseAs[String])
               assert(response.status === StatusCodes.Unauthorized)
       }

       "not return sensitive information (token, password) in the response " in {
           val request = Get(baseApiUrl + "/v1/resources/http%3A%2F%2Fdata.knora.org%2Fc5058f3a?username=root&password=test")
               val response = singleAwaitingRequest(request)
               //log.debug("==>> " + responseAs[String])
               // assert(status === StatusCodes.OK)
               val body: String = Await.result(Unmarshal(response.entity).to[String], 1.seconds)
               assert(body contains "\"password\":null")
               assert(body contains "\"token\":null")
       }
    }
}
