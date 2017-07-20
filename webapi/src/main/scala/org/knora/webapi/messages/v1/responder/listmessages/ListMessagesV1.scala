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

package org.knora.webapi.messages.v1.responder.listmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.responder.{KnoraRequestV1, KnoraResponseV1}
import spray.json._


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait for messages that can be sent to `HierarchicalListsResponderV1`.
  */
sealed trait ListsResponderRequestV1 extends KnoraRequestV1


/**
  * Requests a list of all lists or the lists inside a project.
  *
  * @param projectIri  the IRI of the project.
  * @param userProfile the profile of the user making the request.
  */
case class ListsGetRequestV1(projectIri: Option[IRI] = None,
                             userProfile: UserProfileV1) extends ListsResponderRequestV1

/**
  * Requests a list. A successful response will be a [[ListExtendedGetResponseV1]]
  *
  * @param iri         the IRI of the list.
  * @param userProfile the profile of the user making the request.
  */
case class ListExtendedGetRequestV1(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV1

/**
  * Requests a list. A successful response will be a [[HListGetResponseV1]]
  *
  * @param iri         the IRI of the list.
  * @param userProfile the profile of the user making the request.
  */
case class ListGetRequestV1(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV1

/**
  * Requests a list. A successful response will be a [[HListGetResponseV1]].
  *
  * @param iri         the IRI of the list.
  * @param userProfile the profile of the user making the request.
  */
case class HListGetRequestV1(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV1

/**
  * Requests a selection (flat list). A successful response will be a [[SelectionGetResponseV1]].
  *
  * @param iri         the IRI of the list.
  * @param userProfile the profile of the user making the request.
  */
case class SelectionGetRequestV1(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV1

/**
  * Requests the path from the root node of a list to a particular node. A successful response will be
  * a [[NodePathGetResponseV1]].
  *
  * @param iri         the IRI of the node.
  * @param userProfile the profile of the user making the request.
  */
case class NodePathGetRequestV1(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV1


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Responses

case class ListsGetResponseV1(lists: Seq[ListInfoV1]) extends KnoraResponseV1 with ListV1JsonProtocol {
    def toJsValue = listsGetResponseV1Format.write(this)
}

/**
  * An abstract class extended by `HListGetResponseV1` and `SelectionGetResponseV1`.
  */
sealed abstract class ListGetResponseV1 extends KnoraResponseV1

/**
  * Provides a information about the list and the list itself.
  *
  * @param info the basic information about a list.
  * @param nodes the whole list.
  */
case class ListExtendedGetResponseV1(info: ListInfoV1, nodes: Seq[ListNodeV1]) extends ListGetResponseV1 with ListV1JsonProtocol {
    def toJsValue = listExtendedGetResponseV1Format.write(this)
}

/**
  * Provides a hierarchical list representing a "hlist" in the old SALSAH.
  *
  * @param hlist the list requested.
  */
case class HListGetResponseV1(hlist: Seq[ListNodeV1]) extends ListGetResponseV1 with ListV1JsonProtocol {
    def toJsValue = hlistGetResponseV1Format.write(this)
}

/**
  * Provides a hierarchical list representing a "selection" in the old SALSAH.
  *
  * @param selection the list requested.
  */
case class SelectionGetResponseV1(selection: Seq[ListNodeV1]) extends ListGetResponseV1 with ListV1JsonProtocol {
    def toJsValue = selectionGetResponseV1Format.write(this)
}

/**
  * Responds to a [[NodePathGetRequestV1]] by providing the path to a particular hierarchical list node.
  *
  * @param nodelist a list of the nodes composing the path from the list's root node up to and including the specified node.
  */
case class NodePathGetResponseV1(nodelist: Seq[NodePathElementV1]) extends ListGetResponseV1 with ListV1JsonProtocol {
    def toJsValue = nodePathGetResponseV1Format.write(this)
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents a hierarchical list node in Knora API v1 format.
  *
  * @param id       the IRI of the list node.
  * @param name     the name of the list node.
  * @param label    the label of the list node.
  * @param children the list node's child nodes.
  * @param level    the depth of the node in the tree.
  * @param position the position of the node among its siblings.
  */
case class ListNodeV1(id: IRI, name: Option[String], label: Option[String], children: Seq[ListNodeV1], level: Int, position: Int)

/**
  * Represents a node on a hierarchical list path.
  *
  * @param id    the IRI of the list node.
  * @param name  the name of the list node.
  * @param label the label of the list node.
  */
case class NodePathElementV1(id: IRI, name: Option[String], label: Option[String])

/**
  * Represents information about a list. This information is stored in the list's root node.
  *
  * @param id         the IRI of the list.
  * @param projectIri the IRI of the project this list belongs to (optional).
  * @param name       the name of the list (optional).
  * @param comment    the comment attached to the list (optional).
  * @param label      the label of the list (optional).
  */
case class ListInfoV1(id: IRI, projectIri: Option[IRI], name: Option[String], comment: Option[String], label: Option[String])

/**
  * Represents a list, including the basic information and the whole tree.
  *
  * @param info the basic list information.
  * @param nodes the complete list tree.
  */
case class ListV1(info: ListInfoV1, nodes: Seq[ListNodeV1])

/**
  * An enumeration whose values correspond to the types of hierarchical list objects that [[org.knora.webapi.responders.v1.ListsResponderV1]] actor can
  * produce: "hlists" | "selections".
  */
object PathType extends Enumeration {
    val HList, Selection = Value
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API v1 JSON providing data about lists.
  */
trait ListV1JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit object HierarchicalListV1JsonFormat extends JsonFormat[ListNodeV1] {
        /**
          * Recursively converts a [[ListNodeV1]] to a [[JsValue]].
          *
          * @param tree a [[ListNodeV1]].
          * @return a [[JsValue]].
          */
        def write(tree: ListNodeV1): JsValue = {
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
                "level" -> tree.level.toJson,
                "position" -> tree.position.toJson
            ) ++ childrenOption

            JsObject(fields)
        }

        /**
          * Not implemented.
          */
        def read(value: JsValue): ListNodeV1 = {

            /*
            value.asJsObject.getFields("id", "name", "children", "level", "position") match {
                case Seq(JsString(id), JsString(name), JsString(label), Seq(children), JsNumber(level), JsNumber(position)) =>
                    ListNodeV1(
                        id = id,
                        name = Some(name),
                        label = Some(label),
                        children = Seq.empty[ListNodeV1],
                        level = level.toInt,
                        position = position.toInt
                    )
                case _ => throw new DeserializationException("Color expected")
            }
            */



            /*
            val fields = value.asJsObject.fields

            val name: Option[String] = {
                val name = fields("name").convertTo[String]
                if (name.nonEmpty) {
                    Some(name)
                } else {
                    None
                }
            }

            val label: Option[String] = {
                val label = fields("label").convertTo[String]
                if (label.nonEmpty) {
                    Some(label)
                } else {
                    None
                }
            }

            val children = Seq.empty[ListNodeV1]

            ListNodeV1(
                id = fields("id").convertTo[IRI],
                name = name,
                label = label,
                children = children,
                level = fields("level").convertTo[Int],
                position = fields("position").convertTo[Int]
            )
            */

            ???
        }
    }

    implicit val hlistGetResponseV1Format: RootJsonFormat[HListGetResponseV1] = jsonFormat(HListGetResponseV1, "hlist")
    implicit val selectionGetResponseV1Format: RootJsonFormat[SelectionGetResponseV1] = jsonFormat(SelectionGetResponseV1, "selection")
    implicit val nodePathElementV1Format: JsonFormat[NodePathElementV1] = jsonFormat(NodePathElementV1, "id", "name", "label")
    implicit val nodePathGetResponseV1Format: RootJsonFormat[NodePathGetResponseV1] = jsonFormat(NodePathGetResponseV1, "nodelist")
    implicit val listInfoV1Format: JsonFormat[ListInfoV1] = jsonFormat5(ListInfoV1)
    implicit val listsGetResponseV1Format: RootJsonFormat[ListsGetResponseV1] = jsonFormat(ListsGetResponseV1, "lists")
    implicit val listExtendedGetResponseV1Format: RootJsonFormat[ListExtendedGetResponseV1] = jsonFormat(ListExtendedGetResponseV1, "info", "nodes")
}
