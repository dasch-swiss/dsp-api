/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2.authentication

import zio.*

import scala.annotation.unused

import dsp.errors.BadCredentialsException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LoginForm
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LoginPayload
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LoginPayload.EmailPassword
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LoginPayload.IriPassword
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LoginPayload.UsernamePassword
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.LogoutResponse
import org.knora.webapi.slice.api.v2.authentication.AuthenticationEndpointsV2.TokenResponse
import org.knora.webapi.slice.security.Authenticator
import org.knora.webapi.slice.security.Authenticator.BAD_CRED_NOT_VALID

final class AuthenticationRestService(authenticator: Authenticator, appConfig: AppConfig) {

  def loginForm(@unused ignored: Unit): UIO[String] =
    val apiUrl = appConfig.knoraApi.externalKnoraApiBaseUrl
    val form   =
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

  def authenticate(login: LoginForm): IO[BadCredentialsException, TokenResponse] =
    (for {
      username <- ZIO.fromEither(Username.from(login.username))
      userJwt  <- authenticator.authenticate(username, login.password)
      (_, jwt)  = userJwt
    } yield TokenResponse(jwt))
      .orElseFail(BadCredentialsException(BAD_CRED_NOT_VALID))

  def authenticate(login: LoginPayload): IO[BadCredentialsException, TokenResponse] =
    (login match {
      case IriPassword(iri, password)           => authenticator.authenticate(iri, password)
      case UsernamePassword(username, password) => authenticator.authenticate(username, password)
      case EmailPassword(email, password)       => authenticator.authenticate(email, password)
    }).mapBoth(_ => BadCredentialsException(BAD_CRED_NOT_VALID), (_, jwt) => TokenResponse(jwt))

  def logout(tokenFromBearer: Option[String]) =
    ZIO.foreachDiscard(tokenFromBearer)(authenticator.invalidateToken).ignore.as(LogoutResponse(0, "Logout OK"))
}

object AuthenticationRestService {
  private[authentication] val layer = zio.ZLayer.derive[AuthenticationRestService]
}
