/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.ZServerEndpoint
import zio.ZLayer

import org.knora.webapi.slice.admin.api.service.ProjectsLegalInfoRestService

final class ProjectsLegalInfoServerEndpoints(
  endpoints: ProjectsLegalInfoEndpoints,
  restService: ProjectsLegalInfoRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    endpoints.getProjectAuthorships.serverLogic(restService.findAuthorships),
    endpoints.getProjectLicenses.serverLogic(restService.findLicenses),
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
