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
            createList(featureFactoryConfig) ~
            getListNode(featureFactoryConfig) ~
            updateList(featureFactoryConfig) ~
            createListChildNode(featureFactoryConfig) ~
            deleteListNode(featureFactoryConfig) ~
            getListInfo(featureFactoryConfig) ~
            getListNodeInfo(featureFactoryConfig)

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

    /* create a new list (root node) */
    @ApiOperation(value = "Add new list", nickname = "addList", httpMethod = "POST", response = classOf[ListGetResponseADM])
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"list\" to create", required = true,
            dataTypeClass = classOf[CreateListApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    private def createList(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath) {
        post {
            /* create a list */
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
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    private def getListNode(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) { iri =>
        get {
            requestContext =>
                val dummyResponse: String =
                    """{ "result": "You are using the new list API" }""".stripMargin

                val httpResponse = FastFuture.successful {
                    featureFactoryConfig.addHeaderToHttpResponse(
                        HttpResponse(
                            status = StatusCodes.OK,
                            entity = HttpEntity(
                                ContentTypes.`application/json`,
                                dummyResponse
                            )
                        )
                    )
                }

                requestContext.complete(httpResponse)
        }
    }

    /**
     * update list
     */
    @Path("/{IRI}")
    @ApiOperation(value = "Update basic list information", nickname = "putList", httpMethod = "PUT", response = classOf[ListInfoGetResponseADM])
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"list\" to update", required = true,
            dataTypeClass = classOf[ChangeListInfoApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    private def updateList(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) { iri =>
        put {
            /* update existing list node (either root or child) */
            entity(as[ChangeListInfoApiRequestADM]) { apiRequest =>
                requestContext =>
                    val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                    val requestMessage: Future[ListInfoChangeRequestADM] = for {
                        requestingUser <- getUserADM(requestContext)
                    } yield ListInfoChangeRequestADM(
                        listIri = listIri,
                        changeListRequest = apiRequest,
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
    @ApiOperation(value = "Add new child node", nickname = "addListChildNode", httpMethod = "POST", response = classOf[ListNodeInfoGetResponseADM])
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"node\" to create", required = true,
            dataTypeClass = classOf[CreateChildNodeApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    private def createListChildNode(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / Segment) { iri =>
        post {
            /* add node to existing list node. the existing list node can be either the root or a child */
            entity(as[CreateChildNodeApiRequestADM]) { apiRequest =>
                requestContext =>
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

    private def getListInfo(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / "infos" / Segment) { iri =>
        get {
            /* return information about a list (without children) */
            requestContext =>
                val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                val requestMessage: Future[ListInfoGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ListInfoGetRequestADM(listIri, requestingUser)

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

    private def getListNodeInfo(featureFactoryConfig: FeatureFactoryConfig): Route = path(ListsBasePath / "nodes" / Segment) { iri =>
        get {
            /* return information about a single node (without children) */
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
        } ~
            put {
                /* update list node */
                throw NotImplementedException("Method not implemented.")
                ???
            } ~
            delete {
                /* delete list node */
                throw NotImplementedException("Method not implemented.")
                ???
            }
    }
}
