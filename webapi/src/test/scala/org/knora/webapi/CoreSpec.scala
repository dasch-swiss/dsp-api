/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.ask
import akka.stream.Materializer
import akka.testkit.ImplicitSender
import akka.testkit.TestKit
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import zio._

import scala.concurrent.ExecutionContext

import org.knora.webapi.app.ApplicationActor
import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.core.Core
import org.knora.webapi.core.Logging
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.app.appmessages.AppStart
import org.knora.webapi.messages.app.appmessages.SetAllowReloadOverHTTPState
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceFlushDB
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v2.responder.ontologymessages.LoadOntologiesRequestV2
import org.knora.webapi.settings.KnoraDispatchers
import org.knora.webapi.settings.KnoraSettings
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.settings._
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.cache.settings.CacheServiceSettings
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.store.triplestore.TriplestoreServiceManager
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.impl.TriplestoreServiceHttpConnectorImpl
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.util.StartupUtils

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
  val log: Logger = Logger(this.getClass())

  /**
   * The effect layers which will be used to run the managers effect.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers =
    ZLayer.make[CacheServiceManager & IIIFServiceManager & TriplestoreServiceManager & TriplestoreService & AppConfig](
      Runtime.removeDefaultLoggers,
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
      Logging.slf4j
    )

  // The ZIO runtime used to run functional effects
  lazy val runtime =
    Unsafe.unsafe { implicit u =>
      Runtime.unsafe.fromLayer(effectLayers)
    }

  // The effect for building managers and config.
  lazy val managers = for {
    csm       <- ZIO.service[CacheServiceManager]
    iiifsm    <- ZIO.service[IIIFServiceManager]
    tssm      <- ZIO.service[TriplestoreServiceManager]
    appConfig <- ZIO.service[AppConfig]
  } yield (csm, iiifsm, tssm, appConfig)

  /**
   * Create managers and config by unsafe running them.
   */
  val (cacheServiceManager, iiifServiceManager, triplestoreServiceManager, appConfig) =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe
        .run(
          managers
        )
        .getOrElse(c => throw FiberFailure(c))
    }

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

    prepareRepository(rdfDataObjects)
  }

  final override def afterAll(): Unit = {

    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.shutdown()
    }

    /* Stop the server when everything else has finished */
    TestKit.shutdownActorSystem(system)
  }

  private def prepareRepository(rdfDataObjects: List[RdfDataObject]): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run {
        for {
          ec  <- ZIO.executor.map(_.asExecutionContext)
          _   <- ZIO.logInfo("Loading test data started ...")
          tss <- ZIO.service[TriplestoreService]
          _   <- tss.resetTripleStoreContent(rdfDataObjects).timeout(480.seconds)
          _   <- ZIO.logInfo("... loading test data done.")
          _   <- ZIO.logInfo("Loading load ontologies into cache started ...")
          _ <- ZIO
                 .fromFuture(_ =>
                   appActor
                     .ask(LoadOntologiesRequestV2(KnoraSystemInstances.Users.SystemUser))(
                       akka.util.Timeout.create(2.minutes)
                     )
                 )
                 .timeout(60.seconds)
          _ <- ZIO.logInfo("... loading ontologies into cache done.")
          _ <- ZIO.logInfo("CacheServiceFlushDB started ...")
          _ <-
            ZIO
              .fromFuture(_ =>
                appActor
                  .ask(CacheServiceFlushDB(KnoraSystemInstances.Users.SystemUser))(akka.util.Timeout.create(30.seconds))
              )
              .timeout(15.seconds)
          _ <- ZIO.logInfo("... CacheServiceFlushDB done.")
        } yield ()
      }
    }
}
