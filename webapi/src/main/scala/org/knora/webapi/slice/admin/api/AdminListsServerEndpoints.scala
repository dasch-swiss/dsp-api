/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.tapir.ztapir.*
import zio.*

import org.knora.webapi.slice.admin.api.Requests.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User

final case class AdminListsServerEndpoints(
  private val adminListsEndpoints: AdminListsEndpoints,
  private val restService: AdminListRestService,
) {
  val serverEndpoints: List[ZServerEndpoint[Any, Any]] = List(
    adminListsEndpoints.getListsQueryByProjectIriOption.zServerLogic(restService.getLists),
    adminListsEndpoints.getListsByIri.zServerLogic(restService.listGetRequestADM),
    adminListsEndpoints.getListsByIriInfo.zServerLogic(restService.listNodeInfoGetRequestADM),
    adminListsEndpoints.getListsInfosByIri.zServerLogic(restService.listNodeInfoGetRequestADM),
    adminListsEndpoints.getListsNodesByIri.zServerLogic(restService.listNodeInfoGetRequestADM),
    adminListsEndpoints.getListsCanDeleteByIri.zServerLogic(restService.canDeleteListRequestADM),
    adminListsEndpoints.postLists.serverLogic(restService.listCreateRootNode),
    adminListsEndpoints.postListsChild.serverLogic(restService.listCreateChildNode),
    adminListsEndpoints.putListsByIriName.serverLogic(restService.listChangeName),
    adminListsEndpoints.putListsByIriLabels.serverLogic(restService.listChangeLabels),
    adminListsEndpoints.putListsByIriComments.serverLogic(restService.listChangeComments),
    adminListsEndpoints.putListsByIriPosition.serverLogic(restService.nodePositionChangeRequest),
    adminListsEndpoints.putListsByIri.serverLogic(restService.listChange),
    adminListsEndpoints.deleteListsByIri.serverLogic(restService.deleteListItemRequestADM),
    adminListsEndpoints.deleteListsComment.serverLogic(restService.deleteListNodeCommentsADM),
  )
}

object AdminListsServerEndpoints {
  val layer = ZLayer.derive[AdminListsServerEndpoints]
}
