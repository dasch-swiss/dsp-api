/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api
import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.admin.api.service.ProjectsLegalInfoRestService

final class ProjectsLegalInfoServerEndpoints(
  private val endpoints: ProjectsLegalInfoEndpoints,
  private val restService: ProjectsLegalInfoRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
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

object ProjectsLegalInfoServerEndpoints {
  val layer = ZLayer.derive[ProjectsLegalInfoServerEndpoints]
}
