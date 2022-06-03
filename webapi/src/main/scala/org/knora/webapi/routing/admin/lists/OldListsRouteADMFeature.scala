/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.routing.admin.lists

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.PathMatcher
import akka.http.scaladsl.server.Route
import io.swagger.annotations._
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.exceptions.ForbiddenException
import org.knora.webapi.feature.Feature
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCreatePayloadADM.ListChildNodeCreatePayloadADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCreatePayloadADM.ListRootNodeCreatePayloadADM
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.routing.Authenticator
import org.knora.webapi.routing.KnoraRoute
import org.knora.webapi.routing.KnoraRouteData
import org.knora.webapi.routing.RouteUtilADM
import zio.prelude.Validation

import java.util.UUID
import javax.ws.rs.Path
import scala.concurrent.Future
import dsp.valueobjects.Iri._
import dsp.valueobjects.List._
import dsp.valueobjects.ListErrorMessages

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
      getListNode(featureFactoryConfig) ~
      getListOrNodeInfo(featureFactoryConfig, "infos") ~
      getListOrNodeInfo(featureFactoryConfig, "nodes") ~
      getListInfo(featureFactoryConfig) ~
      createListRootNode(featureFactoryConfig) ~
      createListChildNode(featureFactoryConfig) ~
      updateList(featureFactoryConfig)

  @ApiOperation(value = "Get lists", nickname = "getlists", httpMethod = "GET", response = classOf[ListsGetResponseADM])
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  /**
   * Returns all lists optionally filtered by project.
   */
  private def getLists(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath) {
    get {
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

  @Path("/{IRI}")
  @ApiOperation(value = "Get a list", nickname = "getlist", httpMethod = "GET", response = classOf[ListGetResponseADM])
  @ApiResponses(
    Array(
      new ApiResponse(code = 500, message = "Internal server error")
    )
  )
  /**
   * Returns a list node, root or child, with children (if exist).
   */
  private def getListNode(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) { iri =>
    get { requestContext =>
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
   * Returns basic information about list node, root or child, w/o children (if exist).
   */
  private def getListOrNodeInfo(featureFactoryConfig: FeatureFactoryConfig, routeSwitch: String): Route =
    path(ListsBasePath / routeSwitch / Segment) { iri =>
      get { requestContext =>
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

  /**
   * Returns basic information about a node, root or child, w/o children.
   */
  private def getListInfo(featureFactoryConfig: FeatureFactoryConfig): Route =
//  Brought from new lists route implementation, has the e functionality as getListOrNodeInfo
    path(ListsBasePath / Segment / "info") { iri =>
      get { requestContext =>
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
        dataTypeClass = classOf[ListRootNodeCreateApiRequestADM],
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
   * Creates a new list (root node).
   */
  private def createListRootNode(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath) {
    post {
      entity(as[ListRootNodeCreateApiRequestADM]) { apiRequest => requestContext =>
        val maybeId: Validation[Throwable, Option[ListIri]]    = ListIri.make(apiRequest.id)
        val projectIri: Validation[Throwable, ProjectIri]      = ProjectIri.make(apiRequest.projectIri)
        val maybeName: Validation[Throwable, Option[ListName]] = ListName.make(apiRequest.name)
        val labels: Validation[Throwable, Labels]              = Labels.make(apiRequest.labels)
        val comments: Validation[Throwable, Comments]          = Comments.make(apiRequest.comments)
        val validatedListRootNodeCreatePayload: Validation[Throwable, ListRootNodeCreatePayloadADM] =
          Validation.validateWith(maybeId, projectIri, maybeName, labels, comments)(ListRootNodeCreatePayloadADM)

        val requestMessage: Future[ListRootNodeCreateRequestADM] = for {
          payload        <- toFuture(validatedListRootNodeCreatePayload)
          requestingUser <- getUserADM(requestContext, featureFactoryConfig)

          // check if the requesting user is allowed to perform operation
          _ = if (
                !requestingUser.permissions.isProjectAdmin(
                  projectIri.toOption.get.value
                ) && !requestingUser.permissions.isSystemAdmin
              ) {
                // not project or a system admin
                throw ForbiddenException(ListErrorMessages.ListCreatePermission)
              }
        } yield ListRootNodeCreateRequestADM(
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
        dataTypeClass = classOf[ListChildNodeCreateApiRequestADM],
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
   * Creates a new list child node.
   */
  private def createListChildNode(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) {
    iri =>
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
            requestingUser <- getUserADM(requestContext, featureFactoryConfig)

            // check if the requesting user is allowed to perform operation
            _ = if (
                  !requestingUser.permissions.isProjectAdmin(
                    projectIri.toOption.get.value
                  ) && !requestingUser.permissions.isSystemAdmin
                ) {
                  // not project or a system admin
                  throw ForbiddenException(ListErrorMessages.ListCreatePermission)
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
  private def updateList(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) { iri =>
    put {
      entity(as[ListNodeChangeApiRequestADM]) { apiRequest => requestContext =>
        // check if requested Iri matches the route Iri
        val listIri: Validation[Throwable, ListIri] = if (iri == apiRequest.listIri) {
          ListIri.make(apiRequest.listIri)
        } else {
          Validation.fail(throw BadRequestException("Route and payload listIri mismatch."))
        }

        val projectIri: Validation[Throwable, ProjectIri]       = ProjectIri.make(apiRequest.projectIri)
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
          requestingUser <- getUserADM(requestContext, featureFactoryConfig)
          // check if the requesting user is allowed to perform operation
          _ = if (
                !requestingUser.permissions.isProjectAdmin(
                  projectIri.toOption.get.value
                ) && !requestingUser.permissions.isSystemAdmin
              ) {
                // not project or a system admin
                throw ForbiddenException(ListErrorMessages.ListNodeCreatePermission)
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
}
