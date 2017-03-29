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

package org.knora.webapi.responders.v2

import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlConstructRequest, SparqlConstructResponse}
import org.knora.webapi.messages.v2.responder.searchmessages.{FulltextSearchGetRequestV2, SearchGetResponseV2, SearchResourceResultRowV2, SearchValueResultRowV2}
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.ConstructResponseUtilV2
import org.knora.webapi.util.ConstructResponseUtilV2.ResourcesAndValueObjects

import scala.concurrent.Future

class SearchResponderV2 extends Responder {

    def receive = {
        case searchGetRequest: FulltextSearchGetRequestV2 => future2Message(sender(), fulltextSearchV2(searchGetRequest), log)
    }

    private def fulltextSearchV2(searchGetRequest: FulltextSearchGetRequestV2): Future[SearchGetResponseV2] = {

        for {
            searchSparql <- Future(queries.sparql.v2.txt.searchFulltext(
                triplestore = settings.triplestoreType,
                searchTerms = searchGetRequest.searchValue
            ).toString())

            searchResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(searchSparql)).mapTo[SparqlConstructResponse]

            // separate resources and value objects
            queryResultsSeparated: ResourcesAndValueObjects = ConstructResponseUtilV2.splitResourcesAndValueObjects(constructQueryResults = searchResponse)

            resourceResultRows: Seq[SearchResourceResultRowV2] = queryResultsSeparated.resources.map {
                case (resourceIri: IRI, assertions: Seq[(IRI, String)]) =>

                    val rdfLabel = ConstructResponseUtilV2.getPredicateValueFromAssertions(subjectIri = resourceIri, predicate = OntologyConstants.Rdfs.Label, assertions = assertions)

                    val resourceClass = ConstructResponseUtilV2.getPredicateValueFromAssertions(subjectIri = resourceIri, predicate = OntologyConstants.Rdf.Type, assertions = assertions)

                    // get all the objects from the assertions
                    val objects: Seq[String] = assertions.map {
                        case (pred, obj) =>
                            obj
                    }

                    // check if one or more of the objects points to a value object
                    val valueObjectIris: Set[IRI] = queryResultsSeparated.valueObjects.keySet.intersect(objects.toSet)

                    SearchResourceResultRowV2(
                        resourceIri = resourceIri,
                        resourceClass = resourceClass,
                        label = rdfLabel,
                        valueObjects = valueObjectIris.map {
                            (valObj: IRI) =>

                                val valueObjectClass = ConstructResponseUtilV2.getPredicateValueFromAssertions(subjectIri = valObj, predicate = OntologyConstants.Rdf.Type, assertions = queryResultsSeparated.valueObjects.
                                    getOrElse(valObj, throw InconsistentTriplestoreDataException(s"value object not found $valObj")))

                                val valueObjectValueHasString = ConstructResponseUtilV2.getPredicateValueFromAssertions(subjectIri = valObj, predicate = OntologyConstants.KnoraBase.ValueHasString, assertions = queryResultsSeparated.valueObjects.
                                    getOrElse(valObj, throw InconsistentTriplestoreDataException(s"value object not found $valObj")))

                                // get the property that points from the resource to the value object
                                val propertyIri = assertions.find {
                                    case (pred, obj) =>
                                        obj == valObj
                                }.map(_._1)

                                SearchValueResultRowV2(
                                    valueClass = valueObjectClass,
                                    value = valueObjectValueHasString,
                                    valueObjectIri = valObj,
                                    propertyIri = propertyIri.getOrElse(throw InconsistentTriplestoreDataException(s"no property connecting $resourceIri with $valObj"))
                                )
                        }.toVector
                    )
            }.toVector


        } yield SearchGetResponseV2(nhits = queryResultsSeparated.resources.size, results = resourceResultRows)

    }
}