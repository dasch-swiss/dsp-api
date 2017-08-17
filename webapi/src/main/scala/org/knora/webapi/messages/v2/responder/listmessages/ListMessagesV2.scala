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

import java.{lang, util}

import com.github.jsonldjava.core.{JsonLdOptions, JsonLdProcessor}
import com.github.jsonldjava.utils.JsonUtils
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder.{KnoraRequestV2, KnoraResponseV2}

import scala.collection.JavaConverters._

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// API requests


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Messages

/**
  * An abstract trait for messages that can be sent to `HierarchicalListsResponderV2`.
  */
sealed trait ListsResponderRequestV2 extends KnoraRequestV2


/**
  * Requests a list of all lists or the lists inside a project. A successful response will be a [[ReadListsSequenceV2]]
  *
  * @param projectIri  the IRI of the project.
  * @param userProfile the profile of the user making the request.
  */
case class ListsGetRequestV2(projectIri: Option[IRI] = None,
                             userProfile: UserProfileV1) extends ListsResponderRequestV2

/**
  * Requests a list. A successful response will be a [[ReadListsSequenceV2]]
  *
  * @param iri         the IRI of the list.
  * @param userProfile the profile of the user making the request.
  */
case class ListGetRequestV2(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV2


/**
  * Request basic information about a list node. A successful response will be a [[ReadListsSequenceV2]]
  *
  * @param iri         the IRI of the list node.
  * @param userProfile the profile of the user making the request.
  */
case class ListNodeInfoGetRequestV2(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV2


/**
  * Requests the path from the root node of a list to a particular node. A successful response will be
  * a [[ReadListsSequenceV2]].
  *
  * @param iri         the IRI of the node.
  * @param userProfile the profile of the user making the request.
  */
case class NodePathGetRequestV2(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV2


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Responses

/**
  * Represents a sequence of list nodes.
  *
  * @param items the sequence of [[ListNodeV2]].
  */
case class ReadListsSequenceV2(items: Seq[ListNodeV2]) extends KnoraResponseV2 with ListV2JsonLDProtocol {

    def toXML: String = ???

    def toJsonLDWithValueObject(settings: SettingsImpl): String = readListsSequenceV2Writer(items, settings)
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages


/**
  * An abstract class extended by `ListRootNodeV2` and `ListChildNodeV2`.
  *
  * @param id         the IRI of the list node.
  * @param labels     the labels of the list in all available languages.
  * @param comments   the comments attached to the list in all available languages.
  * @param children   the children of this list node.
  */
sealed abstract class ListNodeV2(id: IRI, labels: Seq[StringV2], comments: Seq[StringV2], children: Seq[ListNodeV2]) {
    def sorted: ListNodeV2
}

/**
  * Represents information about a list. This information is found in the list's root node.
  *
  * @param id         the IRI of the list.
  * @param projectIri the IRI of the project this list belongs to (optional).
  * @param labels     the labels of the list in all available languages.
  * @param comments   the comments attached to the list in all available languages.
  */
case class ListRootNodeV2(id: IRI, projectIri: Option[IRI], labels: Seq[StringV2], comments: Seq[StringV2], children: Seq[ListChildNodeV2]) extends ListNodeV2(id, labels, comments, children) {

    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListRootNodeV2]].
      */
    override def sorted: ListRootNodeV2 = {
        ListRootNodeV2(
            id = id,
            projectIri = projectIri,
            labels = labels.sortBy(_.value),
            comments = comments.sortBy(_.value),
            children = children.sortBy(_.id).map(_.sorted)
        )
    }
}

/**
  * Represents a hierarchical list node in Knora API V2 format.
  *
  * @param id             the IRI of the list node.
  * @param name           the name of the list node.
  * @param labels          the label(s) of the list node.
  * @param comments        the comment(s) attached to the list in a specific language (if language tags are used) .
  * @param children the list node's child nodes.
  * @param position       the position of the node among its siblings (optional).
  */
case class ListChildNodeV2(id: IRI, name: Option[String], labels: Seq[StringV2], comments: Seq[StringV2], children: Seq[ListChildNodeV2], position: Option[Int]) extends ListNodeV2(id, labels, comments, children) {

    /**
      * Sorts the whole hierarchy.
      *
      * @return a sorted [[ListChildNodeV2]].
      */
    override def sorted: ListChildNodeV2 = {
        ListChildNodeV2(
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
    def toObject: Object = {
        if (language.nonEmpty) {
            Map(
                "@language" -> language.get,
                "@value" -> value
            ).asJava
        } else {
            value
        }
    }
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON-LD formatting

/**
  * A json-ld protocol for generating Knora API V2 JSON-LD providing data about lists.
  */
trait ListV2JsonLDProtocol {

    private val rdfsLabel: IRI = "http://www.w3.org/2000/01/rdf-schema#label"
    private val rdfsComment: IRI = "http://www.w3.org/2000/01/rdf-schema#comment"

    /**
      * Returns an JSON-LD string representing a sequence of list nodes.
      *
      * @param items the sequence of [[ListNodeV2]].
      * @param settings the system settings.
      * @return a JSON-LD [[String]].
      */
    def readListsSequenceV2Writer(items: Seq[ListNodeV2], settings: SettingsImpl): String = {

        val context = new util.HashMap[String, String]()
        context.put("@vocab", "http://schema.org/")
        context.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
        context.put("knora-base", "http://www.knora.org/ontology/knora-base#")

        val json: util.HashMap[String, Object] = new util.HashMap[String, Object]()

        val listsSeq: util.List[Object] = items.map {
            node: ListNodeV2 => node match {
                case rootNode: ListRootNodeV2 => listRootNodeV2Writer(rootNode, settings)
                case childNode: ListChildNodeV2 => listChildNodeV2Writer(childNode, settings)
            }
        }.asJava

        json.put("@type", "ItemList")

        json.put("http://schema.org/numberOfItems", new lang.Integer(items.size))

        json.put("http://schema.org/itemListElement", listsSeq)

        val compacted = JsonLdProcessor.compact(json, context, new JsonLdOptions())

        JsonUtils.toPrettyString(compacted)
    }

    /**
      *  Returns an object which will be part of an JSON-LD string.
      *
      * @param node
      * @param settings
      * @return
      */
    private def listRootNodeV2Writer(node: ListRootNodeV2, settings: SettingsImpl): Object = {

        // ListRootNodeV2(id: IRI, projectIri: Option[IRI], labels: Seq[StringWithOptionalLang], comments: Seq[StringWithOptionalLang], children: Seq[ListChildNodeV2]) extends ListNodeV2(id, labels, comments, children)

        val result: util.HashMap[String, Object] = new util.HashMap[String, Object]()

        result.put("@id", node.id)

        result.put("@type", OntologyConstants.KnoraBase.ListNode)

        result.put(OntologyConstants.KnoraBase.IsRootNode, new lang.Boolean(true))

        if (node.projectIri.nonEmpty) {
            result.put(OntologyConstants.KnoraBase.AttachedToProject, node.projectIri.get)
        }

        if (node.labels.nonEmpty) {

            val labels: util.List[Object] = node.labels.map {
                label: StringV2 => label.toObject
            }.asJava

            result.put(rdfsLabel, labels)
        }

        if (node.comments.nonEmpty) {

            val comments: util.List[Object] = node.comments.map {
                comment: StringV2 => comment.toObject
            }.asJava

            result.put(rdfsComment, comments)
        }

        if (node.children.nonEmpty) {

            val nodes: util.List[Object] = node.children.map {
                child: ListChildNodeV2 => listChildNodeV2Writer(child, settings)
            }.asJava

            result.put(OntologyConstants.KnoraBase.HasSubListNode, nodes)
        }

        result
    }

    /**
      * Returns an object which will be part of an JSON-LD string.
      *
      * @param node
      * @param settings
      * @return
      */
    def listChildNodeV2Writer(node: ListChildNodeV2, settings: SettingsImpl): Object = {

        // ListChildNodeV2(id: IRI, hasRoot: IRI, name: Option[String], labels: Seq[StringWithOptionalLang], comments: Seq[StringWithOptionalLang], children: Seq[ListChildNodeV2], position: Option[Int]) extends ListNodeV2(id, labels, comments, children)

        val result: util.HashMap[String, Object] = new util.HashMap[String, Object]()

        result.put("@id", node.id)

        result.put("@type", OntologyConstants.KnoraBase.ListNode)

        if (node.name.nonEmpty) {
            result.put(OntologyConstants.KnoraBase.ListNodeName, node.name.get)
        }

        if (node.labels.nonEmpty) {

            val labels: util.List[Object] = node.labels.map {
                label: StringV2 => label.toObject
            }.asJava

            result.put(rdfsLabel, labels)
        }

        if (node.comments.nonEmpty) {

            val comments: util.List[Object] = node.comments.map {
                comment: StringV2 => comment.toObject
            }.asJava

            result.put(rdfsComment, comments)
        }

        if (node.children.nonEmpty) {
            val nodes: util.List[Object] = node.children.map {
                child: ListChildNodeV2 => listChildNodeV2Writer(child, settings)
            }.asJava

            result.put(OntologyConstants.KnoraBase.HasSubListNode, nodes)
        }

        if (node.position.nonEmpty) {
            result.put(OntologyConstants.KnoraBase.ListNodePosition, new lang.Integer(node.position.get))
        }

        result
    }

}