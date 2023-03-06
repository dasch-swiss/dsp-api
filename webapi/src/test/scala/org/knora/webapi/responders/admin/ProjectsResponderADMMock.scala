/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
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

import dsp.valueobjects.Iri
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectAdminMembersGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectCreatePayloadADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectDataGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectKeywordsGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectMembersGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectOperationResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectRestrictedViewSettingsADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectRestrictedViewSettingsGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectUpdatePayloadADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsKeywordsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

object ProjectsResponderADMMock extends Mock[ProjectsResponderADM] {

  object ProjectsGetRequestADM      extends Effect[Unit, Throwable, ProjectsGetResponseADM]
  object GetSingleProjectADM        extends Effect[(ProjectIdentifierADM, Boolean), Throwable, Option[ProjectADM]]
  object GetSingleProjectADMRequest extends Effect[ProjectIdentifierADM, Throwable, ProjectGetResponseADM]
  object ProjectMembersGetRequestADM
      extends Effect[(ProjectIdentifierADM, UserADM), Throwable, ProjectMembersGetResponseADM]
  object ProjectAdminMembersGetRequestADM
      extends Effect[(ProjectIdentifierADM, UserADM), Throwable, ProjectAdminMembersGetResponseADM]
  object ProjectsKeywordsGetRequestADM extends Effect[Unit, Throwable, ProjectsKeywordsGetResponseADM]
  object ProjectKeywordsGetRequestADM  extends Effect[Iri.ProjectIri, Throwable, ProjectKeywordsGetResponseADM]
  object ProjectRestrictedViewSettingsGetADM
      extends Effect[ProjectIdentifierADM, Throwable, Option[ProjectRestrictedViewSettingsADM]]
  object ProjectRestrictedViewSettingsGetRequestADM
      extends Effect[ProjectIdentifierADM, Throwable, ProjectRestrictedViewSettingsGetResponseADM]
  object ProjectCreateRequestADM
      extends Effect[(ProjectCreatePayloadADM, UserADM, UUID), Throwable, ProjectOperationResponseADM]
  object ChangeBasicInformationRequestADM
      extends Effect[(Iri.ProjectIri, ProjectUpdatePayloadADM, UserADM, UUID), Throwable, ProjectOperationResponseADM]
  object ProjectDataGetRequestADM extends Effect[(ProjectIdentifierADM, UserADM), Throwable, ProjectDataGetResponseADM]

  val compose: URLayer[mock.Proxy, ProjectsResponderADM] =
    ZLayer {
      for {
        proxy <- ZIO.service[Proxy]
      } yield new ProjectsResponderADM {
        override def projectsGetRequestADM(): Task[ProjectsGetResponseADM] =
          proxy(ProjectsGetRequestADM)
        override def getSingleProjectADM(id: ProjectIdentifierADM, skipCache: Boolean): Task[Option[ProjectADM]] =
          proxy(GetSingleProjectADM, (id, skipCache))
        override def getSingleProjectADMRequest(id: ProjectIdentifierADM): Task[ProjectGetResponseADM] =
          proxy(GetSingleProjectADMRequest, id)
        override def projectMembersGetRequestADM(
          id: ProjectIdentifierADM,
          user: UserADM
        ): Task[ProjectMembersGetResponseADM] =
          proxy(ProjectMembersGetRequestADM, (id, user))
        override def projectAdminMembersGetRequestADM(
          id: ProjectIdentifierADM,
          user: UserADM
        ): Task[ProjectAdminMembersGetResponseADM] =
          proxy(ProjectAdminMembersGetRequestADM, (id, user))
        override def projectsKeywordsGetRequestADM(): Task[ProjectsKeywordsGetResponseADM] =
          proxy(ProjectsKeywordsGetRequestADM, ())
        override def projectKeywordsGetRequestADM(projectIri: Iri.ProjectIri): Task[ProjectKeywordsGetResponseADM] =
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
          createPayload: ProjectCreatePayloadADM,
          requestingUser: UserADM,
          apiRequestID: UUID
        ): Task[ProjectOperationResponseADM] =
          proxy(ProjectCreateRequestADM, (createPayload, requestingUser, apiRequestID))
        override def changeBasicInformationRequestADM(
          projectIri: Iri.ProjectIri,
          updatePayload: ProjectUpdatePayloadADM,
          user: UserADM,
          apiRequestID: UUID
        ): Task[ProjectOperationResponseADM] =
          proxy(ChangeBasicInformationRequestADM, (projectIri, updatePayload, user, apiRequestID))
        override def projectDataGetRequestADM(
          id: ProjectIdentifierADM,
          user: UserADM
        ): Task[ProjectDataGetResponseADM] = proxy(ProjectDataGetRequestADM, (id, user))
      }
    }
}
