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

import java.time.Instant

import akka.pattern._
import akka.http.scaladsl.util.FastFuture
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.projectmessages.ProjectsNamedGraphGetV1
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.OwlCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.util.ActorUtil.{future2Message, handleUnexpectedMessage}
import org.knora.webapi.util.{CacheUtil, ErrorHandlingMap, KnoraIdUtil}
import org.knora.webapi._
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.util.StringFormatter.OntologyID

import scala.concurrent.Future

class OntologyResponderV2 extends Responder {

    private val knoraIdUtil = new KnoraIdUtil
    private val valueFactory = SimpleValueFactory.getInstance()

    /**
      * The name of the ontology cache.
      */
    private val OntologyCacheName = "ontologyCache"

    /**
      * The cache key under which cached ontology data is stored.
      */
    private val OntologyCacheKey = "ontologyCacheData"

    /**
      * A container for all the cached ontology data.
      *
      * @param namedGraphs                         the set of available named graphs.
      * @param namedGraphClasses                   a map of named graph IRIs to sets of non-standoff class IRIs defined in each named graph.
      * @param namedGraphProperties                a map of property IRIs to sets of non-standoff property IRIs defined in each named graph.
      * @param classDefs                           a map of class IRIs to definitions.
      * @param resourceAndValueSubClassOfRelations a map of IRIs of resource and value classes to sets of the IRIs of their base classes.
      * @param resourceSuperClassOfRelations       a map of IRIs of resource classes to sets of the IRIs of their subclasses.
      * @param propertyDefs                        a map of property IRIs to property definitions.
      * @param namedGraphStandoffClasses           a map of named graph IRIs to sets of standoff class IRIs defined in each named graph.
      * @param namedGraphStandoffProperties        a map of property IRIs to sets of standoff property IRIs defined in each named graph.
      * @param standoffClassDefs                   a map of standoff class IRIs to definitions.
      * @param standoffPropertyDefs                a map of property IRIs to property definitions.
      * @param standoffClassDefsWithDataType       a map of standoff class IRIs to class definitions, including only standoff datatype tags.
      */
    case class OntologyCacheData(namedGraphs: Set[IRI],
                                 namedGraphClasses: Map[IRI, Set[IRI]],
                                 namedGraphProperties: Map[IRI, Set[IRI]],
                                 classDefs: Map[IRI, ReadClassInfoV2],
                                 resourceAndValueSubClassOfRelations: Map[IRI, Set[IRI]],
                                 resourceSuperClassOfRelations: Map[IRI, Set[IRI]],
                                 propertyDefs: Map[IRI, PropertyEntityInfoV2],
                                 namedGraphStandoffClasses: Map[IRI, Set[IRI]],
                                 namedGraphStandoffProperties: Map[IRI, Set[IRI]],
                                 standoffClassDefs: Map[IRI, ReadClassInfoV2],
                                 standoffPropertyDefs: Map[IRI, PropertyEntityInfoV2],
                                 standoffClassDefsWithDataType: Map[IRI, ReadClassInfoV2])

    def receive = {
        case LoadOntologiesRequestV2(userProfile) => future2Message(sender(), loadOntologies(userProfile), log)
        case EntityInfoGetRequestV2(classIris, propertyIris, userProfile) => future2Message(sender(), getEntityInfoResponseV2(classIris, propertyIris, userProfile), log)
        case StandoffEntityInfoGetRequestV2(standoffClassIris, standoffPropertyIris, userProfile) => future2Message(sender(), getStandoffEntityInfoResponseV2(standoffClassIris, standoffPropertyIris, userProfile), log)
        case StandoffClassesWithDataTypeGetRequestV2(userProfile) => future2Message(sender(), getStandoffStandoffClassesWithDataTypeV2(userProfile), log)
        case StandoffAllPropertyEntitiesGetRequestV2(userProfile) => future2Message(sender(), getAllStandoffPropertyEntitiesV2(userProfile), log)
        case CheckSubClassRequestV2(subClassIri, superClassIri, userProfile) => future2Message(sender(), checkSubClassV2(subClassIri, superClassIri, userProfile), log)
        case SubClassesGetRequestV2(resourceClassIri, userProfile) => future2Message(sender(), getSubClassesV2(resourceClassIri, userProfile), log)
        case NamedGraphEntitiesRequestV2(namedGraphIri, userProfile) => future2Message(sender(), getNamedGraphEntityInfoV2ForNamedGraphV2(namedGraphIri, userProfile), log)
        case NamedGraphEntitiesGetRequestV2(namedGraphIris, responseSchema, allLanguages, userProfile) => future2Message(sender(), getEntitiesForNamedGraphV2(namedGraphIris, responseSchema, allLanguages, userProfile), log)
        case ClassesGetRequestV2(resourceClassIris, responseSchema, allLanguages, userProfile) => future2Message(sender(), getClassDefinitionsV2(resourceClassIris, responseSchema, allLanguages, userProfile), log)
        case PropertyEntitiesGetRequestV2(propertyIris, allLanguages, userProfile) => future2Message(sender(), getPropertyDefinitionsV2(propertyIris, allLanguages, userProfile), log)
        case NamedGraphsGetRequestV2(userProfile) => future2Message(sender(), getNamedGraphsV2(userProfile), log)
        case createOntologyRequest: CreateOntologyRequestV2 => future2Message(sender(), createOntology(createOntologyRequest), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Loads and caches all ontology information.
      *
      * @param userProfile the profile of the user making the request.
      * @return a [[LoadOntologiesResponse]].
      */
    private def loadOntologies(userProfile: UserProfileV1): Future[SuccessResponseV2] = {
        // TODO: determine whether the user is authorised to reload the ontologies (depends on pull request #168).

        /**
          * A temporary container for information about an OWL cardinality and the property it applies to.
          *
          * @param propertyIri      the IRI of the property that the cardinality applies to.
          * @param cardinalityIri   the IRI of the cardinality (e.g. `http://www.w3.org/2002/07/owl#minCardinality`).
          * @param cardinalityValue the value of the cardinality (in Knora, this is always 0 or 1).
          * @param isLinkProp       `true` if the property is a subproperty of `knora-base:hasLinkTo`.
          * @param isLinkValueProp  `true` if the property is a subproperty of `knora-base:hasLinkToValue`.
          * @param isFileValueProp  `true` if the property is a subproperty of `knora-base:hasFileValue`.
          */
        case class OwlCardinalityOnProperty(propertyIri: IRI,
                                            cardinalityIri: IRI,
                                            cardinalityValue: Int,
                                            isLinkProp: Boolean = false,
                                            isLinkValueProp: Boolean = false,
                                            isFileValueProp: Boolean = false)

        /**
          * Gets the IRI of the ontology that an entity belongs to. This is assumed to be the namespace
          * part of the IRI of the entity (i.e. everything up to and including the first `#` character or the last `/`
          * character), minus the trailing `#` or `/`.
          *
          * @param entityIri the entity IRI.
          * @return the IRI of the ontology that the entity belongs to.
          */
        def getOntologyIri(entityIri: IRI): IRI = {
            valueFactory.createIRI(entityIri).getNamespace.stripSuffix("#").stripSuffix("/")
        }

        /**
          * Recursively walks up an entity hierarchy, collecting the IRIs of all base entities.
          *
          * @param iri             the IRI of an entity.
          * @param directRelations a map of entities to their direct base entities.
          * @return all the base entities of the specified entity.
          */
        def getAllBaseDefs(iri: IRI, directRelations: Map[IRI, Set[IRI]]): Set[IRI] = {
            directRelations.get(iri) match {
                case Some(baseDefs) =>
                    baseDefs ++ baseDefs.flatMap(baseDef => getAllBaseDefs(baseDef, directRelations))

                case None => Set.empty[IRI]
            }
        }

        /**
          * Given a resource class, recursively adds its inherited cardinalities to the cardinalities it defines
          * directly. A cardinality for a subproperty in a subclass overrides a cardinality for a base property in
          * a base class.
          *
          * @param resourceClassIri                 the IRI of the resource class whose properties are to be computed.
          * @param directSubClassOfRelations        a map of the direct `rdfs:subClassOf` relations defined on each resource class.
          * @param allSubPropertyOfRelations        a map in which each property IRI points to the full set of its base properties.
          * @param directResourceClassCardinalities a map of the cardinalities defined directly on each resource class.
          * @return a map in which each key is the IRI of a property that has a cardinality in the resource class (or that it inherits
          *         from its base classes), and each value is the cardinality on the property.
          */
        def inheritCardinalities(resourceClassIri: IRI,
                                 directSubClassOfRelations: Map[IRI, Set[IRI]],
                                 allSubPropertyOfRelations: Map[IRI, Set[IRI]],
                                 directResourceClassCardinalities: Map[IRI, Map[IRI, OwlCardinalityOnProperty]]): Map[IRI, OwlCardinalityOnProperty] = {
            // Recursively get properties that are available to inherit from base classes. If we have no information about
            // a class, that could mean that it isn't a subclass of knora-base:Resource (e.g. it's something like
            // foaf:Person), in which case we assume that it has no base classes.
            val cardinalitiesAvailableToInherit: Map[IRI, OwlCardinalityOnProperty] = directSubClassOfRelations.getOrElse(resourceClassIri, Set.empty[IRI]).foldLeft(Map.empty[IRI, OwlCardinalityOnProperty]) {
                case (acc, baseClass) =>
                    acc ++ inheritCardinalities(
                        resourceClassIri = baseClass,
                        directSubClassOfRelations = directSubClassOfRelations,
                        allSubPropertyOfRelations = allSubPropertyOfRelations,
                        directResourceClassCardinalities = directResourceClassCardinalities
                    )
            }

            // Get the properties that have cardinalities defined directly on this class. Again, if we have no information
            // about a class, we assume that it has no cardinalities.
            val thisClassCardinalities: Map[IRI, OwlCardinalityOnProperty] = directResourceClassCardinalities.getOrElse(resourceClassIri, Map.empty[IRI, OwlCardinalityOnProperty])

            // From the properties that are available to inherit, filter out the ones that are overridden by properties
            // with cardinalities defined directly on this class.
            val inheritedCardinalities: Map[IRI, OwlCardinalityOnProperty] = cardinalitiesAvailableToInherit.filterNot {
                case (baseClassProp, baseClassCardinality) => thisClassCardinalities.exists {
                    case (thisClassProp, cardinality) =>
                        allSubPropertyOfRelations.get(thisClassProp) match {
                            case Some(baseProps) => baseProps.contains(baseClassProp)
                            case None => thisClassProp == baseClassProp
                        }
                }
            }

            // Add the inherited properties to the ones with directly defined cardinalities.
            thisClassCardinalities ++ inheritedCardinalities
        }

        for {
            // Get all resource class definitions.
            resourceDefsSparql <- Future(queries.sparql.v2.txt.getResourceClassDefinitions(triplestore = settings.triplestoreType).toString())
            resourceDefsResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(resourceDefsSparql)).mapTo[SparqlSelectResponse]
            resourceDefsRows: Seq[VariableResultsRow] = resourceDefsResponse.results.bindings

            // Get the value class hierarchy.
            valueClassesSparql = queries.sparql.v2.txt.getValueClassHierarchy(triplestore = settings.triplestoreType).toString()
            valueClassesResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(valueClassesSparql)).mapTo[SparqlSelectResponse]
            valueClassesRows: Seq[VariableResultsRow] = valueClassesResponse.results.bindings

            // Get all property definitions.
            propertyDefsSparql = queries.sparql.v2.txt.getPropertyDefinitions(triplestore = settings.triplestoreType).toString()
            propertyDefsResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(propertyDefsSparql)).mapTo[SparqlSelectResponse]
            propertyDefsRows: Seq[VariableResultsRow] = propertyDefsResponse.results.bindings

            // Make a map of IRIs of named graphs to IRIs of resource classes defined in each one, excluding resource
            // classes that can't be instantiated directly.
            graphClassMap: Map[IRI, Set[IRI]] = resourceDefsRows.groupBy(_.rowMap("graph")).map {
                case (graphIri: IRI, graphRows: Seq[VariableResultsRow]) =>
                    graphIri -> (graphRows.map(_.rowMap("resourceClass")).toSet -- OntologyConstants.KnoraBase.AbstractResourceClasses)
            } + (OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri -> KnoraApiV2Simple.Classes.keySet) +
                (OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri -> KnoraApiV2WithValueObjects.Classes.keySet)

            // Make a map of IRIs of named graphs to IRIs of properties defined in each one, excluding knora-base:resourceProperty,
            // which is never used directly.
            graphPropMap: Map[IRI, Set[IRI]] = propertyDefsRows.groupBy(_.rowMap("graph")).map {
                case (graphIri, graphRows) =>
                    graphIri -> (graphRows.map(_.rowMap("prop")).toSet - OntologyConstants.KnoraBase.ResourceProperty)
            } + (OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri -> KnoraApiV2Simple.Properties.keySet) +
                (OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri -> KnoraApiV2WithValueObjects.Properties.keySet)

            // Group the rows representing resource class definitions by resource class IRI. This needs to include abstract resource classes such as
            // knora-base:Resource, so cardinalities can be inherited from them.
            resourceDefsGrouped: Map[IRI, Seq[VariableResultsRow]] = resourceDefsRows.groupBy(_.rowMap("resourceClass"))
            resourceClassIris = resourceDefsGrouped.keySet

            // Group the rows representing property definitions by property IRI, excluding knora-base:resourceProperty, which is never used directly.
            propertyDefsGrouped: Map[IRI, Seq[VariableResultsRow]] = propertyDefsRows.groupBy(_.rowMap("prop")) - OntologyConstants.KnoraBase.ResourceProperty
            propertyIris = propertyDefsGrouped.keySet

            // Group the rows representing value class relations by value class IRI.
            valueClassesGrouped: Map[IRI, Seq[VariableResultsRow]] = valueClassesRows.groupBy(_.rowMap("valueClass"))

            // Make a map of resource class IRIs to their immediate base classes.
            directResourceSubClassOfRelations: Map[IRI, Set[IRI]] = resourceDefsGrouped.map {
                case (resourceClassIri, rows) =>
                    val baseClasses = rows.filter(_.rowMap.get("resourceClassPred").contains(OntologyConstants.Rdfs.SubClassOf)).map(_.rowMap("resourceClassObj")).toSet
                    (resourceClassIri, baseClasses)
            }

            // Make a map of property IRIs to their immediate base properties.
            directSubPropertyOfRelations: Map[IRI, Set[IRI]] = propertyDefsGrouped.map {
                case (propertyIri, rows) =>
                    val baseProperties = rows.filter(_.rowMap.get("propPred").contains(OntologyConstants.Rdfs.SubPropertyOf)).map(_.rowMap("propObj")).toSet
                    (propertyIri, baseProperties)
            }

            // Make a map in which each resource class IRI points to the full set of its base classes. A class is also
            // a subclass of itself.
            allResourceSubClassOfRelations: Map[IRI, Set[IRI]] = resourceClassIris.map {
                resourceClassIri => (resourceClassIri, getAllBaseDefs(resourceClassIri, directResourceSubClassOfRelations) + resourceClassIri)
            }.toMap

            // Make a map in which each resource class IRI points to the full set of its subclasses. A class is also
            // a subclass of itself.
            allResourceSuperClassOfRelations: Map[IRI, Set[IRI]] = allResourceSubClassOfRelations.toVector.flatMap {
                case (subClass: IRI, baseClasses: Set[IRI]) =>
                    baseClasses.toVector.map {
                        baseClass => baseClass -> subClass
                    }
            }.groupBy(_._1).map {
                case (baseClass: IRI, baseClassAndSubClasses: Vector[(IRI, IRI)]) =>
                    baseClass -> baseClassAndSubClasses.map(_._2).toSet
            }

            // Make a map in which each property IRI points to the full set of its base properties. A property is also
            // a subproperty of itself.
            allSubPropertyOfRelations: Map[IRI, Set[IRI]] = propertyIris.map {
                propertyIri => (propertyIri, getAllBaseDefs(propertyIri, directSubPropertyOfRelations) + propertyIri)
            }.toMap

            // Make a map in which each value class IRI points to the full set of its base classes (excluding the ones
            // whose names end in "Base", since they aren't used directly). A class is also a subclass of itself (this
            // is handled by the SPARQL query).
            allValueSubClassOfRelations: Map[IRI, Set[IRI]] = valueClassesGrouped.map {
                case (valueClassIri, baseClassRows) =>
                    valueClassIri -> baseClassRows.map(_.rowMap("baseClass")).filterNot(_.endsWith("Base")).toSet
            }

            // Make a set of all subproperties of knora-base:hasLinkTo.
            linkProps: Set[IRI] = propertyIris.filter(prop => allSubPropertyOfRelations(prop).contains(OntologyConstants.KnoraBase.HasLinkTo))

            // Make a set of all subproperties of knora-base:hasLinkToValue.
            linkValueProps: Set[IRI] = propertyIris.filter(prop => allSubPropertyOfRelations(prop).contains(OntologyConstants.KnoraBase.HasLinkToValue))

            // Make a set of all subproperties of knora-base:hasFileValue.
            fileValueProps: Set[IRI] = propertyIris.filter(prop => allSubPropertyOfRelations(prop).contains(OntologyConstants.KnoraBase.HasFileValue))

            // Make a map of the cardinalities defined directly on each resource class. Each resource class IRI points to a map of
            // property IRIs to OwlCardinality objects.
            directResourceClassCardinalities: Map[IRI, Map[IRI, OwlCardinalityOnProperty]] = resourceDefsGrouped.map {
                case (resourceClassIri, rows) =>
                    val resourceClassCardinalities: Map[IRI, OwlCardinalityOnProperty] = rows.filter(_.rowMap.contains("cardinalityProp")).map {
                        cardinalityRow =>
                            val cardinalityRowMap = cardinalityRow.rowMap
                            val propertyIri = cardinalityRowMap("cardinalityProp")

                            val owlCardinality = OwlCardinalityOnProperty(
                                propertyIri = propertyIri,
                                cardinalityIri = cardinalityRowMap("cardinality"),
                                cardinalityValue = cardinalityRowMap("cardinalityVal").toInt,
                                isLinkProp = linkProps.contains(propertyIri),
                                isLinkValueProp = linkValueProps.contains(propertyIri),
                                isFileValueProp = fileValueProps.contains(propertyIri)
                            )

                            propertyIri -> owlCardinality
                    }.toMap

                    resourceClassIri -> resourceClassCardinalities
            }

            // Allow each resource class to inherit cardinalities from its base classes.
            resourceCardinalitiesWithInheritance: Map[IRI, Set[OwlCardinalityOnProperty]] = resourceClassIris.map {
                resourceClassIri =>
                    val resourceClassCardinalities: Set[OwlCardinalityOnProperty] = inheritCardinalities(
                        resourceClassIri = resourceClassIri,
                        directSubClassOfRelations = directResourceSubClassOfRelations,
                        allSubPropertyOfRelations = allSubPropertyOfRelations,
                        directResourceClassCardinalities = directResourceClassCardinalities
                    ).values.toSet

                    resourceClassIri -> resourceClassCardinalities
            }.toMap

            // Now that we've done cardinality inheritance, remove the resource class definitions that can't be
            // instantiated directly.
            concreteResourceDefsGrouped = resourceDefsGrouped -- OntologyConstants.KnoraBase.AbstractResourceClasses

            // TODO: distinguish between inherited and non-inherited cardinalities.

            // Construct a ReadClassInfoV2 for each resource class.
            resourceEntityInfos: Map[IRI, ReadClassInfoV2] = concreteResourceDefsGrouped.map {
                case (resourceClassIri, resourceClassRows) =>
                    val ontologyIri = getOntologyIri(resourceClassIri)

                    // Group the rows for each resource class by predicate IRI.
                    val groupedByPredicate: Map[IRI, Seq[VariableResultsRow]] = resourceClassRows.filter(_.rowMap.contains("resourceClassPred")).groupBy(_.rowMap("resourceClassPred")) - OntologyConstants.Rdfs.SubClassOf

                    val predicates: Map[IRI, PredicateInfoV2] = groupedByPredicate.map {
                        case (predicateIri, predicateRows) =>
                            val (predicateRowsWithLang, predicateRowsWithoutLang) = predicateRows.partition(_.rowMap.contains("resourceClassObjLang"))
                            val objects = predicateRowsWithoutLang.map(_.rowMap("resourceClassObj")).toSet
                            val objectsWithLang = predicateRowsWithLang.map {
                                predicateRow => predicateRow.rowMap("resourceClassObjLang") -> predicateRow.rowMap("resourceClassObj")
                            }.toMap

                            predicateIri -> PredicateInfoV2(
                                predicateIri = predicateIri,
                                ontologyIri = getOntologyIri(resourceClassIri),
                                objects = objects,
                                objectsWithLang = objectsWithLang
                            )
                    }

                    // Get the OWL cardinalities for the class.
                    val owlCardinalities = resourceCardinalitiesWithInheritance(resourceClassIri)

                    // Identify the link properties, like value properties, and file value properties in the cardinalities.
                    val linkProps = owlCardinalities.filter(_.isLinkProp).map(_.propertyIri)
                    val linkValueProps = owlCardinalities.filter(_.isLinkValueProp).map(_.propertyIri)
                    val fileValueProps = owlCardinalities.filter(_.isFileValueProp).map(_.propertyIri)

                    // Make sure there is a link value property for each link property.
                    val missingLinkValueProps = linkProps.map(linkProp => knoraIdUtil.linkPropertyIriToLinkValuePropertyIri(linkProp)) -- linkValueProps
                    if (missingLinkValueProps.nonEmpty) {
                        throw InconsistentTriplestoreDataException(s"Resource class $resourceClassIri has cardinalities for one or more link properties without corresponding link value properties. The missing link value property or properties: ${missingLinkValueProps.mkString(", ")}")
                    }

                    // Make sure there is a link property for each link value property.
                    val missingLinkProps = linkValueProps.map(linkValueProp => knoraIdUtil.linkValuePropertyIri2LinkPropertyIri(linkValueProp)) -- linkProps
                    if (missingLinkProps.nonEmpty) {
                        throw InconsistentTriplestoreDataException(s"Resource class $resourceClassIri has cardinalities for one or more link value properties without corresponding link properties. The missing link property or properties: ${missingLinkProps.mkString(", ")}")
                    }

                    val resourceEntityInfo = ReadClassInfoV2(
                        classInfoContent = ClassInfoContentV2(
                            classIri = resourceClassIri,
                            ontologyIri = getOntologyIri(resourceClassIri),
                            predicates = new ErrorHandlingMap(predicates, { key: IRI => s"Predicate $key not found for resource class $resourceClassIri" }),
                            cardinalities = owlCardinalities.map {
                                owlCardinality =>
                                    // Convert the OWL cardinality to a Knora Cardinality enum value.
                                    owlCardinality.propertyIri -> Cardinality.owlCardinality2KnoraCardinality(
                                        propertyIri = owlCardinality.propertyIri,
                                        OwlCardinalityInfo(
                                            owlCardinalityIri = owlCardinality.cardinalityIri,
                                            owlCardinalityValue = owlCardinality.cardinalityValue
                                        )
                                    )
                            }.toMap,
                            subClassOf = directResourceSubClassOfRelations.getOrElse(resourceClassIri, Set.empty[IRI]),
                            ontologySchema = InternalSchema
                        ),
                        canBeInstantiated = ontologyIri != OntologyConstants.KnoraBase.KnoraBaseOntologyIri, // Any resource class defined in a project-specific ontology can be instantiated.
                        linkProperties = linkProps,
                        linkValueProperties = linkValueProps,
                        fileValueProperties = fileValueProps
                    )

                    resourceClassIri -> resourceEntityInfo
            }

            // Construct a PropertyEntityInfoV2 for each property definition, not taking inheritance into account.
            propertyEntityInfos: Map[IRI, PropertyEntityInfoV2] = propertyDefsGrouped.map {
                case (propertyIri, propertyRows) =>
                    val ontologyIri = getOntologyIri(propertyIri)

                    // Group the rows for each property by predicate IRI.
                    val groupedByPredicate: Map[IRI, Seq[VariableResultsRow]] = propertyRows.groupBy(_.rowMap("propPred")) - OntologyConstants.Rdfs.SubPropertyOf

                    val predicates: Map[IRI, PredicateInfoV2] = groupedByPredicate.map {
                        case (predicateIri, predicateRows) =>
                            val (predicateRowsWithLang, predicateRowsWithoutLang) = predicateRows.partition(_.rowMap.contains("propObjLang"))
                            val objects = predicateRowsWithoutLang.map(_.rowMap("propObj")).toSet
                            val objectsWithLang = predicateRowsWithLang.map {
                                predicateRow => predicateRow.rowMap("propObjLang") -> predicateRow.rowMap("propObj")
                            }.toMap

                            predicateIri -> PredicateInfoV2(
                                predicateIri = predicateIri,
                                ontologyIri = ontologyIri,
                                objects = objects,
                                objectsWithLang = objectsWithLang
                            )
                    }

                    val propertyEntityInfo = PropertyEntityInfoV2(
                        propertyIri = propertyIri,
                        ontologyIri = ontologyIri,
                        isEditable = ontologyIri != OntologyConstants.KnoraBase.KnoraBaseOntologyIri, // Any property defined in a project-specific ontology is editable.
                        isLinkProp = linkProps.contains(propertyIri),
                        isLinkValueProp = linkValueProps.contains(propertyIri),
                        isFileValueProp = fileValueProps.contains(propertyIri),
                        predicates = predicates,
                        subPropertyOf = directSubPropertyOfRelations.getOrElse(propertyIri, Set.empty[IRI]),
                        ontologySchema = InternalSchema
                    )

                    propertyIri -> propertyEntityInfo
            }

            //
            // get all the standoff class definitions and their properties
            //

            // get ontology information about the value base classes
            valueBaseClassesSparql <- Future(queries.sparql.v2.txt.getValueBaseClassDefinitions(triplestore = settings.triplestoreType).toString())
            valueBaseClassesResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(valueBaseClassesSparql)).mapTo[SparqlSelectResponse]
            valueBaseClassesRows: Seq[VariableResultsRow] = valueBaseClassesResponse.results.bindings

            // Group the rows representing value base class definitions by value base class IRI.
            valueBaseClassesGrouped: Map[IRI, Seq[VariableResultsRow]] = valueBaseClassesRows.groupBy(_.rowMap("valueBaseClass"))

            // get ontology information about the standoff classes
            standoffClassesSparql <- Future(queries.sparql.v2.txt.getStandoffClassDefinitions(triplestore = settings.triplestoreType).toString())
            standoffClassesResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(standoffClassesSparql)).mapTo[SparqlSelectResponse]
            allStandoffClassRows: Seq[VariableResultsRow] = standoffClassesResponse.results.bindings

            // add the property Iris of the value base classes since they may be used by some standoff classes
            combinedStandoffClasses = valueBaseClassesRows ++ allStandoffClassRows

            // collect all the standoff property Iris from the cardinalities
            standoffPropertyIris = combinedStandoffClasses.foldLeft(Set.empty[IRI]) {
                case (acc, row) =>
                    val standoffPropIri: Option[String] = row.rowMap.get("cardinalityProp")

                    if (standoffPropIri.isDefined) {
                        acc + standoffPropIri.get
                    } else {
                        acc
                    }
            }

            // get information about the standoff properties
            standoffPropsSparql <- Future(queries.sparql.v2.txt.getStandoffPropertyDefinitions(triplestore = settings.triplestoreType, standoffPropertyIris.toList).toString())
            standoffPropsResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(standoffPropsSparql)).mapTo[SparqlSelectResponse]
            standoffPropsRows: Seq[VariableResultsRow] = standoffPropsResponse.results.bindings

            // Make a map of IRIs of named graphs to IRIs of standoff classes defined in each one.
            standoffGraphClassMap: Map[IRI, Set[IRI]] = allStandoffClassRows.groupBy(_.rowMap("graph")).map {
                case (graphIri: IRI, graphRows: Seq[VariableResultsRow]) =>
                    graphIri -> graphRows.map(_.rowMap("standoffClass")).toSet
            }

            // Make a map of IRIs of named graphs to IRIs of standoff properties defined in each one.
            standoffGraphPropMap: Map[IRI, Set[IRI]] = standoffPropsRows.groupBy(_.rowMap("graph")).map {
                case (graphIri, graphRows) =>
                    graphIri -> graphRows.map(_.rowMap("prop")).toSet
            }

            // Group the rows representing standoff class definitions by standoff class IRI.
            standoffClassesGrouped: Map[IRI, Seq[VariableResultsRow]] = allStandoffClassRows.groupBy(_.rowMap("standoffClass"))
            standoffClassIris = standoffClassesGrouped.keySet

            // Group the rows representing property definitions by property IRI.
            standoffPropertyDefsGrouped: Map[IRI, Seq[VariableResultsRow]] = standoffPropsRows.groupBy(_.rowMap("prop"))

            // Make a map of standoff property IRIs to their immediate base properties.
            directStandoffSubPropertyOfRelations: Map[IRI, Set[IRI]] = standoffPropertyDefsGrouped.map {
                case (propertyIri, rows) =>
                    val baseProperties = rows.filter(_.rowMap.get("propPred").contains(OntologyConstants.Rdfs.SubPropertyOf)).map(_.rowMap("propObj")).toSet
                    (propertyIri, baseProperties)
            }

            // Make a map of standoff class IRIs to their immediate base classes.
            directStandoffSubClassOfRelations: Map[IRI, Set[IRI]] = standoffClassesGrouped.map {
                case (standoffClassIri, rows) =>
                    val baseClasses = rows.filter(_.rowMap.get("standoffClassPred").contains(OntologyConstants.Rdfs.SubClassOf)).map(_.rowMap("standoffClassObj")).toSet
                    (standoffClassIri, baseClasses)
            }

            // Make a map in which each standoff class IRI points to the full set of its base classes. A class is also
            // a subclass of itself.
            allStandoffSubClassOfRelations: Map[IRI, Set[IRI]] = standoffClassIris.map {
                standoffClassIri => (standoffClassIri, getAllBaseDefs(standoffClassIri, directStandoffSubClassOfRelations) + standoffClassIri)
            }.toMap

            // Make a map of the cardinalities defined directly on each value base class. Each value base class IRI points to a map of
            // property IRIs to OwlCardinality objects.
            valueBaseClassCardinalities: Map[IRI, Map[IRI, OwlCardinalityOnProperty]] = valueBaseClassesGrouped.map {
                case (valueBaseClassIri, rows) =>
                    val valueBaseClassCardinalities: Map[IRI, OwlCardinalityOnProperty] = rows.filter(_.rowMap.contains("cardinalityProp")).map {
                        cardinalityRow =>
                            val cardinalityRowMap = cardinalityRow.rowMap
                            val propertyIri = cardinalityRowMap("cardinalityProp")

                            val owlCardinality = OwlCardinalityOnProperty(
                                propertyIri = propertyIri,
                                cardinalityIri = cardinalityRowMap("cardinality"),
                                cardinalityValue = cardinalityRowMap("cardinalityVal").toInt
                            )

                            propertyIri -> owlCardinality
                    }.toMap

                    valueBaseClassIri -> valueBaseClassCardinalities
            }

            // Make a map of the cardinalities defined directly on each standoff class. Each standoff class IRI points to a map of
            // property IRIs to OwlCardinality objects.
            directStandoffClassCardinalities: Map[IRI, Map[IRI, OwlCardinalityOnProperty]] = standoffClassesGrouped.map {
                case (standoffClassIri, rows) =>
                    val standoffClassCardinalities: Map[IRI, OwlCardinalityOnProperty] = rows.filter(_.rowMap.contains("cardinalityProp")).map {
                        cardinalityRow =>
                            val cardinalityRowMap = cardinalityRow.rowMap
                            val propertyIri = cardinalityRowMap("cardinalityProp")

                            val owlCardinality = OwlCardinalityOnProperty(
                                propertyIri = propertyIri,
                                cardinalityIri = cardinalityRowMap("cardinality"),
                                cardinalityValue = cardinalityRowMap("cardinalityVal").toInt
                            )

                            propertyIri -> owlCardinality
                    }.toMap

                    standoffClassIri -> standoffClassCardinalities
            }

            // Allow each standoff class to inherit cardinalities from its base classes.
            standoffCardinalitiesWithInheritance = standoffClassIris.map {
                standoffClassIri =>
                    val standoffClassCardinalities: Set[OwlCardinalityOnProperty] = inheritCardinalities(
                        resourceClassIri = standoffClassIri,
                        directSubClassOfRelations = directStandoffSubClassOfRelations,
                        allSubPropertyOfRelations = directStandoffSubPropertyOfRelations,
                        directResourceClassCardinalities = directStandoffClassCardinalities ++ valueBaseClassCardinalities
                    ).values.toSet

                    val prop2Card: Map[IRI, Cardinality.Value] = standoffClassCardinalities.map {
                        (card: OwlCardinalityOnProperty) =>
                            card.propertyIri -> Cardinality.owlCardinality2KnoraCardinality(
                                propertyIri = card.propertyIri,
                                OwlCardinalityInfo(
                                    owlCardinalityIri = card.cardinalityIri,
                                    owlCardinalityValue = card.cardinalityValue
                                )
                            )
                    }.toMap

                    standoffClassIri -> prop2Card
            }.toMap

            // TODO: distinguish between inherited and non-inherited cardinalities.

            standoffClassEntityInfos: Map[IRI, ReadClassInfoV2] = standoffClassesGrouped.map {
                case (standoffClassIri, standoffClassRows) =>

                    val standoffGroupedByPredicate: Map[IRI, Seq[VariableResultsRow]] = standoffClassRows.filter(_.rowMap.contains("standoffClassPred")).groupBy(_.rowMap("standoffClassPred")) - OntologyConstants.Rdfs.SubClassOf

                    val predicates: Map[IRI, PredicateInfoV2] = standoffGroupedByPredicate.map {
                        case (predicateIri, predicateRows) =>
                            val (predicateRowsWithLang, predicateRowsWithoutLang) = predicateRows.partition(_.rowMap.contains("standoffClassObjLang"))
                            val objects = predicateRowsWithoutLang.map(_.rowMap("standoffClassObj")).toSet
                            val objectsWithLang = predicateRowsWithLang.map {
                                predicateRow => predicateRow.rowMap("standoffClassObjLang") -> predicateRow.rowMap("standoffClassObj")
                            }.toMap

                            predicateIri -> PredicateInfoV2(
                                predicateIri = predicateIri,
                                ontologyIri = getOntologyIri(standoffClassIri),
                                objects = objects,
                                objectsWithLang = objectsWithLang
                            )
                    }

                    // determine the data type of the given standoff class IRI
                    // if the resulting set is empty, it is not a typed standoff class
                    val standoffDataType: Set[IRI] = allStandoffSubClassOfRelations(standoffClassIri).intersect(StandoffDataTypeClasses.getStandoffClassIris)
                    if (standoffDataType.size > 1) {
                        throw InconsistentTriplestoreDataException(s"standoff class $standoffClassIri is a subclass of more than one standoff data type class: ${standoffDataType.mkString(", ")}")
                    }

                    val standoffInfo = ReadClassInfoV2(
                        classInfoContent = ClassInfoContentV2(
                            classIri = standoffClassIri,
                            ontologyIri = getOntologyIri(standoffClassIri),
                            predicates = predicates,
                            cardinalities = standoffCardinalitiesWithInheritance(standoffClassIri),
                            standoffDataType = standoffDataType.headOption match {
                                case Some(dataType: IRI) => Some(StandoffDataTypeClasses.lookup(dataType, () => throw InconsistentTriplestoreDataException(s"$dataType is not a valid standoff data type")))
                                case None => None
                            },
                            subClassOf = directStandoffSubClassOfRelations.getOrElse(standoffClassIri, Set.empty[IRI]),
                            ontologySchema = InternalSchema
                        )
                    )

                    standoffClassIri -> standoffInfo
            }

            // Make a map in which each standoff property IRI points to the full set of its base properties. A property is also
            // a subproperty of itself.
            allStandoffSubPropertyOfRelations: Map[IRI, Set[IRI]] = standoffPropertyIris.map {
                propertyIri => (propertyIri, getAllBaseDefs(propertyIri, directStandoffSubPropertyOfRelations) + propertyIri)
            }.toMap


            // Construct a PropertyEntityInfoV2 for each property definition, not taking inheritance into account.
            standoffPropertyEntityInfos: Map[IRI, PropertyEntityInfoV2] = standoffPropertyDefsGrouped.map {
                case (standoffPropertyIri, propertyRows) =>
                    val ontologyIri = getOntologyIri(standoffPropertyIri)

                    // Group the rows for each property by predicate IRI.
                    val groupedByPredicate: Map[IRI, Seq[VariableResultsRow]] = propertyRows.groupBy(_.rowMap("propPred")) - OntologyConstants.Rdfs.SubPropertyOf

                    val predicates: Map[IRI, PredicateInfoV2] = groupedByPredicate.map {
                        case (predicateIri, predicateRows) =>
                            val (predicateRowsWithLang, predicateRowsWithoutLang) = predicateRows.partition(_.rowMap.contains("propObjLang"))
                            val objects = predicateRowsWithoutLang.map(_.rowMap("propObj")).toSet
                            val objectsWithLang = predicateRowsWithLang.map {
                                predicateRow => predicateRow.rowMap("propObjLang") -> predicateRow.rowMap("propObj")
                            }.toMap

                            predicateIri -> PredicateInfoV2(
                                predicateIri = predicateIri,
                                ontologyIri = ontologyIri,
                                objects = objects,
                                objectsWithLang = objectsWithLang
                            )
                    }

                    val standoffPropertyEntityInfo = PropertyEntityInfoV2(
                        propertyIri = standoffPropertyIri,
                        ontologyIri = ontologyIri,
                        predicates = predicates,
                        subPropertyOf = directStandoffSubPropertyOfRelations.getOrElse(standoffPropertyIri, Set.empty[IRI]),
                        isStandoffInternalReferenceProperty = allStandoffSubPropertyOfRelations(standoffPropertyIri).contains(OntologyConstants.KnoraBase.StandoffTagHasInternalReference),
                        ontologySchema = InternalSchema
                    )

                    standoffPropertyIri -> standoffPropertyEntityInfo
            }

            // collect all the standoff classes that have a data type (i.e. are subclasses of a data type standoff class)
            standoffClassEntityInfosWithDataType: Map[IRI, ReadClassInfoV2] = standoffClassEntityInfos.filter {
                case (standoffClassIri: IRI, entityInfo: ReadClassInfoV2) =>
                    entityInfo.classInfoContent.standoffDataType.isDefined
            }

            allClassDefs = resourceEntityInfos ++ KnoraApiV2Simple.Classes ++ KnoraApiV2WithValueObjects.Classes
            allPropertyDefs = propertyEntityInfos ++ KnoraApiV2Simple.Properties ++ KnoraApiV2WithValueObjects.Properties

            // Cache all the data.

            ontologyCacheData: OntologyCacheData = OntologyCacheData(
                namedGraphs = graphClassMap.keySet ++ graphPropMap.keySet ++ standoffGraphClassMap.keySet ++ standoffGraphPropMap.keySet,
                namedGraphClasses = new ErrorHandlingMap[IRI, Set[IRI]](graphClassMap, { key => s"Named graph not found: $key" }),
                namedGraphProperties = new ErrorHandlingMap[IRI, Set[IRI]](graphPropMap, { key => s"Named graph not found: $key" }),
                classDefs = new ErrorHandlingMap[IRI, ReadClassInfoV2](allClassDefs, { key => s"Class not found: $key" }),
                resourceAndValueSubClassOfRelations = new ErrorHandlingMap[IRI, Set[IRI]](allResourceSubClassOfRelations ++ allValueSubClassOfRelations, { key => s"Class not found: $key" }),
                resourceSuperClassOfRelations = new ErrorHandlingMap[IRI, Set[IRI]](allResourceSuperClassOfRelations, { key => s"Class not found: $key" }),
                propertyDefs = new ErrorHandlingMap[IRI, PropertyEntityInfoV2](allPropertyDefs, { key => s"Property not found: $key" }),
                namedGraphStandoffClasses = new ErrorHandlingMap[IRI, Set[IRI]](standoffGraphClassMap, { key => s"Named graph not found: $key" }),
                namedGraphStandoffProperties = new ErrorHandlingMap[IRI, Set[IRI]](standoffGraphPropMap, { key => s"Named graph not found: $key" }),
                standoffClassDefs = new ErrorHandlingMap[IRI, ReadClassInfoV2](standoffClassEntityInfos, { key => s"Standoff class def not found $key" }),
                standoffPropertyDefs = new ErrorHandlingMap[IRI, PropertyEntityInfoV2](standoffPropertyEntityInfos, { key => s"Standoff property def not found $key" }),
                standoffClassDefsWithDataType = new ErrorHandlingMap[IRI, ReadClassInfoV2](standoffClassEntityInfosWithDataType, { key => s"Standoff class def with datatype not found $key" }))

            _ = CacheUtil.put(cacheName = OntologyCacheName, key = OntologyCacheKey, value = ontologyCacheData)

        } yield SuccessResponseV2("Ontologies loaded.")
    }

    /**
      * Gets the ontology data from the cache.
      *
      * @return an [[OntologyCacheData]]
      */
    private def getCacheData: Future[OntologyCacheData] = {
        Future {
            CacheUtil.get[OntologyCacheData](cacheName = OntologyCacheName, key = OntologyCacheKey) match {
                case Some(data) => data
                case None => throw ApplicationCacheException(s"Key '$OntologyCacheKey' not found in application cache '$OntologyCacheName'")
            }
        }
    }

    /**
      * Given a list of resource IRIs and a list of property IRIs (ontology entities), returns an [[EntityInfoGetResponseV1]] describing both resource and property entities.
      *
      * @param classIris    the IRIs of the resource entities to be queried.
      * @param propertyIris the IRIs of the property entities to be queried.
      * @param userProfile  the profile of the user making the request.
      * @return an [[EntityInfoGetResponseV1]].
      */
    private def getEntityInfoResponseV2(classIris: Set[IRI] = Set.empty[IRI], propertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserProfileV1): Future[EntityInfoGetResponseV2] = {
        for {
            cacheData <- getCacheData

            classDefsAvailable: Map[IRI, ReadClassInfoV2] = cacheData.classDefs.filterKeys(classIris)
            propertyDefsAvailable: Map[IRI, PropertyEntityInfoV2] = cacheData.propertyDefs.filterKeys(propertyIris)

            missingClassDefs = classIris -- classDefsAvailable.keySet
            missingPropertyDefs = propertyIris -- propertyDefsAvailable.keySet

            _ = if (missingClassDefs.nonEmpty) {
                throw NotFoundException(s"Some requested classes were not found: ${missingClassDefs.mkString(", ")}")
            }

            _ = if (missingPropertyDefs.nonEmpty) {
                throw NotFoundException(s"Some requested properties were not found: ${missingPropertyDefs.mkString(", ")}")
            }

            response = EntityInfoGetResponseV2(
                classInfoMap = new ErrorHandlingMap(classDefsAvailable, { key => s"Resource class $key not found" }),
                propertyInfoMap = new ErrorHandlingMap(propertyDefsAvailable, { key => s"Property $key not found" })
            )
        } yield response
    }

    /**
      * Given a list of standoff class IRIs and a list of property IRIs (ontology entities), returns an [[StandoffEntityInfoGetResponseV1]] describing both resource and property entities.
      *
      * @param standoffClassIris    the IRIs of the resource entities to be queried.
      * @param standoffPropertyIris the IRIs of the property entities to be queried.
      * @param userProfile          the profile of the user making the request.
      * @return an [[EntityInfoGetResponseV1]].
      */
    private def getStandoffEntityInfoResponseV2(standoffClassIris: Set[IRI] = Set.empty[IRI], standoffPropertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserProfileV1): Future[StandoffEntityInfoGetResponseV2] = {
        for {
            cacheData <- getCacheData
            response = StandoffEntityInfoGetResponseV2(
                standoffClassInfoMap = cacheData.standoffClassDefs.filterKeys(standoffClassIris),
                standoffPropertyInfoMap = cacheData.standoffPropertyDefs.filterKeys(standoffPropertyIris)
            )
        } yield response
    }

    /**
      * Gets information about all standoff classes that are a subclass of a data type standoff class.
      *
      * @param userProfile the profile of the user making the request.
      * @return a [[StandoffClassesWithDataTypeGetResponseV1]]
      */
    private def getStandoffStandoffClassesWithDataTypeV2(userProfile: UserProfileV1): Future[StandoffClassesWithDataTypeGetResponseV2] = {
        for {
            cacheData <- getCacheData
            response = StandoffClassesWithDataTypeGetResponseV2(
                standoffClassInfoMap = cacheData.standoffClassDefsWithDataType
            )
        } yield response
    }

    /**
      * Gets all standoff property entities.
      *
      * @param userProfile the profile of the user making the request.
      * @return a [[StandoffAllPropertiesGetResponseV1]].
      */
    private def getAllStandoffPropertyEntitiesV2(userProfile: UserProfileV1): Future[StandoffAllPropertyEntitiesGetResponseV2] = {
        for {
            cacheData <- getCacheData
            response = StandoffAllPropertyEntitiesGetResponseV2(
                standoffAllPropertiesEntityInfoMap = cacheData.standoffPropertyDefs
            )
        } yield response
    }

    /**
      * Checks whether a certain Knora resource or value class is a subclass of another class.
      *
      * @param subClassIri   the IRI of the resource or value class whose subclassOf relations have to be checked.
      * @param superClassIri the IRI of the resource or value class to check for (whether it is a a super class of `subClassIri` or not).
      * @return a [[CheckSubClassResponseV1]].
      */
    private def checkSubClassV2(subClassIri: IRI, superClassIri: IRI, userProfile: UserProfileV1): Future[CheckSubClassResponseV2] = {
        for {
            cacheData <- getCacheData
            response = CheckSubClassResponseV2(
                isSubClass = cacheData.resourceAndValueSubClassOfRelations(subClassIri).contains(superClassIri)
            )
        } yield response
    }

    /**
      * Gets the IRIs of the subclasses of a resource class.
      *
      * @param resourceClassIri the IRI of the resource class whose subclasses should be returned.
      * @return a [[SubClassesGetResponseV1]].
      */
    private def getSubClassesV2(resourceClassIri: IRI, userProfile: UserProfileV1): Future[SubClassesGetResponseV2] = {
        for {
            cacheData <- getCacheData

            subClassIris = cacheData.resourceSuperClassOfRelations(resourceClassIri).toVector.sorted

            subClasses = subClassIris.map {
                subClassIri =>
                    val resourceClassInfo: ReadClassInfoV2 = cacheData.classDefs(subClassIri)

                    SubClassInfoV2(
                        id = subClassIri,
                        label = resourceClassInfo.getPredicateObject(
                            predicateIri = OntologyConstants.Rdfs.Label,
                            preferredLangs = Some(userProfile.userData.lang, settings.fallbackLanguage)
                        ).getOrElse(throw InconsistentTriplestoreDataException(s"Resource class $subClassIri has no rdfs:label"))
                    )
            }

            response = SubClassesGetResponseV2(
                subClasses = subClasses
            )
        } yield response
    }

    /**
      * Gets the [[NamedGraphEntityInfoV1]] for a named graph
      *
      * @param namedGraphIri the IRI of the named graph to query
      * @param userProfile   the profile of the user making the request.
      * @return a [[NamedGraphEntityInfoV1]].
      */
    private def getNamedGraphEntityInfoV2ForNamedGraphV2(namedGraphIri: IRI, userProfile: UserProfileV1): Future[NamedGraphEntityInfoV2] = {
        for {
            cacheData <- getCacheData

            _ = if (!cacheData.namedGraphs.contains(namedGraphIri)) {
                throw NotFoundException(s"Named graph not found: $namedGraphIri")
            }
        } yield NamedGraphEntityInfoV2(
            namedGraphIri = namedGraphIri,
            propertyIris = cacheData.namedGraphProperties.getOrElse(namedGraphIri, Set.empty[IRI]),
            classIris = cacheData.namedGraphClasses.getOrElse(namedGraphIri, Set.empty[IRI]),
            standoffClassIris = cacheData.namedGraphStandoffClasses.getOrElse(namedGraphIri, Set.empty[IRI]),
            standoffPropertyIris = cacheData.namedGraphStandoffProperties.getOrElse(namedGraphIri, Set.empty[IRI])
        )
    }

    private def getNamedGraphsV2(userProfile: UserProfileV1): Future[ReadNamedGraphsV2] = {
        for {
            // TODO: refactor this for V2
            projectsNamedGraph: Seq[NamedGraphV1] <- (responderManager ? ProjectsNamedGraphGetV1(userProfile)).mapTo[Seq[NamedGraphV1]]

            response = ReadNamedGraphsV2(
                namedGraphs = projectsNamedGraph.map(_.id).toSet
            )
        } yield response
    }

    /**
      * Requests the resource classes defined in the given named graphs.
      *
      * @param namedGraphIris the Iris of the named graphs to be queried.
      * @param userProfile    the profile of the user making the request.
      * @return a [[ReadEntityDefinitionsV2]].
      */
    private def getEntitiesForNamedGraphV2(namedGraphIris: Set[IRI], responseSchema: ApiV2Schema, allLanguages: Boolean, userProfile: UserProfileV1): Future[ReadEntityDefinitionsV2] = {


        for {
            cacheData <- getCacheData

            entitiesForNamedGraphsMap: Map[IRI, NamedGraphEntityInfoV2] = namedGraphIris.map {
                namedGraphIri =>

                    if (!cacheData.namedGraphs.contains(namedGraphIri)) {
                        throw NotFoundException(s"Named graph not found: $namedGraphIri")
                    }

                    namedGraphIri -> NamedGraphEntityInfoV2(
                        namedGraphIri = namedGraphIri,
                        propertyIris = cacheData.namedGraphProperties.getOrElse(namedGraphIri, Set.empty[IRI]),
                        classIris = cacheData.namedGraphClasses.getOrElse(namedGraphIri, Set.empty[IRI]),
                        standoffClassIris = cacheData.namedGraphStandoffClasses.getOrElse(namedGraphIri, Set.empty[IRI]),
                        standoffPropertyIris = cacheData.namedGraphStandoffProperties.getOrElse(namedGraphIri, Set.empty[IRI])
                    )
            }.toMap

            // Get non-standoff classes and properties.

            classIris: Set[IRI] = entitiesForNamedGraphsMap.values.flatMap(_.classIris).toSet
            propertyIris: Set[IRI] = entitiesForNamedGraphsMap.values.flatMap(_.propertyIris).toSet

            readEntityDefsForClasses: ReadEntityDefinitionsV2 <- getClassDefinitionsV2(
                classIris,
                responseSchema,
                allLanguages,
                userProfile = userProfile
            )

            readEntityDefsForProperties: ReadEntityDefinitionsV2 <- getPropertyDefinitionsV2(propertyIris, allLanguages, userProfile = userProfile)

            // Get standoff classes and properties.

            standoffClassIris: Set[IRI] = entitiesForNamedGraphsMap.values.flatMap(_.standoffClassIris).toSet
            standoffPropertyIris: Set[IRI] = entitiesForNamedGraphsMap.values.flatMap(_.standoffPropertyIris).toSet

            standoffEntities <- getStandoffEntityInfoResponseV2(standoffClassIris = standoffClassIris, standoffPropertyIris = standoffPropertyIris, userProfile = userProfile)

            ontologiesWithClasses: Map[IRI, Set[IRI]] = entitiesForNamedGraphsMap.map {
                case (namedGraphIri, namedGraphInfo) => (namedGraphIri, namedGraphInfo.classIris)
            }
        } yield ReadEntityDefinitionsV2(
            ontologies = ontologiesWithClasses,
            classes = readEntityDefsForClasses.classes,
            properties = readEntityDefsForProperties.properties,
            standoffClasses = standoffEntities.standoffClassInfoMap,
            standoffProperties = standoffEntities.standoffPropertyInfoMap,
            userLang = readEntityDefsForClasses.userLang
        )
    }

    /**
      * Requests information about resource classes and their properties.
      *
      * @param classIris   the Iris of the resource classes to query for.
      * @param userProfile the profile of the user making the request.
      * @return a [[ReadEntityDefinitionsV2]].
      */
    private def getClassDefinitionsV2(classIris: Set[IRI], responseSchema: ApiV2Schema, allLanguages: Boolean, userProfile: UserProfileV1): Future[ReadEntityDefinitionsV2] = {
        for {
            // request information about the given resource class Iris
            classInfoResponse: EntityInfoGetResponseV2 <- getEntityInfoResponseV2(classIris = classIris, userProfile = userProfile)

            // Are we returning data in the user's preferred language, or in all available languages?
            userLang = if (!allLanguages) {
                // Just the user's preferred language.
                Some(userProfile.userData.lang)
            } else {
                // All available languages.
                None
            }

        } yield ReadEntityDefinitionsV2(classes = classInfoResponse.classInfoMap, userLang = userLang)
    }

    /**
      * Requests information about property entities.
      *
      * @param propertyIris the Iris of the properties to query for.
      * @param userProfile  the profile of the user making the request.
      * @return a [[ReadEntityDefinitionsV2]].
      */
    private def getPropertyDefinitionsV2(propertyIris: Set[IRI], allLanguages: Boolean, userProfile: UserProfileV1) = {

        for {

            propertiesResponse: EntityInfoGetResponseV2 <- getEntityInfoResponseV2(propertyIris = propertyIris, userProfile = userProfile)

            // Are we returning data in the user's preferred language, or in all available languages?
            userLang = if (!allLanguages) {
                // Just the user's preferred language.
                Some(userProfile.userData.lang)
            } else {
                // All available languages.
                None
            }

        } yield ReadEntityDefinitionsV2(properties = propertiesResponse.propertyInfoMap, userLang = userLang)
    }

    /**
      * Creates a new, empty ontology.
      *
      * @param createOntologyRequest the request message.
      * @return a [[SuccessResponseV2]].
      */
    private def createOntology(createOntologyRequest: CreateOntologyRequestV2): Future[ReadEntityDefinitionsV2] = {
        def makeTaskFuture(internalOntologyIri: IRI): Future[ReadEntityDefinitionsV2] = {
            for {
                currentTime: String <- FastFuture.successful(Instant.now.toString)

                // Make sure the ontology doesn't already exist.

                checkOntologySparql = queries.sparql.v2.txt.getOntologyInfo(
                    triplestore = settings.triplestoreType,
                    ontologyIri = internalOntologyIri
                ).toString

                preUpdateCheckResponse <- (storeManager ? SparqlSelectRequest(checkOntologySparql)).mapTo[SparqlSelectResponse]

                _ = if (preUpdateCheckResponse.results.bindings.nonEmpty) {
                    throw BadRequestException(s"Ontology $internalOntologyIri cannot be created, because it already exists")
                }

                // Create the ontology.

                createOntologySparql = queries.sparql.v2.txt.createOntology(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri,
                    ontologyIri = internalOntologyIri,
                    currentTime = currentTime
                ).toString

                createOntologyResponse <- (storeManager ? SparqlUpdateRequest(createOntologySparql)).mapTo[SparqlUpdateResponse]

                // Check that the update was successful.

                postUpdateCheckResponse <- (storeManager ? SparqlSelectRequest(checkOntologySparql)).mapTo[SparqlSelectResponse]

                lastModDate: Set[String] = postUpdateCheckResponse.results.bindings.map {
                    row => row.rowMap.get("ontologyPred") match {
                        case Some(OntologyConstants.KnoraBase.LastModificationDate) => row.rowMap.get("ontologyObj")
                        case _ => None
                    }
                }.toSet.flatten

                _ = if (lastModDate.size > 1) {
                    throw InconsistentTriplestoreDataException(s"Ontology $internalOntologyIri has more than one knora-base:lastModificationDate")
                }

                _ = if (lastModDate.head != currentTime) {
                    throw UpdateNotPerformedException()
                }

                // TODO: tell the projects responder that the ontology was created, so it can add it to the project's admin data.

                externalOntologyIri = stringFormatter.internalOntologyIriToApiV2WithValueObjectsOntologyIri(internalOntologyIri, () => throw AssertionException(s"Invalid internal ontology IRI: $internalOntologyIri"))

            } yield ReadEntityDefinitionsV2(
                ontologies = Map(externalOntologyIri -> Set.empty[IRI])
            )
        }

        for {
            userProfile <- FastFuture.successful(createOntologyRequest.userProfile)

            // TODO: check whether the user is a project or system admin.

            // TODO: get a real project code from the projects responder.

            projectCode: Option[String] = if (createOntologyRequest.ontologyName == "example") {
                Some("0000")
            } else {
                None
            }

            // Check that the ontology name is valid.
            validOntologyName = stringFormatter.toProjectSpecificOntologyName(createOntologyRequest.ontologyName, () => throw BadRequestException(s"Invalid project-specific ontology name: ${createOntologyRequest.ontologyName}"))

            // Make the internal ontology IRI.
            externalOntologyID = OntologyID(validOntologyName, projectCode)
            internalOntologyIri = stringFormatter.externalOntologyIDToInternalOntologyIri(externalOntologyID)

            // Do the remaining pre-update checks and the update while holding an update lock on the ontology.
            taskResult <- IriLocker.runWithIriLock(
                createOntologyRequest.apiRequestID,
                createOntologyRequest.ontologyName,
                () => makeTaskFuture(internalOntologyIri)
            )
        } yield taskResult
    }
}