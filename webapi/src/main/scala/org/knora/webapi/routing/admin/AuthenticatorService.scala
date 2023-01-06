/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import zhttp.http._
import zio.Task
import zio.ZLayer
import zio.macros.accessible

import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.responders.ActorDeps

@accessible
trait AuthenticatorService {
  def getUser(request: Request): Task[UserADM]
}

object AuthenticatorService {
  val layer: ZLayer[ActorDeps with AppConfig with StringFormatter, Nothing, AuthenticatorServiceLive] =
    ZLayer.fromFunction(AuthenticatorServiceLive(_, _, _))
}
