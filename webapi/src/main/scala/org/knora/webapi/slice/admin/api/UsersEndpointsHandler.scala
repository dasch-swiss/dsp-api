/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.messages.admin.responder.usersmessages.UserOperationResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.slice.admin.api.service.UsersRestService
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

case class UsersEndpointsHandler(
  usersEndpoints: UsersEndpoints,
  restService: UsersRestService,
  mapper: HandlerMapper
) {

  private val getUsersHandler =
    SecuredEndpointHandler[Unit, UsersGetResponseADM](
      usersEndpoints.getUsers,
      requestingUser => _ => restService.listAllUsers(requestingUser)
    )

  private val getUserByIriHandler =
    SecuredEndpointHandler[UserIri, UserResponseADM](
      usersEndpoints.getUserByIri,
      requestingUser => { case (userIri: UserIri) => restService.getUserByIri(requestingUser, userIri) }
    )

  private val deleteUserByIriHandler =
    SecuredEndpointHandler[UserIri, UserOperationResponseADM](
      usersEndpoints.deleteUser,
      requestingUser => { case (userIri: UserIri) => restService.deleteUser(requestingUser, userIri) }
    )

  val allHanders =
    List(getUsersHandler, getUserByIriHandler, deleteUserByIriHandler).map(mapper.mapSecuredEndpointHandler(_))
}

object UsersEndpointsHandler {
  val layer = ZLayer.derive[UsersEndpointsHandler]
}
