/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.admin.responder.listsmessages

import java.util.UUID
import com.typesafe.config.ConfigFactory
import org.knora.webapi.CoreSpec
import org.knora.webapi.exceptions.{BadRequestException, ForbiddenException}
import org.knora.webapi.messages.admin.responder.listsmessages.ListsMessagesUtilADM._
import org.knora.webapi.messages.admin.responder.listsmessages.NodeCreatePayloadADM.{
  ChildNodeCreatePayloadADM,
  RootNodeCreatePayloadADM
}
import org.knora.webapi.messages.admin.responder.valueObjects.{Comments, Labels, Name, Position}
import org.knora.webapi.messages.store.triplestoremessages.{StringLiteralSequenceV2, StringLiteralV2}
import org.knora.webapi.sharedtestdata.SharedTestDataV1.IMAGES_PROJECT_IRI
import org.knora.webapi.sharedtestdata.{SharedListsTestDataADM, SharedTestDataADM}
import spray.json._

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
        comments = Some(StringLiteralSequenceV2(Vector.empty[StringLiteralV2])),
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

    "throw 'BadRequestException' for `CreateListApiRequestADM` when project IRI is empty" in {

      val payload =
        s"""
           |{
           |    "projectIri": "",
           |    "labels": [{ "value": "Neue Liste", "language": "de"}],
           |    "comments": []
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[CreateListApiRequestADM]

      thrown.getMessage should equal(PROJECT_IRI_MISSING_ERROR)

    }

    "throw 'BadRequestException' for `CreateListApiRequestADM` when project IRI is invalid" in {

      val payload =
        s"""
           |{
           |    "projectIri": "not an IRI",
           |    "labels": [{ "value": "Neue Liste", "language": "de"}],
           |    "comments": []
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[CreateListApiRequestADM]

      thrown.getMessage should equal(PROJECT_IRI_INVALID_ERROR)
    }

    "throw 'BadRequestException' for `CreateListApiRequestADM` when labels is empty" in {

      val payload =
        s"""
           |{
           |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
           |    "labels": [],
           |    "comments": []
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[CreateListApiRequestADM]

      thrown.getMessage should equal(LABEL_MISSING_ERROR)
    }

    "throw a 'BadRequestException' for `CreateListApiRequestADM` when an invalid list IRI is given" in {

      val payload =
        s"""
           |{
           |    "id": "invalid-list-IRI",
           |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
           |    "labels": [{ "value": "New List", "language": "en"}],
           |    "comments": []
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[CreateListApiRequestADM]

      thrown.getMessage should equal("Invalid list IRI")
    }

    "throw 'BadRequestException' for `CreateListApiRequestADM` when value of a label is missing" in {

      val payload =
        s"""
           |{
           |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
           |    "labels": [{ "value": "Neuer List Node", "language": "de"}, { "value": "", "language": "en"}],
           |    "comments": []
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[CreateListApiRequestADM]

      thrown.getMessage should equal("String value is missing.")
    }

    "throw 'BadRequestException' for `CreateListApiRequestADM` when value of a comment is missing" in {

      val payload =
        s"""
           |{
           |    "parentNodeIri": "$exampleListIri",
           |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
           |    "labels": [{ "value": "Neuer List Node", "language": "de"}],
           |    "comments": [{ "value": "", "language": "de"}]
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[CreateListApiRequestADM]

      thrown.getMessage should equal("String value is missing.")
    }

    "throw 'BadRequestException' for `ChangeNodeInfoApiRequestADM` when list IRI is empty" in {

      val payload =
        s"""
           |{
           |    "listIri": "",
           |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
           |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
           |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[ChangeNodeInfoApiRequestADM]

      thrown.getMessage should equal("IRI of list item is missing.")
    }

    "throw 'BadRequestException' for `ChangeNodeInfoApiRequestADM` when list IRI is invalid" in {
      val invalidIri = "notvalidIRI"
      val payload =
        s"""
           |{
           |    "listIri": "$invalidIri",
           |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
           |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
           |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[ChangeNodeInfoApiRequestADM]

      thrown.getMessage should equal(s"Invalid IRI is given: $invalidIri.")
    }

    "throw 'BadRequestException' for `ChangeNodeInfoApiRequestADM` when project IRI is empty" in {

      val payload =
        s"""
           |{
           |    "listIri": "$exampleListIri",
           |    "projectIri": "",
           |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}]
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[ChangeNodeInfoApiRequestADM]

      thrown.getMessage should equal(PROJECT_IRI_MISSING_ERROR)
    }

    "throw 'BadRequestException' for `ChangeNodeInfoApiRequestADM` when project IRI is invalid" in {

      val payload =
        s"""
           |{
           |    "listIri": "$exampleListIri",
           |    "projectIri": "notvalidIRI",
           |    "name": "a new name"
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[ChangeNodeInfoApiRequestADM]

      thrown.getMessage should equal(PROJECT_IRI_INVALID_ERROR)
    }

    "throw 'BadRequestException' for `ChangeNodeInfoApiRequestADM` when labels are empty" in {

      val payload =
        s"""
           |{
           |    "listIri": "$exampleListIri",
           |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
           |    "labels": [],
           |    "comments": []
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[ChangeNodeInfoApiRequestADM]

      thrown.getMessage should equal(UPDATE_REQUEST_EMPTY_LABEL_ERROR)
    }

    "throw 'BadRequestException' for `ChangeNodeInfoApiRequestADM` when position is invalid" in {

      val payload =
        s"""
           |{
           |    "listIri": "$exampleListIri",
           |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
           |    "position": -2
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[ChangeNodeInfoApiRequestADM]

      thrown.getMessage should equal(INVALID_POSITION)
    }

    "throw 'BadRequestException' if invalid position given in payload of `createChildNodeRequest`" in {
      val caught = intercept[BadRequestException](
        ListChildNodeCreateRequestADM(
          createChildNodeRequest = ChildNodeCreatePayloadADM(
            parentNodeIri = Some(exampleListIri),
            projectIri = SharedTestDataADM.IMAGES_PROJECT_IRI,
            position = Some(Position.create(-3).fold(e => throw e, v => v)),
            labels = Labels
              .create(Seq(StringLiteralV2(value = "New child node", language = Some("en"))))
              .fold(e => throw e, v => v),
            comments = Some(
              Comments
                .create(Seq(StringLiteralV2(value = "New child comment", language = Some("en"))))
                .fold(e => throw e, v => v)
            )
          ),
          featureFactoryConfig = defaultFeatureFactoryConfig,
          requestingUser = SharedTestDataADM.imagesUser01,
          apiRequestID = UUID.randomUUID()
        )
      )
      assert(caught.getMessage === INVALID_POSITION)
    }

    "throw 'BadRequestException' for `createChildNodeRequest` when no parent node iri is given" in {

      val payload =
        s"""
           |{
           |    "parentNodeIri": "",
           |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
           |    "labels": [{ "value": "Neuer List Node", "language": "de"}],
           |    "comments": []
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[CreateNodeApiRequestADM]

      thrown.getMessage should equal(LIST_NODE_IRI_INVALID_ERROR)

    }

    "throw 'BadRequestException' for `createChildNodeRequest` when parent node iri is invalid" in {

      val payload =
        s"""
           |{
           |    "parentNodeIri": "notvalidIRI",
           |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
           |    "labels": [{ "value": "Neuer List Node", "language": "de"}],
           |    "comments": []
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[CreateNodeApiRequestADM]

      thrown.getMessage should equal(LIST_NODE_IRI_INVALID_ERROR)

    }

    "throw 'BadRequestException' for `createChildNodeRequest` when project iri is empty" in {

      val payload =
        s"""
           |{
           |    "parentNodeIri": "$exampleListIri",
           |    "projectIri": "",
           |    "labels": [{ "value": "Neuer List Node", "language": "de"}],
           |    "comments": []
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[CreateNodeApiRequestADM]

      thrown.getMessage should equal(PROJECT_IRI_MISSING_ERROR)

    }

    "throw 'BadRequestException' for `createChildNodeRequest` when project iri is invalid" in {

      val payload =
        s"""
           |{
           |    "parentNodeIri": "$exampleListIri",
           |    "projectIri": "notvalidIRI",
           |    "labels": [{ "value": "Neuer List Node", "language": "de"}],
           |    "comments": []
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[CreateNodeApiRequestADM]

      thrown.getMessage should equal(PROJECT_IRI_INVALID_ERROR)

    }

    "throw 'BadRequestException' for `createChildNodeRequest` when labels are empty" in {

      val payload =
        s"""
           |{
           |    "parentNodeIri": "$exampleListIri",
           |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
           |    "labels": [],
           |    "comments": []
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[CreateNodeApiRequestADM]

      thrown.getMessage should equal(LABEL_MISSING_ERROR)

    }

    "throw 'BadRequestException' for `createChildNodeRequest` when custom iri of the child node is invalid" in {

      val payload =
        s"""
           |{   "id": "invalid-list-node-IRI",
           |    "parentNodeIri": "$exampleListIri",
           |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
           |    "labels": [{ "value": "Neuer List Node", "language": "de"}],
           |    "comments": []
           |}
                """.stripMargin

      val thrown = the[BadRequestException] thrownBy payload.parseJson.convertTo[CreateNodeApiRequestADM]

      thrown.getMessage should equal("Invalid list node IRI")

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
