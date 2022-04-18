/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.actor.{ActorRef, ActorSystem}
import akka.dispatch.MessageDispatcher
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AsyncWordSpecLike
import zio.Console.printLine
import zio.Schedule.{Decision, WithState}
import zio.{Schedule, _}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
import org.knora.webapi.core.Logging
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.store.cacheservice.CacheServiceManager
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.cacheservice.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.auth.JWTService
import org.knora.webapi.testcontainers.SipiTestContainer
import akka.actor.Props
import org.knora.webapi.app.ApplicationActor

import org.knora.webapi.settings._
import org.knora.webapi.core.Core
import org.knora.webapi.util.StartupUtils
import akka.testkit.TestKit
import org.knora.webapi.messages.app.appmessages.SetAllowReloadOverHTTPState
import org.knora.webapi.messages.app.appmessages.AppStart
import akka.stream.Materializer

object IntegrationSpec {

  /*
        Loads the following (first-listed are higher priority):
            - system properties (e.g., -Dconfig.resource=fuseki.conf)
            - test/resources/application.conf
            - main/resources/application.conf
   */
  val defaultConfig: Config = ConfigFactory.load()

  /* Copied from: akka/akka-testkit/src/test/scala/akka/testkit/AkkaSpec.scala */
  def getCallerName(clazz: Class[_]): String = {
    val s = (Thread.currentThread.getStackTrace map (_.getClassName) drop 1)
      .dropWhile(_ matches "(java.lang.Thread|.*UnitSpec.?$)")
    val reduced = s.lastIndexWhere(_ == clazz.getName) match {
      case -1 => s
      case z  => s drop (z + 1)
    }
    reduced.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
  }
}

/**
 * Defines a base class used in tests where only a running Fuseki Container is needed.
 * Does not start the DSP-API server.
 */
abstract class IntegrationSpec(_system: ActorSystem)
    extends TestKit(_system)
    with Core
    with StartupUtils
    with AsyncWordSpecLike
    with TriplestoreJsonProtocol
    with Matchers
    with BeforeAndAfterAll
    with LazyLogging {

  /* constructors */
  def this(name: String, config: Config) =
    this(ActorSystem(name, TestContainerFuseki.PortConfig.withFallback(config.withFallback(E2ESpec.defaultConfig))))

  def this(config: Config) =
    this(
      ActorSystem(
        "IntegrationSpec",
        TestContainerFuseki.PortConfig.withFallback(config.withFallback(E2ESpec.defaultConfig))
      )
    )

  def this(name: String) = this(ActorSystem(name, TestContainerFuseki.PortConfig.withFallback(E2ESpec.defaultConfig)))

  def this() = this(ActorSystem("IntegrationSpec", TestContainerFuseki.PortConfig.withFallback(E2ESpec.defaultConfig)))

  /* needed by the core trait */
  implicit lazy val settings: KnoraSettingsImpl = KnoraSettings(system)
  implicit val materializer: Materializer       = Materializer.matFromSystem(system)
  implicit override val executionContext        = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)
  implicit val timeout: Timeout                 = settings.defaultTimeout

  // can be overridden in individual spec
  lazy val rdfDataObjects = Seq.empty[RdfDataObject]

  // needs to be initialized early on
  StringFormatter.initForTest()

  // The ZIO runtime used to run functional effects
  val runtime = Runtime(ZEnvironment.empty, RuntimeConfig.default @@ Logging.testing)

  // The effect for building a cache service manager and a IIIF service manager.
  val managers = for {
    csm       <- ZIO.service[CacheServiceManager]
    iiifsm    <- ZIO.service[IIIFServiceManager]
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
      SipiTestContainer.layer
    )

  /**
   * Create both managers by unsafe running them.
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
      Props(new ApplicationActor(cacheServiceManager, iiifServiceManager, appConfig)),
      name = APPLICATION_MANAGER_ACTOR_NAME
    )

  override def beforeAll(): Unit = {

    // set allow reload over http
    appActor ! SetAllowReloadOverHTTPState(true)

    // start the knora service, loading data from the repository
    appActor ! AppStart(ignoreRepository = true, requiresIIIFService = true)

    // waits until knora is up and running
    applicationStateRunning()

    // loadTestData
    loadTestData(rdfDataObjects)

  }

  override def afterAll(): Unit =
    /* Stop the server when everything else has finished */
    TestKit.shutdownActorSystem(system)

  protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit =
    runtime.unsafeRunTask(
      (for {
        testClient <- ZIO.service[TestClientService]
        result     <- testClient.loadTestData(rdfDataObjects)
      } yield result).provide(TestClientService.layer(appConfig, system))
    )
}
