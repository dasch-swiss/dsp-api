/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import org.knora.webapi.messages.admin.responder.listsmessages.{ListADMJsonProtocol, ListItemGetResponseADM, ListsGetResponseADM, NodeInfoGetResponseADM}
import org.knora.webapi.slice.admin.api.model.AdminQueryVariables
import org.knora.webapi.slice.common.api.BaseEndpoints
import org.knora.webapi.slice.search.api.SearchEndpointsInputs.InputIri
import sttp.tapir.*
import sttp.tapir.generic.auto.*
import sttp.tapir.json.spray.jsonBody as sprayJsonBody
import zio.ZLayer

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

  val endpoints =
    List(getListsQueryByProjectIriOption, getListsByIri, getListsByIriInfo)
}

object ListsEndpoints {
  val layer = ZLayer.derive[ListsEndpoints]
}
