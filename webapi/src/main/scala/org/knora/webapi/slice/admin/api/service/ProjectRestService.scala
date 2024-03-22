/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio._

import dsp.errors.NotFoundException
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsGetResponse
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.responders.admin.ProjectsResponderADM
import org.knora.webapi.slice.admin.api.model.ProjectDataGetResponseADM
import org.knora.webapi.slice.admin.api.model.ProjectExportInfoResponse
import org.knora.webapi.slice.admin.api.model.ProjectImportResponse
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.RestrictedViewResponse
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequestsAndResponses.SetRestrictedViewRequest
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Status
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.ProjectExportService
import org.knora.webapi.slice.admin.domain.service.ProjectImportService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer

final case class ProjectRestService(
  format: KnoraResponseRenderer,
  responder: ProjectsResponderADM,
  projectService: ProjectService,
  knoraProjectService: KnoraProjectService,
  projectExportService: ProjectExportService,
  projectImportService: ProjectImportService,
  permissionService: AuthorizationRestService,
) {

  /**
   * Returns all projects as a [[ProjectsGetResponse]].
   *
   * @return
   *     '''success''': information about the projects as a [[ProjectsGetResponse]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project was found
   */
  def listAllProjects(): Task[ProjectsGetResponse] = for {
    internal <- projectService.findAll
    projects  = internal.filter(_.projectIri.isRegularProjectIri)
    external <- format.toExternal(ProjectsGetResponse(projects))
  } yield external

  def findById(id: ProjectIri): Task[ProjectGetResponse] =
    toExternalProjectGetResponse(projectService.findById(id), id)

  def findByShortcode(shortcode: Shortcode): Task[ProjectGetResponse] =
    toExternalProjectGetResponse(projectService.findByShortcode(shortcode), shortcode)

  def findByShortname(shortname: Shortname): Task[ProjectGetResponse] =
    toExternalProjectGetResponse(projectService.findByShortname(shortname), shortname)

  private def toExternalProjectGetResponse(prjTask: Task[Option[Project]], id: StringValue): Task[ProjectGetResponse] =
    prjTask
      .someOrFail(NotFoundException(s"Project not '${id.value} not found."))
      .map(ProjectGetResponse.apply)
      .flatMap(format.toExternal)

  /**
   * Creates a project from the given payload.
   *
   * @param createReq the [[ProjectCreateRequest]] from which to create the project
   * @param user      the [[User]] making the request
   * @return
   *     '''success''': information about the created project as a [[ProjectOperationResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]]
   *                    can be found, if one was provided with the [[ProjectCreateRequest]]
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def createProject(createReq: ProjectCreateRequest, user: User): Task[ProjectOperationResponseADM] = for {
    internal <- ZIO.random.flatMap(_.nextUUID).flatMap(responder.projectCreateRequestADM(createReq, user, _))
    external <- format.toExternalADM(internal)
  } yield external

  /**
   * Deletes the project by its [[ProjectIri]].
   *
   * @param id   the [[ProjectIri]] of the project
   * @param user the [[User]] making the request
   * @return
   *     '''success''': a [[ProjectOperationResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def deleteProject(id: IriIdentifier, user: User): Task[ProjectOperationResponseADM] = {
    val updatePayload = ProjectUpdateRequest(status = Some(Status.Inactive))
    for {
      apiId    <- Random.nextUUID
      internal <- responder.changeBasicInformationRequestADM(id.value, updatePayload, user, apiId)
      external <- format.toExternalADM(internal)
    } yield external
  }

  /**
   * Updates a project, identified by its [[ProjectIri]].
   *
   * @param id        the [[ProjectIri]] of the project
   * @param updateReq the [[ProjectUpdateRequest]]
   * @param user      the [[User]] making the request
   * @return
   *     '''success''': information about the project as a [[ProjectOperationResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def updateProject(
    id: IriIdentifier,
    updateReq: ProjectUpdateRequest,
    user: User,
  ): Task[ProjectOperationResponseADM] = for {
    internal <- Random.nextUUID.flatMap(responder.changeBasicInformationRequestADM(id.value, updateReq, user, _))
    external <- format.toExternalADM(internal)
  } yield external

  /**
   * Returns all data of a specific project, identified by its [[ProjectIri]].
   *
   * @param id   the [[IriIdentifier]] of the project
   * @param user the [[User]] making the request
   * @return
   *     '''success''': data of the project as [[ProjectDataGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[IriIdentifier]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def getAllProjectData(id: IriIdentifier, user: User): Task[ProjectDataGetResponseADM] =
    for {
      project <-
        knoraProjectService.findById(id.value).some.orElseFail(NotFoundException(s"Project ${id.value} not found."))
      _      <- permissionService.ensureSystemAdminOrProjectAdmin(user, project)
      result <- projectExportService.exportProjectTriples(project).map(_.toFile.toPath)
    } yield ProjectDataGetResponseADM(result)

  /**
   * Returns all project members of a specific project, identified by its [[ProjectIdentifierADM]].
   *
   * @param id   the [[ProjectIdentifierADM]] of the project
   * @param user the [[User]] making the request
   * @return
   *     '''success''': list of project members as [[ProjectMembersGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIdentifierADM]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def getProjectMembers(user: User, id: ProjectIdentifierADM): Task[ProjectMembersGetResponseADM] = for {
    internal <- responder.projectMembersGetRequestADM(id, user)
    external <- format.toExternalADM(internal)
  } yield external

  /**
   * Returns all project admins of a specific project, identified by its [[ProjectIdentifierADM]].
   *
   * @param id   the [[ProjectIdentifierADM]] of the project
   * @param user the [[User]] making the request
   * @return
   *     '''success''': list of project admins as [[ProjectAdminMembersGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIdentifierADM]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def getProjectAdminMembers(
    user: User,
    id: ProjectIdentifierADM,
  ): Task[ProjectAdminMembersGetResponseADM] = for {
    internal <- responder.projectAdminMembersGetRequestADM(id, user)
    external <- format.toExternalADM(internal)
  } yield external

  /**
   * Returns all keywords of all projects.
   *
   * @return
   *     '''success''': list of all keywords as a [[ProjectsKeywordsGetResponse]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project was found
   */
  def listAllKeywords(): Task[ProjectsKeywordsGetResponse] = for {
    internal <- responder.projectsKeywordsGetRequestADM()
    external <- format.toExternal(internal)
  } yield external

  /**
   * Returns all keywords of a specific project, identified by its [[ProjectIri]].
   *
   * @param iri      the [[ProjectIri]] of the project
   * @return
   *     '''success''': ist of all keywords as a [[ProjectKeywordsGetResponse]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]] can be found
   */
  def getKeywordsByProjectIri(iri: ProjectIri): Task[ProjectKeywordsGetResponse] = for {
    internal <- responder.projectKeywordsGetRequestADM(iri)
    external <- format.toExternal(internal)
  } yield external

  /**
   * Returns the restricted view settings of a specific project, identified by its [[ProjectIri]].
   *
   * @param id      the [[ProjectIdentifierADM]] of the project
   * @return
   *     '''success''': the restricted view settings as [[ProjectRestrictedViewSettingsGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]] can be found
   */
  def getProjectRestrictedViewSettings(id: ProjectIdentifierADM): Task[ProjectRestrictedViewSettingsGetResponseADM] =
    for {
      project  <- knoraProjectService.findById(id).someOrFail(NotFoundException(s"Project '${getId(id)}' not found."))
      external <- format.toExternal(ProjectRestrictedViewSettingsGetResponseADM.from(project.restrictedView))
    } yield external

  /**
   * Sets project's restricted view settings.
   *
   * @param id   The project's id represented by iri, shortcode or shortname.
   * @param user Requesting user.
   * @param req  Contains the values to be set.
   * @return [[RestrictedViewResponse]].
   */
  def updateProjectRestrictedViewSettings(
    id: ProjectIdentifierADM,
    user: User,
    req: SetRestrictedViewRequest,
  ): Task[RestrictedViewResponse] =
    for {
      project        <- knoraProjectService.findById(id).someOrFail(NotFoundException(s"Project '${getId(id)}' not found."))
      _              <- permissionService.ensureSystemAdminOrProjectAdmin(user, project)
      restrictedView <- req.toRestrictedView
      newSettings    <- knoraProjectService.setProjectRestrictedView(project, restrictedView)
    } yield RestrictedViewResponse.from(newSettings)

  def exportProject(id: Shortcode, user: User): Task[Unit] = for {
    _       <- permissionService.ensureSystemAdmin(user)
    project <- knoraProjectService.findByShortcode(id).someOrFail(NotFoundException(s"Project $id not found."))
    _       <- projectExportService.exportProject(project).logError.forkDaemon
  } yield ()

  def exportProjectAwaiting(shortcode: Shortcode, user: User): Task[ProjectExportInfoResponse] = for {
    _ <- permissionService.ensureSystemAdmin(user)
    project <- knoraProjectService
                 .findByShortcode(shortcode)
                 .someOrFail(NotFoundException(s"Project ${shortcode.value} not found."))
    exportInfo <- projectExportService.exportProject(project).logError
  } yield exportInfo

  def importProject(
    shortcode: Shortcode,
    user: User,
  ): Task[ProjectImportResponse] = for {
    _ <- permissionService.ensureSystemAdmin(user)
    path <-
      projectImportService
        .importProject(shortcode, user)
        .flatMap {
          case Some(export) => export.toAbsolutePath.map(_.toString)
          case None         => ZIO.fail(NotFoundException(s"Project export for ${shortcode.value} not found."))
        }
  } yield ProjectImportResponse(path)

  def listExports(user: User): Task[Chunk[ProjectExportInfoResponse]] = for {
    _       <- permissionService.ensureSystemAdmin(user)
    exports <- projectExportService.listExports().map(_.map(ProjectExportInfoResponse(_)))
  } yield exports
}

object ProjectRestService {
  val layer = ZLayer.derive[ProjectRestService]
}
