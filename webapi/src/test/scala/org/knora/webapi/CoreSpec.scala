/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.LoggingAdapter
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
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
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

import org.knora.webapi.app.ApplicationActor
import org.knora.webapi.core.Core
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.app.appmessages.{AppStart, SetAllowReloadOverHTTPState}
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceFlushDB
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetRepositoryContent}
import org.knora.webapi.messages.util.rdf.RdfFeatureFactory
import org.knora.webapi.messages.util.{KnoraSystemInstances, ResponderData}
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.settings.{KnoraDispatchers, KnoraSettings, KnoraSettingsImpl, _}
import org.knora.webapi.store.cache.settings.CacheServiceSettings
import org.knora.webapi.util.StartupUtils
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceHttpConnectorImpl
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.testcontainers.FusekiTestContainer

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
        config.withFallback(CoreSpec.defaultConfig)
      )
    )

  def this(config: Config) =
    this(
      ActorSystem(
        CoreSpec.getCallerName(classOf[CoreSpec]),
        config.withFallback(CoreSpec.defaultConfig)
      )
    )

  def this(name: String) =
    this(
      ActorSystem(
        name,
        ConfigFactory.load()
      )
    )

  def this() =
    this(
      ActorSystem(
        CoreSpec.getCallerName(classOf[CoreSpec]),
        ConfigFactory.load()
      )
    )

  /* needed by the core trait */
  implicit lazy val settings: KnoraSettingsImpl   = KnoraSettings(system)
  implicit val materializer: Materializer         = Materializer.matFromSystem(system)
  implicit val executionContext: ExecutionContext = system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  // can be overridden in individual spec
  lazy val rdfDataObjects = List.empty[RdfDataObject]

  // needs to be initialized early on
  StringFormatter.initForTest()
  RdfFeatureFactory.init(settings)

  val log: LoggingAdapter = akka.event.Logging(system, this.getClass)

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
    ZLayer.make[CacheServiceManager & IIIFServiceManager & TriplestoreServiceManager & AppConfig](
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer,
      IIIFServiceManager.layer,
      IIIFServiceSipiImpl.layer, // alternative: MockSipiImpl.layer
      AppConfigForTestContainers.testcontainers,
      JWTService.layer,
      SipiTestContainer.layer,
      TriplestoreServiceManager.layer,
      TriplestoreServiceHttpConnectorImpl.layer,
      RepositoryUpdater.layer,
      FusekiTestContainer.layer,
      Logging.fromInfo
    )

  // The ZIO runtime used to run functional effects
  lazy val runtime = Runtime.unsafeFromLayer(effectLayers)

  /**
   * Create both managers by unsafe running them.
   */
  val (cacheServiceManager, iiifServiceManager, triplestoreServiceManager, appConfig) =
    runtime.unsafeRun(managers)

  // start the Application Actor
  lazy val appActor: ActorRef =
    system.actorOf(
      Props(new ApplicationActor(cacheServiceManager, iiifServiceManager, triplestoreServiceManager, appConfig)),
      name = APPLICATION_MANAGER_ACTOR_NAME
    )

  val responderData: ResponderData = ResponderData(
    system = system,
    appActor = appActor,
    knoraSettings = settings,
    cacheServiceSettings = new CacheServiceSettings(system.settings.config)
  )

  final override def beforeAll(): Unit = {
    // set allow reload over http
    appActor ! SetAllowReloadOverHTTPState(true)

    // Start Knora, without reading data from the repository
    appActor ! AppStart(ignoreRepository = true, requiresIIIFService = false)

    // waits until knora is up and running
    applicationStateRunning()

    loadTestData(rdfDataObjects)
  }

  final override def afterAll(): Unit = {
    /* Stop the server when everything else has finished */
    TestKit.shutdownActorSystem(system)

    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    runtime.shutdown()
  }

  protected def loadTestData(rdfDataObjects: List[RdfDataObject]): Unit = {
    logger.info("Loading test data started ...")
    implicit val timeout: Timeout = Timeout(settings.defaultTimeout)
    Try(Await.result(appActor ? ResetRepositoryContent(rdfDataObjects), 479999.milliseconds)) match {
      case Success(res) => logger.info("... loading test data done.")
      case Failure(e)   => logger.error(s"Loading test data failed: ${e.getMessage}")
    }

    logger.info("Loading load ontologies into cache started ...")
    Try(
      Await.result(
        appActor ? LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser),
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
