/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody as sprayJsonBody
import zio.*
import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol.*
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class GroupsEndpoints(baseEndpoints: BaseEndpoints) {
  private val base = "admin" / "groups"
  private val tags = List("Groups", "Admin API")

  val getGroups = baseEndpoints.publicEndpoint.get
    .in(base)
    .out(sprayJsonBody[GroupsGetResponseADM])
    .description("Returns all groups.")
    .tags(tags)

  val getGroup = baseEndpoints.publicEndpoint.get
    .in(base)
    .out(sprayJsonBody[GroupGetResponseADM])
    .description("Returns a single group identified by IRI.")
    .tags(tags)

  val getGroupMembers = baseEndpoints.publicEndpoint.get
    .in(base)
    .out(sprayJsonBody[GroupMembersGetResponseADM])
    .description("Returns all members of a single group.")
    .tags(tags)

  val endpoints: Seq[AnyEndpoint] = Seq(getGroups, getGroup, getGroupMembers)
}

object GroupsEndpoints {
  val layer = ZLayer.derive[GroupsEndpoints]
}
