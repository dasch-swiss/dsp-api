package org.knora.webapi.slice.api.v3.projects;

import zio.*
import org.knora.webapi.slice.api.v3.V3BaseEndpoint.EndpointT

final class V3ProjectsServerEndpoints(endpoints: V3ProjectsEndpoints, restService: V3ProjectsRestService) {

  val serverEndpoints: List[EndpointT] = List(
    endpoints.postProjectIriExports.serverLogic(restService.triggerProjectExportCreate),
    endpoints.getProjectIriExportsExportId.serverLogic(restService.getProjectExportStatus),
//    endpoints.getProjectIriExportsExportIdDownload.serverLogic(restService.downloadProjectExport),
  )
}

object V3ProjectsServerEndpoints {
  val layer = ZLayer.derive[V3ProjectsServerEndpoints]
}
