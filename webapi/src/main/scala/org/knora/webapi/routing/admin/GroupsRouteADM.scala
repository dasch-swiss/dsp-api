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
import org.knora.webapi.messages.admin.responder.groupsmessages.*
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM.*
import org.knora.webapi.routing.RouteUtilZ

/**
 * Provides a routing function for API routes that deal with groups.
 */

final case class GroupsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator & StringFormatter & MessageRelay]
) extends KnoraRoute(routeData, runtime)
    with GroupsADMJsonProtocol {

  private val groupsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "groups")

  override def makeRoute: Route =
    changeGroupStatus() ~
      deleteGroup()

  /**
   * Updates the group's status.
   */
  private def changeGroupStatus(): Route =
    path(groupsBasePath / Segment / "status") { value =>
      put {
        entity(as[ChangeGroupApiRequestADM]) { apiRequest => requestContext =>
          val requestTask = for {
            /**
             * The api request is already checked at time of creation.
             * See case class. Depending on the data sent, we are either
             * doing a general update or status change. Since we are in
             * the status change route, we are only interested in the
             * value of the status property
             */
            _ <- ZIO
                   .fail(BadRequestException("The status property is not allowed to be empty."))
                   .when(apiRequest.status.isEmpty)
            iri <- Iri
                     .validateAndEscapeIri(value)
                     .toZIO
                     .orElseFail(BadRequestException(s"Invalid group IRI $value"))
            requestingUser <- Authenticator.getUserADM(requestContext)
            uuid           <- RouteUtilZ.randomUuid()
          } yield GroupChangeStatusRequestADM(iri, apiRequest, requestingUser, uuid)
          runJsonRouteZ(requestTask, requestContext)
        }
      }
    }

  /**
   * Deletes a group (sets status to false).
   */
  private def deleteGroup(): Route = path(groupsBasePath / Segment) { groupIri =>
    delete { ctx =>
      val task = for {
        r           <- getIriUserUuid(groupIri, ctx)
        changeStatus = ChangeGroupApiRequestADM(status = Some(false))
      } yield GroupChangeStatusRequestADM(r.iri, changeStatus, r.user, r.uuid)
      runJsonRouteZ(task, ctx)
    }
  }
}
