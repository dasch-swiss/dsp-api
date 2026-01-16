/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.`export`

import zio.*

import org.knora.webapi.slice.api.v3.V3BaseEndpoint.EndpointT

final class ExportServerEndpoints(endpoints: ExportEndpoints, restService: ExportRestService) {

  val serverEndpoints: List[EndpointT] =
    List(endpoints.postExportResources.serverLogic(restService.exportResources))
}

object ExportServerEndpoints {
  val layer = ExportRestService.layer >+>
    ExportEndpoints.layer >>>
    ZLayer.derive[ExportServerEndpoints]
}
