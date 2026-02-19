/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import swiss.dasch.infrastructure.AggregatedHealth
import swiss.dasch.infrastructure.Health
import swiss.dasch.infrastructure.HealthCheckService
import swiss.dasch.infrastructure.Metrics
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.version.BuildInfo
import zio.Ref
import zio.Scope
import zio.UIO
import zio.ULayer
import zio.ZIO
import zio.ZLayer
import zio.http.Path
import zio.http.Request
import zio.http.Status
import zio.http.URL
import zio.json.DecoderOps
import zio.json.EncoderOps
import zio.json.ast.Json
import zio.test.Spec
import zio.test.TestResult
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

object MonitoringEndpointsSpec extends ZIOSpecDefault {
  def testWithScope[E, Err](label: String)(assertion: => ZIO[E & Scope, Err, TestResult]): Spec[E, Err] =
    zio.test.test(label)(ZIO.scoped(assertion))

  private def executeRequest(request: Request) =
    for {
      app <- ZIO.serviceWith[MonitoringEndpointsHandler](handler =>
               ZioHttpInterpreter(ZioHttpServerOptions.default).toHttp(handler.endpoints),
             )
      response <- app.runZIO(request).logError
    } yield response

  private val healthEndpointsSuite = suite("get /health")(
    testWithScope("when healthy should return status UP") {
      for {
        _        <- MockHealthCheckService.setHealthUp()
        response <- executeRequest(Request.get(URL(Path.root / "health")))
        bodyJson <- response.body.asString
        status    = response.status
      } yield assertTrue(
        status == Status.Ok,
        bodyJson.fromJson[Json] == "{\"status\":\"UP\"}".fromJson[Json],
      )
    },
    testWithScope("when unhealthy should return status DOWN") {
      for {
        _        <- MockHealthCheckService.setHealthDown()
        response <- executeRequest(Request.get(URL(Path.root / "health")))
        bodyJson <- response.body.asString
        status    = response.status
      } yield {
        assertTrue(
          status == Status.ServiceUnavailable,
          bodyJson.fromJson[Json] == "{\"status\":\"DOWN\"}".fromJson[Json],
        )
      }
    },
  )
  val infoEndpointSuite = suite("get /info")(
    testWithScope("should return 200") {
      for {
        response     <- executeRequest(Request.get(URL(Path.root / "info")))
        bodyAsString <- response.body.asString
        status        = response.status
      } yield {
        assertTrue(
          status == Status.Ok,
          bodyAsString == InfoEndpointResponse(
            name = BuildInfo.name,
            version = BuildInfo.version,
            scalaVersion = BuildInfo.scalaVersion,
            sbtVersion = BuildInfo.sbtVersion,
            buildTime = BuildInfo.buildTime,
            gitCommit = BuildInfo.gitCommit,
          ).toJson,
        )
      }
    },
  )

  val spec = suite("MonitoringEndpoints")(healthEndpointsSuite, infoEndpointSuite)
    .provide(
      MonitoringEndpointsHandler.layer,
      MonitoringEndpoints.layer,
      MockHealthCheckService.layer,
      BaseEndpoints.layer,
      AuthServiceLive.layer,
      SpecConfigurations.jwtConfigLayer,
      Metrics.layer,
    )
}

final class MockHealthCheckService(val statusRef: Ref[Health]) extends HealthCheckService {
  override def check: UIO[AggregatedHealth] = statusRef.get.map(h => AggregatedHealth(h.status, None))
}
object MockHealthCheckService {
  val layer: ULayer[MockHealthCheckService] = ZLayer {
    Ref.make(Health.up).map(new MockHealthCheckService(_))
  }

  def setHealthUp()   = ZIO.serviceWithZIO[MockHealthCheckService](_.statusRef.set(Health.up))
  def setHealthDown() = ZIO.serviceWithZIO[MockHealthCheckService](_.statusRef.set(Health.down))
}
