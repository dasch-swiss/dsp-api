/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.config.AppConfig
import org.knora.webapi.responders.admin.AssetPermissionsResponder
import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.responders.admin.PermissionsResponder
import org.knora.webapi.responders.admin.UsersResponder
import org.knora.webapi.slice.admin.api.service.GroupRestService
import org.knora.webapi.slice.admin.api.service.MaintenanceRestService
import org.knora.webapi.slice.admin.api.service.PermissionRestService
import org.knora.webapi.slice.admin.api.service.ProjectRestService
import org.knora.webapi.slice.admin.api.service.StoreRestService
import org.knora.webapi.slice.admin.api.service.UserRestService
import org.knora.webapi.slice.admin.domain.service.AdministrativePermissionService
import org.knora.webapi.slice.admin.domain.service.GroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.KnoraUserToUserConverter
import org.knora.webapi.slice.admin.domain.service.MaintenanceService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.ProjectExportService
import org.knora.webapi.slice.admin.domain.service.ProjectImportService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.common.api._
import org.knora.webapi.slice.infrastructure.CacheManager
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService

object AdminApiModule {

  type Dependencies =
    AppConfig &
      AdministrativePermissionService &
      AssetPermissionsResponder &
      AuthorizationRestService &
      BaseEndpoints &
      CacheManager &
      GroupsResponderADM &
      GroupService &
      HandlerMapper &
      KnoraProjectService &
      KnoraResponseRenderer &
      KnoraUserService &
      KnoraUserToUserConverter &
      ListsResponder &
      MaintenanceService &
      OntologyCache &
      PasswordService &
      PermissionsResponder &
      ProjectExportService &
      ProjectImportService &
      ProjectService &
      TapirToPekkoInterpreter &
      TriplestoreService &
      UserService &
      UsersResponder

  type Provided = AdminApiEndpoints &
    AdminApiRoutes &
    // the `*RestService`s are only exposed for the integration tests
    GroupRestService &
    UserRestService &
    ProjectRestService &
    PermissionRestService

  val layer: ZLayer[Dependencies, Nothing, Provided] =
    ZLayer.makeSome[Dependencies, Provided](
      AdminApiEndpoints.layer,
      AdminApiRoutes.layer,
      FilesEndpoints.layer,
      FilesEndpointsHandler.layer,
      GroupsEndpoints.layer,
      GroupsEndpointsHandler.layer,
      GroupRestService.layer,
      ListRestService.layer,
      ListsEndpoints.layer,
      ListsEndpointsHandlers.layer,
      MaintenanceRestService.layer,
      MaintenanceEndpoints.layer,
      MaintenanceEndpointsHandlers.layer,
      PermissionsEndpoints.layer,
      PermissionsEndpointsHandlers.layer,
      PermissionRestService.layer,
      ProjectRestService.layer,
      ProjectsEndpoints.layer,
      ProjectsEndpointsHandler.layer,
      StoreRestService.layer,
      StoreEndpoints.layer,
      StoreEndpointsHandler.layer,
      UserRestService.layer,
      UsersEndpoints.layer,
      UsersEndpointsHandler.layer,
    )
}
