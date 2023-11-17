/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import org.apache.pekko
import zio.*
import zio.prelude.Validation

import java.util.UUID

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import dsp.valueobjects.Iri.*
import dsp.valueobjects.List.*
import dsp.valueobjects.ListErrorMessages
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

import pekko.http.scaladsl.server.Directives.*
import pekko.http.scaladsl.server.PathMatcher
import pekko.http.scaladsl.server.Route

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
        val maybeId: Validation[Throwable, Option[ListIri]]    = ListIri.make(apiRequest.id)
        val projectIri: Validation[Throwable, ProjectIri]      = ProjectIri.from(apiRequest.projectIri)
        val maybeName: Validation[Throwable, Option[ListName]] = ListName.make(apiRequest.name)
        val labels: Validation[Throwable, Labels]              = Labels.make(apiRequest.labels)
        val comments: Validation[Throwable, Comments]          = Comments.make(apiRequest.comments)
        val validatedPayload: Validation[Throwable, ListRootNodeCreatePayloadADM] =
          Validation.validateWith(maybeId, projectIri, maybeName, labels, comments)(ListRootNodeCreatePayloadADM)

        val requestMessage = for {
          payload <- validatedPayload.toZIO
          user    <- Authenticator.getUserADM(requestContext)
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
          parentNodeIri = ListIri.make(apiRequest.parentNodeIri)
          id            = ListIri.make(apiRequest.id)
          projectIri    = ProjectIri.from(apiRequest.projectIri)
          name          = ListName.make(apiRequest.name)
          position      = Position.make(apiRequest.position)
          labels        = Labels.make(apiRequest.labels)
          comments      = Comments.make(apiRequest.comments)
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
