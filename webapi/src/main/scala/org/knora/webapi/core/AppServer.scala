/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import org.apache.pekko.actor
import zio._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.domain.AppState
import org.knora.webapi.messages.store.sipimessages.IIIFServiceStatusNOK
import org.knora.webapi.messages.store.sipimessages.IIIFServiceStatusOK
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.slice.admin.repo.service.CacheManager
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater

/**
 * The application bootstrapper
 */
final case class AppServer(
  state: State,
  ts: TriplestoreService,
  ru: RepositoryUpdater,
  as: actor.ActorSystem,
  ontologyCache: OntologyCache,
  sipiService: SipiService,
  hs: HttpServer,
  appConfig: AppConfig,
  cacheManager: CacheManager,
) {

  /**
   * Checks if the TriplestoreService is running and the repository is properly initialized.
   */
  private val checkTriplestoreService: Task[Unit] =
    for {
      _      <- state.set(AppState.WaitingForTriplestore)
      status <- ts.checkTriplestore()
      _ <- status match {
             case TriplestoreStatus.Available           => ZIO.unit
             case TriplestoreStatus.NotInitialized(msg) => ZIO.die(new Exception(msg))
             case TriplestoreStatus.Unavailable(msg)    => ZIO.die(new Exception(msg))
           }
      _ <- state.set(AppState.TriplestoreReady)
    } yield ()

  /**
   * Initiates repository upgrade if `requiresRepository` is `true` an logs the result.
   *
   * @param requiresRepository If `true`, calls the RepositoryUpdater to initiate the repository, otherwise returns ()
   */
  private def upgradeRepository(requiresRepository: Boolean): Task[Unit] =
    for {
      _ <- state.set(AppState.UpdatingRepository)
      _ <- ru.maybeUpgradeRepository.flatMap(response => ZIO.logInfo(response.message)).when(requiresRepository)
      _ <- state.set(AppState.RepositoryUpToDate)
    } yield ()

  /**
   * Initiates building of all caches
   */
  private val buildAllCaches: UIO[Unit] =
    for {
      _ <- state.set(AppState.CreatingCaches)
      _ <- cacheManager.clearAll()
      _ <- state.set(AppState.CachesReady)
    } yield ()

  /**
   * Initiates population of the ontology caches if `requiresRepository` is `true`
   *
   * @param requiresRepository If `true`, calls the AppRouter to populate the ontology caches, otherwise returns ()
   */
  private def populateOntologyCaches(requiresRepository: Boolean): Task[Unit] =
    for {
      _ <- state.set(AppState.LoadingOntologies)
      _ <- ontologyCache
             .loadOntologies(KnoraSystemInstances.Users.SystemUser)
             .when(requiresRepository)
      _ <- state.set(AppState.OntologiesReady)
    } yield ()

  /**
   * Checks if the IIIF service is running
   *
   * @param requiresIIIFService If `true`, checks the status of the IIIFService instance, otherwise returns ()
   */
  private def checkIIIFService(requiresIIIFService: Boolean): UIO[Unit] =
    for {
      _ <- state.set(AppState.WaitingForIIIFService)
      _ <- sipiService
             .getStatus()
             .flatMap {
               case IIIFServiceStatusOK =>
                 ZIO.logInfo("IIIF service running")
               case IIIFServiceStatusNOK =>
                 ZIO.logError("IIIF service not running") *> ZIO.die(new Exception("IIIF service not running"))
             }
             .when(requiresIIIFService)
             .orDie
      _ <- state.set(AppState.IIIFServiceReady)
    } yield ()

  /**
   * Initiates the startup of the DSP-API server.
   *
   * @param requiresAdditionalRepositoryChecks  If `true`, checks if repository service is running, updates data if necessary and loads ontology cache.
   *                                            If `false`, checks if repository service is running but doesn't run upgrades and doesn't load ontology cache.
   * @param requiresIIIFService                 If `true`, ensures that the IIIF service is running.
   */
  def start(
    requiresAdditionalRepositoryChecks: Boolean,
    requiresIIIFService: Boolean,
  ): Task[Unit] =
    for {
      _ <- ZIO.logInfo("=> Startup checks initiated")
      _ <- checkTriplestoreService
      _ <- upgradeRepository(requiresAdditionalRepositoryChecks)
      _ <- buildAllCaches
      _ <- populateOntologyCaches(requiresAdditionalRepositoryChecks)
      _ <- checkIIIFService(requiresIIIFService)
      _ <- ZIO.logInfo("=> Startup checks finished")
      _ <- ZIO.logInfo(s"DSP-API Server started: ${appConfig.knoraApi.internalKnoraApiBaseUrl}")
      _ <- ZIO.logWarning("Resetting DB over HTTP is turned ON").when(appConfig.allowReloadOverHttp)
      _ <- state.set(AppState.Running)
    } yield ()
}

object AppServer {

  type AppServerEnvironment =
    actor.ActorSystem & AppConfig & CacheManager & HttpServer & OntologyCache & RepositoryUpdater & SipiService & State & TriplestoreService

  /**
   * Initializes the AppServer instance with the required services
   */
  def init(): ZIO[AppServerEnvironment, Nothing, AppServer] =
    for {
      state    <- ZIO.service[State]
      ts       <- ZIO.service[TriplestoreService]
      ru       <- ZIO.service[RepositoryUpdater]
      as       <- ZIO.service[actor.ActorSystem]
      oc       <- ZIO.service[OntologyCache]
      iiifs    <- ZIO.service[SipiService]
      hs       <- ZIO.service[HttpServer]
      c        <- ZIO.service[AppConfig]
      cm       <- ZIO.service[CacheManager]
      appServer = AppServer(state, ts, ru, as, oc, iiifs, hs, c, cm)
    } yield appServer

  /**
   * The live AppServer
   */
  val make: ZIO[AppServerEnvironment, Nothing, Unit] =
    for {
      appServer <- AppServer.init()
      _         <- appServer.start(requiresAdditionalRepositoryChecks = true, requiresIIIFService = true).orDie
    } yield ()

  /**
   * The test AppServer with Sipi, which initiates the startup checks. Before this effect does what it does,
   * the complete server should have already been started.
   */
  val testWithSipi: ZIO[AppServerEnvironment, Nothing, Unit] =
    for {
      appServer <- AppServer.init()
      _         <- appServer.start(requiresAdditionalRepositoryChecks = false, requiresIIIFService = true).orDie
    } yield ()

  /**
   * The test AppServer without Sipi, which initiates the startup checks. Before this effect does what it does,
   * the complete server should have already been started.
   */
  val testWithoutSipi: ZIO[AppServerEnvironment, Nothing, Unit] =
    for {
      appServer <- AppServer.init()
      _         <- appServer.start(requiresAdditionalRepositoryChecks = false, requiresIIIFService = false).orDie
    } yield ()
}
