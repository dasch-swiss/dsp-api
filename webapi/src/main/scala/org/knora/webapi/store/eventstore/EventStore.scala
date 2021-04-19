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

import com.eventstore.dbclient.{EventData, EventStoreDBClient, EventStoreDBConnectionString, ReadStreamOptions}
import org.knora.webapi.IRI
import zio.{BootstrapRuntime, Task}

import scala.concurrent.{ExecutionContext, Future}

trait EventStore {
  def saveResourceEvent(resourceIri: IRI, event: String)(implicit ec: ExecutionContext): Future[Boolean]
  def loadResourceEvents(resourceIri: IRI)(implicit ec: ExecutionContext): Future[List[ResourceEvent]]
}

object EventStoreImpl extends EventStore {

  private val settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false")
  private val client = EventStoreDBClient.create(settings)

  override def saveResourceEvent(resourceIri: IRI, event: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    val eventToSave = ResourceCreated(resourceIri, event)
    val eventData = EventData.builderAsJson("ResourceCreated", eventToSave).build()
    val res = client.appendToStream(s"resource-$resourceIri", eventData).get()
    Future(true)
  }

  override def loadResourceEvents(resourceIri: IRI)(implicit ec: ExecutionContext): Future[List[ResourceEvent]] = {
    import collection.JavaConverters._
    val options: ReadStreamOptions = ReadStreamOptions.get.forwards.fromStart
    val result = client.readStream(s"resource-$resourceIri", options).get
    val recordedEvents = result.getEvents.asScala.toList
    val events: List[ResourceEvent] = recordedEvents.map { e =>
      e.getOriginalEvent.getContentType match {
        case "ResourceCreated" => {
          e.getOriginalEvent.getEventDataAs[ResourceCreated](classOf[ResourceCreated])
        }
      }
    }
    Future(events)
  }

}

sealed trait ResourceEvent
case class ResourceCreated(iri: IRI, event: String) extends ResourceEvent

//// ZIO helpers ////
object LegacyRuntime {
  def fromTask[Res](body: => Task[Res]): Future[Res] = {
    Runtime.unsafeRunToFuture(body)
  }
}

object Runtime extends BootstrapRuntime
