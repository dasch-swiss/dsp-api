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

package org.knora.webapi.e2e.v1

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{`Set-Cookie`, _}
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.{SessionJsonProtocol, SessionResponse}
import org.knora.webapi.routing.Authenticator.KNORA_AUTHENTICATION_COOKIE_NAME
import org.knora.webapi.{E2ESpec, SharedAdminTestData}
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._


object AuthenticationV1E2ESpec {
    val config: Config = ConfigFactory.parseString(
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

    private implicit def default(implicit system: ActorSystem) = RouteTestTimeout(30.seconds)

    private val rdfDataObjects = List.empty[RdfDataObject]

    private val rootIri = SharedAdminTestData.rootUser.userData.user_id.get
    private val rootIriEnc = java.net.URLEncoder.encode(rootIri, "utf-8")
    private val rootEmail = SharedAdminTestData.rootUser.userData.email.get
    private val rootEmailEnc = java.net.URLEncoder.encode(rootEmail, "utf-8")
    private val inactiveUserEmailEnc = java.net.URLEncoder.encode(SharedAdminTestData.inactiveUser.userData.email.get, "utf-8")
    private val wrongEmail = "wrong@example.com"
    private val wrongEmailEnc = java.net.URLEncoder.encode(wrongEmail, "utf-8")
    private val testPass = java.net.URLEncoder.encode("test", "utf-8")
    private val wrongPass = java.net.URLEncoder.encode("wrong", "utf-8")

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "The Authentication Route ('v1/authenticate') with credentials supplied via URL parameters" should {

        "succeed with authentication and correct email / correct password" in {
            /* Correct username and password */
            val request = Get(baseApiUrl + s"/v1/authenticate?email=$rootEmailEnc&password=$testPass")
            val response: HttpResponse = singleAwaitingRequest(request)
            log.debug(s"response: ${response.toString}")
            assert(response.status === StatusCodes.OK)
        }

        "fail with authentication and correct email / wrong password" in {
            /* Correct email / wrong password */
            val request = Get(baseApiUrl + s"/v1/authenticate?email=$rootEmail&password=$wrongPass")
            val response: HttpResponse = singleAwaitingRequest(request)
            log.debug(s"response: ${response.toString}")
            assert(response.status === StatusCodes.Unauthorized)
        }
        "fail with authentication if the user is set as 'not active' " in {
            /* User not active */
            val request = Get(baseApiUrl + s"/v1/authenticate?email=$inactiveUserEmailEnc&password=$testPass")
            val response: HttpResponse = singleAwaitingRequest(request)
            log.debug(s"response: ${response.toString}")
            assert(response.status === StatusCodes.Unauthorized)
        }
    }

    "The Authentication Route ('v1/authenticate') with credentials supplied via Basic Auth" should {

        "succeed with authentication and correct email / correct password" in {
            /* Correct email / correct password */
            val request = Get(baseApiUrl + "/v1/authenticate") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
            val response = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK)
        }

        "fail with authentication and correct email / wrong password" in {
            /* Correct username / wrong password */
            val request = Get(baseApiUrl + "/v1/authenticate") ~> addCredentials(BasicHttpCredentials(rootEmail, wrongPass))
            val response = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.Unauthorized)
        }
    }

    "The Session Route ('v1/session') with credentials supplied via URL parameters" should {
        var sid = ""
        "succeed with 'login' and correct email / correct password" in {
            /* Correct username and correct password */
            val request = Get(baseApiUrl + s"/v1/session?login&email=$rootEmailEnc&password=$testPass")
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK)

            //println(response.toString)

            val sr: SessionResponse = Await.result(Unmarshal(response.entity).to[SessionResponse], 1.seconds)
            sid = sr.sid

            assert(response.headers.contains(`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, value = sid, path = Some("/")))))

            /* check for sensitive information leakage */
            val body: String = Await.result(Unmarshal(response.entity).to[String], 1.seconds)
            assert(body contains "\"password\":null")
            assert(body contains "\"token\":null")
        }

        "not return sensitive information (token, password) in the response when checking session" in {
            val request = Get(baseApiUrl + s"/v1/session") ~> Cookie(KNORA_AUTHENTICATION_COOKIE_NAME, value = sid)
            val response = singleAwaitingRequest(request)
            assert(response.status === StatusCodes.OK)

            //println(response.toString)

            val body: String = Await.result(Unmarshal(response.entity).to[String], 1.seconds)
            assert(body contains "\"password\":null")
            assert(body contains "\"token\":null")
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

        "fail with 'login' and correct email / wrong password" in {
            /* Correct username and wrong password */
            val request = Get(baseApiUrl + s"/v1/session?login&email=$rootEmailEnc&password=$wrongPass")
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }

        "fail with 'login' and wrong username" in {
            /* wrong username */
            val request = Get(baseApiUrl + s"/v1/session?login&email=$wrongEmailEnc&password=$testPass")
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }

        "fail with authentication when using wrong session id in cookie" in {
            val request = Get(baseApiUrl + "/v1/session") ~> Cookie(KNORA_AUTHENTICATION_COOKIE_NAME, "123456")
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }
    }

    "The Session Route ('v1/session') with credentials supplied via Basic Auth" should {
        var sid = ""
        "succeed with 'login' and correct email / correct password" in {
            /* Correct username and correct password */
            val request = Get(baseApiUrl + "/v1/session?login") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.OK)

            val sr: SessionResponse = Await.result(Unmarshal(response.entity).to[SessionResponse], 1.seconds)
            sid = sr.sid

            assert(response.headers.contains(`Set-Cookie`(HttpCookie(KNORA_AUTHENTICATION_COOKIE_NAME, value = sid, path = Some("/")))))

            /* check for sensitive information leakage */
            val body: String = Await.result(Unmarshal(response.entity).to[String], 1.seconds)
            assert(body contains "\"password\":null")
            assert(body contains "\"token\":null")
        }

        "not return sensitive information (token, password) in the response when checking session" in {
            val request = Get(baseApiUrl + s"/v1/session") ~> Cookie(KNORA_AUTHENTICATION_COOKIE_NAME, sid)
            val response = singleAwaitingRequest(request)
            assert(response.status === StatusCodes.OK)

            //println(response.toString)

            val body: String = Await.result(Unmarshal(response.entity).to[String], 1.seconds)
            assert(body contains "\"password\":null")
            assert(body contains "\"token\":null")
        }

        "fail with 'login' and correct email / wrong password " in {
            /* Correct username and wrong password */
            val request = Get(baseApiUrl + "/v1/session?login") ~> addCredentials(BasicHttpCredentials(rootEmail, wrongPass))
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }

        "fail with 'login' and wrong email " in {
            /* wrong username */
            val request = Get(baseApiUrl + "/v1/session?login") ~> addCredentials(BasicHttpCredentials(wrongEmail, testPass))
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }
    }

    "The Users Route using the Authenticator trait " should {
        "succeed with authentication using URL parameters and correct email / correct password " in {
            /* Correct email / correct password */
            val request = Get(baseApiUrl + s"/v1/users/$rootIriEnc?email=$rootEmailEnc&password=$testPass")
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.OK)
        }

        "fail with authentication using URL parameters and correct email / wrong password " in {
            /* Correct email / wrong password */
            val request = Get(baseApiUrl + s"/v1/users/$rootIriEnc?email=$rootEmailEnc&password=$wrongPass")

            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }

        "succeed with authentication using HTTP Basic Auth headers and correct username / correct password " in {
            /* Correct email / correct password */
            val request = Get(baseApiUrl + s"/v1/users/$rootIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.OK)
        }

        "fail with authentication using HTTP Basic Auth headers and correct username / wrong password " in {
            /* Correct email / wrong password */
            val request = Get(baseApiUrl + s"/v1/users/$rootIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, wrongPass))
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }

        "not return sensitive information (token, password) in the response " in {
            val request = Get(baseApiUrl + s"/v1/users/$rootIriEnc?email=$rootEmailEnc&password=$testPass")
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            // assert(status === StatusCodes.OK)

            /* check for sensitive information leakage */
            val body: String = Await.result(Unmarshal(response.entity).to[String], 1.seconds)
            assert(body contains "\"password\":null")
            assert(body contains "\"token\":null")
        }
    }
}
