/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import zio.Random
import zio.Task
import zio.ZIO
import zio.ZLayer

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
import org.knora.webapi.slice.api.admin.Requests.*
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.ProjectAdminGrant
import org.knora.webapi.slice.common.api.ProjectCandidate

final class AdminListRestService(
  auth: AuthorizationRestService,
  listsResponder: ListsResponder,
) {

  def getLists(projectIriOpt: Option[Either[ProjectIri, Shortcode]]): Task[ListsGetResponseADM] =
    listsResponder.getLists(projectIriOpt)

  def listGetRequestADM(iri: ListIri): Task[ListItemGetResponseADM] = listsResponder.listGetRequestADM(iri)

  def listNodeInfoGetRequestADM(iri: ListIri): Task[NodeInfoGetResponseADM] =
    listsResponder.listNodeInfoGetRequestADM(iri)

  def listChange(candidate: ProjectCandidate)(iri: ListIri, request: ListChangeRequest): Task[NodeInfoGetResponseADM] =
    for {
      _        <- ZIO.fail(BadRequestException("List IRI in path and body must match")).when(iri != request.listIri)
      grant    <- authorizeForNode(candidate, iri)
      _        <- ensureBodyProjectIsGranted(request.projectIri, grant, iri)
      uuid     <- Random.nextUUID
      response <- listsResponder.nodeInfoChangeRequest(request, grant, uuid)
    } yield response

  def listChangeName(
    candidate: ProjectCandidate,
  )(iri: ListIri, request: ListChangeNameRequest): Task[NodeInfoGetResponseADM] = for {
    grant    <- authorizeForNode(candidate, iri)
    uuid     <- Random.nextUUID
    response <- listsResponder.nodeNameChangeRequest(iri, request, grant, uuid)
  } yield response

  def listChangeLabels(
    candidate: ProjectCandidate,
  )(iri: ListIri, request: ListChangeLabelsRequest): Task[NodeInfoGetResponseADM] = for {
    grant    <- authorizeForNode(candidate, iri)
    uuid     <- Random.nextUUID
    response <- listsResponder.nodeLabelsChangeRequest(iri, request, grant, uuid)
  } yield response

  def listChangeComments(
    candidate: ProjectCandidate,
  )(iri: ListIri, request: ListChangeCommentsRequest): Task[NodeInfoGetResponseADM] = for {
    grant    <- authorizeForNode(candidate, iri)
    uuid     <- Random.nextUUID
    response <- listsResponder.nodeCommentsChangeRequest(iri, request, grant, uuid)
  } yield response

  def nodePositionChangeRequest(candidate: ProjectCandidate)(
    iri: ListIri,
    request: ListChangePositionRequest,
  ): Task[NodePositionChangeResponseADM] = for {
    grant    <- authorizeForNode(candidate, iri)
    uuid     <- Random.nextUUID
    response <- listsResponder.nodePositionChangeRequest(iri, request, grant, uuid)
  } yield response

  def deleteListItemRequestADM(candidate: ProjectCandidate)(iri: ListIri): Task[ListItemDeleteResponseADM] = for {
    grant    <- authorizeForNode(candidate, iri)
    uuid     <- Random.nextUUID
    response <- listsResponder.deleteListItemRequestADM(iri, grant, uuid)
  } yield response

  def listCreateRootNode(candidate: ProjectCandidate)(req: ListCreateRootNodeRequest): Task[ListGetResponseADM] = for {
    grant    <- auth.authorizeProjectAdminById(candidate, req.projectIri)
    uuid     <- Random.nextUUID
    response <- listsResponder.listCreateRootNode(req, grant, uuid)
  } yield response

  def listCreateChildNode(
    candidate: ProjectCandidate,
  )(iri: ListIri, req: ListCreateChildNodeRequest): Task[ChildNodeInfoGetResponseADM] = for {
    _        <- ZIO.fail(BadRequestException("Route and payload parentNodeIri mismatch.")).when(iri != req.parentNodeIri)
    grant    <- authorizeForNode(candidate, iri)
    _        <- ensureBodyProjectIsGranted(req.projectIri, grant, iri)
    uuid     <- Random.nextUUID
    response <- listsResponder.listCreateChildNode(req, grant, uuid)
  } yield response

  def canDeleteListRequestADM(iri: ListIri): Task[CanDeleteListResponseADM] =
    listsResponder.canDeleteList(iri)

  def deleteListNodeCommentsADM(candidate: ProjectCandidate)(iri: ListIri): Task[ListNodeCommentsDeleteResponseADM] =
    for {
      grant    <- authorizeForNode(candidate, iri)
      uuid     <- Random.nextUUID
      response <- listsResponder.deleteListNodeCommentsADM(iri, grant, uuid)
    } yield response

  /** Resolves the node's project first, so a missing node answers 400 before an unauthorized caller gets 403. */
  private def authorizeForNode(candidate: ProjectCandidate, iri: ListIri): Task[ProjectAdminGrant] =
    listsResponder.getProjectIriFromNode(iri).flatMap(auth.authorizeProjectAdminById(candidate, _))

  private def ensureBodyProjectIsGranted(projectIri: ProjectIri, grant: ProjectAdminGrant, iri: ListIri): Task[Unit] =
    ZIO
      .fail(BadRequestException(s"Project IRI in body is not the project of list node ${iri.value}."))
      .when(projectIri != grant.project.id)
      .unit
}

object AdminListRestService {
  val layer = ZLayer.derive[AdminListRestService]
}
