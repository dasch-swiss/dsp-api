/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

import akka.actor.ActorRef
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.messages.admin.responder.usersmessages.{UserADM, UserGetRequestADM, UserIdentifierADM, UserInformationTypeADM, UserResponseADM}
import org.knora.webapi.{ForbiddenException, IRI, KnoraSystemInstances}

import scala.concurrent.{ExecutionContext, Future}

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
    def switchToUser(requestingUser: UserADM,
                     requestedUserIri: IRI,
                     projectIri: IRI,
                     responderManager: ActorRef)(implicit timeout: Timeout, executionContext: ExecutionContext): Future[UserADM] = {
        implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

        if (requestingUser.id == requestedUserIri) {
            FastFuture.successful(requestingUser)
        } else if (!(requestingUser.permissions.isSystemAdmin || requestingUser.permissions.isProjectAdmin(projectIri))) {
            Future.failed(ForbiddenException(s"You are logged in as ${requestingUser.username}, but only a system administrator or project administrator can perform an operation as another user"))
        } else {
            for {
                userResponse: UserResponseADM <- (responderManager ? UserGetRequestADM(
                    identifier = UserIdentifierADM(maybeIri = Some(requestedUserIri)),
                    userInformationTypeADM = UserInformationTypeADM.FULL,
                    requestingUser = KnoraSystemInstances.Users.SystemUser
                )).mapTo[UserResponseADM]
            } yield userResponse.user
        }
    }
}
