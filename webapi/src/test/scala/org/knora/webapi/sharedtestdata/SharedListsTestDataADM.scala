package org.knora.webapi.sharedtestdata

import org.knora.webapi.messages.admin.responder.listsmessages.ListChildNodeADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListChildNodeInfoADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListRootNodeInfoADM
import org.knora.webapi.messages.admin.responder.listsmessages.NodePathElementADM
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2

object SharedListsTestDataADM {

  val otherTreeListInfo: ListRootNodeInfoADM = ListRootNodeInfoADM(
    id = "http://rdfh.ch/lists/0001/otherTreeList",
    projectIri = "http://rdfh.ch/projects/0001",
    name = None,
    labels = StringLiteralSequenceV2(Vector(StringLiteralV2("Tree list root", Some("en")))),
    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
  )

  val summerNodeInfo: ListChildNodeInfoADM = ListChildNodeInfoADM(
    id = "http://rdfh.ch/lists/00FF/526f26ed04",
    name = Some("sommer"),
    labels = StringLiteralSequenceV2(Vector(StringLiteralV2("Sommer"))),
    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2]),
    position = 0,
    hasRootNode = "http://rdfh.ch/lists/00FF/d19af9ab"
  )

  val seasonListNodes: Seq[ListChildNodeADM] = Seq(
    ListChildNodeADM(
      id = "http://rdfh.ch/lists/00FF/526f26ed04",
      name = Some("sommer"),
      labels = StringLiteralSequenceV2(Vector(StringLiteralV2("Sommer"))),
      comments = Some(StringLiteralSequenceV2(Vector.empty[StringLiteralV2])),
      children = Seq.empty[ListChildNodeADM],
      position = 0,
      hasRootNode = "http://rdfh.ch/lists/00FF/d19af9ab"
    ),
    ListChildNodeADM(
      id = "http://rdfh.ch/lists/00FF/eda2792605",
      name = Some("winter"),
      labels = StringLiteralSequenceV2(Vector(StringLiteralV2("Winter"))),
      comments = Some(StringLiteralSequenceV2(Vector.empty[StringLiteralV2])),
      children = Seq.empty[ListChildNodeADM],
      position = 1,
      hasRootNode = "http://rdfh.ch/lists/00FF/d19af9ab"
    )
  )

  val nodePath: Seq[NodePathElementADM] = Seq(
    NodePathElementADM(
      id = "http://rdfh.ch/lists/00FF/691eee1cbe",
      name = Some("4KUN"),
      labels = StringLiteralSequenceV2(Vector(StringLiteralV2("KUNST"))),
      comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
    ),
    NodePathElementADM(
      id = "http://rdfh.ch/lists/00FF/2ebd2706c1",
      name = Some("7"),
      labels = StringLiteralSequenceV2(Vector(StringLiteralV2("FILM UND FOTO"))),
      comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
    ),
    NodePathElementADM(
      id = "http://rdfh.ch/lists/00FF/c7f07a3fc1",
      name = Some("1"),
      labels = StringLiteralSequenceV2(Vector(StringLiteralV2("Heidi Film"))),
      comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
    )
  )

  val treeListInfo: ListRootNodeInfoADM = ListRootNodeInfoADM(
    id = "http://rdfh.ch/lists/0001/treeList",
    projectIri = "http://rdfh.ch/projects/0001",
    name = Some("treelistroot"),
    labels = StringLiteralSequenceV2(
      Vector(
        StringLiteralV2(value = "Tree list root", language = Some("en")),
        StringLiteralV2(value = "Listenwurzel", language = Some("de"))
      )
    ),
    comments = StringLiteralSequenceV2(Vector(StringLiteralV2(value = "Anything Tree List", language = Some("en"))))
  )

  val treeListNode01Info: ListChildNodeInfoADM = ListChildNodeInfoADM(
    id = "http://rdfh.ch/lists/0001/treeList01",
    name = Some("Tree list node 01"),
    labels = StringLiteralSequenceV2(Vector(StringLiteralV2(value = "Tree list node 01", language = Some("en")))),
    comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2]),
    position = 0,
    hasRootNode = "http://rdfh.ch/lists/0001/treeList"
  )
  val treeListChildNodes: Seq[ListChildNodeADM] = Seq(
    ListChildNodeADM(
      id = "http://rdfh.ch/lists/0001/treeList01",
      name = Some("Tree list node 01"),
      labels = StringLiteralSequenceV2(Vector(StringLiteralV2(value = "Tree list node 01", language = Some("en")))),
      comments = Some(StringLiteralSequenceV2(Vector.empty[StringLiteralV2])),
      children = Seq.empty[ListChildNodeADM],
      position = 0,
      hasRootNode = "http://rdfh.ch/lists/0001/treeList"
    ),
    ListChildNodeADM(
      id = "http://rdfh.ch/lists/0001/treeList02",
      name = Some("Tree list node 02"),
      labels = StringLiteralSequenceV2(
        Vector(
          StringLiteralV2(value = "Baumlistenknoten 02", language = Some("de")),
          StringLiteralV2(value = "Tree list node 02", language = Some("en"))
        )
      ),
      comments = Some(StringLiteralSequenceV2(Vector.empty[StringLiteralV2])),
      children = Seq.empty[ListChildNodeADM],
      position = 1,
      hasRootNode = "http://rdfh.ch/lists/0001/treeList"
    ),
    ListChildNodeADM(
      id = "http://rdfh.ch/lists/0001/treeList03",
      name = Some("Tree list node 03"),
      labels = StringLiteralSequenceV2(Vector(StringLiteralV2(value = "Tree list node 03", language = Some("en")))),
      comments = Some(StringLiteralSequenceV2(Vector.empty[StringLiteralV2])),
      children = Seq(
        ListChildNodeADM(
          id = "http://rdfh.ch/lists/0001/treeList10",
          name = Some("Tree list node 10"),
          labels = StringLiteralSequenceV2(Vector(StringLiteralV2(value = "Tree list node 10", language = Some("en")))),
          comments = Some(StringLiteralSequenceV2(Vector.empty[StringLiteralV2])),
          children = Seq.empty[ListChildNodeADM],
          position = 0,
          hasRootNode = "http://rdfh.ch/lists/0001/treeList"
        ),
        ListChildNodeADM(
          id = "http://rdfh.ch/lists/0001/treeList11",
          name = Some("Tree list node 11"),
          labels = StringLiteralSequenceV2(Vector(StringLiteralV2(value = "Tree list node 11", language = Some("en")))),
          comments = Some(StringLiteralSequenceV2(Vector.empty[StringLiteralV2])),
          children = Seq.empty[ListChildNodeADM],
          position = 1,
          hasRootNode = "http://rdfh.ch/lists/0001/treeList"
        )
      ),
      position = 2,
      hasRootNode = "http://rdfh.ch/lists/0001/treeList"
    )
  )

}
