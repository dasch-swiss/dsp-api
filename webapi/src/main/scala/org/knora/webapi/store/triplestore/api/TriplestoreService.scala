/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.api

import zio._
import zio.macros.accessible

import java.nio.file.Path

import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.rdf.QuadFormat
import org.knora.webapi.messages.util.rdf.SparqlSelectResult

@accessible
trait TriplestoreService {

  /**
   * Simulates a read timeout.
   */
  def doSimulateTimeout(): UIO[SparqlSelectResult]

  /**
   * Given a SPARQL SELECT query string, runs the query, returning the result as a [[SparqlSelectResult]].
   *
   * @param sparql          the SPARQL SELECT query string.
   * @param simulateTimeout if `true`, simulate a read timeout.
   * @param isGravsearch    if `true`, takes a long timeout because gravsearch queries can take a long time.
   * @return a [[SparqlSelectResult]].
   */
  def sparqlHttpSelect(
    sparql: String,
    simulateTimeout: Boolean = false,
    isGravsearch: Boolean = false,
    isAdministrativeQuery: Boolean = false
  ): UIO[SparqlSelectResult]

  /**
   * Given a SPARQL CONSTRUCT query string, runs the query, returning the result as a [[SparqlConstructResponse]].
   *
   * @param sparqlConstructRequest the request message.
   * @return a [[SparqlConstructResponse]]
   */
  def sparqlHttpConstruct(sparqlConstructRequest: SparqlConstructRequest): UIO[SparqlConstructResponse]

  /**
   * Given a SPARQL CONSTRUCT query string, runs the query, returns the result as a [[SparqlExtendedConstructResponse]].
   *
   * @param sparqlExtendedConstructRequest the request message.
   * @return a [[SparqlExtendedConstructResponse]]
   */
  def sparqlHttpExtendedConstruct(
    sparqlExtendedConstructRequest: SparqlExtendedConstructRequest
  ): UIO[SparqlExtendedConstructResponse]

  /**
   * Given a SPARQL CONSTRUCT query string, runs the query, saving the result in a file.
   *
   * @param sparql       the SPARQL CONSTRUCT query string.
   * @param graphIri     the named graph IRI to be used in the output file.
   * @param outputFile   the output file.
   * @param outputFormat the output file format.
   * @return a [[FileWrittenResponse]].
   */
  def sparqlHttpConstructFile(
    sparql: String,
    graphIri: IRI,
    outputFile: Path,
    outputFormat: QuadFormat
  ): UIO[FileWrittenResponse]

  /**
   * Performs a SPARQL update operation.
   *
   * @param sparqlUpdate the SPARQL update.
   * @return a [[SparqlUpdateResponse]].
   */
  def sparqlHttpUpdate(sparqlUpdate: String): UIO[SparqlUpdateResponse]

  /**
   * Performs a SPARQL ASK query.
   *
   * @param sparql the SPARQL ASK query.
   * @return a [[SparqlAskResponse]].
   */
  def sparqlHttpAsk(sparql: String): UIO[SparqlAskResponse]

  /**
   * Requests the contents of a named graph, saving the response in a file.
   *
   * @param graphIri             the IRI of the named graph.
   * @param outputFile           the file to be written.
   * @param outputFormat         the output file format.
   *
   * @return a string containing the contents of the graph in N-Quads format.
   */
  def sparqlHttpGraphFile(
    graphIri: IRI,
    outputFile: Path,
    outputFormat: QuadFormat
  ): UIO[FileWrittenResponse]

  /**
   * Requests the contents of a named graph, returning the response as Turtle.
   *
   * @param graphIri the IRI of the named graph.
   * @return a string containing the contents of the graph in Turtle format.
   */
  def sparqlHttpGraphData(graphIri: IRI): UIO[NamedGraphDataResponse]

  /**
   * Resets the content of the triplestore with the data supplied with the request.
   * First performs `dropAllTriplestoreContent` and afterwards `insertDataIntoTriplestore`.
   *
   * @param rdfDataObjects a sequence of paths and graph names referencing data that needs to be inserted.
   * @param prependDefaults denotes if the rdfDataObjects list should be prepended with a default set. Default is `true`.
   */
  def resetTripleStoreContent(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean = true
  ): UIO[ResetRepositoryContentACK]

  /**
   * Drops (deletes) all data from the triplestore using "DROP ALL" SPARQL query.
   */
  def dropAllTriplestoreContent(): UIO[DropAllRepositoryContentACK]

  /**
   * Wipes all triplestore data out using HTTP requests.
   */
  def dropDataGraphByGraph(): UIO[DropDataGraphByGraphACK]

  /**
   * Inserts the data referenced inside the `rdfDataObjects` by appending it to a default set of `rdfDataObjects`
   * based on the list defined in `application.conf` under the `app.triplestore.default-rdf-data` key.
   *
   * @param rdfDataObjects  a sequence of paths and graph names referencing data that needs to be inserted.
   * @param prependDefaults denotes if the rdfDataObjects list should be prepended with a default set. Default is `true`.
   * @return [[InsertTriplestoreContentACK]]
   */
  def insertDataIntoTriplestore(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean
  ): UIO[InsertTriplestoreContentACK]

  /**
   * Checks the Fuseki triplestore if it is available and configured correctly. If it is not
   * configured, tries to automatically configure (initialize) the required dataset.
   */
  def checkTriplestore(): UIO[CheckTriplestoreResponse]

  /**
   * Dumps the whole repository in N-Quads format, saving the response in a file.
   *
   * @param outputFile           the output file.
   * @return a string containing the contents of the graph in N-Quads format.
   */
  def downloadRepository(
    outputFile: Path
  ): UIO[FileWrittenResponse]

  /**
   * Uploads repository content from an N-Quads file.
   *
   * @param inputFile an N-Quads file containing the content to be uploaded to the repository.
   */
  def uploadRepository(inputFile: Path): UIO[RepositoryUploadedResponse]

  /**
   * Puts a data graph into the repository.
   *
   * @param graphContent a data graph in Turtle format to be inserted into the repository.
   * @param graphName    the name of the graph.
   */
  def insertDataGraphRequest(graphContent: String, graphName: String): UIO[InsertGraphDataContentResponse]

}
