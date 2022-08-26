/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.http.interop.ZIOSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Route
import spray.json.JsObject
import spray.json.JsString
import zio._

import org.knora.webapi.core.State
import org.knora.webapi.core.domain.AppState

/**
 * Provides health check logic
 */
trait HealthCheck {

  protected def healthCheck(state: State): ZIO[Any, Nothing, HttpResponse] =
    for {
      state    <- state.get
      result   <- createResult(state)
      response <- createResponse(result)
    } yield response

  private def createResult(state: AppState): UIO[HealthCheckResult] =
    ZIO
      .attempt(
        state match {
          case AppState.Stopped    => unhealthy("Stopped. Please retry later.")
          case AppState.StartingUp => unhealthy("Starting up. Please retry later.")
          case AppState.WaitingForTriplestore =>
            unhealthy("Waiting for triplestore. Please retry later.")
          case AppState.TriplestoreReady =>
            unhealthy("Triplestore ready. Please retry later.")
          case AppState.UpdatingRepository =>
            unhealthy("Updating repository. Please retry later.")
          case AppState.RepositoryUpToDate =>
            unhealthy("Repository up to date. Please retry later.")
          case AppState.CreatingCaches => unhealthy("Creating caches. Please retry later.")
          case AppState.CachesReady    => unhealthy("Caches ready. Please retry later.")
          case AppState.UpdatingSearchIndex =>
            unhealthy("Updating search index. Please retry later.")
          case AppState.SearchIndexReady =>
            unhealthy("Search index ready. Please retry later.")
          case AppState.LoadingOntologies =>
            unhealthy("Loading ontologies. Please retry later.")
          case AppState.OntologiesReady => unhealthy("Ontologies ready. Please retry later.")
          case AppState.WaitingForIIIFService =>
            unhealthy("Waiting for IIIF service. Please retry later.")
          case AppState.IIIFServiceReady =>
            unhealthy("IIIF service ready. Please retry later.")
          case AppState.WaitingForCacheService =>
            unhealthy("Waiting for cache service. Please retry later.")
          case AppState.CacheServiceReady =>
            unhealthy("Cache service ready. Please retry later.")
          case AppState.MaintenanceMode =>
            unhealthy("Application is in maintenance mode. Please retry later.")
          case AppState.Running => healthy
        }
      )
      .orDie

  private def createResponse(result: HealthCheckResult): UIO[HttpResponse] =
    ZIO
      .attempt(
        HttpResponse(
          status = statusCode(result.status),
          entity = HttpEntity(
            ContentTypes.`application/json`,
            JsObject(
              "name"     -> JsString("AppState"),
              "severity" -> JsString("non fatal"),
              "status"   -> JsString(status(result.status)),
              "message"  -> JsString(result.message)
            ).compactPrint
          )
        )
      )
      .orDie

  private def status(s: Boolean) = if (s) "healthy" else "unhealthy"

  private def statusCode(s: Boolean) = if (s) StatusCodes.OK else StatusCodes.ServiceUnavailable

  private case class HealthCheckResult(name: String, severity: String, status: Boolean, message: String)

  private def unhealthy(str: String) =
    HealthCheckResult(
      name = "AppState",
      severity = "non fatal",
      status = false,
      message = str
    )

  private val healthy =
    HealthCheckResult(
      name = "AppState",
      severity = "non fatal",
      status = true,
      message = "Application is healthy"
    )
}

/**
 * Provides the '/health' endpoint serving the health status.
 */
class HealthRoute(routeData: KnoraRouteData) extends KnoraRoute(routeData) with HealthCheck with ZIOSupport {

  /**
   * Returns the route.
   */
  override def makeRoute(): Route =
    path("health") {
      get { requestContext =>
        val res: ZIO[Any, Nothing, HttpResponse] = healthCheck(routeData.state)
        requestContext.complete(res)
      }
    }
}
