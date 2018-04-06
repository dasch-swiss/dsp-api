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

import java.util.UUID

import akka.stream.ActorMaterializer
import org.knora.webapi.messages.v1.responder.standoffmessages.{CreateMappingRequestV1, CreateMappingResponseV1}
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.v1.ValueUtilV1
import org.knora.webapi.util.ActorUtil.future2Message
import akka.actor.Status

import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1

import scala.concurrent.Future

/**
  * Responds to requests relating to the creation of mappings from XML elements and attributes to standoff classes and properties.
  */
class StandoffResponderV2 extends Responder {

    implicit val materializer: ActorMaterializer = ActorMaterializer()

    // Converts SPARQL query results to ApiValueV1 objects.
    val valueUtilV1 = new ValueUtilV1(settings)

    /**
      * Receives a message of type [[org.knora.webapi.messages.v1.responder.standoffmessages.StandoffResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case CreateMappingRequestV1(xml, label, projectIri, mappingName, userProfile, uuid) => future2Message(sender(), createMappingV2(xml, label, projectIri, mappingName, userProfile, uuid), log)
    }

    def createMappingV2(xml: String, label: String, projectIri: IRI, mappingName: String, userProfile: UserProfileV1, apiRequestID: UUID): Future[CreateMappingResponseV1] = {
        // TODO: implement this
        Future(CreateMappingResponseV1(""))
    }

}