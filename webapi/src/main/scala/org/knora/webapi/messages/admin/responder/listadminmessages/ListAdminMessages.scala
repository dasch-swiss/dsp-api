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
import org.knora.webapi.util.jsonld._
import spray.json.{DefaultJsonProtocol, JsValue, JsonFormat, NullOptions, RootJsonFormat}

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
  * @param items a [[ListInfo]] sequence.
  */
case class ListsGetAdminResponse(items: Seq[ListInfo]) extends KnoraAdminResponse with ListAdminJsonProtocol {
    def toJsValue = listsGetAdminResponseFormat.write(this)
}

/**
  * Provides completes information about the list. The basic information (rood node) together the complete list
  * hierarchy (child nodes).
  *
  * @param info  the basic information about a list.
  * @param nodes the whole list.
  */
case class ListGetAdminResponse(info: ListInfo, nodes: Seq[ListNode]) extends KnoraAdminResponse with ListAdminJsonProtocol {

    def toJsValue = listGetAdminResponseFormat.write(this)
}

/**
  * Provides basic information about a list node.
  *
  * @param id       the IRI of the list node.
  * @param labels   the labels of the list in all available languages.
  * @param comments the comments of the list in all available languages.
  */
case class ListNodeInfoGetAdminResponse(id: IRI, labels: Seq[StringV2], comments: Seq[StringV2]) extends KnoraAdminResponse with ListAdminJsonProtocol {

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
  * Represents basic information about a list. This information is stored in the list's root node.
  *
  * @param id         the IRI of the list.
  * @param projectIri the IRI of the project this list belongs to (optional).
  * @param labels     the labels of the list in all available languages.
  * @param comments   the comments attached to the list in all available languages.
  */
case class ListInfo(id: IRI, projectIri: Option[IRI], labels: Seq[StringV2], comments: Seq[StringV2])

/**
  * An abstract class extended by `ListRootNodeV2` and `ListChildNodeV2`.
  *
  * @param id         the IRI of the list node.
  * @param labels     the labels of the list in all available languages.
  * @param comments   the comments attached to the list in all available languages.
  * @param children   the children of this list node.
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
  * @param id             the IRI of the list node.
  * @param name           the name of the list node.
  * @param labels          the label(s) of the list node.
  * @param comments        the comment(s) attached to the list in a specific language (if language tags are used) .
  * @param children the list node's child nodes.
  * @param position       the position of the node among its siblings (optional).
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
case class StringV2(value: String, language: Option[String] = None) {

    /**
      * Returns the string representation.
      *
      * @return
      */
    override def toString: String = {
        if (language.nonEmpty) {
            "StringV2(%s, %s)".format(value, language.get)
        } else {
            value
        }
    }

    /**
      * Returns the value as an integer.
      *
      * @return
      */
    def toInt: Int = value.toInt

    /**
      * Returns the value as an IRI.
      *
      * @return
      */
    def toIri: IRI = value

    /**
      * Returns a java object.
      *
      * @return
      */
    def toJsonLDValue: JsonLDValue = {
        if (language.nonEmpty) {
            JsonLDObject(Map(
                "@language" -> JsonLDString(language.get),
                "@value" -> JsonLDString(value)
            ))
        } else {
            JsonLDString(value)
        }
    }
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API V2 JSON providing data about lists.
  */
trait ListAdminJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with NullOptions {

    implicit val nodePathGetAdminResponseFormat: RootJsonFormat[NodePathGetAdminResponse] = jsonFormat(NodePathGetAdminResponse, "nodelist")
    implicit val stringV2Format: JsonFormat[StringV2] = jsonFormat2(StringV2)
    implicit val listInfoFormat: JsonFormat[ListInfo] = jsonFormat4(ListInfo)
    implicit val listsGetAdminResponseFormat: RootJsonFormat[ListsGetAdminResponse] = jsonFormat(ListsGetAdminResponse, "lists")
    implicit val listGetAdminResponseFormat: RootJsonFormat[ListGetAdminResponse] = jsonFormat(ListGetAdminResponse, "info", "nodes")
    implicit val listNodeInfoGetAdminResponseFormat: RootJsonFormat[ListNodeInfoGetAdminResponse] = jsonFormat(ListNodeInfoGetAdminResponse, "id", "labels", "comments")
}