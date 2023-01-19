/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.twirl

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
case class SearchCriterion(
  propertyIri: IRI,
  comparisonOperator: SearchComparisonOperatorV1.Value,
  valueType: IRI,
  searchValue: Option[String] = None,
  dateStart: Option[Int] = None,
  dateEnd: Option[Int] = None,
  matchBooleanPositiveTerms: Set[String] = Set.empty[String],
  matchBooleanNegativeTerms: Set[String] = Set.empty[String]
)
