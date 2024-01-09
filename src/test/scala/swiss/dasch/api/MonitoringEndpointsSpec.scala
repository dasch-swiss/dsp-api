/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.tapir.server.ziohttp.{ZioHttpInterpreter, ZioHttpServerOptions}
import swiss.dasch.infrastructure.{Health, HealthCheckService, Metrics}
import swiss.dasch.test.SpecConfigurations
import swiss.dasch.version.BuildInfo
import zio.http.{Request, Root, Status, URL}
import zio.json.ast.Json
import zio.json.{DecoderOps, EncoderOps}
import zio.test.{ZIOSpecDefault, assertTrue}
import zio.{Ref, UIO, ULayer, ZIO, ZLayer}

object MonitoringEndpointsSpec extends ZIOSpecDefault {

  private def executeRequest(request: Request) =
    for {
      app <- ZIO.serviceWith[MonitoringEndpointsHandler](handler =>
               ZioHttpInterpreter(ZioHttpServerOptions.default).toHttp(handler.endpoints)
             )
      response <- app.runZIO(request).logError
    } yield response

  private val healthEndpointsSuite = suite("get /health")(
    test("when healthy should return status UP") {
      for {
        _        <- MockHealthCheckService.setHealthUp()
        response <- executeRequest(Request.get(URL(Root / "health")))
        bodyJson <- response.body.asString
        status    = response.status
      } yield assertTrue(
        status == Status.Ok,
        bodyJson.fromJson[Json] == "{\"status\":\"UP\"}".fromJson[Json]
      )
    },
    test("when unhealthy should return status DOWN") {
      for {
        _        <- MockHealthCheckService.setHealthDown()
        response <- executeRequest(Request.get(URL(Root / "health")))
        bodyJson <- response.body.asString
        status    = response.status
      } yield {
        assertTrue(
          status == Status.ServiceUnavailable,
          bodyJson.fromJson[Json] == "{\"status\":\"DOWN\"}".fromJson[Json]
        )
      }
    }
  )
  val infoEndpointSuite = suite("get /info")(
    test("should return 200") {
      for {
        response     <- executeRequest(Request.get(URL(Root / "info")))
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
            buildTime = BuildInfo.builtAtString,
            gitCommit = BuildInfo.gitCommit
          ).toJson
        )
      }
    }
  )

  val spec = suite("MonitoringEndpoints")(healthEndpointsSuite, infoEndpointSuite)
    .provide(
      MonitoringEndpointsHandler.layer,
      MonitoringEndpoints.layer,
      MockHealthCheckService.layer,
      BaseEndpoints.layer,
      AuthServiceLive.layer,
      SpecConfigurations.jwtConfigLayer,
      Metrics.layer
    )
}

final class MockHealthCheckService(val statusRef: Ref[Health]) extends HealthCheckService {
  override def check: UIO[Health] = statusRef.get
}
object MockHealthCheckService {
  val layer: ULayer[MockHealthCheckService] = ZLayer {
    Ref.make(Health.up()).map(new MockHealthCheckService(_))
  }

  def setHealthUp()   = ZIO.serviceWithZIO[MockHealthCheckService](_.statusRef.set(Health.up()))
  def setHealthDown() = ZIO.serviceWithZIO[MockHealthCheckService](_.statusRef.set(Health.down()))
}
