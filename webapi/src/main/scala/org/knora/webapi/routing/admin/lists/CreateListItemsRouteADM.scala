/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.PathMatcher
import org.apache.pekko.http.scaladsl.server.Route
import zio.*
import zio.prelude.Validation

import java.util.UUID

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCreatePayloadADM.ListChildNodeCreatePayloadADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCreatePayloadADM.ListRootNodeCreatePayloadADM
import org.knora.webapi.messages.admin.responder.listsmessages.*
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.ListErrorMessages
import org.knora.webapi.slice.admin.domain.model.ListProperties.*
import org.knora.webapi.slice.common.ToValidation.validateOneWithFrom
import org.knora.webapi.slice.common.ToValidation.validateOptionWithFrom

/**
 * Provides routes to create list items.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the route.
 */
final case class CreateListItemsRouteADM(
  private val routeData: KnoraRouteData,
  override protected implicit val runtime: Runtime[Authenticator & StringFormatter & MessageRelay]
) extends KnoraRoute(routeData, runtime)
    with ListADMJsonProtocol {

  val listsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "lists")

  def makeRoute: Route =
    createListRootNode() ~
      createListChildNode()

  /**
   * Creates a new list (root node).
   */
  private def createListRootNode(): Route = path(listsBasePath) {
    post {
      entity(as[ListRootNodeCreateApiRequestADM]) { apiRequest => requestContext =>
        val maybeId        = validateOptionWithFrom(apiRequest.id, ListIri.from, BadRequestException.apply)
        val projectIri     = validateOneWithFrom(apiRequest.projectIri, ProjectIri.from, BadRequestException.apply)
        val nameValidation = validateOptionWithFrom(apiRequest.name, ListName.from, BadRequestException.apply)
        val labels         = validateOneWithFrom(apiRequest.labels, Labels.from, BadRequestException.apply)
        val comments       = validateOneWithFrom(apiRequest.comments, Comments.from, BadRequestException.apply)

        val requestMessage = for {
          payload <-
            Validation
              .validateWith(maybeId, projectIri, nameValidation, labels, comments)(ListRootNodeCreatePayloadADM)
              .toZIO
          user <- Authenticator.getUserADM(requestContext)
          _ <-
            ZIO
              .fail(ForbiddenException(ListErrorMessages.ListCreatePermission))
              .when(!user.permissions.isProjectAdmin(payload.projectIri.value) && !user.permissions.isSystemAdmin)
        } yield ListRootNodeCreateRequestADM(payload, user, UUID.randomUUID())
        runJsonRouteZ(requestMessage, requestContext)
      }
    }
  }

  /**
   * Creates a new list child node.
   */
  private def createListChildNode(): Route = path(listsBasePath / Segment) { iri =>
    post {
      entity(as[ListChildNodeCreateApiRequestADM]) { apiRequest => requestContext =>
        val validatedPayload = for {
          _ <- ZIO
                 .fail(BadRequestException("Route and payload parentNodeIri mismatch."))
                 .when(iri != apiRequest.parentNodeIri)
          parentNodeIri = validateOneWithFrom(apiRequest.parentNodeIri, ListIri.from, BadRequestException.apply)
          id            = validateOptionWithFrom(apiRequest.id, ListIri.from, BadRequestException.apply)
          projectIri    = validateOneWithFrom(apiRequest.projectIri, ProjectIri.from, BadRequestException.apply)
          name          = validateOptionWithFrom(apiRequest.name, ListName.from, BadRequestException.apply)
          position      = validateOptionWithFrom(apiRequest.position, Position.from, BadRequestException.apply)
          labels        = validateOneWithFrom(apiRequest.labels, Labels.from, BadRequestException.apply)
          comments      = validateOptionWithFrom(apiRequest.comments, Comments.from, BadRequestException.apply)
        } yield Validation.validateWith(id, parentNodeIri, projectIri, name, position, labels, comments)(
          ListChildNodeCreatePayloadADM
        )

        val requestMessage = for {
          payload <- validatedPayload.flatMap(_.toZIO)
          user    <- Authenticator.getUserADM(requestContext)
          _ <- ZIO
                 .fail(ForbiddenException(ListErrorMessages.ListCreatePermission))
                 .when(!user.permissions.isProjectAdmin(payload.projectIri.value) && !user.permissions.isSystemAdmin)
        } yield ListChildNodeCreateRequestADM(payload, user, UUID.randomUUID())
        runJsonRouteZ(requestMessage, requestContext)
      }
    }
  }
}
