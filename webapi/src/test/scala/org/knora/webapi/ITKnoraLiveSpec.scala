/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import java.nio.file.{Files, Path, Paths}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.stream.Materializer
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.app.ApplicationActor
import org.knora.webapi.core.Core
import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.app.appmessages.{AppStart, AppStop, SetAllowReloadOverHTTPState}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.messages.store.sipimessages._
import org.knora.webapi.messages.store.sipimessages.SipiUploadResponseJsonProtocol._
import org.knora.webapi.messages.util.rdf.{JsonLDDocument, JsonLDUtil, RdfFeatureFactory}
import org.knora.webapi.settings._
import org.knora.webapi.util.StartupUtils
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Suite}
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.languageFeature.postfixOps
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.core.Logging
import zio.RuntimeConfig
import zio.ZEnvironment
import zio.ZIO
import zio.Runtime
import org.knora.webapi.testservices.SipiTestClientService
import org.knora.webapi.testservices.FileToUpload
import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.testcontainers.SipiTestContainer
import zio.Runtime
import zio.&
import org.knora.webapi.store.cacheservice.CacheServiceManager
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.cacheservice.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import zio.ZLayer
import org.knora.webapi.config.AppConfig

object ITKnoraLiveSpec {
  val defaultConfig: Config = ConfigFactory.load()
}

/**
 * This class can be used in End-to-End testing. It starts the Knora server and
 * provides access to settings and logging.
 */
class ITKnoraLiveSpec(_system: ActorSystem)
    extends TestKit(_system)
    with Core
    with StartupUtils
    with Suite
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with RequestBuilding
    with TriplestoreJsonProtocol
    with LazyLogging {

  /* constructors */
  def this(name: String, config: Config) =
    this(
      ActorSystem(name, TestContainerFuseki.PortConfig.withFallback(config.withFallback(ITKnoraLiveSpec.defaultConfig)))
    )
  def this(config: Config) =
    this(
      ActorSystem(
        "IntegrationTests",
        TestContainerFuseki.PortConfig.withFallback(config.withFallback(ITKnoraLiveSpec.defaultConfig))
      )
    )
  def this(name: String) =
    this(ActorSystem(name, TestContainerFuseki.PortConfig.withFallback(ITKnoraLiveSpec.defaultConfig)))
  def this() =
    this(ActorSystem("IntegrationTests", TestContainerFuseki.PortConfig.withFallback(ITKnoraLiveSpec.defaultConfig)))

  /* needed by the core trait (represents the KnoraTestCore trait)*/
  implicit lazy val settings: KnoraSettingsImpl   = KnoraSettings(system)
  implicit val materializer: Materializer         = Materializer.matFromSystem(system)
  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  // can be overridden in individual spec
  lazy val rdfDataObjects = Seq.empty[RdfDataObject]

  /* Needs to be initialized before any responders */
  StringFormatter.initForTest()
  RdfFeatureFactory.init(settings)

  val log: LoggingAdapter = akka.event.Logging(system, this.getClass)

// The ZIO runtime used to run functional effects
  val runtime = Runtime(ZEnvironment.empty, RuntimeConfig.default @@ Logging.live)

  /**
   * The effect for building a cache service manager, a IIIF service manager,
   * and the sipi test client.
   */
  val managers = for {
    csm        <- ZIO.service[CacheServiceManager]
    iiifsm     <- ZIO.service[IIIFServiceManager]
    appConfig <- ZIO.service[AppConfig]
  } yield (csm, iiifsm, appConfig)

  /**
   * The effect layers which will be used to run the managers effect.
   * Can be overriden in specs that need other implementations.
   */
  val effectLayers =
    ZLayer.make[CacheServiceManager & IIIFServiceManager & AppConfig](
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer,
      IIIFServiceManager.layer,
      IIIFServiceSipiImpl.layer, // alternative: MockSipiImpl.layer
      AppConfigForTestContainers.testcontainers,
      JWTService.layer,
      // FusekiTestContainer.layer,
      SipiTestContainer.layer,
      SipiTestClientService.layer
    )

  /**
   * Create both managers and the sipi client by unsafe running them.
   */
  val (cacheServiceManager, iiifServiceManager, appConfig) =
    runtime
      .unsafeRun(
        managers
          .provide(
            effectLayers
          )
      )

  // start the Application Actor
  lazy val appActor: ActorRef =
    system.actorOf(
      Props(new ApplicationActor(cacheServiceManager, iiifServiceManager)),
      name = APPLICATION_MANAGER_ACTOR_NAME
    )

  protected val baseApiUrl: String          = settings.internalKnoraApiBaseUrl
  protected val baseInternalSipiUrl: String = settings.internalSipiBaseUrl
  protected val baseExternalSipiUrl: String = settings.externalSipiBaseUrl

  override def beforeAll(): Unit = {

    // set allow reload over http
    appActor ! SetAllowReloadOverHTTPState(true)

    // Start Knora, reading data from the repository
    appActor ! AppStart(ignoreRepository = true, requiresIIIFService = true)

    // waits until knora is up and running
    applicationStateRunning()

    // loadTestData
    loadTestData(rdfDataObjects)
  }

  override def afterAll(): Unit = {
    // turn on/off in logback-test.xml
    log.debug(TestContainersAll.SipiContainer.getLogs())
    /* Stop the server when everything else has finished */
    TestKit.shutdownActorSystem(system)
  }

  protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit = {
    logger.info("Loading test data started ...")
    val request = Post(
      baseApiUrl + "/admin/store/ResetTriplestoreContent",
      HttpEntity(ContentTypes.`application/json`, rdfDataObjects.toJson.compactPrint)
    )
    singleAwaitingRequest(request, 479999.milliseconds)
    logger.info("... loading test data done.")
  }

  protected def getResponseStringOrThrow(request: HttpRequest): String = {
    val response: HttpResponse = singleAwaitingRequest(request)
    val responseBodyStr: String =
      Await.result(response.entity.toStrict(10999.seconds).map(_.data.decodeString("UTF-8")), 10.seconds)

    if (response.status.isSuccess) {
      responseBodyStr
    } else {
      throw AssertionException(
        s"Got HTTP ${response.status.intValue}\n REQUEST: $request,\n RESPONSE: $responseBodyStr"
      )
    }
  }

  protected def checkResponseOK(request: HttpRequest): Unit =
    getResponseStringOrThrow(request)

  protected def getResponseJson(request: HttpRequest): JsObject =
    getResponseStringOrThrow(request).parseJson.asJsObject

  protected def singleAwaitingRequest(request: HttpRequest, duration: Duration = 15999.milliseconds): HttpResponse = {
    val responseFuture: Future[HttpResponse] = Http().singleRequest(request)
    Await.result(responseFuture, duration)
  }

  protected def getResponseJsonLD(request: HttpRequest): JsonLDDocument = {
    val responseBodyStr = getResponseStringOrThrow(request)
    JsonLDUtil.parseJsonLD(responseBodyStr)
  }

  protected def uploadToSipi(loginToken: String, filesToUpload: Seq[FileToUpload]): SipiUploadResponse =
    sipiClient.uploadToSipi(loginToken, filesToUpload)

}
