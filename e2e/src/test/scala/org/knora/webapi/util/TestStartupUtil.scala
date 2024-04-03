/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import zio._
import zio.http._

import org.knora.webapi.core
import org.knora.webapi.core.AppServer
import org.knora.webapi.core.LayersTest
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.util

trait TestStartupUtil {

  private val data = List(
    RdfDataObject(
      path = "test_data/project_data/admin-data.ttl",
      name = "http://www.knora.org/data/admin",
    ),
    RdfDataObject(
      path = "test_data/project_data/permissions-data.ttl",
      name = "http://www.knora.org/data/permissions",
    ),
    RdfDataObject(
      path = "test_data/project_data/system-data.ttl",
      name = "http://www.knora.org/data/0000/SystemProject",
    ),
    RdfDataObject(
      path = "knora-ontologies/knora-admin.ttl",
      name = "http://www.knora.org/ontology/knora-admin",
    ),
    RdfDataObject(
      path = "knora-ontologies/knora-base.ttl",
      name = "http://www.knora.org/ontology/knora-base",
    ),
    RdfDataObject(
      path = "knora-ontologies/salsah-gui.ttl",
      name = "http://www.knora.org/ontology/salsah-gui",
    ),
    RdfDataObject(
      path = "knora-ontologies/standoff-onto.ttl",
      name = "http://www.knora.org/ontology/standoff",
    ),
    RdfDataObject(
      path = "knora-ontologies/standoff-data.ttl",
      name = "http://www.knora.org/data/standoff",
    ),
  )

  protected def prepareRepository(
    rdfDataObjects: List[RdfDataObject],
  ): ZIO[TriplestoreService with OntologyCache, Throwable, Unit] =
    for {
      _   <- ZIO.logInfo("Loading test data started ...")
      tss <- ZIO.service[TriplestoreService]
      rdf  = data ::: rdfDataObjects
      _   <- tss.resetTripleStoreContent(rdf, false).timeout(480.seconds)
      _   <- ZIO.logInfo("... loading test data done.")
      _   <- OntologyCache.loadOntologies(KnoraSystemInstances.Users.SystemUser).orDie
    } yield ()

  protected val testLayers =
    util.Logger.text() >>> core.LayersTest.testsWithFusekiTestcontainers()

  type env = LayersTest.DefaultTestEnvironmentWithoutSipi with Client with Scope

  protected def startApi(
    rdfDataObjects: List[RdfDataObject],
  ): ZIO[AppServer.AppServerEnvironment, Throwable, AppServer] = for {
    appServer <- AppServer.init()
    _         <- appServer.start(requiresAdditionalRepositoryChecks = false, requiresIIIFService = false).orDie
    _         <- prepareRepository(rdfDataObjects)
  } yield appServer

}
