/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin
import zio.http._
import zio.test.Assertion._
import zio.test._

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraJWTTokenCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraSessionCredentialsV2
import zio.http.model._

object AuthenticatorServiceLiveSpec extends ZIOSpecDefault {

  private val cookieName = "cookieName"

  private implicit val sf: StringFormatter = { StringFormatter.initForTest(); StringFormatter.getGeneralInstance }

  val headerInvalidSuite = suite("given invalid header authentication")(
    test("should fail to extract user email (basic auth)") {
      val userMail = "user.example.com"
      val req      = Request.get(URL.empty).setHeaders(Headers.basicAuthorizationHeader(userMail, "pass"))
      for {
        actual <- AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName).exit
      } yield assert(actual)(fails(hasMessage(equalTo("Invalid email Some(user.example.com)"))))
    }
  )

  val headerValidSuite = suite("given valid header authentication")(
    test("should extract user email (basic auth)") {
      val userMail = "user@example.com"
      val req      = Request.get(URL.empty).setHeaders(Headers.basicAuthorizationHeader(userMail, "pass"))
      for {
        actual  <- AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName)
        expected = Some(KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = Some(userMail)), "pass"))
      } yield assertTrue(actual == expected)
    },
    test("should extract jwt token (bearer token)") {
      val jwtToken = "someToken"
      val req      = Request.get(URL.empty).setHeaders(Headers.bearerAuthorizationHeader(jwtToken))
      for {
        actual  <- AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName)
        expected = Some(KnoraJWTTokenCredentialsV2(jwtToken))
      } yield assertTrue(actual == expected)
    },
    test("should extract session cookie") {
      val sessionCookieValue = "session"
      val req =
        Request
          .get(URL.empty)
          .setHeaders(Headers.cookie(Cookie(cookieName, sessionCookieValue, Cookie.Type.request)))
      for {
        actual  <- AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName)
        expected = Some(KnoraSessionCredentialsV2(sessionCookieValue))
      } yield assertTrue(actual == expected)
    }
  )

  val queryParamInvalidSuite = suite("given invalid query parameters authentication")(
    test("should fail if different credentials are provided") {
      val params = QueryParams(
        ("username", "someUsername"),
        ("email", "user@example.com"),
        ("password", "somePassword")
      )
      val req = Request.get(URL.empty.setQueryParams(params))
      for {
        actual <- AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName).exit
      } yield assert(actual)(fails(hasMessage(equalTo("Only one option allowed for user identifier."))))
    },
    test("should fail if if an invalid email is provided") {
      val params = QueryParams(
        ("email", "user.example.com"),
        ("password", "somePassword")
      )
      val req = Request.get(URL.empty.setQueryParams(params))
      for {
        actual <- AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName).exit
      } yield assert(actual)(fails(hasMessage(equalTo("Invalid email Some(user.example.com)"))))
    },
    test("should fail if if an invalid username is provided") {
      val params = QueryParams(
        ("username", "some\rUser"),
        ("password", "somePassword")
      )
      val req = Request.get(URL.empty.setQueryParams(params))
      for {
        actual <- AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName).exit
      } yield assert(actual)(fails(hasMessage(equalTo("Invalid username Some(some\rUser)"))))
    },
    test("should fail if if an invalid IRI is provided") {
      val params = QueryParams(
        ("iri", "notAnIri"),
        ("password", "somePassword")
      )
      val req = Request.get(URL.empty.setQueryParams(params))
      for {
        actual <- AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName).exit
      } yield assert(actual)(fails(hasMessage(equalTo("Invalid user IRI Some(notAnIri)"))))
    }
  )

  val queryParamValidSuite = suite("given valid query parameters authentication")(
    test("should extract jwt token") {
      val jwtToken = "someToken"
      val req      = Request.get(URL.empty.setQueryParams(QueryParams(("token", jwtToken))))
      for {
        actual  <- AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName)
        expected = Some(KnoraJWTTokenCredentialsV2(jwtToken))
      } yield assertTrue(actual == expected)
    },
    test("should extract username and password") {
      val username = "someUsername"
      val password = "somePassword"
      val params = QueryParams(
        ("username", username),
        ("password", password)
      )
      val req = Request.get(URL.empty.setQueryParams(params))
      for {
        actual  <- AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName)
        expected = Some(KnoraPasswordCredentialsV2(UserIdentifierADM(maybeUsername = Some(username)), password))
      } yield assertTrue(actual == expected)
    },
    test("should extract email and password") {
      val email    = "user@example.com"
      val password = "somePassword"
      val params = QueryParams(
        ("email", email),
        ("password", password)
      )
      val req = Request.get(URL.empty.setQueryParams(params))
      for {
        actual  <- AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName)
        expected = Some(KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = Some(email)), password))
      } yield assertTrue(actual == expected)
    },
    test("should extract iri and password") {
      val userIri  = "http://rdfh.ch/users/someUser"
      val password = "somePassword"
      val params = QueryParams(
        ("iri", userIri),
        ("password", password)
      )
      val req = Request.get(URL.empty.setQueryParams(params))
      for {
        actual  <- AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName)
        expected = Some(KnoraPasswordCredentialsV2(UserIdentifierADM(maybeIri = Some(userIri)), password))
      } yield assertTrue(actual == expected)
    }
  )

  val spec = suite("AuthenticatorServiceLiveSpec")(
    suite("given header authentication")(headerValidSuite, headerInvalidSuite),
    suite("given query parameters authentication")(queryParamValidSuite, queryParamInvalidSuite),
    suite("when nothing is given")(
      test("should return None") {
        for {
          actual <- AuthenticatorServiceLive.extractCredentialsFromRequest(Request.get(URL.empty), cookieName)
        } yield assertTrue(actual.isEmpty)
      }
    )
  )
}
