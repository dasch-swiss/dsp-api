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
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectGetRequestADM, ProjectGetResponseADM, ProjectIdentifierADM}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v2.responder.listsmessages.{NodeGetRequestV2, NodeGetResponseV2}
import org.knora.webapi.messages.v2.responder.ontologymessages.StandoffEntityInfoGetResponseV2
import org.knora.webapi.messages.v2.responder.resourcemessages._
import org.knora.webapi.messages.v2.responder.standoffmessages.{GetRemainingStandoffFromTextValueRequestV2, GetStandoffResponseV2, MappingXMLtoStandoff, StandoffTagV2}
import org.knora.webapi.messages.v2.responder.valuemessages._
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.PermissionUtilADM.{EntityPermission, ViewPermission}
import org.knora.webapi.util.date.{CalendarNameV2, DatePrecisionV2}
import org.knora.webapi.util.standoff.StandoffTagUtilV2

import scala.concurrent.{ExecutionContext, Future}


object ConstructResponseUtilV2 {

    private val InferredPredicates = Set(
        OntologyConstants.KnoraBase.HasValue,
        OntologyConstants.KnoraBase.IsMainResource
    )

    /**
     * A map of resource IRIs to resource RDF data.
     */
    type RdfResources = Map[IRI, ResourceWithValueRdfData]

    /**
     * Makes an empty instance of [[RdfResources]].
     */
    def emptyRdfResources: RdfResources = Map.empty

    /**
     * A map of property IRIs to value RDF data.
     */
    type RdfPropertyValues = Map[SmartIri, Seq[ValueRdfData]]

    /**
     * Makes an empty instance of [[RdfPropertyValues]].
     */
    def emptyRdfPropertyValues: RdfPropertyValues = Map.empty

    /**
     * A map of subject IRIs to [[ConstructPredicateObjects]] instances.
     */
    type Statements = Map[IRI, ConstructPredicateObjects]

    /**
     * A flattened map of predicates to objects. This assumes that each predicate has
     * * only one object.
     */
    type FlatPredicateObjects = Map[SmartIri, LiteralV2]

    /**
     * A map of subject IRIs to flattened maps of predicates to objects.
     */
    type FlatStatements = Map[IRI, Map[SmartIri, LiteralV2]]

    /**
     * Makes an empty instance of [[FlatStatements]].
     */
    def emptyFlatStatements: FlatStatements = Map.empty

    /**
     * Represents assertions about an RDF subject.
     */
    sealed trait RdfData {
        /**
         * The IRI of the subject.
         */
        val subjectIri: IRI

        /**
         * Assertions about the subject.
         */
        val assertions: FlatPredicateObjects

        /**
         * Returns the optional string object of the specified predicate. Throws an exception if the object is not a string.
         *
         * @param predicateIri the predicate.
         * @return the string object of the predicate.
         */
        def maybeStringObject(predicateIri: SmartIri): Option[String] = {
            assertions.get(predicateIri).map {
                literal => literal.asStringLiteral(throw InconsistentTriplestoreDataException(s"Unexpected object of $subjectIri $predicateIri: $literal")).value
            }
        }

        /**
         * Returns the required string object of the specified predicate. Throws an exception if the object is not found or
         * is not a string.
         *
         * @param predicateIri the predicate.
         * @return the string object of the predicate.
         */
        def requireStringObject(predicateIri: SmartIri): String = {
            maybeStringObject(predicateIri).getOrElse(throw InconsistentTriplestoreDataException(s"Subject $subjectIri does not have predicate $predicateIri"))
        }

        /**
         * Returns the optional IRI object of the specified predicate. Throws an exception if the object is not an IRI.
         *
         * @param predicateIri the predicate.
         * @return the IRI object of the predicate.
         */
        def maybeIriObject(predicateIri: SmartIri): Option[IRI] = {
            assertions.get(predicateIri).map {
                literal => literal.asIriLiteral(throw InconsistentTriplestoreDataException(s"Unexpected object of $subjectIri $predicateIri: $literal")).value
            }
        }

        /**
         * Returns the required IRI object of the specified predicate. Throws an exception if the object is not found or
         * is not an IRI.
         *
         * @param predicateIri the predicate.
         * @return the IRI object of the predicate.
         */
        def requireIriObject(predicateIri: SmartIri): IRI = {
            maybeIriObject(predicateIri).getOrElse(throw InconsistentTriplestoreDataException(s"Subject $subjectIri does not have predicate $predicateIri"))
        }

        /**
         * Returns the optional integer object of the specified predicate. Throws an exception if the object is not an integer.
         *
         * @param predicateIri the predicate.
         * @return the integer object of the predicate.
         */
        def maybeIntObject(predicateIri: SmartIri): Option[Int] = {
            assertions.get(predicateIri).map {
                literal => literal.asIntLiteral(throw InconsistentTriplestoreDataException(s"Unexpected object of $subjectIri $predicateIri: $literal")).value
            }
        }

        /**
         * Returns the required integer object of the specified predicate. Throws an exception if the object is not found or
         * is not an integer.
         *
         * @param predicateIri the predicate.
         * @return the integer object of the predicate.
         */
        def requireIntObject(predicateIri: SmartIri): Int = {
            maybeIntObject(predicateIri).getOrElse(throw InconsistentTriplestoreDataException(s"Subject $subjectIri does not have predicate $predicateIri"))
        }

        /**
         * Returns the optional boolean object of the specified predicate. Throws an exception if the object is not a boolean.
         *
         * @param predicateIri the predicate.
         * @return the boolean object of the predicate.
         */
        def maybeBooleanObject(predicateIri: SmartIri): Option[Boolean] = {
            assertions.get(predicateIri).map {
                literal => literal.asBooleanLiteral(throw InconsistentTriplestoreDataException(s"Unexpected object of $subjectIri $predicateIri: $literal")).value
            }
        }

        /**
         * Returns the required boolean object of the specified predicate. Throws an exception if the object is not found or
         * is not an boolean value.
         *
         * @param predicateIri the predicate.
         * @return the boolean object of the predicate.
         */
        def requireBooleanObject(predicateIri: SmartIri): Boolean = {
            maybeBooleanObject(predicateIri).getOrElse(throw InconsistentTriplestoreDataException(s"Subject $subjectIri does not have predicate $predicateIri"))
        }

        /**
         * Returns the optional decimal object of the specified predicate. Throws an exception if the object is not a decimal.
         *
         * @param predicateIri the predicate.
         * @return the decimal object of the predicate.
         */
        def maybeDecimalObject(predicateIri: SmartIri): Option[BigDecimal] = {
            assertions.get(predicateIri).map {
                literal => literal.asDecimalLiteral(throw InconsistentTriplestoreDataException(s"Unexpected object of $subjectIri $predicateIri: $literal")).value
            }
        }

        /**
         * Returns the required decimal object of the specified predicate. Throws an exception if the object is not found or
         * is not an decimal value.
         *
         * @param predicateIri the predicate.
         * @return the decimal object of the predicate.
         */
        def requireDecimalObject(predicateIri: SmartIri): BigDecimal = {
            maybeDecimalObject(predicateIri).getOrElse(throw InconsistentTriplestoreDataException(s"Subject $subjectIri does not have predicate $predicateIri"))
        }


        /**
         * Returns the optional timestamp object of the specified predicate. Throws an exception if the object is not a timestamp.
         *
         * @param predicateIri the predicate.
         * @return the timestamp object of the predicate.
         */
        def maybeDateTimeObject(predicateIri: SmartIri): Option[Instant] = {
            assertions.get(predicateIri).map {
                literal => literal.asDateTimeLiteral(throw InconsistentTriplestoreDataException(s"Unexpected object of $subjectIri $predicateIri: $literal")).value
            }
        }

        /**
         * Returns the required timestamp object of the specified predicate. Throws an exception if the object is not found or
         * is not an timestamp value.
         *
         * @param predicateIri the predicate.
         * @return the timestamp object of the predicate.
         */
        def requireDateTimeObject(predicateIri: SmartIri): Instant = {
            maybeDateTimeObject(predicateIri).getOrElse(throw InconsistentTriplestoreDataException(s"Subject $subjectIri does not have predicate $predicateIri"))
        }
    }

    /**
     * Represents the RDF data about a value, possibly including standoff.
     *
     * @param subjectIri       the value object's IRI.
     * @param valueObjectClass the type (class) of the value object.
     * @param nestedResource   the nested resource in case of a link value (either the source or the target of a link value, depending on [[isIncomingLink]]).
     * @param isIncomingLink   indicates if it is an incoming or outgoing link in case of a link value.
     * @param userPermission   the permission that the requesting user has on the value.
     * @param assertions       the value objects assertions.
     * @param standoff         standoff assertions, if any.
     */
    case class ValueRdfData(subjectIri: IRI,
                            valueObjectClass: SmartIri,
                            nestedResource: Option[ResourceWithValueRdfData] = None,
                            isIncomingLink: Boolean = false,
                            userPermission: EntityPermission,
                            assertions: FlatPredicateObjects,
                            standoff: FlatStatements) extends RdfData

    /**
     * Represents a resource and its values.
     *
     * @param subjectIri              the resource IRI.
     * @param assertions              assertions about the resource (direct statements).
     * @param isMainResource          indicates if this represents a top level resource or a referred resource (depending on the query).
     * @param userPermission          the permission that the requesting user has on the resource.
     * @param valuePropertyAssertions assertions about value properties.
     */
    case class ResourceWithValueRdfData(subjectIri: IRI,
                                        assertions: FlatPredicateObjects,
                                        isMainResource: Boolean,
                                        userPermission: Option[EntityPermission],
                                        valuePropertyAssertions: RdfPropertyValues) extends RdfData


    /**
     * A [[ResourceWithValueRdfData]] representing a placeholder for a main resource that the user doesn't
     * have permission to see. It is replaced by `ForbiddenResource` during processing.
     */
    val ForbiddenMainResourcePlaceholder: ResourceWithValueRdfData = ResourceWithValueRdfData(
        subjectIri = StringFormatter.ForbiddenResourceIri,
        assertions = Map.empty,
        isMainResource = true,
        userPermission = Some(ViewPermission),
        valuePropertyAssertions = Map.empty
    )

    /**
     * A [[ResourceWithValueRdfData]] representing a placeholder for a dependent resource that the user doesn't
     * have permission to see. It is replaced by `ForbiddenResource` during processing.
     */
    val ForbiddenDependentResourcePlaceholder: ResourceWithValueRdfData = ResourceWithValueRdfData(
        subjectIri = StringFormatter.ForbiddenResourceIri,
        assertions = Map.empty,
        isMainResource = false,
        userPermission = Some(ViewPermission),
        valuePropertyAssertions = Map.empty
    )

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
    def splitMainResourcesAndValueRdfData(constructQueryResults: SparqlExtendedConstructResponse, requestingUser: UserADM)(implicit stringFormatter: StringFormatter): RdfResources = {

        // An intermediate data structure containing RDF assertions about an entity and the user's permission on the entity.
        case class RdfWithUserPermission(assertions: ConstructPredicateObjects, maybeUserPermission: Option[EntityPermission])

        // Make sure all the subjects are IRIs, because blank nodes are not used in resources.
        val resultsWithIriSubjects: Statements = constructQueryResults.statements.map {
            case (iriSubject: IriSubjectV2, statements: ConstructPredicateObjects) => iriSubject.value -> statements
            case (otherSubject: SubjectV2, _: ConstructPredicateObjects) => throw InconsistentTriplestoreDataException(s"Unexpected subject: $otherSubject")
        }

        // split statements about resources and other statements (value objects and standoff)
        // resources are identified by the triple "resourceIri a knora-base:Resource" which is an inferred information returned by the SPARQL Construct query.
        val (resourceStatements: Statements, nonResourceStatements: Statements) = resultsWithIriSubjects.partition {
            case (_: IRI, assertions: ConstructPredicateObjects) =>
                // check if the subject is a Knora resource
                assertions.getOrElse(OntologyConstants.Rdf.Type.toSmartIri, Seq.empty).contains(IriLiteralV2(OntologyConstants.KnoraBase.Resource))
        }

        val flatResourcesWithValues: RdfResources = resourceStatements.map {
            case (resourceIri: IRI, assertions: ConstructPredicateObjects) =>
                // remove inferred statements (non explicit) returned in the query result
                // the query returns the following inferred information:
                // - every resource is a knora-base:Resource
                // - every value property is a subproperty of knora-base:hasValue
                // - every resource that's a main resource (not a dependent resource) in the query result has knora-base:isMainResource true
                val assertionsExplicit: ConstructPredicateObjects = assertions.filterNot {
                    case (pred: SmartIri, _) => InferredPredicates(pred.toString)
                }.map {
                    case (pred: SmartIri, objs: Seq[LiteralV2]) =>
                        if (pred.toString == OntologyConstants.Rdf.Type) {
                            pred -> objs.filterNot {
                                case IriLiteralV2(OntologyConstants.KnoraBase.Resource) => true
                                case _ => false
                            }
                        } else {
                            pred -> objs
                        }
                }

                // check for the knora-base:isMainResource flag created by the SPARQL CONSTRUCT query
                val isMainResource: Boolean = assertions.get(OntologyConstants.KnoraBase.IsMainResource.toSmartIri) match {
                    case Some(Seq(BooleanLiteralV2(value))) => value
                    case _ => false
                }

                // Make a set of all the value object IRIs, because we're going to associate them with their properties.
                val valueObjectIris: Set[IRI] = assertions.collect {
                    case (pred: SmartIri, objs: Seq[LiteralV2]) if pred.toString == OntologyConstants.KnoraBase.HasValue =>
                        objs.map {
                            case IriLiteralV2(iri) => iri
                            case other => throw InconsistentTriplestoreDataException(s"Unexpected object for $resourceIri knora-base:hasValue: $other")
                        }
                }.flatten.toSet

                // Make a map of property IRIs to sequences of value IRIs.
                val valuePropertyToObjectIris: Map[SmartIri, Seq[IRI]] = assertionsExplicit.map {
                    case (pred: SmartIri, objs: Seq[LiteralV2]) =>
                        // Get only the assertions in which the object is a value object IRI.

                        val valueObjIris: Seq[IriLiteralV2] = objs.collect {
                            case iriObj: IriLiteralV2 if valueObjectIris(iriObj.value) => iriObj
                        }

                        pred -> valueObjIris
                }.filter {
                    case (_: SmartIri, objs: Seq[IriLiteralV2]) => objs.nonEmpty
                }.groupBy {
                    case (pred: SmartIri, _: Seq[IriLiteralV2]) =>
                        // Turn the sequence of assertions into a Map of predicate IRIs to assertions.
                        pred
                }.map {
                    case (pred: SmartIri, valueAssertions: Map[SmartIri, Seq[IriLiteralV2]]) =>
                        // Replace the assertions with their objects, i.e. the value object IRIs.
                        pred -> valueAssertions.values.flatten.map(_.value).toSeq
                }

                // Make a map of property IRIs to sequences of value objects.
                val valuePropertyToValueObject: RdfPropertyValues = valuePropertyToObjectIris.map {
                    case (property: SmartIri, valObjIris: Seq[IRI]) =>

                        // build all the property's value objects by mapping over the value object Iris
                        val valueRdfData: Seq[ValueRdfData] = valObjIris.map {
                            valObjIri: IRI =>
                                val valueObjAssertions: ConstructPredicateObjects = nonResourceStatements(valObjIri)

                                // get the resource's project
                                // value objects belong to the parent resource's project

                                val resourceProjectLiteral: LiteralV2 = assertionsExplicit.getOrElse(
                                    OntologyConstants.KnoraBase.AttachedToProject.toSmartIri,
                                    throw InconsistentTriplestoreDataException(s"Resource $resourceIri has no knora-base:attachedToProject")
                                ).head

                                // prepend the resource's project to the value's assertions, and get the user's permission on the value
                                val maybeUserPermission = PermissionUtilADM.getUserPermissionFromConstructAssertionsADM(
                                    entityIri = valObjIri,
                                    assertions = valueObjAssertions + (OntologyConstants.KnoraBase.AttachedToProject.toSmartIri -> Seq(resourceProjectLiteral)),
                                    requestingUser = requestingUser
                                )

                                valObjIri -> RdfWithUserPermission(valueObjAssertions, maybeUserPermission)
                        }.filter {
                            // check if the user has sufficient permissions to see the value object
                            case (_: IRI, rdfWithUserPermission: RdfWithUserPermission) =>
                                rdfWithUserPermission.maybeUserPermission.nonEmpty
                        }.flatMap {
                            case (valObjIri: IRI, valueRdfWithUserPermission: RdfWithUserPermission) =>
                                // get all the standoff node IRIs possibly belonging to this value object
                                val standoffNodeIris: Set[IRI] = valueRdfWithUserPermission.assertions.collect {
                                    case (pred: SmartIri, objs: Seq[LiteralV2]) if pred.toString == OntologyConstants.KnoraBase.ValueHasStandoff => objs.map(_.toString)
                                }.flatten.toSet

                                // given the standoff node IRIs, get the standoff assertions
                                val standoffAssertions: FlatStatements = nonResourceStatements.collect {
                                    case (subjIri: IRI, assertions: ConstructPredicateObjects) if standoffNodeIris(subjIri) =>
                                        subjIri -> assertions.flatMap {
                                            case (pred: SmartIri, objs: Seq[LiteralV2]) => objs.map {
                                                obj => pred -> obj
                                            }
                                        }
                                }

                                // Flatten the value's statements.
                                val valueStatements: FlatPredicateObjects = valueRdfWithUserPermission.assertions.flatMap {
                                    case (pred: SmartIri, objs: Seq[LiteralV2]) => objs.map {
                                        obj => pred -> obj
                                    }
                                }

                                // Get the rdf:type of the value.
                                val rdfTypeLiteral: LiteralV2 = valueStatements.getOrElse(OntologyConstants.Rdf.Type.toSmartIri, throw InconsistentTriplestoreDataException(s"Value $valObjIri has no rdf:type"))

                                val valueObjectClass: SmartIri = rdfTypeLiteral.asIriLiteral(
                                    throw InconsistentTriplestoreDataException(s"Unexpected object of $valObjIri rdf:type: $rdfTypeLiteral")
                                ).value.toSmartIri

                                // check if it is a link value
                                if (valueObjectClass.toString == OntologyConstants.KnoraBase.LinkValue) {
                                    // create a link value object
                                    Some(
                                        ValueRdfData(
                                            subjectIri = valObjIri,
                                            valueObjectClass = valueObjectClass,
                                            userPermission = valueRdfWithUserPermission.maybeUserPermission.get,
                                            assertions = valueStatements,
                                            standoff = emptyFlatStatements // link value does not contain standoff
                                        )
                                    )

                                } else {
                                    // create a non-link value object
                                    Some(
                                        ValueRdfData(
                                            subjectIri = valObjIri,
                                            valueObjectClass = valueObjectClass,
                                            userPermission = valueRdfWithUserPermission.maybeUserPermission.get,
                                            assertions = valueStatements,
                                            standoff = standoffAssertions
                                        )
                                    )
                                }
                        }

                        property -> valueRdfData
                }.filterNot {
                    // filter out those properties that do not have value objects (they may have been filtered out because the user does not have sufficient permissions to see them)
                    case (_, valObjs: Seq[ValueRdfData]) =>
                        valObjs.isEmpty
                }

                // Flatten the resource assertions.
                val resourceAssertions: FlatPredicateObjects = assertionsExplicit.map {
                    case (pred: SmartIri, objs: Seq[LiteralV2]) => pred -> objs.head
                }

                val userPermission: Option[EntityPermission] = PermissionUtilADM.getUserPermissionFromConstructAssertionsADM(resourceIri, assertions, requestingUser)

                // create a map of resource Iris to a `ResourceWithValueRdfData`
                resourceIri -> ResourceWithValueRdfData(
                    subjectIri = resourceIri,
                    assertions = resourceAssertions,
                    isMainResource = isMainResource,
                    userPermission = userPermission,
                    valuePropertyAssertions = valuePropertyToValueObject
                )
        }

        // Identify the resources that the user has permission to see.

        val (visibleResources, hiddenResources) = flatResourcesWithValues.partition {
            case (_: IRI, resource: ResourceWithValueRdfData) => resource.userPermission.nonEmpty
        }

        val mainResourceIrisVisible: Set[IRI] = visibleResources.collect {
            case (resourceIri: IRI, resource: ResourceWithValueRdfData) if resource.isMainResource => resourceIri
        }.toSet

        val mainResourceIrisNotVisible: Set[IRI] = hiddenResources.collect {
            case (resourceIri: IRI, resource: ResourceWithValueRdfData) if resource.isMainResource => resourceIri
        }.toSet

        val dependentResourceIrisVisible: Set[IRI] = visibleResources.collect {
            case (resourceIri: IRI, resource: ResourceWithValueRdfData) if !resource.isMainResource => resourceIri
        }.toSet

        val dependentResourceIrisNotVisible: Set[IRI] = hiddenResources.collect {
            case (resourceIri: IRI, resource: ResourceWithValueRdfData) if !resource.isMainResource => resourceIri
        }.toSet

        // get incoming links for each resource: a map of resource IRIs to resources that link to it
        val incomingLinksForResource: Map[IRI, RdfResources] = visibleResources.map {
            case (resourceIri: IRI, values: ResourceWithValueRdfData) =>

                // get all incoming links for resourceIri
                val incomingLinksForRes: RdfResources = flatResourcesWithValues.foldLeft(emptyRdfResources) {
                    case (acc: RdfResources, (otherResourceIri: IRI, otherResource: ResourceWithValueRdfData)) =>

                        val incomingLinkPropertyAssertions: RdfPropertyValues = otherResource.valuePropertyAssertions.foldLeft(emptyRdfPropertyValues) {
                            case (acc: RdfPropertyValues, (prop: SmartIri, otherResourceValues: Seq[ValueRdfData])) =>

                                // collect all link values that point to resourceIri
                                val incomingLinkValues: Seq[ValueRdfData] = otherResourceValues.foldLeft(Seq.empty[ValueRdfData]) {
                                    (acc, value: ValueRdfData) =>

                                        // check if it is a link value and points to this resource
                                        if (value.valueObjectClass.toString == OntologyConstants.KnoraBase.LinkValue && value.requireIriObject(OntologyConstants.Rdf.Object.toSmartIri) == resourceIri) {
                                            acc :+ value
                                        } else {
                                            acc
                                        }
                                }

                                if (incomingLinkValues.nonEmpty) {
                                    acc + (prop -> incomingLinkValues)
                                } else {
                                    acc
                                }
                        }

                        if (incomingLinkPropertyAssertions.nonEmpty) {
                            acc + (otherResourceIri -> values.copy(
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
            val resource = visibleResources(resourceIri)

            val transformedValuePropertyAssertions: RdfPropertyValues = resource.valuePropertyAssertions.map {
                case (propIri, values) =>
                    val transformedValues = values.map {
                        value: ValueRdfData =>
                            if (value.valueObjectClass.toString == OntologyConstants.KnoraBase.LinkValue) {
                                val dependentResourceIri: IRI = value.requireIriObject(OntologyConstants.Rdf.Object.toSmartIri)

                                if (alreadyTraversed(dependentResourceIri)) {
                                    value
                                } else {
                                    // Do we have the dependent resource?
                                    if (dependentResourceIrisVisible.contains(dependentResourceIri)) {
                                        // Yes. Nest it in the link value.
                                        val dependentResource: ResourceWithValueRdfData = nestResources(dependentResourceIri, alreadyTraversed + resourceIri)

                                        value.copy(
                                            nestedResource = Some(dependentResource)
                                        )
                                    } else if (dependentResourceIrisNotVisible.contains(dependentResourceIri)) {
                                        // No, because the user doesn't have permission to see it. Nest a placeholder for
                                        // ForbiddenResource in the link value.
                                        value.copy(
                                            nestedResource = Some(ForbiddenDependentResourcePlaceholder)
                                        )
                                    } else {
                                        // We don't have the dependent resource because it is marked as deleted. Just
                                        // return the link value without a nested resource.
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
            val referringResources: RdfResources = incomingLinksForResource(resourceIri).filterNot {
                case (incomingResIri: IRI, _: ResourceWithValueRdfData) =>
                    alreadyTraversed(incomingResIri) || flatResourcesWithValues(incomingResIri).isMainResource
            }

            // link value assertions that point to this resource
            val incomingLinkAssertions: RdfPropertyValues = referringResources.values.foldLeft(emptyRdfPropertyValues) {
                case (acc: RdfPropertyValues, assertions: ResourceWithValueRdfData) =>

                    val values: RdfPropertyValues = assertions.valuePropertyAssertions.flatMap {
                        case (propIri: SmartIri, values: Seq[ValueRdfData]) =>

                            // check if the property Iri already exists (there could be several instances of the same property)
                            if (acc.get(propIri).nonEmpty) {
                                // add values to property Iri (keeping the already existing values)
                                acc + (propIri -> (acc(propIri) ++ values).sortBy(_.subjectIri))
                            } else {
                                // prop Iri does not exists yet, add it
                                acc + (propIri -> values.sortBy(_.subjectIri))
                            }
                    }

                    values
            }

            if (incomingLinkAssertions.nonEmpty) {
                // create a virtual property representing an incoming link
                val incomingProps: (SmartIri, Seq[ValueRdfData]) = OntologyConstants.KnoraBase.HasIncomingLinkValue.toSmartIri -> incomingLinkAssertions.values.toSeq.flatten.map {
                    linkValue: ValueRdfData =>

                        // get the source of the link value (it points to the resource that is currently processed)
                        val sourceIri: IRI = linkValue.requireIriObject(OntologyConstants.Rdf.Subject.toSmartIri)
                        val source = Some(nestResources(resourceIri = sourceIri, alreadyTraversed = alreadyTraversed + resourceIri))

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

        val mainResourcesNested: Map[IRI, ResourceWithValueRdfData] = mainResourceIrisVisible.map {
            resourceIri =>
                val transformedResource = nestResources(resourceIri)
                resourceIri -> transformedResource
        }.toMap

        val forbiddenResourcesForHiddenMainResources: Map[IRI, ResourceWithValueRdfData] = mainResourceIrisNotVisible.map {
            resourceIri => resourceIri -> ForbiddenMainResourcePlaceholder
        }.toMap

        mainResourcesNested ++ forbiddenResourcesForHiddenMainResources
    }

    /**
     * Collect all mapping Iris referred to in the given value assertions.
     *
     * @param valuePropertyAssertions the given assertions (property -> value object).
     * @return a set of mapping Iris.
     */
    def getMappingIrisFromValuePropertyAssertions(valuePropertyAssertions: RdfPropertyValues)(implicit stringFormatter: StringFormatter): Set[IRI] = {
        valuePropertyAssertions.foldLeft(Set.empty[IRI]) {
            case (acc: Set[IRI], (_: SmartIri, valObjs: Seq[ValueRdfData])) =>
                val mappings: Seq[String] = valObjs.filter {
                    valObj: ValueRdfData =>
                        valObj.valueObjectClass == OntologyConstants.KnoraBase.TextValue.toSmartIri && valObj.assertions.get(OntologyConstants.KnoraBase.ValueHasMapping.toSmartIri).nonEmpty
                }.map {
                    textValObj: ValueRdfData => textValObj.requireIriObject(OntologyConstants.KnoraBase.ValueHasMapping.toSmartIri)
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
        val valueLanguageOption: Option[String] = valueObject.maybeStringObject(OntologyConstants.KnoraBase.ValueHasLanguage.toSmartIri)

        if (valueObject.standoff.nonEmpty) {
            // The query included a page of standoff markup. This is either because we've queried the text value
            // and got the first page of its standoff along with it, or because we're querying a subsequent page
            // of standoff for a text value.

            val mappingIri: Option[IRI] = valueObject.maybeIriObject(OntologyConstants.KnoraBase.ValueHasMapping.toSmartIri)
            val mappingAndXsltTransformation: Option[MappingAndXSLTransformation] = mappingIri.flatMap(definedMappingIri => mappings.get(definedMappingIri))

            for {
                standoff: Vector[StandoffTagV2] <- StandoffTagUtilV2.createStandoffTagsV2FromConstructResults(
                    standoffAssertions = valueObject.standoff,
                    responderManager = responderManager,
                    requestingUser = requestingUser
                )

                valueHasMaxStandoffStartIndex: Int = valueObject.requireIntObject(OntologyConstants.KnoraBase.ValueHasMaxStandoffStartIndex.toSmartIri)
                lastStartIndexQueried = standoff.last.startIndex

                // Should we get more the rest of the standoff for the same text value?
                standoffToReturn <- if (queryStandoff && lastStartIndexQueried < valueHasMaxStandoffStartIndex) {
                    // We're supposed to get all the standoff for the text value. Ask the standoff responder for the rest of it.
                    // Each page of markup will be also be processed by this method. The resulting pages will be
                    // concatenated together and returned in a GetStandoffResponseV2.

                    for {
                        standoffResponse <- (responderManager ? GetRemainingStandoffFromTextValueRequestV2(resourceIri = resourceIri, valueIri = valueObject.subjectIri, requestingUser = requestingUser)).mapTo[GetStandoffResponseV2]
                    } yield standoff ++ standoffResponse.standoff
                } else {
                    // We're not supposed to get any more standoff here, either because we have all of it already,
                    // or because we're just supposed to return one page.
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
            internalMimeType = valueObject.requireStringObject(OntologyConstants.KnoraBase.InternalMimeType.toSmartIri),
            internalFilename = valueObject.requireStringObject(OntologyConstants.KnoraBase.InternalFilename.toSmartIri),
            originalFilename = valueObject.maybeStringObject(OntologyConstants.KnoraBase.OriginalFilename.toSmartIri),
            originalMimeType = valueObject.maybeStringObject(OntologyConstants.KnoraBase.OriginalMimeType.toSmartIri)
        )

        valueType match {
            case OntologyConstants.KnoraBase.StillImageFileValue =>
                FastFuture.successful(StillImageFileValueContentV2(
                    ontologySchema = InternalSchema,
                    fileValue = fileValue,
                    dimX = valueObject.requireIntObject(OntologyConstants.KnoraBase.DimX.toSmartIri),
                    dimY = valueObject.requireIntObject(OntologyConstants.KnoraBase.DimY.toSmartIri),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.DocumentFileValue =>
                FastFuture.successful(DocumentFileValueContentV2(
                    ontologySchema = InternalSchema,
                    fileValue = fileValue,
                    pageCount = valueObject.requireIntObject(OntologyConstants.KnoraBase.PageCount.toSmartIri),
                    dimX = valueObject.maybeIntObject(OntologyConstants.KnoraBase.DimX.toSmartIri),
                    dimY = valueObject.maybeIntObject(OntologyConstants.KnoraBase.DimY.toSmartIri),
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
            valueObject.requireIriObject(OntologyConstants.Rdf.Subject.toSmartIri)
        } else {
            valueObject.requireIriObject(OntologyConstants.Rdf.Object.toSmartIri)
        }

        val linkValue = LinkValueContentV2(
            ontologySchema = InternalSchema,
            referredResourceIri = referredResourceIri,
            isIncomingLink = valueObject.isIncomingLink,
            nestedResource = None,
            comment = valueCommentOption
        )

        // Is there a nested resource in the link value?
        valueObject.nestedResource match {
            case Some(nestedResourceAssertions: ResourceWithValueRdfData) =>
                // Yes. Is the nested resource a placeholder for the forbidden resource?
                if (nestedResourceAssertions.subjectIri == StringFormatter.ForbiddenResourceIri) {
                    // Yes. Replace it with the forbidden resource.
                    Future {
                        linkValue.copy(
                            nestedResource = Some(stringFormatter.forbiddenResource)
                        )
                    }
                } else {
                    // No. Construct a ReadResourceV2 representing the nested resource.
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
                        nestedResource = Some(nestedResource)
                    )
                }

            case None =>
                // There is no nested resource.
                FastFuture.successful(linkValue)
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
        val valueObjectValueHasString: Option[String] = valueObject.maybeStringObject(OntologyConstants.KnoraBase.ValueHasString.toSmartIri)

        // every knora-base:value (any of its subclasses) may have a comment
        val valueCommentOption: Option[String] = valueObject.maybeStringObject(OntologyConstants.KnoraBase.ValueHasComment.toSmartIri)

        val valueTypeStr: IRI = valueObject.valueObjectClass.toString

        valueTypeStr match {
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
                val startPrecisionStr = valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasStartPrecision.toSmartIri)
                val endPrecisionStr = valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasEndPrecision.toSmartIri)
                val calendarNameStr = valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasCalendar.toSmartIri)

                FastFuture.successful(DateValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasStartJDN = valueObject.requireIntObject(OntologyConstants.KnoraBase.ValueHasStartJDN.toSmartIri),
                    valueHasEndJDN = valueObject.requireIntObject(OntologyConstants.KnoraBase.ValueHasEndJDN.toSmartIri),
                    valueHasStartPrecision = DatePrecisionV2.parse(startPrecisionStr, throw InconsistentTriplestoreDataException(s"Invalid date precision: $startPrecisionStr")),
                    valueHasEndPrecision = DatePrecisionV2.parse(endPrecisionStr, throw InconsistentTriplestoreDataException(s"Invalid date precision: $endPrecisionStr")),
                    valueHasCalendar = CalendarNameV2.parse(calendarNameStr, throw InconsistentTriplestoreDataException(s"Invalid calendar name: $calendarNameStr")),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.IntValue =>
                FastFuture.successful(IntegerValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasInteger = valueObject.requireIntObject(OntologyConstants.KnoraBase.ValueHasInteger.toSmartIri),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.DecimalValue =>
                FastFuture.successful(DecimalValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasDecimal = valueObject.requireDecimalObject(OntologyConstants.KnoraBase.ValueHasDecimal.toSmartIri),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.BooleanValue =>
                FastFuture.successful(BooleanValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasBoolean = valueObject.requireBooleanObject(OntologyConstants.KnoraBase.ValueHasBoolean.toSmartIri),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.UriValue =>
                FastFuture.successful(UriValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasUri = valueObject.requireIriObject(OntologyConstants.KnoraBase.ValueHasUri.toSmartIri),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.ColorValue =>
                FastFuture.successful(ColorValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasColor = valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasColor.toSmartIri),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.GeomValue =>
                FastFuture.successful(GeomValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasGeometry = valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasGeometry.toSmartIri),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.GeonameValue =>
                FastFuture.successful(GeonameValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasGeonameCode = valueObject.requireStringObject(OntologyConstants.KnoraBase.ValueHasGeonameCode.toSmartIri),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.ListValue =>

                val listNodeIri: IRI = valueObject.requireIriObject(OntologyConstants.KnoraBase.ValueHasListNode.toSmartIri)

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
                    valueHasIntervalStart = valueObject.requireDecimalObject(OntologyConstants.KnoraBase.ValueHasIntervalStart.toSmartIri),
                    valueHasIntervalEnd = valueObject.requireDecimalObject(OntologyConstants.KnoraBase.ValueHasIntervalEnd.toSmartIri),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.TimeValue =>
                FastFuture.successful(TimeValueContentV2(
                    ontologySchema = InternalSchema,
                    valueHasTimeStamp = valueObject.requireDateTimeObject(OntologyConstants.KnoraBase.ValueHasTimeStamp.toSmartIri),
                    comment = valueCommentOption
                ))

            case OntologyConstants.KnoraBase.LinkValue =>
                makeLinkValueContentV2(
                    valueObject = valueObject,
                    valueObjectValueHasString = valueObjectValueHasString.getOrElse(throw AssertionException(s"Value <${valueObject.subjectIri}> has no knora-base:valueHasString")),
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
                    valueObjectValueHasString = valueObjectValueHasString.getOrElse(throw AssertionException(s"Value <${valueObject.subjectIri}> has no knora-base:valueHasString")),
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
        def getDeletionInfo(rdfData: RdfData): Option[DeletionInfo] = {
            val isDeleted: Boolean = rdfData.requireBooleanObject(OntologyConstants.KnoraBase.IsDeleted.toSmartIri)

            if (isDeleted) {
                val deleteDate = rdfData.requireDateTimeObject(OntologyConstants.KnoraBase.DeleteDate.toSmartIri)
                val maybeDeleteComment = rdfData.maybeStringObject(OntologyConstants.KnoraBase.DeleteComment.toSmartIri)

                Some(
                    DeletionInfo(
                        deleteDate = deleteDate,
                        maybeDeleteComment = maybeDeleteComment
                    )
                )
            } else {
                None
            }
        }

        if (resourceWithValueRdfData.subjectIri == StringFormatter.ForbiddenResourceIri) {
            FastFuture.successful(stringFormatter.forbiddenResource)
        } else {
            val resourceLabel: String = resourceWithValueRdfData.requireStringObject(OntologyConstants.Rdfs.Label.toSmartIri)
            val resourceClassStr: IRI = resourceWithValueRdfData.requireIriObject(OntologyConstants.Rdf.Type.toSmartIri)
            val resourceClass = resourceClassStr.toSmartIriWithErr(throw InconsistentTriplestoreDataException(s"Couldn't parse rdf:type of resource <$resourceIri>: <$resourceClassStr>"))
            val resourceAttachedToUser: IRI = resourceWithValueRdfData.requireIriObject(OntologyConstants.KnoraBase.AttachedToUser.toSmartIri)
            val resourceAttachedToProject: IRI = resourceWithValueRdfData.requireIriObject(OntologyConstants.KnoraBase.AttachedToProject.toSmartIri)
            val resourcePermissions: String = resourceWithValueRdfData.requireStringObject(OntologyConstants.KnoraBase.HasPermissions.toSmartIri)
            val resourceCreationDate: Instant = resourceWithValueRdfData.requireDateTimeObject(OntologyConstants.KnoraBase.CreationDate.toSmartIri)
            val resourceLastModificationDate: Option[Instant] = resourceWithValueRdfData.maybeDateTimeObject(OntologyConstants.KnoraBase.LastModificationDate.toSmartIri)
            val resourceDeletionInfo = getDeletionInfo(resourceWithValueRdfData)

            // get the resource's values
            val valueObjectFutures: Map[SmartIri, Seq[Future[ReadValueV2]]] = resourceWithValueRdfData.valuePropertyAssertions.map {
                case (property: SmartIri, valObjs: Seq[ValueRdfData]) =>
                    val readValues: Seq[Future[ReadValueV2]] = valObjs.sortBy(_.subjectIri).sortBy { // order values by value IRI, then by knora-base:valueHasOrder
                        valObj: ValueRdfData =>
                            // set order to zero if not given
                            valObj.maybeIntObject(OntologyConstants.KnoraBase.ValueHasOrder.toSmartIri).getOrElse(0)
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

                                attachedToUser = valObj.requireIriObject(OntologyConstants.KnoraBase.AttachedToUser.toSmartIri)
                                permissions = valObj.requireStringObject(OntologyConstants.KnoraBase.HasPermissions.toSmartIri)
                                valueCreationDate: Instant = valObj.requireDateTimeObject(OntologyConstants.KnoraBase.ValueCreationDate.toSmartIri)
                                valueDeletionInfo = getDeletionInfo(valObj)
                                valueHasUUID: UUID = stringFormatter.decodeUuid(valObj.requireStringObject(OntologyConstants.KnoraBase.ValueHasUUID.toSmartIri))
                                previousValueIri: Option[IRI] = valObj.maybeIriObject(OntologyConstants.KnoraBase.PreviousValue.toSmartIri)

                            } yield valueContent match {
                                case linkValueContentV2: LinkValueContentV2 =>
                                    val valueHasRefCount: Int = valObj.requireIntObject(OntologyConstants.KnoraBase.ValueHasRefCount.toSmartIri)

                                    ReadLinkValueV2(
                                        valueIri = valObj.subjectIri,
                                        attachedToUser = attachedToUser,
                                        permissions = permissions,
                                        userPermission = valObj.userPermission,
                                        valueCreationDate = valueCreationDate,
                                        valueHasUUID = valueHasUUID,
                                        valueContent = linkValueContentV2,
                                        valueHasRefCount = valueHasRefCount,
                                        previousValueIri = previousValueIri,
                                        deletionInfo = valueDeletionInfo
                                    )

                                case textValueContentV2: TextValueContentV2 =>
                                    val maybeValueHasMaxStandoffStartIndex: Option[Int] = valObj.maybeIntObject(OntologyConstants.KnoraBase.ValueHasMaxStandoffStartIndex.toSmartIri)

                                    ReadTextValueV2(
                                        valueIri = valObj.subjectIri,
                                        attachedToUser = attachedToUser,
                                        permissions = permissions,
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
                                        valueIri = valObj.subjectIri,
                                        attachedToUser = attachedToUser,
                                        permissions = permissions,
                                        userPermission = valObj.userPermission,
                                        valueCreationDate = valueCreationDate,
                                        valueHasUUID = valueHasUUID,
                                        valueContent = otherValueContentV2,
                                        previousValueIri = previousValueIri,
                                        deletionInfo = valueDeletionInfo
                                    )
                            }
                    }

                    property -> readValues
            }

            for {
                projectResponse: ProjectGetResponseADM <- (responderManager ? ProjectGetRequestADM(ProjectIdentifierADM(maybeIri = Some(resourceAttachedToProject)), requestingUser = requestingUser)).mapTo[ProjectGetResponseADM]
                valueObjects <- ActorUtil.sequenceSeqFuturesInMap(valueObjectFutures)
            } yield ReadResourceV2(
                resourceIri = resourceIri,
                resourceClassIri = resourceClass,
                label = resourceLabel,
                attachedToUser = resourceAttachedToUser,
                projectADM = projectResponse.project,
                permissions = resourcePermissions,
                userPermission = resourceWithValueRdfData.userPermission.get,
                values = valueObjects,
                creationDate = resourceCreationDate,
                lastModificationDate = resourceLastModificationDate,
                versionDate = versionDate,
                deletionInfo = resourceDeletionInfo
            )
        }
    }

    /**
     * Creates an API response.
     *
     * @param queryResults      the query results.
     * @param orderByResourceIri the order in which the resources should be returned.
     * @param mappings           the mappings to convert standoff to XML, if any.
     * @param queryStandoff      if `true`, make separate queries to get the standoff for text values.
     * @param versionDate      if defined, represents the requested time in the the resources' version history.
     * @param responderManager   the Knora responder manager.
     * @param targetSchema       the schema of response.
     * @param settings           the application's settings.
     * @param requestingUser     the user making the request.
     * @return a collection of [[ReadResourceV2]] representing the search results.
     */
    def createApiResponse(queryResults: RdfResources,
                          orderByResourceIri: Seq[IRI],
                          mappings: Map[IRI, MappingAndXSLTransformation] = Map.empty[IRI, MappingAndXSLTransformation],
                          queryStandoff: Boolean,
                          versionDate: Option[Instant],
                          responderManager: ActorRef,
                          targetSchema: ApiV2Schema,
                          settings: SettingsImpl,
                          requestingUser: UserADM)(implicit stringFormatter: StringFormatter, timeout: Timeout, executionContext: ExecutionContext): Future[Vector[ReadResourceV2]] = {

        // iterate over orderByResourceIris and construct the response in the correct order
        val readResourceFutures: Vector[Future[ReadResourceV2]] = orderByResourceIri.map {
            resourceIri: IRI =>
                val resource: ResourceWithValueRdfData = queryResults(resourceIri)

                // If the user doesn't have permission to see the resource, its IRI will be mapped
                // to ForbiddenMainResourcePlaceholder. Therefore use the IRI from the ResourceWithValueRdfData,
                // not the actual resource IRI.
                constructReadResourceV2(
                    resourceIri = resource.subjectIri,
                    resourceWithValueRdfData = resource,
                    mappings = mappings,
                    queryStandoff = queryStandoff,
                    versionDate = None,
                    responderManager = responderManager,
                    targetSchema = targetSchema,
                    settings = settings,
                    requestingUser = requestingUser
                )

        }.toVector

        Future.sequence(readResourceFutures)
    }
}