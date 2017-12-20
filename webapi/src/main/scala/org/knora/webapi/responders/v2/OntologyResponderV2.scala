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

import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.ontologymessages._
import org.knora.webapi.messages.v1.responder.projectmessages._
import org.knora.webapi.messages.v1.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder.{SuccessResponseV2, ontologymessages}
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.OwlCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.util.ActorUtil.{future2Message, handleUnexpectedMessage}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{CacheUtil, ErrorHandlingMap, SmartIri}

import scala.concurrent.Future

/**
  * Responds to requests dealing with ontologies.
  *
  * The API v2 ontology responder reads ontologies from two sources:
  *
  * - The triplestore.
  * - The constant knora-api v2 ontologies that are defined in Scala rather than in the triplestore, [[KnoraApiV2Simple]] and [[KnoraApiV2WithValueObjects]].
  *
  * It maintains an in-memory cache of all ontology data. This cache can be refreshed by sending a [[LoadOntologiesRequestV2]].
  *
  * Read requests to the ontology responder may contain internal or external IRIs as needed. Response messages from the
  * ontology responder will contain internal IRIs and definitions, unless a constant API v2 ontology was requested,
  * in which case the response will be in the requested API v2 schema.
  *
  * In API v2, the ontology responder can also create and update ontologies. Update requests must contain
  * [[ApiV2WithValueObjects]] IRIs and definitions.
  *
  * The API v1 ontology responder, which is read-only, delegates most of its work to this responder.
  */
class OntologyResponderV2 extends Responder {

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
      * @param ontologyMetadata                    metadata about available ontologies.
      * @param ontologyClasses                     a map of ontology IRIs to sets of non-standoff class IRIs defined in each ontology.
      * @param ontologyProperties                  a map of property IRIs to sets of non-standoff property IRIs defined in each ontology.
      * @param classDefs                           a map of class IRIs to definitions.
      * @param resourceAndValueSubClassOfRelations a map of IRIs of resource and value classes to sets of the IRIs of their base classes.
      * @param resourceSuperClassOfRelations       a map of IRIs of resource classes to sets of the IRIs of their subclasses.
      * @param propertyDefs                        a map of property IRIs to property definitions.
      * @param subPropertyOfRelations              a map of property IRIs to sets of the IRIs of their base properties.
      * @param ontologyStandoffClasses             a map of ontology IRIs to sets of standoff class IRIs defined in each ontology.
      * @param ontologyStandoffProperties          a map of property IRIs to sets of standoff property IRIs defined in each ontology.
      * @param standoffClassDefs                   a map of standoff class IRIs to definitions.
      * @param standoffPropertyDefs                a map of property IRIs to property definitions.
      * @param standoffClassDefsWithDataType       a map of standoff class IRIs to class definitions, including only standoff datatype tags.
      */
    private case class OntologyCacheData(ontologyMetadata: Map[SmartIri, OntologyMetadataV2],
                                         ontologyClasses: Map[SmartIri, Set[SmartIri]],
                                         ontologyProperties: Map[SmartIri, Set[SmartIri]],
                                         classDefs: Map[SmartIri, ReadClassInfoV2],
                                         resourceAndValueSubClassOfRelations: Map[SmartIri, Set[SmartIri]],
                                         resourceSuperClassOfRelations: Map[SmartIri, Set[SmartIri]],
                                         propertyDefs: Map[SmartIri, ReadPropertyInfoV2],
                                         subPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
                                         ontologyStandoffClasses: Map[SmartIri, Set[SmartIri]],
                                         ontologyStandoffProperties: Map[SmartIri, Set[SmartIri]],
                                         standoffClassDefs: Map[SmartIri, ReadClassInfoV2],
                                         standoffPropertyDefs: Map[SmartIri, ReadPropertyInfoV2],
                                         standoffClassDefsWithDataType: Map[SmartIri, ReadClassInfoV2])

    def receive = {
        case LoadOntologiesRequestV2(userProfile) => future2Message(sender(), loadOntologies(userProfile), log)
        case EntityInfoGetRequestV2(classIris, propertyIris, userProfile) => future2Message(sender(), getEntityInfoResponseV2(classIris, propertyIris, userProfile), log)
        case StandoffEntityInfoGetRequestV2(standoffClassIris, standoffPropertyIris, userProfile) => future2Message(sender(), getStandoffEntityInfoResponseV2(standoffClassIris, standoffPropertyIris, userProfile), log)
        case StandoffClassesWithDataTypeGetRequestV2(userProfile) => future2Message(sender(), getStandoffStandoffClassesWithDataTypeV2(userProfile), log)
        case StandoffAllPropertyEntitiesGetRequestV2(userProfile) => future2Message(sender(), getAllStandoffPropertyEntitiesV2(userProfile), log)
        case CheckSubClassRequestV2(subClassIri, superClassIri, userProfile) => future2Message(sender(), checkSubClassV2(subClassIri, superClassIri, userProfile), log)
        case SubClassesGetRequestV2(resourceClassIri, userProfile) => future2Message(sender(), getSubClassesV2(resourceClassIri, userProfile), log)
        case OntologyEntityIrisGetRequestV2(namedGraphIri, userProfile) => future2Message(sender(), getNamedGraphEntityInfoV2ForNamedGraphV2(namedGraphIri, userProfile), log)
        case OntologyEntitiesGetRequestV2(namedGraphIris, responseSchema, allLanguages, userProfile) => future2Message(sender(), getOntologyEntitiesV2(namedGraphIris, responseSchema, allLanguages, userProfile), log)
        case ClassesGetRequestV2(resourceClassIris, responseSchema, allLanguages, userProfile) => future2Message(sender(), getClassDefinitionsV2(resourceClassIris, responseSchema, allLanguages, userProfile), log)
        case PropertiesGetRequestV2(propertyIris, allLanguages, userProfile) => future2Message(sender(), getPropertyDefinitionsV2(propertyIris, allLanguages, userProfile), log)
        case OntologyMetadataGetRequestV2(projectIris, userProfile) => future2Message(sender(), getOntologyMetadataForProjectsV2(projectIris, userProfile), log)
        case createOntologyRequest: CreateOntologyRequestV2 => future2Message(sender(), createOntology(createOntologyRequest), log)
        case changeOntologyMetadataRequest: ChangeOntologyMetadataRequestV2 => future2Message(sender(), changeOntologyMetadata(changeOntologyMetadataRequest), log)
        case createPropertyRequest: CreatePropertyRequestV2 => future2Message(sender(), createProperty(createPropertyRequest), log)
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
        case class OwlCardinalityOnProperty(propertyIri: SmartIri,
                                            cardinalityIri: SmartIri,
                                            cardinalityValue: Int,
                                            isLinkProp: Boolean = false,
                                            isLinkValueProp: Boolean = false,
                                            isFileValueProp: Boolean = false) {
            if (!OntologyConstants.Owl.cardinalityOWLRestrictions.contains(cardinalityIri.toString)) {
                throw InconsistentTriplestoreDataException(s"Invalid OWL cardinality property: $cardinalityIri")
            }

            if (!(cardinalityValue == 0 || cardinalityValue == 1)) {
                throw InconsistentTriplestoreDataException(s"Invalid OWL cardinality value: $cardinalityValue")
            }

            /**
              * Converts this [[OwlCardinalityOnProperty]] to a tuple containing the property IRI and
              * a [[Cardinality.Value]].
              */
            def toClassDefCardinality: (SmartIri, Cardinality.Value) = {
                propertyIri -> Cardinality.owlCardinality2KnoraCardinality(
                    propertyIri = propertyIri.toString,
                    OwlCardinalityInfo(
                        owlCardinalityIri = cardinalityIri.toString,
                        owlCardinalityValue = cardinalityValue
                    )
                )
            }
        }
        /**
          * Recursively walks up an entity hierarchy, collecting the IRIs of all base entities.
          *
          * @param iri             the IRI of an entity.
          * @param directRelations a map of entities to their direct base entities.
          * @return all the base entities of the specified entity.
          */
        def getAllBaseDefs(iri: SmartIri, directRelations: Map[SmartIri, Set[SmartIri]]): Set[SmartIri] = {
            directRelations.get(iri) match {
                case Some(baseDefs) =>
                    baseDefs ++ baseDefs.flatMap(baseDef => getAllBaseDefs(baseDef, directRelations))

                case None => Set.empty[SmartIri]
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
        def inheritCardinalities(resourceClassIri: SmartIri,
                                 directSubClassOfRelations: Map[SmartIri, Set[SmartIri]],
                                 allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
                                 directResourceClassCardinalities: Map[SmartIri, Map[SmartIri, OwlCardinalityOnProperty]]): Map[SmartIri, OwlCardinalityOnProperty] = {
            // Recursively get properties that are available to inherit from base classes. If we have no information about
            // a class, that could mean that it isn't a subclass of knora-base:Resource (e.g. it's something like
            // foaf:Person), in which case we assume that it has no base classes.
            val cardinalitiesAvailableToInherit: Map[SmartIri, OwlCardinalityOnProperty] = directSubClassOfRelations.getOrElse(resourceClassIri, Set.empty[SmartIri]).foldLeft(Map.empty[SmartIri, OwlCardinalityOnProperty]) {
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
            val thisClassCardinalities: Map[SmartIri, OwlCardinalityOnProperty] = directResourceClassCardinalities.getOrElse(resourceClassIri, Map.empty[SmartIri, OwlCardinalityOnProperty])

            // From the properties that are available to inherit, filter out the ones that are overridden by properties
            // with cardinalities defined directly on this class.
            val inheritedCardinalities: Map[SmartIri, OwlCardinalityOnProperty] = cardinalitiesAvailableToInherit.filterNot {
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
            // Get all ontology metdata.
            ontologyMetdataSparql <- Future(queries.sparql.v2.txt.getAllOntologyInfo(triplestore = settings.triplestoreType).toString())
            ontologyMetdataResponse: SparqlConstructResponse <- (storeManager ? SparqlConstructRequest(ontologyMetdataSparql)).mapTo[SparqlConstructResponse]
            ontologyMetadata: Map[SmartIri, OntologyMetadataV2] = ontologyMetdataResponse.statements.flatMap {
                case (ontologyIri: IRI, ontologyStatements: Seq[(IRI, String)]) =>
                    if (ontologyIri == OntologyConstants.KnoraBase.KnoraBaseOntologyIri) {
                        None
                    } else {
                        val ontologySmartIri = ontologyIri.toSmartIri
                        val ontologyStatementMap = ontologyStatements.toMap
                        val ontologyLabel = ontologyStatementMap.getOrElse(OntologyConstants.Rdfs.Label, ontologySmartIri.getOntologyName)
                        val lastModificationDate = ontologyStatementMap.get(OntologyConstants.KnoraBase.LastModificationDate).map(instant => stringFormatter.toInstant(instant, throw InconsistentTriplestoreDataException(s"Invalid UTC instant: $instant")))

                        Some(ontologySmartIri -> OntologyMetadataV2(
                            ontologyIri = ontologySmartIri,
                            label = ontologyLabel,
                            lastModificationDate = lastModificationDate
                        ))
                    }
            }

            // Get all resource class definitions.
            resourceDefsSparql = queries.sparql.v2.txt.getResourceClassDefinitions(triplestore = settings.triplestoreType).toString()
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

            // Make a map of IRIs of ontologies to IRIs of resource classes defined in each one, excluding resource
            // classes that can't be instantiated directly.
            graphClassMap: Map[SmartIri, Set[SmartIri]] = resourceDefsRows.groupBy(_.rowMap("graph").toKnoraInternalSmartIri).map {
                case (graphIri: SmartIri, graphRows: Seq[VariableResultsRow]) =>
                    graphIri -> (graphRows.map(_.rowMap("resourceClass")).toSet -- OntologyConstants.KnoraBase.AbstractResourceClasses).map(_.toKnoraInternalSmartIri)
            } + (OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri.toSmartIri -> KnoraApiV2Simple.Classes.keySet) +
                (OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri -> KnoraApiV2WithValueObjects.Classes.keySet)

            // Make a map of IRIs of ontologies to IRIs of properties defined in each one, excluding knora-base:resourceProperty,
            // which is never used directly.
            graphPropMap: Map[SmartIri, Set[SmartIri]] = propertyDefsRows.groupBy(_.rowMap("graph").toKnoraInternalSmartIri).map {
                case (graphIri, graphRows) =>
                    graphIri -> (graphRows.map(_.rowMap("prop")).toSet - OntologyConstants.KnoraBase.ResourceProperty).map(_.toSmartIri)
            } + (OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri.toSmartIri -> KnoraApiV2Simple.Properties.keySet) +
                (OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri.toSmartIri -> KnoraApiV2WithValueObjects.Properties.keySet)

            // Group the rows representing resource class definitions by resource class IRI. This needs to include abstract resource classes such as
            // knora-base:Resource, so cardinalities can be inherited from them.
            resourceDefsGrouped: Map[SmartIri, Seq[VariableResultsRow]] = resourceDefsRows.groupBy(_.rowMap("resourceClass").toKnoraInternalSmartIri)
            resourceClassIris = resourceDefsGrouped.keySet

            // Group the rows representing property definitions by property IRI, excluding knora-base:resourceProperty, which is never used directly.
            propertyDefsGrouped: Map[SmartIri, Seq[VariableResultsRow]] = propertyDefsRows.groupBy(_.rowMap("prop").toKnoraInternalSmartIri) - OntologyConstants.KnoraBase.ResourceProperty.toKnoraInternalSmartIri
            propertyIris = propertyDefsGrouped.keySet

            // Group the rows representing value class relations by value class IRI.
            valueClassesGrouped: Map[SmartIri, Seq[VariableResultsRow]] = valueClassesRows.groupBy(_.rowMap("valueClass").toKnoraInternalSmartIri)

            // Make a map of resource class IRIs to their immediate base classes.
            directResourceSubClassOfRelations: Map[SmartIri, Set[SmartIri]] = resourceDefsGrouped.map {
                case (resourceClassIri, rows) =>
                    val baseClasses = rows.filter(_.rowMap.get("resourceClassPred").contains(OntologyConstants.Rdfs.SubClassOf)).map(_.rowMap("resourceClassObj").toSmartIri).toSet
                    (resourceClassIri, baseClasses)
            }

            // Make a map of property IRIs to their immediate base properties.
            directSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]] = propertyDefsGrouped.map {
                case (propertyIri, rows) =>
                    val baseProperties = rows.filter(_.rowMap.get("propPred").contains(OntologyConstants.Rdfs.SubPropertyOf)).map(_.rowMap("propObj").toSmartIri).toSet
                    (propertyIri, baseProperties)
            }

            // Make a map in which each resource class IRI points to the full set of its base classes. A class is also
            // a subclass of itself.
            allResourceSubClassOfRelations: Map[SmartIri, Set[SmartIri]] = resourceClassIris.map {
                resourceClassIri => (resourceClassIri, getAllBaseDefs(resourceClassIri, directResourceSubClassOfRelations) + resourceClassIri)
            }.toMap

            // Make a map in which each resource class IRI points to the full set of its subclasses. A class is also
            // a subclass of itself.
            allResourceSuperClassOfRelations: Map[SmartIri, Set[SmartIri]] = allResourceSubClassOfRelations.toVector.flatMap {
                case (subClass: SmartIri, baseClasses: Set[SmartIri]) =>
                    baseClasses.toVector.map {
                        baseClass => baseClass -> subClass
                    }
            }.groupBy(_._1).map {
                case (baseClass: SmartIri, baseClassAndSubClasses: Vector[(SmartIri, SmartIri)]) =>
                    baseClass -> baseClassAndSubClasses.map(_._2).toSet
            }

            // Make a map in which each property IRI points to the full set of its base properties. A property is also
            // a subproperty of itself.
            allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]] = propertyIris.map {
                propertyIri => (propertyIri, getAllBaseDefs(propertyIri, directSubPropertyOfRelations) + propertyIri)
            }.toMap

            // Make a map in which each value class IRI points to the full set of its base classes (excluding the ones
            // whose names end in "Base", since they aren't used directly). A class is also a subclass of itself (this
            // is handled by the SPARQL query).
            allValueSubClassOfRelations: Map[SmartIri, Set[SmartIri]] = valueClassesGrouped.map {
                case (valueClassIri, baseClassRows) =>
                    valueClassIri -> baseClassRows.map(_.rowMap("baseClass")).filterNot(_.endsWith("Base")).toSet.map {
                        iri: IRI => iri.toSmartIri
                    }
            }

            // Make a set of all subproperties of knora-base:hasLinkTo.
            linkProps: Set[SmartIri] = propertyIris.filter(prop => allSubPropertyOfRelations(prop).contains(OntologyConstants.KnoraBase.HasLinkTo.toKnoraInternalSmartIri))

            // Make a set of all subproperties of knora-base:hasLinkToValue.
            linkValueProps: Set[SmartIri] = propertyIris.filter(prop => allSubPropertyOfRelations(prop).contains(OntologyConstants.KnoraBase.HasLinkToValue.toKnoraInternalSmartIri))

            // Make a set of all subproperties of knora-base:hasFileValue.
            fileValueProps: Set[SmartIri] = propertyIris.filter(prop => allSubPropertyOfRelations(prop).contains(OntologyConstants.KnoraBase.HasFileValue.toKnoraInternalSmartIri))

            // Make a map of the cardinalities defined directly on each resource class. Each resource class IRI points to a map of
            // property IRIs to OwlCardinalityOnProperty objects.
            directResourceClassCardinalities: Map[SmartIri, Map[SmartIri, OwlCardinalityOnProperty]] = resourceDefsGrouped.map {
                case (resourceClassIri, rows) =>
                    val resourceClassCardinalities: Map[SmartIri, OwlCardinalityOnProperty] = rows.filter(_.rowMap.contains("cardinalityProp")).map {
                        cardinalityRow =>
                            val cardinalityRowMap = cardinalityRow.rowMap
                            val propertyIri = cardinalityRowMap("cardinalityProp").toKnoraInternalSmartIri

                            val owlCardinality = OwlCardinalityOnProperty(
                                propertyIri = propertyIri,
                                cardinalityIri = cardinalityRowMap("cardinality").toSmartIri,
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
            resourceCardinalitiesWithInheritance: Map[SmartIri, Set[OwlCardinalityOnProperty]] = resourceClassIris.map {
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
            concreteResourceDefsGrouped = resourceDefsGrouped -- OntologyConstants.KnoraBase.AbstractResourceClasses.map(_.toKnoraInternalSmartIri)

            // Construct a ReadClassInfoV2 for each resource class.
            resourceEntityInfos: Map[SmartIri, ReadClassInfoV2] = concreteResourceDefsGrouped.map {
                case (resourceClassIri, resourceClassRows) =>

                    // Group the rows for each resource class by predicate IRI.
                    val groupedByPredicate: Map[SmartIri, Seq[VariableResultsRow]] = resourceClassRows.filter(_.rowMap.contains("resourceClassPred")).groupBy(_.rowMap("resourceClassPred").toSmartIri) - OntologyConstants.Rdfs.SubClassOf.toSmartIri

                    val predicates: Map[SmartIri, PredicateInfoV2] = groupedByPredicate.map {
                        case (predicateIri, predicateRows) =>
                            val (predicateRowsWithLang, predicateRowsWithoutLang) = predicateRows.partition(_.rowMap.contains("resourceClassObjLang"))
                            val objects = predicateRowsWithoutLang.map(_.rowMap("resourceClassObj")).toSet
                            val objectsWithLang = predicateRowsWithLang.map {
                                predicateRow => predicateRow.rowMap("resourceClassObjLang") -> predicateRow.rowMap("resourceClassObj")
                            }.toMap

                            predicateIri -> PredicateInfoV2(
                                predicateIri = predicateIri,
                                ontologyIri = resourceClassIri.getOntologyFromEntity,
                                objects = objects,
                                objectsWithLang = objectsWithLang
                            )
                    }

                    // Get the OWL cardinalities for the class.
                    val allOwlCardinalitiesForClass: Set[OwlCardinalityOnProperty] = resourceCardinalitiesWithInheritance(resourceClassIri)

                    // Identify the link properties, like value properties, and file value properties in the cardinalities.
                    val linkProps = allOwlCardinalitiesForClass.filter(_.isLinkProp).map(_.propertyIri)
                    val linkValueProps = allOwlCardinalitiesForClass.filter(_.isLinkValueProp).map(_.propertyIri)
                    val fileValueProps = allOwlCardinalitiesForClass.filter(_.isFileValueProp).map(_.propertyIri)

                    // Make sure there is a link value property for each link property.
                    val missingLinkValueProps = linkProps.map(_.fromLinkPropToLinkValueProp) -- linkValueProps
                    if (missingLinkValueProps.nonEmpty) {
                        throw InconsistentTriplestoreDataException(s"Resource class $resourceClassIri has cardinalities for one or more link properties without corresponding link value properties. The missing link value property or properties: ${missingLinkValueProps.mkString(", ")}")
                    }

                    // Make sure there is a link property for each link value property.
                    val missingLinkProps = linkValueProps.map(_.fromLinkValuePropToLinkProp) -- linkProps
                    if (missingLinkProps.nonEmpty) {
                        throw InconsistentTriplestoreDataException(s"Resource class $resourceClassIri has cardinalities for one or more link value properties without corresponding link properties. The missing link property or properties: ${missingLinkProps.mkString(", ")}")
                    }

                    // Make maps of the class's direct and inherited cardinalities.

                    val directCardinalities: Map[SmartIri, Cardinality.Value] = directResourceClassCardinalities(resourceClassIri).values.map {
                        cardinalityOnProperty: OwlCardinalityOnProperty => cardinalityOnProperty.toClassDefCardinality
                    }.toMap

                    val inheritedCardinalities: Map[SmartIri, Cardinality.Value] = allOwlCardinalitiesForClass.filterNot {
                        cardinalityOnProperty: OwlCardinalityOnProperty => directCardinalities.contains(cardinalityOnProperty.propertyIri)
                    }.map {
                        cardinalityOnProperty: OwlCardinalityOnProperty => cardinalityOnProperty.toClassDefCardinality
                    }.toMap

                    val ontologyIri = resourceClassIri.getOntologyFromEntity

                    val resourceEntityInfo = ReadClassInfoV2(
                        entityInfoContent = ClassInfoContentV2(
                            rdfType = OntologyConstants.Owl.Class.toSmartIri,
                            classIri = resourceClassIri,
                            ontologyIri = ontologyIri,
                            predicates = new ErrorHandlingMap(predicates, { key: SmartIri => s"Predicate $key not found for resource class $resourceClassIri" }),
                            directCardinalities = directCardinalities,
                            subClassOf = directResourceSubClassOfRelations.getOrElse(resourceClassIri, Set.empty[SmartIri]),
                            ontologySchema = InternalSchema
                        ),
                        canBeInstantiated = !ontologyIri.isKnoraBuiltInDefinitionIri, // Any resource class defined in a project-specific ontology can be instantiated.
                        inheritedCardinalities = inheritedCardinalities,
                        linkProperties = linkProps,
                        linkValueProperties = linkValueProps,
                        fileValueProperties = fileValueProps
                    )

                    resourceClassIri -> resourceEntityInfo
            }

            // Construct a PropertyEntityInfoV2 for each property definition, not taking inheritance into account.
            propertyEntityInfos: Map[SmartIri, ReadPropertyInfoV2] = propertyDefsGrouped.map {
                case (propertyIri, propertyRows) =>
                    // Group the rows for each property by predicate IRI.
                    val groupedByPredicate: Map[SmartIri, Seq[VariableResultsRow]] = propertyRows.groupBy(_.rowMap("propPred").toSmartIri) - OntologyConstants.Rdfs.SubPropertyOf.toSmartIri

                    val predicates: Map[SmartIri, PredicateInfoV2] = groupedByPredicate.map {
                        case (predicateIri, predicateRows) =>
                            val (predicateRowsWithLang, predicateRowsWithoutLang) = predicateRows.partition(_.rowMap.contains("propObjLang"))
                            val objects = predicateRowsWithoutLang.map(_.rowMap("propObj")).toSet
                            val objectsWithLang = predicateRowsWithLang.map {
                                predicateRow => predicateRow.rowMap("propObjLang") -> predicateRow.rowMap("propObj")
                            }.toMap

                            predicateIri -> PredicateInfoV2(
                                predicateIri = predicateIri,
                                ontologyIri = predicateIri.getOntologyFromEntity,
                                objects = objects,
                                objectsWithLang = objectsWithLang
                            )
                    }

                    val ontologyIri = propertyIri.getOntologyFromEntity

                    val propertyEntityInfo = ReadPropertyInfoV2(
                        entityInfoContent = PropertyInfoContentV2(
                            propertyIri = propertyIri,
                            ontologyIri = ontologyIri,
                            predicates = predicates,
                            subPropertyOf = directSubPropertyOfRelations.getOrElse(propertyIri, Set.empty[SmartIri]),
                            ontologySchema = InternalSchema
                        ),
                        isEditable = !ontologyIri.isKnoraBuiltInDefinitionIri, // Any property defined in a project-specific ontology is editable.
                        isLinkProp = linkProps.contains(propertyIri),
                        isLinkValueProp = linkValueProps.contains(propertyIri),
                        isFileValueProp = fileValueProps.contains(propertyIri)
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
            valueBaseClassesGrouped: Map[SmartIri, Seq[VariableResultsRow]] = valueBaseClassesRows.groupBy(_.rowMap("valueBaseClass").toSmartIri)

            // get ontology information about the standoff classes
            standoffClassesSparql <- Future(queries.sparql.v2.txt.getStandoffClassDefinitions(triplestore = settings.triplestoreType).toString())
            standoffClassesResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(standoffClassesSparql)).mapTo[SparqlSelectResponse]
            allStandoffClassRows: Seq[VariableResultsRow] = standoffClassesResponse.results.bindings

            // add the property Iris of the value base classes since they may be used by some standoff classes
            combinedStandoffClasses = valueBaseClassesRows ++ allStandoffClassRows

            // collect all the standoff property Iris from the cardinalities
            standoffPropertyIris = combinedStandoffClasses.foldLeft(Set.empty[SmartIri]) {
                case (acc, row) =>
                    val standoffPropIri: Option[SmartIri] = row.rowMap.get("cardinalityProp").map(_.toKnoraInternalSmartIri)

                    if (standoffPropIri.isDefined) {
                        acc + standoffPropIri.get
                    } else {
                        acc
                    }
            }

            // get information about the standoff properties
            standoffPropsSparql <- Future(queries.sparql.v2.txt.getStandoffPropertyDefinitions(triplestore = settings.triplestoreType, standoffPropertyIris.toList.map(_.toString)).toString())
            standoffPropsResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(standoffPropsSparql)).mapTo[SparqlSelectResponse]
            standoffPropsRows: Seq[VariableResultsRow] = standoffPropsResponse.results.bindings

            // Make a map of IRIs of ontologies to IRIs of standoff classes defined in each one.
            standoffGraphClassMap: Map[SmartIri, Set[SmartIri]] = allStandoffClassRows.groupBy(_.rowMap("graph").toKnoraInternalSmartIri).map {
                case (graphIri: SmartIri, graphRows: Seq[VariableResultsRow]) =>
                    graphIri -> graphRows.map(_.rowMap("standoffClass").toKnoraInternalSmartIri).toSet
            }

            // Make a map of IRIs of ontologies to IRIs of standoff properties defined in each one.
            standoffGraphPropMap: Map[SmartIri, Set[SmartIri]] = standoffPropsRows.groupBy(_.rowMap("graph").toKnoraInternalSmartIri).map {
                case (graphIri, graphRows) =>
                    graphIri -> graphRows.map(_.rowMap("prop").toKnoraInternalSmartIri).toSet
            }

            // Group the rows representing standoff class definitions by standoff class IRI.
            standoffClassesGrouped: Map[SmartIri, Seq[VariableResultsRow]] = allStandoffClassRows.groupBy(_.rowMap("standoffClass").toKnoraInternalSmartIri)
            standoffClassIris = standoffClassesGrouped.keySet

            // Group the rows representing property definitions by property IRI.
            standoffPropertyDefsGrouped: Map[SmartIri, Seq[VariableResultsRow]] = standoffPropsRows.groupBy(_.rowMap("prop").toKnoraInternalSmartIri)

            // Make a map of standoff property IRIs to their immediate base properties.
            directStandoffSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]] = standoffPropertyDefsGrouped.map {
                case (propertyIri, rows) =>
                    val baseProperties = rows.filter(_.rowMap.get("propPred").contains(OntologyConstants.Rdfs.SubPropertyOf)).map(_.rowMap("propObj").toKnoraInternalSmartIri).toSet
                    (propertyIri, baseProperties)
            }

            // Make a map of standoff class IRIs to their immediate base classes.
            directStandoffSubClassOfRelations: Map[SmartIri, Set[SmartIri]] = standoffClassesGrouped.map {
                case (standoffClassIri, rows) =>
                    val baseClasses = rows.filter(_.rowMap.get("standoffClassPred").contains(OntologyConstants.Rdfs.SubClassOf)).map(_.rowMap("standoffClassObj").toKnoraInternalSmartIri).toSet
                    (standoffClassIri, baseClasses)
            }

            // Make a map in which each standoff class IRI points to the full set of its base classes. A class is also
            // a subclass of itself.
            allStandoffSubClassOfRelations: Map[SmartIri, Set[SmartIri]] = standoffClassIris.map {
                standoffClassIri => (standoffClassIri, getAllBaseDefs(standoffClassIri, directStandoffSubClassOfRelations) + standoffClassIri)
            }.toMap

            // Make a map of the cardinalities defined directly on each value base class. Each value base class IRI points to a map of
            // property IRIs to OwlCardinality objects.
            valueBaseClassCardinalities: Map[SmartIri, Map[SmartIri, OwlCardinalityOnProperty]] = valueBaseClassesGrouped.map {
                case (valueBaseClassIri, rows) =>
                    val valueBaseClassCardinalities: Map[SmartIri, OwlCardinalityOnProperty] = rows.filter(_.rowMap.contains("cardinalityProp")).map {
                        cardinalityRow =>
                            val cardinalityRowMap = cardinalityRow.rowMap
                            val propertyIri = cardinalityRowMap("cardinalityProp").toKnoraInternalSmartIri

                            val owlCardinality = OwlCardinalityOnProperty(
                                propertyIri = propertyIri,
                                cardinalityIri = cardinalityRowMap("cardinality").toSmartIri,
                                cardinalityValue = cardinalityRowMap("cardinalityVal").toInt
                            )

                            propertyIri -> owlCardinality
                    }.toMap

                    valueBaseClassIri -> valueBaseClassCardinalities
            }

            // Make a map of the cardinalities defined directly on each standoff class. Each standoff class IRI points to a map of
            // property IRIs to OwlCardinality objects.
            directStandoffClassCardinalities: Map[SmartIri, Map[SmartIri, OwlCardinalityOnProperty]] = standoffClassesGrouped.map {
                case (standoffClassIri, rows) =>
                    val standoffClassCardinalities: Map[SmartIri, OwlCardinalityOnProperty] = rows.filter(_.rowMap.contains("cardinalityProp")).map {
                        cardinalityRow =>
                            val cardinalityRowMap = cardinalityRow.rowMap
                            val propertyIri = cardinalityRowMap("cardinalityProp").toKnoraInternalSmartIri

                            val owlCardinality = OwlCardinalityOnProperty(
                                propertyIri = propertyIri,
                                cardinalityIri = cardinalityRowMap("cardinality").toSmartIri,
                                cardinalityValue = cardinalityRowMap("cardinalityVal").toInt
                            )

                            propertyIri -> owlCardinality
                    }.toMap

                    standoffClassIri -> standoffClassCardinalities
            }

            // Allow each standoff class to inherit cardinalities from its base classes.
            standoffCardinalitiesWithInheritance: Map[SmartIri, Set[OwlCardinalityOnProperty]] = standoffClassIris.map {
                standoffClassIri =>
                    val standoffClassCardinalities: Set[OwlCardinalityOnProperty] = inheritCardinalities(
                        resourceClassIri = standoffClassIri,
                        directSubClassOfRelations = directStandoffSubClassOfRelations,
                        allSubPropertyOfRelations = directStandoffSubPropertyOfRelations,
                        directResourceClassCardinalities = directStandoffClassCardinalities ++ valueBaseClassCardinalities
                    ).values.toSet

                    standoffClassIri -> standoffClassCardinalities
            }.toMap

            standoffClassEntityInfos: Map[SmartIri, ReadClassInfoV2] = standoffClassesGrouped.map {
                case (standoffClassIri, standoffClassRows) =>

                    val standoffGroupedByPredicate: Map[SmartIri, Seq[VariableResultsRow]] = standoffClassRows.filter(_.rowMap.contains("standoffClassPred")).groupBy(_.rowMap("standoffClassPred").toSmartIri) - OntologyConstants.Rdfs.SubClassOf.toSmartIri

                    val predicates: Map[SmartIri, PredicateInfoV2] = standoffGroupedByPredicate.map {
                        case (predicateIri, predicateRows) =>
                            val (predicateRowsWithLang, predicateRowsWithoutLang) = predicateRows.partition(_.rowMap.contains("standoffClassObjLang"))
                            val objects = predicateRowsWithoutLang.map(_.rowMap("standoffClassObj")).toSet
                            val objectsWithLang = predicateRowsWithLang.map {
                                predicateRow => predicateRow.rowMap("standoffClassObjLang") -> predicateRow.rowMap("standoffClassObj")
                            }.toMap

                            predicateIri -> PredicateInfoV2(
                                predicateIri = predicateIri,
                                ontologyIri = standoffClassIri.getOntologyFromEntity,
                                objects = objects,
                                objectsWithLang = objectsWithLang
                            )
                    }

                    val allOwlCardinalitiesForClass: Set[OwlCardinalityOnProperty] = standoffCardinalitiesWithInheritance(standoffClassIri)

                    // Make maps of the class's direct and inherited cardinalities.

                    val directCardinalities: Map[SmartIri, Cardinality.Value] = directStandoffClassCardinalities(standoffClassIri).values.map {
                        cardinalityOnProperty: OwlCardinalityOnProperty => cardinalityOnProperty.toClassDefCardinality
                    }.toMap

                    val inheritedCardinalities: Map[SmartIri, Cardinality.Value] = allOwlCardinalitiesForClass.filterNot {
                        cardinalityOnProperty: OwlCardinalityOnProperty => directCardinalities.contains(cardinalityOnProperty.propertyIri)
                    }.map {
                        cardinalityOnProperty: OwlCardinalityOnProperty => cardinalityOnProperty.toClassDefCardinality
                    }.toMap

                    // determine the data type of the given standoff class IRI
                    // if the resulting set is empty, it is not a typed standoff class
                    val standoffDataType: Set[SmartIri] = allStandoffSubClassOfRelations(standoffClassIri).intersect(StandoffDataTypeClasses.getStandoffClassIris.map(_.toKnoraInternalSmartIri))
                    if (standoffDataType.size > 1) {
                        throw InconsistentTriplestoreDataException(s"standoff class $standoffClassIri is a subclass of more than one standoff data type class: ${standoffDataType.mkString(", ")}")
                    }

                    val standoffInfo = ReadClassInfoV2(
                        entityInfoContent = ClassInfoContentV2(
                            rdfType = OntologyConstants.Owl.Class.toSmartIri,
                            classIri = standoffClassIri,
                            ontologyIri = standoffClassIri.getOntologyFromEntity,
                            predicates = predicates,
                            directCardinalities = directCardinalities,
                            standoffDataType = standoffDataType.headOption match {
                                case Some(dataType: SmartIri) => Some(StandoffDataTypeClasses.lookup(dataType.toString, throw InconsistentTriplestoreDataException(s"$dataType is not a valid standoff data type")))
                                case None => None
                            },
                            subClassOf = directStandoffSubClassOfRelations.getOrElse(standoffClassIri, Set.empty[SmartIri]),
                            ontologySchema = InternalSchema
                        ),
                        inheritedCardinalities = inheritedCardinalities
                    )

                    standoffClassIri -> standoffInfo
            }

            // Make a map in which each standoff property IRI points to the full set of its base properties. A property is also
            // a subproperty of itself.
            allStandoffSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]] = standoffPropertyIris.map {
                propertyIri => (propertyIri, getAllBaseDefs(propertyIri, directStandoffSubPropertyOfRelations) + propertyIri)
            }.toMap


            // Construct a PropertyEntityInfoV2 for each property definition, not taking inheritance into account.
            standoffPropertyEntityInfos: Map[SmartIri, ReadPropertyInfoV2] = standoffPropertyDefsGrouped.map {
                case (standoffPropertyIri, propertyRows) =>
                    // Group the rows for each property by predicate IRI.
                    val groupedByPredicate: Map[SmartIri, Seq[VariableResultsRow]] = propertyRows.groupBy(_.rowMap("propPred").toSmartIri) - OntologyConstants.Rdfs.SubPropertyOf.toSmartIri

                    val predicates: Map[SmartIri, PredicateInfoV2] = groupedByPredicate.map {
                        case (predicateIri, predicateRows) =>
                            val (predicateRowsWithLang, predicateRowsWithoutLang) = predicateRows.partition(_.rowMap.contains("propObjLang"))
                            val objects = predicateRowsWithoutLang.map(_.rowMap("propObj")).toSet
                            val objectsWithLang = predicateRowsWithLang.map {
                                predicateRow => predicateRow.rowMap("propObjLang") -> predicateRow.rowMap("propObj")
                            }.toMap

                            predicateIri -> PredicateInfoV2(
                                predicateIri = predicateIri,
                                ontologyIri = predicateIri.getOntologyFromEntity,
                                objects = objects,
                                objectsWithLang = objectsWithLang
                            )
                    }

                    val standoffPropertyEntityInfo = ReadPropertyInfoV2(
                        entityInfoContent = PropertyInfoContentV2(
                            propertyIri = standoffPropertyIri,
                            ontologyIri = standoffPropertyIri.getOntologyFromEntity,
                            predicates = predicates,
                            subPropertyOf = directStandoffSubPropertyOfRelations.getOrElse(standoffPropertyIri, Set.empty[SmartIri]),
                            ontologySchema = InternalSchema
                        ),
                        isStandoffInternalReferenceProperty = allStandoffSubPropertyOfRelations(standoffPropertyIri).contains(OntologyConstants.KnoraBase.StandoffTagHasInternalReference.toKnoraInternalSmartIri)
                    )

                    standoffPropertyIri -> standoffPropertyEntityInfo
            }

            // collect all the standoff classes that have a data type (i.e. are subclasses of a data type standoff class)
            standoffClassEntityInfosWithDataType: Map[SmartIri, ReadClassInfoV2] = standoffClassEntityInfos.filter {
                case (standoffClassIri: SmartIri, entityInfo: ReadClassInfoV2) =>
                    entityInfo.entityInfoContent.standoffDataType.isDefined
            }

            allClassDefs = resourceEntityInfos ++ KnoraApiV2Simple.Classes ++ KnoraApiV2WithValueObjects.Classes
            allPropertyDefs = propertyEntityInfos ++ KnoraApiV2Simple.Properties ++ KnoraApiV2WithValueObjects.Properties

            // Cache all the data.

            ontologyCacheData: OntologyCacheData = OntologyCacheData(
                ontologyMetadata = new ErrorHandlingMap[SmartIri, OntologyMetadataV2](ontologyMetadata, { key => s"Ontology not found: $key" }),
                ontologyClasses = new ErrorHandlingMap[SmartIri, Set[SmartIri]](graphClassMap, { key => s"Ontology not found: $key" }),
                ontologyProperties = new ErrorHandlingMap[SmartIri, Set[SmartIri]](graphPropMap, { key => s"Ontology not found: $key" }),
                classDefs = new ErrorHandlingMap[SmartIri, ReadClassInfoV2](allClassDefs, { key => s"Class not found: $key" }),
                resourceAndValueSubClassOfRelations = new ErrorHandlingMap[SmartIri, Set[SmartIri]](allResourceSubClassOfRelations ++ allValueSubClassOfRelations, { key => s"Class not found: $key" }),
                resourceSuperClassOfRelations = new ErrorHandlingMap[SmartIri, Set[SmartIri]](allResourceSuperClassOfRelations, { key => s"Class not found: $key" }),
                propertyDefs = new ErrorHandlingMap[SmartIri, ReadPropertyInfoV2](allPropertyDefs, { key => s"Property not found: $key" }),
                subPropertyOfRelations = new ErrorHandlingMap[SmartIri, Set[SmartIri]](allSubPropertyOfRelations, { key => s"Property not found: $key" }),
                ontologyStandoffClasses = new ErrorHandlingMap[SmartIri, Set[SmartIri]](standoffGraphClassMap, { key => s"Ontology not found: $key" }),
                ontologyStandoffProperties = new ErrorHandlingMap[SmartIri, Set[SmartIri]](standoffGraphPropMap, { key => s"Ontology not found: $key" }),
                standoffClassDefs = new ErrorHandlingMap[SmartIri, ReadClassInfoV2](standoffClassEntityInfos, { key => s"Standoff class def not found $key" }),
                standoffPropertyDefs = new ErrorHandlingMap[SmartIri, ReadPropertyInfoV2](standoffPropertyEntityInfos, { key => s"Standoff property def not found $key" }),
                standoffClassDefsWithDataType = new ErrorHandlingMap[SmartIri, ReadClassInfoV2](standoffClassEntityInfosWithDataType, { key => s"Standoff class def with datatype not found $key" }))

            _ = storeCacheData(ontologyCacheData)

        } yield SuccessResponseV2("Ontologies loaded.")
    }

    private def storeCacheData(cacheData: OntologyCacheData): Unit = {
        CacheUtil.put(cacheName = OntologyCacheName, key = OntologyCacheKey, value = cacheData)
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
    private def getEntityInfoResponseV2(classIris: Set[SmartIri] = Set.empty[SmartIri], propertyIris: Set[SmartIri] = Set.empty[SmartIri], userProfile: UserProfileV1): Future[EntityInfoGetResponseV2] = {
        def makeEntityIriForCache(entityIri: SmartIri): SmartIri = {
            if (OntologyConstants.ConstantOntologies.contains(entityIri.getOntologyFromEntity.toString)) {
                // The client is asking about an entity in a constant ontology, so don't translate its IRI.
                entityIri
            } else {
                // The client is asking about a non-constant entity. Translate its IRI to an internal entity IRI.
                entityIri.toOntologySchema(InternalSchema)
            }
        }

        for {
            cacheData <- getCacheData

            classIrisForCache = classIris.map(makeEntityIriForCache)
            propertyIrisForCache = propertyIris.map(makeEntityIriForCache)

            classDefsAvailable: Map[SmartIri, ReadClassInfoV2] = cacheData.classDefs.filterKeys(classIrisForCache)
            propertyDefsAvailable: Map[SmartIri, ReadPropertyInfoV2] = cacheData.propertyDefs.filterKeys(propertyIrisForCache)

            missingClassDefs = classIrisForCache -- classDefsAvailable.keySet
            missingPropertyDefs = propertyIrisForCache -- propertyDefsAvailable.keySet

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
    private def getStandoffEntityInfoResponseV2(standoffClassIris: Set[SmartIri] = Set.empty[SmartIri], standoffPropertyIris: Set[SmartIri] = Set.empty[SmartIri], userProfile: UserProfileV1): Future[StandoffEntityInfoGetResponseV2] = {
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
    private def checkSubClassV2(subClassIri: SmartIri, superClassIri: SmartIri, userProfile: UserProfileV1): Future[CheckSubClassResponseV2] = {
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
    private def getSubClassesV2(resourceClassIri: SmartIri, userProfile: UserProfileV1): Future[SubClassesGetResponseV2] = {
        for {
            cacheData <- getCacheData

            subClassIris = cacheData.resourceSuperClassOfRelations(resourceClassIri).toVector.sorted

            subClasses = subClassIris.map {
                subClassIri =>
                    val resourceClassInfo: ReadClassInfoV2 = cacheData.classDefs(subClassIri)

                    SubClassInfoV2(
                        id = subClassIri,
                        label = resourceClassInfo.entityInfoContent.getPredicateLiteralObject(
                            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
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
      * Gets the [[OntologyEntitiesIriInfoV2]] for an ontology.
      *
      * @param namedGraphIri the IRI of the ontology to query
      * @param userProfile   the profile of the user making the request.
      * @return an [[OntologyEntitiesIriInfoV2]].
      */
    private def getNamedGraphEntityInfoV2ForNamedGraphV2(namedGraphIri: SmartIri, userProfile: UserProfileV1): Future[OntologyEntitiesIriInfoV2] = {
        for {
            cacheData <- getCacheData

            _ = if (!(cacheData.ontologyClasses.contains(namedGraphIri) || cacheData.ontologyProperties.contains(namedGraphIri))) {
                throw NotFoundException(s"Ontology not found: $namedGraphIri")
            }
        } yield OntologyEntitiesIriInfoV2(
            ontologyIri = namedGraphIri,
            propertyIris = cacheData.ontologyProperties.getOrElse(namedGraphIri, Set.empty[SmartIri]),
            classIris = cacheData.ontologyClasses.getOrElse(namedGraphIri, Set.empty[SmartIri]),
            standoffClassIris = cacheData.ontologyStandoffClasses.getOrElse(namedGraphIri, Set.empty[SmartIri]),
            standoffPropertyIris = cacheData.ontologyStandoffProperties.getOrElse(namedGraphIri, Set.empty[SmartIri])
        )
    }

    /**
      * Gets the metadata describing the ontologies that belong to selected projects, or to all projects.
      *
      * @param projectIris the IRIs of the projects selected, or an empty set if all projects are selected.
      * @param userProfile the profile of the user making the request.
      * @return a [[ReadOntologyMetadataV2]].
      */
    private def getOntologyMetadataForProjectsV2(projectIris: Set[SmartIri], userProfile: UserProfileV1): Future[ReadOntologyMetadataV2] = {
        for {
            cacheData <- getCacheData
            projectIriStrs = projectIris.map(_.toString)
            namedGraphInfos: Seq[NamedGraphV1] <- (responderManager ? ProjectsNamedGraphGetV1(userProfile)).mapTo[Seq[NamedGraphV1]]
            filteredNamedGraphInfos = namedGraphInfos.filterNot(_.id == OntologyConstants.KnoraBase.KnoraBaseOntologyIri)
            returnAllOntologies: Boolean = projectIris.isEmpty

            namedGraphsToReturn = if (returnAllOntologies) {
                filteredNamedGraphInfos
            } else {
                filteredNamedGraphInfos.filter(namedGraphInfo => projectIriStrs.contains(namedGraphInfo.project_id))
            }

            ontologyInfoV2s = namedGraphsToReturn.map {
                namedGraphInfo =>
                    val ontologyIri = namedGraphInfo.id.toSmartIri
                    cacheData.ontologyMetadata.getOrElse(ontologyIri, throw InconsistentTriplestoreDataException(s"Ontology $ontologyIri has no metadata"))
            }.toSet

            response = ReadOntologyMetadataV2(
                ontologies = ontologyInfoV2s,
                includeKnoraApi = returnAllOntologies
            )
        } yield response
    }

    /**
      * Requests the entities defined in the given ontologies.
      *
      * @param ontologyIris the IRIs (internal or external) of the ontologies to be queried.
      * @param userProfile  the profile of the user making the request.
      * @return a [[ReadOntologiesV2]].
      */
    private def getOntologyEntitiesV2(ontologyIris: Set[SmartIri], responseSchema: ApiV2Schema, allLanguages: Boolean, userProfile: UserProfileV1): Future[ReadOntologiesV2] = {
        def makeOntologyIriForCache(ontologyIri: SmartIri): SmartIri = {
            if (OntologyConstants.ConstantOntologies.contains(ontologyIri.toString)) {
                // The client is asking about a constant ontology, so don't translate its IRI.
                ontologyIri
            } else {
                // The client is asking about a non-constant ontology. Translate its IRI to an internal ontology IRI.
                ontologyIri.toOntologySchema(InternalSchema)
            }
        }

        for {
            cacheData <- getCacheData

            // Are we returning data in the user's preferred language, or in all available languages?
            userLang = if (!allLanguages) {
                // Just the user's preferred language.
                Some(userProfile.userData.lang)
            } else {
                // All available languages.
                None
            }

            ontologies = ontologyIris.map {
                ontologyIri =>
                    val ontologyIriForCache = makeOntologyIriForCache(ontologyIri)

                    if (!(cacheData.ontologyClasses.contains(ontologyIriForCache) || cacheData.ontologyProperties.contains(ontologyIriForCache))) {
                        throw NotFoundException(s"Ontology not found: $ontologyIriForCache")
                    }

                    ReadOntologyV2(
                        ontologyMetadata = getCachedOntologyMetadata(ontologyIriForCache, cacheData),
                        classes = cacheData.ontologyClasses.getOrElse(ontologyIriForCache, Set.empty[SmartIri]).map {
                            classIri => classIri -> cacheData.classDefs(classIri)
                        }.toMap,
                        properties = cacheData.ontologyProperties.getOrElse(ontologyIriForCache, Set.empty[SmartIri]).map {
                            propertyIri => propertyIri -> cacheData.propertyDefs(propertyIri)
                        }.toMap,
                        standoffClasses = cacheData.ontologyStandoffClasses.getOrElse(ontologyIriForCache, Set.empty[SmartIri]).map {
                            standoffClassIri => standoffClassIri -> cacheData.standoffClassDefs(standoffClassIri)
                        }.toMap,
                        standoffProperties = cacheData.ontologyStandoffProperties.getOrElse(ontologyIriForCache, Set.empty[SmartIri]).map {
                            standoffPropertyIri => standoffPropertyIri -> cacheData.standoffPropertyDefs(standoffPropertyIri)
                        }.toMap,
                        userLang = userLang
                    )
            }.toVector.sortBy(_.ontologyMetadata.ontologyIri)

        } yield ReadOntologiesV2(ontologies = ontologies)
    }

    /**
      * Requests information about OWL classes.
      *
      * @param classIris   the IRIs (internal or external) of the classes to query for.
      * @param userProfile the profile of the user making the request.
      * @return a [[ReadOntologiesV2]].
      */
    private def getClassDefinitionsV2(classIris: Set[SmartIri], responseSchema: ApiV2Schema, allLanguages: Boolean, userProfile: UserProfileV1): Future[ReadOntologiesV2] = {
        for {
            cacheData <- getCacheData

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

            classesInOntologies = classInfoResponse.classInfoMap.values.groupBy(_.entityInfoContent.ontologyIri).map {
                case (ontologyIri, classInfos) =>
                    ReadOntologyV2(
                        ontologyMetadata = getCachedOntologyMetadata(ontologyIri, cacheData),
                        classes = classInfos.map {
                            classInfo => classInfo.entityInfoContent.classIri -> classInfo
                        }.toMap,
                        userLang = userLang
                    )
            }.toSeq

        } yield ReadOntologiesV2(ontologies = classesInOntologies)
    }

    /**
      * Requests information about property entities.
      *
      * @param propertyIris the IRIs (internal or external) of the properties to query for.
      * @param userProfile  the profile of the user making the request.
      * @return a [[ReadOntologiesV2]].
      */
    private def getPropertyDefinitionsV2(propertyIris: Set[SmartIri], allLanguages: Boolean, userProfile: UserProfileV1) = {

        for {
            cacheData <- getCacheData

            propertiesResponse: EntityInfoGetResponseV2 <- getEntityInfoResponseV2(propertyIris = propertyIris, userProfile = userProfile)

            // Are we returning data in the user's preferred language, or in all available languages?
            userLang = if (!allLanguages) {
                // Just the user's preferred language.
                Some(userProfile.userData.lang)
            } else {
                // All available languages.
                None
            }

            propertiesInOntologies = propertiesResponse.propertyInfoMap.values.groupBy(_.entityInfoContent.ontologyIri).map {
                case (ontologyIri, propertyInfos) =>
                    ReadOntologyV2(
                        ontologyMetadata = getCachedOntologyMetadata(ontologyIri, cacheData),
                        properties = propertyInfos.map {
                            propertyInfo => propertyInfo.entityInfoContent.propertyIri -> propertyInfo
                        }.toMap,
                        userLang = userLang
                    )
            }.toSeq

        } yield ReadOntologiesV2(ontologies = propertiesInOntologies)
    }

    private def getCachedOntologyMetadata(ontologyIri: SmartIri, cacheData: OntologyCacheData): OntologyMetadataV2 = {
        ontologyIri.toString match {
            case OntologyConstants.KnoraApiV2Simple.KnoraApiOntologyIri => KnoraApiV2Simple.OntologyMetadata
            case OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiOntologyIri => KnoraApiV2WithValueObjects.OntologyMetadata
            case _ => cacheData.ontologyMetadata.getOrElse(ontologyIri, throw InconsistentTriplestoreDataException(s"No metadata found for ontology $ontologyIri"))
        }
    }

    /**
      * Reads an ontology's metadata.
      *
      * @param internalOntologyIri the ontology's internal IRI.
      * @return an [[OntologyMetadataV2]], or [[None]] if the ontology is not found.
      */
    private def loadOntologyMetadata(internalOntologyIri: SmartIri): Future[Option[OntologyMetadataV2]] = {
        for {
            _ <- Future {
                if (!internalOntologyIri.getOntologySchema.contains(InternalSchema)) {
                    throw AssertionException(s"Expected an internal ontology IRI: $internalOntologyIri")
                }
            }

            getOntologyInfoSparql = queries.sparql.v2.txt.getOntologyInfo(
                triplestore = settings.triplestoreType,
                ontologyIri = internalOntologyIri.toString
            ).toString()

            getOntologyInfoResponse <- (storeManager ? SparqlConstructRequest(getOntologyInfoSparql)).mapTo[SparqlConstructResponse]

            metadata: Option[OntologyMetadataV2] = if (getOntologyInfoResponse.statements.isEmpty) {
                None
            } else {
                getOntologyInfoResponse.statements.get(internalOntologyIri.toString) match {
                    case Some(statements: Seq[(IRI, String)]) =>
                        val statementMap: Map[IRI, Seq[String]] = statements.groupBy {
                            case (pred, _) => pred
                        }.map {
                            case (pred, predStatements) =>
                                pred -> predStatements.map {
                                    case (_, obj) => obj
                                }
                        }

                        val labels: Seq[String] = statementMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[String])
                        val lastModDates: Seq[String] = statementMap.getOrElse(OntologyConstants.KnoraBase.LastModificationDate, Seq.empty[String])

                        val label: String = if (labels.size > 1) {
                            throw InconsistentTriplestoreDataException(s"Ontology $internalOntologyIri has more than one rdfs:label")
                        } else if (labels.isEmpty) {
                            internalOntologyIri.getOntologyName
                        } else {
                            labels.head
                        }

                        val lastModificationDate: Option[Instant] = if (lastModDates.size > 1) {
                            throw InconsistentTriplestoreDataException(s"Ontology $internalOntologyIri has more than one ${OntologyConstants.KnoraBase.LastModificationDate}")
                        } else if (lastModDates.isEmpty) {
                            None
                        } else {
                            val dateStr = lastModDates.head
                            Some(stringFormatter.toInstant(dateStr, throw InconsistentTriplestoreDataException(s"Invalid ${OntologyConstants.KnoraBase.LastModificationDate}: $dateStr")))
                        }

                        Some(OntologyMetadataV2(
                            ontologyIri = internalOntologyIri,
                            label = label,
                            lastModificationDate = lastModificationDate
                        ))

                    case None => None
                }
            }
        } yield metadata
    }

    /**
      * Creates a new, empty ontology.
      *
      * @param createOntologyRequest the request message.
      * @return a [[SuccessResponseV2]].
      */
    private def createOntology(createOntologyRequest: CreateOntologyRequestV2): Future[ReadOntologyMetadataV2] = {
        def makeTaskFuture(internalOntologyIri: SmartIri): Future[ReadOntologyMetadataV2] = {
            for {
                cacheData <- getCacheData

                // Make sure the ontology doesn't already exist.
                existingOntologyMetadata: Option[OntologyMetadataV2] <- loadOntologyMetadata(internalOntologyIri)

                _ = if (existingOntologyMetadata.nonEmpty) {
                    throw BadRequestException(s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)} cannot be created, because it already exists")
                }

                // Create the ontology.

                currentTime: Instant = Instant.now

                createOntologySparql = queries.sparql.v2.txt.createOntology(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri.toString,
                    ontologyIri = internalOntologyIri.toString,
                    ontologyLabel = createOntologyRequest.label,
                    currentTime = currentTime.toString
                ).toString

                _ <- (storeManager ? SparqlUpdateRequest(createOntologySparql)).mapTo[SparqlUpdateResponse]

                // Check that the update was successful.

                maybeNewOntologyMetadata: Option[OntologyMetadataV2] <- loadOntologyMetadata(internalOntologyIri)

                metadata = maybeNewOntologyMetadata match {
                    case Some(newOntologyMetadata) =>
                        if (!newOntologyMetadata.lastModificationDate.contains(currentTime)) {
                            throw UpdateNotPerformedException()
                        } else {
                            newOntologyMetadata
                        }

                    case None => throw UpdateNotPerformedException()
                }

                // Update the ontology cache.

                _ = storeCacheData(cacheData.copy(
                    ontologyMetadata = cacheData.ontologyMetadata + (internalOntologyIri -> metadata)
                ))

                // tell the projects responder that the ontology was created, so it can add it to the project's admin data.
                _ <- (responderManager ? ProjectOntologyAddV1(createOntologyRequest.projectIri.toString, internalOntologyIri.toString, createOntologyRequest.apiRequestID)).mapTo[ProjectInfoV1]

            } yield ReadOntologyMetadataV2(
                ontologies = Set(OntologyMetadataV2(
                    ontologyIri = internalOntologyIri,
                    label = createOntologyRequest.label,
                    lastModificationDate = Some(currentTime)
                ))
            )
        }

        for {
            userProfile <- FastFuture.successful(createOntologyRequest.userProfile)
            projectIri = createOntologyRequest.projectIri

            // check if the requesting user is allowed to create an ontology
            _ = if (!(userProfile.permissionData.isProjectAdmin(projectIri.toString) || userProfile.permissionData.isSystemAdmin)) {
                println(s"userProfile: $userProfile")
                println(s"userProfile.permissionData.isProjectAdmin(<${projectIri.toString}>): ${userProfile.permissionData.isProjectAdmin(projectIri.toString)}")
                throw ForbiddenException(s"A new ontology in the project ${createOntologyRequest.projectIri} can only be created by an admin of that project, or by a system admin.")
            }

            // Get project info for the shortcode.
            maybeProjectInfo: Option[ProjectInfoV1] <- (responderManager ? ProjectInfoByIRIGetV1(projectIri.toString, None)).mapTo[Option[ProjectInfoV1]]
            projectCode: Option[String] = maybeProjectInfo match {
                case Some(pi: ProjectInfoV1) => pi.shortcode
                case None => throw NotFoundException(s"Project $projectIri not found. Cannot add an ontology to a nonexistent project.")
            }

            // Check that the ontology name is valid.
            validOntologyName = stringFormatter.validateProjectSpecificOntologyName(createOntologyRequest.ontologyName, throw BadRequestException(s"Invalid project-specific ontology name: ${createOntologyRequest.ontologyName}"))

            // Make the internal ontology IRI.
            internalOntologyIri = stringFormatter.makeProjectSpecificInternalOntologyIri(validOntologyName, projectCode)

            // Do the remaining pre-update checks and the update while holding an update lock on the ontology.
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID = createOntologyRequest.apiRequestID,
                iri = internalOntologyIri.toString,
                task = () => makeTaskFuture(internalOntologyIri)
            )
        } yield taskResult
    }

    /**
      * Changes ontology metadata.
      *
      * @param changeOntologyMetadataRequest the request to change the metadata.
      * @return a [[ReadOntologyMetadataV2]] containing the new metadata.
      */
    def changeOntologyMetadata(changeOntologyMetadataRequest: ChangeOntologyMetadataRequestV2): Future[ReadOntologyMetadataV2] = {
        def makeTaskFuture(internalOntologyIri: SmartIri): Future[ReadOntologyMetadataV2] = {
            for {
                cacheData <- getCacheData

                // Check that the ontology exists and has not been updated by another user since the client last read its metadata.
                _ <- checkOntologyLastModificationDateBeforeUpdate(internalOntologyIri = internalOntologyIri, expectedLastModificationDate = changeOntologyMetadataRequest.lastModificationDate)

                // Update the metadata.

                currentTime: Instant = Instant.now

                updateSparql = queries.sparql.v2.txt.changeOntologyMetadata(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri.toString,
                    ontologyIri = internalOntologyIri.toString,
                    newLabel = changeOntologyMetadataRequest.label,
                    lastModificationDate = changeOntologyMetadataRequest.lastModificationDate.toString,
                    currentTime = currentTime.toString
                ).toString()

                _ <- (storeManager ? SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

                // Check that the update was successful. This updates the ontology cache.

                maybeNewOntologyMetadata: Option[OntologyMetadataV2] <- loadOntologyMetadata(internalOntologyIri)

                metadata: OntologyMetadataV2 = maybeNewOntologyMetadata match {
                    case Some(newOntologyMetadata) =>
                        if (newOntologyMetadata != OntologyMetadataV2(
                            ontologyIri = internalOntologyIri,
                            label = changeOntologyMetadataRequest.label,
                            lastModificationDate = Some(currentTime)
                        )) {
                            throw UpdateNotPerformedException()
                        } else {
                            newOntologyMetadata
                        }


                    case None => throw NotFoundException(s"Ontology $internalOntologyIri (corresponding to ${internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)}) not found")
                }

                // Update the ontology cache.

                _ = storeCacheData(cacheData.copy(
                    ontologyMetadata = cacheData.ontologyMetadata + (internalOntologyIri -> metadata)
                ))

            } yield ReadOntologyMetadataV2(ontologies = Set(metadata))
        }

        for {
            userProfile <- FastFuture.successful(changeOntologyMetadataRequest.userProfile)

            externalOntologyIri = changeOntologyMetadataRequest.ontologyIri
            _ <- checkExternalOntologyIriForUpdate(externalOntologyIri)

            internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            _ <- checkPermissionsForOntologyUpdate(internalOntologyIri = internalOntologyIri, userProfile = userProfile)

            // Do the remaining pre-update checks and the update while holding an update lock on the ontology.
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID = changeOntologyMetadataRequest.apiRequestID,
                iri = internalOntologyIri.toString,
                task = () => makeTaskFuture(internalOntologyIri = internalOntologyIri)
            )
        } yield taskResult
    }

    /**
      * Creates a property in an existing ontology.
      *
      * @param createPropertyRequest the request to create the property.
      * @return a [[ReadOntologiesV2]] in the internal schema, the containing the definition of the new property.
      */
    private def createProperty(createPropertyRequest: CreatePropertyRequestV2): Future[ReadOntologiesV2] = {
        def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologiesV2] = {
            for {
                cacheData <- getCacheData
                internalPropertyDef = createPropertyRequest.propertyInfoContent.toOntologySchema(InternalSchema)

                // Check that the ontology exists and has not been updated by another user since the client last read it.
                _ <- checkOntologyLastModificationDateBeforeUpdate(internalOntologyIri = internalOntologyIri, expectedLastModificationDate = createPropertyRequest.lastModificationDate)

                // Check that the property's rdf:type is owl:ObjectProperty.

                rdfType: SmartIri = internalPropertyDef.requireIriPredicate(OntologyConstants.Rdf.Type.toSmartIri, throw BadRequestException(s"No rdf:type specified"))

                _ = if (rdfType != OntologyConstants.Owl.ObjectProperty.toSmartIri) {
                    throw BadRequestException(s"Invalid rdf:type for property: $rdfType")
                }

                // Don't allow new file value properties to be created.

                isFileValueProp <- propertyDefIsFileValueProp(internalPropertyDef)

                _ = if (isFileValueProp) {
                    throw BadRequestException("New file value properties cannot be created")
                }

                // Don't allow new link value properties to be created directly, because we do that automatically when creating a link property.

                isLinkValueProp <- propertyDefIsLinkValueProp(internalPropertyDef)

                _ = if (isLinkValueProp) {
                    throw BadRequestException("New link value properties cannot be created directly. Create a link property instead.")
                }

                // Check that the property doesn't exist yet.
                _ = if (cacheData.propertyDefs.contains(internalPropertyIri)) {
                    throw BadRequestException(s"Property ${createPropertyRequest.propertyInfoContent.propertyIri} already exists")
                }

                // Find out whether this property is a link property.
                isLinkProp <- propertyDefIsLinkProp(internalPropertyDef)

                // If we're creating a link property, make the definition of the corresponding link value property.
                maybeLinkValuePropDef: Option[PropertyInfoContentV2] = if (isLinkProp) {
                    val linkValuePropDef = linkPropertyDefToLinkValuePropertyDef(internalPropertyDef)

                    if (cacheData.propertyDefs.contains(linkValuePropDef.propertyIri)) {
                        throw BadRequestException(s"Link value property ${linkValuePropDef.propertyIri} already exists")
                    }

                    Some(linkValuePropDef)
                } else {
                    None
                }

                // Check that the superproperties that are Knora properties exist.

                missingSuperProperties = internalPropertyDef.subPropertyOf.filter(_.isKnoraInternalEntityIri) -- cacheData.propertyDefs.keySet

                _ = if (missingSuperProperties.nonEmpty) {
                    throw BadRequestException(s"One or more specified Knora superproperties do not exist: ${missingSuperProperties.mkString(", ")}")
                }

                // Check that the subject class constraint designates a Knora resource class that exists.

                subjectClassConstraint = internalPropertyDef.requireIriPredicate(OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri, throw BadRequestException(s"No knora-api:subjectType specified"))

                _ = if (!isKnoraInternalResourceClass(subjectClassConstraint, cacheData)) {
                    throw BadRequestException(s"Invalid subject class constraint: ${subjectClassConstraint.toOntologySchema(ApiV2WithValueObjects)}")
                }

                // Check that the object class constraint designates an appropriate class that exists.

                objectClassConstraint: SmartIri = internalPropertyDef.requireIriPredicate(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri, throw BadRequestException(s"No knora-api:objectType specified"))

                // If this is a link property, ensure that its object class constraint refers to a Knora resource class.
                _ = if (isLinkProp) {
                    if (!isKnoraInternalResourceClass(objectClassConstraint, cacheData)) {
                        throw BadRequestException(s"Invalid object class constraint for link property: ${objectClassConstraint.toOntologySchema(ApiV2WithValueObjects)}")
                    }
                } else {
                    // Otherwise, ensure its object class constraint is a Knora value class, and is not LinkValue or a file value class.

                    if (!OntologyConstants.KnoraBase.ValueClasses.contains(objectClassConstraint.toString) ||
                        OntologyConstants.KnoraBase.FileValueClasses.contains(objectClassConstraint.toString) ||
                        objectClassConstraint.toString == OntologyConstants.KnoraBase.LinkValue) {
                        throw BadRequestException(s"Invalid object class constraint for value property: ${objectClassConstraint.toOntologySchema(ApiV2WithValueObjects)}")
                    }
                }

                allSuperPropertyIris: Set[SmartIri] = internalPropertyDef.subPropertyOf.flatMap {
                    superPropertyIri => cacheData.subPropertyOfRelations.getOrElse(superPropertyIri, Set.empty[SmartIri])
                }

                // Check that the subject type is a subclass of the subject types of the base properties.

                _ <- checkPropertyConstraint(
                    newInternalPropertyIri = internalPropertyIri,
                    constraintPredicateIri = OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri,
                    constraintValueInNewProperty = subjectClassConstraint,
                    allSuperPropertyIris = allSuperPropertyIris
                )

                // Check that the object type is a subclass of the object types of the base properties.

                _ <- checkPropertyConstraint(
                    newInternalPropertyIri = internalPropertyIri,
                    constraintPredicateIri = OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
                    constraintValueInNewProperty = objectClassConstraint,
                    allSuperPropertyIris = allSuperPropertyIris
                )

                // Add the property (and the link value property if needed).

                currentTime: Instant = Instant.now

                updateSparql = queries.sparql.v2.txt.createProperty(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri,
                    ontologyIri = internalOntologyIri,
                    propertyDef = internalPropertyDef,
                    maybeLinkValuePropertyDef = maybeLinkValuePropDef,
                    lastModificationDate = createPropertyRequest.lastModificationDate,
                    currentTime = currentTime
                ).toString()

                _ <- (storeManager ? SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

                // Check that the update was successful.

                _ <- checkOntologyLastModificationDateAfterUpdate(internalOntologyIri = internalOntologyIri, expectedLastModificationDate = currentTime)

                // Update the ontology cache.

                readPropertyInfo = ReadPropertyInfoV2(
                    entityInfoContent = internalPropertyDef,
                    isEditable = true,
                    isLinkProp = isLinkProp
                )

                maybeLinkValuePropertyCacheEntry: Option[(SmartIri, ReadPropertyInfoV2)] = maybeLinkValuePropDef.map {
                    linkValuePropDef =>
                        linkValuePropDef.propertyIri -> ReadPropertyInfoV2(
                            entityInfoContent = linkValuePropDef,
                            isLinkValueProp = true
                        )
                }

                updatedOntologyMetadata = cacheData.ontologyMetadata(internalOntologyIri).copy(
                    lastModificationDate = Some(currentTime)
                )

                _ = storeCacheData(cacheData.copy(
                    ontologyMetadata = cacheData.ontologyMetadata + (internalOntologyIri -> updatedOntologyMetadata),
                    propertyDefs = cacheData.propertyDefs ++ maybeLinkValuePropertyCacheEntry + (internalPropertyIri -> readPropertyInfo),
                    subPropertyOfRelations = cacheData.subPropertyOfRelations + (internalPropertyIri -> (allSuperPropertyIris + internalPropertyIri))
                ))

                // Read the data back from the cache.

                response <- getPropertyDefinitionsV2(propertyIris = Set(internalPropertyIri), allLanguages = true, userProfile = createPropertyRequest.userProfile)
            } yield response
        }

        for {
            userProfile <- FastFuture.successful(createPropertyRequest.userProfile)

            externalOntologyIri = createPropertyRequest.propertyInfoContent.ontologyIri
            _ <- checkExternalOntologyIriForUpdate(externalOntologyIri)

            externalPropertyIri = createPropertyRequest.propertyInfoContent.propertyIri
            _ <- checkExternalEntityIriForUpdate(externalEntityIri = externalPropertyIri, externalOntologyIri = externalOntologyIri)

            internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)
            _ <- checkPermissionsForOntologyUpdate(internalOntologyIri = internalOntologyIri, userProfile = userProfile)

            // Do the remaining pre-update checks and the update while holding an update lock on the ontology.
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID = createPropertyRequest.apiRequestID,
                iri = internalOntologyIri.toString,
                task = () => makeTaskFuture(
                    internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema),
                    internalOntologyIri = internalOntologyIri
                )
            )
        } yield taskResult
    }

    /**
      * Checks whether a class IRI refers to a Knora internal resource class.
      *
      * @param classIri the class IRI.
      * @return `true` if the class IRI refers to a Knora internal resource class.
      */
    private def isKnoraInternalResourceClass(classIri: SmartIri, cacheData: OntologyCacheData): Boolean = {
        classIri.isKnoraInternalEntityIri &&
            cacheData.classDefs.contains(classIri) &&
            cacheData.resourceAndValueSubClassOfRelations(classIri).contains(OntologyConstants.KnoraBase.Resource.toSmartIri)
    }

    /**
      * Before creating a new property, checks that the new property's `knora-base:subjectClassConstraint` or `knora-base:objectClassConstraint`
      * is compatible with (i.e. a subclass of) the ones in all its base properties.
      *
      * @param newInternalPropertyIri       the internal IRI of the new property.
      * @param constraintPredicateIri       the internal IRI of the constraint, i.e. `knora-base:subjectClassConstraint` or `knora-base:objectClassConstraint`.
      * @param constraintValueInNewProperty the value of the constraint in the new property.
      * @param allSuperPropertyIris         the IRIs of all the base properties of the new property, including indirect base properties, but not including the new
      *                                     property itself.
      * @return unit, but throws an exception if the new property's constraint value is not valid.
      */
    private def checkPropertyConstraint(newInternalPropertyIri: SmartIri, constraintPredicateIri: SmartIri, constraintValueInNewProperty: SmartIri, allSuperPropertyIris: Set[SmartIri]): Future[Unit] = {
        for {
            cacheData <- getCacheData

            // Get the definitions of all the superproperties of the new property for which definitions are available.
            superPropertyInfos: Set[ReadPropertyInfoV2] = allSuperPropertyIris.flatMap(superPropertyIri => cacheData.propertyDefs.get(superPropertyIri))

            // For each superproperty definition, get the value of the specified constraint in that definition, if any. Here we
            // make a map of superproperty IRIs to superproperty constraint values.
            superPropertyConstraintValues: Map[SmartIri, SmartIri] = superPropertyInfos.flatMap {
                superPropertyInfo =>
                    superPropertyInfo.entityInfoContent.predicates.get(constraintPredicateIri).flatMap(_.objects.headOption.map(_.toSmartIri)).map {
                        superPropertyConstraintValue => superPropertyInfo.entityInfoContent.propertyIri -> superPropertyConstraintValue
                    }
            }.toMap

            // Check that the constraint value in the new property is a subclass of the constraint value in every superproperty.

            superClassesOfConstraintValueInNewProperty = cacheData.resourceAndValueSubClassOfRelations(constraintValueInNewProperty)

            _ = superPropertyConstraintValues.foreach {
                case (superPropertyIri, superPropertyConstraintValue) =>
                    if (!superClassesOfConstraintValueInNewProperty.contains(superPropertyConstraintValue)) {
                        throw BadRequestException(s"Property ${newInternalPropertyIri.toOntologySchema(ApiV2WithValueObjects)} cannot have a ${constraintPredicateIri.toOntologySchema(ApiV2WithValueObjects)} of " +
                            s"${constraintValueInNewProperty.toOntologySchema(ApiV2WithValueObjects)}, because that is not a subclass of " +
                            s"${superPropertyConstraintValue.toOntologySchema(ApiV2WithValueObjects)}, which is the ${constraintPredicateIri.toOntologySchema(ApiV2WithValueObjects)} of " +
                            s"a base property, ${superPropertyIri.toOntologySchema(ApiV2WithValueObjects)}"
                        )
                    }
            }

        } yield ()
    }

    /**
      * Checks the last modification date of an ontology before an update.
      *
      * @param internalOntologyIri          the internal IRI of the ontology.
      * @param expectedLastModificationDate the last modification date that should now be attached to the ontology.
      * @return a failed Future if the expected last modification date is not found.
      */
    private def checkOntologyLastModificationDateBeforeUpdate(internalOntologyIri: SmartIri, expectedLastModificationDate: Instant): Future[Unit] = {
        checkOntologyLastModificationDate(
            internalOntologyIri = internalOntologyIri,
            expectedLastModificationDate = expectedLastModificationDate,
            errorFun = throw BadRequestException(s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)} has been modified by another user, please reload it and try again.")
        )
    }

    /**
      * Checks the last modification date of an ontology after an update.
      *
      * @param internalOntologyIri          the internal IRI of the ontology.
      * @param expectedLastModificationDate the last modification date that should now be attached to the ontology.
      * @return a failed Future if the expected last modification date is not found.
      */
    private def checkOntologyLastModificationDateAfterUpdate(internalOntologyIri: SmartIri, expectedLastModificationDate: Instant): Future[Unit] = {
        checkOntologyLastModificationDate(
            internalOntologyIri = internalOntologyIri,
            expectedLastModificationDate = expectedLastModificationDate,
            errorFun = throw UpdateNotPerformedException(s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)} was not updated. Please report this as a possible bug.")
        )
    }

    /**
      * Checks the last modification date of an ontology.
      *
      * @param internalOntologyIri          the internal IRI of the ontology.
      * @param expectedLastModificationDate the last modification date that the ontology is expected to have.
      * @param errorFun                     a function that throws an exception. It will be called if the expected last modification date is not found.
      * @return a failed Future if the expected last modification date is not found.
      */
    private def checkOntologyLastModificationDate(internalOntologyIri: SmartIri, expectedLastModificationDate: Instant, errorFun: => Nothing): Future[Unit] = {
        for {
            existingOntologyMetadata: Option[OntologyMetadataV2] <- loadOntologyMetadata(internalOntologyIri)

            _ = existingOntologyMetadata match {
                case Some(metadata) =>
                    metadata.lastModificationDate match {
                        case Some(lastModificationDate) =>
                            if (lastModificationDate != expectedLastModificationDate) {
                                errorFun
                            }

                        case None => throw InconsistentTriplestoreDataException(s"Ontology $internalOntologyIri has no ${OntologyConstants.KnoraBase.LastModificationDate}")
                    }

                case None => throw NotFoundException(s"Ontology $internalOntologyIri (corresponding to ${internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)}) not found")
            }
        } yield ()
    }

    /**
      * Checks whether the user has permission to update an ontology.
      *
      * @param internalOntologyIri the internal IRI of the ontology.
      * @param userProfile         the profile of the user making the request.
      * @return a failed Future if the user doesn't have the necessary permission.
      */
    private def checkPermissionsForOntologyUpdate(internalOntologyIri: SmartIri, userProfile: UserProfileV1): Future[Unit] = {
        for {
            // Get the project that the ontology belongs to.
            projectInfo: ProjectInfoResponseV1 <- (responderManager ? ProjectInfoByOntologyGetRequestV1(
                internalOntologyIri.toString,
                Some(userProfile)
            )).mapTo[ProjectInfoResponseV1]


            _ = if (!userProfile.permissionData.isProjectAdmin(projectInfo.project_info.id.toString) && !userProfile.permissionData.isSystemAdmin) {
                // not a project or system admin
                throw ForbiddenException("Ontologies can be modified only by a project or system admin.")
            }
        } yield ()
    }


    /**
      * Checks whether an ontology IRI is valid for an update.
      *
      * @param externalOntologyIri the external IRI of the ontology.
      * @return a failed Future if the IRI is not valid for an update.
      */
    private def checkExternalOntologyIriForUpdate(externalOntologyIri: SmartIri): Future[Unit] = {
        if (!externalOntologyIri.isKnoraOntologyIri) {
            FastFuture.failed(throw BadRequestException(s"Invalid ontology IRI for request: $externalOntologyIri}"))
        } else if (!externalOntologyIri.getOntologySchema.contains(ApiV2WithValueObjects)) {
            FastFuture.failed(throw BadRequestException(s"Invalid ontology schema for request: $externalOntologyIri"))
        } else if (externalOntologyIri.isKnoraBuiltInDefinitionIri) {
            FastFuture.failed(throw BadRequestException(s"Ontology $externalOntologyIri cannot be modified via the Knora API"))
        } else {
            FastFuture.successful(())
        }
    }

    /**
      * Checks whether an entity IRI is valid for an update.
      *
      * @param externalEntityIri   the external IRI of the entity.
      * @param externalOntologyIri the external IRI of the entity's ontology, which should already have been checked
      *                            using `checkExternalOntologyIriForUpdate`.
      * @return a failed Future if the entity IRI is not valid for an update, or is not from the specified ontology.
      */
    private def checkExternalEntityIriForUpdate(externalEntityIri: SmartIri, externalOntologyIri: SmartIri): Future[Unit] = {
        if (!externalEntityIri.isKnoraApiV2EntityIri) {
            FastFuture.failed(throw BadRequestException(s"Invalid entity IRI for request: $externalEntityIri"))
        } else if (!externalEntityIri.getOntologySchema.contains(ApiV2WithValueObjects)) {
            FastFuture.failed(throw BadRequestException(s"Invalid ontology schema for request: $externalEntityIri"))
        } else if (externalEntityIri.getOntologyFromEntity != externalOntologyIri) {
            FastFuture.failed(throw BadRequestException(s"Entity $externalEntityIri cannot be part of $externalOntologyIri"))
        } else if (externalEntityIri.isKnoraBuiltInDefinitionIri) {
            FastFuture.failed(throw BadRequestException(s"Ontology $externalOntologyIri cannot be modified via the Knora API"))
        } else {
            FastFuture.successful(())
        }
    }

    /**
      * Checks whether a property definition represents a link property, according to its rdfs:subPropertyOf.
      *
      * @param internalPropertyDef the property definition, in the internal schema.
      * @return `true` if the definition represents a link property.
      */
    private def propertyDefIsLinkProp(internalPropertyDef: PropertyInfoContentV2): Future[Boolean] = {
        for {
            cacheData <- getCacheData

            isLinkProp = internalPropertyDef.subPropertyOf.exists {
                superPropIri =>
                    cacheData.propertyDefs.get(superPropIri) match {
                        case Some(superPropDef) => superPropDef.isLinkProp
                        case None => false
                    }
            }
        } yield isLinkProp
    }

    /**
      * Checks whether a property definition represents a link value property, according to its rdfs:subPropertyOf.
      *
      * @param internalPropertyDef the property definition, in the internal schema.
      * @return `true` if the definition represents a link value property.
      */
    private def propertyDefIsLinkValueProp(internalPropertyDef: PropertyInfoContentV2): Future[Boolean] = {
        for {
            cacheData <- getCacheData

            isLinkProp = internalPropertyDef.subPropertyOf.exists {
                superPropIri =>
                    cacheData.propertyDefs.get(superPropIri) match {
                        case Some(superPropDef) => superPropDef.isLinkValueProp
                        case None => false
                    }
            }
        } yield isLinkProp
    }

    /**
      * Checks whether a property definition represents a file value property, according to its rdfs:subPropertyOf.
      *
      * @param internalPropertyDef the property definition, in the internal schema.
      * @return `true` if the definition represents a file value property.
      */
    private def propertyDefIsFileValueProp(internalPropertyDef: PropertyInfoContentV2): Future[Boolean] = {
        for {
            cacheData <- getCacheData

            isFileValueProp = internalPropertyDef.subPropertyOf.exists {
                superPropIri =>
                    cacheData.propertyDefs.get(superPropIri) match {
                        case Some(superPropDef) => superPropDef.isFileValueProp
                        case None => false
                    }
            }
        } yield isFileValueProp
    }

    /**
      * Given the definition of a link property, returns the definition of the corresponding link value property.
      *
      * @param internalPropertyDef the definition of the the link property, in the internal schema.
      * @return the definition of the corresponding link value property.
      */
    private def linkPropertyDefToLinkValuePropertyDef(internalPropertyDef: PropertyInfoContentV2): PropertyInfoContentV2 = {
        val newIri = internalPropertyDef.propertyIri.fromLinkPropToLinkValueProp

        val newPredicates: Map[SmartIri, PredicateInfoV2] = (internalPropertyDef.predicates - OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri) +
            (OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri -> PredicateInfoV2(
                predicateIri = OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
                ontologyIri = internalPropertyDef.ontologyIri,
                objects = Set(OntologyConstants.KnoraBase.LinkValue)
            ))

        internalPropertyDef.copy(
            propertyIri = newIri,
            predicates = newPredicates,
            subPropertyOf = Set(OntologyConstants.KnoraBase.HasLinkToValue.toSmartIri)
        )
    }
}