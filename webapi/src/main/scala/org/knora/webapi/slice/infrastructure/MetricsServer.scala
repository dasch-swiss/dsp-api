/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import zio.*
import zio.http.*
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.prometheus
import zio.metrics.jvm.DefaultJvmMetrics

import org.knora.webapi.config.InstrumentationServerConfig
import org.knora.webapi.core.State
import org.knora.webapi.slice.admin.api.AdminApiEndpoints
import org.knora.webapi.slice.common.api.ApiV2Endpoints
import org.knora.webapi.slice.infrastructure.api.PrometheusApp

object MetricsServer {

  private val metricsServer: ZIO[Server & PrometheusApp & AdminApiEndpoints & ApiV2Endpoints, Nothing, Unit] = for {
    docs       <- DocsServer.docsEndpoints.map(endpoints => ZioHttpInterpreter().toHttp(endpoints))
    prometheus <- ZIO.service[PrometheusApp]
    _          <- Server.install(prometheus.route ++ docs)
    _          <- ZIO.never
  } yield ()

  val make: ZIO[State & InstrumentationServerConfig & ApiV2Endpoints & AdminApiEndpoints, Throwable, Unit] =
    for {
      apiV2Endpoints    <- ZIO.service[ApiV2Endpoints]
      adminApiEndpoints <- ZIO.service[AdminApiEndpoints]
      config            <- ZIO.service[InstrumentationServerConfig]
      port               = config.port
      interval           = config.interval
      metricsConfig      = MetricsConfig(interval)
      _ <- ZIO.logInfo(
             s"Starting instrumentation http server on http://localhost:$port, find docs on http://localhost:$port/docs"
           )
      _ <- metricsServer
             .provideSome[State](
               ZLayer.succeed(adminApiEndpoints),
               ZLayer.succeed(apiV2Endpoints),
               Server.defaultWithPort(port),
               prometheus.publisherLayer,
               ZLayer.succeed(metricsConfig) >>> prometheus.prometheusLayer,
               Runtime.enableRuntimeMetrics,
               Runtime.enableFiberRoots,
               DefaultJvmMetrics.live.unit,
               PrometheusApp.layer
             )
    } yield ()
}

object DocsServer {

  val docsEndpoints =
    for {
      apiV2       <- ZIO.serviceWith[ApiV2Endpoints](_.endpoints)
      admin       <- ZIO.serviceWith[AdminApiEndpoints](_.endpoints)
      allEndpoints = (apiV2 ++ admin).toList
    } yield SwaggerInterpreter().fromEndpoints[Task](allEndpoints, BuildInfo.name, BuildInfo.version)
}
