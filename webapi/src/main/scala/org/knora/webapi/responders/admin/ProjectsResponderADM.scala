/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin
import com.typesafe.scalalogging.LazyLogging
import zio.*
import zio.macros.accessible

import java.util.UUID

import dsp.errors.*
import dsp.valueobjects.Iri
import dsp.valueobjects.RestrictedViewSize
import dsp.valueobjects.V2
import org.knora.webapi.*
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.instrumentation.InstrumentationSupport
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.*
import org.knora.webapi.messages.admin.responder.permissionsmessages.*
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.*
import org.knora.webapi.messages.admin.responder.projectsmessages.*
import org.knora.webapi.messages.admin.responder.usersmessages.UserGetADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceFlushDB
import org.knora.webapi.messages.store.cacheservicemessages.CacheServiceGetProjectADM
import org.knora.webapi.messages.store.cacheservicemessages.CacheServicePutProjectADM
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectUpdateRequest
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.ProjectADMService
import org.knora.webapi.store.cache.settings.CacheServiceSettings
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.util.ZioHelper

/**
 * Returns information about projects.
 */
@accessible
trait ProjectsResponderADM {

  /**
   * Gets all the projects and returns them as a [[ProjectADM]].
   *
   * @param withSystemProjects includes system projcets in response.
   * @return all the projects as a [[ProjectADM]].
   *
   *         NotFoundException if no projects are found.
   */
  def projectsGetRequestADM(withSystemProjects: Boolean): Task[ProjectsGetResponseADM]

  /**
   * Gets the project with the given project IRI, shortname, shortcode or UUID and returns the information
   * as a [[ProjectGetResponseADM]].
   *
   * @param id the IRI, shortname, shortcode or UUID of the project.
   * @return Information about the project as a [[ProjectGetResponseADM]].
   *
   *         [[NotFoundException]] When no project for the given IRI can be found.
   */
  def getSingleProjectADMRequest(id: ProjectIdentifierADM): Task[ProjectGetResponseADM]

  /**
   * Tries to retrieve a [[ProjectADM]] either from triplestore or cache if caching is enabled.
   * If project is not found in cache but in triplestore, then project is written to cache.
   */
  def getProjectFromCacheOrTriplestore(identifier: ProjectIdentifierADM): Task[Option[ProjectADM]]

  /**
   * Gets the members of a project with the given IRI, shortname, shortcode or UUID. Returns an empty list
   * if none are found.
   *
   * @param id     the IRI, shortname, shortcode or UUID of the project.
   * @param user the user making the request.
   * @return the members of a project as a [[ProjectMembersGetResponseADM]]
   */
  def projectMembersGetRequestADM(id: ProjectIdentifierADM, user: User): Task[ProjectMembersGetResponseADM]

  /**
   * Gets the admin members of a project with the given IRI, shortname, shortcode or UUIDe. Returns an empty list
   * if none are found
   *
   * @param id     the IRI, shortname, shortcode or UUID of the project.
   * @param user the user making the request.
   * @return the members of a project as a [[ProjectMembersGetResponseADM]]
   */
  def projectAdminMembersGetRequestADM(id: ProjectIdentifierADM, user: User): Task[ProjectAdminMembersGetResponseADM]

  /**
   * Gets all unique keywords for all projects and returns them. Returns an empty list if none are found.
   *
   * @return all keywords for all projects as [[ProjectsKeywordsGetResponseADM]]
   */
  def projectsKeywordsGetRequestADM(): Task[ProjectsKeywordsGetResponseADM]

  /**
   * Gets all keywords for a single project and returns them. Returns an empty list if none are found.
   *
   * @param projectIri the IRI of the project.
   * @return keywords for a projects as [[ProjectKeywordsGetResponseADM]]
   */
  def projectKeywordsGetRequestADM(projectIri: ProjectIri): Task[ProjectKeywordsGetResponseADM]

  /**
   * Get project's restricted view settings.
   *
   * @param id the project's identifier (IRI / shortcode / shortname)
   * @return [[ProjectRestrictedViewSettingsADM]]
   */
  def projectRestrictedViewSettingsGetADM(id: ProjectIdentifierADM): Task[Option[ProjectRestrictedViewSettingsADM]]

  /**
   * Get project's restricted view settings.
   *
   * @param id the project's identifier (IRI / shortcode / shortname)
   * @return [[ProjectRestrictedViewSettingsGetResponseADM]]
   */
  def projectRestrictedViewSettingsGetRequestADM(
    id: ProjectIdentifierADM
  ): Task[ProjectRestrictedViewSettingsGetResponseADM]

  /**
   * Creates a project.
   *
   * @param createReq            the new project's information.
   * @param requestingUser       the user that is making the request.
   * @param apiRequestID         the unique api request ID.
   * @return A [[ProjectOperationResponseADM]].
   *
   *         [[ForbiddenException]]      In the case that the user is not allowed to perform the operation.
   *
   *         [[DuplicateValueException]] In the case when either the shortname or shortcode are not unique.
   *
   *         [[BadRequestException]]     In the case when the shortcode is invalid.
   */
  def projectCreateRequestADM(
    createReq: ProjectCreateRequest,
    requestingUser: User,
    apiRequestID: UUID
  ): Task[ProjectOperationResponseADM]

  /**
   * Update project's basic information.
   *
   * @param projectIri    the IRI of the project.
   * @param updateReq     the update payload.
   * @param user          the user making the request.
   * @param apiRequestID  the unique api request ID.
   * @return A [[ProjectOperationResponseADM]].
   *
   *         [[ForbiddenException]] In the case that the user is not allowed to perform the operation.
   */
  def changeBasicInformationRequestADM(
    projectIri: ProjectIri,
    updateReq: ProjectUpdateRequest,
    user: User,
    apiRequestID: UUID
  ): Task[ProjectOperationResponseADM]

}

final case class ProjectsResponderADMLive(
  private val triplestore: TriplestoreService,
  private val messageRelay: MessageRelay,
  private val iriService: IriService,
  private val projectService: ProjectADMService,
  private val cacheServiceSettings: CacheServiceSettings,
  implicit private val stringFormatter: StringFormatter
) extends ProjectsResponderADM
    with MessageHandler
    with LazyLogging
    with InstrumentationSupport {

  // Global lock IRI used for project creation and update
  private val PROJECTS_GLOBAL_LOCK_IRI = "http://rdfh.ch/projects"

  override def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[ProjectsResponderRequestADM]

  /**
   * Receives a message extending [[ProjectsResponderRequestADM]], and returns an appropriate response message.
   */
  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case ProjectsGetRequestADM(withSystemProjects) => projectsGetRequestADM(withSystemProjects)
    case ProjectGetADM(identifier)                 => getProjectFromCacheOrTriplestore(identifier)
    case ProjectGetRequestADM(identifier)          => getSingleProjectADMRequest(identifier)
    case ProjectMembersGetRequestADM(identifier, requestingUser) =>
      projectMembersGetRequestADM(identifier, requestingUser)
    case ProjectAdminMembersGetRequestADM(identifier, requestingUser) =>
      projectAdminMembersGetRequestADM(identifier, requestingUser)
    case ProjectsKeywordsGetRequestADM() => projectsKeywordsGetRequestADM()
    case ProjectKeywordsGetRequestADM(projectIri) =>
      projectKeywordsGetRequestADM(projectIri)
    case ProjectRestrictedViewSettingsGetADM(identifier) =>
      projectRestrictedViewSettingsGetADM(identifier)
    case ProjectRestrictedViewSettingsGetRequestADM(identifier) =>
      projectRestrictedViewSettingsGetRequestADM(identifier)
    case ProjectCreateRequestADM(createRequest, requestingUser, apiRequestID) =>
      projectCreateRequestADM(createRequest, requestingUser, apiRequestID)
    case ProjectChangeRequestADM(
          projectIri,
          projectUpdatePayload,
          requestingUser,
          apiRequestID
        ) =>
      changeBasicInformationRequestADM(
        projectIri,
        projectUpdatePayload,
        requestingUser,
        apiRequestID
      )
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  /**
   * Gets all the projects but not system projects and returns them as a [[ProjectADM]].
   *
   * @param  withSystemProjects includes system projcets in response.
   * @return all the projects as a [[ProjectADM]].
   *
   *         [[NotFoundException]] if no projects are found.
   */
  override def projectsGetRequestADM(withSystemProjects: Boolean): Task[ProjectsGetResponseADM] =
    projectService.findAll
      .flatMap(projects =>
        (projects, withSystemProjects) match {
          case (Nil, _)  => ZIO.fail(NotFoundException(s"No projects found"))
          case (_, true) => ZIO.succeed(ProjectsGetResponseADM(projects))
          case _ =>
            val noSystemProjects: List[ProjectADM] = projects.filter(p => p.id.startsWith("http://rdfh.ch/projects/"))
            ZIO.succeed(ProjectsGetResponseADM(noSystemProjects))
        }
      )

  /**
   * Gets the project with the given project IRI, shortname, shortcode or UUID and returns the information
   * as a [[ProjectGetResponseADM]].
   *
   * @param id           the IRI, shortname, shortcode or UUID of the project.
   * @return Information about the project as a [[ProjectGetResponseADM]].
   *
   *         [[NotFoundException]] When no project for the given IRI can be found.
   */
  override def getSingleProjectADMRequest(id: ProjectIdentifierADM): Task[ProjectGetResponseADM] =
    getProjectFromCacheOrTriplestore(id)
      .flatMap(ZIO.fromOption(_))
      .mapBoth(_ => NotFoundException(s"Project '${getId(id)}' not found"), ProjectGetResponseADM)

  /**
   * Gets the members of a project with the given IRI, shortname, shortcode or UUID. Returns an empty list
   * if none are found.
   *
   * @param id           the IRI, shortname, shortcode or UUID of the project.
   * @param user       the user making the request.
   * @return the members of a project as a [[ProjectMembersGetResponseADM]]
   */
  override def projectMembersGetRequestADM(
    id: ProjectIdentifierADM,
    user: User
  ): Task[ProjectMembersGetResponseADM] =
    for {
      /* Get project and verify permissions. */
      project <- getProjectFromCacheOrTriplestore(id)
                   .flatMap(ZIO.fromOption(_))
                   .orElseFail(NotFoundException(s"Project '${getId(id)}' not found."))
      _ <- ZIO
             .fail(ForbiddenException("SystemAdmin or ProjectAdmin permissions are required."))
             .when {
               val userPermissions = user.permissions
               !userPermissions.isSystemAdmin &&
               !userPermissions.isProjectAdmin(project.id) &&
               !user.isSystemUser
             }

      query = Construct(
                sparql.admin.txt
                  .getProjectMembers(
                    maybeIri = id.asIriIdentifierOption,
                    maybeShortname = id.asShortnameIdentifierOption,
                    maybeShortcode = id.asShortcodeIdentifierOption
                  )
              )

      statements <- triplestore
                      .query(query)
                      .flatMap(_.asExtended)
                      .map(_.statements.toList)

      // get project member IRI from results rows
      userIris =
        if (statements.nonEmpty) { statements.map(_._1.toString) }
        else { Seq.empty[IRI] }

      maybeUserFutures: Seq[Task[Option[User]]] =
        userIris.map { userIri =>
          messageRelay
            .ask[Option[User]](
              UserGetADM(
                identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                userInformationTypeADM = UserInformationTypeADM.Restricted,
                requestingUser = KnoraSystemInstances.Users.SystemUser
              )
            )
        }
      maybeUsers      <- ZioHelper.sequence(maybeUserFutures)
      users: Seq[User] = maybeUsers.flatten

    } yield ProjectMembersGetResponseADM(members = users)

  /**
   * Gets the admin members of a project with the given IRI, shortname, shortcode or UUIDe. Returns an empty list
   * if none are found
   *
   * @param id           the IRI, shortname, shortcode or UUID of the project.
   * @param user       the user making the request.
   * @return the members of a project as a [[ProjectMembersGetResponseADM]]
   */
  override def projectAdminMembersGetRequestADM(
    id: ProjectIdentifierADM,
    user: User
  ): Task[ProjectAdminMembersGetResponseADM] =
    for {
      /* Get project and verify permissions. */
      project <- getProjectFromCacheOrTriplestore(id)
                   .flatMap(ZIO.fromOption(_))
                   .orElseFail(NotFoundException(s"Project '${getId(id)}' not found."))
      _ <- ZIO
             .fail(ForbiddenException("SystemAdmin or ProjectAdmin permissions are required."))
             .when {
               !user.permissions.isSystemAdmin &&
               !user.permissions.isProjectAdmin(project.id)
             }

      query = Construct(
                sparql.admin.txt
                  .getProjectAdminMembers(
                    maybeIri = id.asIriIdentifierOption,
                    maybeShortname = id.asShortnameIdentifierOption,
                    maybeShortcode = id.asShortcodeIdentifierOption
                  )
              )

      statements <- triplestore.query(query).flatMap(_.asExtended).map(_.statements.toList)

      // get project member IRI from results rows
      userIris = if (statements.nonEmpty) { statements.map(_._1.toString) }
                 else { Seq.empty[IRI] }

      maybeUserTasks: Seq[Task[Option[User]]] = userIris.map { userIri =>
                                                  messageRelay
                                                    .ask[Option[User]](
                                                      UserGetADM(
                                                        identifier = UserIdentifierADM(maybeIri = Some(userIri)),
                                                        userInformationTypeADM = UserInformationTypeADM.Restricted,
                                                        requestingUser = KnoraSystemInstances.Users.SystemUser
                                                      )
                                                    )
                                                }
      maybeUsers      <- ZioHelper.sequence(maybeUserTasks)
      users: Seq[User] = maybeUsers.flatten

    } yield ProjectAdminMembersGetResponseADM(members = users)

  /**
   * Gets all unique keywords for all projects and returns them. Returns an empty list if none are found.
   *
   * @return all keywords for all projects as [[ProjectsKeywordsGetResponseADM]]
   */
  override def projectsKeywordsGetRequestADM(): Task[ProjectsKeywordsGetResponseADM] =
    projectService.findAllProjectsKeywords

  /**
   * Gets all keywords for a single project and returns them. Returns an empty list if none are found.
   *
   * @param projectIri           the IRI of the project.
   * @return keywords for a projects as [[ProjectKeywordsGetResponseADM]]
   */
  override def projectKeywordsGetRequestADM(projectIri: ProjectIri): Task[ProjectKeywordsGetResponseADM] =
    for {
      id <- IriIdentifier.fromString(projectIri.value).toZIO.mapError(e => BadRequestException(e.getMessage))
      keywords <- projectService
                    .findProjectKeywordsBy(id)
                    .someOrFail(NotFoundException(s"Project '${projectIri.value}' not found."))
    } yield keywords

  /**
   * Get project's restricted view settings.
   *
   * @param id  the project's identifier (IRI / shortcode / shortname / UUID)
   *
   * @return [[ProjectRestrictedViewSettingsADM]]
   */
  override def projectRestrictedViewSettingsGetADM(
    id: ProjectIdentifierADM
  ): Task[Option[ProjectRestrictedViewSettingsADM]] = {
    val query = Construct(
      sparql.admin.txt
        .getProjects(
          maybeIri = id.asIriIdentifierOption,
          maybeShortname = id.asShortnameIdentifierOption,
          maybeShortcode = id.asShortcodeIdentifierOption
        )
    )
    for {
      projectResponse <- triplestore.query(query).flatMap(_.asExtended)
      restrictedViewSettings =
        if (projectResponse.statements.nonEmpty) {

          val (_, propsMap): (SubjectV2, Map[SmartIri, Seq[LiteralV2]]) = projectResponse.statements.head

          val size = propsMap
            .get(OntologyConstants.KnoraAdmin.ProjectRestrictedViewSize.toSmartIri)
            .map(_.head.asInstanceOf[StringLiteralV2].value)
          val watermark = propsMap
            .get(OntologyConstants.KnoraAdmin.ProjectRestrictedViewWatermark.toSmartIri)
            .map(_.head.asInstanceOf[StringLiteralV2].value)

          Some(ProjectRestrictedViewSettingsADM(size, watermark))
        } else {
          None
        }

    } yield restrictedViewSettings
  }

  /**
   * Get project's restricted view settings.
   *
   * @param id  the project's identifier (IRI / shortcode / shortname / UUID)
   *
   * @return [[ProjectRestrictedViewSettingsGetResponseADM]]
   */
  override def projectRestrictedViewSettingsGetRequestADM(
    id: ProjectIdentifierADM
  ): Task[ProjectRestrictedViewSettingsGetResponseADM] =
    projectRestrictedViewSettingsGetADM(id)
      .flatMap(ZIO.fromOption(_))
      .mapBoth(
        _ => NotFoundException(s"Project '${getId(id)}' not found."),
        ProjectRestrictedViewSettingsGetResponseADM
      )

  /**
   * Update project's basic information.
   *
   * @param projectIri    the IRI of the project.
   * @param updateReq     the update payload.
   * @param user          the user making the request.
   * @param apiRequestID  the unique api request ID.
   * @return A [[ProjectOperationResponseADM]].
   *
   *         [[ForbiddenException]] In the case that the user is not allowed to perform the operation.
   */
  override def changeBasicInformationRequestADM(
    projectIri: ProjectIri,
    updateReq: ProjectUpdateRequest,
    user: User,
    apiRequestID: UUID
  ): Task[ProjectOperationResponseADM] = {

    /**
     * The actual change project task run with an IRI lock.
     */
    def changeProjectTask(
      projectIri: ProjectIri,
      updateReq: ProjectUpdateRequest,
      requestingUser: User
    ): Task[ProjectOperationResponseADM] =
      // check if the requesting user is allowed to perform updates
      if (!requestingUser.permissions.isProjectAdmin(projectIri.value) && !requestingUser.permissions.isSystemAdmin) {
        ZIO.fail(ForbiddenException("Project's information can only be changed by a project or system admin."))
      } else {
        updateProjectADM(projectIri, updateReq)
      }

    val task = changeProjectTask(projectIri, updateReq, user)
    IriLocker.runWithIriLock(apiRequestID, projectIri.value, task)
  }

  /**
   * Main project update method.
   *
   * @param projectIri           the IRI of the project.
   * @param projectUpdatePayload the data to be updated. Update means exchanging what is in the triplestore with
   *                             this data. If only some parts of the data need to be changed, then this needs to
   *                             be prepared in the step before this one.
   *
   * @return A [[ProjectOperationResponseADM]].
   *
   *         [[NotFoundException]] In the case that the project's IRI is not found.
   */
  private def updateProjectADM(projectIri: ProjectIri, projectUpdatePayload: ProjectUpdateRequest) = {

    val areAllParamsNone: Boolean = projectUpdatePayload.productIterator.forall {
      case param: Option[Any] => param.isEmpty
      case _                  => false
    }

    if (areAllParamsNone) { ZIO.fail(BadRequestException("No data would be changed. Aborting update request.")) }
    else {
      val projectId = IriIdentifier.from(projectIri)
      for {
        _ <- projectService
               .findByProjectIdentifier(projectId)
               .flatMap(ZIO.fromOption(_))
               .orElseFail(NotFoundException(s"Project '${projectIri.value}' not found. Aborting update request."))

        // we are changing the project, so lets get rid of the cached copy
        _ <- messageRelay.ask[Any](CacheServiceFlushDB(KnoraSystemInstances.Users.SystemUser))

        /* Update project */
        updateQuery = sparql.admin.txt.updateProject(
                        adminNamedGraphIri = "http://www.knora.org/data/admin",
                        projectIri = projectIri.value,
                        maybeShortname = projectUpdatePayload.shortname.map(_.value),
                        maybeLongname = projectUpdatePayload.longname.map(_.value),
                        maybeDescriptions = projectUpdatePayload.description.map(_.map(_.value)),
                        maybeKeywords = projectUpdatePayload.keywords.map(_.map(_.value)),
                        maybeLogo = projectUpdatePayload.logo.map(_.value),
                        maybeStatus = projectUpdatePayload.status.map(_.value),
                        maybeSelfjoin = projectUpdatePayload.selfjoin.map(_.value)
                      )
        _ <- triplestore.query(Update(updateQuery))

        /* Verify that the project was updated. */
        updatedProject <-
          projectService
            .findByProjectIdentifier(projectId)
            .flatMap(ZIO.fromOption(_))
            .orElseFail(UpdateNotPerformedException("Project was not updated. Please report this as a possible bug."))

        _ <- ZIO.logDebug(
               s"updateProjectADM - projectUpdatePayload: $projectUpdatePayload /  updatedProject: $updatedProject"
             )

        _ <- checkProjectUpdate(updatedProject, projectUpdatePayload)

      } yield ProjectOperationResponseADM(project = updatedProject)
    }
  }

  /**
   * Checks if all fields of a projectUpdatePayload are represented in the updated [[ProjectADM]]. If so, the
   * update is considered successful.
   *
   * @param updatedProject       The updated project against which the projectUpdatePayload is compared.
   * @param projectUpdatePayload The payload which defines what should have been updated.
   *
   *         [[UpdateNotPerformedException]] If one of the fields was not updated.
   */
  private def checkProjectUpdate(
    updatedProject: ProjectADM,
    projectUpdatePayload: ProjectUpdateRequest
  ): Task[Unit] = ZIO.attempt {
    if (projectUpdatePayload.shortname.nonEmpty) {
      projectUpdatePayload.shortname
        .map(_.value)
        .map(Iri.fromSparqlEncodedString)
        .filter(_ == updatedProject.shortname)
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'shortname' was not updated. Please report this as a possible bug."
          )
        )
    }

    if (projectUpdatePayload.shortname.nonEmpty) {
      projectUpdatePayload.longname
        .map(_.value)
        .map(Iri.fromSparqlEncodedString)
        .filter(updatedProject.longname.contains(_))
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'longname' was not updated. Please report this as a possible bug."
          )
        )
    }

    if (projectUpdatePayload.description.nonEmpty) {
      projectUpdatePayload.description
        .map(_.map(_.value))
        .map(_.map(d => V2.StringLiteralV2(Iri.fromSparqlEncodedString(d.value), d.language)))
        .filter(updatedProject.description.diff(_).isEmpty)
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'description' was not updated. Please report this as a possible bug."
          )
        )
    }

    if (projectUpdatePayload.keywords.nonEmpty) {
      projectUpdatePayload.keywords
        .map(_.map(_.value))
        .map(_.map(key => Iri.fromSparqlEncodedString(key)))
        .filter(_.sorted == updatedProject.keywords.sorted)
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'keywords' was not updated. Please report this as a possible bug."
          )
        )
    }

    if (projectUpdatePayload.logo.nonEmpty) {
      projectUpdatePayload.logo
        .map(_.value)
        .map(Iri.fromSparqlEncodedString)
        .filter(updatedProject.logo.contains(_))
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'logo' was not updated. Please report this as a possible bug."
          )
        )
    }

    if (projectUpdatePayload.status.nonEmpty) {
      val _ = projectUpdatePayload.status
        .map(_.value)
        .filter(_ == updatedProject.status)
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'status' was not updated. Please report this as a possible bug."
          )
        )
    }

    if (projectUpdatePayload.selfjoin.nonEmpty) {
      val _ = projectUpdatePayload.selfjoin
        .map(_.value)
        .filter(_ == updatedProject.selfjoin)
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'selfjoin' was not updated. Please report this as a possible bug."
          )
        )
    }
  }

  /**
   * Creates a project.
   *
   * @param createReq            the new project's information.
   * @param requestingUser       the user that is making the request.
   * @param apiRequestID         the unique api request ID.
   * @return A [[ProjectOperationResponseADM]].
   *
   *         [[ForbiddenException]]      In the case that the user is not allowed to perform the operation.
   *
   *         [[DuplicateValueException]] In the case when either the shortname or shortcode are not unique.
   *
   *         [[BadRequestException]]     In the case when the shortcode is invalid.
   */
  override def projectCreateRequestADM(
    createReq: ProjectCreateRequest,
    requestingUser: User,
    apiRequestID: UUID
  ): Task[ProjectOperationResponseADM] = {

    /**
     * Creates following permissions for the new project
     * 1. Permissions for project admins to do all operations on project level and to create, modify, delete, change rights,
     * view, and restricted view of all new resources and values that belong to this project.
     * 2. Permissions for project members to create, modify, view and restricted view of all new resources and values that belong to this project.
     *
     * @param projectIri The IRI of the new project.
     *
     *         [[BadRequestException]] If a permission is not created.
     */
    def createPermissionsForAdminsAndMembersOfNewProject(projectIri: IRI): Task[Unit] =
      for {
        // Give the admins of the new project rights for any operation in project level, and rights to create resources.
        _ <- messageRelay
               .ask[AdministrativePermissionCreateResponseADM](
                 AdministrativePermissionCreateRequestADM(
                   createRequest = CreateAdministrativePermissionAPIRequestADM(
                     forProject = projectIri,
                     forGroup = OntologyConstants.KnoraAdmin.ProjectAdmin,
                     hasPermissions =
                       Set(PermissionADM.ProjectAdminAllPermission, PermissionADM.ProjectResourceCreateAllPermission)
                   ).prepareHasPermissions,
                   requestingUser = requestingUser,
                   apiRequestID = UUID.randomUUID()
                 )
               )

        // Give the members of the new project rights to create resources.
        _ <- messageRelay
               .ask[AdministrativePermissionCreateResponseADM](
                 AdministrativePermissionCreateRequestADM(
                   createRequest = CreateAdministrativePermissionAPIRequestADM(
                     forProject = projectIri,
                     forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
                     hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission)
                   ).prepareHasPermissions,
                   requestingUser = requestingUser,
                   apiRequestID = UUID.randomUUID()
                 )
               )

        // Give the admins of the new project rights to change rights, modify, delete, view,
        // and restricted view of all resources and values that belong to the project.
        _ <- messageRelay
               .ask[DefaultObjectAccessPermissionCreateResponseADM](
                 DefaultObjectAccessPermissionCreateRequestADM(
                   createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
                     forProject = projectIri,
                     forGroup = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
                     hasPermissions = Set(
                       PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectAdmin),
                       PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
                     )
                   ).prepareHasPermissions,
                   requestingUser = requestingUser,
                   apiRequestID = UUID.randomUUID()
                 )
               )

        // Give the members of the new project rights to modify, view, and restricted view of all resources and values
        // that belong to the project.
        _ <- messageRelay
               .ask[DefaultObjectAccessPermissionCreateResponseADM](
                 DefaultObjectAccessPermissionCreateRequestADM(
                   createRequest = CreateDefaultObjectAccessPermissionAPIRequestADM(
                     forProject = projectIri,
                     forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
                     hasPermissions = Set(
                       PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectAdmin),
                       PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember)
                     )
                   ).prepareHasPermissions,
                   requestingUser = requestingUser,
                   apiRequestID = UUID.randomUUID()
                 )
               )
      } yield ()

    def projectCreateTask(
      createProjectRequest: ProjectCreateRequest,
      requestingUser: User
    ): Task[ProjectOperationResponseADM] =
      for {
        // check if the supplied shortname is unique
        _ <- ZIO
               .fail(
                 DuplicateValueException(
                   s"Project with the shortname: '${createProjectRequest.shortname.value}' already exists"
                 )
               )
               .whenZIO(projectByShortnameExists(createProjectRequest.shortname.value))

        // check if the optionally supplied shortcode is valid and unique
        _ <- ZIO
               .fail(
                 DuplicateValueException(
                   s"Project with the shortcode: '${createProjectRequest.shortcode.value}' already exists"
                 )
               )
               .whenZIO(projectByShortcodeExists(createProjectRequest.shortcode.value))

        // check if the requesting user is allowed to create project
        _ <- ZIO
               .fail(ForbiddenException("A new project can only be created by a system admin."))
               .when(!requestingUser.permissions.isSystemAdmin)

        // check the custom IRI; if not given, create an unused IRI
        customProjectIri: Option[SmartIri] = createProjectRequest.id.map(_.value).map(_.toSmartIri)
        newProjectIRI                     <- iriService.checkOrCreateEntityIri(customProjectIri, stringFormatter.makeRandomProjectIri)
        maybeLongname                      = createProjectRequest.longname.map(_.value)
        maybeLogo                          = createProjectRequest.logo.map(_.value)
        descriptions                       = createProjectRequest.description.map(_.value)
        _                                 <- ZIO.fail(BadRequestException("Project description is required.")).when(descriptions.isEmpty)

        createNewProjectSparql = sparql.admin.txt
                                   .createNewProject(
                                     AdminConstants.adminDataNamedGraph.value,
                                     projectIri = newProjectIRI,
                                     projectClassIri = OntologyConstants.KnoraAdmin.KnoraProject,
                                     shortname = createProjectRequest.shortname.value,
                                     shortcode = createProjectRequest.shortcode.value,
                                     maybeLongname = maybeLongname,
                                     descriptions = descriptions,
                                     maybeKeywords = if (createProjectRequest.keywords.nonEmpty) {
                                       Some(createProjectRequest.keywords.map(_.value))
                                     } else None,
                                     maybeLogo = maybeLogo,
                                     status = createProjectRequest.status.value,
                                     hasSelfJoinEnabled = createProjectRequest.selfjoin.value
                                   )
        _ <- triplestore.query(Update(createNewProjectSparql))

        // try to retrieve newly created project (will also add to cache)
        id <- IriIdentifier.fromString(newProjectIRI).toZIO.mapError(e => BadRequestException(e.getMessage))
        // check to see if we could retrieve the new project

        newProjectADM <- projectService
                           .findByProjectIdentifier(id)
                           .someOrFail(
                             UpdateNotPerformedException(
                               s"Project $newProjectIRI was not created. Please report this as a possible bug."
                             )
                           )
        // create permissions for admins and members of the new group
        _ <- createPermissionsForAdminsAndMembersOfNewProject(newProjectIRI)
        _ <- projectService.setProjectRestrictedViewSize(newProjectADM, RestrictedViewSize.default)

      } yield ProjectOperationResponseADM(project = newProjectADM.unescape)

    val task = projectCreateTask(createReq, requestingUser)
    IriLocker.runWithIriLock(apiRequestID, PROJECTS_GLOBAL_LOCK_IRI, task)
  }

  ////////////////////
  // Helper Methods //
  ////////////////////

  /**
   * Tries to retrieve a [[ProjectADM]] either from triplestore or cache if caching is enabled.
   * If project is not found in cache but in triplestore, then project is written to cache.
   */
  override def getProjectFromCacheOrTriplestore(
    identifier: ProjectIdentifierADM
  ): Task[Option[ProjectADM]] =
    if (cacheServiceSettings.cacheServiceEnabled) {
      // caching enabled
      getProjectFromCache(identifier).flatMap {
        case None =>
          // none found in cache. getting from triplestore.
          projectService.findByProjectIdentifier(identifier).flatMap {
            case None =>
              // also none found in triplestore. finally returning none.
              logger.debug("getProjectFromCacheOrTriplestore - not found in cache and in triplestore")
              ZIO.succeed(None)
            case Some(project) =>
              // found a project in the triplestore. need to write to cache.
              logger.debug(
                "getProjectFromCacheOrTriplestore - not found in cache but found in triplestore. need to write to cache."
              )
              // writing project to cache and afterwards returning the project found in the triplestore
              messageRelay
                .ask[Unit](CacheServicePutProjectADM(project))
                .as(Some(project))
          }
        case Some(project) =>
          logger.debug("getProjectFromCacheOrTriplestore - found in cache. returning project.")
          ZIO.succeed(Some(project))
      }
    } else {
      // caching disabled
      logger.debug("getProjectFromCacheOrTriplestore - caching disabled. getting from triplestore.")
      projectService.findByProjectIdentifier(identifier)
    }

  /**
   * Helper method for checking if a project identified by shortname exists.
   *
   * @param shortname the shortname of the project.
   * @return a [[Boolean]].
   */
  private def projectByShortnameExists(shortname: String): Task[Boolean] =
    triplestore.query(Ask(sparql.admin.txt.checkProjectExistsByShortname(shortname)))

  /**
   * Helper method for checking if a project identified by shortcode exists.
   *
   * @param shortcode the shortcode of the project.
   * @return a [[Boolean]].
   */
  private def projectByShortcodeExists(shortcode: String): Task[Boolean] =
    triplestore.query(Ask(sparql.admin.txt.checkProjectExistsByShortcode(shortcode)))

  private def getProjectFromCache(identifier: ProjectIdentifierADM): Task[Option[ProjectADM]] =
    messageRelay.ask[Option[ProjectADM]](CacheServiceGetProjectADM(identifier)).map(_.map(_.unescape))
}

object ProjectsResponderADMLive {
  val layer: URLayer[
    MessageRelay & TriplestoreService & StringFormatter & ProjectADMService & IriService & AppConfig,
    ProjectsResponderADMLive
  ] = ZLayer.fromZIO {
    for {
      c       <- ZIO.service[AppConfig].map(new CacheServiceSettings(_))
      iris    <- ZIO.service[IriService]
      ps      <- ZIO.service[ProjectADMService]
      sf      <- ZIO.service[StringFormatter]
      ts      <- ZIO.service[TriplestoreService]
      mr      <- ZIO.service[MessageRelay]
      handler <- mr.subscribe(ProjectsResponderADMLive(ts, mr, iris, ps, c, sf))
    } yield handler
  }
}
