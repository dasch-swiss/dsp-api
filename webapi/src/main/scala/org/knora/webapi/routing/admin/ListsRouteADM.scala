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
    private val CreateChildNodeRequest = classRef(OntologyConstants.KnoraAdminV2.CreateChildNodeRequest.toSmartIri)
    private val StringLiteral = classRef(OntologyConstants.KnoraAdminV2.StringLiteral.toSmartIri)
    private val anythingList = URLEncoder.encode("http://rdfh.ch/lists/0001/treeList", "UTF-8")
    private val anythingListNode = URLEncoder.encode("http://rdfh.ch/lists/0001/treeList01", "UTF-8")

    /**
     * Returns the route.
     */
    /* concatenate paths in the CORRECT order and return */
    override def knoraApiPath: Route = getLists ~ postList ~ getList ~ putListWithIRI ~ deleteList ~ getListInfo ~
        putListInfo ~ postListChildNode ~ getListNode ~ putNodeWithIRI ~ deleteListNode ~ getListNodeInfo ~ putNodeInfo

    // -------------------------------------
    // --------------- LISTS ---------------
    // -------------------------------------

    /* return all lists optionally filtered by project */
    @ApiOperation(
        value = "Get all lists optionally filtered by project",
        nickname = "getlists",
        httpMethod = "GET",
        response = classOf[ListsGetResponseADM]
    )
    @ApiResponses(Array(
        new ApiResponse(code = 500, message = "Internal server error")
    ))
    /* return all lists optionally filtered by project */
    def getLists: Route = path(ListsBasePath) {
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
    def postList: Route = path(ListsBasePath) {
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

    /** get a list with all list nodes */
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
    def getList: Route = path(ListsBasePath / Segment) { iri =>
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

    /** create new list (root node) with given IRI */
    def putListWithIRI: Route = path(ListsBasePath / Segment) { iri =>
        put {
            throw NotImplementedException("Method not implemented.")
            ???
        }
    }

    /** delete list/node which should also delete all children */
    def deleteList: Route = path(ListsBasePath / Segment) { iri =>
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
    def getListInfo: Route = path(ListsBasePath / Segment / "Info") { iri =>
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

    private val getListInfoFunction: ClientFunction =
        "getListInfo" description "Returns information about a list." params (
            "iri" description "The IRI of the list." paramType UriDatatype
            ) doThis {
            httpGet(
                path = arg("iri") / str("Info")
            )
        } returns ListInfoResponse

    private def getListInfoTestResponse: Future[SourceCodeFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$ListsBasePathString/$anythingList/Info") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield SourceCodeFileContent(
            filePath = SourceCodeFilePath.makeJsonPath("get-list-info-response"),
            text = responseStr
        )
    }

    /** update existing list info */
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
    def putListInfo: Route = path(ListsBasePath / Segment / Segment) { (iri, attribute) =>
        put {
            entity(as[ChangeListInfoApiRequestADM]) { apiRequest =>
                requestContext =>
                    val listIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list IRI: $iri"))

                    val requestPayload = attribute match {
                        case "ListInfoName" =>
                            ChangeListInfoPayloadADM(
                                listIri = apiRequest.listIri,
                                projectIri = apiRequest.projectIri,
                                name = apiRequest.name.getOrElse(throw BadRequestException("Missing parameter for name")) match {
                                    case "" => Some(None)
                                    case _ => Some(apiRequest.name)
                                }
                            )

                        case "ListInfoLabel" =>
                            ChangeListInfoPayloadADM(
                                listIri = apiRequest.listIri,
                                projectIri = apiRequest.projectIri,
                                labels = apiRequest.labels
                            )

                        case "ListInfoComment" =>
                            ChangeListInfoPayloadADM(
                                listIri = apiRequest.listIri,
                                projectIri = apiRequest.projectIri,
                                comments = apiRequest.comments
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

    private val updateListNameFunction: ClientFunction =
        "updateListName" description "Updates the name of a list." params(
            "listIri" description "The IRI of the list." paramType UriDatatype,
            "projectIri" description "The IRI of the project that the list belongs to." paramType UriDatatype,
            "name" description "The new name of the list." paramType StringDatatype
        ) doThis {
            httpPut(
                path = arg("listIri") / str("ListInfoName"),
                body = Some(json(
                    "listIri" -> arg("listIri"),
                    "projectIri" -> arg("projectIri"),
                    "name" -> arg("name")
                ))
            )
        } returns ListInfoResponse

    private def updateListNameTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("update-list-name-request"),
                text = SharedTestDataADM.updateListNameRequest(
                    listIri = "http://rdfh.ch/lists/0001/treeList",
                    projectIri = SharedTestDataADM.ANYTHING_PROJECT_IRI,
                    name = "newTestName"
                )
            )
        )
    }

    private val updateListLabelsFunction: ClientFunction =
        "updateListLabels" description "Updates the labels of a list." params(
            "listIri" description "The IRI of the list." paramType UriDatatype,
            "projectIri" description "The IRI of the project that the list belongs to." paramType UriDatatype,
            "labels" description "The new labels of the list." paramType ArrayType(StringLiteral)
        ) doThis {
            httpPut(
                path = arg("listIri") / str("ListInfoLabel"),
                body = Some(json(
                    "listIri" -> arg("listIri"),
                    "projectIri" -> arg("projectIri"),
                    "labels" -> arg("labels")
                ))
            )
        } returns ListInfoResponse

    private def updateListLabelsTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("update-list-labels-request"),
                text = SharedTestDataADM.updateListLabelsRequest(
                    listIri = "http://rdfh.ch/lists/0001/treeList",
                    projectIri = SharedTestDataADM.ANYTHING_PROJECT_IRI,
                    labels = SharedTestDataADM.updatedLabels
                )
            )
        )
    }

    private val updateListCommentsFunction: ClientFunction =
        "updateListComments" description "Updates the comments of a list." params(
            "listIri" description "The IRI of the list." paramType UriDatatype,
            "projectIri" description "The IRI of the project that the list belongs to." paramType UriDatatype,
            "comments" description "The new comments of the list." paramType ArrayType(StringLiteral)
        ) doThis {
            httpPut(
                path = arg("listIri") / str("ListInfoComment"),
                body = Some(json(
                    "listIri" -> arg("listIri"),
                    "projectIri" -> arg("projectIri"),
                    "comments" -> arg("comments")
                ))
            )
        } returns ListInfoResponse

    private def updateListCommentsTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("update-list-comments-request"),
                text = SharedTestDataADM.updateListCommentsRequest(
                    listIri = "http://rdfh.ch/lists/0001/treeList",
                    projectIri = SharedTestDataADM.ANYTHING_PROJECT_IRI,
                    comments = SharedTestDataADM.updatedComments
                )
            )
        )
    }

    // -------------------------------------
    // --------------- NODES ---------------
    // -------------------------------------


    /** create a new child node */
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
    def postListChildNode: Route = path(ListsBasePath / "nodes") {
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

    private val createChildNodeFunction: ClientFunction =
        "createChildNode" description "Creates a child node in a list." params (
            "node" description "The node to be created." paramType CreateChildNodeRequest
            ) doThis {
            httpPost(
                path = str("nodes"),
                body = Some(arg("node"))
            )
        } returns ListNodeInfoResponse

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

    /** return node with children */
    def getListNode: Route = path(ListsBasePath / "nodes" / Segment) { iri =>
        get {
            throw NotImplementedException("Method not implemented.")
            ???
        }
    }

    /** create new child node with given IRI */
    def putNodeWithIRI: Route = path(ListsBasePath / "nodes" / Segment) { iri =>
        put {
            throw NotImplementedException("Method not implemented.")
            ???
        }
    }

    /** delete list node with children if not used */
    def deleteListNode: Route = path(ListsBasePath / "nodes" / Segment) { iri =>
        delete {
            throw NotImplementedException("Method not implemented.")
            ???
        }
    }

    /** return information about a single node (without children) */
    def getListNodeInfo: Route = path(ListsBasePath / "nodes" / Segment / "Info") { iri =>
        get {
            requestContext =>
                val nodeIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param list node IRI: $iri"))

                val requestMessage: Future[ListNodeInfoGetRequestADM] = for {
                    requestingUser <- getUserADM(requestContext)
                } yield ListNodeInfoGetRequestADM(nodeIri, requestingUser)

                RouteUtilADM.runJsonRoute(
                    requestMessage,
                    requestContext,
                    settings,
                    responderManager,
                    log
                )
        }
    }

    private val getListNodeInfoFunction: ClientFunction =
        "getListNodeInfo" description "Returns information about a list node." params (
            "iri" description "The IRI of the node." paramType UriDatatype
            ) doThis {
            httpGet(
                path = str("nodes") / arg("iri") / str("Info")
            )
        } returns ListNodeInfoResponse

    private def getListNodeInfoTestResponse: Future[SourceCodeFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl$ListsBasePathString/nodes/$anythingListNode/Info") ~> addCredentials(BasicHttpCredentials(SharedTestDataADM.rootUser.email, SharedTestDataADM.testPass)))
        } yield SourceCodeFileContent(
            filePath = SourceCodeFilePath.makeJsonPath("get-list-node-info-response"),
            text = responseStr
        )
    }

    /** update list node */
    def putNodeInfo: Route = path(ListsBasePath / "nodes" / Segment / Segment) { (iri, attribute) =>
        put {
            entity(as[ChangeListNodeInfoApiRequestADM]) { apiRequest =>
                requestContext =>
                    val nodeIri = stringFormatter.validateAndEscapeIri(iri, throw BadRequestException(s"Invalid param node IRI: $iri"))

                    val requestPayload = attribute match {
                        case "NodeInfoName" =>
                            ChangeListNodeInfoPayloadADM(
                                nodeIri = apiRequest.nodeIri,
                                projectIri = apiRequest.projectIri,
                                name = apiRequest.name.getOrElse(throw BadRequestException("Missing parameter for name")) match {
                                    case "" => Some(None)
                                    case _ => Some(apiRequest.name)
                                }
                            )

                        case "NodeInfoLabel" => ChangeListNodeInfoPayloadADM(
                            nodeIri = apiRequest.nodeIri,
                            projectIri = apiRequest.projectIri,
                            labels = apiRequest.labels
                        )
                        case "NodeInfoComment" =>
                            ChangeListNodeInfoPayloadADM(
                                nodeIri = apiRequest.nodeIri,
                                projectIri = apiRequest.projectIri,
                                comments = apiRequest.comments
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

    private val updateListNodeNameFunction: ClientFunction =
        "updateListNodeName" description "Updates the name of a list node." params(
            "nodeIri" description "The IRI of the list node." paramType UriDatatype,
            "projectIri" description "The IRI of the project that the list belongs to." paramType UriDatatype,
            "name" description "The new name of the list node." paramType StringDatatype
        ) doThis {
            httpPut(
                path = str("nodes") / arg("nodeIri") / str("NodeInfoName"),
                body = Some(json(
                    "nodeIri" -> arg("nodeIri"),
                    "projectIri" -> arg("projectIri"),
                    "name" -> arg("name")
                ))
            )
        } returns ListInfoResponse

    private def updateListNodeNameTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("update-list-node-name-request"),
                text = SharedTestDataADM.updateListNodeNameRequest(
                    nodeIri = "http://rdfh.ch/lists/0001/treeList01",
                    projectIri = SharedTestDataADM.ANYTHING_PROJECT_IRI,
                    name = "newTestName"
                )
            )
        )
    }

    private val updateListNodeLabelsFunction: ClientFunction =
        "updateListNodeLabels" description "Updates the labels of a list node." params(
            "nodeIri" description "The IRI of the list node." paramType UriDatatype,
            "projectIri" description "The IRI of the project that the list belongs to." paramType UriDatatype,
            "labels" description "The new labels of the list node." paramType ArrayType(StringLiteral)
        ) doThis {
            httpPut(
                path = str("nodes") / arg("nodeIri") / str("NodeInfoLabel"),
                body = Some(json(
                    "nodeIri" -> arg("nodeIri"),
                    "projectIri" -> arg("projectIri"),
                    "labels" -> arg("labels")
                ))
            )
        } returns ListInfoResponse

    private def updateListNodeLabelsTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("update-list-node-labels-request"),
                text = SharedTestDataADM.updateListNodeLabelsRequest(
                    nodeIri = "http://rdfh.ch/lists/0001/treeList01",
                    projectIri = SharedTestDataADM.ANYTHING_PROJECT_IRI,
                    labels = SharedTestDataADM.updatedLabels
                )
            )
        )
    }

    private val updateListNodeCommentsFunction: ClientFunction =
        "updateListNodeComments" description "Updates the comments of a list node." params(
            "nodeIri" description "The IRI of the list node." paramType UriDatatype,
            "projectIri" description "The IRI of the project that the list belongs to." paramType UriDatatype,
            "comments" description "The new comments of the list node." paramType ArrayType(StringLiteral)
        ) doThis {
            httpPut(
                path = str("nodes") / arg("nodeIri") / str("NodeInfoComment"),
                body = Some(json(
                    "nodeIri" -> arg("nodeIri"),
                    "projectIri" -> arg("projectIri"),
                    "comments" -> arg("comments")
                ))
            )
        } returns ListInfoResponse

    private def updateListNodeCommentsTestRequest: Future[SourceCodeFileContent] = {
        FastFuture.successful(
            SourceCodeFileContent(
                filePath = SourceCodeFilePath.makeJsonPath("update-list-node-comments-request"),
                text = SharedTestDataADM.updateListNodeCommentsRequest(
                    nodeIri = "http://rdfh.ch/lists/0001/treeList01",
                    projectIri = SharedTestDataADM.ANYTHING_PROJECT_IRI,
                    comments = SharedTestDataADM.updatedComments
                )
            )
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
        updateListNameFunction,
        updateListLabelsFunction,
        updateListCommentsFunction,
        createChildNodeFunction,
        getListInfoFunction,
        getListNodeInfoFunction,
        updateListNodeNameFunction,
        updateListNodeLabelsFunction,
        updateListNodeCommentsFunction
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
                updateListNameTestRequest,
                updateListLabelsTestRequest,
                updateListCommentsTestRequest,
                createChildNodeTestRequest,
                getListInfoTestResponse,
                getListNodeInfoTestResponse,
                updateListNodeNameTestRequest,
                updateListNodeLabelsTestRequest,
                updateListNodeCommentsTestRequest
            )
        }
    }
}
