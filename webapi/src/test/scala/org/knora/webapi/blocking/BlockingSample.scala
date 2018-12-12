/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.blocking

import akka.actor.{Actor, ActorSystem, Props}
import org.knora.webapi.KnoraDispatchers

import scala.concurrent.{ExecutionContext, Future}

/**
  * This code is used as a playground to better understand blocking inside actors and how to isolate the negative effects.
  */

class BlockingFutureActor extends Actor {
    implicit val executionContext: ExecutionContext = context.system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)

    def receive = {
        case i: Int ⇒
            println(s"Calling blocking Future: ${i}")
            Future {
                Thread.sleep(5000) //block for 5 seconds
                println(s"Blocking future finished ${i}")
            }
    }
}

class PrintActor extends Actor {
    def receive = {
        case i: Int ⇒
            println(s"PrintActor: ${i}")
    }
}

object BlockingSample {
    def main(args: Array[String]) = {
        val system = ActorSystem("BlockingSample")

        try {
            // #blocking-main
            val actor1 = system.actorOf(Props(new BlockingFutureActor).withDispatcher(KnoraDispatchers.KnoraActorDispatcher))
            val actor2 = system.actorOf(Props(new PrintActor).withDispatcher(KnoraDispatchers.KnoraActorDispatcher))

            for (i ← 1 to 10000) {
                actor1 ! i
                actor2 ! i
            }
            // #blocking-main
        } finally {
            Thread.sleep(1000 * 30) // 30 seconds
            system.terminate()
        }
    }
}