/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import org.knora.webapi.config.Features
import zio.*
import org.knora.webapi.slice.api.v3.V3BaseEndpoint.EndpointT

final class V3ProjectsServerEndpoints(
  endpoints: V3ProjectsEndpoints,
  restService: V3ProjectsRestService,
  features: Features,
) {

  val serverEndpoints: List[EndpointT] = List(
    endpoints.postProjectIriExports.serverLogic(restService.triggerProjectExportCreate),
    endpoints.getProjectIriExportsExportId.serverLogic(restService.getProjectExportStatus),
    endpoints.deleteProjectIriExportsExportId.serverLogic(restService.deleteProjectExport),
    endpoints.getProjectIriExportsExportIdDownload.serverLogic(restService.downloadProjectExport),
  ) ++ (
    if features.allowImportMigrationBagit then
      List(
        endpoints.postProjectIriImports.serverLogic(restService.triggerProjectImportCreate),
        endpoints.getProjectIriImportsImportId.serverLogic(restService.getProjectImportStatus),
        endpoints.deleteProjectIriImportsImportId.serverLogic(restService.deleteProjectImport),
      )
    else Nil
  )
}

object V3ProjectsServerEndpoints {
  val layer: URLayer[V3ProjectsEndpoints & V3ProjectsRestService & Features, V3ProjectsServerEndpoints] =
    ZLayer.derive[V3ProjectsServerEndpoints]
}
