/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api.service

import zio.Task
import zio._
import zio.macros.accessible

import java.nio.file.Files

import dsp.errors.BadRequestException
import dsp.errors.NotFoundException
import dsp.valueobjects.Iri.ProjectIri
import dsp.valueobjects.Project
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.responders.admin.ProjectsResponderADM
import org.knora.webapi.slice.admin.domain.service.KnoraProjectRepo
import org.knora.webapi.slice.admin.domain.service.ProjectExportService

@accessible
trait ProjectADMRestService {
  def getProjectsADMRequest(): Task[ProjectsGetResponseADM]
  def getSingleProjectADMRequest(identifier: ProjectIdentifierADM): Task[ProjectGetResponseADM]
  def createProjectADMRequest(
    payload: ProjectCreatePayloadADM,
    requestingUser: UserADM
  ): Task[ProjectOperationResponseADM]
  def deleteProject(projectIri: ProjectIri, requestingUser: UserADM): Task[ProjectOperationResponseADM]
  def updateProject(
    projectIri: ProjectIri,
    payload: ProjectUpdatePayloadADM,
    requestingUser: UserADM
  ): Task[ProjectOperationResponseADM]
  def getAllProjectData(
    iriIdentifier: IriIdentifier,
    requestingUser: UserADM
  ): Task[ProjectDataGetResponseADM]
  def getProjectMembers(
    projectIdentifier: ProjectIdentifierADM,
    requestingUser: UserADM
  ): Task[ProjectMembersGetResponseADM]
  def getProjectAdmins(
    projectIdentifier: ProjectIdentifierADM,
    requestingUser: UserADM
  ): Task[ProjectAdminMembersGetResponseADM]
  def getKeywords(): Task[ProjectsKeywordsGetResponseADM]
  def getKeywordsByProjectIri(
    projectIri: ProjectIri
  ): Task[ProjectKeywordsGetResponseADM]
  def getProjectRestrictedViewSettings(
    identifier: ProjectIdentifierADM
  ): Task[ProjectRestrictedViewSettingsGetResponseADM]
}

final case class ProjectsADMRestServiceLive(
  responder: ProjectsResponderADM,
  projectRepo: KnoraProjectRepo,
  projectExportService: ProjectExportService,
  permissionService: RestPermissionService
) extends ProjectADMRestService {

  /**
   * Returns all projects as a [[ProjectsGetResponseADM]].
   *
   * @return
   *     '''success''': information about the projects as a [[ProjectsGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project was found
   */
  def getProjectsADMRequest(): Task[ProjectsGetResponseADM] =
    responder.projectsGetRequestADM(withSystemProjects = false)

  /**
   * Finds the project by its [[ProjectIdentifierADM]] and returns the information as a [[ProjectGetResponseADM]].
   *
   * @param identifier           a [[ProjectIdentifierADM]] instance
   * @return
   *     '''success''': information about the project as a [[ProjectGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIdentifierADM]] can be found
   */
  def getSingleProjectADMRequest(identifier: ProjectIdentifierADM): Task[ProjectGetResponseADM] =
    responder.getSingleProjectADMRequest(identifier)

  /**
   * Creates a project from the given payload.
   *
   * @param payload         the [[ProjectCreatePayloadADM]] from which to create the project
   * @param user  the [[UserADM]] making the request
   * @return
   *     '''success''': information about the created project as a [[ProjectOperationResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]]
   *                    can be found, if one was provided with the [[ProjectCreatePayloadADM]]
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def createProjectADMRequest(payload: ProjectCreatePayloadADM, user: UserADM): Task[ProjectOperationResponseADM] =
    ZIO.random.flatMap(_.nextUUID).flatMap(responder.projectCreateRequestADM(payload, user, _))

  /**
   * Deletes the project by its [[ProjectIri]].
   *
   * @param projectIri           the [[ProjectIri]] of the project
   * @param user                 the [[UserADM]] making the request
   * @return
   *     '''success''': a [[ProjectOperationResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def deleteProject(projectIri: ProjectIri, user: UserADM): Task[ProjectOperationResponseADM] =
    for {
      projectStatus <-
        Project.ProjectStatus.make(false).toZIO.orElseFail(BadRequestException("Invalid project status."))
      updatePayload = ProjectUpdatePayloadADM(status = Some(projectStatus))
      response     <- changeBasicInformationRequestADM(projectIri, updatePayload, user)
    } yield response

  private def changeBasicInformationRequestADM(
    projectIri: ProjectIri,
    payload: ProjectUpdatePayloadADM,
    user: UserADM
  ): Task[ProjectOperationResponseADM] =
    for {
      id       <- ZIO.random.flatMap(_.nextUUID)
      response <- responder.changeBasicInformationRequestADM(projectIri, payload, user, id)
    } yield response

  /**
   * Updates a project, identified by its [[ProjectIri]].
   *
   * @param projectIri           the [[ProjectIri]] of the project
   * @param payload              the [[ProjectUpdatePayloadADM]]
   * @param user       the [[UserADM]] making the request
   * @return
   *     '''success''': information about the project as a [[ProjectOperationResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def updateProject(
    projectIri: ProjectIri,
    payload: ProjectUpdatePayloadADM,
    user: UserADM
  ): Task[ProjectOperationResponseADM] = for {
    response <- changeBasicInformationRequestADM(projectIri, payload, user)
  } yield response

  /**
   * Returns all data of a specific project, identified by its [[ProjectIri]].
   *
   * @param id    the [[IriIdentifier]] of the project
   * @param user       the [[UserADM]] making the request
   * @return
   *     '''success''': data of the project as [[ProjectDataGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[IriIdentifier]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def getAllProjectData(id: IriIdentifier, user: UserADM): Task[ProjectDataGetResponseADM] =
    for {
      project <- projectRepo.findById(id).some.orElseFail(NotFoundException(s"Project ${id.value} not found."))
      _       <- permissionService.ensureSystemOrProjectAdmin(user, project)
      tmpFile <- ZIO.attempt(Files.createTempDirectory(project.shortname))
      result  <- projectExportService.exportProjectTriples(project, tmpFile)
    } yield ProjectDataGetResponseADM(result)

  /**
   * Returns all project members of a specific project, identified by its [[ProjectIdentifierADM]].
   *
   * @param id    the [[ProjectIdentifierADM]] of the project
   * @param user       the [[UserADM]] making the request
   * @return
   *     '''success''': list of project members as [[ProjectMembersGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIdentifierADM]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def getProjectMembers(id: ProjectIdentifierADM, user: UserADM): Task[ProjectMembersGetResponseADM] =
    responder.projectMembersGetRequestADM(id, user)

  /**
   * Returns all project admins of a specific project, identified by its [[ProjectIdentifierADM]].
   *
   * @param id    the [[ProjectIdentifierADM]] of the project
   * @param user       the [[UserADM]] making the request
   * @return
   *     '''success''': list of project admins as [[ProjectAdminMembersGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIdentifierADM]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def getProjectAdmins(id: ProjectIdentifierADM, user: UserADM): Task[ProjectAdminMembersGetResponseADM] =
    responder.projectAdminMembersGetRequestADM(id, user)

  /**
   * Returns all keywords of all projects.
   *
   * @return
   *     '''success''': list of all keywords as a [[ProjectsKeywordsGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project was found
   */
  def getKeywords(): Task[ProjectsKeywordsGetResponseADM] = responder.projectsKeywordsGetRequestADM()

  /**
   * Returns all keywords of a specific project, identified by its [[ProjectIri]].
   *
   * @param projectIri      the [[ProjectIri]] of the project
   * @return
   *     '''success''': ist of all keywords as a [[ProjectKeywordsGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]] can be found
   */
  def getKeywordsByProjectIri(projectIri: ProjectIri): Task[ProjectKeywordsGetResponseADM] =
    responder.projectKeywordsGetRequestADM(projectIri)

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

}

object ProjectsADMRestServiceLive {
  val layer: ZLayer[
    ProjectsResponderADM with KnoraProjectRepo with ProjectExportService with RestPermissionService,
    Nothing,
    ProjectsADMRestServiceLive
  ] =
    ZLayer.fromFunction(ProjectsADMRestServiceLive.apply _)
}
