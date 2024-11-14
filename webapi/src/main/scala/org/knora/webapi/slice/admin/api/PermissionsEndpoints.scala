/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.*
import sttp.tapir.EndpointIO.Example
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.json.JsonDecoder

import org.knora.webapi.messages.admin.responder.permissionsmessages.*
import org.knora.webapi.slice.admin.api.AdminPathVariables.groupIriPathVar
import org.knora.webapi.slice.admin.api.AdminPathVariables.permissionIri
import org.knora.webapi.slice.admin.api.AdminPathVariables.projectIri
import org.knora.webapi.slice.admin.api.PermissionEndpointsRequests.ChangeDoapForWhatRequest
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.service.KnoraGroupRepo
import org.knora.webapi.slice.common.api.BaseEndpoints

object PermissionEndpointsRequests {
  final case class ChangeDoapForWhatRequest(
    forGroup: Option[String],
    forResourceClass: Option[String],
    forProperty: Option[String],
  )
  object ChangeDoapForWhatRequest {
    given JsonCodec[ChangeDoapForWhatRequest] = DeriveJsonCodec.gen[ChangeDoapForWhatRequest]
  }
}

final case class PermissionsEndpoints(base: BaseEndpoints) {

  private val permissionsBase = "admin" / "permissions"

  val postPermissionsAp = base.securedEndpoint.post
    .in(permissionsBase / "ap")
    .description("Create a new administrative permission")
    .in(jsonBody[CreateAdministrativePermissionAPIRequestADM])
    .out(jsonBody[AdministrativePermissionCreateResponseADM])

  val getPermissionsApByProjectIri = base.securedEndpoint.get
    .in(permissionsBase / "ap" / projectIri)
    .description("Get all administrative permissions for a project.")
    .out(jsonBody[AdministrativePermissionsForProjectGetResponseADM])

  val getPermissionsApByProjectAndGroupIri = base.securedEndpoint.get
    .in(permissionsBase / "ap" / projectIri / groupIriPathVar)
    .description("Get all administrative permissions for a project and a group.")
    .out(jsonBody[AdministrativePermissionGetResponseADM])

  val getPermissionsDoapByProjectIri = base.securedEndpoint.get
    .in(permissionsBase / "doap" / projectIri)
    .description("Get all default object access permissions for a project.")
    .out(jsonBody[DefaultObjectAccessPermissionsForProjectGetResponseADM])

  val getPermissionsByProjectIri = base.securedEndpoint.get
    .in(permissionsBase / projectIri)
    .description("Get all permissions for a project.")
    .out(jsonBody[PermissionsForProjectGetResponseADM])

  val deletePermission = base.securedEndpoint.delete
    .in(permissionsBase / permissionIri)
    .description("Delete an permission.")
    .out(jsonBody[PermissionDeleteResponseADM])

  val postPermissionsDoap = base.securedEndpoint.post
    .in(permissionsBase / "doap")
    .description("Create a new default object access permission")
    .in(jsonBody[CreateDefaultObjectAccessPermissionAPIRequestADM])
    .out(jsonBody[DefaultObjectAccessPermissionCreateResponseADM])

  val putPermissionsDoapForWhat = base.securedEndpoint.put
    .in(permissionsBase / "doap" / permissionIri)
    .description("Create a new default object access permission")
    .in(
      jsonBody[ChangeDoapForWhatRequest]
        .description(
          "Default object access permissions can be only for group, resource class, property or both resource class and property." +
            "If an invalid combination is provided, the request will fail with a Bad Request response." +
            "The iris for resource class and property must be valid Api V2 complex iris.",
        )
        .examples(
          List(
            Example(
              ChangeDoapForWhatRequest(Some(KnoraGroupRepo.builtIn.ProjectMember.id.value), None, None),
              name = Some("For a group"),
              summary = None,
              description = None,
            ),
            Example(
              ChangeDoapForWhatRequest(
                None,
                Some("http://api.dasch.swiss/ontology/0803/incunabula/v2#bild"),
                Some("http://api.dasch.swiss/ontology/0803/incunabula/v2#pagenum"),
              ),
              name = Some("For a resource class and a property"),
              summary = None,
              description = None,
            ),
          ),
        ),
    )
    .out(jsonBody[DefaultObjectAccessPermissionGetResponseADM])

  val putPermissionsProjectIriGroup = base.securedEndpoint.put
    .in(permissionsBase / permissionIri / "group")
    .description("Update a permission's group")
    .in(jsonBody[ChangePermissionGroupApiRequestADM])
    .out(jsonBody[PermissionGetResponseADM])

  val putPerrmissionsHasPermissions = base.securedEndpoint.put
    .in(permissionsBase / permissionIri / "hasPermissions")
    .description("Update a permission's set of hasPermissions")
    .in(jsonBody[ChangePermissionHasPermissionsApiRequestADM])
    .out(jsonBody[PermissionGetResponseADM])

  val putPermisssionsResourceClass = base.securedEndpoint.put
    .in(permissionsBase / permissionIri / "resourceClass")
    .description("Update a DOAP's resource class. Use `PUT /admin/permissions/doap/{permissionIri}` instead.")
    .in(jsonBody[ChangePermissionResourceClassApiRequestADM])
    .out(jsonBody[DefaultObjectAccessPermissionGetResponseADM])
    .deprecated()

  val putPermissionsProperty = base.securedEndpoint.put
    .in(permissionsBase / permissionIri / "property")
    .description("Update a DAOP's property. Use `PUT /admin/permissions/doap/{permissionIri}` instead.")
    .in(jsonBody[ChangePermissionPropertyApiRequestADM])
    .out(jsonBody[DefaultObjectAccessPermissionGetResponseADM])
    .deprecated()

  val endpoints: Seq[AnyEndpoint] = Seq(
    postPermissionsAp,
    getPermissionsApByProjectIri,
    getPermissionsApByProjectAndGroupIri,
    getPermissionsDoapByProjectIri,
    getPermissionsByProjectIri,
    deletePermission,
    postPermissionsDoap,
    putPermissionsDoapForWhat,
    putPermissionsProjectIriGroup,
    putPerrmissionsHasPermissions,
    putPermisssionsResourceClass,
    putPermissionsProperty,
  ).map(_.endpoint.tag("Admin Permissions"))
}

object PermissionsEndpoints {
  val layer = ZLayer.derive[PermissionsEndpoints]
}
