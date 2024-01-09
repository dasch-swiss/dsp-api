/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.*
import zio.macros.accessible

import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsGetResponseADM
import org.knora.webapi.responders.admin.GroupsResponderADM

@accessible
trait GroupsRestService {
  def getAllGroups: Task[GroupsGetResponseADM]
}

final case class GroupsRestServiceLive(
  responder: GroupsResponderADM
) extends GroupsRestService {
  override def getAllGroups: Task[GroupsGetResponseADM] = responder.groupsGetRequestADM
}

object GroupsRestServiceLive {
  val layer = ZLayer.derive[GroupsRestServiceLive]
}
