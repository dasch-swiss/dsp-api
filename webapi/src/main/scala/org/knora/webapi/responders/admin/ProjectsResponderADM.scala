/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin
import com.typesafe.scalalogging.LazyLogging
import zio._

import java.util.UUID

import dsp.errors._
import dsp.valueobjects.Iri
import org.knora.webapi._
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages._
import org.knora.webapi.messages.admin.responder.permissionsmessages._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.Responder
import org.knora.webapi.slice.admin.AdminConstants
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.RestrictedView
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.store.cache.CacheService
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update

final case class ProjectsResponderADM(
  private val iriService: IriService,
  private val cacheService: CacheService,
  private val permissionsResponderADM: PermissionsResponderADM,
  private val projectService: ProjectService,
  private val triplestore: TriplestoreService,
  implicit private val stringFormatter: StringFormatter,
) extends MessageHandler
    with LazyLogging {

  // Global lock IRI used for project creation and update
  private val PROJECTS_GLOBAL_LOCK_IRI = "http://rdfh.ch/projects"

  def isResponsibleFor(message: ResponderRequest): Boolean = message.isInstanceOf[ProjectsResponderRequestADM]

  /**
   * Receives a message extending [[ProjectsResponderRequestADM]], and returns an appropriate response message.
   */
  def handle(msg: ResponderRequest): Task[Any] = msg match {
    case ProjectCreateRequestADM(createRequest, requestingUser, apiRequestID) =>
      projectCreateRequestADM(createRequest, requestingUser, apiRequestID)
    case ProjectChangeRequestADM(
          projectIri,
          projectUpdatePayload,
          requestingUser,
          apiRequestID,
        ) =>
      changeBasicInformationRequestADM(
        projectIri,
        projectUpdatePayload,
        requestingUser,
        apiRequestID,
      )
    case other => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

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
    apiRequestID: UUID,
  ): Task[ProjectOperationResponseADM] = {

    /**
     * The actual change project task run with an IRI lock.
     */
    def changeProjectTask(
      projectIri: ProjectIri,
      updateReq: ProjectUpdateRequest,
      requestingUser: User,
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
               .someOrFail(NotFoundException(s"Project '${projectIri.value}' not found. Aborting update request."))

        // we are changing the project, so lets get rid of the cache
        _ <- cacheService.clearCache()

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
                        maybeSelfjoin = projectUpdatePayload.selfjoin.map(_.value),
                      )
        _ <- triplestore.query(Update(updateQuery))

        /* Verify that the project was updated. */
        updatedProject <-
          projectService
            .findByProjectIdentifier(projectId)
            .someOrFail(UpdateNotPerformedException("Project was not updated. Please report this as a possible bug."))

        _ <- ZIO.logDebug(
               s"updateProjectADM - projectUpdatePayload: $projectUpdatePayload /  updatedProject: $updatedProject",
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
    updatedProject: Project,
    projectUpdatePayload: ProjectUpdateRequest,
  ): Task[Unit] = ZIO.attempt {
    if (projectUpdatePayload.shortname.nonEmpty) {
      projectUpdatePayload.shortname
        .map(_.value)
        .map(Iri.fromSparqlEncodedString)
        .filter(_ == updatedProject.shortname)
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'shortname' was not updated. Please report this as a possible bug.",
          ),
        )
    }

    if (projectUpdatePayload.shortname.nonEmpty) {
      projectUpdatePayload.longname
        .map(_.value)
        .map(Iri.fromSparqlEncodedString)
        .filter(updatedProject.longname.contains(_))
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'longname' was not updated. Please report this as a possible bug.",
          ),
        )
    }

    if (projectUpdatePayload.description.nonEmpty) {
      projectUpdatePayload.description
        .map(_.map(_.value))
        .map(_.map(d => StringLiteralV2.from(Iri.fromSparqlEncodedString(d.value), d.language)))
        .filter(updatedProject.description.diff(_).isEmpty)
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'description' was not updated. Please report this as a possible bug.",
          ),
        )
    }

    if (projectUpdatePayload.keywords.nonEmpty) {
      projectUpdatePayload.keywords
        .map(_.map(_.value))
        .map(_.map(key => Iri.fromSparqlEncodedString(key)))
        .filter(_.sorted == updatedProject.keywords.sorted)
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'keywords' was not updated. Please report this as a possible bug.",
          ),
        )
    }

    if (projectUpdatePayload.logo.nonEmpty) {
      projectUpdatePayload.logo
        .map(_.value)
        .map(Iri.fromSparqlEncodedString)
        .filter(updatedProject.logo.contains(_))
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'logo' was not updated. Please report this as a possible bug.",
          ),
        )
    }

    if (projectUpdatePayload.status.nonEmpty) {
      val _ = projectUpdatePayload.status
        .map(_.value)
        .filter(_ == updatedProject.status)
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'status' was not updated. Please report this as a possible bug.",
          ),
        )
    }

    if (projectUpdatePayload.selfjoin.nonEmpty) {
      val _ = projectUpdatePayload.selfjoin
        .map(_.value)
        .filter(_ == updatedProject.selfjoin)
        .getOrElse(
          throw UpdateNotPerformedException(
            "Project's 'selfjoin' was not updated. Please report this as a possible bug.",
          ),
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
  def projectCreateRequestADM(
    createReq: ProjectCreateRequest,
    requestingUser: User,
    apiRequestID: UUID,
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
        _ <- permissionsResponderADM.createAdministrativePermission(
               CreateAdministrativePermissionAPIRequestADM(
                 forProject = projectIri,
                 forGroup = OntologyConstants.KnoraAdmin.ProjectAdmin,
                 hasPermissions =
                   Set(PermissionADM.ProjectAdminAllPermission, PermissionADM.ProjectResourceCreateAllPermission),
               ),
               requestingUser,
               UUID.randomUUID(),
             )

        // Give the members of the new project rights to create resources.
        _ <- permissionsResponderADM.createAdministrativePermission(
               CreateAdministrativePermissionAPIRequestADM(
                 forProject = projectIri,
                 forGroup = OntologyConstants.KnoraAdmin.ProjectMember,
                 hasPermissions = Set(PermissionADM.ProjectResourceCreateAllPermission),
               ),
               requestingUser,
               UUID.randomUUID(),
             )

        // Give the admins of the new project rights to change rights, modify, delete, view,
        // and restricted view of all resources and values that belong to the project.
        groupAdmin <- ZIO.attempt(
                        CreateDefaultObjectAccessPermissionAPIRequestADM(
                          forProject = projectIri,
                          forGroup = Some(OntologyConstants.KnoraAdmin.ProjectAdmin),
                          hasPermissions = Set(
                            PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectAdmin),
                            PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
                          ),
                        ),
                      )
        _ <- permissionsResponderADM.createDefaultObjectAccessPermission(groupAdmin, requestingUser, UUID.randomUUID())

        // Give the members of the new project rights to modify, view, and restricted view of all resources and values
        // that belong to the project.
        groupMember <- ZIO.attempt(
                         CreateDefaultObjectAccessPermissionAPIRequestADM(
                           forProject = projectIri,
                           forGroup = Some(OntologyConstants.KnoraAdmin.ProjectMember),
                           hasPermissions = Set(
                             PermissionADM.changeRightsPermission(OntologyConstants.KnoraAdmin.ProjectAdmin),
                             PermissionADM.modifyPermission(OntologyConstants.KnoraAdmin.ProjectMember),
                           ),
                         ),
                       )
        _ <- permissionsResponderADM.createDefaultObjectAccessPermission(groupMember, requestingUser, UUID.randomUUID())
      } yield ()

    def projectCreateTask(
      createProjectRequest: ProjectCreateRequest,
      requestingUser: User,
    ): Task[ProjectOperationResponseADM] =
      for {
        // check if the supplied shortname is unique
        _ <- ZIO
               .fail(
                 DuplicateValueException(
                   s"Project with the shortname: '${createProjectRequest.shortname.value}' already exists",
                 ),
               )
               .whenZIO(projectByShortnameExists(createProjectRequest.shortname.value))

        // check if the optionally supplied shortcode is valid and unique
        _ <- ZIO
               .fail(
                 DuplicateValueException(
                   s"Project with the shortcode: '${createProjectRequest.shortcode.value}' already exists",
                 ),
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
                                     hasSelfJoinEnabled = createProjectRequest.selfjoin.value,
                                   )
        _ <- triplestore.query(Update(createNewProjectSparql))

        // try to retrieve newly created project (will also add to cache)
        id <- IriIdentifier.fromString(newProjectIRI).toZIO.mapError(e => BadRequestException(e.getMessage))
        // check to see if we could retrieve the new project

        newProjectADM <- projectService
                           .findByProjectIdentifier(id)
                           .someOrFail(
                             UpdateNotPerformedException(
                               s"Project $newProjectIRI was not created. Please report this as a possible bug.",
                             ),
                           )
        // create permissions for admins and members of the new group
        _ <- createPermissionsForAdminsAndMembersOfNewProject(newProjectIRI)
        _ <- projectService.setProjectRestrictedView(newProjectADM, RestrictedView.default)

      } yield ProjectOperationResponseADM(project = newProjectADM.unescape)

    val task = projectCreateTask(createReq, requestingUser)
    IriLocker.runWithIriLock(apiRequestID, PROJECTS_GLOBAL_LOCK_IRI, task)
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
}

object ProjectsResponderADM {
  val layer = ZLayer.fromZIO {
    for {
      iris    <- ZIO.service[IriService]
      cs      <- ZIO.service[CacheService]
      ps      <- ZIO.service[ProjectService]
      sf      <- ZIO.service[StringFormatter]
      ts      <- ZIO.service[TriplestoreService]
      mr      <- ZIO.service[MessageRelay]
      pr      <- ZIO.service[PermissionsResponderADM]
      handler <- mr.subscribe(ProjectsResponderADM(iris, cs, pr, ps, ts, sf))
    } yield handler
  }
}
