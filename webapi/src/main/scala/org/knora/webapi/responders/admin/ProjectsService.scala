/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio._

import dsp.errors.BadRequestException
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
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given IRI can be found
   */
  def getSingleProjectADMRequest(identifier: ProjectIdentifierADM): Task[ProjectGetResponseADM] =
    bridge.askAppActor(ProjectGetRequestADM(identifier))

  /**
   * Creates a project
   *
   * @param payload   a [[CreateProjectPayload]] instance
   * @return
   *     '''success''': information about the project as a [[ProjectOperationResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given IRI can be found
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
   * Deletes the project by its [[IRI]].
   *
   * @param projectIri           the [[IRI]] of the project
   * @param requestingUser       the user making the request.
   * @return
   *     '''success''': a [[ProjectOperationResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given IRI can be found
   *                    [[dsp.errors.ForbiddenException]] when the user is not allowed to perform the operation
   */
  def deleteProject(projectIri: ProjectIri, requestingUser: UserADM): Task[ProjectOperationResponseADM] = {
    val projectStatus = Project.ProjectStatus.make(false).getOrElse(throw BadRequestException("Invalid project status"))
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
   * Updates a project
   *
   * @param projectIri           the [[IRI]] of the project
   * @param payload              a [[ProjectUpdatePayloadADM]] instance
   * @param requestingUser       the user making the request.
   * @return
   *     '''success''': information about the project as a [[ProjectOperationResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given IRI can be found
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

  def getAllProjectData(
    projectIdentifier: IriIdentifier,
    requestingUser: UserADM
  ): Task[ProjectDataGetResponseADM] =
    bridge.askAppActor(ProjectDataGetRequestADM(projectIdentifier, requestingUser))

  def getProjectMembers(
    projectIdentifier: ProjectIdentifierADM,
    requestingUser: UserADM
  ): Task[ProjectMembersGetResponseADM] =
    bridge.askAppActor(ProjectMembersGetRequestADM(projectIdentifier, requestingUser))
  def getProjectAdmins(
    projectIdentifier: ProjectIdentifierADM,
    requestingUser: UserADM
  ): Task[ProjectAdminMembersGetResponseADM] =
    bridge.askAppActor(ProjectAdminMembersGetRequestADM(projectIdentifier, requestingUser))

  /**
   * Returns all keywords of all projects as a [[ProjectsKeywordsGetResponseADM]].
   *
   * @return
   *     '''success''': list of all keywords as a [[ProjectsKeywordsGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project was found
   */
  def getKeywords(): Task[ProjectsKeywordsGetResponseADM] =
    bridge.askAppActor(ProjectsKeywordsGetRequestADM())

  def getKeywordsByProjectIri(
    projectIri: ProjectIri
  ): Task[ProjectKeywordsGetResponseADM] =
    bridge.askAppActor(ProjectKeywordsGetRequestADM(projectIri))
}

object ProjectsService {
  val live: URLayer[ActorToZioBridge, ProjectsServiceLive] =
    ZLayer.fromFunction(ProjectsServiceLive.apply _)
}
