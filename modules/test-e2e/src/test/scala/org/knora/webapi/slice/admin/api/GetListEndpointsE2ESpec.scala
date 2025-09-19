/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.api

import sttp.client4.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.admin.responder.listsmessages.*
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.sharedtestdata.SharedListsTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient

object GetListEndpointsE2ESpec extends E2EZSpec {

  override val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/project_data/images-demo-data.ttl",
      name = "http://www.knora.org/data/00FF/images",
    ),
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )
  val treeListIri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList")

  override val e2eSpec = suite("The admin lists route (/admin/lists)")(
    test("return all lists") {
      TestApiClient
        .getJson[ListsGetResponseADM](uri"/admin/lists", rootUser)
        .flatMap(_.assert200)
        .map(response => assertTrue(response.lists.size == 9))
    },
    test("return all lists belonging to a project") {
      TestApiClient
        .getJson[ListsGetResponseADM](uri"/admin/lists".withParam("projectIri", imagesProjectIri.value), rootUser)
        .flatMap(_.assert200)
        .map(response => assertTrue(response.lists.size == 4))
    },
    test("return basic list information (w/o children)") {
      TestApiClient
        .getJson[RootNodeInfoGetResponseADM](uri"/admin/lists/infos/$treeListIri", rootUser)
        .flatMap(_.assert200)
        .map(info => assertTrue(info.listinfo.sorted == SharedListsTestDataADM.treeListInfo.sorted))
    },
    test("return basic list information (w/o children) for new merged GET route") {
      TestApiClient
        .getJson[RootNodeInfoGetResponseADM](uri"/admin/lists/$treeListIri/info", rootUser)
        .flatMap(_.assert200)
        .map(info => assertTrue(info.listinfo.sorted == SharedListsTestDataADM.treeListInfo.sorted))
    },
    test("return a complete list") {
      TestApiClient
        .getJson[ListGetResponseADM](uri"/admin/lists/$treeListIri", rootUser)
        .flatMap(_.assert200)
        .map(_.list)
        .map { list =>
          assertTrue(
            list.listinfo.sorted == SharedListsTestDataADM.treeListInfo.sorted,
            list.children.map(_.sorted) == SharedListsTestDataADM.treeListChildNodes.map(_.sorted),
          )
        }
    },
    test("return node info w/o children") {
      val treeListNode01Iri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList01")
      TestApiClient
        .getJson[ChildNodeInfoGetResponseADM](uri"/admin/lists/nodes/$treeListNode01Iri", rootUser)
        .flatMap(_.assert200)
        .map(info => assertTrue(info.nodeinfo == SharedListsTestDataADM.treeListNode01Info))
    },
    test("return a complete node with children") {
      val treeListNode03Iri = ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList03")
      TestApiClient
        .getJson[ListNodeGetResponseADM](uri"/admin/lists/$treeListNode03Iri", rootUser)
        .flatMap(_.assert200)
        .map(_.node)
        .map(node =>
          assertTrue(
            node.nodeinfo.id == "http://rdfh.ch/lists/0001/treeList03",
            node.nodeinfo.name.contains("Tree list node 03"),
            node.children.size == 2,
          ),
        )
    },
  )
}
