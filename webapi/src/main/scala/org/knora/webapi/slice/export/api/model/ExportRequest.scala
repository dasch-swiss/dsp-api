/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.export_.api

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

final case class ExportRequest(
  resourceClass: String,
  selectedProperties: List[String],
)

object ExportRequest {
  implicit val codec: JsonCodec[ExportRequest] = DeriveJsonCodec.gen[ExportRequest]
}
