/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.ZIO
import zio.ZLayer

import dsp.errors.BadRequestException
import org.knora.webapi.messages.admin.responder.listsmessages.CanDeleteListResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ChildNodeInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListItemDeleteResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCommentsDeleteResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.NodeInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.NodePositionChangeResponseADM
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.api.Requests.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

final case class ListsEndpointsHandlers(
  listsEndpoints: ListsEndpoints,
  listsResponder: ListsResponder,
  listRestService: ListRestService,
  mapper: HandlerMapper,
) {

  private val getListsQueryByProjectIriHandler = PublicEndpointHandler(
    listsEndpoints.getListsQueryByProjectIriOption,
    (iriShortcode: Option[Either[ProjectIri, Shortcode]]) => listsResponder.getLists(iriShortcode),
  )

  private val getListsByIriHandler = PublicEndpointHandler(
    listsEndpoints.getListsByIri,
    (iri: ListIri) => listsResponder.listGetRequestADM(iri.value),
  )

  private val getListsByIriInfoHandler = PublicEndpointHandler(
    listsEndpoints.getListsByIriInfo,
    (iri: ListIri) => listsResponder.listNodeInfoGetRequestADM(iri.value),
  )

  private val getListsInfosByIriHandler = PublicEndpointHandler(
    listsEndpoints.getListsInfosByIri,
    (iri: ListIri) => listsResponder.listNodeInfoGetRequestADM(iri.value),
  )

  private val getListsNodesByIriHandler = PublicEndpointHandler(
    listsEndpoints.getListsNodesByIri,
    (iri: ListIri) => listsResponder.listNodeInfoGetRequestADM(iri.value),
  )

  // Creates
  private val postListsCreateRootNodeHandler =
    SecuredEndpointHandler[ListCreateRootNodeRequest, ListGetResponseADM](
      listsEndpoints.postLists,
      user => req => listRestService.listCreateRootNode(req, user),
    )

  private val postListsCreateChildNodeHandler =
    SecuredEndpointHandler[(ListIri, ListCreateChildNodeRequest), ChildNodeInfoGetResponseADM](
      listsEndpoints.postListsChild,
      user => { case (iri: ListIri, req: ListCreateChildNodeRequest) =>
        ZIO
          .fail(BadRequestException("Route and payload parentNodeIri mismatch."))
          .when(iri != req.parentNodeIri) *>
          listRestService.listCreateChildNode(req, user)
      },
    )

  // Updates
  private val putListsByIriNameHandler =
    SecuredEndpointHandler[(ListIri, ListChangeNameRequest), NodeInfoGetResponseADM](
      listsEndpoints.putListsByIriName,
      (user: User) => { case (iri: ListIri, newName: ListChangeNameRequest) =>
        listRestService.listChangeName(iri, newName, user)
      },
    )

  private val putListsByIriLabelsHandler =
    SecuredEndpointHandler[(ListIri, ListChangeLabelsRequest), NodeInfoGetResponseADM](
      listsEndpoints.putListsByIriLabels,
      (user: User) => { case (iri: ListIri, request: ListChangeLabelsRequest) =>
        listRestService.listChangeLabels(iri, request, user)
      },
    )

  private val putListsByIriCommentsHandler =
    SecuredEndpointHandler[(ListIri, ListChangeCommentsRequest), NodeInfoGetResponseADM](
      listsEndpoints.putListsByIriComments,
      (user: User) => { case (iri: ListIri, request: ListChangeCommentsRequest) =>
        listRestService.listChangeComments(iri, request, user)
      },
    )

  private val putListsByIriPositionHandler =
    SecuredEndpointHandler[(ListIri, ListChangePositionRequest), NodePositionChangeResponseADM](
      listsEndpoints.putListsByIriPosistion,
      (user: User) => { case (iri: ListIri, request: ListChangePositionRequest) =>
        listRestService.nodePositionChangeRequest(iri, request, user)
      },
    )

  private val putListsByIriHandler = SecuredEndpointHandler[(ListIri, ListChangeRequest), NodeInfoGetResponseADM](
    listsEndpoints.putListsByIri,
    (user: User) => { case (iri: ListIri, request: ListChangeRequest) =>
      listRestService.listChange(iri, request, user)
    },
  )

  // Deletes
  private val deleteListsByIriHandler = SecuredEndpointHandler[ListIri, ListItemDeleteResponseADM](
    listsEndpoints.deleteListsByIri,
    user => listIri => listRestService.deleteListItemRequestADM(listIri, user),
  )

  private val getListsCanDeleteByIriHandler = PublicEndpointHandler[ListIri, CanDeleteListResponseADM](
    listsEndpoints.getListsCanDeleteByIri,
    listsResponder.canDeleteListRequestADM,
  )

  private val deleteListsCommentHandler = SecuredEndpointHandler[ListIri, ListNodeCommentsDeleteResponseADM](
    listsEndpoints.deleteListsComment,
    _ => listIri => listsResponder.deleteListNodeCommentsADM(listIri),
  )

  private val public = List(
    getListsByIriHandler,
    getListsQueryByProjectIriHandler,
    getListsByIriInfoHandler,
    getListsInfosByIriHandler,
    getListsNodesByIriHandler,
    getListsCanDeleteByIriHandler,
  ).map(mapper.mapPublicEndpointHandler(_))

  private val secured = List(
    postListsCreateRootNodeHandler,
    postListsCreateChildNodeHandler,
    putListsByIriNameHandler,
    putListsByIriLabelsHandler,
    putListsByIriCommentsHandler,
    putListsByIriPositionHandler,
    putListsByIriHandler,
    deleteListsByIriHandler,
    deleteListsCommentHandler,
  ).map(mapper.mapSecuredEndpointHandler(_))

  val allHandlers = public ++ secured
}

object ListsEndpointsHandlers {
  val layer = ZLayer.derive[ListsEndpointsHandlers]
}
