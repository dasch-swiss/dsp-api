/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.store.redis

import akka.actor.{Actor, ActorLogging, ActorSystem, Status}
import akka.event.LoggingReceive
import org.knora.webapi.messages.store.redismessages.RedisPut
import org.knora.webapi.{KnoraDispatchers, Settings, UnexpectedMessageException}
import scalacache._
import scalacache.redis._
import scalacache.serialization.binary._

import scala.concurrent.ExecutionContext

class RedisManager extends Actor with ActorLogging {

    implicit val system: ActorSystem = context.system
    implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
    private val settings = Settings(system)

    implicit val redisCache: Cache[String] = RedisCache("localhost", 6379)

    def receive = LoggingReceive {
        case RedisPut[T](key, value:T) => ???
        case other => sender ! Status.Failure(UnexpectedMessageException(s"RedisManager received an unexpected message: $other"))
    }



}