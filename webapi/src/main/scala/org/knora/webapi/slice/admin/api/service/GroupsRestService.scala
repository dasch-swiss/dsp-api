/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio._

import dsp.errors.NotFoundException
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.GroupMembersGetResponseADM
import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupStatusUpdateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.GroupService
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer

final case class GroupsRestService(
  auth: AuthorizationRestService,
  groupService: GroupService,
  responder: GroupsResponderADM,
  format: KnoraResponseRenderer,
) {

  def getGroups: Task[GroupsGetResponseADM] = for {
    internal <- groupService.findAll
    external <- format.toExternalADM(GroupsGetResponseADM(internal))
  } yield external

  def getGroupByIri(iri: GroupIri): Task[GroupGetResponseADM] =
    for {
      internal <- responder
                    .groupGetADM(iri.value)
                    .someOrFail(NotFoundException(s"Group <${iri.value}> not found."))
                    .map(GroupGetResponseADM.apply)
      external <- format.toExternalADM(internal)
    } yield external

  def getGroupMembers(iri: GroupIri, user: User): Task[GroupMembersGetResponseADM] =
    for {
      internal <- responder.groupMembersGetRequest(iri, user)
      external <- format.toExternalADM(internal)
    } yield external

  def postGroup(request: GroupCreateRequest, user: User): Task[GroupGetResponseADM] =
    for {
      _        <- auth.ensureSystemAdminOrProjectAdmin(user, request.project)
      uuid     <- Random.nextUUID
      internal <- responder.createGroup(request, uuid)
      external <- format.toExternalADM(internal)
    } yield external

  def putGroup(iri: GroupIri, request: GroupUpdateRequest, user: User): Task[GroupGetResponseADM] =
    for {
      _        <- auth.ensureSystemAdminOrProjectAdminOfGroup(user, iri)
      uuid     <- Random.nextUUID
      internal <- responder.updateGroup(iri, request, uuid)
      external <- format.toExternalADM(internal)
    } yield external

  def putGroupStatus(iri: GroupIri, request: GroupStatusUpdateRequest, user: User): Task[GroupGetResponseADM] =
    for {
      _        <- auth.ensureSystemAdminOrProjectAdminOfGroup(user, iri)
      uuid     <- Random.nextUUID
      internal <- responder.updateGroupStatus(iri, request, uuid)
      external <- format.toExternalADM(internal)
    } yield external

  def deleteGroup(iri: GroupIri, user: User): Task[GroupGetResponseADM] =
    for {
      _        <- auth.ensureSystemAdminOrProjectAdminOfGroup(user, iri)
      uuid     <- Random.nextUUID
      internal <- responder.deleteGroup(iri, uuid)
      external <- format.toExternalADM(internal)
    } yield external
}

object GroupsRestService {
  val layer = ZLayer.derive[GroupsRestService]
}
