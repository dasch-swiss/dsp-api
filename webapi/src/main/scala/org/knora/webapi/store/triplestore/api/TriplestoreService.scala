/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.api

import org.eclipse.rdf4j.sparqlbuilder.core.query.ConstructQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.InsertDataQuery
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery
import play.twirl.api.TxtFormat
import zio.*

import java.nio.file.Path

import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.util.rdf.QuadFormat
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.slice.common.repo.rdf.RdfModel
import org.knora.webapi.slice.resourceinfo.domain.InternalIri
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.SparqlTimeout.Standard
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.store.triplestore.domain.TriplestoreStatus
import org.knora.webapi.store.triplestore.errors.TriplestoreResponseException
import org.knora.webapi.store.triplestore.upgrade.GraphsForMigration

trait TriplestoreService {

  /**
   * Performs a SPARQL ASK query.
   *
   * @param sparql the SPARQL [[Ask]] query.
   * @return a [[Boolean]].
   */
  def query(sparql: Ask): Task[Boolean]

  /**
   * Performs a SPARQL CONSTRUCT query.
   *
   * @param sparql The SPARQL [[Construct]] query.
   * @return a [[SparqlConstructResponse]]
   */
  def query(sparql: Construct): Task[SparqlConstructResponse]

  def queryRdf(sparql: Construct): Task[String]

  final def queryRdfModel(sparql: Construct): Task[RdfModel] = queryRdf(sparql)
    .flatMap(RdfModel.fromTurtle(_).mapError(e => TriplestoreResponseException(e.toString)))

  /**
   * Performs a SPARQL SELECT query.
   *
   * @param sparql The SPARQL [[Select]] query.
   * @return A [[SparqlSelectResult]].
   */
  def query(sparql: Select): Task[SparqlSelectResult]

  /**
   * Performs a SPARQL update operation, i.e. an INSERT or DELETE query.
   *
   * @param sparql the SPARQL [[Update]] query.
   * @return a [[Unit]].
   */
  def query(sparql: Update): Task[Unit]

  /**
   * Given a SPARQL CONSTRUCT query string, runs the query, saving the result in a file.
   *
   * @param sparql       the SPARQL CONSTRUCT query string.
   * @param graphIri     the named graph IRI to be used in the output file.
   * @param outputFile   the output file.
   * @param outputFormat the output file format.
   * @return [[Unit]].
   */
  def queryToFile(
    sparql: Construct,
    graphIri: InternalIri,
    outputFile: zio.nio.file.Path,
    outputFormat: QuadFormat,
  ): Task[Unit]

  /**
   * Requests the contents of a named graph, saving the response in a file.
   *
   * @param graphIri     the IRI of the named graph.
   * @param outputFile   the file to be written.
   * @param outputFormat the output file format.
   */
  def downloadGraph(graphIri: InternalIri, outputFile: zio.nio.file.Path, outputFormat: QuadFormat): Task[Unit]

  /**
   * Resets the content of the triplestore with the data supplied with the request.
   * First performs `dropAllTriplestoreContent` and afterwards `insertDataIntoTriplestore`.
   *
   * @param rdfDataObjects  a sequence of paths and graph names referencing data that needs to be inserted.
   * @param prependDefaults denotes if the rdfDataObjects list should be prepended with a default set. Default is `true`.
   */
  def resetTripleStoreContent(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean = true,
  ): Task[Unit]

  /**
   * Wipes all triplestore data out using HTTP requests.
   */
  def dropDataGraphByGraph(): Task[Unit]

  /**
   * Inserts the data referenced inside the `rdfDataObjects` by appending it to a default set of `rdfDataObjects`
   * based on the list defined in `application.conf` under the `app.triplestore.default-rdf-data` key.
   *
   * @param rdfDataObjects  a sequence of paths and graph names referencing data that needs to be inserted.
   * @param prependDefaults denotes if the rdfDataObjects list should be prepended with a default set. Default is `true`.
   * @return [[Unit]]
   */
  def insertDataIntoTriplestore(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean,
  ): Task[Unit]

  /**
   * Checks the Fuseki triplestore if it is available and configured correctly. If it is not
   * configured, tries to automatically configure () the required dataset.
   */
  def checkTriplestore(): Task[TriplestoreStatus]

  /**
   * Dumps the whole repository in N-Quads format, saving the response in a file.
   *
   * @param outputFile the output file.
   */
  def downloadRepository(outputFile: Path, graphs: GraphsForMigration): Task[Unit]

  /**
   * Uploads repository content from an N-Quads file.
   *
   * @param inputFile an N-Quads file containing the content to be uploaded to the repository.
   */
  def uploadRepository(inputFile: Path): Task[Unit]

  def dropGraph(graphName: String): Task[Unit]

  def compact(): Task[Boolean]
}

object TriplestoreService {
  object Queries {

    enum SparqlTimeout {
      case Standard
      case Maintenance
      case Gravsearch
    }

    sealed trait SparqlQuery {
      val sparql: String
      def timeout: SparqlTimeout = Standard
    }

    case class Ask(sparql: String) extends SparqlQuery
    object Ask {
      def apply(sparql: TxtFormat.Appendable): Ask = Ask(sparql.toString)
    }

    case class Select(sparql: String, override val timeout: SparqlTimeout = SparqlTimeout.Standard) extends SparqlQuery
    object Select {
      def apply(sparql: TxtFormat.Appendable): Select = Select(sparql.toString)

      def gravsearch(sparql: TxtFormat.Appendable): Select = Select.gravsearch(sparql.toString)
      def gravsearch(sparql: String): Select               = Select(sparql, SparqlTimeout.Gravsearch)
    }

    case class Construct(sparql: String, override val timeout: SparqlTimeout = SparqlTimeout.Standard)
        extends SparqlQuery
    object Construct {
      def apply(query: ConstructQuery): Construct        = Construct(query.getQueryString)
      def apply(sparql: TxtFormat.Appendable): Construct = Construct(sparql.toString)

      def gravsearch(query: ConstructQuery): Construct       = Construct.gravsearch(query.getQueryString)
      def gravsearch(query: TxtFormat.Appendable): Construct = Construct.gravsearch(query.toString)
      def gravsearch(query: String): Construct               = Construct(query, SparqlTimeout.Gravsearch)
    }

    case class Update(sparql: String) extends SparqlQuery
    object Update {
      def apply(sparql: TxtFormat.Appendable): Update = Update(sparql.toString())
      def apply(query: ModifyQuery): Update           = Update(query.getQueryString)
      def apply(query: InsertDataQuery): Update       = Update(query.getQueryString)
    }
  }
}
