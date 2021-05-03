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

import com.eventstore.dbclient.WriteResult
import com.typesafe.config.ConfigFactory
import org.knora.webapi.CoreSpec

import scala.concurrent.duration._

object EventStoreManagerSpec {
  val config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * 1. test read/write
  * a. start event store container
  * b. create event for resource
  * c. read stream for resource and check that event was returned
  *
  * 2. test subscription
  * a. subscribe to resources category
  * b. create event for resource
  * c. listen for created event
  */
class EventStoreManagerSpec extends CoreSpec(EventStoreManagerSpec.config) {
  "The EventStoreManager" should {

    val resourceIri = "IRI"
    val eventContent = "event content"

    "successfully store an event" in {
      val event = ResourceCreated(resourceIri, eventContent)
      storeManager ! EventStoreSaveResourceEventRequest(event)
      val received = expectMsgType[Boolean](5.seconds)
      received should be(true)
    }

    "successfully retrieve resource events by IRI" in {
      storeManager ! EventStoreGetResourceEventsRequest(resourceIri)
      val received = expectMsgType[List[ResourceEvent]](5.seconds)
      received should be(List(ResourceCreated(resourceIri, eventContent)))
    }
  }
}
