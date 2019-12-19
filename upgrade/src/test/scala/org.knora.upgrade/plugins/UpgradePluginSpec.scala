/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.upgrade.plugins

import java.io.{BufferedReader, File, FileReader}

import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.model.impl.LinkedHashModel
import org.eclipse.rdf4j.query.{Binding, TupleQuery, TupleQueryResult}
import org.eclipse.rdf4j.repository.sail.{SailRepository, SailRepositoryConnection}
import org.eclipse.rdf4j.rio.helpers.StatementCollector
import org.eclipse.rdf4j.rio.{RDFFormat, RDFParser, Rio}
import org.eclipse.rdf4j.sail.memory.MemoryStore
import org.knora.webapi.messages.store.triplestoremessages.{SparqlSelectResponse, SparqlSelectResponseBody, SparqlSelectResponseHeader, VariableResultsRow}
import org.knora.webapi.util.ErrorHandlingMap
import org.scalatest.{Matchers, WordSpecLike}

import scala.collection.JavaConverters._
import scala.collection.mutable.ArrayBuffer

/**
  * Provides helper methods for specs that test upgrade plugins.
  */
abstract class UpgradePluginSpec extends WordSpecLike with Matchers {
    /**
      * Parses a TriG file and returns it as an RDF4J [[Model]].
      *
      * @param path the file path of the TriG file.
      * @return a [[Model]].
      */
    def trigFileToModel(path: String): Model = {
        val trigParser: RDFParser = Rio.createParser(RDFFormat.TRIG)
        val fileReader = new BufferedReader(new FileReader(new File(path)))
        val model = new LinkedHashModel()
        trigParser.setRDFHandler(new StatementCollector(model))
        trigParser.parse(fileReader, "")
        model
    }

    /**
      * Makes an in-memory RDF4J [[SailRepository]] containing a [[Model]].
      *
      * @param model the model to be added to the repository.
      * @return the repository.
      */
    def makeRepository(model: Model): SailRepository = {
        val repository = new SailRepository(new MemoryStore())
        repository.init()
        val connection: SailRepositoryConnection = repository.getConnection
        connection.add(model)
        connection.close()
        repository
    }

    /**
      * Wraps expected SPARQL SELECT results in a [[SparqlSelectResponseBody]].
      *
      * @param rows the expected results.
      * @return a [[SparqlSelectResponseBody]] containing the expected results.
      */
    def expectedResult(rows: Seq[Map[String, String]]): SparqlSelectResponseBody = {
        val rowMaps = rows.map {
            mapToWrap => VariableResultsRow(new ErrorHandlingMap[String, String](mapToWrap, { key: String => s"No value found for SPARQL query variable '$key' in query result row" }))
        }

        SparqlSelectResponseBody(bindings = rowMaps)
    }

    /**
      * Does a SPARQL SELECT query using a connection to an RDF4J [[SailRepository]].
      *
      * @param selectQuery the query.
      * @param connection a connection to the repository.
      * @return a [[SparqlSelectResponse]] containing the query results.
      */
    def doSelect(selectQuery: String, connection: SailRepositoryConnection): SparqlSelectResponse = {
        val tupleQuery: TupleQuery = connection.prepareTupleQuery(selectQuery)
        val tupleQueryResult: TupleQueryResult = tupleQuery.evaluate

        val header = SparqlSelectResponseHeader(tupleQueryResult.getBindingNames.asScala)
        val rowBuffer = ArrayBuffer.empty[VariableResultsRow]

        while (tupleQueryResult.hasNext) {
            val bindings: Iterable[Binding] = tupleQueryResult.next.asScala

            val rowMap: Map[String, String] = bindings.map {
                binding => binding.getName -> binding.getValue.stringValue
            }.toMap

            rowBuffer.append(VariableResultsRow(new ErrorHandlingMap[String, String](rowMap, { key: String => s"No value found for SPARQL query variable '$key' in query result row" })))
        }

        tupleQueryResult.close()

        SparqlSelectResponse(
            head = header,
            results = SparqlSelectResponseBody(bindings = rowBuffer)
        )
    }
}
