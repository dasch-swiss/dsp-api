/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import zio._
import zio.http._

import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.util.KnoraSystemInstances

trait AuthenticatorService {
  def getUser(request: Request): Task[UserADM]
}

object AuthenticatorService {
  val layer = ZLayer.fromFunction(AuthenticatorServiceLive.apply _)

  def mock(user: Option[UserADM] = None) = ZLayer(
    ZIO.succeed(
      new AuthenticatorService() {
        override def getUser(request: Request): Task[UserADM] =
          ZIO.attempt(user.getOrElse(KnoraSystemInstances.Users.AnonymousUser))
      }
    )
  )
}
