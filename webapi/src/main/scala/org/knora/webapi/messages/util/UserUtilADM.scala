/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
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
import org.knora.webapi.responders.ResponderManager

import scala.concurrent.ExecutionContext
import scala.concurrent.Future

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
    appActor: ActorRef
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
        userResponse: UserResponseADM <-
          appActor
            .ask(
              UserGetRequestADM(
                identifier = UserIdentifierADM(maybeIri = Some(requestedUserIri)),
                userInformationTypeADM = UserInformationTypeADM.Full,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = KnoraSystemInstances.Users.SystemUser
              )
            )
            .mapTo[UserResponseADM]
      } yield userResponse.user
    }
  }
}
