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

import org.scalatest._
import matchers.should.Matchers._
import org.scalatest.wordspec.AsyncWordSpec

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
class EventStoreSpec extends AsyncWordSpec {
  "the event store" should {
    "allow storing and reading the event" in {
      val save = EventStoreImpl.saveResourceEvent("IRI", "event")
      save map { result =>
        assert(result)
      }

      for {
        result <- EventStoreImpl.loadResourceEvents("IRI")
        resourceCreatedEvent = result.head.asInstanceOf[ResourceCreated]
      } yield resourceCreatedEvent shouldBe ResourceCreated("IRI", "event")
    }
    // "allow subscribing and receiving events" in {}
  }
}
