/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import akka.http.scaladsl.util.FastFuture
import akka.pattern._

import java.time.Instant
import scala.concurrent.Future

import dsp.constants.SalsahGui
import dsp.errors._
import org.knora.webapi._
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.SparqlUpdateRequest
import org.knora.webapi.messages.store.triplestoremessages.SparqlUpdateResponse
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.twirl.queries.sparql._
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.v2.responder.CanDoResponseV2
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.v2.ontology.Cache
import org.knora.webapi.responders.v2.ontology.Cache.ONTOLOGY_CACHE_LOCK_IRI
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.responders.v2.ontology.OntologyHelpers.isFileValueProp
import org.knora.webapi.responders.v2.ontology.OntologyHelpers.isKnoraResourceProperty
import org.knora.webapi.responders.v2.ontology.OntologyHelpers.isLinkProp
import org.knora.webapi.responders.v2.ontology.OntologyHelpers.isLinkValueProp
import org.knora.webapi.responders.v2.ontology.OntologyLegacyRepo
import org.knora.webapi.util._

/**
 * Responds to requests dealing with ontologies.
 *
 * The API v2 ontology responder reads ontologies from two sources:
 *
 * - The triplestore.
 * - The constant knora-api v2 ontologies that are defined in Scala rather than in the triplestore, [[KnoraBaseToApiV2SimpleTransformationRules]] and [[KnoraBaseToApiV2ComplexTransformationRules]].
 *
 * It maintains an in-memory cache of all ontology data. This cache can be refreshed by sending a [[LoadOntologiesRequestV2]].
 *
 * Read requests to the ontology responder may contain internal or external IRIs as needed. Response messages from the
 * ontology responder will contain internal IRIs and definitions, unless a constant API v2 ontology was requested,
 * in which case the response will be in the requested API v2 schema.
 *
 * In API v2, the ontology responder can also create and update ontologies. Update requests must contain
 * [[ApiV2Complex]] IRIs and definitions.
 *
 * The API v1 ontology responder, which is read-only, delegates most of its work to this responder.
 */
class OntologyResponderV2(responderData: ResponderData) extends Responder(responderData.actorDeps) {

  /**
   * Receives a message of type [[OntologiesResponderRequestV2]], and returns an appropriate response message.
   */
  def receive(msg: OntologiesResponderRequestV2): Future[Product] = msg match {
    case LoadOntologiesRequestV2(requestingUser) =>
      Cache.loadOntologies(appActor, requestingUser)
    case EntityInfoGetRequestV2(classIris, propertyIris, requestingUser) =>
      getEntityInfoResponseV2(classIris, propertyIris, requestingUser)
    case StandoffEntityInfoGetRequestV2(standoffClassIris, standoffPropertyIris, requestingUser) =>
      getStandoffEntityInfoResponseV2(standoffClassIris, standoffPropertyIris, requestingUser)
    case StandoffClassesWithDataTypeGetRequestV2(requestingUser) =>
      getStandoffStandoffClassesWithDataTypeV2(requestingUser)
    case StandoffAllPropertyEntitiesGetRequestV2(requestingUser) => getAllStandoffPropertyEntitiesV2(requestingUser)
    case CheckSubClassRequestV2(subClassIri, superClassIri, _) =>
      checkSubClassV2(subClassIri, superClassIri)
    case SubClassesGetRequestV2(resourceClassIri, requestingUser) => getSubClassesV2(resourceClassIri, requestingUser)
    case OntologyKnoraEntityIrisGetRequestV2(namedGraphIri, requestingUser) =>
      getKnoraEntityIrisInNamedGraphV2(namedGraphIri, requestingUser)
    case OntologyEntitiesGetRequestV2(ontologyIri, allLanguages, requestingUser) =>
      getOntologyEntitiesV2(ontologyIri, allLanguages, requestingUser)
    case ClassesGetRequestV2(resourceClassIris, allLanguages, requestingUser) =>
      getClassDefinitionsFromOntologyV2(resourceClassIris, allLanguages, requestingUser)
    case PropertiesGetRequestV2(propertyIris, allLanguages, requestingUser) =>
      getPropertyDefinitionsFromOntologyV2(propertyIris, allLanguages, requestingUser)
    case OntologyMetadataGetByProjectRequestV2(projectIris, requestingUser) =>
      getOntologyMetadataForProjectsV2(projectIris, requestingUser)
    case OntologyMetadataGetByIriRequestV2(ontologyIris, requestingUser) =>
      getOntologyMetadataByIriV2(ontologyIris, requestingUser)
    case createOntologyRequest: CreateOntologyRequestV2 => createOntology(createOntologyRequest)
    case changeOntologyMetadataRequest: ChangeOntologyMetadataRequestV2 =>
      changeOntologyMetadata(changeOntologyMetadataRequest)
    case deleteOntologyCommentRequest: DeleteOntologyCommentRequestV2 =>
      deleteOntologyComment(deleteOntologyCommentRequest)
    case createClassRequest: CreateClassRequestV2 => createClass(createClassRequest)
    case changeClassLabelsOrCommentsRequest: ChangeClassLabelsOrCommentsRequestV2 =>
      changeClassLabelsOrComments(changeClassLabelsOrCommentsRequest)
    case addCardinalitiesToClassRequest: AddCardinalitiesToClassRequestV2 =>
      addCardinalitiesToClass(addCardinalitiesToClassRequest)
    case replaceCardinalityRequest: ReplaceCardinalitiesRequestV2 =>
      replaceClassCardinalities(replaceCardinalityRequest)
    case canDeleteCardinalitiesFromClassRequestV2: CanDeleteCardinalitiesFromClassRequestV2 =>
      canDeleteCardinalitiesFromClass(canDeleteCardinalitiesFromClassRequestV2)
    case deleteCardinalitiesFromClassRequest: DeleteCardinalitiesFromClassRequestV2 =>
      deleteCardinalitiesFromClass(deleteCardinalitiesFromClassRequest)
    case changeGuiOrderRequest: ChangeGuiOrderRequestV2 => changeGuiOrder(changeGuiOrderRequest)
    case canDeleteClassRequest: CanDeleteClassRequestV2 => canDeleteClass(canDeleteClassRequest)
    case deleteClassRequest: DeleteClassRequestV2       => deleteClass(deleteClassRequest)
    case createPropertyRequest: CreatePropertyRequestV2 => createProperty(createPropertyRequest)
    case changePropertyLabelsOrCommentsRequest: ChangePropertyLabelsOrCommentsRequestV2 =>
      changePropertyLabelsOrComments(changePropertyLabelsOrCommentsRequest)
    case deletePropertyCommentRequest: DeletePropertyCommentRequestV2 =>
      deletePropertyComment(deletePropertyCommentRequest)
    case deleteClassCommentRequest: DeleteClassCommentRequestV2 =>
      deleteClassComment(deleteClassCommentRequest)
    case changePropertyGuiElementRequest: ChangePropertyGuiElementRequest =>
      changePropertyGuiElement(changePropertyGuiElementRequest)
    case canDeletePropertyRequest: CanDeletePropertyRequestV2 => canDeleteProperty(canDeletePropertyRequest)
    case deletePropertyRequest: DeletePropertyRequestV2       => deleteProperty(deletePropertyRequest)
    case canDeleteOntologyRequest: CanDeleteOntologyRequestV2 => canDeleteOntology(canDeleteOntologyRequest)
    case deleteOntologyRequest: DeleteOntologyRequestV2       => deleteOntology(deleteOntologyRequest)
    case other                                                => handleUnexpectedMessage(other, log, this.getClass.getName)
  }

  /**
   * Given a list of resource IRIs and a list of property IRIs (ontology entities), returns an [[EntityInfoGetResponseV2]] describing both resource and property entities.
   *
   * @param classIris      the IRIs of the resource entities to be queried.
   * @param propertyIris   the IRIs of the property entities to be queried.
   * @param requestingUser the user making the request.
   * @return an [[EntityInfoGetResponseV2]].
   */
  private def getEntityInfoResponseV2(
    classIris: Set[SmartIri] = Set.empty[SmartIri],
    propertyIris: Set[SmartIri] = Set.empty[SmartIri],
    requestingUser: UserADM
  ): Future[EntityInfoGetResponseV2] =
    OntologyHelpers.getEntityInfoResponseV2(classIris, propertyIris, requestingUser)

  /**
   * Given a list of standoff class IRIs and a list of property IRIs (ontology entities), returns an [[StandoffEntityInfoGetResponseV2]] describing both resource and property entities.
   *
   * @param standoffClassIris    the IRIs of the resource entities to be queried.
   * @param standoffPropertyIris the IRIs of the property entities to be queried.
   * @param requestingUser       the user making the request.
   * @return a [[StandoffEntityInfoGetResponseV2]].
   */
  private def getStandoffEntityInfoResponseV2(
    standoffClassIris: Set[SmartIri] = Set.empty[SmartIri],
    standoffPropertyIris: Set[SmartIri] = Set.empty[SmartIri],
    requestingUser: UserADM
  ): Future[StandoffEntityInfoGetResponseV2] =
    for {
      cacheData <- Cache.getCacheData

      entitiesInWrongSchema =
        (standoffClassIris ++ standoffPropertyIris).filter(_.getOntologySchema.contains(ApiV2Simple))

      _ = if (entitiesInWrongSchema.nonEmpty) {
            throw NotFoundException(
              s"Some requested standoff classes were not found: ${entitiesInWrongSchema.mkString(", ")}"
            )
          }

      classIrisForCache    = standoffClassIris.map(_.toOntologySchema(InternalSchema))
      propertyIrisForCache = standoffPropertyIris.map(_.toOntologySchema(InternalSchema))

      classOntologies: Iterable[ReadOntologyV2] = cacheData.ontologies.view
                                                    .filterKeys(classIrisForCache.map(_.getOntologyFromEntity))
                                                    .values
      propertyOntologies: Iterable[ReadOntologyV2] = cacheData.ontologies.view
                                                       .filterKeys(propertyIrisForCache.map(_.getOntologyFromEntity))
                                                       .values

      classDefsAvailable: Map[SmartIri, ReadClassInfoV2] = classOntologies.flatMap { ontology =>
                                                             ontology.classes.filter { case (classIri, classDef) =>
                                                               classDef.isStandoffClass && standoffClassIris.contains(
                                                                 classIri
                                                               )
                                                             }
                                                           }.toMap

      propertyDefsAvailable: Map[SmartIri, ReadPropertyInfoV2] = propertyOntologies.flatMap { ontology =>
                                                                   ontology.properties.filter { case (propertyIri, _) =>
                                                                     standoffPropertyIris.contains(
                                                                       propertyIri
                                                                     ) && cacheData.standoffProperties.contains(
                                                                       propertyIri
                                                                     )
                                                                   }
                                                                 }.toMap

      missingClassDefs    = classIrisForCache -- classDefsAvailable.keySet
      missingPropertyDefs = propertyIrisForCache -- propertyDefsAvailable.keySet

      _ = if (missingClassDefs.nonEmpty) {
            throw NotFoundException(
              s"Some requested standoff classes were not found: ${missingClassDefs.mkString(", ")}"
            )
          }

      _ = if (missingPropertyDefs.nonEmpty) {
            throw NotFoundException(
              s"Some requested standoff properties were not found: ${missingPropertyDefs.mkString(", ")}"
            )
          }

      response = StandoffEntityInfoGetResponseV2(
                   standoffClassInfoMap =
                     new ErrorHandlingMap(classDefsAvailable, key => s"Resource class $key not found"),
                   standoffPropertyInfoMap =
                     new ErrorHandlingMap(propertyDefsAvailable, key => s"Property $key not found")
                 )
    } yield response

  /**
   * Gets information about all standoff classes that are a subclass of a data type standoff class.
   *
   * @param requestingUser the user making the request.
   * @return a [[StandoffClassesWithDataTypeGetResponseV2]]
   */
  private def getStandoffStandoffClassesWithDataTypeV2(
    requestingUser: UserADM
  ): Future[StandoffClassesWithDataTypeGetResponseV2] =
    for {
      cacheData <- Cache.getCacheData
    } yield StandoffClassesWithDataTypeGetResponseV2(
      standoffClassInfoMap = cacheData.ontologies.values.flatMap { ontology =>
        ontology.classes.filter { case (_, classDef) =>
          classDef.isStandoffClass && classDef.standoffDataType.isDefined
        }
      }.toMap
    )

  /**
   * Gets all standoff property entities.
   *
   * @param requestingUser the user making the request.
   * @return a [[StandoffAllPropertyEntitiesGetResponseV2]].
   */
  private def getAllStandoffPropertyEntitiesV2(
    requestingUser: UserADM
  ): Future[StandoffAllPropertyEntitiesGetResponseV2] =
    for {
      cacheData <- Cache.getCacheData
    } yield StandoffAllPropertyEntitiesGetResponseV2(
      standoffAllPropertiesEntityInfoMap = cacheData.ontologies.values.flatMap { ontology =>
        ontology.properties.view.filterKeys(cacheData.standoffProperties)
      }.toMap
    )

  /**
   * Checks whether a certain Knora resource or value class is a subclass of another class.
   *
   * @param subClassIri   the IRI of the resource or value class whose subclassOf relations have to be checked.
   * @param superClassIri the IRI of the resource or value class to check for (whether it is a a super class of `subClassIri` or not).
   * @return a [[CheckSubClassResponseV2]].
   */
  private def checkSubClassV2(subClassIri: SmartIri, superClassIri: SmartIri): Future[CheckSubClassResponseV2] =
    for {
      cacheData <- Cache.getCacheData
      response = CheckSubClassResponseV2(
                   isSubClass = cacheData.subClassOfRelations.get(subClassIri) match {
                     case Some(baseClasses) => baseClasses.contains(superClassIri)
                     case None              => throw BadRequestException(s"Class $subClassIri not found")
                   }
                 )
    } yield response

  /**
   * Gets the IRIs of the subclasses of a class.
   *
   * @param classIri the IRI of the class whose subclasses should be returned.
   * @return a [[SubClassesGetResponseV2]].
   */
  private def getSubClassesV2(classIri: SmartIri, requestingUser: UserADM): Future[SubClassesGetResponseV2] =
    for {
      cacheData <- Cache.getCacheData

      subClassIris = cacheData.superClassOfRelations(classIri).toVector.sorted

      subClasses = subClassIris.map { subClassIri =>
                     val classInfo: ReadClassInfoV2 =
                       cacheData.ontologies(subClassIri.getOntologyFromEntity).classes(subClassIri)

                     SubClassInfoV2(
                       id = subClassIri,
                       label = classInfo.entityInfoContent
                         .getPredicateStringLiteralObject(
                           predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                           preferredLangs = Some(requestingUser.lang, responderData.appConfig.fallbackLanguage)
                         )
                         .getOrElse(
                           throw InconsistentRepositoryDataException(s"Resource class $subClassIri has no rdfs:label")
                         )
                     )
                   }
    } yield SubClassesGetResponseV2(
      subClasses = subClasses
    )

  /**
   * Gets the [[OntologyKnoraEntitiesIriInfoV2]] for an ontology.
   *
   * @param ontologyIri    the IRI of the ontology to query
   * @param requestingUser the user making the request.
   * @return an [[OntologyKnoraEntitiesIriInfoV2]].
   */
  private def getKnoraEntityIrisInNamedGraphV2(
    ontologyIri: SmartIri,
    requestingUser: UserADM
  ): Future[OntologyKnoraEntitiesIriInfoV2] =
    for {
      cacheData <- Cache.getCacheData
      ontology   = cacheData.ontologies(ontologyIri)
    } yield OntologyKnoraEntitiesIriInfoV2(
      ontologyIri = ontologyIri,
      propertyIris = ontology.properties.keySet.filter { propertyIri =>
        isKnoraResourceProperty(propertyIri, cacheData)
      },
      classIris = ontology.classes.filter { case (_, classDef) =>
        classDef.isResourceClass
      }.keySet,
      standoffClassIris = ontology.classes.filter { case (_, classDef) =>
        classDef.isStandoffClass
      }.keySet,
      standoffPropertyIris = ontology.properties.keySet.filter(cacheData.standoffProperties)
    )

  /**
   * Gets the metadata describing the ontologies that belong to selected projects, or to all projects.
   *
   * @param projectIris    the IRIs of the projects selected, or an empty set if all projects are selected.
   * @param requestingUser the user making the request.
   * @return a [[ReadOntologyMetadataV2]].
   */
  private def getOntologyMetadataForProjectsV2(
    projectIris: Set[SmartIri],
    requestingUser: UserADM
  ): Future[ReadOntologyMetadataV2] =
    for {
      cacheData                   <- Cache.getCacheData
      returnAllOntologies: Boolean = projectIris.isEmpty

      ontologyMetadata: Set[OntologyMetadataV2] =
        if (returnAllOntologies) {
          cacheData.ontologies.values.map(_.ontologyMetadata).toSet
        } else {
          cacheData.ontologies.values.filter { ontology =>
            projectIris.contains(ontology.ontologyMetadata.projectIri.get)
          }.map { ontology =>
            ontology.ontologyMetadata
          }.toSet
        }
    } yield ReadOntologyMetadataV2(
      ontologies = ontologyMetadata
    )

  /**
   * Gets the metadata describing the specified ontologies, or all ontologies.
   *
   * @param ontologyIris   the IRIs of the ontologies selected, or an empty set if all ontologies are selected.
   * @param requestingUser the user making the request.
   * @return a [[ReadOntologyMetadataV2]].
   */
  private def getOntologyMetadataByIriV2(
    ontologyIris: Set[SmartIri],
    requestingUser: UserADM
  ): Future[ReadOntologyMetadataV2] =
    for {
      cacheData                   <- Cache.getCacheData
      returnAllOntologies: Boolean = ontologyIris.isEmpty

      ontologyMetadata: Set[OntologyMetadataV2] =
        if (returnAllOntologies) {
          cacheData.ontologies.values.map(_.ontologyMetadata).toSet
        } else {
          val ontologyIrisForCache =
            ontologyIris.map(_.toOntologySchema(InternalSchema))
          val missingOntologies =
            ontologyIrisForCache -- cacheData.ontologies.keySet

          if (missingOntologies.nonEmpty) {
            throw BadRequestException(
              s"One or more requested ontologies were not found: ${missingOntologies
                  .mkString(", ")}"
            )
          }

          cacheData.ontologies.view
            .filterKeys(ontologyIrisForCache)
            .values
            .map { ontology =>
              ontology.ontologyMetadata
            }
            .toSet
        }
    } yield ReadOntologyMetadataV2(
      ontologies = ontologyMetadata
    )

  /**
   * Requests the entities defined in the given ontology.
   *
   * @param ontologyIri    the IRI (internal or external) of the ontology to be queried.
   * @param requestingUser the user making the request.
   * @return a [[ReadOntologyV2]].
   */
  private def getOntologyEntitiesV2(
    ontologyIri: SmartIri,
    allLanguages: Boolean,
    requestingUser: UserADM
  ): Future[ReadOntologyV2] =
    for {
      cacheData <- Cache.getCacheData

      _ = if (ontologyIri.getOntologyName == "standoff" && ontologyIri.getOntologySchema.contains(ApiV2Simple)) {
            throw BadRequestException(s"The standoff ontology is not available in the API v2 simple schema")
          }

      ontology = cacheData.ontologies.get(ontologyIri.toOntologySchema(InternalSchema)) match {
                   case Some(cachedOntology) => cachedOntology
                   case None                 => throw NotFoundException(s"Ontology not found: $ontologyIri")
                 }

      // Are we returning data in the user's preferred language, or in all available languages?
      userLang =
        if (!allLanguages) {
          // Just the user's preferred language.
          Some(requestingUser.lang)
        } else {
          // All available languages.
          None
        }
    } yield ontology.copy(
      userLang = userLang
    )

  /**
   * Requests information about OWL classes in a single ontology.
   *
   * @param classIris      the IRIs (internal or external) of the classes to query for.
   * @param requestingUser the user making the request.
   * @return a [[ReadOntologyV2]].
   */
  private def getClassDefinitionsFromOntologyV2(
    classIris: Set[SmartIri],
    allLanguages: Boolean,
    requestingUser: UserADM
  ): Future[ReadOntologyV2] =
    OntologyHelpers.getClassDefinitionsFromOntologyV2(classIris, allLanguages, requestingUser)

  /**
   * Requests information about properties in a single ontology.
   *
   * @param propertyIris   the IRIs (internal or external) of the properties to query for.
   * @param requestingUser the user making the request.
   * @return a [[ReadOntologyV2]].
   */
  private def getPropertyDefinitionsFromOntologyV2(
    propertyIris: Set[SmartIri],
    allLanguages: Boolean,
    requestingUser: UserADM
  ): Future[ReadOntologyV2] =
    for {
      cacheData <- Cache.getCacheData

      ontologyIris = propertyIris.map(_.getOntologyFromEntity)

      _ = if (ontologyIris.size != 1) {
            throw BadRequestException(s"Only one ontology may be queried per request")
          }

      propertyInfoResponse: EntityInfoGetResponseV2 <-
        getEntityInfoResponseV2(propertyIris = propertyIris, requestingUser = requestingUser)
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
      properties = propertyInfoResponse.propertyInfoMap,
      userLang = userLang
    )

  /**
   * Creates a new, empty ontology.
   *
   * @param createOntologyRequest the request message.
   * @return a [[SuccessResponseV2]].
   */
  private def createOntology(createOntologyRequest: CreateOntologyRequestV2): Future[ReadOntologyMetadataV2] = {
    def makeTaskFuture(internalOntologyIri: SmartIri): Future[ReadOntologyMetadataV2] =
      for {
        // Make sure the ontology doesn't already exist.
        existingOntologyMetadata: Option[OntologyMetadataV2] <- OntologyHelpers.loadOntologyMetadata(
                                                                  appActor,
                                                                  internalOntologyIri = internalOntologyIri
                                                                )

        _ = if (existingOntologyMetadata.nonEmpty) {
              throw BadRequestException(
                s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} cannot be created, because it already exists"
              )
            }

        // If this is a shared ontology, make sure it's in the default shared ontologies project.
        _ =
          if (
            createOntologyRequest.isShared && createOntologyRequest.projectIri.toString != OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject
          ) {
            throw BadRequestException(
              s"Shared ontologies must be created in project <${OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject}>"
            )
          }

        // If it's in the default shared ontologies project, make sure it's a shared ontology.
        _ =
          if (
            createOntologyRequest.projectIri.toString == OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject && !createOntologyRequest.isShared
          ) {
            throw BadRequestException(
              s"Ontologies created in project <${OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject}> must be shared"
            )
          }

        // Create the ontology.

        currentTime: Instant = Instant.now

        createOntologySparql = v2.txt
                                 .createOntology(
                                   ontologyNamedGraphIri = internalOntologyIri,
                                   ontologyIri = internalOntologyIri,
                                   projectIri = createOntologyRequest.projectIri,
                                   isShared = createOntologyRequest.isShared,
                                   ontologyLabel = createOntologyRequest.label,
                                   ontologyComment = createOntologyRequest.comment,
                                   currentTime = currentTime
                                 )
                                 .toString

        _ <- appActor.ask(SparqlUpdateRequest(createOntologySparql)).mapTo[SparqlUpdateResponse]

        // Check that the update was successful. To do this, we have to undo the SPARQL-escaping of the input.

        unescapedNewMetadata = OntologyMetadataV2(
                                 ontologyIri = internalOntologyIri,
                                 projectIri = Some(createOntologyRequest.projectIri),
                                 label = Some(createOntologyRequest.label),
                                 comment = createOntologyRequest.comment,
                                 lastModificationDate = Some(currentTime)
                               ).unescape

        maybeLoadedOntologyMetadata: Option[OntologyMetadataV2] <- OntologyHelpers.loadOntologyMetadata(
                                                                     appActor,
                                                                     internalOntologyIri = internalOntologyIri
                                                                   )

        _ = maybeLoadedOntologyMetadata match {
              case Some(loadedOntologyMetadata) =>
                if (loadedOntologyMetadata != unescapedNewMetadata) {
                  throw UpdateNotPerformedException()
                }

              case None => throw UpdateNotPerformedException()
            }

        // Update the ontology cache with the unescaped metadata.

        _ <- Cache.cacheUpdatedOntologyWithoutUpdatingMaps(
               internalOntologyIri,
               ReadOntologyV2(ontologyMetadata = unescapedNewMetadata)
             )

      } yield ReadOntologyMetadataV2(ontologies = Set(unescapedNewMetadata))

    for {
      requestingUser <- FastFuture.successful(createOntologyRequest.requestingUser)
      projectIri      = createOntologyRequest.projectIri

      // check if the requesting user is allowed to create an ontology
      _ =
        if (
          !(requestingUser.permissions.isProjectAdmin(projectIri.toString) || requestingUser.permissions.isSystemAdmin)
        ) {
          throw ForbiddenException(
            s"A new ontology in the project ${createOntologyRequest.projectIri} can only be created by an admin of that project, or by a system admin."
          )
        }

      // Get project info for the shortcode.
      projectInfo: ProjectGetResponseADM <-
        appActor
          .ask(
            ProjectGetRequestADM(identifier =
              IriIdentifier
                .fromString(projectIri.toString)
                .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
            )
          )
          .mapTo[ProjectGetResponseADM]

      // Check that the ontology name is valid.
      validOntologyName =
        stringFormatter.validateProjectSpecificOntologyName(
          createOntologyRequest.ontologyName,
          throw BadRequestException(s"Invalid project-specific ontology name: ${createOntologyRequest.ontologyName}")
        )

      // Make the internal ontology IRI.
      internalOntologyIri = stringFormatter.makeProjectSpecificInternalOntologyIri(
                              validOntologyName,
                              createOntologyRequest.isShared,
                              projectInfo.project.shortcode
                            )

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID = createOntologyRequest.apiRequestID,
                      iri = ONTOLOGY_CACHE_LOCK_IRI,
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
  private def changeOntologyMetadata(
    changeOntologyMetadataRequest: ChangeOntologyMetadataRequestV2
  ): Future[ReadOntologyMetadataV2] = {
    def makeTaskFuture(internalOntologyIri: SmartIri): Future[ReadOntologyMetadataV2] =
      for {
        cacheData <- Cache.getCacheData

        // Check that the user has permission to update the ontology.
        projectIri <- OntologyHelpers.checkPermissionsForOntologyUpdate(
                        internalOntologyIri = internalOntologyIri,
                        requestingUser = changeOntologyMetadataRequest.requestingUser
                      )

        // Check that the ontology exists and has not been updated by another user since the client last read its metadata.
        _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = changeOntologyMetadataRequest.lastModificationDate
             )

        // get the metadata of the ontology.
        oldMetadata: OntologyMetadataV2 = cacheData.ontologies(internalOntologyIri).ontologyMetadata
        // Was there a comment in the ontology metadata?
        ontologyHasComment: Boolean = oldMetadata.comment.nonEmpty

        // Update the metadata.

        currentTime: Instant = Instant.now

        updateSparql = v2.txt
                         .changeOntologyMetadata(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           newLabel = changeOntologyMetadataRequest.label,
                           hasOldComment = ontologyHasComment,
                           deleteOldComment = ontologyHasComment && changeOntologyMetadataRequest.comment.nonEmpty,
                           newComment = changeOntologyMetadataRequest.comment,
                           lastModificationDate = changeOntologyMetadataRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

        // Check that the update was successful. To do this, we have to undo the SPARQL-escaping of the input.

        // Is there any new label given?
        label =
          if (changeOntologyMetadataRequest.label.isEmpty) {
            // No. Consider the old label for checking the update.
            oldMetadata.label
          } else {
            // Yes. Consider the new label for checking the update.
            changeOntologyMetadataRequest.label
          }

        // Is there any new comment given?
        comment =
          if (changeOntologyMetadataRequest.comment.isEmpty) {
            // No. Consider the old comment for checking the update.
            oldMetadata.comment
          } else {
            // Yes. Consider the new comment for checking the update.
            changeOntologyMetadataRequest.comment
          }

        unescapedNewMetadata = OntologyMetadataV2(
                                 ontologyIri = internalOntologyIri,
                                 projectIri = Some(projectIri),
                                 label = label,
                                 comment = comment,
                                 lastModificationDate = Some(currentTime)
                               ).unescape

        maybeLoadedOntologyMetadata: Option[OntologyMetadataV2] <-
          OntologyHelpers.loadOntologyMetadata(
            appActor,
            internalOntologyIri = internalOntologyIri
          )

        _ = maybeLoadedOntologyMetadata match {
              case Some(loadedOntologyMetadata) =>
                if (loadedOntologyMetadata != unescapedNewMetadata) {
                  throw UpdateNotPerformedException()
                }

              case None => throw UpdateNotPerformedException()
            }

        // Update the ontology cache with the unescaped metadata.
        updatedOntology = cacheData
                            .ontologies(internalOntologyIri)
                            .copy(ontologyMetadata = unescapedNewMetadata)
        _ <- Cache.cacheUpdatedOntologyWithoutUpdatingMaps(
               internalOntologyIri,
               updatedOntology
             )

      } yield ReadOntologyMetadataV2(ontologies = Set(unescapedNewMetadata))

    for {
      _                  <- OntologyHelpers.checkExternalOntologyIriForUpdate(changeOntologyMetadataRequest.ontologyIri)
      internalOntologyIri = changeOntologyMetadataRequest.ontologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID = changeOntologyMetadataRequest.apiRequestID,
                      iri = ONTOLOGY_CACHE_LOCK_IRI,
                      task = () => makeTaskFuture(internalOntologyIri = internalOntologyIri)
                    )
    } yield taskResult
  }

  def deleteOntologyComment(
    deleteOntologyCommentRequestV2: DeleteOntologyCommentRequestV2
  ): Future[ReadOntologyMetadataV2] = {
    def makeTaskFuture(internalOntologyIri: SmartIri): Future[ReadOntologyMetadataV2] =
      for {
        cacheData <- Cache.getCacheData

        // Check that the user has permission to update the ontology.
        projectIri <- OntologyHelpers.checkPermissionsForOntologyUpdate(
                        internalOntologyIri = internalOntologyIri,
                        requestingUser = deleteOntologyCommentRequestV2.requestingUser
                      )

        // Check that the ontology exists and has not been updated by another user since the client last read its metadata.
        _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = deleteOntologyCommentRequestV2.lastModificationDate
             )

        // get the metadata of the ontology.
        oldMetadata: OntologyMetadataV2 = cacheData.ontologies(internalOntologyIri).ontologyMetadata
        // Was there a comment in the ontology metadata?
        ontologyHasComment: Boolean = oldMetadata.comment.nonEmpty

        // Update the metadata.

        currentTime: Instant = Instant.now

        updateSparql = v2.txt
                         .changeOntologyMetadata(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           newLabel = None,
                           hasOldComment = ontologyHasComment,
                           deleteOldComment = true,
                           newComment = None,
                           lastModificationDate = deleteOntologyCommentRequestV2.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

        // Check that the update was successful.

        unescapedNewMetadata = OntologyMetadataV2(
                                 ontologyIri = internalOntologyIri,
                                 projectIri = Some(projectIri),
                                 label = oldMetadata.label,
                                 comment = None,
                                 lastModificationDate = Some(currentTime)
                               ).unescape

        maybeLoadedOntologyMetadata: Option[OntologyMetadataV2] <-
          OntologyHelpers.loadOntologyMetadata(
            appActor,
            internalOntologyIri = internalOntologyIri
          )

        _ = maybeLoadedOntologyMetadata match {
              case Some(loadedOntologyMetadata) =>
                if (loadedOntologyMetadata != unescapedNewMetadata) {
                  throw UpdateNotPerformedException()
                }

              case None => throw UpdateNotPerformedException()
            }

        // Update the ontology cache with the unescaped metadata.

        updatedOntology = cacheData
                            .ontologies(internalOntologyIri)
                            .copy(ontologyMetadata = unescapedNewMetadata)
        _ <- Cache.cacheUpdatedOntologyWithoutUpdatingMaps(internalOntologyIri, updatedOntology)

      } yield ReadOntologyMetadataV2(ontologies = Set(unescapedNewMetadata))

    for {
      _                  <- OntologyHelpers.checkExternalOntologyIriForUpdate(deleteOntologyCommentRequestV2.ontologyIri)
      internalOntologyIri = deleteOntologyCommentRequestV2.ontologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID = deleteOntologyCommentRequestV2.apiRequestID,
                      iri = ONTOLOGY_CACHE_LOCK_IRI,
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
        cacheData                           <- Cache.getCacheData
        internalClassDef: ClassInfoContentV2 = createClassRequest.classInfoContent.toOntologySchema(InternalSchema)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = createClassRequest.lastModificationDate
             )

        // Check that the class's rdf:type is owl:Class.

        rdfType: SmartIri = internalClassDef.requireIriObject(
                              OntologyConstants.Rdf.Type.toSmartIri,
                              throw BadRequestException(s"No rdf:type specified")
                            )

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

        missingBaseClasses =
          internalClassDef.subClassOf
            .filter(_.isKnoraInternalEntityIri)
            .filter(baseClassIri => !OntologyHelpers.isKnoraInternalResourceClass(baseClassIri, cacheData))

        _ = if (missingBaseClasses.nonEmpty) {
              throw BadRequestException(
                s"One or more specified base classes are invalid: ${missingBaseClasses.mkString(", ")}"
              )
            }

        // Check for rdfs:subClassOf cycles.

        allBaseClassIrisWithoutSelf: Set[SmartIri] = internalClassDef.subClassOf.flatMap { baseClassIri =>
                                                       cacheData.subClassOfRelations
                                                         .getOrElse(baseClassIri, Set.empty[SmartIri])
                                                         .toSet
                                                     }

        _ = if (allBaseClassIrisWithoutSelf.contains(internalClassIri)) {
              throw BadRequestException(
                s"Class ${createClassRequest.classInfoContent.classIri} would have a cyclical rdfs:subClassOf"
              )
            }

        // Check that the class is a subclass of knora-base:Resource.

        allBaseClassIris: Seq[SmartIri] = internalClassIri +: allBaseClassIrisWithoutSelf.toSeq

        _ = if (!allBaseClassIris.contains(OntologyConstants.KnoraBase.Resource.toSmartIri)) {
              throw BadRequestException(
                s"Class ${createClassRequest.classInfoContent.classIri} would not be a subclass of knora-api:Resource"
              )
            }

        // Check that the cardinalities are valid, and add any inherited cardinalities.
        (internalClassDefWithLinkValueProps, cardinalitiesForClassWithInheritance) =
          OntologyHelpers
            .checkCardinalitiesBeforeAddingAndIfNecessaryAddLinkValueProperties(
              internalClassDef = internalClassDef,
              allBaseClassIris = allBaseClassIris.toSet,
              cacheData = cacheData
            )
            .fold(e => throw e.head, v => v)

        // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
        _ = Cache.checkOntologyReferencesInClassDef(
              cache = cacheData,
              classDef = internalClassDefWithLinkValueProps,
              errorFun = { msg: String =>
                throw BadRequestException(msg)
              }
            )

        // Prepare to update the ontology cache, undoing the SPARQL-escaping of the input.

        propertyIrisOfAllCardinalitiesForClass = cardinalitiesForClassWithInheritance.keySet

        inheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] =
          cardinalitiesForClassWithInheritance.filterNot { case (propertyIri, _) =>
            internalClassDefWithLinkValueProps.directCardinalities.contains(propertyIri)
          }

        unescapedClassDefWithLinkValueProps = internalClassDefWithLinkValueProps.unescape

        readClassInfo = ReadClassInfoV2(
                          entityInfoContent = unescapedClassDefWithLinkValueProps,
                          allBaseClasses = allBaseClassIris,
                          isResourceClass = true,
                          canBeInstantiated = true,
                          inheritedCardinalities = inheritedCardinalities,
                          knoraResourceProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                            isKnoraResourceProperty(propertyIri, cacheData)
                          ),
                          linkProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                            isLinkProp(propertyIri, cacheData)
                          ),
                          linkValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                            isLinkValueProp(propertyIri, cacheData)
                          ),
                          fileValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                            isFileValueProp(propertyIri, cacheData)
                          )
                        )

        // Add the SPARQL-escaped class to the triplestore.

        currentTime: Instant = Instant.now

        updateSparql = v2.txt
                         .createClass(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           classDef = internalClassDefWithLinkValueProps,
                           lastModificationDate = createClassRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

        // Check that the ontology's last modification date was updated.

        _ <- OntologyHelpers.checkOntologyLastModificationDateAfterUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = currentTime
             )

        // Check that the data that was saved corresponds to the data that was submitted.

        loadedClassDef <- OntologyHelpers.loadClassDefinition(
                            appActor,
                            classIri = internalClassIri
                          )

        _ = if (loadedClassDef != unescapedClassDefWithLinkValueProps) {
              throw InconsistentRepositoryDataException(
                s"Attempted to save class definition $unescapedClassDefWithLinkValueProps, but $loadedClassDef was saved"
              )
            }

        // Update the cache.

        updatedOntology = ontology.copy(
                            ontologyMetadata = ontology.ontologyMetadata.copy(
                              lastModificationDate = Some(currentTime)
                            ),
                            classes = ontology.classes + (internalClassIri -> readClassInfo)
                          )

        _ <- Cache.cacheUpdatedOntologyWithClass(internalOntologyIri, updatedOntology, internalClassIri)

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

      externalClassIri    = createClassRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <- OntologyHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri = externalOntologyIri,
             externalEntityIri = externalClassIri,
             requestingUser = requestingUser
           )

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID = createClassRequest.apiRequestID,
                      iri = ONTOLOGY_CACHE_LOCK_IRI,
                      task = () =>
                        makeTaskFuture(
                          internalClassIri = internalClassIri,
                          internalOntologyIri = internalOntologyIri
                        )
                    )
    } yield taskResult
  }

  /**
   * Changes GUI orders in cardinalities in a class definition.
   *
   * @param changeGuiOrderRequest the request message.
   * @return the updated class definition.
   */
  private def changeGuiOrder(changeGuiOrderRequest: ChangeGuiOrderRequestV2): Future[ReadOntologyV2] = {
    def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologyV2] = {
      for {
        cacheData                           <- Cache.getCacheData
        internalClassDef: ClassInfoContentV2 = changeGuiOrderRequest.classInfoContent.toOntologySchema(InternalSchema)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = changeGuiOrderRequest.lastModificationDate
             )

        // Check that the class's rdf:type is owl:Class.

        rdfType: SmartIri = internalClassDef.requireIriObject(
                              OntologyConstants.Rdf.Type.toSmartIri,
                              throw BadRequestException(s"No rdf:type specified")
                            )

        _ = if (rdfType != OntologyConstants.Owl.Class.toSmartIri) {
              throw BadRequestException(s"Invalid rdf:type for property: $rdfType")
            }

        // Check that the class exists.

        ontology = cacheData.ontologies(internalOntologyIri)

        currentReadClassInfo: ReadClassInfoV2 =
          ontology.classes
            .getOrElse(
              internalClassIri,
              throw BadRequestException(s"Class ${changeGuiOrderRequest.classInfoContent.classIri} does not exist")
            )

        // Check that the properties submitted already have cardinalities.

        wrongProperties: Set[SmartIri] =
          internalClassDef.directCardinalities.keySet -- currentReadClassInfo.entityInfoContent.directCardinalities.keySet

        _ = if (wrongProperties.nonEmpty) {
              throw BadRequestException(
                s"One or more submitted properties do not have cardinalities in class ${changeGuiOrderRequest.classInfoContent.classIri}: ${wrongProperties
                    .map(_.toOntologySchema(ApiV2Complex))
                    .mkString(", ")}"
              )
            }

        linkValuePropCardinalities = internalClassDef.directCardinalities.filter {
                                       case (propertyIri: SmartIri, _: KnoraCardinalityInfo) =>
                                         val propertyDef = cacheData
                                           .ontologies(propertyIri.getOntologyFromEntity)
                                           .properties(propertyIri)
                                         propertyDef.isLinkProp
                                     }.map {
                                       case (
                                             propertyIri: SmartIri,
                                             cardinalityWithCurrentGuiOrder: KnoraCardinalityInfo
                                           ) =>
                                         propertyIri.fromLinkPropToLinkValueProp -> cardinalityWithCurrentGuiOrder
                                     }

        internalClassDefWithLinkValueProps = internalClassDef.directCardinalities ++ linkValuePropCardinalities

        // Make an updated class definition.

        newReadClassInfo =
          currentReadClassInfo.copy(
            entityInfoContent = currentReadClassInfo.entityInfoContent.copy(
              directCardinalities = currentReadClassInfo.entityInfoContent.directCardinalities.map {
                case (propertyIri: SmartIri, cardinalityWithCurrentGuiOrder: KnoraCardinalityInfo) =>
                  internalClassDefWithLinkValueProps.get(propertyIri) match {
                    case Some(cardinalityWithNewGuiOrder) =>
                      propertyIri -> cardinalityWithCurrentGuiOrder.copy(guiOrder = cardinalityWithNewGuiOrder.guiOrder)

                    case None => propertyIri -> cardinalityWithCurrentGuiOrder
                  }
              }
            )
          )

        // Replace the cardinalities in the class definition in the triplestore.

        currentTime: Instant = Instant.now

        updateSparql = v2.txt
                         .replaceClassCardinalities(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           classIri = internalClassIri,
                           newCardinalities = newReadClassInfo.entityInfoContent.directCardinalities,
                           lastModificationDate = changeGuiOrderRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

        // Check that the ontology's last modification date was updated.

        _ <- OntologyHelpers.checkOntologyLastModificationDateAfterUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = currentTime
             )

        // Check that the data that was saved corresponds to the data that was submitted.

        loadedClassDef: ClassInfoContentV2 <- OntologyHelpers.loadClassDefinition(
                                                appActor,
                                                classIri = internalClassIri
                                              )

        _ = if (loadedClassDef != newReadClassInfo.entityInfoContent) {
              throw InconsistentRepositoryDataException(
                s"Attempted to save class definition ${newReadClassInfo.entityInfoContent}, but $loadedClassDef was saved"
              )
            }

        // Update the cache.

        updatedOntology = ontology.copy(
                            ontologyMetadata = ontology.ontologyMetadata.copy(
                              lastModificationDate = Some(currentTime)
                            ),
                            classes = ontology.classes + (internalClassIri -> newReadClassInfo)
                          )

        // Update subclasses and write the cache.

        _ <- Cache.cacheUpdatedOntologyWithClass(internalOntologyIri, updatedOntology, internalClassIri)

        // Read the data back from the cache.

        response <- getClassDefinitionsFromOntologyV2(
                      classIris = Set(internalClassIri),
                      allLanguages = true,
                      requestingUser = changeGuiOrderRequest.requestingUser
                    )

      } yield response
    }

    for {
      requestingUser <- FastFuture.successful(changeGuiOrderRequest.requestingUser)

      externalClassIri    = changeGuiOrderRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <- OntologyHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri = externalOntologyIri,
             externalEntityIri = externalClassIri,
             requestingUser = requestingUser
           )

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID = changeGuiOrderRequest.apiRequestID,
                      iri = ONTOLOGY_CACHE_LOCK_IRI,
                      task = () =>
                        makeTaskFuture(
                          internalClassIri = internalClassIri,
                          internalOntologyIri = internalOntologyIri
                        )
                    )
    } yield taskResult
  }

  /**
   * Adds cardinalities to an existing class definition.
   *
   * @param addCardinalitiesRequest the request to add the cardinalities.
   * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
   */
  private def addCardinalitiesToClass(
    addCardinalitiesRequest: AddCardinalitiesToClassRequestV2
  ): Future[ReadOntologyV2] = {
    def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologyV2] = {
      for {
        cacheData                           <- Cache.getCacheData
        internalClassDef: ClassInfoContentV2 = addCardinalitiesRequest.classInfoContent.toOntologySchema(InternalSchema)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = addCardinalitiesRequest.lastModificationDate
             )

        // Check that the class's rdf:type is owl:Class.

        rdfType: SmartIri = internalClassDef.requireIriObject(
                              OntologyConstants.Rdf.Type.toSmartIri,
                              throw BadRequestException(s"No rdf:type specified")
                            )

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

        existingReadClassInfo: ReadClassInfoV2 =
          ontology.classes.getOrElse(
            internalClassIri,
            throw BadRequestException(s"Class ${addCardinalitiesRequest.classInfoContent.classIri} does not exist")
          )

        existingClassDef: ClassInfoContentV2 = existingReadClassInfo.entityInfoContent

        redundantCardinalities = existingClassDef.directCardinalities.keySet
                                   .intersect(internalClassDef.directCardinalities.keySet)

        _ = if (redundantCardinalities.nonEmpty) {
              throw BadRequestException(
                s"The cardinalities of ${addCardinalitiesRequest.classInfoContent.classIri} already include the following property or properties: ${redundantCardinalities
                    .mkString(", ")}"
              )
            }

        // Is there any property with minCardinality>0 or Cardinality=1?
        hasCardinality: Option[(SmartIri, KnoraCardinalityInfo)] =
          addCardinalitiesRequest.classInfoContent.directCardinalities.find {
            case (_, constraint: KnoraCardinalityInfo) => constraint.cardinality.min > 0
          }

        _ <- hasCardinality match {
               // If there is, check that the class isn't used in data.
               case Some((propIri: SmartIri, cardinality: KnoraCardinalityInfo)) =>
                 iriService.throwIfClassIsUsedInData(
                   classIri = internalClassIri,
                   errorFun = throw BadRequestException(
                     s"Cardinality ${cardinality.toString} for $propIri cannot be added to class ${addCardinalitiesRequest.classInfoContent.classIri}, because it is used in data"
                   )
                 )
               case None => Future.successful(())
             }

        // Make an updated class definition.

        newInternalClassDef = existingClassDef.copy(
                                directCardinalities =
                                  existingClassDef.directCardinalities ++ internalClassDef.directCardinalities
                              )

        // Check that the new cardinalities are valid, and add any inherited cardinalities.

        allBaseClassIrisWithoutInternal: Seq[SmartIri] = newInternalClassDef.subClassOf.toSeq.flatMap { baseClassIri =>
                                                           cacheData.subClassOfRelations.getOrElse(
                                                             baseClassIri,
                                                             Seq.empty[SmartIri]
                                                           )
                                                         }

        allBaseClassIris: Seq[SmartIri] = internalClassIri +: allBaseClassIrisWithoutInternal

        (newInternalClassDefWithLinkValueProps, cardinalitiesForClassWithInheritance) =
          OntologyHelpers
            .checkCardinalitiesBeforeAddingAndIfNecessaryAddLinkValueProperties(
              internalClassDef = newInternalClassDef,
              allBaseClassIris = allBaseClassIris.toSet,
              cacheData = cacheData,
              existingLinkPropsToKeep = existingReadClassInfo.linkProperties
            )
            .fold(e => throw e.head, v => v)

        // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
        _ = Cache.checkOntologyReferencesInClassDef(
              cache = cacheData,
              classDef = newInternalClassDefWithLinkValueProps,
              errorFun = { msg: String =>
                throw BadRequestException(msg)
              }
            )

        // Prepare to update the ontology cache. (No need to deal with SPARQL-escaping here, because there
        // isn't any text to escape in cardinalities.)

        propertyIrisOfAllCardinalities = cardinalitiesForClassWithInheritance.keySet

        inheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] =
          cardinalitiesForClassWithInheritance.filterNot { case (propertyIri, _) =>
            newInternalClassDefWithLinkValueProps.directCardinalities.contains(propertyIri)
          }

        readClassInfo = ReadClassInfoV2(
                          entityInfoContent = newInternalClassDefWithLinkValueProps,
                          allBaseClasses = allBaseClassIris,
                          isResourceClass = true,
                          canBeInstantiated = true,
                          inheritedCardinalities = inheritedCardinalities,
                          knoraResourceProperties =
                            propertyIrisOfAllCardinalities.filter(isKnoraResourceProperty(_, cacheData)),
                          linkProperties = propertyIrisOfAllCardinalities.filter(isLinkProp(_, cacheData)),
                          linkValueProperties = propertyIrisOfAllCardinalities.filter(isLinkValueProp(_, cacheData)),
                          fileValueProperties = propertyIrisOfAllCardinalities.filter(isFileValueProp(_, cacheData))
                        )

        // Add the cardinalities to the class definition in the triplestore.

        currentTime: Instant = Instant.now

        cardinalitiesToAdd: Map[SmartIri, KnoraCardinalityInfo] =
          newInternalClassDefWithLinkValueProps.directCardinalities -- existingClassDef.directCardinalities.keySet

        updateSparql = v2.txt
                         .addCardinalitiesToClass(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           classIri = internalClassIri,
                           cardinalitiesToAdd = cardinalitiesToAdd,
                           lastModificationDate = addCardinalitiesRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

        // Check that the ontology's last modification date was updated.

        _ <- OntologyHelpers.checkOntologyLastModificationDateAfterUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = currentTime
             )

        // Check that the data that was saved corresponds to the data that was submitted.

        loadedClassDef <- OntologyHelpers.loadClassDefinition(
                            appActor,
                            classIri = internalClassIri
                          )

        _ = if (loadedClassDef != newInternalClassDefWithLinkValueProps) {
              throw InconsistentRepositoryDataException(
                s"Attempted to save class definition $newInternalClassDefWithLinkValueProps, but $loadedClassDef was saved"
              )
            }

        // Update subclasses and write the cache.

        updatedOntology = ontology.copy(
                            ontologyMetadata = ontology.ontologyMetadata.copy(
                              lastModificationDate = Some(currentTime)
                            ),
                            classes = ontology.classes + (internalClassIri -> readClassInfo)
                          )

        _ <- Cache.cacheUpdatedOntologyWithClass(internalOntologyIri, updatedOntology, internalClassIri)

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

      externalClassIri    = addCardinalitiesRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <- OntologyHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri = externalOntologyIri,
             externalEntityIri = externalClassIri,
             requestingUser = requestingUser
           )

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID = addCardinalitiesRequest.apiRequestID,
                      iri = ONTOLOGY_CACHE_LOCK_IRI,
                      task = () =>
                        makeTaskFuture(
                          internalClassIri = internalClassIri,
                          internalOntologyIri = internalOntologyIri
                        )
                    )
    } yield taskResult
  }

  private def checkRdfTypeOfClassIsClass(classInfo: ClassInfoContentV2): ClassInfoContentV2 = {
    val rdfType: SmartIri = classInfo.requireIriObject(
      OntologyConstants.Rdf.Type.toSmartIri,
      throw BadRequestException(s"No rdf:type specified")
    )
    if (rdfType != OntologyConstants.Owl.Class.toSmartIri) {
      throw BadRequestException(s"Invalid rdf:type for property: $rdfType")
    }
    classInfo
  }

  // Make an updated class definition.
  // Check that the new cardinalities are valid, and don't add any inherited cardinalities.
  // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
  private def makeUpdatedClassModel(request: ReplaceCardinalitiesRequestV2): Future[ReadClassInfoV2] = {
    val newClassInfo        = checkRdfTypeOfClassIsClass(request.classInfoContent.toOntologySchema(InternalSchema))
    val classIriExternal    = newClassInfo.classIri
    val classIri            = classIriExternal.toOntologySchema(InternalSchema)
    val ontologyIriExternal = classIri.getOntologyFromEntity
    val ontologyIri         = ontologyIriExternal.toOntologySchema(InternalSchema)
    for {
      cacheData <- OntologyLegacyRepo.getCache
      oldClassInfo <-
        OntologyLegacyRepo
          .findClassBy(classIri, ontologyIri)
          .map(_.getOrElse(throw BadRequestException(s"Class $ontologyIriExternal does not exist")).entityInfoContent)

      newInternalClassDef: ClassInfoContentV2 =
        oldClassInfo.copy(directCardinalities = newClassInfo.directCardinalities)

      // Check that the new cardinalities are valid, and don't add any inherited cardinalities.
      allBaseClassIrisWithoutInternal: Seq[SmartIri] = newInternalClassDef.subClassOf.toSeq.flatMap { baseClassIri =>
                                                         cacheData.subClassOfRelations.getOrElse(
                                                           baseClassIri,
                                                           Seq.empty[SmartIri]
                                                         )
                                                       }

      allBaseClassIris: Seq[SmartIri] = classIri +: allBaseClassIrisWithoutInternal

      (newInternalClassDefWithLinkValueProps, cardinalitiesForClassWithInheritance) =
        OntologyHelpers
          .checkCardinalitiesBeforeAddingAndIfNecessaryAddLinkValueProperties(
            internalClassDef = newInternalClassDef,
            allBaseClassIris = allBaseClassIris.toSet,
            cacheData = cacheData
          )
          .fold(e => throw e.head, v => v)

      // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
      _ = Cache.checkOntologyReferencesInClassDef(
            cache = cacheData,
            classDef = newInternalClassDefWithLinkValueProps,
            errorFun = { msg: String =>
              throw BadRequestException(msg)
            }
          )

      // Build the model
      propertyIrisOfAllCardinalitiesForClass: Set[SmartIri] = cardinalitiesForClassWithInheritance.keySet

      inheritedCardinalities: Map[SmartIri, KnoraCardinalityInfo] =
        cardinalitiesForClassWithInheritance.filterNot { case (propertyIri, _) =>
          newInternalClassDefWithLinkValueProps.directCardinalities.contains(propertyIri)
        }
      readClassInfo = ReadClassInfoV2(
                        entityInfoContent = newInternalClassDefWithLinkValueProps,
                        allBaseClasses = allBaseClassIris,
                        isResourceClass = true,
                        canBeInstantiated = true,
                        inheritedCardinalities = inheritedCardinalities,
                        knoraResourceProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                          isKnoraResourceProperty(propertyIri, cacheData)
                        ),
                        linkProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                          isLinkProp(propertyIri, cacheData)
                        ),
                        linkValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                          isLinkValueProp(propertyIri, cacheData)
                        ),
                        fileValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                          isFileValueProp(propertyIri, cacheData)
                        )
                      )
    } yield readClassInfo
  }

  private def replaceClassCardinalitiesInPersistence(
    request: ReplaceCardinalitiesRequestV2,
    newReadClassInfo: ReadClassInfoV2
  ): Future[ReadOntologyV2] = {
    val timeOfUpdate = Instant.now()
    val classIri     = request.classInfoContent.classIri.toOntologySchema(InternalSchema)
    for {
      _ <- replaceClassCardinalitiesInTripleStore(request, newReadClassInfo, timeOfUpdate)
      _ <- replaceClassCardinalitiesInOntologyCache(request, newReadClassInfo, timeOfUpdate)
      // Return the response with the new data from the cache
      response <- getClassDefinitionsFromOntologyV2(
                    classIris = Set(classIri),
                    allLanguages = true,
                    requestingUser = request.requestingUser
                  )
    } yield response
  }

  private def replaceClassCardinalitiesInTripleStore(
    request: ReplaceCardinalitiesRequestV2,
    newReadClassInfo: ReadClassInfoV2,
    timeOfUpdate: Instant
  ): Future[Unit] = {
    val classIri    = request.classInfoContent.classIri.toOntologySchema(InternalSchema)
    val ontologyIri = classIri.getOntologyFromEntity
    val updateSparql = v2.txt
      .replaceClassCardinalities(
        ontologyNamedGraphIri = ontologyIri,
        ontologyIri = ontologyIri,
        classIri = classIri,
        newCardinalities = newReadClassInfo.entityInfoContent.directCardinalities,
        lastModificationDate = request.lastModificationDate,
        currentTime = timeOfUpdate
      )
      .toString()
    for {
      _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]
      _ <- OntologyHelpers.checkOntologyLastModificationDateAfterUpdate(
             appActor,
             internalOntologyIri = ontologyIri,
             expectedLastModificationDate = timeOfUpdate
           )
      loadedClassDef <- OntologyHelpers.loadClassDefinition(appActor, classIri)
      _ = if (loadedClassDef != newReadClassInfo.entityInfoContent) {
            throw InconsistentRepositoryDataException(
              s"Attempted to save class definition ${newReadClassInfo.entityInfoContent}, but $loadedClassDef was saved"
            )
          }
    } yield ()
  }

  private def replaceClassCardinalitiesInOntologyCache(
    request: ReplaceCardinalitiesRequestV2,
    newReadClassInfo: ReadClassInfoV2,
    timeOfUpdate: Instant
  ): Future[Unit] = {
    val classIriExternal    = request.classInfoContent.classIri
    val classIri            = classIriExternal.toOntologySchema(InternalSchema)
    val ontologyIriExternal = classIriExternal.getOntologyFromEntity
    val ontologyIri         = classIri.getOntologyFromEntity
    for {
      ontology <- OntologyLegacyRepo
                    .findOntologyBy(ontologyIri)
                    .map(_.getOrElse(throw BadRequestException(s"Ontology $ontologyIriExternal does not exist")))
      updatedOntologyMetaData = ontology.ontologyMetadata.copy(lastModificationDate = Some(timeOfUpdate))
      updatedOntologyClasses  = ontology.classes + (classIri -> newReadClassInfo)
      updatedOntology         = ontology.copy(ontologyMetadata = updatedOntologyMetaData, classes = updatedOntologyClasses)
      _                      <- Cache.cacheUpdatedOntologyWithClass(ontologyIri, updatedOntology, classIri)
    } yield ()
  }

  /**
   * Replaces a class's cardinalities with new ones.
   *
   * @param request the [[ReplaceCardinalitiesRequestV2]] defining the cardinalities.
   * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
   */
  def replaceClassCardinalities(request: ReplaceCardinalitiesRequestV2): Future[ReadOntologyV2] = {
    val classIriExternal    = request.classInfoContent.classIri
    val classIri            = classIriExternal.toOntologySchema(InternalSchema)
    val ontologyIriExternal = classIriExternal.getOntologyFromEntity

    def taskFuture: () => Future[ReadOntologyV2] = () =>
      for {
        newReadClassInfo <- makeUpdatedClassModel(request)
        _                <- checkPreconditions(request)
        response         <- replaceClassCardinalitiesInPersistence(request, newReadClassInfo)
      } yield response

    for {
      _ <- OntologyHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri = ontologyIriExternal,
             externalEntityIri = classIriExternal,
             requestingUser = request.requestingUser
           )
      response <- IriLocker.runWithIriLock(request.apiRequestID, ONTOLOGY_CACHE_LOCK_IRI, taskFuture)
    } yield response
  }

  private def checkPreconditions(request: ReplaceCardinalitiesRequestV2): Future[Unit] = {
    val classIriExternal = request.classInfoContent.classIri
    val classIri         = classIriExternal.toOntologySchema(InternalSchema)
    val ontologyIri      = classIri.getOntologyFromEntity
    for {
      _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
             appActor,
             internalOntologyIri = ontologyIri,
             expectedLastModificationDate = request.lastModificationDate
           )
      _ <- iriService.throwIfEntityIsUsed(
             entityIri = classIri,
             ignoreKnoraConstraints = true,
             errorFun = throw BadRequestException(
               s"The cardinalities of class $classIriExternal cannot be changed, because it is used in data or has a subclass"
             )
           )
    } yield ()
  }

  /**
   * FIXME(DSP-1856): Only works if a single cardinality is supplied.
   * Checks if cardinalities can be removed from a class.
   *
   * @param canDeleteCardinalitiesFromClassRequest the request to remove cardinalities.
   * @return a [[CanDoResponseV2]] indicating whether a class's cardinalities can be deleted.
   */
  private def canDeleteCardinalitiesFromClass(
    canDeleteCardinalitiesFromClassRequest: CanDeleteCardinalitiesFromClassRequestV2
  ): Future[CanDoResponseV2] =
    for {
      requestingUser <- FastFuture.successful(canDeleteCardinalitiesFromClassRequest.requestingUser)

      externalClassIri    = canDeleteCardinalitiesFromClassRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <- OntologyHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri = externalOntologyIri,
             externalEntityIri = externalClassIri,
             requestingUser = requestingUser
           )

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID = canDeleteCardinalitiesFromClassRequest.apiRequestID,
                      iri = ONTOLOGY_CACHE_LOCK_IRI,
                      task = () =>
                        CardinalityHandler.canDeleteCardinalitiesFromClass(
                          appActor,
                          deleteCardinalitiesFromClassRequest = canDeleteCardinalitiesFromClassRequest,
                          internalClassIri = internalClassIri,
                          internalOntologyIri = internalOntologyIri
                        )
                    )
    } yield taskResult

  /**
   * FIXME(DSP-1856): Only works if a single cardinality is supplied.
   * Removes cardinalities (from class to properties) from class if properties are not used inside data.
   *
   * @param deleteCardinalitiesFromClassRequest the request to remove cardinalities.
   * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
   */
  private def deleteCardinalitiesFromClass(
    deleteCardinalitiesFromClassRequest: DeleteCardinalitiesFromClassRequestV2
  ): Future[ReadOntologyV2] =
    for {
      requestingUser <- FastFuture.successful(deleteCardinalitiesFromClassRequest.requestingUser)

      externalClassIri    = deleteCardinalitiesFromClassRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <- OntologyHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri = externalOntologyIri,
             externalEntityIri = externalClassIri,
             requestingUser = requestingUser
           )

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID = deleteCardinalitiesFromClassRequest.apiRequestID,
                      iri = ONTOLOGY_CACHE_LOCK_IRI,
                      task = () =>
                        CardinalityHandler.deleteCardinalitiesFromClass(
                          appActor,
                          deleteCardinalitiesFromClassRequest = deleteCardinalitiesFromClassRequest,
                          internalClassIri = internalClassIri,
                          internalOntologyIri = internalOntologyIri
                        )
                    )
    } yield taskResult

  /**
   * Checks whether a class can be deleted.
   *
   * @param canDeleteClassRequest the request message.
   * @return a [[CanDoResponseV2]].
   */
  private def canDeleteClass(canDeleteClassRequest: CanDeleteClassRequestV2): Future[CanDoResponseV2] = {
    val internalClassIri: SmartIri    = canDeleteClassRequest.classIri.toOntologySchema(InternalSchema)
    val internalOntologyIri: SmartIri = internalClassIri.getOntologyFromEntity

    for {
      cacheData <- Cache.getCacheData

      ontology =
        cacheData.ontologies.getOrElse(
          internalOntologyIri,
          throw BadRequestException(s"Ontology ${canDeleteClassRequest.classIri.getOntologyFromEntity} does not exist")
        )

      _ = if (!ontology.classes.contains(internalClassIri)) {
            throw BadRequestException(s"Class ${canDeleteClassRequest.classIri} does not exist")
          }

      userCanUpdateOntology <-
        OntologyHelpers.canUserUpdateOntology(internalOntologyIri, canDeleteClassRequest.requestingUser)
      classIsUsed <- iriService.isEntityUsed(entityIri = internalClassIri)
    } yield CanDoResponseV2(userCanUpdateOntology && !classIsUsed)
  }

  /**
   * Deletes a class.
   *
   * @param deleteClassRequest the request to delete the class.
   * @return a [[SuccessResponseV2]].
   */
  private def deleteClass(deleteClassRequest: DeleteClassRequestV2): Future[ReadOntologyMetadataV2] = {
    def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologyMetadataV2] =
      for {
        cacheData <- Cache.getCacheData

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = deleteClassRequest.lastModificationDate
             )

        // Check that the class exists.

        ontology = cacheData.ontologies(internalOntologyIri)

        _ = if (!ontology.classes.contains(internalClassIri)) {
              throw BadRequestException(s"Class ${deleteClassRequest.classIri} does not exist")
            }

        // Check that the class isn't used in data or ontologies.

        _ <- iriService.throwIfEntityIsUsed(
               entityIri = internalClassIri,
               errorFun = throw BadRequestException(
                 s"Class ${deleteClassRequest.classIri} cannot be deleted, because it is used in data or ontologies"
               )
             )

        // Delete the class from the triplestore.

        currentTime: Instant = Instant.now

        updateSparql = v2.txt
                         .deleteClass(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           classIri = internalClassIri,
                           lastModificationDate = deleteClassRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

        // Check that the ontology's last modification date was updated.

        _ <- OntologyHelpers.checkOntologyLastModificationDateAfterUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = currentTime
             )

        // Update the cache.

        updatedOntology = ontology.copy(
                            ontologyMetadata = ontology.ontologyMetadata.copy(
                              lastModificationDate = Some(currentTime)
                            ),
                            classes = ontology.classes - internalClassIri
                          )

        _ <- Cache.cacheUpdatedOntology(internalOntologyIri, updatedOntology)

      } yield ReadOntologyMetadataV2(Set(updatedOntology.ontologyMetadata))

    for {
      requestingUser <- FastFuture.successful(deleteClassRequest.requestingUser)

      externalClassIri    = deleteClassRequest.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <- OntologyHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri = externalOntologyIri,
             externalEntityIri = externalClassIri,
             requestingUser = requestingUser
           )

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID = deleteClassRequest.apiRequestID,
                      iri = ONTOLOGY_CACHE_LOCK_IRI,
                      task = () =>
                        makeTaskFuture(
                          internalClassIri = internalClassIri,
                          internalOntologyIri = internalOntologyIri
                        )
                    )
    } yield taskResult
  }

  /**
   * Checks whether a property can be deleted.
   *
   * @param canDeletePropertyRequest the request message.
   * @return a [[CanDoResponseV2]] indicating whether the property can be deleted.
   */
  private def canDeleteProperty(canDeletePropertyRequest: CanDeletePropertyRequestV2): Future[CanDoResponseV2] = {
    val internalPropertyIri = canDeletePropertyRequest.propertyIri.toOntologySchema(InternalSchema)
    val internalOntologyIri = internalPropertyIri.getOntologyFromEntity

    for {
      cacheData <- Cache.getCacheData

      ontology = cacheData.ontologies.getOrElse(
                   internalOntologyIri,
                   throw BadRequestException(
                     s"Ontology ${canDeletePropertyRequest.propertyIri.getOntologyFromEntity} does not exist"
                   )
                 )

      propertyDef: ReadPropertyInfoV2 =
        ontology.properties.getOrElse(
          internalPropertyIri,
          throw BadRequestException(s"Property ${canDeletePropertyRequest.propertyIri} does not exist")
        )

      _ = if (propertyDef.isLinkValueProp) {
            throw BadRequestException(
              s"A link value property cannot be deleted directly; check the corresponding link property instead"
            )
          }

      userCanUpdateOntology <-
        OntologyHelpers.canUserUpdateOntology(internalOntologyIri, canDeletePropertyRequest.requestingUser)
      propertyIsUsed <- iriService.isEntityUsed(internalPropertyIri)
    } yield CanDoResponseV2(userCanUpdateOntology && !propertyIsUsed)
  }

  /**
   * Deletes a property. If the property is a link property, the corresponding link value property is also deleted.
   *
   * @param deletePropertyRequest the request to delete the property.
   * @return a [[ReadOntologyMetadataV2]].
   */
  private def deleteProperty(deletePropertyRequest: DeletePropertyRequestV2): Future[ReadOntologyMetadataV2] = {
    def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologyMetadataV2] =
      for {
        cacheData <- Cache.getCacheData

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = deletePropertyRequest.lastModificationDate
             )

        // Check that the property exists.

        ontology = cacheData.ontologies(internalOntologyIri)
        propertyDef: ReadPropertyInfoV2 =
          ontology.properties.getOrElse(
            internalPropertyIri,
            throw BadRequestException(s"Property ${deletePropertyRequest.propertyIri} does not exist")
          )

        _ = if (propertyDef.isLinkValueProp) {
              throw BadRequestException(
                s"A link value property cannot be deleted directly; delete the corresponding link property instead"
              )
            }

        maybeInternalLinkValuePropertyIri: Option[SmartIri] =
          if (propertyDef.isLinkProp) {
            Some(internalPropertyIri.fromLinkPropToLinkValueProp)
          } else {
            None
          }

        // Check that the property isn't used in data or ontologies.

        _ <- iriService.throwIfEntityIsUsed(
               entityIri = internalPropertyIri,
               errorFun = throw BadRequestException(
                 s"Property ${deletePropertyRequest.propertyIri} cannot be deleted, because it is used in data or ontologies"
               )
             )

        _ <- maybeInternalLinkValuePropertyIri match {
               case Some(internalLinkValuePropertyIri) =>
                 iriService.throwIfEntityIsUsed(
                   entityIri = internalLinkValuePropertyIri,
                   errorFun = throw BadRequestException(
                     s"Property ${deletePropertyRequest.propertyIri} cannot be deleted, because the corresponding link value property, ${internalLinkValuePropertyIri
                         .toOntologySchema(ApiV2Complex)}, is used in data or ontologies"
                   )
                 )

               case None => FastFuture.successful(())
             }

        // Delete the property from the triplestore.

        currentTime: Instant = Instant.now

        updateSparql = v2.txt
                         .deleteProperty(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           propertyIri = internalPropertyIri,
                           maybeLinkValuePropertyIri = maybeInternalLinkValuePropertyIri,
                           lastModificationDate = deletePropertyRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

        // Check that the ontology's last modification date was updated.

        _ <- OntologyHelpers.checkOntologyLastModificationDateAfterUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = currentTime
             )

        // Update the cache.

        propertiesToRemoveFromCache = Set(internalPropertyIri) ++ maybeInternalLinkValuePropertyIri

        updatedOntology =
          ontology.copy(
            ontologyMetadata = ontology.ontologyMetadata.copy(
              lastModificationDate = Some(currentTime)
            ),
            properties = ontology.properties -- propertiesToRemoveFromCache
          )

        _ <- Cache.cacheUpdatedOntology(internalOntologyIri, updatedOntology)

      } yield ReadOntologyMetadataV2(Set(updatedOntology.ontologyMetadata))

    for {
      requestingUser <- FastFuture.successful(deletePropertyRequest.requestingUser)

      externalPropertyIri = deletePropertyRequest.propertyIri
      externalOntologyIri = externalPropertyIri.getOntologyFromEntity

      _ <- OntologyHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri = externalOntologyIri,
             externalEntityIri = externalPropertyIri,
             requestingUser = requestingUser
           )

      internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID = deletePropertyRequest.apiRequestID,
                      iri = ONTOLOGY_CACHE_LOCK_IRI,
                      task = () =>
                        makeTaskFuture(
                          internalPropertyIri = internalPropertyIri,
                          internalOntologyIri = internalOntologyIri
                        )
                    )
    } yield taskResult
  }

  /**
   * Checks whether an ontology can be deleted.
   *
   * @param canDeleteOntologyRequest the request message.
   * @return a [[CanDoResponseV2]] indicating whether an ontology can be deleted.
   */
  private def canDeleteOntology(canDeleteOntologyRequest: CanDeleteOntologyRequestV2): Future[CanDoResponseV2] = {
    val internalOntologyIri: SmartIri = canDeleteOntologyRequest.ontologyIri.toOntologySchema(InternalSchema)

    for {
      cacheData <- Cache.getCacheData

      ontology = cacheData.ontologies.getOrElse(
                   internalOntologyIri,
                   throw BadRequestException(
                     s"Ontology ${canDeleteOntologyRequest.ontologyIri.getOntologyFromEntity} does not exist"
                   )
                 )

      userCanUpdateOntology <-
        OntologyHelpers.canUserUpdateOntology(internalOntologyIri, canDeleteOntologyRequest.requestingUser)
      subjectsUsingOntology <- OntologyHelpers.getSubjectsUsingOntology(appActor, ontology)
    } yield CanDoResponseV2(userCanUpdateOntology && subjectsUsingOntology.isEmpty)
  }

  private def deleteOntology(deleteOntologyRequest: DeleteOntologyRequestV2): Future[SuccessResponseV2] = {
    def makeTaskFuture(internalOntologyIri: SmartIri): Future[SuccessResponseV2] =
      for {
        cacheData <- Cache.getCacheData

        // Check that the user has permission to update the ontology.
        _ <- OntologyHelpers.checkPermissionsForOntologyUpdate(
               internalOntologyIri = internalOntologyIri,
               requestingUser = deleteOntologyRequest.requestingUser
             )

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = deleteOntologyRequest.lastModificationDate
             )

        // Check that none of the entities in the ontology are used in data or in other ontologies.

        ontology                         = cacheData.ontologies(internalOntologyIri)
        subjectsUsingOntology: Set[IRI] <- OntologyHelpers.getSubjectsUsingOntology(appActor, ontology)

        _ = if (subjectsUsingOntology.nonEmpty) {
              val sortedSubjects: Seq[IRI] = subjectsUsingOntology.map(s => "<" + s + ">").toVector.sorted

              throw BadRequestException(
                s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} cannot be deleted, because of subjects that refer to it: ${sortedSubjects
                    .mkString(", ")}"
              )
            }

        // Delete everything in the ontology's named graph.

        updateSparql = v2.txt
                         .deleteOntology(
                           ontologyNamedGraphIri = internalOntologyIri
                         )
                         .toString()

        _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

        // Check that the ontology has been deleted.

        maybeOntologyMetadata <- OntologyHelpers.loadOntologyMetadata(
                                   appActor,
                                   internalOntologyIri = internalOntologyIri
                                 )

        _ = if (maybeOntologyMetadata.nonEmpty) {
              throw UpdateNotPerformedException(
                s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} was not deleted. Please report this as a possible bug."
              )
            }

        // Remove the ontology from the cache.
        _ <- Cache.deleteOntology(internalOntologyIri)
      } yield SuccessResponseV2(s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} has been deleted")

    for {
      _                  <- OntologyHelpers.checkExternalOntologyIriForUpdate(deleteOntologyRequest.ontologyIri)
      internalOntologyIri = deleteOntologyRequest.ontologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID = deleteOntologyRequest.apiRequestID,
                      iri = ONTOLOGY_CACHE_LOCK_IRI,
                      task = () =>
                        makeTaskFuture(
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
        cacheData          <- Cache.getCacheData
        internalPropertyDef = createPropertyRequest.propertyInfoContent.toOntologySchema(InternalSchema)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = createPropertyRequest.lastModificationDate
             )

        // Check that the property's rdf:type is owl:ObjectProperty.

        rdfType: SmartIri = internalPropertyDef.requireIriObject(
                              OntologyConstants.Rdf.Type.toSmartIri,
                              throw BadRequestException(s"No rdf:type specified")
                            )

        _ = if (rdfType != OntologyConstants.Owl.ObjectProperty.toSmartIri) {
              throw BadRequestException(s"Invalid rdf:type for property: $rdfType")
            }

        // Check that the property doesn't exist yet.

        ontology = cacheData.ontologies(internalOntologyIri)

        _ = if (ontology.properties.contains(internalPropertyIri)) {
              throw BadRequestException(
                s"Property ${createPropertyRequest.propertyInfoContent.propertyIri} already exists"
              )
            }

        // Check that the property's IRI isn't already used for something else.
        _ = if (ontology.classes.contains(internalPropertyIri) || ontology.individuals.contains(internalPropertyIri)) {
              throw BadRequestException(s"IRI ${createPropertyRequest.propertyInfoContent.propertyIri} is already used")
            }

        // Check that the base properties that have Knora IRIs are defined as Knora resource properties.

        knoraSuperProperties = internalPropertyDef.subPropertyOf.filter(_.isKnoraInternalEntityIri)
        invalidSuperProperties = knoraSuperProperties.filterNot(baseProperty =>
                                   isKnoraResourceProperty(
                                     baseProperty,
                                     cacheData
                                   ) && baseProperty.toString != OntologyConstants.KnoraBase.ResourceProperty
                                 )

        _ = if (invalidSuperProperties.nonEmpty) {
              throw BadRequestException(
                s"One or more specified base properties are invalid: ${invalidSuperProperties.mkString(", ")}"
              )
            }

        // Check for rdfs:subPropertyOf cycles.

        allKnoraSuperPropertyIrisWithoutSelf: Set[SmartIri] = knoraSuperProperties.flatMap { superPropertyIri =>
                                                                cacheData.subPropertyOfRelations.getOrElse(
                                                                  superPropertyIri,
                                                                  Set.empty[SmartIri]
                                                                )
                                                              }

        _ = if (allKnoraSuperPropertyIrisWithoutSelf.contains(internalPropertyIri)) {
              throw BadRequestException(
                s"Property ${createPropertyRequest.propertyInfoContent.propertyIri} would have a cyclical rdfs:subPropertyOf"
              )
            }

        // Check the property is a subproperty of knora-base:hasValue or knora-base:hasLinkTo, but not both.

        allKnoraSuperPropertyIris: Set[SmartIri] = allKnoraSuperPropertyIrisWithoutSelf + internalPropertyIri

        isValueProp     = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasValue.toSmartIri)
        isLinkProp      = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasLinkTo.toSmartIri)
        isLinkValueProp = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasLinkToValue.toSmartIri)
        isFileValueProp = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasFileValue.toSmartIri)

        _ = if (!(isValueProp || isLinkProp)) {
              throw BadRequestException(
                s"Property ${createPropertyRequest.propertyInfoContent.propertyIri} would not be a subproperty of knora-api:hasValue or knora-api:hasLinkTo"
              )
            }

        _ = if (isValueProp && isLinkProp) {
              throw BadRequestException(
                s"Property ${createPropertyRequest.propertyInfoContent.propertyIri} would be a subproperty of both knora-api:hasValue and knora-api:hasLinkTo"
              )
            }

        // Don't allow new file value properties to be created.

        _ = if (isFileValueProp) {
              throw BadRequestException("New file value properties cannot be created")
            }

        // Don't allow new link value properties to be created directly, because we do that automatically when creating a link property.

        _ = if (isLinkValueProp) {
              throw BadRequestException(
                "New link value properties cannot be created directly. Create a link property instead."
              )
            }

        // If we're creating a link property, make the definition of the corresponding link value property.
        maybeLinkValuePropertyDef: Option[PropertyInfoContentV2] =
          if (isLinkProp) {
            val linkValuePropertyDef = OntologyHelpers
              .linkPropertyDefToLinkValuePropertyDef(
                internalPropertyDef
              )

            if (
              ontology.properties.contains(
                linkValuePropertyDef.propertyIri
              )
            ) {
              throw BadRequestException(
                s"Link value property ${linkValuePropertyDef.propertyIri} already exists"
              )
            }

            Some(linkValuePropertyDef)
          } else {
            None
          }

        // Check that the subject class constraint, if provided, designates a Knora resource class that exists.

        maybeSubjectClassConstraintPred: Option[PredicateInfoV2] =
          internalPropertyDef.predicates.get(OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri)
        maybeSubjectClassConstraint = maybeSubjectClassConstraintPred.map(
                                        _.requireIriObject(throw BadRequestException("Invalid knora-api:subjectType"))
                                      )

        _ = maybeSubjectClassConstraint.foreach { subjectClassConstraint =>
              if (!OntologyHelpers.isKnoraInternalResourceClass(subjectClassConstraint, cacheData)) {
                throw BadRequestException(
                  s"Invalid subject class constraint: ${subjectClassConstraint.toOntologySchema(ApiV2Complex)}"
                )
              }
            }

        // Check that the object class constraint designates an appropriate class that exists.

        objectClassConstraint: SmartIri = internalPropertyDef.requireIriObject(
                                            OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
                                            throw BadRequestException(s"No knora-api:objectType specified")
                                          )

        // If this is a value property, ensure its object class constraint is not LinkValue or a file value class.
        _ = if (!isLinkProp) {
              if (
                objectClassConstraint.toString == OntologyConstants.KnoraBase.LinkValue ||
                OntologyConstants.KnoraBase.FileValueClasses.contains(objectClassConstraint.toString)
              ) {
                throw BadRequestException(
                  s"Invalid object class constraint for value property: ${objectClassConstraint.toOntologySchema(ApiV2Complex)}"
                )
              }
            }

        // Check that the subject class, if provided, is a subclass of the subject classes of the base properties.

        _ = maybeSubjectClassConstraint match {
              case Some(subjectClassConstraint) =>
                Cache.checkPropertyConstraint(
                  cacheData = cacheData,
                  internalPropertyIri = internalPropertyIri,
                  constraintPredicateIri = OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri,
                  constraintValueToBeChecked = subjectClassConstraint,
                  allSuperPropertyIris = allKnoraSuperPropertyIris,
                  errorSchema = ApiV2Complex,
                  errorFun = { msg: String =>
                    throw BadRequestException(msg)
                  }
                )

              case None => ()
            }

        // Check that the object class is a subclass of the object classes of the base properties.

        _ = Cache.checkPropertyConstraint(
              cacheData = cacheData,
              internalPropertyIri = internalPropertyIri,
              constraintPredicateIri = OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
              constraintValueToBeChecked = objectClassConstraint,
              allSuperPropertyIris = allKnoraSuperPropertyIris,
              errorSchema = ApiV2Complex,
              errorFun = { msg: String =>
                throw BadRequestException(msg)
              }
            )

        // Check that the property definition doesn't refer to any non-shared ontologies in other projects.
        _ = Cache.checkOntologyReferencesInPropertyDef(
              ontologyCacheData = cacheData,
              propertyDef = internalPropertyDef,
              errorFun = { msg: String =>
                throw BadRequestException(msg)
              }
            )

        // Add the property (and the link value property if needed) to the triplestore.

        currentTime: Instant = Instant.now

        updateSparql = v2.txt
                         .createProperty(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           propertyDef = internalPropertyDef,
                           maybeLinkValuePropertyDef = maybeLinkValuePropertyDef,
                           lastModificationDate = createPropertyRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

        // Check that the ontology's last modification date was updated.

        _ <- OntologyHelpers.checkOntologyLastModificationDateAfterUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = currentTime
             )

        // Check that the data that was saved corresponds to the data that was submitted. To make this comparison,
        // we have to undo the SPARQL-escaping of the input.

        loadedPropertyDef <- OntologyHelpers.loadPropertyDefinition(
                               appActor,
                               propertyIri = internalPropertyIri
                             )

        unescapedInputPropertyDef = internalPropertyDef.unescape

        _ = if (loadedPropertyDef != unescapedInputPropertyDef) {
              throw InconsistentRepositoryDataException(
                s"Attempted to save property definition $unescapedInputPropertyDef, but $loadedPropertyDef was saved"
              )
            }

        maybeLoadedLinkValuePropertyDefFuture: Option[Future[PropertyInfoContentV2]] =
          maybeLinkValuePropertyDef.map { linkValuePropertyDef =>
            OntologyHelpers.loadPropertyDefinition(
              appActor,
              propertyIri = linkValuePropertyDef.propertyIri
            )
          }

        maybeLoadedLinkValuePropertyDef: Option[PropertyInfoContentV2] <-
          ActorUtil.optionFuture2FutureOption(maybeLoadedLinkValuePropertyDefFuture)
        maybeUnescapedNewLinkValuePropertyDef = maybeLinkValuePropertyDef.map(_.unescape)

        _ = (maybeLoadedLinkValuePropertyDef, maybeUnescapedNewLinkValuePropertyDef) match {
              case (Some(loadedLinkValuePropertyDef), Some(unescapedNewLinkPropertyDef)) =>
                if (loadedLinkValuePropertyDef != unescapedNewLinkPropertyDef) {
                  throw InconsistentRepositoryDataException(
                    s"Attempted to save link value property definition $unescapedNewLinkPropertyDef, but $loadedLinkValuePropertyDef was saved"
                  )
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

        maybeLinkValuePropertyCacheEntry: Option[(SmartIri, ReadPropertyInfoV2)] =
          maybeUnescapedNewLinkValuePropertyDef.map { unescapedNewLinkPropertyDef =>
            unescapedNewLinkPropertyDef.propertyIri -> ReadPropertyInfoV2(
              entityInfoContent = unescapedNewLinkPropertyDef,
              isEditable = true,
              isResourceProp = true,
              isLinkValueProp = true
            )
          }

        updatedOntologyMetadata = ontology.ontologyMetadata.copy(
                                    lastModificationDate = Some(currentTime)
                                  )

        updatedOntology =
          ontology.copy(
            ontologyMetadata = updatedOntologyMetadata,
            properties =
              ontology.properties ++ maybeLinkValuePropertyCacheEntry + (internalPropertyIri -> readPropertyInfo)
          )

        _ <- Cache.cacheUpdatedOntology(internalOntologyIri, updatedOntology)

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

      _ <- OntologyHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri = externalOntologyIri,
             externalEntityIri = externalPropertyIri,
             requestingUser = requestingUser
           )

      internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID = createPropertyRequest.apiRequestID,
                      iri = ONTOLOGY_CACHE_LOCK_IRI,
                      task = () =>
                        makeTaskFuture(
                          internalPropertyIri = internalPropertyIri,
                          internalOntologyIri = internalOntologyIri
                        )
                    )
    } yield taskResult
  }

  /**
   * Changes the values of `salsah-gui:guiElement` and `salsah-gui:guiAttribute` in a property definition.
   *
   * @param changePropertyGuiElementRequest the request to change the property's GUI element and GUI attribute.
   * @return a [[ReadOntologyV2]] containing the modified property definition.
   */
  private def changePropertyGuiElement(
    changePropertyGuiElementRequest: ChangePropertyGuiElementRequest
  ): Future[ReadOntologyV2] = {
    def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologyV2] = {
      for {
        cacheData <- Cache.getCacheData

        ontology = cacheData.ontologies(internalOntologyIri)

        currentReadPropertyInfo: ReadPropertyInfoV2 =
          ontology.properties.getOrElse(
            internalPropertyIri,
            throw NotFoundException(s"Property ${changePropertyGuiElementRequest.propertyIri} not found")
          )

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = changePropertyGuiElementRequest.lastModificationDate
             )

        // If this is a link property, also change the GUI element and attribute of the corresponding link value property.

        maybeCurrentLinkValueReadPropertyInfo: Option[ReadPropertyInfoV2] =
          if (currentReadPropertyInfo.isLinkProp) {
            val linkValuePropertyIri =
              internalPropertyIri.fromLinkPropToLinkValueProp
            Some(
              ontology.properties.getOrElse(
                linkValuePropertyIri,
                throw InconsistentRepositoryDataException(
                  s"Link value property $linkValuePropertyIri not found"
                )
              )
            )
          } else {
            None
          }

        // Do the update.

        currentTime: Instant = Instant.now

        newGuiElementIri =
          changePropertyGuiElementRequest.newGuiObject.guiElement.map(guiElement => guiElement.value.toSmartIri)

        newGuiAttributeIris =
          changePropertyGuiElementRequest.newGuiObject.guiAttributes.map(guiAttribute => guiAttribute.value)

        updateSparql = v2.txt
                         .changePropertyGuiElement(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           propertyIri = internalPropertyIri,
                           maybeLinkValuePropertyIri =
                             maybeCurrentLinkValueReadPropertyInfo.map(_.entityInfoContent.propertyIri),
                           maybeNewGuiElement = newGuiElementIri,
                           newGuiAttributes = newGuiAttributeIris,
                           lastModificationDate = changePropertyGuiElementRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

        // Check that the ontology's last modification date was updated.

        _ <- OntologyHelpers.checkOntologyLastModificationDateAfterUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = currentTime
             )

        // Check that the data that was saved corresponds to the data that was submitted. To make this comparison,
        // we have to undo the SPARQL-escaping of the input.

        loadedPropertyDef <- OntologyHelpers.loadPropertyDefinition(
                               appActor,
                               propertyIri = internalPropertyIri
                             )

        maybeNewGuiElementPredicate: Option[(SmartIri, PredicateInfoV2)] =
          newGuiElementIri.map { guiElement: SmartIri =>
            SalsahGui.GuiElementProp.toSmartIri -> PredicateInfoV2(
              predicateIri = SalsahGui.GuiElementProp.toSmartIri,
              objects = Seq(SmartIriLiteralV2(guiElement))
            )
          }

        maybeUnescapedNewGuiAttributePredicate: Option[(SmartIri, PredicateInfoV2)] =
          if (newGuiAttributeIris.nonEmpty) {
            Some(
              SalsahGui.GuiAttribute.toSmartIri -> PredicateInfoV2(
                predicateIri = SalsahGui.GuiAttribute.toSmartIri,
                objects = newGuiAttributeIris.map(StringLiteralV2(_)).toSeq
              )
            )
          } else {
            None
          }

        unescapedNewPropertyDef: PropertyInfoContentV2 = currentReadPropertyInfo.entityInfoContent.copy(
                                                           predicates =
                                                             currentReadPropertyInfo.entityInfoContent.predicates -
                                                               SalsahGui.GuiElementProp.toSmartIri -
                                                               SalsahGui.GuiAttribute.toSmartIri ++
                                                               maybeNewGuiElementPredicate ++
                                                               maybeUnescapedNewGuiAttributePredicate
                                                         )

        _ = if (loadedPropertyDef != unescapedNewPropertyDef) {
              throw InconsistentRepositoryDataException(
                s"Attempted to save property definition $unescapedNewPropertyDef, but $loadedPropertyDef was saved"
              )
            }

        maybeLoadedLinkValuePropertyDefFuture: Option[Future[PropertyInfoContentV2]] =
          maybeCurrentLinkValueReadPropertyInfo.map { linkValueReadPropertyInfo =>
            OntologyHelpers.loadPropertyDefinition(
              appActor,
              propertyIri = linkValueReadPropertyInfo.entityInfoContent.propertyIri
            )
          }

        maybeLoadedLinkValuePropertyDef: Option[PropertyInfoContentV2] <-
          ActorUtil.optionFuture2FutureOption(maybeLoadedLinkValuePropertyDefFuture)

        maybeUnescapedNewLinkValuePropertyDef: Option[PropertyInfoContentV2] =
          maybeLoadedLinkValuePropertyDef.map { loadedLinkValuePropertyDef =>
            val unescapedNewLinkPropertyDef = maybeCurrentLinkValueReadPropertyInfo.get.entityInfoContent.copy(
              predicates = maybeCurrentLinkValueReadPropertyInfo.get.entityInfoContent.predicates -
                SalsahGui.GuiElementProp.toSmartIri -
                SalsahGui.GuiAttribute.toSmartIri ++
                maybeNewGuiElementPredicate ++
                maybeUnescapedNewGuiAttributePredicate
            )

            if (loadedLinkValuePropertyDef != unescapedNewLinkPropertyDef) {
              throw InconsistentRepositoryDataException(
                s"Attempted to save link value property definition $unescapedNewLinkPropertyDef, but $loadedLinkValuePropertyDef was saved"
              )
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

        maybeLinkValuePropertyCacheEntry: Option[(SmartIri, ReadPropertyInfoV2)] =
          maybeUnescapedNewLinkValuePropertyDef.map { unescapedNewLinkPropertyDef =>
            unescapedNewLinkPropertyDef.propertyIri -> ReadPropertyInfoV2(
              entityInfoContent = unescapedNewLinkPropertyDef,
              isEditable = true,
              isResourceProp = true,
              isLinkValueProp = true
            )
          }

        updatedOntologyMetadata = ontology.ontologyMetadata.copy(
                                    lastModificationDate = Some(currentTime)
                                  )

        updatedOntology =
          ontology.copy(
            ontologyMetadata = updatedOntologyMetadata,
            properties =
              ontology.properties ++ maybeLinkValuePropertyCacheEntry + (internalPropertyIri -> newReadPropertyInfo)
          )

        _ <- Cache.cacheUpdatedOntology(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.

        response <- getPropertyDefinitionsFromOntologyV2(
                      propertyIris = Set(internalPropertyIri),
                      allLanguages = true,
                      requestingUser = changePropertyGuiElementRequest.requestingUser
                    )
      } yield response
    }

    for {
      requestingUser <- FastFuture.successful(changePropertyGuiElementRequest.requestingUser)

      externalPropertyIri = changePropertyGuiElementRequest.propertyIri.value.toSmartIri
      externalOntologyIri = externalPropertyIri.getOntologyFromEntity

      _ <- OntologyHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri = externalOntologyIri,
             externalEntityIri = externalPropertyIri,
             requestingUser = requestingUser
           )

      internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID = changePropertyGuiElementRequest.apiRequestID,
                      iri = ONTOLOGY_CACHE_LOCK_IRI,
                      task = () =>
                        makeTaskFuture(
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
  private def changePropertyLabelsOrComments(
    changePropertyLabelsOrCommentsRequest: ChangePropertyLabelsOrCommentsRequestV2
  ): Future[ReadOntologyV2] = {
    def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologyV2] = {
      for {
        cacheData <- Cache.getCacheData

        ontology = cacheData.ontologies(internalOntologyIri)

        currentReadPropertyInfo: ReadPropertyInfoV2 =
          ontology.properties.getOrElse(
            internalPropertyIri,
            throw NotFoundException(s"Property ${changePropertyLabelsOrCommentsRequest.propertyIri} not found")
          )

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = changePropertyLabelsOrCommentsRequest.lastModificationDate
             )

        // If this is a link property, also change the labels/comments of the corresponding link value property.

        maybeCurrentLinkValueReadPropertyInfo: Option[ReadPropertyInfoV2] =
          if (currentReadPropertyInfo.isLinkProp) {
            val linkValuePropertyIri =
              internalPropertyIri.fromLinkPropToLinkValueProp
            Some(
              ontology.properties.getOrElse(
                linkValuePropertyIri,
                throw InconsistentRepositoryDataException(
                  s"Link value property $linkValuePropertyIri not found"
                )
              )
            )
          } else {
            None
          }

        // Do the update.

        currentTime: Instant = Instant.now

        updateSparql = v2.txt
                         .changePropertyLabelsOrComments(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           propertyIri = internalPropertyIri,
                           maybeLinkValuePropertyIri =
                             maybeCurrentLinkValueReadPropertyInfo.map(_.entityInfoContent.propertyIri),
                           predicateToUpdate = changePropertyLabelsOrCommentsRequest.predicateToUpdate,
                           newObjects = changePropertyLabelsOrCommentsRequest.newObjects,
                           lastModificationDate = changePropertyLabelsOrCommentsRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

        // Check that the ontology's last modification date was updated.

        _ <- OntologyHelpers.checkOntologyLastModificationDateAfterUpdate(
               appActor = appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = currentTime
             )

        // Check that the data that was saved corresponds to the data that was submitted. To make this comparison,
        // we have to undo the SPARQL-escaping of the input.

        loadedPropertyDef <- OntologyHelpers.loadPropertyDefinition(
                               appActor,
                               propertyIri = internalPropertyIri
                             )

        unescapedNewLabelOrCommentPredicate: PredicateInfoV2 =
          PredicateInfoV2(
            predicateIri = changePropertyLabelsOrCommentsRequest.predicateToUpdate,
            objects = changePropertyLabelsOrCommentsRequest.newObjects
          ).unescape

        unescapedNewPropertyDef: PropertyInfoContentV2 =
          currentReadPropertyInfo.entityInfoContent.copy(
            predicates =
              currentReadPropertyInfo.entityInfoContent.predicates + (changePropertyLabelsOrCommentsRequest.predicateToUpdate -> unescapedNewLabelOrCommentPredicate)
          )

        _ = if (loadedPropertyDef != unescapedNewPropertyDef) {
              throw InconsistentRepositoryDataException(
                s"Attempted to save property definition $unescapedNewPropertyDef, but $loadedPropertyDef was saved"
              )
            }

        maybeLoadedLinkValuePropertyDefFuture: Option[Future[PropertyInfoContentV2]] =
          maybeCurrentLinkValueReadPropertyInfo.map { linkValueReadPropertyInfo =>
            OntologyHelpers.loadPropertyDefinition(
              appActor,
              propertyIri = linkValueReadPropertyInfo.entityInfoContent.propertyIri
            )
          }

        maybeLoadedLinkValuePropertyDef: Option[PropertyInfoContentV2] <-
          ActorUtil.optionFuture2FutureOption(maybeLoadedLinkValuePropertyDefFuture)

        maybeUnescapedNewLinkValuePropertyDef: Option[PropertyInfoContentV2] =
          maybeLoadedLinkValuePropertyDef.map { loadedLinkValuePropertyDef =>
            val unescapedNewLinkPropertyDef = maybeCurrentLinkValueReadPropertyInfo.get.entityInfoContent.copy(
              predicates =
                maybeCurrentLinkValueReadPropertyInfo.get.entityInfoContent.predicates + (changePropertyLabelsOrCommentsRequest.predicateToUpdate -> unescapedNewLabelOrCommentPredicate)
            )

            if (loadedLinkValuePropertyDef != unescapedNewLinkPropertyDef) {
              throw InconsistentRepositoryDataException(
                s"Attempted to save link value property definition $unescapedNewLinkPropertyDef, but $loadedLinkValuePropertyDef was saved"
              )
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

        maybeLinkValuePropertyCacheEntry: Option[(SmartIri, ReadPropertyInfoV2)] =
          maybeUnescapedNewLinkValuePropertyDef.map { unescapedNewLinkPropertyDef =>
            unescapedNewLinkPropertyDef.propertyIri -> ReadPropertyInfoV2(
              entityInfoContent = unescapedNewLinkPropertyDef,
              isEditable = true,
              isResourceProp = true,
              isLinkValueProp = true
            )
          }

        updatedOntologyMetadata = ontology.ontologyMetadata.copy(
                                    lastModificationDate = Some(currentTime)
                                  )

        updatedOntology =
          ontology.copy(
            ontologyMetadata = updatedOntologyMetadata,
            properties =
              ontology.properties ++ maybeLinkValuePropertyCacheEntry + (internalPropertyIri -> newReadPropertyInfo)
          )

        _ <- Cache.cacheUpdatedOntologyWithoutUpdatingMaps(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.

        response <- getPropertyDefinitionsFromOntologyV2(
                      propertyIris = Set(internalPropertyIri),
                      allLanguages = true,
                      requestingUser = changePropertyLabelsOrCommentsRequest.requestingUser
                    )
      } yield response
    }

    for {
      requestingUser <- FastFuture.successful(changePropertyLabelsOrCommentsRequest.requestingUser)

      externalPropertyIri = changePropertyLabelsOrCommentsRequest.propertyIri
      externalOntologyIri = externalPropertyIri.getOntologyFromEntity

      _ <- OntologyHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri = externalOntologyIri,
             externalEntityIri = externalPropertyIri,
             requestingUser = requestingUser
           )

      internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID = changePropertyLabelsOrCommentsRequest.apiRequestID,
                      iri = ONTOLOGY_CACHE_LOCK_IRI,
                      task = () =>
                        makeTaskFuture(
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
  private def changeClassLabelsOrComments(
    changeClassLabelsOrCommentsRequest: ChangeClassLabelsOrCommentsRequestV2
  ): Future[ReadOntologyV2] = {
    def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Future[ReadOntologyV2] =
      for {
        cacheData <- Cache.getCacheData

        ontology = cacheData.ontologies(internalOntologyIri)
        currentReadClassInfo: ReadClassInfoV2 =
          ontology.classes.getOrElse(
            internalClassIri,
            throw NotFoundException(s"Class ${changeClassLabelsOrCommentsRequest.classIri} not found")
          )

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = changeClassLabelsOrCommentsRequest.lastModificationDate
             )

        // Do the update.

        currentTime: Instant = Instant.now

        updateSparql = v2.txt
                         .changeClassLabelsOrComments(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           classIri = internalClassIri,
                           predicateToUpdate = changeClassLabelsOrCommentsRequest.predicateToUpdate,
                           newObjects = changeClassLabelsOrCommentsRequest.newObjects,
                           lastModificationDate = changeClassLabelsOrCommentsRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

        // Check that the ontology's last modification date was updated.

        _ <- OntologyHelpers.checkOntologyLastModificationDateAfterUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = currentTime
             )

        // Check that the data that was saved corresponds to the data that was submitted. To make this comparison,
        // we have to undo the SPARQL-escaping of the input.

        loadedClassDef: ClassInfoContentV2 <- OntologyHelpers.loadClassDefinition(
                                                appActor,
                                                classIri = internalClassIri
                                              )

        unescapedNewLabelOrCommentPredicate = PredicateInfoV2(
                                                predicateIri = changeClassLabelsOrCommentsRequest.predicateToUpdate,
                                                objects = changeClassLabelsOrCommentsRequest.newObjects
                                              ).unescape

        unescapedNewClassDef: ClassInfoContentV2 =
          currentReadClassInfo.entityInfoContent.copy(
            predicates =
              currentReadClassInfo.entityInfoContent.predicates + (changeClassLabelsOrCommentsRequest.predicateToUpdate -> unescapedNewLabelOrCommentPredicate)
          )

        _ = if (loadedClassDef != unescapedNewClassDef) {
              throw InconsistentRepositoryDataException(
                s"Attempted to save class definition $unescapedNewClassDef, but $loadedClassDef was saved"
              )
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

        _ <- Cache.cacheUpdatedOntologyWithoutUpdatingMaps(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.

        response <- getClassDefinitionsFromOntologyV2(
                      classIris = Set(internalClassIri),
                      allLanguages = true,
                      requestingUser = changeClassLabelsOrCommentsRequest.requestingUser
                    )
      } yield response

    for {
      requestingUser <- FastFuture.successful(changeClassLabelsOrCommentsRequest.requestingUser)

      externalClassIri    = changeClassLabelsOrCommentsRequest.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <- OntologyHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri = externalOntologyIri,
             externalEntityIri = externalClassIri,
             requestingUser = requestingUser
           )

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      apiRequestID = changeClassLabelsOrCommentsRequest.apiRequestID,
                      iri = ONTOLOGY_CACHE_LOCK_IRI,
                      task = () =>
                        makeTaskFuture(
                          internalClassIri = internalClassIri,
                          internalOntologyIri = internalOntologyIri
                        )
                    )
    } yield taskResult
  }

  /**
   * Delete the `rdfs:comment` in a property definition.
   *
   * @param deletePropertyCommentRequest the request to delete the property's comment
   * @return a [[ReadOntologyV2]] containing the modified property definition.
   */
  private def deletePropertyComment(
    deletePropertyCommentRequest: DeletePropertyCommentRequestV2
  ): Future[ReadOntologyV2] = {
    def makeTaskFuture(
      cacheData: Cache.OntologyCacheData,
      internalPropertyIri: SmartIri,
      internalOntologyIri: SmartIri,
      ontology: ReadOntologyV2,
      propertyToUpdate: ReadPropertyInfoV2
    ): Future[ReadOntologyV2] =
      for {

        // Check that the ontology exists and has not been updated by another user since the client last read its metadata.
        _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = deletePropertyCommentRequest.lastModificationDate
             )

        // If this is a link property, also delete the comment of the corresponding link value property.
        maybeLinkValueOfPropertyToUpdate: Option[ReadPropertyInfoV2] =
          if (propertyToUpdate.isLinkProp) {
            val linkValuePropertyIri: SmartIri = internalPropertyIri.fromLinkPropToLinkValueProp
            Some(
              ontology.properties.getOrElse(
                linkValuePropertyIri,
                throw InconsistentRepositoryDataException(
                  s"Link value property $linkValuePropertyIri not found"
                )
              )
            )
          } else {
            None
          }

        maybeLinkValueOfPropertyToUpdateIri: Option[SmartIri] =
          if (propertyToUpdate.isLinkProp) {
            Some(internalPropertyIri.fromLinkPropToLinkValueProp)
          } else {
            None
          }

        currentTime: Instant = Instant.now

        // Delete the comment
        updateSparql: String = v2.txt
                                 .deletePropertyComment(
                                   ontologyNamedGraphIri = internalOntologyIri,
                                   ontologyIri = internalOntologyIri,
                                   propertyIri = internalPropertyIri,
                                   maybeLinkValuePropertyIri = maybeLinkValueOfPropertyToUpdateIri,
                                   lastModificationDate = deletePropertyCommentRequest.lastModificationDate,
                                   currentTime = currentTime
                                 )
                                 .toString()

        _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

        // Check that the ontology's last modification date was updated.
        _ <- OntologyHelpers.checkOntologyLastModificationDateAfterUpdate(
               appActor = appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = currentTime
             )

        // Check that the update was successful.
        loadedPropertyDef: PropertyInfoContentV2 <- OntologyHelpers.loadPropertyDefinition(
                                                      appActor,
                                                      propertyIri = internalPropertyIri
                                                    )

        propertyDefWithoutComment: PropertyInfoContentV2 =
          propertyToUpdate.entityInfoContent.copy(
            predicates = propertyToUpdate.entityInfoContent.predicates.-(
              OntologyConstants.Rdfs.Comment.toSmartIri
            ) // the "-" deletes the entry with the comment
          )

        _ = if (loadedPropertyDef != propertyDefWithoutComment) {
              throw InconsistentRepositoryDataException(
                s"Attempted to save property definition $propertyDefWithoutComment, but $loadedPropertyDef was saved"
              )
            }

        maybeLoadedLinkValuePropertyDefFuture: Option[Future[PropertyInfoContentV2]] =
          maybeLinkValueOfPropertyToUpdate.map { linkValueReadPropertyInfo: ReadPropertyInfoV2 =>
            OntologyHelpers.loadPropertyDefinition(
              appActor,
              propertyIri = linkValueReadPropertyInfo.entityInfoContent.propertyIri
            )
          }

        maybeLoadedLinkValuePropertyDef: Option[PropertyInfoContentV2] <-
          ActorUtil.optionFuture2FutureOption(maybeLoadedLinkValuePropertyDefFuture)

        maybeNewLinkValuePropertyDef: Option[PropertyInfoContentV2] =
          maybeLoadedLinkValuePropertyDef.map { loadedLinkValuePropertyDef: PropertyInfoContentV2 =>
            val newLinkPropertyDef: PropertyInfoContentV2 =
              maybeLinkValueOfPropertyToUpdate.get.entityInfoContent.copy(
                predicates = maybeLinkValueOfPropertyToUpdate.get.entityInfoContent.predicates
                  .-(OntologyConstants.Rdfs.Comment.toSmartIri)
              )

            if (loadedLinkValuePropertyDef != newLinkPropertyDef) {
              throw InconsistentRepositoryDataException(
                s"Attempted to save link value property definition $newLinkPropertyDef, but $loadedLinkValuePropertyDef was saved"
              )
            }

            newLinkPropertyDef
          }

        // Update the ontology cache using the new property definition.
        newReadPropertyInfo: ReadPropertyInfoV2 = ReadPropertyInfoV2(
                                                    entityInfoContent = loadedPropertyDef,
                                                    isEditable = true,
                                                    isResourceProp = true,
                                                    isLinkProp = propertyToUpdate.isLinkProp
                                                  )

        maybeLinkValuePropertyCacheEntry: Option[(SmartIri, ReadPropertyInfoV2)] =
          maybeNewLinkValuePropertyDef.map { newLinkPropertyDef: PropertyInfoContentV2 =>
            newLinkPropertyDef.propertyIri -> ReadPropertyInfoV2(
              entityInfoContent = newLinkPropertyDef,
              isEditable = true,
              isResourceProp = true,
              isLinkValueProp = true
            )
          }

        updatedOntologyMetadata: OntologyMetadataV2 = ontology.ontologyMetadata.copy(
                                                        lastModificationDate = Some(currentTime)
                                                      )

        updatedOntology: ReadOntologyV2 =
          ontology.copy(
            ontologyMetadata = updatedOntologyMetadata,
            properties =
              ontology.properties ++ maybeLinkValuePropertyCacheEntry + (internalPropertyIri -> newReadPropertyInfo)
          )

        _ <- Cache.cacheUpdatedOntologyWithoutUpdatingMaps(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.

        response: ReadOntologyV2 <- getPropertyDefinitionsFromOntologyV2(
                                      propertyIris = Set(internalPropertyIri),
                                      allLanguages = true,
                                      requestingUser = deletePropertyCommentRequest.requestingUser
                                    )

      } yield response

    for {
      requestingUser: UserADM <- FastFuture.successful(deletePropertyCommentRequest.requestingUser)

      externalPropertyIri: SmartIri = deletePropertyCommentRequest.propertyIri
      externalOntologyIri: SmartIri = externalPropertyIri.getOntologyFromEntity

      _ <- OntologyHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri = externalOntologyIri,
             externalEntityIri = externalPropertyIri,
             requestingUser = requestingUser
           )

      internalPropertyIri: SmartIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri: SmartIri = externalOntologyIri.toOntologySchema(InternalSchema)

      cacheData: Cache.OntologyCacheData <- Cache.getCacheData

      ontology: ReadOntologyV2 = cacheData.ontologies(internalOntologyIri)

      propertyToUpdate: ReadPropertyInfoV2 =
        ontology.properties.getOrElse(
          internalPropertyIri,
          throw NotFoundException(s"Property ${deletePropertyCommentRequest.propertyIri} not found")
        )

      hasComment: Boolean = propertyToUpdate.entityInfoContent.predicates.contains(
                              OntologyConstants.Rdfs.Comment.toSmartIri
                            )

      taskResult: ReadOntologyV2 <-
        if (hasComment) for {
          // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
          taskResult: ReadOntologyV2 <- IriLocker.runWithIriLock(
                                          apiRequestID = deletePropertyCommentRequest.apiRequestID,
                                          iri = ONTOLOGY_CACHE_LOCK_IRI,
                                          task = () =>
                                            makeTaskFuture(
                                              cacheData = cacheData,
                                              internalPropertyIri = internalPropertyIri,
                                              internalOntologyIri = internalOntologyIri,
                                              ontology = ontology,
                                              propertyToUpdate = propertyToUpdate
                                            )
                                        )
        } yield taskResult
        else {
          // not change anything if property has no comment
          getPropertyDefinitionsFromOntologyV2(
            propertyIris = Set(internalPropertyIri),
            allLanguages = true,
            requestingUser = deletePropertyCommentRequest.requestingUser
          )
        }
    } yield taskResult
  }

  /**
   * Delete the `rdfs:comment` in a class definition.
   *
   * @param deleteClassCommentRequest the request to delete the class' comment
   * @return a [[ReadOntologyV2]] containing the modified class definition.
   */
  private def deleteClassComment(
    deleteClassCommentRequest: DeleteClassCommentRequestV2
  ): Future[ReadOntologyV2] = {
    def makeTaskFuture(
      cacheData: Cache.OntologyCacheData,
      internalClassIri: SmartIri,
      internalOntologyIri: SmartIri,
      ontology: ReadOntologyV2,
      classToUpdate: ReadClassInfoV2
    ): Future[ReadOntologyV2] =
      for {

        // Check that the ontology exists and has not been updated by another user since the client last read its metadata.
        _ <- OntologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = deleteClassCommentRequest.lastModificationDate
             )

        currentTime: Instant = Instant.now

        // Delete the comment
        updateSparql: String = v2.txt
                                 .deleteClassComment(
                                   ontologyNamedGraphIri = internalOntologyIri,
                                   ontologyIri = internalOntologyIri,
                                   classIri = internalClassIri,
                                   lastModificationDate = deleteClassCommentRequest.lastModificationDate,
                                   currentTime = currentTime
                                 )
                                 .toString()

        _ <- appActor.ask(SparqlUpdateRequest(updateSparql)).mapTo[SparqlUpdateResponse]

        // Check that the ontology's last modification date was updated.
        _ <- OntologyHelpers.checkOntologyLastModificationDateAfterUpdate(
               appActor = appActor,
               internalOntologyIri = internalOntologyIri,
               expectedLastModificationDate = currentTime
             )

        // Check that the update was successful.
        loadedClassDef: ClassInfoContentV2 <- OntologyHelpers.loadClassDefinition(
                                                appActor,
                                                classIri = internalClassIri
                                              )

        classDefWithoutComment: ClassInfoContentV2 =
          classToUpdate.entityInfoContent.copy(
            predicates = classToUpdate.entityInfoContent.predicates.-(
              OntologyConstants.Rdfs.Comment.toSmartIri
            ) // the "-" deletes the entry with the comment
          )

        _ = if (loadedClassDef != classDefWithoutComment) {
              throw InconsistentRepositoryDataException(
                s"Attempted to save class definition $classDefWithoutComment, but $loadedClassDef was saved"
              )
            }

        // Update the ontology cache using the new class definition.
        newReadClassInfo: ReadClassInfoV2 = classToUpdate.copy(
                                              entityInfoContent = classDefWithoutComment
                                            )

        updatedOntologyMetadata: OntologyMetadataV2 = ontology.ontologyMetadata.copy(
                                                        lastModificationDate = Some(currentTime)
                                                      )

        updatedOntology: ReadOntologyV2 =
          ontology.copy(
            ontologyMetadata = updatedOntologyMetadata,
            classes = ontology.classes + (internalClassIri -> newReadClassInfo)
          )

        _ <- Cache.cacheUpdatedOntologyWithoutUpdatingMaps(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.

        response: ReadOntologyV2 <- getClassDefinitionsFromOntologyV2(
                                      classIris = Set(internalClassIri),
                                      allLanguages = true,
                                      requestingUser = deleteClassCommentRequest.requestingUser
                                    )

      } yield response

    for {
      requestingUser: UserADM <- FastFuture.successful(deleteClassCommentRequest.requestingUser)

      externalClassIri: SmartIri    = deleteClassCommentRequest.classIri
      externalOntologyIri: SmartIri = externalClassIri.getOntologyFromEntity

      _ <- OntologyHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri = externalOntologyIri,
             externalEntityIri = externalClassIri,
             requestingUser = requestingUser
           )

      internalClassIri: SmartIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri: SmartIri = externalOntologyIri.toOntologySchema(InternalSchema)

      cacheData: Cache.OntologyCacheData <- Cache.getCacheData

      ontology: ReadOntologyV2 = cacheData.ontologies(internalOntologyIri)

      classToUpdate: ReadClassInfoV2 =
        ontology.classes.getOrElse(
          internalClassIri,
          throw NotFoundException(s"Class ${deleteClassCommentRequest.classIri} not found")
        )

      hasComment: Boolean = classToUpdate.entityInfoContent.predicates.contains(
                              OntologyConstants.Rdfs.Comment.toSmartIri
                            )

      taskResult: ReadOntologyV2 <-
        if (hasComment) for {
          // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
          taskResult: ReadOntologyV2 <- IriLocker.runWithIriLock(
                                          apiRequestID = deleteClassCommentRequest.apiRequestID,
                                          iri = ONTOLOGY_CACHE_LOCK_IRI,
                                          task = () =>
                                            makeTaskFuture(
                                              cacheData = cacheData,
                                              internalClassIri = internalClassIri,
                                              internalOntologyIri = internalOntologyIri,
                                              ontology = ontology,
                                              classToUpdate = classToUpdate
                                            )
                                        )
        } yield taskResult
        else {
          // not change anything if class has no comment
          getClassDefinitionsFromOntologyV2(
            classIris = Set(internalClassIri),
            allLanguages = true,
            requestingUser = deleteClassCommentRequest.requestingUser
          )
        }
    } yield taskResult
  }

}
