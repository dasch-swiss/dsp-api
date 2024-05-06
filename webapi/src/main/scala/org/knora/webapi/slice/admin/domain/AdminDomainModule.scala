/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain

import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.responders.IriService
import org.knora.webapi.slice.admin.domain.service.GroupService
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service._
import org.knora.webapi.slice.admin.domain.service.maintenance.MaintenanceService
import org.knora.webapi.slice.admin.repo.AdminRepoModule
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.store.triplestore.api.TriplestoreService

object AdminDomainModule {

  type Dependencies =
    AppConfig & AdminRepoModule.Provided & IriService & OntologyRepo & TriplestoreService

  type Provided =
    // format: off
    AdministrativePermissionService &
    CacheManager &
    GroupService &
    KnoraGroupService &
    KnoraProjectService &
    KnoraUserService &
    KnoraUserToUserConverter &
    MaintenanceService &
    PasswordService &
    ProjectService &
    UserService
    // format: on

  val layer = ZLayer.makeSome[Dependencies, Provided](
    AdministrativePermissionService.layer,
    GroupService.layer,
    KnoraGroupService.layer,
    KnoraProjectService.layer,
    KnoraUserService.layer,
    KnoraUserToUserConverter.layer,
    MaintenanceService.layer,
    PasswordService.layer,
    ProjectService.layer,
    UserService.layer,
  )
}
