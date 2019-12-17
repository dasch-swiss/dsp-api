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
    private val CreateListRequest = classRef(OntologyConstants.KnoraAdminV2.CreateListRequest.toSmartIri)
    private val ListResponse = classRef(OntologyConstants.KnoraAdminV2.ListResponse.toSmartIri)
    private val ListInfoResponse = classRef(OntologyConstants.KnoraAdminV2.ListInfoResponse.toSmartIri)
    private val ListNodeInfoResponse = classRef(OntologyConstants.KnoraAdminV2.ListNodeInfoResponse.toSmartIri)
    private val UpdateListInfoRequest = classRef(OntologyConstants.KnoraAdminV2.UpdateListInfoRequest.toSmartIri)
    private val CreateChildNodeRequest = classRef(OntologyConstants.KnoraAdminV2.CreateChildNodeRequest.toSmartIri)
    private val anythingList = URLEncoder.encode("http://rdfh.ch/lists/0001/treeList", "UTF-8")
    private val anythingListNode = URLEncoder.encode("http://rdfh.ch/lists/0001/treeList01", "UTF-8")

    /**
     * Returns the route.
     */
    override def knoraApiPath: Route = getLists ~ postList ~ getList ~ putList ~ postListChildNode ~ deleteListNode ~
        getListInfo ~ updateListInfo ~ getListNodeInfo

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

    private val getListsFunction: ClientFunction =
        "getLists" description "Returns a list of lists." params() doThis {
            httpGet(BasePath)
        } returns ListsResponse


    // #getListsInProjectFunction
    private val getListsInProjectFunction: ClientFunction =
        "getListsInProject" description "Returns a list of lists in a project." params (
            "projectIri" description "The IRI of the project." paramType UriDatatype
            ) doThis {
            httpGet(
                path = BasePath,
                params = Seq("projectIri" -> arg("projectIri"))
            )
        } returns ListsResponse
    // #getListsInProjectFunction

    private def getListsTestResponse: Future[SourceCodeFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(baseApiUrl + ListsBasePathString) ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield SourceCodeFileContent(
            filePath = SourceCodeFilePath.makeJsonPath("get-lists-response"),
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

    private val createListFunction: ClientFunction =
        "createList" description "Creates a list." params (
            "listInfo" description "Information about the list to be created." paramType CreateListRequest
            ) doThis {
            httpPost(
                path = BasePath,
                body = Some(arg("listInfo"))
            )
        } returns ListResponse

    private def createListTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("create-list-request"),
                text = SharedTestDataADM.createListRequest
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

    private val getListFunction: ClientFunction =
        "getList" description "Gets a list." params (
            "iri" description "The IRI of the list." paramType UriDatatype
            ) doThis {
            httpGet(
                path = arg("iri")
            )
        } returns ListResponse

    private def getListTestResponse: Future[SourceCodeFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$ListsBasePathString/$anythingList") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield SourceCodeFileContent(
            filePath = SourceCodeFilePath.makeJsonPath("get-list-response"),
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

    private val updateListInfoFunction: ClientFunction =
        "updateListInfo" description "Updates information about a list." params (
            "listInfo" description "Information about the list to be created." paramType UpdateListInfoRequest
            ) doThis {
            httpPut(
                path = argMember("listInfo", "listIri"),
                body = Some(arg("listInfo"))
            )
        } returns ListInfoResponse

    private def updateListInfoTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("update-list-info-request"),
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

    private val createChildNodeFunction: ClientFunction =
        "createChildNode" description "Creates a child node in a list." params (
            "node" description "The node to be created." paramType CreateChildNodeRequest
            ) doThis {
            httpPost(
                path = argMember("node", "parentNodeIri"),
                body = Some(arg("node"))
            )
        } returns ListInfoResponse

    private def createChildNodeTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("create-child-node-request"),
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

    private val getListInfoFunction: ClientFunction =
        "getListInfo" description "Returns information about a list." params (
            "iri" description "The IRI of the list." paramType UriDatatype
            ) doThis {
            httpGet(
                path = str("infos") / arg("iri")
            )
        } returns ListInfoResponse

    private def getListInfoTestResponse: Future[SourceCodeFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$ListsBasePathString/infos/$anythingList") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield SourceCodeFileContent(
            filePath = SourceCodeFilePath.makeJsonPath("get-list-info-response"),
            text = responseStr
        )
    }

    def updateListInfo: Route = path(ListsBasePath / "infos" / Segment) { iri =>
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

    private val getListNodeInfoFunction: ClientFunction =
        "getListNodeInfo" description "Returns information about a list node." params (
            "iri" description "The IRI of the node." paramType UriDatatype
            ) doThis {
            httpGet(
                path = str("nodes") / arg("iri")
            )
        } returns ListNodeInfoResponse

    private def getListNodeInfoTestResponse: Future[SourceCodeFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$ListsBasePathString/nodes/$anythingListNode") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield SourceCodeFileContent(
            filePath = SourceCodeFilePath.makeJsonPath("get-list-node-info-response"),
            text = responseStr
        )
    }

    /**
     * The functions defined by this [[ClientEndpoint]].
     */
    override val functions: Seq[ClientFunction] = Seq(
        getListsFunction,
        getListsInProjectFunction,
        createListFunction,
        getListFunction,
        updateListInfoFunction,
        createChildNodeFunction,
        getListInfoFunction,
        getListNodeInfoFunction
    )

    /**
     * Returns test data for this endpoint.
     *
     * @return a set of test data files to be used for testing this endpoint.
     */
    override def getTestData(implicit executionContext: ExecutionContext, actorSystem: ActorSystem, materializer: ActorMaterializer): Future[Set[SourceCodeFileContent]] = {
        Future.sequence {
            Set(
                getListsTestResponse,
                createListTestRequest,
                getListTestResponse,
                updateListInfoTestRequest,
                createChildNodeTestRequest,
                getListInfoTestResponse,
                getListNodeInfoTestResponse
            )
        }
    }
}
