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

package org.knora.webapi.util.search.gravsearch

import org.knora.webapi.OntologyConstants
import org.knora.webapi.util.search.QueryVariable


/**
  * Utility methods for [[org.knora.webapi.responders.v2.SearchResponderV2]] (Gravsearch).
  */
object GravsearchUtilV2 {

    object FulltextSearch {

        /**
          * Constants for fulltext query.
          *
          * These constants are used to create SPARQL CONSTRUCT queries to be executed by the triplestore and to process the results that are returned.
          */
        object FullTextSearchConstants {

            // SPARQL variable representing the concatenated IRIs of value objects matching the search criteria
            val valueObjectConcatVar: QueryVariable = QueryVariable("valueObjectConcat")

            // SPARQL variable representing the resources matching the search criteria
            val resourceVar: QueryVariable = QueryVariable("resource")

            // SPARQL variable representing the predicates of a resource
            val resourcePropVar: QueryVariable = QueryVariable("resourceProp")

            // SPARQL variable representing the objects of a resource
            val resourceObjectVar: QueryVariable = QueryVariable("resourceObj")

            // SPARQL variable representing the property pointing to a value object from a resource
            val resourceValueProp: QueryVariable = QueryVariable("resourceValueProp")

            // SPARQL variable representing the value objects of a resource
            val resourceValueObject: QueryVariable = QueryVariable("resourceValueObject")

            // SPARQL variable representing the predicates of a value object
            val resourceValueObjectProp: QueryVariable = QueryVariable("resourceValueObjectProp")

            // SPARQL variable representing the objects of a value object
            val resourceValueObjectObj: QueryVariable = QueryVariable("resourceValueObjectObj")

            // SPARQL variable representing the standoff nodes of a (text) value object
            val standoffNodeVar: QueryVariable = QueryVariable("standoffNode")

            // SPARQL variable representing the predicates of a standoff node of a (text) value object
            val standoffPropVar: QueryVariable = QueryVariable("standoffProp")

            // SPARQL variable representing the objects of a standoff node of a (text) value object
            val standoffValueVar: QueryVariable = QueryVariable("standoffValue")
        }
    }

    object FullTextSearchCount {

    }

    object Gravsearch {

        /**
          * Constants used in the processing of Gravsearch queries.
          *
          * These constants are used to create SPARQL CONSTRUCT queries to be executed by the triplestore and to process the results that are returned.
          */
        object GravsearchConstants {

            // SPARQL variable representing the main resource and its properties
            val mainResourceVar: QueryVariable = QueryVariable("mainResourceVar")

            // SPARQL variable representing main and dependent resources
            val mainAndDependentResourceVar: QueryVariable = QueryVariable("mainAndDependentResource")

            // SPARQL variable representing the predicates of the main and dependent resources
            val mainAndDependentResourcePropVar: QueryVariable = QueryVariable("mainAndDependentResourceProp")

            // SPARQL variable representing the objects of the main and dependent resources
            val mainAndDependentResourceObjectVar: QueryVariable = QueryVariable("mainAndDependentResourceObj")

            // SPARQL variable representing the value objects of the main and dependent resources
            val mainAndDependentResourceValueObject: QueryVariable = QueryVariable("mainAndDependentResourceValueObject")

            // SPARQL variable representing the properties pointing to value objects from the main and dependent resources
            val mainAndDependentResourceValueProp: QueryVariable = QueryVariable("mainAndDependentResourceValueProp")

            // SPARQL variable representing the predicates of value objects of the main and dependent resources
            val mainAndDependentResourceValueObjectProp: QueryVariable = QueryVariable("mainAndDependentResourceValueObjectProp")

            // SPARQL variable representing the objects of value objects of the main and dependent resources
            val mainAndDependentResourceValueObjectObj: QueryVariable = QueryVariable("mainAndDependentResourceValueObjectObj")

            // SPARQL variable representing the standoff nodes of a (text) value object
            val standoffNodeVar: QueryVariable = QueryVariable("standoffNode")

            // SPARQL variable representing the predicates of a standoff node of a (text) value object
            val standoffPropVar: QueryVariable = QueryVariable("standoffProp")

            // SPARQL variable representing the objects of a standoff node of a (text) value object
            val standoffValueVar: QueryVariable = QueryVariable("standoffValue")

            // SPARQL variable representing a list node pointed to by a (list) value object
            val listNode: QueryVariable = QueryVariable("listNode")

            // SPARQL variable representing the label of a list node pointed to by a (list) value object
            val listNodeLabel: QueryVariable = QueryVariable("listNodeLabel")

            // A set of types that can be treated as dates by the knora-api:toSimpleDate function.
            val dateTypes = Set(OntologyConstants.KnoraApiV2WithValueObjects.DateValue, OntologyConstants.KnoraApiV2WithValueObjects.StandoffTag)
        }

    }

    object GravsearchCount {

    }

}
