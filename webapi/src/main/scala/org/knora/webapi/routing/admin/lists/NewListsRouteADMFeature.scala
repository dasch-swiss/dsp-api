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

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.http.scaladsl.util.FastFuture
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi.IRI
import org.knora.webapi.exceptions.{BadRequestException, NotImplementedException}
import org.knora.webapi.feature.{Feature, FeatureFactoryConfig}
import org.knora.webapi.messages.admin.responder.listsmessages._
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
class NewListsRouteADMFeature(routeData: KnoraRouteData) extends KnoraRoute(routeData)
    with Feature with Authenticator with ListADMJsonProtocol {

    import NewListsRouteADMFeature._

    def makeRoute(featureFactoryConfig: FeatureFactoryConfig): Route =
        getLists(featureFactoryConfig) ~
            createListItem(featureFactoryConfig) ~
            getListNode(featureFactoryConfig) ~
            updateList(featureFactoryConfig) ~
            deleteListNode(featureFactoryConfig) ~
            getNodeInfo(featureFactoryConfig)

    /* return all lists optionally filtered by project */
    @ApiOperation(value = "Get lists", nickname = "getlists", httpMethod = "GET", response = classOf[ListsGetResponseADM])
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /* return all lists optionally filtered by project */
    private def getLists(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath) {
        get {
            /* return all lists */
            parameters("projectIri".?) { maybeProjectIri: Option[IRI] =>
                requestContext =>
                    val projectIri = stringFormatter.toOptionalIri(maybeProjectIri, throw BadRequestException(s"Invalid param project IRI: $maybeProjectIri"))

                    val requestMessage: Future[ListsGetRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ListsGetRequestADM(projectIri, requestingUser)

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
    // TODO: fix swagger
    @ApiOperation(value = "Add new list item", nickname = "addListItem", httpMethod = "POST", response = classOf[ListGetResponseADM])
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"list\" item to create", required = true,
            dataTypeClass = classOf[CreateListApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    private def createListItem(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath) {
        post {
            /* create a list item (root or child node) */
            entity(as[CreateNodeApiRequestADM]) { apiRequest =>
                requestContext =>
                    val requestMessage = for {
                                requestingUser <- getUserADM(requestContext)
                                // Is parent node IRI given in the payload?
                                createRequest = if (apiRequest.parentNodeIri.isEmpty) {
                                    // No, create a new list with given information of its root node.
                                    ListCreateRequestADM(
                                        createRootNode = apiRequest,
                                        requestingUser = requestingUser,
                                        apiRequestID = UUID.randomUUID()
                                    )
                                } else {
                                    // Yes, create a new child and attach it to the parent node.
                                    ListChildNodeCreateRequestADM(
                                        createChildNodeRequest = apiRequest,
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
    @ApiOperation(value = "Get a list node", nickname = "getlistNode", httpMethod = "GET", response = classOf[ListGetResponseADM])
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    private def getListNode(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) { iri =>
        get {
            /* return a node, root or child, with all children */
            requestContext =>
                val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                val requestMessage: Future[ListGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ListGetRequestADM(listIri, requestingUser)

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
    @ApiOperation(value = "Update basic list information", nickname = "putList", httpMethod = "PUT", response = classOf[RootNodeInfoGetResponseADM])
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"list\" to update", required = true,
            dataTypeClass = classOf[ChangeNodeInfoApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    private def updateList(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) { iri =>
        put {
            /* update existing list node (either root or child) */
            entity(as[ChangeNodeInfoApiRequestADM]) { apiRequest =>
                requestContext =>
                    val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                    val requestMessage: Future[NodeInfoChangeRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield NodeInfoChangeRequestADM(
                        listIri = listIri,
                        changeNodeRequest = apiRequest,
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

    /* delete list node which should also delete its children */
    private def deleteListNode(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) { iri =>
        delete {
            /* delete (deactivate) list */
            throw NotImplementedException("Method not implemented.")
            ???
        }
    }

    private def getNodeInfo(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / "infos" / Segment) { iri =>
        get {
            /* return information about a node, root or child, without children */
            requestContext =>
                val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                val requestMessage: Future[ListNodeInfoGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ListNodeInfoGetRequestADM(listIri, requestingUser)

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
