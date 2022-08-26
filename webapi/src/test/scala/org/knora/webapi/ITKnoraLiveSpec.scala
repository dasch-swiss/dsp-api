/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.scalalogging.Logger
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.testkit.TestKit
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.RdfFeatureFactory
import org.knora.webapi.settings._
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.testcontainers.SipiTestContainer
import org.knora.webapi.testservices.FileToUpload
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.core.TestStartupUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import spray.json._
import zio._

import scala.concurrent.ExecutionContext
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceHttpConnectorImpl
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.testcontainers.FusekiTestContainer
import akka.testkit.TestKitBase
import scala.concurrent.Await
import scala.concurrent.ExecutionContextExecutor

/**
 * This class can be used in End-to-End testing. It starts the Knora server and
 * provides access to settings and logging.
 */
abstract class ITKnoraLiveSpec
    extends ZIOApp
    with TestKitBase
    with TestStartupUtils
    with Suite
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with RequestBuilding
    with TriplestoreJsonProtocol
    with LazyLogging {

  implicit lazy val system: akka.actor.ActorSystem = akka.actor.ActorSystem("ITKnoraLiveSpec", ConfigFactory.load())
  implicit lazy val ec: ExecutionContextExecutor   = system.dispatcher
  implicit lazy val settings: KnoraSettingsImpl    = KnoraSettings(system)
  lazy val rdfDataObjects                          = List.empty[RdfDataObject]
  val log: Logger                                  = Logger(this.getClass())
  val baseApiUrl                                   = settings.internalKnoraApiBaseUrl
  val baseInternalSipiUrl                          = settings.internalSipiBaseUrl

  /**
   * The `Environment` that we require to exist at startup.
   */
  type Environment = core.TestLayers.DefaultTestEnvironmentWithSipi

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  override val bootstrap: ZLayer[
    ZIOAppArgs with Scope,
    Any,
    Environment
  ] =
    ZLayer.empty ++ Runtime.removeDefaultLoggers ++ logging.consoleJson() ++ effectLayers

  // no idea why we need that, but we do
  val environmentTag: EnvironmentTag[Environment] = EnvironmentTag[Environment]

  /* Here we start our TestApplication */
  val run =
    (for {
      _ <- ZIO.scoped(core.Startup.run(false, false))
      _ <- prepareRepository(rdfDataObjects)
      _ <- ZIO.never
    } yield ()).forever

  /**
   * The effect layers from which the App is built.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers = core.TestLayers.defaultTestLayersWithSipi(system)

  // The appActor is built inside the AppRouter layer. We need to surface the reference to it,
  // so that we can use it in tests. This seems the easies way of doing it at the moment.
  val appActorF = system
    .actorSelection(APPLICATION_MANAGER_ACTOR_PATH)
    .resolveOne(new scala.concurrent.duration.FiniteDuration(1, scala.concurrent.duration.SECONDS))
  val appActor =
    Await.result(appActorF, new scala.concurrent.duration.FiniteDuration(1, scala.concurrent.duration.SECONDS))

  final override def beforeAll(): Unit =
    // waits until knora is up and running
    applicationStateRunning(appActor, system)

  // loadTestData
  // loadTestData(rdfDataObjects)

  final override def afterAll(): Unit = {

    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    // Unsafe.unsafe { implicit u =>
    //  runtime.unsafe.shutdown()
    // }

    /* Stop the server when everything else has finished */
    // TestKit.shutdownActorSystem(system)

  }

  protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          (for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.loadTestData(rdfDataObjects)
          } yield result).provide(TestClientService.layer(system), AppConfig.test)
        )
        .getOrThrowFiberFailure()
    }

  protected def getResponseStringOrThrow(request: HttpRequest): String =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          (for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.getResponseString(request)
          } yield result).provide(TestClientService.layer(system), AppConfig.test)
        )
        .getOrThrowFiberFailure()
    }

  protected def checkResponseOK(request: HttpRequest): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          (for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.checkResponseOK(request)
          } yield result).provide(TestClientService.layer(system), AppConfig.test)
        )
        .getOrThrowFiberFailure()
    }

  protected def getResponseJson(request: HttpRequest): JsObject =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          (for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.getResponseJson(request)
          } yield result).provide(TestClientService.layer(system), AppConfig.test)
        )
        .getOrThrowFiberFailure()
    }

  protected def singleAwaitingRequest(request: HttpRequest, duration: zio.Duration = 15.seconds): HttpResponse =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          (for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.singleAwaitingRequest(request, duration)
          } yield result).provide(TestClientService.layer(system), AppConfig.test)
        )
        .getOrThrowFiberFailure()
    }

  protected def getResponseJsonLD(request: HttpRequest): JsonLDDocument =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          (for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.getResponseJsonLD(request)
          } yield result).provide(TestClientService.layer(system), AppConfig.test)
        )
        .getOrThrowFiberFailure()
    }

  protected def uploadToSipi(loginToken: String, filesToUpload: Seq[FileToUpload]): SipiUploadResponse =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          (for {
            testClient <- ZIO.service[TestClientService]
            result     <- testClient.uploadToSipi(loginToken, filesToUpload)
          } yield result).provide(TestClientService.layer(system), AppConfig.test)
        )
        .getOrThrow()
    }

}
