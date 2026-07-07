/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionGroupApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionHasPermissionsApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionPropertyApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.ChangePermissionResourceClassApiRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateAdministrativePermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateDefaultObjectAccessPermissionAPIRequestADM
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.api.admin.PermissionEndpointsRequests.ChangeDoapRequest
import org.knora.webapi.slice.api.admin.service.PermissionRestService

final class PermissionsServerEndpoints(
  permissionsEndpoints: PermissionsEndpoints,
  restService: PermissionRestService,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    permissionsEndpoints.postPermissionsAp.serverLogic(restService.createAdministrativePermission),
    permissionsEndpoints.getPermissionsApByProjectIri.serverLogic(restService.getPermissionsApByProjectIri),
    permissionsEndpoints.getPermissionsApByProjectAndGroupIri.serverLogic(
      restService.getPermissionsApByProjectAndGroupIri,
    ),
    permissionsEndpoints.getPermissionsDoapByProjectIri.serverLogic(restService.getPermissionsDaopByProjectIri),
    permissionsEndpoints.getPermissionsByProjectIri.serverLogic(restService.getPermissionsByProjectIri),
    permissionsEndpoints.putPermissionsDoapForWhat.serverLogic(restService.updateDoapForWhat),
    permissionsEndpoints.putPermissionsProjectIriGroup.serverLogic(restService.updatePermissionGroup),
    permissionsEndpoints.putPerrmissionsHasPermissions.serverLogic(restService.updatePermissionHasPermissions),
    permissionsEndpoints.putPermissionsProperty.serverLogic(restService.updatePermissionProperty),
    permissionsEndpoints.putPermisssionsResourceClass.serverLogic(restService.updatePermissionResourceClass),
    permissionsEndpoints.deletePermission.serverLogic(restService.deletePermission),
    permissionsEndpoints.postPermissionsDoap.serverLogic(restService.createDefaultObjectAccessPermission),
  )
}
object PermissionsServerEndpoints {
  val layer = ZLayer.derive[PermissionsServerEndpoints]
}
