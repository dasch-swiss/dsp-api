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

package org.knora.webapi.routing.v2

import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.http.scaladsl.client.RequestBuilding.Get
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import org.knora.webapi._
import org.knora.webapi.messages.v2.responder.listsmessages.{ListGetRequestV2, NodeGetRequestV2}
import org.knora.webapi.routing.{Authenticator, KnoraRoute, KnoraRouteData, RouteUtilV2}
import org.knora.webapi.util.{ClientEndpoint, TestDataFileContent, TestDataFilePath}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Provides a function for API routes that deal with lists and nodes.
 */
class ListsRouteV2(routeData: KnoraRouteData) extends KnoraRoute(routeData) with Authenticator with ClientEndpoint {

    // Directory name for generated test data
    override val directoryName: String = "lists"

    /**
     * Returns the route.
     */
    override def knoraApiPath: Route = getList ~ getNode

    private def getList: Route = path("v2" / "lists" / Segment) { lIri: String =>
        get {
            /* return a list (a graph with all list nodes) */
            requestContext =>
                val requestMessage: Future[ListGetRequestV2] = for {
                    requestingUser <- getUserADM(requestContext)
                    listIri: IRI = stringFormatter.validateAndEscapeIri(lIri, throw BadRequestException(s"Invalid list IRI: '$lIri'"))
                } yield ListGetRequestV2(listIri, requestingUser)

                RouteUtilV2.runRdfRouteWithFuture(
                    requestMessageF = requestMessage,
                    requestContext = requestContext,
                    settings = settings,
                    responderManager = responderManager,
                    log = log,
                    targetSchema = ApiV2Complex,
                    schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                )
        }
    }

    // Lists to return in test data.
    private val testLists: Map[String, IRI] = Map(
        "treelist" -> SharedTestDataADM.treeList,
        "othertreelist" -> SharedTestDataADM.otherTreeList
    )

    private def getListTestResponses: Future[Set[TestDataFileContent]] = {
        val responseFutures: Iterable[Future[TestDataFileContent]] = testLists.map {
            case (filename, listIri) =>
                val encodedListIri = URLEncoder.encode(listIri, "UTF-8")

                for {
                    responseStr <- doTestDataRequest(Get(s"$baseApiUrl/v2/lists/$encodedListIri"))
                } yield TestDataFileContent(
                    filePath = TestDataFilePath.makeJsonPath(filename),
                    text = responseStr
                )
        }

        Future.sequence(responseFutures).map(_.toSet)
    }

    private def getNode: Route = path("v2" / "node" / Segment) { nIri: String =>
        get {
            /* return a list node */
            requestContext =>
                val requestMessage: Future[NodeGetRequestV2] = for {
                    requestingUser <- getUserADM(requestContext)
                    nodeIri: IRI = stringFormatter.validateAndEscapeIri(nIri, throw BadRequestException(s"Invalid list IRI: '$nIri'"))
                } yield NodeGetRequestV2(nodeIri, requestingUser)

                RouteUtilV2.runRdfRouteWithFuture(
                    requestMessageF = requestMessage,
                    requestContext = requestContext,
                    settings = settings,
                    responderManager = responderManager,
                    log = log,
                    targetSchema = ApiV2Complex,
                    schemaOptions = RouteUtilV2.getSchemaOptions(requestContext)
                )
        }
    }

    private def getNodeTestResponse: Future[TestDataFileContent] = {
        for {
            responseStr <- doTestDataRequest(Get(s"$baseApiUrl/v2/node/${URLEncoder.encode(SharedTestDataADM.treeListNode, "UTF-8")}"))
        } yield TestDataFileContent(
            filePath = TestDataFilePath.makeJsonPath("listnode"),
            text = responseStr
        )
    }


    override def getTestData(implicit executionContext: ExecutionContext,
                             actorSystem: ActorSystem,
                             materializer: ActorMaterializer): Future[Set[TestDataFileContent]] = {
        for {
            testLists <- getListTestResponses
            testNode <- getNodeTestResponse
        } yield testLists + testNode
    }
}
