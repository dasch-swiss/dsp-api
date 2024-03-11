/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.infrastructure.api

import sttp.tapir.AnyEndpoint
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.ZIO
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

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
  val current = VersionResponse(
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

final case class VersionEndpoint(baseEndpoints: BaseEndpoints) {

  private[infrastructure] val getVersion = baseEndpoints.publicEndpoint.get
    .in("version")
    .out(jsonBody[VersionResponse].example(VersionResponse.current))

  val endpoints: Seq[AnyEndpoint] = List(getVersion).map(_.tag("Version"))
}

object VersionEndpoint {
  val layer = zio.ZLayer.derive[VersionEndpoint]
}

final case class ManagementRoutes(
  endpoint: VersionEndpoint,
  mapper: HandlerMapper,
  tapirToPekko: TapirToPekkoInterpreter,
) {

  private val versionEndpointHandler =
    PublicEndpointHandler[Unit, VersionResponse](endpoint.getVersion, _ => ZIO.succeed(VersionResponse.current))

  val routes = List(versionEndpointHandler)
    .map(mapper.mapPublicEndpointHandler)
    .map(tapirToPekko.toRoute)
}

object ManagementRoutes {
  val layer = zio.ZLayer.derive[ManagementRoutes]
}
