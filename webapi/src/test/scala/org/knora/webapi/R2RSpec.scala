/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import java.nio.file.{Files, Path, Paths}

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.app.ApplicationActor
import org.knora.webapi.core.Core
import org.knora.webapi.feature.{FeatureFactoryConfig, KnoraSettingsFeatureFactoryConfig, TestFeatureFactoryConfig}
import org.knora.webapi.http.handler
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.app.appmessages.{AppStart, AppStop, SetAllowReloadOverHTTPState}
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetRepositoryContent}
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl, _}
import org.knora.webapi.util.{FileUtil, StartupUtils}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, Suite}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

import zio.Runtime
import zio.&
import zio.ZEnvironment
import zio.RuntimeConfig
import org.knora.webapi.core.Logging
import zio.ZIO
import org.knora.webapi.store.cacheservice.CacheServiceManager
import zio.ZLayer
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.cacheservice.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.auth.JWTService
import org.knora.webapi.testcontainers.SipiTestContainer
import org.knora.webapi.config.AppConfig
import org.knora.webapi.testservices.TestClientService

/**
 * R(oute)2R(esponder) Spec base class. Please, for any new E2E tests, use E2ESpec.
 */
class R2RSpec
    extends Core
    with StartupUtils
    with Suite
    with ScalatestRouteTest
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with LazyLogging {

  /* needed by the core trait */
  implicit lazy val _system: ActorSystem = ActorSystem(
    actorSystemNameFrom(getClass),
    TestContainerFuseki.PortConfig.withFallback(
      ConfigFactory.parseString(testConfigSource).withFallback(ConfigFactory.load())
    )
  )

  implicit lazy val settings: KnoraSettingsImpl = KnoraSettings(_system)

  StringFormatter.initForTest()
  RdfFeatureFactory.init(settings)

  protected val defaultFeatureFactoryConfig: FeatureFactoryConfig = new TestFeatureFactoryConfig(
    testToggles = Set.empty,
    parent = new KnoraSettingsFeatureFactoryConfig(settings)
  )

  lazy val executionContext: ExecutionContext = _system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  // override so that we can use our own system
  override def createActorSystem(): ActorSystem = _system

  def actorRefFactory: ActorSystem = _system

  implicit val knoraExceptionHandler: ExceptionHandler = handler.KnoraExceptionHandler(settings)

  implicit val timeout: Timeout = Timeout(settings.defaultTimeout)

  // The ZIO runtime used to run functional effects
  val runtime = Runtime(ZEnvironment.empty, RuntimeConfig.default @@ Logging.fromInfo)

  // The effect for building a cache service manager and a IIIF service manager.
  lazy val managers = for {
    csm       <- ZIO.service[CacheServiceManager]
    iiifsm    <- ZIO.service[IIIFServiceManager]
    appConfig <- ZIO.service[AppConfig]
  } yield (csm, iiifsm, appConfig)

  /**
   * The effect layers which will be used to run the managers effect.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers =
    ZLayer.make[CacheServiceManager & IIIFServiceManager & AppConfig](
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer,
      IIIFServiceManager.layer,
      IIIFServiceSipiImpl.layer, // alternative: MockSipiImpl.layer
      AppConfigForTestContainers.testcontainers,
      JWTService.layer,
      // FusekiTestContainer.layer,
      SipiTestContainer.layer
    )

  /**
   * Create both managers by unsafe running them.
   */
  lazy val (cacheServiceManager, iiifServiceManager, appConfig) =
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
      Props(new ApplicationActor(cacheServiceManager, iiifServiceManager, appConfig)),
      name = APPLICATION_MANAGER_ACTOR_NAME
    )

  // The main application actor forwards messages to the responder manager and the store manager.
  val responderManager: ActorRef = appActor
  val storeManager: ActorRef     = appActor

  val routeData: KnoraRouteData = KnoraRouteData(
    system = system,
    appActor = appActor
  )

  lazy val rdfDataObjects = List.empty[RdfDataObject]

  val log: LoggingAdapter = akka.event.Logging(system, this.getClass)

  override def beforeAll(): Unit = {
    // set allow reload over http
    appActor ! SetAllowReloadOverHTTPState(true)

    // start the knora service, loading data from the repository
    appActor ! AppStart(ignoreRepository = true, requiresIIIFService = false)

    // waits until knora is up and running
    applicationStateRunning()

    loadTestData(rdfDataObjects)
  }

  override def afterAll(): Unit =
    /* Stop the server when everything else has finished */
    appActor ! AppStop()

  protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit =
    runtime.unsafeRunTask(
      (for {
        testClient <- ZIO.service[TestClientService]
        result     <- testClient.loadTestData(rdfDataObjects)
      } yield result).provide(TestClientService.layer(appConfig, system))
    )

  protected def responseToJsonLDDocument(httpResponse: HttpResponse): JsonLDDocument = {
    val responseBodyFuture: Future[String] = httpResponse.entity.toStrict(5.seconds).map(_.data.decodeString("UTF-8"))
    val responseBodyStr                    = Await.result(responseBodyFuture, 5.seconds)
    JsonLDUtil.parseJsonLD(responseBodyStr)
  }

  protected def parseTurtle(turtleStr: String): RdfModel = {
    val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(defaultFeatureFactoryConfig)
    rdfFormatUtil.parseToRdfModel(rdfStr = turtleStr, rdfFormat = Turtle)
  }

  protected def parseRdfXml(rdfXmlStr: String): RdfModel = {
    val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil(defaultFeatureFactoryConfig)
    rdfFormatUtil.parseToRdfModel(rdfStr = rdfXmlStr, rdfFormat = RdfXml)
  }

  /**
   * Reads or writes a test data file.
   *
   * @param responseAsString the API response received from Knora.
   * @param file             the file in which the expected API response is stored.
   * @param writeFile        if `true`, writes the response to the file and returns it, otherwise returns the current contents of the file.
   * @return the expected response.
   */
  protected def readOrWriteTextFile(responseAsString: String, file: Path, writeFile: Boolean = false): String =
    if (writeFile) {
      val testOutputDir = Paths.get("..", "test_data", "ontologyR2RV2")
      val newOutputFile = testOutputDir.resolve(file)
      Files.createDirectories(newOutputFile.getParent)
      FileUtil.writeTextFile(
        newOutputFile,
        responseAsString.replaceAll(settings.externalSipiIIIFGetUrl, "IIIF_BASE_URL")
      )
      responseAsString
    } else {
      FileUtil.readTextFile(file).replaceAll("IIIF_BASE_URL", settings.externalSipiIIIFGetUrl)
    }
}
