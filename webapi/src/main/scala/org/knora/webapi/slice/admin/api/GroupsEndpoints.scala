/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.*
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.messages.admin.responder.usersmessages.GroupMembersGetResponseADM
import org.knora.webapi.slice.admin.api.AdminPathVariables.groupIriPathVar
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupStatusUpdateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.common.api.BaseEndpoints

final case class GroupsEndpoints(baseEndpoints: BaseEndpoints) {

  private val base = "admin" / "groups"

  private val groupGetResponse =
    jsonBody[GroupGetResponseADM].example(Examples.GroupEndpointsExample.groupGetResponseADM)

  val getGroups = baseEndpoints.publicEndpoint.get
    .in(base)
    .out(jsonBody[GroupsGetResponseADM].example(Examples.GroupEndpointsExample.groupsGetResponseADM))
    .description("Return all groups.")

  val getGroupByIri = baseEndpoints.publicEndpoint.get
    .in(base / groupIriPathVar)
    .out(groupGetResponse)
    .description("Return a single group identified by its IRI.")

  val getGroupMembers = baseEndpoints.securedEndpoint.get
    .in(base / groupIriPathVar / "members")
    .out(jsonBody[GroupMembersGetResponseADM].example(Examples.GroupEndpointsExample.groupGetMembersResponse))
    .description("Return all members of a single group.")

  val postGroup = baseEndpoints.securedEndpoint.post
    .in(base)
    .in(jsonBody[GroupCreateRequest].example(Examples.GroupEndpointsExample.groupCreateRequest))
    .out(groupGetResponse)
    .name("Create new group")
    .description(
      "**Required permissions**: User must SystemAdmin or ProjectAdmin of the project the group is created in.",
    )

  val putGroup = baseEndpoints.securedEndpoint.put
    .in(base / groupIriPathVar)
    .in(jsonBody[GroupUpdateRequest].example(Examples.GroupEndpointsExample.groupUpdateRequest))
    .out(groupGetResponse)
    .description("Update a group's basic information.")

  val putGroupStatus = baseEndpoints.securedEndpoint.put
    .in(base / groupIriPathVar / "status")
    .in(jsonBody[GroupStatusUpdateRequest])
    .out(groupGetResponse)
    .description("Updates a group's status.")

  val deleteGroup = baseEndpoints.securedEndpoint.delete
    .in(base / groupIriPathVar)
    .out(groupGetResponse)
    .description("Deletes a group by changing its status to 'false'.")

  private val securedEndpoints = Seq(getGroupMembers, postGroup, putGroup, putGroupStatus, deleteGroup).map(_.endpoint)

  val endpoints: Seq[AnyEndpoint] = (Seq(getGroups, getGroupByIri) ++ securedEndpoints)
    .map(_.tag("Admin Groups"))
}

object GroupsRequests {
  import org.knora.webapi.slice.admin.api.Codecs.ZioJsonCodec.*
  final case class GroupCreateRequest(
    id: Option[GroupIri] = None,
    name: GroupName,
    descriptions: GroupDescriptions,
    project: ProjectIri,
    status: GroupStatus,
    selfjoin: GroupSelfJoin,
  )
  object GroupCreateRequest {
    implicit val jsonCodec: JsonCodec[GroupCreateRequest] = DeriveJsonCodec.gen[GroupCreateRequest]
  }

  final case class GroupUpdateRequest(
    name: Option[GroupName] = None,
    descriptions: Option[GroupDescriptions] = None,
    status: Option[GroupStatus] = None,
    selfjoin: Option[GroupSelfJoin] = None,
  )
  object GroupUpdateRequest {
    implicit val jsonCodec: JsonCodec[GroupUpdateRequest] = DeriveJsonCodec.gen[GroupUpdateRequest]
  }

  final case class GroupStatusUpdateRequest(
    status: GroupStatus,
  )
  object GroupStatusUpdateRequest {
    implicit val jsonCodec: JsonCodec[GroupStatusUpdateRequest] = DeriveJsonCodec.gen[GroupStatusUpdateRequest]
  }
}

object GroupsEndpoints {
  val layer = ZLayer.derive[GroupsEndpoints]
}
