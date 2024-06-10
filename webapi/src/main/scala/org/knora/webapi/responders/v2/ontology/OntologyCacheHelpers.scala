/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2.ontology

import zio.*
import scala.collection.immutable
import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.*
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.ontology.domain.model.Cardinality.*
import org.knora.webapi.slice.ontology.repo.service.OntologyCache

trait OntologyCacheHelpers {

  /**
   * Requests information about OWL classes in a single ontology.
   *
   * @param classIris      the IRIs (internal or external) of the classes to query for.
   * @param requestingUser the user making the request.
   * @return a [[ReadOntologyV2]].
   */
  def getClassDefinitionsFromOntologyV2(
    classIris: Set[SmartIri],
    allLanguages: Boolean,
    requestingUser: User,
  ): Task[ReadOntologyV2]

  /**
   * Given a list of resource IRIs and a list of property IRIs (ontology entities), returns an [[EntityInfoGetResponseV2]] describing both resource and property entities.
   *
   * @param classIris      the IRIs of the resource entities to be queried.
   * @param propertyIris   the IRIs of the property entities to be queried.
   * @param requestingUser the user making the request.
   * @return an [[EntityInfoGetResponseV2]].
   */
  def getEntityInfoResponseV2(
    classIris: Set[SmartIri] = Set.empty[SmartIri],
    propertyIris: Set[SmartIri] = Set.empty[SmartIri],
    requestingUser: User,
  ): Task[EntityInfoGetResponseV2]

  /**
   * Before an update of an ontology entity, checks that the entity's external IRI, and that of its ontology,
   * are valid, and checks that the user has permission to update the ontology.
   *
   * @param externalOntologyIri the external IRI of the ontology.
   * @param externalEntityIri   the external IRI of the entity.
   * @param requestingUser      the user making the request.
   */
  final def checkOntologyAndEntityIrisForUpdate(
    externalOntologyIri: SmartIri,
    externalEntityIri: SmartIri,
    requestingUser: User,
  ): Task[Unit] =
    for {
      _ <- OntologyHelpers.checkExternalOntologyIriForUpdate(externalOntologyIri)
      _ <- OntologyHelpers.checkExternalEntityIriForUpdate(externalEntityIri)
      _ <- checkPermissionsForOntologyUpdate(
             internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema),
             requestingUser = requestingUser,
           )
    } yield ()

  /**
   * Throws an exception if the requesting user does not have permission to update an ontology.
   *
   * @param internalOntologyIri the internal IRI of the ontology.
   * @param requestingUser      the user making the request.
   * @return the project IRI.
   */
  def checkPermissionsForOntologyUpdate(internalOntologyIri: SmartIri, requestingUser: User): Task[SmartIri]

  /**
   * Checks whether the requesting user has permission to update an ontology.
   *
   * @param internalOntologyIri the internal IRI of the ontology.
   * @param requestingUser      the user making the request.
   * @return `true` if the user has permission to update the ontology
   */
  def canUserUpdateOntology(internalOntologyIri: SmartIri, requestingUser: User): Task[Boolean]
}

final case class OntologyCacheHelpersLive(
  ontologyCache: OntologyCache,
) extends OntologyCacheHelpers {

  override def getClassDefinitionsFromOntologyV2(
    classIris: Set[SmartIri],
    allLanguages: Boolean,
    requestingUser: User,
  ): Task[ReadOntologyV2] =
    for {
      cacheData <- ontologyCache.getCacheData

      ontologyIris = classIris.map(_.getOntologyFromEntity)

      _ = if (ontologyIris.size != 1) {
            throw BadRequestException(s"Only one ontology may be queried per request")
          }

      classInfoResponse  <- getEntityInfoResponseV2(classIris = classIris, requestingUser = requestingUser)
      internalOntologyIri = ontologyIris.head.toOntologySchema(InternalSchema)

      // Are we returning data in the user's preferred language, or in all available languages?
      userLang =
        if (!allLanguages) {
          // Just the user's preferred language.
          Some(requestingUser.lang)
        } else {
          // All available languages.
          None
        }
    } yield ReadOntologyV2(
      ontologyMetadata = cacheData.ontologies(internalOntologyIri).ontologyMetadata,
      classes = classInfoResponse.classInfoMap,
      userLang = userLang,
    )

  override def getEntityInfoResponseV2(
    classIris: Set[SmartIri] = Set.empty[SmartIri],
    propertyIris: Set[SmartIri] = Set.empty[SmartIri],
    requestingUser: User,
  ): Task[EntityInfoGetResponseV2] = {
    for {
      cacheData <- ontologyCache.getCacheData

      // See if any of the requested entities are not Knora entities.

      nonKnoraEntities = (classIris ++ propertyIris).filter(!_.isKnoraEntityIri)

      _ = if (nonKnoraEntities.nonEmpty) {
            throw BadRequestException(
              s"Some requested entities are not Knora entities: ${nonKnoraEntities.mkString(", ")}",
            )
          }

      // See if any of the requested entities are unavailable in the requested schema.

      classesUnavailableInSchema = classIris.foldLeft(Set.empty[SmartIri]) { case (acc, classIri) =>
                                     // Is this class IRI hard-coded in the requested schema?
                                     if (
                                       KnoraBaseToApiV2SimpleTransformationRules.externalClassesToAdd
                                         .contains(classIri) ||
                                       KnoraBaseToApiV2ComplexTransformationRules.externalClassesToAdd
                                         .contains(classIri)
                                     ) {
                                       // Yes, so it's available.
                                       acc
                                     } else {
                                       // No. Is it among the classes removed from the internal ontology in the requested schema?
                                       classIri.getOntologySchema.get match {
                                         case apiV2Schema: ApiV2Schema =>
                                           val internalClassIri =
                                             classIri.toOntologySchema(InternalSchema)
                                           val knoraBaseClassesToRemove = OntologyTransformationRules
                                             .getTransformationRules(
                                               apiV2Schema,
                                             )
                                             .internalClassesToRemove

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

      propertiesUnavailableInSchema = propertyIris.foldLeft(Set.empty[SmartIri]) { case (acc, propertyIri) =>
                                        // Is this property IRI hard-coded in the requested schema?
                                        if (
                                          KnoraBaseToApiV2SimpleTransformationRules.externalPropertiesToAdd
                                            .contains(propertyIri) ||
                                          KnoraBaseToApiV2ComplexTransformationRules.externalPropertiesToAdd
                                            .contains(propertyIri)
                                        ) {
                                          // Yes, so it's available.
                                          acc
                                        } else {
                                          // No. See if it's available in the requested schema.
                                          propertyIri.getOntologySchema.get match {
                                            case apiV2Schema: ApiV2Schema =>
                                              val internalPropertyIri =
                                                propertyIri.toOntologySchema(InternalSchema)

                                              // If it's a link value property and it's requested in the simple schema, it's unavailable.
                                              if (
                                                apiV2Schema == ApiV2Simple && OntologyHelpers
                                                  .isLinkValueProp(
                                                    internalPropertyIri,
                                                    cacheData,
                                                  )
                                              ) {
                                                acc + propertyIri
                                              } else {
                                                // Is it among the properties removed from the internal ontology in the requested schema?

                                                val knoraBasePropertiesToRemove =
                                                  OntologyTransformationRules
                                                    .getTransformationRules(
                                                      apiV2Schema,
                                                    )
                                                    .internalPropertiesToRemove

                                                if (
                                                  knoraBasePropertiesToRemove.contains(
                                                    internalPropertyIri,
                                                  )
                                                ) {
                                                  // Yes. Include it in the set of unavailable properties.
                                                  acc + propertyIri
                                                } else {
                                                  // No. It's available.
                                                  acc
                                                }
                                              }

                                            case InternalSchema => acc
                                          }
                                        }
                                      }

      entitiesUnavailableInSchema = classesUnavailableInSchema ++ propertiesUnavailableInSchema

      _ <- ZIO.when(entitiesUnavailableInSchema.nonEmpty) {
             ZIO.fail(
               NotFoundException(
                 s"Some requested entities were not found: ${entitiesUnavailableInSchema.mkString(", ")}",
               ),
             )
           }

      // See if any of the requested entities are hard-coded for knora-api.
      hardCodedExternalClassesAvailable =
        KnoraBaseToApiV2SimpleTransformationRules.externalClassesToAdd.view
          .filterKeys(classIris)
          .toMap ++
          KnoraBaseToApiV2ComplexTransformationRules.externalClassesToAdd.view.filterKeys(classIris).toMap

      hardCodedExternalPropertiesAvailable =
        KnoraBaseToApiV2SimpleTransformationRules.externalPropertiesToAdd.view
          .filterKeys(propertyIris)
          .toMap ++
          KnoraBaseToApiV2ComplexTransformationRules.externalPropertiesToAdd.view.filterKeys(propertyIris)

      // Convert the remaining external entity IRIs to internal ones.

      internalToExternalClassIris =
        (classIris -- hardCodedExternalClassesAvailable.keySet)
          .map(externalIri => externalIri.toOntologySchema(InternalSchema) -> externalIri)
          .toMap
      internalToExternalPropertyIris =
        (propertyIris -- hardCodedExternalPropertiesAvailable.keySet)
          .map(externalIri => externalIri.toOntologySchema(InternalSchema) -> externalIri)
          .toMap

      classIrisForCache    = internalToExternalClassIris.keySet
      propertyIrisForCache = internalToExternalPropertyIris.keySet

      // Get the entities that are available in the ontology cache.

      classOntologiesForCache =
        cacheData.ontologies.view.filterKeys(classIrisForCache.map(_.getOntologyFromEntity)).toMap.values

      propertyOntologiesForCache =
        cacheData.ontologies.view.filterKeys(propertyIrisForCache.map(_.getOntologyFromEntity)).toMap.values

      classesAvailableFromCache =
        classOntologiesForCache.flatMap(ontology => ontology.classes.view.filterKeys(classIrisForCache).toMap).toMap

      propertiesAvailableFromCache = propertyOntologiesForCache.flatMap { ontology =>
                                       ontology.properties.view.filterKeys(propertyIrisForCache).toMap
                                     }.toMap

      allClassesAvailable    = classesAvailableFromCache ++ hardCodedExternalClassesAvailable
      allPropertiesAvailable = propertiesAvailableFromCache ++ hardCodedExternalPropertiesAvailable

      // See if any entities are missing.

      allExternalClassIrisAvailable = allClassesAvailable.keySet.map { classIri =>
                                        if (classIri.getOntologySchema.contains(InternalSchema)) {
                                          internalToExternalClassIris(classIri)
                                        } else {
                                          classIri
                                        }
                                      }

      allExternalPropertyIrisAvailable = allPropertiesAvailable.keySet.map { propertyIri =>
                                           if (propertyIri.getOntologySchema.contains(InternalSchema)) {
                                             internalToExternalPropertyIris(propertyIri)
                                           } else {
                                             propertyIri
                                           }
                                         }

      missingClasses    = classIris -- allExternalClassIrisAvailable
      missingProperties = propertyIris -- allExternalPropertyIrisAvailable

      missingEntities = missingClasses ++ missingProperties

      _ <- ZIO.when(missingEntities.nonEmpty) {
             ZIO.fail(NotFoundException(s"Some requested entities were not found: ${missingEntities.mkString(", ")}"))
           }

      response = EntityInfoGetResponseV2(
                   classInfoMap = new ErrorHandlingMap(allClassesAvailable, key => s"Resource class $key not found"),
                   propertyInfoMap = new ErrorHandlingMap(allPropertiesAvailable, key => s"Property $key not found"),
                 )
    } yield response
  }

  override def checkPermissionsForOntologyUpdate(
    internalOntologyIri: SmartIri,
    requestingUser: User,
  ): Task[SmartIri] =
    for {
      cacheData <- ontologyCache.getCacheData

      projectIri =
        cacheData.ontologies
          .getOrElse(
            internalOntologyIri,
            throw NotFoundException(s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} not found"),
          )
          .ontologyMetadata
          .projectIri
          .get

      _ = if (
            !requestingUser.permissions.isProjectAdmin(projectIri.toString) && !requestingUser.permissions.isSystemAdmin
          ) {
            // not a project or system admin
            throw ForbiddenException("Ontologies can be modified only by a project or system admin.")
          }

    } yield projectIri

  override def canUserUpdateOntology(internalOntologyIri: SmartIri, requestingUser: User): Task[Boolean] =
    for {
      cacheData <- ontologyCache.getCacheData

      projectIri =
        cacheData.ontologies
          .getOrElse(
            internalOntologyIri,
            throw NotFoundException(s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} not found"),
          )
          .ontologyMetadata
          .projectIri
          .get
    } yield requestingUser.permissions.isProjectAdmin(projectIri.toString) || requestingUser.permissions.isSystemAdmin
}

object OntologyCacheHelpersLive { val layer = ZLayer.derive[OntologyCacheHelpersLive] }
