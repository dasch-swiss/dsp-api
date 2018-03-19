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
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.admin.responder.{KnoraRequestADM, KnoraResponseADM}
import org.knora.webapi.messages.store.triplestoremessages.{StringLiteralV2, TriplestoreJsonProtocol}
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
  * Represents an API request payload that asks the Knora API server to update an existing list's basic information.
  * Sending an empty sequence means removing all data for the corresponding property. At least one label is required.
  *
  * @param projectIri the IRI of the project the list belongs to.
  * @param labels     the labels.
  * @param comments   the comments.
  */
case class ChangeListInfoApiRequestADM(projectIri: IRI,
                                       labels: Seq[StringLiteralV2],
                                       comments: Seq[StringLiteralV2]) extends ListADMJsonProtocol {

    private val stringFormatter = StringFormatter.getInstanceForConstantOntologies

    if (projectIri.isEmpty) {
        throw BadRequestException(PROJECT_IRI_MISSING_ERROR)
    }

    if (!stringFormatter.isKnoraProjectIriStr(projectIri)) {
        throw BadRequestException(PROJECT_IRI_INVALID_ERROR)
    }

    if (labels.isEmpty) {
        throw BadRequestException(REQUEST_REMOVING_ALL_LABELS_ERROR)
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

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Responses

/**
  * Represents a sequence of list info nodes.
  *
  * @param lists a [[ListADM]] sequence.
  */
case class ListsGetResponseADM(lists: Seq[ListADM]) extends KnoraResponseADM with ListADMJsonProtocol {
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
case class ListInfoGetResponseADM(listinfo: ListInfoADM) extends KnoraResponseADM with ListADMJsonProtocol {

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
case class NodePathGetResponseADM(nodelist: Seq[ListNodeADM]) extends KnoraResponseADM with ListADMJsonProtocol {

    def toJsValue = nodePathGetResponseADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages


case class ListADM(listinfo: ListInfoADM, children: Seq[ListNodeADM]) {
    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[List]].
      */
    def sorted: ListADM = {
        ListADM(
            listinfo = listinfo,
            children = children.sortBy(_.id)
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
case class ListInfoADM(id: IRI, projectIri: IRI, labels: Seq[StringLiteralV2], comments: Seq[StringLiteralV2]) {
    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListInfoADM]].
      */
    def sorted: ListInfoADM = {
        ListInfoADM(
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
case class ListNodeInfoADM(id: IRI, name: Option[String], labels: Seq[StringLiteralV2], comments: Seq[StringLiteralV2], position: Option[Int]) {
    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListNodeInfoADM]].
      */
    def sorted: ListNodeInfoADM = {
        ListNodeInfoADM(
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
case class ListNodeADM(id: IRI, name: Option[String], labels: Seq[StringLiteralV2], comments: Seq[StringLiteralV2], children: Seq[ListNodeADM], position: Option[Int]) {

    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListNodeADM]].
      */
    def sorted: ListNodeADM = {
        ListNodeADM(
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
                "labels" -> JsArray(nodeInfo.labels.map(_.toJson).toVector),
                "comments" -> JsArray(nodeInfo.comments.map(_.toJson).toVector)
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
                labels = labels,
                comments = comments
            )

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
            JsObject(
                "id" -> nodeInfo.id.toJson,
                "name" -> nodeInfo.name.toJson,
                "labels" -> JsArray(nodeInfo.labels.map(_.toJson).toVector),
                "comments" -> JsArray(nodeInfo.comments.map(_.toJson).toVector),
                "position" -> nodeInfo.position.toJson
            )
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

            val position = fields.get("position").map(_.convertTo[Int])

            ListNodeInfoADM(
                id = id,
                name = name,
                labels = labels,
                comments = comments,
                position = position
            )

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

            val children: Seq[ListNodeADM] = fields.get("children") match {
                case Some(JsArray(values)) => values.map(read)
                case None => Seq.empty[ListNodeADM]
                case _ => throw DeserializationException("The expected field 'children' is in the wrong format.")
            }

            val position = fields.get("position").map(_.convertTo[Int])

            ListNodeADM(
                id = id,
                name = name,
                labels = labels,
                comments = comments,
                children = children,
                position = position
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

            val listinfo = fields.getOrElse("listinfo", throw DeserializationException("The expected field 'listinfo' is missing.")).convertTo[ListInfoADM]
            val children = fields.get("children") match {
                case Some(JsArray(values)) => values.map(_.convertTo[ListNodeADM])
                case None => Seq.empty[ListNodeADM]
                case _ => throw DeserializationException("The expected field 'children' is in the wrong format.")
            }

            ListADM(
                listinfo = listinfo,
                children = children
            )
        }
    }


    implicit val createListApiRequestADMFormat: RootJsonFormat[CreateListApiRequestADM] = jsonFormat(CreateListApiRequestADM, "projectIri", "labels", "comments")
    implicit val changeListInfoApiRequestADMFormat: RootJsonFormat[ChangeListInfoApiRequestADM] = jsonFormat(ChangeListInfoApiRequestADM, "projectIri", "labels", "comments")
    implicit val nodePathGetResponseADMFormat: RootJsonFormat[NodePathGetResponseADM] = jsonFormat(NodePathGetResponseADM, "nodelist")
    implicit val listsGetResponseADMFormat: RootJsonFormat[ListsGetResponseADM] = jsonFormat(ListsGetResponseADM, "lists")
    implicit val listGetResponseADMFormat: RootJsonFormat[ListGetResponseADM] = jsonFormat(ListGetResponseADM, "list")
    implicit val listInfoGetResponseADMFormat: RootJsonFormat[ListInfoGetResponseADM] = jsonFormat(ListInfoGetResponseADM, "listinfo")
    implicit val listNodeInfoGetResponseADMFormat: RootJsonFormat[ListNodeInfoGetResponseADM] = jsonFormat(ListNodeInfoGetResponseADM, "nodeinfo")
}