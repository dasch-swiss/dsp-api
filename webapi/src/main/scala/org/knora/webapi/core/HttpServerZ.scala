/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio._
import zio.http._
import zio.http.middleware.Cors
import zio.http.model.Method

import scala.annotation.tailrec

import org.knora.webapi.config.AppConfig
import org.knora.webapi.routing.admin.ProjectsRouteZ
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoRoute

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

  private def metricsMiddleware() = {
    // in order to avoid extensive amounts of labels, we should use the `pathLabelMapper`
    // which maps paths containing slug values (`/project/iri/SOME-IRI`)
    // to paths with a slug inserted instead (`/projects/iri/:iri`).
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
    Middleware.metrics(
      pathLabelMapper = { case request: Request => step(request.path.segments.toList, Path.empty).toString },
      concurrentRequestsName = "zio_http_concurrent_requests_total",
      totalRequestsName = "zio_http_requests_total",
      requestDurationName = "zio_http_request_duration_seconds"
    )
  }

  private val apiRoutes: URIO[ProjectsRouteZ with ResourceInfoRoute, HttpApp[Any, Nothing]] = for {
    projectsRoute <- ZIO.service[ProjectsRouteZ].map(_.route)
    riRoute       <- ZIO.service[ResourceInfoRoute].map(_.route)
  } yield projectsRoute ++ riRoute

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
