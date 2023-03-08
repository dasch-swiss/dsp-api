/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import zio.Task
import zio._

import org.knora.webapi.IRI
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.admin.responder.listsmessages.ChildNodeInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListGetRequestADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeInfoGetRequestADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder.listsmessages._
import org.knora.webapi.responders.Responder

/**
 * Responds to requests relating to lists and nodes.
 */
trait ListsResponderV2
final case class ListsResponderV2Live(
  appConfig: AppConfig,
  messageRelay: MessageRelay
) extends ListsResponderV2
    with MessageHandler {

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
  private def getList(
    listIri: IRI,
    requestingUser: UserADM
  ): Task[ListGetResponseV2] =
    for {
      listResponseADM <-
        messageRelay
          .ask[ListGetResponseADM](
            ListGetRequestADM(
              iri = listIri,
              requestingUser = requestingUser
            )
          )

    } yield ListGetResponseV2(
      list = listResponseADM.list,
      requestingUser.lang,
      appConfig.fallbackLanguage
    )

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
    requestingUser: UserADM
  ): Task[NodeGetResponseV2] =
    for {
      nodeResponse <-
        messageRelay
          .ask[ChildNodeInfoGetResponseADM](
            ListNodeInfoGetRequestADM(
              iri = nodeIri,
              requestingUser = requestingUser
            )
          )

    } yield NodeGetResponseV2(
      node = nodeResponse.nodeinfo,
      requestingUser.lang,
      appConfig.fallbackLanguage
    )
}

object ListsResponderV2Live {
  val layer: URLayer[
    AppConfig with MessageRelay,
    ListsResponderV2
  ] = ZLayer.fromZIO {
    for {
      ac      <- ZIO.service[AppConfig]
      mr      <- ZIO.service[MessageRelay]
      handler <- mr.subscribe(ListsResponderV2Live(ac, mr))
    } yield handler
  }
}
