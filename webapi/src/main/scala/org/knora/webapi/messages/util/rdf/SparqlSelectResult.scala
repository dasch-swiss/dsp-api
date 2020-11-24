/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.util.rdf

import org.knora.webapi.exceptions.InconsistentRepositoryDataException

/**
 * Represents the result of a SPARQL SELECT query.
 *
 * @param head    the header of the response, containing the variable names.
 * @param results the body of the response, containing rows of query results.
 */
case class SparqlSelectResult(head: SparqlSelectResultHeader, results: SparqlSelectResultBody) {

    /**
     * Returns the contents of the first row of results.
     *
     * @return a [[Map]] representing the contents of the first row of results.
     */
    def getFirstRow: VariableResultsRow = {
        results.bindings.headOption match {
            case Some(row: VariableResultsRow) => row
            case None => throw InconsistentRepositoryDataException(s"A SPARQL query unexpectedly returned an empty result")
        }
    }
}

/**
 * Represents the header of the result of a SPARQL SELECT query.
 *
 * @param vars the names of the variables that were used in the SPARQL SELECT statement.
 */
case class SparqlSelectResultHeader(vars: Seq[String])

/**
 * Represents the body of the result of a SPARQL SELECT query.
 *
 * @param bindings the bindings of values to the variables used in the SPARQL SELECT statement.
 *                 Empty rows are not allowed.
 */
case class SparqlSelectResultBody(bindings: Seq[VariableResultsRow]) {
    require(bindings.forall(_.rowMap.nonEmpty), "Empty rows are not allowed in a SparqlSelectResponseBody")
}

/**
 * Represents a row of results in the result of a SPARQL SELECT query.
 *
 * @param rowMap a map of variable names to values in the row. An empty string is not allowed as a variable
 *               name or value.
 */
case class VariableResultsRow(rowMap: Map[String, String]) {
    require(rowMap.forall {
        case (key, value) => key.nonEmpty && value.nonEmpty
    }, "An empty string is not allowed as a variable name or value in a VariableResultsRow")
}
