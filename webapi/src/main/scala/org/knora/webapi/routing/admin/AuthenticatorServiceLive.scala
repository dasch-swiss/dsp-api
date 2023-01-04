/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import akka.actor.ActorRef
import akka.actor.ActorSystem
import zhttp.http._
import zio._

import scala.concurrent.ExecutionContext

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraJWTTokenCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraSessionCredentialsV2
import org.knora.webapi.responders.ActorDeps
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.admin.AuthenticatorServiceLive.extractCredentialsFromRequest

case class AuthenticatorServiceLive(actorDeps: ActorDeps, appConfig: AppConfig, stringFormatter: StringFormatter)
    extends AuthenticatorService {
  private implicit val sf: StringFormatter  = stringFormatter
  private implicit val system: ActorSystem  = actorDeps.system
  private implicit val appActor: ActorRef   = actorDeps.appActor
  private implicit val ec: ExecutionContext = actorDeps.executionContext

  private val authCookieName = Authenticator.calculateCookieName(appConfig)

  override def getUser(request: Request): Task[UserADM] =
    extractCredentialsFromRequest(request, authCookieName)
      .flatMap(credentials => ZIO.fromFuture(_ => Authenticator.getUserADMThroughCredentialsV2(credentials, appConfig)))
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
