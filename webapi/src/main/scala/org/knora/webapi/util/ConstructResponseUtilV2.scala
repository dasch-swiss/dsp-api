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

import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.messages.v1.store.triplestoremessages.SparqlConstructResponse
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.{IRI, OntologyConstants}



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

        // 2 tuple: value objects (._1) and resources (._2)
        val splitResults: (Map[IRI, Seq[(IRI, String)]], Map[IRI, Seq[(IRI, String)]]) = constructQueryResults.statements.partition {
            case (subjectIri: IRI, assertions: Seq[(IRI, String)]) =>

                val predicateMap: Map[IRI, String] = new ErrorHandlingMap(assertions.toMap, { key: IRI => s"Predicate $key not found for $subjectIri" })

                val rdfType = predicateMap(OntologyConstants.Rdf.Type)

                // TODO: add support for standoff nodes

                // returns true if it is a valueObject, false in case of a resource
                OntologyConstants.KnoraBase.ValueClasses.contains(rdfType)

        }

        ResourcesAndValueObjects(resources = splitResults._2, valueObjects = splitResults._1)

    }

    def createValueV2FromSparqlResults(valueObjects: Map[IRI, Seq[(IRI, String)]]): Map[IRI, ValueV2] = {

        valueObjects.map {
            case (valObjIri: IRI, valueAssertions: Seq[(IRI, String)]) =>

                // make predicate the keys of a map
                val predicateMapForValueObj: ErrorHandlingMap[IRI, String] = new ErrorHandlingMap(valueAssertions.toMap, { key: IRI => s"Predicate $key not found for $valObjIri" })

                val valueObjectClass = predicateMapForValueObj(OntologyConstants.Rdf.Type)

                val valueObjectValueHasString: String = predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasString)

                val valueCommentOption: Option[String] = predicateMapForValueObj.get(OntologyConstants.KnoraBase.ValueHasComment)

                val valueV2: ValueV2 = valueObjectClass match {
                    case OntologyConstants.KnoraBase.TextValue =>
                        // TODO: handle standoff mapping and conversion to XML
                        TextValueV2(valueHasString = valueObjectValueHasString, comment = valueCommentOption)

                    case OntologyConstants.KnoraBase.DateValue =>

                        DateValueV2(
                            valueHasString = valueObjectValueHasString,
                            valueHasStartJDN = predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasStartJDN).toInt,
                            valueHasEndJDN = predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasEndJDN).toInt,
                            valueHasStartPrecision = KnoraPrecisionV1.lookup(predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasStartPrecision)),
                            valueHasEndPrecision = KnoraPrecisionV1.lookup(predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasEndPrecision)),
                            valueHasCalendar = KnoraCalendarV1.lookup(predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasCalendar)),
                            comment = valueCommentOption
                        )

                    case OntologyConstants.KnoraBase.IntValue =>
                        IntegerValueV2(valueHasString = valueObjectValueHasString, valueHasInteger = predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasInteger).toInt, comment = valueCommentOption)

                    case OntologyConstants.KnoraBase.DecimalValue =>
                        DecimalValueV2(valueHasString = valueObjectValueHasString, valueHasDecimal = BigDecimal(predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasDecimal)), comment = valueCommentOption)

                    // TODO: implement all value object classes (file values)
                    case other =>
                        TextValueV2(valueHasString = valueObjectValueHasString, comment = valueCommentOption)
                }

                (valObjIri, valueV2)
        }
    }

    def createResponseForResources(queryResultsSeparated: ResourcesAndValueObjects): Vector[ResourceV2_] = {

        queryResultsSeparated.resources.map {
            case (resourceIri: IRI, assertions: Seq[(IRI, String)]) =>

                // make predicate the keys of a map
                val predicateMapForResource: ErrorHandlingMap[IRI, String] = new ErrorHandlingMap(assertions.toMap, { key: IRI => s"Predicate $key not found for $resourceIri" })

                val rdfLabel: String = predicateMapForResource(OntologyConstants.Rdfs.Label)

                val resourceClass = predicateMapForResource(OntologyConstants.Rdf.Type)

                // get all the objects from the assertions
                val objects: Seq[String] = assertions.map {
                    case (pred, obj) =>
                        obj
                }

                val objMap = new ErrorHandlingMap(assertions.map {
                    case (pred, obj) =>
                        (obj, pred)
                }.toMap, { key: IRI => s"object $key not found for $resourceIri" })

                // check if one or more of the objects points to a value object
                // TODO: is this really necessary? Do we not already know all the value objects? What about standoff values (third level)?
                val valueObjectIris: Set[IRI] = queryResultsSeparated.valueObjects.keySet.intersect(objects.toSet)

                val valuesV2: Map[IRI, ValueV2] = createValueV2FromSparqlResults(queryResultsSeparated.valueObjects)

                val propertiesAsTuples: Vector[(IRI, ValueV2)] = valueObjectIris.map {
                    (valObjIri) =>
                        // get the property that points from the resource to the value object
                        val propertyIri = objMap(valObjIri)

                        (propertyIri, valuesV2(valObjIri))
                }.toVector

                val propMap: Map[IRI, Seq[ValueV2]] = propertiesAsTuples.foldLeft(Map.empty[IRI, Seq[ValueV2]]) {
                    case (acc: Map[IRI, Seq[ValueV2]], (propIri: IRI, value: ValueV2)) =>

                        if (acc.keySet.contains(propIri)) {
                            val existingValsForProp: Seq[ValueV2] = acc(propIri)

                            acc + (propIri -> (value +: existingValsForProp))

                        } else {
                            acc + (propIri -> Vector(value))
                        }
                }

                ResourceV2_(
                    resourceClass = resourceClass,
                    label = rdfLabel,
                    valueObjects = propMap
                )
        }.toVector

    }

}