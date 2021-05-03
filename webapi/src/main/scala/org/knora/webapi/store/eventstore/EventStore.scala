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

import akka.http.scaladsl.util.FastFuture
import com.eventstore.dbclient._
import org.knora.webapi.IRI
import zio.{BootstrapRuntime, IO, Task}

import scala.concurrent.{ExecutionContext, Future}

trait EventStore {
  def saveResourceEvent(event: ResourceEvent): Future[Boolean]
  def loadResourceEvents(resourceIri: IRI)(implicit ec: ExecutionContext): Future[List[ResourceEvent]]
}

/**
  * Live implementation talking to a running Event Store DB server
  *
  * to run event store locally inside Docker:
  *   $ docker run --name esdb-node -it -p 2113:2113 -p 1113:1113 eventstore/eventstore:latest --insecure --run-projections=All --enable-atom-pub-over-http=true
  *
  * go to the following address to see the streams: http://0.0.0.0:2113/web/index.html#/streams
  *
  */
object EventStoreLiveImpl extends EventStore {

  private val settings = EventStoreDBConnectionString.parse("esdb://localhost:2113?tls=false")
  private val client = EventStoreDBClient.create(settings)

  override def saveResourceEvent(event: ResourceEvent): Future[Boolean] = {
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
      case e: ResourceCreated => EventData.builderAsJson("ResourceCreated", e).build()
    }
  }

  /**
    * Appends (writes) the event data to the event store.
    * @param resourceIri the IRI of the resource this event relates to.
    * @param eventData the packaged event data.
    * @return a [[zio.Task]] containing [[WriteResult]].
    */
  def appendToStream(resourceIri: IRI, eventData: EventData): Task[Boolean] = {
    IO.effectAsync[Throwable, Boolean] { callback =>
      client
        .appendToStream(s"resource-$resourceIri", eventData)
        .handle[Unit]((result: WriteResult, err) => {
          err match {
            case null => callback(IO.succeed(true)) // at the moment we don't care about the result.
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
          e.getEvent.getEventDataAs(classOf[ResourceCreated])
        }
      }
    }
    Future(events)
  }
}

/**
  * In-Memory implementation for development and testing purposes
  */
object EventStoreInMemImpl extends EventStore {

  var m: scala.collection.mutable.Map[IRI, List[ResourceEvent]] =
    scala.collection.mutable.Map[IRI, List[ResourceEvent]]()

  /**
    * Save the event to the in-memory map.
    */
  def saveResourceEvent(event: ResourceEvent): Future[Boolean] = {
    val previousEvents: List[ResourceEvent] = m.getOrElse(event.iri, List[ResourceEvent]())
    m(event.iri) = previousEvents :+ event
    FastFuture.successful(true)
  }

  /**
    * Return saved events from the in-memory map.
    */
  def loadResourceEvents(resourceIri: IRI)(implicit ec: ExecutionContext): Future[List[ResourceEvent]] = {
    FastFuture.successful(m.getOrElse(resourceIri, List[ResourceEvent]()))
  }
}

//// ZIO helpers ////
object LegacyRuntime {
  def fromTask[Res](body: => Task[Res]): Future[Res] = {
    Runtime.unsafeRunToFuture(body)
  }
}

object Runtime extends BootstrapRuntime
