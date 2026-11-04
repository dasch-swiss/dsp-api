/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.*
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.messages.admin.responder.usersmessages.GroupMembersGetResponseADM
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.*
import org.knora.webapi.slice.api.admin.AdminPathVariables.groupIriPathVar
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupStatusUpdateRequest
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.Policy

final class GroupsEndpoints(baseEndpoints: BaseEndpoints) {

  private val base = "admin" / "groups"

  private val publicReason      = "Group names and descriptions are public project metadata."
  private val publicRequirement = Policy.Public(publicReason).requirement

  private val groupGetResponse =
    jsonBody[GroupGetResponseADM].example(Examples.GroupEndpointsExample.groupGetResponseADM)

  val getGroups = baseEndpoints
    .publicPolicyEndpoint(publicReason)
    .get
    .in(base)
    .out(jsonBody[GroupsGetResponseADM].example(Examples.GroupEndpointsExample.groupsGetResponseADM))
    .description(s"Return all groups. $publicRequirement")

  val getGroupByIri = baseEndpoints
    .publicPolicyEndpoint(publicReason)
    .get
    .in(base / groupIriPathVar)
    .out(groupGetResponse)
    .description(s"Return a single group identified by its IRI. $publicRequirement")

  val getGroupMembers = baseEndpoints.securedEndpoint.get
    .in(base / groupIriPathVar / "members")
    .out(jsonBody[GroupMembersGetResponseADM].example(Examples.GroupEndpointsExample.groupGetMembersResponse))
    .description(
      "Return all members of a single group. Requires SystemAdmin or ProjectAdmin permissions for the group's project.",
    )

  private val bodyProject = "body project"
  val postGroup           = baseEndpoints
    .projectAdminPolicyEndpoint(bodyProject)
    .post
    .in(base)
    .in(jsonBody[GroupCreateRequest].example(Examples.GroupEndpointsExample.groupCreateRequest))
    .out(groupGetResponse)
    .name("Create new group")
    .description(s"Create new group. ${Policy.ProjectAdmin(bodyProject).requirement}")

  val putGroup = baseEndpoints.securedEndpoint.put
    .in(base / groupIriPathVar)
    .in(jsonBody[GroupUpdateRequest].example(Examples.GroupEndpointsExample.groupUpdateRequest))
    .out(groupGetResponse)
    .description(
      "Update a group's basic information. Requires SystemAdmin or ProjectAdmin permissions for the group's project.",
    )

  val putGroupStatus = baseEndpoints.securedEndpoint.put
    .in(base / groupIriPathVar / "status")
    .in(jsonBody[GroupStatusUpdateRequest])
    .out(groupGetResponse)
    .description("Updates a group's status. Requires SystemAdmin or ProjectAdmin permissions for the group's project.")

  val deleteGroup = baseEndpoints.securedEndpoint.delete
    .in(base / groupIriPathVar)
    .out(groupGetResponse)
    .description(
      "Deletes a group by changing its status to 'false'. Requires SystemAdmin or ProjectAdmin permissions for the group's project.",
    )
}

object GroupsRequests {
  import org.knora.webapi.slice.api.admin.Codecs.ZioJsonCodec.*
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
