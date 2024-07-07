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
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.SecuredEndpointHandler
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.Authenticator.BAD_CRED_NOT_VALID
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.CheckResponse
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload.EmailPassword
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload.IriPassword
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LoginPayload.UsernamePassword
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.LogoutResponse
import org.knora.webapi.slice.security.api.AuthenticationEndpointsV2.TokenResponse

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
          token =>
            (
              CookieValueWithMeta.unsafeApply(
                domain = Some(appConfig.cookieDomain),
                httpOnly = true,
                path = Some("/"),
                value = token.jwtString,
              ),
              TokenResponse(token.jwtString),
            ),
        )
      },
    )

  val deleteV2Authentication =
    PublicEndpointHandler[(Option[String], Option[String]), (CookieValueWithMeta, LogoutResponse)](
      endpoints.deleteV2Authentication,
      (tokenFromBearer: Option[String], tokenFromCookie: Option[String]) => {
        for {
          _ <- ZIO.foreachDiscard(Seq(tokenFromBearer, tokenFromCookie).flatten)(authenticator.invalidateToken)
        } yield (
          CookieValueWithMeta.unsafeApply(
            domain = Some(appConfig.cookieDomain),
            expires = Some(Instant.MIN),
            httpOnly = true,
            maxAge = Some(0),
            path = Some("/"),
            value = "",
          ),
          LogoutResponse(0, "Logout OK"),
        )
      },
    )

  val getV2Login = PublicEndpointHandler[Unit, String](
    endpoints.getV2Login,
    _ => {
      val apiUrl = appConfig.knoraApi.externalKnoraApiBaseUrl
      val form =
        s"""
           |<html>
           |<body>
           |<div align="center">
           |    <section class="container">
           |        <div class="login">
           |            <h1>DSP-API Login</h1>
           |            <form name="myform" action="$apiUrl/v2/login" method="post">
           |                <p>
           |                    <input type="text" name="username" value="" placeholder="Username">
           |                </p>
           |                <p>
           |                    <input type="password" name="password" value="" placeholder="Password">
           |                </p>
           |                <p class="submit">
           |                    <input type="submit" name="submit" value="Login">
           |                </p>
           |            </form>
           |        </div>
           |
           |    </section>
           |
           |    <section class="about">
           |        <p class="about-author">
           |            &copy; 2015&ndash;2022 <a href="https://dasch.swiss" target="_blank">dasch.swiss</a>
           |    </section>
           |</div>
           |</body>
           |</html>
            """.stripMargin
      ZIO.succeed(form)
    },
  )

  private val secure = List(getV2Authentication).map(mapper.mapSecuredEndpointHandler(_))
  private val public =
    List(postV2Authentication, deleteV2Authentication, getV2Login).map(mapper.mapPublicEndpointHandler(_))
  val allHandlers = secure ++ public
}

object AuthenticationEndpointsV2Handler {
  val layer = ZLayer.derive[AuthenticationEndpointsV2Handler]
}
