/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.testkit.TestKitBase
import com.typesafe.scalalogging.LazyLogging
import com.typesafe.scalalogging.Logger
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import spray.json._
import zio._
import zio.logging.slf4j.bridge.Slf4jBridge

import scala.concurrent.ExecutionContext

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.settings._
import org.knora.webapi.testservices.FileToUpload
import org.knora.webapi.testservices.TestClientService

/**
 * This class can be used in End-to-End testing. It starts the Knora server and
 * provides access to settings and logging.
 */
abstract class ITKnoraLiveSpec
    extends AnyWordSpec
    with TestKitBase
    with TestStartupUtils
    with Matchers
    with BeforeAndAfterAll
    with RequestBuilding
    with TriplestoreJsonProtocol
    with LazyLogging {

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
    Scope,
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
  val routerAndConfig = for {
    router <- ZIO.service[core.AppRouter]
    config <- ZIO.service[AppConfig]
  } yield (router, config)

  println("before running routerAndConfig")

  /**
   * Create router and config by unsafe running them.
   */
  val (router, config) =
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
  Unsafe.unsafe { implicit u =>
    runtime.unsafe.fork(appServerTest)
  }

  implicit lazy val system: akka.actor.ActorSystem     = router.system
  implicit lazy val executionContext: ExecutionContext = system.dispatcher
  implicit lazy val settings: KnoraSettingsImpl        = KnoraSettings(system)
  lazy val rdfDataObjects                              = List.empty[RdfDataObject]
  val log: Logger                                      = Logger(this.getClass())
  val appActor                                         = router.ref

  // needed by some tests
  val baseApiUrl          = settings.internalKnoraApiBaseUrl
  val baseInternalSipiUrl = settings.internalSipiBaseUrl

  final override def beforeAll(): Unit =
    // waits until knora is up and running
    applicationStateRunning(appActor, system)

  // loadTestData
  // loadTestData(rdfDataObjects)

  final override def afterAll(): Unit =
    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.shutdown()
    }

  protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.loadTestData(rdfDataObjects)
          } yield result
        )
        .getOrThrowFiberFailure()
    }

  protected def getResponseStringOrThrow(request: HttpRequest): String =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.getResponseString(request)
          } yield result
        )
        .getOrThrowFiberFailure()
    }

  protected def checkResponseOK(request: HttpRequest): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.checkResponseOK(request)
          } yield result
        )
        .getOrThrowFiberFailure()
    }

  protected def getResponseJson(request: HttpRequest): JsObject =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.getResponseJson(request)
          } yield result
        )
        .getOrThrowFiberFailure()
    }

  protected def singleAwaitingRequest(request: HttpRequest, duration: zio.Duration = 15.seconds): HttpResponse =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.singleAwaitingRequest(request, duration)
          } yield result
        )
        .getOrThrowFiberFailure()
    }

  protected def getResponseJsonLD(request: HttpRequest): JsonLDDocument =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.getResponseJsonLD(request)
          } yield result
        )
        .getOrThrowFiberFailure()
    }

  protected def uploadToSipi(loginToken: String, filesToUpload: Seq[FileToUpload]): SipiUploadResponse =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.uploadToSipi(loginToken, filesToUpload)
          } yield result
        )
        .getOrThrow()
    }

}
