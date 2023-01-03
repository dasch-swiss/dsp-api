/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.get
import akka.http.scaladsl.server.Directives.path
import akka.http.scaladsl.server.Route
import zio._
import zio.json._

import org.knora.webapi.core.State
import org.knora.webapi.core.domain.AppState
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.util.LogAspect

/**
 * Provides health check logic
 */
trait HealthCheck {

  protected def healthCheck(state: State): UIO[HttpResponse] =
    for {
      _        <- ZIO.logDebug("get application state")
      state    <- state.getAppState
      result   <- setHealthState(state)
      _        <- ZIO.logDebug("set health state")
      response <- createResponse(result)
      _        <- ZIO.logDebug("getting application state done")
    } yield response

  private def setHealthState(state: AppState): UIO[HealthCheckResult] =
    ZIO.succeed(
      state match {
        case AppState.Stopped                     => unhealthy("Stopped. Please retry later.")
        case AppState.StartingUp                  => unhealthy("Starting up. Please retry later.")
        case AppState.WaitingForTriplestore       => unhealthy("Waiting for triplestore. Please retry later.")
        case AppState.TriplestoreReady            => unhealthy("Triplestore ready. Please retry later.")
        case AppState.UpdatingRepository          => unhealthy("Updating repository. Please retry later.")
        case AppState.RepositoryUpToDate          => unhealthy("Repository up to date. Please retry later.")
        case AppState.CreatingCaches              => unhealthy("Creating caches. Please retry later.")
        case AppState.CachesReady                 => unhealthy("Caches ready. Please retry later.")
        case AppState.UpdatingSearchIndex         => unhealthy("Updating search index. Please retry later.")
        case AppState.SearchIndexReady            => unhealthy("Search index ready. Please retry later.")
        case AppState.LoadingOntologies           => unhealthy("Loading ontologies. Please retry later.")
        case AppState.FailedToLoadOntologies(msg) => fatal(msg)
        case AppState.OntologiesReady             => unhealthy("Ontologies ready. Please retry later.")
        case AppState.WaitingForIIIFService       => unhealthy("Waiting for IIIF service. Please retry later.")
        case AppState.IIIFServiceReady            => unhealthy("IIIF service ready. Please retry later.")
        case AppState.WaitingForCacheService      => unhealthy("Waiting for cache service. Please retry later.")
        case AppState.CacheServiceReady           => unhealthy("Cache service ready. Please retry later.")
        case AppState.MaintenanceMode             => unhealthy("Application is in maintenance mode. Please retry later.")
        case AppState.Running                     => healthy
      }
    )

  private def createResponse(result: HealthCheckResult): UIO[HttpResponse] =
    ZIO
      .attempt(
        HttpResponse(
          status = statusCode(result.status),
          entity = HttpEntity(
            ContentTypes.`application/json`,
            result.toJson
          )
        )
      )
      .orDie

  private def status(s: Boolean) = if (s) "healthy" else "unhealthy"

  private def statusCode(s: Boolean) = if (s) StatusCodes.OK else StatusCodes.ServiceUnavailable

  private case class HealthCheckResult(name: String, severity: String, status: Boolean, message: String)

  private object HealthCheckResult {
    implicit val encoder: JsonEncoder[HealthCheckResult] = DeriveJsonEncoder.gen[HealthCheckResult]
  }

  private def fatal(message: String) =
    HealthCheckResult(
      name = "AppState",
      severity = "fatal",
      status = false,
      message = message
    )

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
 * Provides the '/health' endpoint serving the health status.
 */
final case class HealthRoute(routeData: KnoraRouteData, runtime: Runtime[State])
    extends HealthCheck
    with Authenticator {

  /**
   * Returns the route.
   */
  def makeRoute: Route =
    path("health") {
      get { requestContext =>
        val res: ZIO[State, Nothing, HttpResponse] = {
          for {
            _     <- ZIO.logDebug("health route start")
            ec    <- ZIO.executor.map(_.asExecutionContext)
            state <- ZIO.service[State]
            requestingUser <-
              ZIO
                .fromFuture(_ =>
                  getUserADM(requestContext, routeData.appConfig)(routeData.system, routeData.appActor, ec)
                )
                .orElse(ZIO.succeed(KnoraSystemInstances.Users.AnonymousUser))
            result <- healthCheck(state)
            _      <- ZIO.logDebug("health route finished") @@ ZIOAspect.annotated("user-id", requestingUser.id.toString())
          } yield result
        } @@ LogAspect.logSpan("health-request") @@ LogAspect.logAnnotateCorrelationId(requestContext.request)

        // executing our effect and returning a future to Akka Http
        Unsafe.unsafe { implicit u =>
          val resF = runtime.unsafe.runToFuture(res)
          requestContext.complete(resF)
        }
      }
    }
}
