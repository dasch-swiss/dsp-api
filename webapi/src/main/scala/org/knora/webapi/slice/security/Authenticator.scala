/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security

import org.apache.commons.codec.binary.Base32
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.headers
import org.apache.pekko.http.scaladsl.model.headers.HttpCookie
import org.apache.pekko.http.scaladsl.model.headers.HttpCookiePair
import org.apache.pekko.http.scaladsl.server.RequestContext
import org.apache.pekko.util.ByteString
import spray.json.*
import zio.*

import java.util.Base64

import dsp.errors.AuthenticationException
import dsp.errors.BadCredentialsException
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.admin.responder.usersmessages.*
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.v2.routing.authenticationmessages.*
import org.knora.webapi.messages.v2.routing.authenticationmessages.CredentialsIdentifier.EmailIdentifier
import org.knora.webapi.messages.v2.routing.authenticationmessages.CredentialsIdentifier.IriIdentifier
import org.knora.webapi.messages.v2.routing.authenticationmessages.CredentialsIdentifier.UsernameIdentifier
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraJWTTokenCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraPasswordCredentialsV2
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraSessionCredentialsV2
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.infrastructure.InvalidTokenCache
import org.knora.webapi.slice.infrastructure.Jwt
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.security.Authenticator.BAD_CRED_NOT_VALID
import org.knora.webapi.slice.security.AuthenticatorErrors.LoginFailed

sealed trait AuthenticatorErrors
object AuthenticatorErrors {
  case object LoginFailed extends AuthenticatorErrors
}

/**
 * This trait is used in routes that need authentication support. It provides methods that use the [[RequestContext]]
 * to extract credentials, authenticate provided credentials, and look up cached credentials through the use of the
 * session id. All private methods used in this trait can be found in the companion object.
 */
trait Authenticator {

  /**
   * Returns a User that match the credentials found in the [[RequestContext]].
   * The credentials can be email/password as parameters or auth headers, or session token in a cookie header. If no
   * credentials are found, then a default UserProfile is returned. If the credentials are not correct, then the
   * corresponding error is returned.
   *
   * @param requestContext       a [[RequestContext]] containing the http request
   * @return a [[User]]
   */
  def getUserADM(requestContext: RequestContext): Task[User]

  /**
   * Calculates the cookie name, where the external host and port are encoded as a base32 string
   * to make the name of the cookie unique between environments.
   *
   * The default padding needs to be changed from '=' to '9' because '=' is not allowed inside the cookie!!!
   * This also needs to be changed in all the places that base32 is used to calculate the cookie name, e.g., sipi.
   *
   * @return the calculated cookie name as [[String]]
   */
  def calculateCookieName(): String

  /**
   * Tries to retrieve a [[User]] based on the supplied credentials. If both email/password and session
   * token are supplied, then the user profile for the session token is returned. This method should only be used
   * with authenticated credentials.
   *
   * @param credentials          the user supplied credentials.
   * @return a [[User]]
   *
   *         [[AuthenticationException]] when the IRI can not be found inside the token, which is probably a bug.
   */
  def getUserADMThroughCredentialsV2(credentials: KnoraCredentialsV2): Task[User]
  def verifyJwt(jwtToken: String): Task[User] = getUserADMThroughCredentialsV2(KnoraJWTTokenCredentialsV2(jwtToken))

  /**
   * Used to logout the user, i.e. returns a header deleting the cookie and puts the token on the 'invalidated' list.
   *
   * @param requestContext a [[RequestContext]] containing the http request
   * @return a [[HttpResponse]]
   */
  def doLogoutV2(requestContext: RequestContext): Task[HttpResponse]

  /**
   * Checks if the provided credentials are valid, and if so returns a JWT token for the client to save.
   *
   * @param credentials          the user supplied [[KnoraPasswordCredentialsV2]] containing the user's login information.
   * @return a [[HttpResponse]] containing either a failure message or a message with a cookie header containing
   *         the generated session id.
   */
  def doLoginV2(credentials: KnoraPasswordCredentialsV2): Task[HttpResponse]

  def authenticate(userIri: UserIri, password: String): IO[LoginFailed.type, Jwt]
  def authenticate(username: Username, password: String): IO[LoginFailed.type, Jwt]
  def authenticate(email: Email, password: String): IO[LoginFailed.type, Jwt]

  /**
   * Checks if the credentials provided in [[RequestContext]] are valid.
   *
   * @param requestContext a [[RequestContext]] containing the http request
   * @return a [[HttpResponse]]
   */
  def doAuthenticateV2(requestContext: RequestContext): Task[HttpResponse]

  /**
   * Returns a simple login form for testing purposes
   *
   * @param requestContext    a [[RequestContext]] containing the http request
   * @return                  a [[HttpResponse]] with an html login form
   */
  def presentLoginFormV2(requestContext: RequestContext): Task[HttpResponse]

  /**
   * Tries to authenticate the supplied credentials (email/password or token). In the case of email/password,
   * authentication is performed checking if the supplied email/password combination is valid by retrieving the
   * user's profile. In the case of the token, the token itself is validated. If both are supplied, then both need
   * to be valid.
   *
   * @param credentials          the user supplied and extracted credentials.
   * @return true if the credentials are valid. If the credentials are invalid, then the corresponding exception
   *         will be returned.
   *
   *         [[BadCredentialsException]] when no credentials are supplied; when user is not active;
   *         when the password does not match; when the supplied token is not valid.
   */
  def authenticateCredentialsV2(credentials: KnoraCredentialsV2): Task[Unit]
}

object Authenticator {
  val BAD_CRED_NONE_SUPPLIED = "bad credentials: none found"
  val BAD_CRED_NOT_VALID     = "bad credentials: not valid"
}

final case class AuthenticatorLive(
  private val appConfig: AppConfig,
  private val userService: UserService,
  private val jwtService: JwtService,
  private val scopeResolver: ScopeResolver,
  private val passwordService: PasswordService,
  private val cache: InvalidTokenCache,
) extends Authenticator {

  /**
   * Checks if the provided credentials are valid, and if so returns a JWT token for the client to save.
   *
   * @param credentials          the user supplied [[KnoraPasswordCredentialsV2]] containing the user's login information.
   * @return a [[HttpResponse]] containing either a failure message or a message with a cookie header containing
   *         the generated session id.
   */
  override def doLoginV2(credentials: KnoraPasswordCredentialsV2): Task[HttpResponse] =
    for {
      _ <- authenticateCredentialsV2(credentials)
      user <- credentials.identifier match {
                case CredentialsIdentifier.IriIdentifier(userIri)       => getUserByIri(userIri)
                case CredentialsIdentifier.EmailIdentifier(email)       => getUserByEmail(email)
                case CredentialsIdentifier.UsernameIdentifier(username) => getUserByUsername(username)
              }
      cookieDomain = Some(appConfig.cookieDomain)
      jwtString   <- createToken(user).map(_.jwtString)

      httpResponse = HttpResponse(
                       headers = List(
                         headers.`Set-Cookie`(
                           HttpCookie(
                             calculateCookieName(),
                             jwtString,
                             domain = cookieDomain,
                             path = Some("/"),
                             httpOnly = true,
                           ),
                         ),
                       ), // set path to "/" to make the cookie valid for the whole domain (and not just a segment like v1 etc.)
                       status = StatusCodes.OK,
                       entity = HttpEntity(
                         ContentTypes.`application/json`,
                         JsObject(
                           "token" -> JsString(jwtString),
                         ).compactPrint,
                       ),
                     )

    } yield httpResponse

  override def authenticate(userIri: UserIri, password: String): IO[AuthenticatorErrors.LoginFailed.type, Jwt] =
    for {
      user <- getUserByIri(userIri).orElseFail(LoginFailed)
      jwt  <- ensureActiveUserAndPasswordMatch(user, password)
    } yield jwt

  override def authenticate(username: Username, password: String): IO[AuthenticatorErrors.LoginFailed.type, Jwt] =
    for {
      user <- getUserByUsername(username).orElseFail(LoginFailed)
      jwt  <- ensureActiveUserAndPasswordMatch(user, password)
    } yield jwt

  override def authenticate(email: Email, password: String): IO[AuthenticatorErrors.LoginFailed.type, Jwt] =
    for {
      user <- getUserByEmail(email).orElseFail(LoginFailed)
      jwt  <- ensureActiveUserAndPasswordMatch(user, password)
    } yield jwt

  /* check if the user is active, if not, then no need to check the password */
  private def ensureActiveUserAndPasswordMatch(
    user: User,
    password: String,
  ): IO[AuthenticatorErrors.LoginFailed.type, Jwt] =
    for {
      _   <- ZIO.fail(LoginFailed).when(!user.isActive)
      _   <- ZIO.fail(LoginFailed).when(KnoraUserRepo.builtIn.findOneBy(_.id.value == user.id).isDefined)
      _   <- ZIO.fail(LoginFailed).when(!user.password.exists(passwordService.matchesStr(password, _)))
      jwt <- createToken(user)
    } yield jwt

  private def createToken(user: User) = scopeResolver.resolve(user).flatMap(jwtService.createJwt(user.userIri, _))

  /**
   * Returns a simple login form for testing purposes
   *
   * @param requestContext    a [[RequestContext]] containing the http request
   * @return                  a [[HttpResponse]] with an html login form
   */
  override def presentLoginFormV2(requestContext: RequestContext): Task[HttpResponse] = {
    val apiUrl = appConfig.knoraApi.externalKnoraApiBaseUrl
    val form =
      s"""
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
        """.stripMargin

    val httpResponse = HttpResponse(
      status = StatusCodes.OK,
      entity = HttpEntity(
        ContentTypes.`text/html(UTF-8)`,
        form,
      ),
    )

    ZIO.succeed(httpResponse)
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // Authentication ENTRY POINT
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Checks if the credentials provided in [[RequestContext]] are valid.
   *
   * @param requestContext a [[RequestContext]] containing the http request
   * @return a [[HttpResponse]]
   */
  override def doAuthenticateV2(requestContext: RequestContext): Task[HttpResponse] =
    for {
      credentials <- ZIO
                       .fromOption(extractCredentialsV2(requestContext))
                       .orElseFail(BadCredentialsException(BAD_CRED_NOT_VALID))
      response <- authenticateCredentialsV2(credentials).as {
                    HttpResponse(
                      status = StatusCodes.OK,
                      entity = HttpEntity(
                        ContentTypes.`application/json`,
                        JsObject(
                          "message" -> JsString("credentials are OK"),
                        ).compactPrint,
                      ),
                    )
                  }
    } yield response

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // LOGOUT ENTRY POINT
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Used to logout the user, i.e. returns a header deleting the cookie and puts the token on the 'invalidated' list.
   *
   * @param requestContext a [[RequestContext]] containing the http request
   * @return a [[HttpResponse]]
   */
  override def doLogoutV2(requestContext: RequestContext): Task[HttpResponse] = ZIO.attempt {

    val credentials  = extractCredentialsV2(requestContext)
    val cookieDomain = Some(appConfig.cookieDomain)

    credentials match {
      case Some(KnoraSessionCredentialsV2(sessionToken)) =>
        cache.put(sessionToken)

        HttpResponse(
          headers = List(
            headers.`Set-Cookie`(
              HttpCookie(
                calculateCookieName(),
                "",
                domain = cookieDomain,
                path = Some("/"),
                httpOnly = true,
                expires = Some(DateTime(1970, 1, 1, 0, 0, 0)),
                maxAge = Some(0),
              ),
            ),
          ),
          status = StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            JsObject(
              "status"  -> JsNumber(0),
              "message" -> JsString("Logout OK"),
            ).compactPrint,
          ),
        )
      case Some(KnoraJWTTokenCredentialsV2(jwtToken)) =>
        cache.put(jwtToken)

        HttpResponse(
          headers = List(
            headers.`Set-Cookie`(
              HttpCookie(
                calculateCookieName(),
                "",
                domain = cookieDomain,
                path = Some("/"),
                httpOnly = true,
                expires = Some(DateTime(1970, 1, 1, 0, 0, 0)),
              ),
            ),
          ),
          status = StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            JsObject(
              "status"  -> JsNumber(0),
              "message" -> JsString("Logout OK"),
            ).compactPrint,
          ),
        )
      case _ =>
        // nothing to do
        HttpResponse(
          status = StatusCodes.OK,
          entity = HttpEntity(
            ContentTypes.`application/json`,
            JsObject(
              "status"  -> JsNumber(0),
              "message" -> JsString("Logout OK"),
            ).compactPrint,
          ),
        )
    }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // GET USER PROFILE / AUTHENTICATION ENTRY POINT
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Returns a User that match the credentials found in the [[RequestContext]].
   * The credentials can be email/password as parameters or auth headers, or session token in a cookie header. If no
   * credentials are found, then a default UserProfile is returned. If the credentials are not correct, then the
   * corresponding error is returned.
   *
   * @param requestContext       a [[RequestContext]] containing the http request
   * @return a [[User]]
   */
  override def getUserADM(requestContext: RequestContext): Task[User] =
    ZIO
      .fromOption(extractCredentialsV2(requestContext))
      .flatMap(getUserADMThroughCredentialsV2(_).asSomeError.map(_.ofType(UserInformationType.Full)))
      .unsome
      .map(_.getOrElse(KnoraSystemInstances.Users.AnonymousUser))

  /**
   * Tries to authenticate the supplied credentials (email/password or token). In the case of email/password,
   * authentication is performed checking if the supplied email/password combination is valid by retrieving the
   * user's profile. In the case of the token, the token itself is validated. If both are supplied, then both need
   * to be valid.
   *
   * @param credentials          the user supplied and extracted credentials.
   * @return true if the credentials are valid. If the credentials are invalid, then the corresponding exception
   *         will be returned.
   *
   *         [[BadCredentialsException]] when no credentials are supplied; when user is not active;
   *         when the password does not match; when the supplied token is not valid.
   */
  override def authenticateCredentialsV2(credentials: KnoraCredentialsV2): Task[Unit] =
    credentials match {
      case passCreds: KnoraPasswordCredentialsV2 =>
        for {
          user <- passCreds.identifier match {
                    case CredentialsIdentifier.IriIdentifier(userIri)       => getUserByIri(userIri)
                    case CredentialsIdentifier.EmailIdentifier(email)       => getUserByEmail(email)
                    case CredentialsIdentifier.UsernameIdentifier(username) => getUserByUsername(username)
                  }
          _ <- ensureActiveUserAndPasswordMatch(user, passCreds.password)
                 .orElseFail(BadCredentialsException(BAD_CRED_NOT_VALID))
        } yield ()
      case KnoraJWTTokenCredentialsV2(jwtToken) =>
        ZIO
          .fail(BadCredentialsException(BAD_CRED_NOT_VALID))
          .unless(jwtService.isTokenValid(jwtToken))
          .unit
      case KnoraSessionCredentialsV2(sessionToken) =>
        ZIO
          .fail(BadCredentialsException(BAD_CRED_NOT_VALID))
          .unless(jwtService.isTokenValid(sessionToken))
          .unit
    }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // HELPER METHODS
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Tries to extract the credentials from the requestContext (parameters, auth headers, token)
   *
   * @param requestContext a [[RequestContext]] containing the http request
   * @return an optional [[KnoraCredentialsV2]].
   */
  private def extractCredentialsV2(requestContext: RequestContext): Option[KnoraCredentialsV2] = {

    val credentialsFromParameters: Option[KnoraCredentialsV2] = extractCredentialsFromParametersV2(requestContext)

    val credentialsFromHeaders: Option[KnoraCredentialsV2] = extractCredentialsFromHeaderV2(requestContext)

    // return found credentials based on precedence: 1. url parameters, 2. header (basic auth, token)
    val credentials = if (credentialsFromParameters.nonEmpty) {
      credentialsFromParameters
    } else {
      credentialsFromHeaders
    }

    credentials
  }

  /**
   * Tries to extract credentials supplied as URL parameters.
   *
   * @param requestContext the HTTP request context.
   * @return an optional [[KnoraCredentialsV2]].
   */
  private def extractCredentialsFromParametersV2(requestContext: RequestContext): Option[KnoraCredentialsV2] = {
    // extract email/password from parameters
    val params: Map[String, Seq[String]] = requestContext.request.uri.query().toMultiMap

    // check for iri, email, or username parameters
    val maybeIriIdentifier: Option[String]      = params.get("iri").map(_.head)
    val maybeEmailIdentifier: Option[String]    = params.get("email").map(_.head)
    val maybeUsernameIdentifier: Option[String] = params.get("username").map(_.head)
    val maybeIdentifier: Option[String] =
      List(maybeIriIdentifier, maybeEmailIdentifier, maybeUsernameIdentifier).flatten.headOption

    val maybePassword: Option[String] = params.get("password").map(_.head)

    val maybePassCreds: Option[KnoraPasswordCredentialsV2] =
      if (maybeIdentifier.nonEmpty && maybePassword.nonEmpty)
        CredentialsIdentifier
          .fromOptions(iri = maybeIriIdentifier, email = maybeEmailIdentifier, username = maybeUsernameIdentifier)
          .map(KnoraPasswordCredentialsV2(_, maybePassword.get))
      else None

    val maybeToken: Option[String] = params get "token" map (_.head)

    val maybeTokenCreds: Option[KnoraJWTTokenCredentialsV2] = if (maybeToken.nonEmpty) {
      Some(KnoraJWTTokenCredentialsV2(maybeToken.get))
    } else {
      None
    }

    // prefer password credentials
    if (maybePassCreds.nonEmpty) {
      maybePassCreds
    } else {
      maybeTokenCreds
    }
  }

  /**
   * Tries to extract the credentials (email/password, token) from the authorization header and the session token
   * from the cookie header.
   *
   * The authorization header looks something like this: 'Authorization: Basic xyz, Bearer xyz.xyz.xyz'
   * if both the email/password and token are sent.
   *
   * If more then one set of credentials is found, then they are selected as follows:
   *    1. email/password
   *    2. authorization token
   *    3. session token
   *
   * @param requestContext   the HTTP request context.
   * @return an optional [[KnoraCredentialsV2]].
   */
  private def extractCredentialsFromHeaderV2(requestContext: RequestContext): Option[KnoraCredentialsV2] = {

    // Session token from cookie header
    val cookies: Seq[HttpCookiePair] = requestContext.request.cookies
    val maybeSessionCreds: Option[KnoraSessionCredentialsV2] =
      cookies.find(_.name == calculateCookieName()) match {
        case Some(authCookie) =>
          val value: String = authCookie.value
          Some(KnoraSessionCredentialsV2(value))
        case None =>
          None
      }

    // Authorization header
    val headers: Seq[HttpHeader] = requestContext.request.headers
    val (maybePassCreds, maybeTokenCreds) = headers.find(_.name == "Authorization") match {
      case Some(authHeader: HttpHeader) =>
        // the authorization header can hold different schemes
        val credsArr: Array[String] = authHeader.value().split(",")

        // in v2 we support the basic scheme
        val maybeBasicAuthValue = credsArr.find(_.contains("Basic"))

        // try to decode email/password
        val (maybeEmail, maybePassword) = maybeBasicAuthValue match {
          case Some(value) =>
            val trimmedValue    = value.substring(5).trim() // remove 'Basic '
            val decodedValue    = ByteString.fromArray(Base64.getDecoder.decode(trimmedValue)).decodeString("UTF8")
            val decodedValueArr = decodedValue.split(":", 2)
            (Some(decodedValueArr(0)), Some(decodedValueArr(1)))
          case None =>
            (None, None)
        }

        val maybePassCreds: Option[KnoraPasswordCredentialsV2] =
          (maybeEmail, maybePassword) match {
            case (Some(email), Some(password)) =>
              Email
                .from(email)
                .toOption
                .map(CredentialsIdentifier.EmailIdentifier.apply)
                .map(KnoraPasswordCredentialsV2(_, password))
            case _ => None
          }

        // and the bearer scheme
        val maybeToken = credsArr.find(_.contains("Bearer")) match {
          case Some(value) =>
            Some(value.substring(6).trim()) // remove 'Bearer '
          case None =>
            None
        }

        val maybeTokenCreds: Option[KnoraJWTTokenCredentialsV2] = if (maybeToken.nonEmpty) {
          Some(KnoraJWTTokenCredentialsV2(maybeToken.get))
        } else {
          None
        }

        (maybePassCreds, maybeTokenCreds)

      case None =>
        (None, None)
    }

    // prefer password over token over session
    if (maybePassCreds.nonEmpty) {
      maybePassCreds
    } else if (maybeTokenCreds.nonEmpty) {
      maybeTokenCreds
    } else {
      maybeSessionCreds
    }
  }

  /**
   * Tries to retrieve a [[User]] based on the supplied credentials. If both email/password and session
   * token are supplied, then the user profile for the session token is returned. This method should only be used
   * with authenticated credentials.
   *
   * @param credentials          the user supplied credentials.
   * @return a [[User]]
   *
   *         [[AuthenticationException]] when the IRI can not be found inside the token, which is probably a bug.
   */
  override def getUserADMThroughCredentialsV2(credentials: KnoraCredentialsV2): Task[User] =
    for {
      _ <- authenticateCredentialsV2(credentials)
      user <- credentials match {
                case passCreds: KnoraPasswordCredentialsV2 =>
                  passCreds.identifier match {
                    case CredentialsIdentifier.IriIdentifier(userIri)       => getUserByIri(userIri)
                    case CredentialsIdentifier.EmailIdentifier(email)       => getUserByEmail(email)
                    case CredentialsIdentifier.UsernameIdentifier(username) => getUserByUsername(username)
                  }
                case KnoraJWTTokenCredentialsV2(jwtToken) =>
                  for {
                    userIri <-
                      jwtService
                        .extractUserIriFromToken(jwtToken)
                        .some
                        .orElseFail(
                          AuthenticationException("No IRI found inside token. Please report this as a possible bug."),
                        )
                    iri <- ZIO
                             .fromEither(UserIri.from(userIri))
                             .orElseFail(AuthenticationException("Empty user identifier is not allowed."))
                    user <- getUserByIri(iri)
                  } yield user
                case KnoraSessionCredentialsV2(sessionToken) =>
                  for {
                    userIri <-
                      jwtService
                        .extractUserIriFromToken(sessionToken)
                        .some
                        .orElseFail(
                          AuthenticationException("No IRI found inside token. Please report this as a possible bug."),
                        )
                    iri <- ZIO
                             .fromEither(UserIri.from(userIri))
                             .orElseFail(AuthenticationException("Empty user identifier is not allowed."))
                    user <- getUserByIri(iri)
                  } yield user
              }
    } yield user

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // TRIPLE STORE ACCESS
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  /**
   * Tries to get a [[User]].
   *
   * @param iri           the IRI of the user to be queried
   * @return a [[User]]
   *
   *         [[BadCredentialsException]] when either the supplied email is empty or no user with such an email could be found.
   */
  private def getUserByIri(iri: UserIri): Task[User] =
    userService.findUserByIri(iri).someOrFail(BadCredentialsException(BAD_CRED_NOT_VALID))

  /**
   * Tries to get a [[User]].
   *
   * @param email           the IRI, email, or username of the user to be queried
   * @return a [[User]]
   *
   *         [[BadCredentialsException]] when either the supplied email is empty or no user with such an email could be found.
   */
  private def getUserByEmail(email: Email): Task[User] =
    userService.findUserByEmail(email).someOrFail(BadCredentialsException(BAD_CRED_NOT_VALID))

  /**
   * Tries to get a [[User]].
   *
   * @param username           the IRI, email, or username of the user to be queried
   * @return a [[User]]
   *
   *         [[BadCredentialsException]] when either the supplied email is empty or no user with such an email could be found.
   */
  private def getUserByUsername(username: Username): Task[User] =
    userService.findUserByUsername(username).someOrFail(BadCredentialsException(BAD_CRED_NOT_VALID))

  /**
   * Calculates the cookie name, where the external host and port are encoded as a base32 string
   * to make the name of the cookie unique between environments.
   *
   * The default padding needs to be changed from '=' to '9' because '=' is not allowed inside the cookie!!!
   * This also needs to be changed in all the places that base32 is used to calculate the cookie name, e.g., sipi.
   *
   * @return the calculated cookie name as [[String]]
   */
  override def calculateCookieName(): String = {
    //
    val base32 = new Base32('9'.toByte)
    "KnoraAuthentication" + base32.encodeAsString(appConfig.knoraApi.externalKnoraApiHostPort.getBytes())
  }

}

object AuthenticatorLive {
  val layer = ZLayer.derive[AuthenticatorLive]
}
