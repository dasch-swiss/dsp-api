/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio._
import zio.http._
import zio.http.middleware.Cors
import zio.http.model.Method
import zio.logging.LogAnnotation.TraceId

import java.util.UUID
import scala.annotation.tailrec

import org.knora.webapi.config.AppConfig
import org.knora.webapi.routing.admin.ProjectsRouteZ
import org.knora.webapi.slice.resourceinfo.api.ResourceInfoRoute

object HttpServerZ {

  private val corsMiddleware =
    Middleware.cors(
      Cors.CorsConfig(
        anyOrigin = true,
        anyMethod = false,
        allowedMethods = Some(Set(Method.GET, Method.PUT, Method.DELETE, Method.POST))
      )
    )

  private val loggingMiddleware = Middleware.requestLogging()

  private val tracingMiddleware = new Middleware[Any, Nothing, Request, Response, Request, Response] {
    override def apply[R1, E1](http: Http[R1, E1, Request, Response])(implicit
      trace: Trace
    ): Http[R1, E1, Request, Response] =
      Http.collectZIO[Request] { case request =>
        http(request).mapError(_.get) @@ TraceId(UUID.randomUUID())
      }
  }

  private def metricsMiddleware() = {
    // in order to avoid extensive amounts of labels, we should replace path segment slugs
    // see docs/03-endpoints/instrumentation/metrics.md
    val slugs = Set(
      "iri",
      "shortcode",
      "shortname"
    )
    @tailrec
    def replaceSlugs(seg: List[Path.Segment], acc: Path): Path =
      seg match {
        case head :: slug :: next if slugs.contains(head.text) => replaceSlugs(next, acc / head.text / s":${head.text}")
        case head :: next                                      => replaceSlugs(next, acc / head.text)
        case Nil                                               => acc
      }
    Middleware.metrics(
      pathLabelMapper = { case request: Request => replaceSlugs(request.path.segments.toList, Path.empty).toString },
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
      metrics              = metricsMiddleware()
      routesWithMiddleware = routes @@ corsMiddleware @@ metrics @@ loggingMiddleware @@ tracingMiddleware
      serverConfig         = ZLayer.succeed(ServerConfig.default.port(port))
      _                   <- Server.serve(routesWithMiddleware).provide(Server.live, serverConfig).forkDaemon
      _                   <- ZIO.logInfo(">>> Acquire ZIO HTTP Server <<<")
    } yield ()
  }
}
