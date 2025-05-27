/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import zio.*
import zio.http.*

import org.knora.webapi.config.KnoraApi
import org.knora.webapi.core.AppServer.AppServerEnvironment
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.slice.common.api.DspApiServerEndpoints
import org.knora.webapi.slice.common.api.TapirToZioHttpInterpreter
import org.knora.webapi.slice.infrastructure.DspApiServer
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService

/**
 * This trait is only used for testing. It is necessary so that E2E tests will only start
 * after the KnoraService is ready.
 */
object TestStartupUtils {

  def startDspApi(rdfDataObjects: List[RdfDataObject]): ZIO[
    KnoraApi & TapirToZioHttpInterpreter & DspApiServerEndpoints & TriplestoreService & OntologyCache &
      AppServerEnvironment,
    Throwable,
    Unit,
  ] =
    for {
      _ <- AppServer.test
      _ <- prepareRepository(rdfDataObjects)
      _ <- (DspApiServer.make *> ZIO.never).forkDaemon
      _ <- waitForDspApiServer
    } yield ()

  private val waitForDspApiServer =
    ZIO.serviceWithZIO[KnoraApi] { knoraApi =>
      val url = knoraApi.internalKnoraApiBaseUrl + "/version"
      ZIO.logInfo(s"Waiting for DSP API Server to be ready at $url") *>
        ZIO.scoped {
          Client
            .streaming(Request.get(url))
            .either
            .repeatUntil(_.map(_.status.isSuccess).getOrElse(false))
            .timeout(2.seconds)
        }.provide(Client.default) *>
        ZIO.logInfo("DSP API Server is ready")
    }

  /**
   * Load the test data and caches
   *
   * @param rdfDataObjects a list of [[RdfDataObject]]
   */
  def prepareRepository(
    rdfDataObjects: List[RdfDataObject],
  ): ZIO[TriplestoreService with OntologyCache, Throwable, Unit] =
    for {
      _   <- ZIO.logInfo(s"Loading test data: ${rdfDataObjects.map(_.name).mkString}")
      tss <- ZIO.service[TriplestoreService]
      _   <- tss.resetTripleStoreContent(rdfDataObjects).timeout(480.seconds)
      _   <- ZIO.logInfo("... loading test data done.")
      _   <- ZIO.serviceWithZIO[OntologyCache](_.refreshCache()).orDie
    } yield ()
}
