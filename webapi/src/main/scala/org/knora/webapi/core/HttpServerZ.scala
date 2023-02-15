/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio._
import zio.http._
import zio.http.middleware.Cors
import zio.http.model.Method

import org.knora.webapi.config.AppConfig
import org.knora.webapi.routing.admin.ProjectsRouteZ
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoRoute
import scala.annotation.tailrec

object HttpServerZ {

  private val corsMiddleware =
    for {
      config <- ZIO.service[AppConfig]
      corsConfig =
        Cors.CorsConfig(
          anyOrigin = false,
          anyMethod = false,
          allowedMethods = Some(Set(Method.GET, Method.PUT, Method.DELETE, Method.POST)),
          allowedOrigins = { origin =>
            config.httpServer.corsAllowedOrigins.contains(origin)
          }
        )
    } yield Middleware.cors(corsConfig)

  private val apiRoutes: URIO[ProjectsRouteZ with ResourceInfoRoute, HttpApp[Any, Nothing]] = for {
    projectsRoute <- ZIO.service[ProjectsRouteZ].map(_.route)
    riRoute       <- ZIO.service[ResourceInfoRoute].map(_.route)
  } yield projectsRoute ++ riRoute

  private def pathLabelMapper(): PartialFunction[Request, String] = { case r: Request =>
    val slugs = Set(
      "iri",
      "shortcode",
      "shortname"
    )
    @tailrec
    def step(seg: List[Path.Segment], acc: Path): Path =
      seg match {
        case head :: slug :: next if slugs.contains(head.text) => step(next, acc / head.text / s":${head.text}")
        case head :: next                                      => step(next, acc / head.text)
        case Nil                                               => acc
      }
    // TODO: have to check if the assumption holds that a path with max. 4 segments doesn't contain any slugs
    if (r.path.segments.size <= 4) r.path.toString()
    else step(r.path.segments.toList, Path.empty).toString()
  }

  private def metricsMiddleware() =
    Middleware.metrics(
      pathLabelMapper = pathLabelMapper(),
      concurrentRequestsName = "zio_http_concurrent_requests_total",
      totalRequestsName = "zio_http_requests_total",
      requestDurationName = "zio_http_request_duration_seconds"
    )

  val layer: ZLayer[ResourceInfoRoute with ProjectsRouteZ with AppConfig, Nothing, Unit] = ZLayer {
    for {
      port                <- ZIO.service[AppConfig].map(_.knoraApi.externalZioPort)
      routes              <- apiRoutes
      cors                <- corsMiddleware
      metrics              = metricsMiddleware()
      routesWithMiddleware = routes @@ cors @@ metrics
      serverConfig         = ZLayer.succeed(ServerConfig.default.port(port))
      _                   <- Server.serve(routesWithMiddleware).provide(Server.live, serverConfig).forkDaemon
      _                   <- ZIO.logInfo(">>> Acquire ZIO HTTP Server <<<")
    } yield ()
  }
}
