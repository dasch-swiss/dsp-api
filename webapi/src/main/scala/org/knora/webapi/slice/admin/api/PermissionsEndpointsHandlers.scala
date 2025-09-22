/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionGroupApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionHasPermissionsApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionPropertyApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionResourceClassApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateAdministrativePermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateDefaultObjectAccessPermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionDeleteResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsForProjectGetResponseADM
import org.knora.webapi.slice.admin.api.PermissionEndpointsRequests.ChangeDoapRequest
import org.knora.webapi.slice.admin.api.service.PermissionRestService
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

final case class PermissionsEndpointsHandlers(
  permissionsEndpoints: PermissionsEndpoints,
  restService: PermissionRestService,
  mapper: HandlerMapper,
) {

  val allHanders = List(
    SecuredEndpointHandler(permissionsEndpoints.postPermissionsAp, restService.createAdministrativePermission),
    SecuredEndpointHandler(permissionsEndpoints.getPermissionsApByProjectIri, restService.getPermissionsApByProjectIri),
    SecuredEndpointHandler(
      permissionsEndpoints.getPermissionsApByProjectAndGroupIri,
      restService.getPermissionsApByProjectAndGroupIri,
    ),
    SecuredEndpointHandler(
      permissionsEndpoints.getPermissionsDoapByProjectIri,
      restService.getPermissionsDaopByProjectIri,
    ),
    SecuredEndpointHandler(permissionsEndpoints.getPermissionsByProjectIri, restService.getPermissionsByProjectIri),
    SecuredEndpointHandler(permissionsEndpoints.putPermissionsDoapForWhat, restService.updateDoapForWhat),
    SecuredEndpointHandler(permissionsEndpoints.putPermissionsProjectIriGroup, restService.updatePermissionGroup),
    SecuredEndpointHandler(
      permissionsEndpoints.putPerrmissionsHasPermissions,
      restService.updatePermissionHasPermissions,
    ),
    SecuredEndpointHandler(permissionsEndpoints.putPermissionsProperty, restService.updatePermissionProperty),
    SecuredEndpointHandler(
      permissionsEndpoints.putPermisssionsResourceClass,
      restService.updatePermissionResourceClass,
    ),
    SecuredEndpointHandler(permissionsEndpoints.deletePermission, restService.deletePermission),
    SecuredEndpointHandler(permissionsEndpoints.postPermissionsDoap, restService.createDefaultObjectAccessPermission),
  ).map(mapper.mapSecuredEndpointHandler)
}

object PermissionsEndpointsHandlers {

  val layer = ZLayer.derive[PermissionsEndpointsHandlers]
}
