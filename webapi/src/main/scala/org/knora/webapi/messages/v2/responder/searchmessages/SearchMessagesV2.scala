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

package org.knora.webapi.messages.v2.responder.searchmessages

import org.knora.webapi.IRI
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.util.search.v2.SimpleConstructQuery

/**
  * An abstract trait for messages that can be sent to `SearchResponderV2`.
  */
sealed trait SearchResponderRequestV2 extends KnoraRequestV2 {

    def userProfile: UserProfileV1
}

/**
  * Requests a fulltext search. A successful response will be a [[ReadResourcesSequenceV2]].
  *
  * @param searchValue the values to search for.
  * @param userProfile the profile of the user making the request.
  */
case class FulltextSearchGetRequestV2(searchValue: String,
                                      userProfile: UserProfileV1) extends SearchResponderRequestV2

/**
  *
  * Requests an extended search. A successful response will be a [[ReadResourcesSequenceV2]].
  *
  * @param constructQuery a Sparql construct query provided by the client.
  * @param userProfile the profile of the user making the request.
  */
case class ExtendedSearchGetRequestV2(constructQuery: SimpleConstructQuery,
                                      userProfile: UserProfileV1) extends SearchResponderRequestV2


case class ExtendedSearchFilterPattern(expression: ExtendedSearchFilterExpression) extends ExtendedSearchQueryPattern {
    def rdfValue: String = {
        s"FILTER(${expression.rdfValue})"
    }
}

// An abstract trait representing a filter expression
sealed trait ExtendedSearchFilterExpression {

    def rdfValue: String
}

sealed trait ExtendedSearchQueryPattern {

    def rdfValue: String
}

case class ExtendedSearchOptionalPattern(patterns: Seq[ExtendedSearchQueryPattern]) extends ExtendedSearchQueryPattern {

    def rdfValue: String = ???
}

/**
  * Represents a comparison expression in a FILTER.
  *
  * @param leftArg  the left argument.
  * @param operator the operator.
  * @param rightArg the right argument.
  */
case class ExtendedSearchCompareExpression(leftArg: ExtendedSearchFilterExpression, operator: String, rightArg: ExtendedSearchFilterExpression) extends ExtendedSearchFilterExpression {

    def rdfValue = s"${leftArg.rdfValue} $operator ${rightArg.rdfValue}"
}

/**
  * Represents an AND expression in a filter.
  *
  * @param leftArg  the left argument.
  * @param rightArg the right argument.
  */
case class ExtendedSearchAndExpression(leftArg: ExtendedSearchFilterExpression, rightArg: ExtendedSearchFilterExpression) extends ExtendedSearchFilterExpression {

    def rdfValue = s"${leftArg.rdfValue} && ${rightArg.rdfValue}"
}

/**
  * Represents an OR expression in a filter.
  *
  * @param leftArg  the left argument.
  * @param rightArg the right argument.
  */
case class ExtendedSearchOrExpression(leftArg: ExtendedSearchFilterExpression, rightArg: ExtendedSearchFilterExpression) extends ExtendedSearchFilterExpression {

    def rdfValue = s"${leftArg.rdfValue} || ${rightArg.rdfValue}"
}


// An abstract trait representing a an entity in an extended search query.
sealed trait ExtendedSearchEntity extends ExtendedSearchFilterExpression

/**
  * A variable in a Sparql query.
  *
  * @param variableName the name of the variable.
  */
case class ExtendedSearchVar(variableName: String) extends ExtendedSearchEntity {

    def rdfValue: String = s"?$variableName"
}

/**
  * Represents a Knora internal entity in a Sparql query (property or resource class).
  *
  * @param iri the Iri of the internal entity.
  */
case class ExtendedSearchInternalEntityIri(iri: IRI) extends ExtendedSearchEntity {

    def rdfValue = s"<$iri>"
}

/**
  * Represents an Iri in a Sparql query that is not a Knora internal entity.
  *
  * @param iri the Iri of the external entity.
  */
case class ExtendedSearchIri(iri: IRI) extends ExtendedSearchEntity {

    def rdfValue = s"<$iri>"
}

/**
  * Represents a literal in a Sparql query.
  *
  * @param value    the value of the literal
  * @param datatype the datatype of the literal.
  */
case class ExtendedSearchXsdLiteral(value: String, datatype: IRI) extends ExtendedSearchEntity {

    def rdfValue = s""""${value}"^^<$datatype>"""
}

/**
  * Represents a statement (triple) in a Sparql query
  *
  * @param subj the statement's subject.
  * @param pred the statement's predicate.
  * @param obj  the statement's object.
  */
case class ExtendedSearchStatementPattern(subj: ExtendedSearchEntity, pred: ExtendedSearchEntity, obj: ExtendedSearchEntity, disableInference: Boolean) extends ExtendedSearchQueryPattern {

    def rdfValue: String = s"${subj.rdfValue} ${pred.rdfValue} ${obj.rdfValue} ."

}

case class ExtendedSearchUnionPattern(blocks: Seq[Seq[ExtendedSearchQueryPattern]]) extends ExtendedSearchQueryPattern {
    def rdfValue: String = ???
}


/**
  * Represents an extended search query.
  *
  * @param constructClause the construct clause of the extended search query.
  * @param whereClause the where clause of the extended search query.
  */
case class ExtendedSearchQuery(constructClause: Vector[ExtendedSearchStatementPattern], whereClause: Vector[ExtendedSearchQueryPattern])

/**
  * Requests a search of resources by their label. A successful response will be a [[ReadResourcesSequenceV2]].
  *
  * @param searchValue the values to search for.
  * @param userProfile the profile of the user making the request.
  */
case class SearchResourceByLabelRequestV2(searchValue: String,
                                          userProfile: UserProfileV1) extends SearchResponderRequestV2