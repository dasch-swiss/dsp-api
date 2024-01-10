/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.*
import zio.macros.accessible

import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.slice.admin.domain.model.GroupIri
import org.knora.webapi.slice.admin.domain.model.User

@accessible
trait GroupsRestService {
  def getGroups: Task[GroupsGetResponseADM]
  def getGroup(iri: GroupIri): Task[GroupGetResponseADM]
  def getGroupMembers(iri: GroupIri, user: User): Task[GroupMembersGetResponseADM]
}

final case class GroupsRestServiceLive(
  responder: GroupsResponderADM
) extends GroupsRestService {
  override def getGroups: Task[GroupsGetResponseADM] = responder.groupsGetRequestADM

  override def getGroup(iri: GroupIri): Task[GroupGetResponseADM] = responder.groupGetRequest(iri)

  override def getGroupMembers(iri: GroupIri, user: User): Task[GroupMembersGetResponseADM] =
    responder.groupMembersGetRequest(iri, user)
}

object GroupsRestServiceLive {
  val layer = ZLayer.derive[GroupsRestServiceLive]
}
