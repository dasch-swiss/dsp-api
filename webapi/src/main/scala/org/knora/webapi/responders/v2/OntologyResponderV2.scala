/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectGetRequestADM, ProjectGetResponseADM}
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.{KnoraCardinalityInfo, OwlCardinalityInfo}
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.messages.v2.responder.standoffmessages.StandoffDataTypeClasses
import org.knora.webapi.responders.{IriLocker, ActorBasedResponder}
import org.knora.webapi.util.ActorUtil.{future2Message, handleUnexpectedMessage}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter.{SalsahGuiAttribute, SalsahGuiAttributeDefinition}
import org.knora.webapi.util._

import scala.concurrent.Future

/**
  * Responds to requests dealing with ontologies.
  *
  * The API v2 ontology responder reads ontologies from two sources:
  *
  * - The triplestore.
  * - The constant knora-api v2 ontologies that are defined in Scala rather than in the triplestore, [[KnoraApiV2SimpleTransformationRules]] and [[KnoraApiV2WithValueObjectsTransformationRules]].
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
class OntologyResponderV2 extends ActorBasedResponder {

    /**
      * The name of the ontology cache.
      */
    private val OntologyCacheName = "ontologyCache"

    /**
      * The cache key under which cached ontology data is stored.
      */
    private val OntologyCacheKey = "ontologyCacheData"

    private case class OntologyCacheData(ontologies: Map[SmartIri, ReadOntologyV2],
                                         subClassOfRelations: Map[SmartIri, Set[SmartIri]],
                                         superClassOfRelations: Map[SmartIri, Set[SmartIri]],
                                         subPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
                                         guiAttributeDefinitions: Map[SmartIri, Set[SalsahGuiAttributeDefinition]],
                                         propertiesUsedInStandoffCardinalities: Set[SmartIri])

    override def receive: Receive = {
        case LoadOntologiesRequestV2(requestingUser) => future2Message(sender(), loadOntologies(requestingUser), log)
        case EntityInfoGetRequestV2(classIris, propertyIris, requestingUser) => future2Message(sender(), getEntityInfoResponseV2(classIris, propertyIris, requestingUser), log)
        case StandoffEntityInfoGetRequestV2(standoffClassIris, standoffPropertyIris, requestingUser) => future2Message(sender(), getStandoffEntityInfoResponseV2(standoffClassIris, standoffPropertyIris, requestingUser), log)
        case StandoffClassesWithDataTypeGetRequestV2(requestingUser) => future2Message(sender(), getStandoffStandoffClassesWithDataTypeV2(requestingUser), log)
        case StandoffAllPropertyEntitiesGetRequestV2(requestingUser) => future2Message(sender(), getAllStandoffPropertyEntitiesV2(requestingUser), log)
        case CheckSubClassRequestV2(subClassIri, superClassIri, requestingUser) => future2Message(sender(), checkSubClassV2(subClassIri, superClassIri, requestingUser), log)
        case SubClassesGetRequestV2(resourceClassIri, requestingUser) => future2Message(sender(), getSubClassesV2(resourceClassIri, requestingUser), log)
        case OntologyKnoraEntityIrisGetRequestV2(namedGraphIri, requestingUser) => future2Message(sender(), getKnoraEntityIrisInNamedGraphV2(namedGraphIri, requestingUser), log)
        case OntologyEntitiesGetRequestV2(ontologyIri, allLanguages, requestingUser) => future2Message(sender(), getOntologyEntitiesV2(ontologyIri, allLanguages, requestingUser), log)
        case ClassesGetRequestV2(resourceClassIris, allLanguages, requestingUser) => future2Message(sender(), getClassDefinitionsFromOntologyV2(resourceClassIris, allLanguages, requestingUser), log)
        case PropertiesGetRequestV2(propertyIris, allLanguages, requestingUser) => future2Message(sender(), getPropertyDefinitionsFromOntologyV2(propertyIris, allLanguages, requestingUser), log)
        case OntologyMetadataGetByProjectRequestV2(projectIris, requestingUser) => future2Message(sender(), getOntologyMetadataForProjectsV2(projectIris, requestingUser), log)
        case OntologyMetadataGetByIriRequestV2(ontologyIris, requestingUser) => future2Message(sender(), getOntologyMetadataByIriV2(ontologyIris, requestingUser), log)
        case createOntologyRequest: CreateOntologyRequestV2 => future2Message(sender(), createOntology(createOntologyRequest), log)
        case changeOntologyMetadataRequest: ChangeOntologyMetadataRequestV2 => future2Message(sender(), changeOntologyMetadata(changeOntologyMetadataRequest), log)
        case createClassRequest: CreateClassRequestV2 => future2Message(sender(), createClass(createClassRequest), log)
        case changeClassLabelsOrCommentsRequest: ChangeClassLabelsOrCommentsRequestV2 => future2Message(sender(), changeClassLabelsOrComments(changeClassLabelsOrCommentsRequest), log)
        case addCardinalitiesToClassRequest: AddCardinalitiesToClassRequestV2 => future2Message(sender(), addCardinalitiesToClass(addCardinalitiesToClassRequest), log)
        case changeCardinalitiesRequest: ChangeCardinalitiesRequestV2 => future2Message(sender(), changeClassCardinalities(changeCardinalitiesRequest), log)
        case deleteClassRequest: DeleteClassRequestV2 => future2Message(sender(), deleteClass(deleteClassRequest), log)
        case createPropertyRequest: CreatePropertyRequestV2 => future2Message(sender(), createProperty(createPropertyRequest), log)
        case changePropertyLabelsOrCommentsRequest: ChangePropertyLabelsOrCommentsRequestV2 => future2Message(sender(), changePropertyLabelsOrComments(changePropertyLabelsOrCommentsRequest), log)
        case deletePropertyRequest: DeletePropertyRequestV2 => future2Message(sender(), deleteProperty(deletePropertyRequest), log)
        case deleteOntologyRequest: DeleteOntologyRequestV2 => future2Message(sender(), deleteOntology(deleteOntologyRequest), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /**
      * Represents the contents of a named graph representing an ontology.
      *
      * @param ontologyIri       the ontology IRI, which is also the IRI of the named graph.
      * @param constructResponse the triplestore's response to a CONSTRUCT query that gets the contents of the named graph.
      */
    private case class OntologyGraph(ontologyIri: SmartIri, constructResponse: SparqlExtendedConstructResponse)

    /**
      * Loads and caches all ontology information.
      *
      * @param requestingUser the user making the request.
      * @return a [[SuccessResponseV2]].
      */
    private def loadOntologies(requestingUser: UserADM): Future[SuccessResponseV2] = {
        for {
            _ <- Future {
                if (!(requestingUser.id == KnoraSystemInstances.Users.SystemUser.id || requestingUser.permissions.isSystemAdmin)) {
                    throw ForbiddenException(s"Only a system administrator can reload ontologies")
                }
            }

            // Get all ontology metadata.
            allOntologyMetdataSparql <- FastFuture.successful(queries.sparql.v2.txt.getAllOntologyMetadata(triplestore = settings.triplestoreType).toString())
            allOntologyMetadataResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(allOntologyMetdataSparql)).mapTo[SparqlSelectResponse]
            allOntologyMetadata: Map[SmartIri, OntologyMetadataV2] = buildOntologyMetadata(allOntologyMetadataResponse)

            // Get the contents of each named graph containing an ontology.
            ontologyGraphResponseFutures: Iterable[Future[OntologyGraph]] = allOntologyMetadata.keys.map {
                ontologyIri =>
                    val ontologyGraphConstructQuery = queries.sparql.v2.txt.getOntologyGraph(
                        triplestore = settings.triplestoreType,
                        ontologyGraph = ontologyIri
                    ).toString

                    (storeManager ? SparqlExtendedConstructRequest(ontologyGraphConstructQuery)).mapTo[SparqlExtendedConstructResponse].map {
                        response => OntologyGraph(ontologyIri = ontologyIri, constructResponse = response)
                    }
            }

            ontologyGraphs: Iterable[OntologyGraph] <- Future.sequence(ontologyGraphResponseFutures)

            _ = makeOntologyCache(allOntologyMetadata, ontologyGraphs)

        } yield SuccessResponseV2("Ontologies loaded.")
    }

    /**
      * Given ontology metdata and ontology graphs read from the triplestore, constructs the ontology cache.
      *
      * @param allOntologyMetadata a map of ontology IRIs to ontology metadata.
      * @param ontologyGraphs      a list of ontology graphs.
      */
    private def makeOntologyCache(allOntologyMetadata: Map[SmartIri, OntologyMetadataV2], ontologyGraphs: Iterable[OntologyGraph]): Unit = {
        // Get the IRIs of all the entities in each ontology.

        // A map of ontology IRIs to class IRIs in each ontology.
        val classIrisPerOntology: Map[SmartIri, Set[SmartIri]] = getEntityIrisFromOntologyGraphs(
            ontologyGraphs = ontologyGraphs,
            entityTypes = Set(OntologyConstants.Owl.Class)
        )

        // A map of ontology IRIs to property IRIs in each ontology.
        val propertyIrisPerOntology: Map[SmartIri, Set[SmartIri]] = getEntityIrisFromOntologyGraphs(
            ontologyGraphs = ontologyGraphs,
            entityTypes = Set(
                OntologyConstants.Owl.ObjectProperty,
                OntologyConstants.Owl.DatatypeProperty,
                OntologyConstants.Owl.AnnotationProperty,
                OntologyConstants.Rdf.Property
            )
        )

        // A map of ontology IRIs to named individual IRIs in each ontology.
        val individualIrisPerOntology: Map[SmartIri, Set[SmartIri]] = getEntityIrisFromOntologyGraphs(
            ontologyGraphs = ontologyGraphs,
            entityTypes = Set(OntologyConstants.Owl.NamedIndividual)
        )

        // Construct entity definitions.

        // A map of class IRIs to class definitions.
        val allClassDefs: Map[SmartIri, ClassInfoContentV2] = ontologyGraphs.flatMap {
            ontologyGraph =>
                constructResponseToClassDefinitions(
                    classIris = classIrisPerOntology(ontologyGraph.ontologyIri),
                    constructResponse = ontologyGraph.constructResponse
                )
        }.toMap

        // A map of property IRIs to property definitions.
        val allPropertyDefs: Map[SmartIri, PropertyInfoContentV2] = ontologyGraphs.flatMap {
            ontologyGraph =>
                constructResponseToPropertyDefinitions(
                    propertyIris = propertyIrisPerOntology(ontologyGraph.ontologyIri),
                    constructResponse = ontologyGraph.constructResponse
                )
        }.toMap

        // A map of OWL named individual IRIs to named individuals.
        val allIndividuals: Map[SmartIri, IndividualInfoContentV2] = ontologyGraphs.flatMap {
            ontologyGraph =>
                constructResponseToIndividuals(
                    individualIris = individualIrisPerOntology(ontologyGraph.ontologyIri),
                    constructResponse = ontologyGraph.constructResponse
                )
        }.toMap

        // A map of salsah-gui:Guielement individuals to their GUI attribute definitions.
        val allGuiAttributeDefinitions: Map[SmartIri, Set[SalsahGuiAttributeDefinition]] = makeGuiAttributeDefinitions(allIndividuals)

        // Determine relations between entities.

        // A map of class IRIs to their immediate base classes.
        val directSubClassOfRelations: Map[SmartIri, Set[SmartIri]] = allClassDefs.map {
            case (classIri, classDef) => classIri -> classDef.subClassOf
        }

        // A map of property IRIs to their immediate base properties.
        val directSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]] = allPropertyDefs.map {
            case (propertyIri, propertyDef) => propertyIri -> propertyDef.subPropertyOf
        }

        val allClassIris = allClassDefs.keySet
        val allPropertyIris = allPropertyDefs.keySet

        // A map in which each resource class IRI points to the full set of its base classes. A class is also
        // a subclass of itself.
        val allSubClassOfRelations: Map[SmartIri, Set[SmartIri]] = allClassIris.map {
            classIri => (classIri, getAllBaseDefs(classIri, directSubClassOfRelations) + classIri)
        }.toMap

        // A map in which each resource class IRI points to the full set of its subclasses. A class is also
        // a subclass of itself.
        val allSuperClassOfRelations: Map[SmartIri, Set[SmartIri]] = calculateSuperClassOfRelations(allSubClassOfRelations)

        // Make a map in which each property IRI points to the full set of its base properties. A property is also
        // a subproperty of itself.
        val allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]] = allPropertyIris.map {
            propertyIri => (propertyIri, getAllBaseDefs(propertyIri, directSubPropertyOfRelations) + propertyIri)
        }.toMap

        // A set of all subproperties of knora-base:resourceProperty.
        val allKnoraResourceProps: Set[SmartIri] = allPropertyIris.filter {
            prop =>
                val allPropSubPropertyOfRelations = allSubPropertyOfRelations(prop)
                prop == OntologyConstants.KnoraBase.ResourceProperty.toSmartIri ||
                    allPropSubPropertyOfRelations.contains(OntologyConstants.KnoraBase.HasValue.toSmartIri) ||
                    allPropSubPropertyOfRelations.contains(OntologyConstants.KnoraBase.HasLinkTo.toSmartIri)
        }

        // A set of all subproperties of knora-base:hasLinkTo.
        val allLinkProps: Set[SmartIri] = allPropertyIris.filter(prop => allSubPropertyOfRelations(prop).contains(OntologyConstants.KnoraBase.HasLinkTo.toSmartIri))

        // A set of all subproperties of knora-base:hasLinkToValue.
        val allLinkValueProps: Set[SmartIri] = allPropertyIris.filter(prop => allSubPropertyOfRelations(prop).contains(OntologyConstants.KnoraBase.HasLinkToValue.toSmartIri))

        // A set of all subproperties of knora-base:hasFileValue.
        val allFileValueProps: Set[SmartIri] = allPropertyIris.filter(prop => allSubPropertyOfRelations(prop).contains(OntologyConstants.KnoraBase.HasFileValue.toSmartIri))

        // A map of the cardinalities defined directly on each resource class. Each class IRI points to a map of
        // property IRIs to OwlCardinalityInfo objects.
        val directClassCardinalities: Map[SmartIri, Map[SmartIri, OwlCardinalityInfo]] = allClassDefs.map {
            case (classIri, classDef) =>
                classIri -> classDef.directCardinalities.map {
                    case (propertyIri, knoraCardinalityInfo) =>
                        propertyIri -> Cardinality.knoraCardinality2OwlCardinality(knoraCardinalityInfo)
                }
        }

        // Allow each class to inherit cardinalities from its base classes.
        val classCardinalitiesWithInheritance: Map[SmartIri, Map[SmartIri, OwlCardinalityInfo]] = allClassIris.map {
            resourceClassIri =>
                val resourceClassCardinalities: Map[SmartIri, OwlCardinalityInfo] = inheritCardinalitiesInLoadedClass(
                    classIri = resourceClassIri,
                    directSubClassOfRelations = directSubClassOfRelations,
                    allSubPropertyOfRelations = allSubPropertyOfRelations,
                    directClassCardinalities = directClassCardinalities
                )

                resourceClassIri -> resourceClassCardinalities
        }.toMap

        // Construct a ReadClassInfoV2 for each class.
        val readClassInfos: Map[SmartIri, ReadClassInfoV2] = makeReadClassInfos(
            classDefs = allClassDefs,
            directClassCardinalities = directClassCardinalities,
            classCardinalitiesWithInheritance = classCardinalitiesWithInheritance,
            directSubClassOfRelations = directSubClassOfRelations,
            allSubClassOfRelations = allSubClassOfRelations,
            allSubPropertyOfRelations = allSubPropertyOfRelations,
            allPropertyDefs = allPropertyDefs,
            allKnoraResourceProps = allKnoraResourceProps,
            allLinkProps = allLinkProps,
            allLinkValueProps = allLinkValueProps,
            allFileValueProps = allFileValueProps
        )

        // Construct a ReadPropertyInfoV2 for each property definition.
        val readPropertyInfos: Map[SmartIri, ReadPropertyInfoV2] = makeReadPropertyInfos(
            propertyDefs = allPropertyDefs,
            directSubPropertyOfRelations = directSubPropertyOfRelations,
            allSubPropertyOfRelations = allSubPropertyOfRelations,
            allSubClassOfRelations = allSubClassOfRelations,
            allGuiAttributeDefinitions = allGuiAttributeDefinitions,
            allKnoraResourceProps = allKnoraResourceProps,
            allLinkProps = allLinkProps,
            allLinkValueProps = allLinkValueProps,
            allFileValueProps = allFileValueProps
        )

        // Construct a ReadIndividualV2 for each OWL named individual.
        val readIndividualInfos = makeReadIndividualInfos(allIndividuals)

        // A ReadOntologyV2 for each ontology to be cached.
        val readOntologies: Map[SmartIri, ReadOntologyV2] = allOntologyMetadata.map {
            case (ontologyIri, ontologyMetadata) =>
                ontologyIri -> ReadOntologyV2(
                    ontologyMetadata = ontologyMetadata,
                    classes = readClassInfos.filter {
                        case (classIri, _) => classIri.getOntologyFromEntity == ontologyIri
                    },
                    properties = readPropertyInfos.filter {
                        case (propertyIri, _) => propertyIri.getOntologyFromEntity == ontologyIri
                    },
                    individuals = readIndividualInfos.filter {
                        case (individualIri, _) => individualIri.getOntologyFromEntity == ontologyIri
                    },
                    isWholeOntology = true
                )
        }

        // A set of the IRIs of all properties used in cardinalities in standoff classes.
        val propertiesUsedInStandoffCardinalities: Set[SmartIri] = readClassInfos.flatMap {
            case (_, readClassInfo) =>
                if (readClassInfo.isStandoffClass) {
                    readClassInfo.allCardinalities.keySet
                } else {
                    Set.empty[SmartIri]
                }
        }.toSet

        // Construct the ontology cache data.
        val ontologyCacheData: OntologyCacheData = OntologyCacheData(
            ontologies = new ErrorHandlingMap[SmartIri, ReadOntologyV2](readOntologies, { key => s"Ontology not found: $key" }),
            subClassOfRelations = new ErrorHandlingMap[SmartIri, Set[SmartIri]](allSubClassOfRelations, { key => s"Class not found: $key" }),
            superClassOfRelations = new ErrorHandlingMap[SmartIri, Set[SmartIri]](allSuperClassOfRelations, { key => s"Class not found: $key" }),
            subPropertyOfRelations = new ErrorHandlingMap[SmartIri, Set[SmartIri]](allSubPropertyOfRelations, { key => s"Property not found: $key" }),
            guiAttributeDefinitions = new ErrorHandlingMap[SmartIri, Set[SalsahGuiAttributeDefinition]](allGuiAttributeDefinitions, { key => s"salsah-gui:Guielement not found: $key" }),
            propertiesUsedInStandoffCardinalities = propertiesUsedInStandoffCardinalities
        )

        // Check property subject and object class constraints.

        readPropertyInfos.foreach {
            case (propertyIri, readPropertyInfo) =>
                val allSuperPropertyIris: Set[SmartIri] = allSubPropertyOfRelations.getOrElse(propertyIri, Set.empty[SmartIri])

                readPropertyInfo.entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri) match {
                    case Some(subjectClassConstraint) =>
                        // Each property's subject class constraint, if provided, must be a subclass of the subject class constraints of all its base properties.
                        checkPropertyConstraint(
                            cacheData = ontologyCacheData,
                            internalPropertyIri = propertyIri,
                            constraintPredicateIri = OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri,
                            constraintValueToBeChecked = subjectClassConstraint,
                            allSuperPropertyIris = allSuperPropertyIris,
                            errorSchema = InternalSchema,
                            errorFun = { msg: String => throw InconsistentTriplestoreDataException(msg) }
                        )

                        // If the property is defined in a project-specific ontology, its subject class constraint, if provided, must be a Knora resource or standoff class.
                        if (!propertyIri.isKnoraBuiltInDefinitionIri) {
                            val baseClassesOfSubjectClassConstraint = allSubClassOfRelations(subjectClassConstraint)

                            if (!(baseClassesOfSubjectClassConstraint.contains(OntologyConstants.KnoraBase.Resource.toSmartIri) ||
                                baseClassesOfSubjectClassConstraint.contains(OntologyConstants.KnoraBase.StandoffTag.toSmartIri))) {
                                throw InconsistentTriplestoreDataException(s"Property $propertyIri is defined in a project-specific ontology, but its knora-base:subjectClassConstraint, $subjectClassConstraint, is not a subclass of knora-base:Resource or knora-base:StandoffTag")
                            }
                        }

                    case None => ()
                }

                readPropertyInfo.entityInfoContent.getPredicateIriObject(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri) match {
                    case Some(objectClassConstraint) =>
                        // Each property's object class constraint, if provided, must be a subclass of the object class constraints of all its base properties.
                        checkPropertyConstraint(
                            cacheData = ontologyCacheData,
                            internalPropertyIri = propertyIri,
                            constraintPredicateIri = OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
                            constraintValueToBeChecked = objectClassConstraint,
                            allSuperPropertyIris = allSuperPropertyIris,
                            errorSchema = InternalSchema,
                            errorFun = { msg: String => throw InconsistentTriplestoreDataException(msg) }
                        )

                    case None =>
                        // A resource property must have an object class constraint, unless it's knora-base:resourceProperty.
                        if (readPropertyInfo.isResourceProp && propertyIri != OntologyConstants.KnoraBase.ResourceProperty.toSmartIri) {
                            throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")
                        }
                }
        }

        // Check references between ontologies.
        checkReferencesBetweenOntologies(ontologyCacheData)

        // Update the cache.
        storeCacheData(ontologyCacheData)
    }

    /**
      * Checks a reference between an ontology entity and another ontology entity to see if the target
      * is in a non-shared ontology in another project.
      *
      * @param ontologyCacheData the ontology cache data.
      * @param sourceEntityIri the entity whose definition contains the reference.
      * @param targetEntityIri the entity that's the target of the reference.
      * @param errorFun a function that throws an exception with the specified message if the reference is invalid.
      */
    private def checkOntologyReferenceInEntity(ontologyCacheData: OntologyCacheData, sourceEntityIri: SmartIri, targetEntityIri: SmartIri, errorFun: String => Nothing): Unit = {
        if (targetEntityIri.isKnoraDefinitionIri) {
            val sourceOntologyIri = sourceEntityIri.getOntologyFromEntity
            val sourceOntologyMetadata = ontologyCacheData.ontologies(sourceOntologyIri).ontologyMetadata

            val targetOntologyIri = targetEntityIri.getOntologyFromEntity
            val targetOntologyMetadata = ontologyCacheData.ontologies(targetOntologyIri).ontologyMetadata

            if (sourceOntologyMetadata.projectIri != targetOntologyMetadata.projectIri) {
                if (!(targetOntologyIri.isKnoraBuiltInDefinitionIri || targetOntologyIri.isKnoraSharedDefinitionIri)) {
                    errorFun(s"Entity $sourceEntityIri refers to entity $targetEntityIri, which is in a non-shared ontology that belongs to another project")
                }
            }
        }
    }

    /**
      * Checks a property definition to ensure that it doesn't refer to any other non-shared ontologies.
      *
      * @param ontologyCacheData the ontology cache data.
      * @param propertyDef the property definition.
      * @param errorFun a function that throws an exception with the specified message if the property definition is invalid.
      */
    private def checkOntologyReferencesInPropertyDef(ontologyCacheData: OntologyCacheData, propertyDef: PropertyInfoContentV2, errorFun: String => Nothing): Unit = {
        // Ensure that the property isn't a subproperty of any property in a non-shared ontology in another project.

        for (subPropertyOf <- propertyDef.subPropertyOf) {
            checkOntologyReferenceInEntity(
                ontologyCacheData = ontologyCacheData,
                sourceEntityIri = propertyDef.propertyIri,
                targetEntityIri = subPropertyOf,
                errorFun = errorFun
            )
        }

        // Ensure that the property doesn't have subject or object constraints pointing to a non-shared ontology in another project.

        propertyDef.getPredicateIriObject(OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri) match {
            case Some(subjectClassConstraint) =>
                checkOntologyReferenceInEntity(
                    ontologyCacheData = ontologyCacheData,
                    sourceEntityIri = propertyDef.propertyIri,
                    targetEntityIri = subjectClassConstraint,
                    errorFun = errorFun
                )

            case None => ()
        }

        propertyDef.getPredicateIriObject(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri) match {
            case Some(objectClassConstraint) =>
                checkOntologyReferenceInEntity(
                    ontologyCacheData = ontologyCacheData,
                    sourceEntityIri = propertyDef.propertyIri,
                    targetEntityIri = objectClassConstraint,
                    errorFun = errorFun
                )

            case None => ()
        }
    }

    /**
      * Checks a class definition to ensure that it doesn't refer to any non-shared ontologies in other projects.
      *
      * @param ontologyCacheData the ontology cache data.
      * @param classDef the class definition.
      * @param errorFun a function that throws an exception with the specified message if the property definition is invalid.
      */
    private def checkOntologyReferencesInClassDef(ontologyCacheData: OntologyCacheData, classDef: ClassInfoContentV2, errorFun: String => Nothing): Unit = {
        for (subClassOf <- classDef.subClassOf) {
            checkOntologyReferenceInEntity(
                ontologyCacheData = ontologyCacheData,
                sourceEntityIri = classDef.classIri,
                targetEntityIri = subClassOf,
                errorFun = errorFun
            )
        }

        for (cardinalityPropIri <- classDef.directCardinalities.keys) {
            checkOntologyReferenceInEntity(
                ontologyCacheData = ontologyCacheData,
                sourceEntityIri = classDef.classIri,
                targetEntityIri = cardinalityPropIri,
                errorFun = errorFun
            )
        }
    }

    /**
      * Checks references between ontologies to ensure that they do not refer to non-shared ontologies in other projects.
      *
      * @param ontologyCacheData the ontology cache data.
      */
    private def checkReferencesBetweenOntologies(ontologyCacheData: OntologyCacheData): Unit = {
        for (ontology <- ontologyCacheData.ontologies.values) {
            for (propertyInfo <- ontology.properties.values) {
                checkOntologyReferencesInPropertyDef(
                    ontologyCacheData = ontologyCacheData,
                    propertyDef = propertyInfo.entityInfoContent,
                    errorFun = { msg: String => throw InconsistentTriplestoreDataException(msg) }
                )
            }

            for (classInfo <- ontology.classes.values) {
                checkOntologyReferencesInClassDef(
                    ontologyCacheData = ontologyCacheData,
                    classDef = classInfo.entityInfoContent,
                    errorFun = { msg: String => throw InconsistentTriplestoreDataException(msg) }
                )
            }
        }
    }

    /**
      * Given a list of ontology graphs, finds the IRIs of all subjects whose `rdf:type` is contained in a given set of types.
      *
      * @param ontologyGraphs a list of ontology graphs.
      * @param entityTypes    the types of entities to be found.
      * @return a map of ontology IRIs to sets of the IRIs of entities with matching types in each ontology.
      */
    private def getEntityIrisFromOntologyGraphs(ontologyGraphs: Iterable[OntologyGraph], entityTypes: Set[IRI]): Map[SmartIri, Set[SmartIri]] = {
        val entityTypesAsIriLiterals = entityTypes.map(entityType => IriLiteralV2(entityType))

        ontologyGraphs.map {
            ontologyGraph =>
                val entityIrisInGraph: Set[SmartIri] = ontologyGraph.constructResponse.statements.foldLeft(Set.empty[SmartIri]) {
                    case (acc, (subjectIri: IriSubjectV2, subjectStatements)) =>
                        val subjectTypeLiterals: Seq[IriLiteralV2] = subjectStatements.getOrElse(OntologyConstants.Rdf.Type, throw InconsistentTriplestoreDataException(s"Subject $subjectIri has no rdf:type")).collect {
                            case iriLiteral: IriLiteralV2 => iriLiteral
                        }

                        if (subjectTypeLiterals.exists(entityTypesAsIriLiterals.contains)) {
                            acc + subjectIri.value.toSmartIri
                        } else {
                            acc
                        }

                    case (acc, _) => acc
                }

                ontologyGraph.ontologyIri -> entityIrisInGraph
        }.toMap
    }

    /**
      * Given the triplestore's response to `getAllOntologyMetadata.scala.txt`, constructs a map of ontology IRIs
      * to ontology metadata for the ontology cache.
      *
      * @param allOntologyMetadataResponse the triplestore's response to the SPARQL query `getallOntologyMetadata.scala.txt`.
      * @return a map of ontology IRIs to ontology metadata.
      */
    private def buildOntologyMetadata(allOntologyMetadataResponse: SparqlSelectResponse): Map[SmartIri, OntologyMetadataV2] = {
        allOntologyMetadataResponse.results.bindings.groupBy(_.rowMap("ontologyGraph")).map {
            case (ontologyGraph: IRI, rows: Seq[VariableResultsRow]) =>
                val ontologyIri = rows.head.rowMap("ontologyIri")

                if (ontologyIri != ontologyGraph) {
                    throw InconsistentTriplestoreDataException(s"Ontology $ontologyIri must be stored in named graph $ontologyIri, but it is in $ontologyGraph")
                }

                val ontologySmartIri = ontologyIri.toSmartIri

                if (!ontologySmartIri.isKnoraOntologyIri) {
                    throw InconsistentTriplestoreDataException(s"Ontology $ontologySmartIri is not a Knora ontology")
                }

                val ontologyMetadataMap: Map[IRI, String] = rows.map {
                    row =>
                        val pred = row.rowMap.getOrElse("ontologyPred", throw InconsistentTriplestoreDataException(s"Empty predicate in ontology $ontologyIri"))
                        val obj = row.rowMap.getOrElse("ontologyObj", throw InconsistentTriplestoreDataException(s"Empty object for predicate $pred in ontology $ontologyIri"))
                        pred -> obj
                }.toMap

                val projectIri = ontologyMetadataMap.getOrElse(OntologyConstants.KnoraBase.AttachedToProject, throw InconsistentTriplestoreDataException(s"Ontology $ontologyIri has no knora-base:attachedToProject")).toSmartIri
                val ontologyLabel = ontologyMetadataMap.getOrElse(OntologyConstants.Rdfs.Label, ontologySmartIri.getOntologyName)
                val lastModificationDate = ontologyMetadataMap.get(OntologyConstants.KnoraBase.LastModificationDate).map(instant => stringFormatter.toInstant(instant, throw InconsistentTriplestoreDataException(s"Invalid UTC instant: $instant")))

                ontologySmartIri -> OntologyMetadataV2(
                    ontologyIri = ontologySmartIri,
                    projectIri = Some(projectIri),
                    label = Some(ontologyLabel),
                    lastModificationDate = lastModificationDate
                )
        }
    }

    /**
      * Constructs a map of class IRIs to [[ReadClassInfoV2]] instances, based on class definitions loaded from the
      * triplestore.
      *
      * @param classDefs                         a map of class IRIs to class definitions.
      * @param directClassCardinalities          a map of the cardinalities defined directly on each class. Each resource class
      *                                          IRI points to a map of property IRIs to [[OwlCardinalityInfo]] objects.
      * @param classCardinalitiesWithInheritance a map of the cardinalities defined directly on each class or inherited from
      *                                          base classes. Each class IRI points to a map of property IRIs to
      *                                          [[OwlCardinalityInfo]] objects.
      * @param directSubClassOfRelations         a map of class IRIs to their immediate base classes.
      * @param allSubClassOfRelations            a map of class IRIs to all their base classes.
      * @param allSubPropertyOfRelations         a map of property IRIs to all their base properties.
      * @param allPropertyDefs                   a map of property IRIs to property definitions.
      * @param allKnoraResourceProps             a set of the IRIs of all Knora resource properties.
      * @param allLinkProps                      a set of the IRIs of all link properties.
      * @param allLinkValueProps                 a set of the IRIs of link value properties.
      * @param allFileValueProps                 a set of the IRIs of all file value properties.
      * @return a map of resource class IRIs to their definitions.
      */
    private def makeReadClassInfos(classDefs: Map[SmartIri, ClassInfoContentV2],
                                   directClassCardinalities: Map[SmartIri, Map[SmartIri, OwlCardinalityInfo]],
                                   classCardinalitiesWithInheritance: Map[SmartIri, Map[SmartIri, OwlCardinalityInfo]],
                                   directSubClassOfRelations: Map[SmartIri, Set[SmartIri]],
                                   allSubClassOfRelations: Map[SmartIri, Set[SmartIri]],
                                   allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
                                   allPropertyDefs: Map[SmartIri, PropertyInfoContentV2],
                                   allKnoraResourceProps: Set[SmartIri],
                                   allLinkProps: Set[SmartIri],
                                   allLinkValueProps: Set[SmartIri],
                                   allFileValueProps: Set[SmartIri]): Map[SmartIri, ReadClassInfoV2] = {
        classDefs.map {
            case (classIri, classDef) =>
                val ontologyIri = classIri.getOntologyFromEntity

                // Get the OWL cardinalities for the class.
                val allOwlCardinalitiesForClass: Map[SmartIri, OwlCardinalityInfo] = classCardinalitiesWithInheritance(classIri)
                val allPropertyIrisForCardinalitiesInClass: Set[SmartIri] = allOwlCardinalitiesForClass.keys.toSet

                // Identify the Knora resource properties, link properties, link value properties, and file value properties in the cardinalities.
                val knoraResourcePropsInClass = allPropertyIrisForCardinalitiesInClass.filter(allKnoraResourceProps)
                val linkPropsInClass = allPropertyIrisForCardinalitiesInClass.filter(allLinkProps)
                val linkValuePropsInClass = allPropertyIrisForCardinalitiesInClass.filter(allLinkValueProps)
                val fileValuePropsInClass = allPropertyIrisForCardinalitiesInClass.filter(allFileValueProps)

                // Make sure there is a link value property for each link property.

                val missingLinkValueProps = linkPropsInClass.map(_.fromLinkPropToLinkValueProp) -- linkValuePropsInClass

                if (missingLinkValueProps.nonEmpty) {
                    throw InconsistentTriplestoreDataException(s"Resource class $classIri has cardinalities for one or more link properties without corresponding link value properties. The missing (or incorrectly defined) property or properties: ${missingLinkValueProps.mkString(", ")}")
                }

                // Make sure there is a link property for each link value property.

                val missingLinkProps = linkValuePropsInClass.map(_.fromLinkValuePropToLinkProp) -- linkPropsInClass

                if (missingLinkProps.nonEmpty) {
                    throw InconsistentTriplestoreDataException(s"Resource class $classIri has cardinalities for one or more link value properties without corresponding link properties. The missing (or incorrectly defined) property or properties: ${missingLinkProps.mkString(", ")}")
                }

                // Make sure that the cardinality for each link property is the same as the cardinality for the corresponding link value property.
                for (linkProp <- linkPropsInClass) {
                    val linkValueProp: SmartIri = linkProp.fromLinkPropToLinkValueProp
                    val linkPropCardinality: OwlCardinalityInfo = allOwlCardinalitiesForClass(linkProp)
                    val linkValuePropCardinality: OwlCardinalityInfo = allOwlCardinalitiesForClass(linkValueProp)

                    if (!linkPropCardinality.equalsWithoutGuiOrder(linkValuePropCardinality)) {
                        throw InconsistentTriplestoreDataException(s"In class $classIri, the cardinality for $linkProp is different from the cardinality for $linkValueProp")
                    }
                }

                // The class's direct cardinalities.

                val directCardinalities: Map[SmartIri, KnoraCardinalityInfo] = directClassCardinalities(classIri).map {
                    case (propertyIri, owlCardinalityInfo) =>
                        propertyIri -> Cardinality.owlCardinality2KnoraCardinality(propertyIri = propertyIri.toString, owlCardinality = owlCardinalityInfo)
                }

                val directCardinalityPropertyIris = directCardinalities.keySet
                val allBaseClasses = allSubClassOfRelations(classIri)
                val isKnoraResourceClass = allBaseClasses.contains(OntologyConstants.KnoraBase.Resource.toSmartIri)
                val isStandoffClass = allBaseClasses.contains(OntologyConstants.KnoraBase.StandoffTag.toSmartIri)
                val isValueClass = !(isKnoraResourceClass || isStandoffClass) && allBaseClasses.contains(OntologyConstants.KnoraBase.Value.toSmartIri)

                // If the class is defined in project-specific ontology, do the following checks.
                if (!ontologyIri.isKnoraBuiltInDefinitionIri) {
                    // It must be either a resource class or a standoff class, but not both.
                    if (!(isKnoraResourceClass ^ isStandoffClass)) {
                        throw InconsistentTriplestoreDataException(s"Class $classIri must be a subclass either of knora-base:Resource or of knora-base:StandoffTag (but not both)")
                    }

                    // All its cardinalities must be on properties that are defined.
                    val cardinalitiesOnMissingProps = directCardinalityPropertyIris.filterNot(allPropertyDefs.keySet)

                    if (cardinalitiesOnMissingProps.nonEmpty) {
                        throw InconsistentTriplestoreDataException(s"Class $classIri has one or more cardinalities on undefined properties: ${cardinalitiesOnMissingProps.mkString(", ")}")
                    }

                    // It cannot have cardinalities both on property P and on a subproperty of P.

                    val maybePropertyAndSubproperty: Option[(SmartIri, SmartIri)] = findPropertyAndSubproperty(
                        propertyIris = allPropertyIrisForCardinalitiesInClass,
                        subPropertyOfRelations = allSubPropertyOfRelations
                    )

                    maybePropertyAndSubproperty match {
                        case Some((basePropertyIri, propertyIri)) =>
                            throw InconsistentTriplestoreDataException(s"Class $classIri has a cardinality on property $basePropertyIri and on its subproperty $propertyIri")

                        case None => ()
                    }

                    if (isKnoraResourceClass) {
                        // If it's a resource class, all its directly defined cardinalities must be on Knora resource properties, not including knora-base:resourceProperty or knora-base:hasValue.

                        val cardinalitiesOnInvalidProps = directCardinalityPropertyIris.filterNot(allKnoraResourceProps)

                        if (cardinalitiesOnInvalidProps.nonEmpty) {
                            throw InconsistentTriplestoreDataException(s"Resource class $classIri has one or more cardinalities on properties that are not Knora resource properties: ${cardinalitiesOnInvalidProps.mkString(", ")}")
                        }

                        Set(OntologyConstants.KnoraBase.ResourceProperty, OntologyConstants.KnoraBase.HasValue).foreach {
                            invalidProp =>
                                if (directCardinalityPropertyIris.contains(invalidProp.toSmartIri)) {
                                    throw InconsistentTriplestoreDataException(s"Class $classIri has a cardinality on property $invalidProp, which is not allowed")
                                }
                        }

                        // A cardinality on a property with a boolean object must be 1 or 0-1.

                        val invalidCardinalitiesOnBooleanProps: Set[SmartIri] = directCardinalities.filter {
                            case (propertyIri, knoraCardinalityInfo) =>
                                val propertyObjectClassConstraint: SmartIri = allPropertyDefs(propertyIri).requireIriObject(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri, throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint"))
                                propertyObjectClassConstraint == OntologyConstants.KnoraBase.BooleanValue.toSmartIri &&
                                    !(knoraCardinalityInfo.cardinality == Cardinality.MustHaveOne || knoraCardinalityInfo.cardinality == Cardinality.MayHaveOne)
                        }.keySet

                        if (invalidCardinalitiesOnBooleanProps.nonEmpty) {
                            throw InconsistentTriplestoreDataException(s"Class $classIri has one or more invalid cardinalities on boolean properties: ${invalidCardinalitiesOnBooleanProps.mkString(", ")}")
                        }


                        // All its base classes with Knora IRIs must also be resource classes.
                        for (baseClass <- classDef.subClassOf) {
                            if (baseClass.isKnoraDefinitionIri && !allSubClassOfRelations(baseClass).contains(OntologyConstants.KnoraBase.Resource.toSmartIri)) {
                                throw InconsistentTriplestoreDataException(s"Class $classIri is a subclass of knora-base:Resource, but its base class $baseClass is not")
                            }
                        }

                        // It must have an rdfs:label.
                        if (!classDef.predicates.contains(OntologyConstants.Rdfs.Label.toSmartIri)) {
                            throw InconsistentTriplestoreDataException(s"Class $classIri has no rdfs:label")
                        }
                    } else {
                        // If it's a standoff class, none of its cardinalities must be on Knora resource properties.

                        val cardinalitiesOnInvalidProps = directCardinalityPropertyIris.filter(allKnoraResourceProps)

                        if (cardinalitiesOnInvalidProps.nonEmpty) {
                            throw InconsistentTriplestoreDataException(s"Standoff class $classIri has one or more cardinalities on properties that are Knora resource properties: ${cardinalitiesOnInvalidProps.mkString(", ")}")
                        }

                        // All its base classes with Knora IRIs must also be standoff classes.
                        for (baseClass <- classDef.subClassOf) {
                            if (baseClass.isKnoraDefinitionIri) {
                                if (isStandoffClass && !allSubClassOfRelations(baseClass).contains(OntologyConstants.KnoraBase.StandoffTag.toSmartIri)) {
                                    throw InconsistentTriplestoreDataException(s"Class $classIri is a subclass of knora-base:StandoffTag, but its base class $baseClass is not")
                                }
                            }
                        }
                    }
                }

                // Each class must be a subclass of all the classes that are subject class constraints of the properties in its cardinalities.
                checkSubjectClassConstraintsViaCardinalities(
                    internalClassDef = classDef,
                    allBaseClassIris = allBaseClasses,
                    allClassCardinalityKnoraPropertyDefs = allPropertyDefs.filterKeys(allOwlCardinalitiesForClass.keySet),
                    errorSchema = InternalSchema,
                    errorFun = { msg: String => throw InconsistentTriplestoreDataException(msg) }
                )

                val inheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] = allOwlCardinalitiesForClass.filterNot {
                    case (propertyIri, _) => directCardinalityPropertyIris.contains(propertyIri)
                }.map {
                    case (propertyIri, owlCardinalityInfo) =>
                        propertyIri -> Cardinality.owlCardinality2KnoraCardinality(propertyIri = propertyIri.toString, owlCardinality = owlCardinalityInfo)
                }

                // Get the class's standoff data type, if any. A standoff class that has a datatype is a subclass of one of the classes
                // in StandoffDataTypeClasses.

                val standoffDataType: Set[SmartIri] = allSubClassOfRelations(classIri).intersect(StandoffDataTypeClasses.getStandoffClassIris.map(_.toSmartIri))

                if (standoffDataType.size > 1) {
                    throw InconsistentTriplestoreDataException(s"Class $classIri is a subclass of more than one standoff datatype: ${standoffDataType.mkString(", ")}")
                }

                // A class can be instantiated if it's in a built-in ontology and marked with knora-base:canBeInstantiated, or if it's
                // a resource class in a project-specific ontology.
                val canBeInstantiated = if (ontologyIri.isKnoraBuiltInDefinitionIri) {
                    classDef.predicates.get(OntologyConstants.KnoraBase.CanBeInstantiated.toSmartIri).flatMap(_.objects.headOption) match {
                        case Some(booleanLiteral: BooleanLiteralV2) => booleanLiteral.value
                        case _ => false
                    }
                } else {
                    isKnoraResourceClass
                }

                val readClassInfo = ReadClassInfoV2(
                    entityInfoContent = classDef,
                    allBaseClasses = allBaseClasses,
                    isResourceClass = isKnoraResourceClass,
                    isStandoffClass = isStandoffClass,
                    isValueClass = isValueClass,
                    canBeInstantiated = canBeInstantiated,
                    inheritedCardinalities = inheritedCardinalities,
                    knoraResourceProperties = knoraResourcePropsInClass,
                    linkProperties = linkPropsInClass,
                    linkValueProperties = linkValuePropsInClass,
                    fileValueProperties = fileValuePropsInClass,
                    standoffDataType = standoffDataType.headOption match {
                        case Some(dataType: SmartIri) =>
                            Some(StandoffDataTypeClasses.lookup(dataType.toString,
                                throw InconsistentTriplestoreDataException(s"$dataType is not a valid standoff datatype")))

                        case None => None
                    }
                )

                classIri -> readClassInfo
        }
    }

    /**
      * Constructs a map of property IRIs to [[ReadPropertyInfoV2]] instances, based on property definitions loaded from the
      * triplestore.
      *
      * @param propertyDefs                 a map of property IRIs to property definitions.
      * @param directSubPropertyOfRelations a map of property IRIs to their immediate base properties.
      * @param allSubPropertyOfRelations    a map of property IRIs to all their base properties.
      * @param allSubClassOfRelations       a map of class IRIs to all their base classes.
      * @param allGuiAttributeDefinitions   a map of `Guielement` IRIs to sets of [[SalsahGuiAttributeDefinition]].
      * @param allKnoraResourceProps        a set of the IRIs of all Knora resource properties.
      * @param allLinkProps                 a set of the IRIs of all link properties.
      * @param allLinkValueProps            a set of the IRIs of link value properties.
      * @param allFileValueProps            a set of the IRIs of all file value properties.
      * @return a map of property IRIs to [[ReadPropertyInfoV2]] instances.
      */
    private def makeReadPropertyInfos(propertyDefs: Map[SmartIri, PropertyInfoContentV2],
                                      directSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
                                      allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
                                      allSubClassOfRelations: Map[SmartIri, Set[SmartIri]],
                                      allGuiAttributeDefinitions: Map[SmartIri, Set[SalsahGuiAttributeDefinition]],
                                      allKnoraResourceProps: Set[SmartIri],
                                      allLinkProps: Set[SmartIri],
                                      allLinkValueProps: Set[SmartIri],
                                      allFileValueProps: Set[SmartIri]): Map[SmartIri, ReadPropertyInfoV2] = {
        propertyDefs.map {
            case (propertyIri, propertyDef) =>
                val ontologyIri = propertyIri.getOntologyFromEntity

                validateGuiAttributes(
                    propertyInfoContent = propertyDef,
                    allGuiAttributeDefinitions = allGuiAttributeDefinitions,
                    errorFun = { msg: String => throw InconsistentTriplestoreDataException(msg) }
                )

                val isResourceProp = allKnoraResourceProps.contains(propertyIri)
                val isValueProp = allSubPropertyOfRelations(propertyIri).contains(OntologyConstants.KnoraBase.HasValue.toSmartIri)
                val isLinkProp = allLinkProps.contains(propertyIri)
                val isLinkValueProp = allLinkValueProps.contains(propertyIri)
                val isFileValueProp = allFileValueProps.contains(propertyIri)

                // If the property is defined in a project-specific ontology and is a Knora resource property (a subproperty of knora-base:hasValue or knora-base:hasLinkTo), do the following checks.
                if (!propertyIri.isKnoraBuiltInDefinitionIri && isResourceProp) {
                    // The property must be a subproperty of knora-base:hasValue or knora-base:hasLinkTo, but not both.
                    if (isValueProp && isLinkProp) {
                        throw InconsistentTriplestoreDataException(s"Property $propertyIri cannot be a subproperty of both knora-base:hasValue and knora-base:hasLinkTo")
                    }

                    // It can't be a subproperty of knora-base:hasFileValue.
                    if (isFileValueProp) {
                        throw InconsistentTriplestoreDataException(s"Property $propertyIri cannot be a subproperty of knora-base:hasFileValue")
                    }

                    // Each of its base properties that has a Knora IRI must also be a Knora resource property.
                    for (baseProperty <- propertyDef.subPropertyOf) {
                        if (baseProperty.isKnoraDefinitionIri && !allKnoraResourceProps.contains(baseProperty)) {
                            throw InconsistentTriplestoreDataException(s"Property $propertyIri is a subproperty of knora-base:hasValue or knora-base:hasLinkTo, but its base property $baseProperty is not")
                        }
                    }

                    // It must have an rdfs:label.
                    if (!propertyDef.predicates.contains(OntologyConstants.Rdfs.Label.toSmartIri)) {
                        throw InconsistentTriplestoreDataException(s"Property $propertyIri has no rdfs:label")
                    }
                }

                // A property is editable if it's in a built-in ontology and marked with knora-base:isEditable,
                // or if it's a resource property in a project-specific ontology.
                val isEditable = if (ontologyIri.isKnoraBuiltInDefinitionIri) {
                    propertyDef.predicates.get(OntologyConstants.KnoraBase.IsEditable.toSmartIri).flatMap(_.objects.headOption) match {
                        case Some(booleanLiteral: BooleanLiteralV2) => booleanLiteral.value
                        case _ => false
                    }
                } else {
                    isResourceProp
                }

                val propertyEntityInfo = ReadPropertyInfoV2(
                    entityInfoContent = propertyDef,
                    isResourceProp = isResourceProp,
                    isEditable = isEditable,
                    isLinkProp = isLinkProp,
                    isLinkValueProp = isLinkValueProp,
                    isFileValueProp = isFileValueProp,
                    isStandoffInternalReferenceProperty = allSubPropertyOfRelations(propertyIri).contains(OntologyConstants.KnoraBase.StandoffTagHasInternalReference.toSmartIri)
                )

                propertyIri -> propertyEntityInfo
        }
    }

    /**
      * Constructs a map of OWL named individual IRIs to [[ReadIndividualInfoV2]] instances.
      *
      * @param individualDefs a map of OWL named individual IRIs to named individuals.
      * @return a map of individual IRIs to [[ReadIndividualInfoV2]] instances.
      */
    private def makeReadIndividualInfos(individualDefs: Map[SmartIri, IndividualInfoContentV2]): Map[SmartIri, ReadIndividualInfoV2] = {
        individualDefs.map {
            case (individualIri, individual) =>
                individualIri -> ReadIndividualInfoV2(individual)
        }
    }

    /**
      * Given all the OWL named individuals available, constructs a map of `salsah-gui:Guielement` individuals to
      * their GUI attribute definitions.
      *
      * @param allIndividuals all the OWL named individuals available.
      * @return a map of `salsah-gui:Guielement` individuals to their GUI attribute definitions.
      */
    private def makeGuiAttributeDefinitions(allIndividuals: Map[SmartIri, IndividualInfoContentV2]): Map[SmartIri, Set[SalsahGuiAttributeDefinition]] = {
        val guiElementIndividuals: Map[SmartIri, IndividualInfoContentV2] = allIndividuals.filter {
            case (_, individual) => individual.getRdfType.toString == OntologyConstants.SalsahGui.GuiElementClass
        }

        guiElementIndividuals.map {
            case (guiElementIri, guiElementIndividual) =>
                val attributeDefs: Set[SalsahGuiAttributeDefinition] = guiElementIndividual.predicates.get(OntologyConstants.SalsahGui.GuiAttributeDefinition.toSmartIri) match {
                    case Some(predicateInfo) =>
                        predicateInfo.objects.map {
                            case StringLiteralV2(attributeDefStr, None) =>
                                stringFormatter.toSalsahGuiAttributeDefinition(
                                    attributeDefStr,
                                    throw InconsistentTriplestoreDataException(s"Invalid salsah-gui:guiAttributeDefinition in $guiElementIri: $attributeDefStr")
                                )

                            case other =>
                                throw InconsistentTriplestoreDataException(s"Invalid salsah-gui:guiAttributeDefinition in $guiElementIri: $other")
                        }.toSet

                    case None => Set.empty[SalsahGuiAttributeDefinition]
                }

                guiElementIri -> attributeDefs
        }
    }

    /**
      * Validates the GUI attributes of a resource class property.
      *
      * @param propertyInfoContent        the property definition.
      * @param allGuiAttributeDefinitions the GUI attribute definitions for each GUI element.
      * @param errorFun                   a function that throws an exception. It will be passed the message to be included in the exception.
      */
    private def validateGuiAttributes(propertyInfoContent: PropertyInfoContentV2, allGuiAttributeDefinitions: Map[SmartIri, Set[SalsahGuiAttributeDefinition]], errorFun: String => Nothing): Unit = {
        val propertyIri = propertyInfoContent.propertyIri
        val predicates = propertyInfoContent.predicates

        // Find out which salsah-gui:Guielement the property uses, if any.
        val maybeGuiElementPred: Option[PredicateInfoV2] = predicates.get(OntologyConstants.SalsahGui.GuiElementProp.toSmartIri)
        val maybeGuiElementIri: Option[SmartIri] = maybeGuiElementPred.map(_.requireIriObject(throw InconsistentTriplestoreDataException(s"Property $propertyIri has an invalid object for ${OntologyConstants.SalsahGui.GuiElementProp}")))

        // Get that Guielement's attribute definitions, if any.
        val guiAttributeDefs: Set[SalsahGuiAttributeDefinition] = maybeGuiElementIri match {
            case Some(guiElementIri) =>
                allGuiAttributeDefinitions.getOrElse(guiElementIri, errorFun(s"Property $propertyIri has salsah-gui:guiElement $guiElementIri, which doesn't exist"))

            case None => Set.empty[SalsahGuiAttributeDefinition]
        }

        // If the property has the predicate salsah-gui:guiAttribute, syntactically validate the objects of that predicate.
        val guiAttributes: Set[SalsahGuiAttribute] = predicates.get(OntologyConstants.SalsahGui.GuiAttribute.toSmartIri) match {
            case Some(guiAttributePred) =>
                val guiElementIri = maybeGuiElementIri.getOrElse(errorFun(s"Property $propertyIri has salsah-gui:guiAttribute, but no salsah-gui:guiElement"))

                if (guiAttributeDefs.isEmpty) {
                    errorFun(s"Property $propertyIri has salsah-gui:guiAttribute, but $guiElementIri has no salsah-gui:guiAttributeDefinition")
                }

                // Syntactically validate each attribute.
                guiAttributePred.objects.map {
                    case StringLiteralV2(guiAttributeObj, None) =>
                        stringFormatter.toSalsahGuiAttribute(
                            s = guiAttributeObj,
                            attributeDefs = guiAttributeDefs,
                            errorFun = errorFun(s"Property $propertyIri contains an invalid salsah-gui:guiAttribute: $guiAttributeObj")
                        )

                    case other =>
                        errorFun(s"Property $propertyIri contains an invalid salsah-gui:guiAttribute: $other")
                }.toSet

            case None => Set.empty[SalsahGuiAttribute]
        }

        // Check that all required GUI attributes are provided.
        val requiredAttributeNames = guiAttributeDefs.filter(_.isRequired).map(_.attributeName)
        val providedAttributeNames = guiAttributes.map(_.attributeName)
        val missingAttributeNames: Set[String] = requiredAttributeNames -- providedAttributeNames

        if (missingAttributeNames.nonEmpty) {
            errorFun(s"Property $propertyIri has one or more missing objects of salsah-gui:guiAttribute: ${missingAttributeNames.mkString(", ")}")
        }
    }

    /**
      * Updates the ontology cache.
      *
      * @param cacheData the updated data to be cached.
      */
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
                case None => throw ApplicationCacheException(s"The Knora API server has not loaded any ontologies, perhaps because of an invalid ontology")
            }
        }
    }

    /**
      * Given a list of resource IRIs and a list of property IRIs (ontology entities), returns an [[EntityInfoGetResponseV2]] describing both resource and property entities.
      *
      * @param classIris      the IRIs of the resource entities to be queried.
      * @param propertyIris   the IRIs of the property entities to be queried.
      * @param requestingUser the user making the request.
      * @return an [[EntityInfoGetResponseV2]].
      */
    private def getEntityInfoResponseV2(classIris: Set[SmartIri] = Set.empty[SmartIri], propertyIris: Set[SmartIri] = Set.empty[SmartIri], requestingUser: UserADM): Future[EntityInfoGetResponseV2] = {
        for {
            cacheData <- getCacheData

            // See if any of the requested entities are not Knora entities.

            nonKnoraEntities = (classIris ++ propertyIris).filter(!_.isKnoraEntityIri)

            _ = if (nonKnoraEntities.nonEmpty) {
                throw BadRequestException(s"Some requested entities are not Knora entities: ${nonKnoraEntities.mkString(", ")}")
            }

            // See if any of the requested entities are unavailable in the requested schema.

            classesUnavailableInSchema: Set[SmartIri] = classIris.foldLeft(Set.empty[SmartIri]) {
                case (acc, classIri) =>
                    // Is this class IRI hard-coded in the requested schema?
                    if (KnoraApiV2SimpleTransformationRules.knoraApiClassesToAdd.contains(classIri) || KnoraApiV2WithValueObjectsTransformationRules.knoraApiClassesToAdd.contains(classIri)) {
                        // Yes, so it's available.
                        acc
                    } else {
                        // No. Is it among the classes removed from knora-base to create the requested schema?
                        classIri.getOntologySchema.get match {
                            case apiV2Schema: ApiV2Schema =>
                                val internalClassIri = classIri.toOntologySchema(InternalSchema)
                                val knoraBaseClassesToRemove = KnoraBaseTransformationRules.getTransformationRules(apiV2Schema).knoraBaseClassesToRemove

                                if (knoraBaseClassesToRemove.contains(internalClassIri)) {
                                    // Yes. Include it in the set of unavailable classes.
                                    acc + classIri
                                } else {
                                    // No. It's available.
                                    acc
                                }

                            case InternalSchema => acc
                        }
                    }
            }

            propertiesUnavailableInSchema: Set[SmartIri] = propertyIris.foldLeft(Set.empty[SmartIri]) {
                case (acc, propertyIri) =>
                    // Is this property IRI hard-coded in the requested schema?
                    if (KnoraApiV2SimpleTransformationRules.knoraApiPropertiesToAdd.contains(propertyIri) || KnoraApiV2WithValueObjectsTransformationRules.knoraApiPropertiesToAdd.contains(propertyIri)) {
                        // Yes, so it's available.
                        acc
                    } else {
                        // No. Is it among the properties removed from knora-base to create the requested schema?
                        propertyIri.getOntologySchema.get match {
                            case apiV2Schema: ApiV2Schema =>
                                val internalPropertyIri = propertyIri.toOntologySchema(InternalSchema)
                                val knoraBasePropertiesToRemove = KnoraBaseTransformationRules.getTransformationRules(apiV2Schema).knoraBasePropertiesToRemove

                                if (knoraBasePropertiesToRemove.contains(internalPropertyIri)) {
                                    // Yes. Include it in the set of unavailable properties.
                                    acc + propertyIri
                                } else {
                                    // No. It's available.
                                    acc
                                }

                            case InternalSchema => acc
                        }
                    }
            }

            entitiesUnavailableInSchema = classesUnavailableInSchema ++ propertiesUnavailableInSchema

            _ = if (entitiesUnavailableInSchema.nonEmpty) {
                throw NotFoundException(s"Some requested entities were not found: ${entitiesUnavailableInSchema.mkString(", ")}")
            }

            // See if any of the requested entities are hard-coded for knora-api.

            hardCodedKnoraApiClassesAvailable: Map[SmartIri, ReadClassInfoV2] = KnoraApiV2SimpleTransformationRules.knoraApiClassesToAdd.filterKeys(classIris) ++
                KnoraApiV2WithValueObjectsTransformationRules.knoraApiClassesToAdd.filterKeys(classIris)

            hardCodedKnoraApiPropertiesAvailable: Map[SmartIri, ReadPropertyInfoV2] = KnoraApiV2SimpleTransformationRules.knoraApiPropertiesToAdd.filterKeys(propertyIris) ++
                KnoraApiV2WithValueObjectsTransformationRules.knoraApiPropertiesToAdd.filterKeys(propertyIris)

            // Convert the remaining external entity IRIs to internal ones.

            internalToExternalClassIris: Map[SmartIri, SmartIri] = (classIris -- hardCodedKnoraApiClassesAvailable.keySet).map(externalIri => externalIri.toOntologySchema(InternalSchema) -> externalIri).toMap
            internalToExternalPropertyIris: Map[SmartIri, SmartIri] = (propertyIris -- hardCodedKnoraApiPropertiesAvailable.keySet).map(externalIri => externalIri.toOntologySchema(InternalSchema) -> externalIri).toMap

            classIrisForCache = internalToExternalClassIris.keySet
            propertyIrisForCache = internalToExternalPropertyIris.keySet

            // Get the entities that are available in the ontology cache.

            classOntologiesForCache: Iterable[ReadOntologyV2] = cacheData.ontologies.filterKeys(classIrisForCache.map(_.getOntologyFromEntity)).values
            propertyOntologiesForCache: Iterable[ReadOntologyV2] = cacheData.ontologies.filterKeys(propertyIrisForCache.map(_.getOntologyFromEntity)).values

            classesAvailableFromCache: Map[SmartIri, ReadClassInfoV2] = classOntologiesForCache.flatMap {
                ontology => ontology.classes.filterKeys(classIrisForCache)
            }.toMap

            propertiesAvailableFromCache: Map[SmartIri, ReadPropertyInfoV2] = propertyOntologiesForCache.flatMap {
                ontology => ontology.properties.filterKeys(propertyIrisForCache)
            }.toMap

            allClassesAvailable: Map[SmartIri, ReadClassInfoV2] = classesAvailableFromCache ++ hardCodedKnoraApiClassesAvailable
            allPropertiesAvailable: Map[SmartIri, ReadPropertyInfoV2] = propertiesAvailableFromCache ++ hardCodedKnoraApiPropertiesAvailable

            // See if any entities are missing.

            allExternalClassIrisAvailable: Set[SmartIri] = allClassesAvailable.keySet.map {
                classIri =>
                    if (classIri.getOntologySchema.contains(InternalSchema)) {
                        internalToExternalClassIris(classIri)
                    } else {
                        classIri
                    }
            }

            allExternalPropertyIrisAvailable = allPropertiesAvailable.keySet.map {
                propertyIri =>
                    if (propertyIri.getOntologySchema.contains(InternalSchema)) {
                        internalToExternalPropertyIris(propertyIri)
                    } else {
                        propertyIri
                    }
            }

            missingClasses = classIris -- allExternalClassIrisAvailable
            missingProperties = propertyIris -- allExternalPropertyIrisAvailable

            missingEntities = missingClasses ++ missingProperties

            _ = if (missingEntities.nonEmpty) {
                throw NotFoundException(s"Some requested entities were not found: ${missingEntities.mkString(", ")}")
            }

            response = EntityInfoGetResponseV2(
                classInfoMap = new ErrorHandlingMap(allClassesAvailable, { key => s"Resource class $key not found" }),
                propertyInfoMap = new ErrorHandlingMap(allPropertiesAvailable, { key => s"Property $key not found" })
            )
        } yield response
    }

    /**
      * Given a list of standoff class IRIs and a list of property IRIs (ontology entities), returns an [[StandoffEntityInfoGetResponseV2]] describing both resource and property entities.
      *
      * @param standoffClassIris    the IRIs of the resource entities to be queried.
      * @param standoffPropertyIris the IRIs of the property entities to be queried.
      * @param requestingUser       the user making the request.
      * @return a [[StandoffEntityInfoGetResponseV2]].
      */
    private def getStandoffEntityInfoResponseV2(standoffClassIris: Set[SmartIri] = Set.empty[SmartIri], standoffPropertyIris: Set[SmartIri] = Set.empty[SmartIri], requestingUser: UserADM): Future[StandoffEntityInfoGetResponseV2] = {
        for {
            cacheData <- getCacheData

            entitiesInWrongSchema = (standoffClassIris ++ standoffPropertyIris).filter(_.getOntologySchema.contains(ApiV2Simple))

            _ = if (entitiesInWrongSchema.nonEmpty) {
                throw NotFoundException(s"Some requested standoff classes were not found: ${entitiesInWrongSchema.mkString(", ")}")
            }

            classIrisForCache = standoffClassIris.map(_.toOntologySchema(InternalSchema))
            propertyIrisForCache = standoffPropertyIris.map(_.toOntologySchema(InternalSchema))

            classOntologies: Iterable[ReadOntologyV2] = cacheData.ontologies.filterKeys(classIrisForCache.map(_.getOntologyFromEntity)).values
            propertyOntologies: Iterable[ReadOntologyV2] = cacheData.ontologies.filterKeys(propertyIrisForCache.map(_.getOntologyFromEntity)).values

            classDefsAvailable: Map[SmartIri, ReadClassInfoV2] = classOntologies.flatMap {
                ontology =>
                    ontology.classes.filter {
                        case (classIri, classDef) => classDef.isStandoffClass && standoffClassIris.contains(classIri)
                    }
            }.toMap

            propertyDefsAvailable: Map[SmartIri, ReadPropertyInfoV2] = propertyOntologies.flatMap {
                ontology =>
                    ontology.properties.filter {
                        case (propertyIri, _) => standoffPropertyIris.contains(propertyIri) && cacheData.propertiesUsedInStandoffCardinalities.contains(propertyIri)
                    }
            }.toMap

            missingClassDefs = classIrisForCache -- classDefsAvailable.keySet
            missingPropertyDefs = propertyIrisForCache -- propertyDefsAvailable.keySet

            _ = if (missingClassDefs.nonEmpty) {
                throw NotFoundException(s"Some requested standoff classes were not found: ${missingClassDefs.mkString(", ")}")
            }

            _ = if (missingPropertyDefs.nonEmpty) {
                throw NotFoundException(s"Some requested standoff properties were not found: ${missingPropertyDefs.mkString(", ")}")
            }

            response = StandoffEntityInfoGetResponseV2(
                standoffClassInfoMap = new ErrorHandlingMap(classDefsAvailable, { key => s"Resource class $key not found" }),
                standoffPropertyInfoMap = new ErrorHandlingMap(propertyDefsAvailable, { key => s"Property $key not found" })
            )
        } yield response
    }

    /**
      * Gets information about all standoff classes that are a subclass of a data type standoff class.
      *
      * @param requestingUser the user making the request.
      * @return a [[StandoffClassesWithDataTypeGetResponseV2]]
      */
    private def getStandoffStandoffClassesWithDataTypeV2(requestingUser: UserADM): Future[StandoffClassesWithDataTypeGetResponseV2] = {
        for {
            cacheData <- getCacheData
        } yield StandoffClassesWithDataTypeGetResponseV2(
            standoffClassInfoMap = cacheData.ontologies.values.flatMap {
                ontology =>
                    ontology.classes.filter {
                        case (_, classDef) => classDef.isStandoffClass && classDef.standoffDataType.isDefined
                    }
            }.toMap
        )
    }

    /**
      * Gets all standoff property entities.
      *
      * @param requestingUser the user making the request.
      * @return a [[StandoffAllPropertyEntitiesGetResponseV2]].
      */
    private def getAllStandoffPropertyEntitiesV2(requestingUser: UserADM): Future[StandoffAllPropertyEntitiesGetResponseV2] = {
        for {
            cacheData <- getCacheData
        } yield StandoffAllPropertyEntitiesGetResponseV2(
            standoffAllPropertiesEntityInfoMap = cacheData.ontologies.values.flatMap {
                ontology => ontology.properties.filterKeys(cacheData.propertiesUsedInStandoffCardinalities)
            }.toMap
        )
    }

    /**
      * Checks whether a certain Knora resource or value class is a subclass of another class.
      *
      * @param subClassIri   the IRI of the resource or value class whose subclassOf relations have to be checked.
      * @param superClassIri the IRI of the resource or value class to check for (whether it is a a super class of `subClassIri` or not).
      * @return a [[CheckSubClassResponseV2]].
      */
    private def checkSubClassV2(subClassIri: SmartIri, superClassIri: SmartIri, requestingUser: UserADM): Future[CheckSubClassResponseV2] = {
        for {
            cacheData <- getCacheData
            response = CheckSubClassResponseV2(
                isSubClass =
                    cacheData.subClassOfRelations.get(subClassIri) match {
                        case Some(baseClasses) => baseClasses.contains(superClassIri)
                        case None => throw BadRequestException(s"Class $subClassIri not found")
                    }
            )
        } yield response
    }

    /**
      * Gets the IRIs of the subclasses of a class.
      *
      * @param classIri the IRI of the class whose subclasses should be returned.
      * @return a [[SubClassesGetResponseV2]].
      */
    private def getSubClassesV2(classIri: SmartIri, requestingUser: UserADM): Future[SubClassesGetResponseV2] = {
        for {
            cacheData <- getCacheData

            subClassIris = cacheData.superClassOfRelations(classIri).toVector.sorted

            subClasses = subClassIris.map {
                subClassIri =>
                    val classInfo: ReadClassInfoV2 = cacheData.ontologies(subClassIri.getOntologyFromEntity).classes(subClassIri)

                    SubClassInfoV2(
                        id = subClassIri,
                        label = classInfo.entityInfoContent.getPredicateStringLiteralObject(
                            predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                            preferredLangs = Some(requestingUser.lang, settings.fallbackLanguage)
                        ).getOrElse(throw InconsistentTriplestoreDataException(s"Resource class $subClassIri has no rdfs:label"))
                    )
            }
        } yield SubClassesGetResponseV2(
            subClasses = subClasses
        )
    }

    /**
      * Gets the [[OntologyKnoraEntitiesIriInfoV2]] for an ontology.
      *
      * @param ontologyIri    the IRI of the ontology to query
      * @param requestingUser the user making the request.
      * @return an [[OntologyKnoraEntitiesIriInfoV2]].
      */
    private def getKnoraEntityIrisInNamedGraphV2(ontologyIri: SmartIri, requestingUser: UserADM): Future[OntologyKnoraEntitiesIriInfoV2] = {
        for {
            cacheData <- getCacheData
            ontology = cacheData.ontologies(ontologyIri)
        } yield OntologyKnoraEntitiesIriInfoV2(
            ontologyIri = ontologyIri,
            propertyIris = ontology.properties.keySet.filter {
                propertyIri => isKnoraResourceProperty(propertyIri, cacheData)
            },
            classIris = ontology.classes.filter {
                case (_, classDef) => classDef.isResourceClass
            }.keySet,
            standoffClassIris = ontology.classes.filter {
                case (_, classDef) => classDef.isStandoffClass
            }.keySet,
            standoffPropertyIris = ontology.properties.keySet.filter(cacheData.propertiesUsedInStandoffCardinalities)
        )
    }

    /**
      * Gets the metadata describing the ontologies that belong to selected projects, or to all projects.
      *
      * @param projectIris    the IRIs of the projects selected, or an empty set if all projects are selected.
      * @param requestingUser the user making the request.
      * @return a [[ReadOntologyMetadataV2]].
      */
    private def getOntologyMetadataForProjectsV2(projectIris: Set[SmartIri], requestingUser: UserADM): Future[ReadOntologyMetadataV2] = {
        for {
            cacheData <- getCacheData
            returnAllOntologies: Boolean = projectIris.isEmpty

            ontologyMetadata: Set[OntologyMetadataV2] = if (returnAllOntologies) {
                cacheData.ontologies.values.map(_.ontologyMetadata).toSet
            } else {
                cacheData.ontologies.values.filter {
                    ontology => projectIris.contains(ontology.ontologyMetadata.projectIri.get)
                }.map {
                    ontology => ontology.ontologyMetadata
                }.toSet
            }
        } yield ReadOntologyMetadataV2(
            ontologies = ontologyMetadata
        )
    }

    /**
      * Gets the metadata describing the specified ontologies, or all ontologies.
      *
      * @param ontologyIris    the IRIs of the ontologies selected, or an empty set if all ontologies are selected.
      * @param requestingUser the user making the request.
      * @return a [[ReadOntologyMetadataV2]].
      */
    private def getOntologyMetadataByIriV2(ontologyIris: Set[SmartIri], requestingUser: UserADM): Future[ReadOntologyMetadataV2] = {
        for {
            cacheData <- getCacheData
            returnAllOntologies: Boolean = ontologyIris.isEmpty

            ontologyMetadata: Set[OntologyMetadataV2] = if (returnAllOntologies) {
                cacheData.ontologies.values.map(_.ontologyMetadata).toSet
            } else {
                val ontologyIrisForCache = ontologyIris.map(_.toOntologySchema(InternalSchema))
                val missingOntologies = ontologyIrisForCache -- cacheData.ontologies.keySet

                if (missingOntologies.nonEmpty) {
                    throw BadRequestException(s"One or more requested ontologies were not found: ${missingOntologies.mkString(", ")}")
                }

                cacheData.ontologies.filterKeys(ontologyIrisForCache).values.map {
                    ontology => ontology.ontologyMetadata
                }.toSet
            }
        } yield ReadOntologyMetadataV2(
            ontologies = ontologyMetadata
        )
    }

    /**
      * Requests the entities defined in the given ontology.
      *
      * @param ontologyIri    the IRI (internal or external) of the ontology to be queried.
      * @param requestingUser the user making the request.
      * @return a [[ReadOntologyV2]].
      */
    private def getOntologyEntitiesV2(ontologyIri: SmartIri, allLanguages: Boolean, requestingUser: UserADM): Future[ReadOntologyV2] = {
        for {
            cacheData <- getCacheData

            _ = if (ontologyIri.getOntologyName == "standoff" && ontologyIri.getOntologySchema.contains(ApiV2Simple)) {
                throw BadRequestException(s"The standoff ontology is not available in the API v2 simple schema")
            }

            // Are we returning data in the user's preferred language, or in all available languages?
            userLang = if (!allLanguages) {
                // Just the user's preferred language.
                Some(requestingUser.lang)
            } else {
                // All available languages.
                None
            }
        } yield cacheData.ontologies(ontologyIri.toOntologySchema(InternalSchema)).copy(
            userLang = userLang
        )
    }

    /**
      * Requests information about OWL classes in a single ontology.
      *
      * @param classIris      the IRIs (internal or external) of the classes to query for.
      * @param requestingUser the user making the request.
      * @return a [[ReadOntologyV2]].
      */
    private def getClassDefinitionsFromOntologyV2(classIris: Set[SmartIri], allLanguages: Boolean, requestingUser: UserADM): Future[ReadOntologyV2] = {
        for {
            cacheData <- getCacheData

            ontologyIris = classIris.map(_.getOntologyFromEntity)

            _ = if (ontologyIris.size != 1) {
                throw BadRequestException(s"Only one ontology may be queried per request")
            }

            classInfoResponse: EntityInfoGetResponseV2 <- getEntityInfoResponseV2(classIris = classIris, requestingUser = requestingUser)
            internalOntologyIri = ontologyIris.head.toOntologySchema(InternalSchema)

            // Are we returning data in the user's preferred language, or in all available languages?
            userLang = if (!allLanguages) {
                // Just the user's preferred language.
                Some(requestingUser.lang)
            } else {
                // All available languages.
                None
            }
        } yield ReadOntologyV2(
            ontologyMetadata = cacheData.ontologies(internalOntologyIri).ontologyMetadata,
            classes = classInfoResponse.classInfoMap,
            userLang = userLang
        )
    }

    /**
      * Requests information about properties in a single ontology.
      *
      * @param propertyIris   the IRIs (internal or external) of the properties to query for.
      * @param requestingUser the user making the request.
      * @return a [[ReadOntologyV2]].
      */
    private def getPropertyDefinitionsFromOntologyV2(propertyIris: Set[SmartIri], allLanguages: Boolean, requestingUser: UserADM): Future[ReadOntologyV2] = {
        for {
            cacheData <- getCacheData

            ontologyIris = propertyIris.map(_.getOntologyFromEntity)

            _ = if (ontologyIris.size != 1) {
                throw BadRequestException(s"Only one ontology may be queried per request")
            }

            propertyInfoResponse: EntityInfoGetResponseV2 <- getEntityInfoResponseV2(propertyIris = propertyIris, requestingUser = requestingUser)
            internalOntologyIri = ontologyIris.head.toOntologySchema(InternalSchema)

            // Are we returning data in the user's preferred language, or in all available languages?
            userLang = if (!allLanguages) {
                // Just the user's preferred language.
                Some(requestingUser.lang)
            } else {
                // All available languages.
                None
            }
        } yield ReadOntologyV2(
            ontologyMetadata = cacheData.ontologies(internalOntologyIri).ontologyMetadata,
            properties = propertyInfoResponse.propertyInfoMap,
            userLang = userLang
        )
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
                ontologyIri = internalOntologyIri
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

                        val projectIris: Seq[String] = statementMap.getOrElse(OntologyConstants.KnoraBase.AttachedToProject, throw InconsistentTriplestoreDataException(s"Ontology $internalOntologyIri has no knora-base:attachedToProject"))
                        val labels: Seq[String] = statementMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[String])
                        val lastModDates: Seq[String] = statementMap.getOrElse(OntologyConstants.KnoraBase.LastModificationDate, Seq.empty[String])

                        val projectIri = if (projectIris.size > 1) {
                            throw InconsistentTriplestoreDataException(s"Ontology $internalOntologyIri has more than one knora-base:attachedToProject")
                        } else {
                            projectIris.head.toSmartIri
                        }

                        if (!internalOntologyIri.isKnoraBuiltInDefinitionIri) {
                            if (projectIri.toString == OntologyConstants.KnoraBase.SystemProject) {
                                throw InconsistentTriplestoreDataException(s"Ontology $internalOntologyIri cannot be in project ${OntologyConstants.KnoraBase.SystemProject}")
                            }

                            if (internalOntologyIri.isKnoraSharedDefinitionIri && projectIri.toString != OntologyConstants.KnoraBase.DefaultSharedOntologiesProject) {
                                throw InconsistentTriplestoreDataException(s"Shared ontology $internalOntologyIri must be in project ${OntologyConstants.KnoraBase.DefaultSharedOntologiesProject}")
                            }
                        }

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
                            projectIri = Some(projectIri),
                            label = Some(label),
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

                // If this is a shared ontology, make sure it's in the default shared ontologies project.
                _ = if (createOntologyRequest.isShared && createOntologyRequest.projectIri.toString != OntologyConstants.KnoraBase.DefaultSharedOntologiesProject) {
                    throw BadRequestException(s"Shared ontologies must be created in project <${OntologyConstants.KnoraBase.DefaultSharedOntologiesProject}>")
                }

                // If it's in the default shared ontologies project, make sure it's a shared ontology.
                _ = if (createOntologyRequest.projectIri.toString == OntologyConstants.KnoraBase.DefaultSharedOntologiesProject && !createOntologyRequest.isShared) {
                    throw BadRequestException(s"Ontologies created in project <${OntologyConstants.KnoraBase.DefaultSharedOntologiesProject}> must be shared")
                }

                // Create the ontology.

                currentTime: Instant = Instant.now

                createOntologySparql = queries.sparql.v2.txt.createOntology(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri,
                    ontologyIri = internalOntologyIri,
                    projectIri = createOntologyRequest.projectIri,
                    isShared = createOntologyRequest.isShared,
                    ontologyLabel = createOntologyRequest.label,
                    currentTime = currentTime
                ).toString

                _ <- (storeManager ? SparqlUpdateRequest(createOntologySparql)).mapTo[SparqlUpdateResponse]

                // Check that the update was successful. To do this, we have to undo the SPARQL-escaping of the input.

                unescapedNewMetadata = OntologyMetadataV2(
                    ontologyIri = internalOntologyIri,
                    projectIri = Some(createOntologyRequest.projectIri),
                    label = Some(createOntologyRequest.label),
                    lastModificationDate = Some(currentTime)
                ).unescape

                maybeLoadedOntologyMetadata: Option[OntologyMetadataV2] <- loadOntologyMetadata(internalOntologyIri)

                _ = maybeLoadedOntologyMetadata match {
                    case Some(loadedOntologyMetadata) =>
                        if (loadedOntologyMetadata != unescapedNewMetadata) {
                            throw UpdateNotPerformedException()
                        }

                    case None => throw UpdateNotPerformedException()
                }

                // Update the ontology cache with the unescaped metadata.

                _ = storeCacheData(cacheData.copy(
                    ontologies = cacheData.ontologies + (internalOntologyIri -> ReadOntologyV2(ontologyMetadata = unescapedNewMetadata))
                ))

            } yield ReadOntologyMetadataV2(ontologies = Set(unescapedNewMetadata))
        }

        for {
            requestingUser <- FastFuture.successful(createOntologyRequest.requestingUser)
            projectIri = createOntologyRequest.projectIri

            // check if the requesting user is allowed to create an ontology
            _ = if (!(requestingUser.permissions.isProjectAdmin(projectIri.toString) || requestingUser.permissions.isSystemAdmin)) {
                // println(s"requestingUser: $requestingUser")
                // println(s"requestingUser.permissionData.isProjectAdmin(<${projectIri.toString}>): ${requestingUser.permissionData.isProjectAdmin(projectIri.toString)}")
                throw ForbiddenException(s"A new ontology in the project ${createOntologyRequest.projectIri} can only be created by an admin of that project, or by a system admin.")
            }

            // Get project info for the shortcode.
            projectInfo: ProjectGetResponseADM <- (responderManager ? ProjectGetRequestADM(maybeIri = Some(projectIri.toString), requestingUser = requestingUser)).mapTo[ProjectGetResponseADM]

            // Check that the ontology name is valid.
            validOntologyName = stringFormatter.validateProjectSpecificOntologyName(createOntologyRequest.ontologyName, throw BadRequestException(s"Invalid project-specific ontology name: ${createOntologyRequest.ontologyName}"))

            // Make the internal ontology IRI.
            internalOntologyIri = stringFormatter.makeProjectSpecificInternalOntologyIri(validOntologyName, createOntologyRequest.isShared, projectInfo.project.shortcode)

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

                // Check that the user has permission to update the ontology.
                projectIri <- checkPermissionsForOntologyUpdate(
                    internalOntologyIri = internalOntologyIri,
                    requestingUser = changeOntologyMetadataRequest.requestingUser
                )

                // Check that the ontology exists and has not been updated by another user since the client last read its metadata.
                _ <- checkOntologyLastModificationDateBeforeUpdate(internalOntologyIri = internalOntologyIri, expectedLastModificationDate = changeOntologyMetadataRequest.lastModificationDate)

                // Update the metadata.

                currentTime: Instant = Instant.now

                updateSparql = queries.sparql.v2.txt.changeOntologyMetadata(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri,
                    ontologyIri = internalOntologyIri,
                    newLabel = changeOntologyMetadataRequest.label,
                    lastModificationDate = changeOntologyMetadataRequest.lastModificationDate,
                    currentTime = currentTime
                ).toString()

                _ <- (storeManager ? SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

                // Check that the update was successful. To do this, we have to undo the SPARQL-escaping of the input.

                unescapedNewMetadata = OntologyMetadataV2(
                    ontologyIri = internalOntologyIri,
                    projectIri = Some(projectIri),
                    label = Some(changeOntologyMetadataRequest.label),
                    lastModificationDate = Some(currentTime)
                ).unescape

                maybeLoadedOntologyMetadata: Option[OntologyMetadataV2] <- loadOntologyMetadata(internalOntologyIri)

                _ = maybeLoadedOntologyMetadata match {
                    case Some(loadedOntologyMetadata) =>
                        if (loadedOntologyMetadata != unescapedNewMetadata) {
                            throw UpdateNotPerformedException()
                        }

                    case None => throw UpdateNotPerformedException()
                }

                // Update the ontology cache with the unescaped metadata.

                _ = storeCacheData(cacheData.copy(
                    ontologies = cacheData.ontologies + (internalOntologyIri -> ReadOntologyV2(ontologyMetadata = unescapedNewMetadata))
                ))

            } yield ReadOntologyMetadataV2(ontologies = Set(unescapedNewMetadata))
        }

        for {
            _ <- checkExternalOntologyIriForUpdate(changeOntologyMetadataRequest.ontologyIri)
            internalOntologyIri = changeOntologyMetadataRequest.ontologyIri.toOntologySchema(InternalSchema)

            // Do the remaining pre-update checks and the update while holding an update lock on the ontology.
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID = changeOntologyMetadataRequest.apiRequestID,
                iri = internalOntologyIri.toString,
                task = () => makeTaskFuture(internalOntologyIri = internalOntologyIri)
            )
        } yield taskResult
    }

    /**
      * Creates a class in an existing ontology.
      *
      * @param createClassRequest the request to create the class.
      * @return a [[ReadOntologyV2]] in the internal schema, the containing the definition of the new class.
      */
    private def createClass(createClassRequest: CreateClassRequestV2): Future[ReadOntologyV2] = {
        def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologyV2] = {
            for {
                cacheData <- getCacheData
                internalClassDef: ClassInfoContentV2 = createClassRequest.classInfoContent.toOntologySchema(InternalSchema)

                // Check that the ontology exists and has not been updated by another user since the client last read it.
                _ <- checkOntologyLastModificationDateBeforeUpdate(
                    internalOntologyIri = internalOntologyIri,
                    expectedLastModificationDate = createClassRequest.lastModificationDate
                )

                // Check that the class's rdf:type is owl:Class.

                rdfType: SmartIri = internalClassDef.requireIriObject(OntologyConstants.Rdf.Type.toSmartIri, throw BadRequestException(s"No rdf:type specified"))

                _ = if (rdfType != OntologyConstants.Owl.Class.toSmartIri) {
                    throw BadRequestException(s"Invalid rdf:type for property: $rdfType")
                }

                ontology = cacheData.ontologies(internalOntologyIri)

                // Check that the class doesn't exist yet.
                _ = if (ontology.classes.contains(internalClassIri)) {
                    throw BadRequestException(s"Class ${createClassRequest.classInfoContent.classIri} already exists")
                }

                // Check that the class's IRI isn't already used for something else.
                _ = if (ontology.properties.contains(internalClassIri) || ontology.individuals.contains(internalClassIri)) {
                    throw BadRequestException(s"IRI ${createClassRequest.classInfoContent.classIri} is already used")
                }

                // Check that the base classes that have Knora IRIs are defined as Knora resource classes.

                missingBaseClasses = internalClassDef.subClassOf.filter(_.isKnoraInternalEntityIri).filter(baseClassIri => !isKnoraInternalResourceClass(baseClassIri, cacheData))

                _ = if (missingBaseClasses.nonEmpty) {
                    throw BadRequestException(s"One or more specified base classes are invalid: ${missingBaseClasses.mkString(", ")}")
                }

                // Check for rdfs:subClassOf cycles.

                allBaseClassIrisWithoutSelf: Set[SmartIri] = internalClassDef.subClassOf.flatMap {
                    baseClassIri => cacheData.subClassOfRelations.getOrElse(baseClassIri, Set.empty[SmartIri])
                }

                _ = if (allBaseClassIrisWithoutSelf.contains(internalClassIri)) {
                    throw BadRequestException(s"Class ${createClassRequest.classInfoContent.classIri} would have a cyclical rdfs:subClassOf")
                }

                // Check that the class is a subclass of knora-base:Resource.

                allBaseClassIris: Set[SmartIri] = allBaseClassIrisWithoutSelf + internalClassIri

                _ = if (!allBaseClassIris.contains(OntologyConstants.KnoraBase.Resource.toSmartIri)) {
                    throw BadRequestException(s"Class ${createClassRequest.classInfoContent.classIri} would not be a subclass of knora-api:Resource")
                }

                // Check that the cardinalities are valid, and add any inherited cardinalities.
                (internalClassDefWithLinkValueProps, cardinalitiesForClassWithInheritance) = checkCardinalitiesBeforeAdding(
                    internalClassDef = internalClassDef,
                    allBaseClassIris = allBaseClassIris,
                    cacheData = cacheData
                )

                // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
                _ = checkOntologyReferencesInClassDef(
                    ontologyCacheData = cacheData,
                    classDef = internalClassDefWithLinkValueProps,
                    errorFun = { msg: String => throw BadRequestException(msg) }
                )

                // Prepare to update the ontology cache, undoing the SPARQL-escaping of the input.

                propertyIrisOfAllCardinalitiesForClass = cardinalitiesForClassWithInheritance.keySet

                inheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] = cardinalitiesForClassWithInheritance.filterNot {
                    case (propertyIri, _) => internalClassDefWithLinkValueProps.directCardinalities.contains(propertyIri)
                }

                unescapedClassDefWithLinkValueProps = internalClassDefWithLinkValueProps.unescape

                readClassInfo = ReadClassInfoV2(
                    entityInfoContent = unescapedClassDefWithLinkValueProps,
                    isResourceClass = true,
                    canBeInstantiated = true,
                    inheritedCardinalities = inheritedCardinalities,
                    knoraResourceProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri => isKnoraResourceProperty(propertyIri, cacheData)),
                    linkProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri => isLinkProp(propertyIri, cacheData)),
                    linkValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri => isLinkValueProp(propertyIri, cacheData)),
                    fileValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri => isFileValueProp(propertyIri, cacheData))
                )

                // Add the SPARQL-escaped class to the triplestore.

                currentTime: Instant = Instant.now

                updateSparql = queries.sparql.v2.txt.createClass(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri,
                    ontologyIri = internalOntologyIri,
                    classDef = internalClassDefWithLinkValueProps,
                    lastModificationDate = createClassRequest.lastModificationDate,
                    currentTime = currentTime
                ).toString()

                _ <- (storeManager ? SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

                // Check that the ontology's last modification date was updated.

                _ <- checkOntologyLastModificationDateAfterUpdate(internalOntologyIri = internalOntologyIri, expectedLastModificationDate = currentTime)

                // Check that the data that was saved corresponds to the data that was submitted.

                loadedClassDef <- loadClassDefinition(internalClassIri)

                _ = if (loadedClassDef != unescapedClassDefWithLinkValueProps) {
                    throw InconsistentTriplestoreDataException(s"Attempted to save class definition $unescapedClassDefWithLinkValueProps, but $loadedClassDef was saved")
                }

                // Update the cache.

                updatedSubClassOfRelations = cacheData.subClassOfRelations + (internalClassIri -> allBaseClassIris)
                updatedSuperClassOfRelations = calculateSuperClassOfRelations(updatedSubClassOfRelations)

                updatedOntology = ontology.copy(
                    ontologyMetadata = ontology.ontologyMetadata.copy(
                        lastModificationDate = Some(currentTime)
                    ),
                    classes = ontology.classes + (internalClassIri -> readClassInfo)
                )

                _ = storeCacheData(cacheData.copy(
                    ontologies = cacheData.ontologies + (internalOntologyIri -> updatedOntology),
                    subClassOfRelations = updatedSubClassOfRelations,
                    superClassOfRelations = updatedSuperClassOfRelations
                ))

                // Read the data back from the cache.

                response <- getClassDefinitionsFromOntologyV2(
                    classIris = Set(internalClassIri),
                    allLanguages = true,
                    requestingUser = createClassRequest.requestingUser
                )
            } yield response
        }

        for {
            requestingUser <- FastFuture.successful(createClassRequest.requestingUser)

            externalClassIri = createClassRequest.classInfoContent.classIri
            externalOntologyIri = externalClassIri.getOntologyFromEntity

            _ <- checkOntologyAndEntityIrisForUpdate(
                externalOntologyIri = externalOntologyIri,
                externalEntityIri = externalClassIri,
                requestingUser = requestingUser
            )

            internalClassIri = externalClassIri.toOntologySchema(InternalSchema)
            internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

            // Do the remaining pre-update checks and the update while holding an update lock on the ontology.
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID = createClassRequest.apiRequestID,
                iri = internalOntologyIri.toString,
                task = () => makeTaskFuture(
                    internalClassIri = internalClassIri,
                    internalOntologyIri = internalOntologyIri
                )
            )
        } yield taskResult
    }

    /**
      * Before creating a new class or adding cardinalities to an existing class, checks the validity of the
      * cardinalities directly defined on the class. Adds link value properties for the corresponding
      * link properties.
      *
      * @param internalClassDef the internal definition of the class.
      * @param allBaseClassIris the IRIs of all the class's base classes, including the class itself.
      * @param cacheData        the ontology cache.
      * @return the updated class definition, and the cardinalities resulting from inheritance.
      */
    private def checkCardinalitiesBeforeAdding(internalClassDef: ClassInfoContentV2,
                                               allBaseClassIris: Set[SmartIri],
                                               cacheData: OntologyCacheData): (ClassInfoContentV2, Map[SmartIri, KnoraCardinalityInfo]) = {
        // If the class has cardinalities, check that the properties are already defined as Knora properties.

        val propertyDefsForDirectCardinalities: Set[ReadPropertyInfoV2] = internalClassDef.directCardinalities.keySet.map {
            propertyIri =>
                if (!isKnoraResourceProperty(propertyIri, cacheData) || propertyIri.toString == OntologyConstants.KnoraBase.ResourceProperty || propertyIri.toString == OntologyConstants.KnoraBase.HasValue) {
                    throw NotFoundException(s"Invalid property for cardinality: <${propertyIri.toOntologySchema(ApiV2WithValueObjects)}>")
                }

                cacheData.ontologies(propertyIri.getOntologyFromEntity).properties(propertyIri)
        }

        val linkPropsInClass: Set[SmartIri] = propertyDefsForDirectCardinalities.filter(_.isLinkProp).map(_.entityInfoContent.propertyIri)
        val linkValuePropsInClass: Set[SmartIri] = propertyDefsForDirectCardinalities.filter(_.isLinkValueProp).map(_.entityInfoContent.propertyIri)

        // Don't allow link value prop cardinalities to be included in the request.

        if (linkValuePropsInClass.nonEmpty) {
            throw BadRequestException(s"Resource class ${internalClassDef.classIri.toOntologySchema(ApiV2WithValueObjects)} has cardinalities for one or more link value properties: ${linkValuePropsInClass.map(_.toOntologySchema(ApiV2WithValueObjects)).mkString(", ")}. Just submit the link properties, and the link value properties will be included automatically.")
        }

        // Add a link value prop cardinality for each link prop cardinality.

        val linkValuePropCardinalitiesToAdd: Map[SmartIri, KnoraCardinalityInfo] = linkPropsInClass.map {
            linkPropIri =>
                val linkValuePropIri = linkPropIri.fromLinkPropToLinkValueProp

                // Ensure that the link value prop exists.
                cacheData.ontologies(linkValuePropIri.getOntologyFromEntity).properties.getOrElse(linkValuePropIri, throw NotFoundException(s"Link value property <$linkValuePropIri> not found"))

                linkValuePropIri -> internalClassDef.directCardinalities(linkPropIri)
        }.toMap

        val classDefWithLinkValueProps = internalClassDef.copy(
            directCardinalities = internalClassDef.directCardinalities ++ linkValuePropCardinalitiesToAdd
        )

        // Get the cardinalities that the class can inherit.

        val cardinalitiesAvailableToInherit: Map[SmartIri, KnoraCardinalityInfo] = classDefWithLinkValueProps.subClassOf.flatMap {
            baseClassIri => cacheData.ontologies(baseClassIri.getOntologyFromEntity).classes(baseClassIri).allCardinalities
        }.toMap

        // Check that the cardinalities directly defined on the class are compatible with any inheritable
        // cardinalities, and let directly-defined cardinalities override cardinalities in base classes.

        val thisClassKnoraCardinalities = classDefWithLinkValueProps.directCardinalities.map {
            case (propertyIri, knoraCardinality) => propertyIri -> Cardinality.knoraCardinality2OwlCardinality(knoraCardinality)
        }

        val inheritableKnoraCardinalities = cardinalitiesAvailableToInherit.map {
            case (propertyIri, knoraCardinality) => propertyIri -> Cardinality.knoraCardinality2OwlCardinality(knoraCardinality)
        }

        val cardinalitiesForClassWithInheritance: Map[SmartIri, KnoraCardinalityInfo] = overrideCardinalities(
            thisClassCardinalities = thisClassKnoraCardinalities,
            inheritableCardinalities = inheritableKnoraCardinalities,
            allSubPropertyOfRelations = cacheData.subPropertyOfRelations,
            errorSchema = ApiV2WithValueObjects,
            { msg: String => throw BadRequestException(msg) }
        ).map {
            case (propertyIri, owlCardinalityInfo) => propertyIri -> Cardinality.owlCardinality2KnoraCardinality(propertyIri = propertyIri.toString, owlCardinality = owlCardinalityInfo)
        }

        // Check that the class is a subclass of all the classes that are subject class constraints of the Knora resource properties in its cardinalities.

        val knoraResourcePropertyIrisInCardinalities = cardinalitiesForClassWithInheritance.keySet.filter {
            propertyIri =>
                isKnoraResourceProperty(
                    propertyIri = propertyIri,
                    cacheData = cacheData
                )
        }

        val allClassCardinalityKnoraPropertyDefs: Map[SmartIri, PropertyInfoContentV2] = knoraResourcePropertyIrisInCardinalities.map {
            propertyIri => propertyIri -> cacheData.ontologies(propertyIri.getOntologyFromEntity).properties(propertyIri).entityInfoContent
        }.toMap

        checkSubjectClassConstraintsViaCardinalities(
            internalClassDef = classDefWithLinkValueProps,
            allBaseClassIris = allBaseClassIris,
            allClassCardinalityKnoraPropertyDefs = allClassCardinalityKnoraPropertyDefs,
            errorSchema = ApiV2WithValueObjects,
            errorFun = { msg: String => throw BadRequestException(msg) }
        )

        // It cannot have cardinalities both on property P and on a subproperty of P.

        val maybePropertyAndSubproperty: Option[(SmartIri, SmartIri)] = findPropertyAndSubproperty(
            propertyIris = cardinalitiesForClassWithInheritance.keySet,
            subPropertyOfRelations = cacheData.subPropertyOfRelations
        )

        maybePropertyAndSubproperty match {
            case Some((basePropertyIri, propertyIri)) =>
                throw BadRequestException(s"Class <${classDefWithLinkValueProps.classIri.toOntologySchema(ApiV2WithValueObjects)}> has a cardinality on property <${basePropertyIri.toOntologySchema(ApiV2WithValueObjects)}> and on its subproperty <${propertyIri.toOntologySchema(ApiV2WithValueObjects)}>")

            case None => ()
        }

        (classDefWithLinkValueProps, cardinalitiesForClassWithInheritance)
    }

    /**
      * Given a set of property IRIs, determines whether the set contains a property P and a subproperty of P.
      *
      * @param propertyIris           the set of property IRIs.
      * @param subPropertyOfRelations all the subproperty relations in the triplestore.
      * @return a property and its subproperty, if found.
      */
    private def findPropertyAndSubproperty(propertyIris: Set[SmartIri], subPropertyOfRelations: Map[SmartIri, Set[SmartIri]]): Option[(SmartIri, SmartIri)] = {
        propertyIris.flatMap {
            propertyIri =>
                val maybeBasePropertyIri: Option[SmartIri] = (propertyIris - propertyIri).find {
                    otherPropertyIri =>
                        subPropertyOfRelations.get(propertyIri).exists {
                            baseProperties: Set[SmartIri] => baseProperties.contains(otherPropertyIri)
                        }
                }

                maybeBasePropertyIri.map {
                    basePropertyIri => (basePropertyIri, propertyIri)
                }
        }.headOption
    }

    /**
      * Checks that a class is a subclass of all the classes that are subject class constraints of the Knora resource properties in its cardinalities.
      *
      * @param internalClassDef                     the class definition.
      * @param allBaseClassIris                     the IRIs of all the class's base classes.
      * @param allClassCardinalityKnoraPropertyDefs the definitions of all the Knora resource properties on which the class has cardinalities (whether directly defined
      *                                             or inherited).
      * @param errorSchema                          the ontology schema to be used in error messages.
      * @param errorFun                             a function that throws an exception. It will be called with an error message argument if the cardinalities are invalid.
      */
    private def checkSubjectClassConstraintsViaCardinalities(internalClassDef: ClassInfoContentV2,
                                                             allBaseClassIris: Set[SmartIri],
                                                             allClassCardinalityKnoraPropertyDefs: Map[SmartIri, PropertyInfoContentV2],
                                                             errorSchema: OntologySchema,
                                                             errorFun: String => Nothing): Unit = {
        allClassCardinalityKnoraPropertyDefs.foreach {
            case (propertyIri, propertyDef) =>
                propertyDef.predicates.get(OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri) match {
                    case Some(subjectClassConstraintPred) =>
                        val subjectClassConstraint = subjectClassConstraintPred.requireIriObject(throw InconsistentTriplestoreDataException(s"Property $propertyIri has an invalid object for ${OntologyConstants.KnoraBase.SubjectClassConstraint}"))

                        if (!allBaseClassIris.contains(subjectClassConstraint)) {
                            val hasOrWouldInherit = if (internalClassDef.directCardinalities.contains(propertyIri)) {
                                "has"
                            } else {
                                "would inherit"
                            }

                            errorFun(s"Class ${internalClassDef.classIri.toOntologySchema(errorSchema)} $hasOrWouldInherit a cardinality for property ${propertyIri.toOntologySchema(errorSchema)}, but is not a subclass of that property's ${OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri.toOntologySchema(errorSchema)}, ${subjectClassConstraint.toOntologySchema(errorSchema)}")
                        }

                    case None => ()
                }
        }
    }

    /**
      * Adds cardinalities to an existing class definition.
      *
      * @param addCardinalitiesRequest the request to add the cardinalities.
      * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
      */
    private def addCardinalitiesToClass(addCardinalitiesRequest: AddCardinalitiesToClassRequestV2): Future[ReadOntologyV2] = {
        def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologyV2] = {
            for {
                cacheData <- getCacheData
                internalClassDef: ClassInfoContentV2 = addCardinalitiesRequest.classInfoContent.toOntologySchema(InternalSchema)

                // Check that the ontology exists and has not been updated by another user since the client last read it.
                _ <- checkOntologyLastModificationDateBeforeUpdate(
                    internalOntologyIri = internalOntologyIri,
                    expectedLastModificationDate = addCardinalitiesRequest.lastModificationDate
                )

                // Check that the class's rdf:type is owl:Class.

                rdfType: SmartIri = internalClassDef.requireIriObject(OntologyConstants.Rdf.Type.toSmartIri, throw BadRequestException(s"No rdf:type specified"))

                _ = if (rdfType != OntologyConstants.Owl.Class.toSmartIri) {
                    throw BadRequestException(s"Invalid rdf:type for property: $rdfType")
                }

                // Check that cardinalities were submitted.

                _ = if (internalClassDef.directCardinalities.isEmpty) {
                    throw BadRequestException("No cardinalities specified")
                }

                // Check that the class exists, that it's a Knora resource class, and that the submitted cardinalities aren't for properties that already have cardinalities
                // directly defined on the class.

                ontology = cacheData.ontologies(internalOntologyIri)

                existingClassDef: ClassInfoContentV2 = ontology.classes.getOrElse(internalClassIri,
                    throw BadRequestException(s"Class ${addCardinalitiesRequest.classInfoContent.classIri} does not exist")).entityInfoContent

                redundantCardinalities = existingClassDef.directCardinalities.keySet.intersect(internalClassDef.directCardinalities.keySet)

                _ = if (redundantCardinalities.nonEmpty) {
                    throw BadRequestException(s"The cardinalities of ${addCardinalitiesRequest.classInfoContent.classIri} already include the following property or properties: ${redundantCardinalities.mkString(", ")}")
                }

                // Check that the class isn't used in data, and that it has no subclasses.

                _ <- isEntityUsed(
                    entityIri = internalClassIri,
                    errorFun = throw BadRequestException(s"Cardinalities cannot be added to class ${addCardinalitiesRequest.classInfoContent.classIri}, because it is used in data or has a subclass"),
                    ignoreKnoraConstraints = true // It's OK if a property refers to the class via knora-base:subjectClassConstraint or knora-base:objectClassConstraint.
                )

                // Make an updated class definition.

                newInternalClassDef = existingClassDef.copy(
                    directCardinalities = existingClassDef.directCardinalities ++ internalClassDef.directCardinalities
                )

                // Check that the new cardinalities are valid, and add any inherited cardinalities.

                allBaseClassIris: Set[SmartIri] = newInternalClassDef.subClassOf.flatMap {
                    baseClassIri => cacheData.subClassOfRelations.getOrElse(baseClassIri, Set.empty[SmartIri])
                } + internalClassIri

                (newInternalClassDefWithLinkValueProps, cardinalitiesForClassWithInheritance) = checkCardinalitiesBeforeAdding(
                    internalClassDef = newInternalClassDef,
                    allBaseClassIris = allBaseClassIris,
                    cacheData = cacheData
                )

                // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
                _ = checkOntologyReferencesInClassDef(
                    ontologyCacheData = cacheData,
                    classDef = newInternalClassDefWithLinkValueProps,
                    errorFun = { msg: String => throw BadRequestException(msg) }
                )

                // Prepare to update the ontology cache. (No need to deal with SPARQL-escaping here, because there
                // isn't any text to escape in cardinalities.)

                propertyIrisOfAllCardinalitiesForClass = cardinalitiesForClassWithInheritance.keySet

                inheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] = cardinalitiesForClassWithInheritance.filterNot {
                    case (propertyIri, _) => newInternalClassDefWithLinkValueProps.directCardinalities.contains(propertyIri)
                }

                readClassInfo = ReadClassInfoV2(
                    entityInfoContent = newInternalClassDefWithLinkValueProps,
                    isResourceClass = true,
                    canBeInstantiated = true,
                    inheritedCardinalities = inheritedCardinalities,
                    knoraResourceProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri => isKnoraResourceProperty(propertyIri, cacheData)),
                    linkProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri => isLinkProp(propertyIri, cacheData)),
                    linkValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri => isLinkValueProp(propertyIri, cacheData)),
                    fileValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri => isFileValueProp(propertyIri, cacheData))
                )

                // Add the cardinalities to the class definition in the triplestore.

                currentTime: Instant = Instant.now

                updateSparql = queries.sparql.v2.txt.addCardinalitiesToClass(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri,
                    ontologyIri = internalOntologyIri,
                    classIri = internalClassIri,
                    cardinalitiesToAdd = newInternalClassDefWithLinkValueProps.directCardinalities,
                    lastModificationDate = addCardinalitiesRequest.lastModificationDate,
                    currentTime = currentTime
                ).toString()

                _ <- (storeManager ? SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

                // Check that the ontology's last modification date was updated.

                _ <- checkOntologyLastModificationDateAfterUpdate(internalOntologyIri = internalOntologyIri, expectedLastModificationDate = currentTime)

                // Check that the data that was saved corresponds to the data that was submitted.

                loadedClassDef <- loadClassDefinition(internalClassIri)

                _ = if (loadedClassDef != newInternalClassDefWithLinkValueProps) {
                    throw InconsistentTriplestoreDataException(s"Attempted to save class definition $newInternalClassDefWithLinkValueProps, but $loadedClassDef was saved")
                }

                // Update the cache.

                updatedOntology = ontology.copy(
                    ontologyMetadata = ontology.ontologyMetadata.copy(
                        lastModificationDate = Some(currentTime)
                    ),
                    classes = ontology.classes + (internalClassIri -> readClassInfo)
                )

                _ = storeCacheData(cacheData.copy(
                    ontologies = cacheData.ontologies + (internalOntologyIri -> updatedOntology)
                ))

                // Read the data back from the cache.

                response <- getClassDefinitionsFromOntologyV2(
                    classIris = Set(internalClassIri),
                    allLanguages = true,
                    requestingUser = addCardinalitiesRequest.requestingUser
                )
            } yield response
        }

        for {
            requestingUser <- FastFuture.successful(addCardinalitiesRequest.requestingUser)

            externalClassIri = addCardinalitiesRequest.classInfoContent.classIri
            externalOntologyIri = externalClassIri.getOntologyFromEntity

            _ <- checkOntologyAndEntityIrisForUpdate(
                externalOntologyIri = externalOntologyIri,
                externalEntityIri = externalClassIri,
                requestingUser = requestingUser
            )

            internalClassIri = externalClassIri.toOntologySchema(InternalSchema)
            internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

            // Do the remaining pre-update checks and the update while holding an update lock on the ontology.
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID = addCardinalitiesRequest.apiRequestID,
                iri = internalOntologyIri.toString,
                task = () => makeTaskFuture(
                    internalClassIri = internalClassIri,
                    internalOntologyIri = internalOntologyIri
                )
            )
        } yield taskResult
    }

    /**
      * Replaces a class's cardinalities with new ones.
      *
      * @param changeCardinalitiesRequest the request to add the cardinalities.
      * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
      */
    private def changeClassCardinalities(changeCardinalitiesRequest: ChangeCardinalitiesRequestV2): Future[ReadOntologyV2] = {
        def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologyV2] = {
            for {
                cacheData <- getCacheData
                internalClassDef: ClassInfoContentV2 = changeCardinalitiesRequest.classInfoContent.toOntologySchema(InternalSchema)

                // Check that the ontology exists and has not been updated by another user since the client last read it.
                _ <- checkOntologyLastModificationDateBeforeUpdate(
                    internalOntologyIri = internalOntologyIri,
                    expectedLastModificationDate = changeCardinalitiesRequest.lastModificationDate
                )

                // Check that the class's rdf:type is owl:Class.

                rdfType: SmartIri = internalClassDef.requireIriObject(OntologyConstants.Rdf.Type.toSmartIri, throw BadRequestException(s"No rdf:type specified"))

                _ = if (rdfType != OntologyConstants.Owl.Class.toSmartIri) {
                    throw BadRequestException(s"Invalid rdf:type for property: $rdfType")
                }

                // Check that the class exists.

                ontology = cacheData.ontologies(internalOntologyIri)

                existingClassDef: ClassInfoContentV2 = ontology.classes.getOrElse(internalClassIri,
                    throw BadRequestException(s"Class ${changeCardinalitiesRequest.classInfoContent.classIri} does not exist")).entityInfoContent

                // Check that the class isn't used in data, and that it has no subclasses.

                _ <- isEntityUsed(
                    entityIri = internalClassIri,
                    errorFun = throw BadRequestException(s"The cardinalities of class ${changeCardinalitiesRequest.classInfoContent.classIri} cannot be changed, because it is used in data or has a subclass"),
                    ignoreKnoraConstraints = true // It's OK if a property refers to the class via knora-base:subjectClassConstraint or knora-base:objectClassConstraint.
                )

                // Make an updated class definition.

                newInternalClassDef = existingClassDef.copy(
                    directCardinalities = internalClassDef.directCardinalities
                )

                // Check that the new cardinalities are valid, and add any inherited cardinalities.

                allBaseClassIris: Set[SmartIri] = newInternalClassDef.subClassOf.flatMap {
                    baseClassIri => cacheData.subClassOfRelations.getOrElse(baseClassIri, Set.empty[SmartIri])
                } + internalClassIri

                (newInternalClassDefWithLinkValueProps, cardinalitiesForClassWithInheritance) = checkCardinalitiesBeforeAdding(
                    internalClassDef = newInternalClassDef,
                    allBaseClassIris = allBaseClassIris,
                    cacheData = cacheData
                )

                // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
                _ = checkOntologyReferencesInClassDef(
                    ontologyCacheData = cacheData,
                    classDef = newInternalClassDefWithLinkValueProps,
                    errorFun = { msg: String => throw BadRequestException(msg) }
                )

                // Prepare to update the ontology cache. (No need to deal with SPARQL-escaping here, because there
                // isn't any text to escape in cardinalities.)

                propertyIrisOfAllCardinalitiesForClass = cardinalitiesForClassWithInheritance.keySet

                inheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] = cardinalitiesForClassWithInheritance.filterNot {
                    case (propertyIri, _) => newInternalClassDefWithLinkValueProps.directCardinalities.contains(propertyIri)
                }

                readClassInfo = ReadClassInfoV2(
                    entityInfoContent = newInternalClassDefWithLinkValueProps,
                    isResourceClass = true,
                    canBeInstantiated = true,
                    inheritedCardinalities = inheritedCardinalities,
                    knoraResourceProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri => isKnoraResourceProperty(propertyIri, cacheData)),
                    linkProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri => isLinkProp(propertyIri, cacheData)),
                    linkValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri => isLinkValueProp(propertyIri, cacheData)),
                    fileValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri => isFileValueProp(propertyIri, cacheData))
                )

                // Add the cardinalities to the class definition in the triplestore.

                currentTime: Instant = Instant.now

                updateSparql = queries.sparql.v2.txt.replaceClassCardinalities(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri,
                    ontologyIri = internalOntologyIri,
                    classIri = internalClassIri,
                    newCardinalities = newInternalClassDefWithLinkValueProps.directCardinalities,
                    lastModificationDate = changeCardinalitiesRequest.lastModificationDate,
                    currentTime = currentTime
                ).toString()

                _ <- (storeManager ? SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

                // Check that the ontology's last modification date was updated.

                _ <- checkOntologyLastModificationDateAfterUpdate(internalOntologyIri = internalOntologyIri, expectedLastModificationDate = currentTime)

                // Check that the data that was saved corresponds to the data that was submitted.

                loadedClassDef <- loadClassDefinition(internalClassIri)

                _ = if (loadedClassDef != newInternalClassDefWithLinkValueProps) {
                    throw InconsistentTriplestoreDataException(s"Attempted to save class definition $newInternalClassDefWithLinkValueProps, but $loadedClassDef was saved")
                }

                // Update the cache.

                updatedOntology = ontology.copy(
                    ontologyMetadata = ontology.ontologyMetadata.copy(
                        lastModificationDate = Some(currentTime)
                    ),
                    classes = ontology.classes + (internalClassIri -> readClassInfo)
                )

                _ = storeCacheData(cacheData.copy(
                    ontologies = cacheData.ontologies + (internalOntologyIri -> updatedOntology)
                ))

                // Read the data back from the cache.

                response <- getClassDefinitionsFromOntologyV2(
                    classIris = Set(internalClassIri),
                    allLanguages = true,
                    requestingUser = changeCardinalitiesRequest.requestingUser
                )
            } yield response
        }

        for {
            requestingUser <- FastFuture.successful(changeCardinalitiesRequest.requestingUser)

            externalClassIri = changeCardinalitiesRequest.classInfoContent.classIri
            externalOntologyIri = externalClassIri.getOntologyFromEntity

            _ <- checkOntologyAndEntityIrisForUpdate(
                externalOntologyIri = externalOntologyIri,
                externalEntityIri = externalClassIri,
                requestingUser = requestingUser
            )

            internalClassIri = externalClassIri.toOntologySchema(InternalSchema)
            internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

            // Do the remaining pre-update checks and the update while holding an update lock on the ontology.
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID = changeCardinalitiesRequest.apiRequestID,
                iri = internalOntologyIri.toString,
                task = () => makeTaskFuture(
                    internalClassIri = internalClassIri,
                    internalOntologyIri = internalOntologyIri
                )
            )
        } yield taskResult
    }

    /**
      * Deletes a class.
      *
      * @param deleteClassRequest the request to delete the class.
      * @return a [[SuccessResponseV2]].
      */
    private def deleteClass(deleteClassRequest: DeleteClassRequestV2): Future[ReadOntologyMetadataV2] = {
        def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologyMetadataV2] = {
            for {
                cacheData <- getCacheData

                // Check that the ontology exists and has not been updated by another user since the client last read it.
                _ <- checkOntologyLastModificationDateBeforeUpdate(
                    internalOntologyIri = internalOntologyIri,
                    expectedLastModificationDate = deleteClassRequest.lastModificationDate
                )

                // Check that the class exists.

                ontology = cacheData.ontologies(internalOntologyIri)

                _ = if (!ontology.classes.contains(internalClassIri)) {
                    throw BadRequestException(s"Class ${deleteClassRequest.classIri} does not exist")
                }

                // Check that the class isn't used in data or ontologies.

                _ <- isEntityUsed(
                    entityIri = internalClassIri,
                    errorFun = throw BadRequestException(s"Class ${deleteClassRequest.classIri} cannot be deleted, because it is used in data or ontologies")
                )

                // Delete the class from the triplestore.

                currentTime: Instant = Instant.now

                updateSparql = queries.sparql.v2.txt.deleteClass(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri,
                    ontologyIri = internalOntologyIri,
                    classIri = internalClassIri,
                    lastModificationDate = deleteClassRequest.lastModificationDate,
                    currentTime = currentTime
                ).toString()

                _ <- (storeManager ? SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

                // Check that the ontology's last modification date was updated.

                _ <- checkOntologyLastModificationDateAfterUpdate(internalOntologyIri = internalOntologyIri, expectedLastModificationDate = currentTime)

                // Update the cache.

                updatedOntology = ontology.copy(
                    ontologyMetadata = ontology.ontologyMetadata.copy(
                        lastModificationDate = Some(currentTime)
                    ),
                    classes = ontology.classes - internalClassIri
                )

                updatedSubClassOfRelations = cacheData.subClassOfRelations - internalClassIri
                updatedSuperClassOfRelations = calculateSuperClassOfRelations(updatedSubClassOfRelations)

                _ = storeCacheData(cacheData.copy(
                    ontologies = cacheData.ontologies + (internalOntologyIri -> updatedOntology),
                    subClassOfRelations = updatedSubClassOfRelations,
                    superClassOfRelations = updatedSuperClassOfRelations
                ))
            } yield ReadOntologyMetadataV2(Set(updatedOntology.ontologyMetadata))
        }

        for {
            requestingUser <- FastFuture.successful(deleteClassRequest.requestingUser)

            externalClassIri = deleteClassRequest.classIri
            externalOntologyIri = externalClassIri.getOntologyFromEntity

            _ <- checkOntologyAndEntityIrisForUpdate(
                externalOntologyIri = externalOntologyIri,
                externalEntityIri = externalClassIri,
                requestingUser = requestingUser
            )

            internalClassIri = externalClassIri.toOntologySchema(InternalSchema)
            internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

            // Do the remaining pre-update checks and the update while holding an update lock on the ontology.
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID = deleteClassRequest.apiRequestID,
                iri = internalOntologyIri.toString,
                task = () => makeTaskFuture(
                    internalClassIri = internalClassIri,
                    internalOntologyIri = internalOntologyIri
                )
            )
        } yield taskResult
    }

    /**
      * Deletes a property. If the property is a link property, the corresponding link value property is also deleted.
      *
      * @param deletePropertyRequest the request to delete the property.
      * @return a [[ReadOntologyMetadataV2]].
      */
    private def deleteProperty(deletePropertyRequest: DeletePropertyRequestV2): Future[ReadOntologyMetadataV2] = {
        def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologyMetadataV2] = {
            for {
                cacheData <- getCacheData

                // Check that the ontology exists and has not been updated by another user since the client last read it.
                _ <- checkOntologyLastModificationDateBeforeUpdate(
                    internalOntologyIri = internalOntologyIri,
                    expectedLastModificationDate = deletePropertyRequest.lastModificationDate
                )

                // Check that the property exists.

                ontology = cacheData.ontologies(internalOntologyIri)
                propertyDef: ReadPropertyInfoV2 = ontology.properties.getOrElse(internalPropertyIri, throw BadRequestException(s"Property ${deletePropertyRequest.propertyIri} does not exist"))

                _ = if (propertyDef.isLinkValueProp) {
                    throw BadRequestException(s"A link value property cannot be deleted directly; delete the corresponding link property instead")
                }

                maybeInternalLinkValuePropertyIri: Option[SmartIri] = if (propertyDef.isLinkProp) {
                    Some(internalPropertyIri.fromLinkPropToLinkValueProp)
                } else {
                    None
                }

                // Check that the property isn't used in data or ontologies.

                _ <- isEntityUsed(
                    entityIri = internalPropertyIri,
                    errorFun = throw BadRequestException(s"Property ${deletePropertyRequest.propertyIri} cannot be deleted, because it is used in data or ontologies")
                )

                _ <- maybeInternalLinkValuePropertyIri match {
                    case Some(internalLinkValuePropertyIri) =>
                        isEntityUsed(
                            entityIri = internalLinkValuePropertyIri,
                            errorFun = throw BadRequestException(s"Property ${deletePropertyRequest.propertyIri} cannot be deleted, because the corresponding link value property, ${internalLinkValuePropertyIri.toOntologySchema(ApiV2WithValueObjects)}, is used in data or ontologies")
                        )

                    case None => FastFuture.successful(())
                }

                // Delete the property from the triplestore.

                currentTime: Instant = Instant.now

                updateSparql = queries.sparql.v2.txt.deleteProperty(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri,
                    ontologyIri = internalOntologyIri,
                    propertyIri = internalPropertyIri,
                    maybeLinkValuePropertyIri = maybeInternalLinkValuePropertyIri,
                    lastModificationDate = deletePropertyRequest.lastModificationDate,
                    currentTime = currentTime
                ).toString()

                _ <- (storeManager ? SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

                // Check that the ontology's last modification date was updated.

                _ <- checkOntologyLastModificationDateAfterUpdate(internalOntologyIri = internalOntologyIri, expectedLastModificationDate = currentTime)

                // Update the cache.

                propertiesToRemoveFromCache = Set(internalPropertyIri) ++ maybeInternalLinkValuePropertyIri

                updatedOntology = ontology.copy(
                    ontologyMetadata = ontology.ontologyMetadata.copy(
                        lastModificationDate = Some(currentTime)
                    ),
                    properties = ontology.properties -- propertiesToRemoveFromCache
                )

                updatedSubPropertyOfRelations = cacheData.subPropertyOfRelations -- propertiesToRemoveFromCache

                _ = storeCacheData(cacheData.copy(
                    ontologies = cacheData.ontologies + (internalOntologyIri -> updatedOntology),
                    subPropertyOfRelations = updatedSubPropertyOfRelations
                ))
            } yield ReadOntologyMetadataV2(Set(updatedOntology.ontologyMetadata))
        }

        for {
            requestingUser <- FastFuture.successful(deletePropertyRequest.requestingUser)

            externalPropertyIri = deletePropertyRequest.propertyIri
            externalOntologyIri = externalPropertyIri.getOntologyFromEntity

            _ <- checkOntologyAndEntityIrisForUpdate(
                externalOntologyIri = externalOntologyIri,
                externalEntityIri = externalPropertyIri,
                requestingUser = requestingUser
            )

            internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
            internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

            // Do the remaining pre-update checks and the update while holding an update lock on the ontology.
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID = deletePropertyRequest.apiRequestID,
                iri = internalOntologyIri.toString,
                task = () => makeTaskFuture(
                    internalPropertyIri = internalPropertyIri,
                    internalOntologyIri = internalOntologyIri
                )
            )
        } yield taskResult
    }

    def deleteOntology(deleteOntologyRequest: DeleteOntologyRequestV2): Future[SuccessResponseV2] = {
        def makeTaskFuture(internalOntologyIri: SmartIri): Future[SuccessResponseV2] = {
            for {
                cacheData <- getCacheData

                // Check that the user has permission to update the ontology.
                _ <- checkPermissionsForOntologyUpdate(
                    internalOntologyIri = internalOntologyIri,
                    requestingUser = deleteOntologyRequest.requestingUser
                )

                // Check that the ontology exists and has not been updated by another user since the client last read it.
                _ <- checkOntologyLastModificationDateBeforeUpdate(
                    internalOntologyIri = internalOntologyIri,
                    expectedLastModificationDate = deleteOntologyRequest.lastModificationDate
                )

                // Check that none of the entities in the ontology are used in data.

                ontology = cacheData.ontologies(internalOntologyIri)

                isOntologyUsedSparql = queries.sparql.v2.txt.isOntologyUsed(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri,
                    classIris = ontology.classes.keySet,
                    propertyIris = ontology.properties.keySet
                ).toString()

                isOntologyUsedResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(isOntologyUsedSparql)).mapTo[SparqlSelectResponse]

                _ = if (isOntologyUsedResponse.results.bindings.nonEmpty) {
                    val subjects: Seq[String] = isOntologyUsedResponse.results.bindings.map {
                        row => row.rowMap("s")
                    }.map(s => "<" + s + ">").toVector.sorted

                    throw BadRequestException(s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)} cannot be deleted, because of subjects that refer to it: ${subjects.mkString(", ")}")
                }

                // Delete everything in the ontology's named graph.

                updateSparql = queries.sparql.v2.txt.deleteOntology(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri
                ).toString()

                _ <- (storeManager ? SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

                // Check that the ontology has been deleted.

                maybeOntologyMetadata <- loadOntologyMetadata(internalOntologyIri)

                _ = if (maybeOntologyMetadata.nonEmpty) {
                    throw UpdateNotPerformedException(s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)} was not deleted. Please report this as a possible bug.")
                }

            } yield SuccessResponseV2(s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)} has been deleted")
        }

        for {
            _ <- checkExternalOntologyIriForUpdate(deleteOntologyRequest.ontologyIri)
            internalOntologyIri = deleteOntologyRequest.ontologyIri.toOntologySchema(InternalSchema)

            // Do the remaining pre-update checks and the update while holding an update lock on the ontology.
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID = deleteOntologyRequest.apiRequestID,
                iri = internalOntologyIri.toString,
                task = () => makeTaskFuture(
                    internalOntologyIri = internalOntologyIri
                )
            )
        } yield taskResult
    }

    /**
      * Creates a property in an existing ontology.
      *
      * @param createPropertyRequest the request to create the property.
      * @return a [[ReadOntologyV2]] in the internal schema, the containing the definition of the new property.
      */
    private def createProperty(createPropertyRequest: CreatePropertyRequestV2): Future[ReadOntologyV2] = {
        def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologyV2] = {
            for {
                cacheData <- getCacheData
                internalPropertyDef = createPropertyRequest.propertyInfoContent.toOntologySchema(InternalSchema)

                // Check that the ontology exists and has not been updated by another user since the client last read it.
                _ <- checkOntologyLastModificationDateBeforeUpdate(internalOntologyIri = internalOntologyIri, expectedLastModificationDate = createPropertyRequest.lastModificationDate)

                // Check that the property's rdf:type is owl:ObjectProperty.

                rdfType: SmartIri = internalPropertyDef.requireIriObject(OntologyConstants.Rdf.Type.toSmartIri, throw BadRequestException(s"No rdf:type specified"))

                _ = if (rdfType != OntologyConstants.Owl.ObjectProperty.toSmartIri) {
                    throw BadRequestException(s"Invalid rdf:type for property: $rdfType")
                }

                // Check that the property doesn't exist yet.

                ontology = cacheData.ontologies(internalOntologyIri)

                _ = if (ontology.properties.contains(internalPropertyIri)) {
                    throw BadRequestException(s"Property ${createPropertyRequest.propertyInfoContent.propertyIri} already exists")
                }

                // Check that the property's IRI isn't already used for something else.
                _ = if (ontology.classes.contains(internalPropertyIri) || ontology.individuals.contains(internalPropertyIri)) {
                    throw BadRequestException(s"IRI ${createPropertyRequest.propertyInfoContent.propertyIri} is already used")
                }

                // Check that the base properties that have Knora IRIs are defined as Knora resource properties.

                knoraSuperProperties = internalPropertyDef.subPropertyOf.filter(_.isKnoraInternalEntityIri)
                invalidSuperProperties = knoraSuperProperties.filterNot(baseProperty => isKnoraResourceProperty(baseProperty, cacheData) && baseProperty.toString != OntologyConstants.KnoraBase.ResourceProperty)

                _ = if (invalidSuperProperties.nonEmpty) {
                    throw BadRequestException(s"One or more specified base properties are invalid: ${invalidSuperProperties.mkString(", ")}")
                }

                // Check for rdfs:subPropertyOf cycles.

                allKnoraSuperPropertyIrisWithoutSelf: Set[SmartIri] = knoraSuperProperties.flatMap {
                    superPropertyIri => cacheData.subPropertyOfRelations.getOrElse(superPropertyIri, Set.empty[SmartIri])
                }

                _ = if (allKnoraSuperPropertyIrisWithoutSelf.contains(internalPropertyIri)) {
                    throw BadRequestException(s"Property ${createPropertyRequest.propertyInfoContent.propertyIri} would have a cyclical rdfs:subPropertyOf")
                }

                // Check the property is a subproperty of knora-base:hasValue or knora-base:hasLinkTo, but not both.

                allKnoraSuperPropertyIris: Set[SmartIri] = allKnoraSuperPropertyIrisWithoutSelf + internalPropertyIri

                isValueProp = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasValue.toSmartIri)
                isLinkProp = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasLinkTo.toSmartIri)
                isLinkValueProp = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasLinkToValue.toSmartIri)
                isFileValueProp = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasFileValue.toSmartIri)

                _ = if (!(isValueProp || isLinkProp)) {
                    throw BadRequestException(s"Property ${createPropertyRequest.propertyInfoContent.propertyIri} would not be a subproperty of knora-api:hasValue or knora-api:hasLinkTo")
                }

                _ = if (isValueProp && isLinkProp) {
                    throw BadRequestException(s"Property ${createPropertyRequest.propertyInfoContent.propertyIri} would be a subproperty of both knora-api:hasValue and knora-api:hasLinkTo")
                }

                // Don't allow new file value properties to be created.

                _ = if (isFileValueProp) {
                    throw BadRequestException("New file value properties cannot be created")
                }

                // Don't allow new link value properties to be created directly, because we do that automatically when creating a link property.

                _ = if (isLinkValueProp) {
                    throw BadRequestException("New link value properties cannot be created directly. Create a link property instead.")
                }

                // Check the property's salsah-gui:guiElement and salsah-gui:guiAttribute.
                _ = validateGuiAttributes(
                    propertyInfoContent = internalPropertyDef,
                    allGuiAttributeDefinitions = cacheData.guiAttributeDefinitions,
                    errorFun = { msg: String => throw BadRequestException(msg) }
                )

                // If we're creating a link property, make the definition of the corresponding link value property.
                maybeLinkValuePropertyDef: Option[PropertyInfoContentV2] = if (isLinkProp) {
                    val linkValuePropertyDef = linkPropertyDefToLinkValuePropertyDef(internalPropertyDef)

                    if (ontology.properties.contains(linkValuePropertyDef.propertyIri)) {
                        throw BadRequestException(s"Link value property ${linkValuePropertyDef.propertyIri} already exists")
                    }

                    Some(linkValuePropertyDef)
                } else {
                    None
                }

                // Check that the subject class constraint, if provided, designates a Knora resource class that exists.

                maybeSubjectClassConstraintPred: Option[PredicateInfoV2] = internalPropertyDef.predicates.get(OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri)
                maybeSubjectClassConstraint = maybeSubjectClassConstraintPred.map(_.requireIriObject(throw BadRequestException("Invalid knora-api:subjectType")))

                _ = maybeSubjectClassConstraint.foreach {
                    subjectClassConstraint =>
                        if (!isKnoraInternalResourceClass(subjectClassConstraint, cacheData)) {
                            throw BadRequestException(s"Invalid subject class constraint: ${subjectClassConstraint.toOntologySchema(ApiV2WithValueObjects)}")
                        }
                }

                // Check that the object class constraint designates an appropriate class that exists.

                objectClassConstraint: SmartIri = internalPropertyDef.requireIriObject(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri, throw BadRequestException(s"No knora-api:objectType specified"))

                // If this is a value property, ensure its object class constraint is not LinkValue or a file value class.
                _ = if (!isLinkProp) {
                    if (objectClassConstraint.toString == OntologyConstants.KnoraBase.LinkValue ||
                        OntologyConstants.KnoraBase.FileValueClasses.contains(objectClassConstraint.toString)) {
                        throw BadRequestException(s"Invalid object class constraint for value property: ${objectClassConstraint.toOntologySchema(ApiV2WithValueObjects)}")
                    }
                }

                // Check that the subject class, if provided, is a subclass of the subject classes of the base properties.

                _ = maybeSubjectClassConstraint match {
                    case Some(subjectClassConstraint) =>
                        checkPropertyConstraint(
                            cacheData = cacheData,
                            internalPropertyIri = internalPropertyIri,
                            constraintPredicateIri = OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri,
                            constraintValueToBeChecked = subjectClassConstraint,
                            allSuperPropertyIris = allKnoraSuperPropertyIris,
                            errorSchema = ApiV2WithValueObjects,
                            errorFun = { msg: String => throw BadRequestException(msg) }
                        )

                    case None => ()
                }

                // Check that the object class is a subclass of the object classes of the base properties.

                _ = checkPropertyConstraint(
                    cacheData = cacheData,
                    internalPropertyIri = internalPropertyIri,
                    constraintPredicateIri = OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
                    constraintValueToBeChecked = objectClassConstraint,
                    allSuperPropertyIris = allKnoraSuperPropertyIris,
                    errorSchema = ApiV2WithValueObjects,
                    errorFun = { msg: String => throw BadRequestException(msg) }
                )

                // Check that the property definition doesn't refer to any non-shared ontologies in other projects.
                _ = checkOntologyReferencesInPropertyDef(
                    ontologyCacheData = cacheData,
                    propertyDef = internalPropertyDef,
                    errorFun = { msg: String => throw BadRequestException(msg) }
                )

                // Add the property (and the link value property if needed) to the triplestore.

                currentTime: Instant = Instant.now

                updateSparql = queries.sparql.v2.txt.createProperty(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri,
                    ontologyIri = internalOntologyIri,
                    propertyDef = internalPropertyDef,
                    maybeLinkValuePropertyDef = maybeLinkValuePropertyDef,
                    lastModificationDate = createPropertyRequest.lastModificationDate,
                    currentTime = currentTime
                ).toString()

                _ <- (storeManager ? SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

                // Check that the ontology's last modification date was updated.

                _ <- checkOntologyLastModificationDateAfterUpdate(internalOntologyIri = internalOntologyIri, expectedLastModificationDate = currentTime)

                // Check that the data that was saved corresponds to the data that was submitted. To make this comparison,
                // we have to undo the SPARQL-escaping of the input.

                loadedPropertyDef <- loadPropertyDefinition(internalPropertyIri)
                unescapedInputPropertyDef = internalPropertyDef.unescape

                _ = if (loadedPropertyDef != unescapedInputPropertyDef) {
                    throw InconsistentTriplestoreDataException(s"Attempted to save property definition $unescapedInputPropertyDef, but $loadedPropertyDef was saved")
                }

                maybeLoadedLinkValuePropertyDefFuture: Option[Future[PropertyInfoContentV2]] = maybeLinkValuePropertyDef.map(linkValuePropertyDef => loadPropertyDefinition(linkValuePropertyDef.propertyIri))
                maybeLoadedLinkValuePropertyDef: Option[PropertyInfoContentV2] <- ActorUtil.optionFuture2FutureOption(maybeLoadedLinkValuePropertyDefFuture)
                maybeUnescapedNewLinkValuePropertyDef = maybeLinkValuePropertyDef.map(_.unescape)

                _ = (maybeLoadedLinkValuePropertyDef, maybeUnescapedNewLinkValuePropertyDef) match {
                    case (Some(loadedLinkValuePropertyDef), Some(unescapedNewLinkPropertyDef)) =>
                        if (loadedLinkValuePropertyDef != unescapedNewLinkPropertyDef) {
                            throw InconsistentTriplestoreDataException(s"Attempted to save link value property definition $unescapedNewLinkPropertyDef, but $loadedLinkValuePropertyDef was saved")
                        }

                    case _ => ()
                }

                // Update the ontology cache, using the unescaped definition(s).

                readPropertyInfo = ReadPropertyInfoV2(
                    entityInfoContent = unescapedInputPropertyDef,
                    isEditable = true,
                    isResourceProp = true,
                    isLinkProp = isLinkProp
                )

                maybeLinkValuePropertyCacheEntry: Option[(SmartIri, ReadPropertyInfoV2)] = maybeUnescapedNewLinkValuePropertyDef.map {
                    unescapedNewLinkPropertyDef =>
                        unescapedNewLinkPropertyDef.propertyIri -> ReadPropertyInfoV2(
                            entityInfoContent = unescapedNewLinkPropertyDef,
                            isResourceProp = true,
                            isLinkValueProp = true
                        )
                }

                updatedOntologyMetadata = ontology.ontologyMetadata.copy(
                    lastModificationDate = Some(currentTime)
                )

                updatedOntology = ontology.copy(
                    ontologyMetadata = updatedOntologyMetadata,
                    properties = ontology.properties ++ maybeLinkValuePropertyCacheEntry + (internalPropertyIri -> readPropertyInfo)
                )

                _ = storeCacheData(cacheData.copy(
                    ontologies = cacheData.ontologies + (internalOntologyIri -> updatedOntology),
                    subPropertyOfRelations = cacheData.subPropertyOfRelations + (internalPropertyIri -> allKnoraSuperPropertyIris)
                ))

                // Read the data back from the cache.

                response <- getPropertyDefinitionsFromOntologyV2(
                    propertyIris = Set(internalPropertyIri),
                    allLanguages = true,
                    requestingUser = createPropertyRequest.requestingUser
                )
            } yield response
        }

        for {
            requestingUser <- FastFuture.successful(createPropertyRequest.requestingUser)

            externalPropertyIri = createPropertyRequest.propertyInfoContent.propertyIri
            externalOntologyIri = externalPropertyIri.getOntologyFromEntity

            _ <- checkOntologyAndEntityIrisForUpdate(
                externalOntologyIri = externalOntologyIri,
                externalEntityIri = externalPropertyIri,
                requestingUser = requestingUser
            )

            internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
            internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

            // Do the remaining pre-update checks and the update while holding an update lock on the ontology.
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID = createPropertyRequest.apiRequestID,
                iri = internalOntologyIri.toString,
                task = () => makeTaskFuture(
                    internalPropertyIri = internalPropertyIri,
                    internalOntologyIri = internalOntologyIri
                )
            )
        } yield taskResult
    }

    /**
      * Changes the values of `rdfs:label` or `rdfs:comment` in a property definition.
      *
      * @param changePropertyLabelsOrCommentsRequest the request to change the property's labels or comments.
      * @return a [[ReadOntologyV2]] containing the modified property definition.
      */
    private def changePropertyLabelsOrComments(changePropertyLabelsOrCommentsRequest: ChangePropertyLabelsOrCommentsRequestV2): Future[ReadOntologyV2] = {
        def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologyV2] = {
            for {
                cacheData <- getCacheData

                ontology = cacheData.ontologies(internalOntologyIri)

                currentReadPropertyInfo: ReadPropertyInfoV2 = ontology.properties.getOrElse(internalPropertyIri, throw NotFoundException(s"Property ${changePropertyLabelsOrCommentsRequest.propertyIri} not found"))

                // Check that the ontology exists and has not been updated by another user since the client last read it.
                _ <- checkOntologyLastModificationDateBeforeUpdate(internalOntologyIri = internalOntologyIri, expectedLastModificationDate = changePropertyLabelsOrCommentsRequest.lastModificationDate)

                // Check that the new labels/comments are different from the current ones.

                currentLabelsOrComments: Seq[OntologyLiteralV2] = currentReadPropertyInfo.entityInfoContent.predicates.get(changePropertyLabelsOrCommentsRequest.predicateToUpdate) match {
                    case Some(pred) => pred.objects
                    case None => Seq.empty[OntologyLiteralV2]
                }

                _ = if (currentLabelsOrComments == changePropertyLabelsOrCommentsRequest.newObjects) {
                    throw BadRequestException(s"The submitted objects of ${changePropertyLabelsOrCommentsRequest.propertyIri} are the same as the current ones in property ${changePropertyLabelsOrCommentsRequest.propertyIri}")
                }

                // If this is a link property, also change the labels/comments of the corresponding link value property.

                maybeCurrentLinkValueReadPropertyInfo: Option[ReadPropertyInfoV2] = if (currentReadPropertyInfo.isLinkProp) {
                    val linkValuePropertyIri = internalPropertyIri.fromLinkPropToLinkValueProp
                    Some(ontology.properties.getOrElse(linkValuePropertyIri, throw InconsistentTriplestoreDataException(s"Link value property $linkValuePropertyIri not found")))
                } else {
                    None
                }

                // Do the update.

                currentTime: Instant = Instant.now

                updateSparql = queries.sparql.v2.txt.changePropertyLabelsOrComments(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri,
                    ontologyIri = internalOntologyIri,
                    propertyIri = internalPropertyIri,
                    maybeLinkValuePropertyIri = maybeCurrentLinkValueReadPropertyInfo.map(_.entityInfoContent.propertyIri),
                    predicateToUpdate = changePropertyLabelsOrCommentsRequest.predicateToUpdate,
                    newObjects = changePropertyLabelsOrCommentsRequest.newObjects,
                    lastModificationDate = changePropertyLabelsOrCommentsRequest.lastModificationDate,
                    currentTime = currentTime
                ).toString()

                _ <- (storeManager ? SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

                // Check that the ontology's last modification date was updated.

                _ <- checkOntologyLastModificationDateAfterUpdate(internalOntologyIri = internalOntologyIri, expectedLastModificationDate = currentTime)

                // Check that the data that was saved corresponds to the data that was submitted. To make this comparison,
                // we have to undo the SPARQL-escaping of the input.

                loadedPropertyDef <- loadPropertyDefinition(internalPropertyIri)

                unescapedNewLabelOrCommentPredicate: PredicateInfoV2 = PredicateInfoV2(
                    predicateIri = changePropertyLabelsOrCommentsRequest.predicateToUpdate,
                    objects = changePropertyLabelsOrCommentsRequest.newObjects
                ).unescape

                unescapedNewPropertyDef: PropertyInfoContentV2 = currentReadPropertyInfo.entityInfoContent.copy(
                    predicates = currentReadPropertyInfo.entityInfoContent.predicates + (changePropertyLabelsOrCommentsRequest.predicateToUpdate -> unescapedNewLabelOrCommentPredicate)
                )

                _ = if (loadedPropertyDef != unescapedNewPropertyDef) {
                    throw InconsistentTriplestoreDataException(s"Attempted to save property definition $unescapedNewPropertyDef, but $loadedPropertyDef was saved")
                }

                maybeLoadedLinkValuePropertyDefFuture: Option[Future[PropertyInfoContentV2]] = maybeCurrentLinkValueReadPropertyInfo.map {
                    linkValueReadPropertyInfo => loadPropertyDefinition(linkValueReadPropertyInfo.entityInfoContent.propertyIri)
                }

                maybeLoadedLinkValuePropertyDef: Option[PropertyInfoContentV2] <- ActorUtil.optionFuture2FutureOption(maybeLoadedLinkValuePropertyDefFuture)

                maybeUnescapedNewLinkValuePropertyDef: Option[PropertyInfoContentV2] = maybeLoadedLinkValuePropertyDef.map {
                    loadedLinkValuePropertyDef =>
                        val unescapedNewLinkPropertyDef = maybeCurrentLinkValueReadPropertyInfo.get.entityInfoContent.copy(
                            predicates = currentReadPropertyInfo.entityInfoContent.predicates + (changePropertyLabelsOrCommentsRequest.predicateToUpdate -> unescapedNewLabelOrCommentPredicate)
                        )

                        if (loadedLinkValuePropertyDef != unescapedNewLinkPropertyDef) {
                            throw InconsistentTriplestoreDataException(s"Attempted to save link value property definition $unescapedNewLinkPropertyDef, but $loadedLinkValuePropertyDef was saved")
                        }

                        unescapedNewLinkPropertyDef
                }

                // Update the ontology cache, using the unescaped definition(s).

                newReadPropertyInfo = ReadPropertyInfoV2(
                    entityInfoContent = unescapedNewPropertyDef,
                    isEditable = true,
                    isResourceProp = true,
                    isLinkProp = currentReadPropertyInfo.isLinkProp
                )

                maybeLinkValuePropertyCacheEntry: Option[(SmartIri, ReadPropertyInfoV2)] = maybeUnescapedNewLinkValuePropertyDef.map {
                    unescapedNewLinkPropertyDef =>
                        unescapedNewLinkPropertyDef.propertyIri -> ReadPropertyInfoV2(
                            entityInfoContent = unescapedNewLinkPropertyDef,
                            isResourceProp = true,
                            isLinkValueProp = true
                        )
                }

                updatedOntologyMetadata = ontology.ontologyMetadata.copy(
                    lastModificationDate = Some(currentTime)
                )

                updatedOntology = ontology.copy(
                    ontologyMetadata = updatedOntologyMetadata,
                    properties = ontology.properties ++ maybeLinkValuePropertyCacheEntry + (internalPropertyIri -> newReadPropertyInfo)
                )

                _ = storeCacheData(cacheData.copy(
                    ontologies = cacheData.ontologies + (internalOntologyIri -> updatedOntology)
                ))

                // Read the data back from the cache.

                response <- getPropertyDefinitionsFromOntologyV2(propertyIris = Set(internalPropertyIri), allLanguages = true, requestingUser = changePropertyLabelsOrCommentsRequest.requestingUser)
            } yield response
        }

        for {
            requestingUser <- FastFuture.successful(changePropertyLabelsOrCommentsRequest.requestingUser)

            externalPropertyIri = changePropertyLabelsOrCommentsRequest.propertyIri
            externalOntologyIri = externalPropertyIri.getOntologyFromEntity

            _ <- checkOntologyAndEntityIrisForUpdate(
                externalOntologyIri = externalOntologyIri,
                externalEntityIri = externalPropertyIri,
                requestingUser = requestingUser
            )

            internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
            internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

            // Do the remaining pre-update checks and the update while holding an update lock on the ontology.
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID = changePropertyLabelsOrCommentsRequest.apiRequestID,
                iri = internalOntologyIri.toString,
                task = () => makeTaskFuture(
                    internalPropertyIri = internalPropertyIri,
                    internalOntologyIri = internalOntologyIri
                )
            )
        } yield taskResult
    }


    /**
      * Changes the values of `rdfs:label` or `rdfs:comment` in a class definition.
      *
      * @param changeClassLabelsOrCommentsRequest the request to change the class's labels or comments.
      * @return a [[ReadOntologyV2]] containing the modified class definition.
      */
    private def changeClassLabelsOrComments(changeClassLabelsOrCommentsRequest: ChangeClassLabelsOrCommentsRequestV2): Future[ReadOntologyV2] = {
        def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologyV2] = {
            for {
                cacheData <- getCacheData

                ontology = cacheData.ontologies(internalOntologyIri)
                currentReadClassInfo: ReadClassInfoV2 = ontology.classes.getOrElse(internalClassIri, throw NotFoundException(s"Class ${changeClassLabelsOrCommentsRequest.classIri} not found"))

                // Check that the ontology exists and has not been updated by another user since the client last read it.
                _ <- checkOntologyLastModificationDateBeforeUpdate(internalOntologyIri = internalOntologyIri, expectedLastModificationDate = changeClassLabelsOrCommentsRequest.lastModificationDate)

                // Check that the new labels/comments are different from the current ones.

                currentLabelsOrComments: Seq[OntologyLiteralV2] = currentReadClassInfo.entityInfoContent.predicates.get(changeClassLabelsOrCommentsRequest.predicateToUpdate) match {
                    case Some(pred) => pred.objects
                    case None => Seq.empty[OntologyLiteralV2]
                }

                _ = if (currentLabelsOrComments == changeClassLabelsOrCommentsRequest.newObjects) {
                    throw BadRequestException(s"The submitted objects of ${changeClassLabelsOrCommentsRequest.predicateToUpdate} are the same as the current ones in class ${changeClassLabelsOrCommentsRequest.classIri}")
                }

                // Do the update.

                currentTime: Instant = Instant.now

                updateSparql = queries.sparql.v2.txt.changeClassLabelsOrComments(
                    triplestore = settings.triplestoreType,
                    ontologyNamedGraphIri = internalOntologyIri,
                    ontologyIri = internalOntologyIri,
                    classIri = internalClassIri,
                    predicateToUpdate = changeClassLabelsOrCommentsRequest.predicateToUpdate,
                    newObjects = changeClassLabelsOrCommentsRequest.newObjects,
                    lastModificationDate = changeClassLabelsOrCommentsRequest.lastModificationDate,
                    currentTime = currentTime
                ).toString()

                _ <- (storeManager ? SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

                // Check that the ontology's last modification date was updated.

                _ <- checkOntologyLastModificationDateAfterUpdate(internalOntologyIri = internalOntologyIri, expectedLastModificationDate = currentTime)

                // Check that the data that was saved corresponds to the data that was submitted. To make this comparison,
                // we have to undo the SPARQL-escaping of the input.

                loadedClassDef: ClassInfoContentV2 <- loadClassDefinition(internalClassIri)

                unescapedNewLabelOrCommentPredicate = PredicateInfoV2(
                    predicateIri = changeClassLabelsOrCommentsRequest.predicateToUpdate,
                    objects = changeClassLabelsOrCommentsRequest.newObjects
                ).unescape

                unescapedNewClassDef: ClassInfoContentV2 = currentReadClassInfo.entityInfoContent.copy(
                    predicates = currentReadClassInfo.entityInfoContent.predicates + (changeClassLabelsOrCommentsRequest.predicateToUpdate -> unescapedNewLabelOrCommentPredicate)
                )

                _ = if (loadedClassDef != unescapedNewClassDef) {
                    throw InconsistentTriplestoreDataException(s"Attempted to save class definition $unescapedNewClassDef, but $loadedClassDef was saved")
                }

                // Update the ontology cache, using the unescaped definition(s).

                newReadClassInfo = currentReadClassInfo.copy(
                    entityInfoContent = unescapedNewClassDef
                )

                updatedOntology = ontology.copy(
                    ontologyMetadata = ontology.ontologyMetadata.copy(
                        lastModificationDate = Some(currentTime)
                    ),
                    classes = ontology.classes + (internalClassIri -> newReadClassInfo)
                )

                _ = storeCacheData(cacheData.copy(
                    ontologies = cacheData.ontologies + (internalOntologyIri -> updatedOntology)
                ))

                // Read the data back from the cache.

                response <- getClassDefinitionsFromOntologyV2(
                    classIris = Set(internalClassIri),
                    allLanguages = true,
                    requestingUser = changeClassLabelsOrCommentsRequest.requestingUser
                )
            } yield response
        }

        for {
            requestingUser <- FastFuture.successful(changeClassLabelsOrCommentsRequest.requestingUser)

            externalClassIri = changeClassLabelsOrCommentsRequest.classIri
            externalOntologyIri = externalClassIri.getOntologyFromEntity

            _ <- checkOntologyAndEntityIrisForUpdate(
                externalOntologyIri = externalOntologyIri,
                externalEntityIri = externalClassIri,
                requestingUser = requestingUser
            )

            internalClassIri = externalClassIri.toOntologySchema(InternalSchema)
            internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

            // Do the remaining pre-update checks and the update while holding an update lock on the ontology.
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID = changeClassLabelsOrCommentsRequest.apiRequestID,
                iri = internalOntologyIri.toString,
                task = () => makeTaskFuture(
                    internalClassIri = internalClassIri,
                    internalOntologyIri = internalOntologyIri
                )
            )
        } yield taskResult
    }

    /**
      * Checks whether an entity is used in the triplestore.
      *
      * @param entityIri              the IRI of the entity.
      * @param errorFun               a function that throws an exception. It will be called if the entity is used.
      * @param ignoreKnoraConstraints if true, ignores the use of the entity in Knora subject or object constraints.
      */
    private def isEntityUsed(entityIri: SmartIri, errorFun: => Nothing, ignoreKnoraConstraints: Boolean = false): Future[Unit] = {
        // #sparql-select
        for {
            isEntityUsedSparql <- Future(queries.sparql.v2.txt.isEntityUsed(
                triplestore = settings.triplestoreType,
                entityIri = entityIri,
                ignoreKnoraConstraints = ignoreKnoraConstraints
            ).toString())

            isEntityUsedResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(isEntityUsedSparql)).mapTo[SparqlSelectResponse]
            // #sparql-select

            _ = if (isEntityUsedResponse.results.bindings.nonEmpty) {
                errorFun
            }
        } yield ()
    }

    /**
      * Before an update of an ontology entity, checks that the entity's external IRI, and that of its ontology,
      * are valid, and checks that the user has permission to update the ontology.
      *
      * @param externalOntologyIri the external IRI of the ontology.
      * @param externalEntityIri   the external IRI of the entity.
      * @param requestingUser      the user making the request.
      */
    private def checkOntologyAndEntityIrisForUpdate(externalOntologyIri: SmartIri,
                                                    externalEntityIri: SmartIri,
                                                    requestingUser: UserADM): Future[Unit] = {
        for {
            _ <- checkExternalOntologyIriForUpdate(externalOntologyIri)
            _ <- checkExternalEntityIriForUpdate(externalEntityIri = externalEntityIri)
            _ <- checkPermissionsForOntologyUpdate(
                internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema),
                requestingUser = requestingUser
            )
        } yield ()
    }

    /**
      * Loads a property definition from the triplestore and converts it to a [[PropertyInfoContentV2]].
      *
      * @param propertyIri the IRI of the property to be loaded.
      * @return a [[PropertyInfoContentV2]] representing the property definition.
      */
    private def loadPropertyDefinition(propertyIri: SmartIri): Future[PropertyInfoContentV2] = {
        for {
            sparql <- Future(queries.sparql.v2.txt.getPropertyDefinition(
                triplestore = settings.triplestoreType,
                propertyIri = propertyIri
            ).toString())

            constructResponse <- (storeManager ? SparqlExtendedConstructRequest(sparql)).mapTo[SparqlExtendedConstructResponse]
        } yield constructResponseToPropertyDefinition(
            propertyIri = propertyIri,
            constructResponse = constructResponse
        )
    }

    /**
      * Given a map of predicate IRIs to predicate objects describing an entity, returns a map of smart IRIs to [[PredicateInfoV2]]
      * objects that can be used to construct an [[EntityInfoContentV2]].
      *
      * @param entityDefMap a map of predicate IRIs to predicate objects.
      * @return a map of smart IRIs to [[PredicateInfoV2]] objects.
      */
    private def getEntityPredicatesFromConstructResponse(entityDefMap: Map[IRI, Seq[LiteralV2]]): Map[SmartIri, PredicateInfoV2] = {
        entityDefMap.map {
            case (predIriStr: IRI, predObjs: Seq[LiteralV2]) =>
                val predicateIri = predIriStr.toSmartIri

                val predicateInfo = PredicateInfoV2(
                    predicateIri = predicateIri,
                    objects = predObjs.map {
                        case IriLiteralV2(iriStr) => SmartIriLiteralV2(iriStr.toSmartIri)
                        case ontoLiteral: OntologyLiteralV2 => ontoLiteral
                        case other => throw InconsistentTriplestoreDataException(s"Predicate $predicateIri has an invalid object: $other")
                    }
                )

                predicateIri -> predicateInfo
        }
    }

    /**
      * Extracts property definitions from a SPARQL CONSTRUCT response.
      *
      * @param propertyIris      the IRIs of the properties to be read.
      * @param constructResponse the SPARQL construct response to be read.
      * @return a map of property IRIs to property definitions.
      */
    private def constructResponseToPropertyDefinitions(propertyIris: Set[SmartIri], constructResponse: SparqlExtendedConstructResponse): Map[SmartIri, PropertyInfoContentV2] = {
        propertyIris.map {
            propertyIri =>
                propertyIri -> constructResponseToPropertyDefinition(
                    propertyIri = propertyIri,
                    constructResponse = constructResponse
                )
        }.toMap
    }

    /**
      * Converts a SPARQL CONSTRUCT response to a [[PropertyInfoContentV2]].
      *
      * @param propertyIri       the IRI of the property to be read.
      * @param constructResponse the SPARQL CONSTRUCT response to be read.
      * @return a [[PropertyInfoContentV2]] representing a property definition.
      */
    private def constructResponseToPropertyDefinition(propertyIri: SmartIri, constructResponse: SparqlExtendedConstructResponse): PropertyInfoContentV2 = {
        // All properties defined in the triplestore must be in Knora ontologies.

        val ontologyIri = propertyIri.getOntologyFromEntity

        if (!ontologyIri.isKnoraOntologyIri) {
            throw InconsistentTriplestoreDataException(s"Property $propertyIri is not in a Knora ontology")
        }

        val statements = constructResponse.statements

        // Get the statements whose subject is the property.
        val propertyDefMap: Map[IRI, Seq[LiteralV2]] = statements(IriSubjectV2(propertyIri.toString))

        val subPropertyOf: Set[SmartIri] = propertyDefMap.get(OntologyConstants.Rdfs.SubPropertyOf) match {
            case Some(baseProperties) =>
                baseProperties.map {
                    case iriLiteral: IriLiteralV2 => iriLiteral.value.toSmartIri
                    case other => throw InconsistentTriplestoreDataException(s"Unexpected object for rdfs:subPropertyOf: $other")
                }.toSet

            case None => Set.empty[SmartIri]
        }

        val otherPreds: Map[SmartIri, PredicateInfoV2] = getEntityPredicatesFromConstructResponse(propertyDefMap - OntologyConstants.Rdfs.SubPropertyOf)

        // salsah-gui:guiOrder isn't allowed here.
        if (otherPreds.contains(OntologyConstants.SalsahGui.GuiOrder.toSmartIri)) {
            throw InconsistentTriplestoreDataException(s"Property $propertyIri contains salsah-gui:guiOrder")
        }

        val propertyDef = PropertyInfoContentV2(
            propertyIri = propertyIri,
            subPropertyOf = subPropertyOf,
            predicates = otherPreds,
            ontologySchema = propertyIri.getOntologySchema.get
        )

        if (!propertyIri.isKnoraBuiltInDefinitionIri && propertyDef.getRdfTypes.contains(OntologyConstants.Owl.TransitiveProperty.toSmartIri)) {
            throw InconsistentTriplestoreDataException(s"Project-specific property $propertyIri cannot be an owl:TransitiveProperty")
        }

        propertyDef
    }

    /**
      * Reads OWL named individuals from a SPARQL CONSTRUCT response.
      *
      * @param individualIris    the IRIs of the named individuals to be read.
      * @param constructResponse the SPARQL CONSTRUCT response.
      * @return a map of individual IRIs to named individuals.
      */
    private def constructResponseToIndividuals(individualIris: Set[SmartIri], constructResponse: SparqlExtendedConstructResponse): Map[SmartIri, IndividualInfoContentV2] = {
        individualIris.map {
            individualIri =>
                individualIri -> constructResponseToIndividual(
                    individualIri = individualIri,
                    constructResponse = constructResponse
                )
        }.toMap
    }

    /**
      * Reads an OWL named individual from a SPARQL CONSTRUCT response.
      *
      * @param individualIri     the IRI of the individual to be read.
      * @param constructResponse the SPARQL CONSTRUCT response.
      * @return an [[IndividualInfoContentV2]] representing the named individual.
      */
    private def constructResponseToIndividual(individualIri: SmartIri, constructResponse: SparqlExtendedConstructResponse): IndividualInfoContentV2 = {
        val statements = constructResponse.statements

        // Get the statements whose subject is the individual.
        val individualMap: Map[IRI, Seq[LiteralV2]] = statements(IriSubjectV2(individualIri.toString))

        val predicates: Map[SmartIri, PredicateInfoV2] = getEntityPredicatesFromConstructResponse(individualMap)

        IndividualInfoContentV2(
            individualIri = individualIri,
            predicates = predicates,
            ontologySchema = individualIri.getOntologySchema.get
        )
    }

    /**
      * Loads a class definition from the triplestore and converts it to a [[ClassInfoContentV2]].
      *
      * @param classIri the IRI of the class to be loaded.
      * @return a [[ClassInfoContentV2]] representing the class definition.
      */
    private def loadClassDefinition(classIri: SmartIri): Future[ClassInfoContentV2] = {
        for {
            sparql <- Future(queries.sparql.v2.txt.getClassDefinition(
                triplestore = settings.triplestoreType,
                classIri = classIri
            ).toString())

            constructResponse <- (storeManager ? SparqlExtendedConstructRequest(sparql)).mapTo[SparqlExtendedConstructResponse]
        } yield constructResponseToClassDefinition(
            classIri = classIri,
            constructResponse = constructResponse
        )
    }

    /**
      * Extracts class definitions from a SPARQL CONSTRUCT response.
      *
      * @param classIris         the IRIs of the classes to be read.
      * @param constructResponse the SPARQL CONSTRUCT response to be read.
      * @return a map of class IRIs to class definitions.
      */
    private def constructResponseToClassDefinitions(classIris: Set[SmartIri], constructResponse: SparqlExtendedConstructResponse): Map[SmartIri, ClassInfoContentV2] = {
        classIris.map {
            classIri =>
                classIri -> constructResponseToClassDefinition(
                    classIri = classIri,
                    constructResponse = constructResponse
                )
        }.toMap
    }

    /**
      * Converts a SPARQL CONSTRUCT response to a [[ClassInfoContentV2]].
      *
      * @param classIri          the IRI of the class to be read.
      * @param constructResponse the SPARQL CONSTRUCT response to be read.
      * @return a [[ClassInfoContentV2]] representing a class definition.
      */
    private def constructResponseToClassDefinition(classIri: SmartIri, constructResponse: SparqlExtendedConstructResponse): ClassInfoContentV2 = {
        // All classes defined in the triplestore must be in Knora ontologies.

        val ontologyIri = classIri.getOntologyFromEntity

        if (!ontologyIri.isKnoraOntologyIri) {
            throw InconsistentTriplestoreDataException(s"Class $classIri is not in a Knora ontology")
        }

        val statements = constructResponse.statements

        // Get the statements whose subject is the class.
        val classDefMap: Map[IRI, Seq[LiteralV2]] = statements(IriSubjectV2(classIri.toString))

        // Get the IRIs of the class's base classes.

        val subClassOfObjects: Seq[LiteralV2] = classDefMap.getOrElse(OntologyConstants.Rdfs.SubClassOf, Seq.empty[LiteralV2])

        val subClassOf: Set[SmartIri] = subClassOfObjects.collect {
            case iriLiteral: IriLiteralV2 => iriLiteral.value.toSmartIri
        }.toSet

        // Get the blank nodes representing cardinalities.

        val restrictionBlankNodeIDs: Set[BlankNodeLiteralV2] = subClassOfObjects.collect {
            case blankNodeLiteral: BlankNodeLiteralV2 => blankNodeLiteral
        }.toSet

        val directCardinalities: Map[SmartIri, KnoraCardinalityInfo] = restrictionBlankNodeIDs.map {
            blankNodeID =>
                val blankNode: Map[IRI, Seq[LiteralV2]] = statements.getOrElse(BlankNodeSubjectV2(blankNodeID.value), throw InconsistentTriplestoreDataException(s"Blank node '${blankNodeID.value}' not found in construct query result"))

                val blankNodeTypeObjs: Seq[LiteralV2] = blankNode.getOrElse(OntologyConstants.Rdf.Type, throw InconsistentTriplestoreDataException(s"Blank node '${blankNodeID.value}' has no rdf:type"))

                blankNodeTypeObjs match {
                    case Seq(IriLiteralV2(OntologyConstants.Owl.Restriction)) => ()
                    case _ => throw InconsistentTriplestoreDataException(s"Blank node '${blankNodeID.value}' is not an owl:Restriction")
                }

                val onPropertyObjs: Seq[LiteralV2] = blankNode.getOrElse(OntologyConstants.Owl.OnProperty, throw InconsistentTriplestoreDataException(s"Blank node '${blankNodeID.value}' has no owl:onProperty"))

                val propertyIri: IRI = onPropertyObjs match {
                    case Seq(propertyIri: IriLiteralV2) => propertyIri.value
                    case other => throw InconsistentTriplestoreDataException(s"Invalid object for owl:onProperty: $other")
                }

                val owlCardinalityPreds: Set[IRI] = blankNode.keySet.filter {
                    predicate => OntologyConstants.Owl.cardinalityOWLRestrictions(predicate)
                }

                if (owlCardinalityPreds.size != 1) {
                    throw InconsistentTriplestoreDataException(s"Expected one cardinality predicate in blank node '${blankNodeID.value}', got ${owlCardinalityPreds.size}")
                }

                val owlCardinalityIri = owlCardinalityPreds.head

                val owlCardinalityValue: Int = blankNode(owlCardinalityIri) match {
                    case Seq(IntLiteralV2(intVal)) => intVal
                    case other => throw InconsistentTriplestoreDataException(s"Expected one integer object for predicate $owlCardinalityIri in blank node '${blankNodeID.value}', got $other")
                }

                val guiOrder: Option[Int] = blankNode.get(OntologyConstants.SalsahGui.GuiOrder) match {
                    case Some(Seq(IntLiteralV2(intVal))) => Some(intVal)
                    case None => None
                    case other => throw InconsistentTriplestoreDataException(s"Expected one integer object for predicate ${OntologyConstants.SalsahGui.GuiOrder} in blank node '${blankNodeID.value}', got $other")
                }

                // salsah-gui:guiElement and salsah-gui:guiAttribute aren't allowed here.

                if (blankNode.contains(OntologyConstants.SalsahGui.GuiElementProp)) {
                    throw InconsistentTriplestoreDataException(s"Class $classIri contains salsah-gui:guiElement in an owl:Restriction")
                }

                if (blankNode.contains(OntologyConstants.SalsahGui.GuiAttribute)) {
                    throw InconsistentTriplestoreDataException(s"Class $classIri contains salsah-gui:guiAttribute in an owl:Restriction")
                }

                propertyIri.toSmartIri -> Cardinality.owlCardinality2KnoraCardinality(
                    propertyIri = propertyIri,
                    owlCardinality = Cardinality.OwlCardinalityInfo(
                        owlCardinalityIri = owlCardinalityIri,
                        owlCardinalityValue = owlCardinalityValue,
                        guiOrder = guiOrder
                    )
                )
        }.toMap

        // Get any other predicates of the class.

        val otherPreds: Map[SmartIri, PredicateInfoV2] = getEntityPredicatesFromConstructResponse(classDefMap - OntologyConstants.Rdfs.SubClassOf)

        ClassInfoContentV2(
            classIri = classIri,
            subClassOf = subClassOf,
            predicates = otherPreds,
            directCardinalities = directCardinalities,
            ontologySchema = classIri.getOntologySchema.get
        )
    }

    /**
      * Checks that a property's `knora-base:subjectClassConstraint` or `knora-base:objectClassConstraint` is compatible with (i.e. a subclass of)
      * the ones in all its base properties.
      *
      * @param internalPropertyIri        the internal IRI of the property to be checked.
      * @param constraintPredicateIri     the internal IRI of the constraint, i.e. `knora-base:subjectClassConstraint` or `knora-base:objectClassConstraint`.
      * @param constraintValueToBeChecked the constraint value to be checked.
      * @param allSuperPropertyIris       the IRIs of all the base properties of the property, including indirect base properties and the property itself.
      * @param errorSchema                the ontology schema to be used for error messages.
      * @param errorFun                   a function that throws an exception. It will be called with an error message argument if the property constraint is invalid.
      */
    private def checkPropertyConstraint(cacheData: OntologyCacheData,
                                        internalPropertyIri: SmartIri,
                                        constraintPredicateIri: SmartIri,
                                        constraintValueToBeChecked: SmartIri,
                                        allSuperPropertyIris: Set[SmartIri],
                                        errorSchema: OntologySchema,
                                        errorFun: String => Nothing): Unit = {
        // The property constraint value must be a Knora class, or one of a limited set of classes defined in OWL.
        val superClassesOfConstraintValueToBeChecked: Set[SmartIri] = if (OntologyConstants.Owl.ClassesThatCanBeKnoraClassConstraints.contains(constraintValueToBeChecked.toString)) {
            Set(constraintValueToBeChecked)
        } else {
            cacheData.subClassOfRelations.getOrElse(
                constraintValueToBeChecked,
                errorFun(s"Property ${internalPropertyIri.toOntologySchema(errorSchema)} cannot have a ${constraintPredicateIri.toOntologySchema(errorSchema)} of " +
                    s"${constraintValueToBeChecked.toOntologySchema(errorSchema)}"
                )
            )
        }

        // Get the definitions of all the Knora superproperties of the property.
        val superPropertyInfos: Set[ReadPropertyInfoV2] = (allSuperPropertyIris - internalPropertyIri).collect {
            case superPropertyIri if superPropertyIri.isKnoraDefinitionIri =>
                cacheData.ontologies(superPropertyIri.getOntologyFromEntity).properties.getOrElse(
                    superPropertyIri,
                    errorFun(s"Property ${internalPropertyIri.toOntologySchema(errorSchema)} is a subproperty of $superPropertyIri, which is undefined")
                )
        }

        // For each superproperty definition, get the value of the specified constraint in that definition, if any. Here we
        // make a map of superproperty IRIs to superproperty constraint values.
        val superPropertyConstraintValues: Map[SmartIri, SmartIri] = superPropertyInfos.flatMap {
            superPropertyInfo =>
                superPropertyInfo.entityInfoContent.predicates.get(constraintPredicateIri).map(_.requireIriObject(throw InconsistentTriplestoreDataException(s"Property ${superPropertyInfo.entityInfoContent.propertyIri} has an invalid object for $constraintPredicateIri"))).map {
                    superPropertyConstraintValue => superPropertyInfo.entityInfoContent.propertyIri -> superPropertyConstraintValue
                }
        }.toMap

        // Check that the constraint value to be checked is a subclass of the constraint value in every superproperty.

        superPropertyConstraintValues.foreach {
            case (superPropertyIri, superPropertyConstraintValue) =>
                if (!superClassesOfConstraintValueToBeChecked.contains(superPropertyConstraintValue)) {
                    errorFun(s"Property ${internalPropertyIri.toOntologySchema(errorSchema)} cannot have a ${constraintPredicateIri.toOntologySchema(errorSchema)} of " +
                        s"${constraintValueToBeChecked.toOntologySchema(errorSchema)}, because that is not a subclass of " +
                        s"${superPropertyConstraintValue.toOntologySchema(errorSchema)}, which is the ${constraintPredicateIri.toOntologySchema(errorSchema)} of " +
                        s"a base property, ${superPropertyIri.toOntologySchema(errorSchema)}"
                    )
                }
        }
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
            errorFun = throw EditConflictException(s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)} has been modified by another user, please reload it and try again.")
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
      * @param requestingUser      the user making the request.
      * @return the project IRI.
      */
    private def checkPermissionsForOntologyUpdate(internalOntologyIri: SmartIri, requestingUser: UserADM): Future[SmartIri] = {
        for {
            cacheData <- getCacheData

            projectIri = cacheData.ontologies.getOrElse(
                internalOntologyIri,
                throw NotFoundException(s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2WithValueObjects)} not found")
            ).ontologyMetadata.projectIri.get

            _ = if (!requestingUser.permissions.isProjectAdmin(projectIri.toString) && !requestingUser.permissions.isSystemAdmin) {
                // not a project or system admin
                throw ForbiddenException("Ontologies can be modified only by a project or system admin.")
            }

        } yield projectIri
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
      * @param externalEntityIri the external IRI of the entity.
      * @return a failed Future if the entity IRI is not valid for an update, or is not from the specified ontology.
      */
    private def checkExternalEntityIriForUpdate(externalEntityIri: SmartIri): Future[Unit] = {
        if (!externalEntityIri.isKnoraApiV2EntityIri) {
            FastFuture.failed(throw BadRequestException(s"Invalid entity IRI for request: $externalEntityIri"))
        } else if (!externalEntityIri.getOntologySchema.contains(ApiV2WithValueObjects)) {
            FastFuture.failed(throw BadRequestException(s"Invalid ontology schema for request: $externalEntityIri"))
        } else {
            FastFuture.successful(())
        }
    }

    /**
      * Given the definition of a link property, returns the definition of the corresponding link value property.
      *
      * @param internalPropertyDef the definition of the the link property, in the internal schema.
      * @return the definition of the corresponding link value property.
      */
    private def linkPropertyDefToLinkValuePropertyDef(internalPropertyDef: PropertyInfoContentV2): PropertyInfoContentV2 = {
        val linkValuePropIri = internalPropertyDef.propertyIri.fromLinkPropToLinkValueProp

        val newPredicates: Map[SmartIri, PredicateInfoV2] = (internalPropertyDef.predicates - OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri) +
            (OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri -> PredicateInfoV2(
                predicateIri = OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
                objects = Seq(SmartIriLiteralV2(OntologyConstants.KnoraBase.LinkValue.toSmartIri))
            ))

        internalPropertyDef.copy(
            propertyIri = linkValuePropIri,
            predicates = newPredicates,
            subPropertyOf = Set(OntologyConstants.KnoraBase.HasLinkToValue.toSmartIri)
        )
    }

    /**
      * Given the cardinalities directly defined on a given resource class, and the cardinalities that it could inherit (directly
      * or indirectly) from its base classes, combines the two, filtering out the base class cardinalities ones that are overridden
      * by cardinalities defined directly on the given class. Checks that if a directly defined cardinality overrides an inheritable one,
      * the directly defined one is at least as restrictive as the inheritable one.
      *
      * @param thisClassCardinalities    the cardinalities directly defined on a given resource class.
      * @param inheritableCardinalities  the cardinalities that the given resource class could inherit from its base classes.
      * @param allSubPropertyOfRelations a map in which each property IRI points to the full set of its base properties.
      * @param errorSchema               the ontology schema to be used in error messages.
      * @param errorFun                  a function that throws an exception. It will be called with an error message argument if the cardinalities are invalid.
      * @return a map in which each key is the IRI of a property that has a cardinality in the resource class (or that it inherits
      *         from its base classes), and each value is the cardinality on the property.
      */
    private def overrideCardinalities(thisClassCardinalities: Map[SmartIri, OwlCardinalityInfo],
                                      inheritableCardinalities: Map[SmartIri, OwlCardinalityInfo],
                                      allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
                                      errorSchema: OntologySchema,
                                      errorFun: String => Nothing): Map[SmartIri, OwlCardinalityInfo] = {
        thisClassCardinalities ++ inheritableCardinalities.filterNot {
            case (baseClassProp, baseClassCardinality) => thisClassCardinalities.exists {
                case (thisClassProp, thisClassCardinality) =>
                    // Can the directly defined cardinality override the inheritable one?

                    val canOverride = allSubPropertyOfRelations.get(thisClassProp) match {
                        case Some(baseProps) =>
                            baseProps.contains(baseClassProp)

                        case None =>
                            // If the class has a cardinality for a non-Knora property like rdfs:label (which can happen only
                            // if it's a built-in class), we won't have any information about the base properties of that property.
                            thisClassProp == baseClassProp
                    }

                    if (canOverride) {
                        // Yes. Is the directly defined one at least as restrictive as the inheritable one?

                        val thisClassKnoraCardinality: KnoraCardinalityInfo = Cardinality.owlCardinality2KnoraCardinality(
                            propertyIri = thisClassProp.toString,
                            owlCardinality = thisClassCardinality
                        )

                        val inheritableKnoraCardinality: KnoraCardinalityInfo = Cardinality.owlCardinality2KnoraCardinality(
                            propertyIri = baseClassProp.toString,
                            owlCardinality = baseClassCardinality
                        )

                        if (!Cardinality.isCompatible(directCardinality = thisClassKnoraCardinality.cardinality, inheritableCardinality = inheritableKnoraCardinality.cardinality)) {
                            // No. Throw an exception.
                            errorFun(s"The directly defined cardinality $thisClassKnoraCardinality on ${thisClassProp.toOntologySchema(errorSchema)} is not compatible with the inherited cardinality $inheritableKnoraCardinality on ${baseClassProp.toOntologySchema(errorSchema)}, because it is less restrictive")
                        } else {
                            // Yes. Filter out the inheritable one, because the directly defined one overrides it.
                            true
                        }
                    } else {
                        // No. Let the class inherit the inheritable cardinality.
                        false
                    }
            }
        }
    }

    /**
      * Given all the `rdfs:subClassOf` relations between classes, calculates all the inverse relations.
      *
      * @param allSubClassOfRelations all the `rdfs:subClassOf` relations between classes.
      * @return a map of IRIs of resource classes to sets of the IRIs of their subclasses.
      */
    private def calculateSuperClassOfRelations(allSubClassOfRelations: Map[SmartIri, Set[SmartIri]]) = {
        allSubClassOfRelations.toVector.flatMap {
            case (subClass: SmartIri, baseClasses: Set[SmartIri]) =>
                baseClasses.toVector.map {
                    baseClass => baseClass -> subClass
                }
        }.groupBy(_._1).map {
            case (baseClass: SmartIri, baseClassAndSubClasses: Vector[(SmartIri, SmartIri)]) =>
                baseClass -> baseClassAndSubClasses.map(_._2).toSet
        }
    }

    /**
      * Recursively walks up an entity hierarchy read from the triplestore, collecting the IRIs of all base entities.
      *
      * @param iri             the IRI of an entity.
      * @param directRelations a map of entities to their direct base entities.
      * @return all the base entities of the specified entity.
      */
    private def getAllBaseDefs(iri: SmartIri, directRelations: Map[SmartIri, Set[SmartIri]]): Set[SmartIri] = {
        def getAllBaseDefsRec(initialIri: SmartIri, currentIri: SmartIri): Set[SmartIri] = {
            directRelations.get(currentIri) match {
                case Some(baseDefs) =>
                    baseDefs ++ baseDefs.flatMap {
                        baseDef =>
                            if (baseDef == initialIri) {
                                throw InconsistentTriplestoreDataException(s"Entity $initialIri has an inheritance cycle with entity $baseDef")
                            } else {
                                getAllBaseDefsRec(initialIri, baseDef)
                            }
                    }

                case None => Set.empty[SmartIri]
            }
        }

        getAllBaseDefsRec(initialIri = iri, currentIri = iri)
    }

    /**
      * Given a class loaded from the triplestore, recursively adds its inherited cardinalities to the cardinalities it defines
      * directly. A cardinality for a subproperty in a subclass overrides a cardinality for a base property in
      * a base class.
      *
      * @param classIri                  the IRI of the class whose properties are to be computed.
      * @param directSubClassOfRelations a map of the direct `rdfs:subClassOf` relations defined on each class.
      * @param allSubPropertyOfRelations a map in which each property IRI points to the full set of its base properties.
      * @param directClassCardinalities  a map of the cardinalities defined directly on each class.
      * @return a map in which each key is the IRI of a property that has a cardinality in the class (or that it inherits
      *         from its base classes), and each value is the cardinality on the property.
      */
    private def inheritCardinalitiesInLoadedClass(classIri: SmartIri,
                                                  directSubClassOfRelations: Map[SmartIri, Set[SmartIri]],
                                                  allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
                                                  directClassCardinalities: Map[SmartIri, Map[SmartIri, OwlCardinalityInfo]]): Map[SmartIri, OwlCardinalityInfo] = {
        // Recursively get properties that are available to inherit from base classes. If we have no information about
        // a class, that could mean that it isn't a subclass of knora-base:Resource (e.g. it's something like
        // foaf:Person), in which case we assume that it has no base classes.
        val cardinalitiesAvailableToInherit: Map[SmartIri, OwlCardinalityInfo] = directSubClassOfRelations.getOrElse(classIri, Set.empty[SmartIri]).foldLeft(Map.empty[SmartIri, OwlCardinalityInfo]) {
            case (acc, baseClass) =>
                acc ++ inheritCardinalitiesInLoadedClass(
                    classIri = baseClass,
                    directSubClassOfRelations = directSubClassOfRelations,
                    allSubPropertyOfRelations = allSubPropertyOfRelations,
                    directClassCardinalities = directClassCardinalities
                )
        }

        // Get the properties that have cardinalities defined directly on this class. Again, if we have no information
        // about a class, we assume that it has no cardinalities.
        val thisClassCardinalities: Map[SmartIri, OwlCardinalityInfo] = directClassCardinalities.getOrElse(classIri, Map.empty[SmartIri, OwlCardinalityInfo])

        // Combine the cardinalities defined directly on this class with the ones that are available to inherit.
        overrideCardinalities(
            thisClassCardinalities = thisClassCardinalities,
            inheritableCardinalities = cardinalitiesAvailableToInherit,
            allSubPropertyOfRelations = allSubPropertyOfRelations,
            errorSchema = InternalSchema,
            { msg: String => throw InconsistentTriplestoreDataException(msg) }
        )
    }

    /**
      * Checks whether a class IRI refers to a Knora internal resource class.
      *
      * @param classIri the class IRI.
      * @return `true` if the class IRI refers to a Knora resource class, or `false` if the class
      *         does not exist or is not a Knora internal resource class.
      */
    private def isKnoraInternalResourceClass(classIri: SmartIri, cacheData: OntologyCacheData): Boolean = {
        classIri.isKnoraInternalEntityIri &&
            cacheData.ontologies(classIri.getOntologyFromEntity).classes.get(classIri).exists(_.isResourceClass)
    }

    /**
      * Checks whether a property is a subproperty of `knora-base:resourceProperty`.
      *
      * @param propertyIri the property IRI.
      * @param cacheData   the ontology cache.
      * @return `true` if the property is a subproperty of `knora-base:resourceProperty`.
      */
    private def isKnoraResourceProperty(propertyIri: SmartIri, cacheData: OntologyCacheData): Boolean = {
        propertyIri.isKnoraEntityIri &&
            cacheData.ontologies(propertyIri.getOntologyFromEntity).properties.get(propertyIri).exists(_.isResourceProp)
    }

    /**
      * Checks whether a property is a subproperty of `knora-base:hasLinkTo`.
      *
      * @param propertyIri the property IRI.
      * @param cacheData   the ontology cache.
      * @return `true` if the property is a subproperty of `knora-base:hasLinkTo`.
      */
    private def isLinkProp(propertyIri: SmartIri, cacheData: OntologyCacheData): Boolean = {
        propertyIri.isKnoraEntityIri &&
            cacheData.ontologies(propertyIri.getOntologyFromEntity).properties.get(propertyIri).exists(_.isLinkProp)
    }

    /**
      * Checks whether a property is a subproperty of `knora-base:hasLinkToValue`.
      *
      * @param propertyIri the property IRI.
      * @param cacheData   the ontology cache.
      * @return `true` if the property is a subproperty of `knora-base:hasLinkToValue`.
      */
    private def isLinkValueProp(propertyIri: SmartIri, cacheData: OntologyCacheData): Boolean = {
        propertyIri.isKnoraEntityIri &&
            cacheData.ontologies(propertyIri.getOntologyFromEntity).properties.get(propertyIri).exists(_.isLinkValueProp)
    }

    /**
      * Checks whether a property is a subproperty of `knora-base:hasFileValue`.
      *
      * @param propertyIri the property IRI.
      * @param cacheData   the ontology cache.
      * @return `true` if the property is a subproperty of `knora-base:hasFileValue`.
      */
    private def isFileValueProp(propertyIri: SmartIri, cacheData: OntologyCacheData): Boolean = {
        propertyIri.isKnoraEntityIri &&
            cacheData.ontologies(propertyIri.getOntologyFromEntity).properties.get(propertyIri).exists(_.isFileValueProp)
    }
}