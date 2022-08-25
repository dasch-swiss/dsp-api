/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import io.swagger.annotations._
import zio.prelude.Validation

import java.util.UUID
import javax.ws.rs.Path
import scala.concurrent.Future

import dsp.errors.BadRequestException
import dsp.errors.ForbiddenException
import dsp.valueobjects.Iri._
import dsp.valueobjects.List._
import dsp.valueobjects.ListErrorMessages
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
class UpdateListItemsRouteADM(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Authenticator
    with ListADMJsonProtocol {

  val ListsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "lists")

  def makeRoute(): Route =
    updateNodeName() ~
      updateNodeLabels() ~
      updateNodeComments() ~
      updateNodePosition() ~
      updateList()

  @Path("/{IRI}/name")
  @ApiOperation(
    value = "Update Node Name",
    nickname = "putNodeName",
    httpMethod = "PUT",
    response = classOf[NodeInfoGetResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "\"node name\" to update",
        required = true,
        dataTypeClass = classOf[ChangeNodeNameApiRequestADM],
        paramType = "body"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  /**
   * Update name of an existing list node, either root or child.
   */
  private def updateNodeName(): Route =
    path(ListsBasePath / Segment / "name") { iri =>
      put {
        entity(as[ChangeNodeNameApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val namePayload: NodeNameChangePayloadADM =
            NodeNameChangePayloadADM(ListName.make(apiRequest.name).fold(e => throw e.head, v => v))

          val requestMessage: Future[NodeNameChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext)
          } yield NodeNameChangeRequestADM(
            nodeIri = nodeIri,
            changeNodeNameRequest = namePayload,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            settings = settings,
            appActor = appActor,
            log = log
          )
        }
      }
    }

  @Path("/{IRI}/labels")
  @ApiOperation(
    value = "Update Node Labels",
    nickname = "putNodeLabels",
    httpMethod = "PUT",
    response = classOf[NodeInfoGetResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "\"node labels\" to update",
        required = true,
        dataTypeClass = classOf[ChangeNodeLabelsApiRequestADM],
        paramType = "body"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  /**
   * Update labels of an existing list node, either root or child.
   */
  private def updateNodeLabels(): Route =
    path(ListsBasePath / Segment / "labels") { iri =>
      put {
        entity(as[ChangeNodeLabelsApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val labelsPayload: NodeLabelsChangePayloadADM =
            NodeLabelsChangePayloadADM(Labels.make(apiRequest.labels).fold(e => throw e.head, v => v))

          val requestMessage: Future[NodeLabelsChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext)
          } yield NodeLabelsChangeRequestADM(
            nodeIri = nodeIri,
            changeNodeLabelsRequest = labelsPayload,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            settings = settings,
            appActor = appActor,
            log = log
          )
        }
      }
    }

  @Path("/{IRI}/comments")
  @ApiOperation(
    value = "Update Node Comments",
    nickname = "putNodeComments",
    httpMethod = "PUT",
    response = classOf[NodeInfoGetResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "\"node comments\" to update",
        required = true,
        dataTypeClass = classOf[ChangeNodeCommentsApiRequestADM],
        paramType = "body"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  /**
   * Updates comments of an existing list node, either root or child.
   */
  private def updateNodeComments(): Route =
    path(ListsBasePath / Segment / "comments") { iri =>
      put {
        entity(as[ChangeNodeCommentsApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val commentsPayload: NodeCommentsChangePayloadADM =
            NodeCommentsChangePayloadADM(Comments.make(apiRequest.comments).fold(e => throw e.head, v => v))

          val requestMessage: Future[NodeCommentsChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext)
          } yield NodeCommentsChangeRequestADM(
            nodeIri = nodeIri,
            changeNodeCommentsRequest = commentsPayload,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            settings = settings,
            appActor = appActor,
            log = log
          )
        }
      }
    }

  @Path("/{IRI}/position")
  @ApiOperation(
    value = "Update Node Position",
    nickname = "putNodePosition",
    httpMethod = "PUT",
    response = classOf[ListGetResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "\"node position\" to update",
        required = true,
        dataTypeClass = classOf[ChangeNodeCommentsApiRequestADM],
        paramType = "body"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  /**
   * Updates position of an existing list child node.
   */
  private def updateNodePosition(): Route =
    path(ListsBasePath / Segment / "position") { iri =>
      put {
        entity(as[ChangeNodePositionApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val requestMessage: Future[NodePositionChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext)
          } yield NodePositionChangeRequestADM(
            nodeIri = nodeIri,
            changeNodePositionRequest = apiRequest,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            settings = settings,
            appActor = appActor,
            log = log
          )
        }
      }
    }

  @Path("/{IRI}")
  @ApiOperation(
    value = "Update basic list information",
    nickname = "putList",
    httpMethod = "PUT",
    response = classOf[RootNodeInfoGetResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "\"list\" to update",
        required = true,
        dataTypeClass = classOf[ListNodeChangeApiRequestADM],
        paramType = "body"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  /**
   * Updates existing list node, either root or child.
   */
  private def updateList(): Route = path(ListsBasePath / Segment) { iri =>
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
          requestingUser <- getUserADM(requestContext)
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
          settings = settings,
          appActor = appActor,
          log = log
        )
      }
    }
  }
}
