/*
 * Copyright © 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import io.swagger.annotations._
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.{Feature, FeatureFactoryConfig}
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.admin.responder.valueObjects.{Comments, Labels, ListName}
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}

import java.util.UUID
import javax.ws.rs.Path
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
  private def updateNodeName(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / Segment / "name") { iri =>
      put {
        entity(as[ChangeNodeNameApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val namePayload: NodeNameChangePayloadADM =
            NodeNameChangePayloadADM(ListName.make(apiRequest.name).fold(e => throw e.head, v => v))

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
  private def updateNodeLabels(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / Segment / "labels") { iri =>
      put {
        entity(as[ChangeNodeLabelsApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val labelsPayload: NodeLabelsChangePayloadADM =
            NodeLabelsChangePayloadADM(Labels.make(apiRequest.labels).fold(e => throw e.head, v => v))

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
  private def updateNodeComments(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / Segment / "comments") { iri =>
      put {
        entity(as[ChangeNodeCommentsApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val commentsPayload: NodeCommentsChangePayloadADM =
            NodeCommentsChangePayloadADM(Comments.make(apiRequest.comments).fold(e => throw e.head, v => v))

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
  private def updateNodePosition(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / Segment / "position") { iri =>
      put {
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
