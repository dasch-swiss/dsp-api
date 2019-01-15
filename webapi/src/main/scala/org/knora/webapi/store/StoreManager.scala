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
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreRequest
import org.knora.webapi.store.triplestore.TriplestoreManager

/**
  * This actor receives messages for different stores, and forwards them to corresponding store manager. At the moment only triple stores are implemented,
  * but in the future, support for different remote repositories will probably be needed. This place would then be the crossroad for these different kinds
  * of 'stores' and their requests.
  */
class StoreManager extends Actor with ActorLogging {
    this: ActorMaker =>

    private val settings = Settings(context.system)

    /**
      * Start the TriplestoreManagerActor
      */
    var triplestoreManager: ActorRef = _

    override def preStart = {
        log.debug("StoreManager: start with preStart")
        triplestoreManager = makeActor(Props(new TriplestoreManager with LiveActorMaker).withDispatcher(KnoraDispatchers.KnoraActorDispatcher), TRIPLESTORE_MANAGER_ACTOR_NAME)
        log.debug("StoreManager: finished with preStart")
    }

    def receive = LoggingReceive {
        case tripleStoreMessage: TriplestoreRequest => triplestoreManager forward tripleStoreMessage
        case other => sender ! Status.Failure(UnexpectedMessageException(s"StoreManager received an unexpected message: $other"))
    }
}
