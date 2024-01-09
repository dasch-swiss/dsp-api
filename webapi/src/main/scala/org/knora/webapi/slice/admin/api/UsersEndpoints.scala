/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody as sprayJsonBody
import zio.*

import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol.*
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class UsersEndpoints(baseEndpoints: BaseEndpoints) {
  private val base = "admin" / "users"
  private val tags = List("Users", "Admin API")

  val getUsers = baseEndpoints.securedEndpoint.get
    .in(base)
    .out(sprayJsonBody[UsersGetResponseADM])
    .description("Returns all users.")
    .tags(tags)
}

object UsersEndpoints {
  val layer = ZLayer.derive[UsersEndpoints]
}
