/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.store

import akka.actor._
import akka.event.LoggingReceive
import org.knora.webapi._
import org.knora.webapi.messages.store.sipimessages.IIIFRequest
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreRequest
import org.knora.webapi.store.iiif.IIIFManager
import org.knora.webapi.store.triplestore.TriplestoreManager

/**
  * This actor receives messages for different stores, and forwards them to the corresponding store manager.
  * At the moment only triple stores and Sipi are implemented, but in the future, support for different
  * remote repositories will probably be needed. This place would then be the crossroad for these different kinds
  * of 'stores' and their requests.
  */
class StoreManager extends Actor with ActorLogging {
    this: ActorMaker =>

    /**
      * Starts the TriplestoreManager
      */
    protected lazy val triplestoreManager = makeActor(Props(new TriplestoreManager with LiveActorMaker).withDispatcher(KnoraDispatchers.KnoraActorDispatcher), TriplestoreManagerActorName)

    /**
      * Starts the iiifManager
      */
    protected lazy val iiifManager = makeActor(Props(new IIIFManager with LiveActorMaker).withDispatcher(KnoraDispatchers.KnoraActorDispatcher), IIIFManagerActorName)

    def receive = LoggingReceive {
        case tripleStoreMessage: TriplestoreRequest => triplestoreManager forward tripleStoreMessage
        case iiifMessages: IIIFRequest => iiifManager forward iiifMessages
        case other => sender ! Status.Failure(UnexpectedMessageException(s"StoreManager received an unexpected message: $other"))
    }
}
