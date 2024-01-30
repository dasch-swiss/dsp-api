/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody as sprayJsonBody
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.listsmessages.ListADMJsonProtocol
import org.knora.webapi.messages.admin.responder.listsmessages.ListItemGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListsGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.NodeInfoGetResponseADM
import org.knora.webapi.slice.admin.api.model.AdminQueryVariables
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.search.api.SearchEndpointsInputs.InputIri

case class ListsEndpoints(baseEndpoints: BaseEndpoints) extends ListADMJsonProtocol {
  private val base = "admin" / "lists"

  val getListsQueryByProjectIriOption = baseEndpoints.publicEndpoint.get
    .in(base)
    .in(AdminQueryVariables.projectIriOption)
    .out(sprayJsonBody[ListsGetResponseADM].description("Contains the list of all root nodes of each found list."))
    .description("Get all lists or all lists belonging to a project.")

  private val listIriPathVar: EndpointInput.PathCapture[InputIri] = path[InputIri].description("The IRI of the list.")
  val getListsByIri = baseEndpoints.publicEndpoint.get
    .in(base / listIriPathVar)
    .out(sprayJsonBody[ListItemGetResponseADM])
    .description("Returns a list node, root or child, with children (if exist).")

  val getListsByIriInfo = baseEndpoints.publicEndpoint.get
    .in(base / listIriPathVar / "info")
    .out(sprayJsonBody[NodeInfoGetResponseADM])
    .description("Returns basic information about a list node, root or child, w/o children (if exist).")

  val getListsInfosByIri = baseEndpoints.publicEndpoint.get
    .in(base / "infos" / listIriPathVar)
    .out(sprayJsonBody[NodeInfoGetResponseADM])
    .description("Returns basic information about a list node, root or child, w/o children (if exist).")

  val getListsNodesByIri = baseEndpoints.publicEndpoint.get
    .in(base / "nodes" / listIriPathVar)
    .out(sprayJsonBody[NodeInfoGetResponseADM])
    .description("Returns basic information about a list node, root or child, w/o children (if exist).")

  val endpoints =
    List(getListsQueryByProjectIriOption, getListsByIri, getListsByIriInfo)
}

object ListsEndpoints {
  val layer = ZLayer.derive[ListsEndpoints]
}
