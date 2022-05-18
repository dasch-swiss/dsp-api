/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore

import org.knora.webapi.messages.store.triplestoremessages.UpdateRepositoryRequest
import org.knora.webapi.settings._
import org.knora.webapi._
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater

import zio._
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.exceptions.UnexpectedMessageException
import org.knora.webapi.messages.store.triplestoremessages.SimulateTimeoutRequest
import org.knora.webapi.messages.store.triplestoremessages.InsertGraphDataContentRequest
import org.knora.webapi.messages.store.triplestoremessages.UploadRepositoryRequest
import java.nio.file.Path
import org.knora.webapi.messages.store.triplestoremessages.DownloadRepositoryRequest
import org.knora.webapi.messages.store.triplestoremessages.CheckTriplestoreRequest
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.InsertRepositoryContent
import org.knora.webapi.messages.store.triplestoremessages.DropAllTRepositoryContent
import org.knora.webapi.messages.store.triplestoremessages.ResetRepositoryContent
import org.knora.webapi.messages.store.triplestoremessages.SparqlAskRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlUpdateRequest
import org.knora.webapi.messages.store.triplestoremessages.NamedGraphDataRequest
import org.knora.webapi.messages.util.rdf.QuadFormat
import org.knora.webapi.messages.store.triplestoremessages.NamedGraphFileRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlConstructFileRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlConstructRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlSelectRequest
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreRequest

/**
 * This service receives akka messages and translates them to calls to ZIO besed service implementations.
 * This will be removed when Akka-Actors are removed.
 *
 * @param ts       a triplestore service.
 * @param updater  a RepositoryUpdater for processing requests to update the repository.
 */
final case class TriplestoreServiceManager(
  ts: TriplestoreService,
  updater: RepositoryUpdater
) {
  def receive(message: TriplestoreRequest) = message match {
    case UpdateRepositoryRequest()           => updater.maybeUpdateRepository
    case SparqlSelectRequest(sparql: String) => ts.sparqlHttpSelect(sparql)
    case sparqlConstructRequest: SparqlConstructRequest =>
      ts.sparqlHttpConstruct(sparqlConstructRequest)
    case sparqlExtendedConstructRequest: SparqlExtendedConstructRequest =>
      ts.sparqlHttpExtendedConstruct(sparqlExtendedConstructRequest)
    case SparqlConstructFileRequest(
          sparql: String,
          graphIri: IRI,
          outputFile: Path,
          outputFormat: QuadFormat
        ) =>
      ts.sparqlHttpConstructFile(sparql, graphIri, outputFile, outputFormat)
    case NamedGraphFileRequest(
          graphIri: IRI,
          outputFile: Path,
          outputFormat: QuadFormat
        ) =>
      ts.sparqlHttpGraphFile(graphIri, outputFile, outputFormat)
    case NamedGraphDataRequest(graphIri: IRI) => ts.sparqlHttpGraphData(graphIri)
    case SparqlUpdateRequest(sparql: String)  => ts.sparqlHttpUpdate(sparql)
    case SparqlAskRequest(sparql: String)     => ts.sparqlHttpAsk(sparql)
    case ResetRepositoryContent(rdfDataObjects: Seq[RdfDataObject], prependDefaults: Boolean) =>
      ts.resetTripleStoreContent(rdfDataObjects, prependDefaults)
    case DropAllTRepositoryContent() => ts.dropAllTriplestoreContent()
    case InsertRepositoryContent(rdfDataObjects: Seq[RdfDataObject]) =>
      ts.insertDataIntoTriplestore(rdfDataObjects, true)
    // case HelloTriplestore(msg: String) if msg == settings.triplestoreType => sender() ! HelloTriplestore(settings.triplestoreType)
    case CheckTriplestoreRequest() => ts.checkTriplestore()
    // case SearchIndexUpdateRequest(subjectIri: Option[String]) => try2Message(sender(), Success(SparqlUpdateResponse()), log)
    case DownloadRepositoryRequest(outputFile: Path) => ts.downloadRepository(outputFile)
    case UploadRepositoryRequest(inputFile: Path)    => ts.uploadRepository(inputFile)
    case InsertGraphDataContentRequest(graphContent: String, graphName: String) =>
      ts.insertDataGraphRequest(graphContent, graphName)
    case SimulateTimeoutRequest() => ts.doSimulateTimeout()
    case other =>
      ZIO.die(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
  }
}

object TriplestoreServiceManager {
  val layer: ZLayer[TriplestoreService & RepositoryUpdater, Nothing, TriplestoreServiceManager] = {
    ZLayer {
      for {
        ts      <- ZIO.service[TriplestoreService]
        updater <- ZIO.service[RepositoryUpdater]
      } yield TriplestoreServiceManager(ts, updater)
    }
  }
}
