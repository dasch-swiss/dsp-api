package org.knora.webapi.slice.admin.api.service

import org.knora.webapi.messages.admin.responder.groupsmessages.GroupsGetResponseADM
import org.knora.webapi.responders.admin.GroupsResponderADM
import zio.macros.accessible
import zio.*

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
