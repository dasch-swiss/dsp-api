/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure.api

import sttp.model.StatusCode
import sttp.tapir.AnyEndpoint
import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.statusCode
import zio.UIO
import zio.ZIO
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.core.State
import org.knora.webapi.core.domain.AppState
import org.knora.webapi.http.version.BuildInfo
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.TapirToPekkoInterpreter

final case class VersionResponse private (
  webapi: String,
  buildCommit: String,
  buildTime: String,
  fuseki: String,
  pekkoHttp: String,
  scala: String,
  sipi: String,
  name: String = "version",
)

object VersionResponse {
  val current: VersionResponse = VersionResponse(
    BuildInfo.version,
    BuildInfo.buildCommit,
    BuildInfo.buildTime,
    BuildInfo.fuseki.split(":").last,
    BuildInfo.pekkoHttp.split(":").last,
    BuildInfo.scalaVersion.split(":").last,
    BuildInfo.sipi.split(":").last,
  )

  implicit val codec: JsonCodec[VersionResponse] = DeriveJsonCodec.gen[VersionResponse]
}

case class HealthResponse private (name: String, severity: String, status: Boolean, message: String)

object HealthResponse {

  private val healthy: HealthResponse =
    HealthResponse("AppState", "non fatal", true, "Application is healthy")

  private def unhealthy(message: String): HealthResponse =
    HealthResponse("AppState", "non fatal", false, message)

  def from(appState: AppState): HealthResponse = appState match {
    case AppState.Stopped               => unhealthy("Stopped. Please retry later.")
    case AppState.WaitingForTriplestore => unhealthy("Waiting for triplestore. Please retry later.")
    case AppState.TriplestoreReady      => unhealthy("Triplestore ready. Please retry later.")
    case AppState.UpdatingRepository    => unhealthy("Updating repository. Please retry later.")
    case AppState.RepositoryUpToDate    => unhealthy("Repository up to date. Please retry later.")
    case AppState.LoadingOntologies     => unhealthy("Loading ontologies. Please retry later.")
    case AppState.OntologiesReady       => unhealthy("Ontologies ready. Please retry later.")
    case AppState.MaintenanceMode       => unhealthy("Application is in maintenance mode. Please retry later.")
    case AppState.Running               => healthy
  }

  implicit val encoder: JsonCodec[HealthResponse] = DeriveJsonCodec.gen[HealthResponse]
}

final case class ManagementEndpoints(baseEndpoints: BaseEndpoints) {

  private[infrastructure] val getVersion = baseEndpoints.publicEndpoint.get
    .in("version")
    .out(jsonBody[VersionResponse].example(VersionResponse.current))

  private[infrastructure] val getHealth = baseEndpoints.publicEndpoint.get
    .in("health")
    .out(jsonBody[HealthResponse])
    .out(statusCode)

  val endpoints: Seq[AnyEndpoint] = List(getVersion, getHealth).map(_.tag("Management"))
}

object ManagementEndpoints {
  val layer = zio.ZLayer.derive[ManagementEndpoints]
}

final case class ManagementRoutes(
  endpoint: ManagementEndpoints,
  state: State,
  mapper: HandlerMapper,
  tapirToPekko: TapirToPekkoInterpreter,
) {

  private val versionEndpointHandler =
    PublicEndpointHandler[Unit, VersionResponse](endpoint.getVersion, _ => ZIO.succeed(VersionResponse.current))

  private val healthEndpointHandler =
    PublicEndpointHandler[Unit, (HealthResponse, StatusCode)](endpoint.getHealth, _ => createHealthResponse)

  private val createHealthResponse: UIO[(HealthResponse, StatusCode)] =
    state.getAppState.map { s =>
      val response = HealthResponse.from(s)
      (response, if (response.status) StatusCode.Ok else StatusCode.ServiceUnavailable)
    }

  val routes = List(versionEndpointHandler, healthEndpointHandler)
    .map(mapper.mapPublicEndpointHandler(_))
    .map(tapirToPekko.toRoute)
}

object ManagementRoutes {
  val layer = zio.ZLayer.derive[ManagementRoutes]
}
