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

import org.knora.webapi.messages.v1.responder.ontologymessages.StandoffEntityInfoGetResponseV1
import org.knora.webapi.messages.v1.responder.standoffmessages.{GetMappingResponseV1, MappingXMLtoStandoff}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.valuemessages.{KnoraCalendarV1, KnoraPrecisionV1}
import org.knora.webapi.messages.v1.store.triplestoremessages.SparqlConstructResponse
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.twirl._
import org.knora.webapi.util.standoff.StandoffTagUtilV1
import org.knora.webapi._


object ConstructResponseUtilV2 {

    val InferredPredicates = Set(
        OntologyConstants.KnoraBase.HasValue,
        OntologyConstants.KnoraBase.HasLinkTo,
        OntologyConstants.KnoraBase.IsMainResource
    )

    /**
      * Represents the RDF data about a value, possibly including standoff.
      *
      * @param valueObjectIri  the value object's Iri.
      * @param assertionsAsMap the value objects assertions.
      * @param standoff        standoff assertions, if any.
      */
    case class ValueRdfData(valueObjectIri: IRI, valueObjectClass: IRI, assertionsAsMap: Map[IRI, String], assertions: Seq[(IRI, String)], standoff: Map[IRI, Map[IRI, String]])

    /**
      * Represents a resource and its values.
      *
      * @param resourceAssertions      assertions about the resource (direct statements).
      * @param isMainResource          indicates if this represents a top level resource or a referred resource (depending on the query).
      * @param valuePropertyAssertions assertions about value properties.
      */
    case class ResourceWithValueRdfData(resourceAssertions: Seq[(IRI, String)], isMainResource: Boolean, valuePropertyAssertions: Map[IRI, Seq[ValueRdfData]])

    /**
      * Represents a mapping including information about the standoff entities.
      * May include a default XSL transformation.
      *
      * @param mapping           the mapping from XML to standoff and vice versa.
      * @param standoffEntities  information about the standoff entities referred to in the mapping.
      * @param XSLTransformation the default XSL transformation to convert the resulting XML (e.g., to HTML), if any.
      */
    case class MappingAndXSLTransformation(mapping: MappingXMLtoStandoff, standoffEntities: StandoffEntityInfoGetResponseV1, XSLTransformation: Option[String])

    /**
      * A [[SparqlConstructResponse]] may contain both resources and value RDF data objects as well as standoff.
      * This method turns a graph (i.e. triples) into a structure organized by the principle of resources and their values, i.e. a map of resource Iris to [[ResourceWithValueRdfData]].
      *
      * @param constructQueryResults the results of a SPARQL construct query representing resources and their values.
      * @return a Map[resource Iri -> [[ResourceWithValueRdfData]]].
      */
    def splitResourcesAndValueRdfData(constructQueryResults: SparqlConstructResponse, userProfile: UserProfileV1): Map[IRI, ResourceWithValueRdfData] = {

        // split statements about resources and other statements (value objects and standoff)
        // resources are identified by the triple "resourceIri a knora-base:Resource" which is an inferred information returned by the SPARQL Construct query.
        val (resourceStatements: Map[IRI, Seq[(IRI, String)]], nonResourceStatements: Map[IRI, Seq[(IRI, String)]]) = constructQueryResults.statements.partition {

            case (subjectIri: IRI, assertions: Seq[(IRI, String)]) =>

                // check if the subject is a Knora resource
                assertions.contains((OntologyConstants.Rdf.Type, OntologyConstants.KnoraBase.Resource))

        }

        resourceStatements.filterNot {
            case (resIri: IRI, assertions: Seq[(IRI, String)]) =>
                // filter out those resources that the user has not sufficient permissions to see
                // please note that this also applies to referred resources
                PermissionUtilV1.getUserPermissionV1FromAssertions(resIri, assertions, userProfile).isEmpty
        }.map {
            case (resourceIri: IRI, assertions: Seq[(IRI, String)]) =>

                // remove inferred statements (non explicit) returned in the query result
                // the query returns the following inferred information:
                // - every resource is a knora-base:Resource
                // - every value property is a subproperty of knora-base:hasValue
                // - every linking property is a subproperty of knora-base:hasLinkTo
                // - every resource that's a main resource (not a dependent resource) in the query result has knora-base:isMainResource true
                val assertionsExplicit: Seq[(IRI, String)] = assertions.filterNot {
                    case (pred, obj) =>
                        (pred == OntologyConstants.Rdf.Type && obj == OntologyConstants.KnoraBase.Resource) || InferredPredicates(pred)
                }

                // check for the knora-base:isMainResource flag created by the SPARQL CONSTRUCT query
                val isMainResource: Boolean = assertions.exists {
                    case (pred, obj) =>
                        pred == OntologyConstants.KnoraBase.IsMainResource && obj.toBoolean
                }

                // Make a set of all the value object IRIs, because we're going to associate them with their properties.
                val valueObjectIris: Set[String] = assertions.filter {
                    case (pred, obj) => pred == OntologyConstants.KnoraBase.HasValue
                }.map {
                    case (pred, obj) => obj
                }.toSet


                // create a map of (value) property IRIs to value object Iris (the same property may have several instances)
                val valuePropertyToObjectIris: Map[IRI, Seq[IRI]] = assertionsExplicit.filter {
                    case (_, obj: String) =>
                        // Get only the assertions in which the object is a value object IRI.
                        valueObjectIris(obj)
                }.groupBy {
                    case (pred: IRI, _) =>
                        // Turn the sequence of assertions into a Map of predicate IRIs to assertions.
                        pred
                }.map {
                    case (pred, assertions: Seq[(IRI, IRI)]) =>
                        // Replace the assertions with their objects, i.e. the value object IRIs.
                        val objs = assertions.map {
                            case (_, obj) => obj
                        }

                        pred -> objs
                }

                val predicateMap: ErrorHandlingMap[IRI, String] = new ErrorHandlingMap(assertionsExplicit.toMap, { key: IRI => s"predicate $key not found for $resourceIri" })

                // create a map of (value) properties to value objects (the same property may have several instances)
                // resolve the value object Iris and create value objects instead
                val valuePropertyToValueObject: Map[IRI, Seq[ValueRdfData]] = valuePropertyToObjectIris.map {
                    case (property: IRI, valObjIris: Seq[IRI]) =>

                        // build all the property's value objects by mapping over the value object Iris
                        val valueRdfData = valObjIris.filter {
                            // check if the user has sufficient permissions to see the value object
                            valObjIri =>
                                val valueObjAssertions: Seq[(IRI, String)] = nonResourceStatements(valObjIri)

                                // get the resource's project
                                // value objects belong to the parent resource's project
                                val resourceProject: String = predicateMap(OntologyConstants.KnoraBase.AttachedToProject)

                                // prepend the resource's project to the value's assertions
                                PermissionUtilV1.getUserPermissionV1FromAssertions(valObjIri, (OntologyConstants.KnoraBase.AttachedToProject, resourceProject) +: valueObjAssertions, userProfile).nonEmpty
                        }.map {
                            valObjIri: IRI =>

                                // get all the standoff node Iris possibly belonging to this value object
                                // do so by accessing the non resource statements using the value object Iri as a key
                                val standoffNodeIris: Set[IRI] = nonResourceStatements(valObjIri).filter {
                                    case (pred: IRI, _) =>
                                        pred == OntologyConstants.KnoraBase.ValueHasStandoff
                                }.map {
                                    case (_, obj: IRI) =>
                                        // we are only interested in the standoff node Iri
                                        obj
                                }.toSet

                                // given the standoff node Iris, get the standoff assertions
                                // do so by accessing the non resource statements using the standoff node Iri as a key
                                val (standoffAssertions: Map[IRI, Seq[(IRI, String)]], valueAssertions: Map[IRI, Seq[(IRI, String)]]) = nonResourceStatements.partition {
                                    case (subjIri: IRI, _) =>
                                        standoffNodeIris(subjIri)
                                }

                                val predicateMapForValueAssertions: ErrorHandlingMap[IRI, String] =
                                    new ErrorHandlingMap(valueAssertions(valObjIri).toMap, { key: IRI => s"Predicate $key not found for $valObjIri (value object)" })

                                // create a value object
                                ValueRdfData(
                                    valueObjectIri = valObjIri,
                                    valueObjectClass = predicateMapForValueAssertions(OntologyConstants.Rdf.Type),
                                    assertionsAsMap = predicateMapForValueAssertions,
                                    assertions = valueAssertions(valObjIri),
                                    standoff = standoffAssertions.map {
                                        case (standoffNodeIri: IRI, standoffAssertions: Seq[(IRI, String)]) =>
                                            (standoffNodeIri, standoffAssertions.toMap)
                                    })

                        }

                        property -> valueRdfData
                }.filterNot { // filter out those properties that do not have value objects (they may have been filtered out because the user does not have sufficient permissions to see them)
                    case (_, valObj: Seq[ValueRdfData]) =>
                        valObj.isEmpty
                }

                // TODO: filter out the link values that point to resources that the user didn't have permission to see

                // TODO: garbage collect dependent resources that nothing points to anymore

                // create a map of resource Iris to a `ResourceWithValueRdfData`
                (resourceIri, ResourceWithValueRdfData(resourceAssertions = assertionsExplicit, isMainResource = isMainResource, valuePropertyAssertions = valuePropertyToValueObject))
        }
    }

    /**
      * Collect all mapping Iris referred to in the given value assertions.
      *
      * @param valuePropertyAssertions the given assertions (property -> value object).
      * @return a set of mapping Iris.
      */
    def getMappingIrisFromValuePropertyAssertions(valuePropertyAssertions: Map[IRI, Seq[ValueRdfData]]): Set[IRI] = {
        valuePropertyAssertions.foldLeft(Set.empty[IRI]) {
            case (acc: Set[IRI], (valueObjIri: IRI, valObjs: Seq[ValueRdfData])) =>
                val mappings: Seq[String] = valObjs.filter {
                    (valObj: ValueRdfData) =>
                        valObj.valueObjectClass == OntologyConstants.KnoraBase.TextValue && valObj.assertionsAsMap.get(OntologyConstants.KnoraBase.ValueHasMapping).nonEmpty
                }.map {
                    case (textValObj: ValueRdfData) =>
                        textValObj.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasMapping)
                }

                acc ++ mappings
        }
    }

    /**
      * Given a [[ValueRdfData]], create a [[ValueContentV2]], considering the specific type of the given [[ValueRdfData]].
      *
      * @param valueObject the given [[ValueRdfData]].
      * @param queryResult complete results of the SPARQL Construct query, needed in case an Iri of a referred resource has to be resolved.
      * @return a [[ValueContentV2]] representing a value.
      */
    def createValueContentV2FromValueRdfData(valueObject: ValueRdfData, mappings: Map[IRI, MappingAndXSLTransformation], settings: SettingsImpl, queryResult: Option[Map[IRI, ResourceWithValueRdfData]] = None): ValueContentV2 = {

        // every knora-base:Value (any of its subclasses) has a string representation
        val valueObjectValueHasString: String = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasString)

        // every knora-base:value (any of its subclasses) may have a comment
        val valueCommentOption: Option[String] = valueObject.assertionsAsMap.get(OntologyConstants.KnoraBase.ValueHasComment)

        valueObject.valueObjectClass match {
            case OntologyConstants.KnoraBase.TextValue =>

                if (valueObject.standoff.nonEmpty) {
                    // standoff nodes given
                    // get the Iri of the mapping
                    val mappingIri: IRI = valueObject.assertionsAsMap.getOrElse(OntologyConstants.KnoraBase.ValueHasMapping, throw InconsistentTriplestoreDataException(s"no mapping Iri associated with standoff belonging to textValue ${valueObject.valueObjectIri}"))

                    val mapping: MappingAndXSLTransformation = mappings(mappingIri)

                    val standoffTags: Vector[StandoffTagV1] = StandoffTagUtilV1.createStandoffTagsV1FromSparqlResults(mapping.standoffEntities, valueObject.standoff)

                    TextValueContentV2(valueHasString = valueObjectValueHasString, standoff = Some(StandoffAndMapping(standoff = standoffTags, mappingIri = mappingIri, mapping = mapping.mapping, XSLT = mapping.XSLTransformation)), comment = valueCommentOption)

                } else {
                    // no standoff nodes given
                    TextValueContentV2(valueHasString = valueObjectValueHasString, standoff = None, comment = valueCommentOption)
                }


            case OntologyConstants.KnoraBase.DateValue =>

                DateValueContentV2(
                    valueHasString = valueObjectValueHasString,
                    valueHasStartJDN = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasStartJDN).toInt,
                    valueHasEndJDN = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasEndJDN).toInt,
                    valueHasStartPrecision = KnoraPrecisionV1.lookup(valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasStartPrecision)),
                    valueHasEndPrecision = KnoraPrecisionV1.lookup(valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasEndPrecision)),
                    valueHasCalendar = KnoraCalendarV1.lookup(valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasCalendar)),
                    comment = valueCommentOption
                )

            case OntologyConstants.KnoraBase.IntValue =>
                IntegerValueContentV2(valueHasString = valueObjectValueHasString, valueHasInteger = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasInteger).toInt, comment = valueCommentOption)

            case OntologyConstants.KnoraBase.DecimalValue =>
                DecimalValueContentV2(valueHasString = valueObjectValueHasString, valueHasDecimal = BigDecimal(valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasDecimal)), comment = valueCommentOption)

            case OntologyConstants.KnoraBase.BooleanValue =>
                BooleanValueContentV2(valueHasString = valueObjectValueHasString, valueHasBoolean = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasBoolean).toBoolean, comment = valueCommentOption)

            case OntologyConstants.KnoraBase.UriValue =>
                UriValueContentV2(valueHasString = valueObjectValueHasString, valueHasUri = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasUri), comment = valueCommentOption)

            case OntologyConstants.KnoraBase.ColorValue =>
                ColorValueContentV2(valueHasString = valueObjectValueHasString, valueHasColor = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasColor), comment = valueCommentOption)

            case OntologyConstants.KnoraBase.GeomValue =>
                GeomValueContentV2(valueHasString = valueObjectValueHasString, valueHasGeometry = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasGeometry), comment = valueCommentOption)

            case OntologyConstants.KnoraBase.GeonameValue =>
                GeonameValueContentV2(valueHasString = valueObjectValueHasString, valueHasGeonameCode = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasGeonameCode), comment = valueCommentOption)

            case OntologyConstants.KnoraBase.ListValue =>
                HierarchicalListValueContentV2(valueHasString = valueObjectValueHasString, valueHasListNode = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasListNode), comment = valueCommentOption)

            case OntologyConstants.KnoraBase.IntervalValue =>
                IntervalValueContentV2(valueHasString = valueObjectValueHasString, valueHasIntervalStart = BigDecimal(valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasIntervalStart)), valueHasIntervalEnd = BigDecimal(valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasIntervalEnd)), comment = valueCommentOption)

            case OntologyConstants.KnoraBase.LinkValue =>
                val referredResourceIri = valueObject.assertionsAsMap(OntologyConstants.Rdf.Object)

                // check if the referred resource's Iri can be resolved:
                // check if `queryResult` is given (it's optional) and if it contains the referred resource's Iri as a key (the user may not have sufficient permission to see the referred resource)

                // TODO: only return the link when the user has permissions to se the referred resource

                // TODO: follow links recursively and build nested structures of `ReadResourceV2` (a resource might point to a resource that may point to another resource)
                val referredResourceOption: Option[ReadResourceV2] = if (queryResult.nonEmpty && queryResult.get.get(referredResourceIri).nonEmpty) {

                    // access the assertions about the referred resource
                    val referredResourceInfoMap: ErrorHandlingMap[IRI, String] = new ErrorHandlingMap(queryResult.get(referredResourceIri).resourceAssertions.toMap, { key: IRI => s"Predicate $key not found for ${referredResourceIri} (referred resource)" })

                    Some(ReadResourceV2(resourceIri = valueObject.assertionsAsMap(OntologyConstants.Rdf.Object), label = referredResourceInfoMap(OntologyConstants.Rdfs.Label), resourceClass = referredResourceInfoMap(OntologyConstants.Rdf.Type), resourceInfos = Map.empty[IRI, LiteralV2], values = Map.empty[IRI, Seq[ReadValueV2]]))

                } else {
                    None
                }

                LinkValueContentV2(
                    valueHasString = valueObjectValueHasString,
                    subject = valueObject.assertionsAsMap(OntologyConstants.Rdf.Subject),
                    predicate = valueObject.assertionsAsMap(OntologyConstants.Rdf.Predicate),
                    referredResourceIri = valueObject.assertionsAsMap(OntologyConstants.Rdf.Object),
                    comment = valueCommentOption,
                    referredResourceOption // may be non in case the referred resource's Iri could not be resolved
                )

            case OntologyConstants.KnoraBase.StillImageFileValue =>

                StillImageFileValueContentV2(
                    internalMimeType = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.InternalMimeType),
                    internalFilename = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.InternalFilename),
                    originalFilename = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.OriginalFilename),
                    originalMimeType = valueObject.assertionsAsMap.get(OntologyConstants.KnoraBase.OriginalMimeType),
                    dimX = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.DimX).toInt,
                    dimY = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.DimY).toInt,
                    qualityLevel = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.QualityLevel).toInt,
                    isPreview = InputValidation.optionStringToBoolean(valueObject.assertionsAsMap.get(OntologyConstants.KnoraBase.IsPreview)),
                    valueHasString = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasString),
                    comment = valueCommentOption,
                    settings = settings
                )

            case OntologyConstants.KnoraBase.TextFileValue =>

                TextFileValueContentV2(
                    internalMimeType = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.InternalMimeType),
                    internalFilename = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.InternalFilename),
                    originalFilename = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.OriginalFilename),
                    originalMimeType = valueObject.assertionsAsMap.get(OntologyConstants.KnoraBase.OriginalMimeType),
                    valueHasString = valueObject.assertionsAsMap(OntologyConstants.KnoraBase.ValueHasString),
                    comment = valueCommentOption,
                    settings = settings
                )




            // TODO: implement all value object classes
            case other =>
                TextValueContentV2(valueHasString = valueObjectValueHasString, standoff = None, comment = valueCommentOption)
        }

    }

    /**
      * Creates a response to a full resource request.
      *
      * @param resourceIri     the Iri of the requested resource.
      * @param resourceResults the results returned by the triplestore.
      * @return a [[ReadResourceV2]].
      */
    def createFullResourceResponse(resourceIri: IRI, mappings: Map[IRI, MappingAndXSLTransformation], resourceResults: Map[IRI, ResourceWithValueRdfData], settings: SettingsImpl): ReadResourceV2 = {

        // a full resource query also returns the resources referred to by the requested resource
        // however, the should not be included as resources, but as the target og a linking property
        val resourceAssertionsMap = resourceResults(resourceIri).resourceAssertions.toMap

        val rdfLabel: String = resourceAssertionsMap(OntologyConstants.Rdfs.Label)

        val resourceClass = resourceAssertionsMap(OntologyConstants.Rdf.Type)

        // get the resource's values
        val valueObjects: Map[IRI, Seq[ReadValueV2]] = resourceResults(resourceIri).valuePropertyAssertions.map {
            case (property: IRI, valObjs: Seq[ValueRdfData]) =>
                (property, valObjs.map {
                    valObj =>
                        val readValue: ValueContentV2 = createValueContentV2FromValueRdfData(valObj, mappings = mappings, settings, Some(resourceResults))

                        ReadValueV2(valObj.valueObjectIri, readValue)
                })
        }

        ReadResourceV2(
            resourceIri = resourceIri,
            resourceClass = resourceClass,
            label = rdfLabel,
            values = valueObjects,
            resourceInfos = Map.empty[IRI, LiteralV2]
        )

    }

    /**
      * Creates a response to a fulltext search.
      *
      * @param searchResults the results returned by the triplestore.
      * @return a collection of [[ReadResourceV2]], representing the search results.
      */
    def createFulltextSearchResponse(searchResults: Map[IRI, ResourceWithValueRdfData], settings: SettingsImpl): Vector[ReadResourceV2] = {

        // each entry represents a resource that matches the search criteria
        // this is because linking properties are excluded from fulltext search
        searchResults.map {
            case (resourceIri, assertions) =>

                val resourceAssertionsMap = assertions.resourceAssertions.toMap

                val rdfLabel: String = resourceAssertionsMap(OntologyConstants.Rdfs.Label)

                val resourceClass = resourceAssertionsMap(OntologyConstants.Rdf.Type)

                // get the resource's values
                val valueObjects: Map[IRI, Seq[ReadValueV2]] = assertions.valuePropertyAssertions.map {
                    case (property: IRI, valObjs: Seq[ValueRdfData]) =>
                        (property, valObjs.map {
                            valObj =>
                                val readValue = createValueContentV2FromValueRdfData(valObj, mappings = Map.empty[IRI, MappingAndXSLTransformation], settings)

                                ReadValueV2(valObj.valueObjectIri, readValue)
                        })
                }

                ReadResourceV2(
                    resourceIri = resourceIri,
                    resourceClass = resourceClass,
                    label = rdfLabel,
                    values = valueObjects,
                    resourceInfos = Map.empty[IRI, LiteralV2]
                )
        }.toVector
    }
}