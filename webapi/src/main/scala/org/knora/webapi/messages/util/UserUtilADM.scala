/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.util

import akka.actor.ActorRef
import akka.http.scaladsl.util.FastFuture
import akka.pattern.ask
import akka.util.Timeout
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.ForbiddenException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages._

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
   * @param featureFactoryConfig the feature factory configuration.
   * @return a [[UserADM]] representing the requested user.
   */
  def switchToUser(
    requestingUser: UserADM,
    requestedUserIri: IRI,
    projectIri: IRI,
    featureFactoryConfig: FeatureFactoryConfig,
    responderManager: ActorRef
  )(implicit timeout: Timeout, executionContext: ExecutionContext): Future[UserADM] = {
    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    if (requestingUser.id == requestedUserIri) {
      FastFuture.successful(requestingUser)
    } else if (!(requestingUser.permissions.isSystemAdmin || requestingUser.permissions.isProjectAdmin(projectIri))) {
      Future.failed(
        ForbiddenException(
          s"You are logged in as ${requestingUser.username}, but only a system administrator or project administrator can perform an operation as another user"
        )
      )
    } else {
      for {
        userResponse: UserResponseADM <- (responderManager ? UserGetRequestADM(
          identifier = UserIdentifierADM(maybeIri = Some(requestedUserIri)),
          userInformationTypeADM = UserInformationTypeADM.FULL,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = KnoraSystemInstances.Users.SystemUser
        )).mapTo[UserResponseADM]
      } yield userResponse.user
    }
  }
}
