/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure
import sttp.apispec.openapi
import sttp.apispec.openapi.Contact
import sttp.apispec.openapi.Info
import sttp.apispec.openapi.OpenAPI
import sttp.tapir.server.ziohttp.ZioHttpInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import zio.*
import zio.http.*
import zio.metrics.connectors.MetricsConfig
import zio.metrics.connectors.prometheus
import zio.metrics.jvm.DefaultJvmMetrics

import org.knora.webapi.config.InstrumentationServerConfig
import org.knora.webapi.config.KnoraApi
import org.knora.webapi.core.State
import org.knora.webapi.http.version.BuildInfo
import org.knora.webapi.routing.Endpoints
import org.knora.webapi.slice.infrastructure.api.PrometheusRoutes

object MetricsServer {

  private val metricsServer: RIO[Endpoints & KnoraApi & PrometheusRoutes & Server, Unit] =
    for {
      docs       <- DocsServer.docsEndpoints.map(endpoints => ZioHttpInterpreter().toHttp(endpoints))
      prometheus <- ZIO.service[PrometheusRoutes]
      _          <- Server.install(prometheus.routes ++ docs)
      _          <- ZIO.never.unit
    } yield ()

  type MetricsServerEnv = Endpoints & InstrumentationServerConfig & KnoraApi & State

  val make: ZIO[MetricsServerEnv, Throwable, Unit] =
    for {
      _              <- ZIO.logInfo("Starting metrics and docs server...")
      knoraApiConfig <- ZIO.service[KnoraApi]
      endpoints      <- ZIO.service[Endpoints]
      config         <- ZIO.service[InstrumentationServerConfig]
      port            = config.port
      interval        = config.interval
      metricsConfig   = MetricsConfig(interval)
      _ <-
        ZIO.logInfo(
          s"Docs and metrics available at " +
            s"${knoraApiConfig.externalProtocol}://${knoraApiConfig.externalHost}:$port/docs & " +
            s"${knoraApiConfig.externalProtocol}://${knoraApiConfig.externalHost}:$port/metrics",
        )
      _ <- metricsServer.provide(
             ZLayer.succeed(knoraApiConfig),
             ZLayer.succeed(endpoints),
             Server.defaultWithPort(port),
             prometheus.publisherLayer,
             ZLayer.succeed(metricsConfig) >>> prometheus.prometheusLayer,
             Runtime.enableRuntimeMetrics,
             Runtime.enableFiberRoots,
             DefaultJvmMetrics.liveV2.unit,
             PrometheusRoutes.layer,
           )
    } yield ()
}

object DocsServer {

  val docsEndpoints =
    for {
      config       <- ZIO.service[KnoraApi]
      allEndpoints <- ZIO.serviceWith[Endpoints](_.serverEndpoints)
      info = Info(
               title = "DSP-API",
               version = BuildInfo.version,
               summary = Some(
                 "DSP-API is part of the the DaSCH Service Platform, a repository for the long-term preservation and reuse of data in the humanities.",
               ),
               contact = Some(Contact(name = Some("DaSCH"), url = Some("https://www.dasch.swiss/"))),
             )
    } yield SwaggerInterpreter(customiseDocsModel = addServer(config))
      .fromServerEndpoints[Task](allEndpoints, info)

  private def addServer(config: KnoraApi) = (openApi: OpenAPI) => {
    openApi.copy(servers =
      List(openapi.Server(url = config.externalKnoraApiBaseUrl, description = Some("The dsp-api server"))),
    )
  }
}
