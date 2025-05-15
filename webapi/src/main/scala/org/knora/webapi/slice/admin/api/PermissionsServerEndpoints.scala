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
import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.ZServerEndpoint

final case class PermissionsServerEndpoints(
  private val permissionsEndpoints: PermissionsEndpoints,
  private val restService: PermissionRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    permissionsEndpoints.postPermissionsAp
      .serverLogic(restService.createAdministrativePermission),
    permissionsEndpoints.getPermissionsApByProjectIri
      .serverLogic(restService.getPermissionsApByProjectIri),
    permissionsEndpoints.getPermissionsApByProjectAndGroupIri
      .serverLogic(restService.getPermissionsApByProjectAndGroupIri),
    permissionsEndpoints.getPermissionsDoapByProjectIri
      .serverLogic(restService.getPermissionsDaopByProjectIri),
    permissionsEndpoints.getPermissionsByProjectIri
      .serverLogic(restService.getPermissionsByProjectIri),
    permissionsEndpoints.deletePermission
      .serverLogic(restService.deletePermission),
    permissionsEndpoints.postPermissionsDoap
      .serverLogic(restService.createDefaultObjectAccessPermission),
    permissionsEndpoints.putPermissionsDoapForWhat
      .serverLogic(restService.updateDoapForWhat),
    permissionsEndpoints.putPermissionsProjectIriGroup
      .serverLogic(restService.updatePermissionGroup),
    permissionsEndpoints.putPerrmissionsHasPermissions
      .serverLogic(restService.updatePermissionHasPermissions),
    permissionsEndpoints.putPermisssionsResourceClass
      .serverLogic(restService.updatePermissionResourceClass),
    permissionsEndpoints.putPermissionsProperty
      .serverLogic(restService.updatePermissionProperty),
  )
}

object PermissionsServerEndpoints {
  val layer = ZLayer.derive[PermissionsServerEndpoints]
}
