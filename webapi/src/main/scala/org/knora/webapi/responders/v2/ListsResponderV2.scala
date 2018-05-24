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

import akka.actor.Status
import akka.pattern._
import akka.stream.ActorMaterializer
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.listsmessages.{ListGetRequestADM, ListGetResponseADM, ListNodeInfoGetRequestADM, ListNodeInfoGetResponseADM}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.v2.responder.listsmessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil.{future2Message, handleUnexpectedMessage}

import scala.concurrent.Future

/**
  * Responds to requests relating to the creation of mappings from XML elements and attributes to standoff classes and properties.
  */
class ListsResponderV2 extends Responder {

    implicit val materializer: ActorMaterializer = ActorMaterializer()

    /**
      * Receives a message of type [[org.knora.webapi.messages.v2.responder.listsmessages.ListsResponderRequestV2]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case ListsGetRequestV2(listIri, userProfile) => future2Message(sender(), getList(listIri, userProfile), log)
        case NodeGetRequestV2(nodeIri, userProfile) => future2Message(sender(), getNode(nodeIri, userProfile), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Gets a list from the triplestore.
      *
      * @param listIri the Iri of the list's root node.
      * @param userProfile the user making the request.
      * @return a [[ListGetResponseV2]].
      */
    def getList(listIri: IRI, userProfile: UserADM): Future[ListGetResponseV2] = {

        for {
            listResponseADM: ListGetResponseADM <- (responderManager ? ListGetRequestADM(iri = listIri, requestingUser = userProfile)).mapTo[ListGetResponseADM]

        } yield ListGetResponseV2(list = listResponseADM.list, userProfile.lang, settings.fallbackLanguage)
    }

    /**
      * Gets a single list node from the triplestore.
      *
      * @param nodeIri the Iri of the list node.
      * @param userProfile the user making the request.
      * @return a  [[NodeGetResponseV2]].
      */
    def getNode(nodeIri: IRI, userProfile: UserADM): Future[NodeGetResponseV2] = {

        for {
            nodeResponse: ListNodeInfoGetResponseADM <- (responderManager ? ListNodeInfoGetRequestADM(iri = nodeIri, requestingUser = userProfile)).mapTo[ListNodeInfoGetResponseADM]
        } yield NodeGetResponseV2(node = nodeResponse.nodeinfo, userProfile.lang, settings.fallbackLanguage)

    }
}