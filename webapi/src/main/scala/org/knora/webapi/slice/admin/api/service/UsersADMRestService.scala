/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.*
import zio.macros.accessible

import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.responders.admin.UsersResponderADM

@accessible
trait UsersADMRestService {

  def listAllUsers(user: UserADM): Task[UsersGetResponseADM]
}

final case class UsersADMRestServiceLive(
  responder: UsersResponderADM
) extends UsersADMRestService {

  override def listAllUsers(user: UserADM): Task[UsersGetResponseADM] =
    for {
      result <- responder.getAllUserADMRequest(user)
    } yield result
}

object UsersADMRestServiceLive {
  val layer = ZLayer.derive[UsersADMRestServiceLive]
}
