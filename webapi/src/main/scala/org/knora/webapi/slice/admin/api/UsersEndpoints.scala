/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol.*
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.slice.common.api.BaseEndpoints
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody as sprayJsonBody
import zio.*

final case class UsersEndpoints(baseEndpoints: BaseEndpoints) {

  private val projectsBase = "admin" / "users"

  private val tags = List("Users", "Admin API")

  val getUsers = baseEndpoints.securedEndpoint.get
    .in(projectsBase)
    .out(sprayJsonBody[UsersGetResponseADM])
    .description("Returns all users.")
    .tags(tags)

  val endpoints: Seq[AnyEndpoint] = Seq(getUsers).map(_.endpoint)
}

object UsersEndpoints {
  val layer = ZLayer.derive[UsersEndpoints]
}
