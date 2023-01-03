package org.knora.webapi.routing.admin
import zhttp.http.Cookie
import zhttp.http.Headers
import zhttp.http.Request
import zhttp.http.URL
import zio.test._

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraJWTTokenCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraSessionCredentialsV2

object AuthenticatorServiceLiveSpec extends ZIOSpecDefault {

  private val cookieName = "cookieName"

  private implicit val sf: StringFormatter = { StringFormatter.initForTest(); StringFormatter.getGeneralInstance }

  val spec = suite("AuthenticatorServiceLiveSpec")(
    suite("given header authentication")(
      test("should extract user email (basic auth)") {
        val userMail: String = "user@example.com"
        val req              = Request().setHeaders(Headers.basicAuthorizationHeader(userMail, "pass"))
        val actual           = AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName)
        val expected         = Some(KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = Some(userMail)), "pass"))
        assertTrue(actual == expected)
      },
      test("should extract jwt token (bearer token)") {
        val jwtToken = "someToken"
        val req      = Request().setHeaders(Headers.bearerAuthorizationHeader(jwtToken))
        val actual   = AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName)
        val expected = Some(KnoraJWTTokenCredentialsV2(jwtToken))
        assertTrue(actual == expected)
      },
      test("should extract session cookie") {
        val sessionCookieValue = "session"
        val req                = Request().setHeaders(Headers.cookie(Cookie(cookieName, sessionCookieValue)))
        val actual             = AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName)
        val expected           = Some(KnoraSessionCredentialsV2(sessionCookieValue))
        assertTrue(actual == expected)
      }
    ),
    suite("given query parameters authentication")(
      test("should extract jwt token") {
        val jwtToken = "someToken"
        val req      = Request().setUrl(URL.empty.setQueryParams(Map("token" -> List(jwtToken))))
        val actual   = AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName)
        val expected = Some(KnoraJWTTokenCredentialsV2(jwtToken))
        assertTrue(actual == expected)
      },
      test("should extract username and password") {
        val username = "someUsername"
        val password = "somePassword"
        val req =
          Request().setUrl(URL.empty.setQueryParams(Map("username" -> List(username), "password" -> List(password))))
        val actual   = AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName)
        val expected = Some(KnoraPasswordCredentialsV2(UserIdentifierADM(maybeUsername = Some(username)), password))
        assertTrue(actual == expected)
      },
      test("should extract email and password") {
        val email    = "user@example.com"
        val password = "somePassword"
        val req =
          Request().setUrl(URL.empty.setQueryParams(Map("email" -> List(email), "password" -> List(password))))
        val actual   = AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName)
        val expected = Some(KnoraPasswordCredentialsV2(UserIdentifierADM(maybeEmail = Some(email)), password))
        assertTrue(actual == expected)
      },
      test("should extract iri and password") {
        val userIri  = "http://rdfh.ch/users/someUser"
        val password = "somePassword"
        val req =
          Request().setUrl(URL.empty.setQueryParams(Map("iri" -> List(userIri), "password" -> List(password))))
        val actual   = AuthenticatorServiceLive.extractCredentialsFromRequest(req, cookieName)
        val expected = Some(KnoraPasswordCredentialsV2(UserIdentifierADM(maybeIri = Some(userIri)), password))
        assertTrue(actual == expected)
      }
    ),
    suite("when nothing is given")(
      test("should return None") {
        val actual = AuthenticatorServiceLive.extractCredentialsFromRequest(Request(), cookieName)
        assertTrue(actual.isEmpty)
      }
    )
  )
}
