/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.*
import zio.macros.accessible

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import dsp.valueobjects.RestrictedViewSize
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.*
import org.knora.webapi.messages.admin.responder.projectsmessages.*
import org.knora.webapi.responders.admin.ProjectsResponderADM
import org.knora.webapi.slice.admin.api.model.ProjectDataGetResponseADM
import org.knora.webapi.slice.admin.api.model.ProjectExportInfoResponse
import org.knora.webapi.slice.admin.api.model.ProjectImportResponse
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectSetRestrictedViewSizeRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectUpdateRequest
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Status
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.domain.service.ProjectExportService
import org.knora.webapi.slice.admin.domain.service.ProjectImportService
import org.knora.webapi.slice.common.api.AuthorizationRestService

@accessible
trait ProjectADMRestService {

  def listAllProjects(): Task[ProjectsGetResponseADM]

  def findProject(id: ProjectIdentifierADM): Task[ProjectGetResponseADM]

  def createProject(createReq: ProjectCreateRequest, user: User): Task[ProjectOperationResponseADM]

  def updateProject(
    id: IriIdentifier,
    updateReq: ProjectUpdateRequest,
    user: User
  ): Task[ProjectOperationResponseADM]

  def deleteProject(id: IriIdentifier, user: User): Task[ProjectOperationResponseADM]

  def getAllProjectData(id: IriIdentifier, user: User): Task[ProjectDataGetResponseADM]

  def exportProject(shortcode: String, user: User): Task[Unit]
  def exportProject(id: ShortcodeIdentifier, user: User): Task[Unit]

  def importProject(shortcode: String, user: User): Task[ProjectImportResponse]

  def importProject(shortcode: ShortcodeIdentifier, user: User): Task[ProjectImportResponse] =
    importProject(shortcode.value.value, user)

  def listExports(user: User): Task[Chunk[ProjectExportInfoResponse]]

  def getProjectMembers(user: User, id: ProjectIdentifierADM): Task[ProjectMembersGetResponseADM]

  def getProjectAdminMembers(user: User, id: ProjectIdentifierADM): Task[ProjectAdminMembersGetResponseADM]

  def listAllKeywords(): Task[ProjectsKeywordsGetResponseADM]

  def getKeywordsByProjectIri(iri: ProjectIri): Task[ProjectKeywordsGetResponseADM]

  def getProjectRestrictedViewSettings(id: ProjectIdentifierADM): Task[ProjectRestrictedViewSettingsGetResponseADM]

  def updateProjectRestrictedViewSettings(
    id: ProjectIdentifierADM,
    user: User,
    setSizeReq: ProjectSetRestrictedViewSizeRequest
  ): Task[ProjectRestrictedViewSizeResponseADM]
}

final case class ProjectsADMRestServiceLive(
  responder: ProjectsResponderADM,
  projectRepo: KnoraProjectRepo,
  projectExportService: ProjectExportService,
  projectImportService: ProjectImportService,
  permissionService: AuthorizationRestService
) extends ProjectADMRestService {

  /**
   * Returns all projects as a [[ProjectsGetResponseADM]].
   *
   * @return
   *     '''success''': information about the projects as a [[ProjectsGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project was found
   */
  def listAllProjects(): Task[ProjectsGetResponseADM] =
    responder.projectsGetRequestADM(withSystemProjects = false)

  /**
   * Finds the project by its [[ProjectIdentifierADM]] and returns the information as a [[ProjectGetResponseADM]].
   *
   * @param id           a [[ProjectIdentifierADM]] instance
   * @return
   *     '''success''': information about the project as a [[ProjectGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIdentifierADM]] can be found
   */
  def findProject(id: ProjectIdentifierADM): Task[ProjectGetResponseADM] = responder.getSingleProjectADMRequest(id)

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
  def createProject(createReq: ProjectCreateRequest, user: User): Task[ProjectOperationResponseADM] =
    ZIO.random.flatMap(_.nextUUID).flatMap(responder.projectCreateRequestADM(createReq, user, _))

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
      response <- responder.changeBasicInformationRequestADM(id.value, updatePayload, user, apiId)
    } yield response
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
    user: User
  ): Task[ProjectOperationResponseADM] =
    Random.nextUUID.flatMap(responder.changeBasicInformationRequestADM(id.value, updateReq, user, _))

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
      project <- projectRepo.findById(id).some.orElseFail(NotFoundException(s"Project ${id.value} not found."))
      _       <- permissionService.ensureSystemAdminOrProjectAdmin(user, project)
      result  <- projectExportService.exportProjectTriples(project).map(_.toFile.toPath)
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
  def getProjectMembers(user: User, id: ProjectIdentifierADM): Task[ProjectMembersGetResponseADM] =
    responder.projectMembersGetRequestADM(id, user)

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
    id: ProjectIdentifierADM
  ): Task[ProjectAdminMembersGetResponseADM] =
    responder.projectAdminMembersGetRequestADM(id, user)

  /**
   * Returns all keywords of all projects.
   *
   * @return
   *     '''success''': list of all keywords as a [[ProjectsKeywordsGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project was found
   */
  def listAllKeywords(): Task[ProjectsKeywordsGetResponseADM] = responder.projectsKeywordsGetRequestADM()

  /**
   * Returns all keywords of a specific project, identified by its [[ProjectIri]].
   *
   * @param iri      the [[ProjectIri]] of the project
   * @return
   *     '''success''': ist of all keywords as a [[ProjectKeywordsGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]] can be found
   */
  def getKeywordsByProjectIri(iri: ProjectIri): Task[ProjectKeywordsGetResponseADM] =
    responder.projectKeywordsGetRequestADM(iri)

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
    responder.projectRestrictedViewSettingsGetRequestADM(id)

  /**
   * Sets project's restricted view settings.
   *
   * @param id the project's id represented by iri, shortcode or shortname,
   * @param user requesting user,
   * @param setSizeReq value to be set,
   * @return [[ProjectRestrictedViewSizeResponseADM]].
   */
  override def updateProjectRestrictedViewSettings(
    id: ProjectIdentifierADM,
    user: User,
    setSizeReq: ProjectSetRestrictedViewSizeRequest
  ): Task[ProjectRestrictedViewSizeResponseADM] =
    for {
      size    <- ZIO.fromEither(RestrictedViewSize.make(setSizeReq.size)).mapError(BadRequestException(_))
      project <- projectRepo.findById(id).someOrFail(NotFoundException(s"Project '${getId(id)}' not found."))
      _       <- permissionService.ensureSystemAdminOrProjectAdmin(user, project)
      _       <- projectRepo.setProjectRestrictedViewSize(project, size)
    } yield ProjectRestrictedViewSizeResponseADM(size)

  override def exportProject(shortcodeStr: String, user: User): Task[Unit] =
    convertStringToShortcodeId(shortcodeStr).flatMap(exportProject(_, user))

  override def exportProject(id: ShortcodeIdentifier, user: User): Task[Unit] = for {
    _       <- permissionService.ensureSystemAdmin(user)
    project <- projectRepo.findById(id).someOrFail(NotFoundException(s"Project $id not found."))
    _       <- projectExportService.exportProject(project).logError.forkDaemon
  } yield ()

  private def convertStringToShortcodeId(shortcodeStr: String): IO[BadRequestException, ShortcodeIdentifier] =
    Shortcode.from(shortcodeStr).toZIO.mapBoth(err => BadRequestException(err.msg), ShortcodeIdentifier.from)

  override def importProject(
    shortcodeStr: String,
    user: User
  ): Task[ProjectImportResponse] = for {
    _         <- permissionService.ensureSystemAdmin(user)
    shortcode <- convertStringToShortcodeId(shortcodeStr)
    path <-
      projectImportService
        .importProject(shortcode.value, user)
        .flatMap {
          case Some(export) => export.toAbsolutePath.map(_.toString)
          case None         => ZIO.fail(NotFoundException(s"Project export for ${shortcode.value} not found."))
        }
  } yield ProjectImportResponse(path)

  override def listExports(user: User): Task[Chunk[ProjectExportInfoResponse]] = for {
    _       <- permissionService.ensureSystemAdmin(user)
    exports <- projectExportService.listExports().map(_.map(ProjectExportInfoResponse(_)))
  } yield exports
}

object ProjectsADMRestServiceLive {
  val layer: URLayer[
    ProjectsResponderADM & KnoraProjectRepo & ProjectExportService & ProjectImportService & AuthorizationRestService,
    ProjectsADMRestServiceLive
  ] = ZLayer.fromFunction(ProjectsADMRestServiceLive.apply _)
}
