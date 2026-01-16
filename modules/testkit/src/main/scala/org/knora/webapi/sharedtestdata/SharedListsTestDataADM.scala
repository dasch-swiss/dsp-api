/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.sharedtestdata

import org.knora.webapi.messages.admin.responder.listsmessages.ListChildNodeADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListChildNodeInfoADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListRootNodeInfoADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.slice.common.domain.LanguageCode.DE
import org.knora.webapi.slice.common.domain.LanguageCode.EN

object SharedListsTestDataADM {
  val otherTreeListInfo: ListRootNodeInfoADM = ListRootNodeInfoADM(
    id = "http://rdfh.ch/lists/0001/otherTreeList",
    projectIri = "http://rdfh.ch/projects/0001",
    name = None,
    labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Tree list root", EN))),
    comments = StringLiteralSequenceV2.empty,
  )

  val summerNodeInfo: ListChildNodeInfoADM = ListChildNodeInfoADM(
    id = "http://rdfh.ch/lists/00FF/526f26ed04",
    name = Some("sommer"),
    labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Sommer"))),
    comments = StringLiteralSequenceV2.empty,
    position = 0,
    hasRootNode = "http://rdfh.ch/lists/00FF/d19af9ab",
  )

  val seasonListNodes: Seq[ListChildNodeADM] = Seq(
    ListChildNodeADM(
      id = "http://rdfh.ch/lists/00FF/526f26ed04",
      name = Some("sommer"),
      labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Sommer"))),
      comments = StringLiteralSequenceV2.empty,
      children = Seq.empty[ListChildNodeADM],
      position = 0,
      hasRootNode = "http://rdfh.ch/lists/00FF/d19af9ab",
    ),
    ListChildNodeADM(
      id = "http://rdfh.ch/lists/00FF/eda2792605",
      name = Some("winter"),
      labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Winter"))),
      comments = StringLiteralSequenceV2.empty,
      children = Seq.empty[ListChildNodeADM],
      position = 1,
      hasRootNode = "http://rdfh.ch/lists/00FF/d19af9ab",
    ),
  )

  val treeListInfo: ListRootNodeInfoADM = ListRootNodeInfoADM(
    id = "http://rdfh.ch/lists/0001/treeList",
    projectIri = "http://rdfh.ch/projects/0001",
    name = Some("treelistroot"),
    labels = StringLiteralSequenceV2(
      Vector(
        StringLiteralV2.from("Tree list root", EN),
        StringLiteralV2.from("Listenwurzel", DE),
      ),
    ),
    comments = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Anything Tree List", EN))),
  )

  val treeListNode01Info: ListChildNodeInfoADM = ListChildNodeInfoADM(
    id = "http://rdfh.ch/lists/0001/treeList01",
    name = Some("Tree list node 01"),
    labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Tree list node 01", EN))),
    comments = StringLiteralSequenceV2.empty,
    position = 0,
    hasRootNode = "http://rdfh.ch/lists/0001/treeList",
  )
  val treeListChildNodes: Seq[ListChildNodeADM] = Seq(
    ListChildNodeADM(
      id = "http://rdfh.ch/lists/0001/treeList01",
      name = Some("Tree list node 01"),
      labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Tree list node 01", EN))),
      comments = StringLiteralSequenceV2.empty,
      children = Seq.empty[ListChildNodeADM],
      position = 0,
      hasRootNode = "http://rdfh.ch/lists/0001/treeList",
    ),
    ListChildNodeADM(
      id = "http://rdfh.ch/lists/0001/treeList02",
      name = Some("Tree list node 02"),
      labels = StringLiteralSequenceV2(
        Vector(
          StringLiteralV2.from("Baumlistenknoten 02", DE),
          StringLiteralV2.from("Tree list node 02", EN),
        ),
      ),
      comments = StringLiteralSequenceV2.empty,
      children = Seq.empty[ListChildNodeADM],
      position = 1,
      hasRootNode = "http://rdfh.ch/lists/0001/treeList",
    ),
    ListChildNodeADM(
      id = "http://rdfh.ch/lists/0001/treeList03",
      name = Some("Tree list node 03"),
      labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Tree list node 03", EN))),
      comments = StringLiteralSequenceV2.empty,
      children = Seq(
        ListChildNodeADM(
          id = "http://rdfh.ch/lists/0001/treeList10",
          name = Some("Tree list node 10"),
          labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Tree list node 10", EN))),
          comments = StringLiteralSequenceV2.empty,
          children = Seq.empty[ListChildNodeADM],
          position = 0,
          hasRootNode = "http://rdfh.ch/lists/0001/treeList",
        ),
        ListChildNodeADM(
          id = "http://rdfh.ch/lists/0001/treeList11",
          name = Some("Tree list node 11"),
          labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Tree list node 11", EN))),
          comments = StringLiteralSequenceV2.empty,
          children = Seq.empty[ListChildNodeADM],
          position = 1,
          hasRootNode = "http://rdfh.ch/lists/0001/treeList",
        ),
      ),
      position = 2,
      hasRootNode = "http://rdfh.ch/lists/0001/treeList",
    ),
  )

}
