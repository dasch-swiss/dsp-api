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

import java.net.URLEncoder
import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{PathMatcher, Route}
import akka.http.scaladsl.util.FastFuture
import akka.stream.Materializer
import io.swagger.annotations._
import javax.ws.rs.Path
import org.knora.webapi._
import org.knora.webapi.exceptions.{BadRequestException, NotImplementedException}
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilADM}
import org.knora.webapi.util.{ClientEndpoint, TestDataFileContent, TestDataFilePath}

import org.knora.webapi.sharedtestdata.SharedTestDataADM

import scala.concurrent.{ExecutionContext, Future}

object ListsRouteADM {
    val ListsBasePath: PathMatcher[Unit] = PathMatcher("admin" / "lists")
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
     * The directory name to be used for this endpoint's code.
     */
    override val directoryName: String = "lists"

    private val anythingList = URLEncoder.encode("http://rdfh.ch/lists/0001/treeList", "UTF-8")
    private val anythingListNode = URLEncoder.encode("http://rdfh.ch/lists/0001/treeList01", "UTF-8")

    /**
     * Returns the route.
     */
    override def knoraApiPath: Route = getLists ~ postList ~ getList ~ putList ~ postListChildNode ~ deleteListNode ~
        getListInfo ~ getListNodeInfo

    /* return all lists optionally filtered by project */
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

    private def getListsTestResponse: Future[TestDataFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(baseApiUrl + ListsBasePathString) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield TestDataFileContent(
            filePath = TestDataFilePath.makeJsonPath("get-lists-response"),
            text = responseStr
        )
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

    private def createListTestRequest: Future[Set[TestDataFileContent]] = {
        FastFuture.successful(
            Set(
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("create-list-request"),
                    text = SharedTestDataADM.createListRequest
                ),
                TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath("create-list-with-custom-IRI-request"),
                    text = SharedTestDataADM.createListWithCustomIriRequest
                )
            )
        )
    }

    /* get a list */
    @Path("/{IRI}")
    @ApiOperation(value = "Get a list", nickname = "getlist", httpMethod = "GET", response = classOf[ListGetResponseADM])
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
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

    private def getListTestResponse: Future[TestDataFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$ListsBasePathString/$anythingList") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield TestDataFileContent(
            filePath = TestDataFilePath.makeJsonPath("get-list-response"),
            text = responseStr
        )
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

    private def updateListInfoTestRequest: Future[TestDataFileContent] = {
        FastFuture.successful(
            TestDataFileContent(
                filePath = TestDataFilePath.makeJsonPath("update-list-info-request"),
                text = SharedTestDataADM.updateListInfoRequest("http://rdfh.ch/lists/0001/treeList01")
            )
        )
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

    private def createChildNodeTestRequest: Future[TestDataFileContent] = {
        FastFuture.successful(
            TestDataFileContent(
                filePath = TestDataFilePath.makeJsonPath("create-child-node-request"),
                text = SharedTestDataADM.addChildListNodeRequest(
                    parentNodeIri = "http://rdfh.ch/lists/0001/treeList01",
                    name = "abc123",
                    label = "test node",
                    comment = "a node for testing"
                )
            )
        )
    }

    /* delete list node which should also delete its children */
    def deleteListNode: Route = path(ListsBasePath / Segment) { iri =>
        delete {
            /* delete (deactivate) list */
            throw NotImplementedException("Method not implemented.")
            ???
        }
    }

    def getListInfo: Route = path(ListsBasePath / "infos" / Segment) { iri =>
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
        }
    }

    private def getListInfoTestResponse: Future[TestDataFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$ListsBasePathString/infos/$anythingList") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield TestDataFileContent(
            filePath = TestDataFilePath.makeJsonPath("get-list-info-response"),
            text = responseStr
        )
    }

    def getListNodeInfo: Route = path(ListsBasePath / "nodes" / Segment) { iri =>
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

    private def getListNodeInfoTestResponse: Future[TestDataFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$ListsBasePathString/nodes/$anythingListNode") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield TestDataFileContent(
            filePath = TestDataFilePath.makeJsonPath("get-list-node-info-response"),
            text = responseStr
        )
    }

    /**
     * Returns test data for this endpoint.
     *
     * @return a set of test data files to be used for testing this endpoint.
     */
    override def getTestData(implicit executionContext: ExecutionContext, actorSystem: ActorSystem, materializer: Materializer): Future[Set[TestDataFileContent]] = {

        for {
            getListsResponse <- getListsTestResponse
            createListRequest <- createListTestRequest
            getListResponse <- getListTestResponse
            updateListInfoRequest <- updateListInfoTestRequest
            createChildNodeRequest <- createChildNodeTestRequest
            getListInfoTestResponse <- getListInfoTestResponse
            getListNodeInfoTestResponse <- getListNodeInfoTestResponse
        } yield createListRequest + getListsResponse + getListResponse + updateListInfoRequest +
            createChildNodeRequest + getListInfoTestResponse + getListNodeInfoTestResponse
    }
}
