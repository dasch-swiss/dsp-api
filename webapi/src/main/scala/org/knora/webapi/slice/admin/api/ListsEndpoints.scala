/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody as sprayJsonBody
import sttp.tapir.json.zio.jsonBody as zioJsonBody
import zio.ZLayer
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec

import org.knora.webapi.messages.admin.responder.listsmessages.*
import org.knora.webapi.slice.admin.api.Requests.ListChangeCommentsRequest
import org.knora.webapi.slice.admin.api.Requests.ListChangeLabelsRequest
import org.knora.webapi.slice.admin.api.Requests.ListChangeNameRequest
import org.knora.webapi.slice.admin.api.Requests.ListChangePositionRequest
import org.knora.webapi.slice.admin.api.Requests.ListChangeRequest
import org.knora.webapi.slice.admin.api.Requests.ListCreateChildNodeRequest
import org.knora.webapi.slice.admin.api.Requests.ListCreateRootNodeRequest
import org.knora.webapi.slice.admin.api.model.AdminQueryVariables
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.*
import org.knora.webapi.slice.common.api.BaseEndpoints


case class ListsEndpoints(baseEndpoints: BaseEndpoints) extends ListADMJsonProtocol {
  import org.knora.webapi.slice.admin.api.Codecs.TapirCodec.listIri

  private val base = "admin" / "lists"

  val getListsQueryByProjectIriOption = baseEndpoints.publicEndpoint.get
    .in(base)
    .in(AdminQueryVariables.projectIriOption)
    .out(sprayJsonBody[ListsGetResponseADM].description("Contains the list of all root nodes of each found list."))
    .description("Get all lists or all lists belonging to a project.")

  private val listIriPathVar = path[ListIri].description("The IRI of the list.")
  val getListsByIri = baseEndpoints.publicEndpoint.get
    .in(base / listIriPathVar)
    .out(sprayJsonBody[ListItemGetResponseADM])
    .description("Returns a list node, root or child, with children (if exist).")

  private val getListInfoDesc = "Returns basic information about a list node, root or child, w/o children (if exist)."
  val getListsByIriInfo = baseEndpoints.publicEndpoint.get
    .in(base / listIriPathVar / "info")
    .out(sprayJsonBody[NodeInfoGetResponseADM])
    .description(getListInfoDesc)

  private val getListInfoDeprecation = "*Deprecated*. Use GET admin/lists/<listIri>/info instead. "
  val getListsInfosByIri = baseEndpoints.publicEndpoint.get
    .in(base / "infos" / listIriPathVar)
    .out(sprayJsonBody[NodeInfoGetResponseADM])
    .description(getListInfoDeprecation + getListInfoDesc)
    .deprecated()

  val getListsNodesByIri = baseEndpoints.publicEndpoint.get
    .in(base / "nodes" / listIriPathVar)
    .out(sprayJsonBody[NodeInfoGetResponseADM])
    .description(getListInfoDeprecation + getListInfoDesc)
    .deprecated()

  // Creates
  val postLists = baseEndpoints.securedEndpoint.post
    .in(base)
    .in(zioJsonBody[ListCreateRootNodeRequest])
    .out(sprayJsonBody[ListGetResponseADM])

  val postListsChild = baseEndpoints.securedEndpoint.post
    .in(base / listIriPathVar)
    .in(zioJsonBody[ListCreateChildNodeRequest])
    .out(sprayJsonBody[ChildNodeInfoGetResponseADM])

  // Updates
  val putListsByIriName = baseEndpoints.securedEndpoint.put
    .in(base / listIriPathVar / "name")
    .in(zioJsonBody[ListChangeNameRequest])
    .out(sprayJsonBody[NodeInfoGetResponseADM])

  val putListsByIriLabels = baseEndpoints.securedEndpoint.put
    .in(base / listIriPathVar / "labels")
    .in(zioJsonBody[ListChangeLabelsRequest])
    .out(sprayJsonBody[NodeInfoGetResponseADM])

  val putListsByIriComments = baseEndpoints.securedEndpoint.put
    .in(base / listIriPathVar / "comments")
    .in(zioJsonBody[ListChangeCommentsRequest])
    .out(sprayJsonBody[NodeInfoGetResponseADM])

  val putListsByIriPosistion = baseEndpoints.securedEndpoint.put
    .in(base / listIriPathVar / "position")
    .in(zioJsonBody[ListChangePositionRequest])
    .out(sprayJsonBody[NodePositionChangeResponseADM])

  val putListsByIri = baseEndpoints.securedEndpoint.put
    .in(base / listIriPathVar)
    .in(zioJsonBody[ListChangeRequest])
    .out(sprayJsonBody[NodeInfoGetResponseADM])

  // Deletes
  val deleteListsByIri = baseEndpoints.securedEndpoint.delete
    .in(base / listIriPathVar)
    .out(sprayJsonBody[ListItemDeleteResponseADM])

  val getListsCanDeleteByIri = baseEndpoints.publicEndpoint.get
    .in(base / "candelete" / listIriPathVar)
    .out(sprayJsonBody[CanDeleteListResponseADM])
    .description("Checks if a list can be deleted (none of its nodes is used in data).")

  val deleteListsComment = baseEndpoints.securedEndpoint.delete
    .in(base / "comments" / listIriPathVar)
    .out(sprayJsonBody[ListNodeCommentsDeleteResponseADM])

  private val secured =
    List(
      postLists,
      putListsByIriName,
      putListsByIriLabels,
      putListsByIriComments,
      putListsByIriPosistion,
      putListsByIri,
      deleteListsByIri,
      deleteListsComment
    ).map(_.endpoint)

  private val public = List(getListsQueryByProjectIriOption, getListsByIri, getListsByIriInfo, getListsCanDeleteByIri)

  val endpoints = (secured ++ public).map(_.tag("Admin Lists"))
}

object Requests {
  import Codecs.ZioJsonCodec.*

  sealed trait ListCreateRequest

  case class ListCreateRootNodeRequest(
    id: Option[ListIri],
    comments: Comments,
    labels: Labels,
    name: Option[ListName],
    projectIri: ProjectIri
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
    projectIri: ProjectIri
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
    comments: Option[Comments] = None
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

object ListsEndpoints {
  val layer = ZLayer.derive[ListsEndpoints]
}
