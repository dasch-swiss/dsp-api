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

package org.knora.webapi.messages.admin.responder.listsmessages


import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.{KnoraRequestADM, KnoraResponseADM}
import org.knora.webapi.messages.store.triplestoremessages.{StringLiteralV2, TriplestoreJsonProtocol}
import org.knora.webapi.messages.v1.responder.usermessages.UserADM
import spray.json.{DefaultJsonProtocol, JsArray, JsObject, JsValue, JsonFormat, RootJsonFormat, _}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait for messages that can be sent to `HierarchicalListsResponderV2`.
  */
sealed trait ListsResponderRequestADM extends KnoraRequestADM


/**
  * Requests a list of all lists or the lists inside a project. A successful response will be a [[ListsGetResponseADM]]
  *
  * @param projectIri  the IRI of the project.
  * @param userProfile the profile of the user making the request.
  */
case class ListsGetRequestADM(projectIri: Option[IRI] = None,
                              userProfile: UserADM) extends ListsResponderRequestADM

/**
  * Requests a list. A successful response will be a [[ListGetResponseADM]]
  *
  * @param iri         the IRI of the list.
  * @param userProfile the profile of the user making the request.
  */
case class ListGetRequestADM(iri: IRI,
                             userProfile: UserADM) extends ListsResponderRequestADM


/**
  * Request basic information about a list. A successful response will be a [[ListInfoGetResponseADM]]
  *
  * @param iri         the IRI of the list node.
  * @param userProfile the profile of the user making the request.
  */
case class ListInfoGetRequestADM(iri: IRI,
                                 userProfile: UserADM) extends ListsResponderRequestADM

/**
  * Request basic information about a list node. A successful response will be a [[ListNodeInfoGetResponseADM]]
  *
  * @param iri         the IRI of the list node.
  * @param userProfile the profile of the user making the request.
  */
case class ListNodeInfoGetRequestADM(iri: IRI,
                                     userProfile: UserADM) extends ListsResponderRequestADM


/**
  * Requests the path from the root node of a list to a particular node. A successful response will be
  * a [[NodePathGetResponseADM]].
  *
  * @param iri         the IRI of the node.
  * @param userProfile the profile of the user making the request.
  */
case class NodePathGetRequestADM(iri: IRI,
                                 userProfile: UserADM) extends ListsResponderRequestADM


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Responses

/**
  * Represents a sequence of list info nodes.
  *
  * @param items a [[ListInfo]] sequence.
  */
case class ListsGetResponseADM(items: Seq[ListInfo]) extends KnoraResponseADM with ListADMJsonProtocol {
    def toJsValue = listsGetAdminResponseFormat.write(this)
}

/**
  * Provides completes information about the list. The basic information (rood node) and all the child nodes.
  *
  * @param list the complete list.
  */
case class ListGetResponseADM(list: FullList) extends KnoraResponseADM with ListADMJsonProtocol {

    def toJsValue = listGetAdminResponseFormat.write(this)
}


/**
  * Provides basic information about a list (root) node (without it's children).
  *
  * @param listinfo the basic information about a list.
  */
case class ListInfoGetResponseADM(listinfo: ListInfo) extends KnoraResponseADM with ListADMJsonProtocol {

    def toJsValue: JsValue = listInfoGetAdminResponseFormat.write(this)
}


/**
  * Provides basic information about a list (child) node (without it's children).
  *
  * @param nodeinfo the basic information about a list node.
  */
case class ListNodeInfoGetResponseADM(nodeinfo: ListNodeInfo) extends KnoraResponseADM with ListADMJsonProtocol {

    def toJsValue: JsValue = listNodeInfoGetAdminResponseFormat.write(this)
}

/**
  * Responds to a [[NodePathGetRequestADM]] by providing the path to a particular hierarchical list node.
  *
  * @param nodelist a list of the nodes composing the path from the list's root node up to and including the specified node.
  */
case class NodePathGetResponseADM(nodelist: Seq[ListNode]) extends KnoraResponseADM with ListADMJsonProtocol {

    def toJsValue = nodePathGetAdminResponseFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages


case class FullList(listinfo: ListInfo, children: Seq[ListNode]) {
    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[List]].
      */
    def sorted: FullList = {
        FullList(
            listinfo = listinfo,
            children = children.sortBy(_.id)
        )
    }
}



/**
  * Represents basic information about a list, the information stored in the list's root node.
  *
  * @param id         the IRI of the list.
  * @param projectIri the IRI of the project this list belongs to (optional).
  * @param labels     the labels of the list in all available languages.
  * @param comments   the comments attached to the list in all available languages.
  */
case class ListInfo(id: IRI, projectIri: Option[IRI], labels: Seq[StringLiteralV2], comments: Seq[StringLiteralV2]) {
    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListInfo]].
      */
    def sorted: ListInfo = {
        ListInfo(
            id = id,
            projectIri = projectIri,
            labels = labels.sortBy(_.value),
            comments = comments.sortBy(_.value)
        )
    }
}

/**
  * Represents basic information about a list node, the information which is found in the list's child node.
  *
  * @param id       the IRI of the list.
  * @param name     the name of the list node.
  * @param labels   the labels of the node in all available languages.
  * @param comments the comments attached to the node in all available languages.
  * @param position the position of the node among its siblings (optional).
  */
case class ListNodeInfo(id: IRI, name: Option[String], labels: Seq[StringLiteralV2], comments: Seq[StringLiteralV2], position: Option[Int]) {
    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListNodeInfo]].
      */
    def sorted: ListNodeInfo = {
        ListNodeInfo(
            id = id,
            name = name,
            labels = labels.sortBy(_.value),
            comments = comments.sortBy(_.value),
            position = position
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
case class ListNode(id: IRI, name: Option[String], labels: Seq[StringLiteralV2], comments: Seq[StringLiteralV2], children: Seq[ListNode], position: Option[Int]) {

    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListNode]].
      */
    def sorted: ListNode = {
        ListNode(
            id = id,
            name = name,
            labels = labels.sortBy(_.value),
            comments = comments.sortBy(_.value),
            children = children.sortBy(_.id).map(_.sorted),
            position = position
        )
    }
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API V2 JSON providing data about lists.
  */
trait ListADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with TriplestoreJsonProtocol {

    implicit object ListInfoFormat extends JsonFormat[ListInfo] {
        /**
          * Converts a [[ListInfo]] to a [[JsValue]].
          *
          * @param nodeInfo a [[ListInfo]].
          * @return a [[JsValue]].
          */
        def write(nodeInfo: ListInfo): JsValue = {
            JsObject(
                "id" -> nodeInfo.id.toJson,
                "projectIri" -> nodeInfo.projectIri.toJson,
                "labels" -> JsArray(nodeInfo.labels.map(_.toJson).toVector),
                "comments" -> JsArray(nodeInfo.comments.map(_.toJson).toVector)
            )
        }

        /**
          * Converts a [[JsValue]] to a [[ListInfo]].
          *
          * @param value a [[JsValue]].
          * @return a [[ListInfo]].
          */
        def read(value: JsValue): ListInfo = {

            val fields = value.asJsObject.fields

            val id = fields.getOrElse("id", throw DeserializationException("The expected field 'id' is missing.")).convertTo[String]
            val projectIri: Option[IRI] = fields.get("projectIri").map(_.convertTo[String])
            val labels = fields.get("labels") match {
                case Some(JsArray(values)) => values.map(_.convertTo[StringLiteralV2])
                case None => Seq.empty[StringLiteralV2]
                case _ => throw DeserializationException("The expected field 'labels' is in the wrong format.")
            }
            val comments = fields.get("comments") match {
                case Some(JsArray(values)) => values.map(_.convertTo[StringLiteralV2])
                case None => Seq.empty[StringLiteralV2]
                case _ => throw DeserializationException("The expected field 'comments' is in the wrong format.")
            }

            ListInfo(
                id = id,
                projectIri = projectIri,
                labels = labels,
                comments = comments
            )

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
            JsObject(
                "id" -> nodeInfo.id.toJson,
                "name" -> nodeInfo.name.toJson,
                "labels" -> JsArray(nodeInfo.labels.map(_.toJson).toVector),
                "comments" -> JsArray(nodeInfo.comments.map(_.toJson).toVector),
                "position" -> nodeInfo.position.toJson
            )
        }

        /**
          * Converts a [[JsValue]] to a [[ListNodeInfo]].
          *
          * @param value a [[JsValue]].
          * @return a [[ListNodeInfo]].
          */
        def read(value: JsValue): ListNodeInfo = {

            val fields = value.asJsObject.fields

            val id = fields.getOrElse("id", throw DeserializationException("The expected field 'id' is missing.")).convertTo[String]
            val name = fields.get("name").map(_.convertTo[String])
            val labels = fields.get("labels") match {
                case Some(JsArray(values)) => values.map(_.convertTo[StringLiteralV2])
                case None => Seq.empty[StringLiteralV2]
                case _ => throw DeserializationException("The expected field 'labels' is in the wrong format.")
            }

            val comments = fields.get("comments") match {
                case Some(JsArray(values)) => values.map(_.convertTo[StringLiteralV2])
                case None => Seq.empty[StringLiteralV2]
                case _ => throw DeserializationException("The expected field 'comments' is in the wrong format.")
            }

            val position = fields.get("position").map(_.convertTo[Int])

            ListNodeInfo(
                id = id,
                name = name,
                labels = labels,
                comments = comments,
                position = position
            )

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
            JsObject(
                "id" -> node.id.toJson,
                "name" -> node.name.toJson,
                "labels" -> JsArray(node.labels.map(_.toJson).toVector),
                "comments" -> JsArray(node.comments.map(_.toJson).toVector),
                "children" -> JsArray(node.children.map(write).toVector),
                "position" -> node.position.toJson
            )
        }

        /**
          * Converts a [[JsValue]] to a [[ListNode]].
          *
          * @param value a [[JsValue]].
          * @return a [[ListNode]].
          */
        def read(value: JsValue): ListNode = {

            val fields = value.asJsObject.fields

            val id = fields.getOrElse("id", throw DeserializationException("The expected field 'id' is missing.")).convertTo[String]
            val name = fields.get("name").map(_.convertTo[String])
            val labels = fields.get("labels") match {
                case Some(JsArray(values)) => values.map(_.convertTo[StringLiteralV2])
                case None => Seq.empty[StringLiteralV2]
                case _ => throw DeserializationException("The expected field 'labels' is in the wrong format.")
            }

            val comments = fields.get("comments") match {
                case Some(JsArray(values)) => values.map(_.convertTo[StringLiteralV2])
                case None => Seq.empty[StringLiteralV2]
                case _ => throw DeserializationException("The expected field 'comments' is in the wrong format.")
            }

            val children: Seq[ListNode] = fields.get("children") match {
                case Some(JsArray(values)) => values.map(read)
                case None => Seq.empty[ListNode]
                case _ => throw DeserializationException("The expected field 'children' is in the wrong format.")
            }

            val position = fields.get("position").map(_.convertTo[Int])

            ListNode(
                id = id,
                name = name,
                labels = labels,
                comments = comments,
                children = children,
                position = position
            )

        }
    }

    implicit object ListFormat extends JsonFormat[FullList] {
        /**
          * Converts a [[FullList]] to a [[JsValue]].
          *
          * @param list a [[FullList]].
          * @return a [[JsValue]].
          */
        def write(list: FullList): JsValue = {
            JsObject(
                "listinfo" -> list.listinfo.toJson,
                "children" -> JsArray(list.children.map(_.toJson).toVector)
            )
        }

        /**
          * Converts a [[JsValue]] to a [[List]].
          *
          * @param value a [[JsValue]].
          * @return a [[List]].
          */
        def read(value: JsValue): FullList = {

            val fields = value.asJsObject.fields

            val listinfo = fields.getOrElse("listinfo", throw DeserializationException("The expected field 'listinfo' is missing.")).convertTo[ListInfo]
            val children = fields.get("children") match {
                case Some(JsArray(values)) => values.map(_.convertTo[ListNode])
                case None => Seq.empty[ListNode]
                case _ => throw DeserializationException("The expected field 'children' is in the wrong format.")
            }

            FullList(
                listinfo = listinfo,
                children = children
            )
        }
    }


    implicit val nodePathGetAdminResponseFormat: RootJsonFormat[NodePathGetResponseADM] = jsonFormat(NodePathGetResponseADM, "nodelist")
    implicit val listsGetAdminResponseFormat: RootJsonFormat[ListsGetResponseADM] = jsonFormat(ListsGetResponseADM, "items")
    implicit val listGetAdminResponseFormat: RootJsonFormat[ListGetResponseADM] = jsonFormat(ListGetResponseADM, "list")
    implicit val listInfoGetAdminResponseFormat: RootJsonFormat[ListInfoGetResponseADM] = jsonFormat(ListInfoGetResponseADM, "listinfo")
    implicit val listNodeInfoGetAdminResponseFormat: RootJsonFormat[ListNodeInfoGetResponseADM] = jsonFormat(ListNodeInfoGetResponseADM, "nodeinfo")
}