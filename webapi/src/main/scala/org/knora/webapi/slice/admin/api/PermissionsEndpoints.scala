/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody as sprayJsonBody
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateAdministrativePermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateDefaultObjectAccessPermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionDeleteResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsADMJsonProtocol
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsForProjectGetResponseADM
import org.knora.webapi.slice.admin.api.AdminPathVariables.groupIri
import org.knora.webapi.slice.admin.api.AdminPathVariables.permissionIri
import org.knora.webapi.slice.admin.api.AdminPathVariables.projectIri
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class PermissionsEndpoints(base: BaseEndpoints) extends PermissionsADMJsonProtocol {

  private val permissionsBase = "admin" / "permissions"

  val postPermissionsAp = base.securedEndpoint.post
    .in(permissionsBase / "ap")
    .description("Create a new administrative permission")
    .in(sprayJsonBody[CreateAdministrativePermissionAPIRequestADM])
    .out(sprayJsonBody[AdministrativePermissionCreateResponseADM])

  val getPermissionsApByProjectIri = base.securedEndpoint.get
    .in(permissionsBase / "ap" / projectIri)
    .description("Get all administrative permissions for a project.")
    .out(sprayJsonBody[AdministrativePermissionsForProjectGetResponseADM])

  val getPermissionsApByProjectAndGroupIri = base.securedEndpoint.get
    .in(permissionsBase / "ap" / projectIri / groupIri)
    .description("Get all administrative permissions for a project and a group.")
    .out(sprayJsonBody[AdministrativePermissionGetResponseADM])

  val getPermissionsDaopByProjectIri = base.securedEndpoint.get
    .in(permissionsBase / "doap" / projectIri)
    .description("Get all default object access permissions for a project.")
    .out(sprayJsonBody[DefaultObjectAccessPermissionsForProjectGetResponseADM])

  val getPermissionsByProjectIri = base.securedEndpoint.get
    .in(permissionsBase / projectIri)
    .description("Get all permissions for a project.")
    .out(sprayJsonBody[PermissionsForProjectGetResponseADM])

  val deletePermission = base.securedEndpoint.delete
    .in(permissionsBase / permissionIri)
    .description("Delete an permission.")
    .out(sprayJsonBody[PermissionDeleteResponseADM])

  val postPermissionsDoap = base.securedEndpoint.post
    .in(permissionsBase / "doap")
    .description("Create a new default object access permission")
    .in(sprayJsonBody[CreateDefaultObjectAccessPermissionAPIRequestADM])
    .out(sprayJsonBody[DefaultObjectAccessPermissionCreateResponseADM])
}

object PermissionsEndpoints {
  val layer = ZLayer.derive[PermissionsEndpoints]
}
