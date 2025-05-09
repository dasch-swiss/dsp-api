/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.domain.AppState
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.iiif.api.SipiService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus.Available
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater

/**
 * The application bootstrapper
 */
final case class AppServer(
  state: State,
  ts: TriplestoreService,
  ru: RepositoryUpdater,
  ontologyCache: OntologyCache,
  appConfig: AppConfig,
) {

  /**
   * Checks if the TriplestoreService is running and the repository is properly initialized.
   */
  private val checkTriplestoreService: Task[Unit] =
    for {
      _ <- state.set(AppState.WaitingForTriplestore)
      _ <- ts.checkTriplestore().filterOrDieWith(_ == Available)(s => new Exception(s.msg))
      _ <- state.set(AppState.TriplestoreReady)
    } yield ()

  /**
   * Initiates repository upgrade if `requiresRepository` is `true` and logs the result.
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
   * Initiates population of the ontology caches if `requiresRepository` is `true`
   *
   * @param requiresRepository If `true`, calls the AppRouter to populate the ontology caches, otherwise returns ()
   */
  private def populateOntologyCaches(requiresRepository: Boolean): Task[Unit] =
    for {
      _ <- state.set(AppState.LoadingOntologies)
      _ <-
        ZIO.when(requiresRepository) {
          ontologyCache
            .refreshCache()
            .tap(cd => ZIO.logInfo(s"Ontology cache loaded: ${cd.ontologies.size} ontologies"))
        }
      _ <- state.set(AppState.OntologiesReady)
    } yield ()

  /**
   * Initiates the startup of the DSP-API server.
   *
   * @param requiresAdditionalRepositoryChecks  If `true`, checks if repository service is running, updates data if necessary and loads ontology cache.
   *                                            If `false`, checks if repository service is running but doesn't run upgrades and doesn't load ontology cache.
   */
  def start(
    requiresAdditionalRepositoryChecks: Boolean,
  ): Task[Unit] =
    for {
      _ <- ZIO.logInfo("=> Startup checks initiated")
      _ <- checkTriplestoreService
      _ <- upgradeRepository(requiresAdditionalRepositoryChecks)
      _ <- populateOntologyCaches(requiresAdditionalRepositoryChecks)
      _ <- ZIO.logInfo("=> Startup checks finished")
      _ <- ZIO.logWarning("Resetting DB over HTTP is turned ON").when(appConfig.allowReloadOverHttp)
      _ <- state.set(AppState.Running)
    } yield ()
}

object AppServer {

  type AppServerEnvironment = AppConfig & OntologyCache & RepositoryUpdater & SipiService & State & TriplestoreService

  /**
   * Initializes the AppServer instance with the required services
   */
  def init(): URIO[AppServerEnvironment, AppServer] =
    for {
      state    <- ZIO.service[State]
      ts       <- ZIO.service[TriplestoreService]
      ru       <- ZIO.service[RepositoryUpdater]
      oc       <- ZIO.service[OntologyCache]
      c        <- ZIO.service[AppConfig]
      appServer = AppServer(state, ts, ru, oc, c)
    } yield appServer

  /**
   * The live AppServer
   */
  val make: URIO[AppServerEnvironment, Unit] =
    for {
      appServer <- AppServer.init()
      _         <- appServer.start(requiresAdditionalRepositoryChecks = true).orDie
    } yield ()

  /**
   * The test AppServer with or without Sipi, which initiates the startup checks. Before this effect does what it does,
   * the complete server should have already been started.
   */
  val test: URIO[AppServerEnvironment, Unit] =
    for {
      appServer <- AppServer.init()
      _         <- appServer.start(requiresAdditionalRepositoryChecks = false).orDie
    } yield ()
}
