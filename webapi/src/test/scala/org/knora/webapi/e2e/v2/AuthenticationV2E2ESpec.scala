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

package org.knora.webapi.e2e.v2

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.{AuthenticationV2JsonProtocol, LoginResponse}
import org.knora.webapi.{E2ESpec, SharedTestDataV1}
import spray.json._

import scala.concurrent.Await
import scala.concurrent.duration._


object AuthenticationV2E2ESpec {
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
class AuthenticationV2E2ESpec extends E2ESpec(AuthenticationV2E2ESpec.config) with AuthenticationV2JsonProtocol with TriplestoreJsonProtocol {

    private implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds)

    implicit override lazy val log = akka.event.Logging(system, this.getClass())

    private val rdfDataObjects = List[RdfDataObject](
        // RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        // RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images")
    )

    private val rootIri = SharedTestDataV1.rootUser.userData.user_id.get
    private val rootIriEnc = java.net.URLEncoder.encode(rootIri, "utf-8")
    private val rootEmail = SharedTestDataV1.rootUser.userData.email.get
    private val rootEmailEnc = java.net.URLEncoder.encode(rootEmail, "utf-8")
    private val inactiveUserEmailEnc = java.net.URLEncoder.encode(SharedTestDataV1.inactiveUser.userData.email.get, "utf-8")
    private val wrongEmail = "wrong@example.com"
    private val wrongEmailEnc = java.net.URLEncoder.encode(wrongEmail, "utf-8")
    private val testPass = java.net.URLEncoder.encode("test", "utf-8")
    private val wrongPass = java.net.URLEncoder.encode("wrong", "utf-8")

    "Load test data" in {
        // send POST to 'v1/store/ResetTriplestoreContent'
        val request = Post(baseApiUrl + "/v1/store/ResetTriplestoreContent", HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint))
        singleAwaitingRequest(request, 300.seconds)
    }

    "The Authentication Route ('v2/authentication') with credentials supplied via URL parameters" should {

        "authenticate with correct email and password" in {
            /* Correct username and password */
            val request = Get(baseApiUrl + s"/v2/authentication?email=$rootEmailEnc&password=$testPass")
            val response: HttpResponse = singleAwaitingRequest(request)
            log.debug(s"response: ${response.toString}")
            assert(response.status === StatusCodes.OK)
        }

        "fail authentication with correct email and wrong password" in {
            /* Correct email / wrong password */
            val request = Get(baseApiUrl + s"/v2/authentication?email=$rootEmail&password=$wrongPass")
            val response: HttpResponse = singleAwaitingRequest(request)
            log.debug(s"response: ${response.toString}")
            assert(response.status === StatusCodes.Unauthorized)
        }
        "fail authentication with the user set as 'not active' " in {
            /* User not active */
            val request = Get(baseApiUrl + s"/v2/authentication?email=$inactiveUserEmailEnc&password=$testPass")
            val response: HttpResponse = singleAwaitingRequest(request)
            log.debug(s"response: ${response.toString}")
            assert(response.status === StatusCodes.Unauthorized)
        }
    }

    "The Authentication Route ('v2/authentication') with credentials supplied via Basic Auth" should {

        "authenticate with correct email and password" in {
            /* Correct email / correct password */
            val request = Get(baseApiUrl + "/v2/authentication") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
            val response = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK)
        }

        "fail authentication with correct email and wrong password" in {
            /* Correct username / wrong password */
            val request = Get(baseApiUrl + "/v2/authentication") ~> addCredentials(BasicHttpCredentials(rootEmail, wrongPass))
            val response = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.Unauthorized)
        }

        "fail authentication with the user set as 'not active' " in {
            /* User not active */
            val request = Get(baseApiUrl + s"/v2/authentication") ~> addCredentials(BasicHttpCredentials(inactiveUserEmailEnc, testPass))
            val response: HttpResponse = singleAwaitingRequest(request)
            log.debug(s"response: ${response.toString}")
            assert(response.status === StatusCodes.Unauthorized)
        }
    }

    "The Authentication Route ('v2/authentication')" should {

        var token = ""

        "login" in {
            /* Correct username and correct password */

            val params =
                s"""
                   |{
                   |    "email": "$rootEmail",
                   |    "password": "$testPass"
                   |}
                """.stripMargin

            val request = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK)

            //println(response.toString)

            val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)
            token = lr.token

            token.nonEmpty should be (true)
            log.debug("token: {}", token)
        }

        "authenticate with token in header" in {
            // authenticate by calling '/v2/authenticate' without parameters but by providing token (from earlier login) in authorization header
            val request = Get(baseApiUrl + "/v2/authentication") ~> addCredentials(GenericHttpCredentials("Bearer", token))
            val response = singleAwaitingRequest(request)
            log.debug("response: {}", response.toString())
            assert(response.status === StatusCodes.OK)
        }

        "authenticate with token in request parameter" in {
            // authenticate by calling '/v2/authenticate' with parameters providing the token (from earlier login)
            val request = Get(baseApiUrl + s"/v2/authentication?token=$token")
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.OK)
        }

        "logout when providing token in header" in {
            // do logout with stored token
            val request = Delete(baseApiUrl + "/v2/authentication?") ~> addCredentials(GenericHttpCredentials("Bearer", token))
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.OK)
        }

        "logout when providing token as request parameter" in {

            val params =
                s"""
                   |{
                   |    "email": "$rootEmail",
                   |    "password": "$testPass"
                   |}
                """.stripMargin

            val request1 = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
            val response1: HttpResponse = singleAwaitingRequest(request1)
            assert(response1.status == StatusCodes.OK)
            val token = Await.result(Unmarshal(response1.entity).to[LoginResponse], 1.seconds).token

            val request2 = Delete(baseApiUrl + s"/v2/authentication?token=$token")
            val response2 = singleAwaitingRequest(request2)
            //log.debug("==>> " + responseAs[String])
            assert(response2.status === StatusCodes.OK)
        }

        "fail with authentication when providing the token after logout" in {
            val request = Get(baseApiUrl + "/v2/authentication") ~> addCredentials(GenericHttpCredentials("Bearer", token))
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }


        "fail 'login' with correct email / wrong password" in {
            /* Correct username and wrong password */

            val params =
                s"""
                   |{
                   |    "email": "$rootEmail",
                   |    "password": "wrong"
                   |}
                """.stripMargin



            val request = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }

        "fail 'login' with wrong username" in {
            /* wrong username */
            val params =
                s"""
                   |{
                   |    "email": "wrong",
                   |    "password": "wrong"
                   |}
                """.stripMargin

            val request = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }

        "fail authentication with wrong token in header" in {
            val request = Get(baseApiUrl + "/v2/authentication") ~> addCredentials(GenericHttpCredentials("Bearer", "123456"))
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }

        "fail authentication with wrong token as parameter" in {
            val request = Get(baseApiUrl + "/v2/authentication?token=123456")
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }
    }

    "The Users Route using the Authenticator trait " should {

        var token = ""

        "login in v2" in {
            /* Correct username and correct password */

            val params =
                s"""
                   |{
                   |    "email": "$rootEmail",
                   |    "password": "$testPass"
                   |}
                """.stripMargin

            val request = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
            val response: HttpResponse = singleAwaitingRequest(request)
            assert(response.status == StatusCodes.OK)

            //println(response.toString)

            val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)
            token = lr.token

            token.nonEmpty should be (true)
            log.debug("token: {}", token)
        }

        "allow access with authentication using URL parameters and token from v2" in {
            /* Correct email / correct password */
            val request = Get(baseApiUrl + s"/v1/users/$rootIriEnc?token=$token")
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.OK)
        }

        "fail with authentication using URL parameters and wrong token" in {
            /* Correct email / wrong password */
            val request = Get(baseApiUrl + s"/v1/users/$rootIriEnc?token=wrong")

            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }

        "allow access with authentication using HTTP Bearer Auth header and token from v2" in {
            /* Correct email / correct password */
            val request = Get(baseApiUrl + s"/v1/users/$rootIriEnc") ~> addCredentials(GenericHttpCredentials("Bearer", token))
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.OK)
        }

        "fail with authentication using HTTP Bearer Auth header and wrong token " in {
            /* Correct email / wrong password */
            val request = Get(baseApiUrl + s"/v1/users/$rootIriEnc") ~> addCredentials(GenericHttpCredentials("Bearer", "123456"))
            val response = singleAwaitingRequest(request)
            //log.debug("==>> " + responseAs[String])
            assert(response.status === StatusCodes.Unauthorized)
        }

        "not return sensitive information (token, password) in the response " in {
            val request = Get(baseApiUrl + s"/v1/users/$rootIriEnc?token=$token")
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
