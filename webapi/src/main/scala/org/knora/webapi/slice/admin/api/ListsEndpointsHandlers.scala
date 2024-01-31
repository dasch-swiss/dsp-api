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
import org.knora.webapi.messages.admin.responder.listsmessages.NodeInfoGetResponseADM
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.api.Requests.ListChangeCommentsRequest
import org.knora.webapi.slice.admin.api.Requests.ListChangeLabelsRequest
import org.knora.webapi.slice.admin.api.Requests.ListChangeNameRequest
import org.knora.webapi.slice.admin.api.Requests.ListChangeRequest
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
  projectRepo: KnoraProjectRepo
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
}

object ListRestService {
  val layer = ZLayer.derive[ListRestService]
}

final case class ListsEndpointsHandlers(
  listsEndpoints: ListsEndpoints,
  listsResponder: ListsResponder,
  listRestService: ListRestService,
  mapper: HandlerMapper
) {

  private val getListsQueryByProjectIriHandler = PublicEndpointHandler(
    listsEndpoints.getListsQueryByProjectIriOption,
    (iri: Option[ProjectIri]) => listsResponder.getLists(iri)
  )

  private val getListsByIriHandler = PublicEndpointHandler(
    listsEndpoints.getListsByIri,
    (iri: ListIri) => listsResponder.listGetRequestADM(iri.value)
  )

  private val getListsByIriInfoHandler = PublicEndpointHandler(
    listsEndpoints.getListsByIriInfo,
    (iri: ListIri) => listsResponder.listNodeInfoGetRequestADM(iri.value)
  )

  private val getListsInfosByIriHandler = PublicEndpointHandler(
    listsEndpoints.getListsInfosByIri,
    (iri: ListIri) => listsResponder.listNodeInfoGetRequestADM(iri.value)
  )

  private val getListsNodesByIriHandler = PublicEndpointHandler(
    listsEndpoints.getListsNodesByIri,
    (iri: ListIri) => listsResponder.listNodeInfoGetRequestADM(iri.value)
  )

  // Updates
  private val putListsByIriHandler = SecuredEndpointHandler[(ListIri, ListChangeRequest), NodeInfoGetResponseADM](
    listsEndpoints.putListsByIri,
    (user: User) => { case (iri: ListIri, request: ListChangeRequest) =>
      listRestService.listChange(iri, request, user)
    }
  )

  private val putListsByIriNameHandler =
    SecuredEndpointHandler[(ListIri, ListChangeNameRequest), NodeInfoGetResponseADM](
      listsEndpoints.putListsByIriName,
      (user: User) => { case (iri: ListIri, newName: ListChangeNameRequest) =>
        listRestService.listChangeName(iri, newName, user)
      }
    )

  private val putListsByIriLabelsHandler =
    SecuredEndpointHandler[(ListIri, ListChangeLabelsRequest), NodeInfoGetResponseADM](
      listsEndpoints.putListsByIriLabels,
      (user: User) => { case (iri: ListIri, request: ListChangeLabelsRequest) =>
        listRestService.listChangeLabels(iri, request, user)
      }
    )

  private val putListsByIriCommentsHandler =
    SecuredEndpointHandler[(ListIri, ListChangeCommentsRequest), NodeInfoGetResponseADM](
      listsEndpoints.putListsByIriComments,
      (user: User) => { case (iri: ListIri, request: ListChangeCommentsRequest) =>
        listRestService.listChangeComments(iri, request, user)
      }
    )

  private val public = List(
    getListsByIriHandler,
    getListsQueryByProjectIriHandler,
    getListsByIriInfoHandler,
    getListsInfosByIriHandler,
    getListsNodesByIriHandler
  ).map(mapper.mapPublicEndpointHandler(_))

  private val secured = List(
    putListsByIriHandler,
    putListsByIriNameHandler,
    putListsByIriLabelsHandler,
    putListsByIriCommentsHandler
  ).map(mapper.mapSecuredEndpointHandler(_))

  val allHandlers = public ++ secured
}

object ListsEndpointsHandlers {
  val layer = ZLayer.derive[ListsEndpointsHandlers]
}
