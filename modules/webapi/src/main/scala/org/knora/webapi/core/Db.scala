/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.*

import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.domain.AppState
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus.Available
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater

/**
 * The application bootstrapper
 */
object Db { self =>

  private def setState(s: AppState) = ZIO.serviceWithZIO[State](_.set(s))
  private val triplestore           = ZIO.serviceWithZIO[TriplestoreService]

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
        setState(AppState.Running)
    }
    .orDie
    .unit

  private val checkTriplestore =
    setState(AppState.WaitingForTriplestore) *>
      triplestore(_.checkTriplestore().filterOrDieWith(_ == Available)(s => new Exception(s.msg))) *>
      setState(AppState.TriplestoreReady)

  private def resetTripleStoreContent(data: List[RdfDataObject]) =
    ZIO.logInfo(s"Loading test data: ${data.map(_.name).mkString}") *>
      triplestore(_.resetTripleStoreContent(data).timeout(480.seconds)) *>
      ZIO.logInfo("... loading test data done.")

  private def upgradeRepository =
    setState(AppState.UpdatingRepository) *>
      ZIO.serviceWithZIO[RepositoryUpdater](_.maybeUpgradeRepository).tap(r => ZIO.logInfo(r.message)) *>
      setState(AppState.RepositoryUpToDate)

  private def refreshCache =
    setState(AppState.LoadingOntologies) *>
      ZIO
        .serviceWithZIO[OntologyCache](_.refreshCache())
        .tap(cd => ZIO.logInfo(s"Ontology cache loaded: ${cd.ontologies.size} ontologies")) *>
      setState(AppState.OntologiesReady)
}
