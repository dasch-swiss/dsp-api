/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.URModule
import org.knora.webapi.slice.admin.domain.service.*
import org.knora.webapi.slice.admin.domain.service.GroupService
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service.maintenance.MaintenanceService
import org.knora.webapi.slice.admin.repo.AdminRepoModule
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.resourceinfo.domain.IriConverter
import org.knora.webapi.store.triplestore.api.TriplestoreService

object AdminDomainModule
    extends URModule[
      // format: off
      AdminRepoModule.Provided &
      AppConfig &
      CacheManager &
      DspIngestClient &
      IriConverter &
      IriService &
      OntologyCache &
      OntologyRepo &
      TriplestoreService
      ,
      AdministrativePermissionService &
      DefaultObjectAccessPermissionService &
      GroupService &
      KnoraGroupService &
      KnoraProjectService &
      KnoraUserService &
      KnoraUserToUserConverter &
      MaintenanceService &
      PasswordService &
      ProjectEraseService &
      ProjectService &
      UserService
      // format: on
    ] { self =>
  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      AdministrativePermissionService.layer,
      DefaultObjectAccessPermissionService.layer,
      GroupService.layer,
      KnoraGroupService.layer,
      KnoraProjectService.layer,
      KnoraUserService.layer,
      KnoraUserToUserConverter.layer,
      MaintenanceService.layer,
      PasswordService.layer,
      ProjectEraseService.layer,
      ProjectService.layer,
      UserService.layer,
    )
}
