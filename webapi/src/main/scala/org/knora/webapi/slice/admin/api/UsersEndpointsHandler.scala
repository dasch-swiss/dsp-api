/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.messages.admin.responder.usersmessages.UserOperationResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.slice.admin.api.service.UsersADMRestService
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointAndZioHandler

case class UsersEndpointsHandler(
  usersEndpoints: UsersEndpoints,
  restService: UsersADMRestService,
  mapper: HandlerMapper
) {

  private val getUsersHandler =
    SecuredEndpointAndZioHandler[
      Unit,
      UsersGetResponseADM
    ](
      usersEndpoints.getUsers,
      requestingUser => _ => restService.listAllUsers(requestingUser)
    )

  private val deleteUserByIriHandler =
    SecuredEndpointAndZioHandler[UserIri, UserOperationResponseADM](
      usersEndpoints.deleteUser,
      requestingUser => { case (userIri: UserIri) => restService.deleteUser(requestingUser, userIri) }
    )

  val allHanders = List(getUsersHandler, deleteUserByIriHandler).map(mapper.mapEndpointAndHandler(_))
}

object UsersEndpointsHandler {
  val layer = ZLayer.derive[UsersEndpointsHandler]
}
