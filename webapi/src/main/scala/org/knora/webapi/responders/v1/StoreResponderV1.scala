/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v1

import akka.actor.Status
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.messages.v1.responder.storemessages.{ResetTriplestoreContentRequestV1, ResetTriplestoreContentResponseV1}
import org.knora.webapi.messages.v1.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.util.ActorUtil._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

/**
  * This responder is used by [[org.knora.webapi.routing.v1.StoreRouteV1]], for piping through HTTP requests to the
  * 'Store Module'
  */
class StoreResponderV1 extends ResponderV1 {

    override implicit val timeout = Timeout(300 seconds)

    def receive = {
        case ResetTriplestoreContentRequestV1(rdfDataObjects) => future2Message(sender(), resetTriplestoreContent(rdfDataObjects), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /**
      * This method send a [[ResetTriplestoreContent]] message to the [[org.knora.webapi.store.triplestore.TriplestoreManagerActor]].
      *
      * @param rdfDataObjects the payload consisting of a list of [[RdfDataObject]] send inside the message.
      * @return a future containing a [[ResetTriplestoreContentResponseV1]].
      */
    private def resetTriplestoreContent(rdfDataObjects: Seq[RdfDataObject]): Future[ResetTriplestoreContentResponseV1] = {

        log.debug(s"resetTriplestoreContent called with: ${rdfDataObjects.toString}")
        log.debug(s"StartupFlags.allowResetTriplestoreContentOperationOverHTTP = ${StartupFlags.allowResetTriplestoreContentOperationOverHTTP.get}")
        if (!StartupFlags.allowResetTriplestoreContentOperationOverHTTP.get) {
            Future(throw ForbiddenException("The ResetTriplestoreContent operation is not allowed. Did you start the server with the right flag?"))
        } else {
            
            for {
                resetResponse <- (storeManager ? ResetTriplestoreContent(rdfDataObjects)).mapTo[ResetTriplestoreContentACK]
                loadOntologiesResponse <- (responderManager ? LoadOntologiesRequest).mapTo[LoadOntologiesResponse]
            } yield ResetTriplestoreContentResponseV1("success")


            /*
            val future = storeManager ? ResetTriplestoreContent(rdfDataObjects)
            val result = Await.result(future, 300.seconds)
            Future(ResetTriplestoreContentResponseV1(s"${result.toString}"))
            */
        }
    }

}
