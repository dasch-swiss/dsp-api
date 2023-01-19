/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio._

import org.knora.webapi.IRI
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
  def deleteProject(iri: IRI, requestingUser: UserADM): Task[ProjectOperationResponseADM]
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
  def deleteProject(projectIri: IRI, requestingUser: UserADM): Task[ProjectOperationResponseADM] =
    for {
      random      <- ZIO.random
      requestUuid <- random.nextUUID
      response <- bridge.askAppActor[ProjectOperationResponseADM](
                    ProjectChangeRequestADM(
                      projectIri = projectIri,
                      changeProjectRequest = ChangeProjectApiRequestADM(status = Some(false)),
                      requestingUser = requestingUser,
                      apiRequestID = requestUuid
                    )
                  )
    } yield response
}

object ProjectsService {
  val live: URLayer[ActorToZioBridge, ProjectsServiceLive] =
    ZLayer.fromFunction(ProjectsServiceLive.apply _)
}
