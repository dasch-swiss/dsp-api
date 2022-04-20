/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.event.LoggingAdapter
import akka.pattern.ask
import akka.stream.Materializer
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.app.ApplicationActor
import org.knora.webapi.core.Core
import org.knora.webapi.feature.{FeatureFactoryConfig, KnoraSettingsFeatureFactoryConfig, TestFeatureFactoryConfig}
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.app.appmessages.{AppStart, AppStop, SetAllowReloadOverHTTPState}
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceFlushDB
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetRepositoryContent}
import org.knora.webapi.messages.util.rdf.RdfFeatureFactory
import org.knora.webapi.messages.util.{KnoraSystemInstances, ResponderData}
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl, _}
import org.knora.webapi.store.cacheservice.settings.CacheServiceSettings
import org.knora.webapi.util.StartupUtils
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.{AnyWordSpecLike, AsyncWordSpecLike}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

import zio.Runtime
import org.knora.webapi.core.Logging
import org.knora.webapi.store.cacheservice.CacheServiceManager
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.cacheservice.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.auth.JWTService
import org.knora.webapi.testcontainers.SipiTestContainer
import zio.ZEnvironment
import zio.RuntimeConfig
import zio.ZIO
import zio.ZLayer
import zio.&
import org.knora.webapi.config.AppConfig

abstract class AsyncCoreSpec(_system: ActorSystem)
    extends TestKit(_system)
    with Core
    with StartupUtils
    with AsyncWordSpecLike
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
        CoreSpec.getCallerName(classOf[AsyncCoreSpec]),
        TestContainerFuseki.PortConfig.withFallback(ConfigFactory.load(config.withFallback(CoreSpec.defaultConfig)))
      )
    )

  def this(name: String) = this(ActorSystem(name, TestContainerFuseki.PortConfig.withFallback(ConfigFactory.load())))

  def this() =
    this(
      ActorSystem(
        CoreSpec.getCallerName(classOf[AsyncCoreSpec]),
        TestContainerFuseki.PortConfig.withFallback(ConfigFactory.load())
      )
    )

  /* needed by the core trait */
  implicit lazy val settings: KnoraSettingsImpl = KnoraSettings(system)
  implicit val materializer: Materializer       = Materializer.matFromSystem(system)
  override implicit val executionContext: ExecutionContext =
    system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  // can be overridden in individual spec
  lazy val rdfDataObjects = Seq.empty[RdfDataObject]

  // needs to be initialized early on
  StringFormatter.initForTest()
  RdfFeatureFactory.init(settings)

  val log: LoggingAdapter = akka.event.Logging(system, this.getClass)

  // The ZIO runtime used to run functional effects
  val runtime = Runtime(ZEnvironment.empty, RuntimeConfig.default @@ Logging.fromInfo)

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

  // The main application actor forwards messages to the responder manager and the store manager.
  val responderManager: ActorRef = appActor
  val storeManager: ActorRef     = appActor

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
    // set allow reload over http
    appActor ! SetAllowReloadOverHTTPState(true)

    // Start Knora, without reading data from the repository
    appActor ! AppStart(ignoreRepository = true, requiresIIIFService = false)

    // waits until knora is up and running
    applicationStateRunning()

    loadTestData(rdfDataObjects)

    // memusage()
  }

  final override def afterAll(): Unit =
    appActor ! AppStop()

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

    logger.info("Flush Redis cache started ...")
    Try(Await.result(appActor ? CacheServiceFlushDB(KnoraSystemInstances.Users.SystemUser), 5 seconds)) match {
      case Success(res) => logger.info("... flushing Redis cache done.")
      case Failure(e)   => logger.error(s"Flushing Redis cache failed: ${e.getMessage}")
    }
  }
}
