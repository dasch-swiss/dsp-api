/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

/**
  * Utility functions for converting to SPARQL literal strings.
  */
object SparqlUtil {

    /**
      * Converts any given value of any type to a SPARQL literal string.
      * @param value the value to be converted.
      * @return a string containing the SPARQL literal.
      */
    def any2SparqlLiteral(value: Any): String = {
        value match {
            case value: Boolean => if (value.asInstanceOf[Boolean]) {
                "\"true\"^^xsd:boolean"
            } else {
                "\"false\"^^xsd:boolean"
            }
            case value: String if value.nonEmpty => "\"" + value.asInstanceOf[String] + "\"^^xsd:string"
            case value: String if value.isEmpty => ""
        }
    }
}
