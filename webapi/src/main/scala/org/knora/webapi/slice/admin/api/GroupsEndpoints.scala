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
import org.knora.webapi.messages.admin.responder.usersmessages.GroupMembersGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersADMJsonProtocol.*
import org.knora.webapi.slice.admin.api.AdminPathVariables.groupIri
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class GroupsEndpoints(baseEndpoints: BaseEndpoints) {

  private val base = "admin" / "groups"

  val getGroups = baseEndpoints.publicEndpoint.get
    .in(base)
    .out(sprayJsonBody[GroupsGetResponseADM])
    .description("Returns all groups.")

  val getGroupByIri = baseEndpoints.publicEndpoint.get
    .in(base / groupIri)
    .out(sprayJsonBody[GroupGetResponseADM])
    .description("Returns a single group identified by IRI.")

  val getGroupMembers = baseEndpoints.securedEndpoint.get
    .in(base / groupIri / "members")
    .out(sprayJsonBody[GroupMembersGetResponseADM])
    .description("Returns all members of a single group.")

  val postGroup = baseEndpoints.securedEndpoint.post
    .in(base)
    .in(sprayJsonBody[CreateGroupApiRequestADM])
    .out(sprayJsonBody[GroupsGetResponseADM])
    .description("Creates a new group.")

  private val securedEndpoins = Seq(getGroupMembers, postGroup).map(_.endpoint)

  val endpoints: Seq[AnyEndpoint] = (Seq(getGroups, getGroupByIri) ++ securedEndpoins)
    .map(_.tag("Admin Groups"))
}

object GroupsEndpoints {
  val layer = ZLayer.derive[GroupsEndpoints]
}
