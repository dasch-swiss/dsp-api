/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.store.triplestore

import akka.actor.Props
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.v1.store.triplestoremessages.{HelloTriplestore, Initialized, InitializedResponse}
import org.knora.webapi.store._
import org.knora.webapi.{CoreSpec, LiveActorMaker}

import scala.concurrent.duration._

/**
  * TODO: document this.
  */
object FakeTriplestoreSpec {
    val config = ConfigFactory.parseString(
        """
          # app {
          #   triplestore {
          #       dbtype = "fake-triplestore"
          #
          #       embedded-jena-tdb {
          #          persisted = true // "false" -> memory, "true" -> disk
          #          storage-path = "_TMP" // ignored if "memory"
          #       }
          #
          #       fake-jena-tdb {
          #          fake-persisted-storage = true
          #          fake-triplestore-data-dir = "_TMP_FAKE/query-log"
          #
          #       }
          #   }
          #}
        """.stripMargin)
}

class FakeTriplestoreSpec extends CoreSpec("FakeTriplestoreTestSystem", FakeTriplestoreSpec.config) with ImplicitSender {

    val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    /*
    * Send message to actor under test and check the result.
    * All must complete under 1 second or the test will fail
    * The Akka documentation describes a bunch of other methods
    * but this is the one I the most
    */
    "FakeTriplestoreActor " when {
        "receiving a Hello " should {
            "reply " ignore {
                within(1000.millis) {
                    storeManager ! HelloTriplestore("FakeTriplestore")
                    expectMsg(HelloTriplestore("FakeTriplestore"))
                }
            }
        }
        "asked if Initialized  " should {
            "confirm " ignore {
                storeManager ! Initialized
                expectMsg(InitializedResponse(true))
            }
        }
    }
}