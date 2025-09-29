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
import org.knora.webapi.sharedtestdata.SharedListsTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

object DeleteAdminListsEndpointsE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = anythingRdfTestdata

  override val e2eSpec = suite("The admin lists route (/admin/lists)")(
    suite("deleting list items")(
      test("return forbidden exception when requesting user is not system or project admin") {
        val listIri = ListIri.unsafeFrom(SharedListsTestDataADM.otherTreeListInfo.id)
        TestApiClient
          .deleteJson[ListItemDeleteResponseADM](uri"/admin/lists/$listIri", anythingUser1)
          .map(response => assertTrue(response.code == StatusCode.Forbidden))
      },
      test("delete first of two child node and remaining child") {
        val deleteIri = "http://rdfh.ch/lists/0001/notUsedList0141"
        TestApiClient
          .deleteJson[ChildNodeDeleteResponseADM](uri"/admin/lists/$deleteIri", anythingAdminUser)
          .flatMap(_.assert200)
          .map(_.node)
          .map(node =>
            assertTrue(
              node.id == "http://rdfh.ch/lists/0001/notUsedList014",
              node.children.size == 1,
              node.children.head.id == "http://rdfh.ch/lists/0001/notUsedList0142",
              node.children.head.position == 0,
            ),
          )
      },
      test("delete a middle node and shift its siblings") {
        val listIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList02")
        TestApiClient
          .deleteJson[ChildNodeDeleteResponseADM](uri"/admin/lists/$listIri", anythingAdminUser)
          .flatMap(_.assert200)
          .map(_.node)
          .map(node =>
            assertTrue(
              node.children.size == 2,
              node.children.last.id == "http://rdfh.ch/lists/0001/notUsedList03",
              node.children.last.position == 1,
              node.children.last.children.size == 1,
              node.children.head.children.size == 5,
            ),
          )
      },
      test("delete the single child of a node") {
        val listIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList031")
        TestApiClient
          .deleteJson[ChildNodeDeleteResponseADM](uri"/admin/lists/$listIri", anythingAdminUser)
          .flatMap(_.assert200)
          .map(_.node)
          .map(node => assertTrue(node.id == "http://rdfh.ch/lists/0001/notUsedList03", node.children.isEmpty))
      },
      test("delete a list entirely with all its children") {
        val listIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
        TestApiClient
          .deleteJson[ListDeleteResponseADM](uri"/admin/lists/$listIri", anythingAdminUser)
          .flatMap(_.assert200)
          .map(response => assertTrue(response.deleted))
      },
    ),
    suite("The admin lists candelete route (/admin/lists/candelete)")(
      suite("used to query if list can be deleted")(
        test("return positive response for unused list") {
          val unusedList = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/notUsedList")
          TestApiClient
            .getJson[CanDeleteListResponseADM](uri"/admin/lists/candelete/$unusedList", rootUser)
            .flatMap(_.assert200)
            .map(response => assertTrue(response.canDeleteList, response.listIri == unusedList.value))
        },
        test("return negative response for used list") {
          val usedList = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList01")
          TestApiClient
            .getJson[CanDeleteListResponseADM](uri"/admin/lists/candelete/$usedList", rootUser)
            .flatMap(_.assert200)
            .map(response => assertTrue(!response.canDeleteList, response.listIri == usedList.value))
        },
      ),
      suite("The admin lists comments route (/admin/lists/comments)")(
        suite("deleting comments")(
          test("delete child node comments") {
            val childNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/testList01")
            TestApiClient
              .deleteJson[ListNodeCommentsDeleteResponseADM](uri"/admin/lists/comments/$childNodeIri", rootUser)
              .flatMap(_.assert200)
              .map(response => assertTrue(response.commentsDeleted, response.nodeIri == childNodeIri.value))
          },
          test("return exception for root node comments") {
            val childNodeIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/testList")
            TestApiClient
              .deleteJson[ListNodeCommentsDeleteResponseADM](uri"/admin/lists/comments/$childNodeIri", rootUser)
              .map(response => assertTrue(response.code == StatusCode.BadRequest))
          },
        ),
      ),
    ),
  )
}
