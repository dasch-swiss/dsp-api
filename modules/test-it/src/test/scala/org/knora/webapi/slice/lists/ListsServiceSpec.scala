/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.lists

import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.admin.responder.listsmessages
import org.knora.webapi.messages.admin.responder.listsmessages.ListADM
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.listsmessages.ListGetResponseV2
import org.knora.webapi.messages.v2.responder.listsmessages.NodeGetResponseV2
import org.knora.webapi.sharedtestdata.SharedListsTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.lists.domain.ListsService

object ListsServiceSpec extends E2EZSpec {

  private val listsService = ZIO.serviceWithZIO[ListsService]

  override lazy val rdfDataObjects: List[RdfDataObject] = List(anythingRdfData)

  override val e2eSpec = suite("The ListsService")(
    test("return a list") {
      for {
        actual <- listsService(_.getList(ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList"), anythingUser2))
      } yield assertTrue(
        actual == ListGetResponseV2(
          ListADM(
            SharedListsTestDataADM.treeListInfo.sorted,
            SharedListsTestDataADM.treeListChildNodes.map(_.sorted),
          ),
          userLang = "de",
          fallbackLang = "en",
        ),
      )
    },
    test("return a node") {
      for {
        actual <- listsService(_.getNode(ListIri.unsafeFrom("http://rdfh.ch/lists/0001/treeList11"), anythingUser2))
      } yield assertTrue(
        actual == NodeGetResponseV2(
          listsmessages.ListChildNodeInfoADM(
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
    },
  )
}
