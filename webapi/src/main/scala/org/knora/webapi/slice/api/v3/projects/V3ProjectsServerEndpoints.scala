/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects;

import zio.*

import org.knora.webapi.slice.api.v3.V3BaseEndpoint.EndpointT

final class V3ProjectsServerEndpoints(endpoints: V3ProjectsEndpoints, restService: V3ProjectsRestService) {

  val serverEndpoints: List[EndpointT] = List(
    endpoints.postProjectIriExports.serverLogic(restService.triggerProjectExportCreate),
    endpoints.getProjectIriExportsExportId.serverLogic(restService.getProjectExportStatus),
    endpoints.deleteProjectIriExportsExportId.serverLogic(restService.deleteProjectExport),
    endpoints.getProjectIriExportsExportIdDownload.serverLogic(restService.downloadProjectExport),
  )
}

object V3ProjectsServerEndpoints {
  val layer = ZLayer.derive[V3ProjectsServerEndpoints]
}
