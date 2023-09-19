/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.ApiPathCodecSegments.shortcodePathVar
import swiss.dasch.api.ApiProblem.{ BadRequest, InternalServerError, NotFound }
import zio.*
import zio.http.*
import zio.http.codec.*
import zio.http.codec.HttpCodec.*
import zio.http.endpoint.*
import zio.json.{ DeriveJsonCodec, JsonCodec }
import zio.schema.{ DeriveSchema, Schema }

object MaintenanceEndpoint {

  final case class MappingEntry(internalFilename: String, originalFilename: String)

  object MappingEntry {
    given codec: JsonCodec[MappingEntry] = DeriveJsonCodec.gen[MappingEntry]
    given schema: Schema[MappingEntry]   = DeriveSchema.gen[MappingEntry]
  }

  private val maintenance = "maintenance"

  val applyTopLeftCorrectionEndpoint = Endpoint
    .post(maintenance / "apply-top-left-correction" / shortcodePathVar)
    .out[String](Status.Accepted)
    .outErrors(
      HttpCodec.error[NotFound](Status.NotFound),
      HttpCodec.error[BadRequest](Status.BadRequest),
      HttpCodec.error[InternalServerError](Status.InternalServerError),
    )

  val needsTopLeftCorrectionEndpoint = Endpoint
    .get(maintenance / "needs-top-left-correction")
    .out[String](Status.Accepted)
    .outErrors(
      HttpCodec.error[NotFound](Status.NotFound),
      HttpCodec.error[BadRequest](Status.BadRequest),
      HttpCodec.error[InternalServerError](Status.InternalServerError),
    )

  val createOriginalsEndpoint = Endpoint
    .post(maintenance / "create-originals" / shortcodePathVar)
    .inCodec(ContentCodec.content[Chunk[MappingEntry]](MediaType.application.json))
    .out[String](Status.Accepted)
    .outErrors(
      HttpCodec.error[NotFound](Status.NotFound),
      HttpCodec.error[BadRequest](Status.BadRequest),
      HttpCodec.error[InternalServerError](Status.InternalServerError),
    )

  val needsOriginalsEndpoint = Endpoint
    .get(maintenance / "needs-originals")
    .query(queryBool("imagesOnly").optional)
    .out[String](Status.Accepted)
    .outErrors(
      HttpCodec.error[NotFound](Status.NotFound),
      HttpCodec.error[BadRequest](Status.BadRequest),
      HttpCodec.error[InternalServerError](Status.InternalServerError),
    )
}
