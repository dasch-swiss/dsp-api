package org.knora.webapi.responders.admin
import zio.Task
import zio.URLayer
import zio.ZLayer

import java.util.UUID

import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.admin.responder.groupsmessages.GroupADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionADM
import org.knora.webapi.messages.admin.responder.permissionsmessages.PermissionsDataADM
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.responders.ActorToZioBridge

final case class RestProjectsService(bridge: ActorToZioBridge) {

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
   * @param identifier   a [[ProjectIdentifierADM]] instance
   * @return
   *     '''success''': information about the project as a [[ProjectGetResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given IRI can be found
   */
  def getSingleProjectADMRequest(identifier: ProjectIdentifierADM): Task[ProjectGetResponseADM] =
    bridge.askAppActor(ProjectGetRequestADM(identifier))

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
    bridge.askAppActor(
      ProjectChangeRequestADM(
        projectIri = projectIri,
        changeProjectRequest = ChangeProjectApiRequestADM(status = Some(false)),
        requestingUser = requestingUser,
        apiRequestID = UUID.randomUUID()
      )
    )

  /**
   * Creates a project
   *
   * @param payload   a [[CreateProjectPayload]] instance
   * @return
   *     '''success''': information about the project as a [[ProjectOperationResponseADM]]
   *
   *     '''failure''': [[dsp.errors.NotFoundException]] when no project for the given IRI can be found
   */
  def createProjectADMRequest(payload: ProjectCreatePayloadADM): Task[ProjectOperationResponseADM] = {
    val requestingUser: UserADM =
      UserADM(
        id = "http://rdfh.ch/users/root",
        username = "root",
        email = "root@example.com",
        password = Option("$2a$12$7XEBehimXN1rbhmVgQsyve08.vtDmKK7VMin4AdgCEtE4DWgfQbTK"),
        token = None,
        givenName = "System",
        familyName = "Administrator",
        status = true,
        lang = "de",
        groups = Seq.empty[GroupADM],
        projects = Seq.empty[ProjectADM],
        sessionId = None,
        permissions = PermissionsDataADM(
          groupsPerProject = Map(
            OntologyConstants.KnoraAdmin.SystemProject -> List(OntologyConstants.KnoraAdmin.SystemAdmin)
          ),
          administrativePermissionsPerProject = Map.empty[IRI, Set[PermissionADM]]
        )
      )
    val requestUuid: UUID = UUID.randomUUID()
    bridge.askAppActor(ProjectCreateRequestADM(payload, requestingUser, requestUuid))
  }
}

object RestProjectsService {
  val layer: URLayer[ActorToZioBridge, RestProjectsService] = ZLayer.fromFunction(RestProjectsService.apply _)
}
