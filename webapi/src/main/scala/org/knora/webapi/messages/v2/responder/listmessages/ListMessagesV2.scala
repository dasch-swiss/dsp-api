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
import org.knora.jsonld.{KnoraJsonLDFormat, KnoraJsonLDSupport, _}
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

    def toJsonLDWithValueObject(settings: SettingsImpl): String = ReadListsSequenceV2JsonLDFormat.write(this)
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
trait ListV2JsonLDProtocol extends KnoraJsonLDSupport {

    implicit object ReadListsSequenceV2JsonLDFormat extends KnoraJsonLDFormat[ReadListsSequenceV2] {
        /**
          * Converts a [[Map[String, AnyRef]]] to a [[ReadListsSequenceV2]].
          *
          * @param expanded a [[Map[String, AnyRef]]].
          * @return a [[ReadListsSequenceV2]].
          */
        def read(expanded: Map[String, Any]): ReadListsSequenceV2 = {
            val seq = readListsSequenceV2Reader(expanded)

            ReadListsSequenceV2(items = seq)
        }

        /**
          * Recursively converts a [[ReadListsSequenceV2]] to a JSON-LD [[String]].
          *
          * @param seq a [[ReadListsSequenceV2]].
          * @return a [[String]].
          */
        def write(seq: ReadListsSequenceV2): String = {
            readListsSequenceV2Writer(seq.items)
        }
    }

    private val ID = "@id"
    private val TYPE = "@type"

    /******************************************************************************************************************/
    /** READER                                                                                                        */
    /******************************************************************************************************************/

    private def readListsSequenceV2Reader(expanded: Map[String, Any]): Seq[ListNodeV2] = {

        val items: Seq[Any] = expanded.getOrElse(OntologyConstants.SchemaOrg.ItemListElement, invalidJsonLDError("Missing 'itemListElement")) match {
            case x: Seq[Any] => x
            case x: Any => Seq(x)
        }

        val result: Seq[ListNodeV2] = items map {
            case item: Map[String, Any] => {
                val objectType = item.getOrElse(TYPE, invalidJsonLDError("Field '@type' is missing.")).toString()
                objectType match {
                    case OntologyConstants.KnoraBase.ListNode => listRootNodeV2Reader(item)
                    case other => invalidJsonLDError(s"The sent items are of the wrong type. Only '${OntologyConstants.KnoraBase.ListNode}' is allowed.")
                }
            }
            case _ => invalidJsonLDError("Expecting objects.")
        }

        result
    }

    private def listRootNodeV2Reader(item: Map[String, Any]): ListRootNodeV2 = {

        val id: IRI = item.getOrElse(ID, invalidJsonLDError("Field '@id' is missing.")).toString()
        val projectIri: Option[IRI] = item.get(OntologyConstants.KnoraBase.AttachedToProject).map(_.toString)
        val labels: Seq[StringV2] = item.get(OntologyConstants.Rdfs.Label) match {
            case Some(x: Seq[Any]) => x.map(stringV2Reader)
            case Some(x: Any) => Seq(stringV2Reader(x))
            case None => Seq.empty[StringV2]
        }

        val comments: Seq[StringV2] = item.get(OntologyConstants.Rdfs.Comment) match {
            case Some(x: Seq[Any]) => x.map(stringV2Reader)
            case Some(x: Any) => Seq(stringV2Reader(x))
            case None => Seq.empty[StringV2]
        }

        val children: Seq[ListChildNodeV2] = item.get(OntologyConstants.KnoraBase.HasSubListNode) match {
            case Some(x: Seq[Map[String, Any]]) => x.map(listChildNodeV2Reader)
            case Some(x: Map[String, Any]) => Seq(listChildNodeV2Reader(x))
            case Some(_) => invalidJsonLDError("Expecting objects.")
            case None => Seq.empty[ListChildNodeV2]
        }

        ListRootNodeV2(
            id = id,
            projectIri = projectIri,
            labels = labels,
            comments = comments,
            children = children
        )
    }

    private def listChildNodeV2Reader(node:  Map[String, Any]): ListChildNodeV2 = {

        val id: IRI = node.getOrElse(ID, invalidJsonLDError("Field '@id' is missing.")).toString()

        val name: Option[String] = node.get(OntologyConstants.KnoraBase.ListNodeName).map(_.toString)

        val labels: Seq[StringV2] = node.get(OntologyConstants.Rdfs.Label) match {
            case Some(x: Seq[Any]) => x.map(stringV2Reader)
            case Some(x: Any) => Seq(stringV2Reader(x))
            case None => Seq.empty[StringV2]
        }

        val comments: Seq[StringV2] = node.get(OntologyConstants.Rdfs.Comment) match {
            case Some(x: Seq[Any]) => x.map(stringV2Reader)
            case Some(x: Any) => Seq(stringV2Reader(x))
            case None => Seq.empty[StringV2]
        }

        val children: Seq[ListChildNodeV2] = node.get(OntologyConstants.KnoraBase.HasSubListNode) match {
            case Some(x: Seq[Map[String, Any]]) => x.map(listChildNodeV2Reader)
            case Some(x: Map[String, Any]) => Seq(listChildNodeV2Reader(x))
            case Some(_) => invalidJsonLDError("Expecting objects.")
            case None => Seq.empty[ListChildNodeV2]
        }

        val position: Option[Int] = node.get(OntologyConstants.KnoraBase.ListNodePosition).map(_.toString.toInt)

        ListChildNodeV2(
            id = id,
            name = name,
            labels = labels,
            comments = comments,
            children = children,
            position = position
        )
    }

    private def stringV2Reader(label: Any): StringV2 = {

        label match {
            case l: Map[String, String] => {
                val value = l.getOrElse("@value", throw invalidJsonLDError("Value is missing.")).toString
                val language = l.getOrElse("@language", throw invalidJsonLDError("Language is missing.")).toString
                StringV2(value = value, language = Some(language))
            }
            case l: String => {
                StringV2(l)
            }
            case other => invalidJsonLDError("Expecting object or string value.")
        }
    }

    /******************************************************************************************************************/
    /** WRITER                                                                                                        */
    /******************************************************************************************************************/

    /**
      * Returns an JSON-LD string representing a sequence of list nodes.
      *
      * @param items the sequence of [[ListNodeV2]].
      * @return a JSON-LD [[String]].
      */
    private def readListsSequenceV2Writer(items: Seq[ListNodeV2]): String = {

        val context = new util.HashMap[String, String]()
        context.put("@vocab", "http://schema.org/")
        context.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
        context.put("knora-base", "http://www.knora.org/ontology/knora-base#")

        val json: util.HashMap[String, Object] = new util.HashMap[String, Object]()

        val listsSeq: util.List[Object] = items.map {
            node: ListNodeV2 => node match {
                case rootNode: ListRootNodeV2 => listRootNodeV2Writer(rootNode)
                case childNode: ListChildNodeV2 => listChildNodeV2Writer(childNode)
            }
        }.asJava

        json.put("@type", "ItemList")

        json.put("http://schema.org/numberOfItems", new lang.Integer(items.size))

        json.put("http://schema.org/itemListElement", listsSeq)

        val compacted: util.Map[IRI, AnyRef] = JsonLdProcessor.compact(json, context, new JsonLdOptions())

        JsonUtils.toPrettyString(compacted)
    }

    /**
      *  Returns an object which will be part of an JSON-LD string.
      *
      * @param node
      * @return
      */
    private def listRootNodeV2Writer(node: ListRootNodeV2): Object = {

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

            result.put(OntologyConstants.Rdfs.Label, labels)
        }

        if (node.comments.nonEmpty) {

            val comments: util.List[Object] = node.comments.map {
                comment: StringV2 => comment.toObject
            }.asJava

            result.put(OntologyConstants.Rdfs.Comment, comments)
        }

        if (node.children.nonEmpty) {

            val nodes: util.List[Object] = node.children.map {
                child: ListChildNodeV2 => listChildNodeV2Writer(child)
            }.asJava

            result.put(OntologyConstants.KnoraBase.HasSubListNode, nodes)
        }

        result
    }

    /**
      * Returns an object which will be part of an JSON-LD string.
      *
      * @param node
      * @return
      */
    private def listChildNodeV2Writer(node: ListChildNodeV2): Object = {

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

            result.put(OntologyConstants.Rdfs.Label, labels)
        }

        if (node.comments.nonEmpty) {

            val comments: util.List[Object] = node.comments.map {
                comment: StringV2 => comment.toObject
            }.asJava

            result.put(OntologyConstants.Rdfs.Comment, comments)
        }

        if (node.children.nonEmpty) {
            val nodes: util.List[Object] = node.children.map {
                child: ListChildNodeV2 => listChildNodeV2Writer(child)
            }.asJava

            result.put(OntologyConstants.KnoraBase.HasSubListNode, nodes)
        }

        if (node.position.nonEmpty) {
            result.put(OntologyConstants.KnoraBase.ListNodePosition, new lang.Integer(node.position.get))
        }

        result
    }
}