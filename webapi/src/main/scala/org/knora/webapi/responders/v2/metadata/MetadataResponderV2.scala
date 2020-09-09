/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2.metadata

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}

object MetadataResponderV2 {

    sealed trait MetadataProtocol

    sealed trait MetadataProtocolRequest extends MetadataProtocol
    final case class GetMetadataRequest(replyTo: ActorRef[GetMetadataResponse]) extends MetadataProtocolRequest

    sealed trait MetadataProtocolResponse
    final case class GetMetadataResponse(metadata: String) extends MetadataProtocolResponse

    def apply(): Behavior[MetadataProtocolRequest] =
        responder()

    private def responder(): Behavior[MetadataProtocolRequest] =
        Behaviors.receive { (context, message) =>
            message match {
                case GetMetadataRequest(replyTo) =>
                    context.log.info(s"Message: $message")
                    replyTo ! GetMetadataResponse("blabla")
                    Behaviors.same
            }
        }
}
