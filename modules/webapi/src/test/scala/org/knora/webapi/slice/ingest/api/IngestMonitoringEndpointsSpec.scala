/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import org.junit.runner.RunWith
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.server.ziohttp.ZioHttpServerOptions
import zio.*
import zio.http.Path
import zio.http.Request
import zio.http.Status
import zio.http.URL
import zio.json.DecoderOps
import zio.json.EncoderOps
import zio.json.ast.Json
import zio.metrics.connectors.prometheus
import zio.nio.file.Files
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.http.version.BuildInfo
import org.knora.webapi.slice.ingest.config.StorageConfig
import org.knora.webapi.slice.ingest.infrastructure.AggregatedHealth
import org.knora.webapi.slice.ingest.infrastructure.FileSystemHealthIndicatorLive
import org.knora.webapi.slice.ingest.infrastructure.Health
import org.knora.webapi.slice.ingest.infrastructure.HealthCheckService
import org.knora.webapi.slice.ingest.infrastructure.HealthCheckServiceLive
import org.knora.webapi.slice.ingest.testutil.SpecConfigurations

@RunWith(classOf[DspZTestJUnitRunner])
class IngestMonitoringEndpointsSpec extends ZIOSpecDefault {
  private val ingest = Path.root / "ingest"

  private def executeRequest(request: Request) =
    for {
      app <- ZIO.serviceWith[IngestMonitoringServerEndpoints](se =>
               ZioHttpInterpreter(ZioHttpServerOptions.default).toHttp(se.serverEndpoints),
             )
      response <- ZIO.scoped(app.runZIO(request)).logError
    } yield response

  private def getJson(path: Path) =
    for {
      response <- executeRequest(Request.get(URL(path)))
      body     <- response.body.asString
    } yield (response.status, body.fromJson[Json])

  private val healthEndpointsSuite = suite("GET /ingest/health")(
    test("when healthy should return status UP") {
      for {
        _              <- MockHealthCheckService.setHealthUp()
        (status, json) <- getJson(ingest / "health")
      } yield assertTrue(status == Status.Ok, json == "{\"status\":\"UP\"}".fromJson[Json])
    },
    test("when unhealthy should return status DOWN") {
      for {
        _              <- MockHealthCheckService.setHealthDown()
        (status, json) <- getJson(ingest / "health")
      } yield assertTrue(status == Status.ServiceUnavailable, json == "{\"status\":\"DOWN\"}".fromJson[Json])
    },
  )

  private val infoEndpointSuite = suite("GET /ingest/info")(
    test("should return the dsp-api build under the ingest name") {
      for {
        response <- executeRequest(Request.get(URL(ingest / "info")))
        body     <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == InfoEndpointResponse(
          name = "dsp-ingest",
          version = BuildInfo.version,
          scalaVersion = BuildInfo.scalaVersion,
          sbtVersion = "bazel",
          buildTime = BuildInfo.buildTime,
          gitCommit = BuildInfo.gitCommit,
        ).toJson,
        body
          .fromJson[Json]
          .toOption
          .flatMap(_.asObject)
          .map(_.keys.toSet)
          .contains(
            Set("name", "version", "scalaVersion", "sbtVersion", "buildTime", "gitCommit"),
          ),
      )
    },
  )

  private val metricsEndpointSuite = suite("GET /ingest/metrics")(
    test("should serve what the publisher holds") {
      for {
        publisher <- ZIO.service[prometheus.PrometheusPublisher]
        _         <- publisher.set("# a published metric")
        response  <- executeRequest(Request.get(URL(ingest / "metrics")))
        body      <- response.body.asString
      } yield assertTrue(response.status == Status.Ok, body == "# a published metric")
    },
  )

  private val healthOverFileSystemSuite = suite("GET /ingest/health with the file system indicator")(
    test("when the folders exist should return status UP without components") {
      getJson(ingest / "health").map { case (status, json) =>
        assertTrue(status == Status.Ok, json == "{\"status\":\"UP\"}".fromJson[Json])
      }
    },
    test("when a folder is missing should report only the folders component") {
      for {
        _              <- ZIO.serviceWithZIO[StorageConfig](c => Files.deleteRecursive(c.tempPath))
        (status, json) <- getJson(ingest / "health")
        components      = json.toOption.flatMap(_.asObject).flatMap(_.get("components"))
      } yield assertTrue(
        status == Status.ServiceUnavailable,
        components.flatMap(_.asObject).map(_.keys.toSet).contains(Set("folders")),
      )
    },
  ).provide(
    FileSystemHealthIndicatorLive.layer,
    HealthCheckServiceLive.layer,
    IngestAuthenticator.layer,
    IngestBaseEndpoints.layer,
    IngestMonitoringEndpoints.layer,
    IngestMonitoringServerEndpoints.layer,
    prometheus.publisherLayer,
    SpecConfigurations.jwtConfigLayer,
    SpecConfigurations.storageConfigLayer,
  )

  val spec = suite("IngestMonitoringEndpoints")(
    (healthEndpointsSuite + infoEndpointSuite + metricsEndpointSuite).provide(
      IngestAuthenticator.layer,
      IngestBaseEndpoints.layer,
      IngestMonitoringEndpoints.layer,
      IngestMonitoringServerEndpoints.layer,
      MockHealthCheckService.layer,
      prometheus.publisherLayer,
      SpecConfigurations.jwtConfigLayer,
    ),
    healthOverFileSystemSuite,
  )
}

final class MockHealthCheckService(val statusRef: Ref[Health]) extends HealthCheckService {
  override def check: UIO[AggregatedHealth] = statusRef.get.map(h => AggregatedHealth(h.status, None))
}

object MockHealthCheckService {
  val layer: ULayer[MockHealthCheckService] = ZLayer(Ref.make(Health.up).map(new MockHealthCheckService(_)))

  def setHealthUp()   = ZIO.serviceWithZIO[MockHealthCheckService](_.statusRef.set(Health.up))
  def setHealthDown() = ZIO.serviceWithZIO[MockHealthCheckService](_.statusRef.set(Health.down))
}
