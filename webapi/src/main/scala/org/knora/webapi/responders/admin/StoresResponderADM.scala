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

package org.knora.webapi.responders.admin

import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.storesmessages.{ResetTriplestoreContentRequestADM, ResetTriplestoreContentResponseADM}
import org.knora.webapi.messages.app.appmessages.GetAllowReloadOverHTTPState
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent, ResetTriplestoreContentACK}
import org.knora.webapi.messages.v1.responder.ontologymessages.{LoadOntologiesRequest, LoadOntologiesResponse}
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * This responder is used by [[org.knora.webapi.routing.admin.StoreRouteADM]], for piping through HTTP requests to the
  * 'Store Module'
  */
class StoresResponderADM extends Responder {

    /**
      * A user representing the Knora API server, used in those cases where a user is required.
      */
    private val systemUser = KnoraSystemInstances.Users.SystemUser

    def receive = {
        case ResetTriplestoreContentRequestADM(rdfDataObjects: Seq[RdfDataObject]) => future2Message(sender(), resetTriplestoreContent(rdfDataObjects), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * This method send a [[ResetTriplestoreContent]] message to the [[org.knora.webapi.store.triplestore.TriplestoreManager]].
      *
      * @param rdfDataObjects the payload consisting of a list of [[RdfDataObject]] send inside the message.
      * @return a future containing a [[ResetTriplestoreContentResponseADM]].
      */
    private def resetTriplestoreContent(rdfDataObjects: Seq[RdfDataObject]): Future[ResetTriplestoreContentResponseADM] = {
        log.debug(s"resetTriplestoreContent - called")
        log.debug(s"resetTriplestoreContent called with: {}", rdfDataObjects.toString)
        val allowReloadOverHTTP = Await.result(applicationStateActor ? GetAllowReloadOverHTTPState(), 1.second).asInstanceOf[Boolean]
        log.debug(s"StartupFlags.allowReloadOverHTTP = {}", allowReloadOverHTTP)

        for {
            value: Boolean <- (applicationStateActor ? GetAllowReloadOverHTTPState()).mapTo[Boolean]
            _ = if (!value) {
                //println("resetTriplestoreContent - will throw ForbiddenException")
                throw ForbiddenException("The ResetTriplestoreContent operation is not allowed. Did you start the server with the right flag?")
            }

            // need to send this of with a longer timeout
            resetResponse <- (storeManager ? ResetTriplestoreContent(rdfDataObjects)).mapTo[ResetTriplestoreContentACK]
            _ = log.debug(s"resetTriplestoreContent - triplestore reset done - {}", resetResponse.toString)

            loadOntologiesResponse <- (responderManager ? LoadOntologiesRequest(systemUser)).mapTo[LoadOntologiesResponse]
            _ = log.debug(s"resetTriplestoreContent - load ontology done - {}", loadOntologiesResponse.toString)

            result = ResetTriplestoreContentResponseADM(message = "success")

        } yield result
    }

}
