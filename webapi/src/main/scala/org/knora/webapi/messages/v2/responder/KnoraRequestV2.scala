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

package org.knora.webapi.messages.v2.responder

import java.util.UUID

import akka.actor.ActorSelection
import akka.event.LoggingAdapter
import akka.util.Timeout
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.util.jsonld.JsonLDDocument

import scala.concurrent.{ExecutionContext, Future}

/**
  * A tagging trait for messages that can be sent to Knora API v2 responders.
  */
trait KnoraRequestV2

/**
  * A trait for objects that can generate case class instances based on JSON-LD input.
  *
  * @tparam C the type of the case class that can be generated.
  */
trait KnoraJsonLDRequestReaderV2[C] {
    /**
      * Converts JSON-LD input into a case class instance.
      *
      * @param jsonLDDocument   the JSON-LD input.
      * @param apiRequestID     the UUID of the API request.
      * @param requestingUser   the user making the request.
      * @param responderManager a reference to the responder manager.
      * @param log              a logging adapter.
      * @param timeout          a timeout for `ask` messages.
      * @param executionContext an execution context for futures.
      * @return a case class instance representing the input.
      */
    def fromJsonLD(jsonLDDocument: JsonLDDocument,
                   apiRequestID: UUID,
                   requestingUser: UserADM,
                   responderManager: ActorSelection,
                   log: LoggingAdapter)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[C]
}
