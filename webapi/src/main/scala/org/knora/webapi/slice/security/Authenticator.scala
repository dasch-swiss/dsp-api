/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security

import org.apache.commons.codec.binary.Base32
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.model.headers
import org.apache.pekko.http.scaladsl.model.headers.HttpCookiePair
import org.apache.pekko.http.scaladsl.server.RequestContext
import org.apache.pekko.util.ByteString
import zio.*

import java.util.Base64

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

case object LoginFailed

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

  def verifyJwt(jwtToken: String): IO[LoginFailed.type, User]
  def invalidateToken(jwt: String): IO[LoginFailed.type, Unit]
  def authenticate(userIri: UserIri, password: String): IO[LoginFailed.type, (User, Jwt)]
  def authenticate(username: Username, password: String): IO[LoginFailed.type, (User, Jwt)]
  def authenticate(email: Email, password: String): IO[LoginFailed.type, (User, Jwt)]
}

object Authenticator {
  val BAD_CRED_NOT_VALID = "bad credentials: not valid"
}

final case class AuthenticatorLive(
  private val appConfig: AppConfig,
  private val userService: UserService,
  private val jwtService: JwtService,
  private val scopeResolver: ScopeResolver,
  private val passwordService: PasswordService,
  private val cache: InvalidTokenCache,
) extends Authenticator {

  override def authenticate(userIri: UserIri, password: String): IO[LoginFailed.type, (User, Jwt)] = for {
    user <- getUserByIri(userIri).orElseFail(LoginFailed)
    jwt  <- ensurePasswordMatch(user, password)
    jwt  <- createToken(user)
  } yield (user, jwt)

  override def authenticate(username: Username, password: String): IO[LoginFailed.type, (User, Jwt)] = for {
    user <- getUserByUsername(username).orElseFail(LoginFailed)
    jwt  <- ensurePasswordMatch(user, password)
    jwt  <- createToken(user)
  } yield (user, jwt)

  override def authenticate(email: Email, password: String): IO[LoginFailed.type, (User, Jwt)] = for {
    user <- getUserByEmail(email).orElseFail(LoginFailed)
    jwt  <- ensurePasswordMatch(user, password)
    jwt  <- createToken(user)
  } yield (user, jwt)

  private def ensurePasswordMatch(user: User, password: String): IO[LoginFailed.type, Unit] =
    ZIO.fail(LoginFailed).when(!user.password.exists(passwordService.matchesStr(password, _))).unit

  private def createToken(user: User) = scopeResolver.resolve(user).flatMap(jwtService.createJwt(user.userIri, _))

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
    getUserADMThroughCredentialsV2(credentials).unit

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
  private def getUserADMThroughCredentialsV2(credentials: KnoraCredentialsV2): Task[User] = {
    credentials match {
      case credentials: KnoraPasswordCredentialsV2 =>
        (credentials.identifier match {
          case IriIdentifier(userIri)       => authenticate(userIri, credentials.password)
          case EmailIdentifier(email)       => authenticate(email, credentials.password)
          case UsernameIdentifier(username) => authenticate(username, credentials.password)
        }).map(_._1)
      case t @ (_: KnoraSessionCredentialsV2 | _: KnoraJWTTokenCredentialsV2) =>
        val jwtToken = t match {
          case KnoraJWTTokenCredentialsV2(token) => token
          case KnoraSessionCredentialsV2(token)  => token
        }
        verifyJwt(jwtToken)
    }
  }.orElseFail(BadCredentialsException(BAD_CRED_NOT_VALID))

  override def verifyJwt(jwtToken: String): IO[LoginFailed.type, User] = (for {
    userIri <- jwtService.extractUserIriFromToken(jwtToken).some.map(UserIri.from).right
    user    <- getUserByIri(userIri)
  } yield user).orElseFail(LoginFailed)

  private def getUserByIri(iri: UserIri): IO[LoginFailed.type, User] =
    userService.findUserByIri(iri).some.tap(ensureActiveUser).orElseFail(LoginFailed)

  private def getUserByEmail(email: Email): IO[LoginFailed.type, User] =
    userService.findUserByEmail(email).some.tap(ensureActiveUser).orElseFail(LoginFailed)

  private def getUserByUsername(username: Username): IO[LoginFailed.type, User] =
    userService.findUserByUsername(username).some.tap(ensureActiveUser).orElseFail(LoginFailed)

  private def ensureActiveUser(user: User): IO[LoginFailed.type, Unit] = for {
    _ <- ZIO.fail(LoginFailed).when(!user.isActive)
    _ <- ZIO.fail(LoginFailed).when(KnoraUserRepo.builtIn.findOneBy(_.id.value == user.id).isDefined)
  } yield ()

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
    val base32 = new Base32('9'.toByte)
    "KnoraAuthentication" + base32.encodeAsString(appConfig.knoraApi.externalKnoraApiHostPort.getBytes())
  }

  override def invalidateToken(jwt: String): IO[LoginFailed.type, Unit] =
    verifyJwt(jwt).as(cache.put(jwt))
}

object AuthenticatorLive {
  val layer = ZLayer.derive[AuthenticatorLive]
}
