/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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

final case class AdminListsEndpointsHandlers(
  private val adminListsEndpoints: AdminListsEndpoints,
  private val adminListsRestService: AdminListRestService,
  private val listsResponder: ListsResponder,
  private val mapper: HandlerMapper,
) {

  private val getListsQueryByProjectIriHandler = PublicEndpointHandler(
    adminListsEndpoints.getListsQueryByProjectIriOption,
    (iriShortcode: Option[Either[ProjectIri, Shortcode]]) => listsResponder.getLists(iriShortcode),
  )

  private val getListsByIriHandler = PublicEndpointHandler(
    adminListsEndpoints.getListsByIri,
    (iri: ListIri) => listsResponder.listGetRequestADM(iri.value),
  )

  private val getListsByIriInfoHandler = PublicEndpointHandler(
    adminListsEndpoints.getListsByIriInfo,
    (iri: ListIri) => listsResponder.listNodeInfoGetRequestADM(iri.value),
  )

  private val getListsInfosByIriHandler = PublicEndpointHandler(
    adminListsEndpoints.getListsInfosByIri,
    (iri: ListIri) => listsResponder.listNodeInfoGetRequestADM(iri.value),
  )

  private val getListsNodesByIriHandler = PublicEndpointHandler(
    adminListsEndpoints.getListsNodesByIri,
    (iri: ListIri) => listsResponder.listNodeInfoGetRequestADM(iri.value),
  )

  // Creates
  private val postListsCreateRootNodeHandler =
    SecuredEndpointHandler[ListCreateRootNodeRequest, ListGetResponseADM](
      adminListsEndpoints.postLists,
      user => req => adminListsRestService.listCreateRootNode(req, user),
    )

  private val postListsCreateChildNodeHandler =
    SecuredEndpointHandler[(ListIri, ListCreateChildNodeRequest), ChildNodeInfoGetResponseADM](
      adminListsEndpoints.postListsChild,
      user => { case (iri: ListIri, req: ListCreateChildNodeRequest) =>
        ZIO
          .fail(BadRequestException("Route and payload parentNodeIri mismatch."))
          .when(iri != req.parentNodeIri) *>
          adminListsRestService.listCreateChildNode(req, user)
      },
    )

  // Updates
  private val putListsByIriNameHandler =
    SecuredEndpointHandler[(ListIri, ListChangeNameRequest), NodeInfoGetResponseADM](
      adminListsEndpoints.putListsByIriName,
      (user: User) => { case (iri: ListIri, newName: ListChangeNameRequest) =>
        adminListsRestService.listChangeName(iri, newName, user)
      },
    )

  private val putListsByIriLabelsHandler =
    SecuredEndpointHandler[(ListIri, ListChangeLabelsRequest), NodeInfoGetResponseADM](
      adminListsEndpoints.putListsByIriLabels,
      (user: User) => { case (iri: ListIri, request: ListChangeLabelsRequest) =>
        adminListsRestService.listChangeLabels(iri, request, user)
      },
    )

  private val putListsByIriCommentsHandler =
    SecuredEndpointHandler[(ListIri, ListChangeCommentsRequest), NodeInfoGetResponseADM](
      adminListsEndpoints.putListsByIriComments,
      (user: User) => { case (iri: ListIri, request: ListChangeCommentsRequest) =>
        adminListsRestService.listChangeComments(iri, request, user)
      },
    )

  private val putListsByIriPositionHandler =
    SecuredEndpointHandler[(ListIri, ListChangePositionRequest), NodePositionChangeResponseADM](
      adminListsEndpoints.putListsByIriPosition,
      (user: User) => { case (iri: ListIri, request: ListChangePositionRequest) =>
        adminListsRestService.nodePositionChangeRequest(iri, request, user)
      },
    )

  private val putListsByIriHandler = SecuredEndpointHandler[(ListIri, ListChangeRequest), NodeInfoGetResponseADM](
    adminListsEndpoints.putListsByIri,
    (user: User) => { case (iri: ListIri, request: ListChangeRequest) =>
      adminListsRestService.listChange(iri, request, user)
    },
  )

  // Deletes
  private val deleteListsByIriHandler = SecuredEndpointHandler[ListIri, ListItemDeleteResponseADM](
    adminListsEndpoints.deleteListsByIri,
    user => listIri => adminListsRestService.deleteListItemRequestADM(listIri, user),
  )

  private val getListsCanDeleteByIriHandler = PublicEndpointHandler[ListIri, CanDeleteListResponseADM](
    adminListsEndpoints.getListsCanDeleteByIri,
    listsResponder.canDeleteListRequestADM,
  )

  private val deleteListsCommentHandler = SecuredEndpointHandler[ListIri, ListNodeCommentsDeleteResponseADM](
    adminListsEndpoints.deleteListsComment,
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

object AdminListsEndpointsHandlers {
  val layer = ZLayer.derive[AdminListsEndpointsHandlers]
}
