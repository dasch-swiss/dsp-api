/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.*

import dsp.errors.BadRequestException
import org.knora.webapi.messages.admin.responder.usersmessages.UserOperationResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UsersGetResponseADM
import org.knora.webapi.responders.admin.UsersResponderADM
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri
import org.knora.webapi.slice.admin.domain.model.UserStatus
import org.knora.webapi.slice.common.api.KnoraResponseRenderer

final case class UsersRestService(
  responder: UsersResponderADM,
  format: KnoraResponseRenderer
) {

  def listAllUsers(user: User): Task[UsersGetResponseADM] = for {
    internal <- responder.getAllUserADMRequest(user)
    external <- format.toExternal(internal)
  } yield external

  def deleteUser(requestingUser: User, deleteIri: UserIri): Task[UserOperationResponseADM] = for {
    _ <- ZIO
           .fail(BadRequestException("Changes to built-in users are not allowed."))
           .when(deleteIri.isBuiltInUser)
    uuid     <- Random.nextUUID
    internal <- responder.changeUserStatusADM(deleteIri.value, UserStatus.Inactive, requestingUser, uuid)
    external <- format.toExternal(internal)
  } yield external
}

object UsersRestService {
  val layer = ZLayer.derive[UsersRestService]
}
