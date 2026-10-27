/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.schema.DeriveSchema
import zio.schema.Schema

case class UploadResponse(status: String = "ok")

object UploadResponse {
  given schema: Schema[UploadResponse] = DeriveSchema.gen[UploadResponse]

  given codec: JsonCodec[UploadResponse] = DeriveJsonCodec.gen[UploadResponse]
}
