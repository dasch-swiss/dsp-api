/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of the DaSCH Service Platform.
 *
 *  The DaSCH Service Platform  is free software: you can redistribute it
 *  and/or modify it under the terms of the GNU Affero General Public
 *  License as published by the Free Software Foundation, either version 3
 *  of the License, or (at your option) any later version.
 *
 *  The DaSCH Service Platform is distributed in the hope that it will be
 *  useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 *  of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with the DaSCH Service Platform.  If not, see
 *  <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.responders.v2.ontology

import akka.actor.ActorRef
import akka.http.scaladsl.util.FastFuture
import org.knora.webapi.{ApiV2Complex, InternalSchema, KnoraBaseVersion, OntologySchema}
import org.knora.webapi.exceptions.{
  ApplicationCacheException,
  BadRequestException,
  ForbiddenException,
  InconsistentRepositoryDataException
}
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.util.cache.CacheUtil
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.{OntologyConstants, SmartIri, StringFormatter}
import org.knora.webapi.messages.StringFormatter.SalsahGuiAttributeDefinition
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.{
  SparqlExtendedConstructRequest,
  SparqlExtendedConstructResponse,
  SparqlSelectRequest
}
import org.knora.webapi.messages.util.rdf.SparqlSelectResult
import org.knora.webapi.messages.util.{ErrorHandlingMap, KnoraSystemInstances, OntologyUtil}
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.Cardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages.{
  ClassInfoContentV2,
  IndividualInfoContentV2,
  OntologyMetadataV2,
  PropertyInfoContentV2,
  ReadClassInfoV2,
  ReadOntologyV2,
  ReadPropertyInfoV2
}
import org.knora.webapi.responders.v2.ontology.OntologyHelpers.OntologyGraph
import org.knora.webapi.settings.KnoraSettingsImpl

import scala.concurrent.{ExecutionContext, Future}
import akka.pattern._
import akka.util.Timeout
import com.typesafe.scalalogging.{LazyLogging, Logger}

object Cache extends LazyLogging {

  private val log: Logger = logger

  // The name of the ontology cache.
  private val OntologyCacheName = "ontologyCache"

  // The cache key under which cached ontology data is stored.
  private val OntologyCacheKey = "ontologyCacheData"

  // The global ontology cache lock. This is needed because every ontology update replaces the whole ontology cache
  // (because definitions in one ontology can refer to definitions in another ontology). Without a global lock,
  // concurrent updates (even to different ontologies) could overwrite each other.
  val ONTOLOGY_CACHE_LOCK_IRI = "http://rdfh.ch/ontologies"

  /**
    * The in-memory cache of ontologies.
    *
    * @param ontologies              a map of ontology IRIs to ontologies.
    * @param subClassOfRelations     a map of subclasses to their base classes.
    * @param superClassOfRelations   a map of base classes to their subclasses.
    * @param subPropertyOfRelations  a map of subproperties to their base proeprties.
    * @param guiAttributeDefinitions a map of salsah-gui:Guielement individuals to their GUI attribute definitions.
    * @param standoffProperties      a set of standoff properties.
    */
  case class OntologyCacheData(ontologies: Map[SmartIri, ReadOntologyV2],
                               subClassOfRelations: Map[SmartIri, Seq[SmartIri]],
                               superClassOfRelations: Map[SmartIri, Set[SmartIri]],
                               subPropertyOfRelations: Map[SmartIri, Set[SmartIri]],
                               guiAttributeDefinitions: Map[SmartIri, Set[SalsahGuiAttributeDefinition]],
                               standoffProperties: Set[SmartIri]) {
    lazy val allPropertyDefs: Map[SmartIri, PropertyInfoContentV2] = ontologies.values
      .flatMap(_.properties.map {
        case (propertyIri, readPropertyInfo) => propertyIri -> readPropertyInfo.entityInfoContent
      })
      .toMap
  }

  /**
    * Loads and caches all ontology information.
    *
    * @param featureFactoryConfig the feature factory configuration.
    * @param requestingUser       the user making the request.
    * @return a [[SuccessResponseV2]].
    */
  def loadOntologies(settings: KnoraSettingsImpl,
                     storeManager: ActorRef,
                     featureFactoryConfig: FeatureFactoryConfig,
                     requestingUser: UserADM)(implicit ec: ExecutionContext,
                                              stringFormat: StringFormatter,
                                              timeout: Timeout): Future[SuccessResponseV2] = {
    val loadOntologiesFuture: Future[SuccessResponseV2] = for {
      _ <- Future {
        if (!(requestingUser.id == KnoraSystemInstances.Users.SystemUser.id || requestingUser.permissions.isSystemAdmin)) {
          throw ForbiddenException(s"Only a system administrator can reload ontologies")
        }
      }

      // Get all ontology metadata.
      allOntologyMetadataSparql <- FastFuture.successful(
        org.knora.webapi.messages.twirl.queries.sparql.v2.txt
          .getAllOntologyMetadata(triplestore = settings.triplestoreType)
          .toString())
      allOntologyMetadataResponse: SparqlSelectResult <- (storeManager ? SparqlSelectRequest(allOntologyMetadataSparql))
        .mapTo[SparqlSelectResult]
      allOntologyMetadata: Map[SmartIri, OntologyMetadataV2] = OntologyHelpers.buildOntologyMetadata(
        allOntologyMetadataResponse)

      knoraBaseOntologyMetadata: OntologyMetadataV2 = allOntologyMetadata.getOrElse(
        OntologyConstants.KnoraBase.KnoraBaseOntologyIri.toSmartIri,
        throw InconsistentRepositoryDataException(s"No knora-base ontology found"))
      knoraBaseOntologyVersion: String = knoraBaseOntologyMetadata.ontologyVersion.getOrElse(
        throw InconsistentRepositoryDataException(
          "The knora-base ontology in the repository is not up to date. See the Knora documentation on repository updates."))

      _ = if (knoraBaseOntologyVersion != KnoraBaseVersion) {
        throw InconsistentRepositoryDataException(
          s"The knora-base ontology in the repository has version '$knoraBaseOntologyVersion', but this version of Knora requires '$KnoraBaseVersion'. See the Knora documentation on repository updates.")
      }

      // Get the contents of each named graph containing an ontology.
      ontologyGraphResponseFutures: Iterable[Future[OntologyGraph]] = allOntologyMetadata.keys.map { ontologyIri =>
        val ontologyGraphConstructQuery = org.knora.webapi.messages.twirl.queries.sparql.v2.txt
          .getOntologyGraph(
            triplestore = settings.triplestoreType,
            ontologyGraph = ontologyIri
          )
          .toString

        (storeManager ? SparqlExtendedConstructRequest(
          sparql = ontologyGraphConstructQuery,
          featureFactoryConfig = featureFactoryConfig
        )).mapTo[SparqlExtendedConstructResponse].map { response =>
          OntologyGraph(ontologyIri = ontologyIri, constructResponse = response)
        }
      }

      ontologyGraphs: Iterable[OntologyGraph] <- Future.sequence(ontologyGraphResponseFutures)

      _ = makeOntologyCache(allOntologyMetadata, ontologyGraphs)
    } yield SuccessResponseV2("Ontologies loaded.")

    loadOntologiesFuture.recover {
      case exception: Throwable =>
        exception match {
          case inconsistentRepositoryDataException: InconsistentRepositoryDataException =>
            log.error(inconsistentRepositoryDataException.message)
            SuccessResponseV2(
              s"An error occurred when loading ontologies: ${inconsistentRepositoryDataException.message}")

          case other => throw other
        }
    }
  }

  /**
    * Given ontology metdata and ontology graphs read from the triplestore, constructs the ontology cache.
    *
    * @param allOntologyMetadata a map of ontology IRIs to ontology metadata.
    * @param ontologyGraphs      a list of ontology graphs.
    */
  def makeOntologyCache(allOntologyMetadata: Map[SmartIri, OntologyMetadataV2],
                        ontologyGraphs: Iterable[OntologyGraph])(implicit stringFormatter: StringFormatter): Unit = {
    // Get the IRIs of all the entities in each ontology.

    // A map of ontology IRIs to class IRIs in each ontology.
    val classIrisPerOntology: Map[SmartIri, Set[SmartIri]] = OntologyHelpers.getEntityIrisFromOntologyGraphs(
      ontologyGraphs = ontologyGraphs,
      entityTypes = Set(OntologyConstants.Owl.Class)
    )

    // A map of ontology IRIs to property IRIs in each ontology.
    val propertyIrisPerOntology: Map[SmartIri, Set[SmartIri]] = OntologyHelpers.getEntityIrisFromOntologyGraphs(
      ontologyGraphs = ontologyGraphs,
      entityTypes = Set(
        OntologyConstants.Owl.ObjectProperty,
        OntologyConstants.Owl.DatatypeProperty,
        OntologyConstants.Owl.AnnotationProperty,
        OntologyConstants.Rdf.Property
      )
    )

    // A map of ontology IRIs to named individual IRIs in each ontology.
    val individualIrisPerOntology: Map[SmartIri, Set[SmartIri]] = OntologyHelpers.getEntityIrisFromOntologyGraphs(
      ontologyGraphs = ontologyGraphs,
      entityTypes = Set(OntologyConstants.Owl.NamedIndividual)
    )

    // Construct entity definitions.

    // A map of class IRIs to class definitions.
    val allClassDefs: Map[SmartIri, ClassInfoContentV2] = ontologyGraphs.flatMap { ontologyGraph =>
      OntologyHelpers.constructResponseToClassDefinitions(
        classIris = classIrisPerOntology(ontologyGraph.ontologyIri),
        constructResponse = ontologyGraph.constructResponse
      )
    }.toMap

    // A map of property IRIs to property definitions.
    val allPropertyDefs: Map[SmartIri, PropertyInfoContentV2] = ontologyGraphs.flatMap { ontologyGraph =>
      OntologyHelpers.constructResponseToPropertyDefinitions(
        propertyIris = propertyIrisPerOntology(ontologyGraph.ontologyIri),
        constructResponse = ontologyGraph.constructResponse
      )
    }.toMap

    // A map of OWL named individual IRIs to named individuals.
    val allIndividuals: Map[SmartIri, IndividualInfoContentV2] = ontologyGraphs.flatMap { ontologyGraph =>
      OntologyHelpers.constructResponseToIndividuals(
        individualIris = individualIrisPerOntology(ontologyGraph.ontologyIri),
        constructResponse = ontologyGraph.constructResponse
      )
    }.toMap

    // A map of salsah-gui:Guielement individuals to their GUI attribute definitions.
    val allGuiAttributeDefinitions: Map[SmartIri, Set[SalsahGuiAttributeDefinition]] =
      OntologyHelpers.makeGuiAttributeDefinitions(allIndividuals)

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

    // A map in which each class IRI points to the full sequence of its base classes.
    val allSubClassOfRelations: Map[SmartIri, Seq[SmartIri]] = allClassIris.toSeq.map { classIri =>
      // get the hierarchically ordered base classes.
      val baseClasses: Seq[SmartIri] = OntologyUtil.getAllBaseDefs(classIri, directSubClassOfRelations)
      // prepend the classIri to the sequence of base classes because a class is also a subclass of itself.
      (classIri, classIri +: baseClasses)
    }.toMap

    // A map in which each class IRI points to the full set of its subclasses. A class is also
    // a subclass of itself.
    val allSuperClassOfRelations: Map[SmartIri, Set[SmartIri]] =
      OntologyHelpers.calculateSuperClassOfRelations(allSubClassOfRelations)

    // Make a map in which each property IRI points to the full set of its base properties. A property is also
    // a subproperty of itself.
    val allSubPropertyOfRelations: Map[SmartIri, Set[SmartIri]] = allPropertyIris.map { propertyIri =>
      (propertyIri, OntologyUtil.getAllBaseDefs(propertyIri, directSubPropertyOfRelations).toSet + propertyIri)
    }.toMap

    // A set of all subproperties of knora-base:resourceProperty.
    val allKnoraResourceProps: Set[SmartIri] = allPropertyIris.filter { prop =>
      val allPropSubPropertyOfRelations = allSubPropertyOfRelations(prop)
      prop == OntologyConstants.KnoraBase.ResourceProperty.toSmartIri ||
      allPropSubPropertyOfRelations.contains(OntologyConstants.KnoraBase.HasValue.toSmartIri) ||
      allPropSubPropertyOfRelations.contains(OntologyConstants.KnoraBase.HasLinkTo.toSmartIri)
    }

    // A set of all subproperties of knora-base:hasLinkTo.
    val allLinkProps: Set[SmartIri] = allPropertyIris.filter(prop =>
      allSubPropertyOfRelations(prop).contains(OntologyConstants.KnoraBase.HasLinkTo.toSmartIri))

    // A set of all subproperties of knora-base:hasLinkToValue.
    val allLinkValueProps: Set[SmartIri] = allPropertyIris.filter(prop =>
      allSubPropertyOfRelations(prop).contains(OntologyConstants.KnoraBase.HasLinkToValue.toSmartIri))

    // A set of all subproperties of knora-base:hasFileValue.
    val allFileValueProps: Set[SmartIri] = allPropertyIris.filter(prop =>
      allSubPropertyOfRelations(prop).contains(OntologyConstants.KnoraBase.HasFileValue.toSmartIri))

    // A map of the cardinalities defined directly on each resource class. Each class IRI points to a map of
    // property IRIs to KnoraCardinalityInfo objects.
    val directClassCardinalities: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]] = allClassDefs.map {
      case (classIri, classDef) =>
        classIri -> classDef.directCardinalities
    }

    // Allow each class to inherit cardinalities from its base classes.
    val classCardinalitiesWithInheritance: Map[SmartIri, Map[SmartIri, KnoraCardinalityInfo]] = allClassIris.map {
      resourceClassIri =>
        val resourceClassCardinalities: Map[SmartIri, KnoraCardinalityInfo] =
          OntologyHelpers.inheritCardinalitiesInLoadedClass(
            classIri = resourceClassIri,
            directSubClassOfRelations = directSubClassOfRelations,
            allSubPropertyOfRelations = allSubPropertyOfRelations,
            directClassCardinalities = directClassCardinalities
          )

        resourceClassIri -> resourceClassCardinalities
    }.toMap

    // Construct a ReadClassInfoV2 for each class.
    val readClassInfos: Map[SmartIri, ReadClassInfoV2] = OntologyHelpers.makeReadClassInfos(
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
    val readPropertyInfos: Map[SmartIri, ReadPropertyInfoV2] = OntologyHelpers.makeReadPropertyInfos(
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
    val readIndividualInfos = OntologyHelpers.makeReadIndividualInfos(allIndividuals)

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

    // A set of the IRIs of all properties whose subject class constraint is a standoff class.
    val propertiesWithStandoffTagSubjects: Set[SmartIri] = readPropertyInfos.flatMap {
      case (propertyIri, readPropertyInfo) =>
        readPropertyInfo.entityInfoContent.getPredicateIriObject(
          OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri) match {
          case Some(subjectClassConstraint: SmartIri) =>
            readClassInfos.get(subjectClassConstraint) match {
              case Some(subjectReadClassInfo: ReadClassInfoV2) =>
                if (subjectReadClassInfo.isStandoffClass) {
                  Some(propertyIri)
                } else {
                  None
                }

              case None => None
            }

          case None => None
        }
    }.toSet

    // Construct the ontology cache data.
    val ontologyCacheData: OntologyCacheData = OntologyCacheData(
      ontologies = new ErrorHandlingMap[SmartIri, ReadOntologyV2](readOntologies, { key =>
        s"Ontology not found: $key"
      }),
      subClassOfRelations = new ErrorHandlingMap[SmartIri, Seq[SmartIri]](allSubClassOfRelations, { key =>
        s"Class not found: $key"
      }),
      superClassOfRelations = new ErrorHandlingMap[SmartIri, Set[SmartIri]](allSuperClassOfRelations, { key =>
        s"Class not found: $key"
      }),
      subPropertyOfRelations = new ErrorHandlingMap[SmartIri, Set[SmartIri]](allSubPropertyOfRelations, { key =>
        s"Property not found: $key"
      }),
      guiAttributeDefinitions =
        new ErrorHandlingMap[SmartIri, Set[SalsahGuiAttributeDefinition]](allGuiAttributeDefinitions, { key =>
          s"salsah-gui:Guielement not found: $key"
        }),
      standoffProperties = propertiesUsedInStandoffCardinalities ++ propertiesWithStandoffTagSubjects
    )

    // Check property subject and object class constraints.

    readPropertyInfos.foreach {
      case (propertyIri, readPropertyInfo) =>
        val allSuperPropertyIris: Set[SmartIri] = allSubPropertyOfRelations.getOrElse(propertyIri, Set.empty[SmartIri])

        readPropertyInfo.entityInfoContent.getPredicateIriObject(
          OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri) match {
          case Some(subjectClassConstraint) =>
            // Each property's subject class constraint, if provided, must be a subclass of the subject class constraints of all its base properties.
            checkPropertyConstraint(
              cacheData = ontologyCacheData,
              internalPropertyIri = propertyIri,
              constraintPredicateIri = OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri,
              constraintValueToBeChecked = subjectClassConstraint,
              allSuperPropertyIris = allSuperPropertyIris,
              errorSchema = InternalSchema,
              errorFun = { msg: String =>
                throw InconsistentRepositoryDataException(msg)
              }
            )

            // If the property is defined in a project-specific ontology, its subject class constraint, if provided, must be a Knora resource or standoff class.
            if (!propertyIri.isKnoraBuiltInDefinitionIri) {
              val baseClassesOfSubjectClassConstraint = allSubClassOfRelations(subjectClassConstraint)

              if (!(baseClassesOfSubjectClassConstraint.contains(OntologyConstants.KnoraBase.Resource.toSmartIri) ||
                    baseClassesOfSubjectClassConstraint.contains(OntologyConstants.KnoraBase.StandoffTag.toSmartIri))) {
                throw InconsistentRepositoryDataException(
                  s"Property $propertyIri is defined in a project-specific ontology, but its knora-base:subjectClassConstraint, $subjectClassConstraint, is not a subclass of knora-base:Resource or knora-base:StandoffTag")
              }
            }

          case None => ()
        }

        readPropertyInfo.entityInfoContent.getPredicateIriObject(
          OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri) match {
          case Some(objectClassConstraint) =>
            // Each property's object class constraint, if provided, must be a subclass of the object class constraints of all its base properties.
            checkPropertyConstraint(
              cacheData = ontologyCacheData,
              internalPropertyIri = propertyIri,
              constraintPredicateIri = OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
              constraintValueToBeChecked = objectClassConstraint,
              allSuperPropertyIris = allSuperPropertyIris,
              errorSchema = InternalSchema,
              errorFun = { msg: String =>
                throw InconsistentRepositoryDataException(msg)
              }
            )

          case None =>
            // A resource property must have an object class constraint, unless it's knora-base:resourceProperty.
            if (readPropertyInfo.isResourceProp && propertyIri != OntologyConstants.KnoraBase.ResourceProperty.toSmartIri) {
              throw InconsistentRepositoryDataException(
                s"Property $propertyIri has no knora-base:objectClassConstraint")
            }
        }
    }

    // Check references between ontologies.
    checkReferencesBetweenOntologies(ontologyCacheData)

    // Update the cache.
    storeCacheData(ontologyCacheData)
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
  def checkPropertyConstraint(cacheData: OntologyCacheData,
                              internalPropertyIri: SmartIri,
                              constraintPredicateIri: SmartIri,
                              constraintValueToBeChecked: SmartIri,
                              allSuperPropertyIris: Set[SmartIri],
                              errorSchema: OntologySchema,
                              errorFun: String => Nothing): Unit = {
    // The property constraint value must be a Knora class, or one of a limited set of classes defined in OWL.
    val superClassesOfConstraintValueToBeChecked: Set[SmartIri] =
      if (OntologyConstants.Owl.ClassesThatCanBeKnoraClassConstraints.contains(constraintValueToBeChecked.toString)) {
        Set(constraintValueToBeChecked)
      } else {
        cacheData.subClassOfRelations
          .getOrElse(
            constraintValueToBeChecked,
            errorFun(
              s"Property ${internalPropertyIri.toOntologySchema(errorSchema)} cannot have a ${constraintPredicateIri
                .toOntologySchema(errorSchema)} of " +
                s"${constraintValueToBeChecked.toOntologySchema(errorSchema)}")
          )
          .toSet
      }

    // Get the definitions of all the Knora superproperties of the property.
    val superPropertyInfos: Set[ReadPropertyInfoV2] = (allSuperPropertyIris - internalPropertyIri).collect {
      case superPropertyIri if superPropertyIri.isKnoraDefinitionIri =>
        cacheData
          .ontologies(superPropertyIri.getOntologyFromEntity)
          .properties
          .getOrElse(
            superPropertyIri,
            errorFun(
              s"Property ${internalPropertyIri.toOntologySchema(errorSchema)} is a subproperty of $superPropertyIri, which is undefined")
          )
    }

    // For each superproperty definition, get the value of the specified constraint in that definition, if any. Here we
    // make a map of superproperty IRIs to superproperty constraint values.
    val superPropertyConstraintValues: Map[SmartIri, SmartIri] = superPropertyInfos.flatMap { superPropertyInfo =>
      superPropertyInfo.entityInfoContent.predicates
        .get(constraintPredicateIri)
        .map(_.requireIriObject(throw InconsistentRepositoryDataException(
          s"Property ${superPropertyInfo.entityInfoContent.propertyIri} has an invalid object for $constraintPredicateIri")))
        .map { superPropertyConstraintValue =>
          superPropertyInfo.entityInfoContent.propertyIri -> superPropertyConstraintValue
        }
    }.toMap

    // Check that the constraint value to be checked is a subclass of the constraint value in every superproperty.

    superPropertyConstraintValues.foreach {
      case (superPropertyIri, superPropertyConstraintValue) =>
        if (!superClassesOfConstraintValueToBeChecked.contains(superPropertyConstraintValue)) {
          errorFun(
            s"Property ${internalPropertyIri.toOntologySchema(errorSchema)} cannot have a ${constraintPredicateIri
              .toOntologySchema(errorSchema)} of " +
              s"${constraintValueToBeChecked.toOntologySchema(errorSchema)}, because that is not a subclass of " +
              s"${superPropertyConstraintValue.toOntologySchema(errorSchema)}, which is the ${constraintPredicateIri
                .toOntologySchema(errorSchema)} of " +
              s"a base property, ${superPropertyIri.toOntologySchema(errorSchema)}")
        }
    }
  }

  /**
    * Checks a reference between an ontology entity and another ontology entity to see if the target
    * is in a non-shared ontology in another project.
    *
    * @param ontologyCacheData the ontology cache data.
    * @param sourceEntityIri   the entity whose definition contains the reference.
    * @param targetEntityIri   the entity that's the target of the reference.
    * @param errorFun          a function that throws an exception with the specified message if the reference is invalid.
    */
  private def checkOntologyReferenceInEntity(ontologyCacheData: OntologyCacheData,
                                             sourceEntityIri: SmartIri,
                                             targetEntityIri: SmartIri,
                                             errorFun: String => Nothing): Unit = {
    if (targetEntityIri.isKnoraDefinitionIri) {
      val sourceOntologyIri = sourceEntityIri.getOntologyFromEntity
      val sourceOntologyMetadata = ontologyCacheData.ontologies(sourceOntologyIri).ontologyMetadata

      val targetOntologyIri = targetEntityIri.getOntologyFromEntity
      val targetOntologyMetadata = ontologyCacheData.ontologies(targetOntologyIri).ontologyMetadata

      if (sourceOntologyMetadata.projectIri != targetOntologyMetadata.projectIri) {
        if (!(targetOntologyIri.isKnoraBuiltInDefinitionIri || targetOntologyIri.isKnoraSharedDefinitionIri)) {
          errorFun(
            s"Entity $sourceEntityIri refers to entity $targetEntityIri, which is in a non-shared ontology that belongs to another project")
        }
      }
    }
  }

  /**
    * Checks a property definition to ensure that it doesn't refer to any other non-shared ontologies.
    *
    * @param ontologyCacheData the ontology cache data.
    * @param propertyDef       the property definition.
    * @param errorFun          a function that throws an exception with the specified message if the property definition is invalid.
    */
  def checkOntologyReferencesInPropertyDef(
      ontologyCacheData: OntologyCacheData,
      propertyDef: PropertyInfoContentV2,
      errorFun: String => Nothing)(implicit stringFormatter: StringFormatter): Unit = {
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
    * @param classDef          the class definition.
    * @param errorFun          a function that throws an exception with the specified message if the property definition is invalid.
    */
  def checkOntologyReferencesInClassDef(ontologyCacheData: OntologyCacheData,
                                        classDef: ClassInfoContentV2,
                                        errorFun: String => Nothing): Unit = {
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
  private def checkReferencesBetweenOntologies(ontologyCacheData: OntologyCacheData)(
      implicit stringFormatter: StringFormatter): Unit = {
    for (ontology <- ontologyCacheData.ontologies.values) {
      for (propertyInfo <- ontology.properties.values) {
        checkOntologyReferencesInPropertyDef(
          ontologyCacheData = ontologyCacheData,
          propertyDef = propertyInfo.entityInfoContent,
          errorFun = { msg: String =>
            throw InconsistentRepositoryDataException(msg)
          }
        )
      }

      for (classInfo <- ontology.classes.values) {
        checkOntologyReferencesInClassDef(
          ontologyCacheData = ontologyCacheData,
          classDef = classInfo.entityInfoContent,
          errorFun = { msg: String =>
            throw InconsistentRepositoryDataException(msg)
          }
        )
      }
    }
  }

  /**
    * Updates the ontology cache.
    *
    * @param cacheData the updated data to be cached.
    */
  def storeCacheData(cacheData: OntologyCacheData): Unit = {
    CacheUtil.put(cacheName = OntologyCacheName, key = OntologyCacheKey, value = cacheData)
  }

  /**
    * Gets the ontology data from the cache.
    *
    * @return an [[OntologyCacheData]]
    */
  def getCacheData(implicit ec: ExecutionContext): Future[OntologyCacheData] = {
    Future {
      CacheUtil.get[OntologyCacheData](cacheName = OntologyCacheName, key = OntologyCacheKey) match {
        case Some(data) => data
        case None =>
          throw ApplicationCacheException(
            s"The Knora API server has not loaded any ontologies, perhaps because of an invalid ontology")
      }
    }
  }

  /**
    * Given the IRI of a base class, updates inherited cardinalities in subclasses.
    *
    * @param baseClassIri the internal IRI of the base class.
    * @param cacheData the ontology cache.
    *
    * @return the updated ontology cache.
    */
  def updateSubClasses(baseClassIri: SmartIri, cacheData: OntologyCacheData): OntologyCacheData = {
    // Get the class definitions of all the subclasses of the base class.

    val allSubClassIris: Set[SmartIri] = cacheData.superClassOfRelations(baseClassIri)

    val allSubClasses: Set[ReadClassInfoV2] = allSubClassIris.map { subClassIri =>
      cacheData.ontologies(subClassIri.getOntologyFromEntity).classes(subClassIri)
    }

    // Filter them to get only the direct subclasses.

    val directSubClasses: Set[ReadClassInfoV2] = allSubClasses.filter { subClass =>
      subClass.entityInfoContent.subClassOf
        .contains(baseClassIri) && subClass.entityInfoContent.classIri != baseClassIri
    }

    // Iterate over the subclasses, updating cardinalities.
    val cacheDataWithUpdatedSubClasses = directSubClasses.foldLeft(cacheData) {
      case (cacheDataAcc: OntologyCacheData, directSubClass: ReadClassInfoV2) =>
        val directSubClassIri = directSubClass.entityInfoContent.classIri

        // Get the cardinalities that this subclass can inherit from its direct base classes.

        val inheritableCardinalities: Map[SmartIri, KnoraCardinalityInfo] =
          directSubClass.entityInfoContent.subClassOf.flatMap { baseClassIri =>
            cacheData.ontologies(baseClassIri.getOntologyFromEntity).classes(baseClassIri).allCardinalities
          }.toMap

        // Override inherited cardinalities with directly defined cardinalities.
        val newInheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] = OntologyHelpers.overrideCardinalities(
          classIri = directSubClassIri,
          thisClassCardinalities = directSubClass.entityInfoContent.directCardinalities,
          inheritableCardinalities = inheritableCardinalities,
          allSubPropertyOfRelations = cacheData.subPropertyOfRelations,
          errorSchema = ApiV2Complex,
          errorFun = { msg: String =>
            throw BadRequestException(msg)
          }
        )

        // Update the cache.

        val ontologyIri = directSubClass.entityInfoContent.classIri.getOntologyFromEntity
        val ontology: ReadOntologyV2 = cacheDataAcc.ontologies(ontologyIri)

        val updatedOntology = ontology.copy(
          classes = ontology.classes + (directSubClassIri -> directSubClass.copy(
            inheritedCardinalities = newInheritedCardinalities
          ))
        )

        cacheDataAcc.copy(
          ontologies = cacheDataAcc.ontologies + (ontologyIri -> updatedOntology)
        )
    }

    // Recurse to subclasses of subclasses.

    directSubClasses.map(_.entityInfoContent.classIri).foldLeft(cacheDataWithUpdatedSubClasses) {
      case (cacheDataAcc: OntologyCacheData, directSubClassIri: SmartIri) =>
        updateSubClasses(baseClassIri = directSubClassIri, cacheDataAcc)
    }
  }

}
