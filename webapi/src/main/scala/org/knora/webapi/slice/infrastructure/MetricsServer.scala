/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure

import org.knora.webapi.config.InstrumentationServerConfig
import org.knora.webapi.core.State
import org.knora.webapi.slice.admin.api.AdminApiEndpoints
import org.knora.webapi.slice.common.api.ApiV2Endpoints
import org.knora.webapi.slice.infrastructure.api.PrometheusApp
import sttp.apispec.openapi.OpenAPI
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import zio.*
import zio.http.*
import zio.metrics.connectors.{MetricsConfig, prometheus}
import zio.metrics.jvm.DefaultJvmMetrics

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

  private val replaceServerPort: OpenAPI => OpenAPI = openApi => {
    def newPort(url: String) = if (url.contains("api")) "" else "3333"
    val newServers           = openApi.servers.map(server => server.copy(url = server.url.replace(":3339", newPort(server.url))))
    openApi.copy(servers = newServers)
  }

  val docsEndpoints =
    for {
      apiV2       <- ZIO.serviceWith[ApiV2Endpoints](_.endpoints)
      admin       <- ZIO.serviceWith[AdminApiEndpoints](_.endpoints)
      allEndpoints = (apiV2 ++ admin).toList
    } yield SwaggerInterpreter(customiseDocsModel = replaceServerPort)
      .fromEndpoints[Task](allEndpoints, BuildInfo.name, BuildInfo.version)
}
