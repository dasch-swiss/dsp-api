/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.*
import zio.macros.accessible

import dsp.errors.NotFoundException
import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.messages.admin.responder.usersmessages.GroupMembersGetResponseADM
import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupCreateRequest
import org.knora.webapi.slice.admin.api.GroupsRequests.GroupUpdateRequest
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer

@accessible
trait GroupsRestService {
  def getGroups: Task[GroupsGetResponseADM]
  def getGroupByIri(iri: GroupIri): Task[GroupGetResponseADM]
  def getGroupMembers(iri: GroupIri, user: User): Task[GroupMembersGetResponseADM]
  def postGroup(request: GroupCreateRequest, user: User): Task[GroupGetResponseADM]
  def putGroup(iri: GroupIri, request: GroupUpdateRequest, user: User): Task[GroupGetResponseADM]
  def putGroupStatus(iri: GroupIri, request: GroupUpdateRequest, user: User): Task[GroupGetResponseADM]
}

final case class GroupsRestServiceLive(
  auth: AuthorizationRestService,
  responder: GroupsResponderADM,
  format: KnoraResponseRenderer
) extends GroupsRestService {
  override def getGroups: Task[GroupsGetResponseADM] = for {
    internal <- responder.groupsGetADM.map(GroupsGetResponseADM)
    external <- format.toExternal(internal)
  } yield external

  override def getGroupByIri(iri: GroupIri): Task[GroupGetResponseADM] =
    for {
      internal <- responder
                    .groupGetADM(iri.value)
                    .someOrFail(NotFoundException(s"Group <${iri.value}> not found."))
                    .map(GroupGetResponseADM.apply)
      external <- format.toExternal(internal)
    } yield external

  override def getGroupMembers(iri: GroupIri, user: User): Task[GroupMembersGetResponseADM] =
    for {
      internal <- responder.groupMembersGetRequest(iri, user)
      external <- format.toExternal(internal)
    } yield external

  override def postGroup(request: GroupCreateRequest, user: User): Task[GroupGetResponseADM] =
    for {
      _        <- auth.ensureSystemAdminOrProjectAdmin(user, request.project)
      uuid     <- Random.nextUUID
      internal <- responder.createGroup(request, uuid)
      external <- format.toExternal(internal)
    } yield external

  override def putGroup(iri: GroupIri, request: GroupUpdateRequest, user: User): Task[GroupGetResponseADM] =
    for {
      _        <- auth.ensureSystemAdminOrProjectAdminOfGroup(user, iri)
      uuid     <- Random.nextUUID
      internal <- responder.updateGroup(iri, request, uuid)
      external <- format.toExternal(internal)
    } yield external

  override def putGroupStatus(iri: GroupIri, request: GroupUpdateRequest, user: User): Task[GroupGetResponseADM] =
    for {
      _        <- auth.ensureSystemAdminOrProjectAdminOfGroup(user, iri)
      uuid     <- Random.nextUUID
      internal <- responder.changeGroupStatusRequestADM(iri, request, user, uuid)
      external <- format.toExternal(internal)
    } yield external

}

object GroupsRestServiceLive {
  val layer = ZLayer.derive[GroupsRestServiceLive]
}
