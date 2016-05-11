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
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.triplestoremessages._
import org.knora.webapi.messages.v1storemessages.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK, TriplestoreAdminResponse}
import org.knora.webapi.util.ActorUtil._

import scala.concurrent.Future

/**
  * This responder is used by the Ckan route, for serving data to the Ckan harverster, which is published
  * under http://data.humanities.ch
  */
class StoreResponderV1 extends ResponderV1 {

    def receive = {
        case ResetTriplestoreContent(rdfDataObjects) => future2Message(sender(), resetTriplestoreContent(rdfDataObjects), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    private def resetTriplestoreContent(rdfDataObjects: Seq[RdfDataObject]): Future[TriplestoreAdminResponse] = {

        if (!StartupFlags.allowResetTriplestoreContentOperation.get) {
            throw ForbiddenException("The ResetTriplestoreContent operation is not allowed. Did you start the server with the right flag?")
        }

        for {
            response <- (storeManager ? ResetTriplestoreContent(rdfDataObjects)).mapTo[ResetTriplestoreContentACK]
            result = TriplestoreAdminResponse("ResetTripleStoreContent done!")
        } yield result
    }

}
