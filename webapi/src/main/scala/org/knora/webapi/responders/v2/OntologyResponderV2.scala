/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import com.typesafe.scalalogging.LazyLogging
import zio.Task
import zio.URLayer
import zio.ZIO
import zio.ZLayer

import java.time.Instant

import dsp.constants.SalsahGui
import dsp.errors._
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.store.triplestoremessages.SmartIriLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.v2.responder.CanDoResponseV2
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.messages.v2.responder.ontologymessages._
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.repo.model.OntologyCacheData
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCache.ONTOLOGY_CACHE_LOCK_IRI
import org.knora.webapi.store.triplestore.api.TriplestoreService

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
trait OntologyResponderV2

final case class OntologyResponderV2Live(
  appConfig: AppConfig,
  cardinalityHandler: CardinalityHandler,
  cardinalityService: CardinalityService,
  iriService: IriService,
  messageRelay: MessageRelay,
  ontologyCache: OntologyCache,
  ontologyHelpers: OntologyHelpers,
  ontologyRepo: OntologyRepo,
  triplestoreService: TriplestoreService,
  implicit val stringFormatter: StringFormatter
) extends OntologyResponderV2
    with MessageHandler
    with LazyLogging {

  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[OntologiesResponderRequestV2]

  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case r: LoadOntologiesRequestV2 => ontologyCache.loadOntologies(r.requestingUser)
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
      ontologyHelpers.getClassDefinitionsFromOntologyV2(resourceClassIris, allLanguages, requestingUser)
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
    case r: ReplaceClassCardinalitiesRequestV2 => replaceClassCardinalities(r)
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
    case other                                                => Responder.handleUnexpectedMessage(other, this.getClass.getName)
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
  ): Task[EntityInfoGetResponseV2] =
    ontologyHelpers.getEntityInfoResponseV2(classIris, propertyIris, requestingUser)

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
  ): Task[StandoffEntityInfoGetResponseV2] =
    for {
      cacheData <- ontologyCache.getCacheData

      entitiesInWrongSchema =
        (standoffClassIris ++ standoffPropertyIris).filter(_.getOntologySchema.contains(ApiV2Simple))

      _ <- ZIO.fail {
             NotFoundException(
               s"Some requested standoff classes were not found: ${entitiesInWrongSchema.mkString(", ")}"
             )
           }.when(entitiesInWrongSchema.nonEmpty)

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

      _ <- ZIO.fail {
             NotFoundException(s"Some requested standoff classes were not found: ${missingClassDefs.mkString(", ")}")
           }.when(missingClassDefs.nonEmpty)

      _ <- ZIO.fail {
             NotFoundException(
               s"Some requested standoff properties were not found: ${missingPropertyDefs.mkString(", ")}"
             )
           }.when(missingPropertyDefs.nonEmpty)

      response =
        StandoffEntityInfoGetResponseV2(
          standoffClassInfoMap = new ErrorHandlingMap(classDefsAvailable, key => s"Resource class $key not found"),
          standoffPropertyInfoMap = new ErrorHandlingMap(propertyDefsAvailable, key => s"Property $key not found")
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
  ): Task[StandoffClassesWithDataTypeGetResponseV2] =
    for {
      cacheData <- ontologyCache.getCacheData
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
  ): Task[StandoffAllPropertyEntitiesGetResponseV2] =
    for {
      cacheData <- ontologyCache.getCacheData
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
  private def checkSubClassV2(subClassIri: SmartIri, superClassIri: SmartIri): Task[CheckSubClassResponseV2] =
    for {
      cacheData <- ontologyCache.getCacheData
      response = CheckSubClassResponseV2(
                   isSubClass = cacheData.classToSuperClassLookup.get(subClassIri) match {
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
  private def getSubClassesV2(classIri: SmartIri, requestingUser: UserADM): Task[SubClassesGetResponseV2] =
    for {
      cacheData <- ontologyCache.getCacheData

      subClassIris = cacheData.classToSubclassLookup(classIri).toVector.sorted

      subClasses = subClassIris.map { subClassIri =>
                     val classInfo: ReadClassInfoV2 =
                       cacheData.ontologies(subClassIri.getOntologyFromEntity).classes(subClassIri)

                     SubClassInfoV2(
                       id = subClassIri,
                       label = classInfo.entityInfoContent
                         .getPredicateStringLiteralObject(
                           predicateIri = OntologyConstants.Rdfs.Label.toSmartIri,
                           preferredLangs = Some(requestingUser.lang, appConfig.fallbackLanguage)
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
  ): Task[OntologyKnoraEntitiesIriInfoV2] =
    for {
      cacheData <- ontologyCache.getCacheData
      ontology   = cacheData.ontologies(ontologyIri)
    } yield OntologyKnoraEntitiesIriInfoV2(
      ontologyIri = ontologyIri,
      propertyIris = ontology.properties.keySet.filter { propertyIri =>
        OntologyHelpers.isKnoraResourceProperty(propertyIri, cacheData)
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
  ): Task[ReadOntologyMetadataV2] =
    for {
      cacheData                   <- ontologyCache.getCacheData
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
  ): Task[ReadOntologyMetadataV2] =
    for {
      cacheData                   <- ontologyCache.getCacheData
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
  ): Task[ReadOntologyV2] =
    for {
      cacheData <- ontologyCache.getCacheData

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
  ): Task[ReadOntologyV2] =
    for {
      cacheData <- ontologyCache.getCacheData

      ontologyIris = propertyIris.map(_.getOntologyFromEntity)

      _ = if (ontologyIris.size != 1) {
            throw BadRequestException(s"Only one ontology may be queried per request")
          }

      propertyInfoResponse <- getEntityInfoResponseV2(propertyIris = propertyIris, requestingUser = requestingUser)
      internalOntologyIri   = ontologyIris.head.toOntologySchema(InternalSchema)

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
  private def createOntology(createOntologyRequest: CreateOntologyRequestV2): Task[ReadOntologyMetadataV2] = {
    def makeTaskFuture(internalOntologyIri: SmartIri): Task[ReadOntologyMetadataV2] =
      for {
        // Make sure the ontology doesn't already exist.
        existingOntologyMetadata <- ontologyHelpers.loadOntologyMetadata(internalOntologyIri)

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

        createOntologySparql = twirl.queries.sparql.v2.txt
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

        _ <- triplestoreService.sparqlHttpUpdate(createOntologySparql)

        // Check that the update was successful. To do this, we have to undo the SPARQL-escaping of the input.

        unescapedNewMetadata = OntologyMetadataV2(
                                 ontologyIri = internalOntologyIri,
                                 projectIri = Some(createOntologyRequest.projectIri),
                                 label = Some(createOntologyRequest.label),
                                 comment = createOntologyRequest.comment,
                                 lastModificationDate = Some(currentTime)
                               ).unescape

        maybeLoadedOntologyMetadata <- ontologyHelpers.loadOntologyMetadata(internalOntologyIri)

        _ = maybeLoadedOntologyMetadata match {
              case Some(loadedOntologyMetadata) =>
                if (loadedOntologyMetadata != unescapedNewMetadata) {
                  throw UpdateNotPerformedException()
                }

              case None => throw UpdateNotPerformedException()
            }

        _ <- // Update the ontology cache with the unescaped metadata.
          ontologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(
            internalOntologyIri,
            ReadOntologyV2(ontologyMetadata = unescapedNewMetadata)
          )

      } yield ReadOntologyMetadataV2(ontologies = Set(unescapedNewMetadata))

    for {
      requestingUser <- ZIO.succeed(createOntologyRequest.requestingUser)
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
      projectInfo <-
        messageRelay
          .ask[ProjectGetResponseADM](
            ProjectGetRequestADM(identifier =
              IriIdentifier
                .fromString(projectIri.toString)
                .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
            )
          )

      // Check that the ontology name is valid.
      validOntologyName =
        ValuesValidator
          .validateProjectSpecificOntologyName(createOntologyRequest.ontologyName)
          .getOrElse(
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
                      createOntologyRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalOntologyIri)
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
  ): Task[ReadOntologyMetadataV2] = {
    def makeTaskFuture(internalOntologyIri: SmartIri): Task[ReadOntologyMetadataV2] =
      for {
        cacheData <- ontologyCache.getCacheData

        // Check that the user has permission to update the ontology.
        projectIri <-
          ontologyHelpers.checkPermissionsForOntologyUpdate(
            internalOntologyIri,
            changeOntologyMetadataRequest.requestingUser
          )

        // Check that the ontology exists and has not been updated by another user since the client last read its metadata.
        _ <- ontologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               changeOntologyMetadataRequest.lastModificationDate
             )

        // get the metadata of the ontology.
        oldMetadata: OntologyMetadataV2 = cacheData.ontologies(internalOntologyIri).ontologyMetadata
        // Was there a comment in the ontology metadata?
        ontologyHasComment: Boolean = oldMetadata.comment.nonEmpty

        // Update the metadata.

        currentTime: Instant = Instant.now

        updateSparql = twirl.queries.sparql.v2.txt
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

        _ <- triplestoreService.sparqlHttpUpdate(updateSparql)

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

        maybeLoadedOntologyMetadata <- ontologyHelpers.loadOntologyMetadata(internalOntologyIri)

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
        _ <- ontologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(
               internalOntologyIri,
               updatedOntology
             )

      } yield ReadOntologyMetadataV2(ontologies = Set(unescapedNewMetadata))

    for {
      _                  <- ontologyHelpers.checkExternalOntologyIriForUpdate(changeOntologyMetadataRequest.ontologyIri)
      internalOntologyIri = changeOntologyMetadataRequest.ontologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      changeOntologyMetadataRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalOntologyIri = internalOntologyIri)
                    )
    } yield taskResult
  }

  def deleteOntologyComment(
    deleteOntologyCommentRequestV2: DeleteOntologyCommentRequestV2
  ): Task[ReadOntologyMetadataV2] = {
    def makeTaskFuture(internalOntologyIri: SmartIri): Task[ReadOntologyMetadataV2] =
      for {
        cacheData <- ontologyCache.getCacheData

        // Check that the user has permission to update the ontology.
        projectIri <- ontologyHelpers.checkPermissionsForOntologyUpdate(
                        internalOntologyIri,
                        deleteOntologyCommentRequestV2.requestingUser
                      )

        // Check that the ontology exists and has not been updated by another user since the client last read its metadata.
        _ <- ontologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               deleteOntologyCommentRequestV2.lastModificationDate
             )

        // get the metadata of the ontology.
        oldMetadata: OntologyMetadataV2 = cacheData.ontologies(internalOntologyIri).ontologyMetadata
        // Was there a comment in the ontology metadata?
        ontologyHasComment: Boolean = oldMetadata.comment.nonEmpty

        // Update the metadata.

        currentTime: Instant = Instant.now

        updateSparql = twirl.queries.sparql.v2.txt
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

        _ <- triplestoreService.sparqlHttpUpdate(updateSparql)

        // Check that the update was successful.

        unescapedNewMetadata = OntologyMetadataV2(
                                 ontologyIri = internalOntologyIri,
                                 projectIri = Some(projectIri),
                                 label = oldMetadata.label,
                                 comment = None,
                                 lastModificationDate = Some(currentTime)
                               ).unescape

        maybeLoadedOntologyMetadata <- ontologyHelpers.loadOntologyMetadata(internalOntologyIri)

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
        _ <- ontologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(internalOntologyIri, updatedOntology)
      } yield ReadOntologyMetadataV2(ontologies = Set(unescapedNewMetadata))

    for {
      _                  <- ontologyHelpers.checkExternalOntologyIriForUpdate(deleteOntologyCommentRequestV2.ontologyIri)
      internalOntologyIri = deleteOntologyCommentRequestV2.ontologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      deleteOntologyCommentRequestV2.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalOntologyIri = internalOntologyIri)
                    )
    } yield taskResult
  }

  /**
   * Creates a class in an existing ontology.
   *
   * @param createClassRequest the request to create the class.
   * @return a [[ReadOntologyV2]] in the internal schema, the containing the definition of the new class.
   */
  private def createClass(createClassRequest: CreateClassRequestV2): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] = {
      for {
        cacheData                           <- ontologyCache.getCacheData
        internalClassDef: ClassInfoContentV2 = createClassRequest.classInfoContent.toOntologySchema(InternalSchema)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               createClassRequest.lastModificationDate
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
                                                       cacheData.classToSuperClassLookup
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
        _ = OntologyCache.checkOntologyReferencesInClassDef(
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
                            OntologyHelpers.isKnoraResourceProperty(propertyIri, cacheData)
                          ),
                          linkProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                            OntologyHelpers.isLinkProp(propertyIri, cacheData)
                          ),
                          linkValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                            OntologyHelpers.isLinkValueProp(propertyIri, cacheData)
                          ),
                          fileValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(propertyIri =>
                            OntologyHelpers.isFileValueProp(propertyIri, cacheData)
                          )
                        )

        // Add the SPARQL-escaped class to the triplestore.

        currentTime: Instant = Instant.now

        updateSparql = twirl.queries.sparql.v2.txt
                         .createClass(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           classDef = internalClassDefWithLinkValueProps,
                           lastModificationDate = createClassRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- triplestoreService.sparqlHttpUpdate(updateSparql)

        // Check that the ontology's last modification date was updated.
        _ <- ontologyHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the data that was saved corresponds to the data that was submitted.
        loadedClassDef <- ontologyHelpers.loadClassDefinition(internalClassIri)

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

        _ <- ontologyCache.cacheUpdatedOntologyWithClass(internalOntologyIri, updatedOntology, internalClassIri)

        // Read the data back from the cache.
        response <- ontologyHelpers.getClassDefinitionsFromOntologyV2(
                      classIris = Set(internalClassIri),
                      allLanguages = true,
                      requestingUser = createClassRequest.requestingUser
                    )
      } yield response
    }

    for {
      requestingUser <- ZIO.succeed(createClassRequest.requestingUser)

      externalClassIri    = createClassRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <- ontologyHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      createClassRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalClassIri, internalOntologyIri)
                    )
    } yield taskResult
  }

  /**
   * Changes GUI orders in cardinalities in a class definition.
   *
   * @param changeGuiOrderRequest the request message.
   * @return the updated class definition.
   */
  private def changeGuiOrder(changeGuiOrderRequest: ChangeGuiOrderRequestV2): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] = {
      for {
        cacheData                           <- ontologyCache.getCacheData
        internalClassDef: ClassInfoContentV2 = changeGuiOrderRequest.classInfoContent.toOntologySchema(InternalSchema)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               changeGuiOrderRequest.lastModificationDate
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

        updateSparql = twirl.queries.sparql.v2.txt
                         .replaceClassCardinalities(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           classIri = internalClassIri,
                           newCardinalities = newReadClassInfo.entityInfoContent.directCardinalities,
                           lastModificationDate = changeGuiOrderRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- triplestoreService.sparqlHttpUpdate(updateSparql)

        // Check that the ontology's last modification date was updated.
        _ <- ontologyHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the data that was saved corresponds to the data that was submitted.
        loadedClassDef <- ontologyHelpers.loadClassDefinition(internalClassIri)

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
        _ <- ontologyCache.cacheUpdatedOntologyWithClass(internalOntologyIri, updatedOntology, internalClassIri)

        // Read the data back from the cache.
        response <- ontologyHelpers.getClassDefinitionsFromOntologyV2(
                      classIris = Set(internalClassIri),
                      allLanguages = true,
                      requestingUser = changeGuiOrderRequest.requestingUser
                    )

      } yield response
    }

    for {
      requestingUser <- ZIO.succeed(changeGuiOrderRequest.requestingUser)

      externalClassIri    = changeGuiOrderRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <- ontologyHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      changeGuiOrderRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalClassIri, internalOntologyIri)
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
  ): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] = {
      for {
        cacheData                           <- ontologyCache.getCacheData
        internalClassDef: ClassInfoContentV2 = addCardinalitiesRequest.classInfoContent.toOntologySchema(InternalSchema)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               addCardinalitiesRequest.lastModificationDate
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
               case Some((propIri: SmartIri, cardinality: KnoraCardinalityInfo)) =>
                 ZIO
                   .fail(
                     BadRequestException(
                       s"Cardinality ${cardinality.toString} for $propIri cannot be added to class ${addCardinalitiesRequest.classInfoContent.classIri}, because it is used in data"
                     )
                   )
                   .whenZIO(iriService.isClassUsedInData(internalClassIri))
               case None => ZIO.unit
             }

        // Make an updated class definition.
        newInternalClassDef = existingClassDef.copy(
                                directCardinalities =
                                  existingClassDef.directCardinalities ++ internalClassDef.directCardinalities
                              )

        // Check that the new cardinalities are valid, and add any inherited cardinalities.

        allBaseClassIrisWithoutInternal: Seq[SmartIri] = newInternalClassDef.subClassOf.toSeq.flatMap { baseClassIri =>
                                                           cacheData.classToSuperClassLookup.getOrElse(
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
        _ = OntologyCache.checkOntologyReferencesInClassDef(
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
                          knoraResourceProperties = propertyIrisOfAllCardinalities.filter(
                            OntologyHelpers.isKnoraResourceProperty(_, cacheData)
                          ),
                          linkProperties =
                            propertyIrisOfAllCardinalities.filter(OntologyHelpers.isLinkProp(_, cacheData)),
                          linkValueProperties =
                            propertyIrisOfAllCardinalities.filter(OntologyHelpers.isLinkValueProp(_, cacheData)),
                          fileValueProperties =
                            propertyIrisOfAllCardinalities.filter(OntologyHelpers.isFileValueProp(_, cacheData))
                        )

        // Add the cardinalities to the class definition in the triplestore.

        currentTime: Instant = Instant.now

        cardinalitiesToAdd: Map[SmartIri, KnoraCardinalityInfo] =
          newInternalClassDefWithLinkValueProps.directCardinalities -- existingClassDef.directCardinalities.keySet

        updateSparql = twirl.queries.sparql.v2.txt
                         .addCardinalitiesToClass(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           classIri = internalClassIri,
                           cardinalitiesToAdd = cardinalitiesToAdd,
                           lastModificationDate = addCardinalitiesRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- triplestoreService.sparqlHttpUpdate(updateSparql)

        // Check that the ontology's last modification date was updated.
        _ <- ontologyHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the data that was saved corresponds to the data that was submitted.
        loadedClassDef <- ontologyHelpers.loadClassDefinition(internalClassIri)

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

        // FIXME: should update subclasses in cache
        _ <- ontologyCache.cacheUpdatedOntologyWithClass(internalOntologyIri, updatedOntology, internalClassIri)

        // Read the data back from the cache.
        response <- ontologyHelpers.getClassDefinitionsFromOntologyV2(
                      classIris = Set(internalClassIri),
                      allLanguages = true,
                      requestingUser = addCardinalitiesRequest.requestingUser
                    )
      } yield response
    }

    for {
      requestingUser <- ZIO.succeed(addCardinalitiesRequest.requestingUser)

      externalClassIri    = addCardinalitiesRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <- ontologyHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      addCardinalitiesRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalClassIri, internalOntologyIri)
                    )
    } yield taskResult
  }

  /**
   * Replace cardinalities of a particular class.
   *
   * Fails if any of the new cardinalities is not consistent with the ontology or if persistent data is not compatible.
   *
   * @param request the [[ReplaceClassCardinalitiesRequestV2]] defining the cardinalities.
   * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
   */
  private def replaceClassCardinalities(request: ReplaceClassCardinalitiesRequestV2): Task[ReadOntologyV2] = {
    val task = for {
      newModel   <- makeUpdatedClassModel(request)
      validModel <- checkLastModificationDateAndCanCardinalitiesBeSet(request, newModel)
      response   <- replaceClassCardinalitiesInPersistence(request, validModel)
    } yield response
    val classIriExternal    = request.classInfoContent.classIri
    val ontologyIriExternal = classIriExternal.getOntologyFromEntity
    for {
      _ <- ontologyHelpers.checkOntologyAndEntityIrisForUpdate(
             ontologyIriExternal,
             classIriExternal,
             request.requestingUser
           )
      response <- IriLocker.runWithIriLock(request.apiRequestID, ONTOLOGY_CACHE_LOCK_IRI, task)
    } yield response
  }

  // Make an updated class definition.
  // Check that the new cardinalities are valid, and don't add any inherited cardinalities.
  // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
  private def makeUpdatedClassModel(request: ReplaceClassCardinalitiesRequestV2): Task[ReadClassInfoV2] = {
    val newClassInfo        = checkRdfTypeOfClassIsClass(request.classInfoContent.toOntologySchema(InternalSchema))
    val classIriExternal    = newClassInfo.classIri
    val classIri            = classIriExternal.toOntologySchema(InternalSchema)
    val ontologyIriExternal = classIri.getOntologyFromEntity
    for {
      cacheData <- ontologyCache.getCacheData
      oldClassInfo <-
        ontologyRepo
          .findClassBy(classIri.toInternalIri)
          .flatMap(ZIO.fromOption(_))
          .mapBoth(_ => BadRequestException(s"Class $ontologyIriExternal does not exist"), _.entityInfoContent)

      // Check that the new cardinalities are valid, and don't add any inherited cardinalities.
      newInternalClassDef = oldClassInfo.copy(directCardinalities = newClassInfo.directCardinalities)
      allBaseClassIrisWithoutInternal: Seq[SmartIri] = newInternalClassDef.subClassOf.toSeq.flatMap { baseClassIri =>
                                                         cacheData.classToSuperClassLookup.getOrElse(
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
      _ = OntologyCache.checkOntologyReferencesInClassDef(
            cache = cacheData,
            classDef = newInternalClassDefWithLinkValueProps,
            errorFun = { msg: String =>
              throw BadRequestException(msg)
            }
          )

      // Build the model
      inheritedCardinalities = cardinalitiesForClassWithInheritance.filterNot { case (propertyIri, _) =>
                                 newInternalClassDefWithLinkValueProps.directCardinalities.contains(propertyIri)
                               }
      propertyIrisOfAllCardinalitiesForClass = cardinalitiesForClassWithInheritance.keySet
      knoraResourceProperties =
        propertyIrisOfAllCardinalitiesForClass.filter(OntologyHelpers.isKnoraResourceProperty(_, cacheData))
      linkProperties      = propertyIrisOfAllCardinalitiesForClass.filter(OntologyHelpers.isLinkProp(_, cacheData))
      linkValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(OntologyHelpers.isLinkValueProp(_, cacheData))
      fileValueProperties = propertyIrisOfAllCardinalitiesForClass.filter(OntologyHelpers.isFileValueProp(_, cacheData))
    } yield ReadClassInfoV2(
      entityInfoContent = newInternalClassDefWithLinkValueProps,
      allBaseClasses = allBaseClassIris,
      isResourceClass = true,
      canBeInstantiated = true,
      inheritedCardinalities = inheritedCardinalities,
      knoraResourceProperties = knoraResourceProperties,
      linkProperties = linkProperties,
      linkValueProperties = linkValueProperties,
      fileValueProperties = fileValueProperties
    )
  }

  private def checkRdfTypeOfClassIsClass(classInfo: ClassInfoContentV2): ClassInfoContentV2 = {
    val rdfType: SmartIri = classInfo.requireIriObject(
      OntologyConstants.Rdf.Type.toSmartIri,
      throw BadRequestException(s"No rdf:type specified")
    )
    if (rdfType != OntologyConstants.Owl.Class.toSmartIri) {
      throw BadRequestException(s"Invalid rdf:type of property: $rdfType.")
    }
    classInfo
  }

  private def checkLastModificationDateAndCanCardinalitiesBeSet(
    request: ReplaceClassCardinalitiesRequestV2,
    newModel: ReadClassInfoV2
  ): Task[ReadClassInfoV2] = {
    val classIriExternal     = request.classInfoContent.classIri
    val classIri             = classIriExternal.toOntologySchema(InternalSchema)
    val ontologyIri          = classIri.getOntologyFromEntity
    val lastModificationDate = request.lastModificationDate
    for {
      _           <- ontologyHelpers.checkOntologyLastModificationDateBeforeUpdate(ontologyIri, lastModificationDate)
      checkResult <- checkCanCardinalitiesBeSet(newModel.entityInfoContent)
      _            = checkResult.fold(checkFailedErrorMsg => throw BadRequestException(checkFailedErrorMsg), _ => ())
    } yield newModel
  }

  private def checkCanCardinalitiesBeSet(newModel: ClassInfoContentV2): Task[Either[String, Unit]] = {
    val classIri             = newModel.classIri.toInternalIri
    val cardinalitiesToCheck = newModel.directCardinalities.map { case (p, c) => (p.toInternalIri, c.cardinality) }

    val iterableOfZioChecks = cardinalitiesToCheck.map { case (p, c) =>
      cardinalityService.canSetCardinality(classIri, p, c).map(_.fold(a => a, b => List(b)))
    }
    val checkResults =
      iterableOfZioChecks.reduceLeftOption((a, b) => a.zipWith(b)(_ ::: _)).getOrElse(ZIO.succeed(List.empty))

    checkResults
      .map(_.filter(_.isFailure))
      .map {
        case Nil    => Right(())
        case errors => Left(errors.mkString(" "))
      }
  }

  private def replaceClassCardinalitiesInPersistence(
    request: ReplaceClassCardinalitiesRequestV2,
    newReadClassInfo: ReadClassInfoV2
  ): Task[ReadOntologyV2] = {
    val timeOfUpdate = Instant.now()
    val classIri     = request.classInfoContent.classIri.toOntologySchema(InternalSchema)
    for {
      _ <- replaceClassCardinalitiesInTripleStore(request, newReadClassInfo, timeOfUpdate)
      _ <- replaceClassCardinalitiesInOntologyCache(request, newReadClassInfo, timeOfUpdate)
      // Return the response with the new data from the cache
      response <- ontologyHelpers.getClassDefinitionsFromOntologyV2(
                    classIris = Set(classIri),
                    allLanguages = true,
                    requestingUser = request.requestingUser
                  )
    } yield response
  }

  private def replaceClassCardinalitiesInTripleStore(
    request: ReplaceClassCardinalitiesRequestV2,
    newReadClassInfo: ReadClassInfoV2,
    timeOfUpdate: Instant
  ): Task[Unit] = {
    val classIri    = request.classInfoContent.classIri.toOntologySchema(InternalSchema)
    val ontologyIri = classIri.getOntologyFromEntity
    val updateSparql = twirl.queries.sparql.v2.txt
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
      _              <- triplestoreService.sparqlHttpUpdate(updateSparql)
      _              <- ontologyHelpers.checkOntologyLastModificationDateAfterUpdate(ontologyIri, timeOfUpdate)
      loadedClassDef <- ontologyHelpers.loadClassDefinition(classIri)
      _ = if (loadedClassDef != newReadClassInfo.entityInfoContent) {
            throw InconsistentRepositoryDataException(
              s"Attempted to save class definition ${newReadClassInfo.entityInfoContent}, but $loadedClassDef was saved instead."
            )
          }
    } yield ()
  }

  private def replaceClassCardinalitiesInOntologyCache(
    request: ReplaceClassCardinalitiesRequestV2,
    newReadClassInfo: ReadClassInfoV2,
    timeOfUpdate: Instant
  ): Task[Unit] = {
    val classIriExternal    = request.classInfoContent.classIri
    val classIri            = classIriExternal.toOntologySchema(InternalSchema)
    val ontologyIriExternal = classIriExternal.getOntologyFromEntity
    val ontologyIri         = classIri.getOntologyFromEntity
    for {
      ontology <- ontologyRepo
                    .findById(ontologyIri.toInternalIri)
                    .flatMap(ZIO.fromOption(_))
                    .orElseFail(BadRequestException(s"Ontology $ontologyIriExternal does not exist."))
      updatedOntologyMetaData = ontology.ontologyMetadata.copy(lastModificationDate = Some(timeOfUpdate))
      updatedOntologyClasses  = ontology.classes + (classIri -> newReadClassInfo)
      updatedOntology         = ontology.copy(ontologyMetadata = updatedOntologyMetaData, classes = updatedOntologyClasses)
      _                      <- ontologyCache.cacheUpdatedOntologyWithClass(ontologyIri, updatedOntology, classIri)
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
  ): Task[CanDoResponseV2] =
    for {
      requestingUser <- ZIO.succeed(canDeleteCardinalitiesFromClassRequest.requestingUser)

      externalClassIri    = canDeleteCardinalitiesFromClassRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <- ontologyHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      canDeleteCardinalitiesFromClassRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      cardinalityHandler.canDeleteCardinalitiesFromClass(
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
  ): Task[ReadOntologyV2] =
    for {
      requestingUser <- ZIO.succeed(deleteCardinalitiesFromClassRequest.requestingUser)

      externalClassIri    = deleteCardinalitiesFromClassRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <- ontologyHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      deleteCardinalitiesFromClassRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      cardinalityHandler.deleteCardinalitiesFromClass(
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
  private def canDeleteClass(canDeleteClassRequest: CanDeleteClassRequestV2): Task[CanDoResponseV2] = {
    val internalClassIri: SmartIri    = canDeleteClassRequest.classIri.toOntologySchema(InternalSchema)
    val internalOntologyIri: SmartIri = internalClassIri.getOntologyFromEntity

    for {
      cacheData <- ontologyCache.getCacheData

      ontology =
        cacheData.ontologies.getOrElse(
          internalOntologyIri,
          throw BadRequestException(s"Ontology ${canDeleteClassRequest.classIri.getOntologyFromEntity} does not exist")
        )

      _ = if (!ontology.classes.contains(internalClassIri)) {
            throw BadRequestException(s"Class ${canDeleteClassRequest.classIri} does not exist")
          }

      userCanUpdateOntology <-
        ontologyHelpers.canUserUpdateOntology(internalOntologyIri, canDeleteClassRequest.requestingUser)
      classIsUsed <- iriService.isEntityUsed(entityIri = internalClassIri)
    } yield CanDoResponseV2.of(userCanUpdateOntology && !classIsUsed)
  }

  /**
   * Deletes a class.
   *
   * @param deleteClassRequest the request to delete the class.
   * @return a [[SuccessResponseV2]].
   */
  private def deleteClass(deleteClassRequest: DeleteClassRequestV2): Task[ReadOntologyMetadataV2] = {
    def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyMetadataV2] =
      for {
        cacheData <- ontologyCache.getCacheData

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               deleteClassRequest.lastModificationDate
             )

        // Check that the class exists.

        ontology = cacheData.ontologies(internalOntologyIri)

        _ = if (!ontology.classes.contains(internalClassIri)) {
              throw BadRequestException(s"Class ${deleteClassRequest.classIri} does not exist")
            }

        // Check that the class isn't used in data or ontologies.

        _ <- ZIO
               .fail(
                 BadRequestException(
                   s"Class ${deleteClassRequest.classIri} cannot be deleted, because it is used in data or ontologies"
                 )
               )
               .whenZIO(iriService.isEntityUsed(internalClassIri))

        // Delete the class from the triplestore.

        currentTime: Instant = Instant.now

        updateSparql = twirl.queries.sparql.v2.txt
                         .deleteClass(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           classIri = internalClassIri,
                           lastModificationDate = deleteClassRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- triplestoreService.sparqlHttpUpdate(updateSparql)

        // Check that the ontology's last modification date was updated.
        _ <- ontologyHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Update the cache.

        updatedOntology = ontology.copy(
                            ontologyMetadata = ontology.ontologyMetadata.copy(
                              lastModificationDate = Some(currentTime)
                            ),
                            classes = ontology.classes - internalClassIri
                          )

        _ <- ontologyCache.cacheUpdatedOntology(internalOntologyIri, updatedOntology)

      } yield ReadOntologyMetadataV2(Set(updatedOntology.ontologyMetadata))

    for {
      requestingUser <- ZIO.succeed(deleteClassRequest.requestingUser)

      externalClassIri    = deleteClassRequest.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <- ontologyHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      deleteClassRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalClassIri, internalOntologyIri)
                    )
    } yield taskResult
  }

  /**
   * Checks whether a property can be deleted.
   *
   * @param canDeletePropertyRequest the request message.
   * @return a [[CanDoResponseV2]] indicating whether the property can be deleted.
   */
  private def canDeleteProperty(canDeletePropertyRequest: CanDeletePropertyRequestV2): Task[CanDoResponseV2] = {
    val internalPropertyIri = canDeletePropertyRequest.propertyIri.toOntologySchema(InternalSchema)
    val internalOntologyIri = internalPropertyIri.getOntologyFromEntity

    for {
      cacheData <- ontologyCache.getCacheData

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
        ontologyHelpers.canUserUpdateOntology(internalOntologyIri, canDeletePropertyRequest.requestingUser)
      propertyIsUsed <- iriService.isEntityUsed(internalPropertyIri)
    } yield CanDoResponseV2.of(userCanUpdateOntology && !propertyIsUsed)
  }

  /**
   * Deletes a property. If the property is a link property, the corresponding link value property is also deleted.
   *
   * @param deletePropertyRequest the request to delete the property.
   * @return a [[ReadOntologyMetadataV2]].
   */
  private def deleteProperty(deletePropertyRequest: DeletePropertyRequestV2): Task[ReadOntologyMetadataV2] = {
    def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyMetadataV2] =
      for {
        cacheData <- ontologyCache.getCacheData

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               deletePropertyRequest.lastModificationDate
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

        _ <-
          ZIO
            .fail(
              BadRequestException(
                s"Property ${deletePropertyRequest.propertyIri} cannot be deleted, because it is used in data or ontologies"
              )
            )
            .whenZIO(iriService.isEntityUsed(internalPropertyIri))

        _ <- maybeInternalLinkValuePropertyIri match {
               case Some(internalLinkValuePropertyIri) =>
                 ZIO
                   .fail(
                     BadRequestException(
                       s"Property ${deletePropertyRequest.propertyIri} cannot be deleted, because the corresponding link value property, ${internalLinkValuePropertyIri
                           .toOntologySchema(ApiV2Complex)}, is used in data or ontologies"
                     )
                   )
                   .whenZIO(iriService.isEntityUsed(internalLinkValuePropertyIri))
               case None => ZIO.succeed(())
             }

        // Delete the property from the triplestore.

        currentTime: Instant = Instant.now

        updateSparql = twirl.queries.sparql.v2.txt
                         .deleteProperty(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           propertyIri = internalPropertyIri,
                           maybeLinkValuePropertyIri = maybeInternalLinkValuePropertyIri,
                           lastModificationDate = deletePropertyRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- triplestoreService.sparqlHttpUpdate(updateSparql)

        // Check that the ontology's last modification date was updated.
        _ <- ontologyHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Update the cache.

        propertiesToRemoveFromCache = Set(internalPropertyIri) ++ maybeInternalLinkValuePropertyIri

        updatedOntology =
          ontology.copy(
            ontologyMetadata = ontology.ontologyMetadata.copy(
              lastModificationDate = Some(currentTime)
            ),
            properties = ontology.properties -- propertiesToRemoveFromCache
          )

        _ <- ontologyCache.cacheUpdatedOntology(internalOntologyIri, updatedOntology)

      } yield ReadOntologyMetadataV2(Set(updatedOntology.ontologyMetadata))

    for {
      requestingUser <- ZIO.succeed(deletePropertyRequest.requestingUser)

      externalPropertyIri = deletePropertyRequest.propertyIri
      externalOntologyIri = externalPropertyIri.getOntologyFromEntity

      _ <- ontologyHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalPropertyIri, requestingUser)

      internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      deletePropertyRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalPropertyIri, internalOntologyIri)
                    )
    } yield taskResult
  }

  /**
   * Checks whether an ontology can be deleted.
   *
   * @param canDeleteOntologyRequest the request message.
   * @return a [[CanDoResponseV2]] indicating whether an ontology can be deleted.
   */
  private def canDeleteOntology(canDeleteOntologyRequest: CanDeleteOntologyRequestV2): Task[CanDoResponseV2] = {
    val internalOntologyIri: SmartIri = canDeleteOntologyRequest.ontologyIri.toOntologySchema(InternalSchema)

    for {
      cacheData <- ontologyCache.getCacheData

      ontology = cacheData.ontologies.getOrElse(
                   internalOntologyIri,
                   throw BadRequestException(
                     s"Ontology ${canDeleteOntologyRequest.ontologyIri.getOntologyFromEntity} does not exist"
                   )
                 )

      userCanUpdateOntology <-
        ontologyHelpers.canUserUpdateOntology(internalOntologyIri, canDeleteOntologyRequest.requestingUser)
      subjectsUsingOntology <- ontologyHelpers.getSubjectsUsingOntology(ontology)
    } yield CanDoResponseV2.of(userCanUpdateOntology && subjectsUsingOntology.isEmpty)
  }

  private def deleteOntology(deleteOntologyRequest: DeleteOntologyRequestV2): Task[SuccessResponseV2] = {
    def makeTaskFuture(internalOntologyIri: SmartIri): Task[SuccessResponseV2] =
      for {
        cacheData <- ontologyCache.getCacheData

        // Check that the user has permission to update the ontology.
        _ <-
          ontologyHelpers.checkPermissionsForOntologyUpdate(internalOntologyIri, deleteOntologyRequest.requestingUser)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               deleteOntologyRequest.lastModificationDate
             )

        // Check that none of the entities in the ontology are used in data or in other ontologies.

        ontology               = cacheData.ontologies(internalOntologyIri)
        subjectsUsingOntology <- ontologyHelpers.getSubjectsUsingOntology(ontology)

        _ = if (subjectsUsingOntology.nonEmpty) {
              val sortedSubjects: Seq[IRI] = subjectsUsingOntology.map(s => "<" + s + ">").toVector.sorted

              throw BadRequestException(
                s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} cannot be deleted, because of subjects that refer to it: ${sortedSubjects
                    .mkString(", ")}"
              )
            }

        // Delete everything in the ontology's named graph.

        updateSparql = twirl.queries.sparql.v2.txt
                         .deleteOntology(
                           ontologyNamedGraphIri = internalOntologyIri
                         )
                         .toString()

        _ <- triplestoreService.sparqlHttpUpdate(updateSparql)

        // Check that the ontology has been deleted.

        maybeOntologyMetadata <- ontologyHelpers.loadOntologyMetadata(internalOntologyIri)

        _ = if (maybeOntologyMetadata.nonEmpty) {
              throw UpdateNotPerformedException(
                s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} was not deleted. Please report this as a possible bug."
              )
            }

        // Remove the ontology from the cache.
        _ <- ontologyCache.deleteOntology(internalOntologyIri)
      } yield SuccessResponseV2(s"Ontology ${internalOntologyIri.toOntologySchema(ApiV2Complex)} has been deleted")

    for {
      _                  <- ontologyHelpers.checkExternalOntologyIriForUpdate(deleteOntologyRequest.ontologyIri)
      internalOntologyIri = deleteOntologyRequest.ontologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      deleteOntologyRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalOntologyIri)
                    )
    } yield taskResult
  }

  /**
   * Creates a property in an existing ontology.
   *
   * @param createPropertyRequest the request to create the property.
   * @return a [[ReadOntologyV2]] in the internal schema, the containing the definition of the new property.
   */
  private def createProperty(createPropertyRequest: CreatePropertyRequestV2): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] = {
      for {
        cacheData          <- ontologyCache.getCacheData
        internalPropertyDef = createPropertyRequest.propertyInfoContent.toOntologySchema(InternalSchema)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               createPropertyRequest.lastModificationDate
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
                                   OntologyHelpers.isKnoraResourceProperty(
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
            val linkValuePropertyDef = OntologyHelpers.linkPropertyDefToLinkValuePropertyDef(internalPropertyDef)

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
                OntologyCache.checkPropertyConstraint(
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

        _ = OntologyCache.checkPropertyConstraint(
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
        _ = OntologyCache.checkOntologyReferencesInPropertyDef(
              ontologyCacheData = cacheData,
              propertyDef = internalPropertyDef,
              errorFun = { msg: String =>
                throw BadRequestException(msg)
              }
            )

        // Add the property (and the link value property if needed) to the triplestore.

        currentTime: Instant = Instant.now

        updateSparql = twirl.queries.sparql.v2.txt
                         .createProperty(
                           ontologyNamedGraphIri = internalOntologyIri,
                           ontologyIri = internalOntologyIri,
                           propertyDef = internalPropertyDef,
                           maybeLinkValuePropertyDef = maybeLinkValuePropertyDef,
                           lastModificationDate = createPropertyRequest.lastModificationDate,
                           currentTime = currentTime
                         )
                         .toString()

        _ <- triplestoreService.sparqlHttpUpdate(updateSparql)

        // Check that the ontology's last modification date was updated.
        _ <- ontologyHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the data that was saved corresponds to the data that was submitted. To make this comparison,
        // we have to undo the SPARQL-escaping of the input.

        loadedPropertyDef <- ontologyHelpers.loadPropertyDefinition(internalPropertyIri)

        unescapedInputPropertyDef = internalPropertyDef.unescape

        _ = if (loadedPropertyDef != unescapedInputPropertyDef) {
              throw InconsistentRepositoryDataException(
                s"Attempted to save property definition $unescapedInputPropertyDef, but $loadedPropertyDef was saved"
              )
            }

        maybeLoadedLinkValuePropertyDefFuture: Option[Task[PropertyInfoContentV2]] =
          maybeLinkValuePropertyDef.map { linkValuePropertyDef =>
            ontologyHelpers.loadPropertyDefinition(linkValuePropertyDef.propertyIri)
          }

        maybeLoadedLinkValuePropertyDef <-
          ZIO.collectAll(maybeLoadedLinkValuePropertyDefFuture)
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

        _ <- ontologyCache.cacheUpdatedOntology(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.
        response <- getPropertyDefinitionsFromOntologyV2(
                      propertyIris = Set(internalPropertyIri),
                      allLanguages = true,
                      requestingUser = createPropertyRequest.requestingUser
                    )
      } yield response
    }

    for {
      requestingUser <- ZIO.succeed(createPropertyRequest.requestingUser)

      externalPropertyIri = createPropertyRequest.propertyInfoContent.propertyIri
      externalOntologyIri = externalPropertyIri.getOntologyFromEntity

      _ <- ontologyHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalPropertyIri, requestingUser)

      internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      createPropertyRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalPropertyIri, internalOntologyIri)
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
  ): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] = {
      for {
        cacheData <- ontologyCache.getCacheData

        ontology = cacheData.ontologies(internalOntologyIri)

        currentReadPropertyInfo: ReadPropertyInfoV2 =
          ontology.properties.getOrElse(
            internalPropertyIri,
            throw NotFoundException(s"Property ${changePropertyGuiElementRequest.propertyIri} not found")
          )

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               changePropertyGuiElementRequest.lastModificationDate
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

        updateSparql = twirl.queries.sparql.v2.txt
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

        _ <- triplestoreService.sparqlHttpUpdate(updateSparql)

        // Check that the ontology's last modification date was updated.

        _ <- ontologyHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the data that was saved corresponds to the data that was submitted. To make this comparison,
        // we have to undo the SPARQL-escaping of the input.

        loadedPropertyDef <- ontologyHelpers.loadPropertyDefinition(internalPropertyIri)

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

        maybeLoadedLinkValuePropertyDefFuture: Option[Task[PropertyInfoContentV2]] =
          maybeCurrentLinkValueReadPropertyInfo.map { linkValueReadPropertyInfo =>
            ontologyHelpers.loadPropertyDefinition(linkValueReadPropertyInfo.entityInfoContent.propertyIri)
          }

        maybeLoadedLinkValuePropertyDef <- ZIO.collectAll(maybeLoadedLinkValuePropertyDefFuture)

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

        _ <- ontologyCache.cacheUpdatedOntology(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.

        response <- getPropertyDefinitionsFromOntologyV2(
                      propertyIris = Set(internalPropertyIri),
                      allLanguages = true,
                      requestingUser = changePropertyGuiElementRequest.requestingUser
                    )
      } yield response
    }

    for {
      requestingUser <- ZIO.succeed(changePropertyGuiElementRequest.requestingUser)

      externalPropertyIri = changePropertyGuiElementRequest.propertyIri.value.toSmartIri
      externalOntologyIri = externalPropertyIri.getOntologyFromEntity

      _ <- ontologyHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalPropertyIri, requestingUser)

      internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      changePropertyGuiElementRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalPropertyIri, internalOntologyIri)
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
  ): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] = {
      for {
        cacheData <- ontologyCache.getCacheData

        ontology = cacheData.ontologies(internalOntologyIri)

        currentReadPropertyInfo: ReadPropertyInfoV2 =
          ontology.properties.getOrElse(
            internalPropertyIri,
            throw NotFoundException(s"Property ${changePropertyLabelsOrCommentsRequest.propertyIri} not found")
          )

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               changePropertyLabelsOrCommentsRequest.lastModificationDate
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

        updateSparql = twirl.queries.sparql.v2.txt
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

        _ <- triplestoreService.sparqlHttpUpdate(updateSparql)

        // Check that the ontology's last modification date was updated.
        _ <- ontologyHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the data that was saved corresponds to the data that was submitted. To make this comparison,
        // we have to undo the SPARQL-escaping of the input.

        loadedPropertyDef <- ontologyHelpers.loadPropertyDefinition(internalPropertyIri)

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

        maybeLoadedLinkValuePropertyDefFuture: Option[Task[PropertyInfoContentV2]] =
          maybeCurrentLinkValueReadPropertyInfo.map { linkValueReadPropertyInfo =>
            ontologyHelpers.loadPropertyDefinition(linkValueReadPropertyInfo.entityInfoContent.propertyIri)
          }

        maybeLoadedLinkValuePropertyDef <- ZIO.collectAll(maybeLoadedLinkValuePropertyDefFuture)

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

        _ <- ontologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.

        response <- getPropertyDefinitionsFromOntologyV2(
                      propertyIris = Set(internalPropertyIri),
                      allLanguages = true,
                      requestingUser = changePropertyLabelsOrCommentsRequest.requestingUser
                    )
      } yield response
    }

    for {
      requestingUser <- ZIO.succeed(changePropertyLabelsOrCommentsRequest.requestingUser)

      externalPropertyIri = changePropertyLabelsOrCommentsRequest.propertyIri
      externalOntologyIri = externalPropertyIri.getOntologyFromEntity

      _ <- ontologyHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalPropertyIri, requestingUser)

      internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      changePropertyLabelsOrCommentsRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalPropertyIri, internalOntologyIri)
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
  ): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] =
      for {
        cacheData <- ontologyCache.getCacheData

        ontology = cacheData.ontologies(internalOntologyIri)
        currentReadClassInfo: ReadClassInfoV2 =
          ontology.classes.getOrElse(
            internalClassIri,
            throw NotFoundException(s"Class ${changeClassLabelsOrCommentsRequest.classIri} not found")
          )

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               changeClassLabelsOrCommentsRequest.lastModificationDate
             )

        // Do the update.

        currentTime: Instant = Instant.now

        updateSparql = twirl.queries.sparql.v2.txt
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

        _ <- triplestoreService.sparqlHttpUpdate(updateSparql)

        // Check that the ontology's last modification date was updated.
        _ <- ontologyHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the data that was saved corresponds to the data that was submitted. To make this comparison,
        // we have to undo the SPARQL-escaping of the input.
        loadedClassDef <- ontologyHelpers.loadClassDefinition(internalClassIri)

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

        _ <- ontologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.

        response <- ontologyHelpers.getClassDefinitionsFromOntologyV2(
                      classIris = Set(internalClassIri),
                      allLanguages = true,
                      requestingUser = changeClassLabelsOrCommentsRequest.requestingUser
                    )
      } yield response

    for {
      requestingUser <- ZIO.succeed(changeClassLabelsOrCommentsRequest.requestingUser)

      externalClassIri    = changeClassLabelsOrCommentsRequest.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <- ontologyHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(
                      changeClassLabelsOrCommentsRequest.apiRequestID,
                      ONTOLOGY_CACHE_LOCK_IRI,
                      makeTaskFuture(internalClassIri, internalOntologyIri)
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
  ): Task[ReadOntologyV2] = {
    def makeTaskFuture(
      cacheData: OntologyCacheData,
      internalPropertyIri: SmartIri,
      internalOntologyIri: SmartIri,
      ontology: ReadOntologyV2,
      propertyToUpdate: ReadPropertyInfoV2
    ): Task[ReadOntologyV2] =
      for {

        // Check that the ontology exists and has not been updated by another user since the client last read its metadata.
        _ <- ontologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               deletePropertyCommentRequest.lastModificationDate
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
        updateSparql: String = twirl.queries.sparql.v2.txt
                                 .deletePropertyComment(
                                   ontologyNamedGraphIri = internalOntologyIri,
                                   ontologyIri = internalOntologyIri,
                                   propertyIri = internalPropertyIri,
                                   maybeLinkValuePropertyIri = maybeLinkValueOfPropertyToUpdateIri,
                                   lastModificationDate = deletePropertyCommentRequest.lastModificationDate,
                                   currentTime = currentTime
                                 )
                                 .toString()

        _ <- triplestoreService.sparqlHttpUpdate(updateSparql)

        // Check that the ontology's last modification date was updated.
        _ <- ontologyHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the update was successful.
        loadedPropertyDef <- ontologyHelpers.loadPropertyDefinition(internalPropertyIri)

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

        maybeLoadedLinkValuePropertyDefFuture: Option[Task[PropertyInfoContentV2]] =
          maybeLinkValueOfPropertyToUpdate.map { linkValueReadPropertyInfo: ReadPropertyInfoV2 =>
            ontologyHelpers.loadPropertyDefinition(linkValueReadPropertyInfo.entityInfoContent.propertyIri)
          }

        maybeLoadedLinkValuePropertyDef <- ZIO.collectAll(maybeLoadedLinkValuePropertyDefFuture)

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

        _ <- ontologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.

        response <- getPropertyDefinitionsFromOntologyV2(
                      propertyIris = Set(internalPropertyIri),
                      allLanguages = true,
                      requestingUser = deletePropertyCommentRequest.requestingUser
                    )

      } yield response

    for {
      requestingUser <- ZIO.succeed(deletePropertyCommentRequest.requestingUser)

      externalPropertyIri: SmartIri = deletePropertyCommentRequest.propertyIri
      externalOntologyIri: SmartIri = externalPropertyIri.getOntologyFromEntity

      _ <- ontologyHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalPropertyIri, requestingUser)

      internalPropertyIri: SmartIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri: SmartIri = externalOntologyIri.toOntologySchema(InternalSchema)

      cacheData <- ontologyCache.getCacheData

      ontology: ReadOntologyV2 = cacheData.ontologies(internalOntologyIri)

      propertyToUpdate: ReadPropertyInfoV2 =
        ontology.properties.getOrElse(
          internalPropertyIri,
          throw NotFoundException(s"Property ${deletePropertyCommentRequest.propertyIri} not found")
        )

      hasComment: Boolean = propertyToUpdate.entityInfoContent.predicates.contains(
                              OntologyConstants.Rdfs.Comment.toSmartIri
                            )

      taskResult <-
        if (hasComment) for {
          // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
          taskResult <- IriLocker.runWithIriLock(
                          deletePropertyCommentRequest.apiRequestID,
                          ONTOLOGY_CACHE_LOCK_IRI,
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
  ): Task[ReadOntologyV2] = {
    def makeTaskFuture(
      cacheData: OntologyCacheData,
      internalClassIri: SmartIri,
      internalOntologyIri: SmartIri,
      ontology: ReadOntologyV2,
      classToUpdate: ReadClassInfoV2
    ): Task[ReadOntologyV2] =
      for {

        // Check that the ontology exists and has not been updated by another user since the client last read its metadata.
        _ <- ontologyHelpers.checkOntologyLastModificationDateBeforeUpdate(
               internalOntologyIri,
               deleteClassCommentRequest.lastModificationDate
             )

        currentTime: Instant = Instant.now

        // Delete the comment
        updateSparql: String = twirl.queries.sparql.v2.txt
                                 .deleteClassComment(
                                   ontologyNamedGraphIri = internalOntologyIri,
                                   ontologyIri = internalOntologyIri,
                                   classIri = internalClassIri,
                                   lastModificationDate = deleteClassCommentRequest.lastModificationDate,
                                   currentTime = currentTime
                                 )
                                 .toString()

        _ <- triplestoreService.sparqlHttpUpdate(updateSparql)

        // Check that the ontology's last modification date was updated.
        _ <- ontologyHelpers.checkOntologyLastModificationDateAfterUpdate(internalOntologyIri, currentTime)

        // Check that the update was successful.
        loadedClassDef <- ontologyHelpers.loadClassDefinition(internalClassIri)

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

        _ <- ontologyCache.cacheUpdatedOntologyWithoutUpdatingMaps(internalOntologyIri, updatedOntology)

        // Read the data back from the cache.
        response <- ontologyHelpers.getClassDefinitionsFromOntologyV2(
                      classIris = Set(internalClassIri),
                      allLanguages = true,
                      requestingUser = deleteClassCommentRequest.requestingUser
                    )

      } yield response

    for {
      requestingUser <- ZIO.succeed(deleteClassCommentRequest.requestingUser)

      externalClassIri: SmartIri    = deleteClassCommentRequest.classIri
      externalOntologyIri: SmartIri = externalClassIri.getOntologyFromEntity

      _ <- ontologyHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri: SmartIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri: SmartIri = externalOntologyIri.toOntologySchema(InternalSchema)

      cacheData <- ontologyCache.getCacheData

      ontology: ReadOntologyV2 = cacheData.ontologies(internalOntologyIri)

      classToUpdate: ReadClassInfoV2 =
        ontology.classes.getOrElse(
          internalClassIri,
          throw NotFoundException(s"Class ${deleteClassCommentRequest.classIri} not found")
        )

      hasComment: Boolean = classToUpdate.entityInfoContent.predicates.contains(
                              OntologyConstants.Rdfs.Comment.toSmartIri
                            )

      taskResult <-
        if (hasComment) for {
          // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
          taskResult <- IriLocker.runWithIriLock(
                          deleteClassCommentRequest.apiRequestID,
                          ONTOLOGY_CACHE_LOCK_IRI,
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
          ontologyHelpers.getClassDefinitionsFromOntologyV2(
            classIris = Set(internalClassIri),
            allLanguages = true,
            requestingUser = deleteClassCommentRequest.requestingUser
          )
        }
    } yield taskResult
  }
}

object OntologyResponderV2Live {
  val layer: URLayer[
    StringFormatter
      with TriplestoreService
      with OntologyRepo
      with OntologyHelpers
      with OntologyCache
      with CardinalityService
      with CardinalityHandler
      with MessageRelay
      with IriService
      with AppConfig,
    OntologyResponderV2
  ] = ZLayer.fromZIO {
    for {
      config  <- ZIO.service[AppConfig]
      iriS    <- ZIO.service[IriService]
      mr      <- ZIO.service[MessageRelay]
      ch      <- ZIO.service[CardinalityHandler]
      cs      <- ZIO.service[CardinalityService]
      oc      <- ZIO.service[OntologyCache]
      oh      <- ZIO.service[OntologyHelpers]
      or      <- ZIO.service[OntologyRepo]
      ts      <- ZIO.service[TriplestoreService]
      sf      <- ZIO.service[StringFormatter]
      handler <- mr.subscribe(OntologyResponderV2Live(config, ch, cs, iriS, mr, oc, oh, or, ts, sf))
    } yield handler
  }
}
