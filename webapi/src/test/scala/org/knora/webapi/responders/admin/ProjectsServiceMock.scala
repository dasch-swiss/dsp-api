/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.URLayer
import zio._
import zio.mock._

import dsp.valueobjects.Iri._
import org.knora.webapi.messages.admin.responder.projectsmessages._
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

object ProjectsServiceMock extends Mock[ProjectsService] {
  object GetProjects      extends Effect[Unit, Throwable, ProjectsGetResponseADM]
  object GetSingleProject extends Effect[ProjectIdentifierADM, Throwable, ProjectGetResponseADM]
  object CreateProject    extends Effect[(ProjectCreatePayloadADM, UserADM), Throwable, ProjectOperationResponseADM]
  object DeleteProject    extends Effect[(ProjectIri, UserADM), Throwable, ProjectOperationResponseADM]
  object UpdateProject
      extends Effect[(ProjectIri, ProjectUpdatePayloadADM, UserADM), Throwable, ProjectOperationResponseADM]
  object GetAllProjectData
      extends Effect[(ProjectIdentifierADM.IriIdentifier, UserADM), Throwable, ProjectDataGetResponseADM]
  object GetProjectMembers extends Effect[(ProjectIdentifierADM, UserADM), Throwable, ProjectMembersGetResponseADM]
  object GetProjectAdmins  extends Effect[(ProjectIdentifierADM, UserADM), Throwable, ProjectAdminMembersGetResponseADM]
  object GetKeywords       extends Effect[Unit, Throwable, ProjectsKeywordsGetResponseADM]

  override val compose: URLayer[Proxy, ProjectsService] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new ProjectsService {

        def getProjectsADMRequest(): Task[ProjectsGetResponseADM] =
          proxy(GetProjects)

        def getSingleProjectADMRequest(identifier: ProjectIdentifierADM): Task[ProjectGetResponseADM] =
          proxy(GetSingleProject, identifier)

        def createProjectADMRequest(
          payload: ProjectCreatePayloadADM,
          requestingUser: UserADM
        ): Task[ProjectOperationResponseADM] =
          proxy(CreateProject, (payload, requestingUser))

        def deleteProject(iri: ProjectIri, requestingUser: UserADM): Task[ProjectOperationResponseADM] =
          proxy(DeleteProject, (iri, requestingUser))

        def updateProject(
          projectIri: ProjectIri,
          payload: ProjectUpdatePayloadADM,
          requestingUser: UserADM
        ): Task[ProjectOperationResponseADM] =
          proxy(UpdateProject, (projectIri, payload, requestingUser))

        def getAllProjectData(
          iri: ProjectIdentifierADM.IriIdentifier,
          requestingUser: UserADM
        ): Task[ProjectDataGetResponseADM] =
          proxy(GetAllProjectData, (iri, requestingUser))

        def getProjectMembers(
          identifier: ProjectIdentifierADM,
          requestingUser: UserADM
        ): Task[ProjectMembersGetResponseADM] =
          proxy(GetProjectMembers, (identifier, requestingUser))

        def getProjectAdmins(
          identifier: ProjectIdentifierADM,
          requestingUser: UserADM
        ): Task[ProjectAdminMembersGetResponseADM] =
          proxy(GetProjectAdmins, (identifier, requestingUser))

        def getKeywords(): Task[ProjectsKeywordsGetResponseADM] =
          proxy(GetKeywords)

      }
    }
}
