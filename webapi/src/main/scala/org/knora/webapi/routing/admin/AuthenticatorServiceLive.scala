/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import zio._
import zio.http._

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraJWTTokenCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraSessionCredentialsV2
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.admin.AuthenticatorServiceLive.extractCredentialsFromRequest

final case class AuthenticatorServiceLive(
  private val authenticator: Authenticator,
  private implicit val stringFormatter: StringFormatter
) extends AuthenticatorService {

  private val authCookieName = authenticator.calculateCookieName()

  override def getUser(request: Request): Task[UserADM] =
    extractCredentialsFromRequest(request, authCookieName).flatMap(authenticator.getUserADMThroughCredentialsV2)
}

object AuthenticatorServiceLive {

  // visible for testing
  def extractCredentialsFromRequest(request: Request, cookieName: String)(implicit
    sf: StringFormatter
  ): Task[Option[KnoraCredentialsV2]] =
    ZIO.attempt(
      extractCredentialsFromParameters(request).orElse(extractCredentialsFromHeader(request, cookieName))
    )

  private def extractCredentialsFromParameters(request: Request)(implicit
    sf: StringFormatter
  ): Option[KnoraCredentialsV2] =
    extractUserPasswordFromParameters(request).orElse(extractTokenFromParameters(request))

  private def getFirstValueFromParamKey(key: String, request: Request): Option[String] = {
    val url    = request.url
    val params = url.queryParams
    params.get(key).map(_.head)
  }

  private def extractUserPasswordFromParameters(
    request: Request
  )(implicit sf: StringFormatter): Option[KnoraPasswordCredentialsV2] = {
    val maybeIri      = getFirstValueFromParamKey("iri", request)
    val maybeEmail    = getFirstValueFromParamKey("email", request)
    val maybeUsername = getFirstValueFromParamKey("username", request)
    val maybePassword = getFirstValueFromParamKey("password", request)
    for {
      _         <- List(maybeIri, maybeEmail, maybeUsername).flatten.headOption // given at least one of iri, email or username
      password  <- maybePassword
      identifier = UserIdentifierADM(maybeIri, maybeEmail, maybeUsername)
    } yield KnoraPasswordCredentialsV2(identifier, password)
  }

  private def extractTokenFromParameters(request: Request): Option[KnoraJWTTokenCredentialsV2] =
    getFirstValueFromParamKey("token", request).map(KnoraJWTTokenCredentialsV2)

  private def extractCredentialsFromHeader(request: Request, cookieName: String)(implicit
    sf: StringFormatter
  ): Option[KnoraCredentialsV2] =
    extractBasicAuthEmail(request)
      .orElse(
        extractBearerToken(request)
          .orElse(extractSessionCookie(request, cookieName))
      )

  private def extractBasicAuthEmail(
    request: Request
  )(implicit sf: StringFormatter): Option[KnoraPasswordCredentialsV2] =
    request.basicAuthorizationCredentials.map(c =>
      KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = Some(c.uname)), c.upassword)
    )

  private def extractBearerToken(request: Request): Option[KnoraJWTTokenCredentialsV2] =
    request.bearerToken.map(KnoraJWTTokenCredentialsV2)

  private def extractSessionCookie(request: Request, cookieName: String) =
    request.cookieValue(cookieName).map(c => KnoraSessionCredentialsV2(c.toString))
}
