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
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.slice.admin.api.service.PermissionsRestService
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
    SecuredEndpointAndZioHandler[
      IriIdentifier,
      AdministrativePermissionsForProjectGetResponseADM
    ](
      permissionsEndpoints.getPermissionsApByProjectIri,
      user => { case (projectIri: IriIdentifier) =>
        restService.getPermissionsApByProjectIri(projectIri.value, user)
      }
    )

  private val getPermissionsApByProjectAndGroupIriHandler =
    SecuredEndpointAndZioHandler[
      (IriIdentifier, String),
      AdministrativePermissionGetResponseADM
    ](
      permissionsEndpoints.getPermissionsApByProjectAndGroupIri,
      user => { case (projectIri: IriIdentifier, groupIri: String) =>
        restService.getPermissionsApByProjectAndGroupIri(projectIri.value, groupIri, user)
      }
    )

  private val securedHandlers =
    List(postPermissionsApHandler, getPermissionsApByProjectIriHandler, getPermissionsApByProjectAndGroupIriHandler)
      .map(mapper.mapEndpointAndHandler(_))

  val allHanders = securedHandlers
}

object PermissionsEndpointsHandlers {

  val layer = ZLayer.derive[PermissionsEndpointsHandlers]
}
