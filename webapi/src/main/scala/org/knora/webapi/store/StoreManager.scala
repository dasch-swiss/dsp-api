/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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
import org.knora.webapi.core.{LiveActorMaker, _}
import org.knora.webapi.exceptions.UnexpectedMessageException
import org.knora.webapi.feature.{FeatureFactoryConfig, KnoraSettingsFeatureFactoryConfig}
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceRequest
import org.knora.webapi.messages.store.sipimessages.IIIFRequest
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreRequest
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl, _}
import org.knora.webapi.store.cacheservice.CacheServiceManager
import org.knora.webapi.store.eventstore.{EventStore, EventStoreManager, EventStoreRequest}
import org.knora.webapi.store.iiif.IIIFManager
import org.knora.webapi.store.triplestore.TriplestoreManager

import scala.concurrent.ExecutionContext

/**
  * This actor receives messages for different stores, and forwards them to the corresponding store manager.
  * At the moment only triple stores and Sipi are implemented, but in the future, support for different
  * remote repositories will probably be needed. This place would then be the crossroad for these different kinds
  * of 'stores' and their requests.
  *
  * @param appActor a reference to the main application actor.
  * @param es a reference to the EventStore implementation.
  */
class StoreManager(appActor: ActorRef, es: EventStore) extends Actor with ActorLogging {
  this: ActorMaker =>

  /**
    * The Knora Akka actor system.
    */
  protected implicit val system: ActorSystem = context.system

  /**
    * The Akka actor system's execution context for futures.
    */
  protected implicit val executionContext: ExecutionContext =
    system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  /**
    * The Knora settings.
    */
  protected val settings: KnoraSettingsImpl = KnoraSettings(system)

  /**
    * The default feature factory configuration.
    */
  protected val defaultFeatureFactoryConfig: FeatureFactoryConfig = new KnoraSettingsFeatureFactoryConfig(settings)

  /**
    * Starts the Triplestore Manager Actor
    */
  protected lazy val triplestoreManager: ActorRef = makeActor(
    Props(
      new TriplestoreManager(
        appActor = appActor,
        settings = settings,
        defaultFeatureFactoryConfig = defaultFeatureFactoryConfig
      ) with LiveActorMaker).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    TriplestoreManagerActorName
  )

  /**
    * Starts the IIIF Manager Actor
    */
  protected lazy val iiifManager: ActorRef = makeActor(
    Props(new IIIFManager with LiveActorMaker).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    IIIFManagerActorName)

  /**
    * Instantiates the Redis Manager
    */
  protected lazy val redisManager: ActorRef = makeActor(
    Props(new CacheServiceManager).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    RedisManagerActorName)

  protected lazy val eventStoreManager: ActorRef = makeActor(
    Props(new EventStoreManager(es)).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
    EventStoreManagerActorName)

  def receive: Receive = LoggingReceive {
    case tripleStoreMessage: TriplestoreRequest => triplestoreManager forward tripleStoreMessage
    case iiifMessages: IIIFRequest              => iiifManager forward iiifMessages
    case redisMessages: CacheServiceRequest     => redisManager forward redisMessages
    case eventStoreMessages: EventStoreRequest  => eventStoreManager forward eventStoreMessages
    case other =>
      sender ! Status.Failure(UnexpectedMessageException(s"StoreManager received an unexpected message: $other"))
  }
}
