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

package org.knora.webapi.util.search

/**
  * A trait for classes that can get type information from a parsed SPARQL search query in different ways.
  */
trait TypeInspector {
    /**
      * Given the WHERE clause from a parsed SPARQL search query, returns information about the types found
      * in the query.
      *
      * TODO: change this method signature so it has a way of getting info about entity IRIs in the API ontologies.
      *
      * @param whereClause the SPARQL WHERE clause.
      * @return information about the types that were found in the query.
      */
    def inspectTypes(whereClause: WhereClause): TypeInspectionResult
}
