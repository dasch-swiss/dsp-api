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
import org.knora.webapi.{IRI, OntologyConstants}




object ConstructResponseUtilV2 {

    case class ValueObject(valueObjectIri: IRI, assertions: Seq[(IRI, String)], standoff: Map[IRI, Seq[(IRI, String)]])

    case class ResourceWithValues(resourceAssertions: Seq[(IRI, String)], valuePropertyAssertions: Map[IRI, Seq[ValueObject]], linkPropertyAssertions: Map[IRI, IRI])

    /**
      * A [[SparqlConstructResponse]] may contain both resources and value objects.
      * This method splits the results by their type and returns them as separate structures.
      *
      * @param constructQueryResults the results of a SPARQL construct query.
      * @return a Map[resource Iri -> [[ResourceWithValues]]].
      */
    def splitResourcesAndValueObjects(constructQueryResults: SparqlConstructResponse): Map[IRI, ResourceWithValues] = {

        // spilt statements about resources and other statements (value objects and standoff)
        val (resourceStatements: Map[IRI, Seq[(IRI, String)]], otherStatements: Map[IRI, Seq[(IRI, String)]]) = constructQueryResults.statements.partition {

            case (subjectIri: IRI, assertions: Seq[(IRI, String)]) =>

                assertions.contains((OntologyConstants.Rdf.Type, OntologyConstants.KnoraBase.Resource))

        }

        resourceStatements.map {
            case (resourceIri: IRI, assertions: Seq[(IRI, String)]) =>

                // remove inferred statements (non explicit)
                val assertionsFiltered: Seq[(IRI, String)] = assertions.filterNot {
                    case (pred, obj) =>
                        (pred == OntologyConstants.Rdf.Type && obj == OntologyConstants.KnoraBase.Resource) || pred == OntologyConstants.KnoraBase.HasValue || pred == OntologyConstants.KnoraBase.HasLinkTo
                }

                val objMap: ErrorHandlingMap[String, IRI] = new ErrorHandlingMap(assertionsFiltered.map {
                    case (pred, obj) =>
                        (obj, pred)
                }.toMap, { key: IRI => s"object $key not found for $resourceIri" })

                // get a map from property Iris to value object Iris
                val valuePropertyToObjectIris: Map[IRI, Seq[String]] = assertions.filter {
                    case (pred, obj) =>
                        pred == OntologyConstants.KnoraBase.HasValue
                }.foldLeft(Map.empty[IRI, Seq[String]]) {
                    case (acc, (hasValue: IRI, valObj: String)) =>
                        val propIri = objMap(valObj)

                        if (acc.keySet.contains(propIri)) {
                            val existingValsForProp = acc(propIri)

                            acc + (propIri -> (valObj +: existingValsForProp))

                        } else {
                            acc + (propIri -> Vector(valObj))
                        }

                }

                val valuePropertyToValueObject: Map[IRI, Seq[ValueObject]] = valuePropertyToObjectIris.map {
                    case (property: IRI, valObjs: Seq[String]) =>

                        (property, valObjs.map {
                            case valObjIri =>

                                // get all the standoff nodes possibly belonging to this value object
                                val standoffNodeIris: Seq[String] = otherStatements(valObjIri).filter {
                                    case (pred: IRI, obj: String) =>
                                        pred == OntologyConstants.KnoraBase.ValueHasStandoff
                                }.map {
                                    case (pred: IRI, obj: String) =>
                                        // we are only interested in the standoff node Iri
                                        obj
                                }

                                val standoffAssertions: Map[IRI, Seq[(IRI, String)]] = otherStatements.filter {
                                    case (subjIri: IRI, assertions: Seq[(IRI, String)]) =>
                                        standoffNodeIris.contains(subjIri)

                                }

                                ValueObject(valueObjectIri = valObjIri, assertions = otherStatements(valObjIri), standoff = standoffAssertions)

                        })

                }


                val linkPropToTargets: Map[IRI, String] = assertions.filter {
                    case (pred, obj) =>
                        pred == OntologyConstants.KnoraBase.HasLinkTo
                }.flatMap {
                    case (hasLinkTo, referredRes) =>
                        assertionsFiltered.filter {
                            case (pred, obj) =>
                                obj == referredRes
                        }
                }.toMap



                (resourceIri, ResourceWithValues(resourceAssertions = assertionsFiltered, valuePropertyAssertions = valuePropertyToValueObject, linkPropertyAssertions = linkPropToTargets))


        }



    }
/*
    def createValueV2FromSparqlResults(valueObjects: Map[IRI, Seq[(IRI, String)]]): Map[IRI, ValueObjectV2] = {

        valueObjects.map {
            case (valObjIri: IRI, valueAssertions: Seq[(IRI, String)]) =>

                // make predicate the keys of a map
                val predicateMapForValueObj: ErrorHandlingMap[IRI, String] = new ErrorHandlingMap(valueAssertions.toMap, { key: IRI => s"Predicate $key not found for $valObjIri (value object)" })

                val valueObjectClass = predicateMapForValueObj(OntologyConstants.Rdf.Type)

                val valueObjectValueHasString: String = predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasString)

                val valueCommentOption: Option[String] = predicateMapForValueObj.get(OntologyConstants.KnoraBase.ValueHasComment)

                val valueV2: ValueObjectV2 = valueObjectClass match {
                    case OntologyConstants.KnoraBase.TextValue =>
                        // TODO: handle standoff mapping and conversion to XML
                        TextValueObjectV2(valueHasString = valueObjectValueHasString, comment = valueCommentOption)

                    case OntologyConstants.KnoraBase.DateValue =>

                        DateValueObjectV2(
                            valueHasString = valueObjectValueHasString,
                            valueHasStartJDN = predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasStartJDN).toInt,
                            valueHasEndJDN = predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasEndJDN).toInt,
                            valueHasStartPrecision = KnoraPrecisionV1.lookup(predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasStartPrecision)),
                            valueHasEndPrecision = KnoraPrecisionV1.lookup(predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasEndPrecision)),
                            valueHasCalendar = KnoraCalendarV1.lookup(predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasCalendar)),
                            comment = valueCommentOption
                        )

                    case OntologyConstants.KnoraBase.IntValue =>
                        IntegerValueObjectV2(valueHasString = valueObjectValueHasString, valueHasInteger = predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasInteger).toInt, comment = valueCommentOption)

                    case OntologyConstants.KnoraBase.DecimalValue =>
                        DecimalValueObjectV2(valueHasString = valueObjectValueHasString, valueHasDecimal = BigDecimal(predicateMapForValueObj(OntologyConstants.KnoraBase.ValueHasDecimal)), comment = valueCommentOption)

                    // TODO: implement all value object classes (file values)
                    case other =>
                        TextValueObjectV2(valueHasString = valueObjectValueHasString, comment = valueCommentOption)
                }

                (valObjIri, valueV2)
        }
    }

    def createResponseForResources(queryResultsSeparated: ResourcesAndValueObjects): Vector[ReadResourceV2] = {

        val valuesV2: Map[IRI, ValueObjectV2] = createValueV2FromSparqlResults(queryResultsSeparated.valueObjects)

        queryResultsSeparated.resources.map {
            case (resourceIri: IRI, assertions: Seq[(IRI, String)]) =>

                // make predicate the keys of a map
                val predicateMapForResource: ErrorHandlingMap[IRI, String] = new ErrorHandlingMap(assertions.toMap, { key: IRI => s"Predicate $key not found for $resourceIri (resource)" })

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
                val valueObjectIris: Set[IRI] = valuesV2.keySet.intersect(objects.toSet)

                val propertiesAsTuples: Vector[(IRI, ReadValueV2)] = valueObjectIris.map {
                    (valObjIri) =>
                        // get the property that points from the resource to the value object
                        val propertyIri = objMap(valObjIri)

                        (propertyIri, ReadValueV2(valObjIri, valuesV2(valObjIri)))
                }.toVector

                val propMap: Map[IRI, Seq[ReadValueV2]] = propertiesAsTuples.foldLeft(Map.empty[IRI, Seq[ReadValueV2]]) {
                    case (acc: Map[IRI, Seq[ReadValueV2]], (propIri: IRI, value: ReadValueV2)) =>

                        if (acc.keySet.contains(propIri)) {
                            val existingValsForProp: Seq[ReadValueV2] = acc(propIri)

                            acc + (propIri -> (value +: existingValsForProp))

                        } else {
                            acc + (propIri -> Vector(value))
                        }
                }

                ReadResourceV2(
                    resourceIri = resourceIri,
                    resourceClass = resourceClass,
                    label = rdfLabel,
                    valueObjects = propMap,
                    resourceInfos = Map.empty[IRI, LiteralV2]
                )
        }.toVector

    }*/

}