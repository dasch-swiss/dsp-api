/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.responders

import akka.actor.{ActorRef, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.util.FastFuture
import akka.util.Timeout
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.{KnoraDispatchers, Settings, UnexpectedMessageException}

import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

/**
  * Responder helper methods.
  */
object Responder {

    /**
      * An responder use this method to handle unexpected request messages in a consistent way.
      *
      * @param message the message that was received.
      * @param log     a [[LoggingAdapter]].
      * @param who     the responder receiving the message.
      */
    def handleUnexpectedMessage(message: Any, log: LoggingAdapter, who: String): Future[Nothing] = {
        val unexpectedMessageException = UnexpectedMessageException(s"$who received an unexpected message $message of type ${message.getClass.getCanonicalName}")
        FastFuture.failed(unexpectedMessageException)
    }

}

/**
  * Data needed to be passed to each responder.
  *
  * @param system the actor system.
  * @param applicationStateActor the application state actor ActorRef.
  * @param responderManager the responder manager ActorRef.
  * @param storeManager the store manager ActorRef.
  */
case class ResponderData(system: ActorSystem, applicationStateActor: ActorRef, responderManager: ActorRef, storeManager: ActorRef)

/**
  * An abstract class providing values that are commonly used in Knora responders.
  */
abstract class Responder(responderData: ResponderData) {

    /**
      * The actor system.
      */
    protected implicit val system: ActorSystem = responderData.system

    /**
      * The execution context for futures created in Knora actors.
      */
    protected implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

    /**
      * The application settings.
      */
    protected val settings = Settings(system)

    /**
      * The reference to the responder manager.
      */
    protected val responderManager: ActorRef = responderData.responderManager

    /**
      * The reference to the store manager.
      */
    protected val storeManager = responderData.storeManager

    /**
      * The reference to the application state actor
      */
    protected val applicationStateActor = responderData.applicationStateActor

    /**
      * A string formatter.
      */
    protected implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    /**
      * The application's default timeout for `ask` messages.
      */
    protected implicit val timeout: Timeout = settings.defaultTimeout

    /**
      * Provides logging
      */
    val log: LoggingAdapter = akka.event.Logging(system, this.getClass.getName)
}


