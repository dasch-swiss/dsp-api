/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import zio.Random
import zio.Task
import zio.ZIO
import zio.ZLayer

import scala.annotation.unused

import dsp.errors.BadRequestException
import org.knora.webapi.messages.admin.responder.listsmessages.CanDeleteListResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ChildNodeInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListItemDeleteResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListItemGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCommentsDeleteResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListsGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.NodeInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.NodePositionChangeResponseADM
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.admin.Requests.*
import org.knora.webapi.slice.common.api.AuthorizationRestService

final class AdminListRestService(
  auth: AuthorizationRestService,
  listsResponder: ListsResponder,
  projectService: KnoraProjectService,
) {

  def getLists(projectIriOpt: Option[Either[ProjectIri, Shortcode]]): Task[ListsGetResponseADM] =
    listsResponder.getLists(projectIriOpt)

  def listGetRequestADM(iri: ListIri): Task[ListItemGetResponseADM] = listsResponder.listGetRequestADM(iri)

  def listNodeInfoGetRequestADM(iri: ListIri): Task[NodeInfoGetResponseADM] =
    listsResponder.listNodeInfoGetRequestADM(iri)

  def listChange(user: User)(iri: ListIri, request: ListChangeRequest): Task[NodeInfoGetResponseADM] = for {
    _       <- ZIO.fail(BadRequestException("List IRI in path and body must match")).when(iri != request.listIri)
    project <- projectService
                 .findById(request.projectIri)
                 .someOrFail(BadRequestException("Project not found"))
    _        <- auth.ensureSystemAdminOrProjectAdmin(user, project)
    uuid     <- Random.nextUUID
    response <- listsResponder.nodeInfoChangeRequest(request, uuid)
  } yield response

  def listChangeName(user: User)(iri: ListIri, request: ListChangeNameRequest): Task[NodeInfoGetResponseADM] = for {
    // authorization is currently done in the responder
    uuid     <- Random.nextUUID
    response <- listsResponder.nodeNameChangeRequest(iri, request, user, uuid)
  } yield response

  def listChangeLabels(user: User)(iri: ListIri, request: ListChangeLabelsRequest): Task[NodeInfoGetResponseADM] = for {
    // authorization is currently done in the responder
    uuid     <- Random.nextUUID
    response <- listsResponder.nodeLabelsChangeRequest(iri, request, user, uuid)
  } yield response

  def listChangeComments(user: User)(iri: ListIri, request: ListChangeCommentsRequest): Task[NodeInfoGetResponseADM] =
    for {
      // authorization is currently done in the responder
      uuid     <- Random.nextUUID
      response <- listsResponder.nodeCommentsChangeRequest(iri, request, user, uuid)
    } yield response

  def nodePositionChangeRequest(user: User)(
    iri: ListIri,
    request: ListChangePositionRequest,
  ): Task[NodePositionChangeResponseADM] = for {
    // authorization is currently done in the responder
    uuid     <- Random.nextUUID
    response <- listsResponder.nodePositionChangeRequest(iri, request, user, uuid)
  } yield response

  def deleteListItemRequestADM(user: User)(iri: ListIri): Task[ListItemDeleteResponseADM] = for {
    // authorization is currently done in the responder
    uuid     <- Random.nextUUID
    response <- listsResponder.deleteListItemRequestADM(iri, user, uuid)
  } yield response

  def listCreateRootNode(user: User)(req: ListCreateRootNodeRequest): Task[ListGetResponseADM] = for {
    project <- projectService
                 .findById(req.projectIri)
                 .someOrFail(BadRequestException("Project not found"))
    _        <- auth.ensureSystemAdminOrProjectAdmin(user, project)
    uuid     <- Random.nextUUID
    response <- listsResponder.listCreateRootNode(req, uuid)
  } yield response

  def listCreateChildNode(
    user: User,
  )(iri: ListIri, req: ListCreateChildNodeRequest): Task[ChildNodeInfoGetResponseADM] = for {
    _       <- ZIO.fail(BadRequestException("Route and payload parentNodeIri mismatch.")).when(iri != req.parentNodeIri)
    project <- projectService
                 .findById(req.projectIri)
                 .someOrFail(BadRequestException("Project not found"))
    _        <- auth.ensureSystemAdminOrProjectAdmin(user, project)
    uuid     <- Random.nextUUID
    response <- listsResponder.listCreateChildNode(req, uuid)
  } yield response

  def canDeleteListRequestADM(iri: ListIri): Task[CanDeleteListResponseADM] =
    listsResponder.canDeleteList(iri)

  def deleteListNodeCommentsADM(@unused user: User)(iri: ListIri): Task[ListNodeCommentsDeleteResponseADM] =
    listsResponder.deleteListNodeCommentsADM(iri)
}

object AdminListRestService {
  val layer = ZLayer.derive[AdminListRestService]
}
