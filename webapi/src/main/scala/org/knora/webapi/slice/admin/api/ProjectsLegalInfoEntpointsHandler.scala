package org.knora.webapi.slice.admin.api
import zio.ZLayer

import org.knora.webapi.slice.admin.api.service.ProjectsLegalInfoRestService
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

final class ProjectsLegalInfoEndpointsHandler(
  endpoints: ProjectsLegalInfoEndpoints,
  restService: ProjectsLegalInfoRestService,
  mapper: HandlerMapper,
) {
  val getProjectLicensesHandler = SecuredEndpointHandler(
    endpoints.getProjectLicenses,
    user => shortcode => restService.findByProjectId(shortcode, user),
  )

  val allHandlers = List(getProjectLicensesHandler).map(mapper.mapSecuredEndpointHandler(_))
}

object ProjectsLegalInfoEndpointsHandler {
  val layer = ZLayer.derive[ProjectsLegalInfoEndpointsHandler]
}
