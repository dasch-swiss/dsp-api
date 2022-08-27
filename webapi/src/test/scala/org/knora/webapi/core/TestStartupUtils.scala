/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import akka.actor.ActorRef
import akka.dispatch.MessageDispatcher
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import zio._

import scala.concurrent.Await
import scala.concurrent.Future

import org.knora.webapi.core.AppRouter
import org.knora.webapi.core.domain.AppState
import org.knora.webapi.core.domain.GetAppState
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.store.triplestore.api.TriplestoreService

/**
 * This trait is only used for testing. It is necessary so that E2E tests will only start
 * after the KnoraService is ready.
 */
trait TestStartupUtils extends LazyLogging {
  this: ZIOApp =>

  /**
   * Returns only when the application state is 'Running'.
   */
  def applicationStateRunning(appActor: ActorRef, system: akka.actor.ActorSystem): Unit = {

    val state: AppState =
      Await
        .result(
          appActor.ask(GetAppState())(
            Timeout(new scala.concurrent.duration.FiniteDuration(5, scala.concurrent.duration.SECONDS))
          ),
          new scala.concurrent.duration.FiniteDuration(10, scala.concurrent.duration.SECONDS)
        )
        .asInstanceOf[AppState]

    if (state != AppState.Running) {
      // not in running state
      // we should wait a bit before we call ourselves again
      Await.result(
        blockingFuture(system: akka.actor.ActorSystem),
        new scala.concurrent.duration.FiniteDuration(3L, scala.concurrent.duration.SECONDS)
      )
      applicationStateRunning(appActor, system)
    }
  }

  /**
   * A blocking future running on the blocking dispatcher.
   */
  private def blockingFuture(system: akka.actor.ActorSystem): Future[Unit] = {

    implicit val ctx: MessageDispatcher = system.dispatchers.lookup(KnoraDispatchers.KnoraBlockingDispatcher)

    Future {
      // uses the good "blocking dispatcher" that we configured,
      // instead of the default dispatcher to isolate the blocking.
      Thread.sleep(3000L)
      Future.successful(())
    }
  }

  /**
   * Load the test data and caches
   *
   * @param rdfDataObjects a list of [[RdfDataObject]]
   */
  def prepareRepository(rdfDataObjects: List[RdfDataObject]): ZIO[TriplestoreService with AppRouter, Nothing, Unit] =
    for {
      _         <- ZIO.logInfo("Loading test data started ...")
      tss       <- ZIO.service[TriplestoreService]
      _         <- tss.resetTripleStoreContent(rdfDataObjects).timeout(480.seconds)
      _         <- ZIO.logInfo("... loading test data done.")
      _         <- ZIO.logInfo("Loading load ontologies into cache started ...")
      appRouter <- ZIO.service[AppRouter]
      _         <- appRouter.populateOntologyCaches
      _         <- ZIO.logInfo("... loading ontologies into cache done.")
    } yield ()

}
