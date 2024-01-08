/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.URLayer
import zio.*
import zio.mock.*

import dsp.valueobjects.Iri.*
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.IriIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM.ShortcodeIdentifier
import org.knora.webapi.messages.admin.responder.projectsmessages.*
import org.knora.webapi.slice.admin.api.model.ProjectDataGetResponseADM
import org.knora.webapi.slice.admin.api.model.ProjectExportInfoResponse
import org.knora.webapi.slice.admin.api.model.ProjectImportResponse
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectSetRestrictedViewSizeRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectUpdateRequest
import org.knora.webapi.slice.admin.api.service.ProjectADMRestService
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User

object ProjectADMRestServiceMock extends Mock[ProjectADMRestService] {
  object GetProjects      extends Effect[Unit, Throwable, ProjectsGetResponseADM]
  object GetSingleProject extends Effect[ProjectIdentifierADM, Throwable, ProjectGetResponseADM]
  object CreateProject    extends Effect[(ProjectCreateRequest, User), Throwable, ProjectOperationResponseADM]
  object DeleteProject    extends Effect[(IriIdentifier, User), Throwable, ProjectOperationResponseADM]
  object UpdateProject
      extends Effect[(IriIdentifier, ProjectUpdateRequest, User), Throwable, ProjectOperationResponseADM]
  object GetAllProjectData       extends Effect[(IriIdentifier, User), Throwable, ProjectDataGetResponseADM]
  object GetProjectMembers       extends Effect[(ProjectIdentifierADM, User), Throwable, ProjectMembersGetResponseADM]
  object GetProjectAdmins        extends Effect[(ProjectIdentifierADM, User), Throwable, ProjectAdminMembersGetResponseADM]
  object GetKeywords             extends Effect[Unit, Throwable, ProjectsKeywordsGetResponseADM]
  object GetKeywordsByProjectIri extends Effect[ProjectIri, Throwable, ProjectKeywordsGetResponseADM]
  object GetRestrictedViewSettings
      extends Effect[ProjectIdentifierADM, Throwable, ProjectRestrictedViewSettingsGetResponseADM]

  override val compose: URLayer[Proxy, ProjectADMRestService] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new ProjectADMRestService {

        def listAllProjects(): Task[ProjectsGetResponseADM] =
          proxy(GetProjects)

        def findProject(identifier: ProjectIdentifierADM): Task[ProjectGetResponseADM] =
          proxy(GetSingleProject, identifier)

        def createProject(
          createReq: ProjectCreateRequest,
          requestingUser: User
        ): Task[ProjectOperationResponseADM] =
          proxy(CreateProject, (createReq, requestingUser))

        def deleteProject(
          id: ProjectIdentifierADM.IriIdentifier,
          requestingUser: User
        ): Task[ProjectOperationResponseADM] =
          proxy(DeleteProject, (id, requestingUser))

        def updateProject(
          id: IriIdentifier,
          updateReq: ProjectUpdateRequest,
          requestingUser: User
        ): Task[ProjectOperationResponseADM] =
          proxy(UpdateProject, (id, updateReq, requestingUser))

        def getAllProjectData(
          id: ProjectIdentifierADM.IriIdentifier,
          user: User
        ): Task[ProjectDataGetResponseADM] =
          proxy(GetAllProjectData, (id, user))

        def getProjectMembers(
          requestingUser: User,
          identifier: ProjectIdentifierADM
        ): Task[ProjectMembersGetResponseADM] =
          proxy(GetProjectMembers, (identifier, requestingUser))

        def getProjectAdminMembers(
          requestingUser: User,
          identifier: ProjectIdentifierADM
        ): Task[ProjectAdminMembersGetResponseADM] =
          proxy(GetProjectAdmins, (identifier, requestingUser))

        def listAllKeywords(): Task[ProjectsKeywordsGetResponseADM] =
          proxy(GetKeywords)

        def getKeywordsByProjectIri(
          projectIri: ProjectIri
        ): Task[ProjectKeywordsGetResponseADM] =
          proxy(GetKeywordsByProjectIri, projectIri)

        def getProjectRestrictedViewSettings(
          identifier: ProjectIdentifierADM
        ): Task[ProjectRestrictedViewSettingsGetResponseADM] =
          proxy(GetRestrictedViewSettings, identifier)

        override def exportProject(projectIri: IRI, requestingUser: User): Task[Unit] = ???

        override def exportProject(shortcode: ShortcodeIdentifier, requestingUser: User): Task[Unit] = ???

        override def importProject(projectIri: IRI, requestingUser: User): Task[ProjectImportResponse] = ???

        override def listExports(requestingUser: User): Task[Chunk[ProjectExportInfoResponse]] = ???

        override def updateProjectRestrictedViewSettings(
          id: ProjectIdentifierADM,
          user: User,
          setSizeReq: ProjectSetRestrictedViewSizeRequest
        ): Task[ProjectRestrictedViewSizeResponseADM] = ???
      }
    }
}
