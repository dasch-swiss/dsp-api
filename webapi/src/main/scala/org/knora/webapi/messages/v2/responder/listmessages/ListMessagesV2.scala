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

import java.util

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
  * Requests a list of all lists or the lists inside a project.
  *
  * @param projectIri  the IRI of the project.
  * @param userProfile the profile of the user making the request.
  */
case class ListsGetRequestV2(projectIri: Option[IRI] = None,
                             userProfile: UserProfileV1) extends ListsResponderRequestV2

/**
  * Requests a list. A successful response will be a [[ListGetResponseV2]]
  *
  * @param iri         the IRI of the list.
  * @param userProfile the profile of the user making the request.
  */
case class ListGetRequestV2(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV2

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
  * Requests the path from the root node of a list to a particular node. A successful response will be
  * a [[NodePathGetResponseV2]].
  *
  * @param iri         the IRI of the node.
  * @param userProfile the profile of the user making the request.
  */
case class NodePathGetRequestV2(iri: IRI, userProfile: UserProfileV1) extends ListsResponderRequestV2


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Responses

case class ListsGetResponseV2(lists: Seq[ListInfoV2]) extends KnoraResponseV2 with ListV2JsonLDProtocol {

    def toXML: String = ???

    def toJsonLDWithValueObject(settings: SettingsImpl): String = toJsValue.toString
}

/**
  * An abstract class extended by `HListGetResponseV2` and `SelectionGetResponseV2`.
  */
sealed abstract class ListResponseV2 extends KnoraResponseV2

/**
  * Provides a information about the list and the list itself.
  *
  * @param info  the basic information about a list.
  * @param nodes the whole list.
  */
case class ListGetResponseV2(info: ListInfoV2, nodes: Seq[ListNodeV2]) extends ListResponseV2 with ListV2JsonLDProtocol {

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
case class ListInfoGetResponseV2(id: IRI, projectIri: Option[IRI], labels: Seq[StringWithOptionalLang], comments: Seq[StringWithOptionalLang]) extends KnoraResponseV2 with ListV2JsonLDProtocol {

    def toXML: String = ???

    def toJsonLDWithValueObject(settings: SettingsImpl): String = listInfoGetResponseV2FormatWriter(this, settings)
}

/**
  * Provides basic information about a list node.
  *
  * @param id       the IRI of the list node.
  * @param labels   the labels of the list in all available languages.
  * @param comments the comments of the list in all available languages.
  */
case class ListNodeInfoGetResponseV2(id: IRI, labels: Seq[StringWithOptionalLang], comments: Seq[StringWithOptionalLang]) extends KnoraResponseV2 with ListV2JsonLDProtocol {

    def toXML: String = ???

    def toJsonLDWithValueObject(settings: SettingsImpl): String = toJsValue.toString
}

/**
  * Responds to a [[NodePathGetRequestV2]] by providing the path to a particular hierarchical list node.
  *
  * @param nodelist a list of the nodes composing the path from the list's root node up to and including the specified node.
  */
case class NodePathGetResponseV2(nodelist: Seq[NodePathElementV2]) extends ListResponseV2 {

    def toXML: String = ???

    def toJsonLDWithValueObject(settings: SettingsImpl): String = toJsValue.toString
}


//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages

/**
  * Represents a list, including the basic information (root node) and the whole tree (everything under the root node).
  *
  * @param info  the basic list information.
  * @param nodes the complete list tree.
  */
case class ListV2(info: ListInfoV2, nodes: Seq[ListNodeV2])

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
  * Represents a hierarchical list node in Knora API V2 format.
  *
  * @param id       the IRI of the list node.
  * @param projectIri the IRI of the project this list belongs to (optional). Only allowed for root node.
  * @param name     the name of the list node.
  * @param labels    the label of the list node.
  * @param comments   the comments attached to the list in all available languages (if language tags are used) .
  * @param children the list node's child nodes.
  * @param position the position of the node among its siblings.
  * @param isRoot if true, denotes the root node.
  */
case class ListNodeV2(id: IRI, projectIri: Option[IRI], name: Option[String], labels: Seq[StringWithOptionalLang], comments: Seq[StringWithOptionalLang], children: Seq[ListNodeV2], position: Option[Int], isRoot: Option[Boolean])

/**
  * Represents a node on a hierarchical list path.
  *
  * @param id    the IRI of the list node.
  * @param name  the name of the list node.
  * @param label the label of the list node.
  */
case class NodePathElementV2(id: IRI, name: Option[String], label: Option[String])



/**
  * Represents information about a list node.
  *
  * @param id       the IRI of the list node.
  * @param labels   the labels of the list in all available languages.
  * @param comments the comments attached to the list in all available languages.
  */
case class ListNodeInfoV2(id: IRI, labels: Seq[StringWithOptionalLang], comments: Seq[StringWithOptionalLang])



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
  * @param value    the string value.
  * @param language the optional language tag.
  */
case class StringWithOptionalLang(value: String, language: Option[String])




//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON-LD formatting

/**
  * A json-ld protocol for generating Knora API V2 JSON-LD providing data about lists.
  */
trait ListV2JsonLDProtocol {

    def listInfoGetResponseV2FormatWriter(l: ListInfoGetResponseV2, settings: SettingsImpl): String = {

        val rdfsLabel: IRI = "http://www.w3.org/2000/01/rdf-schema#label"
        val rdfsComment: IRI = "http://www.w3.org/2000/01/rdf-schema#comment"

        val context = new util.HashMap[String, String]()
        context.put("@vocab", "http://schema.org/")
        context.put("rdfs", "http://www.w3.org/2000/01/rdf-schema#")

        val json: util.HashMap[String, Object] = new util.HashMap[String, Object]()

        val labels: util.List[Object] = l.labels.map {
            label: StringWithOptionalLang => stringWithOptionalLangV2FormatWriter(label)
        }.asJava

        val comments: util.List[Object] = l.comments.map {
            comment: StringWithOptionalLang => stringWithOptionalLangV2FormatWriter(comment)
        }.asJava

        json.put("@id", l.id)

        json.put("@type", OntologyConstants.KnoraBase.ListNode)

        json.put(OntologyConstants.KnoraBase.AttachedToProject, l.projectIri)

        json.put(rdfsLabel, labels)

        json.put(rdfsComment, comments)

        val compacted = JsonLdProcessor.compact(json, context, new JsonLdOptions())

        JsonUtils.toPrettyString(compacted)
    }

    /**
      * Returns
      * @param s a string with an optional language tag.
      * @return
      */
    def stringWithOptionalLangV2FormatWriter(s: StringWithOptionalLang): Object = {

        if (s.language.nonEmpty) {
            Map(
                "@language" -> s.language.get,
                "@value" -> s.value
            ).asJava
        } else {
            s.value
        }

    }

}