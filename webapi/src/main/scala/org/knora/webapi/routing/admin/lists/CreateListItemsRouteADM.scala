/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import zio.prelude.Validation

import java.util.UUID
import scala.concurrent.Future

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import dsp.valueobjects.Iri._
import dsp.valueobjects.List._
import dsp.valueobjects.ListErrorMessages
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCreatePayloadADM.ListChildNodeCreatePayloadADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCreatePayloadADM.ListRootNodeCreatePayloadADM
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM

/**
 * Provides routes to create list items.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the route.
 */
class CreateListItemsRouteADM(routeData: KnoraRouteData, appConfig: AppConfig)
    extends KnoraRoute(routeData, appConfig)
    with Authenticator
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
        val maybeId: Validation[Throwable, Option[ListIri]] = ListIri.make(apiRequest.id)
        val projectIri: Validation[Throwable, ProjectIri]   = ProjectIri.make(apiRequest.projectIri)
        val validatedProjectIri: ProjectIri = ProjectIri
          .make(apiRequest.projectIri)
          .fold(e => throw e.head, v => v)
        val maybeName: Validation[Throwable, Option[ListName]] = ListName.make(apiRequest.name)
        val labels: Validation[Throwable, Labels]              = Labels.make(apiRequest.labels)
        val comments: Validation[Throwable, Comments]          = Comments.make(apiRequest.comments)
        val validatedListRootNodeCreatePayload: Validation[Throwable, ListRootNodeCreatePayloadADM] =
          Validation.validateWith(maybeId, projectIri, maybeName, labels, comments)(ListRootNodeCreatePayloadADM)

        val requestMessage: Future[ListRootNodeCreateRequestADM] = for {
          payload        <- toFuture(validatedListRootNodeCreatePayload)
          requestingUser <- getUserADM(requestContext, appConfig)

          // check if the requesting user is allowed to perform operation
          _ =
            if (
              !requestingUser.permissions
                .isProjectAdmin(validatedProjectIri.value) && !requestingUser.permissions.isSystemAdmin
            ) {
              // not project or a system admin
              throw ForbiddenException(ListErrorMessages.ListCreatePermission)
            }
        } yield ListRootNodeCreateRequestADM(
          createRootNode = payload,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }
  }

  /**
   * Creates a new list child node.
   */
  private def createListChildNode(): Route = path(listsBasePath / Segment) { iri =>
    post {
      entity(as[ListChildNodeCreateApiRequestADM]) { apiRequest => requestContext =>
        // check if requested ListIri matches the Iri passed in the route
        val parentNodeIri: Validation[Throwable, ListIri] = if (iri == apiRequest.parentNodeIri) {
          ListIri.make(apiRequest.parentNodeIri)
        } else {
          Validation.fail(throw BadRequestException("Route and payload parentNodeIri mismatch."))
        }

        val id: Validation[Throwable, Option[ListIri]]        = ListIri.make(apiRequest.id)
        val projectIri: Validation[Throwable, ProjectIri]     = ProjectIri.make(apiRequest.projectIri)
        val validatedProjectIri: ProjectIri                   = projectIri.fold(e => throw e.head, v => v)
        val name: Validation[Throwable, Option[ListName]]     = ListName.make(apiRequest.name)
        val position: Validation[Throwable, Option[Position]] = Position.make(apiRequest.position)
        val labels: Validation[Throwable, Labels]             = Labels.make(apiRequest.labels)
        val comments: Validation[Throwable, Option[Comments]] = Comments.make(apiRequest.comments)
        val validatedCreateChildNodePeyload: Validation[Throwable, ListChildNodeCreatePayloadADM] =
          Validation.validateWith(id, parentNodeIri, projectIri, name, position, labels, comments)(
            ListChildNodeCreatePayloadADM
          )

        val requestMessage: Future[ListChildNodeCreateRequestADM] = for {
          payload        <- toFuture(validatedCreateChildNodePeyload)
          requestingUser <- getUserADM(requestContext, appConfig)

          // check if the requesting user is allowed to perform operation
          _ =
            if (
              !requestingUser.permissions
                .isProjectAdmin(validatedProjectIri.value) && !requestingUser.permissions.isSystemAdmin
            ) {
              // not project or a system admin
              throw ForbiddenException(ListErrorMessages.ListCreatePermission)
            }
        } yield ListChildNodeCreateRequestADM(
          createChildNodeRequest = payload,
          requestingUser = requestingUser,
          apiRequestID = UUID.randomUUID()
        )

        RouteUtilADM.runJsonRoute(
          requestMessageF = requestMessage,
          requestContext = requestContext,
          appActor = appActor,
          log = log
        )
      }
    }
  }
}
