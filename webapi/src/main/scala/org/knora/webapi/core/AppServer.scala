/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio._

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.domain.AppState
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceStatusNOK
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceStatusOK
import org.knora.webapi.messages.store.sipimessages.IIIFServiceStatusNOK
import org.knora.webapi.messages.store.sipimessages.IIIFServiceStatusOK
import org.knora.webapi.store.cache.api.CacheService
import org.knora.webapi.store.iiif.api.IIIFService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.util.cache.CacheUtil

/**
 * The application bootstrapper
 */
final case class AppServer(
  state: State,
  ts: TriplestoreService,
  ru: RepositoryUpdater,
  as: ActorSystem,
  ar: AppRouter,
  iiifs: IIIFService,
  cs: CacheService,
  hs: HttpServer,
  appConfig: AppConfig
) {

  /**
   * Checks if the TriplestoreService is running and the repository is properly initialized.
   */
  private val checkTriplestoreService: ZIO[Any, Nothing, Unit] =
    for {
      _      <- state.set(AppState.WaitingForTriplestore)
      status <- ts.checkTriplestore().map(_.triplestoreStatus)
      _ <- status match {
             case TriplestoreStatus.Available(_)        => ZIO.unit
             case TriplestoreStatus.NotInitialized(msg) => ZIO.die(new Exception(msg))
             case TriplestoreStatus.Unavailable(msg)    => ZIO.die(new Exception(msg))
           }
      _ <- state.set(AppState.TriplestoreReady)
    } yield ()

  /**
   * Initiates repository upgrade if `requiresRepository` is `true` an logs the result.
   */
  private def upgradeRepository(requiresRepository: Boolean): ZIO[Any, Nothing, Unit] =
    for {
      _ <- state.set(AppState.UpdatingRepository)
      _ <- if (requiresRepository)
             ru.maybeUpgradeRepository.flatMap(response => ZIO.logInfo(response.message))
           else
             ZIO.unit
      _ <- state.set(AppState.RepositoryUpToDate)
    } yield ()

  /* Initiates building of all caches */
  private val buildAllCaches: ZIO[Any, Nothing, Unit] =
    for {
      _ <- state.set(AppState.CreatingCaches)
      _ <- ZIO.attempt {
             CacheUtil.removeAllCaches()
             CacheUtil.createCaches(appConfig.cacheConfigs)
           }.orDie
      _ <- state.set(AppState.CachesReady)
    } yield ()

  /* Initiates population of the ontology caches if `requiresRepository` is `true` */
  private def populateOntologyCaches(requiresRepository: Boolean): ZIO[Any, Nothing, Unit] =
    for {
      _ <- state.set(AppState.LoadingOntologies)
      _ <- if (requiresRepository)
             ar.populateOntologyCaches
           else
             ZIO.unit
      _ <- state.set(AppState.OntologiesReady)
    } yield ()

  /* Checks if the IIIF service is running */
  private def checkIIIFService(requiresIIIFService: Boolean): ZIO[Any, Nothing, Unit] =
    for {
      _ <- state.set(AppState.WaitingForIIIFService)
      _ <- if (requiresIIIFService)
             iiifs
               .getStatus()
               .flatMap(status =>
                 status match {
                   case IIIFServiceStatusOK =>
                     ZIO.logInfo("IIIF service running")
                   case IIIFServiceStatusNOK =>
                     ZIO.logError("IIIF service not running") *> ZIO.die(new Exception("IIIF service not running"))
                 }
               )
           else
             ZIO.unit
      _ <- state.set(AppState.IIIFServiceReady)
    } yield ()

  /* Checks if the Cache service is running */
  private val checkCacheService: ZIO[Any, Nothing, Unit] =
    for {
      _ <- state.set(AppState.WaitingForCacheService)
      _ <- cs.getStatus
             .flatMap(status =>
               status match {
                 case CacheServiceStatusNOK =>
                   ZIO.logError("Cache service not running.") *> ZIO.die(new Exception("Cache service not running."))
                 case CacheServiceStatusOK =>
                   ZIO.unit
               }
             )
      _ <- state.set(AppState.CacheServiceReady)
    } yield ()

  /**
   * Prints the welcome message
   */
  private val printBanner: ZIO[Any, Nothing, Unit] =
    for {
      _ <-
        ZIO.logInfo(
          s"DSP-API Server started: ${appConfig.knoraApi.internalKnoraApiBaseUrl}"
        )

      _ = if (appConfig.allowReloadOverHttp) {
            ZIO.logWarning("Resetting DB over HTTP is turned ON")
          }
    } yield ()

  /**
   * Initiates the startup sequence of the DSP-API server.
   *
   * @param requiresRepository  if `true`, check if it is running, run upgrading and loading ontology cache.
   *                                If `false`, check if it is running but don't run upgrading AND loading ontology cache.
   * @param requiresIIIFService if `true`, ensure that the IIIF service is started.
   */
  def start(
    requiresRepository: Boolean,
    requiresIIIFService: Boolean
  ) =
    for {
      _ <- ZIO.logInfo("=> Startup checks initiated")
      _ <- checkTriplestoreService
      _ <- upgradeRepository(requiresRepository)
      _ <- buildAllCaches
      _ <- populateOntologyCaches(requiresRepository)
      _ <- checkIIIFService(requiresIIIFService)
      _ <- checkCacheService
      _ <- ZIO.logInfo("=> Startup checks finished")
      _ <- printBanner
      _ <- state.set(AppState.Running)
    } yield ()
}

object AppServer {

  private type AppServerEnvironment =
    TriplestoreService
      with RepositoryUpdater
      with ActorSystem
      with AppRouter
      with IIIFService
      with CacheService
      with HttpServer
      with AppConfig
      with State

  private def startup(
    requiresRepository: Boolean,
    requiresIIIFService: Boolean
  ): ZIO[AppServerEnvironment, Nothing, Unit] =
    for {
      state  <- ZIO.service[State]
      ts     <- ZIO.service[TriplestoreService]
      ru     <- ZIO.service[RepositoryUpdater]
      as     <- ZIO.service[ActorSystem]
      ar     <- ZIO.service[AppRouter]
      iiifs  <- ZIO.service[IIIFService]
      cs     <- ZIO.service[CacheService]
      hs     <- ZIO.service[HttpServer]
      config <- ZIO.service[AppConfig]
      _      <- AppServer(state, ts, ru, as, ar, iiifs, cs, hs, config).start(requiresRepository, requiresIIIFService)
    } yield ()

  /* Live version */
  val live: ZLayer[AppServerEnvironment, Nothing, Unit] =
    ZLayer {
      startup(true, true)
    }

  /**
   * The AppServer test layer with Sipi, which initiates the startup checks. Before this layer does what it does,
   * the complete server should have already been started.
   */
  val testWithSipi: ZLayer[AppServerEnvironment, Nothing, Unit] =
    ZLayer {
      startup(false, true)
    }

  /**
   * The AppServer test layer without Sipi, which initiates the startup checks. Before this layer does what it does,
   * the complete server should have already been started.
   */
  val testWithoutSipi: ZLayer[AppServerEnvironment, Nothing, Unit] =
    ZLayer {
      startup(false, false)
    }
}
