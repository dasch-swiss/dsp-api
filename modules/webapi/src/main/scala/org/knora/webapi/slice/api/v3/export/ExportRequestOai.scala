/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.`export`

import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

final case class ExportRequestOai(
  shortcode: Shortcode,
)

object ExportRequestOai {
  import org.knora.webapi.slice.api.admin.Codecs.ZioJsonCodec.shortcode
  implicit val codec: JsonCodec[ExportRequestOai] = DeriveJsonCodec.gen[ExportRequestOai]
}
