package org.knora.webapi.routing.admin

import zhttp.http._
import zio._

import org.knora.webapi.messages.admin.responder.usersmessages._

trait AuthenticatorService {
  def getUser(request: Request): Task[UserADM]
}

object AuthenticatorService {
  val layer = ZLayer.fromFunction(AuthenticatorServiceLive.apply _)

  def mock(user: UserADM) = ZLayer(
    ZIO.succeed(
      new AuthenticatorService() {
        override def getUser(request: Request): Task[UserADM] =
          ZIO.attempt(user)
      }
    )
  )
}
