/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import com.typesafe.scalalogging.LazyLogging
import zio._

import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.groupsmessages._
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.slice.admin.domain.service.KnoraUserService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.store.triplestore.api.TriplestoreService

final case class GroupsResponderADM(
  triplestore: TriplestoreService,
  messageRelay: MessageRelay,
  iriService: IriService,
  knoraUserService: KnoraUserService,
  projectService: ProjectService,
)(implicit val stringFormatter: StringFormatter)
    extends MessageHandler
    with GroupsADMJsonProtocol
    with LazyLogging {

  override def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[GroupsResponderRequestADM]

  /**
   * Receives a message extending [[GroupsResponderRequestADM]], and returns an appropriate response message
   */
  def handle(msg: ResponderRequest): Task[Any] = msg match {
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }
}

object GroupsResponderADM {
  val layer = ZLayer.fromZIO {
    for {
      ts      <- ZIO.service[TriplestoreService]
      iris    <- ZIO.service[IriService]
      sf      <- ZIO.service[StringFormatter]
      kus     <- ZIO.service[KnoraUserService]
      ps      <- ZIO.service[ProjectService]
      mr      <- ZIO.service[MessageRelay]
      handler <- mr.subscribe(GroupsResponderADM(ts, mr, iris, kus, ps)(sf))
    } yield handler
  }
}
