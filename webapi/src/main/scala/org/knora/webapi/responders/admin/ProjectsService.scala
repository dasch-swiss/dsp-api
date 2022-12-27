package org.knora.webapi.responders.admin
import zio.Task
import zio.URLayer
import zio.ZLayer

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetRequestADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.responders.ActorToZioBridge
import org.knora.webapi.IRI

final case class ProjectsService(bridge: ActorToZioBridge) {

  /**
   * Finds the project by its [[ProjectIdentifierADM]] and returns the information as a [[ProjectGetResponseADM]].
   * Checks permissions whether the [[UserADM]] requesting the project may see the result.
   *
   * @param projectIri           an [[IRI]] identifying the project
   * @param requestingUser       the user making the request
   * @return
   *     '''success''': information about the project as a [[ProjectGetResponseADM]]
   *
   *     '''error''': [[dsp.errors.NotFoundException]] when no project for the given IRI can be found
   *                  [[dsp.errors.ValidationException]] if the given `projectIri` is invalid
   */
  def getSingleProjectADMRequest(
    projectIri: IRI,
    requestingUser: UserADM
  ): Task[ProjectGetResponseADM] =
    ProjectIdentifierADM.IriIdentifier
      .fromString(projectIri)
      .toZIO
      .flatMap(getSingleProjectADMRequest(_, requestingUser))

  /**
   * Finds the project by its [[ProjectIdentifierADM]] and returns the information as a [[ProjectGetResponseADM]].
   * Checks permissions whether the [[UserADM]] requesting the project may see the result.
   *
   * @param identifier           a [[ProjectIdentifierADM]] instance
   * @param requestingUser       the user making the request
   * @return
   *     '''success''': information about the project as a [[ProjectGetResponseADM]]
   *
   *     '''error''': [[dsp.errors.NotFoundException]] when no project for the given IRI can be found
   */
  def getSingleProjectADMRequest(
    identifier: ProjectIdentifierADM,
    requestingUser: UserADM
  ): Task[ProjectGetResponseADM] =
    bridge.askAppActor(ProjectGetRequestADM(identifier, requestingUser))
}

object ProjectsService {
  val layer: URLayer[ActorToZioBridge, ProjectsService] = ZLayer.fromFunction(ProjectsService.apply _)
}
