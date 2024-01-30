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
import org.knora.webapi.slice.admin.api.Codecs.TapirCodec.*
import org.knora.webapi.slice.admin.api.Codecs.ZioJsonCodec.*
import org.knora.webapi.slice.admin.api.Requests.ListPutRequest
import org.knora.webapi.slice.admin.api.model.AdminQueryVariables
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.*
import org.knora.webapi.slice.common.api.BaseEndpoints

case class ListsEndpoints(baseEndpoints: BaseEndpoints) extends ListADMJsonProtocol {
  import org.knora.webapi.slice.admin.api.Codecs.TapirCodec.*

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

  val putListsByIri = baseEndpoints.securedEndpoint.put
    .in(base / listIriPathVar)
    .in(zioJsonBody[ListPutRequest])
    .out(sprayJsonBody[NodeInfoGetResponseADM])

  private val secured = List(putListsByIri).map(_.endpoint)
  private val public  = List(getListsQueryByProjectIriOption, getListsByIri, getListsByIriInfo)

  val endpoints = (secured ++ public).map(_.tag("Admin Lists"))
}

object Requests {
  case class ListPutRequest(
    listIri: ListIri,
    projectIri: ProjectIri,
    hasRootNode: Option[ListIri] = None,
    position: Option[Position] = None,
    name: Option[ListName] = None,
    labels: Option[Labels] = None,
    comments: Option[Comments] = None
  ) {
    def toListNodeChangePayloadADM: ListNodeChangePayloadADM =
      ListNodeChangePayloadADM(listIri, projectIri, hasRootNode, position, name, labels, comments)
  }
  object ListPutRequest {
    import org.knora.webapi.slice.admin.api.Codecs.ZioJsonCodec.*

    def from(l: ListNodeChangePayloadADM): ListPutRequest =
      ListPutRequest(l.listIri, l.projectIri, l.hasRootNode, l.position, l.name, l.labels, l.comments)

    implicit val jsonCodec: JsonCodec[ListPutRequest] = DeriveJsonCodec.gen[ListPutRequest]
  }
}

object ListsEndpoints {
  val layer = ZLayer.derive[ListsEndpoints]
}
