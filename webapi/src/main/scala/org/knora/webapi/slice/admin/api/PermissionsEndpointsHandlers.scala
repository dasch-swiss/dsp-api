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
import org.knora.webapi.slice.common.api.SecuredEndpointAndZioHandler

final case class PermissionsEndpointsHandlers(
  permissionsEndpoints: PermissionsEndpoints,
  restService: PermissionsRestService,
  mapper: HandlerMapper
) {

  private val postPermissionsApHandler =
    SecuredEndpointAndZioHandler[
      CreateAdministrativePermissionAPIRequestADM,
      AdministrativePermissionCreateResponseADM
    ](
      permissionsEndpoints.postPermissionsAp,
      user => { case (request: CreateAdministrativePermissionAPIRequestADM) =>
        restService.createAdministrativePermission(request, user)
      }
    )

  private val getPermissionsApByProjectIriHandler =
    SecuredEndpointAndZioHandler[IriIdentifier, AdministrativePermissionsForProjectGetResponseADM](
      permissionsEndpoints.getPermissionsApByProjectIri,
      user => { case (projectIri: IriIdentifier) =>
        restService.getPermissionsApByProjectIri(projectIri.value, user)
      }
    )

  private val getPermissionsApByProjectAndGroupIriHandler =
    SecuredEndpointAndZioHandler[(IriIdentifier, GroupIri), AdministrativePermissionGetResponseADM](
      permissionsEndpoints.getPermissionsApByProjectAndGroupIri,
      user => { case (projectIri: IriIdentifier, groupIri: GroupIri) =>
        restService.getPermissionsApByProjectAndGroupIri(projectIri.value, groupIri, user)
      }
    )

  private val getPermissionsDaopByProjectIriHandler =
    SecuredEndpointAndZioHandler[IriIdentifier, DefaultObjectAccessPermissionsForProjectGetResponseADM](
      permissionsEndpoints.getPermissionsDoapByProjectIri,
      user => { case (projectIri: IriIdentifier) =>
        restService.getPermissionsDaopByProjectIri(projectIri.value, user)
      }
    )

  private val getPermissionsByProjectIriHandler =
    SecuredEndpointAndZioHandler[IriIdentifier, PermissionsForProjectGetResponseADM](
      permissionsEndpoints.getPermissionsByProjectIri,
      user => { case (projectIri: IriIdentifier) =>
        restService.getPermissionsByProjectIri(projectIri.value, user)
      }
    )

  private val deletePermissionHandler =
    SecuredEndpointAndZioHandler[PermissionIri, PermissionDeleteResponseADM](
      permissionsEndpoints.deletePermission,
      user => { case (permissionIri: PermissionIri) =>
        restService.deletePermission(permissionIri, user)
      }
    )

  private val postPermissionsDoapHandler =
    SecuredEndpointAndZioHandler[
      CreateDefaultObjectAccessPermissionAPIRequestADM,
      DefaultObjectAccessPermissionCreateResponseADM
    ](
      permissionsEndpoints.postPermissionsDoap,
      user => { case (request: CreateDefaultObjectAccessPermissionAPIRequestADM) =>
        restService.createDefaultObjectAccessPermission(request, user)
      }
    )

  private val putPermissionsProjectIriGroupHandler =
    SecuredEndpointAndZioHandler[
      (PermissionIri, ChangePermissionGroupApiRequestADM),
      PermissionGetResponseADM
    ](
      permissionsEndpoints.putPermissionsProjectIriGroup,
      user => { case (permissionIri: PermissionIri, request: ChangePermissionGroupApiRequestADM) =>
        restService.updatePermissionGroup(permissionIri, request, user)
      }
    )

  private val putPermissionsHasPermissionsHandler =
    SecuredEndpointAndZioHandler[
      (PermissionIri, ChangePermissionHasPermissionsApiRequestADM),
      PermissionGetResponseADM
    ](
      permissionsEndpoints.putPerrmissionsHasPermissions,
      user => { case (permissionIri: PermissionIri, request: ChangePermissionHasPermissionsApiRequestADM) =>
        restService.updatePermissionHasPermissions(permissionIri, request, user)
      }
    )

  private val putPermissionsResourceClass =
    SecuredEndpointAndZioHandler[(PermissionIri, ChangePermissionResourceClassApiRequestADM), PermissionGetResponseADM](
      permissionsEndpoints.putPermisssionsResourceClass,
      user => { case (permissionIri: PermissionIri, request: ChangePermissionResourceClassApiRequestADM) =>
        restService.updatePermissionResourceClass(permissionIri, request, user)
      }
    )

  private val putPermissionsProperty =
    SecuredEndpointAndZioHandler[(PermissionIri, ChangePermissionPropertyApiRequestADM), PermissionGetResponseADM](
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
    ).map(mapper.mapEndpointAndHandler(_))

  val allHanders = securedHandlers
}

object PermissionsEndpointsHandlers {

  val layer = ZLayer.derive[PermissionsEndpointsHandlers]
}
