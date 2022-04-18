/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.app

import akka.actor.Terminated
import com.typesafe.config.ConfigFactory
import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.Logging
import org.knora.webapi.messages.app.appmessages.AppStart
import org.knora.webapi.store.cacheservice.CacheServiceManager
import org.knora.webapi.store.cacheservice.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import zio._
import zio.config.typesafe.TypesafeConfig

import java.util.concurrent.TimeUnit

/**
 * Starts Knora by bringing everything into scope by using the cake pattern.
 * The [[LiveCore]] trait provides an actor system and the main application
 * actor.
 */
object Main extends scala.App with LiveCore {

  // The ZIO runtime used to run functional effects
  val runtime = Runtime(ZEnvironment.empty, RuntimeConfig.default @@ Logging.live)

  // The effect for building a cache service manager, a IIIF service manager, and AppConfig.
  val managers = for {
    csm       <- ZIO.service[CacheServiceManager]
    iiifsm    <- ZIO.service[IIIFServiceManager]
    appConfig <- ZIO.service[AppConfig]
  } yield (csm, iiifsm, appConfig)

  /**
   * Create both managers by unsafe running them.
   */
  val (cacheServiceManager, iiifServiceManager, appConfig) =
    runtime
      .unsafeRun(
        managers
          .provide(
            CacheServiceInMemImpl.layer,
            CacheServiceManager.layer,
            AppConfig.live,
            IIIFServiceManager.layer,
            IIIFServiceSipiImpl.layer,
            JWTService.layer
          )
      )

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
    })
  )

  system.registerOnTermination {
    println("ActorSystem terminated")
  }
}
