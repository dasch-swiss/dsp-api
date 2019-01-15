/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.util

import akka.pattern.{AskTimeoutException, ask}
import org.knora.webapi.messages.app.appmessages.AppState.AppState
import org.knora.webapi.messages.app.appmessages.{ActorReady, ActorReadyAck, AppState, GetAppState}
import org.knora.webapi.{Core, KnoraService}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * This trait is only used for testing. It is necessary so that E2E tests will only start
  * after the KnoraService is ready.
  */
trait StartupUtils {
    this: Core with KnoraService =>

    /**
      * Returns only when the application state actor is ready.
      */
    def applicationStateActorReady(): Unit = {

        try {
            Await.result(applicationStateActor ? ActorReady(), 1.second).asInstanceOf[ActorReadyAck]
            log.info("KnoraService - applicationStateActorReady")
        } catch {
            case e: AskTimeoutException => {
                // if we are here, then the ask timed out, so we need to try again until the actor is ready
                Await.result(blockingFuture(), 1.second)
                applicationStateActorReady()
            }
        }
    }

    /**
      * Returns only when the application state is 'Running'.
      */
    def applicationStateRunning(): Unit = {

        val state: AppState = Await.result(applicationStateActor ? GetAppState(), 1.second).asInstanceOf[AppState]

        if (state != AppState.Running) {
            // not in running state
            // we should wait a bit before we call ourselves again
            Await.result(blockingFuture(), 3.5.second)
            applicationStateRunning()
        }
    }


    /**
      * A blocking future running on the blocking dispatcher.
      */
    private def blockingFuture(): Future[Unit] = {

        val delay: Long = 3.second.toMillis

        Future {
            // uses the good "blocking dispatcher" that we configured,
            // instead of the default dispatcher to isolate the blocking.
            Thread.sleep(delay)
            Future.successful(())
        }
    }

}
