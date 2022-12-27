package org.knora.webapi.routing.admin

import akka.actor.ActorRef
import akka.actor.ActorSystem
import zhttp.http._
import zio.Task
import zio.ZIO

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

case class AuthenticatorServiceLive(actorDeps: ActorDeps, appConfig: AppConfig, stringFormatter: StringFormatter)
    extends AuthenticatorService {
  private implicit val sf: StringFormatter  = stringFormatter
  private implicit val system: ActorSystem  = actorDeps.system
  private implicit val appActor: ActorRef   = actorDeps.appActor
  private implicit val ec: ExecutionContext = actorDeps.executionContext

  override def getUser(request: Request): Task[UserADM] =
    ZIO
      .succeed(extractCredentials(request))
      .flatMap(credentials => ZIO.fromFuture(_ => Authenticator.getUserADMThroughCredentialsV2(credentials, appConfig)))

  private def extractCredentials(request: Request): Option[KnoraCredentialsV2] = {
    val basicAuth = request.basicAuthorizationCredentials.map(c =>
      KnoraPasswordCredentialsV2(UserIdentifierADM(Some(c.uname)), c.upassword)
    )
    lazy val bearerToken   = request.bearerToken.map(KnoraJWTTokenCredentialsV2)
    lazy val sessionCookie = request.cookieValue(authCookieName).map(c => KnoraSessionCredentialsV2(c.toString))
    basicAuth.orElse(bearerToken.orElse(sessionCookie))
  }

  private val authCookieName = Authenticator.calculateCookieName(appConfig)
}
