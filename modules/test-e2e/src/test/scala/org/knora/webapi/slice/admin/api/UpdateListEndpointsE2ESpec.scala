/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.client4.*
import sttp.model.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.admin.responder.listsmessages.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.sharedtestdata.SharedListsTestDataADM.*
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.api.Requests.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.*
import org.knora.webapi.slice.common.domain.LanguageCode.*
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

object UpdateListEndpointsE2ESpec extends E2EZSpec {

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

  private val treeChildNode: ListChildNodeADM = treeListChildNodes.head
  private val treeListInfoIri: ListIri        = ListIri.unsafeFrom(treeListInfo.id)

  override val e2eSpec = suite("The admin lists route (/admin/lists)")(
    suite("updating list root node")(
      test("update a root node name") {
        val updateReq = ListChangeNameRequest(ListName.unsafeFrom("updated root node name"))
        TestApiClient
          .putJson[RootNodeInfoGetResponseADM, ListChangeNameRequest](
            uri"/admin/lists/${treeListInfo.id}/name",
            updateReq,
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.listinfo)
          .map(info =>
            assertTrue(info.projectIri == anythingProjectIri.value, info.name.contains("updated root node name")),
          )
      },
      test("update a root node label") {
        val updateReq =
          ListChangeLabelsRequest(Labels.unsafeFrom(Seq(StringLiteralV2.from("updated root node label", EN))))
        TestApiClient
          .putJson[RootNodeInfoGetResponseADM, ListChangeLabelsRequest](
            uri"/admin/lists/${treeListInfo.id}/labels",
            updateReq,
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.listinfo)
          .map(info =>
            assertTrue(
              info.projectIri == anythingProjectIri.value,
              info.labels.stringLiterals == Vector(StringLiteralV2.from("updated root node label", EN)),
            ),
          )
      },
      test("update node comments") {
        val updateReq =
          ListChangeCommentsRequest(Comments.unsafeFrom(Seq(StringLiteralV2.from("updated root node comment", EN))))
        TestApiClient
          .putJson[RootNodeInfoGetResponseADM, ListChangeCommentsRequest](
            uri"/admin/lists/${treeListInfo.id}/comments",
            updateReq,
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.listinfo)
          .map(info =>
            assertTrue(
              info.projectIri == anythingProjectIri.value,
              info.comments.stringLiterals == Vector(StringLiteralV2.from("updated root node comment", EN)),
            ),
          )
      },
      test("not delete root node comments") {
        val deleteComments = s"""{ "comments": [] }""".stripMargin
        TestApiClient
          .putJson[RootNodeInfoGetResponseADM, String](
            uri"/admin/lists/${treeListInfo.id}/comments",
            deleteComments,
            anythingAdminUser,
          )
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
    ),
    suite("updating child nodes")(
      test("update only the name of the child node") {
        val changeReq = ListChangeNameRequest(ListName.unsafeFrom("updated third child name"))
        TestApiClient
          .putJson[ChildNodeInfoGetResponseADM, ListChangeNameRequest](
            uri"/admin/lists/${treeChildNode.id}/name",
            changeReq,
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.nodeinfo)
          .map(nodeInfo =>
            assertTrue(nodeInfo.id == treeChildNode.id, nodeInfo.name.contains("updated third child name")),
          )
      },
      test("update only the labels of the child node") {
        val changeReq =
          ListChangeLabelsRequest(Labels.unsafeFrom(Seq(StringLiteralV2.from("updated third child label", EN))))
        TestApiClient
          .putJson[ChildNodeInfoGetResponseADM, ListChangeLabelsRequest](
            uri"/admin/lists/${treeChildNode.id}/labels",
            changeReq,
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.nodeinfo)
          .map(nodeInfo =>
            assertTrue(
              nodeInfo.id == treeChildNode.id,
              nodeInfo.labels.stringLiterals == Seq(StringLiteralV2.from("updated third child label", EN)),
            ),
          )
      },
      test("update only comments of the child node") {
        val changeReq =
          ListChangeCommentsRequest(Comments.unsafeFrom(Seq(StringLiteralV2.from("updated third child comment", EN))))
        TestApiClient
          .putJson[ChildNodeInfoGetResponseADM, ListChangeCommentsRequest](
            uri"/admin/lists/${treeChildNode.id}/comments",
            changeReq,
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.nodeinfo)
          .map(nodeInfo =>
            assertTrue(
              nodeInfo.id == treeChildNode.id,
              nodeInfo.comments.stringLiterals == Seq(StringLiteralV2.from("updated third child comment", EN)),
            ),
          )
      },
      test("not delete child node comments by sending empty array") {
        val deleteComments = s"""{ "comments": [] }""".stripMargin
        TestApiClient
          .putJson[ChildNodeInfoGetResponseADM, String](
            uri"/admin/lists/${treeChildNode.id}/comments",
            deleteComments,
            anythingAdminUser,
          )
          .map(response => assertTrue(response.code == StatusCode.BadRequest))
      },
      test("update only the position of the child node within same parent") {
        val parentNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val nodeIri       = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList014")
        TestApiClient
          .putJson[NodePositionChangeResponseADM, ListChangePositionRequest](
            uri"/admin/lists/$nodeIri/position",
            ListChangePositionRequest(Position.unsafeFrom(1), parentNodeIri),
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.node)
          .map(node => assertTrue(node.id == parentNodeIri.value))
      },
      test("reposition child node to the end of its parent's children") {
        val parentNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList01")
        val nodeIri       = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList012")
        TestApiClient
          .putJson[NodePositionChangeResponseADM, ListChangePositionRequest](
            uri"/admin/lists/${nodeIri.value}/position",
            ListChangePositionRequest(Position.unsafeFrom(1), parentNodeIri),
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.node)
          .map(node => assertTrue(node.id == parentNodeIri.value))
      },
      test("update parent and position of the child node") {
        val parentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
        val nodeIri   = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList015")
        TestApiClient
          .putJson[NodePositionChangeResponseADM, ListChangePositionRequest](
            uri"/admin/lists/$nodeIri/position",
            ListChangePositionRequest(Position.unsafeFrom(2), parentIri),
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.node)
          .map(node => assertTrue(node.id == parentIri.value))
      },
      test("reposition child node to end of another parent's children") {
        val parentIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
        val nodeIri   = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList015")
        TestApiClient
          .putJson[NodePositionChangeResponseADM, ListChangePositionRequest](
            uri"/admin/lists/$nodeIri/position",
            ListChangePositionRequest(Position.unsafeFrom(-1), parentIri),
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.node)
          .map(node => assertTrue(node.id == parentIri.value))
      },
    ),
    suite("updating basic list information")(
      test("update basic list information") {
        val changeReq = ListChangeRequest(
          listIri = treeListInfoIri,
          projectIri = anythingProjectIri,
          labels = Some(
            Labels.unsafeFrom(
              Seq(StringLiteralV2.from("Neue geänderte Liste", DE), StringLiteralV2.from("Changed list", EN)),
            ),
          ),
          comments = Some(
            Comments.unsafeFrom(
              Seq(StringLiteralV2.from("Neuer Kommentar", DE), StringLiteralV2.from("New comment", EN)),
            ),
          ),
        )
        TestApiClient
          .putJson[RootNodeInfoGetResponseADM, ListChangeRequest](
            uri"/admin/lists/$treeListInfoIri",
            changeReq,
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.listinfo)
          .map(receivedListInfo =>
            assertTrue(
              receivedListInfo.projectIri == anythingProjectIri.value,
              receivedListInfo.labels.stringLiterals == Vector(
                StringLiteralV2.from("Changed list", EN),
                StringLiteralV2.from("Neue geänderte Liste", DE),
              ),
              receivedListInfo.comments.stringLiterals == Vector(
                StringLiteralV2.from("Neuer Kommentar", DE),
                StringLiteralV2.from("New comment", EN),
              ),
            ),
          )
      },
      test("update basic list information with a new name") {
        val changeReq = ListChangeRequest(
          listIri = treeListInfoIri,
          projectIri = anythingProjectIri,
          name = Some(ListName.unsafeFrom("a totally new name")),
        )
        TestApiClient
          .putJson[RootNodeInfoGetResponseADM, ListChangeRequest](
            uri"/admin/lists/$treeListInfoIri",
            changeReq,
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.listinfo)
          .map(receivedListInfo =>
            assertTrue(
              receivedListInfo.projectIri == anythingProjectIri.value,
              receivedListInfo.name.contains("a totally new name"),
            ),
          )
      },
      test("update basic list information with repeated comment and label in different languages") {
        val listIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList")
        val changeReq = ListChangeRequest(
          listIri = listIri,
          projectIri = anythingProjectIri,
          labels = Some(
            Labels.unsafeFrom(
              Seq(
                StringLiteralV2.from("Test List", EN),
                StringLiteralV2.from("Test Liste", DE),
              ),
            ),
          ),
          comments = Some(
            Comments.unsafeFrom(
              Seq(
                StringLiteralV2.from("test", EN),
                StringLiteralV2.from("Test", DE),
                StringLiteralV2.from("essai", FR),
                StringLiteralV2.from("prova", IT),
              ),
            ),
          ),
        )
        TestApiClient
          .putJson[RootNodeInfoGetResponseADM, ListChangeRequest](
            uri"/admin/lists/$listIri",
            changeReq,
            anythingAdminUser,
          )
          .flatMap(_.assert200)
          .map(_.listinfo)
          .map(receivedListInfo =>
            assertTrue(
              receivedListInfo.projectIri == anythingProjectIri.value,
              receivedListInfo.labels.stringLiterals.size == 2,
              receivedListInfo.comments.stringLiterals.size == 4,
            ),
          )
      },
      test("return a ForbiddenException if the user updating the list is not project or system admin") {
        val changeReq = ListChangeRequest(
          listIri = treeListInfoIri,
          projectIri = anythingProjectIri,
          labels = Some(
            Labels.unsafeFrom(
              Seq(StringLiteralV2.from("Neue geänderte Liste", DE), StringLiteralV2.from("Changed list", EN)),
            ),
          ),
          comments = Some(
            Comments.unsafeFrom(
              Seq(StringLiteralV2.from("Neuer Kommentar", DE), StringLiteralV2.from("New comment", EN)),
            ),
          ),
        )
        TestApiClient
          .putJson[RootNodeInfoGetResponseADM, ListChangeRequest](
            uri"/admin/lists/$treeListInfoIri",
            changeReq,
            anythingUser1,
          )
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
    ),
  )
}
