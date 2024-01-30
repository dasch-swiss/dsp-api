/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.knora.webapi.IRI
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.{MessageHandler, MessageRelay}
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.admin.responder.listsmessages.{ChildNodeInfoGetResponseADM, ListGetResponseADM}
import org.knora.webapi.messages.v2.responder.listsmessages.*
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.admin.ListsResponder
import org.knora.webapi.slice.admin.domain.model.User
import zio.*

final case class ListsResponderV2(
  appConfig: AppConfig,
  listsResponder: ListsResponder,
  messageRelay: MessageRelay
) extends MessageHandler {

  def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[ListsResponderRequestV2]

  /**
   * Receives a message of type [[ListsResponderRequestV2]], and returns an appropriate response message inside a future.
   */
  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case ListGetRequestV2(listIri, requestingUser) =>
      getList(listIri, requestingUser)
    case NodeGetRequestV2(nodeIri, requestingUser) =>
      getNode(nodeIri, requestingUser)
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  /**
   * Gets a list from the triplestore.
   *
   * @param listIri        the Iri of the list's root node.
   * @param requestingUser the user making the request.
   * @return a [[ListGetResponseV2]].
   */
  private def getList(listIri: IRI, requestingUser: User): Task[ListGetResponseV2] =
    listsResponder
      .listGetRequestADM(listIri)
      .mapAttempt(_.asInstanceOf[ListGetResponseADM])
      .map(resp => ListGetResponseV2(resp.list, requestingUser.lang, appConfig.fallbackLanguage))

  /**
   * Gets a single list node from the triplestore.
   *
   * @param nodeIri              the Iri of the list node.
   *
   * @param requestingUser       the user making the request.
   * @return a  [[NodeGetResponseV2]].
   */
  private def getNode(
    nodeIri: IRI,
    requestingUser: User
  ): Task[NodeGetResponseV2] =
    listsResponder
      .listNodeInfoGetRequestADM(nodeIri)
      .flatMap {
        case ChildNodeInfoGetResponseADM(node) => ZIO.succeed(node)
        case _                                 => ZIO.die(new IllegalStateException(s"No child node found $nodeIri"))
      }
      .map(NodeGetResponseV2(_, requestingUser.lang, appConfig.fallbackLanguage))
}

object ListsResponderV2 {
  val layer: URLayer[
    AppConfig & ListsResponder & MessageRelay,
    ListsResponderV2
  ] = ZLayer.fromZIO {
    for {
      ac      <- ZIO.service[AppConfig]
      lr      <- ZIO.service[ListsResponder]
      mr      <- ZIO.service[MessageRelay]
      handler <- mr.subscribe(ListsResponderV2(ac, lr, mr))
    } yield handler
  }
}
