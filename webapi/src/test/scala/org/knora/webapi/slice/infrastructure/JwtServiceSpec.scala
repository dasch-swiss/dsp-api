/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import net.sf.ehcache.CacheManager
import pdi.jwt.JwtAlgorithm
import pdi.jwt.JwtClaim
import pdi.jwt.JwtHeader
import pdi.jwt.JwtZIOJson
import spray.json.JsString
import zio.Scope
import zio.ZIO
import zio.ZLayer
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
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.routing.Authenticator.AUTHENTICATION_INVALIDATION_CACHE_NAME
import org.knora.webapi.routing.JwtService
import org.knora.webapi.routing.JwtServiceLive
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo

object JwtServiceSpec extends ZIOSpecDefault {

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

  private val issuerStr = "https://dsp-api"

  private val dspIngestConfigLayer =
    ZLayer.succeed(DspIngestConfig("https://dps-ingest", "https://dsp-ingest/audience"))

  private val jwtConfigLayer =
    ZLayer.succeed(JwtConfig("n76lPIwWKNeTodFfZPPPYFn7V24R14aE63A+XgS8MMA=", Duration.ofSeconds(10), Some(issuerStr)))

  private def decodeToken(token: String): ZIO[JwtConfig, Nothing, (JwtHeader, JwtClaim, String)] =
    ZIO.serviceWithZIO[JwtConfig] { jwtConfig =>
      ZIO.fromTry(JwtZIOJson.decodeAll(token, jwtConfig.secret, Seq(JwtAlgorithm.HS256))).orDie
    }

  private def getAudience(token: String) =
    decodeToken(token).map { case (_, claims, _) => claims.audience.head }

  private def getUserIri(token: String) =
    decodeToken(token).map { case (_, claims, _) => claims.subject }

  def initCache = ZIO.succeed {
    val cacheManager = CacheManager.getInstance()
    cacheManager.addCacheIfAbsent(AUTHENTICATION_INVALIDATION_CACHE_NAME)
    cacheManager.clearAll()
  }

  val spec: Spec[TestEnvironment with Scope, Any] = suite("JwtService")(
    test("create a token") {
      for {
        token    <- JwtService(_.createJwt(user, Map("foo" -> JsString("bar"))))
        _         = println(token)
        userIri  <- getUserIri(token.jwtString)
        audience <- getAudience(token.jwtString)
      } yield assertTrue(userIri.contains(user.id), audience == Set("Knora", "Sipi"))
    },
    test("create a token with dsp-ingest audience for sys admins") {
      for {
        token            <- JwtService(_.createJwt(user.copy(permissions = systemAdminPermissions)))
        userIriByService <- JwtService(_.extractUserIriFromToken(token.jwtString))
        userIri          <- getUserIri(token.jwtString)
        audience         <- getAudience(token.jwtString)
      } yield assertTrue(
        userIriByService == userIri,
        userIri.contains(user.id),
        audience.contains("https://dsp-ingest/audience"),
      )
    },
    test("create a token for dsp-ingest") {
      for {
        token    <- JwtService(_.createJwtForDspIngest())
        userIri  <- getUserIri(token.jwtString)
        audience <- getAudience(token.jwtString)
      } yield assertTrue(userIri.contains(issuerStr), audience.contains("https://dsp-ingest/audience"))
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
  ).provide(dspIngestConfigLayer, jwtConfigLayer, JwtServiceLive.layer) @@ TestAspect.withLiveEnvironment @@ TestAspect
    .beforeAll(initCache)
}
