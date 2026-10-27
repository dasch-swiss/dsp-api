/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.ztapir.*
import zio.*
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.http.version.BuildInfo
import org.knora.webapi.slice.ingest.infrastructure.AggregatedHealth
import org.knora.webapi.slice.ingest.infrastructure.Health

/** The standalone service's `/info` body, now describing the dsp-api build that serves ingest. */
final case class InfoEndpointResponse(
  name: String = "dsp-ingest",
  version: String = BuildInfo.version,
  scalaVersion: String = BuildInfo.scalaVersion,
  sbtVersion: String = "bazel",
  buildTime: String = BuildInfo.buildTime,
  gitCommit: String = BuildInfo.gitCommit,
)

object InfoEndpointResponse {
  val instance: InfoEndpointResponse           = InfoEndpointResponse()
  given codec: JsonCodec[InfoEndpointResponse] = DeriveJsonCodec.gen[InfoEndpointResponse]
}

final class IngestMonitoringEndpoints(base: IngestBaseEndpoints) {

  private val monitoring = "monitoring"

  val getInfo = base.publicEndpoint.get
    .in("info")
    .out(jsonBody[InfoEndpointResponse].example(InfoEndpointResponse.instance))
    .tag(monitoring)
    .description("Get build and version information. Publicly accessible.")

  val getHealth = base.publicEndpoint.get
    .in("health")
    .out(jsonBody[AggregatedHealth].example(AggregatedHealth(Health.Status.UP, None)))
    .tag(monitoring)
    .description("Get health status. Publicly accessible.")

  val getMetrics = base.publicEndpoint.get
    .in("metrics")
    .out(stringBody)
    .tag(monitoring)
    .description("Get Prometheus metrics. Publicly accessible.")
}

object IngestMonitoringEndpoints {
  val layer = ZLayer.derive[IngestMonitoringEndpoints]
}
