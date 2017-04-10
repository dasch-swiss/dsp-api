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

import org.knora.webapi.messages.v1.responder.standoffmessages.GetMappingResponseV1
import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.messages.v1.store.triplestoremessages.SparqlConstructResponse
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.twirl._
import org.knora.webapi.util.standoff.StandoffTagUtilV1
import org.knora.webapi.{IRI, InconsistentTriplestoreDataException, OntologyConstants}


object ConstructResponseUtilV2 {

    /**
      * Represents a value object possibly containing standoff.
      *
      * @param valueObjectIri  the value object's Iri.
      * @param assertionsAsMap the value objects assertions.
      * @param standoff        standoff assertions, if any.
      */
    case class ValueObject(valueObjectIri: IRI, valueObjectClass: IRI, assertionsAsMap: Map[IRI, String], assertions: Seq[(IRI, String)], standoff: Map[IRI, Map[IRI, String]])

    /**
      * Represents a resource and its values.
      *
      * @param resourceAssertions      assertions about the resource (direct statements).
      * @param valuePropertyAssertions assertions about value properties.
      * @param linkPropertyAssertions  assertions about linking properties.
      */
    case class ResourceWithValues(resourceAssertions: Seq[(IRI, String)], valuePropertyAssertions: Map[IRI, Seq[ValueObject]], linkPropertyAssertions: Map[IRI, IRI])

    /**
      * A [[SparqlConstructResponse]] may contain both resources and value objects as well as standoff.
      * This method turns a graph (i.e. triples) into a structure organized by the principle of resources and their values, i.e. a map of resource Iris to [[ResourceWithValues]].
      *
      * @param constructQueryResults the results of a SPARQL construct query representing resources and their values.
      * @return a Map[resource Iri -> [[ResourceWithValues]]].
      */
    def splitResourcesAndValueObjects(constructQueryResults: SparqlConstructResponse): Map[IRI, ResourceWithValues] = {

        // split statements about resources and other statements (value objects and standoff)
        // resources are identified by the triple "resourceIri a knora-base:Resource" which is an inferred information returned by the SPARQL Construct query.
        val (resourceStatements: Map[IRI, Seq[(IRI, String)]], otherStatements: Map[IRI, Seq[(IRI, String)]]) = constructQueryResults.statements.partition {

            case (subjectIri: IRI, assertions: Seq[(IRI, String)]) =>

                // check if the subject is a Knora resource
                assertions.contains((OntologyConstants.Rdf.Type, OntologyConstants.KnoraBase.Resource))

        }

        resourceStatements.map {
            case (resourceIri: IRI, assertions: Seq[(IRI, String)]) =>

                // remove inferred statements (non explicit) returned in the query result
                // the query returns the following inferred information:
                // - every resource is a knora-base:Resource
                // - every value property is a subproperty of knora-base:hasValue
                // - every linking property is a subproperty of knora-base:hasLinkTo
                val assertionsExplicit: Seq[(IRI, String)] = assertions.filterNot {
                    case (pred, obj) =>
                        (pred == OntologyConstants.Rdf.Type && obj == OntologyConstants.KnoraBase.Resource) || pred == OntologyConstants.KnoraBase.HasValue || pred == OntologyConstants.KnoraBase.HasLinkTo
                }

                // make the objects keys of a map, using only explicit assertions
                // this only works for value properties because value object Iris are only referred to once by a value property
                // (unlike linking properties' targets: the same resource may be referred to several times by different linking properties)
                val objectMap: ErrorHandlingMap[String, IRI] = new ErrorHandlingMap(assertionsExplicit.map {
                    case (pred, obj) =>
                        (obj, pred)
                }.toMap, { key: IRI => s"object $key not found for $resourceIri" })

                // create a map of (value) property Iris to value object Iris (the same property may have several instances)
                val valuePropertyToObjectIris: Map[IRI, Seq[IRI]] = assertions.filter {
                    case (pred, obj) =>
                        // collect all the statements that refer to value objects,
                        // using inferred statements
                        pred == OntologyConstants.KnoraBase.HasValue
                }.foldLeft(Map.empty[IRI, Seq[IRI]]) {
                    // make a map of properties to a collection of the value objects they refer to
                    case (acc: Map[IRI, Seq[IRI]], (hasValue: IRI, valObjIri: IRI)) =>
                        // get the property that points to the value object
                        val propIri = objectMap(valObjIri)

                        // add the value object Iri to this property's collection
                        // check if there already exist value object Iris for the property
                        if (acc.keySet.contains(propIri)) {
                            // the property already exists, add to its collection of value object Iris

                            // get the existing value object Iris for the property in order to preserve them
                            val existingValsForProp = acc(propIri)

                            // prepend the value object Iri to the existing ones and set the property (overwriting it)
                            acc + (propIri -> (valObjIri +: existingValsForProp))

                        } else {
                            // the property does not exist yet, create it and its collection
                            acc + (propIri -> Vector(valObjIri))
                        }

                }

                // create a map of (value) properties to value objects (the same property may have several instances)
                // resolve the value object Iris and create value objects instead
                val valuePropertyToValueObject: Map[IRI, Seq[ValueObject]] = valuePropertyToObjectIris.map {
                    case (property: IRI, valObjIris: Seq[String]) =>

                        // make the property the key of the map, return all its value objects by mapping over the value object Iris
                        (property, valObjIris.map {
                            (valObjIri: IRI) =>

                                // get all the standoff node Iris possibly belonging to this value object
                                // do so by accessing the non resource statements using the value object Iri as a key
                                val standoffNodeIris: Seq[String] = otherStatements(valObjIri).filter {
                                    case (pred: IRI, obj: String) =>
                                        pred == OntologyConstants.KnoraBase.ValueHasStandoff
                                }.map {
                                    case (pred: IRI, obj: String) =>
                                        // we are only interested in the standoff node Iri
                                        obj
                                }

                                // given the standoff node Iris, get the standoff assertions
                                // do so by accessing the non resource statements using the standoff node Iri as a key
                                val (standoffAssertions: Map[IRI, Seq[(IRI, String)]], valueAssertions: Map[IRI, Seq[(IRI, String)]]) = otherStatements.partition {
                                    case (subjIri: IRI, assertions: Seq[(IRI, String)]) =>
                                        standoffNodeIris.contains(subjIri)

                                }

                                val predicateMapForValueAssertions: ErrorHandlingMap[IRI, String] = new ErrorHandlingMap(valueAssertions(valObjIri).toMap, { key: IRI => s"Predicate $key not found for ${valObjIri} (value object)" })

                                // create a value object
                                ValueObject(
                                    valueObjectIri = valObjIri,
                                    valueObjectClass = predicateMapForValueAssertions(OntologyConstants.Rdf.Type),
                                    assertionsAsMap = predicateMapForValueAssertions,
                                    assertions = valueAssertions(valObjIri),
                                    standoff = standoffAssertions.map {
                                        case (standoffNodeIri: IRI, standoffAssertions: Seq[(IRI, String)]) =>
                                            (standoffNodeIri, standoffAssertions.toMap)
                                    })

                        })

                }

                // create a map of linking properties to their targets
                val linkPropToTargets: Map[IRI, IRI] = assertions.filter {
                    case (pred, obj) =>
                        pred == OntologyConstants.KnoraBase.HasLinkTo
                }.flatMap {
                    case (hasLinkTo: IRI, referredRes: IRI) =>
                        // get all the assertions in which the referred resource is the object (using explicit statements only)
                        // like this, we get the linking property
                        assertionsExplicit.filter {
                            case (pred, obj) =>
                                obj == referredRes
                        }
                }.toMap

                // create a map of resource Iris to a `ResourceWithValues`
                (resourceIri, ResourceWithValues(resourceAssertions = assertionsExplicit, valuePropertyAssertions = valuePropertyToValueObject, linkPropertyAssertions = linkPropToTargets))


        }

    }

    /**
      * Collect all mapping Iris referred to in the given value assertions.
      *
      * @param valuePropertyAssertions the given assertions (property -> value object).
      * @return a set of mapping Iris.
      */
    def getMappingIrisFromValuePropertyAssertions(valuePropertyAssertions: Map[IRI, Seq[ValueObject]]): Set[IRI] = {
        valuePropertyAssertions.foldLeft(Set.empty[IRI]) {
            case (acc: Set[IRI], (valueObjIri: IRI, valObjs: Seq[ValueObject])) =>
                val mappings: Seq[String] = valObjs.filter {
                    (valObj: ValueObject) =>
                        valObj.valueObjectClass == OntologyConstants.KnoraBase.TextValue && valObj.assertionsAsMap.get(OntologyConstants.KnoraBase.ValueHasMapping).nonEmpty
                }.map {
                    case (textValObj: ValueObject) =>
                        textValObj.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasMapping)
                }

                acc ++ mappings
        }
    }

    /**
      * Given a [[ValueObject]], create a [[ValueObjectV2]], considering the specific type of the given [[ValueObject]].
      *
      * @param valueObject the given [[ValueObject]].
      * @param queryResult complete results of the SPARQL Construct query, needed in case an Iri of a referred resource has to be resolved.
      * @return a [[ValueObjectV2]] representing a value.
      */
    def createValueV2FromValueObject(valueObject: ValueObject, mappings: Map[IRI, GetMappingResponseV1], queryResult: Option[Map[IRI, ResourceWithValues]] = None): ValueObjectV2 = {

        // every knora-base:Value (any of its subclasses) has a string representation
        val valueObjectValueHasString: String = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasString)

        // every knora-base:value (any of its subclasses) may have a comment
        val valueCommentOption: Option[String] = valueObject.assertionsAsMap.get(OntologyConstants.KnoraBase.ValueHasComment)

        valueObject.valueObjectClass match {
            case OntologyConstants.KnoraBase.TextValue =>
                // TODO: handle standoff mapping and conversion to XML

                //println(valueObject.assertions)

                if (valueObject.standoff.nonEmpty) {
                    // standoff nodes given
                    // get the Iri of the mapping
                    val mappingIri = valueObject.assertionsAsMap.getOrElse(OntologyConstants.KnoraBase.ValueHasMapping, throw InconsistentTriplestoreDataException(s"no mapping Iri associated with standoff belonging to textValue ${valueObject.valueObjectIri}"))

                    val mapping: GetMappingResponseV1 = mappings(mappingIri)

                    val standoffTags: Vector[StandoffTagV1] = StandoffTagUtilV1.createStandoffTagsV1FromSparqlResults(mapping.standoffEntities, valueObject.standoff)

                    TextValueObjectV2(valueHasString = valueObjectValueHasString, mappingIri = Some(mapping.mappingIri), mapping = Some(mapping.mapping), standoff = standoffTags, comment = valueCommentOption)

                } else {
                    // no standoff nodes given
                    TextValueObjectV2(valueHasString = valueObjectValueHasString, mappingIri = None, mapping = None, standoff = Seq.empty[StandoffTagV1], comment = valueCommentOption)
                }


            case OntologyConstants.KnoraBase.DateValue =>

                DateValueObjectV2(
                    valueHasString = valueObjectValueHasString,
                    valueHasStartJDN = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasStartJDN).toInt,
                    valueHasEndJDN = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasEndJDN).toInt,
                    valueHasStartPrecision = KnoraPrecisionV1.lookup(valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasStartPrecision)),
                    valueHasEndPrecision = KnoraPrecisionV1.lookup(valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasEndPrecision)),
                    valueHasCalendar = KnoraCalendarV1.lookup(valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasCalendar)),
                    comment = valueCommentOption
                )

            case OntologyConstants.KnoraBase.IntValue =>
                IntegerValueObjectV2(valueHasString = valueObjectValueHasString, valueHasInteger = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasInteger).toInt, comment = valueCommentOption)

            case OntologyConstants.KnoraBase.DecimalValue =>
                DecimalValueObjectV2(valueHasString = valueObjectValueHasString, valueHasDecimal = BigDecimal(valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasDecimal)), comment = valueCommentOption)

            case OntologyConstants.KnoraBase.LinkValue =>
                val referredResourceIri = valueObject.assertionsAsMap(OntologyConstants.Rdf.Object)

                // check if the referred resource's Iri can be resolved:
                // check if `queryResult` is given (it's optional) and if it contains the referred resource's Iri as a key
                val referredResourceOption: Option[ReferredResourceV2] = if (queryResult.nonEmpty && queryResult.get.get(referredResourceIri).nonEmpty) {

                    // access the assertions about the referred resource
                    val referredResourceInfoMap: ErrorHandlingMap[IRI, String] = new ErrorHandlingMap(queryResult.get(referredResourceIri).resourceAssertions.toMap, { key: IRI => s"Predicate $key not found for ${referredResourceIri} (referred resource)" })

                    Some(ReferredResourceV2(label = referredResourceInfoMap(OntologyConstants.Rdfs.Label), resourceClass = referredResourceInfoMap(OntologyConstants.Rdf.Type)))

                } else {
                    None
                }

                LinkValueObjectV2(
                    valueHasString = valueObjectValueHasString,
                    subject = valueObject.assertionsAsMap(OntologyConstants.Rdf.Subject),
                    predicate = valueObject.assertionsAsMap(OntologyConstants.Rdf.Predicate),
                    referredResourceIri = valueObject.assertionsAsMap(OntologyConstants.Rdf.Object),
                    comment = valueCommentOption,
                    referredResourceOption // may be non in case the referred resource's Iri could not be resolved
                )

            // TODO: implement all value object classes
            case other =>
                TextValueObjectV2(valueHasString = valueObjectValueHasString, mappingIri = None, mapping = None, standoff = Seq.empty[StandoffTagV1], comment = valueCommentOption)
        }

    }

    /**
      * Creates a response to a full resource request.
      *
      * @param resourceIri     the Iri of the requested resource.
      * @param resourceResults the results returned by the triplestore.
      * @return a [[ReadResourceV2]].
      */
    def createFullResourceResponse(resourceIri: IRI, mappings: Map[IRI, GetMappingResponseV1], resourceResults: Map[IRI, ResourceWithValues]): ReadResourceV2 = {

        // access the assertions about the requested resource
        // a full resource query also returns the resources referred to by the requested resource
        // however, the should not be included as resources, but as the target og a linking property
        val resourceAssertionsMap = resourceResults(resourceIri).resourceAssertions.toMap

        val rdfLabel: String = resourceAssertionsMap(OntologyConstants.Rdfs.Label)

        val resourceClass = resourceAssertionsMap(OntologyConstants.Rdf.Type)

        // get the resource's values
        val valueObjects: Map[IRI, Seq[ReadValueV2]] = resourceResults(resourceIri).valuePropertyAssertions.map {
            case (property: IRI, valObjs: Seq[ValueObject]) =>
                (property, valObjs.map {
                    valObj =>
                        val readValue: ValueObjectV2 = createValueV2FromValueObject(valObj, mappings = mappings, Some(resourceResults))

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

    }

    /**
      * Creates a response to a fulltext search.
      *
      * @param searchResults the results returned by the triplestore.
      * @return a collection of [[ReadResourceV2]], representing the search results.
      */
    def createFulltextSearchResponse(searchResults: Map[IRI, ResourceWithValues]): Vector[ReadResourceV2] = {

        // each entry represents a resource that matches the search criteria
        // this is because linking properties are excluded from fulltext search
        searchResults.map {
            case (resourceIri, assertions) =>

                val resourceAssertionsMap = assertions.resourceAssertions.toMap

                val rdfLabel: String = resourceAssertionsMap(OntologyConstants.Rdfs.Label)

                val resourceClass = resourceAssertionsMap(OntologyConstants.Rdf.Type)

                // get the resource's values
                val valueObjects: Map[IRI, Seq[ReadValueV2]] = assertions.valuePropertyAssertions.map {
                    case (property: IRI, valObjs: Seq[ValueObject]) =>
                        (property, valObjs.map {
                            valObj =>
                                val readValue = createValueV2FromValueObject(valObj, mappings = Map.empty[IRI, GetMappingResponseV1])

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