/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.apache.pekko
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.headers.*
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshal
import spray.json.*
import zio.ZIO
import zio.json.ast.Json

import scala.concurrent.Await
import scala.concurrent.duration.*

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.util.MutableTestString

/**
 * End-to-End (E2E) test specification for testing authentication.
 *
 * This spec tests the 'v1/authentication' and 'v1/session' route.
 */
class AuthenticationV2E2ESpec extends E2ESpec with AuthenticationV2JsonProtocol with TriplestoreJsonProtocol {

  private val rootIri      = SharedTestDataADM.rootUser.id
  private val rootIriEnc   = java.net.URLEncoder.encode(rootIri, "utf-8")
  private val rootUsername = SharedTestDataADM.rootUser.username
  private val rootEmail    = SharedTestDataADM.rootUser.email
  private val testPass     = java.net.URLEncoder.encode("test", "utf-8")

  "The Authentication Route ('v2/authentication')" should {
    val token = new MutableTestString

    "login with IRI" in {

      val params =
        s"""
           |{
           |    "iri": "$rootIri",
           |    "password": "$testPass"
           |}
                """.stripMargin

      val request                = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)

      val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)

      lr.token.nonEmpty should be(true)

      token.set(lr.token)
    }

    "login with email" in {

      val params =
        s"""
           |{
           |    "email": "$rootEmail",
           |    "password": "$testPass"
           |}
                """.stripMargin

      val request                = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)

      val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)

      lr.token.nonEmpty should be(true)
    }

    "login with username" in {

      val params =
        s"""
           |{
           |    "username": "$rootUsername",
           |    "password": "$testPass"
           |}
                """.stripMargin

      val request                = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)

      val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)

      lr.token.nonEmpty should be(true)
    }

    "authenticate with token in header" in {
      // authenticate by calling '/v2/authenticate' without parameters but by providing token (from earlier login) in authorization header
      val request =
        Get(baseApiUrl + "/v2/authentication") ~> addCredentials(GenericHttpCredentials("Bearer", token.get))
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.OK)
    }

    "authenticate with token in cookie" in {
      val KnoraAuthenticationCookieName =
        UnsafeZioRun.runOrThrow(ZIO.serviceWith[Authenticator](_.calculateCookieName()))
      val cookieHeader = headers.Cookie(KnoraAuthenticationCookieName, token.get)

      val request  = Get(baseApiUrl + "/v2/authentication") ~> addHeader(cookieHeader)
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.OK)
    }

    "fail authentication with invalid token in cookie" in {
      val KnoraAuthenticationCookieName =
        UnsafeZioRun.runOrThrow(ZIO.serviceWith[Authenticator](_.calculateCookieName()))
      val cookieHeader = headers.Cookie(KnoraAuthenticationCookieName, "not_a_valid_token")

      val request  = Get(baseApiUrl + "/v2/authentication") ~> addHeader(cookieHeader)
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }

    "logout when providing token in header" in {
      // do logout with stored token
      val request =
        Delete(baseApiUrl + "/v2/authentication?") ~> addCredentials(GenericHttpCredentials("Bearer", token.get))
      val response = singleAwaitingRequest(request)
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

      val request1                = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
      val response1: HttpResponse = singleAwaitingRequest(request1)
      assert(response1.status == StatusCodes.OK)
      val token = Await.result(Unmarshal(response1.entity).to[LoginResponse], 1.seconds).token

      val request2 =
        Delete(baseApiUrl + s"/v2/authentication") ~> addCredentials(GenericHttpCredentials("Bearer", token))
      val response2 = singleAwaitingRequest(request2)
      assert(response2.status === StatusCodes.OK)

      // fail with authentication when providing the token after logout
      val request =
        Get(baseApiUrl + "/v2/authentication") ~> addCredentials(GenericHttpCredentials("Bearer", token))
      val response = singleAwaitingRequest(request)
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

      val request  = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }

    "fail 'login' with wrong username" in {
      /* wrong username */
      val params =
        s"""
           |{
           |    "username": "wrong",
           |    "password": "wrong"
           |}
                """.stripMargin

      val request  = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }

    "fail authentication with wrong token in header" in {
      val request  = Get(baseApiUrl + "/v2/authentication") ~> addCredentials(GenericHttpCredentials("Bearer", "123456"))
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }

    "fail authentication with wrong token as parameter" in {
      val request  = Get(baseApiUrl + "/v2/authentication?token=123456")
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }
  }

  "The Users V1 Route using the Authenticator trait " ignore {
    val token = new MutableTestString

    "login in v2" in {
      /* Correct username and correct password */

      val params =
        s"""
           |{
           |    "email": "$rootEmail",
           |    "password": "$testPass"
           |}
                """.stripMargin

      val request                = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)

      val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)

      lr.token.nonEmpty should be(true)

      token.set(lr.token)
    }

    "allow access using URL parameters and token from v2" in {
      /* Correct email / correct password */
      val request  = Get(baseApiUrl + s"/v1/users/$rootIriEnc?token=${token.get}")
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.OK)
    }

    "fail with authentication using URL parameters and wrong token" in {
      /* Correct email / wrong password */
      val request = Get(baseApiUrl + s"/v1/users/$rootIriEnc?token=wrong")

      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }

    "allow access using HTTP Bearer Auth header and token from v2" in {
      /* Correct email / correct password */
      val request =
        Get(baseApiUrl + s"/v1/users/$rootIriEnc") ~> addCredentials(GenericHttpCredentials("Bearer", token.get))
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.OK)
    }

    "fail with authentication using HTTP Bearer Auth header and wrong token " in {
      /* Correct email / wrong password */
      val request =
        Get(baseApiUrl + s"/v1/users/$rootIriEnc") ~> addCredentials(GenericHttpCredentials("Bearer", "123456"))
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }

    "not return sensitive information (token, password) in the response " in {
      val request  = Get(baseApiUrl + s"/v1/users/$rootIriEnc?token=${token.get}")
      val response = singleAwaitingRequest(request)
      // assert(status === StatusCodes.OK)

      /* check for sensitive information leakage */
      val body: String = Await.result(Unmarshal(response.entity).to[String], 1.seconds)
      assert(body contains "\"password\":null")
      assert(body contains "\"token\":null")
    }
  }

  "The Users ADM Route using the Authenticator trait" should {
    val token = new MutableTestString

    "login in v2" in {
      /* Correct username and correct password */

      val params =
        s"""
           |{
           |    "email": "$rootEmail",
           |    "password": "$testPass"
           |}
                """.stripMargin

      val request                = Post(baseApiUrl + s"/v2/authentication", HttpEntity(ContentTypes.`application/json`, params))
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)

      val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)

      lr.token.nonEmpty should be(true)

      token.set(lr.token)
    }

    "allow access using HTTP Bearer Auth header with token from v2" in {
      /* Correct token */
      val request =
        Get(baseApiUrl + s"/admin/users/iri/$rootIriEnc") ~> addCredentials(GenericHttpCredentials("Bearer", token.get))
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.OK)
    }

    "fail with authentication using HTTP Bearer Auth header with wrong token " in {
      /* Wrong token */
      val request =
        Get(baseApiUrl + s"/admin/users/iri/$rootIriEnc") ~> addCredentials(GenericHttpCredentials("Bearer", "123456"))
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }

    "not return the password in the response" in {
      val request  = Get(baseApiUrl + s"/admin/users/iri/$rootIriEnc?token=${token.get}")
      val response = getSuccessResponseAs[Json.Obj](request)
      assert(response.filterKeys(_ == "password").isEmpty)
    }
  }
}

final case class LoginResponse(token: String)

trait AuthenticationV2JsonProtocol extends DefaultJsonProtocol with NullOptions with SprayJsonSupport {
  implicit val SessionResponseFormat: RootJsonFormat[LoginResponse] =
    jsonFormat1(LoginResponse.apply)
}
