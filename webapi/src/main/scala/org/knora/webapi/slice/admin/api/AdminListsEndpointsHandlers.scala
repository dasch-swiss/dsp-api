/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZIO
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.listsmessages.CanDeleteListResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ChildNodeInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListItemDeleteResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCommentsDeleteResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.NodeInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.NodePositionChangeResponseADM
import org.knora.webapi.slice.admin.api.Requests.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

final case class AdminListsEndpointsHandlers(
  private val adminListsEndpoints: AdminListsEndpoints,
  private val restService: AdminListRestService,
  private val mapper: HandlerMapper,
) {

  private val public = List(
    PublicEndpointHandler(adminListsEndpoints.getListsByIri, restService.listGetRequestADM),
    PublicEndpointHandler(adminListsEndpoints.getListsQueryByProjectIriOption, restService.getLists),
    PublicEndpointHandler(adminListsEndpoints.getListsByIriInfo, restService.listNodeInfoGetRequestADM),
    PublicEndpointHandler(adminListsEndpoints.getListsInfosByIri, restService.listNodeInfoGetRequestADM),
    PublicEndpointHandler(adminListsEndpoints.getListsNodesByIri, restService.listNodeInfoGetRequestADM),
    PublicEndpointHandler(adminListsEndpoints.getListsCanDeleteByIri, restService.canDeleteListRequestADM),
  ).map(mapper.mapPublicEndpointHandler)

  private val secured = List(
    SecuredEndpointHandler(adminListsEndpoints.postLists, restService.listCreateRootNode),
    SecuredEndpointHandler(adminListsEndpoints.postListsChild, restService.listCreateChildNode),
    SecuredEndpointHandler(adminListsEndpoints.putListsByIriName, restService.listChangeName),
    SecuredEndpointHandler(adminListsEndpoints.putListsByIriLabels, restService.listChangeLabels),
    SecuredEndpointHandler(adminListsEndpoints.putListsByIriComments, restService.listChangeComments),
    SecuredEndpointHandler(adminListsEndpoints.putListsByIriPosition, restService.nodePositionChangeRequest),
    SecuredEndpointHandler(adminListsEndpoints.putListsByIri, restService.listChange),
    SecuredEndpointHandler(adminListsEndpoints.deleteListsByIri, restService.deleteListItemRequestADM),
    SecuredEndpointHandler(adminListsEndpoints.deleteListsComment, restService.deleteListNodeCommentsADM),
  ).map(mapper.mapSecuredEndpointHandler)

  val allHandlers = public ++ secured
}

object AdminListsEndpointsHandlers {
  val layer = ZLayer.derive[AdminListsEndpointsHandlers]
}
