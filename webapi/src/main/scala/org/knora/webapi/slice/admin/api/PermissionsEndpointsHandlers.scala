/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.AdministrativePermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateAdministrativePermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.CreateDefaultObjectAccessPermissionAPIRequestADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionCreateResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.DefaultObjectAccessPermissionsForProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionDeleteResponseADM
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
      permissionsEndpoints.getPermissionsDaopByProjectIri,
      user => { case (projectIri: IriIdentifier) =>
        restService.getPermissionsDaopByProjectIri(projectIri.value, user)
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

  private val securedHandlers =
    List(
      postPermissionsApHandler,
      getPermissionsApByProjectIriHandler,
      getPermissionsApByProjectAndGroupIriHandler,
      getPermissionsDaopByProjectIriHandler,
      deletePermissionHandler,
      postPermissionsDoapHandler
    ).map(mapper.mapEndpointAndHandler(_))

  val allHanders = securedHandlers
}

object PermissionsEndpointsHandlers {

  val layer = ZLayer.derive[PermissionsEndpointsHandlers]
}