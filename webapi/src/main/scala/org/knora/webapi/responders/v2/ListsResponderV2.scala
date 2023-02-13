/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import akka.pattern._
import zio.ZIO

import scala.concurrent.Future

import org.knora.webapi.IRI
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.admin.responder.listsmessages.ChildNodeInfoGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListGetRequestADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListGetResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeInfoGetRequestADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v2.responder.listsmessages._
import org.knora.webapi.responders.Responder

/**
 * Responds to requests relating to lists and nodes.
 */
class ListsResponderV2(responderData: ResponderData, messageRelay: MessageRelay)
    extends Responder(responderData.actorDeps)
    with MessageHandler {

  messageRelay.subscribe(this)

  override def handle(message: ResponderRequest): zio.Task[Any] =
    ZIO.fromFuture(_ => this.receive(message.asInstanceOf[ListsResponderRequestV2]))

  override def isResponsibleFor(message: ResponderRequest): Boolean = message match {
    case _: ListsResponderRequestV2 => true
    case _                          => false
  }

  /**
   * Receives a message of type [[ListsResponderRequestV2]], and returns an appropriate response message inside a future.
   */
  def receive(msg: ListsResponderRequestV2) = msg match {
    case ListGetRequestV2(listIri, requestingUser) =>
      getList(listIri, requestingUser)
    case NodeGetRequestV2(nodeIri, requestingUser) =>
      getNode(nodeIri, requestingUser)
    case other => handleUnexpectedMessage(other, log, this.getClass.getName)
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
  ): Future[ListGetResponseV2] =
    for {
      listResponseADM: ListGetResponseADM <- appActor
                                               .ask(
                                                 ListGetRequestADM(
                                                   iri = listIri,
                                                   requestingUser = requestingUser
                                                 )
                                               )
                                               .mapTo[ListGetResponseADM]

    } yield ListGetResponseV2(
      list = listResponseADM.list,
      requestingUser.lang,
      responderData.appConfig.fallbackLanguage
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
  ): Future[NodeGetResponseV2] =
    for {
      nodeResponse: ChildNodeInfoGetResponseADM <- appActor
                                                     .ask(
                                                       ListNodeInfoGetRequestADM(
                                                         iri = nodeIri,
                                                         requestingUser = requestingUser
                                                       )
                                                     )
                                                     .mapTo[ChildNodeInfoGetResponseADM]
    } yield NodeGetResponseV2(
      node = nodeResponse.nodeinfo,
      requestingUser.lang,
      responderData.appConfig.fallbackLanguage
    )

}
