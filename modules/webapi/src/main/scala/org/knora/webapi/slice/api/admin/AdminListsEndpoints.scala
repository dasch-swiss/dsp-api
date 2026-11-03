/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.zio.jsonBody
import zio.ZLayer
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.messages.admin.responder.listsmessages.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.*
import org.knora.webapi.slice.api.admin.Requests.ListChangeCommentsRequest
import org.knora.webapi.slice.api.admin.Requests.ListChangeLabelsRequest
import org.knora.webapi.slice.api.admin.Requests.ListChangeNameRequest
import org.knora.webapi.slice.api.admin.Requests.ListChangePositionRequest
import org.knora.webapi.slice.api.admin.Requests.ListChangeRequest
import org.knora.webapi.slice.api.admin.Requests.ListCreateChildNodeRequest
import org.knora.webapi.slice.api.admin.Requests.ListCreateRootNodeRequest
import org.knora.webapi.slice.api.admin.model.AdminQueryVariables
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.common.api.Policy

final class AdminListsEndpoints(baseEndpoints: BaseEndpoints) {

  private val base = "admin" / "lists"

  private val readReason        = "Lists are controlled vocabularies of a project's data model; anyone may read them."
  private val publicRequirement = Policy.Public(readReason).requirement

  val getListsQueryByProjectIriOption = baseEndpoints
    .publicPolicyEndpoint(readReason)
    .get
    .in(base)
    .in(AdminQueryVariables.projectIriOrShortcodeQueryOption)
    .out(jsonBody[ListsGetResponseADM].description("Contains the list of all root nodes of each found list."))
    .description(
      "Get all lists or all lists belonging to a project. " +
        "Note that you can provide either a project IRI or a project shortcode. " +
        publicRequirement,
    )

  private val listIriPathVar = path[ListIri].description("The IRI of the list.")
  val getListsByIri          = baseEndpoints
    .publicPolicyEndpoint(readReason)
    .get
    .in(base / listIriPathVar)
    .out(jsonBody[ListItemGetResponseADM])
    .description(s"Returns a list node, root or child, with children (if exist). $publicRequirement")

  private val getListInfoDesc =
    s"Returns basic information about a list node, root or child, w/o children (if exist). $publicRequirement"
  val getListsByIriInfo = baseEndpoints
    .publicPolicyEndpoint(readReason)
    .get
    .in(base / listIriPathVar / "info")
    .out(jsonBody[NodeInfoGetResponseADM])
    .description(getListInfoDesc)

  private val getListInfoDeprecation = "*Deprecated*. Use GET admin/lists/<listIri>/info instead. "
  val getListsInfosByIri             = baseEndpoints
    .publicPolicyEndpoint(readReason)
    .get
    .in(base / "infos" / listIriPathVar)
    .out(jsonBody[NodeInfoGetResponseADM])
    .description(getListInfoDeprecation + getListInfoDesc)
    .deprecated()

  val getListsNodesByIri = baseEndpoints
    .publicPolicyEndpoint(readReason)
    .get
    .in(base / "nodes" / listIriPathVar)
    .out(jsonBody[NodeInfoGetResponseADM])
    .description(getListInfoDeprecation + getListInfoDesc)
    .deprecated()

  private val listNode            = "list node"
  private val listNodeRequirement = Policy.ProjectAdmin(listNode).requirement

  // Creates
  private val bodyProjectIri = "body projectIri"
  val postLists              = baseEndpoints
    .projectAdminPolicyEndpoint(bodyProjectIri)
    .post
    .in(base)
    .in(jsonBody[ListCreateRootNodeRequest])
    .out(jsonBody[ListGetResponseADM])
    .description(
      s"Creates a new list by creating the root node of the list. ${Policy.ProjectAdmin(bodyProjectIri).requirement}",
    )

  private val parentListNode = "parent list node"
  val postListsChild         = baseEndpoints
    .projectAdminPolicyEndpoint(parentListNode)
    .post
    .in(base / listIriPathVar)
    .in(jsonBody[ListCreateChildNodeRequest])
    .out(jsonBody[ChildNodeInfoGetResponseADM])
    .description(
      s"Creates a new list node as a child of the specified list node. ${Policy.ProjectAdmin(parentListNode).requirement}",
    )

  // Updates
  val putListsByIriName = baseEndpoints
    .projectAdminPolicyEndpoint(listNode)
    .put
    .in(base / listIriPathVar / "name")
    .in(jsonBody[ListChangeNameRequest])
    .out(jsonBody[NodeInfoGetResponseADM])
    .description(s"Update a list node's name. $listNodeRequirement")

  val putListsByIriLabels = baseEndpoints
    .projectAdminPolicyEndpoint(listNode)
    .put
    .in(base / listIriPathVar / "labels")
    .in(jsonBody[ListChangeLabelsRequest])
    .out(jsonBody[NodeInfoGetResponseADM])
    .description(
      s"Update a list node's labels. $listNodeRequirement",
    )

  val putListsByIriComments = baseEndpoints
    .projectAdminPolicyEndpoint(listNode)
    .put
    .in(base / listIriPathVar / "comments")
    .in(jsonBody[ListChangeCommentsRequest])
    .out(jsonBody[NodeInfoGetResponseADM])
    .description(
      s"Update a list node's comments. $listNodeRequirement",
    )

  val putListsByIriPosition = baseEndpoints
    .projectAdminPolicyEndpoint(listNode)
    .put
    .in(base / listIriPathVar / "position")
    .in(jsonBody[ListChangePositionRequest])
    .out(jsonBody[NodePositionChangeResponseADM])
    .description(
      s"Update a list node's position. $listNodeRequirement",
    )

  val putListsByIri = baseEndpoints
    .projectAdminPolicyEndpoint(listNode)
    .put
    .in(base / listIriPathVar)
    .in(jsonBody[ListChangeRequest])
    .out(jsonBody[NodeInfoGetResponseADM])
    .description(
      s"Update a list node's properties. $listNodeRequirement",
    )

  // Deletes
  val deleteListsByIri = baseEndpoints
    .projectAdminPolicyEndpoint(listNode)
    .delete
    .in(base / listIriPathVar)
    .out(jsonBody[ListItemDeleteResponseADM])
    .description(s"Delete a list node. $listNodeRequirement")

  val getListsCanDeleteByIri = baseEndpoints
    .publicPolicyEndpoint("Answers only whether a list is in use, never which data uses it.")
    .get
    .in(base / "candelete" / listIriPathVar)
    .out(jsonBody[CanDeleteListResponseADM])
    .description(s"Checks if a list can be deleted (none of its nodes is used in data). $publicRequirement")

  val deleteListsComment = baseEndpoints
    .projectAdminPolicyEndpoint(listNode)
    .delete
    .in(base / "comments" / listIriPathVar)
    .out(jsonBody[ListNodeCommentsDeleteResponseADM])
    .description(
      s"Delete a list node's comments. $listNodeRequirement",
    )
}

object Requests {
  import Codecs.ZioJsonCodec.*

  sealed trait ListCreateRequest

  case class ListCreateRootNodeRequest(
    id: Option[ListIri],
    comments: Comments,
    labels: Labels,
    name: Option[ListName],
    projectIri: ProjectIri,
  ) extends ListCreateRequest
  object ListCreateRootNodeRequest {
    implicit val jsonCodec: JsonCodec[ListCreateRootNodeRequest] = DeriveJsonCodec.gen[ListCreateRootNodeRequest]
  }

  case class ListCreateChildNodeRequest(
    id: Option[ListIri],
    comments: Option[Comments],
    labels: Labels,
    name: Option[ListName],
    parentNodeIri: ListIri,
    position: Option[Position],
    projectIri: ProjectIri,
  ) extends ListCreateRequest
  object ListCreateChildNodeRequest {
    implicit val jsonCodec: JsonCodec[ListCreateChildNodeRequest] = DeriveJsonCodec.gen[ListCreateChildNodeRequest]
  }

  case class ListChangeRequest(
    listIri: ListIri,
    projectIri: ProjectIri,
    hasRootNode: Option[ListIri] = None,
    position: Option[Position] = None,
    name: Option[ListName] = None,
    labels: Option[Labels] = None,
    comments: Option[Comments] = None,
  )
  object ListChangeRequest {
    implicit val jsonCodec: JsonCodec[ListChangeRequest] = DeriveJsonCodec.gen[ListChangeRequest]
  }

  final case class ListChangeNameRequest(name: ListName)
  object ListChangeNameRequest {
    implicit val jsonCodec: JsonCodec[ListChangeNameRequest] = DeriveJsonCodec.gen[ListChangeNameRequest]
  }

  final case class ListChangeLabelsRequest(labels: Labels)
  object ListChangeLabelsRequest {
    implicit val jsonCodec: JsonCodec[ListChangeLabelsRequest] = DeriveJsonCodec.gen[ListChangeLabelsRequest]
  }

  final case class ListChangeCommentsRequest(comments: Comments)
  object ListChangeCommentsRequest {
    implicit val jsonCodec: JsonCodec[ListChangeCommentsRequest] = DeriveJsonCodec.gen[ListChangeCommentsRequest]
  }

  final case class ListChangePositionRequest(position: Position, parentNodeIri: ListIri)
  object ListChangePositionRequest {
    implicit val jsonCodec: JsonCodec[ListChangePositionRequest] = DeriveJsonCodec.gen[ListChangePositionRequest]
  }
}

object AdminListsEndpoints {
  val layer = ZLayer.derive[AdminListsEndpoints]
}
