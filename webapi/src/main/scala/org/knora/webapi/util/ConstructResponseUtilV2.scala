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

package org.knora.webapi.util

import org.knora.webapi.messages.v1.store.triplestoremessages.SparqlConstructResponse
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, OntologyConstants}

object ConstructResponseUtilV2 {

    case class ResourcesAndValueObjects(resources: Map[IRI, Seq[(IRI, String)]], valueObjects: Map[IRI, Seq[(IRI, String)]])

    /**
      * A [[SparqlConstructResponse]] may contain both resources and value objects.
      * This method splits the results by their type and returns them as separate structures.
      *
      * @param constructQueryResults the results of a SPARQL construct query.
      * @return a [[ResourcesAndValueObjects]].
      */
    def splitResourcesAndValueObjects(constructQueryResults: SparqlConstructResponse): ResourcesAndValueObjects = {

        val (valueObjects: Map[IRI, Seq[(IRI, String)]], resources: Map[IRI, Seq[(IRI, String)]]) = constructQueryResults.statements.partition {
            case (subject: IRI, assertions: Seq[(IRI, String)]) =>

                // group assertions by predicate (assertions is a sequence of 2 tuples: pred, obj)
                val groupedByPredicate: Map[IRI, Seq[(IRI, String)]] = assertions.groupBy {
                    case (pred: IRI, obj: String) =>
                        pred
                }

                val rdfType = getPredicateValueFromAssertions(subject, OntologyConstants.Rdf.Type, assertions)

                // returns true if it is a valueObject, false in case of a resource
                OntologyConstants.KnoraBase.ValueClasses.contains(rdfType)

        }

        ResourcesAndValueObjects(resources = resources, valueObjects = valueObjects)

    }

    def getPredicateValueFromAssertions(subjectIri: IRI, predicate: IRI, assertions: Seq[(IRI, String)]): String = {

        // group assertions by predicate (assertions is a sequence of 2 tuples: pred, obj)
        val groupedByPredicate: Map[IRI, Seq[(IRI, String)]] = assertions.groupBy {
            case (pred: IRI, obj: String) =>
                pred
        }

        // get the assertion representing the predicate
        val assertionForPredicate: Seq[(IRI, String)] = groupedByPredicate.getOrElse(predicate, throw InconsistentTriplestoreDataException(s"no $predicate given for $subjectIri"))

        // make sure that there is exactly one assertion for predicate
        if (assertionForPredicate.size != 1) throw InconsistentTriplestoreDataException(s"not exactly one $predicate given for $subjectIri")

        // the assertion is a 2 tuple: pre, obj
        // get the object representing value of the predicate
        assertionForPredicate.head._2

    }

    
}