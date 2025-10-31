/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.export_.api

import zio.*
import zio.ZLayer

import org.knora.webapi.slice.api.v3.V3BaseEndpoint.EndpointT

final case class ExportServerEndpoints(
  exportEndpoints: ExportEndpoints,
  exportRestService: ExportRestService,
) {
  val endpoints: List[EndpointT] =
    List(
      exportEndpoints.postExportResources.serverLogic(exportRestService.exportResources),
    )
}

object ExportServerEndpoints {
  val layer = ZLayer.derive[ExportServerEndpoints]
}
