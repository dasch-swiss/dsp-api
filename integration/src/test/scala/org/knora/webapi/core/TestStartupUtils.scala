/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.core

import com.typesafe.scalalogging.LazyLogging
import zio.*

import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService

/**
 * This trait is only used for testing. It is necessary so that E2E tests will only start
 * after the KnoraService is ready.
 */
trait TestStartupUtils extends LazyLogging {

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
      _   <- ZIO.serviceWithZIO[OntologyCache](_.loadOntologies(KnoraSystemInstances.Users.SystemUser)).orDie
    } yield ()

}
