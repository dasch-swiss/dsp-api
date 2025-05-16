/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.capabilities.zio.ZioStreams
import sttp.tapir.ztapir.*
import zio.ZIO
import zio.ZLayer

import dsp.errors.BadRequestException
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.api.Requests.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User

final case class ListsServerEndpoints(
  private val listsEndpoints: ListsEndpoints,
  private val listsResponder: ListsResponder,
  private val listRestService: ListRestService,
) {

  val serverEndpoints: List[ZServerEndpoint[Any, ZioStreams]] = List(
    listsEndpoints.getListsQueryByProjectIriOption
      .zServerLogic(listsResponder.getLists),
    listsEndpoints.getListsByIri
      .zServerLogic((iri: ListIri) => listsResponder.listGetRequestADM(iri.value)),
    listsEndpoints.getListsByIriInfo
      .zServerLogic((iri: ListIri) => listsResponder.listNodeInfoGetRequestADM(iri.value)),
    listsEndpoints.getListsInfosByIri
      .zServerLogic((iri: ListIri) => listsResponder.listNodeInfoGetRequestADM(iri.value)),
    listsEndpoints.getListsNodesByIri
      .zServerLogic((iri: ListIri) => listsResponder.listNodeInfoGetRequestADM(iri.value)),
    listsEndpoints.postLists
      .serverLogic(listRestService.listCreateRootNode),
    listsEndpoints.postListsChild
      .serverLogic(user => { case (iri: ListIri, req: ListCreateChildNodeRequest) =>
        ZIO
          .fail(BadRequestException("Route and payload parentNodeIri mismatch."))
          .when(iri != req.parentNodeIri) *>
          listRestService.listCreateChildNode(user)(req)
      }),
    listsEndpoints.putListsByIriName
      .serverLogic(listRestService.listChangeName),
    listsEndpoints.putListsByIriLabels
      .serverLogic(listRestService.listChangeLabels),
    listsEndpoints.putListsByIriComments
      .serverLogic(listRestService.listChangeComments),
    listsEndpoints.putListsByIriPosistion
      .serverLogic(listRestService.nodePositionChangeRequest),
    listsEndpoints.putListsByIri
      .serverLogic(listRestService.listChange),
    listsEndpoints.deleteListsByIri
      .serverLogic(listRestService.deleteListItemRequestADM),
    listsEndpoints.getListsCanDeleteByIri
      .zServerLogic(listsResponder.canDeleteListRequestADM),
    listsEndpoints.deleteListsComment
      .serverLogic(listsResponder.deleteListNodeCommentsADM),
  )
}

object ListsServerEndpoints {
  val layer = ZLayer.derive[ListsServerEndpoints]
}
