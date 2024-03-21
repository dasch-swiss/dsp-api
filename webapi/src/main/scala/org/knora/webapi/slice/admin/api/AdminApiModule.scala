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
import org.knora.webapi.responders.admin.PermissionsResponderADM
import org.knora.webapi.responders.admin.ProjectsResponderADM
import org.knora.webapi.responders.admin.UsersResponder
import org.knora.webapi.slice.admin.api.service.GroupsRestService
import org.knora.webapi.slice.admin.api.service.MaintenanceRestService
import org.knora.webapi.slice.admin.api.service.PermissionsRestService
import org.knora.webapi.slice.admin.api.service.ProjectRestService
import org.knora.webapi.slice.admin.api.service.StoreRestService
import org.knora.webapi.slice.admin.api.service.UsersRestService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.KnoraUserToUserConverter
import org.knora.webapi.slice.admin.domain.service.MaintenanceService
import org.knora.webapi.slice.admin.domain.service.PasswordService
import org.knora.webapi.slice.admin.domain.service.ProjectExportService
import org.knora.webapi.slice.admin.domain.service.ProjectImportService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.common.api.*
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.cache.CacheService
import org.knora.webapi.store.triplestore.api.TriplestoreService

object AdminApiModule {

  type Dependencies =
    AppConfig & AssetPermissionsResponder & AuthorizationRestService & BaseEndpoints & CacheService & GroupsResponderADM & HandlerMapper & KnoraProjectService & KnoraResponseRenderer & KnoraUserService & KnoraUserToUserConverter & ListsResponder & MaintenanceService & OntologyCache & PasswordService & PermissionsResponderADM & ProjectExportService & ProjectImportService & ProjectService & ProjectsResponderADM & TapirToPekkoInterpreter & TriplestoreService & UserService & UsersResponder

  type Provided = AdminApiEndpoints & AdminApiRoutes &
    // the `*RestService`s are only exposed for the integration tests
    UsersRestService & ProjectRestService & PermissionsRestService

  val layer: ZLayer[Dependencies, Nothing, Provided] =
    ZLayer.makeSome[Dependencies, Provided](
      AdminApiEndpoints.layer,
      AdminApiRoutes.layer,
      FilesEndpoints.layer,
      FilesEndpointsHandler.layer,
      GroupsEndpoints.layer,
      GroupsEndpointsHandler.layer,
      GroupsRestService.layer,
      ListRestService.layer,
      ListsEndpoints.layer,
      ListsEndpointsHandlers.layer,
      MaintenanceRestService.layer,
      MaintenanceEndpoints.layer,
      MaintenanceEndpointsHandlers.layer,
      PermissionsEndpoints.layer,
      PermissionsEndpointsHandlers.layer,
      PermissionsRestService.layer,
      ProjectRestService.layer,
      ProjectsEndpoints.layer,
      ProjectsEndpointsHandler.layer,
      StoreRestService.layer,
      StoreEndpoints.layer,
      StoreEndpointsHandler.layer,
      UsersRestService.layer,
      UsersEndpoints.layer,
      UsersEndpointsHandler.layer,
    )
}
