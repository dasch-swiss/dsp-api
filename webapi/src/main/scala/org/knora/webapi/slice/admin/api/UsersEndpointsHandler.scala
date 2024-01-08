/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZLayer

import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.slice.admin.api.service.UsersADMRestService
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.SecuredEndpointAndZioHandler

case class UsersEndpointsHandler(
  usersEndpoints: UsersEndpoints,
  restService: UsersADMRestService,
  mapper: HandlerMapper
) {

  val getUsersHandler =
    SecuredEndpointAndZioHandler[
      Unit,
      UsersGetResponseADM
    ](usersEndpoints.getUsers, user => { case (_: Unit) => restService.listAllUsers(user) })

//  private val handlers        = List().map(mapper.mapEndpointAndHandler)
  private val securedHandlers = List(getUsersHandler).map(mapper.mapEndpointAndHandler(_))

  val allHanders = /* handlers ++ */ securedHandlers
}

object UsersEndpointsHandler {
  val layer = ZLayer.derive[UsersEndpointsHandler]
}
