/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio._

import java.util.UUID

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
  ): Task[ProjectOperationResponseADM] = {
    val requestUuid: UUID = UUID.randomUUID()
    bridge.askAppActor(ProjectCreateRequestADM(payload, requestingUser, requestUuid))
  }
}

// object ProjectsServiceMock extends ProjectsService {

//   object GetProjectsEffect extends Eff

//   // ---------------------------

//   private val project =
//     ProjectADM("", "", "", None, Seq(StringLiteralV2("")), Seq.empty, None, Seq.empty, true, false)

//   private val projectCreateProject    = project.copy(id = "createProjectADMRequest")
//   private val projectGetSingleProject = project.copy(id = "getSingleProjectADMRequest")
//   private val projectGetProjects      = project.copy(id = "getProjectsADMRequest")

//   val createProjectResponseAsString    = ProjectOperationResponseADM(projectCreateProject).toJsValue.toString()
//   val getSingleProjectResponseAsString = ProjectGetResponseADM(projectGetSingleProject).toJsValue.toString()
//   val getProjectsResponseAsString      = ProjectsGetResponseADM(Seq(projectGetProjects)).toJsValue.toString()

//   override def getProjectsADMRequest(): Task[ProjectsGetResponseADM] =
//     ZIO.attempt(ProjectsGetResponseADM(Seq(projectGetProjects)))

//   override def getSingleProjectADMRequest(identifier: ProjectIdentifierADM): Task[ProjectGetResponseADM] =
//     ZIO.attempt(ProjectGetResponseADM(projectGetSingleProject))

//   override def createProjectADMRequest(
//     payload: ProjectCreatePayloadADM,
//     requestingUser: UserADM
//   ): Task[ProjectOperationResponseADM] = ZIO.attempt(ProjectOperationResponseADM(projectCreateProject))
// }

object ProjectsService {
  val live: URLayer[ActorToZioBridge, ProjectsServiceLive] = ZLayer.fromFunction(ProjectsServiceLive.apply _)
}
