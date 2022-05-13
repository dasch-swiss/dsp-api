/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.listsmessages

import com.typesafe.config.ConfigFactory
import org.knora.webapi.CoreSpec
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCreatePayloadADM.ListChildNodeCreatePayloadADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListsErrorMessagesADM._
import dsp.valueObjects
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.sharedtestdata.SharedListsTestDataADM
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import spray.json._

import java.util.UUID

object ListsMessagesADMSpec {
  val config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * This spec is used to test 'ListAdminMessages'.
 */
class ListsMessagesADMSpec extends CoreSpec(ListsMessagesADMSpec.config) with ListADMJsonProtocol {
  val exampleListIri = "http://rdfh.ch/lists/00FF/abcd"

  "Conversion from case class to JSON and back" should {
    "work for a 'ListRootNodeInfoADM'" in {

      val listInfo = ListRootNodeInfoADM(
        id = "http://rdfh.ch/lists/73d0ec0302",
        projectIri = "http://rdfh.ch/projects/00FF",
        labels = StringLiteralSequenceV2(
          Vector(
            StringLiteralV2("Title", Some("en")),
            StringLiteralV2("Titel", Some("de")),
            StringLiteralV2("Titre", Some("fr"))
          )
        ),
        comments = StringLiteralSequenceV2(
          Vector(StringLiteralV2("Hierarchisches Stichwortverzeichnis / Signatur der Bilder", Some("de")))
        )
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
        labels = StringLiteralSequenceV2(Vector(StringLiteralV2("Sommer"))),
        comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2]),
        position = 0,
        hasRootNode = "http://rdfh.ch/lists/00FF/d19af9ab"
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
        labels = StringLiteralSequenceV2(Vector(StringLiteralV2("Sommer"))),
        comments = Some(StringLiteralSequenceV2(Vector.empty[StringLiteralV2])),
        children = Seq.empty[ListChildNodeADM],
        position = 0,
        hasRootNode = "http://rdfh.ch/lists/00FF/d19af9ab"
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

    "throw 'BadRequestException' if invalid position given in payload of `createChildNodeRequest`" in {
      val caught = intercept[BadRequestException](
        ListChildNodeCreateRequestADM(
          createChildNodeRequest = ListChildNodeCreatePayloadADM(
            parentNodeIri = ListIRI.make(exampleListIri).fold(e => throw e.head, v => v),
            projectIri = ProjectIRI.make(SharedTestDataADM.IMAGES_PROJECT_IRI).fold(e => throw e.head, v => v),
            position = Some(Position.make(-3).fold(e => throw e.head, v => v)),
            labels = Labels
              .make(Seq(StringLiteralV2(value = "New child node", language = Some("en"))))
              .fold(e => throw e.head, v => v),
            comments = Some(
              Comments
                .make(Seq(StringLiteralV2(value = "New child comment", language = Some("en"))))
                .fold(e => throw e.head, v => v)
            )
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === INVALID_POSITION)
    }

    "throw 'BadRequestException' for `ChangeNodePositionApiRequestADM` when no parent node iri is given" in {
      val payload =
        s"""
           |{
           |    "parentNodeIri": "",
           |    "position": 1
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[ChangeNodePositionApiRequestADM]

      thrown.getMessage should equal("IRI of parent node is missing.")
    }

    "throw 'BadRequestException' for `ChangeNodePositionApiRequestADM` when parent node IRI is invalid" in {
      val invalid_parentIri = "invalid-iri"
      val payload =
        s"""
           |{
           |    "parentNodeIri": "$invalid_parentIri",
           |    "position": 1
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[ChangeNodePositionApiRequestADM]

      thrown.getMessage should equal(s"Invalid IRI is given: $invalid_parentIri.")
    }

    "throw 'BadRequestException' for `ChangeNodePositionApiRequestADM` when position is invalid" in {
      val payload =
        s"""
           |{
           |    "parentNodeIri": "http://rdfh.ch/lists/0001/notUsedList01",
           |    "position": -2
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[ChangeNodePositionApiRequestADM]

      thrown.getMessage should equal(INVALID_POSITION)
    }
  }
}
