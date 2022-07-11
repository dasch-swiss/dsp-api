/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.http.scaladsl.unmarshalling.Unmarshal
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.v2.routing.authenticationmessages.AuthenticationV2JsonProtocol
import org.knora.webapi.messages.v2.routing.authenticationmessages.LoginResponse
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.MutableTestString

import scala.concurrent.Await
import scala.concurrent.duration._
import org.knora.webapi.routing.Authenticator

object AuthenticationV2E2ESpec {
  val config: Config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * End-to-End (E2E) test specification for testing authentication.
 *
 * This spec tests the 'v1/authentication' and 'v1/session' route.
 */
class AuthenticationV2E2ESpec
    extends E2ESpec(AuthenticationV2E2ESpec.config)
    with AuthenticationV2JsonProtocol
    with TriplestoreJsonProtocol {

  private implicit def default(implicit system: ActorSystem): RouteTestTimeout =
    RouteTestTimeout(settings.defaultTimeout)

  private val rootIri              = SharedTestDataADM.rootUser.id
  private val rootIriEnc           = java.net.URLEncoder.encode(rootIri, "utf-8")
  private val rootUsername         = SharedTestDataADM.rootUser.username
  private val rootUsernameEnc      = java.net.URLEncoder.encode(rootUsername, "utf-8")
  private val rootEmail            = SharedTestDataADM.rootUser.email
  private val rootEmailEnc         = java.net.URLEncoder.encode(rootEmail, "utf-8")
  private val inactiveUserEmail    = SharedTestDataADM.inactiveUser.email
  private val inactiveUserEmailEnc = java.net.URLEncoder.encode(inactiveUserEmail, "utf-8")
  private val wrongEmail           = "wrong@example.com"
  private val wrongEmailEnc        = java.net.URLEncoder.encode(wrongEmail, "utf-8")
  private val testPass             = java.net.URLEncoder.encode("test", "utf-8")
  private val wrongPass            = java.net.URLEncoder.encode("wrong", "utf-8")

  "The Authentication Route ('v2/authentication') with credentials supplied via URL parameters" should {

    "authenticate with correct user IRI and password" in {
      /* Correct username and password */
      val request                = Get(baseApiUrl + s"/v2/authentication?iri=$rootIriEnc&password=$testPass")
      val response: HttpResponse = singleAwaitingRequest(request)
      logger.debug(s"response: ${response.toString}")
      assert(response.status === StatusCodes.OK)
    }

    "authenticate with correct email and password" in {
      /* Correct username and password */
      val request                = Get(baseApiUrl + s"/v2/authentication?email=$rootEmailEnc&password=$testPass")
      val response: HttpResponse = singleAwaitingRequest(request)
      logger.debug(s"response: ${response.toString}")
      assert(response.status === StatusCodes.OK)
    }

    "authenticate with correct username and password" in {
      /* Correct username and password */
      val request                = Get(baseApiUrl + s"/v2/authentication?username=$rootUsernameEnc&password=$testPass")
      val response: HttpResponse = singleAwaitingRequest(request)
      logger.debug(s"response: ${response.toString}")
      assert(response.status === StatusCodes.OK)
    }

    "fail authentication with correct email and wrong password" in {
      /* Correct email / wrong password */
      val request                = Get(baseApiUrl + s"/v2/authentication?email=$rootEmail&password=$wrongPass")
      val response: HttpResponse = singleAwaitingRequest(request)
      logger.debug(s"response: ${response.toString}")
      assert(response.status === StatusCodes.Unauthorized)
    }
    "fail authentication with the user set as 'not active' " in {
      /* User not active */
      val request                = Get(baseApiUrl + s"/v2/authentication?email=$inactiveUserEmailEnc&password=$testPass")
      val response: HttpResponse = singleAwaitingRequest(request)
      logger.debug(s"response: ${response.toString}")
      assert(response.status === StatusCodes.Unauthorized)
    }
  }

  "The Authentication Route ('v2/authentication') with credentials supplied via Basic Auth" should {

    "authenticate with correct email and password" in {
      /* Correct email / correct password */
      val request  = Get(baseApiUrl + "/v2/authentication") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
      val response = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)
    }

    "fail authentication with correct email and wrong password" in {
      /* Correct username / wrong password */
      val request  = Get(baseApiUrl + "/v2/authentication") ~> addCredentials(BasicHttpCredentials(rootEmail, wrongPass))
      val response = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.Unauthorized)
    }

    "fail authentication with the user set as 'not active' " in {
      /* User not active */
      val request =
        Get(baseApiUrl + s"/v2/authentication") ~> addCredentials(BasicHttpCredentials(inactiveUserEmail, testPass))
      val response: HttpResponse = singleAwaitingRequest(request)
      logger.debug(s"response: ${response.toString}")
      assert(response.status === StatusCodes.Unauthorized)
    }
  }

  "The Authentication Route ('v2/authentication')" should {

    var token = new MutableTestString

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

      // println(response.toString)

      val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)

      lr.token.nonEmpty should be(true)
      logger.debug("token: {}", lr.token)

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

      // println(response.toString)

      val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)

      lr.token.nonEmpty should be(true)
      logger.debug("token: {}", lr.token)
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

      // println(response.toString)

      val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)

      lr.token.nonEmpty should be(true)
      logger.debug("token: {}", lr.token)
    }

    "authenticate with token in header" in {
      // authenticate by calling '/v2/authenticate' without parameters but by providing token (from earlier login) in authorization header
      val request =
        Get(baseApiUrl + "/v2/authentication") ~> addCredentials(GenericHttpCredentials("Bearer", token.get))
      val response = singleAwaitingRequest(request)
      logger.debug("response: {}", response.toString())
      assert(response.status === StatusCodes.OK)
    }

    "authenticate with token in request parameter" in {
      // authenticate by calling '/v2/authenticate' with parameters providing the token (from earlier login)
      val request  = Get(baseApiUrl + s"/v2/authentication?token=${token.get}")
      val response = singleAwaitingRequest(request)
      // logger.debug("==>> " + responseAs[String])
      assert(response.status === StatusCodes.OK)
    }

    "authenticate with token in cookie" in {
      val KnoraAuthenticationCookieName = Authenticator.calculateCookieName(settings)
      val request =
        Get(baseApiUrl + "/v2/authentication") ~> addHeader(HttpHeader.parse(KnoraAuthenticationCookieName, token.get))
    }

    "logout when providing token in header" in {
      // do logout with stored token
      val request =
        Delete(baseApiUrl + "/v2/authentication?") ~> addCredentials(GenericHttpCredentials("Bearer", token.get))
      val response = singleAwaitingRequest(request)
      // logger.debug("==>> " + responseAs[String])
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

      val request2  = Delete(baseApiUrl + s"/v2/authentication?token=$token")
      val response2 = singleAwaitingRequest(request2)
      // logger.debug("==>> " + responseAs[String])
      assert(response2.status === StatusCodes.OK)
    }

    "fail with authentication when providing the token after logout" in {
      val request =
        Get(baseApiUrl + "/v2/authentication") ~> addCredentials(GenericHttpCredentials("Bearer", token.get))
      val response = singleAwaitingRequest(request)
      // logger.debug("==>> " + responseAs[String])
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
      //log.debug("==>> " + responseAs[String])
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
      //log.debug("==>> " + responseAs[String])
      assert(response.status === StatusCodes.Unauthorized)
    }

    "fail authentication with wrong token in header" in {
      val request  = Get(baseApiUrl + "/v2/authentication") ~> addCredentials(GenericHttpCredentials("Bearer", "123456"))
      val response = singleAwaitingRequest(request)
      //log.debug("==>> " + responseAs[String])
      assert(response.status === StatusCodes.Unauthorized)
    }

    "fail authentication with wrong token as parameter" in {
      val request  = Get(baseApiUrl + "/v2/authentication?token=123456")
      val response = singleAwaitingRequest(request)
      //log.debug("==>> " + responseAs[String])
      assert(response.status === StatusCodes.Unauthorized)
    }
  }

  "The Users V1 Route using the Authenticator trait " should {

    var token = new MutableTestString

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

      //println(response.toString)

      val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)

      lr.token.nonEmpty should be(true)
      logger.debug("token: {}", lr.token)

      token.set(lr.token)
    }

    "allow access using URL parameters and token from v2" in {
      /* Correct email / correct password */
      val request  = Get(baseApiUrl + s"/v1/users/$rootIriEnc?token=${token.get}")
      val response = singleAwaitingRequest(request)
      // logger.debug("==>> " + responseAs[String])
      assert(response.status === StatusCodes.OK)
    }

    "fail with authentication using URL parameters and wrong token" in {
      /* Correct email / wrong password */
      val request = Get(baseApiUrl + s"/v1/users/$rootIriEnc?token=wrong")

      val response = singleAwaitingRequest(request)
      // logger.debug("==>> " + responseAs[String])
      assert(response.status === StatusCodes.Unauthorized)
    }

    "allow access using HTTP Bearer Auth header and token from v2" in {
      /* Correct email / correct password */
      val request =
        Get(baseApiUrl + s"/v1/users/$rootIriEnc") ~> addCredentials(GenericHttpCredentials("Bearer", token.get))
      val response = singleAwaitingRequest(request)
      // logger.debug("==>> " + responseAs[String])
      assert(response.status === StatusCodes.OK)
    }

    "fail with authentication using HTTP Bearer Auth header and wrong token " in {
      /* Correct email / wrong password */
      val request =
        Get(baseApiUrl + s"/v1/users/$rootIriEnc") ~> addCredentials(GenericHttpCredentials("Bearer", "123456"))
      val response = singleAwaitingRequest(request)
      // logger.debug("==>> " + responseAs[String])
      assert(response.status === StatusCodes.Unauthorized)
    }

    "not return sensitive information (token, password) in the response " in {
      val request  = Get(baseApiUrl + s"/v1/users/$rootIriEnc?token=${token.get}")
      val response = singleAwaitingRequest(request)
      // logger.debug("==>> " + responseAs[String])
      // assert(status === StatusCodes.OK)

      /* check for sensitive information leakage */
      val body: String = Await.result(Unmarshal(response.entity).to[String], 1.seconds)
      assert(body contains "\"password\":null")
      assert(body contains "\"token\":null")
    }
  }

  "The Users ADM Route using the Authenticator trait" should {

    var token = new MutableTestString

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

      // println(response.toString)

      val lr: LoginResponse = Await.result(Unmarshal(response.entity).to[LoginResponse], 1.seconds)

      lr.token.nonEmpty should be(true)
      // logger.debug("token: {}", lr.token)

      token.set(lr.token)
    }

    "allow access using URL parameters with token from v2" in {
      /* Correct token */
      val request  = Get(baseApiUrl + s"/admin/users/iri/$rootIriEnc?token=${token.get}")
      val response = singleAwaitingRequest(request)
      // logger.debug(response.toString())
      assert(response.status === StatusCodes.OK)
    }

    "fail with authentication using URL parameters with wrong token" in {
      /* Wrong token */
      val request = Get(baseApiUrl + s"/admin/users/iri/$rootIriEnc?token=wrong")

      val response = singleAwaitingRequest(request)
      // logger.debug("==>> " + responseAs[String])
      assert(response.status === StatusCodes.Unauthorized)
    }

    "allow access using HTTP Bearer Auth header with token from v2" in {
      /* Correct token */
      val request =
        Get(baseApiUrl + s"/admin/users/iri/$rootIriEnc") ~> addCredentials(GenericHttpCredentials("Bearer", token.get))
      val response = singleAwaitingRequest(request)
      // logger.debug("==>> " + responseAs[String])
      assert(response.status === StatusCodes.OK)
    }

    "fail with authentication using HTTP Bearer Auth header with wrong token " in {
      /* Wrong token */
      val request =
        Get(baseApiUrl + s"/admin/users/iri/$rootIriEnc") ~> addCredentials(GenericHttpCredentials("Bearer", "123456"))
      val response = singleAwaitingRequest(request)
      // logger.debug("==>> " + responseAs[String])
      assert(response.status === StatusCodes.Unauthorized)
    }

    "not return sensitive information (token, password) in the response " in {
      val request  = Get(baseApiUrl + s"/admin/users/iri/$rootIriEnc?token=${token.get}")
      val response = singleAwaitingRequest(request)
      // logger.debug("==>> " + responseAs[String])
      // assert(status === StatusCodes.OK)

      /* check for sensitive information leakage */
      val body: String = Await.result(Unmarshal(response.entity).to[String], 1.seconds)
      assert(body contains "\"password\":null")
      assert(body contains "\"token\":null")
    }
  }
}
