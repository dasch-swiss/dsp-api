/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.admin.lists

import org.apache.pekko

import scala.concurrent.duration._

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import org.knora.webapi.sharedtestdata.SharedListsTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.AkkaHttpUtils

import pekko.http.scaladsl.model.ContentTypes
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.model.StatusCodes
import pekko.http.scaladsl.testkit.RouteTestTimeout

/**
 * End-to-End (E2E) test specification for testing update node props routes.
 */
class UpdateListItemsRouteADME2ESpec
    extends E2ESpec
    with TriplestoreJsonProtocol
    with IntegrationTestListADMJsonProtocol {

  implicit def default: RouteTestTimeout = RouteTestTimeout(5.seconds)

  override lazy val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

  val rootCreds: CredentialsADM = CredentialsADM(
    SharedTestDataADM.rootUser,
    "test",
  )

  val normalUserCreds: CredentialsADM = CredentialsADM(
    SharedTestDataADM.normalUser,
    "test",
  )

  val anythingUserCreds: CredentialsADM = CredentialsADM(
    SharedTestDataADM.anythingUser1,
    "test",
  )

  val anythingAdminUserCreds: CredentialsADM = CredentialsADM(
    SharedTestDataADM.anythingAdminUser,
    "test",
  )

  val treeListInfo: ListRootNodeInfoADM    = SharedListsTestDataADM.treeListInfo
  val treeListNodes: Seq[ListChildNodeADM] = SharedListsTestDataADM.treeListChildNodes
  val treeChildNode                        = treeListNodes.head
  val newListIri: String                   = treeListInfo.id

  "The admin lists route (/admin/lists)" when {
    "updating list root node" should {
      "update only node name" in {
        val updateNodeName =
          s"""{
             |    "name": "updated root node name"
             |}""".stripMargin
        val encodedListUrl = java.net.URLEncoder.encode(treeListInfo.id, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl + "/name",
          HttpEntity(ContentTypes.`application/json`, updateNodeName),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val receivedListInfo: ListRootNodeInfoADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

        receivedListInfo.projectIri should be(SharedTestDataADM.anythingProjectIri)

        receivedListInfo.name should be(Some("updated root node name"))
      }

      "update only node labels" in {
        val updateNodeLabels =
          s"""{
             |    "labels": [{"language": "se", "value": "nya märkningen"}]
             |}""".stripMargin
        val encodedListUrl = java.net.URLEncoder.encode(treeListInfo.id, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl + "/labels",
          HttpEntity(ContentTypes.`application/json`, updateNodeLabels),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val receivedListInfo: ListRootNodeInfoADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

        receivedListInfo.projectIri should be(SharedTestDataADM.anythingProjectIri)

        val labels: Seq[StringLiteralV2] = receivedListInfo.labels.stringLiterals
        labels.size should be(1)
        labels should contain(StringLiteralV2.from(value = "nya märkningen", language = Some("se")))
      }

      "update node comments" in {
        val updateCommentsLabels =
          s"""{
             |    "comments": [{"language": "se", "value": "nya kommentarer"}]
             |}""".stripMargin
        val encodedListUrl = java.net.URLEncoder.encode(treeListInfo.id, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl + "/comments",
          HttpEntity(ContentTypes.`application/json`, updateCommentsLabels),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)
        val receivedListInfo: ListRootNodeInfoADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

        receivedListInfo.projectIri should be(SharedTestDataADM.anythingProjectIri)

        val comments: Seq[StringLiteralV2] = receivedListInfo.comments.stringLiterals
        comments.size should be(1)
        comments should contain(StringLiteralV2.from(value = "nya kommentarer", language = Some("se")))
      }

      "not delete root node comments" in {
        val deleteComments =
          s"""{
             |    "comments": []
             |}""".stripMargin
        val encodedListUrl = java.net.URLEncoder.encode(treeListInfo.id, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl + "/comments",
          HttpEntity(ContentTypes.`application/json`, deleteComments),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.BadRequest)
      }
    }

    "updating child nodes" should {
      "update only the name of the child node" in {
        val newName = "updated third child name"
        val updateNodeName =
          s"""{
             |    "name": "$newName"
             |}""".stripMargin

        val encodedListUrl = java.net.URLEncoder.encode(treeChildNode.id, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl + "/name",
          HttpEntity(ContentTypes.`application/json`, updateNodeName),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val receivedNodeInfo: ListChildNodeInfoADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListChildNodeInfoADM]
        receivedNodeInfo.name.get should be(newName)
      }

      "update only the labels of the child node" in {
        val updateNodeLabels =
          s"""{
             |    "labels": [{"language": "se", "value": "nya märkningen för nod"}]
             |}""".stripMargin

        val encodedListUrl = java.net.URLEncoder.encode(treeChildNode.id, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl + "/labels",
          HttpEntity(ContentTypes.`application/json`, updateNodeLabels),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val receivedNodeInfo: ListChildNodeInfoADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListChildNodeInfoADM]
        val labels: Seq[StringLiteralV2] = receivedNodeInfo.labels.stringLiterals
        labels.size should be(1)
        labels should contain(StringLiteralV2.from(value = "nya märkningen för nod", language = Some("se")))
      }

      "update only comments of the child node" in {
        val updateNodeComments =
          s"""{
             |    "comments": [{"language": "se", "value": "nya kommentarer för nod"}]
             |}""".stripMargin

        val encodedListUrl = java.net.URLEncoder.encode(treeChildNode.id, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl + "/comments",
          HttpEntity(ContentTypes.`application/json`, updateNodeComments),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val receivedNodeInfo: ListChildNodeInfoADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("nodeinfo").convertTo[ListChildNodeInfoADM]
        val comments: Seq[StringLiteralV2] = receivedNodeInfo.comments.stringLiterals
        comments.size should be(1)
        comments should contain(StringLiteralV2.from(value = "nya kommentarer för nod", language = Some("se")))
      }

      "not delete child node comments by sending empty array" in {
        val deleteNodeComments =
          s"""{
             |    "comments": []
             |}""".stripMargin

        val encodedListUrl = java.net.URLEncoder.encode(treeChildNode.id, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl + "/comments",
          HttpEntity(ContentTypes.`application/json`, deleteNodeComments),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.BadRequest)
      }

      "not update the position of a node if given IRI is invalid" in {
        val parentIri   = "http://rdfh.ch/lists/0001/notUsedList01"
        val newPosition = 1
        val nodeIri     = "invalid-iri"
        val updateNodeName =
          s"""{
             |    "parentNodeIri": "$parentIri",
             |    "position": $newPosition
             |}""".stripMargin

        val encodedListUrl = java.net.URLEncoder.encode(nodeIri, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl + "/position",
          HttpEntity(ContentTypes.`application/json`, updateNodeName),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.BadRequest)
      }

      "update only the position of the child node within same parent" in {
        val parentIri   = "http://rdfh.ch/lists/0001/notUsedList01"
        val newPosition = 1
        val nodeIri     = "http://rdfh.ch/lists/0001/notUsedList014"
        val updateNodeName =
          s"""{
             |    "parentNodeIri": "$parentIri",
             |    "position": $newPosition
             |}""".stripMargin

        val encodedListUrl = java.net.URLEncoder.encode(nodeIri, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl + "/position",
          HttpEntity(ContentTypes.`application/json`, updateNodeName),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val receivedNode: ListNodeADM = AkkaHttpUtils.httpResponseToJson(response).fields("node").convertTo[ListNodeADM]
        receivedNode.id should be(parentIri)
      }

      "reposition child node to the end of its parent's children" in {
        val parentIri   = "http://rdfh.ch/lists/0001/notUsedList01"
        val newPosition = -1
        val nodeIri     = "http://rdfh.ch/lists/0001/notUsedList012"
        val updateNodeName =
          s"""{
             |    "parentNodeIri": "$parentIri",
             |    "position": $newPosition
             |}""".stripMargin

        val encodedListUrl = java.net.URLEncoder.encode(nodeIri, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl + "/position",
          HttpEntity(ContentTypes.`application/json`, updateNodeName),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val receivedNode: ListNodeADM = AkkaHttpUtils.httpResponseToJson(response).fields("node").convertTo[ListNodeADM]
        receivedNode.id should be(parentIri)
      }

      "update parent and position of the child node" in {
        val parentIri   = "http://rdfh.ch/lists/0001/notUsedList"
        val newPosition = 2
        val nodeIri     = "http://rdfh.ch/lists/0001/notUsedList015"
        val updateNodeName =
          s"""{
             |    "parentNodeIri": "$parentIri",
             |    "position": $newPosition
             |}""".stripMargin

        val encodedListUrl = java.net.URLEncoder.encode(nodeIri, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl + "/position",
          HttpEntity(ContentTypes.`application/json`, updateNodeName),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val receivedNode: ListNodeADM = AkkaHttpUtils.httpResponseToJson(response).fields("node").convertTo[ListNodeADM]
        receivedNode.id should be(parentIri)
      }

      "reposition child node to end of another parent's children" in {
        val parentIri   = "http://rdfh.ch/lists/0001/notUsedList"
        val newPosition = -1
        val nodeIri     = "http://rdfh.ch/lists/0001/notUsedList015"
        val updateNodeName =
          s"""{
             |    "parentNodeIri": "$parentIri",
             |    "position": $newPosition
             |}""".stripMargin

        val encodedListUrl = java.net.URLEncoder.encode(nodeIri, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl + "/position",
          HttpEntity(ContentTypes.`application/json`, updateNodeName),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)

        response.status should be(StatusCodes.OK)

        val receivedNode: ListNodeADM = AkkaHttpUtils.httpResponseToJson(response).fields("node").convertTo[ListNodeADM]
        receivedNode.id should be(parentIri)
      }
    }

    "updating basic list information" should {
      "update basic list information" in {
        val updateListInfo: String =
          s"""{
             |    "listIri": "$newListIri",
             |    "projectIri": "${SharedTestDataADM.anythingProjectIri}",
             |    "labels": [{ "value": "Neue geänderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
             |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
             |}""".stripMargin

        val encodedListUrl = java.net.URLEncoder.encode(newListIri, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl,
          HttpEntity(ContentTypes.`application/json`, updateListInfo),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val receivedListInfo: ListRootNodeInfoADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

        receivedListInfo.projectIri should be(SharedTestDataADM.anythingProjectIri)

        val labels: Seq[StringLiteralV2] = receivedListInfo.labels.stringLiterals
        labels.size should be(2)

        val comments = receivedListInfo.comments.stringLiterals
        comments.size should be(2)
      }

      "update basic list information with a new name" in {
        val updateListName =
          s"""{
             |    "listIri": "$newListIri",
             |    "projectIri": "${SharedTestDataADM.anythingProjectIri}",
             |    "name": "a totally new name"
             |}""".stripMargin
        val encodedListUrl = java.net.URLEncoder.encode(newListIri, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl,
          HttpEntity(ContentTypes.`application/json`, updateListName),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val receivedListInfo: ListRootNodeInfoADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

        receivedListInfo.projectIri should be(SharedTestDataADM.anythingProjectIri)

        receivedListInfo.name should be(Some("a totally new name"))
      }

      "update basic list information with repeated comment and label in different languages" in {
        val updateListInfoWithRepeatedCommentAndLabelValuesRequest: String =
          s"""{
             |    "listIri": "http://rdfh.ch/lists/0001/treeList",
             |    "projectIri": "${SharedTestDataADM.anythingProjectIri}",
             |  "labels": [
             |    {"language": "en", "value": "Test List"},
             |    {"language": "se", "value": "Test List"}
             |  ],
             |  "comments": [
             |    {"language": "en", "value": "test"},
             |    {"language": "de", "value": "test"},
             |    {"language": "fr", "value": "test"},
             |     {"language": "it", "value": "test"}
             |  ]
             |}""".stripMargin

        val encodedListUrl = java.net.URLEncoder.encode("http://rdfh.ch/lists/0001/treeList", "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl,
          HttpEntity(ContentTypes.`application/json`, updateListInfoWithRepeatedCommentAndLabelValuesRequest),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.OK)

        val receivedListInfo: ListRootNodeInfoADM =
          AkkaHttpUtils.httpResponseToJson(response).fields("listinfo").convertTo[ListRootNodeInfoADM]

        receivedListInfo.projectIri should be(SharedTestDataADM.anythingProjectIri)

        val labels: Seq[StringLiteralV2] = receivedListInfo.labels.stringLiterals
        labels.size should be(2)

        val comments = receivedListInfo.comments.stringLiterals
        comments.size should be(4)
      }

      "return a ForbiddenException if the user updating the list is not project or system admin" in {
        val params =
          s"""
             |{
             |    "listIri": "$newListIri",
             |    "projectIri": "${SharedTestDataADM.anythingProjectIri}",
             |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
             |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
             |}
                """.stripMargin

        val encodedListUrl = java.net.URLEncoder.encode(newListIri, "utf-8")

        val request = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl,
          HttpEntity(ContentTypes.`application/json`, params),
        ) ~> addCredentials(anythingUserCreds.basicHttpCredentials)
        val response: HttpResponse = singleAwaitingRequest(request)
        response.status should be(StatusCodes.Forbidden)
      }

      "return a BadRequestException during list change when payload is not correct" in {
        val encodedListUrl = java.net.URLEncoder.encode(newListIri, "utf-8")

        // empty list IRI
        val params01 =
          s"""
             |{
             |    "listIri": "",
             |    "projectIri": "${SharedTestDataADM.anythingProjectIri}",
             |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
             |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
             |}
                """.stripMargin

        val request01 = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl,
          HttpEntity(ContentTypes.`application/json`, params01),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response01: HttpResponse = singleAwaitingRequest(request01)
        response01.status should be(StatusCodes.BadRequest)

        // empty project
        val params02 =
          s"""
             |{
             |    "listIri": "$newListIri",
             |    "projectIri": "",
             |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
             |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
             |}
                """.stripMargin

        val request02 = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl,
          HttpEntity(ContentTypes.`application/json`, params02),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response02: HttpResponse = singleAwaitingRequest(request02)
        response02.status should be(StatusCodes.BadRequest)

        // empty parameters
        val params03 =
          s"""
             |{
             |    "listIri": "$newListIri",
             |    "projectIri": "${SharedTestDataADM.anythingProjectIri}",
             |    "labels": [],
             |    "comments": [{ "value": "XXXXX", "language": "en"}]
             |}
                """.stripMargin

        val request03 = Put(
          baseApiUrl + s"/admin/lists/" + encodedListUrl,
          HttpEntity(ContentTypes.`application/json`, params03),
        ) ~> addCredentials(anythingAdminUserCreds.basicHttpCredentials)
        val response03: HttpResponse = singleAwaitingRequest(request03)
        response03.status should be(StatusCodes.BadRequest)

      }

    }
  }
}
