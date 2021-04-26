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

import com.eventstore.dbclient.{
  EventData,
  EventStoreDBClient,
  EventStoreDBConnectionString,
  ReadStreamOptions,
  WriteResult
}
import org.knora.webapi.IRI
import zio.blocking.Blocking
import zio.{BootstrapRuntime, IO, RIO, Task, UIO, ZIO}

import java.util.concurrent.CompletableFuture
import scala.concurrent.{ExecutionContext, Future, promise}

trait EventStore {
  def saveResourceEvent(resourceIri: IRI, event: String): Future[WriteResult]
  def loadResourceEvents(resourceIri: IRI)(implicit ec: ExecutionContext): Future[List[ResourceEvent]]
}

object EventStoreImpl extends EventStore {

  private val settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false")
  private val client = EventStoreDBClient.create(settings)

  override def saveResourceEvent(resourceIri: IRI, event: String): Future[WriteResult] = {
    val eventData = packageEvent(resourceIri, event)
    LegacyRuntime.fromTask(appendToStream(resourceIri, eventData))
    // appendToStream(resourceIri, eventData)
  }

  def packageEvent(resourceIri: IRI, event: String): EventData = {
    val eventToSave = ResourceCreated(resourceIri, event)
    val eventData = EventData.builderAsJson("ResourceCreated", eventToSave).build()
    eventData
  }

  def appendToStream(resourceIri: IRI, eventData: EventData): Task[WriteResult] = {
    // val result: CompletableFuture[WriteResult] = client.appendToStream(s"resource-$resourceIri", eventData)
    // scala.compat.java8.FutureConverters.toScala(result)

    IO.effectAsync[Throwable, WriteResult] { callback =>
      client
        .appendToStream(s"resource-$resourceIri", eventData)
        .handle[Unit]((result: WriteResult, err) => {
          err match {
            case null => callback(IO.succeed(result))
            case ex   => callback(IO.fail(ex))
          }
        })
    }
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
