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

import com.eventstore.dbclient._
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.DataConversionException
import zio.json._
import zio.{BootstrapRuntime, IO, Task}

import scala.concurrent.{ExecutionContext, Future}

trait EventStore {
  def saveResourceEvent(event: ResourceEvent): Future[WriteResult]
  def loadResourceEvents(resourceIri: IRI)(implicit ec: ExecutionContext): Future[List[ResourceEvent]]
}

object EventStoreImpl extends EventStore {

  private val settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false")
  private val client = EventStoreDBClient.create(settings)

  override def saveResourceEvent(event: ResourceEvent): Future[WriteResult] = {
    val eventData = packageEvent(event)
    LegacyRuntime.fromTask(appendToStream(event.iri, eventData))
  }

  /**
    * Packages the [[ResourceEvent]] into a data structure that the event store expects.
    * @param event a [[ResourceEvent]].
    * @return [[com.eventstore.dbclient.EventData]].
    */
  def packageEvent(event: ResourceEvent): EventData = {
    event match {
      case e: ResourceCreated => EventDataBuilder.json("ResourceCreated", e.toJson).build()
    }
  }

  /**
    * Appends (writes) the event data to the event store.
    * @param resourceIri the IRI of the resource this event relates to.
    * @param eventData the packaged event data.
    * @return a [[zio.Task]] containing [[WriteResult]].
    */
  def appendToStream(resourceIri: IRI, eventData: EventData): Task[WriteResult] = {
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
    val recordedEvents: List[ResolvedEvent] = result.getEvents.asScala.toList
    val events: List[ResourceEvent] = recordedEvents.map { e =>
      e.getEvent.getEventType match {
        case "ResourceCreated" => {
          // e.getEvent.getEventDataAs(classOf[ResourceCreated])
          val rawData: Array[Byte] = e.getEvent.getEventData
          rawData.toString
            .fromJson[ResourceCreated]
            .getOrElse(throw DataConversionException("Problem deserializing event from event store."))
        }
      }
    }
    Future(events)
  }
}

//// ZIO helpers ////
object LegacyRuntime {
  def fromTask[Res](body: => Task[Res]): Future[Res] = {
    Runtime.unsafeRunToFuture(body)
  }
}

object Runtime extends BootstrapRuntime
