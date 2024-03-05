/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import zio.Random
import zio.Task
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
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.HandlerMapper
import org.knora.webapi.slice.common.api.PublicEndpointHandler
import org.knora.webapi.slice.common.api.SecuredEndpointHandler

final case class ListRestService(
  auth: AuthorizationRestService,
  listsResponder: ListsResponder,
  projectRepo: KnoraProjectRepo,
) {
  def listChange(iri: ListIri, request: ListChangeRequest, user: User): Task[NodeInfoGetResponseADM] = for {
    _ <- ZIO.fail(BadRequestException("List IRI in path and body must match")).when(iri != request.listIri)
    project <- projectRepo
                 .findById(request.projectIri)
                 .someOrFail(BadRequestException("Project not found"))
    _        <- auth.ensureSystemAdminOrProjectAdmin(user, project)
    uuid     <- Random.nextUUID
    response <- listsResponder.nodeInfoChangeRequest(request, uuid)
  } yield response

  def listChangeName(iri: ListIri, request: ListChangeNameRequest, user: User): Task[NodeInfoGetResponseADM] = for {
    // authorization is currently done in the responder
    uuid     <- Random.nextUUID
    response <- listsResponder.nodeNameChangeRequest(iri, request, user, uuid)
  } yield response

  def listChangeLabels(iri: ListIri, request: ListChangeLabelsRequest, user: User): Task[NodeInfoGetResponseADM] = for {
    // authorization is currently done in the responder
    uuid     <- Random.nextUUID
    response <- listsResponder.nodeLabelsChangeRequest(iri, request, user, uuid)
  } yield response

  def listChangeComments(iri: ListIri, request: ListChangeCommentsRequest, user: User): Task[NodeInfoGetResponseADM] =
    for {
      // authorization is currently done in the responder
      uuid     <- Random.nextUUID
      response <- listsResponder.nodeCommentsChangeRequest(iri, request, user, uuid)
    } yield response

  def nodePositionChangeRequest(
    iri: ListIri,
    request: ListChangePositionRequest,
    user: User,
  ): Task[NodePositionChangeResponseADM] = for {
    // authorization is currently done in the responder
    uuid     <- Random.nextUUID
    response <- listsResponder.nodePositionChangeRequest(iri, request, user, uuid)
  } yield response

  def deleteListItemRequestADM(iri: ListIri, user: User): Task[ListItemDeleteResponseADM] = for {
    // authorization is currently done in the responder
    uuid     <- Random.nextUUID
    response <- listsResponder.deleteListItemRequestADM(iri, user, uuid)
  } yield response

  def listCreateRootNode(req: ListCreateRootNodeRequest, user: User): Task[ListGetResponseADM] = for {
    project <- projectRepo
                 .findById(req.projectIri)
                 .someOrFail(BadRequestException("Project not found"))
    _        <- auth.ensureSystemAdminOrProjectAdmin(user, project)
    uuid     <- Random.nextUUID
    response <- listsResponder.listCreateRootNode(req, uuid)
  } yield response

  def listCreateChildNode(req: ListCreateChildNodeRequest, user: User): Task[ChildNodeInfoGetResponseADM] = for {
    project <- projectRepo
                 .findById(req.projectIri)
                 .someOrFail(BadRequestException("Project not found"))
    _        <- auth.ensureSystemAdminOrProjectAdmin(user, project)
    uuid     <- Random.nextUUID
    response <- listsResponder.listCreateChildNode(req, uuid)
  } yield response

}

object ListRestService {
  val layer = ZLayer.derive[ListRestService]
}

final case class ListsEndpointsHandlers(
  listsEndpoints: ListsEndpoints,
  listsResponder: ListsResponder,
  listRestService: ListRestService,
  mapper: HandlerMapper,
) {

  private val getListsQueryByProjectIriHandler = PublicEndpointHandler(
    listsEndpoints.getListsQueryByProjectIriOption,
    (iri: Option[ProjectIri]) => listsResponder.getLists(iri),
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
