/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.actor
import akka.testkit.ImplicitSender
import akka.testkit.TestKitBase
import com.typesafe.scalalogging.Logger
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import zio._
import zio.logging.slf4j.bridge.Slf4jBridge

import scala.concurrent.ExecutionContext

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.settings.KnoraSettings
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.store.cache.settings.CacheServiceSettings

abstract class CoreSpec
    extends AnyWordSpec
    with TestKitBase
    with TestStartupUtils
    with Matchers
    with BeforeAndAfterAll
    with ImplicitSender {

  /**
   * The `Environment` that we require to exist at startup.
   * Can be overriden in specs that need other implementations.
   */
  type Environment = core.LayersTest.DefaultTestEnvironmentWithoutSipi

  /**
   * The effect layers from which the App is built.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers = core.LayersTest.defaultLayersTestWithoutSipi

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  private val bootstrap: ZLayer[
    Any,
    Any,
    Environment
  ] =
    ZLayer.empty ++ Runtime.removeDefaultLoggers ++ logging.consoleJson() ++ Slf4jBridge.initialize ++ effectLayers

  // no idea why we need that, but we do
  private val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  // add scope to bootstrap
  private val bootstrapWithScope = Scope.default >>>
    bootstrap +!+ ZLayer.environment[Scope]

  // maybe a configured runtime?
  val runtime = Unsafe.unsafe { implicit u =>
    Runtime.unsafe
      .fromLayer(bootstrapWithScope)
  }

  println("after configuring the runtime")

  // An effect for getting stuff out, so that we can pass them
  // to some legacy code
  private val routerAndConfig = for {
    router <- ZIO.service[core.AppRouter]
    config <- ZIO.service[AppConfig]
  } yield (router, config)

  println("before running routerAndConfig")

  /**
   * Create router and config by unsafe running them.
   */
  private val (router, config) =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          routerAndConfig
        )
        .getOrThrowFiberFailure()
    }

  println(router.ref)

  println("before running AppServer")

  // this effect represents our application
  private val appServerTest =
    for {
      _     <- core.AppServer.start(false, false)
      _     <- prepareRepository(rdfDataObjects) // main difference to the live version
      never <- ZIO.never
    } yield never

  /* Here we start our main effect in a separate fiber */
  val appServerTestF: CancelableFuture[Nothing] = Unsafe.unsafe { implicit u =>
    runtime.unsafe.runToFuture(appServerTest)
  }

  implicit lazy val system: actor.ActorSystem          = router.system
  implicit lazy val executionContext: ExecutionContext = system.dispatcher
  implicit lazy val settings: KnoraSettingsImpl        = KnoraSettings(system)
  lazy val rdfDataObjects                              = List.empty[RdfDataObject]
  val log: Logger                                      = Logger(this.getClass())
  val appActor                                         = router.ref

  // needed by some tests
  val cacheServiceSettings = new CacheServiceSettings(system.settings.config)
  val responderData        = ResponderData(system, appActor, settings, cacheServiceSettings)

  final override def beforeAll(): Unit =
    // waits until knora is up and running
    applicationStateRunning(appActor, system)

  final override def afterAll(): Unit = {

    appServerTestF.cancel()

    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.shutdown()
    }
  }

}
