/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
import org.knora.webapi.slice.admin.api.AdminApiEndpoints
import org.knora.webapi.slice.common.api.ApiV2Endpoints
import org.knora.webapi.slice.infrastructure.api.PrometheusApp

object MetricsServer {

  private val metricsServer
    : ZIO[AdminApiEndpoints & ApiV2Endpoints & KnoraApi & PrometheusApp & Server, Nothing, Unit] = for {
    docs       <- DocsServer.docsEndpoints.map(endpoints => ZioHttpInterpreter().toHttp(endpoints))
    prometheus <- ZIO.service[PrometheusApp]
    _          <- Server.install(prometheus.route ++ docs)
    _          <- ZIO.never.as(())
  } yield ()

  val make: ZIO[KnoraApi & State & InstrumentationServerConfig & ApiV2Endpoints & AdminApiEndpoints, Throwable, Unit] =
    for {
      knoraApiConfig    <- ZIO.service[KnoraApi]
      apiV2Endpoints    <- ZIO.service[ApiV2Endpoints]
      adminApiEndpoints <- ZIO.service[AdminApiEndpoints]
      config            <- ZIO.service[InstrumentationServerConfig]
      port               = config.port
      interval           = config.interval
      metricsConfig      = MetricsConfig(interval)
      _ <- ZIO.logInfo(
             s"Starting api on ${knoraApiConfig.externalKnoraApiBaseUrl}, " +
               s"find docs on ${knoraApiConfig.externalProtocol}://${knoraApiConfig.externalHost}:$port/docs"
           )
      _ <- metricsServer
             .provideSome(
               ZLayer.succeed(knoraApiConfig),
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
      config      <- ZIO.service[KnoraApi]
      apiV2       <- ZIO.serviceWith[ApiV2Endpoints](_.endpoints)
      admin       <- ZIO.serviceWith[AdminApiEndpoints](_.endpoints)
      allEndpoints = (apiV2 ++ admin).toList
      info = Info(
               title = "DSP-API",
               version = BuildInfo.version,
               summary = Some(
                 "DSP-API is part of the the DaSCH Service Platform, a repository for the long-term preservation and reuse of data in the humanities."
               ),
               contact = Some(Contact(name = Some("DaSCH"), url = Some("https://www.dasch.swiss/")))
             )
    } yield SwaggerInterpreter(customiseDocsModel = addServer(config))
      .fromEndpoints[Task](allEndpoints, info)

  private def addServer(config: KnoraApi) = (openApi: OpenAPI) => {
    openApi.copy(servers =
      List(openapi.Server(url = config.externalKnoraApiBaseUrl, description = Some("The dsp-api server")))
    )
  }
}
