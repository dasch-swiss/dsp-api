/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.lists

import org.apache.pekko
import org.apache.pekko.testkit.ImplicitSender
import zio.*

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.admin.responder.listsmessages
import org.knora.webapi.messages.admin.responder.listsmessages.ListADM
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.listsmessages.ListGetResponseV2
import org.knora.webapi.messages.v2.responder.listsmessages.NodeGetResponseV2
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedListsTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.lists.domain.ListsService

class ListsServiceSpec extends CoreSpec with ImplicitSender {

  private val listsService = ZIO.serviceWith[ListsService]

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

  "The ListsService" should {
    "return a list" in {
      val actual = UnsafeZioRun.runOrThrow(
        listsService(
          _.getList(ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList"), SharedTestDataADM.anythingUser2),
        ),
      )
      assert(
        actual == ListGetResponseV2(
          list = ListADM(
            listinfo = SharedListsTestDataADM.treeListInfo.sorted,
            children = SharedListsTestDataADM.treeListChildNodes.map(_.sorted),
          ),
          userLang = "de",
          fallbackLang = "en",
        ),
      )
    }

    "return a node" in {
      val actual = UnsafeZioRun.runOrThrow(
        listsService(
          _.getNode(ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList11"), SharedTestDataADM.anythingUser2),
        ),
      )
      assert(
        actual == NodeGetResponseV2(
          node = listsmessages.ListChildNodeInfoADM(
            name = Some("Tree list node 11"),
            id = "http://rdfh.ch/lists/0001/treeList11",
            labels = StringLiteralSequenceV2(
              stringLiterals = Vector(
                StringLiteralV2.from(
                  value = "Tree list node 11",
                  language = Some("en"),
                ),
              ),
            ),
            position = 1,
            hasRootNode = "http://rdfh.ch/lists/0001/treeList",
            comments = StringLiteralSequenceV2.empty,
          ),
          userLang = "de",
          fallbackLang = "en",
        ),
      )
    }
  }
}
