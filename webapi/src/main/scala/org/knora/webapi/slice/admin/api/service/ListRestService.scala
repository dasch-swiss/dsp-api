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
import org.knora.webapi.messages.admin.responder.listsmessages.ChildNodeInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListItemDeleteResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.NodeInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.NodePositionChangeResponseADM
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.api.Requests.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.api.AuthorizationRestService

final case class ListRestService(
  auth: AuthorizationRestService,
  listsResponder: ListsResponder,
  knoraProjectService: KnoraProjectService,
) {
  def listChange(iri: ListIri, request: ListChangeRequest, user: User): Task[NodeInfoGetResponseADM] = for {
    _ <- ZIO.fail(BadRequestException("List IRI in path and body must match")).when(iri != request.listIri)
    project <- knoraProjectService
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
    project <- knoraProjectService
                 .findById(req.projectIri)
                 .someOrFail(BadRequestException("Project not found"))
    _        <- auth.ensureSystemAdminOrProjectAdmin(user, project)
    uuid     <- Random.nextUUID
    response <- listsResponder.listCreateRootNode(req, uuid)
  } yield response

  def listCreateChildNode(req: ListCreateChildNodeRequest, user: User): Task[ChildNodeInfoGetResponseADM] = for {
    project <- knoraProjectService
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
