package org.knora.webapi.routing.admin
import zhttp.http.Cookie
import zhttp.http.Headers
import zhttp.http.Request
import zio.test._

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraJWTTokenCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraSessionCredentialsV2

object AuthenticatorServiceLiveSpec extends ZIOSpecDefault {

  private val cookieName = "cookieName"

  private implicit val sf = { StringFormatter.initForTest(); StringFormatter.getGeneralInstance }

  val spec = suite("AuthenticatorServiceLiveSpec header extraction")(
    test("should extract user email (basic auth)") {
      val userMail: String = "user@example.com"
      val req              = Request().setHeaders(Headers.basicAuthorizationHeader(userMail, "pass"))
      val actual           = AuthenticatorServiceLive.extractCredentialsFromHeader(req, cookieName)
      val expected         = Some(KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = Some(userMail)), "pass"))
      assertTrue(actual == expected)
    },
    test("should extract jwt token (bearer token)") {
      val jwtToken = "someToken"
      val req      = Request().setHeaders(Headers.bearerAuthorizationHeader(jwtToken))
      val actual   = AuthenticatorServiceLive.extractCredentialsFromHeader(req, cookieName)
      val expected = Some(KnoraJWTTokenCredentialsV2(jwtToken))
      assertTrue(actual == expected)
    },
    test("should extract session cookie") {
      val sessionCookieValue = "session"
      val req                = Request().setHeaders(Headers.cookie(Cookie(cookieName, sessionCookieValue)))
      val actual             = AuthenticatorServiceLive.extractCredentialsFromHeader(req, cookieName)
      val expected           = Some(KnoraSessionCredentialsV2(sessionCookieValue))
      assertTrue(actual == expected)
    }
  )
}
