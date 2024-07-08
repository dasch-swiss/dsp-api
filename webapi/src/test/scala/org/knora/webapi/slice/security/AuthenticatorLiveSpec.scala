/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security

import zio.*
import zio.Exit
import zio.test.*
import zio.test.ZIOSpecDefault

import dsp.errors.BadCredentialsException
import dsp.valueobjects.LanguageCode
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.v2.routing.authenticationmessages.KnoraCredentialsV2.KnoraJWTTokenCredentialsV2
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionRepo
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraUser
import org.knora.webapi.slice.admin.domain.model.Password
import org.knora.webapi.slice.admin.domain.model.SystemAdmin
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.AdministrativePermissionService
import org.knora.webapi.slice.admin.domain.service.GroupService
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserRepo
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.KnoraUserToUserConverter
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.admin.repo.service.AdministrativePermissionRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.InvalidTokenCache
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.infrastructure.JwtServiceLive
import org.knora.webapi.slice.infrastructure.Scope as AuthScope
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

object AuthenticatorLiveSpec extends ZIOSpecDefault {

  private val authenticator = ZIO.serviceWithZIO[Authenticator]
  private val userRepo      = ZIO.serviceWithZIO[KnoraUserRepo]
  private val jwtService    = ZIO.serviceWithZIO[JwtService]

  private val password = "secret"
  private def createUser(status: UserStatus) =
    for {
      passwordHash <- ZIO.serviceWith[PasswordService](_.hashPassword(Password.unsafeFrom(password)))
      user = KnoraUser(
               UserIri.makeNew,
               Username.unsafeFrom("username"),
               Email.unsafeFrom("foo@example.com"),
               FamilyName.unsafeFrom("family"),
               GivenName.unsafeFrom("given"),
               passwordHash,
               LanguageCode.unsafeFrom("en"),
               status,
               Chunk.empty[ProjectIri],
               Chunk.empty[GroupIri],
               SystemAdmin.IsNotSystemAdmin,
               Chunk.empty[ProjectIri],
             )
      user <- userRepo(_.save(user))
    } yield user

  val spec = suite("AuthenticatorLiveSpec")(
    test("given the user does not exist authentication should fail") {
      for {
        exit1 <- authenticator(_.authenticate(Email.unsafeFrom("unknown@example.com"), password)).exit
        exit2 <- authenticator(_.authenticate(Username.unsafeFrom("unknownUser"), password)).exit
        exit3 <- authenticator(_.authenticate(UserIri.makeNew, password)).exit
      } yield assertTrue(
        exit1 == Exit.fail(AuthenticatorError.UserNotFound),
        exit2 == Exit.fail(AuthenticatorError.UserNotFound),
        exit3 == Exit.fail(AuthenticatorError.UserNotFound),
      )
    },
    test("given the user exists but the password is incorrect authentication should fail") {
      for {
        user  <- createUser(UserStatus.Active)
        exit1 <- authenticator(_.authenticate(user.email, "invalidPassword")).exit
        exit2 <- authenticator(_.authenticate(user.username, "invalidPassword")).exit
        exit3 <- authenticator(_.authenticate(user.id, "invalidPassword")).exit
      } yield assertTrue(
        exit1 == Exit.fail(AuthenticatorError.BadCredentials),
        exit2 == Exit.fail(AuthenticatorError.BadCredentials),
        exit3 == Exit.fail(AuthenticatorError.BadCredentials),
      )
    },
    test("given the user exists and the password is correct authentication should succeed") {
      for {
        user  <- createUser(UserStatus.Active)
        user1 <- authenticator(_.authenticate(user.email, password))
        user2 <- authenticator(_.authenticate(user.username, password))
        user3 <- authenticator(_.authenticate(user.id, password))
      } yield assertTrue(
        user1._1.userIri == user.id,
        user2._1.userIri == user.id,
        user3._1.userIri == user.id,
      )
    },
    test("given an inactive user exists and the password is correct authentication should succeed") {
      for {
        user  <- createUser(UserStatus.Inactive)
        exit1 <- authenticator(_.authenticate(user.email, password)).exit
        exit2 <- authenticator(_.authenticate(user.username, password)).exit
        exit3 <- authenticator(_.authenticate(user.id, password)).exit
      } yield assertTrue(
        exit1 == Exit.fail(AuthenticatorError.UserNotActive),
        exit2 == Exit.fail(AuthenticatorError.UserNotActive),
        exit3 == Exit.fail(AuthenticatorError.UserNotActive),
      )
    },
    test("given the user exists and the jwt is valid authentication should succeed") {
      for {
        user  <- createUser(UserStatus.Active)
        jwt   <- jwtService(_.createJwt(user.id, AuthScope.empty))
        user1 <- authenticator(_.authenticate(jwt.jwtString))
        user2 <- authenticator(_.authenticateCredentialsV2(KnoraJWTTokenCredentialsV2(jwt.jwtString)))
      } yield assertTrue(
        user1.userIri == user.id,
        user2 == (),
      )
    },
    test("given the user exists and the jwt is valid when invalidating the token then authentication should fail") {
      for {
        user  <- createUser(UserStatus.Active)
        jwt   <- jwtService(_.createJwt(user.id, AuthScope.empty))
        _     <- authenticator(_.invalidateToken(jwt.jwtString))
        exit1 <- authenticator(_.authenticate(jwt.jwtString)).exit
        exit2 <- authenticator(_.authenticateCredentialsV2(KnoraJWTTokenCredentialsV2(jwt.jwtString))).exit
      } yield assertTrue(
        exit1 == Exit.fail(AuthenticatorError.BadCredentials),
        exit2 == Exit.fail(BadCredentialsException("bad credentials: not valid")),
      )
    },
    test("given the user exists but is inactive and the jwt is valid authentication should succeed") {
      for {
        user  <- createUser(UserStatus.Inactive)
        jwt   <- jwtService(_.createJwt(user.id, AuthScope.empty))
        exit1 <- authenticator(_.authenticate(jwt.jwtString)).exit
        exit2 <- authenticator(_.authenticateCredentialsV2(KnoraJWTTokenCredentialsV2(jwt.jwtString))).exit
      } yield assertTrue(
        exit1 == Exit.fail(AuthenticatorError.UserNotActive),
        exit2 == Exit.fail(BadCredentialsException("bad credentials: not valid")),
      )
    },
  ).provide(
    AdministrativePermissionRepoLive.layer,
    AdministrativePermissionService.layer,
    AppConfig.layer,
    AuthenticatorLive.layer,
    CacheManager.layer,
    GroupService.layer,
    InvalidTokenCache.layer,
    IriConverter.layer,
    IriService.layer,
    JwtServiceLive.layer,
    KnoraGroupRepoLive.layer,
    KnoraGroupService.layer,
    KnoraProjectRepoLive.layer,
    KnoraProjectService.layer,
    KnoraUserRepoLive.layer,
    KnoraUserService.layer,
    KnoraUserToUserConverter.layer,
    OntologyCacheFake.emptyCache,
    OntologyRepoLive.layer,
    PasswordService.layer,
    ProjectService.layer,
    ScopeResolver.layer,
    StringFormatter.test,
    TriplestoreServiceInMemory.emptyLayer,
    UserService.layer,
  ) @@ TestAspect.withLiveEnvironment
}
