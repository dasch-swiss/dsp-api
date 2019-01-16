/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.store

import akka.actor.{ActorRef, Props}
import org.knora.webapi.store.iiif.MockableSipiManager
import org.knora.webapi.{KnoraDispatchers, LiveActorMaker}

class MockableStoreManager(mockStoreConnectors: Map[String, ActorRef]) extends StoreManager with LiveActorMaker{

    /**
      * Starts the MockableSipiManager
      */
    override lazy val sipiManager = makeActor(Props(new MockableSipiManager(mockStoreConnectors) with LiveActorMaker).withDispatcher(KnoraDispatchers.KnoraActorDispatcher), IIIFManagerActorName)

}
