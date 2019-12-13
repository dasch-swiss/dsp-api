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

package org.knora.webapi.routing.admin

import java.util.UUID

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}

import scala.concurrent.Future

/**
 * Provides API routes that deal with lists.
 */

@Api(value = "lists", produces = "application/json")
@Path("/admin/lists")
class ListsRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with ListADMJsonProtocol {

    /* concatenate paths in the CORRECT order and return */
    override def knoraApiPath: Route = getLists ~ postList ~ getList ~ putListWithIRI ~ deleteList ~ getListInfo ~
        putListInfo ~ postListChildNode ~ getListNode ~ putNodeWithIRI ~ deleteListNode ~ getListNodeInfo ~ putNodeInfo

    // -------------------------------------
    // --------------- LISTS ---------------
    // -------------------------------------

    @ApiOperation(
        value = "Get all lists optionally filtered by project",
        nickname = "getlists",
        httpMethod = "GET",
        response = classOf[ListsGetResponseADM]
    )
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /** return all lists optionally filtered by project */
    def getLists: Route = path("admin" / "lists") {
        get {
            parameters("projectIri".?) { maybeProjectIri: Option[IRI] =>
                requestContext =>
                    val projectIri = stringFormatter.toOptionalIri(maybeProjectIri, throw BadRequestException(s"Invalid param project IRI: $maybeProjectIri"))

                    val requestMessage: Future[ListsGetRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ListsGetRequestADM(projectIri, requestingUser)

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }

    @ApiOperation(
        value = "Add new list",
        nickname = "addList",
        httpMethod = "POST",
        response = classOf[ListGetResponseADM]
    )
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"list\" to create", required = true,
            dataTypeClass = classOf[CreateListApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /** create a new list (root node) **/
    def postList: Route = path("admin" / "lists") {
        post {
            entity(as[CreateListApiRequestADM]) { apiRequest =>
                requestContext =>
                    val requestMessage: Future[ListCreateRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ListCreateRequestADM(
                        createListRequest = apiRequest,
                        requestingUser = requestingUser,
                        apiRequestID = UUID.randomUUID()
                    )

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }

    @Path("/{IRI}")
    @ApiOperation(
        value = "Get a list with all list nodes",
        nickname = "getList",
        httpMethod = "GET",
        response = classOf[ListGetResponseADM]
    )
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /** get a list with all list nodes */
    def getList: Route = path("admin" / "lists" / Segment) { iri =>
        get {
            requestContext =>
                val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                val requestMessage: Future[ListGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ListGetRequestADM(listIri, requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    /** create new list (root node) with given IRI */
    def putListWithIRI: Route = path("admin" / "lists" / Segment) { iri =>
        put {
            throw NotImplementedException("Method not implemented.")
            ???
        }
    }

    /** delete list/node which should also delete all children */
    def deleteList: Route = path("admin" / "lists" / Segment) { iri =>
        delete {
            throw NotImplementedException("Method not implemented.")
            ???
        }
    }

    @Path("/{IRI}/Info")
    @ApiOperation(
        value = "Get basic list information (without children)",
        nickname = "getListInfo",
        httpMethod = "GET",
        response = classOf[ListInfoGetResponseADM]
    )
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /** return basic information about a list (without children) */
    def getListInfo: Route = path("admin" / "lists" / Segment / "Info") { iri =>
        get {
            requestContext =>
                val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                val requestMessage: Future[ListInfoGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ListInfoGetRequestADM(listIri, requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    @Path("/{IRI}/Info")
    @ApiOperation(
        value = "Update basic list information",
        nickname = "putListInfo",
        httpMethod = "PUT",
        response = classOf[ListInfoGetResponseADM]
    )
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"list\" to update", required = true,
            dataTypeClass = classOf[ChangeListInfoApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /** update existing list info */
    def putListInfo: Route = path("admin" / "lists" / Segment / Segment) { (iri, attribute) =>
        put {
            entity(as[ChangeListInfoApiRequestADM]) { apiRequest =>
                requestContext =>
                    val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                    val requestPayload = attribute match {
                        case "ListInfoName" => ChangeListInfoPayloadADM(
                            listIri = apiRequest.listIri,
                            projectIri = apiRequest.projectIri,
                            name = apiRequest.name match {
                                case "" => Some(None)
                                case _ => Some(Some(apiRequest.name))
                            }
                        )
                        case "ListInfoLabel" => ChangeListInfoPayloadADM(
                            listIri = apiRequest.listIri,
                            projectIri = apiRequest.projectIri,
                            labels = Some(apiRequest.labels)
                        )
                        case "ListInfoComment" => ChangeListInfoPayloadADM(
                            listIri = apiRequest.listIri,
                            projectIri = apiRequest.projectIri,
                            comments = Some(apiRequest.comments)
                        )
                        case _ => throw BadRequestException(s"Invalid attribute: $attribute")
                    }

                    val requestMessage: Future[ListInfoChangeRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ListInfoChangeRequestADM(
                        listIri = listIri,
                        changeListRequest = requestPayload,
                        requestingUser = requestingUser,
                        apiRequestID = UUID.randomUUID()
                    )

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }

    // -------------------------------------
    // --------------- NODES ---------------
    // -------------------------------------

    @Path("/{IRI}")
    @ApiOperation(
        value = "Add new child node",
        nickname = "addListChildNode",
        httpMethod = "POST",
        response = classOf[ListNodeInfoGetResponseADM]
    )
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"node\" to create", required = true,
            dataTypeClass = classOf[CreateChildNodeApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /** create a new child node */
    def postListChildNode: Route = path("admin" / "nodes") {
        post {
            /* add node to existing list node. the existing list node can be either the root or a child */
            entity(as[CreateChildNodeApiRequestADM]) { apiRequest =>
                requestContext =>
                    val iri = apiRequest.parentNodeIri
                    val parentNodeIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                    val requestMessage: Future[ListChildNodeCreateRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ListChildNodeCreateRequestADM(
                        parentNodeIri = parentNodeIri,
                        createChildNodeRequest = apiRequest,
                        requestingUser = requestingUser,
                        apiRequestID = UUID.randomUUID()
                    )

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }

    /** return node with children */
    def getListNode: Route = path("admin" / "nodes" / Segment) { iri =>
        get {
            throw NotImplementedException("Method not implemented.")
            ???
        }
    }

    /** create new child node with given IRI */
    def putNodeWithIRI: Route = path("admin" / "nodes" / Segment) { iri =>
        put {
            throw NotImplementedException("Method not implemented.")
            ???
        }
    }

    /** delete list node with children if not used */
    def deleteListNode: Route = path("admin" / "nodes" / Segment) { iri =>
        delete {
            throw NotImplementedException("Method not implemented.")
            ???
        }
    }

    /** return information about a single node (without children) */
    def getListNodeInfo: Route = path("admin" / "nodes" / Segment / "Info") { iri =>
        get {
            requestContext =>
                val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                val requestMessage: Future[ListNodeInfoGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ListNodeInfoGetRequestADM(listIri, requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    /** update list node */
    def putNodeInfo: Route = path("admin" / "nodes" / Segment / Segment) { (iri, attribute) =>
        put {
            entity(as[ChangeListNodeInfoApiRequestADM]) { apiRequest =>
                requestContext =>
                    val nodeIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

                    val requestPayload = attribute match {
                        case "NodeInfoName" => ChangeListNodeInfoPayloadADM(
                            nodeIri = apiRequest.nodeIri,
                            projectIri = apiRequest.projectIri,
                            name = apiRequest.name match {
                                case "" => Some(None)
                                case _ => Some(Some(apiRequest.name))
                            }
                        )
                        case "NodeInfoLabel" => ChangeListNodeInfoPayloadADM(
                            nodeIri = apiRequest.nodeIri,
                            projectIri = apiRequest.projectIri,
                            labels = Some(apiRequest.labels)
                        )
                        case "NodeInfoComment" => ChangeListNodeInfoPayloadADM(
                            nodeIri = apiRequest.nodeIri,
                            projectIri = apiRequest.projectIri,
                            comments = Some(apiRequest.comments)
                        )
                        case "NodeInfoPosition" => throw NotImplementedException("Move listnode to new position is not implemented.")
                        case "NodeInfoParent" => throw NotImplementedException("Move listnode to new parent is not implemented.")
                        case _ => throw BadRequestException(s"Invalid attribute: $attribute")
                    }

                    val requestMessage: Future[ListNodeInfoChangeRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ListNodeInfoChangeRequestADM(
                        nodeIri = nodeIri,
                        changeNodeRequest = requestPayload,
                        requestingUser = requestingUser,
                        apiRequestID = UUID.randomUUID()
                    )

                    RouteUtilADM.runJsonRoute(
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }
}