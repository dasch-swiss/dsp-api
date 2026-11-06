/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

import zio.*

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.messages.admin.responder.usersmessages.GroupMembersGetResponseADM
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.service.GroupService
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupStatusUpdateRequest
import org.knora.webapi.slice.api.admin.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.common.api.ProjectCandidate

final case class GroupRestService(
  private val auth: AuthorizationRestService,
  private val format: KnoraResponseRenderer,
  private val groupService: GroupService,
  private val knoraGroupService: KnoraGroupService,
  private val userService: UserService,
) {

  def getGroups: Task[GroupsGetResponseADM] = for {
    internal <- groupService.findAllRegularGroups.orDie
    external <- format.toExternal(GroupsGetResponseADM(internal))
  } yield external

  def getGroupByIri(iri: GroupIri): Task[GroupGetResponseADM] =
    for {
      internal <- groupService
                    .findById(iri)
                    .someOrFail(NotFoundException(s"Group <${iri.value}> not found."))
                    .map(GroupGetResponseADM.apply)
      external <- format.toExternal(internal)
    } yield external

  def getGroupMembers(candidate: ProjectCandidate)(iri: GroupIri): Task[GroupMembersGetResponseADM] =
    for {
      _        <- auth.authorizeProjectAdminOfGroup(candidate, iri)
      internal <- userService.findByGroupMembership(iri).map(GroupMembersGetResponseADM.from)
      external <- format.toExternal(internal)
    } yield external

  def postGroup(candidate: ProjectCandidate)(request: GroupCreateRequest): Task[GroupGetResponseADM] =
    for {
      grant    <- auth.authorizeProjectAdminById(candidate, request.project)
      internal <- groupService.createGroup(request, grant.project).map(GroupGetResponseADM.apply)
      external <- format.toExternal(internal)
    } yield external

  def putGroup(candidate: ProjectCandidate)(iri: GroupIri, request: GroupUpdateRequest): Task[GroupGetResponseADM] =
    for {
      groupAndGrant <- auth.authorizeProjectAdminOfGroup(candidate, iri)
      (group, _)     = groupAndGrant
      _             <- ZIO
             .fail(BadRequestException("No data would be changed. Aborting update request."))
             .when(List(request.name, request.descriptions, request.status, request.selfjoin).flatten.isEmpty)
      groupToUpdate <- groupService.toGroup(group)
      internal      <- groupService.updateGroup(groupToUpdate, request).map(GroupGetResponseADM.apply)
      external      <- format.toExternal(internal)
    } yield external

  def putGroupStatus(
    candidate: ProjectCandidate,
  )(iri: GroupIri, request: GroupStatusUpdateRequest): Task[GroupGetResponseADM] =
    updateStatus(iri, request.status, candidate)

  def deleteGroup(candidate: ProjectCandidate)(iri: GroupIri): Task[GroupGetResponseADM] =
    updateStatus(iri, GroupStatus.inactive, candidate)

  private def updateStatus(iri: GroupIri, status: GroupStatus, candidate: ProjectCandidate) =
    for {
      groupAndGrant <- auth.authorizeProjectAdminOfGroup(candidate, iri)
      (group, _)     = groupAndGrant
      updated       <- knoraGroupService.updateGroupStatus(group, status)
      internal      <- groupService.toGroup(updated).map(GroupGetResponseADM.apply)
      external      <- format.toExternal(internal)
    } yield external
}

object GroupRestService {
  val layer = ZLayer.derive[GroupRestService]
}
