/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.model.StatusCode
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import sttp.tapir.ztapir.*
import swiss.dasch.api.ProjectsEndpoints.shortcodePathVar
import zio.json.{DeriveJsonCodec, JsonCodec}
import zio.schema.{DeriveSchema, Schema}
import zio.{Chunk, ZLayer}

final case class MappingEntry(internalFilename: String, originalFilename: String)

object MappingEntry {
  given codec: JsonCodec[MappingEntry] = DeriveJsonCodec.gen[MappingEntry]
  given schema: Schema[MappingEntry]   = DeriveSchema.gen[MappingEntry]
}

final case class MaintenanceEndpoints(base: BaseEndpoints) {

  private val maintenance = "maintenance"

  val applyTopLeftCorrectionEndpoint = base.secureEndpoint.post
    .in(maintenance / "apply-top-left-correction" / shortcodePathVar)
    .out(statusCode(StatusCode.Accepted))
    .tag(maintenance)

  val needsTopLeftCorrectionEndpoint = base.secureEndpoint.get
    .in(maintenance / "needs-top-left-correction")
    .out(stringBody)
    .out(statusCode(StatusCode.Accepted))
    .tag(maintenance)

  val wasTopLeftCorrectionAppliedEndpoint = base.secureEndpoint.get
    .in(maintenance / "was-top-left-correction-applied")
    .out(stringBody)
    .out(statusCode(StatusCode.Accepted))
    .tag(maintenance)

  val createOriginalsEndpoint = base.secureEndpoint.post
    .in(maintenance / "create-originals" / shortcodePathVar)
    .in(jsonBody[Chunk[MappingEntry]])
    .out(statusCode(StatusCode.Accepted))
    .tag(maintenance)

  val needsOriginalsEndpoint = base.secureEndpoint.get
    .in(maintenance / "needs-originals")
    .in(query[Option[Boolean]]("imagesOnly"))
    .out(stringBody)
    .out(statusCode(StatusCode.Accepted))
    .tag(maintenance)

  val extractImageMetadataAndAddToInfoFileEndpoint = base.secureEndpoint.post
    .in(maintenance / "extract-image-metadata-and-add-to-info-file")
    .out(stringBody)
    .out(statusCode(StatusCode.Accepted))
    .tag(maintenance)

  val endpoints = List(
    applyTopLeftCorrectionEndpoint,
    createOriginalsEndpoint,
    needsTopLeftCorrectionEndpoint,
    needsOriginalsEndpoint,
    extractImageMetadataAndAddToInfoFileEndpoint
  )
}

object MaintenanceEndpoints {
  val layer = ZLayer.derive[MaintenanceEndpoints]
}
