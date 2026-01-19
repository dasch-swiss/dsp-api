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
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.CheckResponse
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LoginPayload
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LoginPayload.EmailPassword
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LoginPayload.IriPassword
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LoginPayload.UsernamePassword
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LogoutResponse
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.TokenResponse
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.Authenticator.BAD_CRED_NOT_VALID

final case class AuthenticationRestService(
  private val authenticator: Authenticator,
  private val appConfig: AppConfig,
) {

  def checkAuthentication(token: Option[String]): IO[BadCredentialsException, (CookieValueWithMeta, CheckResponse)] =
    token match {
      case None            => ZIO.fail(BadCredentialsException(BAD_CRED_NOT_VALID))
      case Some(jwtString) =>
        authenticator
          .isTokenValid(jwtString)
          .mapBoth(
            _ => BadCredentialsException(BAD_CRED_NOT_VALID),
            _ => (setCookie(jwtString), CheckResponse("credentials are OK")),
          )
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
