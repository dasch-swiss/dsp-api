/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.twirl

import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.searchmessages.SearchComparisonOperatorV1

/**
  * The extended search template's representation of an extended search criterion.
  *
  * @param propertyIri               the IRI of the property to be searched.
  * @param comparisonOperator        the comparison operator.
  * @param valueType                 the type of value to search for.
  * @param searchValue               the value to compare with, if we are comparing strings or numbers.
  * @param dateStart                 the start of the date range to compare with, if we are comparing dates.
  * @param dateEnd                   the end of the date range to compare with, if we are comparing dates.
  * @param matchBooleanPositiveTerms the terms to include if we are using MATCH BOOLEAN.
  * @param matchBooleanNegativeTerms the terms to exclude if we are using MATCH BOOLEAN.
  */
case class SearchCriterion(propertyIri: IRI,
                           comparisonOperator: SearchComparisonOperatorV1.Value,
                           valueType: IRI,
                           searchValue: Option[String] = None,
                           dateStart: Option[Int] = None,
                           dateEnd: Option[Int] = None,
                           matchBooleanPositiveTerms: Set[String] = Set.empty[String],
                           matchBooleanNegativeTerms: Set[String] = Set.empty[String])
