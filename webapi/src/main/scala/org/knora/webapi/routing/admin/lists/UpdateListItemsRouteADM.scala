/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import java.util.UUID
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import io.swagger.annotations._

import javax.ws.rs.Path
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.{Feature, FeatureFactoryConfig}
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.admin.responder.valueObjects.{Comments, Labels, ListName}
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}

import scala.concurrent.Future

object UpdateListItemsRouteADM {
  val ListsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "lists")
}

/**
 * A [[Feature]] that provides routes to delete list items.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the route.
 */
class UpdateListItemsRouteADM(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Feature
    with Authenticator
    with ListADMJsonProtocol {

  import UpdateListItemsRouteADM._

  def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    updateNodeName(featureFactoryConfig) ~
      updateNodeLabels(featureFactoryConfig) ~
      updateNodeComments(featureFactoryConfig) ~
      updateNodePosition(featureFactoryConfig)

  /**
   * update node name
   */
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
  private def updateNodeName(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / Segment / "name") { iri =>
      put {
        /* update name of an existing list node (either root or child) */
        entity(as[ChangeNodeNameApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val namePayload: NodeNameChangePayloadADM =
            NodeNameChangePayloadADM(ListName.create(apiRequest.name).fold(e => throw e, v => v))

          val requestMessage: Future[NodeNameChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext, featureFactoryConfig)
          } yield NodeNameChangeRequestADM(
            nodeIri = nodeIri,
            changeNodeNameRequest = namePayload,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
        }
      }
    }

  /**
   * update node labels
   */
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
  private def updateNodeLabels(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / Segment / "labels") { iri =>
      put {
        /* update labels of an existing list node (either root or child) */
        entity(as[ChangeNodeLabelsApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val labelsPayload: NodeLabelsChangePayloadADM =
            NodeLabelsChangePayloadADM(Labels.create(apiRequest.labels).fold(e => throw e, v => v))

          val requestMessage: Future[NodeLabelsChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext, featureFactoryConfig)
          } yield NodeLabelsChangeRequestADM(
            nodeIri = nodeIri,
            changeNodeLabelsRequest = labelsPayload,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
        }
      }
    }

  /**
   * update node comments
   */
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
  private def updateNodeComments(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / Segment / "comments") { iri =>
      put {
        /* update comments of an existing list node (either root or child) */
        entity(as[ChangeNodeCommentsApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val commentsPayload: NodeCommentsChangePayloadADM = if (apiRequest.comments.isEmpty) {
            NodeCommentsChangePayloadADM(None)
          } else {
            NodeCommentsChangePayloadADM(Some(Comments.create(apiRequest.comments).fold(e => throw e, v => v)))
          }

          val requestMessage: Future[NodeCommentsChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext, featureFactoryConfig)
          } yield NodeCommentsChangeRequestADM(
            nodeIri = nodeIri,
            changeNodeCommentsRequest = commentsPayload,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
        }
      }
    }

  /**
   * update node position
   */
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
  private def updateNodePosition(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / Segment / "position") { iri =>
      put {
        /* update labels of an existing list node (either root or child) */
        entity(as[ChangeNodePositionApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val requestMessage: Future[NodePositionChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext, featureFactoryConfig)
          } yield NodePositionChangeRequestADM(
            nodeIri = nodeIri,
            changeNodePositionRequest = apiRequest,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser,
            apiRequestID = UUID.randomUUID()
          )

          RouteUtilADM.runJsonRoute(
            requestMessageF = requestMessage,
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig,
            settings = settings,
            responderManager = responderManager,
            log = log
          )
        }
      }
    }
}
