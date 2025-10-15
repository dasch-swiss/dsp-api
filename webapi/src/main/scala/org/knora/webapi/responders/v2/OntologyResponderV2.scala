/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import eu.timepit.refined.types.string.NonEmptyString
import zio.*
import zio.prelude.Validation

import java.time.Instant
import java.util.UUID

import dsp.errors.*
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.ErrorHandlingMap
import org.knora.webapi.messages.v2.responder.CanDoResponseV2
import org.knora.webapi.messages.v2.responder.SuccessResponseV2
import org.knora.webapi.messages.v2.responder.ontologymessages.*
import org.knora.webapi.messages.v2.responder.ontologymessages.OwlCardinality.KnoraCardinalityInfo
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.responders.v2.ontology.CardinalityHandler
import org.knora.webapi.responders.v2.ontology.OntologyHelpers
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.common.KnoraIris.OntologyIri
import org.knora.webapi.slice.common.KnoraIris.PropertyIri
import org.knora.webapi.slice.common.KnoraIris.ResourceClassIri
import org.knora.webapi.slice.common.domain.InternalIri
import org.knora.webapi.slice.ontology.api.AddCardinalitiesToClassRequestV2
import org.knora.webapi.slice.ontology.api.ChangeGuiOrderRequestV2
import org.knora.webapi.slice.ontology.api.ChangePropertyLabelsOrCommentsRequestV2
import org.knora.webapi.slice.ontology.api.CreateClassRequestV2
import org.knora.webapi.slice.ontology.api.ReplaceClassCardinalitiesRequestV2
import org.knora.webapi.slice.ontology.domain.model.Cardinality
import org.knora.webapi.slice.ontology.domain.model.OntologyName
import org.knora.webapi.slice.ontology.domain.service.CardinalityService
import org.knora.webapi.slice.ontology.domain.service.ChangeCardinalityCheckResult.CanSetCardinalityCheckResult
import org.knora.webapi.slice.ontology.domain.service.OntologyCacheHelpers
import org.knora.webapi.slice.ontology.domain.service.OntologyRepo
import org.knora.webapi.slice.ontology.domain.service.OntologyTriplestoreHelpers
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.slice.ontology.repo.service.OntologyCache.ONTOLOGY_CACHE_LOCK_IRI
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

/**
 * Responds to requests dealing with ontologies.
 *
 * The API v2 ontology responder reads ontologies from two sources:
 *
 * - The triplestore.
 * - The constant knora-api v2 ontologies that are defined in Scala rather than in the triplestore, [[KnoraBaseToApiV2SimpleTransformationRules]] and [[KnoraBaseToApiV2ComplexTransformationRules]].
 *
 * It maintains an in-memory cache of all ontology data. This cache can be refreshed by using [[OntologyCache.refreshCache]].
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
final case class OntologyResponderV2(
  appConfig: AppConfig,
  cardinalityHandler: CardinalityHandler,
  cardinalityService: CardinalityService,
  iriService: IriService,
  ontologyCache: OntologyCache,
  ontologyCacheHelpers: OntologyCacheHelpers,
  ontologyTriplestoreHelpers: OntologyTriplestoreHelpers,
  ontologyRepo: OntologyRepo,
  knoraProjectService: KnoraProjectService,
  triplestoreService: TriplestoreService,
)(implicit val stringFormatter: StringFormatter)
    extends MessageHandler {

  override def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[OntologiesResponderRequestV2]

  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case EntityInfoGetRequestV2(classIris, propertyIris, requestingUser) =>
      getEntityInfoResponseV2(classIris, propertyIris, requestingUser)
    case StandoffEntityInfoGetRequestV2(standoffClassIris, standoffPropertyIris) =>
      getStandoffEntityInfoResponseV2(standoffClassIris, standoffPropertyIris)
    case StandoffClassesWithDataTypeGetRequestV2(_) =>
      getStandoffStandoffClassesWithDataTypeV2
    case StandoffAllPropertyEntitiesGetRequestV2(_) => getAllStandoffPropertyEntitiesV2
    case CheckSubClassRequestV2(subClassIri, superClassIri, _) =>
      checkSubClassV2(subClassIri, superClassIri)
    case SubClassesGetRequestV2(resourceClassIri, requestingUser) =>
      getSubClassesV2(resourceClassIri, requestingUser)
    case OntologyEntitiesGetRequestV2(ontologyIri, allLanguages, requestingUser) =>
      getOntologyEntitiesV2(ontologyIri, allLanguages, requestingUser)
    case ClassesGetRequestV2(resourceClassIris, allLanguages, requestingUser) =>
      ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(resourceClassIris, allLanguages, requestingUser)
    case PropertiesGetRequestV2(propertyIris, allLanguages, requestingUser) =>
      getPropertyDefinitionsFromOntologyV2(propertyIris, allLanguages, requestingUser)
    case OntologyMetadataGetByIriRequestV2(ontologyIris) =>
      getOntologyMetadataByIriV2(ontologyIris)
    case createOntologyRequest: CreateOntologyRequestV2 => createOntology(createOntologyRequest)
    case changeClassLabelsOrCommentsRequest: ChangeClassLabelsOrCommentsRequestV2 =>
      changeClassLabelsOrComments(changeClassLabelsOrCommentsRequest)
    case canDeleteCardinalitiesFromClassRequestV2: CanDeleteCardinalitiesFromClassRequestV2 =>
      canDeleteCardinalitiesFromClass(canDeleteCardinalitiesFromClassRequestV2)
    case deleteCardinalitiesFromClassRequest: DeleteCardinalitiesFromClassRequestV2 =>
      deleteCardinalitiesFromClass(deleteCardinalitiesFromClassRequest)
    case deleteClassRequest: DeleteClassRequestV2       => deleteClass(deleteClassRequest)
    case createPropertyRequest: CreatePropertyRequestV2 => createProperty(createPropertyRequest)
    case req: ChangePropertyGuiElementRequest           => changePropertyGuiElement(req)
    case other                                          => Responder.handleUnexpectedMessage(other, this.getClass.getName)
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
    propertyIris: Set[SmartIri],
    requestingUser: User,
  ): Task[EntityInfoGetResponseV2] =
    ontologyCacheHelpers.getEntityInfoResponseV2(classIris, propertyIris, requestingUser)

  /**
   * Given a list of standoff class IRIs and a list of property IRIs (ontology entities), returns an [[StandoffEntityInfoGetResponseV2]] describing both resource and property entities.
   *
   * @param standoffClassIris    the IRIs of the resource entities to be queried.
   * @param standoffPropertyIris the IRIs of the property entities to be queried.
   * @return a [[StandoffEntityInfoGetResponseV2]].
   */
  private def getStandoffEntityInfoResponseV2(
    standoffClassIris: Set[SmartIri],
    standoffPropertyIris: Set[SmartIri],
  ): Task[StandoffEntityInfoGetResponseV2] =
    for {
      cacheData <- ontologyCache.getCacheData

      entitiesInWrongSchema =
        (standoffClassIris ++ standoffPropertyIris).filter(_.getOntologySchema.contains(ApiV2Simple))

      _ <- ZIO.fail {
             NotFoundException(
               s"Some requested standoff classes were not found: ${entitiesInWrongSchema.mkString(", ")}",
             )
           }.when(entitiesInWrongSchema.nonEmpty)

      classIrisForCache    = standoffClassIris.map(_.toOntologySchema(InternalSchema))
      propertyIrisForCache = standoffPropertyIris.map(_.toOntologySchema(InternalSchema))

      classOntologies =
        cacheData.ontologies.view.filterKeys(classIrisForCache.map(_.getOntologyFromEntity)).values
      propertyOntologies =
        cacheData.ontologies.view.filterKeys(propertyIrisForCache.map(_.getOntologyFromEntity)).values

      classDefsAvailable = classOntologies.flatMap { ontology =>
                             ontology.classes.filter { case (classIri, classDef) =>
                               classDef.isStandoffClass && standoffClassIris.contains(classIri)
                             }
                           }.toMap

      propertyDefsAvailable = propertyOntologies.flatMap { ontology =>
                                ontology.properties.filter { case (propertyIri, _) =>
                                  standoffPropertyIris.contains(propertyIri) && cacheData.standoffProperties.contains(
                                    propertyIri,
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
               s"Some requested standoff properties were not found: ${missingPropertyDefs.mkString(", ")}",
             )
           }.when(missingPropertyDefs.nonEmpty)

      response =
        StandoffEntityInfoGetResponseV2(
          standoffClassInfoMap = new ErrorHandlingMap(classDefsAvailable, key => s"Resource class $key not found"),
          standoffPropertyInfoMap = new ErrorHandlingMap(propertyDefsAvailable, key => s"Property $key not found"),
        )
    } yield response

  /**
   * Gets information about all standoff classes that are a subclass of a data type standoff class.
   *
   * @return a [[StandoffClassesWithDataTypeGetResponseV2]]
   */
  private def getStandoffStandoffClassesWithDataTypeV2: Task[StandoffClassesWithDataTypeGetResponseV2] =
    for {
      cacheData <- ontologyCache.getCacheData
    } yield StandoffClassesWithDataTypeGetResponseV2(
      standoffClassInfoMap = cacheData.ontologies.values.flatMap { ontology =>
        ontology.classes.filter { case (_, classDef) =>
          classDef.isStandoffClass && classDef.standoffDataType.isDefined
        }
      }.toMap,
    )

  /**
   * Gets all standoff property entities.
   *
   * @return a [[StandoffAllPropertyEntitiesGetResponseV2]].
   */
  private def getAllStandoffPropertyEntitiesV2: Task[StandoffAllPropertyEntitiesGetResponseV2] =
    ontologyCache.getCacheData.map { data =>
      val ontologies: Iterable[ReadOntologyV2] = data.ontologies.values
      ontologies.flatMap(_.properties.view.filterKeys(data.standoffProperties)).toMap
    }.map(StandoffAllPropertyEntitiesGetResponseV2.apply)

  /**
   * Checks whether a certain Knora resource or value class is a subclass of another class.
   *
   * @param subClassIri   the IRI of the resource or value class whose subclassOf relations have to be checked.
   * @param superClassIri the IRI of the resource or value class to check for (whether it is a super class of `subClassIri` or not).
   * @return a [[CheckSubClassResponseV2]].
   */
  private def checkSubClassV2(subClassIri: SmartIri, superClassIri: SmartIri): Task[CheckSubClassResponseV2] =
    for {
      cacheData <- ontologyCache.getCacheData
      isSubClass <- ZIO
                      .fromOption(cacheData.classToSuperClassLookup.get(subClassIri))
                      .mapBoth(_ => BadRequestException(s"Class $subClassIri not found"), _.contains(superClassIri))
    } yield CheckSubClassResponseV2(isSubClass)

  /**
   * Gets the IRIs of the subclasses of a class.
   *
   * @param classIri the IRI of the class whose subclasses should be returned.
   * @return a [[SubClassesGetResponseV2]].
   */
  private def getSubClassesV2(classIri: SmartIri, requestingUser: User): Task[SubClassesGetResponseV2] =
    for {
      cacheData <- ontologyCache.getCacheData
      subClasses <-
        ZIO.foreach(cacheData.classToSubclassLookup(classIri).toVector.sorted) { subClassIri =>
          val labelValueMaybe = cacheData
            .ontologies(subClassIri.getOntologyFromEntity)
            .classes(subClassIri)
            .entityInfoContent
            .getPredicateStringLiteralObject(
              OntologyConstants.Rdfs.Label.toSmartIri,
              Some(requestingUser.lang, appConfig.fallbackLanguage),
            )
          ZIO
            .fromOption(labelValueMaybe)
            .mapBoth(
              _ => InconsistentRepositoryDataException(s"Resource class $subClassIri has no rdfs:label"),
              SubClassInfoV2(subClassIri, _),
            )
        }
    } yield SubClassesGetResponseV2(subClasses)

  /**
   * Gets the metadata describing the ontologies that belong to selected projects, or to all projects.
   *
   * @param projectIris    the IRIs of the projects selected, or an empty set if all projects are selected.
   * @return a [[ReadOntologyMetadataV2]].
   */
  def getOntologyMetadataForProjects(projectIris: Set[ProjectIri]): Task[ReadOntologyMetadataV2] = for {
    allOntologies <- ontologyRepo.findAll().map(_.map(_.ontologyMetadata).toSet)
    ontologies     = allOntologies.filter(ontology => projectIris.contains(ontology.projectIri.orNull))
  } yield ReadOntologyMetadataV2(ontologies)

  def getOntologyMetadataForProject(projectIris: ProjectIri): Task[ReadOntologyMetadataV2] =
    getOntologyMetadataForProjects(Set(projectIris))

  def getOntologyMetadataForAllProjects: Task[ReadOntologyMetadataV2] =
    ontologyRepo.findAll().map(_.map(_.ontologyMetadata).toSet).map(ReadOntologyMetadataV2.apply)

  /**
   * Gets the metadata describing the specified ontologies, or all ontologies.
   *
   * @param ontologyIris   the IRIs of the ontologies selected, or an empty set if all ontologies are selected.
   * @return a [[ReadOntologyMetadataV2]].
   */
  private def getOntologyMetadataByIriV2(ontologyIris: Set[SmartIri]): Task[ReadOntologyMetadataV2] =
    for {
      cacheData          <- ontologyCache.getCacheData
      returnAllOntologies = ontologyIris.isEmpty
      ontologyMetadata <-
        if (returnAllOntologies) { ZIO.succeed(cacheData.ontologies.values.map(_.ontologyMetadata).toSet) }
        else {
          val ontologyIrisForCache = ontologyIris.map(_.toOntologySchema(InternalSchema))
          val missingOntologies    = ontologyIrisForCache -- cacheData.ontologies.keySet
          if (missingOntologies.nonEmpty) {
            val msg = s"One or more requested ontologies were not found: ${missingOntologies.mkString(", ")}"
            ZIO.fail(BadRequestException(msg))
          } else {
            ZIO.succeed(
              cacheData.ontologies.view
                .filterKeys(ontologyIrisForCache)
                .values
                .map(ontology => ontology.ontologyMetadata)
                .toSet,
            )
          }
        }
    } yield ReadOntologyMetadataV2(ontologyMetadata)

  /**
   * Requests the entities defined in the given ontology.
   *
   * @param ontologyIri    the IRI (internal or external) of the ontology to be queried.
   * @param requestingUser the user making the request.
   * @return a [[ReadOntologyV2]].
   */
  def getOntologyEntitiesV2(
    ontologyIri: OntologyIri,
    allLanguages: Boolean,
    requestingUser: User,
  ): Task[ReadOntologyV2] =
    for {
      ontology <- getOntologyOrFailNotFound(ontologyIri)
      _ <- ZIO
             .fail(BadRequestException(s"The standoff ontology is not available in the API v2 simple schema"))
             .when(
               ontologyIri.ontologyName.value == "standoff" &&
                 ontologyIri.smartIri.getOntologySchema.contains(ApiV2Simple),
             )
      // Are we returning data in the user's preferred language, or in all available languages?
      userLang = Some(requestingUser.lang).filter(_ => !allLanguages)
    } yield ontology.copy(userLang = userLang)

  def getPropertyFromOntologyV2(
    propertyIri: PropertyIri,
    allLanguages: Boolean,
    requestingUser: User,
  ): Task[ReadOntologyV2] =
    getPropertyDefinitionsFromOntologyV2(Set(propertyIri.smartIri), allLanguages, requestingUser)

  /**
   * Requests information about properties in a single ontology.
   *
   * @param propertyIris   the IRIs (internal or external) of the properties to query for.
   * @param requestingUser the user making the request.
   * @return a [[ReadOntologyV2]].
   */
  def getPropertiesFromOntologyV2(
    propertyIris: Set[PropertyIri],
    allLanguages: Boolean,
    requestingUser: User,
  ): Task[ReadOntologyV2] =
    getPropertyDefinitionsFromOntologyV2(propertyIris.map(_.smartIri), allLanguages, requestingUser)

  private def getPropertyDefinitionsFromOntologyV2(
    propertyIris: Set[SmartIri],
    allLanguages: Boolean,
    requestingUser: User,
  ): Task[ReadOntologyV2] =
    for {
      ontologyIri <- ZIO
                       .succeed(propertyIris.map(_.ontologyIri))
                       .filterOrFail(_.size == 1)(BadRequestException(s"Only one ontology may be queried per request"))
                       .map(_.head)
      ontology             <- getOntologyOrFailNotFound(ontologyIri)
      propertyInfoResponse <- getEntityInfoResponseV2(propertyIris = propertyIris, requestingUser = requestingUser)
      userLang              = if allLanguages then None else Some(requestingUser.lang)
    } yield ReadOntologyV2(
      ontologyMetadata = ontology.ontologyMetadata,
      properties = propertyInfoResponse.propertyInfoMap,
      userLang = userLang,
    )

  /**
   * Creates a new, empty ontology.
   *
   * @param createOntologyRequest the request message.
   * @return a [[SuccessResponseV2]].
   */
  def createOntology(createOntologyRequest: CreateOntologyRequestV2): Task[ReadOntologyMetadataV2] = {
    def makeTaskFuture(ontologyIri: OntologyIri): Task[ReadOntologyMetadataV2] =
      for {
        _ <- ontologyRepo
               .findById(ontologyIri)
               .filterOrFail(_.isEmpty)(
                 BadRequestException(
                   s"Ontology ${ontologyIri.toComplexSchema.toIri} cannot be created, because it already exists",
                 ),
               )

        // If this is a shared ontology, make sure it's in the default shared ontologies project.
        _ <-
          ZIO.when(
            createOntologyRequest.isShared && createOntologyRequest.projectIri != OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject,
          ) {
            val msg =
              s"Shared ontologies must be created in project <${OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject}>"
            ZIO.fail(BadRequestException(msg))
          }

        // If it's in the default shared ontologies project, make sure it's a shared ontology.
        _ <-
          ZIO.when(
            !createOntologyRequest.isShared && createOntologyRequest.projectIri == OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject,
          ) {
            val msg =
              s"Ontologies created in project <${OntologyConstants.KnoraAdmin.DefaultSharedOntologiesProject}> must be shared"
            ZIO.fail(BadRequestException(msg))
          }

        // Create the ontology.
        currentTime <- Clock.instant
        createOntologySparql = sparql.v2.txt
                                 .createOntology(
                                   ontologyNamedGraphIri = ontologyIri.toInternalSchema,
                                   ontologyIri = ontologyIri.toInternalSchema,
                                   projectIri = createOntologyRequest.projectIri,
                                   isShared = createOntologyRequest.isShared,
                                   ontologyLabel = createOntologyRequest.label,
                                   ontologyComment = createOntologyRequest.comment,
                                   currentTime = currentTime,
                                 )
        _ <- save(Update(createOntologySparql))
      } yield ReadOntologyMetadataV2(ontologies =
        Set(
          OntologyMetadataV2(
            ontologyIri = ontologyIri.smartIri,
            projectIri = Some(createOntologyRequest.projectIri),
            label = Some(createOntologyRequest.label),
            comment = createOntologyRequest.comment,
            lastModificationDate = Some(currentTime),
          ).unescape,
        ),
      )

    for {
      // Check that the ontology name is valid.
      validOntologyName <-
        ZIO
          .fromEither(OntologyName.from(createOntologyRequest.ontologyName))
          .mapError(BadRequestException.apply)
          .filterOrFail(!_.isBuiltIn)(BadRequestException("A built in ontology cannot be created"))

      // Make the internal ontology IRI.
      projectIri = createOntologyRequest.projectIri
      project <-
        knoraProjectService.findById(projectIri).someOrFail(BadRequestException(s"Project not found: $projectIri"))
      ontologyIri: OntologyIri =
        OntologyIri.makeNew(validOntologyName, createOntologyRequest.isShared, Some(project.shortcode), stringFormatter)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(createOntologyRequest.apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(
                      makeTaskFuture(ontologyIri),
                    )
    } yield taskResult
  }

  private def save(sparql: Update) = triplestoreService.query(sparql) *> ontologyCache.refreshCache()

  /**
   * Changes ontology metadata.
   * @param ontologyIri           The IRI of the ontology to be updated.
   * @param label                 The new label for the ontology.
   * @param comment               The new comment for the ontology.
   * @param lastModificationDate  The last modification date of the ontology.
   * @param apiRequestID          The API request ID.
   * @param requestingUser        The user making the request.
   * @return a [[ReadOntologyMetadataV2]] containing the new metadata.
   */
  def changeOntologyMetadata(
    ontologyIri: OntologyIri,
    label: Option[String],
    comment: Option[NonEmptyString],
    lastModificationDate: Instant,
    apiRequestID: UUID,
    requestingUser: User,
  ): Task[ReadOntologyMetadataV2] = {
    val changeTask: Task[ReadOntologyMetadataV2] = for {
      _                 <- OntologyHelpers.checkOntologyIriForUpdate(ontologyIri)
      ontology          <- getOntologyOrFailNotFound(ontologyIri)
      projectIri        <- ontologyCacheHelpers.checkPermissionsForOntologyUpdate(ontologyIri, requestingUser)
      _                 <- ontologyTriplestoreHelpers.checkOntologyLastModificationDate(ontologyIri, lastModificationDate)
      oldMetadata        = ontology.ontologyMetadata
      ontologyHasComment = oldMetadata.comment.nonEmpty
      currentTime       <- Clock.instant
      updateSparql = sparql.v2.txt.changeOntologyMetadata(
                       ontologyNamedGraphIri = ontologyIri.toInternalSchema,
                       ontologyIri = ontologyIri.toInternalSchema,
                       newLabel = label,
                       hasOldComment = ontologyHasComment,
                       deleteOldComment = ontologyHasComment && comment.nonEmpty,
                       newComment = comment,
                       lastModificationDate = lastModificationDate,
                       currentTime = currentTime,
                     )
      _ <- save(Update(updateSparql))
    } yield ReadOntologyMetadataV2(
      Set(
        OntologyMetadataV2(
          ontologyIri = ontologyIri.toInternalSchema,
          projectIri = Some(projectIri),
          label = label.orElse(oldMetadata.label),
          comment = comment.orElse(oldMetadata.comment),
          lastModificationDate = Some(currentTime),
        ).unescape,
      ),
    )

    IriLocker.runWithIriLock(apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(changeTask)
  }

  private def getOntologyOrFailNotFound(iri: OntologyIri) =
    ontologyRepo.findById(iri).someOrFail(NotFoundException(s"Ontology not found: ${iri.toComplexSchema}"))

  def deleteOntologyComment(
    ontologyIri: OntologyIri,
    lastModificationDate: Instant,
    apiRequestID: UUID,
    requestingUser: User,
  ): Task[ReadOntologyMetadataV2] = {
    val deleteComment = for {
      _          <- OntologyHelpers.checkOntologyIriForUpdate(ontologyIri)
      projectIri <- ontologyCacheHelpers.checkPermissionsForOntologyUpdate(ontologyIri, requestingUser)
      _          <- ontologyTriplestoreHelpers.checkOntologyLastModificationDate(ontologyIri, lastModificationDate)
      ontology   <- getOntologyOrFailNotFound(ontologyIri)

      oldMetadata        = ontology.ontologyMetadata
      ontologyHasComment = oldMetadata.comment.nonEmpty

      currentTime <- Clock.instant
      updateSparql = sparql.v2.txt.changeOntologyMetadata(
                       ontologyNamedGraphIri = ontologyIri.toInternalSchema,
                       ontologyIri = ontologyIri.toInternalSchema,
                       newLabel = None,
                       hasOldComment = ontologyHasComment,
                       deleteOldComment = true,
                       newComment = None,
                       lastModificationDate = lastModificationDate,
                       currentTime = currentTime,
                     )
      _ <- save(Update(updateSparql))
    } yield ReadOntologyMetadataV2(
      Set(
        OntologyMetadataV2(
          ontologyIri = ontologyIri.toInternalSchema,
          projectIri = Some(projectIri),
          label = oldMetadata.label,
          comment = None,
          lastModificationDate = Some(currentTime),
        ).unescape,
      ),
    )

    IriLocker.runWithIriLock(apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(deleteComment)
  }

  /**
   * Creates a class in an existing ontology.
   *
   * @param createClassRequest the request to create the class.
   * @return a [[ReadOntologyV2]] in the internal schema, the containing the definition of the new class.
   */
  def createClass(createClassRequest: CreateClassRequestV2): Task[ReadOntologyV2] = {
    val task =
      for {
        requestingUser <- ZIO.succeed(createClassRequest.requestingUser)

        predicates          = createClassRequest.classInfoContent.predicates.values
        externalClassIri    = createClassRequest.classInfoContent.classIri
        externalOntologyIri = externalClassIri.getOntologyFromEntity

        _ <-
          ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(
            externalOntologyIri,
            externalClassIri,
            requestingUser,
          )

        internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
        internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

        // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
        _ <- Validation
               .validate(
                 PredicateInfoV2.checkRequiredStringLiteralWithLanguageTag(Rdfs.Label, predicates),
                 PredicateInfoV2.checkOptionalStringLiteralWithLanguageTag(Rdfs.Comment, predicates),
               )
               .mapError(BadRequestException.apply)
               .toZIO
        cacheData                           <- ontologyCache.getCacheData
        internalClassDef: ClassInfoContentV2 = createClassRequest.classInfoContent.toOntologySchema(InternalSchema)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDate(
               internalOntologyIri,
               createClassRequest.lastModificationDate,
             )
        // Check that the class's rdf:type is owl:Class.

        rdfType <- ZIO
                     .fromOption(internalClassDef.getIriObject(OntologyConstants.Rdf.Type.toSmartIri))
                     .orElseFail(BadRequestException(s"No rdf:type specified"))
        _ <- ZIO.when(rdfType != OntologyConstants.Owl.Class.toSmartIri) {
               ZIO.fail(BadRequestException(s"Invalid rdf:type for property: $rdfType"))
             }

        ontology = cacheData.ontologies(internalOntologyIri)

        // Check that the class doesn't exist yet.
        _ <- ZIO.when(ontology.classes.contains(internalClassIri)) {
               ZIO.fail(BadRequestException(s"Class ${createClassRequest.classInfoContent.classIri} already exists"))
             }

        // Check that the class's IRI isn't already used for something else.
        _ <-
          ZIO.when(ontology.properties.contains(internalClassIri) || ontology.individuals.contains(internalClassIri)) {
            ZIO.fail(BadRequestException(s"IRI ${createClassRequest.classInfoContent.classIri} is already used"))
          }

        // Check that the base classes that have Knora IRIs are defined as Knora resource classes.
        missingBaseClasses =
          internalClassDef.subClassOf
            .filter(_.isKnoraInternalEntityIri)
            .filter(baseClassIri => !OntologyHelpers.isKnoraInternalResourceClass(baseClassIri, cacheData))
        _ <- ZIO.when(missingBaseClasses.nonEmpty) {
               val msg = s"One or more specified base classes are invalid: ${missingBaseClasses.mkString(", ")}"
               ZIO.fail(BadRequestException(msg))
             }

        // Check for rdfs:subClassOf cycles.

        allBaseClassIrisWithoutSelf: Set[SmartIri] = internalClassDef.subClassOf.flatMap { baseClassIri =>
                                                       cacheData.classToSuperClassLookup
                                                         .getOrElse(baseClassIri, Set.empty[SmartIri])
                                                         .toSet
                                                     }

        _ <- ZIO.when(allBaseClassIrisWithoutSelf.contains(internalClassIri)) {
               val msg = s"Class ${createClassRequest.classInfoContent.classIri} would have a cyclical rdfs:subClassOf"
               ZIO.fail(BadRequestException(msg))
             }

        // Check that the class is a subclass of knora-base:Resource.
        allBaseClassIris: Seq[SmartIri] = internalClassIri +: allBaseClassIrisWithoutSelf.toSeq
        _ <- ZIO.when(!allBaseClassIris.contains(OntologyConstants.KnoraBase.Resource.toSmartIri)) {
               val msg =
                 s"Class ${createClassRequest.classInfoContent.classIri} would not be a subclass of knora-api:Resource"
               ZIO.fail(BadRequestException(msg))
             }

        // Check that the cardinalities are valid, and add any inherited cardinalities.
        cardinalitiesCheckResult <- OntologyHelpers
                                      .checkCardinalitiesBeforeAddingAndIfNecessaryAddLinkValueProperties(
                                        internalClassDef,
                                        allBaseClassIris.toSet,
                                        cacheData,
                                      )
                                      .toZIO
        (internalClassDefWithLinkValueProps, _) = cardinalitiesCheckResult

        // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
        _ <- ZIO.attempt(
               OntologyCache.checkOntologyReferencesInClassDef(
                 cacheData,
                 internalClassDefWithLinkValueProps,
                 (msg: String) => throw BadRequestException(msg),
               ),
             )

        // Add the SPARQL-escaped class to the triplestore.
        currentTime = Instant.now
        updateSparql = sparql.v2.txt.createClass(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         classDef = internalClassDefWithLinkValueProps,
                         lastModificationDate = createClassRequest.lastModificationDate,
                         currentTime = currentTime,
                       )
        _ <- save(Update(updateSparql))

        // Read the data back from the cache.
        response <- ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(
                      classIris = Set(internalClassIri),
                      allLanguages = true,
                      requestingUser = createClassRequest.requestingUser,
                    )
      } yield response
    IriLocker.runWithIriLock(createClassRequest.apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(task)
  }

  /**
   * Changes GUI orders in cardinalities in a class definition.
   *
   * @param changeGuiOrderRequest the request message.
   * @return the updated class definition.
   */
  def changeGuiOrder(changeGuiOrderRequest: ChangeGuiOrderRequestV2): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalClassIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] =
      for {
        cacheData                           <- ontologyCache.getCacheData
        internalClassDef: ClassInfoContentV2 = changeGuiOrderRequest.classInfoContent.toOntologySchema(InternalSchema)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDate(
               internalOntologyIri,
               changeGuiOrderRequest.lastModificationDate,
             )

        // Check that the class's rdf:type is owl:Class.
        rdfType <- ZIO
                     .fromOption(internalClassDef.getIriObject(OntologyConstants.Rdf.Type.toSmartIri))
                     .orElseFail(BadRequestException(s"No rdf:type specified"))
        _ <- ZIO.when(rdfType != OntologyConstants.Owl.Class.toSmartIri) {
               ZIO.fail(BadRequestException(s"Invalid rdf:type for property: $rdfType"))
             }

        // Check that the class exists.
        ontology = cacheData.ontologies(internalOntologyIri)
        currentReadClassInfo <-
          ZIO
            .fromOption(ontology.classes.get(internalClassIri))
            .orElseFail(BadRequestException(s"Class ${changeGuiOrderRequest.classInfoContent.classIri} does not exist"))

        // Check that the properties submitted already have cardinalities.
        wrongProperties: Set[SmartIri] =
          internalClassDef.directCardinalities.keySet -- currentReadClassInfo.entityInfoContent.directCardinalities.keySet
        _ <- ZIO.when(wrongProperties.nonEmpty) {
               val msg =
                 s"One or more submitted properties do not have cardinalities in class ${changeGuiOrderRequest.classInfoContent.classIri}: ${wrongProperties
                     .map(_.toOntologySchema(ApiV2Complex))
                     .mkString(", ")}"
               ZIO.fail(BadRequestException(msg))
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
                                             cardinalityWithCurrentGuiOrder: KnoraCardinalityInfo,
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
              },
            ),
          )

        // Replace the cardinalities in the class definition in the triplestore.

        currentTime <- Clock.instant

        updateSparql = sparql.v2.txt.replaceClassCardinalities(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         classIri = internalClassIri,
                         newCardinalities = newReadClassInfo.entityInfoContent.directCardinalities,
                         lastModificationDate = changeGuiOrderRequest.lastModificationDate,
                         currentTime = currentTime,
                       )
        _ <- save(Update(updateSparql))

        // Read the data back from the cache.
        response <- ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(
                      classIris = Set(internalClassIri),
                      allLanguages = true,
                      requestingUser = changeGuiOrderRequest.requestingUser,
                    )
      } yield response

    for {
      requestingUser <- ZIO.succeed(changeGuiOrderRequest.requestingUser)

      externalClassIri    = changeGuiOrderRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <-
        ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(changeGuiOrderRequest.apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(
                      makeTaskFuture(internalClassIri, internalOntologyIri),
                    )
    } yield taskResult
  }

  /**
   * Adds cardinalities to an existing class definition.
   *
   * @param addCardinalitiesRequest the request to add the cardinalities.
   * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
   */
  def addCardinalitiesToClass(addCardinalitiesRequest: AddCardinalitiesToClassRequestV2): Task[ReadOntologyV2] = {
    val task = for {
      requestingUser <- ZIO.succeed(addCardinalitiesRequest.requestingUser)

      externalClassIri    = addCardinalitiesRequest.classInfoContent.classIri
      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      externalOntologyIri = externalClassIri.getOntologyFromEntity
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      _ <-
        ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      cacheData                           <- ontologyCache.getCacheData
      internalClassDef: ClassInfoContentV2 = addCardinalitiesRequest.classInfoContent.toOntologySchema(InternalSchema)

      // Check that the ontology exists and has not been updated by another user since the client last read it.
      _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDate(
             internalOntologyIri,
             addCardinalitiesRequest.lastModificationDate,
           )

      // Check that the class's rdf:type is owl:Class.
      rdfType <- ZIO
                   .fromOption(internalClassDef.getIriObject(OntologyConstants.Rdf.Type.toSmartIri))
                   .orElseFail(BadRequestException(s"No rdf:type specified"))
      _ <- ZIO.when(rdfType != OntologyConstants.Owl.Class.toSmartIri) {
             ZIO.fail(BadRequestException(s"Invalid rdf:type for property: $rdfType"))
           }

      // Check that cardinalities were submitted.
      _ <- ZIO.when(internalClassDef.directCardinalities.isEmpty) {
             ZIO.fail(BadRequestException("No cardinalities specified"))
           }

      // Check that the class exists, that it's a Knora resource class, and that the submitted cardinalities aren't for properties that already have cardinalities
      // directly defined on the class.
      ontology = cacheData.ontologies(internalOntologyIri)
      existingReadClassInfo <-
        ZIO
          .fromOption(ontology.classes.get(internalClassIri))
          .orElseFail(
            BadRequestException(s"Class ${addCardinalitiesRequest.classInfoContent.classIri} does not exist"),
          )

      existingClassDef: ClassInfoContentV2 = existingReadClassInfo.entityInfoContent

      redundantCardinalities = existingClassDef.directCardinalities.keySet
                                 .intersect(internalClassDef.directCardinalities.keySet)

      _ <- ZIO.when(redundantCardinalities.nonEmpty) {
             val msg =
               s"The cardinalities of ${addCardinalitiesRequest.classInfoContent.classIri} already include the following property or properties: ${redundantCardinalities
                   .mkString(", ")}"
             ZIO.fail(BadRequestException(msg))
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
                     s"Cardinality ${cardinality.toString} for $propIri cannot be added to class ${addCardinalitiesRequest.classInfoContent.classIri}, because it is used in data",
                   ),
                 )
                 .whenZIO(iriService.isClassUsedInData(internalClassIri))
             case None => ZIO.unit
           }

      // Make an updated class definition.
      newInternalClassDef = existingClassDef.copy(
                              directCardinalities =
                                existingClassDef.directCardinalities ++ internalClassDef.directCardinalities,
                            )

      // Check that the new cardinalities are valid, and add any inherited cardinalities.

      allBaseClassIrisWithoutInternal = newInternalClassDef.subClassOf.toSeq.flatMap { baseClassIri =>
                                          cacheData.classToSuperClassLookup.getOrElse(
                                            baseClassIri,
                                            Seq.empty[SmartIri],
                                          )
                                        }

      allBaseClassIris = internalClassIri +: allBaseClassIrisWithoutInternal
      existingLinkPropsToKeep: Set[SmartIri] =
        existingReadClassInfo.entityInfoContent.directCardinalities.keySet
          .flatMap(p => cacheData.ontologies(p.getOntologyFromEntity).properties.get(p))
          .filter(_.isLinkProp)
          .map(_.entityInfoContent.propertyIri)

      cardinalityCheckResult <- OntologyHelpers
                                  .checkCardinalitiesBeforeAddingAndIfNecessaryAddLinkValueProperties(
                                    internalClassDef = newInternalClassDef,
                                    allBaseClassIris = allBaseClassIris.toSet,
                                    cacheData = cacheData,
                                    existingLinkPropsToKeep = existingLinkPropsToKeep,
                                  )
                                  .toZIO
      (newInternalClassDefWithLinkValueProps, _) = cardinalityCheckResult

      // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
      _ <- ZIO.attempt(
             OntologyCache.checkOntologyReferencesInClassDef(
               cacheData,
               newInternalClassDefWithLinkValueProps,
               (msg: String) => throw BadRequestException(msg),
             ),
           )

      // Add the cardinalities to the class definition in the triplestore.
      currentTime = Instant.now
      cardinalitiesToAdd =
        newInternalClassDefWithLinkValueProps.directCardinalities -- existingClassDef.directCardinalities.keySet

      updateSparql = sparql.v2.txt.addCardinalitiesToClass(
                       ontologyNamedGraphIri = internalOntologyIri,
                       ontologyIri = internalOntologyIri,
                       classIri = internalClassIri,
                       cardinalitiesToAdd = cardinalitiesToAdd,
                       lastModificationDate = addCardinalitiesRequest.lastModificationDate,
                       currentTime = currentTime,
                     )
      _ <- save(Update(updateSparql))
      // Read the data back from the cache.
      response <- ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(
                    classIris = Set(internalClassIri),
                    allLanguages = true,
                    requestingUser = addCardinalitiesRequest.requestingUser,
                  )
    } yield response
    IriLocker.runWithIriLock(addCardinalitiesRequest.apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(task)
  }

  /**
   * Replace cardinalities of a particular class.
   *
   * Fails if any of the new cardinalities is not consistent with the ontology or if persistent data is not compatible.
   *
   * @param request the [[ReplaceClassCardinalitiesRequestV2]] defining the cardinalities.
   * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
   */
  def replaceClassCardinalities(request: ReplaceClassCardinalitiesRequestV2): Task[ReadOntologyV2] = {
    val task = for {
      newModel   <- makeUpdatedClassModel(request)
      validModel <- checkLastModificationDateAndCanCardinalitiesBeSet(request, newModel)
      response   <- replaceClassCardinalitiesInPersistence(request, validModel)
    } yield response
    val classIriExternal    = request.classInfoContent.classIri
    val ontologyIriExternal = classIriExternal.getOntologyFromEntity
    for {
      _ <- ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(
             ontologyIriExternal,
             classIriExternal,
             request.requestingUser,
           )
      response <- IriLocker.runWithIriLock(request.apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(task)
    } yield response
  }

  // Make an updated class definition.
  // Check that the new cardinalities are valid, and don't add any inherited cardinalities.
  // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
  private def makeUpdatedClassModel(request: ReplaceClassCardinalitiesRequestV2): Task[ReadClassInfoV2] =
    for {
      newClassInfo       <- checkRdfTypeOfClassIsClass(request.classInfoContent.toOntologySchema(InternalSchema))
      classIriExternal    = newClassInfo.classIri
      classIri            = classIriExternal.toOntologySchema(InternalSchema)
      ontologyIriExternal = classIri.getOntologyFromEntity
      cacheData          <- ontologyCache.getCacheData
      oldClassInfo <-
        ontologyRepo
          .findClassBy(classIri.toInternalIri)
          .flatMap(ZIO.fromOption(_))
          .mapBoth(_ => BadRequestException(s"Class $ontologyIriExternal does not exist"), _.entityInfoContent)

      // Check that the new cardinalities are valid, and don't add any inherited cardinalities.
      newInternalClassDef = oldClassInfo.copy(directCardinalities = newClassInfo.directCardinalities)
      allBaseClassIrisWithoutInternal = newInternalClassDef.subClassOf.toSeq.flatMap { baseClassIri =>
                                          cacheData.classToSuperClassLookup.getOrElse(
                                            baseClassIri,
                                            Seq.empty[SmartIri],
                                          )
                                        }

      allBaseClassIris = classIri +: allBaseClassIrisWithoutInternal

      cardinalityCheckResult <- OntologyHelpers
                                  .checkCardinalitiesBeforeAddingAndIfNecessaryAddLinkValueProperties(
                                    newInternalClassDef,
                                    allBaseClassIris.toSet,
                                    cacheData,
                                  )
                                  .toZIO
      (newInternalClassDefWithLinkValueProps, cardinalitiesForClassWithInheritance) = cardinalityCheckResult

      // Check that the class definition doesn't refer to any non-shared ontologies in other projects.
      _ <- ZIO.attempt(
             OntologyCache.checkOntologyReferencesInClassDef(
               cacheData,
               newInternalClassDefWithLinkValueProps,
               (msg: String) => throw BadRequestException(msg),
             ),
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
      fileValueProperties = fileValueProperties,
    )

  private def checkRdfTypeOfClassIsClass(classInfo: ClassInfoContentV2): Task[ClassInfoContentV2] =
    for {
      rdfType <- ZIO
                   .fromOption(classInfo.getIriObject(OntologyConstants.Rdf.Type.toSmartIri))
                   .orElseFail(BadRequestException(s"No rdf:type specified"))
      _ <- ZIO.when(rdfType != OntologyConstants.Owl.Class.toSmartIri) {
             ZIO.fail(BadRequestException(s"Invalid rdf:type of property: $rdfType."))
           }
    } yield classInfo

  private def checkLastModificationDateAndCanCardinalitiesBeSet(
    request: ReplaceClassCardinalitiesRequestV2,
    newModel: ReadClassInfoV2,
  ): Task[ReadClassInfoV2] = {
    val classIriExternal     = request.classInfoContent.classIri
    val classIri             = classIriExternal.toOntologySchema(InternalSchema)
    val ontologyIri          = classIri.getOntologyFromEntity
    val lastModificationDate = request.lastModificationDate
    for {
      _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDate(ontologyIri, lastModificationDate)
      _ <- checkCanCardinalitiesBeSet(newModel.entityInfoContent).mapError(f => BadRequestException(f.mkString(" ")))
    } yield newModel
  }

  private def checkCanCardinalitiesBeSet(
    newModel: ClassInfoContentV2,
  ): IO[List[CanSetCardinalityCheckResult.Failure], Unit] = {
    val classIri = newModel.classIri.toInternalIri
    val cardinalitiesToCheck: List[(InternalIri, Cardinality)] =
      newModel.directCardinalities.toList.map { case (p, c) => (p.toInternalIri, c.cardinality) }
    for {
      resultsForEachCardinalityChecked <-
        ZIO.foreach(cardinalitiesToCheck) { case (p, c) => cardinalityService.canSetCardinality(classIri, p, c) }.orDie
      errors =
        resultsForEachCardinalityChecked.foldLeft(List.empty)(
          (
            fails: List[CanSetCardinalityCheckResult.Failure],
            nextResult: Either[List[CanSetCardinalityCheckResult.Failure], CanSetCardinalityCheckResult.Success.type],
          ) => nextResult.fold(fails ::: _, _ => fails),
        )
      aggregatedResultMsg <- errors match {
                               case _ if errors.isEmpty => ZIO.unit
                               case errors              => ZIO.fail(errors)
                             }
    } yield aggregatedResultMsg
  }

  private def replaceClassCardinalitiesInPersistence(
    request: ReplaceClassCardinalitiesRequestV2,
    newReadClassInfo: ReadClassInfoV2,
  ): Task[ReadOntologyV2] = {
    val timeOfUpdate = Instant.now()
    val classIri     = request.classInfoContent.classIri.toOntologySchema(InternalSchema)
    for {
      _ <- replaceClassCardinalitiesInTripleStore(request, newReadClassInfo, timeOfUpdate)
      // Return the response with the new data from the cache
      response <- ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(
                    classIris = Set(classIri),
                    allLanguages = true,
                    requestingUser = request.requestingUser,
                  )
    } yield response
  }

  private def replaceClassCardinalitiesInTripleStore(
    request: ReplaceClassCardinalitiesRequestV2,
    newReadClassInfo: ReadClassInfoV2,
    timeOfUpdate: Instant,
  ): Task[Unit] = {
    val classIri    = request.classInfoContent.classIri.toOntologySchema(InternalSchema)
    val ontologyIri = classIri.getOntologyFromEntity
    val updateSparql = sparql.v2.txt.replaceClassCardinalities(
      ontologyNamedGraphIri = ontologyIri,
      ontologyIri = ontologyIri,
      classIri = classIri,
      newCardinalities = newReadClassInfo.entityInfoContent.directCardinalities,
      lastModificationDate = request.lastModificationDate,
      currentTime = timeOfUpdate,
    )
    save(Update(updateSparql)).unit
  }

  /**
   * FIXME(DSP-1856): Only works if a single cardinality is supplied.
   * Checks if cardinalities can be removed from a class.
   *
   * @param canDeleteCardinalitiesFromClassRequest the request to remove cardinalities.
   * @return a [[CanDoResponseV2]] indicating whether a class's cardinalities can be deleted.
   */
  def canDeleteCardinalitiesFromClass(
    canDeleteCardinalitiesFromClassRequest: CanDeleteCardinalitiesFromClassRequestV2,
  ): Task[CanDoResponseV2] =
    for {
      requestingUser <- ZIO.succeed(canDeleteCardinalitiesFromClassRequest.requestingUser)

      externalClassIri    = canDeleteCardinalitiesFromClassRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <-
        ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <-
        IriLocker.runWithIriLock(canDeleteCardinalitiesFromClassRequest.apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(
          cardinalityHandler.canDeleteCardinalitiesFromClass(
            deleteCardinalitiesFromClassRequest = canDeleteCardinalitiesFromClassRequest,
            internalClassIri = internalClassIri,
            internalOntologyIri = internalOntologyIri,
          ),
        )
    } yield taskResult

  /**
   * FIXME(DSP-1856): Only works if a single cardinality is supplied.
   * Removes cardinalities (from class to properties) from class if properties are not used inside data.
   *
   * @param deleteCardinalitiesFromClassRequest the request to remove cardinalities.
   * @return a [[ReadOntologyV2]] in the internal schema, containing the new class definition.
   */
  def deleteCardinalitiesFromClass(
    deleteCardinalitiesFromClassRequest: DeleteCardinalitiesFromClassRequestV2,
  ): Task[ReadOntologyV2] =
    for {
      requestingUser <- ZIO.succeed(deleteCardinalitiesFromClassRequest.requestingUser)

      externalClassIri    = deleteCardinalitiesFromClassRequest.classInfoContent.classIri
      externalOntologyIri = externalClassIri.getOntologyFromEntity

      _ <-
        ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      internalClassIri    = externalClassIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(deleteCardinalitiesFromClassRequest.apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(
                      cardinalityHandler.deleteCardinalitiesFromClass(
                        deleteCardinalitiesFromClassRequest = deleteCardinalitiesFromClassRequest,
                        internalClassIri = internalClassIri,
                        internalOntologyIri = internalOntologyIri,
                      ),
                    )
    } yield taskResult

  /**
   * Checks whether a class can be deleted.
   *
   * @param classIri the IRI of the class to be deleted.
   * @param requestingUser the User making the request.
   * @return a [[CanDoResponseV2]].
   */
  def canDeleteClass(classIri: ResourceClassIri, requestingUser: User): Task[CanDoResponseV2] = for {
    _ <- ontologyRepo
           .findClassBy(classIri)
           .someOrFail(BadRequestException(s"Class $classIri does not exist"))
    userCanUpdateOntology <- ontologyCacheHelpers.canUserUpdateOntology(classIri.ontologyIri, requestingUser)
    classIsUsed           <- iriService.isEntityUsed(classIri.toInternalSchema)
  } yield CanDoResponseV2.of(userCanUpdateOntology && !classIsUsed)

  /**
   * Deletes a class.
   *
   * @param deleteClassRequest the request to delete the class.
   * @return a [[SuccessResponseV2]].
   */
  private def deleteClass(deleteClassRequest: DeleteClassRequestV2): Task[ReadOntologyMetadataV2] =
    deleteClass(
      ResourceClassIri.unsafeFrom(deleteClassRequest.classIri),
      deleteClassRequest.lastModificationDate,
      deleteClassRequest.apiRequestID,
      deleteClassRequest.requestingUser,
    )

  def deleteClass(
    classIri: ResourceClassIri,
    lastModificationDate: Instant,
    apiRequestID: UUID,
    requestingUser: User,
  ): Task[ReadOntologyMetadataV2] = {
    val internalClassIri    = classIri.toInternalSchema
    val internalOntologyIri = internalClassIri.getOntologyFromEntity
    val externalClassIri    = classIri.toComplexSchema
    val externalOntologyIri = externalClassIri.getOntologyFromEntity

    val deleteClassTask = for {
      _ <- ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri,
             externalClassIri,
             requestingUser,
           )

      // Check that the ontology exists and has not been updated by another user since the client last read it.
      _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDate(internalOntologyIri, lastModificationDate)

      // Check that the class exists.
      ontology <- ontologyRepo
                    .findById(classIri.ontologyIri)
                    .someOrFail(BadRequestException(s"Ontology ${classIri.ontologyIri} does not exist"))
      _ <- ZIO
             .fromOption(ontology.classes.get(classIri.toInternalSchema))
             .orElseFail(BadRequestException(s"Class $classIri does not exist"))

      // Check that the class isn't used in data or ontologies.
      _ <- ZIO
             .whenZIO(iriService.isEntityUsed(internalClassIri)) {
               val msg =
                 s"Class $classIri cannot be deleted, because it is used in data or ontologies"
               ZIO.fail(BadRequestException(msg))
             }

      // Delete the class from the triplestore.

      currentTime <- Clock.instant

      updateSparql = sparql.v2.txt.deleteClass(
                       ontologyNamedGraphIri = internalOntologyIri,
                       ontologyIri = internalOntologyIri,
                       classIri = internalClassIri,
                       lastModificationDate = lastModificationDate,
                       currentTime = currentTime,
                     )
      _ <- save(Update(updateSparql))
      updatedOntology = ontology.copy(
                          ontologyMetadata = ontology.ontologyMetadata.copy(
                            lastModificationDate = Some(currentTime),
                          ),
                          classes = ontology.classes - internalClassIri,
                        )
    } yield ReadOntologyMetadataV2(Set(updatedOntology.ontologyMetadata))

    IriLocker.runWithIriLock(apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(deleteClassTask)
  }

  /**
   * Checks whether a property can be deleted.
   *
   * @param propertyIri    the IRI of the property to be deleted.
   * @param requestingUser the user making the request.
   *
   * @return a [[CanDoResponseV2]] indicating whether the property can be deleted.
   */
  def canDeleteProperty(propertyIri: PropertyIri, requestingUser: User): Task[CanDoResponseV2] = {
    val externalPropertyIri = propertyIri.toComplexSchema
    val internalPropertyIri = propertyIri.toInternalSchema
    val externalOntologyIri = externalPropertyIri.getOntologyFromEntity
    val internalOntologyIri = internalPropertyIri.getOntologyFromEntity
    for {
      property <- ontologyRepo
                    .findProperty(propertyIri)
                    .someOrFail(BadRequestException(s"Ontology $externalOntologyIri does not exist"))
      _ <- ZIO.when(property.isLinkValueProp)(ZIO.fail {
             val msg =
               s"A link value property cannot be deleted directly; check the corresponding link property instead"
             BadRequestException(msg)
           })
      userCanUpdateOntology <- ontologyCacheHelpers.canUserUpdateOntology(internalOntologyIri, requestingUser)
      propertyIsUsed        <- iriService.isEntityUsed(internalPropertyIri)
    } yield CanDoResponseV2.of(userCanUpdateOntology && !propertyIsUsed)
  }

  /**
   * Deletes a property. If the property is a link property, the corresponding link value property is also deleted.
   *
   * @param propertyIri          the IRI of the property to be deleted.
   * @param lastModificationDate the ontology's last modification date.
   * @param apiRequestID         the ID of the API request.
   * @param requestingUser       the user making the request.
   * @return a [[ReadOntologyMetadataV2]].
   */
  def deleteProperty(
    propertyIri: PropertyIri,
    lastModificationDate: Instant,
    apiRequestID: UUID,
    requestingUser: User,
  ): Task[ReadOntologyMetadataV2] = {
    val internalPropertyIri = propertyIri.toInternalSchema
    val externalPropertyIri = propertyIri.toComplexSchema
    val externalOntologyIri = externalPropertyIri.getOntologyFromEntity
    val internalOntologyIri = externalOntologyIri.toInternalSchema

    val deleteTask: Task[ReadOntologyMetadataV2] = for {
      _ <- ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri,
             externalPropertyIri,
             requestingUser,
           )
      cacheData <- ontologyCache.getCacheData
      _         <- ontologyTriplestoreHelpers.checkOntologyLastModificationDate(internalOntologyIri, lastModificationDate)
      // Check that the property exists.
      ontology = cacheData.ontologies(internalOntologyIri)
      propertyDef <-
        ZIO
          .fromOption(ontology.properties.get(internalPropertyIri))
          .orElseFail(BadRequestException(s"Property $propertyIri does not exist"))

      _ <- ZIO.when(propertyDef.isLinkValueProp) {
             val msg =
               s"A link value property cannot be deleted directly; delete the corresponding link property instead"
             ZIO.fail(BadRequestException(msg))
           }

      maybeInternalLinkValuePropertyIri: Option[SmartIri] =
        if (propertyDef.isLinkProp) {
          Some(internalPropertyIri.fromLinkPropToLinkValueProp)
        } else {
          None
        }

      _ <- // Check that the property isn't used in data or ontologies.
        ZIO.fail {
          BadRequestException(s"Property $propertyIri cannot be deleted, because it is used in data or ontologies")
        }.whenZIO(iriService.isEntityUsed(internalPropertyIri))

      _ <- maybeInternalLinkValuePropertyIri match {
             case Some(internalLinkValuePropertyIri) =>
               ZIO
                 .fail(
                   BadRequestException(
                     s"Property $propertyIri cannot be deleted, because the corresponding link value property, ${internalLinkValuePropertyIri
                         .toOntologySchema(ApiV2Complex)}, is used in data or ontologies",
                   ),
                 )
                 .whenZIO(iriService.isEntityUsed(internalLinkValuePropertyIri))
             case None => ZIO.unit
           }

      // Delete the property from the triplestore.
      currentTime <- Clock.instant
      updateSparql = sparql.v2.txt.deleteProperty(
                       ontologyNamedGraphIri = internalOntologyIri,
                       ontologyIri = internalOntologyIri,
                       propertyIri = internalPropertyIri,
                       maybeLinkValuePropertyIri = maybeInternalLinkValuePropertyIri,
                       lastModificationDate = lastModificationDate,
                       currentTime = currentTime,
                     )
      _ <- save(Update(updateSparql))

      propertiesToRemoveFromCache = Set(internalPropertyIri) ++ maybeInternalLinkValuePropertyIri
      updatedOntology =
        ontology.copy(
          ontologyMetadata = ontology.ontologyMetadata.copy(
            lastModificationDate = Some(currentTime),
          ),
          properties = ontology.properties -- propertiesToRemoveFromCache,
        )
    } yield ReadOntologyMetadataV2(Set(updatedOntology.ontologyMetadata))
    IriLocker.runWithIriLock(apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(deleteTask)
  }

  /**
   * Checks whether an ontology can be deleted.
   *
   * @param ontologyIri the ontology to delete.
   * @param requestingUser the user making the request.
   * @return a [[CanDoResponseV2]] indicating whether an ontology can be deleted.
   */
  def canDeleteOntology(ontologyIri: OntologyIri, requestingUser: User): Task[CanDoResponseV2] = for {
    ontology <- ontologyRepo
                  .findById(ontologyIri)
                  .someOrFail(BadRequestException(s"Ontology ${ontologyIri.toComplexSchema.toIri} does not exist"))
    userCanUpdateOntology <- ontologyCacheHelpers.canUserUpdateOntology(ontologyIri.toInternalSchema, requestingUser)
    subjectsUsingOntology <- ontologyTriplestoreHelpers.getSubjectsUsingOntology(ontology)
  } yield CanDoResponseV2.of(userCanUpdateOntology && subjectsUsingOntology.isEmpty)

  def deleteOntology(
    ontologyIri: OntologyIri,
    lastModificationDate: Instant,
    apiRequestID: UUID,
  ): Task[SuccessResponseV2] = {
    val deleteTask: Task[SuccessResponseV2] =
      for {
        _                     <- OntologyHelpers.checkOntologyIriForUpdate(ontologyIri)
        ontology              <- getOntologyOrFailNotFound(ontologyIri)
        _                     <- ontologyTriplestoreHelpers.checkOntologyLastModificationDate(ontologyIri, lastModificationDate)
        subjectsUsingOntology <- ontologyTriplestoreHelpers.getSubjectsUsingOntology(ontology)
        _ <- ZIO.when(subjectsUsingOntology.nonEmpty) {
               val sortedSubjects = subjectsUsingOntology.map(s => "<" + s + ">").toVector.sorted.mkString(", ")
               val msg =
                 s"Ontology ${ontologyIri.toComplexSchema} cannot be deleted, because of subjects that refer to it: $sortedSubjects"
               ZIO.fail(BadRequestException(msg))
             }
        _ <- save(Update(sparql.v2.txt.deleteOntology(ontologyIri.toInternalSchema)))
      } yield SuccessResponseV2(s"Ontology ${ontologyIri.toComplexSchema} has been deleted")
    IriLocker.runWithIriLock(apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(deleteTask)
  }

  /**
   * Creates a property in an existing ontology.
   *
   * @param req the request to create the property.
   * @return a [[ReadOntologyV2]] in the internal schema, the containing the definition of the new property.
   */
  def createProperty(req: CreatePropertyRequestV2): Task[ReadOntologyV2] =
    createProperty(req.propertyInfoContent, req.lastModificationDate, req.apiRequestID, req.requestingUser)

  def createProperty(
    propertyInfoContent: PropertyInfoContentV2,
    lastModificationDate: Instant,
    apiRequestID: UUID,
    requestingUser: User,
  ): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] = {
      val predicates = propertyInfoContent.predicates.values
      for {
        _ <- Validation
               .validate(
                 PredicateInfoV2.checkRequiredStringLiteralWithLanguageTag(Rdfs.Label, predicates),
                 PredicateInfoV2.checkOptionalStringLiteralWithLanguageTag(Rdfs.Comment, predicates),
               )
               .mapError(BadRequestException(_))
               .toZIO
        cacheData          <- ontologyCache.getCacheData
        internalPropertyDef = propertyInfoContent.toOntologySchema(InternalSchema)

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDate(
               internalOntologyIri,
               lastModificationDate,
             )

        // Check that the property's rdf:type is owl:ObjectProperty.
        rdfType <- ZIO
                     .fromOption(internalPropertyDef.getIriObject(OntologyConstants.Rdf.Type.toSmartIri))
                     .orElseFail(BadRequestException(s"No rdf:type specified"))
        _ <- ZIO.when(rdfType != OntologyConstants.Owl.ObjectProperty.toSmartIri) {
               ZIO.fail(BadRequestException(s"Invalid rdf:type for property: $rdfType"))
             }

        // Check that the property doesn't exist yet.
        ontology = cacheData.ontologies(internalOntologyIri)
        _ <- ZIO.when(ontology.properties.contains(internalPropertyIri)) {
               val msg = s"Property ${propertyInfoContent.propertyIri} already exists"
               ZIO.fail(BadRequestException(msg))
             }

        // Check that the property's IRI isn't already used for something else.
        _ <- ZIO.when(
               ontology.classes.contains(internalPropertyIri) || ontology.individuals.contains(internalPropertyIri),
             ) {
               ZIO.fail(
                 BadRequestException(s"IRI ${propertyInfoContent.propertyIri} is already used"),
               )
             }

        // Check that the base properties that have Knora IRIs are defined as Knora resource properties.

        knoraSuperProperties = internalPropertyDef.subPropertyOf.filter(_.isKnoraInternalEntityIri)
        invalidSuperProperties = knoraSuperProperties.filterNot(baseProperty =>
                                   OntologyHelpers.isKnoraResourceProperty(
                                     baseProperty,
                                     cacheData,
                                   ) && baseProperty.toString != OntologyConstants.KnoraBase.ResourceProperty,
                                 )

        _ <- ZIO.when(invalidSuperProperties.nonEmpty) {
               val msg = s"One or more specified base properties are invalid: ${invalidSuperProperties.mkString(", ")}"
               ZIO.fail(BadRequestException(msg))
             }

        // Check for rdfs:subPropertyOf cycles.

        allKnoraSuperPropertyIrisWithoutSelf: Set[SmartIri] = knoraSuperProperties.flatMap { superPropertyIri =>
                                                                cacheData.subPropertyOfRelations.getOrElse(
                                                                  superPropertyIri,
                                                                  Set.empty[SmartIri],
                                                                )
                                                              }

        _ <- ZIO.when(allKnoraSuperPropertyIrisWithoutSelf.contains(internalPropertyIri)) {
               val msg =
                 s"Property ${propertyInfoContent.propertyIri} would have a cyclical rdfs:subPropertyOf"
               ZIO.fail(BadRequestException(msg))
             }

        // Check the property is a subproperty of knora-base:hasValue or knora-base:hasLinkTo, but not both.

        allKnoraSuperPropertyIris: Set[SmartIri] = allKnoraSuperPropertyIrisWithoutSelf + internalPropertyIri

        isValueProp     = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasValue.toSmartIri)
        isLinkProp      = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasLinkTo.toSmartIri)
        isLinkValueProp = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasLinkToValue.toSmartIri)
        isFileValueProp = allKnoraSuperPropertyIris.contains(OntologyConstants.KnoraBase.HasFileValue.toSmartIri)

        _ <- ZIO.when(!isValueProp && !isLinkProp) {
               val msg =
                 s"Property ${propertyInfoContent.propertyIri} would not be a subproperty of knora-api:hasValue or knora-api:hasLinkTo"
               ZIO.fail(BadRequestException(msg))
             }

        _ <- ZIO.when(isValueProp && isLinkProp) {
               val msg =
                 s"Property ${propertyInfoContent.propertyIri} would be a subproperty of both knora-api:hasValue and knora-api:hasLinkTo"
               ZIO.fail(BadRequestException(msg))
             }

        // Don't allow new file value properties to be created.
        _ <- ZIO.when(isFileValueProp)(ZIO.fail(BadRequestException("New file value properties cannot be created")))

        // Don't allow new link value properties to be created directly, because we do that automatically when creating a link property.
        _ <- ZIO.when(isLinkValueProp) {
               val msg = "New link value properties cannot be created directly. Create a link property instead."
               ZIO.fail(BadRequestException(msg))
             }

        // If we're creating a link property, make the definition of the corresponding link value property.
        maybeLinkValuePropertyDef <-
          if (isLinkProp) {
            val linkValuePropertyDef = OntologyHelpers.linkPropertyDefToLinkValuePropertyDef(internalPropertyDef)

            if (ontology.properties.contains(linkValuePropertyDef.propertyIri)) {
              ZIO.fail(BadRequestException(s"Link value property ${linkValuePropertyDef.propertyIri} already exists"))
            } else {
              ZIO.some(linkValuePropertyDef)
            }
          } else { ZIO.none }

        // Check that the subject class constraint, if provided, designates a Knora resource class that exists.
        maybeSubjectClassConstraint <-
          ZIO.attempt(
            internalPropertyDef.predicates
              .get(OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri)
              .map(_.requireIriObject(throw BadRequestException("Invalid knora-api:subjectType"))),
          )
        _ <- ZIO.foreachDiscard(maybeSubjectClassConstraint) { subjectClassConstraint =>
               ZIO.unless(OntologyHelpers.isKnoraInternalResourceClass(subjectClassConstraint, cacheData)) {
                 val msg = s"Invalid subject class constraint: ${subjectClassConstraint.toOntologySchema(ApiV2Complex)}"
                 ZIO.fail(BadRequestException(msg))
               }
             }

        // Check that the object class constraint designates an appropriate class that exists.

        objectClassConstraint <-
          ZIO
            .fromOption(internalPropertyDef.getIriObject(OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri))
            .orElseFail(BadRequestException(s"No knora-api:objectType specified"))

        // If this is a value property, ensure its object class constraint is not LinkValue or a file value class.
        _ <-
          ZIO.when(
            !isLinkProp &&
              (objectClassConstraint.toString == OntologyConstants.KnoraBase.LinkValue
                || OntologyConstants.KnoraBase.FileValueClasses.contains(objectClassConstraint.toString)),
          ) {
            val msg =
              s"Invalid object class constraint for value property: ${objectClassConstraint.toOntologySchema(ApiV2Complex)}"
            ZIO.fail(BadRequestException(msg))
          }

        // Check that the subject class, if provided, is a subclass of the subject classes of the base properties.
        _ <- ZIO.attempt(maybeSubjectClassConstraint match {
               case Some(subjectClassConstraint) =>
                 OntologyCache.checkPropertyConstraint(
                   cacheData = cacheData,
                   internalPropertyIri = internalPropertyIri,
                   constraintPredicateIri = OntologyConstants.KnoraBase.SubjectClassConstraint.toSmartIri,
                   constraintValueToBeChecked = subjectClassConstraint,
                   allSuperPropertyIris = allKnoraSuperPropertyIris,
                   errorSchema = ApiV2Complex,
                   errorFun = { (msg: String) => throw BadRequestException(msg) },
                 )

               case None => ()
             })

        // Check that the object class is a subclass of the object classes of the base properties.

        _ <- ZIO.attempt(
               OntologyCache.checkPropertyConstraint(
                 cacheData = cacheData,
                 internalPropertyIri = internalPropertyIri,
                 constraintPredicateIri = OntologyConstants.KnoraBase.ObjectClassConstraint.toSmartIri,
                 constraintValueToBeChecked = objectClassConstraint,
                 allSuperPropertyIris = allKnoraSuperPropertyIris,
                 errorSchema = ApiV2Complex,
                 errorFun = { (msg: String) => throw BadRequestException(msg) },
               ),
             )

        // Check that the property definition doesn't refer to any non-shared ontologies in other projects.
        _ <- ZIO.attempt(
               OntologyCache.checkOntologyReferencesInPropertyDef(
                 ontologyCacheData = cacheData,
                 propertyDef = internalPropertyDef,
                 errorFun = { (msg: String) => throw BadRequestException(msg) },
               ),
             )

        // Add the property (and the link value property if needed) to the triplestore.
        currentTime <- Clock.instant
        updateSparql = sparql.v2.txt.createProperty(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         propertyDef = internalPropertyDef,
                         maybeLinkValuePropertyDef = maybeLinkValuePropertyDef,
                         lastModificationDate = lastModificationDate,
                         currentTime = currentTime,
                       )
        _ <- save(Update(updateSparql))

        // Read the data back from the cache.
        response <- getPropertyDefinitionsFromOntologyV2(
                      propertyIris = Set(internalPropertyIri),
                      allLanguages = true,
                      requestingUser = requestingUser,
                    )
      } yield response
    }

    for {
      requestingUser <- ZIO.succeed(requestingUser)

      externalPropertyIri = propertyInfoContent.propertyIri
      externalOntologyIri = externalPropertyIri.getOntologyFromEntity

      _ <- ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri,
             externalPropertyIri,
             requestingUser,
           )

      internalPropertyIri = externalPropertyIri.toOntologySchema(InternalSchema)
      internalOntologyIri = externalOntologyIri.toOntologySchema(InternalSchema)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(
                      makeTaskFuture(internalPropertyIri, internalOntologyIri),
                    )
    } yield taskResult
  }

  /**
   * Changes the values of `salsah-gui:guiElement` and `salsah-gui:guiAttribute` in a property definition.
   *
   * @param changePropertyGuiElementRequest the request to change the property's GUI element and GUI attribute.
   * @return a [[ReadOntologyV2]] containing the modified property definition.
   */
  def changePropertyGuiElement(
    changePropertyGuiElementRequest: ChangePropertyGuiElementRequest,
  ): Task[ReadOntologyV2] = {
    def makeTaskFuture(internalPropertyIri: SmartIri, internalOntologyIri: SmartIri): Task[ReadOntologyV2] = for {
      cacheData <- ontologyCache.getCacheData

      ontology = cacheData.ontologies(internalOntologyIri)

      currentReadPropertyInfo <-
        ZIO
          .fromOption(ontology.properties.get(internalPropertyIri))
          .orElseFail(NotFoundException(s"Property ${changePropertyGuiElementRequest.propertyIri} not found"))

      // Check that the ontology exists and has not been updated by another user since the client last read it.
      _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDate(
             internalOntologyIri,
             changePropertyGuiElementRequest.lastModificationDate,
           )

      // If this is a link property, also change the GUI element and attribute of the corresponding link value property.
      maybeCurrentLinkValueReadPropertyInfo <-
        if (currentReadPropertyInfo.isLinkProp) {
          val linkValuePropertyIri = internalPropertyIri.fromLinkPropToLinkValueProp
          ZIO
            .fromOption(ontology.properties.get(linkValuePropertyIri))
            .mapBoth(
              _ => InconsistentRepositoryDataException(s"Link value property $linkValuePropertyIri not found"),
              Some(_),
            )
        } else {
          ZIO.none
        }

      // Do the update.
      currentTime <- Clock.instant
      newGuiElementIri =
        changePropertyGuiElementRequest.newGuiObject.guiElement.map(guiElement => guiElement.value.toSmartIri)
      newGuiAttributeIris =
        changePropertyGuiElementRequest.newGuiObject.guiAttributes.map(guiAttribute => guiAttribute.value)
      updateSparql = sparql.v2.txt.changePropertyGuiElement(
                       ontologyNamedGraphIri = internalOntologyIri,
                       ontologyIri = internalOntologyIri,
                       propertyIri = internalPropertyIri,
                       maybeLinkValuePropertyIri =
                         maybeCurrentLinkValueReadPropertyInfo.map(_.entityInfoContent.propertyIri),
                       maybeNewGuiElement = newGuiElementIri,
                       newGuiAttributes = newGuiAttributeIris,
                       lastModificationDate = changePropertyGuiElementRequest.lastModificationDate,
                       currentTime = currentTime,
                     )
      _ <- save(Update(updateSparql))

      // Read the data back from the cache.
      response <- getPropertyDefinitionsFromOntologyV2(
                    propertyIris = Set(internalPropertyIri),
                    allLanguages = true,
                    requestingUser = changePropertyGuiElementRequest.requestingUser,
                  )
    } yield response

    for {
      requestingUser <- ZIO.succeed(changePropertyGuiElementRequest.requestingUser)

      externalPropertyIri = changePropertyGuiElementRequest.propertyIri.smartIri
      externalOntologyIri = externalPropertyIri.getOntologyFromEntity

      _ <- ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(
             externalOntologyIri,
             externalPropertyIri,
             requestingUser,
           )

      internalPropertyIri = externalPropertyIri.toInternalSchema
      internalOntologyIri = externalOntologyIri.toInternalSchema

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(changePropertyGuiElementRequest.apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(
                      makeTaskFuture(internalPropertyIri, internalOntologyIri),
                    )
    } yield taskResult
  }

  /**
   * Changes the values of `rdfs:label` or `rdfs:comment` in a property definition.
   *
   * @param changeReq the request to change the property's labels or comments.
   * @return a [[ReadOntologyV2]] containing the modified property definition.
   */
  def changePropertyLabelsOrComments(changeReq: ChangePropertyLabelsOrCommentsRequestV2): Task[ReadOntologyV2] = {
    val ontologyIri = changeReq.propertyIri.ontologyIri
    val task = for {
      ontology <- getOntologyOrFailNotFound(ontologyIri)
      _ <- ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(
             ontologyIri.toComplexSchema,
             changeReq.propertyIri.toComplexSchema,
             changeReq.requestingUser,
           )

      currentReadPropertyInfo <-
        ZIO
          .fromOption(ontology.properties.get(changeReq.propertyIri.toInternalSchema))
          .orElseFail(NotFoundException(s"Property ${changeReq.propertyIri} not found"))

      // Check that the ontology exists and has not been updated by another user since the client last read it.
      _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDate(
             changeReq.propertyIri.ontologyIri,
             changeReq.lastModificationDate,
           )

      // If this is a link property, also change the labels/comments of the corresponding link value property.
      maybeCurrentLinkValueReadPropertyInfo <-
        if (currentReadPropertyInfo.isLinkProp) {
          val linkValuePropertyIri = changeReq.propertyIri.toInternalSchema.fromLinkPropToLinkValueProp
          ZIO
            .fromOption(ontology.properties.get(linkValuePropertyIri))
            .mapBoth(
              _ => InconsistentRepositoryDataException(s"Link value property $linkValuePropertyIri not found"),
              Some(_),
            )
        } else {
          ZIO.none
        }

      // Do the update.
      currentTime <- Clock.instant
      updateSparql = sparql.v2.txt.changePropertyLabelsOrComments(
                       ontologyNamedGraphIri = ontologyIri.toInternalSchema,
                       ontologyIri = ontologyIri.toInternalSchema,
                       propertyIri = changeReq.propertyIri.toInternalSchema,
                       maybeLinkValuePropertyIri =
                         maybeCurrentLinkValueReadPropertyInfo.map(_.entityInfoContent.propertyIri),
                       predicateToUpdate = changeReq.predicateToUpdate,
                       newObjects = changeReq.newObjects,
                       lastModificationDate = changeReq.lastModificationDate,
                       currentTime = currentTime,
                     )
      _ <- save(Update(updateSparql))

      // Read the data back from the cache.
      response <- getPropertyDefinitionsFromOntologyV2(
                    propertyIris = Set(changeReq.propertyIri.toInternalSchema),
                    allLanguages = true,
                    requestingUser = changeReq.requestingUser,
                  )
    } yield response

    IriLocker.runWithIriLock(changeReq.apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(task)
  }

  /**
   * Changes the values of `rdfs:label` or `rdfs:comment` in a class definition.
   *
   * @param req the request to change the class's labels or comments.
   * @return a [[ReadOntologyV2]] containing the modified class definition.
   */
  def changeClassLabelsOrComments(req: ChangeClassLabelsOrCommentsRequestV2): Task[ReadOntologyV2] = {
    val internalClassIri    = req.classIri.toInternalSchema
    val internalOntologyIri = req.classIri.ontologyIri.toInternalSchema
    val externalClassIri    = req.classIri.toComplexSchema
    val externalOntologyIri = req.classIri.ontologyIri.toComplexSchema

    val changeTask: Task[ReadOntologyV2] =
      for {
        cacheData <- ontologyCache.getCacheData

        ontology = cacheData.ontologies(internalOntologyIri)
        _ <- ZIO
               .fromOption(ontology.classes.get(internalClassIri))
               .orElseFail(NotFoundException(s"Class ${req.classIri} not found"))

        // Check that the ontology exists and has not been updated by another user since the client last read it.
        _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDate(
               internalOntologyIri,
               req.lastModificationDate,
             )

        // Do the update.

        currentTime <- Clock.instant

        updateSparql = sparql.v2.txt.changeClassLabelsOrComments(
                         ontologyNamedGraphIri = internalOntologyIri,
                         ontologyIri = internalOntologyIri,
                         classIri = internalClassIri,
                         predicateToUpdate = req.predicateToUpdate,
                         newObjects = req.newObjects,
                         lastModificationDate = req.lastModificationDate,
                         currentTime = currentTime,
                       )
        _ <- save(Update(updateSparql))

        // Read the data back from the cache.
        response <- ontologyCacheHelpers.getClassDefinitionsFromOntologyV2(
                      classIris = Set(internalClassIri),
                      allLanguages = true,
                      requestingUser = req.requestingUser,
                    )
      } yield response

    for {
      requestingUser <- ZIO.succeed(req.requestingUser)

      _ <-
        ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(externalOntologyIri, externalClassIri, requestingUser)

      // Do the remaining pre-update checks and the update while holding a global ontology cache lock.
      taskResult <- IriLocker.runWithIriLock(req.apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(changeTask)
    } yield taskResult
  }

  def deletePropertyComment(
    propertyIri: PropertyIri,
    lastModificationDate: Instant,
    apiRequestID: UUID,
    requestingUser: User,
  ): Task[ReadOntologyV2] = {
    val ontologyIri = propertyIri.ontologyIri
    def deleteCommentTask(propertyToUpdate: ReadPropertyInfoV2) = for {
      currentTime <- Clock.instant
      updateSparql = sparql.v2.txt.deletePropertyComment(
                       ontologyNamedGraphIri = ontologyIri.toInternalSchema,
                       ontologyIri = ontologyIri.toInternalSchema,
                       propertyIri = propertyToUpdate.propertyIri.toInternalSchema,
                       maybeLinkValuePropertyIri = propertyToUpdate.linkValueProperty.map(_.toInternalSchema),
                       lastModificationDate = lastModificationDate,
                       currentTime = currentTime,
                     )
      _ <- save(Update(updateSparql))
    } yield ()

    for {
      _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDate(ontologyIri, lastModificationDate)
      _ <- ontologyCacheHelpers.checkOntologyAndPropertyIrisForUpdate(ontologyIri, propertyIri, requestingUser)
      propertyToUpdate <- ontologyRepo
                            .findProperty(propertyIri)
                            .someOrFail(NotFoundException(s"Ontology ${ontologyIri.toComplexSchema.toIri} not found"))
      hasComment = propertyToUpdate.entityInfoContent.predicates.contains(OntologyConstants.Rdfs.Comment.toSmartIri)
      _ <- IriLocker
             .runWithIriLock(apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(deleteCommentTask(propertyToUpdate))
             .when(hasComment)
      response <- getPropertiesFromOntologyV2(Set(propertyIri), allLanguages = true, requestingUser = requestingUser)
    } yield response
  }

  /**
   * Delete the `rdfs:comment` in a class definition.
   *
   * @param classIri             the IRI of the class to be modified.
   * @param lastModificationDate the last modification date of the ontology.
   * @param apiRequestID         the api request ID.
   * @param requestingUser       the user making the request.
   *
   * @return a [[ReadOntologyV2]] containing the modified class definition.
   */
  def deleteClassComment(
    classIri: ResourceClassIri,
    lastModificationDate: Instant,
    apiRequestID: UUID,
    requestingUser: User,
  ): Task[ReadOntologyV2] = {
    val ontologyIri = classIri.ontologyIri
    val deleteCommentTask = for {
      currentTime <- Clock.instant
      updateSparql = sparql.v2.txt
                       .deleteClassComment(
                         ontologyNamedGraphIri = ontologyIri.toInternalSchema,
                         ontologyIri = ontologyIri.toInternalSchema,
                         classIri = classIri.toInternalSchema,
                         lastModificationDate = lastModificationDate,
                         currentTime = currentTime,
                       )
      _ <- save(Update(updateSparql))
    } yield ()

    for {
      _ <- ontologyTriplestoreHelpers.checkOntologyLastModificationDate(ontologyIri, lastModificationDate)
      _ <- ontologyCacheHelpers.checkOntologyAndEntityIrisForUpdate(
             ontologyIri.toComplexSchema,
             classIri.toComplexSchema,
             requestingUser,
           )
      classToUpdate <- ontologyRepo
                         .findClassBy(classIri)
                         .someOrFail(NotFoundException(s"Class ${classIri.toComplexSchema} not found"))
      hasComment = classToUpdate.entityInfoContent.predicates.contains(OntologyConstants.Rdfs.Comment.toSmartIri)
      _         <- IriLocker.runWithIriLock(apiRequestID, ONTOLOGY_CACHE_LOCK_IRI)(deleteCommentTask).when(hasComment)
      response  <- ontologyCacheHelpers.getClassAsReadOntologyV2(classIri, allLanguages = true, requestingUser)
    } yield response
  }
}

object OntologyResponderV2 {
  val layer: URLayer[
    AppConfig & CardinalityHandler & CardinalityService & IriService & KnoraProjectService & MessageRelay &
      OntologyCache & OntologyTriplestoreHelpers & OntologyCacheHelpers & OntologyRepo & StringFormatter &
      TriplestoreService,
    OntologyResponderV2,
  ] = ZLayer.fromZIO {
    for {
      ac       <- ZIO.service[AppConfig]
      ch       <- ZIO.service[CardinalityHandler]
      cs       <- ZIO.service[CardinalityService]
      is       <- ZIO.service[IriService]
      kr       <- ZIO.service[KnoraProjectService]
      oc       <- ZIO.service[OntologyCache]
      oth      <- ZIO.service[OntologyTriplestoreHelpers]
      och      <- ZIO.service[OntologyCacheHelpers]
      or       <- ZIO.service[OntologyRepo]
      sf       <- ZIO.service[StringFormatter]
      ts       <- ZIO.service[TriplestoreService]
      responder = OntologyResponderV2(ac, ch, cs, is, oc, och, oth, or, kr, ts)(sf)
      _        <- ZIO.serviceWithZIO[MessageRelay](_.subscribe(responder))
    } yield responder
  }
}
