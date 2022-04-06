/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.app

import akka.actor.Terminated
import org.knora.webapi.messages.app.appmessages.AppStart

import zio.config.typesafe.TypesafeConfig
import com.typesafe.config.ConfigFactory
import org.knora.webapi.config.AppConfig

import zio._
import org.knora.webapi.core.Logging
import java.util.concurrent.TimeUnit

/**
 * Starts Knora by bringing everything into scope by using the cake pattern.
 * The [[LiveCore]] trait provides an actor system and the main application
 * actor.
 */
object Main extends scala.App with LiveCore {

  /**
   * Loads the applicaton configuration using ZIO-Config. ZIO-Config is capable to load
   * the Typesafe-Config format.
   */
  val config = TypesafeConfig.fromTypesafeConfig(ConfigFactory.load().getConfig("app"), AppConfig.config)

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
