/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
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

package org.knora.webapi.e2e.admin.lists

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.E2ESpec
import org.knora.webapi.e2e.{ClientTestDataCollector, TestDataFileContent, TestDataFilePath}
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.routing.authenticationmessages.CredentialsADM
import org.knora.webapi.sharedtestdata.{SharedListsTestDataADM, SharedTestDataADM}
import org.knora.webapi.util.AkkaHttpUtils

import scala.concurrent.duration._

object DeleteListItemsRouteADME2ESpec {
  val config: Config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing  endpoint.
  */
class DeleteListItemsRouteADME2ESpec
    extends E2ESpec(DeleteListItemsRouteADME2ESpec.config)
    with SessionJsonProtocol
    with TriplestoreJsonProtocol
    with ListADMJsonProtocol {

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(5.seconds)

  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("admin", "lists")

  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(settings)

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl",
                  name = "http://www.knora.org/data/wI8G0Ps-F1USDL-F06aRHA"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl",
                  name = "http://www.knora.org/data/U7HxeFSUEQCHJxSLahw3AA")
  )

  val rootCreds: CredentialsADM = CredentialsADM(
    SharedTestDataADM.rootUser,
    "test"
  )

  val normalUserCreds: CredentialsADM = CredentialsADM(
    SharedTestDataADM.normalUser,
    "test"
  )

  val anythingUserCreds: CredentialsADM = CredentialsADM(
    SharedTestDataADM.anythingUser1,
    "test"
  )

  val anythingAdminUserCreds: CredentialsADM = CredentialsADM(
    SharedTestDataADM.anythingAdminUser,
    "test"
  )

  private val treeListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.treeListInfo
  private val treeListNodes: Seq[ListChildNodeADM] = SharedListsTestDataADM.treeListChildNodes

  "The List Items Route (/admin/lists)" when {
    "deleting list items" should {
      "return forbidden exception when requesting user is not system or project admin" in {
        val encodedNodeUrl = java.net.URLEncoder.encode(SharedListsTestDataADM.otherTreeListInfo.id, "utf-8")
        val request = Delete(baseApiUrl + s"/admin/lists/" + encodedNodeUrl) ~> addCredentials(
          BasicHttpCredentials(anythingUserCreds.user.email, anythingUserCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.Forbidden)
      }

      "delete first of two child node and remaining child" in {
        val encodedNodeUrl =
          java.net.URLEncoder.encode("http://rdfh.ch/lists/U7HxeFSUEQCHJxSLahw3AA/notUsedList0141", "utf-8")
        val request = Delete(baseApiUrl + s"/admin/lists/" + encodedNodeUrl) ~> addCredentials(
          BasicHttpCredentials(anythingAdminUserCreds.user.email, anythingAdminUserCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val node = AkkaHttpUtils.httpResponseToJson(response).fields("node").convertTo[ListNodeADM]
        node.getNodeId should be("http://rdfh.ch/lists/U7HxeFSUEQCHJxSLahw3AA/notUsedList014")
        val children = node.getChildren
        children.size should be(1)
        // last child must be shifted one place to left
        val leftChild = children.head
        leftChild.id should be("http://rdfh.ch/lists/U7HxeFSUEQCHJxSLahw3AA/notUsedList0142")
        leftChild.position should be(0)
      }
      "delete a middle node and shift its siblings" in {
        val encodedNodeUrl =
          java.net.URLEncoder.encode("http://rdfh.ch/lists/U7HxeFSUEQCHJxSLahw3AA/notUsedList02", "utf-8")
        val request = Delete(baseApiUrl + s"/admin/lists/" + encodedNodeUrl) ~> addCredentials(
          BasicHttpCredentials(anythingAdminUserCreds.user.email, anythingAdminUserCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val node = AkkaHttpUtils.httpResponseToJson(response).fields("node").convertTo[ListNodeADM]
        val children = node.getChildren
        children.size should be(2)
        // last child must be shifted one place to left
        val lastChild = children.last
        lastChild.id should be("http://rdfh.ch/lists/U7HxeFSUEQCHJxSLahw3AA/notUsedList03")
        lastChild.position should be(1)
        lastChild.children.size should be(1)
        // first child must have its child
        val firstChild = children.head
        firstChild.children.size should be(5)

        clientTestDataCollector.addFile(
          TestDataFileContent(
            filePath = TestDataFilePath(
              directoryPath = clientTestDataPath,
              filename = "delete-list-node-response",
              fileExtension = "json"
            ),
            text = responseToString(response)
          )
        )
      }

      "delete the single child of a node" in {
        val encodedNodeUrl =
          java.net.URLEncoder.encode("http://rdfh.ch/lists/U7HxeFSUEQCHJxSLahw3AA/notUsedList031", "utf-8")
        val request = Delete(baseApiUrl + s"/admin/lists/" + encodedNodeUrl) ~> addCredentials(
          BasicHttpCredentials(anythingAdminUserCreds.user.email, anythingAdminUserCreds.password))
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val node = AkkaHttpUtils.httpResponseToJson(response).fields("node").convertTo[ListNodeADM]
        node.getNodeId should be("http://rdfh.ch/lists/U7HxeFSUEQCHJxSLahw3AA/notUsedList03")
        val children = node.getChildren
        children.size should be(0)
      }
    }

    "delete a list entirely with all its children" in {
      val encodedNodeUrl =
        java.net.URLEncoder.encode("http://rdfh.ch/lists/U7HxeFSUEQCHJxSLahw3AA/notUsedList", "utf-8")
      val request = Delete(baseApiUrl + s"/admin/lists/" + encodedNodeUrl) ~> addCredentials(
        BasicHttpCredentials(anythingAdminUserCreds.user.email, anythingAdminUserCreds.password))
      val response: HttpResponse = singleAwaitingRequest(request)
      response.status should be(StatusCodes.OK)
      val deletedStatus = AkkaHttpUtils.httpResponseToJson(response).fields("deleted")
      deletedStatus.convertTo[Boolean] should be(true)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "delete-list-response",
            fileExtension = "json"
          ),
          text = responseToString(response)
        )
      )
    }

  }
}
