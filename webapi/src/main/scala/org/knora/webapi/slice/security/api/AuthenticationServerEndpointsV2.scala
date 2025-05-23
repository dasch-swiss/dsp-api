/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security.api
import sttp.capabilities.zio.ZioStreams
import sttp.model.headers.CookieValueWithMeta
import sttp.tapir.ztapir.*
import zio.ZIO
import zio.ZLayer

import java.time.Instant

import dsp.errors.AuthenticationException
import dsp.errors.BadCredentialsException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.infrastructure.Jwt
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.Authenticator.BAD_CRED_NOT_VALID
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.CheckResponse
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginForm
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload.EmailPassword
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload.IriPassword
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload.UsernamePassword
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LogoutResponse
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.TokenResponse

case class AuthenticationServerEndpointsV2(
  private val appConfig: AppConfig,
  private val authenticator: Authenticator,
  private val endpoints: AuthenticationEndpointsV2,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    endpoints.getV2Authentication
      .serverLogic(_ => _ => ZIO.succeed(CheckResponse("credentials are OK"))),
    endpoints.postV2Authentication.zServerLogic((login: LoginPayload) =>
      (login match {
        case IriPassword(iri, password)           => authenticator.authenticate(iri, password)
        case UsernamePassword(username, password) => authenticator.authenticate(username, password)
        case EmailPassword(email, password)       => authenticator.authenticate(email, password)
      }).mapBoth(
        _ => BadCredentialsException(BAD_CRED_NOT_VALID),
        (_, token) => setCookieAndResponse(token),
      ).catchAllDefect(e =>
        ZIO.fail(AuthenticationException("An internal error happened during authentication", Some(e))),
      ),
    ),
    endpoints.deleteV2Authentication.zServerLogic((tokenFromBearer: Option[String], tokenFromCookie: Option[String]) =>
      ZIO
        .foreachDiscard(Set(tokenFromBearer, tokenFromCookie).flatten)(authenticator.invalidateToken)
        .ignore
        .as {
          (
            CookieValueWithMeta.unsafeApply(
              domain = Some(appConfig.cookieDomain),
              expires = Some(Instant.EPOCH),
              httpOnly = true,
              maxAge = Some(0),
              path = Some("/"),
              value = "",
            ),
            LogoutResponse(0, "Logout OK"),
          )
        },
    ),
    endpoints.getV2Login.zServerLogic { _ =>
      val apiUrl = appConfig.knoraApi.externalKnoraApiBaseUrl
      val form =
        s"""
           |<html lang="en">
           |  <body>
           |    <div align="center">
           |      <section class="container">
           |        <div class="login">
           |          <h1>DSP-API Login</h1>
           |          <form name="myform" action="$apiUrl/v2/login" method="post">
           |            <p><input type="text" name="username" value="" placeholder="Username"></p>
           |            <p><input type="password" name="password" value="" placeholder="Password"></p>
           |            <p class="submit"><input type="submit" name="submit" value="Login"></p>
           |          </form>
           |        </div>
           |      </section>
           |      <section class="about">
           |        <p class="about-author">&copy; 2015&ndash;2024 <a href="https://dasch.swiss" target="_blank">dasch.swiss</a></p>
           |      </section>
           |    </div>
           |  </body>
           |</html>
            """.stripMargin
      ZIO.succeed(form)
    },
    endpoints.postV2Login.zServerLogic((login: LoginForm) =>
      (for {
        username <- ZIO.fromEither(Username.from(login.username))
        token    <- authenticator.authenticate(username, login.password)
      } yield setCookieAndResponse(token._2)).orElseFail(BadCredentialsException(BAD_CRED_NOT_VALID)),
    ),
  )

  private def setCookieAndResponse(token: Jwt) =
    (
      CookieValueWithMeta.unsafeApply(
        domain = Some(appConfig.cookieDomain),
        httpOnly = true,
        path = Some("/"),
        value = token.jwtString,
      ),
      TokenResponse(token.jwtString),
    )
}

object AuthenticationServerEndpointsV2 {
  val layer = ZLayer.derive[AuthenticationServerEndpointsV2]
}
