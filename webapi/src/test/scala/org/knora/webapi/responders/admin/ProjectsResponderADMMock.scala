/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.Task
import zio.URLayer
import zio.ZIO
import zio.ZLayer
import zio.mock
import zio.mock.Mock
import zio.mock.Proxy

import java.util.UUID

import org.knora.webapi.messages.admin.responder.projectsmessages.*
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectCreateRequest
import org.knora.webapi.slice.admin.api.model.ProjectsEndpointsRequests.ProjectUpdateRequest
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User

object ProjectsResponderADMMock extends Mock[ProjectsResponderADM] {

  object ProjectsGetRequestADM            extends Effect[Unit, Throwable, ProjectsGetResponseADM]
  object GetProjectFromCacheOrTriplestore extends Effect[ProjectIdentifierADM, Throwable, Option[ProjectADM]]
  object GetSingleProjectADMRequest       extends Effect[ProjectIdentifierADM, Throwable, ProjectGetResponseADM]
  object ProjectMembersGetRequestADM
      extends Effect[(ProjectIdentifierADM, User), Throwable, ProjectMembersGetResponseADM]
  object ProjectAdminMembersGetRequestADM
      extends Effect[(ProjectIdentifierADM, User), Throwable, ProjectAdminMembersGetResponseADM]
  object ProjectsKeywordsGetRequestADM extends Effect[Unit, Throwable, ProjectsKeywordsGetResponseADM]
  object ProjectKeywordsGetRequestADM  extends Effect[ProjectIri, Throwable, ProjectKeywordsGetResponseADM]
  object ProjectRestrictedViewSettingsGetADM
      extends Effect[ProjectIdentifierADM, Throwable, Option[ProjectRestrictedViewSettingsADM]]
  object ProjectRestrictedViewSettingsGetRequestADM
      extends Effect[ProjectIdentifierADM, Throwable, ProjectRestrictedViewSettingsGetResponseADM]
  object ProjectCreateRequestADM
      extends Effect[(ProjectCreateRequest, User, UUID), Throwable, ProjectOperationResponseADM]
  object ChangeBasicInformationRequestADM
      extends Effect[(ProjectIri, ProjectUpdateRequest, User, UUID), Throwable, ProjectOperationResponseADM]

  val compose: URLayer[mock.Proxy, ProjectsResponderADM] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new ProjectsResponderADM {
        override def projectsGetRequestADM(withSystemProjects: Boolean): Task[ProjectsGetResponseADM] =
          proxy(ProjectsGetRequestADM)
        override def getProjectFromCacheOrTriplestore(id: ProjectIdentifierADM): Task[Option[ProjectADM]] =
          proxy(GetProjectFromCacheOrTriplestore, id)
        override def getSingleProjectADMRequest(id: ProjectIdentifierADM): Task[ProjectGetResponseADM] =
          proxy(GetSingleProjectADMRequest, id)
        override def projectMembersGetRequestADM(
          id: ProjectIdentifierADM,
          user: User
        ): Task[ProjectMembersGetResponseADM] =
          proxy(ProjectMembersGetRequestADM, (id, user))
        override def projectAdminMembersGetRequestADM(
          id: ProjectIdentifierADM,
          user: User
        ): Task[ProjectAdminMembersGetResponseADM] =
          proxy(ProjectAdminMembersGetRequestADM, (id, user))
        override def projectsKeywordsGetRequestADM(): Task[ProjectsKeywordsGetResponseADM] =
          proxy(ProjectsKeywordsGetRequestADM, ())
        override def projectKeywordsGetRequestADM(projectIri: ProjectIri): Task[ProjectKeywordsGetResponseADM] =
          proxy(ProjectKeywordsGetRequestADM, projectIri)
        override def projectRestrictedViewSettingsGetADM(
          id: ProjectIdentifierADM
        ): Task[Option[ProjectRestrictedViewSettingsADM]] =
          proxy(ProjectRestrictedViewSettingsGetADM, id)
        override def projectRestrictedViewSettingsGetRequestADM(
          id: ProjectIdentifierADM
        ): Task[ProjectRestrictedViewSettingsGetResponseADM] =
          proxy(ProjectRestrictedViewSettingsGetRequestADM, id)
        override def projectCreateRequestADM(
          createReq: ProjectCreateRequest,
          requestingUser: User,
          apiRequestID: UUID
        ): Task[ProjectOperationResponseADM] =
          proxy(ProjectCreateRequestADM, (createReq, requestingUser, apiRequestID))
        override def changeBasicInformationRequestADM(
          projectIri: ProjectIri,
          updateReq: ProjectUpdateRequest,
          user: User,
          apiRequestID: UUID
        ): Task[ProjectOperationResponseADM] =
          proxy(ChangeBasicInformationRequestADM, (projectIri, updateReq, user, apiRequestID))
      }
    }
}
