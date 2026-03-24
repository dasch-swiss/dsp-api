/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.listsmessages

import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assertTrue

import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2

object ListsMessagesADMSpec extends ZIOSpecDefault {

  private val rootIri = "http://rdfh.ch/lists/0001/root"
  private val projIri = "http://rdfh.ch/projects/0001"

  private def makeRoot: ListRootNodeInfoADM =
    ListRootNodeInfoADM(
      id = rootIri,
      projectIri = projIri,
      labels = StringLiteralSequenceV2.empty,
      comments = StringLiteralSequenceV2.empty,
    )

  private def makeChild(id: String, children: Seq[ListChildNodeADM] = Seq.empty): ListChildNodeADM =
    ListChildNodeADM(
      id = id,
      name = None,
      labels = StringLiteralSequenceV2.empty,
      comments = StringLiteralSequenceV2.empty,
      position = 0,
      hasRootNode = rootIri,
      children = children,
    )

  def spec: Spec[Any, Any] =
    suite("ListsMessagesADM")(allDescendantsSuite, listADMSuite, nodeADMSuite)

  private val allDescendantsSuite = suite("ListChildNodeADM.allDescendants")(
    test("returns empty when node has no children") {
      val node = makeChild("http://rdfh.ch/lists/0001/child")
      assertTrue(node.allDescendants == Seq.empty)
    },
    test("returns direct children") {
      val c1   = makeChild("http://rdfh.ch/lists/0001/c1")
      val c2   = makeChild("http://rdfh.ch/lists/0001/c2")
      val node = makeChild("http://rdfh.ch/lists/0001/parent", Seq(c1, c2))
      assertTrue(node.allDescendants.map(_.id) == Seq("http://rdfh.ch/lists/0001/c1", "http://rdfh.ch/lists/0001/c2"))
    },
    test("returns grandchildren recursively") {
      val grandchild = makeChild("http://rdfh.ch/lists/0001/gc")
      val child      = makeChild("http://rdfh.ch/lists/0001/c", Seq(grandchild))
      val node       = makeChild("http://rdfh.ch/lists/0001/parent", Seq(child))
      assertTrue(
        node.allDescendants.map(_.id) == Seq("http://rdfh.ch/lists/0001/c", "http://rdfh.ch/lists/0001/gc"),
      )
    },
  )

  private val listADMSuite = suite("ListADM.withChildren")(
    test("returns only root when no children (REQ-1.2, REQ-1.5)") {
      val list = ListADM(listinfo = makeRoot, children = Seq.empty)
      assertTrue(list.withChildren == Seq(makeRoot))
    },
    test("returns root and immediate children for a flat list") {
      val c1   = makeChild("http://rdfh.ch/lists/0001/c1")
      val c2   = makeChild("http://rdfh.ch/lists/0001/c2")
      val list = ListADM(listinfo = makeRoot, children = Seq(c1, c2))
      assertTrue(
        list.withChildren.map(_.id) == Seq(rootIri, "http://rdfh.ch/lists/0001/c1", "http://rdfh.ch/lists/0001/c2"),
      )
    },
    test("includes grandchildren at depth 2 (REQ-1.1)") {
      val grandchild = makeChild("http://rdfh.ch/lists/0001/gc")
      val child      = makeChild("http://rdfh.ch/lists/0001/c", Seq(grandchild))
      val list       = ListADM(listinfo = makeRoot, children = Seq(child))
      assertTrue(
        list.withChildren.map(_.id) == Seq(
          rootIri,
          "http://rdfh.ch/lists/0001/c",
          "http://rdfh.ch/lists/0001/gc",
        ),
      )
    },
    test("includes all nodes at depth 3+") {
      val greatGrandchild = makeChild("http://rdfh.ch/lists/0001/ggc")
      val grandchild      = makeChild("http://rdfh.ch/lists/0001/gc", Seq(greatGrandchild))
      val child           = makeChild("http://rdfh.ch/lists/0001/c", Seq(grandchild))
      val list            = ListADM(listinfo = makeRoot, children = Seq(child))
      assertTrue(
        list.withChildren.map(_.id) == Seq(
          rootIri,
          "http://rdfh.ch/lists/0001/c",
          "http://rdfh.ch/lists/0001/gc",
          "http://rdfh.ch/lists/0001/ggc",
        ),
      )
    },
  )

  private def makeNodeInfo(id: String): ListChildNodeInfoADM =
    ListChildNodeInfoADM(
      id = id,
      name = None,
      labels = StringLiteralSequenceV2.empty,
      comments = StringLiteralSequenceV2.empty,
      position = 0,
      hasRootNode = rootIri,
    )

  private val nodeADMSuite = suite("NodeADM.withChildren")(
    test("returns only nodeinfo when no children") {
      val node = NodeADM(nodeinfo = makeNodeInfo("http://rdfh.ch/lists/0001/c"), children = Seq.empty)
      assertTrue(node.withChildren.map(_.id) == Seq("http://rdfh.ch/lists/0001/c"))
    },
    test("includes all descendants recursively") {
      val grandchild = makeChild("http://rdfh.ch/lists/0001/gc")
      val child      = makeChild("http://rdfh.ch/lists/0001/c", Seq(grandchild))
      val node       = NodeADM(nodeinfo = makeNodeInfo("http://rdfh.ch/lists/0001/parent"), children = Seq(child))
      assertTrue(
        node.withChildren.map(_.id) == Seq(
          "http://rdfh.ch/lists/0001/parent",
          "http://rdfh.ch/lists/0001/c",
          "http://rdfh.ch/lists/0001/gc",
        ),
      )
    },
  )
}
