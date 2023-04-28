/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.AppRouter
import org.knora.webapi.core.AppServer
import org.knora.webapi.core.TestStartupUtils
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.util.rdf.JsonLDDocument
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.testservices.FileToUpload
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.util.LogAspect

/**
 * This class can be used in End-to-End testing. It starts the DSP stack and
 * provides access to configuration and logging.
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
  type Environment = core.LayersTest.DefaultTestEnvironmentWithSipi

  /**
   * The effect layers from which the App is built.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers = core.LayersTest.integrationTestsWithSipiAndFusekiTestcontainers

  /**
   * `Bootstrap` will ensure that everything is instantiated when the Runtime is created
   * and cleaned up when the Runtime is shutdown.
   */
  private val bootstrap = util.Logger.text() >>> effectLayers

  // create a configured runtime
  implicit val runtime = Unsafe.unsafe(implicit u => Runtime.unsafe.fromLayer(bootstrap))

  // An effect for getting stuff out, so that we can pass them
  // to some legacy code
  val routerAndConfig = for {
    router <- ZIO.service[core.AppRouter]
    config <- ZIO.service[AppConfig]
  } yield (router, config)

  /**
   * Create router and config by unsafe running them.
   */
  val (router: AppRouter, config: AppConfig) =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          routerAndConfig
        )
        .getOrThrowFiberFailure()
    }

  implicit lazy val system: akka.actor.ActorSystem     = router.system
  implicit lazy val executionContext: ExecutionContext = system.dispatcher
  lazy val rdfDataObjects                              = List.empty[RdfDataObject]
  val log: Logger                                      = Logger(this.getClass())
  val appActor                                         = router.ref
  val appConfig                                        = config

  // needed by some tests
  val baseApiUrl          = config.knoraApi.internalKnoraApiBaseUrl
  val baseInternalSipiUrl = config.sipi.internalBaseUrl

  final override def beforeAll(): Unit =
    /* Here we start our app and initialize the repository before each suit runs */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          for {
            _ <- AppServer.testWithSipi
            _ <- prepareRepository(rdfDataObjects) @@ LogAspect.logSpan("prepare-repo")
          } yield ()
        )
        .getOrThrow()
    }

  final override def afterAll(): Unit =
    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.shutdown()
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

  protected def singleAwaitingRequest(request: HttpRequest, duration: Option[zio.Duration] = None): HttpResponse =
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

  protected def responseToString(httpResponse: HttpResponse): String = {
    val responseBodyFuture: Future[String] =
      httpResponse.entity.toStrict(FiniteDuration(10L, TimeUnit.SECONDS)).map(_.data.decodeString("UTF-8"))
    Await.result(responseBodyFuture, FiniteDuration(10L, TimeUnit.SECONDS))
  }

  protected def responseToJsonLDDocument(httpResponse: HttpResponse): JsonLDDocument = {
    val responseBodyFuture: Future[String] =
      httpResponse.entity.toStrict(FiniteDuration(10L, TimeUnit.SECONDS)).map(_.data.decodeString("UTF-8"))
    val responseBodyStr = Await.result(responseBodyFuture, FiniteDuration(10L, TimeUnit.SECONDS))
    JsonLDUtil.parseJsonLD(responseBodyStr)
  }

}
