/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security.api
import sttp.model.headers.CookieValueWithMeta
import zio.ZIO
import zio.ZLayer

import java.time.Instant

import dsp.errors.BadCredentialsException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.SecuredEndpointHandler
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
import dsp.errors.AuthenticationException

case class AuthenticationEndpointsV2Handler(
  appConfig: AppConfig,
  authenticator: Authenticator,
  endpoints: AuthenticationEndpointsV2,
  mapper: HandlerMapper,
) {
  val getV2Authentication =
    SecuredEndpointHandler[Unit, CheckResponse](
      endpoints.getV2Authentication,
      _ => _ => ZIO.succeed(CheckResponse("credentials are OK")),
    )

  val postV2Authentication =
    PublicEndpointHandler[LoginPayload, (CookieValueWithMeta, TokenResponse)](
      endpoints.postV2Authentication,
      (login: LoginPayload) => {
        (login match {
          case IriPassword(iri, password)           => authenticator.authenticate(iri, password)
          case UsernamePassword(username, password) => authenticator.authenticate(username, password)
          case EmailPassword(email, password)       => authenticator.authenticate(email, password)
        }).mapBoth(
          _ => BadCredentialsException(BAD_CRED_NOT_VALID),
          (_, token) => setCookieAndResponse(token),
        ).catchAllDefect(e =>
          ZIO.fail(AuthenticationException("An internal error happened during authentication", Some(e))),
        )
      },
    )

  val deleteV2Authentication =
    PublicEndpointHandler[(Option[String], Option[String]), (CookieValueWithMeta, LogoutResponse)](
      endpoints.deleteV2Authentication,
      (tokenFromBearer: Option[String], tokenFromCookie: Option[String]) => {
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
          }
      },
    )

  val getV2Login = PublicEndpointHandler[Unit, String](
    endpoints.getV2Login,
    _ => {
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
  )

  val postV2Login =
    PublicEndpointHandler[LoginForm, (CookieValueWithMeta, TokenResponse)](
      endpoints.postV2Login,
      (login: LoginForm) => {
        (for {
          username <- ZIO.fromEither(Username.from(login.username))
          token    <- authenticator.authenticate(username, login.password)
        } yield setCookieAndResponse(token._2)).orElseFail(BadCredentialsException(BAD_CRED_NOT_VALID))
      },
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

  private val secure = List(getV2Authentication).map(mapper.mapSecuredEndpointHandler(_))
  private val public =
    List(postV2Authentication, deleteV2Authentication, getV2Login, postV2Login).map(mapper.mapPublicEndpointHandler(_))
  val allHandlers = secure ++ public
}

object AuthenticationEndpointsV2Handler {
  val layer = ZLayer.derive[AuthenticationEndpointsV2Handler]
}
