/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

package org.knora.webapi.messages.v1.responder.listmessages

import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import spray.json._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait for messages that can be sent to `HierarchicalListsResponderV1`.
  */
sealed trait ListsResponderRequestV1 extends KnoraRequestV1 {
    def iri: IRI

    def userProfile: UserProfileV1
}

/**
  * Requests a list. A successful response will be a [[HListGetResponseV1]].
  *
  * @param iri the IRI of the list.
  * @param userProfile the profile of the user making the request.
  */
case class HListGetRequestV1(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV1

/**
  * Requests a selection (flat list). A successful response will be a [[SelectionGetResponseV1]].
  *
  * @param iri the IRI of the list.
  * @param userProfile the profile of the user making the request.
  */
case class SelectionGetRequestV1(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV1

/**
  * Requests the path from the root node of a list to a particular node. A successful response will be
  * a [[NodePathGetResponseV1]].
  *
  * @param iri the IRI of the node.
  * @param userProfile the profile of the user making the request.
  */
case class NodePathGetRequestV1(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV1

/**
  * An abstract class extended by `HListGetResponseV1` and `SelectionGetResponseV1`.
  */
sealed abstract class ListsGetResponseV1 extends KnoraResponseV1 {
    /**
      * Information about the user that made the request.
      *
      * @return a [[UserDataV1]].
      */
    def userdata: UserDataV1
}

/**
  * Provides a hierarchical list representing a "hlist" in the old SALSAH.
  *
  * @param hlist the list requested.
  * @param userdata information about the user that made the request.
  */
case class HListGetResponseV1(hlist: Seq[HierarchicalListV1], userdata: UserDataV1) extends ListsGetResponseV1 {
    def toJsValue = HierarchicalListV1JsonProtocol.hlistGetResponseV1Format.write(this)
}

/**
  * Provides a hierarchical list representing a "selection" in the old SALSAH.
  *
  * @param selection the list requested.
  * @param userdata information about the user that made the request.
  */
case class SelectionGetResponseV1(selection: Seq[HierarchicalListV1], userdata: UserDataV1) extends ListsGetResponseV1 {
    def toJsValue = HierarchicalListV1JsonProtocol.selectionGetResponseV1Format.write(this)
}

/**
  * Responds to a [[NodePathGetRequestV1]] by providing the path to a particular hierarchical list node.
  *
  * @param nodelist a list of the nodes composing the path from the list's root node up to and including the specified node.
  * @param userdata information about the user that made the request.
  */
case class NodePathGetResponseV1(nodelist: Seq[NodePathElementV1], userdata: UserDataV1) extends ListsGetResponseV1 {
    def toJsValue = HierarchicalListV1JsonProtocol.nodePathGetResponseV1Format.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents a hierarchical list node in Knora API v1 format.
  *
  * @param id the IRI of the list node.
  * @param name the name of the list node.
  * @param label the label of the list node.
  * @param children the list node's child nodes.
  * @param level the depth of the node in the tree.
  * @param position the position of the node among its siblings.
  */
case class HierarchicalListV1(id: IRI, name: Option[String], label: Option[String], children: Seq[HierarchicalListV1], level: Int, position: Int)

/**
  * Represents a node on a hierarchical list path.
  *
  * @param id the IRI of the list node.
  * @param name the name of the list node.
  * @param label the label of the list node.
  */
case class NodePathElementV1(id: IRI, name: Option[String], label: Option[String])

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about lists.
  */
object HierarchicalListV1JsonProtocol extends DefaultJsonProtocol with NullOptions {

    implicit object HierarchicalListV1JsonFormat extends JsonFormat[HierarchicalListV1] {
        /**
          * Recursively converts a [[HierarchicalListV1]] to a [[JsValue]].
          *
          * @param tree a [[HierarchicalListV1]].
          * @return a [[JsValue]].
          */
        def write(tree: HierarchicalListV1): JsValue = {
            // Does the node have children?
            val childrenOption = if (tree.children.nonEmpty) {
                // Yes: recursively convert them to JSON.
                Some("children" -> JsArray(tree.children.map(write).toVector))
            } else {
                // No: don't include a "children" array in the output.
                None
            }

            val fields = Map(
                "id" -> tree.id.toJson,
                "name" -> tree.name.toJson,
                "label" -> tree.label.toJson,
                "level" -> tree.level.toJson
            ) ++ childrenOption

            JsObject(fields)
        }

        /**
          * Not implemented.
          */
        def read(jsonVal: JsValue) = ???
    }

    implicit val hlistGetResponseV1Format: RootJsonFormat[HListGetResponseV1] = jsonFormat2(HListGetResponseV1)
    implicit val selectionGetResponseV1Format: RootJsonFormat[SelectionGetResponseV1] = jsonFormat2(SelectionGetResponseV1)
    implicit val nodePathElementV1Format: JsonFormat[NodePathElementV1] = jsonFormat3(NodePathElementV1)
    implicit val nodePathGetResponseV1Format: RootJsonFormat[NodePathGetResponseV1] = jsonFormat2(NodePathGetResponseV1)
}
