/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  The DaSCH Service Platform  is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU Affero General Public
 *  License as published by the Free Software Foundation, either version 3
 *  of the License, or (at your option) any later version.
 *
 *  The DaSCH Service Platform is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with the DaSCH Service Platform.  If not, see
 *  <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.eventstore

import akka.actor.{Actor, ActorLogging, ActorSystem, Status}
import com.eventstore.dbclient.WriteResult
import com.typesafe.scalalogging.{LazyLogging, Logger}
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.UnexpectedMessageException
import org.knora.webapi.instrumentation.InstrumentationSupport
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl}
import org.knora.webapi.util.ActorUtil.future2Message

import scala.concurrent.{ExecutionContext, Future}

/**
  * EventStoreManager is the actor taking requests for accessing the event store.
  * The constructor takes the implementation that will be used to execute the request.
  * @param es the event store implementation.
  */
class EventStoreManager(es: EventStore) extends Actor with ActorLogging with LazyLogging with InstrumentationSupport {

  /**
    * The Knora Akka actor system.
    */
  protected implicit val _system: ActorSystem = context.system

  /**
    * The Akka actor system's execution context for futures.
    */
  protected implicit val ec: ExecutionContext = context.system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  /**
    * The Knora settings.
    */
  protected val s: KnoraSettingsImpl = KnoraSettings(context.system)

  // this is needed for time measurements using 'org.knora.webapi.Timing'
  implicit val l: Logger = logger

  def receive = {
    case EventStoreSaveResourceEventRequest(event: ResourceEvent) =>
      future2Message(sender(), saveResourceEvent(event), log)
    case EventStoreGetResourceEventsRequest(resourceIri: IRI) =>
      future2Message(sender(), getResourcEvents(resourceIri), log)
    case other =>
      sender ! Status.Failure(UnexpectedMessageException(s"EventStoreManager received an unexpected message: $other"))
  }

  private def saveResourceEvent(event: ResourceEvent): Future[Boolean] =
    tracedFuture("event-store-save-resource-event") {
      es.saveResourceEvent(event)
    }

  private def getResourcEvents(resourceIri: IRI): Future[List[ResourceEvent]] =
    tracedFuture("event-store-get-resource-events") {
      es.loadResourceEvents(resourceIri)
    }
}
