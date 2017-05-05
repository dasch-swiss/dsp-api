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
import org.eclipse.rdf4j.model.impl.SimpleValueFactory
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.messages.v2.responder._
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil.{future2Message, handleUnexpectedMessage}
import org.knora.webapi.util.{CacheUtil, ErrorHandlingMap, KnoraIdUtil}
import org.knora.webapi.{ApplicationCacheException, IRI, InconsistentTriplestoreDataException, OntologyConstants}

import scala.concurrent.Future

class OntologiesResponderV2 extends Responder {

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
      * @param namedGraphResourceClasses           a map of named graph IRIs to sets of resource IRIs defined in each named graph.
      * @param namedGraphProperties                a map of property IRIs to sets of property IRIs defined in each named graph.
      * @param resourceClassDefs                   a map of resource class IRIs to resource class definitions.
      * @param resourceAndValueSubClassOfRelations a map of IRIs of resource and value classes to sets of the IRIs of their base classes.
      * @param resourceSuperClassOfRelations       a map of IRIs of resource classes to sets of the IRIs of their subclasses.
      * @param propertyDefs                        a map of property IRIs to property definitions.
      */
    // TODO: rename and move v1 case classes to v2 (v1 may use V2, but not vice versa)
    case class OntologyCacheData(namedGraphResourceClasses: Map[IRI, Set[IRI]],
                                 namedGraphProperties: Map[IRI, Set[IRI]],
                                 resourceClassDefs: Map[IRI, ResourceEntityInfoV1],
                                 resourceAndValueSubClassOfRelations: Map[IRI, Set[IRI]],
                                 resourceSuperClassOfRelations: Map[IRI, Set[IRI]],
                                 propertyDefs: Map[IRI, PropertyEntityInfoV1],
                                 standoffClassDefs: Map[IRI, StandoffClassEntityInfoV1],
                                 standoffPropertyDefs: Map[IRI, StandoffPropertyEntityInfoV1],
                                 standoffClassDefsWithDataType: Map[IRI, StandoffClassEntityInfoV1])

    def receive = {
        case LoadOntologiesRequestV2(userProfile) => future2Message(sender(), loadOntologies(userProfile), log)
        case EntityInfoGetRequestV2(resourceClassIris, propertyIris, userProfile) => future2Message(sender(), getEntityInfoResponseV2(resourceClassIris, propertyIris, userProfile), log)
        case StandoffEntityInfoGetRequestV2(standoffClassIris, standoffPropertyIris, userProfile) => future2Message(sender(), getStandoffEntityInfoResponseV2(standoffClassIris, standoffPropertyIris, userProfile), log)
        case StandoffClassesWithDataTypeGetRequestV2(userProfile) => future2Message(sender(), getStandoffStandoffClassesWithDataTypeV2(userProfile), log)
        case StandoffAllPropertyEntitiesGetRequestV2(userProfile) => future2Message(sender(), getAllStandoffPropertyEntitiesV2(userProfile), log)
        case CheckSubClassRequestV2(subClassIri, superClassIri, userProfile) => future2Message(sender(), checkSubClassV2(subClassIri, superClassIri, userProfile), log)
        case SubClassesGetRequestV2(resourceClassIri, userProfile) => future2Message(sender(), getSubClassesV2(resourceClassIri, userProfile), log)
        case NamedGraphEntitiesRequestV2(namedGraphIri, userProfile) => future2Message(sender(), getNamedGraphEntityInfoV1ForNamedGraph(namedGraphIri, userProfile), log)
        case resourceClassesRequest: ResourceClassesGetRequestV2 => future2Message(sender(), getResourceClassDefinitionsWithCardinalities(resourceClassesRequest.resourceClassIris, resourceClassesRequest.userProfile), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Loads and caches all ontology information.
      *
      * @param userProfile the profile of the user making the request.
      * @return a [[LoadOntologiesResponse]].
      */
    private def loadOntologies(userProfile: UserProfileV1): Future[LoadOntologiesResponseV2] = {
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
        case class OwlCardinality(propertyIri: IRI,
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
                                 directResourceClassCardinalities: Map[IRI, Map[IRI, OwlCardinality]]): Map[IRI, OwlCardinality] = {
            // Recursively get properties that are available to inherit from base classes. If we have no information about
            // a class, that could mean that it isn't a subclass of knora-base:Resource (e.g. it's something like
            // foaf:Person), in which case we assume that it has no base classes.
            val cardinalitiesAvailableToInherit: Map[IRI, OwlCardinality] = directSubClassOfRelations.getOrElse(resourceClassIri, Set.empty[IRI]).foldLeft(Map.empty[IRI, OwlCardinality]) {
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
            val thisClassCardinalities: Map[IRI, OwlCardinality] = directResourceClassCardinalities.getOrElse(resourceClassIri, Map.empty[IRI, OwlCardinality])

            // From the properties that are available to inherit, filter out the ones that are overridden by properties
            // with cardinalities defined directly on this class.
            val inheritedCardinalities: Map[IRI, OwlCardinality] = cardinalitiesAvailableToInherit.filterNot {
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
            }

            // Make a map of IRIs of named graphs to IRIs of properties defined in each one.
            graphPropMap: Map[IRI, Set[IRI]] = propertyDefsRows.groupBy(_.rowMap("graph")).map {
                case (graphIri, graphRows) =>
                    graphIri -> graphRows.map(_.rowMap("prop")).toSet
            }

            // Group the rows representing resource class definitions by resource class IRI.
            resourceDefsGrouped: Map[IRI, Seq[VariableResultsRow]] = resourceDefsRows.groupBy(_.rowMap("resourceClass"))
            resourceClassIris = resourceDefsGrouped.keySet

            // Group the rows representing property definitions by property IRI. Exclude knora-base:resourceProperty and knora-base:hasValue, which is never used directly.
            propertyDefsGrouped: Map[IRI, Seq[VariableResultsRow]] = propertyDefsRows.groupBy(_.rowMap("prop")) - OntologyConstants.KnoraBase.ResourceProperty - OntologyConstants.KnoraBase.HasValue
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
            directResourceClassCardinalities: Map[IRI, Map[IRI, OwlCardinality]] = resourceDefsGrouped.map {
                case (resourceClassIri, rows) =>
                    val resourceClassCardinalities: Map[IRI, OwlCardinality] = rows.filter(_.rowMap.contains("cardinalityProp")).map {
                        cardinalityRow =>
                            val cardinalityRowMap = cardinalityRow.rowMap
                            val propertyIri = cardinalityRowMap("cardinalityProp")

                            val owlCardinality = OwlCardinality(
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
            resourceCardinalitiesWithInheritance: Map[IRI, Set[OwlCardinality]] = resourceClassIris.map {
                resourceClassIri =>
                    val resourceClassCardinalities: Set[OwlCardinality] = inheritCardinalities(
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

            // Construct a ResourceEntityInfoV1 for each resource class.
            resourceEntityInfos: Map[IRI, ResourceEntityInfoV1] = concreteResourceDefsGrouped.map {
                case (resourceClassIri, resourceClassRows) =>
                    // Group the rows for each resource class by predicate IRI.
                    val groupedByPredicate: Map[IRI, Seq[VariableResultsRow]] = resourceClassRows.filter(_.rowMap.contains("resourceClassPred")).groupBy(_.rowMap("resourceClassPred")) - OntologyConstants.Rdfs.SubClassOf

                    val predicates: Map[IRI, PredicateInfoV1] = groupedByPredicate.map {
                        case (predicateIri, predicateRows) =>
                            val (predicateRowsWithLang, predicateRowsWithoutLang) = predicateRows.partition(_.rowMap.contains("resourceClassObjLang"))
                            val objects = predicateRowsWithoutLang.map(_.rowMap("resourceClassObj")).toSet
                            val objectsWithLang = predicateRowsWithLang.map {
                                predicateRow => predicateRow.rowMap("resourceClassObjLang") -> predicateRow.rowMap("resourceClassObj")
                            }.toMap

                            predicateIri -> PredicateInfoV1(
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

                    val resourceEntityInfo = ResourceEntityInfoV1(
                        resourceClassIri = resourceClassIri,
                        ontologyIri = getOntologyIri(resourceClassIri),
                        predicates = new ErrorHandlingMap(predicates, { key: IRI => s"Predicate $key not found for resource class $resourceClassIri" }),
                        cardinalities = owlCardinalities.map {
                            owlCardinality =>
                                // Convert the OWL cardinality to a Knora Cardinality enum value.
                                owlCardinality.propertyIri -> Cardinality.owlCardinality2KnoraCardinality(
                                    propertyIri = owlCardinality.propertyIri,
                                    owlCardinalityIri = owlCardinality.cardinalityIri,
                                    owlCardinalityValue = owlCardinality.cardinalityValue
                                )
                        }.toMap,
                        linkProperties = linkProps,
                        linkValueProperties = linkValueProps,
                        fileValueProperties = fileValueProps
                    )

                    resourceClassIri -> resourceEntityInfo
            }

            // Construct a PropertyEntityInfoV1 for each property definition, not taking inheritance into account.
            propertyEntityInfos: Map[IRI, PropertyEntityInfoV1] = propertyDefsGrouped.map {
                case (propertyIri, propertyRows) =>
                    val ontologyIri = getOntologyIri(propertyIri)

                    // Group the rows for each property by predicate IRI.
                    val groupedByPredicate: Map[IRI, Seq[VariableResultsRow]] = propertyRows.groupBy(_.rowMap("propPred")) - OntologyConstants.Rdfs.SubPropertyOf

                    val predicates: Map[IRI, PredicateInfoV1] = groupedByPredicate.map {
                        case (predicateIri, predicateRows) =>
                            val (predicateRowsWithLang, predicateRowsWithoutLang) = predicateRows.partition(_.rowMap.contains("propObjLang"))
                            val objects = predicateRowsWithoutLang.map(_.rowMap("propObj")).toSet
                            val objectsWithLang = predicateRowsWithLang.map {
                                predicateRow => predicateRow.rowMap("propObjLang") -> predicateRow.rowMap("propObj")
                            }.toMap

                            predicateIri -> PredicateInfoV1(
                                predicateIri = predicateIri,
                                ontologyIri = ontologyIri,
                                objects = objects,
                                objectsWithLang = objectsWithLang
                            )
                    }

                    val propertyEntityInfo = PropertyEntityInfoV1(
                        propertyIri = propertyIri,
                        ontologyIri = ontologyIri,
                        isLinkProp = linkProps.contains(propertyIri),
                        isLinkValueProp = linkValueProps.contains(propertyIri),
                        isFileValueProp = fileValueProps.contains(propertyIri),
                        predicates = predicates
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

            // get ontology information about the standoff classes
            standoffClassesSparql <- Future(queries.sparql.v2.txt.getStandoffClassDefinitions(triplestore = settings.triplestoreType).toString())
            standoffClassesResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(standoffClassesSparql)).mapTo[SparqlSelectResponse]
            standoffClassRows: Seq[VariableResultsRow] = standoffClassesResponse.results.bindings

            // add the property Iris of the value base classes since they may be used by some standoff classes
            combinedStandoffClasses = valueBaseClassesRows ++ standoffClassRows

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

            // Group the rows representing value base class definitions by value base class IRI.
            valueBaseClassesGrouped: Map[IRI, Seq[VariableResultsRow]] = valueBaseClassesRows.groupBy(_.rowMap("valueBaseClass"))
            valueBaseClassIris = valueBaseClassesGrouped.keySet

            // Group the rows representing standoff class definitions by standoff class IRI.
            standoffClassesGrouped: Map[IRI, Seq[VariableResultsRow]] = standoffClassRows.groupBy(_.rowMap("standoffClass"))
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
            valueBaseClassCardinalities: Map[IRI, Map[IRI, OwlCardinality]] = valueBaseClassesGrouped.map {
                case (valueBaseClassIri, rows) =>
                    val valueBaseClassCardinalities: Map[IRI, OwlCardinality] = rows.filter(_.rowMap.contains("cardinalityProp")).map {
                        cardinalityRow =>
                            val cardinalityRowMap = cardinalityRow.rowMap
                            val propertyIri = cardinalityRowMap("cardinalityProp")

                            val owlCardinality = OwlCardinality(
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
            directStandoffClassCardinalities: Map[IRI, Map[IRI, OwlCardinality]] = standoffClassesGrouped.map {
                case (standoffClassIri, rows) =>
                    val standoffClassCardinalities: Map[IRI, OwlCardinality] = rows.filter(_.rowMap.contains("cardinalityProp")).map {
                        cardinalityRow =>
                            val cardinalityRowMap = cardinalityRow.rowMap
                            val propertyIri = cardinalityRowMap("cardinalityProp")

                            val owlCardinality = OwlCardinality(
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
                    val standoffClassCardinalities: Set[OwlCardinality] = inheritCardinalities(
                        resourceClassIri = standoffClassIri,
                        directSubClassOfRelations = directStandoffSubClassOfRelations,
                        allSubPropertyOfRelations = directStandoffSubPropertyOfRelations,
                        directResourceClassCardinalities = directStandoffClassCardinalities ++ valueBaseClassCardinalities
                    ).values.toSet

                    val prop2Card: Map[IRI, Cardinality.Value] = standoffClassCardinalities.map {
                        (card: OwlCardinality) =>
                            card.propertyIri -> Cardinality.owlCardinality2KnoraCardinality(
                                propertyIri = card.propertyIri,
                                owlCardinalityIri = card.cardinalityIri,
                                owlCardinalityValue = card.cardinalityValue
                            )
                    }.toMap

                    standoffClassIri -> prop2Card
            }.toMap

            standoffClassEntityInfos: Map[IRI, StandoffClassEntityInfoV1] = standoffClassesGrouped.map {
                case (standoffClassIri, standoffClassRows) =>

                    val standoffGroupedByPredicate: Map[IRI, Seq[VariableResultsRow]] = standoffClassRows.filter(_.rowMap.contains("standoffClassPred")).groupBy(_.rowMap("standoffClassPred")) - OntologyConstants.Rdfs.SubClassOf

                    val predicates: Map[IRI, PredicateInfoV1] = standoffGroupedByPredicate.map {
                        case (predicateIri, predicateRows) =>
                            val (predicateRowsWithLang, predicateRowsWithoutLang) = predicateRows.partition(_.rowMap.contains("standoffClassObjLang"))
                            val objects = predicateRowsWithoutLang.map(_.rowMap("standoffClassObj")).toSet
                            val objectsWithLang = predicateRowsWithLang.map {
                                predicateRow => predicateRow.rowMap("standoffClassObjLang") -> predicateRow.rowMap("standoffClassObj")
                            }.toMap

                            predicateIri -> PredicateInfoV1(
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

                    val standoffInfo = StandoffClassEntityInfoV1(
                        standoffClassIri = standoffClassIri,
                        ontologyIri = getOntologyIri(standoffClassIri),
                        predicates = predicates,
                        cardinalities = standoffCardinalitiesWithInheritance(standoffClassIri),
                        dataType = standoffDataType.headOption match {
                            case Some(dataType: IRI) => Some(StandoffDataTypeClasses.lookup(dataType, () => throw InconsistentTriplestoreDataException(s"$dataType is not a valid standoff data type")))
                            case None => None
                        }
                    )

                    standoffClassIri -> standoffInfo
            }

            // Make a map in which each standoff property IRI points to the full set of its base properties. A property is also
            // a subproperty of itself.
            allStandoffSubPropertyOfRelations: Map[IRI, Set[IRI]] = standoffPropertyIris.map {
                propertyIri => (propertyIri, getAllBaseDefs(propertyIri, directStandoffSubPropertyOfRelations) + propertyIri)
            }.toMap


            // Construct a StandoffPropertyEntityInfoV1 for each property definition, not taking inheritance into account.
            standoffPropertyEntityInfos: Map[IRI, StandoffPropertyEntityInfoV1] = standoffPropertyDefsGrouped.map {
                case (standoffPropertyIri, propertyRows) =>
                    val ontologyIri = getOntologyIri(standoffPropertyIri)

                    // Group the rows for each property by predicate IRI.
                    val groupedByPredicate: Map[IRI, Seq[VariableResultsRow]] = propertyRows.groupBy(_.rowMap("propPred")) - OntologyConstants.Rdfs.SubPropertyOf

                    val predicates: Map[IRI, PredicateInfoV1] = groupedByPredicate.map {
                        case (predicateIri, predicateRows) =>
                            val (predicateRowsWithLang, predicateRowsWithoutLang) = predicateRows.partition(_.rowMap.contains("propObjLang"))
                            val objects = predicateRowsWithoutLang.map(_.rowMap("propObj")).toSet
                            val objectsWithLang = predicateRowsWithLang.map {
                                predicateRow => predicateRow.rowMap("propObjLang") -> predicateRow.rowMap("propObj")
                            }.toMap

                            predicateIri -> PredicateInfoV1(
                                predicateIri = predicateIri,
                                ontologyIri = ontologyIri,
                                objects = objects,
                                objectsWithLang = objectsWithLang
                            )
                    }

                    val standoffPropertyEntityInfo = StandoffPropertyEntityInfoV1(
                        standoffPropertyIri = standoffPropertyIri,
                        ontologyIri = ontologyIri,
                        predicates = predicates,
                        isSubPropertyOf = allStandoffSubPropertyOfRelations(standoffPropertyIri)
                    )

                    standoffPropertyIri -> standoffPropertyEntityInfo
            }

            // collect all the standoff classes that have a data type (i.e. are subclasses of a data type standoff class)
            standoffClassEntityInfosWithDataType: Map[IRI, StandoffClassEntityInfoV1] = standoffClassEntityInfos.filter {
                case (standoffClassIri: IRI, entityInfo: StandoffClassEntityInfoV1) =>
                    entityInfo.dataType.isDefined
            }

            // Cache all the data.

            ontologyCacheData: OntologyCacheData = OntologyCacheData(
                namedGraphResourceClasses = new ErrorHandlingMap[IRI, Set[IRI]](graphClassMap, { key => s"Named graph not found: $key" }),
                namedGraphProperties = new ErrorHandlingMap[IRI, Set[IRI]](graphPropMap, { key => s"Named graph not found: $key" }),
                resourceClassDefs = new ErrorHandlingMap[IRI, ResourceEntityInfoV1](resourceEntityInfos, { key => s"Resource class not found: $key" }),
                resourceAndValueSubClassOfRelations = new ErrorHandlingMap[IRI, Set[IRI]](allResourceSubClassOfRelations ++ allValueSubClassOfRelations, { key => s"Class not found: $key" }),
                resourceSuperClassOfRelations = new ErrorHandlingMap[IRI, Set[IRI]](allResourceSuperClassOfRelations, { key => s"Class not found: $key" }),
                propertyDefs = new ErrorHandlingMap[IRI, PropertyEntityInfoV1](propertyEntityInfos, { key => s"Property not found: $key" }),
                standoffClassDefs = new ErrorHandlingMap[IRI, StandoffClassEntityInfoV1](standoffClassEntityInfos, { key => s"Standoff class def not found $key" }),
                standoffPropertyDefs = new ErrorHandlingMap[IRI, StandoffPropertyEntityInfoV1](standoffPropertyEntityInfos, { key => s"Standoff property def not found $key" }),
                standoffClassDefsWithDataType = new ErrorHandlingMap[IRI, StandoffClassEntityInfoV1](standoffClassEntityInfosWithDataType, { key => s"Standoff class def with datatype not found $key" }))

            _ = CacheUtil.put(cacheName = OntologyCacheName, key = OntologyCacheKey, value = ontologyCacheData)

        } yield LoadOntologiesResponseV2()
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
      * @param resourceClassIris the IRIs of the resource entities to be queried.
      * @param propertyIris      the IRIs of the property entities to be queried.
      * @param userProfile       the profile of the user making the request.
      * @return an [[EntityInfoGetResponseV1]].
      */
    private def getEntityInfoResponseV2(resourceClassIris: Set[IRI] = Set.empty[IRI], propertyIris: Set[IRI] = Set.empty[IRI], userProfile: UserProfileV1): Future[EntityInfoGetResponseV2] = {
        for {
            cacheData <- getCacheData
            response = EntityInfoGetResponseV2(
                resourceEntityInfoMap = new ErrorHandlingMap(cacheData.resourceClassDefs.filterKeys(resourceClassIris), { key => s"Resource class $key not found" }),
                propertyEntityInfoMap = new ErrorHandlingMap(cacheData.propertyDefs.filterKeys(propertyIris), { key => s"Property $key not found" })
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
                standoffClassEntityInfoMap = cacheData.standoffClassDefs.filterKeys(standoffClassIris),
                standoffPropertyEntityInfoMap = cacheData.standoffPropertyDefs.filterKeys(standoffPropertyIris)
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
                standoffClassEntityInfoMap = cacheData.standoffClassDefsWithDataType
            )
        } yield response
    }

    /**
      * Gets all standoff property entities.
      *
      * @param userProfile the profile of the user making the request.
      * @return a [[StandoffAllPropertyEntitiesGetResponseV1]].
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
      * @param subClassIri   the Iri of the resource or value class whose subclassOf relations have to be checked.
      * @param superClassIri the Iri of the resource or value class to check for (whether it is a a super class of `subClassIri` or not).
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
      * @param resourceClassIri the Iri of the resource class whose subclasses should be returned.
      * @return a [[SubClassesGetResponseV1]].
      */
    private def getSubClassesV2(resourceClassIri: IRI, userProfile: UserProfileV1): Future[SubClassesGetResponseV2] = {
        for {
            cacheData <- getCacheData

            subClassIris = cacheData.resourceSuperClassOfRelations(resourceClassIri).toVector.sorted

            subClasses = subClassIris.map {
                subClassIri =>
                    val resourceClassInfo: ResourceEntityInfoV1 = cacheData.resourceClassDefs(subClassIri)

                    SubClassInfoV1(
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
      * @param namedGraphIri the Iri of the named graph to query
      * @param userProfile   the profile of the user making the request.
      * @return a [[NamedGraphEntityInfoV1]].
      */
    private def getNamedGraphEntityInfoV1ForNamedGraph(namedGraphIri: IRI, userProfile: UserProfileV1): Future[NamedGraphEntityInfoV2] = {
        for {
            cacheData <- getCacheData
            response = NamedGraphEntityInfoV2(
                namedGraphIri = namedGraphIri,
                propertyIris = cacheData.namedGraphProperties(namedGraphIri),
                resourceClasses = cacheData.namedGraphResourceClasses(namedGraphIri)
            )
        } yield response
    }


    /**
      * Gets information about resource entities and their properties.
      *
      * @param resourceClassIris
      * @param userProfile
      * @return
      */
    private def getResourceClassDefinitionsWithCardinalities(resourceClassIris: Set[IRI], userProfile: UserProfileV1) = {
        for {

            // request information about the given resource class Iris
            resourceClassResponse: EntityInfoGetResponseV2 <- getEntityInfoResponseV2(resourceClassIris = resourceClassIris, userProfile = userProfile)

            cacheData <- getCacheData

            // get the subclassOf relations of the given resource classes
            subClassOfRelations: Map[IRI, Set[IRI]] = resourceClassIris.map {
                resClass =>
                    resClass -> cacheData.resourceAndValueSubClassOfRelations(resClass)
            }.toMap

            // get all property Iris from cardinalities
            propertyIris: Set[IRI] = resourceClassResponse.resourceEntityInfoMap.values.foldLeft(Set.empty[IRI]) {
                case (acc: Set[IRI], resourceEntityInfo: ResourceEntityInfoV1) =>
                    acc ++ resourceEntityInfo.cardinalities.keySet
            }

            // request information about the properties for which cardinalities are defined
            propertiesResponse: EntityInfoGetResponseV2 <- getEntityInfoResponseV2(propertyIris = propertyIris, userProfile = userProfile)

        } yield ReadEntityDefinitionsV2(resourceClasses = resourceClassResponse.resourceEntityInfoMap, subClassOfRelations = subClassOfRelations, properties = propertiesResponse.propertyEntityInfoMap)


    }
}