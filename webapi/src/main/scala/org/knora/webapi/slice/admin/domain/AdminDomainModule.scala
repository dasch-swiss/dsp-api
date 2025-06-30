/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.model.AdministrativePermissionRepo
import org.knora.webapi.slice.admin.domain.model.DefaultObjectAccessPermissionRepo
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.admin.domain.service.maintenance.MaintenanceService
import org.knora.webapi.slice.admin.repo.LicenseRepo
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService

object AdminDomainModule { self =>

  type Dependencies =
      // format: off
      AdministrativePermissionRepo &
      AppConfig &
      DefaultObjectAccessPermissionRepo &
      DspIngestClient &
      IriService &
      KnoraGroupRepo &
      KnoraProjectRepo &
      KnoraUserRepo &
      LicenseRepo &
      OntologyCache &
      OntologyRepo &
      TriplestoreService
      // format: on

  type Provided =
      // format: off
      AdministrativePermissionService &
      DefaultObjectAccessPermissionService &
      GroupService &
      KnoraGroupService &
      KnoraProjectService &
      KnoraUserService &
      KnoraUserToUserConverter &
      LegalInfoService &
      MaintenanceService &
      PasswordService &
      ProjectEraseService &
      ProjectService &
      UserService
      // format: on

  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      AdministrativePermissionService.layer,
      DefaultObjectAccessPermissionService.layer,
      GroupService.layer,
      KnoraGroupService.layer,
      KnoraProjectService.layer,
      KnoraUserService.layer,
      KnoraUserToUserConverter.layer,
      LegalInfoService.layer,
      MaintenanceService.layer,
      PasswordService.layer,
      ProjectEraseService.layer,
      ProjectService.layer,
      UserService.layer,
    )
}
