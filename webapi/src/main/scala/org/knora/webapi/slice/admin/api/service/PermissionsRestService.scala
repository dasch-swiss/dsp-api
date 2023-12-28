/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.Random
import zio.Task
import zio.ZIO
import zio.ZLayer

import dsp.errors.ForbiddenException
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateAdministrativePermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.responders.admin.PermissionsResponderADM

final case class PermissionsRestService(responder: PermissionsResponderADM) {

  def createAdministrativePermission(
    request: CreateAdministrativePermissionAPIRequestADM,
    user: UserADM
  ): Task[AdministrativePermissionCreateResponseADM] = ZIO
    .fail(ForbiddenException("A new administrative permission can only be added by system or project admins."))
    .when(!user.isSystemAdmin && !user.permissions.isProjectAdmin(request.forProject) && !user.isSystemUser) *>
    Random.nextUUID.flatMap(uuid => responder.createAdministrativePermission(request, user, uuid))
}

object PermissionsRestService {

  val layer = ZLayer.derive[PermissionsRestService]
}
