/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import dsp.constants.SalsahGui.IRI
import zio.*
import zio.macros.accessible
import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.responders.admin.GroupsResponderADM
import org.knora.webapi.slice.admin.domain.model.User

@accessible
trait GroupsRestService {
  def getGroups: Task[GroupsGetResponseADM]
  def getGroup(iri: IRI): Task[GroupGetResponseADM]
  def getGroupMembers(groupIri: IRI, user: User): Task[GroupMembersGetResponseADM]
}

final case class GroupsRestServiceLive(
  responder: GroupsResponderADM
) extends GroupsRestService {
  override def getGroups: Task[GroupsGetResponseADM] = responder.groupsGetRequestADM

  override def getGroup(iri: IRI): Task[GroupGetResponseADM] = responder.groupGetRequestADM(iri)

  override def getGroupMembers(groupIri: IRI, user: User): Task[GroupMembersGetResponseADM] =
    responder.groupMembersGetRequestADM(groupIri, user)
}

object GroupsRestServiceLive {
  val layer = ZLayer.derive[GroupsRestServiceLive]
}
