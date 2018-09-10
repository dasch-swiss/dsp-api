/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import com.sun.xml.internal.ws.encoding.soap
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.admin.responder.{KnoraRequestADM, KnoraResponseADM}
import org.knora.webapi.messages.store.triplestoremessages.{StringLiteralSequenceV2, StringLiteralV2, TriplestoreJsonProtocol}
import org.knora.webapi.responders.admin.ListsResponderADM._
import org.knora.webapi.util.StringFormatter
import spray.json.{DefaultJsonProtocol, JsArray, JsObject, JsValue, JsonFormat, RootJsonFormat, _}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests


/**
  * Represents an API request payload that asks the Knora API server to create a new list. At least one
  * label needs to be supplied.
  *
  * @param projectIri the IRI of the project the list belongs to.
  * @param labels     the list's labels.
  * @param comments   the list's comments.
  */
case class CreateListApiRequestADM(projectIri: IRI,
                                   labels: Seq[StringLiteralV2],
                                   comments: Seq[StringLiteralV2]) extends ListADMJsonProtocol {

    private val stringFormatter = StringFormatter.getInstanceForConstantOntologies

    if (projectIri.isEmpty) {
        // println(this)
        throw BadRequestException(PROJECT_IRI_MISSING_ERROR)
    }

    if (!stringFormatter.isKnoraProjectIriStr(projectIri)) {
        // println(this)
        throw BadRequestException(PROJECT_IRI_INVALID_ERROR)
    }

    if (labels.isEmpty) {
        // println(this)
        throw BadRequestException(LABEL_MISSING_ERROR)
    }

    def toJsValue: JsValue = createListApiRequestADMFormat.write(this)
}

/**
  * Represents an API request payload that asks the Knora API server to create a new child list node which will be
  * attached to the list node identified by the supplied listNodeIri, where the list node to which a child list node
  * is added can be either a root list node or a child list node. At least one label needs to be supplied. If other
  * child nodes exist, the newly created list node will be appended to the end.
  *
  * @param parentNodeIri
  * @param labels
  * @param comments
  */
case class CreateChildNodeApiRequestADM(parentNodeIri: IRI,
                                        projectIri: IRI,
                                        name: Option[String],
                                        labels: Seq[StringLiteralV2],
                                        comments: Seq[StringLiteralV2]) extends ListADMJsonProtocol {

    private val stringFormatter = StringFormatter.getInstanceForConstantOntologies

    if (parentNodeIri.isEmpty) {
        // println(this)
        throw BadRequestException(LIST_NODE_IRI_MISSING_ERROR)
    }

    if (!stringFormatter.isKnoraListIriStr(parentNodeIri)) {
        // println(this)
        throw BadRequestException(LIST_NODE_IRI_INVALID_ERROR)
    }

    if (projectIri.isEmpty) {
        // println(this)
        throw BadRequestException(PROJECT_IRI_MISSING_ERROR)
    }

    if (!stringFormatter.isKnoraProjectIriStr(projectIri)) {
        // println(this)
        throw BadRequestException(PROJECT_IRI_INVALID_ERROR)
    }

    if (labels.isEmpty) {
        // println(this)
        throw BadRequestException(LABEL_MISSING_ERROR)
    }

    def toJsValue: JsValue = createListNodeApiRequestADMFormat.write(this)
}

/**
  * Represents an API request payload that asks the Knora API server to update an existing list's basic information.
  *
  * @param listIri    the IRI of the list to change.
  * @param projectIri the IRI of the project the list belongs to.
  * @param labels     the labels.
  * @param comments   the comments.
  */
case class ChangeListInfoApiRequestADM(listIri: IRI,
                                       projectIri: IRI,
                                       labels: Seq[StringLiteralV2],
                                       comments: Seq[StringLiteralV2]) extends ListADMJsonProtocol {

    private val stringFormatter = StringFormatter.getInstanceForConstantOntologies

    if (listIri.isEmpty) {
        throw BadRequestException(LIST_IRI_MISSING_ERROR)
    }

    if (!stringFormatter.isKnoraListIriStr(listIri)) {
        throw BadRequestException(LIST_IRI_INVALID_ERROR)
    }

    if (projectIri.isEmpty) {
        throw BadRequestException(PROJECT_IRI_MISSING_ERROR)
    }

    if (!stringFormatter.isKnoraProjectIriStr(projectIri)) {
        throw BadRequestException(PROJECT_IRI_INVALID_ERROR)
    }

    if (labels.isEmpty && comments.isEmpty) {
        throw BadRequestException(REQUEST_NOT_CHANGING_DATA_ERROR)
    }

    def toJsValue: JsValue = changeListInfoApiRequestADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait for messages that can be sent to `HierarchicalListsResponderV2`.
  */
sealed trait ListsResponderRequestADM extends KnoraRequestADM


/**
  * Requests a list of all lists or the lists inside a project. A successful response will be a [[ListsGetResponseADM]]
  *
  * @param projectIri     the IRI of the project.
  * @param requestingUser the user making the request.
  */
case class ListsGetRequestADM(projectIri: Option[IRI] = None,
                              requestingUser: UserADM) extends ListsResponderRequestADM

/**
  * Requests a list. A successful response will be a [[ListGetResponseADM]]
  *
  * @param iri            the IRI of the list.
  * @param requestingUser the user making the request.
  */
case class ListGetRequestADM(iri: IRI,
                             requestingUser: UserADM) extends ListsResponderRequestADM


/**
  * Request basic information about a list. A successful response will be a [[ListInfoGetResponseADM]]
  *
  * @param iri            the IRI of the list node.
  * @param requestingUser the user making the request.
  */
case class ListInfoGetRequestADM(iri: IRI,
                                 requestingUser: UserADM) extends ListsResponderRequestADM

/**
  * Request basic information about a list node. A successful response will be a [[ListNodeInfoGetResponseADM]]
  *
  * @param iri            the IRI of the list node.
  * @param requestingUser the user making the request.
  */
case class ListNodeInfoGetRequestADM(iri: IRI,
                                     requestingUser: UserADM) extends ListsResponderRequestADM


/**
  * Requests the path from the root node of a list to a particular node. A successful response will be
  * a [[NodePathGetResponseADM]].
  *
  * @param iri            the IRI of the node.
  * @param requestingUser the user making the request.
  */
case class NodePathGetRequestADM(iri: IRI,
                                 requestingUser: UserADM) extends ListsResponderRequestADM


/**
  * Requests the creation of a new list.
  *
  * @param createListRequest the [[CreateListApiRequestADM]] information used for creating the new list.
  * @param requestingUser    the user creating the new list.
  * @param apiRequestID      the ID of the API request.
  */
case class ListCreateRequestADM(createListRequest: CreateListApiRequestADM,
                                requestingUser: UserADM,
                                apiRequestID: UUID) extends ListsResponderRequestADM

/**
  * Request updating basic information of an existing list.
  *
  * @param listIri           the IRI of the list to be updated.
  * @param changeListRequest the data which needs to be update.
  * @param requestingUser    the user initiating the request.
  * @param apiRequestID      the ID of the API request.
  */
case class ListInfoChangeRequestADM(listIri: IRI,
                                    changeListRequest: ChangeListInfoApiRequestADM,
                                    requestingUser: UserADM,
                                    apiRequestID: UUID) extends ListsResponderRequestADM

/**
  * Request the creation of a new list node.
  *
  * @param parentNodeIri          the IRI of the list node to which we want to attach the newly created node.
  * @param createChildNodeRequest the new node information.
  * @param requestingUser         the user making the request.
  * @param apiRequestID           the ID of the API request.
  */
case class ListNodeCreateRequestADM(parentNodeIri: IRI,
                                    createChildNodeRequest: CreateChildNodeApiRequestADM,
                                    requestingUser: UserADM,
                                    apiRequestID: UUID) extends ListsResponderRequestADM

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Responses

/**
  * Represents a sequence of list info nodes.
  *
  * @param lists a [[ListRootNodeInfoADM]] sequence.
  */
case class ListsGetResponseADM(lists: Seq[ListNodeInfoADM]) extends KnoraResponseADM with ListADMJsonProtocol {
    def toJsValue = listsGetResponseADMFormat.write(this)
}

/**
  * Provides completes information about the list. The basic information (rood node) and all the child nodes.
  *
  * @param list the complete list.
  */
case class ListGetResponseADM(list: ListADM) extends KnoraResponseADM with ListADMJsonProtocol {

    def toJsValue = listGetResponseADMFormat.write(this)
}


/**
  * Provides basic information about a list (root) node (without it's children).
  *
  * @param listinfo the basic information about a list.
  */
case class ListInfoGetResponseADM(listinfo: ListRootNodeInfoADM) extends KnoraResponseADM with ListADMJsonProtocol {

    def toJsValue: JsValue = listInfoGetResponseADMFormat.write(this)
}


/**
  * Provides basic information about a list (child) node (without it's children).
  *
  * @param nodeinfo the basic information about a list node.
  */
case class ListNodeInfoGetResponseADM(nodeinfo: ListNodeInfoADM) extends KnoraResponseADM with ListADMJsonProtocol {

    def toJsValue: JsValue = listNodeInfoGetResponseADMFormat.write(this)
}


/**
  * Responds to a [[NodePathGetRequestADM]] by providing the path to a particular hierarchical list node.
  *
  * @param nodelist a list of the nodes composing the path from the list's root node up to and including the specified node.
  */
case class NodePathGetResponseADM(elements: Seq[NodePathElementADM]) extends KnoraResponseADM with ListADMJsonProtocol {

    def toJsValue = nodePathGetResponseADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages


case class ListADM(listinfo: ListRootNodeInfoADM, children: Seq[ListChildNodeADM]) {
    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[List]].
      */
    def sorted: ListADM = {
        ListADM(
            listinfo = listinfo,
            children = children.sortBy(_.position) map (_.sorted)
        )
    }
}

/**
  * Represents basic information about a list, the information stored in the list's root node.
  *
  * @param id         the IRI of the list.
  * @param projectIri the IRI of the project this list belongs to.
  * @param labels     the labels of the list in all available languages.
  * @param comments   the comments attached to the list in all available languages.
  */
case class ListInfoADM(id: IRI, projectIri: IRI, labels: StringLiteralSequenceV2, comments: StringLiteralSequenceV2) {
    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListInfoADM]].
      */
    def sorted: ListInfoADM = {
        ListInfoADM(
            id = id,
            projectIri = projectIri,
            labels = labels.sortByStringValue,
            comments = comments.sortByStringValue
        )
    }

    /**
      * Gets the label in the user's preferred language.
      *
      * @param userLang     the user's preferred language.
      * @param fallbackLang language to use if label is not available in user's preferred language.
      * @return the label in the preferred language.
      */
    def getLabelInPreferredLanguage(userLang: String, fallbackLang: String): Option[String] = {
        labels.getPreferredLanguage(userLang, fallbackLang)
    }

    /**
      * Gets the comment in the user's preferred language.
      *
      * @param userLang     the user's preferred language.
      * @param fallbackLang language to use if comment is not available in user's preferred language.
      * @return the comment in the preferred language.
      */
    def getCommentInPreferredLanguage(userLang: String, fallbackLang: String): Option[String] = {
        comments.getPreferredLanguage(userLang, fallbackLang)
    }

}

/**
  * Represents basic information about a list node, the information which is found in the list's root or child node.
  *
  * @param id          the IRI of the list.
  * @param name        the name of the list node.
  * @param labels      the labels of the node in all available languages.
  * @param comments    the comments attached to the node in all available languages.
  * @param position    the position of the node among its siblings (optional).
  * @param hasRootNode the Iri of the root node, if this is not the root node.
  */
abstract class ListNodeInfoADM(id: IRI, name: Option[String], labels: StringLiteralSequenceV2, comments: StringLiteralSequenceV2) {

    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListNodeInfoADM]].
      */
    def sorted: ListNodeInfoADM

    /**
      * Gets the label in the user's preferred language.
      *
      * @param userLang     the user's preferred language.
      * @param fallbackLang language to use if label is not available in user's preferred language.
      * @return the label in the preferred language.
      */
    def getLabelInPreferredLanguage(userLang: String, fallbackLang: String): Option[String] = {
        labels.getPreferredLanguage(userLang, fallbackLang)
    }

    /**
      * Gets the comment in the user's preferred language.
      *
      * @param userLang     the user's preferred language.
      * @param fallbackLang language to use if comment is not available in user's preferred language.
      * @return the comment in the preferred language.
      */
    def getCommentInPreferredLanguage(userLang: String, fallbackLang: String): Option[String] = {
        comments.getPreferredLanguage(userLang, fallbackLang)
    }
}

case class ListRootNodeInfoADM(id: IRI, projectIri: IRI, name: Option[String], labels: StringLiteralSequenceV2, comments: StringLiteralSequenceV2) extends ListNodeInfoADM(id, name, labels, comments) {

    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListRootNodeInfoADM]].
      */
    def sorted: ListRootNodeInfoADM = {
        ListRootNodeInfoADM(
            id = id,
            projectIri = projectIri,
            name = name,
            labels = labels.sortByStringValue,
            comments = comments.sortByStringValue
        )
    }
}

case class ListChildNodeInfoADM(id: IRI, name: Option[String], labels: StringLiteralSequenceV2, comments: StringLiteralSequenceV2, position: Int, hasRootNode: IRI) extends ListNodeInfoADM(id, name, labels, comments) {

    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListChildNodeInfoADM]].
      */
    def sorted: ListChildNodeInfoADM = {
        ListChildNodeInfoADM(
            id = id,
            name = name,
            labels = labels.sortByStringValue,
            comments = comments.sortByStringValue,
            position = position,
            hasRootNode = hasRootNode
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
  */
abstract class ListNodeADM(id: IRI, name: Option[String], labels: StringLiteralSequenceV2, comments: StringLiteralSequenceV2, children: Seq[ListChildNodeADM]) {

    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListNodeADM]].
      */
    def sorted: ListNodeADM

    /**
      * Gets the label in the user's preferred language.
      *
      * @param userLang     the user's preferred language.
      * @param fallbackLang language to use if label is not available in user's preferred language.
      * @return the label in the preferred language.
      */
    def getLabelInPreferredLanguage(userLang: String, fallbackLang: String): Option[String] = {
        labels.getPreferredLanguage(userLang, fallbackLang)
    }

    /**
      * Gets the comment in the user's preferred language.
      *
      * @param userLang     the user's preferred language.
      * @param fallbackLang language to use if comment is not available in user's preferred language.
      * @return the comment in the preferred language.
      */
    def getCommentInPreferredLanguage(userLang: String, fallbackLang: String): Option[String] = {
        comments.getPreferredLanguage(userLang, fallbackLang)
    }
}

/**
  * Represents a hierarchical list root node.
  *
  * @param id         the IRI of the list node.
  * @param projectIri the IRI of the project the list belongs to.
  * @param name       the name of the list node.
  * @param labels     the label(s) of the list node.
  * @param comments   the comment(s) attached to the list in a specific language (if language tags are used) .
  * @param children   the list node's child nodes.
  */
case class ListRootNodeADM(id: IRI, projectIri: IRI, name: Option[String], labels: StringLiteralSequenceV2, comments: StringLiteralSequenceV2, children: Seq[ListChildNodeADM]) extends ListNodeADM(id, name, labels, comments, children) {

    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListNodeADM]].
      */
    def sorted: ListRootNodeADM = {
        ListRootNodeADM(
            id = id,
            projectIri = projectIri,
            name = name,
            labels = labels.sortByStringValue,
            comments = comments.sortByStringValue,
            children = children.sortBy(_.position) map (_.sorted)
        )
    }
}

/**
  * Represents a hierarchical list child node.
  *
  * @param id          the IRI of the list node.
  * @param name        the name of the list node.
  * @param labels      the label(s) of the list node.
  * @param comments    the comment(s) attached to the list in a specific language (if language tags are used) .
  * @param children    the list node's child nodes.
  * @param position    the position of the node among its siblings.
  * @param hasRootNode the root node of the list.
  */
case class ListChildNodeADM(id: IRI, name: Option[String], labels: StringLiteralSequenceV2, comments: StringLiteralSequenceV2, position: Int, hasRootNode: IRI, children: Seq[ListChildNodeADM]) extends ListNodeADM(id, name, labels, comments, children) {


    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListNodeADM]].
      */
    def sorted: ListChildNodeADM = {
        ListChildNodeADM(
            id = id,
            name = name,
            labels = labels.sortByStringValue,
            comments = comments.sortByStringValue,
            position = position,
            hasRootNode = hasRootNode,
            children = children.sortBy(_.position) map (_.sorted)
        )
    }
}

/**
  * Represents an element of a node path.
  *
  * @param id       the IRI of the node path element.
  * @param name     the optional name of the node path element.
  * @param labels   the label(s) of the node path element.
  * @param comments the comment(s) of the node path element.
  */
case class NodePathElementADM(id: IRI, name: Option[String], labels: StringLiteralSequenceV2, comments: StringLiteralSequenceV2)

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
  * A spray-json protocol for generating Knora API V2 JSON providing data about lists.
  */
trait ListADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with TriplestoreJsonProtocol {

    implicit object ListInfoADMFormat extends JsonFormat[ListInfoADM] {
        /**
          * Converts a [[ListInfoADM]] to a [[JsValue]].
          *
          * @param nodeInfo a [[ListInfoADM]].
          * @return a [[JsValue]].
          */
        def write(nodeInfo: ListInfoADM): JsValue = {
            JsObject(
                "id" -> nodeInfo.id.toJson,
                "projectIri" -> nodeInfo.projectIri.toJson,
                "labels" -> JsArray(nodeInfo.labels.stringLiterals.map(_.toJson)),
                "comments" -> JsArray(nodeInfo.comments.stringLiterals.map(_.toJson))
            )
        }

        /**
          * Converts a [[JsValue]] to a [[ListInfoADM]].
          *
          * @param value a [[JsValue]].
          * @return a [[ListInfoADM]].
          */
        def read(value: JsValue): ListInfoADM = {

            val fields = value.asJsObject.fields

            val id = fields.getOrElse("id", throw DeserializationException("The expected field 'id' is missing.")).convertTo[String]
            val projectIri: IRI = fields.getOrElse("projectIri", throw DeserializationException("The expected field 'projectIri' is missing.")).convertTo[String]
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

            ListInfoADM(
                id = id,
                projectIri = projectIri,
                labels = StringLiteralSequenceV2(labels.toVector),
                comments = StringLiteralSequenceV2(comments.toVector)
            )

        }
    }


    implicit object ListRootNodeInfoFormat extends JsonFormat[ListRootNodeInfoADM] {

        def write(node:ListRootNodeInfoADM): JsValue = {
            ListNodeInfoFormat.write(node)
        }

        def read(value: JsValue): ListRootNodeInfoADM = {
            ListNodeInfoFormat.read(value).asInstanceOf[ListRootNodeInfoADM]
        }

    }

    implicit object ListChildNodeInfoFormat extends JsonFormat[ListChildNodeInfoADM] {

        def write(node:ListChildNodeInfoADM): JsValue = {
            ListNodeInfoFormat.write(node)
        }

        def read(value: JsValue): ListChildNodeInfoADM = {
            ListNodeInfoFormat.read(value).asInstanceOf[ListChildNodeInfoADM]
        }

    }

    implicit object ListNodeInfoFormat extends JsonFormat[ListNodeInfoADM] {
        /**
          * Converts a [[ListNodeInfoADM]] to a [[JsValue]].
          *
          * @param nodeInfo a [[ListNodeInfoADM]].
          * @return a [[JsValue]].
          */
        def write(nodeInfo: ListNodeInfoADM): JsValue = {

            nodeInfo match {
                case root: ListRootNodeInfoADM => {
                    JsObject(
                        "id" -> root.id.toJson,
                        "projectIri" -> root.projectIri.toJson,
                        "name" -> root.name.toJson,
                        "labels" -> JsArray(root.labels.stringLiterals.map(_.toJson)),
                        "comments" -> JsArray(root.comments.stringLiterals.map(_.toJson)),
                        "isRootNode" -> true.toJson
                    )
                }
                case child: ListChildNodeInfoADM => {
                    JsObject(
                        "id" -> child.id.toJson,
                        "name" -> child.name.toJson,
                        "labels" -> JsArray(child.labels.stringLiterals.map(_.toJson)),
                        "comments" -> JsArray(child.comments.stringLiterals.map(_.toJson)),
                        "position" -> child.position.toJson,
                        "hasRootNode" -> child.hasRootNode.toJson
                    )
                }
            }
        }

        /**
          * Converts a [[JsValue]] to a [[ListNodeInfoADM]].
          *
          * @param value a [[JsValue]].
          * @return a [[ListNodeInfoADM]].
          */
        def read(value: JsValue): ListNodeInfoADM = {

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

            val maybePosition: Option[Int] = fields.get("position").map(_.convertTo[Int])

            val maybeHasRootNode: Option[IRI] = fields.get("hasRootNode").map(_.convertTo[String])

            val maybeIsRootNode: Option[Boolean] = fields.get("isRootNode").map(_.convertTo[Boolean])

            val isRootNode = maybeIsRootNode match {
                case Some(boolValue) => boolValue
                case None => false
            }

            val maybeProjectIri: Option[IRI] = fields.get("projectIri").map(_.convertTo[IRI])

            if (isRootNode) {
                ListRootNodeInfoADM(
                    id = id,
                    projectIri = maybeProjectIri.getOrElse(throw DeserializationException("The project IRI is not defined.")),
                    name = name,
                    labels = StringLiteralSequenceV2(labels.toVector),
                    comments = StringLiteralSequenceV2(comments.toVector)
                )
            } else {
                ListChildNodeInfoADM(
                    id = id,
                    name = name,
                    labels = StringLiteralSequenceV2(labels.toVector),
                    comments = StringLiteralSequenceV2(comments.toVector),
                    position = maybePosition.getOrElse(throw DeserializationException("The position is not defined.")),
                    hasRootNode = maybeHasRootNode.getOrElse(throw DeserializationException("The root node is not defined."))
                )
            }
        }
    }


    implicit object ListRootNodeFormat extends JsonFormat[ListRootNodeADM] {

        def write(node:ListRootNodeADM): JsValue = {
            ListNodeFormat.write(node)
        }

        def read(value: JsValue): ListRootNodeADM = {
            ListNodeFormat.read(value).asInstanceOf[ListRootNodeADM]
        }

    }

    implicit object ListChildNodeFormat extends JsonFormat[ListChildNodeADM] {

        def write(node:ListChildNodeADM): JsValue = {
            ListNodeFormat.write(node)
        }

        def read(value: JsValue): ListChildNodeADM = {
            ListNodeFormat.read(value).asInstanceOf[ListChildNodeADM]
        }

    }

    implicit object ListNodeFormat extends JsonFormat[ListNodeADM] {
        /**
          * Converts a [[ListNodeADM]] to a [[JsValue]].
          *
          * @param node a [[ListNodeADM]].
          * @return a [[JsValue]].
          */
        def write(node: ListNodeADM): JsValue = {

            node match {
                case root: ListRootNodeADM => {
                    JsObject(
                        "id" -> root.id.toJson,
                        "projectIri" -> root.projectIri.toJson,
                        "name" -> root.name.toJson,
                        "labels" -> JsArray(root.labels.stringLiterals.map(_.toJson)),
                        "comments" -> JsArray(root.comments.stringLiterals.map(_.toJson)),
                        "isRootNode" -> true.toJson,
                        "children" -> JsArray(root.children.map(write).toVector)
                    )
                }
                case child: ListChildNodeADM => {
                    JsObject(
                        "id" -> child.id.toJson,
                        "name" -> child.name.toJson,
                        "labels" -> JsArray(child.labels.stringLiterals.map(_.toJson)),
                        "comments" -> JsArray(child.comments.stringLiterals.map(_.toJson)),
                        "position" -> child.position.toJson,
                        "hasRootNode" -> child.hasRootNode.toJson,
                        "children" -> JsArray(child.children.map(write).toVector)
                    )
                }
            }
        }

        /**
          * Converts a [[JsValue]] to a [[ListNodeADM]].
          *
          * @param value a [[JsValue]].
          * @return a [[ListNodeADM]].
          */
        def read(value: JsValue): ListNodeADM = {

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

            val children: Seq[ListChildNodeADM] = fields.get("children") match {
                case Some(JsArray(values)) => values.map(read).map(_.asInstanceOf[ListChildNodeADM])
                case None => Seq.empty[ListChildNodeADM]
                case _ => throw DeserializationException("The expected field 'children' is in the wrong format.")
            }

            val maybePosition: Option[Int] = fields.get("position").map(_.convertTo[Int])

            val maybeHasRootNode: Option[IRI] = fields.get("hasRootNode").map(_.convertTo[String])

            val maybeIsRootNode: Option[Boolean] = fields.get("isRootNode").map(_.convertTo[Boolean])

            val isRootNode = maybeIsRootNode match {
                case Some(boolValue) => boolValue
                case None => false
            }

            val maybeProjectIri: Option[IRI] = fields.get("projectIri").map(_.convertTo[IRI])

            if (isRootNode) {
                ListRootNodeADM(
                    id = id,
                    projectIri = maybeProjectIri.getOrElse(throw DeserializationException("The project IRI is not defined.")),
                    name = name,
                    labels = StringLiteralSequenceV2(labels.toVector),
                    comments = StringLiteralSequenceV2(comments.toVector),
                    children = children
                )
            } else {
                ListChildNodeADM(
                    id = id,
                    name = name,
                    labels = StringLiteralSequenceV2(labels.toVector),
                    comments = StringLiteralSequenceV2(comments.toVector),
                    position = maybePosition.getOrElse(throw DeserializationException("The position is not defined.")),
                    hasRootNode = maybeHasRootNode.getOrElse(throw DeserializationException("The root node is not defined.")),
                    children = children
                )
            }
        }
    }


    implicit object NodePathElementFormat extends JsonFormat[NodePathElementADM] {
        /**
          * Converts a [[NodePathElementADM]] to a [[JsValue]].
          *
          * @param nodeInfo a [[NodePathElementADM]].
          * @return a [[JsValue]].
          */
        def write(element: NodePathElementADM): JsValue = {

            JsObject(
                "id" -> element.id.toJson,
                "name" -> element.name.toJson,
                "labels" -> JsArray(element.labels.stringLiterals.map(_.toJson)),
                "comments" -> JsArray(element.comments.stringLiterals.map(_.toJson))
            )
        }

        /**
          * Converts a [[JsValue]] to a [[ListNodeInfoADM]].
          *
          * @param value a [[JsValue]].
          * @return a [[ListNodeInfoADM]].
          */
        def read(value: JsValue): NodePathElementADM = {

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

            NodePathElementADM(
                id = id,
                name = name,
                labels = StringLiteralSequenceV2(labels.toVector),
                comments = StringLiteralSequenceV2(comments.toVector)
            )

        }
    }

    implicit object ListFormat extends JsonFormat[ListADM] {
        /**
          * Converts a [[ListADM]] to a [[JsValue]].
          *
          * @param list a [[ListADM]].
          * @return a [[JsValue]].
          */
        def write(list: ListADM): JsValue = {
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
        def read(value: JsValue): ListADM = {

            val fields = value.asJsObject.fields

            val listinfo: ListRootNodeInfoADM = fields.getOrElse("listinfo", throw DeserializationException("The expected field 'listinfo' is missing.")).convertTo[ListInfoADM].asInstanceOf[ListRootNodeInfoADM]
            val children: Seq[ListChildNodeADM] = fields.get("children") match {
                case Some(JsArray(values)) => values.map(_.convertTo[ListNodeADM].asInstanceOf[ListChildNodeADM])
                case None => Seq.empty[ListChildNodeADM]
                case _ => throw DeserializationException("The expected field 'children' is in the wrong format.")
            }

            ListADM(
                listinfo = listinfo,
                children = children
            )
        }
    }


    implicit val createListApiRequestADMFormat: RootJsonFormat[CreateListApiRequestADM] = jsonFormat(CreateListApiRequestADM, "projectIri", "labels", "comments")
    implicit val createListNodeApiRequestADMFormat: RootJsonFormat[CreateChildNodeApiRequestADM] = jsonFormat(CreateChildNodeApiRequestADM, "parentNodeIri", "projectIri", "labels", "comments")
    implicit val changeListInfoApiRequestADMFormat: RootJsonFormat[ChangeListInfoApiRequestADM] = jsonFormat(ChangeListInfoApiRequestADM, "listIri", "projectIri", "labels", "comments")
    implicit val nodePathGetResponseADMFormat: RootJsonFormat[NodePathGetResponseADM] = jsonFormat(NodePathGetResponseADM, "elements")
    implicit val listsGetResponseADMFormat: RootJsonFormat[ListsGetResponseADM] = jsonFormat(ListsGetResponseADM, "lists")
    implicit val listGetResponseADMFormat: RootJsonFormat[ListGetResponseADM] = jsonFormat(ListGetResponseADM, "list")
    implicit val listInfoGetResponseADMFormat: RootJsonFormat[ListInfoGetResponseADM] = jsonFormat(ListInfoGetResponseADM, "listinfo")
    implicit val listNodeInfoGetResponseADMFormat: RootJsonFormat[ListNodeInfoGetResponseADM] = jsonFormat(ListNodeInfoGetResponseADM, "nodeinfo")
}