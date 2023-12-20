package org.knora.webapi.slice.admin.api.service

import zio.*
import zio.macros.accessible

import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM

@accessible
trait UsersADMRestService {

  def listAllUsers(user: UserADM): Task[UsersGetResponseADM]
}

final case class UsersADMRestServiceLive() extends UsersADMRestService {

  override def listAllUsers(user: UserADM): Task[UsersGetResponseADM] = ???
}

object UsersADMRestServiceLive {
  val layer = ZLayer.derive[UsersADMRestServiceLive]
}
