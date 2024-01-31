/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import dsp.errors.NotFoundException
import zio.*
import zio.macros.accessible
import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.messages.admin.responder.usersmessages.GroupMembersGetResponseADM
import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.KnoraResponseRenderer

@accessible
trait GroupsRestService {
  def getGroups: Task[GroupsGetResponseADM]
  def getGroupByIri(iri: GroupIri): Task[GroupGetResponseADM]
  def getGroupMembers(iri: GroupIri, user: User): Task[GroupMembersGetResponseADM]
}

final case class GroupsRestServiceLive(
  responder: GroupsResponderADM,
  format: KnoraResponseRenderer
) extends GroupsRestService {
  override def getGroups: Task[GroupsGetResponseADM] = for {
    internal <- responder.groupsGetRequestADM
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
}

object GroupsRestServiceLive {
  val layer = ZLayer.derive[GroupsRestServiceLive]
}
