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
import akka.util.Timeout
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.Logger
import org.knora.webapi.auth.JWTService
import org.knora.webapi.config.AppConfig
import org.knora.webapi.config.AppConfigForTestContainers
import org.knora.webapi.core.ActorSystem
import org.knora.webapi.core.State
import org.knora.webapi.core.HttpServer
import org.knora.webapi.store.cache.CacheServiceManager
import org.knora.webapi.store.cache.impl.CacheServiceInMemImpl
import org.knora.webapi.store.iiif.IIIFServiceManager
import org.knora.webapi.store.iiif.impl.IIIFServiceSipiImpl
import org.knora.webapi.testcontainers.SipiTestContainer
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import zio._

import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.knora.webapi.messages.StringFormatter
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
import org.knora.webapi.store.triplestore.api.TriplestoreService
import akka.testkit.TestKitBase
import org.knora.webapi.core.AppRouter
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.iiif.api.IIIFService
import akka.actor

abstract class CoreSpec
    extends ZIOApp
    with TestKitBase
    with StartupUtils
    with AnyWordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with ImplicitSender {

  implicit lazy val system: actor.ActorSystem   = akka.actor.ActorSystem("E2ETest", ConfigFactory.load())
  implicit lazy val settings: KnoraSettingsImpl = KnoraSettings(system)
  lazy val rdfDataObjects                       = List.empty[RdfDataObject]
  val log: Logger                               = Logger(this.getClass())

  /**
   * The `Environment` that we require to exist at startup.
   */
  type Environment = core.Environment

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

  /* Here we start our Application */
  val run = ZIO.scoped(core.Startup.run(true, true))

  /**
   * The effect layers which will be used to run the managers effect.
   * Can be overriden in specs that need other implementations.
   */
  lazy val effectLayers: ZLayer[
    Any,
    Nothing,
    core.ActorSystem
      with AppConfig
      with AppRouter
      with CacheServiceManager
      with CacheService
      with HttpServer
      with IIIFServiceManager
      with IIIFService
      with JWTService
      with RepositoryUpdater
      with State
      with TriplestoreServiceManager
      with TriplestoreService
  ] =
    ZLayer.make[core.Environment](
      core.ActorSystemTestImpl.layer(system),
      AppConfigForTestContainers.testcontainers,
      AppRouter.layer,
      CacheServiceManager.layer,
      CacheServiceInMemImpl.layer,
      HttpServer.layer,
      IIIFServiceManager.layer,
      IIIFServiceSipiImpl.layer, // alternative: MockSipiImpl.layer
      JWTService.layer,
      RepositoryUpdater.layer,
      State.layer,
      TriplestoreServiceManager.layer,
      TriplestoreServiceHttpConnectorImpl.layer,
      // testcontainers
      SipiTestContainer.layer,
      FusekiTestContainer.layer
    )

  val appActorF = system.actorSelection(APPLICATION_MANAGER_ACTOR_PATH).resolveOne(new scala.concurrent.duration.FiniteDuration(1, scala.concurrent.duration.SECONDS))
  val appActor = Await.result(appActorF, new scala.concurrent.duration.FiniteDuration(1, scala.concurrent.duration.SECONDS))


  final override def beforeAll(): Unit = {

    // waits until knora is up and running
    applicationStateRunning(appActor)
    prepareRepository(rdfDataObjects)
  }

  final override def afterAll(): Unit =
    /* Stop ZIO runtime and release resources (e.g., running docker containers) */
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.shutdown()
    }

  private def prepareRepository(rdfDataObjects: List[RdfDataObject]): Unit =
    Unsafe.unsafe { implicit u =>
      runtime.unsafe.run {
        for {
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
