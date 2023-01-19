/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.instrumentation.health

import zhttp.http._
import zio._
import zio.json._

import org.knora.webapi.core.State
import org.knora.webapi.core.domain.AppState

/**
 * Provides the '/health' endpoint serving the health status.
 */
final case class HealthRouteZ() {

  val route: HttpApp[State, Nothing] =
    Http.collectZIO[Request] { case Method.GET -> !! / "health" =>
      State.getAppState.map(toHealthCheckResult).flatMap(createResponse)
    }

  /**
   * Transforms the [[AppState]] into a [[HealthCheckResult]]
   *
   * @param state the application's state
   * @return the result which is either unhealthy or healthy, containing a human readable explanation in case of unhealthy
   */
  private def toHealthCheckResult(state: AppState): HealthCheckResult =
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

  /**
   * Creates the HTTP response from the health check result (healthy/unhealthy).
   *
   * @param result the result of the health check
   * @return an HTTP response
   */
  private def createResponse(result: HealthCheckResult): UIO[Response] =
    ZIO.succeed(
      Response
        .json(result.toJson)
        .setStatus(statusCode(result.status))
    )

  /**
   * Returns a string representation "healthy" or "unhealthy" from a boolean.
   *
   * @param s a boolean from which to derive the state
   * @return either "healthy" or "unhealthy"
   */
  private def status(s: Boolean): String = if (s) "healthy" else "unhealthy"

  /**
   * Returns the HTTP status according to the input boolean.
   *
   * @param s a boolean from which to derive the HTTP status
   * @return the HTTP status (OK or ServiceUnavailable)
   */
  private def statusCode(s: Boolean): Status = if (s) Status.Ok else Status.ServiceUnavailable

  /**
   * The result of a health check which is either unhealthy or healthy.
   *
   * @param name     always "AppState"
   * @param severity severity of the health status. Always "non fatal", otherwise the application would not respond
   * @param status   the status (either false = unhealthy or true = healthy)
   * @param message  the message
   */
  private case class HealthCheckResult(name: String, severity: String, status: Boolean, message: String)

  private object HealthCheckResult {
    implicit val encoder: JsonEncoder[HealthCheckResult] = DeriveJsonEncoder.gen[HealthCheckResult]
  }

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

object HealthRouteZ {
  val layer = ZLayer.succeed(HealthRouteZ())
}
