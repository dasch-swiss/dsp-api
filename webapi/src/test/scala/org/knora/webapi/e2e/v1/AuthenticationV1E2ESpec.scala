/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v1

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.model.headers.`Set-Cookie`
import akka.http.scaladsl.unmarshalling.Unmarshal

import scala.concurrent.Await
import scala.concurrent.duration._

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionResponse
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.sharedtestdata.SharedTestDataV1

/**
 * End-to-End (E2E) test specification for testing authentication.
 *
 * This spec tests the 'v1/authentication' and 'v1/session' route.
 */
class AuthenticationV1E2ESpec extends E2ESpec with SessionJsonProtocol with TriplestoreJsonProtocol {

  private val rootIri      = SharedTestDataV1.rootUser.userData.user_id.get
  private val rootIriEnc   = java.net.URLEncoder.encode(rootIri, "utf-8")
  private val rootEmail    = SharedTestDataV1.rootUser.userData.email.get
  private val rootEmailEnc = java.net.URLEncoder.encode(rootEmail, "utf-8")
  private val inactiveUserEmailEnc =
    java.net.URLEncoder.encode(SharedTestDataV1.inactiveUser.userData.email.get, "utf-8")
  private val wrongEmail    = "wrong@example.com"
  private val wrongEmailEnc = java.net.URLEncoder.encode(wrongEmail, "utf-8")
  private val testPass      = java.net.URLEncoder.encode("test", "utf-8")
  private val wrongPass     = java.net.URLEncoder.encode("wrong", "utf-8")

  val KnoraAuthenticationCookieName = Authenticator.calculateCookieName(settings)

  "The Authentication Route ('v1/authenticate') with credentials supplied via URL parameters" should {

    "succeed authentication with correct email and correct password" in {
      /* Correct username and password */
      val request                = Get(baseApiUrl + s"/v1/authenticate?email=$rootEmailEnc&password=$testPass")
      val response: HttpResponse = singleAwaitingRequest(request)
      logger.debug(s"response: ${response.toString}")
      assert(response.status === StatusCodes.OK)
    }

    "fail authentication with correct email and wrong password" in {
      /* Correct email / wrong password */
      val request                = Get(baseApiUrl + s"/v1/authenticate?email=$rootEmail&password=$wrongPass")
      val response: HttpResponse = singleAwaitingRequest(request)
      logger.debug(s"response: ${response.toString}")
      assert(response.status === StatusCodes.Unauthorized)
    }
    "fail authentication if the user is set as 'not active' " in {
      /* User not active */
      val request                = Get(baseApiUrl + s"/v1/authenticate?email=$inactiveUserEmailEnc&password=$testPass")
      val response: HttpResponse = singleAwaitingRequest(request)
      logger.debug(s"response: ${response.toString}")
      assert(response.status === StatusCodes.Unauthorized)
    }
  }

  "The Authentication Route ('v1/authenticate') with credentials supplied via Basic Auth" should {

    "succeed authentication with correct email and correct password" in {
      /* Correct email / correct password */
      val request  = Get(baseApiUrl + "/v1/authenticate") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
      val response = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)
    }

    "fail authentication with correct email and wrong password" in {
      /* Correct username / wrong password */
      val request  = Get(baseApiUrl + "/v1/authenticate") ~> addCredentials(BasicHttpCredentials(rootEmail, wrongPass))
      val response = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.Unauthorized)
    }
  }

  "The Session Route ('v1/session') with credentials supplied via URL parameters" should {
    var sid = ""
    "succeed 'login' with correct email and correct password" in {
      /* Correct username and correct password */
      val request                = Get(baseApiUrl + s"/v1/session?login&email=$rootEmailEnc&password=$testPass")
      val response: HttpResponse = singleAwaitingRequest(request)
      assert(response.status == StatusCodes.OK)

      val sr: SessionResponse = Await.result(Unmarshal(response.entity).to[SessionResponse], 1.seconds)
      sid = sr.sid

      assert(
        response.headers.contains(
          `Set-Cookie`(
            HttpCookie(
              KnoraAuthenticationCookieName,
              value = sid,
              domain = Some(settings.cookieDomain),
              path = Some("/"),
              httpOnly = true
            )
          )
        )
      )

      /* check for sensitive information leakage */
      val body: String = Await.result(Unmarshal(response.entity).to[String], 1.seconds)
      assert(body contains "\"password\":null")
      assert(body contains "\"token\":null")
    }

    "not return sensitive information (token, password) in the response when checking session" in {
      val request  = Get(baseApiUrl + s"/v1/session") ~> Cookie(KnoraAuthenticationCookieName, value = sid)
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.OK)

      val body: String = Await.result(Unmarshal(response.entity).to[String], 1.seconds)
      assert(body contains "\"password\":null")
      assert(body contains "\"token\":null")
    }

    "succeed authentication with correct session id in cookie" in {
      // authenticate by calling '/v1/session' without parameters but by providing session id in cookie from earlier login
      val request  = Get(baseApiUrl + "/v1/session") ~> Cookie(KnoraAuthenticationCookieName, sid)
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.OK)
    }

    "succeed 'logout' with provided session cookie" in {
      // do logout with stored session id
      val request  = Get(baseApiUrl + "/v1/session?logout") ~> Cookie(KnoraAuthenticationCookieName, sid)
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.OK)
      assert(
        response.headers.contains(
          `Set-Cookie`(
            HttpCookie(
              KnoraAuthenticationCookieName,
              "",
              domain = Some(settings.cookieDomain),
              path = Some("/"),
              httpOnly = true,
              expires = Some(DateTime(1970, 1, 1, 0, 0, 0)),
              maxAge = Some(0)
            )
          )
        )
      )
    }

    "fail authentication with provided session cookie after logout" in {
      val request  = Get(baseApiUrl + "/v1/session") ~> Cookie(KnoraAuthenticationCookieName, sid)
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }

    "fail 'login' with correct email and wrong password" in {
      /* Correct username and wrong password */
      val request  = Get(baseApiUrl + s"/v1/session?login&email=$rootEmailEnc&password=$wrongPass")
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }

    "fail 'login' with wrong username" in {
      /* wrong username */
      val request  = Get(baseApiUrl + s"/v1/session?login&email=$wrongEmailEnc&password=$testPass")
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }

    "fail authentication with wrong session id in cookie" in {
      val request  = Get(baseApiUrl + "/v1/session") ~> Cookie(KnoraAuthenticationCookieName, "123456")
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }
  }

  "The Session Route ('v1/session') with credentials supplied via Basic Auth" should {
    var sid = ""
    "succeed 'login' with correct email and correct password" in {
      /* Correct username and correct password */
      val request  = Get(baseApiUrl + "/v1/session?login") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.OK)

      val sr: SessionResponse = Await.result(Unmarshal(response.entity).to[SessionResponse], 1.seconds)
      sid = sr.sid

      assert(
        response.headers.contains(
          `Set-Cookie`(
            HttpCookie(
              KnoraAuthenticationCookieName,
              value = sid,
              domain = Some(settings.cookieDomain),
              path = Some("/"),
              httpOnly = true
            )
          )
        )
      )

      /* check for sensitive information leakage */
      val body: String = Await.result(Unmarshal(response.entity).to[String], 1.seconds)
      assert(body contains "\"password\":null")
      assert(body contains "\"token\":null")
    }

    "not return sensitive information (token, password) in the response when checking session" in {
      val request  = Get(baseApiUrl + s"/v1/session") ~> Cookie(KnoraAuthenticationCookieName, sid)
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.OK)

      val body: String = Await.result(Unmarshal(response.entity).to[String], 1.seconds)
      assert(body contains "\"password\":null")
      assert(body contains "\"token\":null")
    }

    "fail 'login' with correct email and wrong password " in {
      /* Correct username and wrong password */
      val request  = Get(baseApiUrl + "/v1/session?login") ~> addCredentials(BasicHttpCredentials(rootEmail, wrongPass))
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }

    "fail 'login' with wrong email " in {
      /* wrong username */
      val request  = Get(baseApiUrl + "/v1/session?login") ~> addCredentials(BasicHttpCredentials(wrongEmail, testPass))
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }
  }

  "The Users Route using the Authenticator trait " should {
    "succeed authentication using URL parameters with correct email and correct password " in {
      /* Correct email / correct password */
      val request  = Get(baseApiUrl + s"/v1/users/$rootIriEnc?email=$rootEmailEnc&password=$testPass")
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.OK)
    }

    "fail authentication using URL parameters with correct email and wrong password " in {
      /* Correct email / wrong password */
      val request = Get(baseApiUrl + s"/v1/users/$rootIriEnc?email=$rootEmailEnc&password=$wrongPass")

      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }

    "succeed authentication using HTTP Basic Auth headers with correct username and correct password " in {
      /* Correct email / correct password */
      val request =
        Get(baseApiUrl + s"/v1/users/$rootIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, testPass))
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.OK)
    }

    "fail authentication using HTTP Basic Auth headers with correct username and wrong password " in {
      /* Correct email / wrong password */
      val request =
        Get(baseApiUrl + s"/v1/users/$rootIriEnc") ~> addCredentials(BasicHttpCredentials(rootEmail, wrongPass))
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.Unauthorized)
    }

    "not return sensitive information (token, password) in the response " in {
      val request  = Get(baseApiUrl + s"/v1/users/$rootIriEnc?email=$rootEmailEnc&password=$testPass")
      val response = singleAwaitingRequest(request)
      assert(response.status === StatusCodes.OK)

      /* check for sensitive information leakage */
      val body: String = Await.result(Unmarshal(response.entity).to[String], 1.seconds)
      assert(body contains "\"password\":null")
      assert(body contains "\"token\":null")
    }
  }
}
