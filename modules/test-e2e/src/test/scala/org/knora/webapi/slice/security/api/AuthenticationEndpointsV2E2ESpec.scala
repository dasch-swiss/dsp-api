/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security.api

import sttp.client4.*
import sttp.model.StatusCode
import zio.ZIO
import zio.json.JsonDecoder
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.CheckResponse
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LogoutResponse
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.TokenResponse
import org.knora.webapi.testservices.ResponseOps.*
import org.knora.webapi.testservices.TestApiClient

object AuthenticationEndpointsV2E2ESpec extends E2EZSpec {

  private val validPassword = "test"

  override val e2eSpec = suite("The Authentication Endpoints('v2/authentication')")(
    suite("POST v2/authentication (login/get token)")(
      ZIO.succeed(
        List(
          LoginPayload.EmailPassword(rootUser.getEmail, validPassword),
          LoginPayload.UsernamePassword(rootUser.getUsername, validPassword),
          LoginPayload.IriPassword(rootUser.userIri, validPassword),
        ).map(payload =>
          test(s"with valid ${payload.getClass.getSimpleName} should return JWT token") {
            TestApiClient
              .postJson[TokenResponse, LoginPayload](uri"/v2/authentication", payload)
              .flatMap(_.assert200)
              .map(tr => assertTrue(tr.token.nonEmpty))
          },
        ) ++
          List(
            LoginPayload.EmailPassword(rootUser.getEmail, "wrong_password"),
            LoginPayload.EmailPassword(Email.unsafeFrom("unknown@example.com"), validPassword),
            LoginPayload.UsernamePassword(rootUser.getUsername, "wrong_password"),
            LoginPayload.UsernamePassword(Username.unsafeFrom("wrong_username"), validPassword),
            LoginPayload.IriPassword(UserIri.makeNew, validPassword),
            LoginPayload.IriPassword(rootUser.userIri, "wrong_password"),
          ).map(payload =>
            test(s"with invalid $payload should return Unauthorized") {
              TestApiClient
                .postJson[TokenResponse, LoginPayload](uri"/v2/authentication", payload)
                .map(r => assertTrue(r.code == StatusCode.Unauthorized))
            },
          ),
      ),
    ),
    suite("GET v2/authentication (check token)")(
      test("valid token in header should return credentials are OK") {
        for {
          response <- TestApiClient.getJson[CheckResponse](uri"/v2/authentication", rootUser)
        } yield assertTrue(
          response.code == StatusCode.Ok,
          response.body == Right(CheckResponse("credentials are OK")),
        )
      },
      test("valid token in cookie should return credentials are OK") {
        for {
          cookieName <- ZIO.serviceWith[Authenticator](_.calculateCookieName())
          jwt        <- TestApiClient.getRootToken
          response   <- TestApiClient.getJson[CheckResponse](uri"/v2/authentication", _.cookie((cookieName, jwt)))
        } yield assertTrue(
          response.code == StatusCode.Ok,
          response.body == Right(CheckResponse("credentials are OK")),
        )
      },
      test("invalid token in cookie should return Unauthorized") {
        for {
          cookieName <- ZIO.serviceWith[Authenticator](_.calculateCookieName())
          response <-
            TestApiClient.getJson[CheckResponse](uri"/v2/authentication", _.cookie((cookieName, "not_a_valid_token")))
        } yield assertTrue(
          response.code == StatusCode.Unauthorized,
          response.body == Right(CheckResponse("Invalid credentials.")),
        )
      },
      test("invalid token in Authorization Bearer header should return Unauthorized") {
        for {
          response <- TestApiClient.getJson[TokenResponse](uri"/v2/authentication", _.auth.bearer("wrong_token"))
        } yield assertTrue(response.code == StatusCode.Unauthorized)
      },
    ),
    suite("DELETE v2/authentication (logout)")(
      test("logout with token should return LogoutResponse") {
        for {
          response <- TestApiClient.deleteJson[LogoutResponse](uri"/v2/authentication", rootUser)
          check    <- response.assert200
        } yield assertTrue(check == LogoutResponse(0, "Logout OK"))
      },
      test("logout with token should invalidate the token") {
        for {
          token <- TestApiClient.getRootToken
          _ <-
            TestApiClient.deleteJson[LogoutResponse](uri"/v2/authentication", _.auth.bearer(token)).flatMap(_.assert200)
          checkAfterLogout <- TestApiClient.getJson[CheckResponse](uri"/v2/authentication", _.auth.bearer(token))
        } yield assertTrue(
          checkAfterLogout.code == StatusCode.Unauthorized,
          checkAfterLogout.body == Right(CheckResponse("Invalid credentials.")),
        )
      },
    ),
  )
}
