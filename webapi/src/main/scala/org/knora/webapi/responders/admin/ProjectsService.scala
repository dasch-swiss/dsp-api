/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio._

import dsp.valueobjects.Iri.ProjectIri
import dsp.valueobjects.Project
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.responders.ActorToZioBridge

trait ProjectsService {
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

final case class ProjectsServiceLive(bridge: ActorToZioBridge) extends ProjectsService {

  /**
   * Returns all projects as a [[ProjectsGetResponseADM]].
   *
   * @return
   *     '''success''': information about the projects as a [[ProjectsGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project was found
   */
  def getProjectsADMRequest(): Task[ProjectsGetResponseADM] =
    bridge.askAppActor(ProjectsGetRequestADM())

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
    bridge.askAppActor(ProjectGetRequestADM(identifier))

  /**
   * Creates a project from the given payload.
   *
   * @param payload         the [[ProjectCreatePayloadADM]] from which to create the project
   * @param requestingUser  the [[UserADM]] making the request
   * @return
   *     '''success''': information about the created project as a [[ProjectOperationResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]]
   *                    can be found, if one was provided with the [[ProjectCreatePayloadADM]]
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def createProjectADMRequest(
    payload: ProjectCreatePayloadADM,
    requestingUser: UserADM
  ): Task[ProjectOperationResponseADM] = for {
    random      <- ZIO.random
    requestUuid <- random.nextUUID
    request      = ProjectCreateRequestADM(payload, requestingUser, requestUuid)
    response    <- bridge.askAppActor[ProjectOperationResponseADM](request)
  } yield response

  /**
   * Deletes the project by its [[ProjectIri]].
   *
   * @param projectIri           the [[ProjectIri]] of the project
   * @param requestingUser       the [[UserADM]] making the request
   * @return
   *     '''success''': a [[ProjectOperationResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def deleteProject(projectIri: ProjectIri, requestingUser: UserADM): Task[ProjectOperationResponseADM] = {
    val projectStatus =
      Project.ProjectStatus.make(false).getOrElse(throw new IllegalArgumentException("Invalid project status"))
    for {
      random      <- ZIO.random
      requestUuid <- random.nextUUID
      response <- bridge.askAppActor[ProjectOperationResponseADM](
                    ProjectChangeRequestADM(
                      projectIri = projectIri,
                      projectUpdatePayload = ProjectUpdatePayloadADM(status = Some(projectStatus)),
                      requestingUser = requestingUser,
                      apiRequestID = requestUuid
                    )
                  )
    } yield response
  }

  /**
   * Updates a project, identified by its [[ProjectIri]].
   *
   * @param projectIri           the [[ProjectIri]] of the project
   * @param payload              the [[ProjectUpdatePayloadADM]]
   * @param requestingUser       the [[UserADM]] making the request
   * @return
   *     '''success''': information about the project as a [[ProjectOperationResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def updateProject(
    projectIri: ProjectIri,
    payload: ProjectUpdatePayloadADM,
    requestingUser: UserADM
  ): Task[ProjectOperationResponseADM] = for {
    random      <- ZIO.random
    requestUuid <- random.nextUUID
    request = ProjectChangeRequestADM(
                projectIri = projectIri,
                projectUpdatePayload = payload,
                requestingUser = requestingUser,
                apiRequestID = requestUuid
              )
    response <- bridge.askAppActor[ProjectOperationResponseADM](request)
  } yield response

  /**
   * Returns all data of a specific project, identified by its [[ProjectIri]].
   *
   * @param projectIdentifier    the [[IriIdentifier]] of the project
   * @param requestingUser       the [[UserADM]] making the request
   * @return
   *     '''success''': data of the project as [[ProjectDataGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[IriIdentifier]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def getAllProjectData(
    projectIdentifier: IriIdentifier,
    requestingUser: UserADM
  ): Task[ProjectDataGetResponseADM] =
    bridge.askAppActor(ProjectDataGetRequestADM(projectIdentifier, requestingUser))

  /**
   * Returns all project members of a specific project, identified by its [[ProjectIdentifierADM]].
   *
   * @param projectIdentifier    the [[ProjectIdentifierADM]] of the project
   * @param requestingUser       the [[UserADM]] making the request
   * @return
   *     '''success''': list of project members as [[ProjectMembersGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIdentifierADM]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def getProjectMembers(
    projectIdentifier: ProjectIdentifierADM,
    requestingUser: UserADM
  ): Task[ProjectMembersGetResponseADM] =
    bridge.askAppActor(ProjectMembersGetRequestADM(projectIdentifier, requestingUser))

  /**
   * Returns all project admins of a specific project, identified by its [[ProjectIdentifierADM]].
   *
   * @param projectIdentifier    the [[ProjectIdentifierADM]] of the project
   * @param requestingUser       the [[UserADM]] making the request
   * @return
   *     '''success''': list of project admins as [[ProjectAdminMembersGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIdentifierADM]] can be found
   *                    [[dsp.errors.ForbiddenException]] when the requesting user is not allowed to perform the operation
   */
  def getProjectAdmins(
    projectIdentifier: ProjectIdentifierADM,
    requestingUser: UserADM
  ): Task[ProjectAdminMembersGetResponseADM] =
    bridge.askAppActor(ProjectAdminMembersGetRequestADM(projectIdentifier, requestingUser))

  /**
   * Returns all keywords of all projects.
   *
   * @return
   *     '''success''': list of all keywords as a [[ProjectsKeywordsGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project was found
   */
  def getKeywords(): Task[ProjectsKeywordsGetResponseADM] =
    bridge.askAppActor(ProjectsKeywordsGetRequestADM())

  /**
   * Returns all keywords of a specific project, identified by its [[ProjectIri]].
   *
   * @param projectIri      the [[ProjectIri]] of the project
   * @return
   *     '''success''': ist of all keywords as a [[ProjectKeywordsGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]] can be found
   */
  def getKeywordsByProjectIri(
    projectIri: ProjectIri
  ): Task[ProjectKeywordsGetResponseADM] =
    bridge.askAppActor(ProjectKeywordsGetRequestADM(projectIri))

  /**
   * Returns the restricted view settings of a specific project, identified by its [[ProjectIri]].
   *
   * @param projectIri      the [[ProjectIri]] of the project
   * @return
   *     '''success''': the restricted view settings as [[ProjectRestrictedViewSettingsGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given [[ProjectIri]] can be found
   */
  def getProjectRestrictedViewSettings(
    identifier: ProjectIdentifierADM
  ): Task[ProjectRestrictedViewSettingsGetResponseADM] =
    bridge.askAppActor(ProjectRestrictedViewSettingsGetRequestADM(identifier))

}

object ProjectsService {
  val live: URLayer[ActorToZioBridge, ProjectsServiceLive] =
    ZLayer.fromFunction(ProjectsServiceLive.apply _)
}
