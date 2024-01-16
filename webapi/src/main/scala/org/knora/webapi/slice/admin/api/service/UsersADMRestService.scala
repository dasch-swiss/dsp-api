/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.*
import zio.macros.accessible

import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.responders.admin.UsersResponderADM
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.common.api.KnoraResponseRenderer

@accessible
trait UsersADMRestService {

  def listAllUsers(user: User): Task[UsersGetResponseADM]
}

final case class UsersADMRestServiceLive(
  responder: UsersResponderADM,
  format: KnoraResponseRenderer
) extends UsersADMRestService {

  override def listAllUsers(user: User): Task[UsersGetResponseADM] = for {
    internal <- responder.getAllUserADMRequest(user)
    external <- format.toExternal(internal)
  } yield external
}

object UsersADMRestServiceLive {
  val layer = ZLayer.derive[UsersADMRestServiceLive]
}
