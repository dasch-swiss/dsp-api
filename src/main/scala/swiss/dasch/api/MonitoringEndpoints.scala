/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.tapir.PublicEndpoint
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.ztapir.*
import swiss.dasch.infrastructure.Health
import swiss.dasch.version.BuildInfo
import zio.*
import zio.json.{DeriveJsonCodec, JsonCodec}

case class InfoEndpointResponse(
  name: String = BuildInfo.name,
  version: String = BuildInfo.version,
  scalaVersion: String = BuildInfo.scalaVersion,
  sbtVersion: String = BuildInfo.sbtVersion,
  buildTime: String = BuildInfo.builtAtString,
  gitCommit: String = BuildInfo.gitCommit
)

object InfoEndpointResponse {

  val instance: InfoEndpointResponse          = InfoEndpointResponse()
  given code: JsonCodec[InfoEndpointResponse] = DeriveJsonCodec.gen[InfoEndpointResponse]
}

final case class MonitoringEndpoints(base: BaseEndpoints) {

  private val monitoring = "monitoring"

  val infoEndpoint: PublicEndpoint[Unit, ApiProblem, InfoEndpointResponse, Any] =
    base.publicEndpoint.get
      .in("info")
      .out(jsonBody[InfoEndpointResponse].example(InfoEndpointResponse.instance))
      .tag(monitoring)

  val healthEndpoint: PublicEndpoint[Unit, ApiProblem, Health, Any] =
    base.publicEndpoint.get
      .in("health")
      .out(jsonBody[Health].example(Health.up()))
      .tag(monitoring)

  val metricsEndpoint: PublicEndpoint[Unit, ApiProblem, String, Any] =
    base.publicEndpoint.get
      .in("metrics")
      .out(stringBody)
      .tag(monitoring)

  val endpoints = List(infoEndpoint, healthEndpoint, metricsEndpoint)
}
object MonitoringEndpoints {
  val layer = ZLayer.derive[MonitoringEndpoints]
}
