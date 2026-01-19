/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.authentication

import sttp.model.headers.CookieValueWithMeta
import zio.*

import java.time.Instant

import dsp.errors.BadCredentialsException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.CheckResponse
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LoginPayload
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LoginPayload.EmailPassword
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LoginPayload.IriPassword
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LoginPayload.UsernamePassword
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LogoutResponse
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.TokenResponse
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.Authenticator.BAD_CRED_NOT_VALID

final class AuthenticationRestService(
  authenticator: Authenticator,
  appConfig: AppConfig,
) {

  def checkAuthentication(
    token: Option[String],
    usernamePassword: Option[sttp.tapir.model.UsernamePassword],
  ): IO[BadCredentialsException, (Option[CookieValueWithMeta], CheckResponse)] =
    (token, usernamePassword) match {
      case (None, None)            => ZIO.fail(BadCredentialsException(BAD_CRED_NOT_VALID))
      case (Some(jwtString), None) =>
        authenticator
          .isTokenValid(jwtString)
          .mapBoth(
            _ => BadCredentialsException(BAD_CRED_NOT_VALID),
            _ => (Some(setCookie(jwtString)), CheckResponse("credentials are OK")),
          )
      case (_, Some(usernamePassword)) =>
        for {
          email <- ZIO
                     .fromEither(Email.from(usernamePassword.username))
                     .orElseFail(BadCredentialsException(BAD_CRED_NOT_VALID))
          password <- ZIO
                        .fromOption(usernamePassword.password)
                        .orElseFail(BadCredentialsException(BAD_CRED_NOT_VALID))
          resp <- authenticator
                    .authenticate(email, password)
                    .mapBoth(
                      _ => BadCredentialsException(BAD_CRED_NOT_VALID),
                      _ => (None, CheckResponse("credentials are OK")),
                    )
        } yield resp
    }

  private def setCookie(jwtString: String) =
    CookieValueWithMeta.unsafeApply(
      domain = Some(appConfig.cookieDomain),
      httpOnly = true,
      path = Some("/"),
      value = jwtString,
    )

  private val removeCookie = CookieValueWithMeta.unsafeApply(
    domain = Some(appConfig.cookieDomain),
    expires = Some(Instant.EPOCH),
    httpOnly = true,
    maxAge = Some(0),
    path = Some("/"),
    value = "",
  )

  def authenticate(login: LoginPayload): IO[BadCredentialsException, (CookieValueWithMeta, TokenResponse)] =
    (login match {
      case IriPassword(iri, password)           => authenticator.authenticate(iri, password)
      case UsernamePassword(username, password) => authenticator.authenticate(username, password)
      case EmailPassword(email, password)       => authenticator.authenticate(email, password)
    }).mapBoth(
      _ => BadCredentialsException(BAD_CRED_NOT_VALID),
      (_, token) => (setCookie(token.jwtString), TokenResponse(token)),
    )

  def logout(
    tokenFromBearer: Option[String],
    tokenFromCookie: Option[String],
  ): UIO[(CookieValueWithMeta, LogoutResponse)] =
    ZIO
      .foreachDiscard(Set(tokenFromBearer, tokenFromCookie).flatten)(authenticator.invalidateToken)
      .ignore
      .as((removeCookie, LogoutResponse(0, "Logout OK")))
}

object AuthenticationRestService {
  val layer = zio.ZLayer.derive[AuthenticationRestService]
}
