/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.util

import org.knora.webapi.messages.store.triplestoremessages.{SparqlSelectResponse, SparqlSelectResponseBody, SparqlSelectResponseHeader, VariableResultsRow}
import spray.json._

/**
  * A spray-json protocol that parses JSON returned by a SPARQL endpoint. Empty values and empty rows are
  * ignored.
  */
object SparqlResultProtocol extends DefaultJsonProtocol {

    /**
      * Converts a [[JsValue]] to a [[VariableResultsRow]].
      */
    implicit object VariableResultsJsonFormat extends JsonFormat[VariableResultsRow] {
        def read(jsonVal: JsValue): VariableResultsRow = {

            // Collapse the JSON structure into a simpler Map of SPARQL variable names to values.
            val mapToWrap: Map[String, String] = jsonVal.asJsObject.fields.foldLeft(Map.empty[String, String]) {
                case (acc, (key, value)) => value.asJsObject.getFields("value") match {
                    case Seq(JsString(valueStr)) if valueStr.nonEmpty => // Ignore empty strings.
                        acc + (key -> valueStr)
                    case _ => acc
                }
            }

            // Wrap that Map in an ErrorHandlingMap that will gracefully report errors about missing values when they
            // are accessed later.
            VariableResultsRow(new ErrorHandlingMap(mapToWrap, { key: String => s"No value found for SPARQL query variable '$key' in query result row" }))
        }

        def write(variableResultsRow: VariableResultsRow): JsValue = ???
    }

    /**
      * Converts a [[JsValue]] to a [[SparqlSelectResponseBody]].
      */
    implicit object SparqlSelectResponseBodyFormat extends JsonFormat[SparqlSelectResponseBody] {
        def read(jsonVal: JsValue): SparqlSelectResponseBody = {
            jsonVal.asJsObject.fields.get("bindings") match {
                case Some(bindingsJson: JsArray) =>
                    // Filter out empty rows.
                    SparqlSelectResponseBody(bindingsJson.convertTo[Seq[VariableResultsRow]].filter(_.rowMap.keySet.nonEmpty))

                case _ => SparqlSelectResponseBody(Nil)
            }
        }

        def write(sparqlSelectResponseBody: SparqlSelectResponseBody): JsValue = ???
    }

    implicit val headerFormat: JsonFormat[SparqlSelectResponseHeader] = jsonFormat1(SparqlSelectResponseHeader)
    implicit val responseFormat: JsonFormat[SparqlSelectResponse] = jsonFormat2(SparqlSelectResponse)
}
