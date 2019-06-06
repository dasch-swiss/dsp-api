/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import java.time.Instant
import java.util.UUID

import akka.actor.ActorRef
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectGetRequestADM, ProjectGetResponseADM}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.SparqlConstructResponse
import org.knora.webapi.messages.v2.responder.listsmessages.{NodeGetRequestV2, NodeGetResponseV2}
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.standoffmessages.{GetRemainingStandoffFromTextValueRequestV2, GetStandoffResponseV2, MappingXMLtoStandoff, StandoffTagV2}
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.PermissionUtilADM.EntityPermission
import org.knora.webapi.util.date.{CalendarNameV2, DatePrecisionV2}
import org.knora.webapi.util.standoff.StandoffTagUtilV2

import scala.concurrent.{ExecutionContext, Future}


object ConstructResponseUtilV2 {

    private val InferredPredicates = Set(
        OntologyConstants.KnoraBase.HasValue,
        OntologyConstants.KnoraBase.IsMainResource
    )

    /**
      * Represents the RDF data about a value, possibly including standoff.
      *
      * @param valueObjectIri   the value object's IRI.
      * @param valueObjectClass the type (class) of the value object.
      * @param nestedResource   the nested resource in case of a link value (either the source or the target of a link value, depending on [[isIncomingLink]]).
      * @param isIncomingLink   indicates if it is an incoming or outgoing link in case of a link value.
      * @param userPermission   the permission that the requesting user has on the value.
      * @param assertions       the value objects assertions.
      * @param standoff         standoff assertions, if any.
      */
    case class ValueRdfData(valueObjectIri: IRI,
                            valueObjectClass: IRI,
                            nestedResource: Option[ResourceWithValueRdfData] = None,
                            isIncomingLink: Boolean = false,
                            userPermission: EntityPermission,
                            assertions: Map[IRI, String],
                            standoff: Map[IRI, Map[IRI, String]])

    /**
      * Represents a resource and its values.
      *
      * @param resourceAssertions      assertions about the resource (direct statements).
      * @param isMainResource          indicates if this represents a top level resource or a referred resource (depending on the query).
      * @param userPermission          the permission that the requesting user has on the resource.
      * @param valuePropertyAssertions assertions about value properties.
      */
    case class ResourceWithValueRdfData(resourceAssertions: Seq[(IRI, String)],
                                        isMainResource: Boolean,
                                        userPermission: EntityPermission,
                                        valuePropertyAssertions: Map[IRI, Seq[ValueRdfData]])

    /**
      * Represents a mapping including information about the standoff entities.
      * May include a default XSL transformation.
      *
      * @param mapping           the mapping from XML to standoff and vice versa.
      * @param standoffEntities  information about the standoff entities referred to in the mapping.
      * @param XSLTransformation the default XSL transformation to convert the resulting XML (e.g., to HTML), if any.
      */
    case class MappingAndXSLTransformation(mapping: MappingXMLtoStandoff, standoffEntities: StandoffEntityInfoGetResponseV2, XSLTransformation: Option[String])

    /**
      * A [[SparqlConstructResponse]] may contain both resources and value RDF data objects as well as standoff.
      * This method turns a graph (i.e. triples) into a structure organized by the principle of resources and their values, i.e. a map of resource Iris to [[ResourceWithValueRdfData]].
      * The resource Iris represent main resources, dependent resources are contained in the link values as nested structures.
      *
      * @param constructQueryResults the results of a SPARQL construct query representing resources and their values.
      * @return a Map[resource IRI -> [[ResourceWithValueRdfData]]].
      */
    def splitMainResourcesAndValueRdfData(constructQueryResults: SparqlConstructResponse, requestingUser: UserADM): Map[IRI, ResourceWithValueRdfData] = {
        // TODO: use SparqlExtendedConstructResponse for better type safety.

        // An intermediate data structure containing RDF assertions about an entity and the user's permission on the entity.
        case class RdfWithUserPermission(assertions: Seq[(IRI, String)], maybeUserPermission: Option[EntityPermission])

        // split statements about resources and other statements (value objects and standoff)
        // resources are identified by the triple "resourceIri a knora-base:Resource" which is an inferred information returned by the SPARQL Construct query.
        val (resourceStatements: Map[IRI, Seq[(IRI, String)]], nonResourceStatements: Map[IRI, Seq[(IRI, String)]]) = constructQueryResults.statements.partition {
            case (_: IRI, assertions: Seq[(IRI, String)]) =>
                // check if the subject is a Knora resource
                assertions.contains((OntologyConstants.Rdf.Type, OntologyConstants.KnoraBase.Resource))
        }

        // filter out the resources the user does not have permissions to see, including dependent resources.

        val resourceStatementsVisible: Map[IRI, RdfWithUserPermission] = resourceStatements.map {
            case (resourceIri: IRI, assertions: Seq[(IRI, String)]) =>
                val maybeUserPermission: Option[EntityPermission] = PermissionUtilADM.getUserPermissionFromAssertionsADM(resourceIri, assertions, requestingUser)
                resourceIri -> RdfWithUserPermission(assertions, maybeUserPermission)
        }.filter {
            case (resourceIri: IRI, statements: RdfWithUserPermission) => statements.maybeUserPermission.nonEmpty
        }

        val flatResourcesWithValues: Map[IRI, ResourceWithValueRdfData] = resourceStatementsVisible.map {
            case (resourceIri: IRI, resourceRdfWithUserPermission: RdfWithUserPermission) =>
                val assertions = resourceRdfWithUserPermission.assertions

                // remove inferred statements (non explicit) returned in the query result
                // the query returns the following inferred information:
                // - every resource is a knora-base:Resource
                // - every value property is a subproperty of knora-base:hasValue
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
                val valueObjectIris: Set[IRI] = assertions.filter {
                    case (pred, _: String) => pred == OntologyConstants.KnoraBase.HasValue
                }.map {
                    case (_: IRI, obj: IRI) => obj
                }.toSet


                // create a map of (value) property IRIs to value object IRIs (the same property may have several instances)
                val valuePropertyToObjectIris: Map[IRI, Seq[IRI]] = assertionsExplicit.filter {
                    case (_, obj: String) =>
                        // Get only the assertions in which the object is a value object IRI.
                        valueObjectIris(obj)
                }.groupBy {
                    case (pred: IRI, _: String) =>
                        // Turn the sequence of assertions into a Map of predicate IRIs to assertions.
                        pred
                }.map {
                    case (pred, assertions: Seq[(IRI, IRI)]) =>
                        // Replace the assertions with their objects, i.e. the value object IRIs.
                        val objs: Seq[IRI] = assertions.map {
                            case (_: IRI, obj: IRI) => obj
                        }

                        pred -> objs
                }

                val predicateMap: ErrorHandlingMap[IRI, String] = new ErrorHandlingMap(assertionsExplicit.toMap, { key: IRI => s"predicate $key not found for $resourceIri" })

                // create a map of (value) properties to value objects (the same property may have several instances)
                // resolve the value object Iris and create value objects instead
                val valuePropertyToValueObject: Map[IRI, Seq[ValueRdfData]] = valuePropertyToObjectIris.map {
                    case (property: IRI, valObjIris: Seq[IRI]) =>

                        // build all the property's value objects by mapping over the value object Iris
                        val valueRdfData: Seq[ValueRdfData] = valObjIris.map {
                            valObjIri: IRI =>
                                val valueObjAssertions: Seq[(IRI, String)] = nonResourceStatements(valObjIri)

                                // get the resource's project
                                // value objects belong to the parent resource's project
                                val resourceProject: String = predicateMap(OntologyConstants.KnoraBase.AttachedToProject)

                                // prepend the resource's project to the value's assertions, and get the user's permission on the value
                                val maybeUserPermission = PermissionUtilADM.getUserPermissionFromAssertionsADM(valObjIri, (OntologyConstants.KnoraBase.AttachedToProject, resourceProject) +: valueObjAssertions, requestingUser)

                                valObjIri -> RdfWithUserPermission(valueObjAssertions, maybeUserPermission)
                        }.filter {
                            // check if the user has sufficient permissions to see the value object
                            case (valObjIri: IRI, rdfWithUserPermission: RdfWithUserPermission) =>
                                rdfWithUserPermission.maybeUserPermission.nonEmpty
                        }.flatMap {
                            case (valObjIri: IRI, valueRdfWithUserPermission: RdfWithUserPermission) =>

                                val valueStatements: Seq[(IRI, String)] = valueRdfWithUserPermission.assertions

                                // get all list node Iris possibly belonging to this value object
                                val listNodeIris: Set[IRI] = valueStatements.filter {
                                    case (pred: IRI, _: String) =>
                                        pred == OntologyConstants.KnoraBase.ValueHasListNode
                                }.map {
                                    case (_: IRI, obj: IRI) =>
                                        // we are only interested in the list node IRI
                                        obj
                                }.toSet

                                // get all the standoff node Iris possibly belonging to this value object
                                // do so by accessing the non resource statements using the value object IRI as a key
                                val standoffNodeIris: Set[IRI] = valueStatements.filter {
                                    case (pred: IRI, obj: String) =>
                                        pred == OntologyConstants.KnoraBase.ValueHasStandoff
                                }.map {
                                    case (pred: IRI, obj: IRI) =>
                                        // we are only interested in the standoff node IRI
                                        obj
                                }.toSet

                                // given the list node Iris, get the list node assertions
                                val (listNodeAssertions: Map[IRI, Seq[(IRI, String)]], valueAssertionsWithStandoff: Map[IRI, Seq[(IRI, String)]]) = nonResourceStatements.partition {
                                    case (subjIri: IRI, _: Seq[(IRI, String)]) =>
                                        listNodeIris(subjIri)
                                }

                                // given the standoff node Iris, get the standoff assertions
                                // do so by accessing the non resource statements using the standoff node IRI as a key
                                val (standoffAssertions: Map[IRI, Seq[(IRI, String)]], valueAssertions: Map[IRI, Seq[(IRI, String)]]) = valueAssertionsWithStandoff.partition {
                                    case (subjIri: IRI, _: Seq[(IRI, String)]) =>
                                        standoffNodeIris(subjIri)
                                }

                                val predicateMapForValueAssertions: ErrorHandlingMap[IRI, String] =
                                    new ErrorHandlingMap(valueAssertions(valObjIri).toMap, { key: IRI => s"Predicate $key not found for $valObjIri (value object)" })

                                val valueObjectClass: IRI = predicateMapForValueAssertions(OntologyConstants.Rdf.Type)

                                // check if it is a link value
                                if (valueObjectClass == OntologyConstants.KnoraBase.LinkValue) {
                                    // create a value object
                                    Some(ValueRdfData(
                                        valueObjectIri = valObjIri,
                                        valueObjectClass = valueObjectClass,
                                        userPermission = valueRdfWithUserPermission.maybeUserPermission.get,
                                        assertions = predicateMapForValueAssertions,
                                        standoff = Map.empty[IRI, Map[IRI, String]] // link value does not contain standoff
                                    ))

                                } else {

                                    // create a value object
                                    Some(ValueRdfData(
                                        valueObjectIri = valObjIri,
                                        valueObjectClass = valueObjectClass,
                                        userPermission = valueRdfWithUserPermission.maybeUserPermission.get,
                                        assertions = predicateMapForValueAssertions,
                                        standoff = standoffAssertions.map {
                                            case (standoffNodeIri: IRI, standoffAssertions: Seq[(IRI, String)]) =>
                                                (standoffNodeIri, standoffAssertions.toMap)
                                        }))
                                }
                        }

                        property -> valueRdfData
                }.filterNot {
                    // filter out those properties that do not have value objects (they may have been filtered out because the user does not have sufficient permissions to see them)
                    case (_, valObjs: Seq[ValueRdfData]) =>
                        valObjs.isEmpty
                }

                // create a map of resource Iris to a `ResourceWithValueRdfData`
                resourceIri -> ResourceWithValueRdfData(
                    resourceAssertions = assertionsExplicit,
                    isMainResource = isMainResource,
                    userPermission = resourceRdfWithUserPermission.maybeUserPermission.get,
                    valuePropertyAssertions = valuePropertyToValueObject
                )
        }

        // get incoming links for each resource
        val incomingLinksForResource: Map[IRI, Map[IRI, ResourceWithValueRdfData]] = flatResourcesWithValues.map {
            case (resourceIri: IRI, values: ResourceWithValueRdfData) =>

                // get all incoming links for resourceIri
                val incomingLinksForRes: Map[IRI, ResourceWithValueRdfData] = flatResourcesWithValues.foldLeft(Map.empty[IRI, ResourceWithValueRdfData]) {
                    case (acc: Map[IRI, ResourceWithValueRdfData], (incomingResIri: IRI, values: ResourceWithValueRdfData)) =>

                        val incomingLinkPropertyAssertions: Map[IRI, Seq[ValueRdfData]] = values.valuePropertyAssertions.foldLeft(Map.empty[IRI, Seq[ValueRdfData]]) {
                            case (acc: Map[IRI, Seq[ValueRdfData]], (valObjIri: IRI, values: Seq[ValueRdfData])) =>

                                // collect all link values that point to resourceIri
                                val incomingLinkValues: Seq[ValueRdfData] = values.foldLeft(Seq.empty[ValueRdfData]) {
                                    (acc, value: ValueRdfData) =>

                                        // check if it is a link value and points to this resource
                                        if (value.valueObjectClass == OntologyConstants.KnoraBase.LinkValue && value.assertions(OntologyConstants.Rdf.Object) == resourceIri) {
                                            // add incoming link value
                                            acc :+ value
                                        } else {
                                            acc
                                        }
                                }

                                if (incomingLinkValues.nonEmpty) {
                                    acc + (valObjIri -> incomingLinkValues)
                                } else {
                                    acc
                                }
                        }

                        if (incomingLinkPropertyAssertions.nonEmpty) {
                            acc + (incomingResIri -> values.copy(
                                valuePropertyAssertions = incomingLinkPropertyAssertions
                            ))
                        } else {
                            acc
                        }

                }

                // create an entry using the resource's Iri as a key and its incoming links as the value
                resourceIri -> incomingLinksForRes
        }

        /**
          * Given a resource IRI, finds any link values in the resource, and recursively embeds the target resource in each link value.
          *
          * @param resourceIri      the IRI of the resource to start with.
          * @param alreadyTraversed a set (initially empty) of the IRIs of resources that this function has already
          *                         traversed, to prevent an infinite loop if a cycle is encountered.
          * @return the same resource, with any nested resources attached to it.
          */
        def nestResources(resourceIri: IRI, alreadyTraversed: Set[IRI] = Set.empty[IRI]): ResourceWithValueRdfData = {
            val resource = flatResourcesWithValues(resourceIri)

            val transformedValuePropertyAssertions: Map[IRI, Seq[ValueRdfData]] = resource.valuePropertyAssertions.map {
                case (propIri, values) =>
                    val transformedValues = values.map {
                        value =>
                            if (value.valueObjectClass == OntologyConstants.KnoraBase.LinkValue) {
                                val dependentResourceIri: String = value.assertions(OntologyConstants.Rdf.Object)

                                if (alreadyTraversed(dependentResourceIri)) {
                                    value
                                } else {
                                    // If we don't have the dependent resource, that means that the user doesn't have
                                    // permission to see it, or it's been marked as deleted. Just return the link
                                    // value without a nested resource.
                                    if (flatResourcesWithValues.contains(dependentResourceIri)) {
                                        val dependentResource: ResourceWithValueRdfData = nestResources(dependentResourceIri, alreadyTraversed + resourceIri)

                                        value.copy(
                                            nestedResource = Some(dependentResource)
                                        )
                                    } else {
                                        value
                                    }
                                }
                            } else {
                                value
                            }
                    }

                    propIri -> transformedValues
            }

            // incomingLinksForResource contains incoming link values for each resource
            // flatResourcesWithValues contains the complete information

            // filter out those resources that already have been processed
            // and the main resources (they are already present on the top level of the response)
            //
            // the main resources point to dependent resources and would be treated as incoming links of dependent resources
            // this would create circular dependencies

            // resources that point to this resource
            val referringResources: Map[IRI, ResourceWithValueRdfData] = incomingLinksForResource(resourceIri).filterNot {
                case (incomingResIri: IRI, assertions: ResourceWithValueRdfData) =>
                    alreadyTraversed(incomingResIri) || flatResourcesWithValues(incomingResIri).isMainResource
            }

            // link value assertions that point to this resource
            val incomingLinkAssertions: Map[IRI, Seq[ValueRdfData]] = referringResources.values.foldLeft(Map.empty[IRI, Seq[ValueRdfData]]) {
                case (acc: Map[IRI, Seq[ValueRdfData]], assertions: ResourceWithValueRdfData) =>

                    val values: Map[IRI, Seq[ValueRdfData]] = assertions.valuePropertyAssertions.flatMap {
                        case (propIri: IRI, values: Seq[ValueRdfData]) =>

                            // check if the property Iri already exists (there could be several instances of the same property)
                            if (acc.get(propIri).nonEmpty) {
                                // add values to property Iri (keeping the already existing values)
                                acc + (propIri -> (acc(propIri) ++ values).sortBy(_.valueObjectIri))
                            } else {
                                // prop Iri does not exists yet, add it
                                acc + (propIri -> values.sortBy(_.valueObjectIri))
                            }
                    }

                    values
            }

            if (incomingLinkAssertions.nonEmpty) {
                // create a virtual property representing an incoming link
                val incomingProps: (IRI, Seq[ValueRdfData]) = OntologyConstants.KnoraBase.HasIncomingLinkValue -> incomingLinkAssertions.values.toSeq.flatten.map {
                    linkValue: ValueRdfData =>

                        // get the source of the link value (it points to the resource that is currently processed)
                        val source = Some(nestResources(linkValue.assertions(OntologyConstants.Rdf.Subject), alreadyTraversed + resourceIri))

                        linkValue.copy(
                            nestedResource = source,
                            isIncomingLink = true
                        )
                }

                resource.copy(
                    valuePropertyAssertions = transformedValuePropertyAssertions + incomingProps
                )
            } else {
                resource.copy(
                    valuePropertyAssertions = transformedValuePropertyAssertions
                )
            }

        }

        val mainResourceIris: Set[IRI] = flatResourcesWithValues.filter {
            case (_, resource) => resource.isMainResource // only main resources are present on the top level, dependent resources are nested in the link values
        }.map {
            case (resourceIri, _) => resourceIri
        }.toSet

        mainResourceIris.map {
            resourceIri =>
                val transformedResource = nestResources(resourceIri)
                resourceIri -> transformedResource
        }.toMap
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
                    valObj: ValueRdfData =>
                        valObj.valueObjectClass == OntologyConstants.KnoraBase.TextValue && valObj.assertions.get(OntologyConstants.KnoraBase.ValueHasMapping).nonEmpty
                }.map {
                    textValObj: ValueRdfData =>
                        textValObj.assertions(OntologyConstants.KnoraBase.ValueHasMapping)
                }

                // get mappings from linked resources
                val mappingsFromReferredResources: Set[IRI] = valObjs.filter {
                    valObj: ValueRdfData =>
                        valObj.nestedResource.nonEmpty
                }.flatMap {
                    valObj: ValueRdfData =>
                        val referredRes: ResourceWithValueRdfData = valObj.nestedResource.get

                        // recurse on the nested resource's values
                        getMappingIrisFromValuePropertyAssertions(referredRes.valuePropertyAssertions)
                }.toSet

                acc ++ mappings ++ mappingsFromReferredResources
        }
    }

    /**
      * Given a [[ValueRdfData]], constructs a [[TextValueContentV2]]. This method is used to process a text value
      * as returned in an API response, as well as to process a page of standoff markup that is being queried
      * separately from its text value.
      *
      * @param valueObject               the given [[ValueRdfData]].
      * @param valueObjectValueHasString the value's `knora-base:valueHasString`.
      * @param valueCommentOption        the value's comment, if any.
      * @param mappings                  the mappings needed for standoff conversions and XSL transformations.
      * @param queryStandoff             if `true`, make separate queries to get the standoff for the text value.
      * @param responderManager          the Knora responder manager.
      * @param requestingUser            the user making the request.
      * @return a [[TextValueContentV2]].
      */
    private def makeTextValueContentV2(resourceIri: IRI,
                                       valueObject: ValueRdfData,
                                       valueObjectValueHasString: Option[String],
                                       valueCommentOption: Option[String],
                                       mappings: Map[IRI, MappingAndXSLTransformation],
                                       queryStandoff: Boolean,
                                       responderManager: ActorRef,
                                       requestingUser: UserADM)(implicit stringFormatter: StringFormatter, timeout: Timeout, executionContext: ExecutionContext): Future[TextValueContentV2] = {
        // Any knora-base:TextValue may have a language
        val valueLanguageOption: Option[String] = valueObject.assertions.get(OntologyConstants.KnoraBase.ValueHasLanguage)

        if (valueObject.standoff.nonEmpty) {
            // The query included a page of standoff markup. This is either because we've queried the text value
            // and got the first page of its standoff along with it, or because we're querying a subsequent page
            // of standoff for a text value.

            val mappingIri: Option[IRI] = valueObject.assertions.get(OntologyConstants.KnoraBase.ValueHasMapping)
            val mappingAndXsltTransformation: Option[MappingAndXSLTransformation] = mappingIri.flatMap(definedMappingIri => mappings.get(definedMappingIri))

            for {
                standoff: Vector[StandoffTagV2] <- StandoffTagUtilV2.createStandoffTagsV2FromSparqlResults(
                    standoffAssertions = valueObject.standoff,
                    responderManager = responderManager,
                    requestingUser = requestingUser
                )

                valueHasMaxStandoffStartIndex = valueObject.assertions(OntologyConstants.KnoraBase.ValueHasMaxStandoffStartIndex).toInt
                lastStartIndexQueried = standoff.last.startIndex

                // Should we get more the rest of the standoff for the same text value?
                standoffToReturn <- if (queryStandoff && lastStartIndexQueried < valueHasMaxStandoffStartIndex) {
                    // We're supposed to get all the standoff for the text value. Ask the standoff responder for the rest of it.
                    // Each page of markup will be also be processed by this method. The resulting pages will be
                    // concatenated together and returned in a GetStandoffResponseV2.

                    // println(s"***** makeTextValueContentV2: Got <${valueObject.valueObjectIri}> with ${valueObject.standoff.size} standoff tags. Querying the rest.")

                    for {
                        standoffResponse <- (responderManager ? GetRemainingStandoffFromTextValueRequestV2(resourceIri = resourceIri, valueIri = valueObject.valueObjectIri, requestingUser = requestingUser)).mapTo[GetStandoffResponseV2]
                    } yield standoff ++ standoffResponse.standoff
                } else {
                    // We're not supposed to get any more standoff here, either because we have all of it already,
                    // or because we're just supposed to return one page.

                    // println(s"***** makeTextValueContentV2: Got <${valueObject.valueObjectIri}> with ${valueObject.standoff.size} standoff tags. Not querying more here.")

                    FastFuture.successful(standoff)
                }
            } yield TextValueContentV2(
                ontologySchema = InternalSchema,
                maybeValueHasString = valueObjectValueHasString,
                valueHasLanguage = valueLanguageOption,
                standoff = standoffToReturn,
                mappingIri = mappingIri,
                mapping = mappingAndXsltTransformation.map(_.mapping),
                xslt = mappingAndXsltTransformation.flatMap(_.XSLTransformation),
                comment = valueCommentOption
            )
        } else {
            // The query returned no standoff markup.

            // println(s"***** makeTextValueContentV2: Got <${valueObject.valueObjectIri}> with no standoff. Not querying more here.")

            FastFuture.successful(TextValueContentV2(
                ontologySchema = InternalSchema,
                maybeValueHasString = valueObjectValueHasString,
                valueHasLanguage = valueLanguageOption,
                comment = valueCommentOption
            ))
        }
    }

    /**
      * Given a [[ValueRdfData]], constructs a [[FileValueContentV2]].
      *
      * @param valueType                 the IRI of the file value type
      * @param valueObject               the given [[ValueRdfData]].
      * @param valueObjectValueHasString the value's `knora-base:valueHasString`.
      * @param valueCommentOption        the value's comment, if any.
      * @param mappings                  the mappings needed for standoff conversions and XSL transformations.
      * @param responderManager          the Knora responder manager.
      * @param requestingUser            the user making the request.
      * @return a [[FileValueContentV2]].
      */
    private def makeFileValueContentV2(valueType: IRI,
                                       valueObject: ValueRdfData,
                                       valueObjectValueHasString: String,
                                       valueCommentOption: Option[String],
                                       mappings: Map[IRI, MappingAndXSLTransformation],
                                       responderManager: ActorRef,
                                       requestingUser: UserADM)(implicit stringFormatter: StringFormatter, timeout: Timeout, executionContext: ExecutionContext): Future[FileValueContentV2] = {
        val fileValue = FileValueV2(
            internalMimeType = valueObject.assertions(OntologyConstants.KnoraBase.InternalMimeType),
            internalFilename = valueObject.assertions(OntologyConstants.KnoraBase.InternalFilename),
            originalFilename = valueObject.assertions(OntologyConstants.KnoraBase.OriginalFilename),
            originalMimeType = valueObject.assertions(OntologyConstants.KnoraBase.OriginalMimeType)
        )

        valueType match {
            case OntologyConstants.KnoraBase.StillImageFileValue =>

                FastFuture.successful(StillImageFileValueContentV2(
                    ontologySchema = InternalSchema,
                    fileValue = fileValue,
                    dimX = valueObject.assertions(OntologyConstants.KnoraBase.DimX).toInt,
                    dimY = valueObject.assertions(OntologyConstants.KnoraBase.DimY).toInt,
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.TextFileValue =>

                FastFuture.successful(TextFileValueContentV2(
                    ontologySchema = InternalSchema,
                    fileValue = fileValue,
                    comment = valueCommentOption
                ))
        }
    }

    /**
      * Given a [[ValueRdfData]], constructs a [[LinkValueContentV2]].
      *
      * @param valueObject               the given [[ValueRdfData]].
      * @param valueObjectValueHasString the value's `knora-base:valueHasString`.
      * @param valueCommentOption        the value's comment, if any.
      * @param mappings                  the mappings needed for standoff conversions and XSL transformations.
      * @param queryStandoff             if `true`, make separate queries to get the standoff for text values.
      * @param versionDate               if defined, represents the requested time in the the resources' version history.
      * @param responderManager          the Knora responder manager.
      * @param targetSchema              the schema of the response.
      * @param settings                  the application's settings.
      * @param requestingUser            the user making the request.
      * @return a [[LinkValueContentV2]].
      */
    private def makeLinkValueContentV2(valueObject: ValueRdfData,
                                       valueObjectValueHasString: String,
                                       valueCommentOption: Option[String],
                                       mappings: Map[IRI, MappingAndXSLTransformation],
                                       queryStandoff: Boolean,
                                       versionDate: Option[Instant],
                                       responderManager: ActorRef,
                                       targetSchema: ApiV2Schema,
                                       settings: SettingsImpl,
                                       requestingUser: UserADM)(implicit stringFormatter: StringFormatter, timeout: Timeout, executionContext: ExecutionContext): Future[LinkValueContentV2] = {
        val referredResourceIri: IRI = if (valueObject.isIncomingLink) {
            valueObject.assertions(OntologyConstants.Rdf.Subject)
        } else {
            valueObject.assertions(OntologyConstants.Rdf.Object)
        }

        val linkValue = LinkValueContentV2(
            ontologySchema = InternalSchema,
            referredResourceIri = referredResourceIri,
            isIncomingLink = valueObject.isIncomingLink,
            nestedResource = None,
            comment = valueCommentOption
        )

        valueObject.nestedResource match {

            case Some(nestedResourceAssertions: ResourceWithValueRdfData) =>

                // add information about the referred resource

                for {
                    nestedResource <- constructReadResourceV2(
                        resourceIri = referredResourceIri,
                        resourceWithValueRdfData = nestedResourceAssertions,
                        mappings = mappings,
                        queryStandoff = queryStandoff,
                        versionDate = versionDate,
                        responderManager = responderManager,
                        requestingUser = requestingUser,
                        targetSchema = targetSchema,
                        settings = settings
                    )
                } yield linkValue.copy(
                    nestedResource = Some(nestedResource) // construct a `ReadResourceV2`
                )


            case None => FastFuture.successful(linkValue) // do not include information about the referred resource
        }
    }

    /**
      * Given a [[ValueRdfData]], constructs a [[ValueContentV2]], considering the specific type of the given [[ValueRdfData]].
      *
      * @param valueObject      the given [[ValueRdfData]].
      * @param mappings         the mappings needed for standoff conversions and XSL transformations.
      * @param queryStandoff    if `true`, make separate queries to get the standoff for text values.
      * @param versionDate      if defined, represents the requested time in the the resources' version history.
      * @param responderManager the Knora responder manager.
      * @param targetSchema     the schema of the response.
      * @param settings         the application's settings.
      * @param requestingUser   the user making the request.
      * @return a [[ValueContentV2]] representing a value.
      */
    private def createValueContentV2FromValueRdfData(resourceIri: IRI,
                                                     valueObject: ValueRdfData,
                                                     mappings: Map[IRI, MappingAndXSLTransformation],
                                                     queryStandoff: Boolean,
                                                     versionDate: Option[Instant] = None,
                                                     responderManager: ActorRef,
                                                     targetSchema: ApiV2Schema,
                                                     settings: SettingsImpl,
                                                     requestingUser: UserADM)(implicit stringFormatter: StringFormatter, timeout: Timeout, executionContext: ExecutionContext): Future[ValueContentV2] = {
        // every knora-base:Value (any of its subclasses) has a string representation, but it is not necessarily returned with text values.
        val valueObjectValueHasString: Option[String] = valueObject.assertions.get(OntologyConstants.KnoraBase.ValueHasString)

        // every knora-base:value (any of its subclasses) may have a comment
        val valueCommentOption: Option[String] = valueObject.assertions.get(OntologyConstants.KnoraBase.ValueHasComment)

        val valueType: IRI = valueObject.valueObjectClass

        valueType match {
            case OntologyConstants.KnoraBase.TextValue =>
                makeTextValueContentV2(
                    resourceIri = resourceIri,
                    valueObject = valueObject,
                    valueObjectValueHasString = valueObjectValueHasString,
                    valueCommentOption = valueCommentOption,
                    mappings = mappings,
                    queryStandoff = queryStandoff,
                    responderManager = responderManager,
                    requestingUser = requestingUser
                )

            case OntologyConstants.KnoraBase.DateValue =>
                val startPrecisionStr = valueObject.assertions(OntologyConstants.KnoraBase.ValueHasStartPrecision)
                val endPrecisionStr = valueObject.assertions(OntologyConstants.KnoraBase.ValueHasEndPrecision)
                val calendarNameStr = valueObject.assertions(OntologyConstants.KnoraBase.ValueHasCalendar)

                FastFuture.successful(DateValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasStartJDN = valueObject.assertions(OntologyConstants.KnoraBase.ValueHasStartJDN).toInt,
                    valueHasEndJDN = valueObject.assertions(OntologyConstants.KnoraBase.ValueHasEndJDN).toInt,
                    valueHasStartPrecision = DatePrecisionV2.parse(startPrecisionStr, throw InconsistentTriplestoreDataException(s"Invalid date precision: $startPrecisionStr")),
                    valueHasEndPrecision = DatePrecisionV2.parse(endPrecisionStr, throw InconsistentTriplestoreDataException(s"Invalid date precision: $endPrecisionStr")),
                    valueHasCalendar = CalendarNameV2.parse(calendarNameStr, throw InconsistentTriplestoreDataException(s"Invalid calendar name: $calendarNameStr")),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.IntValue =>
                FastFuture.successful(IntegerValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasInteger = valueObject.assertions(OntologyConstants.KnoraBase.ValueHasInteger).toInt,
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.DecimalValue =>
                FastFuture.successful(DecimalValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasDecimal = BigDecimal(valueObject.assertions(OntologyConstants.KnoraBase.ValueHasDecimal)),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.BooleanValue =>
                FastFuture.successful(BooleanValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasBoolean = valueObject.assertions(OntologyConstants.KnoraBase.ValueHasBoolean).toBoolean,
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.UriValue =>
                FastFuture.successful(UriValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasUri = valueObject.assertions(OntologyConstants.KnoraBase.ValueHasUri),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.ColorValue =>
                FastFuture.successful(ColorValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasColor = valueObject.assertions(OntologyConstants.KnoraBase.ValueHasColor),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.GeomValue =>
                FastFuture.successful(GeomValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasGeometry = valueObject.assertions(OntologyConstants.KnoraBase.ValueHasGeometry),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.GeonameValue =>
                FastFuture.successful(GeonameValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasGeonameCode = valueObject.assertions(OntologyConstants.KnoraBase.ValueHasGeonameCode),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.ListValue =>

                val listNodeIri: String = valueObject.assertions(OntologyConstants.KnoraBase.ValueHasListNode)

                val listNode = HierarchicalListValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasListNode = listNodeIri,
                    listNodeLabel = None,
                    comment = valueCommentOption
                )

                // only query the list node if the response is requested in the simple schema
                // (label is required in the simple schema, but not in the complex schema)

                targetSchema match {
                    case ApiV2Simple => for {
                        nodeResponse <- (responderManager ? NodeGetRequestV2(listNodeIri, requestingUser)).mapTo[NodeGetResponseV2]
                    } yield listNode.copy(
                        listNodeLabel = nodeResponse.node.getLabelInPreferredLanguage(userLang = requestingUser.lang, fallbackLang = settings.fallbackLanguage)
                    )

                    case ApiV2Complex => FastFuture.successful(listNode)
                }



            case OntologyConstants.KnoraBase.IntervalValue =>
                FastFuture.successful(IntervalValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasIntervalStart = BigDecimal(valueObject.assertions(OntologyConstants.KnoraBase.ValueHasIntervalStart)),
                    valueHasIntervalEnd = BigDecimal(valueObject.assertions(OntologyConstants.KnoraBase.ValueHasIntervalEnd)),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.LinkValue =>
                makeLinkValueContentV2(
                    valueObject = valueObject,
                    valueObjectValueHasString = valueObjectValueHasString.getOrElse(throw AssertionException(s"Value <${valueObject.valueObjectIri}> has no knora-base:valueHasString")),
                    valueCommentOption = valueCommentOption,
                    mappings = mappings,
                    queryStandoff = queryStandoff,
                    versionDate = versionDate,
                    responderManager = responderManager,
                    requestingUser = requestingUser,
                    targetSchema = targetSchema,
                    settings = settings
                )

            case fileValueClass: IRI if OntologyConstants.KnoraBase.FileValueClasses.contains(fileValueClass) =>
                makeFileValueContentV2(
                    valueType = fileValueClass,
                    valueObject = valueObject,
                    valueObjectValueHasString = valueObjectValueHasString.getOrElse(throw AssertionException(s"Value <${valueObject.valueObjectIri}> has no knora-base:valueHasString")),
                    valueCommentOption = valueCommentOption,
                    mappings = mappings,
                    responderManager = responderManager,
                    requestingUser = requestingUser
                )

            case other => throw NotImplementedException(s"Not implemented yet: $other")
        }
    }

    /**
      *
      * Creates a [[ReadResourceV2]] from a [[ResourceWithValueRdfData]].
      *
      * @param resourceIri              the IRI of the resource.
      * @param resourceWithValueRdfData the Rdf data belonging to the resource.
      * @param mappings                 the mappings needed for standoff conversions and XSL transformations.
      * @param queryStandoff            if `true`, make separate queries to get the standoff for text values.
      * @param versionDate              if defined, represents the requested time in the the resources' version history.
      * @param responderManager         the Knora responder manager.
      * @param targetSchema             the schema of the response.
      * @param settings                 the application's settings.
      * @param requestingUser           the user making the request.
      * @return a [[ReadResourceV2]].
      */
    private def constructReadResourceV2(resourceIri: IRI,
                                        resourceWithValueRdfData: ResourceWithValueRdfData,
                                        mappings: Map[IRI, MappingAndXSLTransformation],
                                        queryStandoff: Boolean,
                                        versionDate: Option[Instant],
                                        responderManager: ActorRef,
                                        targetSchema: ApiV2Schema,
                                        settings: SettingsImpl,
                                        requestingUser: UserADM)(implicit stringFormatter: StringFormatter, timeout: Timeout, executionContext: ExecutionContext): Future[ReadResourceV2] = {
        def getDeletionInfo(entityIri: IRI, assertions: Map[IRI, String]): Option[DeletionInfo] = {
            val isDeletedStr: String = assertions(OntologyConstants.KnoraBase.IsDeleted)
            val resourceIsDeleted: Boolean = stringFormatter.toBoolean(isDeletedStr, throw InconsistentTriplestoreDataException(s"Couldn't parse knora-base:isDeleted in entity <$entityIri>: $isDeletedStr"))

            if (resourceIsDeleted) {
                val deleteDateStr = assertions(OntologyConstants.KnoraBase.DeleteDate)
                val deleteDate = stringFormatter.xsdDateTimeStampToInstant(deleteDateStr, throw InconsistentTriplestoreDataException(s"Couldn't parse knora-base:deleteDate in entity <$entityIri>: $deleteDateStr"))
                val deleteComment = assertions(OntologyConstants.KnoraBase.DeleteComment)

                Some(
                    DeletionInfo(
                        deleteDate = deleteDate,
                        deleteComment = deleteComment
                    )
                )
            } else {
                None
            }
        }

        val resourceAssertionsMap: Map[IRI, String] = new ErrorHandlingMap(resourceWithValueRdfData.resourceAssertions.toMap, { key: IRI => throw InconsistentTriplestoreDataException(s"Resource <$resourceIri> has no <$key>") })
        val resourceLabel: String = resourceAssertionsMap(OntologyConstants.Rdfs.Label)
        val resourceClassStr: IRI = resourceAssertionsMap(OntologyConstants.Rdf.Type)
        val resourceClass = resourceClassStr.toSmartIriWithErr(throw InconsistentTriplestoreDataException(s"Couldn't parse rdf:type of resource <$resourceIri>: <$resourceClassStr>"))
        val resourceAttachedToUser: IRI = resourceAssertionsMap(OntologyConstants.KnoraBase.AttachedToUser)
        val resourceAttachedToProject: IRI = resourceAssertionsMap(OntologyConstants.KnoraBase.AttachedToProject)
        val resourcePermissions: String = resourceAssertionsMap(OntologyConstants.KnoraBase.HasPermissions)
        val resourceCreationDateStr: String = resourceAssertionsMap(OntologyConstants.KnoraBase.CreationDate)
        val resourceCreationDate: Instant = stringFormatter.xsdDateTimeStampToInstant(resourceCreationDateStr, throw InconsistentTriplestoreDataException(s"Couldn't parse knora-base:creationDate: $resourceCreationDateStr"))
        val resourceLastModificationDate: Option[Instant] = resourceAssertionsMap.get(OntologyConstants.KnoraBase.LastModificationDate).map(resourceLastModificationDateStr => stringFormatter.xsdDateTimeStampToInstant(resourceLastModificationDateStr, throw InconsistentTriplestoreDataException(s"Couldn't parse knora-base:lastModificationDate: $resourceLastModificationDateStr")))
        val resourceDeletionInfo = getDeletionInfo(entityIri = resourceIri, assertions = resourceAssertionsMap)

        // get the resource's values
        val valueObjectFutures: Map[SmartIri, Seq[Future[ReadValueV2]]] = resourceWithValueRdfData.valuePropertyAssertions.map {
            case (property: IRI, valObjs: Seq[ValueRdfData]) =>
                val readValues: Seq[Future[ReadValueV2]] = valObjs.sortBy(_.valueObjectIri).sortBy { // order values by value IRI, then by knora-base:valueHasOrder
                    valObj: ValueRdfData =>

                        valObj.assertions.get(OntologyConstants.KnoraBase.ValueHasOrder) match {
                            case Some(orderLiteral: String) => orderLiteral.toInt

                            case None => 0 // set order to zero if not given
                        }

                }.map {
                    valObj: ValueRdfData =>
                        for {
                            valueContent: ValueContentV2 <- createValueContentV2FromValueRdfData(
                                resourceIri = resourceIri,
                                valueObject = valObj,
                                mappings = mappings,
                                queryStandoff = queryStandoff,
                                responderManager = responderManager,
                                requestingUser = requestingUser,
                                targetSchema = targetSchema,
                                settings = settings
                            )

                            valueCreationDateStr: String = valObj.assertions(OntologyConstants.KnoraBase.ValueCreationDate)
                            valueCreationDate: Instant = stringFormatter.xsdDateTimeStampToInstant(valueCreationDateStr, throw InconsistentTriplestoreDataException(s"Couldn't parse knora-base:valueCreationDate in value <${valObj.valueObjectIri}>: $valueCreationDateStr"))
                            valueDeletionInfo = getDeletionInfo(entityIri = valObj.valueObjectIri, assertions = valObj.assertions)
                            valueHasUUID: UUID = stringFormatter.decodeUuid(valObj.assertions(OntologyConstants.KnoraBase.ValueHasUUID))
                            previousValueIri: Option[IRI] = valObj.assertions.get(OntologyConstants.KnoraBase.PreviousValue)

                        } yield valueContent match {
                            case linkValueContentV2: LinkValueContentV2 =>
                                val valueHasRefCountStr: String = valObj.assertions(OntologyConstants.KnoraBase.ValueHasRefCount)
                                val valueHasRefCount: Int = stringFormatter.validateInt(valueHasRefCountStr, throw InconsistentTriplestoreDataException(s"Couldn't parse knora-base:valueHasRefCount in value <${valObj.valueObjectIri}>: $valueHasRefCountStr"))

                                ReadLinkValueV2(
                                    valueIri = valObj.valueObjectIri,
                                    attachedToUser = valObj.assertions(OntologyConstants.KnoraBase.AttachedToUser),
                                    permissions = valObj.assertions(OntologyConstants.KnoraBase.HasPermissions),
                                    userPermission = valObj.userPermission,
                                    valueCreationDate = valueCreationDate,
                                    valueHasUUID = valueHasUUID,
                                    valueContent = linkValueContentV2,
                                    valueHasRefCount = valueHasRefCount,
                                    previousValueIri = previousValueIri,
                                    deletionInfo = valueDeletionInfo
                                )

                            case textValueContentV2: TextValueContentV2 =>
                                val maybeValueHasMaxStandoffStartIndexStr: Option[String] = valObj.assertions.get(OntologyConstants.KnoraBase.ValueHasMaxStandoffStartIndex)

                                val maybeValueHasMaxStandoffStartIndex: Option[Int] = maybeValueHasMaxStandoffStartIndexStr.map {
                                    valueHasMaxStandoffStartIndexStr =>
                                        stringFormatter.validateInt(
                                            valueHasMaxStandoffStartIndexStr,
                                            throw InconsistentTriplestoreDataException(s"Couldn't parse knora-base:valueHasMaxStandoffStartIndex in value <${valObj.valueObjectIri}>: $valueHasMaxStandoffStartIndexStr"))
                                }

                                ReadTextValueV2(
                                    valueIri = valObj.valueObjectIri,
                                    attachedToUser = valObj.assertions(OntologyConstants.KnoraBase.AttachedToUser),
                                    permissions = valObj.assertions(OntologyConstants.KnoraBase.HasPermissions),
                                    userPermission = valObj.userPermission,
                                    valueCreationDate = valueCreationDate,
                                    valueHasUUID = valueHasUUID,
                                    valueContent = textValueContentV2,
                                    valueHasMaxStandoffStartIndex = maybeValueHasMaxStandoffStartIndex,
                                    previousValueIri = previousValueIri,
                                    deletionInfo = valueDeletionInfo
                                )

                            case otherValueContentV2: ValueContentV2 =>
                                ReadOtherValueV2(
                                    valueIri = valObj.valueObjectIri,
                                    attachedToUser = valObj.assertions(OntologyConstants.KnoraBase.AttachedToUser),
                                    permissions = valObj.assertions(OntologyConstants.KnoraBase.HasPermissions),
                                    userPermission = valObj.userPermission,
                                    valueCreationDate = valueCreationDate,
                                    valueHasUUID = valueHasUUID,
                                    valueContent = otherValueContentV2,
                                    previousValueIri = previousValueIri,
                                    deletionInfo = valueDeletionInfo
                                )
                        }
                }

                property.toSmartIri -> readValues
        }

        for {
            projectResponse: ProjectGetResponseADM <- (responderManager ? ProjectGetRequestADM(maybeIri = Some(resourceAttachedToProject), requestingUser = requestingUser)).mapTo[ProjectGetResponseADM]
            valueObjects <- ActorUtil.sequenceSeqFuturesInMap(valueObjectFutures)
        } yield ReadResourceV2(
            resourceIri = resourceIri,
            resourceClassIri = resourceClass,
            label = resourceLabel,
            attachedToUser = resourceAttachedToUser,
            projectADM = projectResponse.project,
            permissions = resourcePermissions,
            userPermission = resourceWithValueRdfData.userPermission,
            values = valueObjects,
            creationDate = resourceCreationDate,
            lastModificationDate = resourceLastModificationDate,
            versionDate = versionDate,
            deletionInfo = resourceDeletionInfo
        )
    }

    /**
      * Creates a response to a full resource request.
      *
      * @param resourceIri      the IRI of the requested resource.
      * @param resourceRdfData  the results returned by the triplestore.
      * @param mappings         the mappings needed for standoff conversions and XSL transformations.
      * @param queryStandoff    if `true`, make separate queries to get the standoff for text values.
      * @param versionDate      if defined, represents the requested time in the the resources' version history.
      * @param responderManager the Knora responder manager.
      * @param targetSchema     the schema of response.
      * @param settings         the application's settings.
      * @param requestingUser   the user making the request.
      * @return a [[ReadResourceV2]].
      */
    def createFullResourceResponse(resourceIri: IRI,
                                   resourceRdfData: ResourceWithValueRdfData,
                                   mappings: Map[IRI, MappingAndXSLTransformation],
                                   queryStandoff: Boolean,
                                   versionDate: Option[Instant],
                                   responderManager: ActorRef,
                                   targetSchema: ApiV2Schema,
                                   settings: SettingsImpl,
                                   requestingUser: UserADM)(implicit stringFormatter: StringFormatter, timeout: Timeout, executionContext: ExecutionContext): Future[ReadResourceV2] = {

        constructReadResourceV2(
            resourceIri = resourceIri,
            resourceWithValueRdfData = resourceRdfData,
            mappings = mappings,
            queryStandoff = queryStandoff,
            versionDate = versionDate,
            responderManager = responderManager,
            requestingUser = requestingUser,
            targetSchema = targetSchema,
            settings = settings
        )
    }

    /**
      * Creates a response to a fulltext or extended search.
      *
      * @param searchResults      the resources that matched the query and the client has permissions to see.
      * @param orderByResourceIri the order in which the resources should be returned.
      * @param mappings           the mappings to convert standoff to XML, if any.
      * @param queryStandoff      if `true`, make separate queries to get the standoff for text values.
      * @param forbiddenResource  the ForbiddenResource, if any.
      * @param responderManager   the Knora responder manager.
      * @param targetSchema       the schema of response.
      * @param settings           the application's settings.
      * @param requestingUser     the user making the request.
      * @return a collection of [[ReadResourceV2]] representing the search results.
      */
    def createSearchResponse(searchResults: Map[IRI, ResourceWithValueRdfData],
                             orderByResourceIri: Seq[IRI],
                             mappings: Map[IRI, MappingAndXSLTransformation] = Map.empty[IRI, MappingAndXSLTransformation],
                             queryStandoff: Boolean,
                             forbiddenResource: Option[ReadResourceV2],
                             responderManager: ActorRef,
                             targetSchema: ApiV2Schema,
                             settings: SettingsImpl,
                             requestingUser: UserADM)(implicit stringFormatter: StringFormatter, timeout: Timeout, executionContext: ExecutionContext): Future[Vector[ReadResourceV2]] = {

        if (orderByResourceIri.toSet != searchResults.keySet && forbiddenResource.isEmpty) throw AssertionException(s"Not all resources are visible, but forbiddenResource is None")

        // iterate over orderByResourceIris and construct the response in the correct order
        val readResourceFutures: Vector[Future[ReadResourceV2]] = orderByResourceIri.map {
            resourceIri: IRI =>

                // the user may not have the permissions to see the resource
                // i.e. it may not be contained in searchResults
                searchResults.get(resourceIri) match {
                    case Some(assertions: ResourceWithValueRdfData) =>
                        // sufficient permissions
                        // add the resource to the list of results
                        constructReadResourceV2(
                            resourceIri = resourceIri,
                            resourceWithValueRdfData = assertions,
                            mappings = mappings,
                            queryStandoff = queryStandoff,
                            versionDate = None,
                            responderManager = responderManager,
                            targetSchema = targetSchema,
                            settings = settings,
                            requestingUser = requestingUser
                        )

                    case None =>
                        // include the forbidden resource instead of skipping (the amount of results should be constant -> limit)
                        Future(forbiddenResource.getOrElse(throw AssertionException(s"Not all resources are visible, but forbiddenResource is None")))

                }
        }.toVector

        Future.sequence(readResourceFutures)
    }
}