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

    def createValueV2FromAssertions(valueObject: ValueObject, queryResult: Option[Map[IRI, ResourceWithValues]] = None): ValueObjectV2 = {

        // make predicate the keys of a map
        val predicateMapForValueObject: ErrorHandlingMap[IRI, String] = new ErrorHandlingMap(valueObject.assertions.toMap, { key: IRI => s"Predicate $key not found for ${valueObject.valueObjectIri} (value object)" })

        val valueObjectClass = predicateMapForValueObject(OntologyConstants.Rdf.Type)

        val valueObjectValueHasString: String = predicateMapForValueObject(OntologyConstants.KnoraBase.ValueHasString)

        val valueCommentOption: Option[String] = predicateMapForValueObject.get(OntologyConstants.KnoraBase.ValueHasComment)

        valueObjectClass match {
            case OntologyConstants.KnoraBase.TextValue =>
                // TODO: handle standoff mapping and conversion to XML

                val textValueAssertions = valueObject.assertions

                val standoffAssertions = valueObject.standoff

                TextValueObjectV2(valueHasString = valueObjectValueHasString, comment = valueCommentOption)

            case OntologyConstants.KnoraBase.DateValue =>

                DateValueObjectV2(
                    valueHasString = valueObjectValueHasString,
                    valueHasStartJDN = predicateMapForValueObject(OntologyConstants.KnoraBase.ValueHasStartJDN).toInt,
                    valueHasEndJDN = predicateMapForValueObject(OntologyConstants.KnoraBase.ValueHasEndJDN).toInt,
                    valueHasStartPrecision = KnoraPrecisionV1.lookup(predicateMapForValueObject(OntologyConstants.KnoraBase.ValueHasStartPrecision)),
                    valueHasEndPrecision = KnoraPrecisionV1.lookup(predicateMapForValueObject(OntologyConstants.KnoraBase.ValueHasEndPrecision)),
                    valueHasCalendar = KnoraCalendarV1.lookup(predicateMapForValueObject(OntologyConstants.KnoraBase.ValueHasCalendar)),
                    comment = valueCommentOption
                )

            case OntologyConstants.KnoraBase.IntValue =>
                IntegerValueObjectV2(valueHasString = valueObjectValueHasString, valueHasInteger = predicateMapForValueObject(OntologyConstants.KnoraBase.ValueHasInteger).toInt, comment = valueCommentOption)

            case OntologyConstants.KnoraBase.DecimalValue =>
                DecimalValueObjectV2(valueHasString = valueObjectValueHasString, valueHasDecimal = BigDecimal(predicateMapForValueObject(OntologyConstants.KnoraBase.ValueHasDecimal)), comment = valueCommentOption)

            case OntologyConstants.KnoraBase.LinkValue =>
                val referredResourceIri = predicateMapForValueObject(OntologyConstants.Rdf.Object)

                // check if the referred resource can be represented
                val referredResourceOption: Option[ReferredResourceV2] = if (queryResult.nonEmpty && queryResult.get.get(referredResourceIri).nonEmpty) {

                    val referredResourceInfoMap: ErrorHandlingMap[IRI, String] = new ErrorHandlingMap(queryResult.get(referredResourceIri).resourceAssertions.toMap, { key: IRI => s"Predicate $key not found for ${referredResourceIri} (referred resource)" })

                    Some(ReferredResourceV2(label = referredResourceInfoMap(OntologyConstants.Rdfs.Label), resourceClass = referredResourceInfoMap(OntologyConstants.Rdf.Type)))

                } else {
                    None
                }

                LinkValueObjectV2(
                    valueHasString = valueObjectValueHasString,
                    subject = predicateMapForValueObject(OntologyConstants.Rdf.Subject),
                    predicate = predicateMapForValueObject(OntologyConstants.Rdf.Predicate),
                    reference = predicateMapForValueObject(OntologyConstants.Rdf.Object),
                    comment = valueCommentOption,
                    referredResourceOption
                )

            // TODO: implement all value object classes (file values)
            case other =>
                TextValueObjectV2(valueHasString = valueObjectValueHasString, comment = valueCommentOption)
        }

    }

    def createFullResourceResponse(resourceIri: IRI, resourceResults: Map[IRI, ResourceWithValues]) = {

        val resourceAssertionsMap = resourceResults(resourceIri).resourceAssertions.toMap

        val rdfLabel: String = resourceAssertionsMap(OntologyConstants.Rdfs.Label)

        val resourceClass = resourceAssertionsMap(OntologyConstants.Rdf.Type)

        val valueObjects: Map[IRI, Seq[ReadValueV2]] = resourceResults(resourceIri).valuePropertyAssertions.map {
            case (property: IRI, valObjs: Seq[ValueObject]) =>
                (property, valObjs.map {
                    valObj =>
                        val readValue: ValueObjectV2 = createValueV2FromAssertions(valObj, Some(resourceResults))

                        ReadValueV2(valObj.valueObjectIri, readValue)
                })
        }

        Vector(ReadResourceV2(
            resourceIri = resourceIri,
            resourceClass = resourceClass,
            label = rdfLabel,
            valueObjects = valueObjects,
            resourceInfos = Map.empty[IRI, LiteralV2]
        ))

    }

    def createFulltextSearchResponse(searchResults: Map[IRI, ResourceWithValues]): Vector[ReadResourceV2] = {

        searchResults.map {
            case (resourceIri, assertions) =>

                val resourceAssertionsMap = assertions.resourceAssertions.toMap

                val rdfLabel: String = resourceAssertionsMap(OntologyConstants.Rdfs.Label)

                val resourceClass = resourceAssertionsMap(OntologyConstants.Rdf.Type)

                val valueObjects: Map[IRI, Seq[ReadValueV2]] = assertions.valuePropertyAssertions.map {
                    case (property: IRI, valObjs: Seq[ValueObject]) =>
                        (property, valObjs.map {
                            valObj =>
                                val readValue = createValueV2FromAssertions(valObj)

                                ReadValueV2(valObj.valueObjectIri, readValue)
                        })
                }

                ReadResourceV2(
                    resourceIri = resourceIri,
                    resourceClass = resourceClass,
                    label = rdfLabel,
                    valueObjects = valueObjects,
                    resourceInfos = Map.empty[IRI, LiteralV2]
                )
        }.toVector


    }


}