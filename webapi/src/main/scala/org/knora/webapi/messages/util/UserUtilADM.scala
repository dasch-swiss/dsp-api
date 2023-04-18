/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import zio._

import dsp.errors.ForbiddenException
import org.knora.webapi.IRI
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationTypeADM.Full
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.SystemUser

/**
 * Utility functions for working with users.
 */
object UserUtilADM {

  /**
   * Allows a system admin or project admin to perform an operation as another user in a specified project.
   * Checks whether the requesting user is a system admin or a project admin in the project, and if so,
   * returns a [[UserADM]] representing the requested user. Otherwise, returns a failed future containing
   * [[ForbiddenException]].
   *
   * @param requestingUser   the requesting user.
   * @param requestedUserIri the IRI of the requested user.
   * @param projectIri       the IRI of the project.
   * @return a [[UserADM]] representing the requested user.
   */
  def switchToUser(
    requestingUser: UserADM,
    requestedUserIri: IRI,
    projectIri: IRI
  ): ZIO[StringFormatter with MessageRelay, Throwable, UserADM] =
    ZIO.serviceWithZIO[StringFormatter] { implicit stringFormatter =>
      if (requestingUser.id == requestedUserIri) {
        ZIO.succeed(requestingUser)
      } else if (!(requestingUser.permissions.isSystemAdmin || requestingUser.permissions.isProjectAdmin(projectIri))) {
        val forbiddenMsg =
          s"You are logged in as ${requestingUser.username}, but only a system administrator or project administrator can perform an operation as another user"
        ZIO.fail(ForbiddenException(forbiddenMsg))
      } else {
        for {
          userResponse <- MessageRelay.ask[UserResponseADM](
                            UserGetRequestADM(UserIdentifierADM(maybeIri = Some(requestedUserIri)), Full, SystemUser)
                          )
        } yield userResponse.user
      }
    }
}
