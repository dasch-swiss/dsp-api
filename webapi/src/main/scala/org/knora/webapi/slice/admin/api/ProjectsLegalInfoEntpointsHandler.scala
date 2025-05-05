/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

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
  val allHandlers = List(
    SecuredEndpointHandler(endpoints.getProjectAuthorships, restService.findAuthorships),
    SecuredEndpointHandler(endpoints.getProjectLicenses, restService.findLicenses),
    SecuredEndpointHandler(endpoints.putProjectLicensesEnable, restService.enableLicense),
    SecuredEndpointHandler(endpoints.putProjectLicensesDisable, restService.disableLicense),
    SecuredEndpointHandler(endpoints.getProjectCopyrightHolders, restService.findCopyrightHolders),
    SecuredEndpointHandler(endpoints.postProjectCopyrightHolders, restService.addCopyrightHolders),
    SecuredEndpointHandler(endpoints.putProjectCopyrightHolders, restService.replaceCopyrightHolder),
  ).map(mapper.mapSecuredEndpointHandler)
}

object ProjectsLegalInfoEndpointsHandler {
  val layer = ZLayer.derive[ProjectsLegalInfoEndpointsHandler]
}
