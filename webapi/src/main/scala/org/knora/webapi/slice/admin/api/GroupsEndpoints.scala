/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol.groupsGetResponseADMFormat
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody as sprayJsonBody
import zio.*
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class GroupsEndpoints(baseEndpoints: BaseEndpoints) {
  private val path = "admin" / "groups"
  private val tags = List("Groups", "Admin API")

  val getGroups = baseEndpoints.publicEndpoint.get
    .in(path)
    .out(sprayJsonBody[GroupsGetResponseADM])
    .description("Returns all groups.")
    .tags(tags)
}

object GroupsEndpoints {
  val layer = ZLayer.derive[GroupsEndpoints]
}
