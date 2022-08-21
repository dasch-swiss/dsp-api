/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import akka.dispatch.MessageDispatcher
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.settings.KnoraDispatchers
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.ActorRef
import org.knora.webapi.core.domain.AppState
import org.knora.webapi.core.domain.GetAppState
import akka.testkit.TestKitBase

/**
 * This trait is only used for testing. It is necessary so that E2E tests will only start
 * after the KnoraService is ready.
 */
trait StartupUtils extends LazyLogging {
  this: TestKitBase =>
  /**
   * Returns only when the application state is 'Running'.
   */
  def applicationStateRunning(appActor: ActorRef): Unit = {

    val state: AppState =
      Await
        .result(
          appActor.ask(GetAppState())(Timeout(5.second)),
          Timeout(10.second).duration
        )
        .asInstanceOf[AppState]

    if (state != AppState.Running) {
      // not in running state
      // we should wait a bit before we call ourselves again
      Await.result(blockingFuture(), 3.5.second)
      applicationStateRunning(appActor)
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
