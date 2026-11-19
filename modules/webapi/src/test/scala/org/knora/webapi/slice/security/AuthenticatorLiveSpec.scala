/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.security

import org.junit.runner.RunWith
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.ztapir.*
import zio.*
import zio.http.Body
import zio.http.Header
import zio.http.Request
import zio.http.Response
import zio.http.Status
import zio.http.URL
import zio.test.*

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import org.knora.jwt.Ed25519TestSigner
import org.knora.jwt.JwtClaim
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.FamilyName
import org.knora.webapi.slice.admin.domain.model.GivenName
import org.knora.webapi.slice.admin.domain.model.GroupIri
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
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.admin.repo.service.AdministrativePermissionRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraGroupRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraProjectRepoLive
import org.knora.webapi.slice.admin.repo.service.KnoraUserRepoLive
import org.knora.webapi.slice.api.v3.EndpointHelper
import org.knora.webapi.slice.api.v3.V3BaseEndpoint
import org.knora.webapi.slice.api.v3.V3ErrorCode
import org.knora.webapi.slice.api.v3.projects.V3ProjectsEndpoints
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.domain.LanguageCode
import org.knora.webapi.slice.common.service.IriConverter
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.InvalidTokenCache
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.infrastructure.JwtServiceLive
import org.knora.webapi.slice.infrastructure.Scope as AuthScope
import org.knora.webapi.slice.ontology.repo.service.OntologyCacheFake
import org.knora.webapi.slice.ontology.repo.service.OntologyRepoLive
import org.knora.webapi.store.triplestore.api.TriplestoreServiceInMemory

@RunWith(classOf[DspZTestJUnitRunner])
class AuthenticatorLiveSpec extends ZIOSpecDefault {

  private val authenticator = ZIO.serviceWithZIO[Authenticator]
  private val userRepo      = ZIO.serviceWithZIO[KnoraUserRepo]
  private val jwtService    = ZIO.serviceWithZIO[JwtService]

  private val password                       = "secret"
  private def createUser(status: UserStatus) =
    for {
      passwordHash <- ZIO.serviceWith[PasswordService](_.hashPassword(Password.unsafeFrom(password)))
      user          = KnoraUser(
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

  private object endpointHelper extends EndpointHelper

  private val serviceKeys = Ed25519TestSigner.generateKeyPair()
  private val trustedKeys = TrustedServiceKeys
    .parse(s"""[{"name":"svc-a","kid":"a-1","publicKey":"${Ed25519TestSigner.spkiBase64(serviceKeys.getPublic)}"}]""")
    .fold(e => throw new IllegalStateException(e), identity)
  private val knoraApi = KnoraApi("0.0.0.0", 3333, "https", "api.example.org", 443, 3333)

  /** The claims of a current service token, valid for the service verifier unless `subject` names someone else. */
  private def serviceClaim(subject: String = "svc-a"): UIO[JwtClaim] =
    Clock.instant.map(now =>
      JwtClaim(
        issuer = Some("svc-a"),
        subject = Some(subject),
        audience = Some(Set("https://api.example.org")),
        issuedAt = Some(now.getEpochSecond),
        expiration = Some(now.getEpochSecond + 300),
        jwtId = Some("jti-1"),
      ),
    )

  private def serviceToken(subject: String = "svc-a"): UIO[String] =
    serviceClaim(subject).map(Ed25519TestSigner.sign(_, "a-1", serviceKeys.getPrivate))

  private def v3Base(auth: Authenticator): V3BaseEndpoint =
    V3BaseEndpoint(auth, new AuthorizationRestService(null, null), ServiceTokenVerifier(trustedKeys, knoraApi))

  private def exportPolicyProbe(auth: Authenticator): ZServerEndpoint[Any, ZioStreams] =
    v3Base(auth)
      .systemAdminOrExportServicePolicy(oneOf(endpointHelper.notFoundVariant(V3ErrorCode.project_not_found)))
      .get
      .in("probe")
      .serverLogic[Any](_ => _ => ZIO.unit)

  private def bearer(request: Request, token: String): Request =
    request.addHeader(Header.Custom("Authorization", s"Bearer $token"))

  private def run(endpoint: ZServerEndpoint[Any, ZioStreams], request: Request): UIO[Response] =
    ZIO.scoped(ZioHttpInterpreter().toHttp(List(endpoint)).runZIO(request))

  /**
   * The status for a service token, and for an active non-admin user's token on the same request; the second shows
   * that the request reaches the real authentication.
   */
  private def serviceAndUserStatus(endpoint: ZServerEndpoint[Any, ZioStreams], request: Request) =
    for {
      service <- serviceToken().flatMap(token => run(endpoint, bearer(request, token)))
      user    <- createUser(UserStatus.Active)
      userJwt <- jwtService(_.createJwt(user.id, AuthScope.empty))
      asUser  <- run(endpoint, bearer(request, userJwt.jwtString))
    } yield (service.status, asUser.status)

  // The user endpoints never consult the service verifier; this one would admit the token if they did.
  private val serviceTokenSuite = suite("a valid service token is refused by the user authentication")(
    test("the HS256 decoder rejects it, also when its sub names an existing user") {
      for {
        user  <- createUser(UserStatus.Active)
        token <- serviceToken(subject = user.id.value)
        exit  <- authenticator(_.authenticate(token)).exit
      } yield assertTrue(exit == Exit.fail(AuthenticatorError.BadCredentials))
    },
    test("a SystemAdmin v3 endpoint (the imports POST) answers 401, and 403 to a non-admin user's token") {
      for {
        auth    <- ZIO.service[Authenticator]
        endpoint = new V3ProjectsEndpoints(v3Base(auth)).postProjectIriImports
                     .serverLogic[Any](_ => _ => ZIO.dieMessage("must not be reached"))
        project   = URLEncoder.encode("http://rdfh.ch/projects/0001", StandardCharsets.UTF_8)
        request   = Request.post(URL.decode(s"/v3/projects/$project/imports").toOption.get, Body.empty)
        statuses <- serviceAndUserStatus(endpoint, request)
      } yield assertTrue(statuses == (Status.Unauthorized, Status.Forbidden))
    },
    test("a bearer SystemAdmin endpoint of BaseEndpoints answers 401, and 403 to a non-admin user's token") {
      for {
        auth    <- ZIO.service[Authenticator]
        endpoint = BaseEndpoints(auth, new AuthorizationRestService(null, null))
                     .bearerSystemAdminEndpoint(_ => ZIO.unit)
                     .get
                     .in("probe")
                     .serverLogic[Any](_ => _ => ZIO.dieMessage("must not be reached"))
        statuses <- serviceAndUserStatus(endpoint, Request.get(URL.decode("/probe").toOption.get))
      } yield assertTrue(statuses == (Status.Unauthorized, Status.Forbidden))
    },
    test("the export policy admits the same token as the service, and still answers 403 to a non-admin user") {
      for {
        auth     <- ZIO.service[Authenticator]
        statuses <- serviceAndUserStatus(exportPolicyProbe(auth), Request.get(URL.decode("/probe").toOption.get))
      } yield assertTrue(statuses == (Status.Ok, Status.Forbidden))
    },
    test("the export policy answers 401 to a service-signed token whose alg is not exactly EdDSA, or a malformed one") {
      for {
        auth   <- ZIO.service[Authenticator]
        claim  <- serviceClaim()
        headers = List(
                    Ed25519TestSigner.headerJson(alg = "HS256", kid = Some("a-1")),
                    Ed25519TestSigner.headerJson(alg = "eddsa", kid = Some("a-1")),
                    """{"typ":"JWT","kid":"a-1"}""",
                  )
        variants = headers.map(Ed25519TestSigner.signRaw(_, claim.toJson, serviceKeys.getPrivate)) :+
                     "not-a-header.e30.c2ln"
        responses <- ZIO.foreach(variants)(t =>
                       run(exportPolicyProbe(auth), bearer(Request.get(URL.decode("/probe").toOption.get), t)),
                     )
      } yield assertTrue(responses.map(_.status) == List.fill(variants.size)(Status.Unauthorized))
    },
  )

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
      } yield assertTrue(
        user1.userIri == user.id,
      )
    },
    test("given the user exists and the jwt is valid when invalidating the token then authentication should fail") {
      for {
        user  <- createUser(UserStatus.Active)
        jwt   <- jwtService(_.createJwt(user.id, AuthScope.empty))
        _     <- authenticator(_.invalidateToken(jwt.jwtString))
        exit1 <- authenticator(_.authenticate(jwt.jwtString)).exit
      } yield assertTrue(
        exit1 == Exit.fail(AuthenticatorError.BadCredentials),
      )
    },
    test("given the user exists but is inactive and the jwt is valid authentication should succeed") {
      for {
        user  <- createUser(UserStatus.Inactive)
        jwt   <- jwtService(_.createJwt(user.id, AuthScope.empty))
        exit1 <- authenticator(_.authenticate(jwt.jwtString)).exit
      } yield assertTrue(
        exit1 == Exit.fail(AuthenticatorError.UserNotActive),
      )
    },
    serviceTokenSuite,
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
    LicenseRepo.layer,
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
