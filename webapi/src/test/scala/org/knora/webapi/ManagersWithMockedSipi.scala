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

import app.Managers
import core.LiveActorMaker
import messages.util.ResponderData
import responders.MockableResponderManager
import settings._
import store.MockableStoreManager
import store.cacheservice.redis.CacheServiceRedisImpl
import store.cacheservice.settings.CacheServiceSettings
import store.iiif.MockSipiConnector

import akka.actor.{Actor, ActorRef, Props}

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
      new MockableStoreManager(mockStoreConnectors = mockStoreConnectors,
                               appActor = self,
                               cs = new CacheServiceRedisImpl(new CacheServiceSettings(context.system.settings.config)))
      with LiveActorMaker),
    name = StoreManagerActorName
  )

  lazy val responderManager: ActorRef = context.actorOf(
    Props(
      new MockableResponderManager(
        mockRespondersOrStoreConnectors = mockResponders,
        appActor = self,
        responderData = ResponderData(system,
                                      self,
                                      knoraSettings = KnoraSettings(system),
                                      cacheServiceSettings = new CacheServiceSettings(system.settings.config))
      )),
    name = RESPONDER_MANAGER_ACTOR_NAME
  )
}
