/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
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
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.{BadRequestException, ForbiddenException}
import org.knora.webapi.feature.{Feature, FeatureFactoryConfig}
import org.knora.webapi.messages.admin.responder.listsmessages.ListsMessagesUtilADM.LIST_CREATE_PERMISSION_ERROR
import org.knora.webapi.messages.admin.responder.listsmessages.NodeCreatePayloadADM.{
  ChildNodeCreatePayloadADM,
  ListCreatePayloadADM
}
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.admin.responder.valueObjects.{
  Comments,
  CustomID,
  Labels,
  ListIRI,
  ListName,
  Position,
  ProjectIRI
}
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}

import scala.concurrent.Future

object NewListsRouteADMFeature {
  val ListsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "lists")
}

/**
 * A [[Feature]] that provides the new list admin API route.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the route.
 */
@Api(value = "lists (new endpoint)", produces = "application/json")
@Path("/admin/lists")
class NewListsRouteADMFeature(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Feature
    with Authenticator
    with ListADMJsonProtocol {

  import NewListsRouteADMFeature._

  def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    getLists(featureFactoryConfig) ~
      createListItem(featureFactoryConfig) ~
      getListItem(featureFactoryConfig) ~
      updateListItem(featureFactoryConfig) ~
      getNodeInfo(featureFactoryConfig)

  /* return all lists optionally filtered by project */
  @Path("/{IRI}")
  @ApiOperation(
    httpMethod = "GET",
    response = classOf[ListsGetResponseADM],
    value = "Get lists",
    nickname = "newGetLists"
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "X-Knora-Feature-Toggles",
        value = "new-list-admin-routes:1 = on/off",
        required = true,
        dataType = "string",
        paramType = "header"
      ),
      new ApiImplicitParam(
        name = "projectIri",
        value = "IRI of the project",
        required = true,
        dataType = "string",
        paramType = "query"
      )
    )
  )
  /* return all lists optionally filtered by project */
  private def getLists(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath) {
    get {
      /* return all lists */
      parameters("projectIri".?) { maybeProjectIri: Option[IRI] => requestContext =>
        val projectIri =
          stringFormatter.validateAndEscapeOptionalIri(
            maybeProjectIri,
            throw BadRequestException(s"Invalid param project IRI: $maybeProjectIri")
          )

        val requestMessage: Future[ListsGetRequestADM] = for {
          requestingUser <- getUserADM(requestContext, featureFactoryConfig)
        } yield ListsGetRequestADM(
          projectIri = projectIri,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
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

  /* create a new list item (root or child node)*/
  @ApiOperation(
    value = "Add new list item",
    nickname = "newAddListItem",
    httpMethod = "POST",
    response = classOf[ListGetResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "\"list\" item to create",
        required = true,
        dataTypeClass = classOf[CreateNodeApiRequestADM],
        paramType = "body"
      ),
      new ApiImplicitParam(
        name = "X-Knora-Feature-Toggles",
        value = "new-list-admin-routes:1 = on/off",
        required = true,
        dataType = "string",
        paramType = "header"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  private def createListItem(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath) {
    post {
      /* create a list item (root or child node) */
      entity(as[CreateNodeApiRequestADM]) { apiRequest => requestContext =>
        val maybeId: Option[CustomID] = apiRequest.id match {
          case Some(value) => Some(CustomID.create(value).fold(e => throw e, v => v))
          case None        => None
        }

        val maybeParentNodeIri: Option[ListIRI] = apiRequest.parentNodeIri match {
          case Some(value) => Some(ListIRI.create(value).fold(e => throw e, v => v))
          case None        => None
        }

        val maybeName: Option[ListName] = apiRequest.name match {
          case Some(value) => Some(ListName.create(value).fold(e => throw e, v => v))
          case None        => None
        }

        val maybePosition: Option[Position] = apiRequest.position match {
          case Some(value) => Some(Position.create(value).fold(e => throw e, v => v))
          case None        => None
        }

        val labels = Labels.create(apiRequest.labels).fold(e => throw e, v => v)
        val comments = Comments.create(apiRequest.comments).fold(e => throw e, v => v)
        val projectIri = ProjectIRI.create(apiRequest.projectIri).fold(e => throw e, v => v)

        val createRootNodePayloadADM: ListCreatePayloadADM = ListCreatePayloadADM(
          id = maybeId,
          projectIri,
          name = maybeName,
          labels,
          comments
        )

        val createChildNodePayloadADM: ChildNodeCreatePayloadADM = ChildNodeCreatePayloadADM(
          id = maybeId,
          parentNodeIri = maybeParentNodeIri,
          projectIri,
          name = maybeName,
          position = maybePosition,
          labels,
          comments = Some(comments)
        )

        val requestMessage = for {
          requestingUser <- getUserADM(requestContext, featureFactoryConfig)

          _ = if (
            !requestingUser.permissions.isProjectAdmin(projectIri.value) && !requestingUser.permissions.isSystemAdmin
          ) {
            // not project or a system admin
            throw ForbiddenException(LIST_CREATE_PERMISSION_ERROR)
          }

          // Is parent node IRI given in the payload?
          createRequest =
            if (apiRequest.parentNodeIri.isEmpty) {
              // No, create a new list with given information of its root node.
              ListCreateRequestADM(
                createRootNode = createRootNodePayloadADM,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = requestingUser,
                apiRequestID = UUID.randomUUID()
              )
            } else {
              // Yes, create a new child and attach it to the parent node.
              ListChildNodeCreateRequestADM(
                createChildNodeRequest = createChildNodePayloadADM,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = requestingUser,
                apiRequestID = UUID.randomUUID()
              )
            }
        } yield createRequest

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

  /* get a node (root or child) */
  @Path("/{IRI}")
  @ApiOperation(
    value = "Get a list item",
    nickname = "newGetlistItem",
    httpMethod = "GET",
    response = classOf[ListGetResponseADM]
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "X-Knora-Feature-Toggles",
        value = "new-list-admin-routes:1 = on/off",
        required = true,
        dataType = "string",
        paramType = "header"
      )
    )
  )
  private def getListItem(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) { iri =>
    get {
      /* return a node, root or child, with all children */
      requestContext =>
        val listIri =
          stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

        val requestMessage: Future[ListGetRequestADM] = for {
          requestingUser <- getUserADM(requestContext, featureFactoryConfig)
        } yield ListGetRequestADM(
          iri = listIri,
          featureFactoryConfig = featureFactoryConfig,
          requestingUser = requestingUser
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

  /**
   * update list
   */
  @Path("/{IRI}")
  @ApiOperation(
    value = "Update basic node information",
    nickname = "newPutListItem",
    httpMethod = "PUT",
    response = classOf[NodeInfoGetResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "\"list\" item to update",
        required = true,
        dataTypeClass = classOf[ChangeNodeInfoApiRequestADM],
        paramType = "body"
      ),
      new ApiImplicitParam(
        name = "X-Knora-Feature-Toggles",
        value = "new-list-admin-routes:1 = on/off",
        required = true,
        dataType = "string",
        paramType = "header"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  private def updateListItem(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) { iri =>
    put {
      /* update existing list node (either root or child) */
      entity(as[ChangeNodeInfoApiRequestADM]) { apiRequest => requestContext =>
        val listIri = ListIRI.create(apiRequest.listIri).fold(e => throw e, v => v)
        val projectIri = ProjectIRI.create(apiRequest.projectIri).fold(e => throw e, v => v)

        val maybeName: Option[ListName] = apiRequest.name match {
          case Some(value) => Some(ListName.create(value).fold(e => throw e, v => v))
          case None        => None
        }

        val maybePosition: Option[Position] = apiRequest.position match {
          case Some(value) => Some(Position.create(value).fold(e => throw e, v => v))
          case None        => None
        }

        val maybeLabels: Option[Labels] = apiRequest.labels match {
          case Some(value) => Some(Labels.create(value).fold(e => throw e, v => v))
          case None        => None
        }

        val maybeComments: Option[Comments] = apiRequest.comments match {
          case Some(value) => Some(Comments.create(value).fold(e => throw e, v => v))
          case None        => None
        }

        val changeNodeInfoPayloadADM: NodeInfoChangePayloadADM = NodeInfoChangePayloadADM(
          listIri,
          projectIri,
          hasRootNode = stringFormatter.validateAndEscapeOptionalIri(
            apiRequest.hasRootNode,
            throw BadRequestException(s"Invalid root node IRI")
          ),
          position = maybePosition,
          name = maybeName,
          labels = maybeLabels,
          comments = maybeComments
        )

        val requestMessage: Future[NodeInfoChangeRequestADM] = for {
          requestingUser <- getUserADM(requestContext, featureFactoryConfig)
          // check if the requesting user is allowed to perform operation
          _ = if (
            !requestingUser.permissions.isProjectAdmin(projectIri.value) && !requestingUser.permissions.isSystemAdmin
          ) {
            // not project or a system admin
            throw ForbiddenException(LIST_CREATE_PERMISSION_ERROR)
          }
        } yield NodeInfoChangeRequestADM(
          //TODO: why "listIri" property is doubled - here and inside "changeNodeRequest"
          listIri = listIri.value,
          changeNodeRequest = changeNodeInfoPayloadADM,
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

  @Path("/{IRI}/info")
  @ApiOperation(
    value = "Get basic node information",
    nickname = "newGetNodeInfo",
    httpMethod = "PUT",
    response = classOf[RootNodeInfoGetResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "X-Knora-Feature-Toggles",
        value = "new-list-admin-routes:1 = on/off",
        required = true,
        dataType = "string",
        paramType = "header"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  private def getNodeInfo(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment / "info") {
    iri =>
      get {
        /* return information about a node, root or child, without children */
        requestContext =>
          val listIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

          val requestMessage: Future[ListNodeInfoGetRequestADM] = for {
            requestingUser <- getUserADM(requestContext, featureFactoryConfig)
          } yield ListNodeInfoGetRequestADM(
            iri = listIri,
            featureFactoryConfig = featureFactoryConfig,
            requestingUser = requestingUser
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
