/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionDeleteResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.slice.admin.api.service.PermissionsRestService
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.PermissionIri
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

final case class PermissionsEndpointsHandlers(
  permissionsEndpoints: PermissionsEndpoints,
  restService: PermissionsRestService,
  mapper: HandlerMapper
) {

  private val postPermissionsApHandler =
    SecuredEndpointHandler[
      CreateAdministrativePermissionAPIRequestADM,
      AdministrativePermissionCreateResponseADM
    ](
      permissionsEndpoints.postPermissionsAp,
      user => { case (request: CreateAdministrativePermissionAPIRequestADM) =>
        restService.createAdministrativePermission(request, user)
      }
    )

  private val getPermissionsApByProjectIriHandler =
    SecuredEndpointHandler[IriIdentifier, AdministrativePermissionsForProjectGetResponseADM](
      permissionsEndpoints.getPermissionsApByProjectIri,
      user => { case (projectIri: IriIdentifier) =>
        restService.getPermissionsApByProjectIri(projectIri.value, user)
      }
    )

  private val getPermissionsApByProjectAndGroupIriHandler =
    SecuredEndpointHandler[(IriIdentifier, GroupIri), AdministrativePermissionGetResponseADM](
      permissionsEndpoints.getPermissionsApByProjectAndGroupIri,
      user => { case (projectIri: IriIdentifier, groupIri: GroupIri) =>
        restService.getPermissionsApByProjectAndGroupIri(projectIri.value, groupIri, user)
      }
    )

  private val getPermissionsDaopByProjectIriHandler =
    SecuredEndpointHandler[IriIdentifier, DefaultObjectAccessPermissionsForProjectGetResponseADM](
      permissionsEndpoints.getPermissionsDoapByProjectIri,
      user => { case (projectIri: IriIdentifier) =>
        restService.getPermissionsDaopByProjectIri(projectIri.value, user)
      }
    )

  private val getPermissionsByProjectIriHandler =
    SecuredEndpointHandler[IriIdentifier, PermissionsForProjectGetResponseADM](
      permissionsEndpoints.getPermissionsByProjectIri,
      user => { case (projectIri: IriIdentifier) =>
        restService.getPermissionsByProjectIri(projectIri.value, user)
      }
    )

  private val deletePermissionHandler =
    SecuredEndpointHandler[PermissionIri, PermissionDeleteResponseADM](
      permissionsEndpoints.deletePermission,
      user => { case (permissionIri: PermissionIri) =>
        restService.deletePermission(permissionIri, user)
      }
    )

  private val postPermissionsDoapHandler =
    SecuredEndpointHandler[
      CreateDefaultObjectAccessPermissionAPIRequestADM,
      DefaultObjectAccessPermissionCreateResponseADM
    ](
      permissionsEndpoints.postPermissionsDoap,
      user => { case (request: CreateDefaultObjectAccessPermissionAPIRequestADM) =>
        restService.createDefaultObjectAccessPermission(request, user)
      }
    )

  private val putPermissionsProjectIriGroupHandler =
    SecuredEndpointHandler[
      (PermissionIri, ChangePermissionGroupApiRequestADM),
      PermissionGetResponseADM
    ](
      permissionsEndpoints.putPermissionsProjectIriGroup,
      user => { case (permissionIri: PermissionIri, request: ChangePermissionGroupApiRequestADM) =>
        restService.updatePermissionGroup(permissionIri, request, user)
      }
    )

  private val putPermissionsHasPermissionsHandler =
    SecuredEndpointHandler[
      (PermissionIri, ChangePermissionHasPermissionsApiRequestADM),
      PermissionGetResponseADM
    ](
      permissionsEndpoints.putPerrmissionsHasPermissions,
      user => { case (permissionIri: PermissionIri, request: ChangePermissionHasPermissionsApiRequestADM) =>
        restService.updatePermissionHasPermissions(permissionIri, request, user)
      }
    )

  private val putPermissionsResourceClass =
    SecuredEndpointHandler[(PermissionIri, ChangePermissionResourceClassApiRequestADM), PermissionGetResponseADM](
      permissionsEndpoints.putPermisssionsResourceClass,
      user => { case (permissionIri: PermissionIri, request: ChangePermissionResourceClassApiRequestADM) =>
        restService.updatePermissionResourceClass(permissionIri, request, user)
      }
    )

  private val putPermissionsProperty =
    SecuredEndpointHandler[(PermissionIri, ChangePermissionPropertyApiRequestADM), PermissionGetResponseADM](
      permissionsEndpoints.putPermissionsProperty,
      user => { case (permissionIri: PermissionIri, request: ChangePermissionPropertyApiRequestADM) =>
        restService.updatePermissionProperty(permissionIri, request, user)
      }
    )

  private val securedHandlers =
    List(
      postPermissionsApHandler,
      getPermissionsApByProjectIriHandler,
      getPermissionsApByProjectAndGroupIriHandler,
      getPermissionsDaopByProjectIriHandler,
      getPermissionsByProjectIriHandler,
      putPermissionsProjectIriGroupHandler,
      putPermissionsHasPermissionsHandler,
      putPermissionsProperty,
      putPermissionsResourceClass,
      deletePermissionHandler,
      postPermissionsDoapHandler
    ).map(mapper.mapSecuredEndpointHandler(_))

  val allHanders = securedHandlers
}

object PermissionsEndpointsHandlers {

  val layer = ZLayer.derive[PermissionsEndpointsHandlers]
}
