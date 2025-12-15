/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.management

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.*
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.core.domain.AppState
import org.knora.webapi.http.version.BuildInfo
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class VersionResponse(
  webapi: String,
  buildCommit: String,
  buildTime: String,
  fuseki: String,
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

  val getVersion = baseEndpoints.publicEndpoint.get
    .in("version")
    .out(jsonBody[VersionResponse].example(VersionResponse.current))
    .description("Get version information. Publicly accessible.")

  val getHealth = baseEndpoints.publicEndpoint.get
    .in("health")
    .out(jsonBody[HealthResponse])
    .out(statusCode)
    .description("Get health status. Publicly accessible.")

  val postStartCompaction = baseEndpoints.securedEndpoint.post
    .in("start-compaction")
    .out(jsonBody[String])
    .out(statusCode)
    .description("Start triplestore compaction. Requires SystemAdmin permissions.")
}

object ManagementEndpoints {
  private[management] val layer = ZLayer.derive[ManagementEndpoints]
}
