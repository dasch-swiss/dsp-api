/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.ExceptionHandler
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.util.Timeout
import com.typesafe.scalalogging.Logger
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Suite
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import zio._

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.knora.webapi.app.ApplicationActor
import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.core.Core
import org.knora.webapi.core.Logging
import org.knora.webapi.http.handler
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.app.appmessages.AppStart
import org.knora.webapi.messages.app.appmessages.AppStop
import org.knora.webapi.messages.app.appmessages.SetAllowReloadOverHTTPState
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.settings.KnoraSettings
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.settings._
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceHttpConnectorImpl
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testservices.TestActorSystemService
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.util.FileUtil
import org.knora.webapi.util.StartupUtils

/**
 * R(oute)2R(esponder) Spec base class. Please, for any new E2E tests, use E2ESpec.
 */
abstract class R2RSpec
    extends Core
    with StartupUtils
    with Suite
    with ScalatestRouteTest
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  /* needed by the core trait */
  implicit lazy val _system: ActorSystem = ActorSystem(
    actorSystemNameFrom(getClass)
  )

  implicit lazy val settings: KnoraSettingsImpl = KnoraSettings(_system)

  StringFormatter.initForTest()

  val log: Logger                             = Logger(this.getClass())
  lazy val executionContext: ExecutionContext = _system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  // override so that we can use our own system
  override def createActorSystem(): ActorSystem = _system

  def actorRefFactory: ActorSystem = _system

  implicit val knoraExceptionHandler: ExceptionHandler = handler.KnoraExceptionHandler(settings)

  implicit val timeout: Timeout = Timeout(settings.defaultTimeout)

  // The effect for building a cache service manager and a IIIF service manager.
  lazy val managers = for {
    csm       <- ZIO.service[CacheServiceManager]
    iiifsm    <- ZIO.service[IIIFServiceManager]
    tssm      <- ZIO.service[TriplestoreServiceManager]
    appConfig <- ZIO.service[AppConfig]
  } yield (csm, iiifsm, tssm, appConfig)

  /**
   * The effect layers which will be used to run the managers effect.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers =
    ZLayer.make[CacheServiceManager & IIIFServiceManager & TriplestoreServiceManager & AppConfig & TestClientService](
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer,
      IIIFServiceManager.layer,
      IIIFServiceSipiImpl.layer, // alternative: MockSipiImpl.layer
      AppConfigForTestContainers.fusekiOnlyTestcontainer,
      JWTService.layer,
      TriplestoreServiceManager.layer,
      TriplestoreServiceHttpConnectorImpl.layer,
      RepositoryUpdater.layer,
      FusekiTestContainer.layer,
      Logging.slf4j,
      TestClientService.layer,
      TestActorSystemService.layer
    )

  // The ZIO runtime used to run functional effects
  lazy val runtime =
    Unsafe.unsafe { implicit u =>
      Runtime.unsafe.fromLayer(effectLayers ++ Runtime.removeDefaultLoggers)
    }

  /**
   * Create both managers by unsafe running them.
   */
  lazy val (cacheServiceManager, iiifServiceManager, triplestoreServiceManager, appConfig) =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(managers).getOrElse(c => throw FiberFailure(c))
    }

  // start the Application Actor
  lazy val appActor: ActorRef =
    system.actorOf(
      Props(new ApplicationActor(cacheServiceManager, iiifServiceManager, triplestoreServiceManager, appConfig)),
      name = APPLICATION_MANAGER_ACTOR_NAME
    )

  val routeData: KnoraRouteData = KnoraRouteData(
    system = system,
    appActor = appActor
  )

  lazy val rdfDataObjects = List.empty[RdfDataObject]

  final override def beforeAll(): Unit = {
    // set allow reload over http
    appActor ! SetAllowReloadOverHTTPState(true)

    // start the knora service, loading data from the repository
    appActor ! AppStart(ignoreRepository = true, requiresIIIFService = false)

    // waits until knora is up and running
    applicationStateRunning()

    loadTestData(rdfDataObjects)
  }

  final override def afterAll(): Unit = {

    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.shutdown()
    }
    /* Stop the server when everything else has finished */
    appActor ! AppStop()

  }

  protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run(
        (for {
          testClient <- ZIO.service[TestClientService]
          result     <- testClient.loadTestData(rdfDataObjects)
        } yield result)
      )
    }

  protected def responseToJsonLDDocument(httpResponse: HttpResponse): JsonLDDocument = {
    val responseBodyFuture: Future[String] =
      httpResponse.entity
        .toStrict(scala.concurrent.duration.Duration(5.toLong, TimeUnit.SECONDS))
        .map(_.data.decodeString("UTF-8"))
    val responseBodyStr =
      Await.result(responseBodyFuture, scala.concurrent.duration.Duration(5.toLong, TimeUnit.SECONDS))
    JsonLDUtil.parseJsonLD(responseBodyStr)
  }

  protected def parseTurtle(turtleStr: String): RdfModel = {
    val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()
    rdfFormatUtil.parseToRdfModel(rdfStr = turtleStr, rdfFormat = Turtle)
  }

  protected def parseRdfXml(rdfXmlStr: String): RdfModel = {
    val rdfFormatUtil: RdfFormatUtil = RdfFeatureFactory.getRdfFormatUtil()
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
