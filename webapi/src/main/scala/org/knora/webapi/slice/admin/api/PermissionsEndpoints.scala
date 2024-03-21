/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir._
import sttp.tapir.generic.auto._
import sttp.tapir.json.spray.{jsonBody => sprayJsonBody}
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.slice.admin.api.AdminPathVariables.groupIriPathVar
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
    .in(permissionsBase / "ap" / projectIri / groupIriPathVar)
    .description("Get all administrative permissions for a project and a group.")
    .out(sprayJsonBody[AdministrativePermissionGetResponseADM])

  val getPermissionsDoapByProjectIri = base.securedEndpoint.get
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

  val putPermissionsProjectIriGroup = base.securedEndpoint.put
    .in(permissionsBase / permissionIri / "group")
    .description("Update a permission's group")
    .in(sprayJsonBody[ChangePermissionGroupApiRequestADM])
    .out(sprayJsonBody[PermissionGetResponseADM])

  val putPerrmissionsHasPermissions = base.securedEndpoint.put
    .in(permissionsBase / permissionIri / "hasPermissions")
    .description("Update a permission's set of hasPermissions")
    .in(sprayJsonBody[ChangePermissionHasPermissionsApiRequestADM])
    .out(sprayJsonBody[PermissionGetResponseADM])

  val putPermisssionsResourceClass = base.securedEndpoint.put
    .in(permissionsBase / permissionIri / "resourceClass")
    .description("Update a permission's resource class")
    .in(sprayJsonBody[ChangePermissionResourceClassApiRequestADM])
    .out(sprayJsonBody[PermissionGetResponseADM])

  val putPermissionsProperty = base.securedEndpoint.put
    .in(permissionsBase / permissionIri / "property")
    .description("Update a permission's property")
    .in(sprayJsonBody[ChangePermissionPropertyApiRequestADM])
    .out(sprayJsonBody[PermissionGetResponseADM])

  val endpoints: Seq[AnyEndpoint] = Seq(
    postPermissionsAp,
    getPermissionsApByProjectIri,
    getPermissionsApByProjectAndGroupIri,
    getPermissionsDoapByProjectIri,
    getPermissionsByProjectIri,
    deletePermission,
    postPermissionsDoap,
    putPermissionsProjectIriGroup,
    putPerrmissionsHasPermissions,
    putPermisssionsResourceClass,
    putPermissionsProperty,
  ).map(_.endpoint.tag("Admin Permissions"))
}

object PermissionsEndpoints {
  val layer = ZLayer.derive[PermissionsEndpoints]
}
