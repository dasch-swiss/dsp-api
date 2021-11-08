/*
 * Copyright © 2021 Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import io.swagger.annotations._
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.{BadRequestException, ForbiddenException}
import org.knora.webapi.feature.{Feature, FeatureFactoryConfig}
import org.knora.webapi.messages.admin.responder.listsmessages.ListsErrorMessagesADM.LIST_CREATE_PERMISSION_ERROR
import org.knora.webapi.messages.admin.responder.listsmessages.NodeCreatePayloadADM.{
  ChildNodeCreatePayloadADM,
  ListCreatePayloadADM
}
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.admin.responder.valueObjects._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}

import java.util.UUID
import javax.ws.rs.Path
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
        val maybeId = ListIRI.make(apiRequest.id).fold(e => throw e.head, v => v)
        val projectIri = ProjectIRI.make(apiRequest.projectIri).fold(e => throw e.head, v => v)
        val maybeName = ListName.make(apiRequest.name).fold(e => throw e.head, v => v)
        val labels = Labels.make(apiRequest.labels).fold(e => throw e.head, v => v)
        val comments = Comments.make(apiRequest.comments).fold(e => throw e.head, v => v)

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
              val createRootNodePayloadADM: ListCreatePayloadADM = ListCreatePayloadADM(
                id = maybeId,
                projectIri,
                name = maybeName,
                labels,
                comments
              )

              ListCreateRequestADM(
                createRootNode = createRootNodePayloadADM,
                featureFactoryConfig = featureFactoryConfig,
                requestingUser = requestingUser,
                apiRequestID = UUID.randomUUID()
              )
            } else {
              // Yes, create a new child and attach it to the parent node.

              // allows to omit comments / send empty comments creating child node
              val maybeComments = if (apiRequest.comments.isEmpty) {
                None
              } else {
                Some(comments)
              }

              val createChildNodePayloadADM: ChildNodeCreatePayloadADM = ChildNodeCreatePayloadADM(
                id = maybeId,
                parentNodeIri = maybeId,
                projectIri,
                name = maybeName,
                position = Position.make(apiRequest.position).fold(e => throw e.head, v => v),
                labels,
                comments = maybeComments
              )

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
        val listIri = ListIRI.make(apiRequest.listIri).fold(e => throw e.head, v => v)
        val projectIri = ProjectIRI.make(apiRequest.projectIri).fold(e => throw e.head, v => v)

        val changeNodeInfoPayloadADM: NodeInfoChangePayloadADM = NodeInfoChangePayloadADM(
          listIri,
          projectIri,
          hasRootNode = ListIRI.make(apiRequest.hasRootNode).fold(e => throw e.head, v => v),
          position = Position.make(apiRequest.position).fold(e => throw e.head, v => v),
          name = ListName.make(apiRequest.name).fold(e => throw e.head, v => v),
          labels = Labels.make(apiRequest.labels).fold(e => throw e.head, v => v),
          comments = Comments.make(apiRequest.comments).fold(e => throw e.head, v => v)
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
