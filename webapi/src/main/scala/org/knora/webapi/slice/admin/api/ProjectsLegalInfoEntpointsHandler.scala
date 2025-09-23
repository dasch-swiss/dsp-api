/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api
import zio.*
import sttp.tapir.ztapir.*

import org.knora.webapi.slice.admin.api.service.ProjectsLegalInfoRestService

final class ProjectsLegalInfoEndpointsHandler(
  endpoints: ProjectsLegalInfoEndpoints,
  restService: ProjectsLegalInfoRestService,
) {
  val allHandlers = Seq(
    endpoints.getProjectLicenses.zServerLogic(restService.findLicenses),
    endpoints.getProjectLicensesIri.zServerLogic(restService.findAvailableLicenseByIdAndShortcode),
    endpoints.getProjectAuthorships.serverLogic(restService.findAuthorships),
    endpoints.putProjectLicensesEnable.serverLogic(restService.enableLicense),
    endpoints.putProjectLicensesDisable.serverLogic(restService.disableLicense),
    endpoints.getProjectCopyrightHolders.serverLogic(restService.findCopyrightHolders),
    endpoints.postProjectCopyrightHolders.serverLogic(restService.addCopyrightHolders),
    endpoints.putProjectCopyrightHolders.serverLogic(restService.replaceCopyrightHolder),
  )
}

object ProjectsLegalInfoEndpointsHandler {
  val layer = ZLayer.derive[ProjectsLegalInfoEndpointsHandler]
}
