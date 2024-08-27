/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security

import org.apache.commons.codec.binary.Base32
import org.apache.pekko.http.scaladsl.model.*
import org.apache.pekko.http.scaladsl.server.RequestContext
import org.apache.pekko.util.ByteString
import zio.*

import java.util.Base64

import dsp.errors.BadCredentialsException
import org.knora.webapi.IRI
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.admin.responder.usersmessages.*
import org.knora.webapi.messages.util.KnoraSystemInstances
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
import org.knora.webapi.slice.security.*
import org.knora.webapi.slice.security.Authenticator.BAD_CRED_NOT_VALID
import org.knora.webapi.slice.security.AuthenticatorError.*
import org.knora.webapi.slice.security.CredentialsIdentifier.*
import org.knora.webapi.slice.security.KnoraCredentialsV2.*

enum AuthenticatorError extends Exception {
  case BadCredentials extends AuthenticatorError
  case UserNotFound   extends AuthenticatorError
  case UserNotActive  extends AuthenticatorError
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

  def invalidateToken(jwt: String): IO[AuthenticatorError, Unit]
  def authenticate(userIri: UserIri, password: String): IO[AuthenticatorError, (User, Jwt)]
  def authenticate(username: Username, password: String): IO[AuthenticatorError, (User, Jwt)]
  def authenticate(email: Email, password: String): IO[AuthenticatorError, (User, Jwt)]
  def authenticate(jwtToken: String): IO[AuthenticatorError, User]
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
  private val invalidTokens: InvalidTokenCache,
) extends Authenticator {

  override def authenticate(userIri: UserIri, password: String): IO[AuthenticatorError, (User, Jwt)] = for {
    user <- getUserByIri(userIri)
    _    <- ensurePasswordMatch(user, password)
    jwt  <- createToken(user)
  } yield (user, jwt)

  override def authenticate(username: Username, password: String): IO[AuthenticatorError, (User, Jwt)] = for {
    user <- getUserByUsername(username)
    _    <- ensurePasswordMatch(user, password)
    jwt  <- createToken(user)
  } yield (user, jwt)

  override def authenticate(email: Email, password: String): IO[AuthenticatorError, (User, Jwt)] = for {
    user <- getUserByEmail(email)
    _    <- ensurePasswordMatch(user, password)
    jwt  <- createToken(user)
  } yield (user, jwt)

  private def ensurePasswordMatch(user: User, password: String): IO[AuthenticatorError, Unit] =
    ZIO.fail(BadCredentials).when(!user.password.exists(passwordService.matchesStr(password, _))).unit

  private def createToken(user: User) = scopeResolver.resolve(user).flatMap(jwtService.createJwt(user.userIri, _))

  /**
   * Returns a User that match the credentials found in the [[RequestContext]].
   * The credentials can be email/password as parameters or auth headers, or session token in a cookie header. If no
   * credentials are found, then a default UserProfile is returned. If the credentials are not correct, then the
   * corresponding error is returned.
   *
   * @param ctx a [[RequestContext]] containing the http request
   * @return a [[User]]
   */
  override def getUserADM(ctx: RequestContext): Task[User] =
    ZIO
      .fromOption(extractCredentials(ctx))
      .flatMap(authenticate(_).asSomeError.map(_.ofType(UserInformationType.Full)))
      .unsome
      .map(_.getOrElse(KnoraSystemInstances.Users.AnonymousUser))

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
  private def extractCredentials(requestContext: RequestContext): Option[KnoraCredentialsV2] = {
    val fromCookie: Option[KnoraJwtCredentialsV2] =
      requestContext.request.cookies
        .find(_.name == calculateCookieName())
        .map(authCookie => KnoraJwtCredentialsV2(authCookie.value))

    // Authorization header
    val headers: Seq[HttpHeader] = requestContext.request.headers
    val (fromBasicAuth, fromBearerToken) = headers.find(_.name == "Authorization") match {
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
                .map(EmailIdentifier.apply)
                .map(KnoraPasswordCredentialsV2(_, password))
            case _ => None
          }

        // and the bearer scheme
        val maybeToken = credsArr.find(_.contains("Bearer")) match {
          case Some(value) =>
            Some(KnoraJwtCredentialsV2(value.substring(6).trim())) // remove 'Bearer '
          case None =>
            None
        }

        (maybePassCreds, maybeToken)

      case None => (None, None)
    }
    fromBasicAuth.orElse(fromBearerToken).orElse(fromCookie)
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
  private def authenticate(credentials: KnoraCredentialsV2): Task[User] = {
    credentials match {
      case credentials: KnoraPasswordCredentialsV2 =>
        (credentials.identifier match {
          case IriIdentifier(userIri)       => authenticate(userIri, credentials.password)
          case EmailIdentifier(email)       => authenticate(email, credentials.password)
          case UsernameIdentifier(username) => authenticate(username, credentials.password)
        }).map(_._1)
      case KnoraJwtCredentialsV2(jwtToken) => authenticate(jwtToken)
    }
  }.orElseFail(BadCredentialsException(BAD_CRED_NOT_VALID))

  override def authenticate(jwtToken: String): IO[AuthenticatorError, User] = for {
    _ <- ZIO.fail(BadCredentials).when(invalidTokens.contains(jwtToken))
    userIri <-
      jwtService.extractUserIriFromToken(jwtToken).logError.some.map(UserIri.from).right.orElseFail(BadCredentials)
    user <- getUserByIri(userIri)
  } yield user

  private def getUserByIri(iri: UserIri): IO[AuthenticatorError, User] =
    userService.findUserByIri(iri).orDie.someOrFail(UserNotFound).tap(ensureActiveUser)

  private def getUserByEmail(email: Email): IO[AuthenticatorError, User] =
    userService.findUserByEmail(email).orDie.someOrFail(UserNotFound).tap(ensureActiveUser)

  private def getUserByUsername(username: Username): IO[AuthenticatorError, User] =
    userService.findUserByUsername(username).orDie.someOrFail(UserNotFound).tap(ensureActiveUser)

  private def ensureActiveUser(user: User): IO[AuthenticatorError, Unit] = for {
    _ <- ZIO.fail(UserNotActive).when(!user.isActive)
    _ <- ZIO.fail(UserNotFound).when(KnoraUserRepo.builtIn.findOneBy(_.id.value == user.id).isDefined)
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

  override def invalidateToken(jwt: String): IO[AuthenticatorError, Unit] =
    authenticate(jwt).as(invalidTokens.put(jwt))
}

object AuthenticatorLive {
  val layer = ZLayer.derive[AuthenticatorLive]
}

sealed trait CredentialsIdentifier
object CredentialsIdentifier {
  def fromOptions(iri: Option[IRI], email: Option[IRI], username: Option[IRI]): Option[CredentialsIdentifier] =
    (iri, email, username) match {
      case (Some(iri), _, _)      => UserIri.from(iri).toOption.map(IriIdentifier.apply)
      case (_, Some(email), _)    => Email.from(email).toOption.map(EmailIdentifier.apply)
      case (_, _, Some(username)) => Username.from(username).toOption.map(UsernameIdentifier.apply)
      case _                      => None
    }

  final case class IriIdentifier(userIri: UserIri)        extends CredentialsIdentifier
  final case class EmailIdentifier(email: Email)          extends CredentialsIdentifier
  final case class UsernameIdentifier(username: Username) extends CredentialsIdentifier
}

sealed trait KnoraCredentialsV2
private object KnoraCredentialsV2 {
  final case class KnoraJwtCredentialsV2(jwtToken: String) extends KnoraCredentialsV2
  final case class KnoraPasswordCredentialsV2(identifier: CredentialsIdentifier, password: String)
      extends KnoraCredentialsV2
}
