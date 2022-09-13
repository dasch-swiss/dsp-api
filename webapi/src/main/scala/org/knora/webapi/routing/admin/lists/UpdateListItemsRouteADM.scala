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
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM

/**
 * Provides routes to update list items.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the route.
 */
class UpdateListItemsRouteADM(routeData: KnoraRouteData, appConfig: AppConfig)
    extends KnoraRoute(routeData, appConfig)
    with Authenticator
    with ListADMJsonProtocol {

  val listsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "lists")

  def makeRoute: Route =
    updateNodeName() ~
      updateNodeLabels() ~
      updateNodeComments() ~
      updateNodePosition() ~
      updateList()

  /**
   * Update name of an existing list node, either root or child.
   */
  private def updateNodeName(): Route =
    path(listsBasePath / Segment / "name") { iri =>
      put {
        entity(as[ChangeNodeNameApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val namePayload: NodeNameChangePayloadADM =
            NodeNameChangePayloadADM(ListName.make(apiRequest.name).fold(e => throw e.head, v => v))

          val requestMessage: Future[NodeNameChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext, appConfig)
          } yield NodeNameChangeRequestADM(
            nodeIri = nodeIri,
            changeNodeNameRequest = namePayload,
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
   * Update labels of an existing list node, either root or child.
   */
  private def updateNodeLabels(): Route =
    path(listsBasePath / Segment / "labels") { iri =>
      put {
        entity(as[ChangeNodeLabelsApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val labelsPayload: NodeLabelsChangePayloadADM =
            NodeLabelsChangePayloadADM(Labels.make(apiRequest.labels).fold(e => throw e.head, v => v))

          val requestMessage: Future[NodeLabelsChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext, appConfig)
          } yield NodeLabelsChangeRequestADM(
            nodeIri = nodeIri,
            changeNodeLabelsRequest = labelsPayload,
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
   * Updates comments of an existing list node, either root or child.
   */
  private def updateNodeComments(): Route =
    path(listsBasePath / Segment / "comments") { iri =>
      put {
        entity(as[ChangeNodeCommentsApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val commentsPayload: NodeCommentsChangePayloadADM =
            NodeCommentsChangePayloadADM(Comments.make(apiRequest.comments).fold(e => throw e.head, v => v))

          val requestMessage: Future[NodeCommentsChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext, appConfig)
          } yield NodeCommentsChangeRequestADM(
            nodeIri = nodeIri,
            changeNodeCommentsRequest = commentsPayload,
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
   * Updates position of an existing list child node.
   */
  private def updateNodePosition(): Route =
    path(listsBasePath / Segment / "position") { iri =>
      put {
        entity(as[ChangeNodePositionApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val requestMessage: Future[NodePositionChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext, appConfig)
          } yield NodePositionChangeRequestADM(
            nodeIri = nodeIri,
            changeNodePositionRequest = apiRequest,
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
   * Updates existing list node, either root or child.
   */
  private def updateList(): Route = path(listsBasePath / Segment) { iri =>
    put {
      entity(as[ListNodeChangeApiRequestADM]) { apiRequest => requestContext =>
        // check if requested Iri matches the route Iri
        val listIri: Validation[Throwable, ListIri] = if (iri == apiRequest.listIri) {
          ListIri.make(apiRequest.listIri)
        } else {
          Validation.fail(throw BadRequestException("Route and payload listIri mismatch."))
        }
        val validatedListIri: ListIri = listIri.fold(e => throw e.head, v => v)

        val projectIri: Validation[Throwable, ProjectIri]       = ProjectIri.make(apiRequest.projectIri)
        val validatedProjectIri: ProjectIri                     = projectIri.fold(e => throw e.head, v => v)
        val hasRootNode: Validation[Throwable, Option[ListIri]] = ListIri.make(apiRequest.hasRootNode)
        val position: Validation[Throwable, Option[Position]]   = Position.make(apiRequest.position)
        val name: Validation[Throwable, Option[ListName]]       = ListName.make(apiRequest.name)
        val labels: Validation[Throwable, Option[Labels]]       = Labels.make(apiRequest.labels)
        val comments: Validation[Throwable, Option[Comments]]   = Comments.make(apiRequest.comments)

        val validatedChangeNodeInfoPayload: Validation[Throwable, ListNodeChangePayloadADM] =
          Validation.validateWith(listIri, projectIri, hasRootNode, position, name, labels, comments)(
            ListNodeChangePayloadADM
          )

        val requestMessage: Future[NodeInfoChangeRequestADM] = for {
          payload        <- toFuture(validatedChangeNodeInfoPayload)
          requestingUser <- getUserADM(requestContext, appConfig)
          // check if the requesting user is allowed to perform operation
          _ = if (
                !requestingUser.permissions.isProjectAdmin(
                  validatedProjectIri.value
                ) && !requestingUser.permissions.isSystemAdmin
              ) {
                // not project or a system admin
                throw ForbiddenException(ListErrorMessages.ListNodeCreatePermission)
              }
        } yield NodeInfoChangeRequestADM(
          listIri = validatedListIri.value,
          changeNodeRequest = payload,
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
