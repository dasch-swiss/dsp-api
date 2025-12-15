/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin.service

import zio.*
import zio.nio.file.Files
import zio.stream.ZStream

import scala.annotation.unused

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import org.knora.webapi.config.Features
import org.knora.webapi.responders.admin.PermissionsResponder
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortname
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Status
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.ProjectEraseService
import org.knora.webapi.slice.admin.domain.service.ProjectExportService
import org.knora.webapi.slice.admin.domain.service.ProjectImportService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.api.admin.model.*
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.ProjectCreateRequest
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.ProjectUpdateRequest
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.RestrictedViewResponse
import org.knora.webapi.slice.api.admin.model.ProjectsEndpointsRequestsAndResponses.SetRestrictedViewRequest
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.api.KnoraResponseRenderer
import org.knora.webapi.slice.ontology.repo.service.OntologyCache
import org.knora.webapi.store.triplestore.api.TriplestoreService

final class ProjectRestService(
  format: KnoraResponseRenderer,
  projectService: ProjectService,
  knoraProjectService: KnoraProjectService,
  permissionResponder: PermissionsResponder,
  projectEraseService: ProjectEraseService,
  projectExportService: ProjectExportService,
  projectImportService: ProjectImportService,
  ontologyCache: OntologyCache,
  userService: UserService,
  auth: AuthorizationRestService,
  features: Features,
  triplestore: TriplestoreService,
) {

  /**
   * Returns all projects as a [[ProjectsGetResponse]].
   *
   * @return
   *     '''success''': information about the projects as a [[ProjectsGetResponse]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project was found
   */
  def listAllProjects(@unused ignored: Unit): Task[ProjectsGetResponse] = for {
    internal <- projectService.findAllRegularProjects
    projects  = internal.filter(_.id.isRegularProjectIri)
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
      .someOrFail(NotFoundException(s"Project '${id.value}' not found."))
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
  def createProject(user: User)(createReq: ProjectCreateRequest): Task[ProjectOperationResponseADM] = for {
    _ <- auth.ensureSystemAdmin(user)
    _ <- ZIO.fail(BadRequestException("Project description is required.")).when(createReq.description.isEmpty)
    _ <- ZIO
           .fail(
             BadRequestException(s"IRI: '${createReq.id.map(_.value).getOrElse("")}' already exists, try another one."),
           )
           .whenZIO(
             createReq.id match {
               case Some(id) => knoraProjectService.existsById(id)
               case None     => ZIO.succeed(false)
             },
           )
    internal <- projectService.createProject(createReq).map(ProjectOperationResponseADM.apply)
    _        <- permissionResponder.createPermissionsForAdminsAndMembersOfNewProject(internal.project.id)
    external <- format.toExternal(internal)
  } yield external

  /**
   * Deletes the project by its [[ProjectIri]].
   *
   * @param projectIri  the [[ProjectIri]] of the project
   * @param user        the [[User]] making the request
   * @return
   *     '''success''': a [[ProjectOperationResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def deleteProject(user: User)(projectIri: ProjectIri): Task[ProjectOperationResponseADM] =
    for {
      project  <- auth.ensureSystemAdminOrProjectAdminById(user, projectIri)
      internal <- projectService
                    .updateProject(project, ProjectUpdateRequest(status = Some(Status.Inactive)))
                    .map(ProjectOperationResponseADM.apply)
      external <- format.toExternal(internal)
    } yield external

  def eraseProject(user: User)(shortcode: Shortcode, keepAssets: Boolean): Task[ProjectOperationResponseADM] =
    for {
      _ <- auth.ensureSystemAdmin(user)
      _ <- ZIO.unless(features.allowEraseProjects)(
             ZIO.fail(ForbiddenException("The feature to erase projects is not enabled.")),
           )
      internal <- projectService
                    .findByShortcode(shortcode)
                    .someOrFail(NotFoundException(s"$shortcode not found"))
      project <- knoraProjectService
                   .findByShortcode(shortcode)
                   .someOrFail(NotFoundException(s"$shortcode not found"))
      _        <- ZIO.logInfo(s"${user.userIri} erases project $shortcode")
      _        <- projectEraseService.eraseProject(project, keepAssets)
      external <- format.toExternal(ProjectOperationResponseADM(internal))
      _        <- ZIO.when(features.triggerCompactionAfterProjectErasure)(triplestore.compact())
    } yield external

  /**
   * Updates a project, identified by its [[ProjectIri]].
   *
   * @param user       the [[User]] making the request
   * @param projectIri the [[ProjectIri]] of the project
   * @param updateReq  the [[ProjectUpdateRequest]]
   * @return
   *     '''success''': information about the project as a [[ProjectOperationResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def updateProject(user: User)(
    projectIri: ProjectIri,
    updateReq: ProjectUpdateRequest,
  ): Task[ProjectOperationResponseADM] = for {
    project <- knoraProjectService
                 .findById(projectIri)
                 .someOrFail(NotFoundException(s"Project '${projectIri.value}' not found."))
    _        <- auth.ensureSystemAdminOrProjectAdmin(user, project)
    internal <- projectService.updateProject(project, updateReq).map(ProjectOperationResponseADM.apply)
    external <- format.toExternal(internal)
  } yield external

  /**
   * Returns all data of a specific project, identified by its [[ProjectIri]].
   *
   * @param id   the [[IriIdentifier]] of the project
   * @param user the [[User]] making the request
   * @return
   *     '''success''': data of the project for download as a stream of bytes, along with a suggested filename and a MIME type
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[IriIdentifier]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def getAllProjectData(user: User)(id: ProjectIri): Task[(String, String, ZStream[Any, Throwable, Byte])] =
    for {
      project <- auth.ensureSystemAdminOrProjectAdminById(user, id)
      path    <- projectExportService.exportProjectTriples(project)
      stream   = ZStream.fromPath(path.toFile.toPath).ensuringWith(_ => ZIO.attempt(Files.deleteIfExists(path)).ignore)
    } yield (s"attachment; filename=project-data.trig", "application/octet-stream", stream)

  def getProjectMembersById(user: User)(id: ProjectIri): Task[ProjectMembersGetResponseADM] =
    auth.ensureSystemAdminOrProjectAdminById(user, id).flatMap(findProjectMembers)
  def getProjectMembersByShortcode(user: User)(id: Shortcode): Task[ProjectMembersGetResponseADM] =
    auth.ensureSystemAdminOrProjectAdminByShortcode(user, id).flatMap(findProjectMembers)
  def getProjectMembersByShortname(user: User)(id: Shortname): Task[ProjectMembersGetResponseADM] =
    auth.ensureSystemAdminOrProjectAdminByShortname(user, id).flatMap(findProjectMembers)
  private def findProjectMembers(project: KnoraProject) =
    userService.findByProjectMembership(project).map(ProjectMembersGetResponseADM.from).flatMap(format.toExternal)

  def getProjectAdminMembersById(user: User)(id: ProjectIri): Task[ProjectAdminMembersGetResponseADM] =
    auth.ensureSystemAdminOrProjectAdminById(user, id).flatMap(findProjectAdminMembers)
  def getProjectAdminMembersByShortcode(user: User)(id: Shortcode): Task[ProjectAdminMembersGetResponseADM] =
    auth.ensureSystemAdminOrProjectAdminByShortcode(user, id).flatMap(findProjectAdminMembers)
  def getProjectAdminMembersByShortname(user: User)(id: Shortname): Task[ProjectAdminMembersGetResponseADM] =
    auth.ensureSystemAdminOrProjectAdminByShortname(user, id).flatMap(findProjectAdminMembers)
  private def findProjectAdminMembers(project: KnoraProject) =
    userService
      .findByProjectAdminMembership(project)
      .map(ProjectAdminMembersGetResponseADM.from)
      .flatMap(format.toExternal)

  /**
   * Returns all keywords of all projects.
   *
   * @return
   *     '''success''': list of all keywords as a [[ProjectsKeywordsGetResponse]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project was found
   */
  def listAllKeywords(@unused ignored: Unit): Task[ProjectsKeywordsGetResponse] = for {
    projects <- knoraProjectService.findAll()
    internal  = ProjectsKeywordsGetResponse(projects.flatMap(_.keywords.map(_.value)).distinct.sorted)
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
    internal <- knoraProjectService
                  .findById(iri)
                  .someOrFail(NotFoundException(s"Project '${iri.value}' not found."))
                  .map(_.keywords.map(_.value))
                  .map(ProjectKeywordsGetResponse.apply)
    external <- format.toExternal(internal)
  } yield external

  def getProjectRestrictedViewSettingsById(id: ProjectIri): Task[ProjectRestrictedViewSettingsGetResponseADM] =
    for {
      project <-
        knoraProjectService.findById(id).someOrFail(NotFoundException(s"Project with id ${id.value} not found."))
      external <- format.toExternal(ProjectRestrictedViewSettingsGetResponseADM.from(project.restrictedView))
    } yield external

  def getProjectRestrictedViewSettingsByShortcode(id: Shortcode): Task[ProjectRestrictedViewSettingsGetResponseADM] =
    for {
      project <- knoraProjectService
                   .findByShortcode(id)
                   .someOrFail(NotFoundException(s"Project with shortcode ${id.value} not found."))
      external <- format.toExternal(ProjectRestrictedViewSettingsGetResponseADM.from(project.restrictedView))
    } yield external

  def getProjectRestrictedViewSettingsByShortname(id: Shortname): Task[ProjectRestrictedViewSettingsGetResponseADM] =
    for {
      project <- knoraProjectService
                   .findByShortname(id)
                   .someOrFail(NotFoundException(s"Project with shortname ${id.value} not found."))
      external <- format.toExternal(ProjectRestrictedViewSettingsGetResponseADM.from(project.restrictedView))
    } yield external

  def updateProjectRestrictedViewSettingsByShortcode(user: User)(
    id: Shortcode,
    req: SetRestrictedViewRequest,
  ): Task[RestrictedViewResponse] =
    auth.ensureSystemAdminOrProjectAdminByShortcode(user, id).flatMap(updateRestrictedViewSettings(_, req))

  def updateProjectRestrictedViewSettingsById(user: User)(
    id: ProjectIri,
    req: SetRestrictedViewRequest,
  ): Task[RestrictedViewResponse] =
    auth.ensureSystemAdminOrProjectAdminById(user, id).flatMap(updateRestrictedViewSettings(_, req))

  private def updateRestrictedViewSettings(project: KnoraProject, req: SetRestrictedViewRequest) =
    for {
      restrictedView <- req.toRestrictedView
      newSettings    <- knoraProjectService.setProjectRestrictedView(project, restrictedView)
    } yield RestrictedViewResponse.from(newSettings)

  def exportProject(user: User)(id: Shortcode): Task[Unit] = for {
    _       <- auth.ensureSystemAdmin(user)
    project <- knoraProjectService.findByShortcode(id).someOrFail(NotFoundException(s"Project $id not found."))
    _       <- projectExportService.exportProject(project).logError.forkDaemon
  } yield ()

  def exportProjectAwaiting(user: User)(shortcode: Shortcode): Task[ProjectExportInfoResponse] = for {
    _       <- auth.ensureSystemAdmin(user)
    project <- knoraProjectService
                 .findByShortcode(shortcode)
                 .someOrFail(NotFoundException(s"Project ${shortcode.value} not found."))
    exportInfo <- projectExportService.exportProject(project).logError
  } yield exportInfo

  def importProject(user: User)(shortcode: Shortcode): Task[ProjectImportResponse] = for {
    _ <- auth.ensureSystemAdmin(user)
    _ <- projectExportService
           .findByShortcode(shortcode)
           .someOrFail(NotFoundException(s"Project export for $shortcode not found."))
    _ <- knoraProjectService
           .findByShortcode(shortcode)
           .some
           .flatMap(projectEraseService.eraseProject(_, keepAssets = false).asSomeError)
           .unsome
    path <- projectImportService
              .importProject(shortcode)
              .someOrFail(IllegalStateException(s"Project export for $shortcode not found."))
              .flatMap(_.toAbsolutePath)
    _ <- ontologyCache.refreshCache()
  } yield ProjectImportResponse(path.toString)

  def listExports(user: User)(@unused ignored: Unit): Task[Chunk[ProjectExportInfoResponse]] = for {
    _       <- auth.ensureSystemAdmin(user)
    exports <- projectExportService.listExports().map(_.map(ProjectExportInfoResponse(_)))
  } yield exports
}

object ProjectRestService {
  val layer = ZLayer.derive[ProjectRestService]
}
