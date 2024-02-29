/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import com.typesafe.scalalogging.LazyLogging
import zio.Task
import zio.ZIO
import zio.ZLayer

import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.admin.responder.usersmessages.*
import org.knora.webapi.responders.Responder
import org.knora.webapi.slice.admin.domain.service.UserService

final case class UsersResponder(userService: UserService) extends MessageHandler with LazyLogging {

  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[UsersResponderRequestADM]

  /**
   * Receives a message extending [[UsersResponderRequestADM]], and returns an appropriate message.
   */
  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case UserGetByIriADM(identifier, userInformationTypeADM, requestingUser) =>
      userService.findUserByIri(identifier).map(_.map(_.filterUserInformation(requestingUser, userInformationTypeADM)))
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }
}

object UsersResponder {
  val layer = ZLayer.fromZIO {
    for {
      us      <- ZIO.service[UserService]
      mr      <- ZIO.service[MessageRelay]
      handler <- mr.subscribe(UsersResponder(us))
    } yield handler
  }
}
