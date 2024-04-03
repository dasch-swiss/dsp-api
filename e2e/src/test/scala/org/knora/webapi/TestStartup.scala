package org.knora.webapi

import zio._
import zio.http._

import org.knora.webapi.core.AppServer
import org.knora.webapi.core.LayersTest
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService

trait TestStartup {

  protected def prepareRepository(
    rdfDataObjects: List[RdfDataObject],
  ): ZIO[TriplestoreService with OntologyCache, Throwable, Unit] =
    for {
      _   <- ZIO.logInfo("Loading test data started ...")
      tss <- ZIO.service[TriplestoreService]
      _   <- tss.resetTripleStoreContent(rdfDataObjects).timeout(480.seconds)
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
