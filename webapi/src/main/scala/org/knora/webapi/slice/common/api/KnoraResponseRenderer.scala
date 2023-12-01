/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common.api

import sttp.model.MediaType
import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.ApiV2Schema
import org.knora.webapi.Rendering
import org.knora.webapi.SchemaRendering
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.util.rdf.JsonLD
import org.knora.webapi.messages.util.rdf.RdfFormat
import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse

final class KnoraResponseRenderer(config: AppConfig) {
  def renderAsJsonLd(response: KnoraResponseV2, schemaRendering: SchemaRendering): Task[(RenderedResponse, MediaType)] =
    render(response, FormatOptions.from(JsonLD, schemaRendering))

  def render(response: KnoraResponseV2, opts: FormatOptions): Task[(RenderedResponse, MediaType)] =
    ZIO.attempt(response.format(opts, config)).map((_, opts.format.mediaType))
}

object KnoraResponseRenderer {
  type RenderedResponse = String
  final case class FormatOptions(format: RdfFormat, schema: ApiV2Schema, rendering: Set[Rendering])
  object FormatOptions {
    def from(f: RdfFormat, s: SchemaRendering): FormatOptions = FormatOptions(f, s.schema, s.rendering)
  }

  val layer = ZLayer.derive[KnoraResponseRenderer]
}
