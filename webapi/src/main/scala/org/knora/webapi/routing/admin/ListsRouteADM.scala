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

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.{Get, addCredentials, _}
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.stream.ActorMaterializer
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.clientapi.EndpointFunctionDSL._
import org.knora.webapi.util.clientapi._

import scala.concurrent.{ExecutionContext, Future}

object ListsRouteADM {
    val ListsBasePath = PathMatcher("admin" / "lists")
    val ListsBasePathString: String = "/admin/lists"
}

/**
 * Provides an akka-http-routing function for API routes that deal with lists.
 */
@Api(value = "lists", produces = "application/json")
@Path("/admin/lists")
class ListsRouteADM(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with ListADMJsonProtocol with ClientEndpoint {

    import ListsRouteADM._

    /**
      * The name of this [[ClientEndpoint]].
      */
    override val name: String = "ListsEndpoint"

    /**
      * The directory name to be used for this endpoint's code.
      */
    override val directoryName: String = "lists"

    /**
      * The URL path of this [[ClientEndpoint]].
      */
    override val urlPath: String = "/lists"

    /**
      * A description of this [[ClientEndpoint]].
      */
    override val description: String = "An endpoint for working with Knora lists."

    // Classes used in client function definitions.

    private val ListsResponse = classRef(OntologyConstants.KnoraAdminV2.ListsResponse.toSmartIri)

    @ApiOperation(value = "Get lists", nickname = "getlists", httpMethod = "GET", response = classOf[ListsGetResponseADM])
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /* return all lists optionally filtered by project */
    def getLists: Route = path(ListsBasePath) {
        get {
            /* return all lists */
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

    private val getListsFunction: ClientFunction =
        "getLists" description "Returns a list of lists." params() doThis {
            httpGet(BasePath)
        } returns ListsResponse


    private def getUsersTestResponse: Future[SourceCodeFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(baseApiUrl + ListsBasePathString) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield SourceCodeFileContent(
            filePath = SourceCodeFilePath.makeJsonPath("get-lists-response"),
            text = responseStr
        )
    }

    @ApiOperation(value = "Add new list", nickname = "addList", httpMethod = "POST", response = classOf[ListGetResponseADM])
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"list\" to create", required = true,
            dataTypeClass = classOf[CreateListApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /* create a new list (root node) */
    def postList: Route = path(ListsBasePath) {
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
    @ApiOperation(value = "Get a list", nickname = "getlist", httpMethod = "GET", response = classOf[ListGetResponseADM])
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /* get a list */
    def getList: Route = path(ListsBasePath / Segment) { iri =>
        get {
            /* return a list (a graph with all list nodes) */
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

    @Path("/{IRI}")
    @ApiOperation(value = "Update basic list information", nickname = "putList", httpMethod = "PUT", response = classOf[ListInfoGetResponseADM])
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"list\" to update", required = true,
            dataTypeClass = classOf[ChangeListInfoApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /**
     * update list
     */
    def putList: Route = path(ListsBasePath / Segment) { iri =>
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
    @ApiOperation(value = "Add new child node", nickname = "addListChildNode", httpMethod = "POST", response = classOf[ListNodeInfoGetResponseADM])
    @ApiImplicitParams(Array(
        new ApiImplicitParam(name = "body", value = "\"node\" to create", required = true,
            dataTypeClass = classOf[CreateChildNodeApiRequestADM], paramType = "body")
    ))
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /**
     * create a new child node
     */
    def postListChildNode: Route = path(ListsBasePath / Segment) { iri =>
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
                        requestMessage,
                        requestContext,
                        settings,
                        responderManager,
                        log
                    )
            }
        }
    }

    /* delete list node which should also delete its children */
    def deleteListNode: Route = path(ListsBasePath / Segment) { iri =>
        delete {
            /* delete (deactivate) list */
            throw NotImplementedException("Method not implemented.")
            ???
        }
    }

    override def knoraApiPath: Route = getLists ~ postList ~ getList ~ putList ~ postListChildNode ~ deleteListNode ~ {

        path(ListsBasePath / "infos" / Segment) { iri =>
            get {
                /* return information about a list (without children) */
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
            } ~
                put {
                    /* update list info */
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
                                requestMessage,
                                requestContext,
                                settings,
                                responderManager,
                                log
                            )
                    }
                }
        } ~
            path(ListsBasePath / "nodes" / Segment) { iri =>
                get {
                    /* return information about a single node (without children) */
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

    /**
      * The functions defined by this [[ClientEndpoint]].
      */
    override val functions: Seq[ClientFunction] = Seq(
        getListsFunction
    )

    /**
      * Returns test data for this endpoint.
      *
      * @return a set of test data files to be used for testing this endpoint.
      */
    override def getTestData(implicit executionContext: ExecutionContext, actorSystem: ActorSystem, materializer: ActorMaterializer): Future[Set[SourceCodeFileContent]] = {
        Future.sequence {
            Set(
                getUsersTestResponse
            )
        }
    }
}
