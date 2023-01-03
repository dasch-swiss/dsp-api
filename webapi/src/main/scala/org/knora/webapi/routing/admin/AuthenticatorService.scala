package org.knora.webapi.routing.admin

import zhttp.http._
import zio.Task
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.usersmessages._

trait AuthenticatorService {
  def getUser(request: Request): Task[UserADM]
}

object AuthenticatorService {
  val layer = ZLayer.fromFunction(AuthenticatorServiceLive(_, _, _))
}
