/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.domain.AppState
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus.Available
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater

/**
 * The application bootstrapper
 */
object Db { self =>

  private val state         = ZIO.serviceWithZIO[State]
  private val triplestore   = ZIO.serviceWithZIO[TriplestoreService]
  private val updater       = ZIO.serviceWithZIO[RepositoryUpdater]
  private val ontologyCache = ZIO.serviceWithZIO[OntologyCache]

  type DbInitEnv = AppConfig & OntologyCache & RepositoryUpdater & State & TriplestoreService

  val init: URIO[DbInitEnv, Unit]                                        = init(None)
  def initWithTestData(data: List[RdfDataObject]): URIO[DbInitEnv, Unit] = init(Some(data))

  /**
   * Checks the status of the triplestore service and initialize the database and ontology cache.
   * Will die if anything goes wrong during initialization.
   */
  private def init(dataMaybe: Option[List[RdfDataObject]]): URIO[DbInitEnv, Unit] = ZIO
    .serviceWithZIO[AppConfig] { appConfig =>
      ZIO.logInfo("=> Startup checks initiated") *>
        checkTriplestore *>
        dataMaybe.map(resetTripleStoreContent).getOrElse(upgradeRepository) *>
        refreshCache *>
        ZIO.logInfo("=> Startup checks finished") *>
        ZIO.logWarning("Resetting DB over HTTP is turned ON").when(appConfig.allowReloadOverHttp) *>
        state(_.set(AppState.Running))
    }
    .orDie
    .unit

  private def resetTripleStoreContent(data: List[RdfDataObject]) =
    ZIO.logInfo(s"Loading test data: ${data.map(_.name).mkString}") *>
      triplestore(_.resetTripleStoreContent(data).timeout(480.seconds)) *>
      ZIO.logInfo("... loading test data done.")

  private val checkTriplestore =
    state(_.set(AppState.WaitingForTriplestore)) *>
      triplestore(_.checkTriplestore().filterOrDieWith(_ == Available)(s => new Exception(s.msg))) *>
      state(_.set(AppState.TriplestoreReady))

  private def upgradeRepository =
    state(_.set(AppState.UpdatingRepository)) *>
      updater(_.maybeUpgradeRepository.flatMap(response => ZIO.logInfo(response.message))) *>
      state(_.set(AppState.RepositoryUpToDate))

  private def refreshCache =
    state(_.set(AppState.LoadingOntologies)) *>
      ontologyCache(_.refreshCache()).tap(cd =>
        ZIO.logInfo(s"Ontology cache loaded: ${cd.ontologies.size} ontologies"),
      ) *>
      state(_.set(AppState.OntologiesReady))
}
