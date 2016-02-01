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

/*
package org.knora.rapier.store.triplestore.embedded

import akka.actor.Props
import akka.testkit.{TestActorRef, ImplicitSender}
import com.typesafe.config.ConfigFactory
import org.knora.rapier.store._
import org.knora.rapier.{TestProbeMaker, CoreSpec}
import org.knora.rapier.messages.Hello

import scala.concurrent.duration._



/**
 * TODO: document this.
 */

object GraphDBActorSpec {
    val config = ConfigFactory.parseString(
        """
          |app {
          |   triplestore {
          |       dbtype = "embedded-jena-graphdb"
          |
          |       embedded {
          |           storage-type = "memory" // "memory" or "disk"
          |           storage-path = "/_DATA/_tdb" // ignored if "memory"
          |           reload-on-restart = true // ignored if "memory" as it will always reload
          |           rdf-data = [
          |               "/_DATA/_ttl/dokubib-onto.ttl",
          |               "/_DATA/_ttl/incunabula-data.ttl",
          |               "/_DATA/_ttl/incunabula-onto.ttl",
          |               "/_DATA/_ttl/knora-base.ttl",
          |               "/_DATA/_ttl/knora-dc.ttl",
          |               "/_DATA/_ttl/salsah-gui.ttl"
          |           ]
          |       }
          |   }
          |}
        """.stripMargin)
}


class GraphDBActorSpec extends CoreSpec("TriplestoreActorTestSystem", GraphDBActorSpec.config) with ImplicitSender {

    val actorUnderTest = TestActorRef(Props(new JenaGraphDBActor with TestProbeMaker), name = EMBEDDED_GRAPH_DB_ACTOR_NAME)

    /*
    * Send message to actor under test and check the result.
    * All must complete under 1 second or the test will fail
    * The Akka documentation describes a bunch of other methods
    * but this is the one I the most
    */
    "GraphTDBActor " when {
        "receiving a Hello " must {
            "reply " in {
                within(1000.millis) {
                    actorUnderTest ! Hello("GraphDB")
                    expectMsg(Hello("GraphDB"))
                }
            }
        }
        "receiving a ResetTripleStoreContent message " must {
            "restore content " in {

            }
        }
    }


}
*/
