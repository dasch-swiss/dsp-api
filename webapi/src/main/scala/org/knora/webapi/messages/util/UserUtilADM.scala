/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util

import zio.*

import dsp.errors.ForbiddenException
import dsp.errors.NotFoundException
import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.usersmessages.UserInformationType.Full
import org.knora.webapi.messages.util.KnoraSystemInstances.Users.SystemUser
import org.knora.webapi.responders.admin.UsersResponder
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.UserIri

/**
 * Utility functions for working with users.
 */
object UserUtilADM {

  /**
   * Allows a system admin or project admin to perform an operation as another user in a specified project.
   * Checks whether the requesting user is a system admin or a project admin in the project, and if so,
   * returns a [[User]] representing the requested user. Otherwise, returns a failed future containing
   * [[ForbiddenException]].
   *
   * @param requestingUser   the requesting user.
   * @param requestedUserIri the IRI of the requested user.
   * @param projectIri       the IRI of the project.
   * @return a [[User]] representing the requested user.
   */
  def switchToUser(
    requestingUser: User,
    requestedUserIri: IRI,
    projectIri: IRI
  ): ZIO[UsersResponder, Throwable, User] = {
    val userIri = UserIri.unsafeFrom(requestedUserIri)
    requestingUser match {
      case _ if requestingUser.id == userIri.value => ZIO.succeed(requestingUser)
      case _ if !(requestingUser.permissions.isSystemAdmin || requestingUser.permissions.isProjectAdmin(projectIri)) =>
        val msg =
          s"You are logged in as ${requestingUser.username}, but only a system administrator or project administrator can perform an operation as another user"
        ZIO.fail(ForbiddenException(msg))
      case _ =>
        UsersResponder
          .findUserByIri(userIri, Full, SystemUser)
          .someOrFail(NotFoundException(s"User '${userIri.value}' not found"))
    }
  }
}
