/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtClaim
import pdi.jwt.JwtHeader
import pdi.jwt.JwtZIOJson
import spray.json.JsString
import zio.IO
import zio.NonEmptyChunk
import zio.Scope
import zio.ZIO
import zio.ZLayer
import zio.json.DecoderOps
import zio.json.DeriveJsonDecoder
import zio.json.JsonDecoder
import zio.test.Gen
import zio.test.Spec
import zio.test.TestAspect
import zio.test.TestEnvironment
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.check

import java.time.Duration
import java.time.Instant
import java.util.UUID

import dsp.valueobjects.UuidUtil
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.JwtConfig
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.routing.InvalidTokenCache
import org.knora.webapi.routing.JwtService
import org.knora.webapi.routing.JwtServiceLive
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Description
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.SelfJoin
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Status
import org.knora.webapi.slice.admin.domain.model.Permission.Administrative
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.repo.KnoraProjectRepoInMemory
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService

final case class ScopeJs(scope: String)
object ScopeJs {
  implicit val decoder: JsonDecoder[ScopeJs] = DeriveJsonDecoder.gen[ScopeJs]
}

object JwtServiceLiveSpec extends ZIOSpecDefault {

  private val JwtService = ZIO.serviceWithZIO[JwtService]

  private val user: User =
    User(
      id = UserIri.makeNew.value,
      username = "testuser",
      email = "test@example.com",
      givenName = "given",
      familyName = "family",
      status = true,
      lang = "en",
      password = None,
      groups = Seq.empty,
      projects = Seq.empty,
      permissions = PermissionsDataADM(),
    )

  private val systemAdminPermissions = PermissionsDataADM(
    groupsPerProject =
      Map(KnoraProjectRepo.builtIn.SystemProject.id.value -> Seq(KnoraGroupRepo.builtIn.SystemAdmin.id.value)),
  )

  private val shortcode  = Shortcode.unsafeFrom("0001")
  private val projectIri = ProjectIri.makeNew
  private val project1 = KnoraProject(
    projectIri,
    Shortname.unsafeFrom("project1"),
    shortcode,
    None,
    NonEmptyChunk(Description.unsafeFrom(StringLiteralV2.unsafeFrom("foo", None))),
    List.empty,
    None,
    Status.Active,
    SelfJoin.CannotJoin,
    RestrictedView.default,
  )

  private val projectAdminProject1Permissions = PermissionsDataADM(
    administrativePermissionsPerProject =
      Map(project1.id.value -> Set(PermissionADM.from(Administrative.ProjectAdminAll))),
  )

  private val issuerStr = "https://dsp-api"

  private val dspIngestConfigLayer =
    ZLayer.succeed(DspIngestConfig("https://dps-ingest", "https://dsp-ingest/audience"))

  private val jwtConfigLayer =
    ZLayer.succeed(JwtConfig("n76lPIwWKNeTodFfZPPPYFn7V24R14aE63A+XgS8MMA=", Duration.ofSeconds(10), Some(issuerStr)))

  private def decodeToken(token: String): ZIO[JwtConfig, Nothing, (JwtHeader, JwtClaim, String)] =
    ZIO.serviceWithZIO[JwtConfig] { jwtConfig =>
      ZIO.fromTry(JwtZIOJson.decodeAll(token, jwtConfig.secret, Seq(JwtAlgorithm.HS256))).orDie
    }

  private def getClaim[A](token: String, extract: JwtClaim => A) =
    decodeToken(token).map { case (_, claims, _) => extract(claims) }

  private def getClaimZIO[A](token: String, extract: JwtClaim => IO[String, A]) =
    decodeToken(token).flatMap { case (_, claims, _) => extract(claims).mapError(new Exception(_)) }

  private def getScopeClaimValue(token: String) =
    getClaimZIO(token, c => ZIO.fromEither(c.content.fromJson[ScopeJs](ScopeJs.decoder))).map(_.scope)

  val spec: Spec[TestEnvironment with Scope, Any] = (suite("JwtService")(
    test("create a token") {
      for {
        token    <- JwtService(_.createJwt(user, Map("foo" -> JsString("bar"))))
        userIri  <- getClaim(token.jwtString, _.subject)
        audience <- getClaim(token.jwtString, _.audience.getOrElse(Set.empty))
        scope    <- getScopeClaimValue(token.jwtString)
      } yield assertTrue(
        userIri.contains(user.id),
        audience == Set("Knora", "Sipi"),
        scope == "",
      )
    },
    test("create a token with admin scope for system admins") {
      for {
        token <- JwtService(_.createJwt(user.copy(permissions = systemAdminPermissions)))
        scope <- getScopeClaimValue(token.jwtString)
      } yield assertTrue(scope == "admin")
    },
    test("create a token for dspIngest") {
      for {
        token <- JwtService(_.createJwtForDspIngest())
        scope <- getScopeClaimValue(token.jwtString)
      } yield assertTrue(scope == "admin")
    },
    test("create a token with admin scope for project admins") {
      for {
        _     <- ZIO.serviceWithZIO[KnoraProjectRepo](_.save(project1))
        token <- JwtService(_.createJwt(user.copy(permissions = projectAdminProject1Permissions)))
        scope <- getScopeClaimValue(token.jwtString)
      } yield assertTrue(scope == "write:project:0001")
    },
    test("create a token with dsp-ingest audience for sys admins") {
      for {
        token            <- JwtService(_.createJwt(user.copy(permissions = systemAdminPermissions)))
        userIriByService <- JwtService(_.extractUserIriFromToken(token.jwtString))
        userIri          <- getClaim(token.jwtString, _.subject)
        audience         <- getClaim(token.jwtString, _.audience.getOrElse(Set.empty))
      } yield assertTrue(
        userIriByService == userIri,
        userIri.contains(user.id),
        audience.contains("https://dsp-ingest/audience"),
      )
    },
    test("create a token for dsp-ingest") {
      for {
        token    <- JwtService(_.createJwtForDspIngest())
        userIri  <- getClaim(token.jwtString, _.subject)
        audience <- getClaim(token.jwtString, _.audience.getOrElse(Set.empty))
        scope    <- getScopeClaimValue(token.jwtString)
      } yield assertTrue(
        userIri.contains(issuerStr),
        audience.contains("https://dsp-ingest/audience"),
        scope == "admin",
      )
    },
    test("validate a self issued token") {
      for {
        token   <- JwtService(_.createJwt(user))
        isValid <- JwtService(_.validateToken(token.jwtString))
      } yield assertTrue(isValid)
    },
    test("fail to validate an invalid token") {
      def createClaim(
        issuer: Option[String] = Some(issuerStr),
        subject: Option[String] = Some(UserIri.makeNew.value),
        audience: Option[Set[String]] = Some(Set("Knora", "Sipi")),
        issuedAt: Option[Long] = Some(Instant.now.getEpochSecond),
        expiration: Option[Long] = Some(Instant.now.plusSeconds(10).getEpochSecond),
        jwtId: Option[String] = Some(UuidUtil.base64Encode(UUID.randomUUID())),
      ) = JwtClaim(
        issuer = issuer,
        subject = subject,
        audience = audience,
        issuedAt = issuedAt,
        expiration = expiration,
        jwtId = jwtId,
      )
      val issuerMissing   = createClaim(issuer = None)
      val invalidSubject  = createClaim(subject = Some("is-invalid"))
      val missingAudience = createClaim(audience = None)
      val missingIat      = createClaim(issuedAt = None)
      val expired         = createClaim(expiration = Some(Instant.now.minusSeconds(10).getEpochSecond))
      val missingJwtId    = createClaim(jwtId = None)
      check(
        Gen.fromIterable(Seq(issuerMissing, invalidSubject, missingAudience, missingIat, expired, missingJwtId)),
      ) { claim =>
        for {
          secret  <- ZIO.serviceWith[JwtConfig](_.secret)
          token    = JwtZIOJson.encode("""{"typ":"JWT","alg":"HS256"}""", claim.toJson, secret, JwtAlgorithm.HS256)
          isValid <- JwtService(_.validateToken(token))
        } yield assertTrue(!isValid)
      }
    },
  ) @@ TestAspect.withLiveEnvironment @@ TestAspect.beforeAll(ZIO.serviceWith[CacheManager](_.clearAll())))
    .provide(
      CacheManager.layer,
      InvalidTokenCache.layer,
      JwtServiceLive.layer,
      KnoraProjectRepoInMemory.layer,
      KnoraProjectService.layer,
      dspIngestConfigLayer,
      jwtConfigLayer,
    )
}
