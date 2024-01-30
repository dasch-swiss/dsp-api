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
import org.knora.webapi.slice.admin.api.Requests.ListPutRequest
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
  def listPutRequestADM(iri: ListIri, request: ListPutRequest, user: User): Task[NodeInfoGetResponseADM] =
    for {
      _ <- ZIO.fail(BadRequestException("List IRI in path and body must match")).when(iri != request.listIri)
      project <- projectRepo
                   .findById(request.projectIri)
                   .someOrFail(BadRequestException("Project not found"))
      _        <- auth.ensureSystemAdminOrProjectAdmin(user, project)
      uuid     <- Random.nextUUID
      response <- listsResponder.nodeInfoChangeRequest(request, uuid)
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

  private val putListsByIriHandler = SecuredEndpointHandler[(ListIri, ListPutRequest), NodeInfoGetResponseADM](
    listsEndpoints.putListsByIri,
    (user: User) => { case (iri: ListIri, request: ListPutRequest) =>
      listRestService.listPutRequestADM(iri, request, user)
    }
  )

  private val public = List(
    getListsByIriHandler,
    getListsQueryByProjectIriHandler,
    getListsByIriInfoHandler,
    getListsInfosByIriHandler,
    getListsNodesByIriHandler
  ).map(mapper.mapPublicEndpointHandler(_))

  private val secured = List(putListsByIriHandler).map(mapper.mapSecuredEndpointHandler(_))

  val allHandlers = public ++ secured
}

object ListsEndpointsHandlers {
  val layer = ZLayer.derive[ListsEndpointsHandlers]
}
