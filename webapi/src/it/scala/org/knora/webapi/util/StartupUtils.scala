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

import akka.dispatch.MessageDispatcher
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.messages.app.appmessages.AppState.AppState
import org.knora.webapi.messages.app.appmessages.{AppState, GetAppState}
import org.knora.webapi.{Core, KnoraDispatchers, KnoraLiveService}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * This trait is only used for testing. It is necessary so that E2E tests will only start
  * after the KnoraService is ready.
  */
trait StartupUtils extends LazyLogging {
    this: Core with KnoraLiveService =>

    /**
      * Returns only when the application state is 'Running'.
      */
    def applicationStateRunning(): Unit = {

        implicit val timeout: Timeout = Timeout(5.second)
        val state: AppState = Await.result(appActor ? GetAppState(), timeout.duration).asInstanceOf[AppState]

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

        implicit val ctx: MessageDispatcher = system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)

        Future {
            // uses the good "blocking dispatcher" that we configured,
            // instead of the default dispatcher to isolate the blocking.
            Thread.sleep(delay)
            Future.successful(())
        }
    }

}
