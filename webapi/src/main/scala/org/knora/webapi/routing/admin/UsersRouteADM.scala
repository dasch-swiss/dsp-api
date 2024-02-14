/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.PathMatcher
import org.apache.pekko.http.scaladsl.server.Route
import zio.*

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.*
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.RouteUtilADM.getIriUserUuid
import org.knora.webapi.routing.RouteUtilADM.runJsonRouteZ
import org.knora.webapi.slice.admin.domain.model.*
import org.knora.webapi.slice.common.api.AuthorizationRestService

/**
 * Provides an pekko-http-routing function for API routes that deal with users.
 */
final case class UsersRouteADM()(
  private implicit val runtime: Runtime[Authenticator & AuthorizationRestService & StringFormatter & MessageRelay]
) {

  private val usersBasePath: PathMatcher[Unit] = PathMatcher("admin" / "users")

  def makeRoute: Route =
    addUserToGroupMembership() ~
      removeUserFromGroupMembership()

  private def validateUserIriAndEnsureRegularUser(userIri: String) =
    ZIO
      .fromEither(UserIri.from(userIri))
      .filterOrFail(_.isRegularUser)("Changes to built-in users are not allowed.")
      .mapBoth(BadRequestException.apply, _.value)

  private def validateAndEscapeGroupIri(groupIri: String) =
    Iri
      .validateAndEscapeIri(groupIri)
      .toZIO
      .orElseFail(BadRequestException(s"Invalid group IRI $groupIri"))

  /**
   * add user to group
   */
  private def addUserToGroupMembership(): Route =
    path(usersBasePath / "iri" / Segment / "group-memberships" / Segment) { (userIri, groupIri) =>
      post { requestContext =>
        val task = for {
          checkedUserIri  <- validateUserIriAndEnsureRegularUser(userIri)
          checkedGroupIri <- validateAndEscapeGroupIri(groupIri)
          r               <- getIriUserUuid(groupIri, requestContext)
        } yield UserGroupMembershipAddRequestADM(checkedUserIri, checkedGroupIri, r.user, r.uuid)
        runJsonRouteZ(task, requestContext)
      }
    }

  /**
   * remove user from group
   */
  private def removeUserFromGroupMembership(): Route =
    path(usersBasePath / "iri" / Segment / "group-memberships" / Segment) { (userIri, groupIri) =>
      delete { requestContext =>
        val task = for {
          checkedUserIri  <- validateUserIriAndEnsureRegularUser(userIri)
          checkedGroupIri <- validateAndEscapeGroupIri(groupIri)
          r               <- getIriUserUuid(groupIri, requestContext)
        } yield UserGroupMembershipRemoveRequestADM(checkedUserIri, checkedGroupIri, r.user, r.uuid)
        runJsonRouteZ(task, requestContext)
      }
    }
}
