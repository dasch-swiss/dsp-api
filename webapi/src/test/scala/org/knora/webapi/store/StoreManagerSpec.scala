/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

import akka.actor.Props
import akka.testkit.ImplicitSender
import com.typesafe.config.ConfigFactory
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.{CoreSpec, LiveActorMaker}

import scala.concurrent.duration._

object StoreManagerSpec {

    private val config = ConfigFactory.parseString(
        """
         akka.loglevel = "DEBUG"
         akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/*
 * In this simple test case, we start our actor under test, send it a message, and test if the message
 * we got in response is the one we expexted.
 *
 * The naming is usualy the class name appended by the word 'spec' all in camel case.
 *
 * All test cases are subclasses of CoreSpec and need to provide parameters
 * providing the actor system name and config.
 *
 * to execute, type 'test' in sbt
 */
class StoreManagerSpec extends CoreSpec(StoreManagerSpec.config) with ImplicitSender {

    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), STORE_MANAGER_ACTOR_NAME)

    private val tsType = settings.triplestoreType

    s"The TriplestoreManager Actor (tsType: $tsType)" when {
        "started by the StoreManager" should {
            "only start answering after initialization has finished " in {
                storeManager ! Initialized()
                expectMsg(60.seconds, InitializedResponse(true))
            }
        }

        "forward a Hello to the triplestore connector" should {
            "reply " in {
                within(1.seconds) {
                    storeManager ! HelloTriplestore(tsType)
                    expectMsg(1.second, HelloTriplestore(tsType))
                }
            }
        }
    }

}