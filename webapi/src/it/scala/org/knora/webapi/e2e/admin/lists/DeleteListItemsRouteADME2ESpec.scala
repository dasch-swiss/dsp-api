/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin.lists

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout

import scala.concurrent.duration._

import org.knora.webapi.E2ESpec
import org.knora.webapi.e2e.ClientTestDataCollector
import org.knora.webapi.e2e.TestDataFileContent
import org.knora.webapi.e2e.TestDataFilePath
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.messages.v1.responder.sessionmessages.SessionJsonProtocol
import org.knora.webapi.messages.v1.routing.authenticationmessages.CredentialsADM
import org.knora.webapi.sharedtestdata.SharedListsTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.AkkaHttpUtils

/**
 * End-to-End (E2E) test specification for testing  endpoint.
 */
class DeleteListItemsRouteADME2ESpec
    extends E2ESpec
    with SessionJsonProtocol
    with TriplestoreJsonProtocol
    with ListADMJsonProtocol {

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(5.seconds)

  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("admin", "lists")

  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(appConfig)

  // Collects client test data
  private def collectClientTestData(fileName: String, fileContent: String): Unit =
    clientTestDataCollector.addFile(
      TestDataFileContent(
        filePath = TestDataFilePath(
          directoryPath = clientTestDataPath,
          filename = fileName,
          fileExtension = "json"
        ),
        text = fileContent
      )
    )

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  val rootCreds: CredentialsADM = CredentialsADM(
    SharedTestDataADM.rootUser,
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

  "The admin lists route (/admin/lists)" when {
    "deleting list items" should {
      "return forbidden exception when requesting user is not system or project admin" in {
        val encodedNodeUrl = java.net.URLEncoder.encode(SharedListsTestDataADM.otherTreeListInfo.id, "utf-8")
        val request = Delete(baseApiUrl + s"/admin/lists/" + encodedNodeUrl) ~> addCredentials(
          BasicHttpCredentials(anythingUserCreds.user.email, anythingUserCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.Forbidden)
      }

      "delete first of two child node and remaining child" in {
        val encodedNodeUrl = java.net.URLEncoder.encode("http://rdfh.ch/lists/0001/notUsedList0141", "utf-8")
        val request = Delete(baseApiUrl + s"/admin/lists/" + encodedNodeUrl) ~> addCredentials(
          BasicHttpCredentials(anythingAdminUserCreds.user.email, anythingAdminUserCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val node = AkkaHttpUtils.httpResponseToJson(response).fields("node").convertTo[ListNodeADM]
        node.getNodeId should be("http://rdfh.ch/lists/0001/notUsedList014")
        val children = node.getChildren
        children.size should be(1)
        // last child must be shifted one place to left
        val leftChild = children.head
        leftChild.id should be("http://rdfh.ch/lists/0001/notUsedList0142")
        leftChild.position should be(0)
      }
      "delete a middle node and shift its siblings" in {
        val encodedNodeUrl = java.net.URLEncoder.encode("http://rdfh.ch/lists/0001/notUsedList02", "utf-8")
        val request = Delete(baseApiUrl + s"/admin/lists/" + encodedNodeUrl) ~> addCredentials(
          BasicHttpCredentials(anythingAdminUserCreds.user.email, anythingAdminUserCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val node     = AkkaHttpUtils.httpResponseToJson(response).fields("node").convertTo[ListNodeADM]
        val children = node.getChildren
        children.size should be(2)
        // last child must be shifted one place to left
        val lastChild = children.last
        lastChild.id should be("http://rdfh.ch/lists/0001/notUsedList03")
        lastChild.position should be(1)
        lastChild.children.size should be(1)
        // first child must have its child
        val firstChild = children.head
        firstChild.children.size should be(5)

        collectClientTestData("delete-list-node-response", responseToString(response))
      }

      "delete the single child of a node" in {
        val encodedNodeUrl = java.net.URLEncoder.encode("http://rdfh.ch/lists/0001/notUsedList031", "utf-8")
        val request = Delete(baseApiUrl + s"/admin/lists/" + encodedNodeUrl) ~> addCredentials(
          BasicHttpCredentials(anythingAdminUserCreds.user.email, anythingAdminUserCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val node = AkkaHttpUtils.httpResponseToJson(response).fields("node").convertTo[ListNodeADM]
        node.getNodeId should be("http://rdfh.ch/lists/0001/notUsedList03")
        val children = node.getChildren
        children.size should be(0)
      }

      "delete a list entirely with all its children" in {
        val encodedNodeUrl = java.net.URLEncoder.encode("http://rdfh.ch/lists/0001/notUsedList", "utf-8")
        val request = Delete(baseApiUrl + s"/admin/lists/" + encodedNodeUrl) ~> addCredentials(
          BasicHttpCredentials(anythingAdminUserCreds.user.email, anythingAdminUserCreds.password)
        )
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val deletedStatus = AkkaHttpUtils.httpResponseToJson(response).fields("deleted")
        deletedStatus.convertTo[Boolean] should be(true)

        collectClientTestData("delete-list-response", responseToString(response))
      }
    }

  }

  "The admin lists candelete route (/admin/lists/candelete)" when {
    "used to query if list can be deleted" should {
      "return positive response for unused list" in {
        val unusedList        = "http://rdfh.ch/lists/0001/notUsedList"
        val unusedListEncoded = java.net.URLEncoder.encode(unusedList, "utf-8")
        val request = Get(baseApiUrl + s"/admin/lists/candelete/" + unusedListEncoded) ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )

        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val canDelete = AkkaHttpUtils.httpResponseToJson(response).fields("canDeleteList")
        canDelete.convertTo[Boolean] should be(true)
        val listIri = AkkaHttpUtils.httpResponseToJson(response).fields("listIri")
        listIri.convertTo[String] should be(unusedList)

        collectClientTestData("candeletelist-response", responseToString(response))
      }

      "return negative response for used list" in {
        val usedList        = "http://rdfh.ch/lists/0001/treeList01"
        val usedListEncoded = java.net.URLEncoder.encode(usedList, "utf-8")
        val request = Get(baseApiUrl + s"/admin/lists/candelete/" + usedListEncoded) ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )

        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val canDelete = AkkaHttpUtils.httpResponseToJson(response).fields("canDeleteList")
        canDelete.convertTo[Boolean] should be(false)
        val listIri = AkkaHttpUtils.httpResponseToJson(response).fields("listIri")
        listIri.convertTo[String] should be(usedList)
      }

      "return exception for bad list iri" in {
        val badlistIri        = "bad list Iri"
        val badListIriEncoded = java.net.URLEncoder.encode(badlistIri, "utf-8")
        val request = Get(baseApiUrl + s"/admin/lists/candelete/" + badListIriEncoded) ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )

        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)
      }
    }
  }

  "The admin lists comments route (/admin/lists/comments)" when {
    "deleting comments" should {
      "delete child node comments" in {
        val childNodeIri        = "http://rdfh.ch/lists/0001/testList01"
        val childNodeIriEncoded = java.net.URLEncoder.encode(childNodeIri, "utf-8")
        val request = Delete(s"$baseApiUrl/admin/lists/comments/$childNodeIriEncoded") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )

        val response: HttpResponse = singleAwaitingRequest(request)
        val responseStr            = responseToString(response)

        response.status should be(StatusCodes.OK)
        val commentsDeleted = AkkaHttpUtils.httpResponseToJson(response).fields("commentsDeleted")
        commentsDeleted.convertTo[Boolean] should be(true)
        val nodeIri = AkkaHttpUtils.httpResponseToJson(response).fields("nodeIri")
        nodeIri.convertTo[String] should be(childNodeIri)

        collectClientTestData("delete-list-node-comments-response", responseStr)
      }

      "return exception for root node comments" in {
        val childNodeIri        = "http://rdfh.ch/lists/0001/testList"
        val childNodeIriEncoded = java.net.URLEncoder.encode(childNodeIri, "utf-8")
        val request = Delete(s"$baseApiUrl/admin/lists/comments/$childNodeIriEncoded") ~> addCredentials(
          BasicHttpCredentials(rootCreds.email, rootCreds.password)
        )

        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)
      }
    }
  }
}
