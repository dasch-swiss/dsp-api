package org.knora.webapi.routing.admin

import org.apache.commons.codec.binary.Base32
import zhttp.http._
import zio.Task
import zio.ZIO

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.AppRouter
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraJWTTokenCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraSessionCredentialsV2
import org.knora.webapi.routing.Authenticator

case class AuthenticatorServiceLive(appConfig: AppConfig, stringFormatter: StringFormatter, appRouter: AppRouter)
    extends AuthenticatorService {
  implicit val sf: StringFormatter = stringFormatter
  implicit val system              = appRouter.system
  implicit val ref                 = appRouter.ref
  implicit val ec                  = appRouter.system.dispatcher

  def credsToUser(creds: Option[KnoraCredentialsV2]): Task[UserADM] =
    ZIO.fromFuture(_ =>
      Authenticator.getUserADMThroughCredentialsV2(
        creds,
        appConfig
      )
    )
  override def getUser(request: Request): Task[UserADM] =
    ZIO.succeed(extractCredentials(request)).flatMap(credentials => credsToUser(creds = credentials))

  val authCookieName: String = {
    //
    val base32 = new Base32('9'.toByte)
    "KnoraAuthentication" + base32.encodeAsString(appConfig.knoraApi.externalKnoraApiHostPort.getBytes())
  }

  private def extractCredentials(request: Request): Option[KnoraCredentialsV2] = {
    // Session token from cookie header
    val maybeSessionCreds = request.cookieValue(authCookieName).map(c => KnoraSessionCredentialsV2(c.toString))

    // Authorization header
    val maybeBasicAuthCreds = request.basicAuthorizationCredentials.map(credentials =>
      KnoraPasswordCredentialsV2(UserIdentifierADM(maybeUsername = Some(credentials.uname)), credentials.upassword)
    )
    val maybeBearerTokenCreds = request.bearerToken.map(KnoraJWTTokenCredentialsV2(_))

    maybeBasicAuthCreds.orElse(maybeBearerTokenCreds.orElse(maybeSessionCreds))
  }
}
