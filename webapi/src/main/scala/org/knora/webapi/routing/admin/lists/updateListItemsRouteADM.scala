/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
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
  @ApiOperation(value = "Update Node Name",
                nickname = "putNodeName",
                httpMethod = "PUT",
                response = classOf[NodeInfoGetResponseADM])
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body",
                           value = "\"node name\" to update",
                           required = true,
                           dataTypeClass = classOf[ChangeNodeNameApiRequestADM],
                           paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    ))
  private def updateNodeName(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / Segment / "name") { iri =>
      put {
        /* update name of an existing list node (either root or child) */
        entity(as[ChangeNodeNameApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val requestMessage: Future[NodeNameChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext, featureFactoryConfig)
          } yield
            NodeNameChangeRequestADM(
              nodeIri = nodeIri,
              changeNodeNameRequest = apiRequest,
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
  @ApiOperation(value = "Update Node Labels",
                nickname = "putNodeLabels",
                httpMethod = "PUT",
                response = classOf[NodeInfoGetResponseADM])
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body",
                           value = "\"node labels\" to update",
                           required = true,
                           dataTypeClass = classOf[ChangeNodeLabelsApiRequestADM],
                           paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    ))
  private def updateNodeLabels(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / Segment / "labels") { iri =>
      put {
        /* update labels of an existing list node (either root or child) */
        entity(as[ChangeNodeLabelsApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val requestMessage: Future[NodeLabelsChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext, featureFactoryConfig)
          } yield
            NodeLabelsChangeRequestADM(
              nodeIri = nodeIri,
              changeNodeLabelsRequest = apiRequest,
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
  @ApiOperation(value = "Update Node Comments",
                nickname = "putNodeComments",
                httpMethod = "PUT",
                response = classOf[NodeInfoGetResponseADM])
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body",
                           value = "\"node comments\" to update",
                           required = true,
                           dataTypeClass = classOf[ChangeNodeCommentsApiRequestADM],
                           paramType = "body"),
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    ))
  private def updateNodeComments(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / Segment / "comments") { iri =>
      put {
        /* update labels of an existing list node (either root or child) */
        entity(as[ChangeNodeCommentsApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val requestMessage: Future[NodeCommentsChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext, featureFactoryConfig)
          } yield
            NodeCommentsChangeRequestADM(
              nodeIri = nodeIri,
              changeNodeCommentsRequest = apiRequest,
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
  @ApiOperation(value = "Update Node Position",
                nickname = "putNodePosition",
                httpMethod = "PUT",
                response = classOf[ListGetResponseADM])
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "body",
                           value = "\"node position\" to update",
                           required = true,
                           dataTypeClass = classOf[ChangeNodeCommentsApiRequestADM],
                           paramType = "body")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    ))
  private def updateNodePosition(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / Segment / "position") { iri =>
      put {
        /* update labels of an existing list node (either root or child) */
        entity(as[ChangeNodePositionApiRequestADM]) { apiRequest => requestContext =>
          val nodeIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

          val requestMessage: Future[NodePositionChangeRequestADM] = for {
            requestingUser <- getUserADM(requestContext, featureFactoryConfig)
          } yield
            NodePositionChangeRequestADM(
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
