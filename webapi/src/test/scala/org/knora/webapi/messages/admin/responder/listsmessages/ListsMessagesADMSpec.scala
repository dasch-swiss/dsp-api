/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

import org.knora.webapi.messages.store.triplestoremessages.{StringLiteralSequenceV2, StringLiteralV2}
import org.knora.webapi.responders.admin.ListsResponderADM._
import org.knora.webapi.{BadRequestException, SharedListsTestDataADM, SharedTestDataADM}
import org.scalatest.{Matchers, WordSpecLike}
import spray.json._

/**
  * This spec is used to test 'ListAdminMessages'.
  */
class ListsMessagesADMSpec extends WordSpecLike with Matchers with ListADMJsonProtocol {

    val exampleListIri = "http://rdfh.ch/lists/00FF/abcd"

    "Conversion from case class to JSON and back" should {

        "work for a 'ListRootNodeInfoADM'" in {

            val listInfo = ListRootNodeInfoADM (
                id = "http://rdfh.ch/lists/73d0ec0302",
                projectIri = "http://rdfh.ch/projects/00FF",
                name = None,
                labels = StringLiteralSequenceV2(Vector(StringLiteralV2("Title", Some("en")), StringLiteralV2("Titel", Some("de")), StringLiteralV2("Titre", Some("fr")))),
                comments = StringLiteralSequenceV2(Vector(StringLiteralV2("Hierarchisches Stichwortverzeichnis / Signatur der Bilder", Some("de"))))
            )

            val json = listInfo.toJson.compactPrint

            // json should be ("")

            val converted = json.parseJson.convertTo[ListRootNodeInfoADM]

            converted should be(listInfo)
        }

        "work for a 'ListChildNodeInfoADM'" in {

            val listNodeInfo = ListChildNodeInfoADM (
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
                comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2]),
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

        "throw 'BadRequestException' for `CreateListApiRequestADM` when project IRI is empty" in {

            val payload =
                s"""
                   |{
                   |    "projectIri": "",
                   |    "labels": [{ "value": "Neue Liste", "language": "de"}],
                   |    "comments": []
                   |}
                """.stripMargin

            val thrown = the [BadRequestException] thrownBy payload.parseJson.convertTo[CreateListApiRequestADM]

            thrown.getMessage should equal (PROJECT_IRI_MISSING_ERROR)

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

            thrown.getMessage should equal (PROJECT_IRI_INVALID_ERROR)
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

            thrown.getMessage should equal (LABEL_MISSING_ERROR)
        }

        "throw 'BadRequestException' for `ChangeListInfoApiRequestADM` when list IRI is empty" in {

            val payload =
                s"""
                   |{
                   |    "listIri": "",
                   |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                   |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
                   |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
                   |}
                """.stripMargin

            val thrown = the [BadRequestException] thrownBy payload.parseJson.convertTo[ChangeListInfoApiRequestADM]

            thrown.getMessage should equal (LIST_IRI_MISSING_ERROR)
        }

        "throw 'BadRequestException' for `ChangeListInfoApiRequestADM` when list IRI is invalid" in {

            val payload =
                s"""
                   |{
                   |    "listIri": "notvalidIRI",
                   |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                   |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
                   |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
                   |}
                """.stripMargin

            val thrown = the [BadRequestException] thrownBy payload.parseJson.convertTo[ChangeListInfoApiRequestADM]

            thrown.getMessage should equal (LIST_IRI_INVALID_ERROR)
        }

        "throw 'BadRequestException' for `ChangeListInfoApiRequestADM` when project IRI is empty" in {

            val payload =
                s"""
                   |{
                   |    "listIri": "$exampleListIri",
                   |    "projectIri": "",
                   |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
                   |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
                   |}
                """.stripMargin

            val thrown = the [BadRequestException] thrownBy payload.parseJson.convertTo[ChangeListInfoApiRequestADM]

            thrown.getMessage should equal (PROJECT_IRI_MISSING_ERROR)
        }

        "throw 'BadRequestException' for `ChangeListInfoApiRequestADM` when project IRI is invalid" in {

            val payload =
                s"""
                   |{
                   |    "listIri": "$exampleListIri",
                   |    "projectIri": "notvalidIRI",
                   |    "labels": [{ "value": "Neue geönderte Liste", "language": "de"}, { "value": "Changed list", "language": "en"}],
                   |    "comments": [{ "value": "Neuer Kommentar", "language": "de"}, { "value": "New comment", "language": "en"}]
                   |}
                """.stripMargin

            val thrown = the [BadRequestException] thrownBy payload.parseJson.convertTo[ChangeListInfoApiRequestADM]

            thrown.getMessage should equal (PROJECT_IRI_INVALID_ERROR)
        }

        "throw 'BadRequestException' for `ChangeListInfoApiRequestADM` when labels and comments are empty" in {

            val payload =
                s"""
                   |{
                   |    "listIri": "$exampleListIri",
                   |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                   |    "labels": [],
                   |    "comments": []
                   |}
                """.stripMargin

            val thrown = the [BadRequestException] thrownBy payload.parseJson.convertTo[ChangeListInfoApiRequestADM]

            thrown.getMessage should equal (REQUEST_NOT_CHANGING_DATA_ERROR)

        }

        "throw 'BadRequestException' for `CreateChildNodeApiRequestADM` when list node iri is empty" in {

            val payload =
                s"""
                   |{
                   |    "parentNodeIri": "",
                   |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                   |    "labels": [{ "value": "Neuer List Node", "language": "de"}],
                   |    "comments": []
                   |}
                """.stripMargin

            val thrown = the [BadRequestException] thrownBy payload.parseJson.convertTo[CreateChildNodeApiRequestADM]

            thrown.getMessage should equal (LIST_NODE_IRI_MISSING_ERROR)

        }

        "throw 'BadRequestException' for `CreateChildNodeApiRequestADM` when list node iri is invalid" in {

            val payload =
                s"""
                   |{
                   |    "parentNodeIri": "notvalidIRI",
                   |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                   |    "labels": [{ "value": "Neuer List Node", "language": "de"}],
                   |    "comments": []
                   |}
                """.stripMargin

            val thrown = the [BadRequestException] thrownBy payload.parseJson.convertTo[CreateChildNodeApiRequestADM]

            thrown.getMessage should equal (LIST_NODE_IRI_INVALID_ERROR)

        }

        "throw 'BadRequestException' for `CreateChildNodeApiRequestADM` when project iri is empty" in {

            val payload =
                s"""
                   |{
                   |    "parentNodeIri": "$exampleListIri",
                   |    "projectIri": "",
                   |    "labels": [{ "value": "Neuer List Node", "language": "de"}],
                   |    "comments": []
                   |}
                """.stripMargin

            val thrown = the [BadRequestException] thrownBy payload.parseJson.convertTo[CreateChildNodeApiRequestADM]

            thrown.getMessage should equal (PROJECT_IRI_MISSING_ERROR)

        }

        "throw 'BadRequestException' for `CreateChildNodeApiRequestADM` when project iri is invalid" in {

            val payload =
                s"""
                   |{
                   |    "parentNodeIri": "$exampleListIri",
                   |    "projectIri": "notvalidIRI",
                   |    "labels": [{ "value": "Neuer List Node", "language": "de"}],
                   |    "comments": []
                   |}
                """.stripMargin

            val thrown = the [BadRequestException] thrownBy payload.parseJson.convertTo[CreateChildNodeApiRequestADM]

            thrown.getMessage should equal (PROJECT_IRI_INVALID_ERROR)

        }

        "throw 'BadRequestException' for `CreateChildNodeApiRequestADM` when labels are empty" in {

            val payload =
                s"""
                   |{
                   |    "parentNodeIri": "$exampleListIri",
                   |    "projectIri": "${SharedTestDataADM.IMAGES_PROJECT_IRI}",
                   |    "labels": [],
                   |    "comments": []
                   |}
                """.stripMargin

            val thrown = the [BadRequestException] thrownBy payload.parseJson.convertTo[CreateChildNodeApiRequestADM]

            thrown.getMessage should equal (LABEL_MISSING_ERROR)

        }

    }
}