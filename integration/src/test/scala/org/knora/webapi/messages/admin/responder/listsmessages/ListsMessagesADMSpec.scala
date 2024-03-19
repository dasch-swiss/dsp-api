/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.listsmessages

import spray.json.*

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.sharedtestdata.SharedListsTestDataADM

/**
 * This spec is used to test 'ListAdminMessages'.
 */
class ListsMessagesADMSpec extends CoreSpec with ListADMJsonProtocol {
  val exampleListIri = "http://rdfh.ch/lists/00FF/abcd"

  "Conversion from case class to JSON and back" should {
    "work for a 'ListRootNodeInfoADM'" in {

      val listInfo = ListRootNodeInfoADM(
        id = "http://rdfh.ch/lists/73d0ec0302",
        projectIri = "http://rdfh.ch/projects/00FF",
        labels = StringLiteralSequenceV2(
          Vector(
            StringLiteralV2.from("Title", Some("en")),
            StringLiteralV2.from("Titel", Some("de")),
            StringLiteralV2.from("Titre", Some("fr")),
          ),
        ),
        comments = StringLiteralSequenceV2(
          Vector(StringLiteralV2.from("Hierarchisches Stichwortverzeichnis / Signatur der Bilder", Some("de"))),
        ),
      )

      val json = listInfo.toJson.compactPrint

      // json should be ("")

      val converted = json.parseJson.convertTo[ListRootNodeInfoADM]

      converted should be(listInfo)
    }

    "work for a 'ListChildNodeInfoADM'" in {
      val listNodeInfo = ListChildNodeInfoADM(
        id = "http://rdfh.ch/lists/00FF/526f26ed04",
        name = Some("sommer"),
        labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Sommer", None))),
        comments = StringLiteralSequenceV2.empty,
        position = 0,
        hasRootNode = "http://rdfh.ch/lists/00FF/d19af9ab",
      )

      val json = listNodeInfo.toJson.compactPrint

      // json should be ("")

      val converted: ListNodeInfoADM = json.parseJson.convertTo[ListChildNodeInfoADM]

      converted should be(listNodeInfo)
    }

    "work for a 'ListChildNodeADM'" in {
      val listNode: ListNodeADM = ListChildNodeADM(
        id = "http://rdfh.ch/lists/00FF/526f26ed04",
        name = Some("sommer"),
        labels = StringLiteralSequenceV2(Vector(StringLiteralV2.from("Sommer", None))),
        comments = StringLiteralSequenceV2.empty,
        children = Seq.empty[ListChildNodeADM],
        position = 0,
        hasRootNode = "http://rdfh.ch/lists/00FF/d19af9ab",
      )

      val json = listNode.toJson.compactPrint

      // json should be ("")

      val converted: ListNodeADM = json.parseJson.convertTo[ListChildNodeADM]

      converted should be(listNode)
    }

    "work for a 'ListADM'" in {
      val listInfo = SharedListsTestDataADM.treeListInfo

      val children = SharedListsTestDataADM.treeListChildNodes

      val json = ListADM(listInfo, children).toJson.compactPrint

      // json should be ("")

      val converted: ListADM = json.parseJson.convertTo[ListADM]

      converted.listinfo should be(listInfo)
      converted.children should be(children)
    }

    "work for a 'NodeADM'" in {
      val nodeInfo = SharedListsTestDataADM.summerNodeInfo

      val children = Seq.empty[ListChildNodeADM]

      val json = NodeADM(nodeInfo, children).toJson.compactPrint

      val converted: NodeADM = json.parseJson.convertTo[NodeADM]

      converted.nodeinfo should be(nodeInfo)
      converted.children should be(children)
    }
  }
}
