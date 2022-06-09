/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.scalalogging.Logger
import akka.pattern.ask
import akka.stream.Materializer
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import akka.util.Timeout
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.core.Logging
import org.knora.webapi.store.cacheservice.CacheServiceManager
import org.knora.webapi.store.cacheservice.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.testcontainers.SipiTestContainer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import zio.&
import zio.Runtime
import zio.ZEnvironment
import zio.ZIO
import zio.ZLayer

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import app.ApplicationActor
import core.Core
import feature.{FeatureFactoryConfig, KnoraSettingsFeatureFactoryConfig, TestFeatureFactoryConfig}
import messages.StringFormatter
import messages.app.appmessages.{AppStart, SetAllowReloadOverHTTPState}
import messages.store.cacheservicemessages.CacheServiceFlushDB
import messages.store.triplestoremessages.{RdfDataObject, ResetRepositoryContent}
import messages.util.rdf.RdfFeatureFactory
import messages.util.{KnoraSystemInstances, ResponderData}
import messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl, _}
import store.cacheservice.settings.CacheServiceSettings
import util.StartupUtils
import akka.testkit.TestActorRef

object CoreSpec {

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
      .dropWhile(_ matches "(java.lang.Thread|.*CoreSpec.?$)")
    val reduced = s.lastIndexWhere(_ == clazz.getName) match {
      case -1 => s
      case z  => s drop (z + 1)
    }
    reduced.head.replaceFirst(""".*\.""", "").replaceAll("[^a-zA-Z_0-9]", "_")
  }
}

abstract class CoreSpec(_system: ActorSystem)
    extends TestKit(_system)
    with Core
    with StartupUtils
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ImplicitSender {

  /* constructors - individual tests can override the configuration by giving their own */
  def this(name: String, config: Config) =
    this(
      ActorSystem(
        name,
        TestContainerFuseki.PortConfig.withFallback(ConfigFactory.load(config.withFallback(CoreSpec.defaultConfig)))
      )
    )

  def this(config: Config) =
    this(
      ActorSystem(
        CoreSpec.getCallerName(classOf[CoreSpec]),
        TestContainerFuseki.PortConfig.withFallback(ConfigFactory.load(config.withFallback(CoreSpec.defaultConfig)))
      )
    )

  def this(name: String) = this(ActorSystem(name, TestContainerFuseki.PortConfig.withFallback(ConfigFactory.load())))

  def this() =
    this(
      ActorSystem(
        CoreSpec.getCallerName(classOf[CoreSpec]),
        TestContainerFuseki.PortConfig.withFallback(ConfigFactory.load())
      )
    )

  /* needed by the core trait */
  implicit lazy val settings: KnoraSettingsImpl   = KnoraSettings(system)
  implicit val materializer: Materializer         = Materializer.matFromSystem(system)
  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  // can be overridden in individual spec
  lazy val rdfDataObjects = Seq.empty[RdfDataObject]

  // needs to be initialized early on
  StringFormatter.initForTest()
  RdfFeatureFactory.init(settings)

  val log: Logger = Logger(this.getClass)

  // The ZIO runtime used to run functional effects
  val runtime = Runtime.unsafeFromLayer(Logging.fromInfo)

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
  val (cacheServiceManager, iiifServiceManager, appConfig) =
    runtime
      .unsafeRun(
        managers
          .provide(
            effectLayers
          )
      )

  // start the Application Actor.
  lazy val appActor: ActorRef =
    system.actorOf(
      Props(new ApplicationActor(cacheServiceManager, iiifServiceManager, appConfig)),
      name = APPLICATION_MANAGER_ACTOR_NAME
    )

  val responderData: ResponderData = ResponderData(
    system = system,
    appActor = appActor,
    knoraSettings = settings,
    cacheServiceSettings = new CacheServiceSettings(system.settings.config)
  )

  protected val defaultFeatureFactoryConfig: FeatureFactoryConfig = new TestFeatureFactoryConfig(
    testToggles = Set.empty,
    parent = new KnoraSettingsFeatureFactoryConfig(settings)
  )

  final override def beforeAll(): Unit = {
    // Start Knora, without reading data from the repository
    appActor.tell(AppStart(ignoreRepository = true, requiresIIIFService = false), akka.actor.ActorRef.noSender)

    // set allow reload over http
    appActor.tell(SetAllowReloadOverHTTPState(true), akka.actor.ActorRef.noSender)

    // waits until knora is up and running
    applicationStateRunning()

    loadTestData(rdfDataObjects)

    // memusage()
  }

  final override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  protected def loadTestData(rdfDataObjects: Seq[RdfDataObject]): Unit = {
    logger.info("Loading test data started ...")
    implicit val timeout: Timeout = Timeout(settings.defaultTimeout)
    Try(Await.result(appActor ? ResetRepositoryContent(rdfDataObjects), 479999.milliseconds)) match {
      case Success(res) => logger.info("... loading test data done.")
      case Failure(e)   => logger.error(s"Loading test data failed: ${e.getMessage}")
    }

    logger.info("Loading load ontologies into cache started ...")
    Try(
      Await.result(
        appActor ? LoadOntologiesRequestV2(
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        ),
        1 minute
      )
    ) match {
      case Success(res) => logger.info("... loading ontologies into cache done.")
      case Failure(e)   => logger.error(s"Loading ontologies into cache failed: ${e.getMessage}")
    }

    logger.info("CacheServiceFlushDB started ...")
    Try(Await.result(appActor ? CacheServiceFlushDB(KnoraSystemInstances.Users.SystemUser), 15 seconds)) match {
      case Success(res) => logger.info("... CacheServiceFlushDB done.")
      case Failure(e)   => logger.error(s"CacheServiceFlushDB failed: ${e.getMessage}")
    }
  }
}
