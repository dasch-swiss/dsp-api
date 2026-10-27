/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest

import org.junit.runner.RunWith
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import swiss.dasch.config.Configuration.ExecutorConfig
import swiss.dasch.config.Configuration.Features
import swiss.dasch.config.Configuration.IngestConfig
import swiss.dasch.config.Configuration.SipiConfig
import swiss.dasch.config.Configuration.StorageConfig
import zio.*
import zio.http.*
import zio.metrics.connectors.prometheus
import zio.nio.file.Files
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.DspIngestConfig
import org.knora.webapi.config.JwtConfig
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.SystemUser
import org.knora.webapi.responders.admin.AssetPermissionsCache
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.api.admin.service.SparqlPassthroughTestEnv
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.infrastructure.InvalidTokenCache
import org.knora.webapi.slice.infrastructure.JwtService
import org.knora.webapi.slice.infrastructure.JwtServiceLive
import org.knora.webapi.slice.infrastructure.Scope as AuthScope
import org.knora.webapi.slice.security.AuthenticatorError

@RunWith(classOf[DspZTestJUnitRunner])
class IngestModuleSpec extends ZIOSpecDefault {
  import IngestModuleSpec.*

  private def getProjects(token: String) =
    serve(Request.get(URL(Path.root / "ingest" / "projects")).addHeader(Header.Authorization.Bearer(token)))
      .map(_.status)

  /** A token minted like dsp-api's, but by a [[JwtServiceLive]] whose settings `change` alters. */
  private def foreignToken(change: (JwtConfig, DspIngestConfig) => (JwtConfig, DspIngestConfig)) =
    for {
      jwt         <- ZIO.service[JwtConfig]
      ingest      <- ZIO.service[DspIngestConfig]
      cache       <- ZIO.service[InvalidTokenCache]
      (jwt2, ing2) = change(jwt, ingest)
      token       <- JwtServiceLive(jwt2, ing2, cache).createJwt(UserIri.unsafeFrom(SystemUser.id), AuthScope.admin)
    } yield token.jwtString

  val spec = suite("IngestModule")(
    test("accepts a user token from JwtService.createJwt") {
      for {
        token  <- ZIO.serviceWithZIO[JwtService](_.createJwt(UserIri.unsafeFrom(SystemUser.id), AuthScope.admin))
        status <- getProjects(token.jwtString)
      } yield assertTrue(status == Status.Ok)
    },
    test("accepts the service token from JwtService.createJwtForDspIngest") {
      for {
        token  <- ZIO.serviceWithZIO[JwtService](_.createJwtForDspIngest())
        status <- getProjects(token.jwtString)
      } yield assertTrue(status == Status.Ok)
    },
    test("refuses a token without ingest's audience") {
      for {
        token  <- foreignToken((jwt, ingest) => (jwt, ingest.copy(audience = "https://elsewhere")))
        status <- getProjects(token)
      } yield assertTrue(status == Status.Unauthorized)
    },
    test("refuses a token from another issuer") {
      for {
        token  <- foreignToken((jwt, ingest) => (jwt.copy(issuer = Some("https://elsewhere")), ingest))
        status <- getProjects(token)
      } yield assertTrue(status == Status.Unauthorized)
    },
  ).provide(layer, CacheManager.layer, InvalidTokenCache.layer, JwtServiceLive.layer) @@ TestAspect.withLiveClock
}

object IngestModuleSpec {

  /** Ingest over empty temporary storage, behind an authenticator and permission cache that refuse everything. */
  val layer: ULayer[AppConfig.AppConfigurations & IngestServerEndpoints] = {
    val storage = ZLayer.scoped(
      Files.createTempDirectoryScoped(None, List.empty).map(dir => StorageConfig(dir.toString, dir.toString)),
    )
    val refusing = ZLayer.succeed(
      SparqlPassthroughTestEnv.authenticatorAnswering(_ => ZIO.fail(AuthenticatorError.BadCredentials)),
    ) ++ ZLayer(
      AssetPermissionsCache.makeCache(1, 1.minute)(_ => ZIO.dieMessage("unused")).map(AssetPermissionsCache(_)),
    )
    AppConfig.layer >+> ZLayer.makeSome[AppConfig, IngestServerEndpoints](
      IngestModule.layer,
      IngestModule.endpointsLayer,
      prometheus.publisherLayer,
      refusing,
      storage.orDie,
      ZLayer.succeed(SipiConfig(useLocalDev = true, localDevImage = "daschswiss/knora-sipi:latest")),
      ZLayer.succeed(IngestConfig(1)),
      ZLayer.succeed(Features(allowEraseProjects = false)),
      ZLayer.succeed(ExecutorConfig(cpuShare = 0.5)),
    )
  }

  def serve(request: Request): URIO[IngestServerEndpoints, Response] =
    ZIO.serviceWithZIO[IngestServerEndpoints](se =>
      ZIO.scoped(ZioHttpInterpreter().toHttp(se.serverEndpoints).runZIO(request)),
    )
}
