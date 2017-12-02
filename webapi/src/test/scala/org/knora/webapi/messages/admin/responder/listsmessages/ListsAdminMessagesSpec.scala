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

package org.knora.webapi.messages.admin.responder.listadminmessages

import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.store.triplestoremessages.StringV2
import org.scalatest.{Matchers, WordSpecLike}
import spray.json._

/**
  * This spec is used to test 'ListAdminMessages'.
  */
class ListsAdminMessagesSpec extends WordSpecLike with Matchers with ListAdminJsonProtocol {

    "Conversion from case class to JSON and back" should {

        "work for a 'ListInfo'" in {

            val listInfo: ListInfo = ListInfo (
                id = "http://data.knora.org/lists/73d0ec0302",
                projectIri = Some("http://rdfh.ch/projects/00FF"),
                labels = Seq(StringV2("Title", Some("en")), StringV2("Titel", Some("de")), StringV2("Titre", Some("fr"))),
                comments = Seq(StringV2("Hierarchisches Stichwortverzeichnis / Signatur der Bilder", Some("de")))
            )

            val json = listInfo.toJson.compactPrint

            // json should be ("")

            val converted: ListInfo = json.parseJson.convertTo[ListInfo]

            converted should be(listInfo)
        }

        "work for a 'ListNodeInfo'" in {

            val listNodeInfo: ListNodeInfo = ListNodeInfo (
                id = "http://rdfh.ch/lists/00FF/526f26ed04",
                name = Some("sommer"),
                labels = Seq(StringV2("Sommer")),
                comments = Seq.empty[StringV2],
                position = Some(0)
            )

            val json = listNodeInfo.toJson.compactPrint

            // json should be ("")

            val converted: ListNodeInfo = json.parseJson.convertTo[ListNodeInfo]

            converted should be(listNodeInfo)
        }

        "work for a 'ListNode'" in {

            val listNode: ListNode = ListNode(
                id = "http://rdfh.ch/lists/00FF/526f26ed04",
                name = Some("sommer"),
                labels = Seq(StringV2("Sommer")),
                comments = Seq.empty[StringV2],
                children = Seq.empty[ListNode],
                position = Some(0)
            )

            val json = listNode.toJson.compactPrint

            // json should be ("")

            val converted: ListNode = json.parseJson.convertTo[ListNode]

            converted should be(listNode)
        }

        "work for a 'FullList'" in {

            val listInfo: ListInfo = ListInfo (
                id = "http://data.knora.org/lists/73d0ec0302",
                projectIri = Some("http://rdfh.ch/projects/00FF"),
                labels = Seq(StringV2("Title", Some("en")), StringV2("Titel", Some("de")), StringV2("Titre", Some("fr"))),
                comments = Seq(StringV2("Hierarchisches Stichwortverzeichnis / Signatur der Bilder", Some("de")))
            )

            val listNode: ListNode = ListNode(
                id = "http://rdfh.ch/lists/00FF/526f26ed04",
                name = Some("sommer"),
                labels = Seq(StringV2("Sommer")),
                comments = Seq.empty[StringV2],
                children = Seq.empty[ListNode],
                position = Some(0)
            )

            val json = FullList(listInfo, Seq(listNode)).toJson.compactPrint

            // json should be ("")

            val converted: FullList = json.parseJson.convertTo[FullList]

            converted.listinfo should be(listInfo)
            converted.children.head should be(listNode)
        }

    }
}