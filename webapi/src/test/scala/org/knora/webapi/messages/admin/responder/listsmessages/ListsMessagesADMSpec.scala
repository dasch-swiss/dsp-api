/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.scalatest.{Matchers, WordSpecLike}
import spray.json._

/**
  * This spec is used to test 'ListAdminMessages'.
  */
class ListsMessagesADMSpec extends WordSpecLike with Matchers with ListADMJsonProtocol {

    "Conversion from case class to JSON and back" should {

        "work for a 'ListInfoADM'" in {

            val listInfo: ListInfoADM = ListInfoADM (
                id = "http://data.knora.org/lists/73d0ec0302",
                projectIri = "http://rdfh.ch/projects/00FF",
                labels = Seq(StringLiteralV2("Title", Some("en")), StringLiteralV2("Titel", Some("de")), StringLiteralV2("Titre", Some("fr"))),
                comments = Seq(StringLiteralV2("Hierarchisches Stichwortverzeichnis / Signatur der Bilder", Some("de")))
            )

            val json = listInfo.toJson.compactPrint

            // json should be ("")

            val converted: ListInfoADM = json.parseJson.convertTo[ListInfoADM]

            converted should be(listInfo)
        }

        "work for a 'ListNodeInfoADM'" in {

            val listNodeInfo: ListNodeInfoADM = ListNodeInfoADM (
                id = "http://rdfh.ch/lists/00FF/526f26ed04",
                name = Some("sommer"),
                labels = Seq(StringLiteralV2("Sommer")),
                comments = Seq.empty[StringLiteralV2],
                position = Some(0)
            )

            val json = listNodeInfo.toJson.compactPrint

            // json should be ("")

            val converted: ListNodeInfoADM = json.parseJson.convertTo[ListNodeInfoADM]

            converted should be(listNodeInfo)
        }

        "work for a 'ListNodeADM'" in {

            val listNode: ListNodeADM = ListNodeADM(
                id = "http://rdfh.ch/lists/00FF/526f26ed04",
                name = Some("sommer"),
                labels = Seq(StringLiteralV2("Sommer")),
                comments = Seq.empty[StringLiteralV2],
                children = Seq.empty[ListNodeADM],
                position = Some(0)
            )

            val json = listNode.toJson.compactPrint

            // json should be ("")

            val converted: ListNodeADM = json.parseJson.convertTo[ListNodeADM]

            converted should be(listNode)
        }

        "work for a 'ListADM'" in {

            val listInfo: ListInfoADM = ListInfoADM (
                id = "http://data.knora.org/lists/73d0ec0302",
                projectIri = "http://rdfh.ch/projects/00FF",
                labels = Seq(StringLiteralV2("Title", Some("en")), StringLiteralV2("Titel", Some("de")), StringLiteralV2("Titre", Some("fr"))),
                comments = Seq(StringLiteralV2("Hierarchisches Stichwortverzeichnis / Signatur der Bilder", Some("de")))
            )

            val listNode: ListNodeADM = ListNodeADM(
                id = "http://rdfh.ch/lists/00FF/526f26ed04",
                name = Some("sommer"),
                labels = Seq(StringLiteralV2("Sommer")),
                comments = Seq.empty[StringLiteralV2],
                children = Seq.empty[ListNodeADM],
                position = Some(0)
            )

            val json = ListADM(listInfo, Seq(listNode)).toJson.compactPrint

            // json should be ("")

            val converted: ListADM = json.parseJson.convertTo[ListADM]

            converted.listinfo should be(listInfo)
            converted.children.head should be(listNode)
        }

    }
}