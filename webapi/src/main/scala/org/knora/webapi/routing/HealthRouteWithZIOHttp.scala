/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import spray.json.JsObject
import spray.json.JsString
import zhttp.http._
import zio._

import org.knora.webapi.core.State
import org.knora.webapi.core.domain.AppState

/**
 * Provides health check logic
 */
trait HealthCheckWithZIOHttp {

  protected def healthCheck(state: State): UIO[Response] =
    for {
      _        <- ZIO.logDebug("get application state")
      state    <- state.get
      result   <- setHealthState(state)
      _        <- ZIO.logDebug("set health state")
      response <- createResponse(result)
      _        <- ZIO.logDebug("getting application state done")
    } yield response

  private def setHealthState(state: AppState): UIO[HealthCheckResult] =
    ZIO.succeed(
      state match {
        case AppState.Stopped                => unhealthy("Stopped. Please retry later.")
        case AppState.StartingUp             => unhealthy("Starting up. Please retry later.")
        case AppState.WaitingForTriplestore  => unhealthy("Waiting for triplestore. Please retry later.")
        case AppState.TriplestoreReady       => unhealthy("Triplestore ready. Please retry later.")
        case AppState.UpdatingRepository     => unhealthy("Updating repository. Please retry later.")
        case AppState.RepositoryUpToDate     => unhealthy("Repository up to date. Please retry later.")
        case AppState.CreatingCaches         => unhealthy("Creating caches. Please retry later.")
        case AppState.CachesReady            => unhealthy("Caches ready. Please retry later.")
        case AppState.UpdatingSearchIndex    => unhealthy("Updating search index. Please retry later.")
        case AppState.SearchIndexReady       => unhealthy("Search index ready. Please retry later.")
        case AppState.LoadingOntologies      => unhealthy("Loading ontologies. Please retry later.")
        case AppState.OntologiesReady        => unhealthy("Ontologies ready. Please retry later.")
        case AppState.WaitingForIIIFService  => unhealthy("Waiting for IIIF service. Please retry later.")
        case AppState.IIIFServiceReady       => unhealthy("IIIF service ready. Please retry later.")
        case AppState.WaitingForCacheService => unhealthy("Waiting for cache service. Please retry later.")
        case AppState.CacheServiceReady      => unhealthy("Cache service ready. Please retry later.")
        case AppState.MaintenanceMode        => unhealthy("Application is in maintenance mode. Please retry later.")
        case AppState.Running                => healthy
      }
    )

  private def createResponse(result: HealthCheckResult): UIO[Response] =
    ZIO.succeed(
      Response
        .json(
          JsObject(
            "name"     -> JsString("AppState"),
            "severity" -> JsString("non fatal"),
            "status"   -> JsString(status(result.status)),
            "message"  -> JsString(result.message)
          ).toString()
        )
        .setStatus(statusCode(result.status))
    )

  private def status(s: Boolean): String = if (s) "healthy" else "unhealthy"

  private def statusCode(s: Boolean): Status = if (s) Status.Ok else Status.ServiceUnavailable

  private case class HealthCheckResult(name: String, severity: String, status: Boolean, message: String)

  private def unhealthy(message: String) =
    HealthCheckResult(
      name = "AppState",
      severity = "non fatal",
      status = false,
      message = message
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
 * Provides the '/healthZ' endpoint serving the health status.
 */
final case class HealthRouteWithZIOHttp(state: State) extends HealthCheckWithZIOHttp {

  /**
   * Returns the route.
   */
  val route: Http[State, Nothing, Request, Response] =
    Http.collectZIO[Request] { case Method.GET -> !! / "healthZ" =>
      for {
        //  ec    <- ZIO.executor.map(_.asExecutionContext) // TODO leave this for reference about how to get the execution context
        state    <- ZIO.service[State]
        response <- healthCheck(state)
      } yield response

    }
}

/**
 * Companion object providing the layer
 */
object HealthRouteWithZIOHttp {
  val layer: ZLayer[State, Nothing, HealthRouteWithZIOHttp] =
    ZLayer.fromFunction(HealthRouteWithZIOHttp.apply _)
}
