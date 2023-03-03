/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore

import zio._

import java.nio.file.Path

import dsp.errors.UnexpectedMessageException
import org.knora.webapi._
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.store.triplestoremessages.CheckTriplestoreRequest
import org.knora.webapi.messages.store.triplestoremessages.DownloadRepositoryRequest
import org.knora.webapi.messages.store.triplestoremessages.DropAllTRepositoryContent
import org.knora.webapi.messages.store.triplestoremessages.InsertGraphDataContentRequest
import org.knora.webapi.messages.store.triplestoremessages.InsertRepositoryContent
import org.knora.webapi.messages.store.triplestoremessages.NamedGraphDataRequest
import org.knora.webapi.messages.store.triplestoremessages.NamedGraphFileRequest
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.ResetRepositoryContent
import org.knora.webapi.messages.store.triplestoremessages.SimulateTimeoutRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlAskRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlConstructFileRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlConstructRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlSelectRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlUpdateRequest
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreRequest
import org.knora.webapi.messages.store.triplestoremessages.UpdateRepositoryRequest
import org.knora.webapi.messages.store.triplestoremessages.UploadRepositoryRequest
import org.knora.webapi.messages.util.rdf.QuadFormat
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.upgrade.RepositoryUpdater

trait TriplestoreRequestMessageHandler extends MessageHandler
final case class TriplestoreRequestMessageHandlerLive(updater: RepositoryUpdater, ts: TriplestoreService)
    extends TriplestoreRequestMessageHandler {
  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[TriplestoreRequest]

  override def handle(message: ResponderRequest): Task[Any] = message match {
    case UpdateRepositoryRequest() => updater.maybeUpgradeRepository
    case SparqlSelectRequest(sparql: String, isGravsearch: Boolean) =>
      ts.sparqlHttpSelect(sparql = sparql, isGravsearch = isGravsearch)
    case sparqlConstructRequest: SparqlConstructRequest => ts.sparqlHttpConstruct(sparqlConstructRequest)
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
      ts.insertDataIntoTriplestore(rdfDataObjects, prependDefaults = true)
    case CheckTriplestoreRequest()                   => ts.checkTriplestore()
    case DownloadRepositoryRequest(outputFile: Path) => ts.downloadRepository(outputFile)
    case UploadRepositoryRequest(inputFile: Path)    => ts.uploadRepository(inputFile)
    case InsertGraphDataContentRequest(graphContent: String, graphName: String) =>
      ts.insertDataGraphRequest(graphContent, graphName)
    case SimulateTimeoutRequest() => ts.doSimulateTimeout()
    case other: Any =>
      ZIO.die(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
  }
}

object TriplestoreRequestMessageHandlerLive {
  val layer: URLayer[MessageRelay with RepositoryUpdater with TriplestoreService, TriplestoreRequestMessageHandler] =
    ZLayer.fromZIO {
      for {
        ts      <- ZIO.service[TriplestoreService]
        ud      <- ZIO.service[RepositoryUpdater]
        mr      <- ZIO.service[MessageRelay]
        handler <- mr.subscribe(TriplestoreRequestMessageHandlerLive(ud, ts))
      } yield handler
    }
}
