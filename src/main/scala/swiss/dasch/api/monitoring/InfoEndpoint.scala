/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.monitoring

import swiss.dasch.version.BuildInfo
import zio.*
import zio.http.*
import zio.http.endpoint.*
import zio.json.{ DeriveJsonEncoder, JsonEncoder }
import zio.schema.{ DeriveSchema, Schema }

object InfoEndpoint {
  case class InfoEndpointResponse(
      name: String,
      version: String,
      scalaVersion: String,
      sbtVersion: String,
      buildTime: String,
      gitCommit: String,
    )
  object InfoEndpointResponse {
    implicit val schema: Schema[InfoEndpointResponse]       = DeriveSchema.gen[InfoEndpointResponse]
    implicit val encoder: JsonEncoder[InfoEndpointResponse] = DeriveJsonEncoder.gen[InfoEndpointResponse]

    def apply(): InfoEndpointResponse = InfoEndpointResponse(
      name = BuildInfo.name,
      version = BuildInfo.version,
      scalaVersion = BuildInfo.scalaVersion,
      sbtVersion = BuildInfo.sbtVersion,
      buildTime = BuildInfo.builtAtString,
      gitCommit = BuildInfo.gitCommit,
    )
  }

  private val infoEndpoint = Endpoint
    .get("info")
    .out[InfoEndpointResponse]

  val app = infoEndpoint.implement(_ => ZIO.succeed(InfoEndpointResponse())).toApp
}
