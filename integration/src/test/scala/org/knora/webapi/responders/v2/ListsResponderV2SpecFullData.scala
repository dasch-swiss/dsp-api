/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.knora.webapi.messages.admin.responder.listsmessages
import org.knora.webapi.messages.admin.responder.listsmessages.ListADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.v2.responder.listsmessages.ListGetResponseV2
import org.knora.webapi.messages.v2.responder.listsmessages.NodeGetResponseV2
import org.knora.webapi.sharedtestdata.SharedListsTestDataADM

class ListsResponderV2SpecFullData {

  val treeList = ListGetResponseV2(
    list = ListADM(
      listinfo = SharedListsTestDataADM.treeListInfo.sorted,
      children = SharedListsTestDataADM.treeListChildNodes.map(_.sorted),
    ),
    userLang = "de",
    fallbackLang = "en",
  )

  val treeNode = NodeGetResponseV2(
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
  )

}
