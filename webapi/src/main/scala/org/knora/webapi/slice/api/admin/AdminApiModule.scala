/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.responders.admin.AssetPermissionsResponder
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.responders.admin.PermissionsResponder
import org.knora.webapi.slice.admin.domain.service.AdministrativePermissionService
import org.knora.webapi.slice.admin.domain.service.GroupService
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.KnoraUserToUserConverter
import org.knora.webapi.slice.admin.domain.service.LegalInfoService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.ProjectEraseService
import org.knora.webapi.slice.admin.domain.service.ProjectExportService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.admin.domain.service.maintenance.MaintenanceService
import org.knora.webapi.slice.api.admin.service.GroupRestService
import org.knora.webapi.slice.api.admin.service.MaintenanceRestService
import org.knora.webapi.slice.api.admin.service.PermissionRestService
import org.knora.webapi.slice.api.admin.service.ProjectRestService
import org.knora.webapi.slice.api.admin.service.ProjectsLegalInfoRestService
import org.knora.webapi.slice.api.admin.service.StoreRestService
import org.knora.webapi.slice.api.admin.service.UserRestService
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService

object AdminApiModule { self =>

  type Dependencies =
      // format: off
      AdministrativePermissionService &
      AppConfig &
      AssetPermissionsResponder &
      AuthorizationRestService &
      BaseEndpoints &
      CacheManager &
      GroupService &
      KnoraGroupService &
      KnoraProjectService &
      KnoraResponseRenderer &
      KnoraUserService &
      KnoraUserToUserConverter &
      LegalInfoService &
      ListsResponder &
      MaintenanceService &
      OntologyCache &
      PasswordService &
      PermissionsResponder &
      ProjectEraseService &
      ProjectExportService &
      ProjectService &
      TriplestoreService &
      UserService
      // format: on

  type Provided =
      // format: off
      AdminApiServerEndpoints &
      // the `*RestService`s are only exposed for the integration tests
      GroupRestService &
      PermissionRestService &
      ProjectRestService &
      UserRestService
      // format: on

  val layer: URLayer[self.Dependencies, self.Provided] =
    // Layer 1: All Endpoints and RestServices are fully independent of each other
    (AdminListsEndpoints.layer ++ AdminListRestService.layer ++
      FilesEndpoints.layer ++
      GroupsEndpoints.layer ++ GroupRestService.layer ++
      MaintenanceEndpoints.layer ++ MaintenanceRestService.layer ++
      PermissionsEndpoints.layer ++ PermissionRestService.layer ++
      ProjectsEndpoints.layer ++ ProjectRestService.layer ++
      ProjectsLegalInfoEndpoints.layer ++ ProjectsLegalInfoRestService.layer ++
      StoreEndpoints.layer ++ StoreRestService.layer ++
      UsersEndpoints.layer ++ UserRestService.layer) >+>
      // Layer 2: Each ServerEndpoints depends on its paired Endpoints + RestService
      (AdminListsServerEndpoints.layer ++ FilesServerEndpoints.layer ++
        GroupsServerEndpoints.layer ++ MaintenanceServerEndpoints.layer ++
        PermissionsServerEndpoints.layer ++ ProjectsServerEndpoints.layer ++
        ProjectsLegalInfoServerEndpoints.layer ++ StoreServerEndpoints.layer ++
        UsersServerEndpoints.layer) >+>
      // Layer 3: Top-level aggregator
      AdminApiServerEndpoints.layer
}
