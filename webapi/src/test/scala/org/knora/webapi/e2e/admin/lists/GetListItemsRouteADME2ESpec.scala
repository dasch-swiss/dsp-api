/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin.lists

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers._
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
 * End-to-End (E2E) test specification for testing lists endpoint.
 */
class GetListItemsRouteADME2ESpec
    extends E2ESpec
    with SessionJsonProtocol
    with TriplestoreJsonProtocol
    with ListADMJsonProtocol {

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(5.seconds)

  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("admin", "lists")

  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(appConfig)

  override lazy val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/ontologies/freetest-onto.ttl",
      name = "http://www.knora.org/ontology/0001/freetest"
    ),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything")
  )

  val rootCreds: CredentialsADM = CredentialsADM(
    SharedTestDataADM.rootUser,
    "test"
  )

  private val treeListInfo: ListRootNodeInfoADM    = SharedListsTestDataADM.treeListInfo
  private val treeListNodes: Seq[ListChildNodeADM] = SharedListsTestDataADM.treeListChildNodes

  "The admin lists route (/admin/lists)" should {
    "return all lists" in {
      val request =
        Get(baseApiUrl + s"/admin/lists") ~> addCredentials(BasicHttpCredentials(rootCreds.email, rootCreds.password))
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.OK)

      val lists: Seq[ListNodeInfoADM] =
        AkkaHttpUtils.httpResponseToJson(response).fields("lists").convertTo[Seq[ListNodeInfoADM]]

      lists.size should be(9)
      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "get-lists-response",
            fileExtension = "json"
          ),
          text = responseToString(response)
        )
      )
    }

    "return all lists belonging to the images project" in {
      val request = Get(
        baseApiUrl + s"/admin/lists?projectIri=http%3A%2F%2Frdfh.ch%2Fprojects%2F00FF"
      ) ~> addCredentials(rootCreds.basicHttpCredentials)
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.OK)

      val lists: Seq[ListNodeInfoADM] =
        AkkaHttpUtils.httpResponseToJson(response).fields("lists").convertTo[Seq[ListNodeInfoADM]]

      lists.size should be(4)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "get-image-project-lists-response",
            fileExtension = "json"
          ),
          text = responseToString(response)
        )
      )
    }

    "return all lists belonging to the anything project" in {
      val request = Get(
        baseApiUrl + s"/admin/lists?projectIri=http%3A%2F%2Frdfh.ch%2Fprojects%2F0001"
      ) ~> addCredentials(rootCreds.basicHttpCredentials)
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.OK)

      val lists: Seq[ListNodeInfoADM] =
        AkkaHttpUtils.httpResponseToJson(response).fields("lists").convertTo[Seq[ListNodeInfoADM]]

      lists.size should be(4)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "get-anything-project-lists-response",
            fileExtension = "json"
          ),
          text = responseToString(response)
        )
      )
    }

    "return basic list information (w/o children)" in {
      val request = Get(
        baseApiUrl + s"/admin/lists/infos/http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FtreeList"
      ) ~> addCredentials(rootCreds.basicHttpCredentials)
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.OK)

      val receivedListInfo: ListRootNodeInfoADM =
        AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

      val expectedListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.treeListInfo

      receivedListInfo.sorted should be(expectedListInfo.sorted)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "get-list-info-response",
            fileExtension = "json"
          ),
          text = responseToString(response)
        )
      )
    }

    "return basic list information (w/o children) for new merged GET route" in {
      // the same test as above, testing the new route
      val request = Get(
        baseApiUrl + s"/admin/lists/http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FtreeList/info"
      ) ~> addCredentials(rootCreds.basicHttpCredentials)
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.OK)

      val receivedListInfo: ListRootNodeInfoADM =
        AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

      val expectedListInfo: ListRootNodeInfoADM = SharedListsTestDataADM.treeListInfo

      receivedListInfo.sorted should be(expectedListInfo.sorted)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "get-list-info-response-new-merged-get-route",
            fileExtension = "json"
          ),
          text = responseToString(response)
        )
      )
    }

    "return a complete list" in {
      val request = Get(
        baseApiUrl + s"/admin/lists/http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FtreeList"
      ) ~> addCredentials(rootCreds.basicHttpCredentials)
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.OK)

      val receivedList: ListADM = AkkaHttpUtils.httpResponseToJson(response).fields("list").convertTo[ListADM]
      receivedList.listinfo.sorted should be(treeListInfo.sorted)
      receivedList.children.map(_.sorted) should be(treeListNodes.map(_.sorted))

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "get-list-response",
            fileExtension = "json"
          ),
          text = responseToString(response)
        )
      )
    }

    "return node info w/o children" in {
      val request = Get(
        baseApiUrl + s"/admin/lists/nodes/http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FtreeList01"
      ) ~> addCredentials(rootCreds.basicHttpCredentials)
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.OK)

      val receivedListInfo: ListChildNodeInfoADM =
        AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListChildNodeInfoADM]

      val expectedListInfo: ListChildNodeInfoADM = SharedListsTestDataADM.treeListNode01Info

      receivedListInfo.sorted should be(expectedListInfo.sorted)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "get-list-node-info-response",
            fileExtension = "json"
          ),
          text = responseToString(response)
        )
      )
    }

    "return a complete node with children" in {
      val request = Get(
        baseApiUrl + s"/admin/lists/http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FtreeList03"
      ) ~> addCredentials(rootCreds.basicHttpCredentials)
      val response: HttpResponse = singleAwaitingRequest(request)
      response.status should be(StatusCodes.OK)

      val receivedNode: NodeADM = AkkaHttpUtils.httpResponseToJson(response).fields("node").convertTo[NodeADM]
      receivedNode.nodeinfo.id should be("http://rdfh.ch/lists/0001/treeList03")
      receivedNode.nodeinfo.name should be(Some("Tree list node 03"))
      receivedNode.children.size should be(2)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "get-node-response",
            fileExtension = "json"
          ),
          text = responseToString(response)
        )
      )
    }
  }
}
