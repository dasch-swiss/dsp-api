/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security

import org.apache.commons.codec.binary.Base32
import zio.*

import org.knora.webapi.config.AppConfig
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

enum CredentialsIdentifier {
  case IriIdentifier(userIri: UserIri)        extends CredentialsIdentifier
  case EmailIdentifier(email: Email)          extends CredentialsIdentifier
  case UsernameIdentifier(username: Username) extends CredentialsIdentifier
}

enum KnoraCredentialsV2 {
  case KnoraJwtCredentialsV2(jwtToken: String)                                         extends KnoraCredentialsV2
  case KnoraPasswordCredentialsV2(identifier: CredentialsIdentifier, password: String) extends KnoraCredentialsV2
}
