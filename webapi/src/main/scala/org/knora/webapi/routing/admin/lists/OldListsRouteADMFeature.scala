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
import org.knora.webapi.messages.admin.responder.listsmessages.ListsErrorMessagesADM.{
  LIST_CREATE_PERMISSION_ERROR,
  LIST_NODE_CREATE_PERMISSION_ERROR
}
import org.knora.webapi.messages.admin.responder.listsmessages.NodeCreatePayloadADM.{
  ChildNodeCreatePayloadADM,
  ListCreatePayloadADM
}
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.admin.responder.valueObjects._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}
import zio.prelude.Validation

import java.util.UUID
import javax.ws.rs.Path
import scala.concurrent.Future

object OldListsRouteADMFeature {
  val ListsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "lists")
}

/**
 * A [[Feature]] that provides the old list admin API route.
 *
 * @param routeData the [[KnoraRouteData]] to be used in constructing the route.
 */
@Api(value = "lists (old endpoint)", produces = "application/json")
@Path("/admin/lists")
class OldListsRouteADMFeature(routeData: KnoraRouteData)
    extends KnoraRoute(routeData)
    with Feature
    with Authenticator
    with ListADMJsonProtocol {

  import OldListsRouteADMFeature._

  def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
    getLists(featureFactoryConfig) ~
      createList(featureFactoryConfig) ~
      getListOrNode(featureFactoryConfig) ~
      updateList(featureFactoryConfig) ~
      createListChildNode(featureFactoryConfig) ~
      getListInfo(featureFactoryConfig) ~
      getListNodeInfo(featureFactoryConfig)

  /* return all lists optionally filtered by project */
  @ApiOperation(value = "Get lists", nickname = "getlists", httpMethod = "GET", response = classOf[ListsGetResponseADM])
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
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
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
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

  /* create a new list (root node) */
  @ApiOperation(
    value = "Add new list",
    nickname = "addList",
    httpMethod = "POST",
    response = classOf[ListGetResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "\"list\" to create",
        required = true,
        dataTypeClass = classOf[CreateListRootNodeApiRequestADM],
        paramType = "body"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
//  createListRoot
  private def createList(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath) {
    post {
      /* create a list */
      entity(as[CreateListRootNodeApiRequestADM]) { apiRequest => requestContext =>
        val maybeId: Validation[Throwable, Option[ListIRI]] = ListIRI.make(apiRequest.id)
        val projectIri: Validation[Throwable, ProjectIRI] = ProjectIRI.make(apiRequest.projectIri)
        val maybeName: Validation[Throwable, Option[ListName]] = ListName.make(apiRequest.name)
        val labels: Validation[Throwable, Labels] = Labels.make(apiRequest.labels)
        val comments: Validation[Throwable, Comments] = Comments.make(apiRequest.comments)
        val validatedListCreatePayload: Validation[Throwable, ListCreatePayloadADM] =
          Validation.validateWith(maybeId, projectIri, maybeName, labels, comments)(ListCreatePayloadADM)

        val requestMessage: Future[ListCreateRequestADM] = for {
          payload <- toFuture(validatedListCreatePayload)
          requestingUser <- getUserADM(requestContext, featureFactoryConfig)

          // check if the requesting user is allowed to perform operation
          _ = if (
            !requestingUser.permissions.isProjectAdmin(
              projectIri.toOption.get.value
            ) && !requestingUser.permissions.isSystemAdmin
          ) {
            // not project or a system admin
            throw ForbiddenException(LIST_CREATE_PERMISSION_ERROR)
          }
        } yield ListCreateRequestADM(
          createRootNode = payload,
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

  /* get a list */
  @Path("/{IRI}")
  @ApiOperation(value = "Get a list", nickname = "getlist", httpMethod = "GET", response = classOf[ListGetResponseADM])
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  private def getListOrNode(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) { iri =>
    get {
      /* return a list (a graph with all list nodes) */
      requestContext =>
        val listIri =
          stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

        val requestMessage: Future[ListGetRequestADM] = for {
          requestingUser <- getUserADM(
            requestContext = requestContext,
            featureFactoryConfig = featureFactoryConfig
          )
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
        dataTypeClass = classOf[ChangeNodeInfoApiRequestADM],
        paramType = "body"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  private def updateList(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) { iri =>
    put {
      /* update existing list node (either root or child) */
      entity(as[ChangeNodeInfoApiRequestADM]) { apiRequest => requestContext =>
        // checks if requested Iri matches the route Iri
        val listIri: Validation[Throwable, ListIRI] = if (iri == apiRequest.listIri) {
          ListIRI.make(apiRequest.listIri)
        } else {
          Validation.fail(throw BadRequestException("ListIri mismatch"))
        }

        val projectIri: Validation[Throwable, ProjectIRI] = ProjectIRI.make(apiRequest.projectIri)
        val hasRootNode: Validation[Throwable, Option[ListIRI]] = ListIRI.make(apiRequest.hasRootNode)
        val position: Validation[Throwable, Option[Position]] = Position.make(apiRequest.position)
        val name: Validation[Throwable, Option[ListName]] = ListName.make(apiRequest.name)
        val labels: Validation[Throwable, Option[Labels]] = Labels.make(apiRequest.labels)
        val comments: Validation[Throwable, Option[Comments]] = Comments.make(apiRequest.comments)

        val validatedChangeNodeInfoPayload: Validation[Throwable, NodeInfoChangePayloadADM] =
          Validation.validateWith(listIri, projectIri, hasRootNode, position, name, labels, comments)(
            NodeInfoChangePayloadADM
          )

        val requestMessage: Future[NodeInfoChangeRequestADM] = for {
          payload <- toFuture(validatedChangeNodeInfoPayload)
          requestingUser <- getUserADM(requestContext, featureFactoryConfig)
          // check if the requesting user is allowed to perform operation
          _ = if (
            !requestingUser.permissions.isProjectAdmin(
              projectIri.toOption.get.value
            ) && !requestingUser.permissions.isSystemAdmin
          ) {
            // not project or a system admin
            throw ForbiddenException(LIST_NODE_CREATE_PERMISSION_ERROR)
          }
        } yield NodeInfoChangeRequestADM(
          listIri = listIri.toOption.get.value,
          changeNodeRequest = payload,
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
   * create a new child node
   */
  @Path("/{IRI}")
  @ApiOperation(
    value = "Add new node",
    nickname = "addListNode",
    httpMethod = "POST",
    response = classOf[ChildNodeInfoGetResponseADM]
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(
        name = "body",
        value = "\"node\" to create",
        required = true,
        dataTypeClass = classOf[CreateListChildApiRequestADM],
        paramType = "body"
      )
    )
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  private def createListChildNode(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) {
    iri =>
      post {
        /* add node to existing list node. the existing list node can be either the root or a child */
        entity(as[CreateListChildApiRequestADM]) { apiRequest => requestContext =>
//          // checks if requested Iri matches the route Iri
//          val parentNodeIri: Validation[Throwable, Option[ListIRI]] = if (iri == apiRequest.parentNodeIri) {
//            ListIRI.make(apiRequest.parentNodeIri)
//          } else {
//            Validation.fail(throw BadRequestException("ListIri mismatch"))
//          }

          val id: Validation[Throwable, Option[ListIRI]] = ListIRI.make(apiRequest.id)
          val parentNodeIri: Validation[Throwable, Option[ListIRI]] = ListIRI.make(apiRequest.parentNodeIri)
          val projectIri: Validation[Throwable, ProjectIRI] = ProjectIRI.make(apiRequest.projectIri)
          val name: Validation[Throwable, Option[ListName]] = ListName.make(apiRequest.name)
          val position: Validation[Throwable, Option[Position]] = Position.make(apiRequest.position)
          val labels: Validation[Throwable, Labels] = Labels.make(apiRequest.labels)
          val comments: Validation[Throwable, Option[Comments]] = Comments.make(apiRequest.comments)
          val validatedCreateChildNodePeyload: Validation[Throwable, ChildNodeCreatePayloadADM] =
            Validation.validateWith(id, parentNodeIri, projectIri, name, position, labels, comments)(
              ChildNodeCreatePayloadADM
            )

          val requestMessage: Future[ListChildNodeCreateRequestADM] = for {
            payload <- toFuture(validatedCreateChildNodePeyload)
            requestingUser <- getUserADM(requestContext, featureFactoryConfig)

            // check if the requesting user is allowed to perform operation
            _ = if (
              !requestingUser.permissions.isProjectAdmin(
                projectIri.toOption.get.value
              ) && !requestingUser.permissions.isSystemAdmin
            ) {
              // not project or a system admin
              throw ForbiddenException(LIST_CREATE_PERMISSION_ERROR)
            }
          } yield ListChildNodeCreateRequestADM(
            createChildNodeRequest = payload,
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

  private def getListInfo(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / "infos" / Segment) {
    iri =>
      get {
        /* return information about a list (without children) */
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

  private def getListNodeInfo(featureFactoryConfig: FeatureFactoryConfig): Route =
    path(ListsBasePath / "nodes" / Segment) { iri =>
      get {
        /* return information about a single node (without children) */
        requestContext =>
          val listIri =
            stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

          val requestMessage: Future[ListNodeInfoGetRequestADM] = for {
            requestingUser <- getUserADM(
              requestContext = requestContext,
              featureFactoryConfig = featureFactoryConfig
            )
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
