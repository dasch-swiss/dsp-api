/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.ApiV2Complex
import org.knora.webapi.ServeStoredXml
import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.common.api.KnoraResponseRenderer.FormatOptions

/**
 * The only producer of the [[ServeStoredXml]] rendering option. The client REST read services apply it to the options
 * they pass to a read; internal readers must never receive the option.
 *
 * The option is added for the complex schema only: reads that fix the complex schema internally, such as search by
 * class, would otherwise serve a request made in the simple schema from the store.
 *
 * @param enabled the value of `Features.serveStoredXml`, read once when the layer is built.
 */
final class StoredXmlServing private (enabled: Boolean) {
  def apply(options: FormatOptions): FormatOptions =
    if (enabled && options.schema == ApiV2Complex) options.copy(rendering = options.rendering + ServeStoredXml)
    else options
}

object StoredXmlServing {
  val layer: URLayer[AppConfig, StoredXmlServing] =
    ZLayer.fromFunction((config: AppConfig) => StoredXmlServing(config.features.serveStoredXml))

  private[v2] def forTest(enabled: Boolean): StoredXmlServing = StoredXmlServing(enabled)
}
