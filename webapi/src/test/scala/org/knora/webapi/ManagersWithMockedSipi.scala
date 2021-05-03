/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

package org.knora.webapi

import akka.actor.{Actor, ActorRef, Props}
import org.knora.webapi.app.Managers
import org.knora.webapi.core.LiveActorMaker
import org.knora.webapi.responders.MockableResponderManager
import org.knora.webapi.settings._
import org.knora.webapi.store.MockableStoreManager
import org.knora.webapi.store.eventstore.{EventStoreInMemImpl}
import org.knora.webapi.store.iiif.MockSipiConnector

/**
  * Mixin trait for running the application with mocked Sipi
  */
trait ManagersWithMockedSipi extends Managers {
  this: Actor =>

  lazy val mockStoreConnectors: Map[String, ActorRef] = Map(
    SipiConnectorActorName -> context.actorOf(Props(new MockSipiConnector)))
  lazy val mockResponders: Map[String, ActorRef] = Map.empty[String, ActorRef]

  lazy val storeManager: ActorRef = context.actorOf(
    Props(
      new MockableStoreManager(mockStoreConnectors = mockStoreConnectors, appActor = self, es = EventStoreInMemImpl)
      with LiveActorMaker),
    name = StoreManagerActorName
  )
  lazy val responderManager: ActorRef = context.actorOf(
    Props(new MockableResponderManager(mockRespondersOrStoreConnectors = mockResponders, appActor = self)),
    name = RESPONDER_MANAGER_ACTOR_NAME)
}
