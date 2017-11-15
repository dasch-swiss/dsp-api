/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.messages.admin.responder.listadminmessages


import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.{KnoraAdminRequest, KnoraAdminResponse}
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.util.jsonld.{JsonLDObject, JsonLDString, JsonLDValue}
import spray.json.{DefaultJsonProtocol, JsArray, JsObject, JsValue, JsonFormat, NullOptions, RootJsonFormat, _}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait for messages that can be sent to `HierarchicalListsResponderV2`.
  */
sealed trait ListsAdminResponderRequest extends KnoraAdminRequest


/**
  * Requests a list of all lists or the lists inside a project. A successful response will be a [[ListsGetAdminResponse]]
  *
  * @param projectIri  the IRI of the project.
  * @param userProfile the profile of the user making the request.
  */
case class ListsGetAdminRequest(projectIri: Option[IRI] = None,
                                userProfile: UserProfileV1) extends ListsAdminResponderRequest

/**
  * Requests a list. A successful response will be a [[ListGetAdminResponse]]
  *
  * @param iri         the IRI of the list.
  * @param userProfile the profile of the user making the request.
  */
case class ListGetAdminRequest(iri: IRI,
                               userProfile: UserProfileV1) extends ListsAdminResponderRequest


/**
  * Request basic information about a list node. A successful response will be a [[ListNodeInfoGetAdminResponse]]
  *
  * @param iri         the IRI of the list node.
  * @param userProfile the profile of the user making the request.
  */
case class ListNodeInfoGetAdminRequest(iri: IRI,
                                       userProfile: UserProfileV1) extends ListsAdminResponderRequest


/**
  * Requests the path from the root node of a list to a particular node. A successful response will be
  * a [[NodePathGetAdminResponse]].
  *
  * @param iri         the IRI of the node.
  * @param userProfile the profile of the user making the request.
  */
case class NodePathGetAdminRequest(iri: IRI,
                                   userProfile: UserProfileV1) extends ListsAdminResponderRequest


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Responses

/**
  * Represents a sequence of list info nodes.
  *
  * @param items a [[ListRootNodeInfo]] sequence.
  */
case class ListsGetAdminResponse(items: Seq[ListNodeInfo]) extends KnoraAdminResponse with ListAdminJsonProtocol {
    def toJsValue = listsGetAdminResponseFormat.write(this)
}

/**
  * Provides completes information about the list. The basic information (rood node) together the complete list
  * hierarchy (child nodes).
  *
  * @param list the whole list.
  */
case class ListGetAdminResponse(list: ListNode) extends KnoraAdminResponse with ListAdminJsonProtocol {

    def toJsValue = listGetAdminResponseFormat.write(this)
}

/**
  * Provides basic information about a list node.
  *
  * @param nodeinfo the basic information about a list node.
  */
case class ListNodeInfoGetAdminResponse(nodeinfo: ListNodeInfo) extends KnoraAdminResponse with ListAdminJsonProtocol {

    def toJsValue: JsValue = listNodeInfoGetAdminResponseFormat.write(this)
}

/**
  * Responds to a [[NodePathGetAdminRequest]] by providing the path to a particular hierarchical list node.
  *
  * @param nodelist a list of the nodes composing the path from the list's root node up to and including the specified node.
  */
case class NodePathGetAdminResponse(nodelist: Seq[ListNode]) extends KnoraAdminResponse with ListAdminJsonProtocol {

    def toJsValue = nodePathGetAdminResponseFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * An abstract class extended by `ListRootNodeInfo` and `ListChildNodeInfo`.
  *
  * @param id       the IRI of the list.
  * @param labels   the labels of the node in all available languages.
  * @param comments the comments attached to the node in all available languages.
  */
sealed abstract class ListNodeInfo(id: IRI, labels: Seq[StringV2], comments: Seq[StringV2]) {
    def sorted: ListNodeInfo
}

/**
  * Represents basic information about a list. This information is stored in the list's root node.
  *
  * @param id         the IRI of the list.
  * @param projectIri the IRI of the project this list belongs to (optional).
  * @param labels     the labels of the list in all available languages.
  * @param comments   the comments attached to the list in all available languages.
  */
case class ListRootNodeInfo(id: IRI, projectIri: Option[IRI], labels: Seq[StringV2], comments: Seq[StringV2]) extends ListNodeInfo(id, labels, comments) {
    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListRootNode]].
      */
    override def sorted: ListRootNodeInfo = {
        ListRootNodeInfo(
            id = id,
            projectIri = projectIri,
            labels = labels.sortBy(_.value),
            comments = comments.sortBy(_.value)
        )
    }
}

/**
  * Represents basic information about a list node. This information which is found in the list's child node.
  *
  * @param id       the IRI of the list.
  * @param name     the name of the list node.
  * @param labels   the labels of the node in all available languages.
  * @param comments the comments attached to the node in all available languages.
  * @param position the position of the node among its siblings (optional).
  */
case class ListChildNodeInfo(id: IRI, name: Option[String], labels: Seq[StringV2], comments: Seq[StringV2], position: Option[Int]) extends ListNodeInfo(id, labels, comments) {
    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListChildNodeInfo]].
      */
    override def sorted: ListChildNodeInfo = {
        ListChildNodeInfo(
            id = id,
            name = name,
            labels = labels.sortBy(_.value),
            comments = comments.sortBy(_.value),
            position = position
        )
    }
}

/**
  * An abstract class extended by `ListRootNode` and `ListChildNode`.
  *
  * @param id       the IRI of the list node.
  * @param labels   the labels of the list in all available languages.
  * @param comments the comments attached to the list in all available languages.
  * @param children the children of this list node.
  */
sealed abstract class ListNode(id: IRI, labels: Seq[StringV2], comments: Seq[StringV2], children: Seq[ListNode]) {
    def sorted: ListNode
}

/**
  * Represents information about a list. This information is found in the list's root node.
  *
  * @param id         the IRI of the list.
  * @param projectIri the IRI of the project this list belongs to (optional).
  * @param labels     the labels of the list in all available languages.
  * @param comments   the comments attached to the list in all available languages.
  */
case class ListRootNode(id: IRI, projectIri: Option[IRI], labels: Seq[StringV2], comments: Seq[StringV2], children: Seq[ListChildNode]) extends ListNode(id, labels, comments, children) {

    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListRootNode]].
      */
    override def sorted: ListRootNode = {
        ListRootNode(
            id = id,
            projectIri = projectIri,
            labels = labels.sortBy(_.value),
            comments = comments.sortBy(_.value),
            children = children.sortBy(_.id).map(_.sorted)
        )
    }
}

/**
  * Represents a hierarchical list node.
  *
  * @param id       the IRI of the list node.
  * @param name     the name of the list node.
  * @param labels   the label(s) of the list node.
  * @param comments the comment(s) attached to the list in a specific language (if language tags are used) .
  * @param children the list node's child nodes.
  * @param position the position of the node among its siblings (optional).
  */
case class ListChildNode(id: IRI, name: Option[String], labels: Seq[StringV2], comments: Seq[StringV2], children: Seq[ListChildNode], position: Option[Int]) extends ListNode(id, labels, comments, children) {

    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListChildNode]].
      */
    override def sorted: ListChildNode = {
        ListChildNode(
            id = id,
            name = name,
            labels = labels.sortBy(_.value),
            comments = comments.sortBy(_.value),
            children = children.sortBy(_.id).map(_.sorted),
            position = position
        )
    }
}

/**
  * Represents a string with an optional language tag.
  *
  * @param value    the string value.
  * @param language the optional language tag.
  */
case class StringV2(value: String, language: Option[String] = None)


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API V2 JSON providing data about lists.
  */
trait ListAdminJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit object StringV2Format extends JsonFormat[StringV2] {
        /**
          * Converts a [[StringV2]] to a [[JsValue]].
          *
          * @param string a [[StringV2]].
          * @return a [[JsValue]].
          */
        def write(string: StringV2): JsValue = {

            if (string.language.isDefined) {
                // have language tag
                JsObject(
                    Map(
                        "value" -> string.value.toJson,
                        "language" -> string.language.toJson
                    )
                )
            } else {
                // no language tag
                string.value.toJson
            }
        }

        /**
          * Converts a [[JsValue]] to a [[StringV2]].
          *
          * @param json a [[JsValue]].
          * @return a [[StringV2]].
          */
        def read(json: JsValue): StringV2 = json match {
            case stringWithLang: JsObject => stringWithLang.getFields("value", "language") match {
                case Seq(JsString(value), JsString(language)) => StringV2(
                    value = value,
                    language = Some(language)
                )
                case _ => throw DeserializationException("JSON object with 'value' and 'language' fields expected.")
            }
            case JsString(value) => StringV2(value, None)
            case _ => throw DeserializationException("Either a JSON object with 'value' and 'language', or plain string expected. ")
        }
    }

    implicit object ListNodeInfoFormat extends JsonFormat[ListNodeInfo] {
        /**
          * Converts a [[ListNodeInfo]] to a [[JsValue]].
          *
          * @param nodeInfo a [[ListNodeInfo]].
          * @return a [[JsValue]].
          */
        def write(nodeInfo: ListNodeInfo): JsValue = {
            // Do we have a Root or Child node?
            nodeInfo match {
                case rootNode: ListRootNodeInfo => {
                    JsObject(
                        "id" -> rootNode.id.toJson,
                        "projectIri" -> rootNode.projectIri.toJson,
                        "labels" -> JsArray(rootNode.labels.map(_.toJson).toVector),
                        "comments" -> JsArray(rootNode.comments.map(_.toJson).toVector)
                    )
                }
                case childNode: ListChildNodeInfo => {
                    JsObject(
                        "id" -> childNode.id.toJson,
                        "name" -> childNode.name.toJson,
                        "labels" -> JsArray(childNode.labels.map(_.toJson).toVector),
                        "comments" -> JsArray(childNode.comments.map(_.toJson).toVector),
                        "position" -> childNode.position.toJson
                    )
                }
                case _ => throw NotImplementedException("Only ListRootNodeInfo or ListChildNodeInfo types are allowed.") // "Only ListRootNodeInfo or ListChildNodeInfo types are allowed.")
            }
        }

        /**
          * Converts a [[JsValue]] to a [[ListNodeInfo]].
          *
          * @param value a [[JsValue]].
          * @return a [[ListNodeInfo]].
          */
        def read(value: JsValue): ListNodeInfo = {

            val fields = value.asJsObject.fields

            if (fields.get("projectIri").isDefined) {
                // root node

                val id = fields.getOrElse("id", throw DeserializationException("The expected field 'id' is missing.")).convertTo[String]
                val projectIri: Option[IRI] = fields.get("projectIri").map(_.convertTo[String])
                val labels = fields.get("labels") match {
                    case Some(JsArray(values)) => values.map(_.convertTo[StringV2])
                    case None => Seq.empty[StringV2]
                    case _ => throw DeserializationException("The expected field 'labels' is in the wrong format.")
                }
                val comments = fields.get("comments") match {
                    case Some(JsArray(values)) => values.map(_.convertTo[StringV2])
                    case None => Seq.empty[StringV2]
                    case _ => throw DeserializationException("The expected field 'comments' is in the wrong format.")
                }

                ListRootNodeInfo(
                    id = id,
                    projectIri = projectIri,
                    labels = labels,
                    comments = comments
                )


            } else {
                // child node

                val id = fields.getOrElse("id", throw DeserializationException("The expected field 'id' is missing.")).convertTo[String]
                val name = fields.get("name").map(_.convertTo[String])
                val labels = fields.get("labels") match {
                    case Some(JsArray(values)) => values.map(_.convertTo[StringV2])
                    case None => Seq.empty[StringV2]
                    case _ => throw DeserializationException("The expected field 'labels' is in the wrong format.")
                }

                val comments = fields.get("comments") match {
                    case Some(JsArray(values)) => values.map(_.convertTo[StringV2])
                    case None => Seq.empty[StringV2]
                    case _ => throw DeserializationException("The expected field 'comments' is in the wrong format.")
                }

                val position = fields.get("position").map(_.convertTo[Int])

                ListChildNodeInfo(
                    id = id,
                    name = name,
                    labels = labels,
                    comments = comments,
                    position = position
                )
            }

        }
    }

    implicit object ListNodeFormat extends JsonFormat[ListNode] {
        /**
          * Converts a [[ListNode]] to a [[JsValue]].
          *
          * @param node a [[ListNode]].
          * @return a [[JsValue]].
          */
        def write(node: ListNode): JsValue = {
            // Do we have a Root of Child node
            node match {
                case rootNode: ListRootNode => {
                    JsObject(
                        "id" -> rootNode.id.toJson,
                        "projectIri" -> rootNode.projectIri.toJson,
                        "labels" -> JsArray(rootNode.labels.map(_.toJson).toVector),
                        "comments" -> JsArray(rootNode.comments.map(_.toJson).toVector),
                        "children" -> JsArray(rootNode.children.map(write).toVector)
                    )
                }
                case childNode: ListChildNode => {
                    JsObject(
                        "id" -> childNode.id.toJson,
                        "name" -> childNode.name.toJson,
                        "labels" -> JsArray(childNode.labels.map(_.toJson).toVector),
                        "comments" -> JsArray(childNode.comments.map(_.toJson).toVector),
                        "children" -> JsArray(childNode.children.map(write).toVector),
                        "position" -> childNode.position.toJson
                    )
                }
                case _ => throw NotImplementedException("Only ListRootNodeInfo or ListChildNodeInfo types are allowed.") // "Only ListRootNodeInfo or ListChildNodeInfo types are allowed.")
            }
        }

        /**
          * Converts a [[JsValue]] to a [[ListNode]].
          *
          * @param value a [[JsValue]].
          * @return a [[ListNode]].
          */
        def read(value: JsValue): ListNode = {

            val fields = value.asJsObject.fields

            if (fields.get("projectIri").isDefined) {
                // root node

                val id = fields.getOrElse("id", throw DeserializationException("The expected field 'id' is missing.")).convertTo[String]
                val projectIri: Option[IRI] = fields.get("projectIri").map(_.convertTo[String])
                val labels = fields.get("labels") match {
                    case Some(JsArray(values)) => values.map(_.convertTo[StringV2])
                    case None => Seq.empty[StringV2]
                    case _ => throw DeserializationException("The expected field 'labels' is in the wrong format.")
                }
                val comments = fields.get("comments") match {
                    case Some(JsArray(values)) => values.map(_.convertTo[StringV2])
                    case None => Seq.empty[StringV2]
                    case _ => throw DeserializationException("The expected field 'comments' is in the wrong format.")
                }

                val children: Seq[ListChildNode] = fields.get("children") match {
                    case Some(JsArray(values)) => values.map(read).map(_.asInstanceOf[ListChildNode])
                    case None => Seq.empty[ListChildNode]
                    case _ => throw DeserializationException("The expected field 'children' is in the wrong format.")
                }

                ListRootNode(
                    id = id,
                    projectIri = projectIri,
                    labels = labels,
                    comments = comments,
                    children = children
                )


            } else {
                // child node

                val id = fields.getOrElse("id", throw DeserializationException("The expected field 'id' is missing.")).convertTo[String]
                val name = fields.get("name").map(_.convertTo[String])
                val labels = fields.get("labels") match {
                    case Some(JsArray(values)) => values.map(_.convertTo[StringV2])
                    case None => Seq.empty[StringV2]
                    case _ => throw DeserializationException("The expected field 'labels' is in the wrong format.")
                }

                val comments = fields.get("comments") match {
                    case Some(JsArray(values)) => values.map(_.convertTo[StringV2])
                    case None => Seq.empty[StringV2]
                    case _ => throw DeserializationException("The expected field 'comments' is in the wrong format.")
                }

                val children: Seq[ListChildNode] = fields.get("children") match {
                    case Some(JsArray(values)) => values.map(read).map(_.asInstanceOf[ListChildNode])
                    case None => Seq.empty[ListChildNode]
                    case _ => throw DeserializationException("The expected field 'children' is in the wrong format.")
                }

                val position = fields.get("position").map(_.convertTo[Int])

                ListChildNode(
                    id = id,
                    name = name,
                    labels = labels,
                    comments = comments,
                    children = children,
                    position = position
                )
            }

        }
    }


    implicit val nodePathGetAdminResponseFormat: RootJsonFormat[NodePathGetAdminResponse] = jsonFormat(NodePathGetAdminResponse, "nodelist")
    implicit val listsGetAdminResponseFormat: RootJsonFormat[ListsGetAdminResponse] = jsonFormat(ListsGetAdminResponse, "items")
    implicit val listGetAdminResponseFormat: RootJsonFormat[ListGetAdminResponse] = jsonFormat(ListGetAdminResponse, "list")
    implicit val listNodeInfoGetAdminResponseFormat: RootJsonFormat[ListNodeInfoGetAdminResponse] = jsonFormat(ListNodeInfoGetAdminResponse, "nodeinfo")
}