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

package org.knora.webapi.messages.v2.responder.listmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder.{KnoraRequestV2, KnoraResponseV2}
import spray.json._


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait for messages that can be sent to `HierarchicalListsResponderV2`.
  */
sealed trait ListsResponderRequestV2 extends KnoraRequestV2


/**
  * Requests a list of all lists or the lists inside a project.
  *
  * @param projectIri  the IRI of the project.
  * @param userProfile the profile of the user making the request.
  */
case class ListsGetRequestV2(projectIri: Option[IRI] = None,
                             userProfile: UserProfileV1) extends ListsResponderRequestV2

/**
  * Requests a list. A successful response will be a [[ListExtendedGetResponseV2]]
  *
  * @param iri         the IRI of the list.
  * @param userProfile the profile of the user making the request.
  */
case class ListExtendedGetRequestV2(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV2

/**
  * Request basic information about a list. A successful response will be a [[ListInfoGetResponseV2]]
  *
  * @param iri         the IRI of the list node.
  * @param userProfile the profile of the user making the request.
  */
case class ListInfoGetRequestV2(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV2

/**
  * Request basic information about a list node. A successful response will be a [[ListNodeInfoGetResponseV2]]
  *
  * @param iri         the IRI of the list node.
  * @param userProfile the profile of the user making the request.
  */
case class ListNodeInfoGetRequestV2(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV2

/**
  * Requests a list. A successful response will be a [[HListGetResponseV2]]
  *
  * @param iri         the IRI of the list.
  * @param userProfile the profile of the user making the request.
  */
case class ListGetRequestV2(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV2

/**
  * Requests a list. A successful response will be a [[HListGetResponseV2]].
  *
  * @param iri         the IRI of the list.
  * @param userProfile the profile of the user making the request.
  */
case class HListGetRequestV2(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV2

/**
  * Requests a selection (flat list). A successful response will be a [[SelectionGetResponseV2]].
  *
  * @param iri         the IRI of the list.
  * @param userProfile the profile of the user making the request.
  */
case class SelectionGetRequestV2(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV2

/**
  * Requests the path from the root node of a list to a particular node. A successful response will be
  * a [[NodePathGetResponseV2]].
  *
  * @param iri         the IRI of the node.
  * @param userProfile the profile of the user making the request.
  */
case class NodePathGetRequestV2(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV2


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Responses

case class ListsGetResponseV2(lists: Seq[ListInfoV2]) extends KnoraResponseV2 with ListV2JsonProtocol {

    def toJsValue = listsGetResponseV2Format.write(this)

    def toXML: String = ???

    def toJsonLDWithValueObject(settings: SettingsImpl): String = toJsValue.toString
}

/**
  * An abstract class extended by `HListGetResponseV2` and `SelectionGetResponseV2`.
  */
sealed abstract class ListGetResponseV2 extends KnoraResponseV2

/**
  * Provides a information about the list and the list itself.
  *
  * @param info  the basic information about a list.
  * @param nodes the whole list.
  */
case class ListExtendedGetResponseV2(info: ListInfoV2, nodes: Seq[ListNodeV2]) extends ListGetResponseV2 with ListV2JsonProtocol {

    def toJsValue = listExtendedGetResponseV2Format.write(this)

    def toXML: String = ???

    def toJsonLDWithValueObject(settings: SettingsImpl): String = toJsValue.toString
}

/**
  * Provides a hierarchical list representing a "hlist" in the old SALSAH.
  *
  * @param hlist the list requested.
  */
case class HListGetResponseV2(hlist: Seq[ListNodeV2]) extends ListGetResponseV2 with ListV2JsonProtocol {

    def toJsValue = hlistGetResponseV2Format.write(this)

    def toXML: String = ???

    def toJsonLDWithValueObject(settings: SettingsImpl): String = toJsValue.toString
}

/**
  * Provides a hierarchical list representing a "selection" in the old SALSAH.
  *
  * @param selection the list requested.
  */
case class SelectionGetResponseV2(selection: Seq[ListNodeV2]) extends ListGetResponseV2 with ListV2JsonProtocol {

    def toJsValue = selectionGetResponseV2Format.write(this)

    def toXML: String = ???

    def toJsonLDWithValueObject(settings: SettingsImpl): String = toJsValue.toString
}

/**
  * Provides basic information about a list.
  *
  * @param id         the IRI of the list.
  * @param projectIri the IRI of the project this list belongs to (optional).
  * @param labels     the labels of the list in all available languages.
  * @param comments   the comments of the list in all available languages.
  */
case class LisInfoGetResponseV2(id: IRI, projectIri: Option[IRI], labels: Seq[StringWithOptionalLang], comments: Seq[StringWithOptionalLang]) extends KnoraResponseV2 with ListV2JsonProtocol {

    def toJsValue: JsValue = listNodeInfoGetResponseV2Format.write(this)

    def toXML: String = ???

    def toJsonLDWithValueObject(settings: SettingsImpl): String = toJsValue.toString
}

/**
  * Provides basic information about a list node.
  *
  * @param id       the IRI of the list node.
  * @param labels   the labels of the list in all available languages.
  * @param comments the comments of the list in all available languages.
  */
case class ListNodeInfoGetResponseV2(id: IRI, labels: Seq[StringWithOptionalLang], comments: Seq[StringWithOptionalLang]) extends KnoraResponseV2 with ListV2JsonProtocol {

    def toJsValue: JsValue = listNodeInfoGetResponseV2Format.write(this)

    def toXML: String = ???

    def toJsonLDWithValueObject(settings: SettingsImpl): String = toJsValue.toString
}

/**
  * Responds to a [[NodePathGetRequestV2]] by providing the path to a particular hierarchical list node.
  *
  * @param nodelist a list of the nodes composing the path from the list's root node up to and including the specified node.
  */
case class NodePathGetResponseV2(nodelist: Seq[NodePathElementV2]) extends ListGetResponseV2 with ListV2JsonProtocol {

    def toJsValue = nodePathGetResponseV2Format.write(this)

    def toXML: String = ???

    def toJsonLDWithValueObject(settings: SettingsImpl): String = toJsValue.toString
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents a hierarchical list node in Knora API V2 format.
  *
  * @param id       the IRI of the list node.
  * @param name     the name of the list node.
  * @param label    the label of the list node.
  * @param children the list node's child nodes.
  * @param level    the depth of the node in the tree.
  * @param position the position of the node among its siblings.
  */
case class ListNodeV2(id: IRI, name: Option[String], label: Option[String], children: Seq[ListNodeV2], level: Int, position: Int)

/**
  * Represents a node on a hierarchical list path.
  *
  * @param id    the IRI of the list node.
  * @param name  the name of the list node.
  * @param label the label of the list node.
  */
case class NodePathElementV2(id: IRI, name: Option[String], label: Option[String])

/**
  * Represents information about a list. This information is stored in the list's root node.
  *
  * @param id         the IRI of the list.
  * @param projectIri the IRI of the project this list belongs to (optional).
  * @param labels     the labels of the list in all available languages.
  * @param comments   the comments attached to the list in all available languages.
  */
case class ListInfoV2(id: IRI, projectIri: Option[IRI], labels: Seq[StringWithOptionalLang], comments: Seq[StringWithOptionalLang])

/**
  * Represents information about a list node.
  *
  * @param id       the IRI of the list node.
  * @param labels   the labels of the list in all available languages.
  * @param comments the comments attached to the list in all available languages.
  */
case class ListNodeInfoV2(id: IRI, labels: Seq[StringWithOptionalLang], comments: Seq[StringWithOptionalLang])

/**
  * Represents a list, including the basic information and the whole tree.
  *
  * @param info  the basic list information.
  * @param nodes the complete list tree.
  */
case class ListV2(info: ListInfoV2, nodes: Seq[ListNodeV2])

/**
  * An enumeration whose values correspond to the types of hierarchical list objects that [[org.knora.webapi.responders.v2.ListsResponderV2]] actor can
  * produce: "hlists" | "selections".
  */
object PathType extends Enumeration {
    val HList, Selection = Value
}

/**
  * Represents a string with an optional language tag.
  *
  * @param value the string value.
  * @param lang  the optional language tag.
  */
case class StringWithOptionalLang(value: String, lang: Option[String])

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API V2 JSON providing data about lists.
  */
trait ListV2JsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit object HierarchicalListV2JsonFormat extends JsonFormat[ListNodeV2] {
        /**
          * Recursively converts a [[ListNodeV2]] to a [[JsValue]].
          *
          * @param tree a [[ListNodeV2]].
          * @return a [[JsValue]].
          */
        def write(tree: ListNodeV2): JsValue = {
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
        def read(value: JsValue): ListNodeV2 = {

            /*
            value.asJsObject.getFields("id", "name", "children", "level", "position") match {
                case Seq(JsString(id), JsString(name), JsString(label), Seq(children), JsNumber(level), JsNumber(position)) =>
                    ListNodeV2(
                        id = id,
                        name = Some(name),
                        label = Some(label),
                        children = Seq.empty[ListNodeV2],
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

            val children = Seq.empty[ListNodeV2]

            ListNodeV2(
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

    implicit val hlistGetResponseV2Format: RootJsonFormat[HListGetResponseV2] = jsonFormat(HListGetResponseV2, "hlist")
    implicit val selectionGetResponseV2Format: RootJsonFormat[SelectionGetResponseV2] = jsonFormat(SelectionGetResponseV2, "selection")
    implicit val nodePathElementV2Format: JsonFormat[NodePathElementV2] = jsonFormat(NodePathElementV2, "id", "name", "label")
    implicit val nodePathGetResponseV2Format: RootJsonFormat[NodePathGetResponseV2] = jsonFormat(NodePathGetResponseV2, "nodelist")
    implicit val stringWithLangV2Format: JsonFormat[StringWithOptionalLang] = jsonFormat2(StringWithOptionalLang)
    implicit val listInfoV2Format: JsonFormat[ListInfoV2] = jsonFormat4(ListInfoV2)
    implicit val listsGetResponseV2Format: RootJsonFormat[ListsGetResponseV2] = jsonFormat(ListsGetResponseV2, "lists")
    implicit val listExtendedGetResponseV2Format: RootJsonFormat[ListExtendedGetResponseV2] = jsonFormat(ListExtendedGetResponseV2, "info", "nodes")
    implicit val listNodeInfoGetResponseV2Format: RootJsonFormat[ListNodeInfoGetResponseV2] = jsonFormat(ListNodeInfoGetResponseV2, "id", "labels", "comments")
}
