/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.app

import akka.actor.Terminated
import zio._

import java.util.concurrent.TimeUnit

import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.Logging
import org.knora.webapi.messages.app.appmessages.AppStart
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceHttpConnectorImpl
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater

/**
 * Starts Knora by bringing everything into scope by using the cake pattern.
 * The [[LiveCore]] trait provides an actor system and the main application
 * actor.
 */
object Main extends scala.App with LiveCore {

  /**
   * The effect layers which will be used to run the managers effect.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers =
    ZLayer.make[CacheServiceManager & IIIFServiceManager & TriplestoreServiceManager & AppConfig](
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer,
      IIIFServiceManager.layer,
      IIIFServiceSipiImpl.layer,
      AppConfig.live,
      JWTService.layer,
      TriplestoreServiceManager.layer,
      TriplestoreServiceHttpConnectorImpl.layer,
      RepositoryUpdater.layer,
      Logging.slf4j
    )

  /**
   * Unsafely creates a `Runtime` from a `ZLayer` whose resources will be
   * allocated immediately, and not released until the `Runtime` is shut down or
   * the end of the application.
   */
  lazy val runtime =
    Unsafe.unsafe { implicit u =>
      Runtime.unsafe.fromLayer(effectLayers ++ Runtime.removeDefaultLoggers)
    }

  // The effect for building a cache service manager, a IIIF service manager, and AppConfig.
  val managers = for {
    csm       <- ZIO.service[CacheServiceManager]
    iiifsm    <- ZIO.service[IIIFServiceManager]
    tssm      <- ZIO.service[TriplestoreServiceManager]
    appConfig <- ZIO.service[AppConfig]
  } yield (csm, iiifsm, tssm, appConfig)

  /**
   * Create both managers by unsafe running them.
   */
  val (cacheServiceManager, iiifServiceManager, triplestoreServiceManager, appConfig) =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          managers
        )
        .getOrElse(c => throw FiberFailure(c))
    }

  /**
   * Start server initialisation
   */
  appActor ! AppStart(ignoreRepository = false, requiresIIIFService = true)

  /**
   * Adds shutting down of our actor system to the shutdown hook.
   * Because we are blocking, we will run this on a separate thread.
   */
  scala.sys.addShutdownHook(
    new Thread(() => {
      import scala.concurrent._
      import scala.concurrent.duration._
      val terminate: Future[Terminated] = system.terminate()
      Await.result(terminate, Duration(30.toLong, TimeUnit.SECONDS))
      runtime.shutdown0()
    })
  )

  system.registerOnTermination {
    println("ActorSystem terminated")
  }
}
