/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.api
import org.apache.jena.query.Dataset
import org.apache.jena.query.QueryExecution
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QuerySolution
import org.apache.jena.query.ResultSet
import zio.Ref
import zio.Scope
import zio.UIO
import zio.ZIO
import zio.ZLayer

import java.nio.file.Path
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.jdk.CollectionConverters.IteratorHasAsScala

import org.knora.webapi.IRI
import org.knora.webapi.messages.store.triplestoremessages.CheckTriplestoreResponse
import org.knora.webapi.messages.store.triplestoremessages.DropAllRepositoryContentACK
import org.knora.webapi.messages.store.triplestoremessages.DropDataGraphByGraphACK
import org.knora.webapi.messages.store.triplestoremessages.FileWrittenResponse
import org.knora.webapi.messages.store.triplestoremessages.InsertGraphDataContentResponse
import org.knora.webapi.messages.store.triplestoremessages.InsertTriplestoreContentACK
import org.knora.webapi.messages.store.triplestoremessages.NamedGraphDataResponse
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.RepositoryUploadedResponse
import org.knora.webapi.messages.store.triplestoremessages.ResetRepositoryContentACK
import org.knora.webapi.messages.store.triplestoremessages.SparqlAskResponse
import org.knora.webapi.messages.store.triplestoremessages.SparqlConstructRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlConstructResponse
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse
import org.knora.webapi.messages.store.triplestoremessages.SparqlUpdateResponse
import org.knora.webapi.messages.util.rdf.QuadFormat
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.rdf.SparqlSelectResultBody
import org.knora.webapi.messages.util.rdf.SparqlSelectResultHeader
import org.knora.webapi.messages.util.rdf.VariableResultsRow

final case class TriplestoreServiceFake(datasetRef: Ref[Dataset]) extends TriplestoreService {

  override def doSimulateTimeout(): UIO[SparqlSelectResult] = ???

  override def sparqlHttpSelect(
    sparql: String,
    simulateTimeout: Boolean,
    isGravsearch: Boolean
  ): UIO[SparqlSelectResult] = {
    require(!simulateTimeout, "`simulateTimeout` parameter is not supported by fake implementation yet")
    require(!isGravsearch, "`isGravsearch` parameter is not supported by fake implementation yet")

    ZIO.scoped(execSelect(sparql).map(toSparqlSelectResult)).orDie
  }

  override def sparqlHttpAsk(query: String): UIO[SparqlAskResponse] =
    ZIO.scoped {
      getQueryExecution(query).map(_.execAsk())
    }.map(SparqlAskResponse).orDie

  private def execSelect(query: String): ZIO[Any with Scope, Throwable, ResultSet] = {
    def executeQuery(qExec: QueryExecution) = ZIO.attempt(qExec.execSelect).debug("exec query")
    def closeResultSet(rs: ResultSet)       = ZIO.succeed(rs.close())
    for {
      qExec <- getQueryExecution(query)
      rs    <- ZIO.acquireRelease(executeQuery(qExec))(closeResultSet).debug
    } yield rs
  }

  private def getQueryExecution(query: String): ZIO[Any with Scope, Throwable, QueryExecution] = {
    def acquire(ds: Dataset, q: String) = ZIO.attempt(QueryExecutionFactory.create(q, ds))
    def release(qExec: QueryExecution)  = ZIO.succeed(qExec.close())
    for {
      ds    <- datasetRef.get.debug("got Dataset")
      qExec <- ZIO.acquireRelease(acquire(ds, query))(release).debug("got qExec")
    } yield qExec
  }

  private def toSparqlSelectResult(resultSet: ResultSet): SparqlSelectResult = {
    val header     = SparqlSelectResultHeader(resultSet.getResultVars.asScala.toList)
    val resultBody = getSelectResultBody(resultSet)
    SparqlSelectResult(header, resultBody)
  }

  private def getSelectResultBody(resultSet: ResultSet): SparqlSelectResultBody = {
    val rows: Seq[VariableResultsRow] = resultSet.asScala
      .foldRight(List.empty[VariableResultsRow])((solution, list) => list.prepended(asVariableResultsRow(solution)))
    SparqlSelectResultBody(rows)
  }

  private def asVariableResultsRow(solution: QuerySolution): VariableResultsRow = {
    val keyValueMap = solution.varNames.asScala.map { key =>
      val node = solution.get(key).asNode
      val value: String = // do not include datatype in string if node is a literal
        if (node.isLiteral) { node.getLiteralLexicalForm }
        else { node.toString }
      key -> value
    }.toMap
    VariableResultsRow(keyValueMap)
  }

  override def sparqlHttpConstruct(sparqlConstructRequest: SparqlConstructRequest): UIO[SparqlConstructResponse] = ???

  override def sparqlHttpExtendedConstruct(
    sparqlExtendedConstructRequest: SparqlExtendedConstructRequest
  ): UIO[SparqlExtendedConstructResponse] = ???

  override def sparqlHttpConstructFile(
    sparql: String,
    graphIri: IRI,
    outputFile: Path,
    outputFormat: QuadFormat
  ): UIO[FileWrittenResponse] = ???

  override def sparqlHttpUpdate(sparqlUpdate: String): UIO[SparqlUpdateResponse] = ???

  override def sparqlHttpGraphFile(
    graphIri: IRI,
    outputFile: Path,
    outputFormat: QuadFormat
  ): UIO[FileWrittenResponse] = ???

  override def sparqlHttpGraphData(graphIri: IRI): UIO[NamedGraphDataResponse] = ???

  override def resetTripleStoreContent(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean
  ): UIO[ResetRepositoryContentACK] = ???

  override def dropAllTriplestoreContent(): UIO[DropAllRepositoryContentACK] = ???

  override def dropDataGraphByGraph(): UIO[DropDataGraphByGraphACK] = ???

  override def insertDataIntoTriplestore(
    rdfDataObjects: List[RdfDataObject],
    prependDefaults: Boolean
  ): UIO[InsertTriplestoreContentACK] = ???

  override def checkTriplestore(): UIO[CheckTriplestoreResponse] = ???

  override def downloadRepository(outputFile: Path): UIO[FileWrittenResponse] = ???

  override def uploadRepository(inputFile: Path): UIO[RepositoryUploadedResponse] = ???

  override def insertDataGraphRequest(graphContent: String, graphName: String): UIO[InsertGraphDataContentResponse] =
    ???
}

object TriplestoreServiceFake {
  val layer: ZLayer[Ref[Dataset], Nothing, TriplestoreService] = ZLayer.fromFunction(TriplestoreServiceFake.apply _)
}
