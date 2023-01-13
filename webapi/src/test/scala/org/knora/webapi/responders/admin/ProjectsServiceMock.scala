/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.URLayer
import zio._
import zio.mock._

import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectCreatePayloadADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectOperationResponseADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectsGetResponseADM
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM

object ProjectsServiceMock extends Mock[ProjectsService] {
  object GetProjects      extends Effect[Unit, Throwable, ProjectsGetResponseADM]
  object GetSingleProject extends Effect[ProjectIdentifierADM, Throwable, ProjectGetResponseADM]
  object CreateProject    extends Effect[(ProjectCreatePayloadADM, UserADM), Throwable, ProjectOperationResponseADM]

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
      }
    }
}
