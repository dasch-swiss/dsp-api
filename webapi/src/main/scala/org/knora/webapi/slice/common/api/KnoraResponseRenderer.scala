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
import org.knora.webapi.messages.util.rdf.RdfFormat
import org.knora.webapi.messages.v2.responder.KnoraResponseV2
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.RenderedResponse

/**
 * Renders a [[KnoraResponseV2]] as a [[RenderedResponse]] (type alias for a [[String]]) ready to be returned to the client.
 */
final class KnoraResponseRenderer(config: AppConfig) {
  def render(response: KnoraResponseV2, opts: FormatOptions): Task[(RenderedResponse, MediaType)] =
    ZIO.attempt(response.format(opts, config)).map((_, opts.rdfFormat.mediaType))
}

object KnoraResponseRenderer {

  type RenderedResponse = String

  final case class FormatOptions(rdfFormat: RdfFormat, schema: ApiV2Schema, rendering: Set[Rendering]) {
    lazy val schemaRendering: SchemaRendering = SchemaRendering(schema, rendering)
  }

  val layer = ZLayer.derive[KnoraResponseRenderer]
}
