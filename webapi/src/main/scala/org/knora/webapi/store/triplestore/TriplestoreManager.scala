/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.event.LoggingReceive
import akka.routing.FromConfig
import org.knora.webapi.core.ActorMaker
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.store.triplestoremessages.UpdateRepositoryRequest
import org.knora.webapi.messages.util.FakeTriplestore
import org.knora.webapi.settings._
import org.knora.webapi.store.triplestore.http.HttpTriplestoreConnector
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater
import org.knora.webapi.util.ActorUtil._

import scala.concurrent.ExecutionContext

import zio._
import org.knora.webapi.store.triplestore.api.TriplestoreService

/**
 * This service receives akka messages and translates them to calls to ZIO besed service implementations.
 * This will be removed when Akka-Actors are removed.
 *
 * @param ts                    a triplestore service.
 * @param updater                    a RepositoryUpdater for processing requests to update the repository.
 */
final case class TriplestoreManager(
  ts: TriplestoreService,
  updater: RepositoryUpdater
) {

  protected implicit val executionContext: ExecutionContext =
    context.system.dispatchers.lookup(KnoraDispatchers.KnoraActorDispatcher)

  private var storeActorRef: ActorRef = _

  // TODO: run the fake triple store as an actor (the fake triple store will not be needed anymore, once the embedded triple store is implemented)
  FakeTriplestore.init(settings.fakeTriplestoreDataDir)

  if (settings.useFakeTriplestore) {
    FakeTriplestore.load()
    log.info("Loaded fake triplestore")
  } else {
    log.debug(s"Using triplestore: ${settings.triplestoreType}")
  }

  if (settings.prepareFakeTriplestore) {
    FakeTriplestore.clear()
    log.info("About to prepare fake triplestore")
  }

  log.debug(settings.triplestoreType)

  // A RepositoryUpdater for processing requests to update the repository.
  private val repositoryUpdater: RepositoryUpdater = new RepositoryUpdater(
    system = context.system,
    appActor = appActor,
    settings = settings,
    featureFactoryConfig = defaultFeatureFactoryConfig
  )

  override def preStart(): Unit = {
    log.debug("TriplestoreManagerActor: start with preStart")

    storeActorRef = makeActor(
      FromConfig.props(Props[HttpTriplestoreConnector]()).withDispatcher(KnoraDispatchers.KnoraActorDispatcher),
      name = HttpTriplestoreActorName
    )

    log.debug("TriplestoreManagerActor: finished with preStart")
  }

  def receive(message: TriplestoreRequest) = message match {
    case UpdateRepositoryRequest()           =>  repositoryUpdater.maybeUpdateRepository
    case SparqlSelectRequest(sparql: String) => try2Message(sender(), sparqlHttpSelect(sparql), log)
    case sparqlConstructRequest: SparqlConstructRequest =>
      try2Message(sender(), sparqlHttpConstruct(sparqlConstructRequest), log)
    case sparqlExtendedConstructRequest: SparqlExtendedConstructRequest =>
      try2Message(sender(), sparqlHttpExtendedConstruct(sparqlExtendedConstructRequest), log)
    case SparqlConstructFileRequest(
          sparql: String,
          graphIri: IRI,
          outputFile: Path,
          outputFormat: QuadFormat,
          featureFactoryConfig: FeatureFactoryConfig
        ) =>
      try2Message(
        sender(),
        sparqlHttpConstructFile(sparql, graphIri, outputFile, outputFormat, featureFactoryConfig),
        log
      )
    case NamedGraphFileRequest(
          graphIri: IRI,
          outputFile: Path,
          outputFormat: QuadFormat,
          featureFactoryConfig: FeatureFactoryConfig
        ) =>
      try2Message(sender(), sparqlHttpGraphFile(graphIri, outputFile, outputFormat, featureFactoryConfig), log)
    case NamedGraphDataRequest(graphIri: IRI) => try2Message(sender(), sparqlHttpGraphData(graphIri), log)
    case SparqlUpdateRequest(sparql: String)  => try2Message(sender(), sparqlHttpUpdate(sparql), log)
    case SparqlAskRequest(sparql: String)     => try2Message(sender(), sparqlHttpAsk(sparql), log)
    case ResetRepositoryContent(rdfDataObjects: Seq[RdfDataObject], prependDefaults: Boolean) =>
      try2Message(sender(), resetTripleStoreContent(rdfDataObjects, prependDefaults), log)
    case DropAllTRepositoryContent() => try2Message(sender(), dropAllTriplestoreContent(), log)
    case InsertRepositoryContent(rdfDataObjects: Seq[RdfDataObject]) =>
      try2Message(sender(), insertDataIntoTriplestore(rdfDataObjects), log)
    case HelloTriplestore(msg: String) if msg == settings.triplestoreType =>
      sender() ! HelloTriplestore(settings.triplestoreType)
    case CheckTriplestoreRequest() => try2Message(sender(), checkFusekiTriplestore(), log)
    case SearchIndexUpdateRequest(subjectIri: Option[String]) =>
      try2Message(sender(), Success(SparqlUpdateResponse()), log)
    case DownloadRepositoryRequest(outputFile: Path, featureFactoryConfig: FeatureFactoryConfig) =>
      try2Message(sender(), downloadRepository(outputFile, featureFactoryConfig), log)
    case UploadRepositoryRequest(inputFile: Path) => try2Message(sender(), uploadRepository(inputFile), log)
    case InsertGraphDataContentRequest(graphContent: String, graphName: String) =>
      try2Message(sender(), insertDataGraphRequest(graphContent, graphName), log)
    case SimulateTimeoutRequest() => try2Message(sender(), doSimulateTimeout(), log)
    case other =>
      sender() ! Status.Failure(
        UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}")
      )
  }

}

object TriplestoreManager {
  val layer: ZLayer[TriplestoreService & RepositoryUpdater, Nothing, TriplestoreManager] = {
    ZLayer {
      for {
        ts      <- ZIO.service[TriplestoreService]
        updater <- ZIO.service[RepositoryUpdater]
      } yield TriplestoreManager(ts, updater)
    }
  }
}
