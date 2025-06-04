/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.URLayer
import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.Features
import org.knora.webapi.responders.admin.AssetPermissionsResponder
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.responders.admin.PermissionsResponder
import org.knora.webapi.slice.admin.api.service.GroupRestService
import org.knora.webapi.slice.admin.api.service.MaintenanceRestService
import org.knora.webapi.slice.admin.api.service.PermissionRestService
import org.knora.webapi.slice.admin.api.service.ProjectRestService
import org.knora.webapi.slice.admin.api.service.ProjectsLegalInfoRestService
import org.knora.webapi.slice.admin.api.service.StoreRestService
import org.knora.webapi.slice.admin.api.service.UserRestService
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
import org.knora.webapi.slice.admin.domain.service.ProjectImportService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.admin.domain.service.maintenance.MaintenanceService
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
      Features &
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
      ProjectImportService &
      ProjectService &
      TapirToZioHttpInterpreter &
      TriplestoreService &
      UserService
      // format: on

  type Provided =
      // format: off
      AdminApiEndpoints &
      AdminApiServerEndpoints &
      // the `*RestService`s are only exposed for the integration tests
      GroupRestService &
      PermissionRestService &
      ProjectRestService &
      UserRestService
      // format: on

  val layer: URLayer[self.Dependencies, self.Provided] =
    ZLayer.makeSome[self.Dependencies, self.Provided](
      AdminApiEndpoints.layer,
      AdminApiServerEndpoints.layer,
      FilesEndpoints.layer,
      FilesServerEndpoints.layer,
      GroupsEndpoints.layer,
      GroupsServerEndpoints.layer,
      GroupRestService.layer,
      ListRestService.layer,
      ListsEndpoints.layer,
      ListsServerEndpoints.layer,
      MaintenanceRestService.layer,
      MaintenanceEndpoints.layer,
      MaintenanceServerEndpoints.layer,
      PermissionsEndpoints.layer,
      PermissionsServerEndpoints.layer,
      PermissionRestService.layer,
      ProjectsLegalInfoEndpoints.layer,
      ProjectsLegalInfoServerEndpoints.layer,
      ProjectsLegalInfoRestService.layer,
      ProjectRestService.layer,
      ProjectsEndpoints.layer,
      ProjectsServerEndpoints.layer,
      StoreRestService.layer,
      StoreEndpoints.layer,
      StoreServerEndpoints.layer,
      UserRestService.layer,
      UsersEndpoints.layer,
      UsersServerEndpoints.layer,
    )
}
