/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern._
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.listsmessages.{ListGetRequestADM, ListGetResponseADM, ListNodeInfoGetRequestADM, ListNodeInfoGetResponseADM}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder.listsmessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.Responder.handleUnexpectedMessage

import scala.concurrent.Future

/**
  * Responds to requests relating to lists and nodes.
  */
class ListsResponderV2(system: ActorSystem, applicationStateActor: ActorRef, responderManager: ActorRef, storeManager: ActorRef) extends Responder(system, applicationStateActor, responderManager, storeManager) {

    /**
      * Receives a message of type [[ListsResponderRequestV2]], and returns an appropriate response message inside a future.
      */
    def receive(msg: ListsResponderRequestV2) = msg match {
        case ListGetRequestV2(listIri, requestingUser) => getList(listIri, requestingUser)
        case NodeGetRequestV2(nodeIri, requestingUser) => getNode(nodeIri, requestingUser)
        case other => handleUnexpectedMessage(other, log, this.getClass.getName)
    }

    /**
      * Gets a list from the triplestore.
      *
      * @param listIri the Iri of the list's root node.
      * @param requestingUser the user making the request.
      * @return a [[ListGetResponseV2]].
      */
    private def getList(listIri: IRI, requestingUser: UserADM): Future[ListGetResponseV2] = {

        for {
            listResponseADM: ListGetResponseADM <- (responderManager ? ListGetRequestADM(iri = listIri, requestingUser = requestingUser)).mapTo[ListGetResponseADM]

        } yield ListGetResponseV2(list = listResponseADM.list, requestingUser.lang, settings.fallbackLanguage)
    }

    /**
      * Gets a single list node from the triplestore.
      *
      * @param nodeIri the Iri of the list node.
      * @param requestingUser the user making the request.
      * @return a  [[NodeGetResponseV2]].
      */
    private def getNode(nodeIri: IRI, requestingUser: UserADM): Future[NodeGetResponseV2] = {

        for {
            nodeResponse: ListNodeInfoGetResponseADM <- (responderManager ? ListNodeInfoGetRequestADM(iri = nodeIri, requestingUser = requestingUser)).mapTo[ListNodeInfoGetResponseADM]
        } yield NodeGetResponseV2(node = nodeResponse.nodeinfo, requestingUser.lang, settings.fallbackLanguage)

    }
}