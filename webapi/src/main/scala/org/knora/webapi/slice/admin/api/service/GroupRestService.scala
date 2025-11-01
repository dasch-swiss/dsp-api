/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.*

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.messages.admin.responder.usersmessages.GroupMembersGetResponseADM
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupStatusUpdateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.GroupStatus
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.GroupService
import org.knora.webapi.slice.admin.domain.service.KnoraGroupService
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer

final case class GroupRestService(
  private val auth: AuthorizationRestService,
  private val format: KnoraResponseRenderer,
  private val groupService: GroupService,
  private val knoraGroupService: KnoraGroupService,
  private val knoraProjectService: KnoraProjectService,
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

  def getGroupMembers(user: User)(iri: GroupIri): Task[GroupMembersGetResponseADM] =
    for {
      _ <- auth.ensureSystemAdminOrProjectAdminOfGroup(user, iri)
      _ <- groupService
             .findById(iri)
             .someOrFail(NotFoundException(s"Group <${iri.value}> not found."))
      internal <- userService.findByGroupMembership(iri).map(GroupMembersGetResponseADM.from)
      external <- format.toExternal(internal)
    } yield external

  def postGroup(user: User)(request: GroupCreateRequest): Task[GroupGetResponseADM] =
    for {
      _       <- auth.ensureSystemAdminOrProjectAdminById(user, request.project)
      project <- knoraProjectService
                   .findById(request.project)
                   .someOrFail(NotFoundException(s"Project <${request.project}> not found."))
      internal <- groupService.createGroup(request, project).map(GroupGetResponseADM.apply)
      external <- format.toExternal(internal)
    } yield external

  def putGroup(user: User)(iri: GroupIri, request: GroupUpdateRequest): Task[GroupGetResponseADM] =
    for {
      _ <- auth.ensureSystemAdminOrProjectAdminOfGroup(user, iri)
      _ <- ZIO
             .fail(BadRequestException("No data would be changed. Aborting update request."))
             .when(List(request.name, request.descriptions, request.status, request.selfjoin).flatten.isEmpty)
      groupToUpdate <- groupService
                         .findById(iri)
                         .someOrFail(NotFoundException(s"Group <${iri.value}> not found."))
      internal <- groupService.updateGroup(groupToUpdate, request).map(GroupGetResponseADM.apply)
      external <- format.toExternal(internal)
    } yield external

  def putGroupStatus(user: User)(iri: GroupIri, request: GroupStatusUpdateRequest): Task[GroupGetResponseADM] =
    updateStatus(iri, request.status, user)

  def deleteGroup(user: User)(iri: GroupIri): Task[GroupGetResponseADM] =
    updateStatus(iri, GroupStatus.inactive, user)

  private def updateStatus(iri: GroupIri, status: GroupStatus, user: User) =
    for {
      groupAndProject <- auth.ensureSystemAdminOrProjectAdminOfGroup(user, iri)
      (group, _)       = groupAndProject
      updated         <- knoraGroupService.updateGroupStatus(group, status)
      internal        <- groupService.toGroup(updated).map(GroupGetResponseADM.apply)
      external        <- format.toExternal(internal)
    } yield external
}

object GroupRestService {
  val layer = ZLayer.derive[GroupRestService]
}
