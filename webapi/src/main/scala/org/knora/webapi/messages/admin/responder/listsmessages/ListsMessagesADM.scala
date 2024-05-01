/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.listsmessages

import dsp.valueobjects.Iri
import org.apache.pekko.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.AdminKnoraResponse
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import spray.json.*
import zio.json.DeriveJsonCodec
import zio.json.JsonCodec
import zio.json.jsonDiscriminator

import scala.util.Try

/**
 * Responds to deletion of list node's comments by returning a success message.
 *
 * @param nodeIri         the IRI of the list that comments are deleted.
 * @param commentsDeleted contains a boolean value if comments were deleted.
 */
final case class ListNodeCommentsDeleteResponseADM(nodeIri: IRI, commentsDeleted: Boolean) extends AdminKnoraResponse
object ListNodeCommentsDeleteResponseADM {
  implicit val codec: JsonCodec[ListNodeCommentsDeleteResponseADM] =
    DeriveJsonCodec.gen[ListNodeCommentsDeleteResponseADM]
}

/**
 * Returns an information if node can be deleted (none of its nodes is used in data).
 *
 * @param listIri           the IRI of the list that is checked.
 * @param canDeleteList contains a boolean value if list node can be deleted.
 */
final case class CanDeleteListResponseADM(listIri: IRI, canDeleteList: Boolean) extends AdminKnoraResponse
object CanDeleteListResponseADM {
  implicit val codec: JsonCodec[CanDeleteListResponseADM] = DeriveJsonCodec.gen[CanDeleteListResponseADM]
}

/**
 * Represents a sequence of list info nodes.
 *
 * @param lists a [[ListRootNodeInfoADM]] sequence.
 */
final case class ListsGetResponseADM(lists: Seq[ListRootNodeInfoADM]) extends AdminKnoraResponse
object ListsGetResponseADM {
  implicit val codec: JsonCodec[ListsGetResponseADM] = DeriveJsonCodec.gen[ListsGetResponseADM]
}

@jsonDiscriminator("type")
sealed trait ListItemGetResponseADM extends AdminKnoraResponse
object ListItemGetResponseADM {
  implicit lazy val codec: JsonCodec[ListItemGetResponseADM] = DeriveJsonCodec.gen[ListItemGetResponseADM]
}

/**
 * Provides completes information about the list. The basic information (root node) and all the child nodes.
 *
 * @param list the complete list.
 */
final case class ListGetResponseADM(list: ListADM) extends ListItemGetResponseADM
object ListGetResponseADM {
  implicit lazy val codec: JsonCodec[ListGetResponseADM] = DeriveJsonCodec.gen[ListGetResponseADM]
}

/**
 * Provides completes information about the node. The basic information (child node) and all its children.
 *
 * @param node the node.
 */
final case class ListNodeGetResponseADM(node: NodeADM) extends ListItemGetResponseADM
object ListNodeGetResponseADM {
  implicit lazy val codec: JsonCodec[ListNodeGetResponseADM] = DeriveJsonCodec.gen[ListNodeGetResponseADM]
}

/**
 * Provides basic information about any node (root or child) without it's children.
 */
@jsonDiscriminator("type")
sealed trait NodeInfoGetResponseADM extends AdminKnoraResponse
object NodeInfoGetResponseADM {
  implicit lazy val codec: JsonCodec[NodeInfoGetResponseADM] = DeriveJsonCodec.gen[NodeInfoGetResponseADM]
}

/**
 * Provides basic information about a root node without it's children.
 *
 * @param listinfo the basic information about a list.
 */
final case class RootNodeInfoGetResponseADM(listinfo: ListRootNodeInfoADM) extends NodeInfoGetResponseADM
object RootNodeInfoGetResponseADM {
  implicit lazy val codec: JsonCodec[RootNodeInfoGetResponseADM] = DeriveJsonCodec.gen[RootNodeInfoGetResponseADM]
}

/**
 * Provides basic information about a child node without it's children.
 *
 * @param nodeinfo the basic information about a list node.
 */
final case class ChildNodeInfoGetResponseADM(nodeinfo: ListChildNodeInfoADM) extends NodeInfoGetResponseADM
object ChildNodeInfoGetResponseADM {
  implicit lazy val codec: JsonCodec[ChildNodeInfoGetResponseADM] = DeriveJsonCodec.gen[ChildNodeInfoGetResponseADM]
}

@jsonDiscriminator("type")
sealed trait ListItemDeleteResponseADM extends AdminKnoraResponse
object ListItemDeleteResponseADM {
  implicit lazy val codec: JsonCodec[ListItemDeleteResponseADM] = DeriveJsonCodec.gen[ListItemDeleteResponseADM]
}

/**
 * Responds to deletion of a list by returning a success message.
 *
 * @param iri the IRI of the list that is deleted.
 */
final case class ListDeleteResponseADM(iri: IRI, deleted: Boolean) extends ListItemDeleteResponseADM
object ListDeleteResponseADM {
  implicit lazy val codec: JsonCodec[ListDeleteResponseADM] = DeriveJsonCodec.gen[ListDeleteResponseADM]
}

/**
 * Responds to deletion of a child node by returning its parent node together with list of its immediate children
 * whose position is updated.
 *
 * @param node the updated parent node.
 */
final case class ChildNodeDeleteResponseADM(node: ListNodeADM) extends ListItemDeleteResponseADM
object ChildNodeDeleteResponseADM {
  implicit lazy val codec: JsonCodec[ChildNodeDeleteResponseADM] = DeriveJsonCodec.gen[ChildNodeDeleteResponseADM]
}

/**
 * Responds to change of a child node's position by returning its parent node together with list of its children.
 *
 * @param node the updated parent node.
 */
final case class NodePositionChangeResponseADM(node: ListNodeADM) extends AdminKnoraResponse
object NodePositionChangeResponseADM {
  implicit val codec: JsonCodec[NodePositionChangeResponseADM] = DeriveJsonCodec.gen[NodePositionChangeResponseADM]
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages
@jsonDiscriminator("type")
sealed trait ListItemADM
object ListItemADM {
  implicit lazy val codec: JsonCodec[ListItemADM] = DeriveJsonCodec.gen[ListItemADM]
}

final case class ListADM(listinfo: ListRootNodeInfoADM, children: Seq[ListChildNodeADM]) extends ListItemADM {

  /**
   * Sorts the whole hierarchy.
   *
   * @return a sorted [[List]].
   */
  def sorted: ListADM = this.copy(children = children.sortBy(_.position).map(_.sorted))
}
object ListADM {
  implicit lazy val codec: JsonCodec[ListADM] = DeriveJsonCodec.gen[ListADM]
}

final case class NodeADM(nodeinfo: ListChildNodeInfoADM, children: Seq[ListChildNodeADM]) extends ListItemADM {

  /**
   * Sorts the whole hierarchy.
   *
   * @return a sorted [[List]].
   */
  def sorted: NodeADM = this.copy(children = children.sortBy(_.position).map(_.sorted))
}
object NodeADM {
  implicit lazy val codec: JsonCodec[NodeADM] = DeriveJsonCodec.gen[NodeADM]
}

/**
 * Represents basic information about a list node, the information which is found in the list's root or child node.
 */
@jsonDiscriminator("type")
sealed trait ListNodeInfoADM {

  /**
   * @return The IRI of the list node.
   */
  def id: IRI

  /**
   * @return The name of the list node.
   */
  def name: Option[String]

  /**
   * @return The labels of the node in all available languages.
   */
  def labels: StringLiteralSequenceV2

  /**
   * @return  The comments attached to the node in all available languages.
   */
  def comments: StringLiteralSequenceV2

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
  def getLabelInPreferredLanguage(userLang: String, fallbackLang: String): Option[String]

  /**
   * Gets the comment in the user's preferred language.
   *
   * @param userLang     the user's preferred language.
   * @param fallbackLang language to use if comment is not available in user's preferred language.
   * @return the comment in the preferred language.
   */
  def getCommentInPreferredLanguage(userLang: String, fallbackLang: String): Option[String]

  final def hasComments: Boolean = comments.nonEmpty
}
object ListNodeInfoADM {
  implicit lazy val codec: JsonCodec[ListNodeInfoADM] = DeriveJsonCodec.gen[ListNodeInfoADM]
}

final case class ListRootNodeInfoADM(
  id: IRI,
  projectIri: IRI,
  name: Option[String] = None,
  labels: StringLiteralSequenceV2,
  comments: StringLiteralSequenceV2,
  isRootNode: Boolean = true,
) extends ListNodeInfoADM {

  /**
   * Sorts the whole hierarchy.
   *
   * @return a sorted [[ListRootNodeInfoADM]].
   */
  def sorted: ListRootNodeInfoADM =
    ListRootNodeInfoADM(
      id = id,
      projectIri = projectIri,
      name = name,
      labels = labels.sortByLanguage,
      comments = comments.sortByLanguage,
    )

  /**
   * unescapes the special characters in labels, comments, and name for comparison in tests.
   */
  def unescape: ListRootNodeInfoADM = {
    val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val unescapedLabels = stringFormatter.unescapeStringLiteralSeq(labels)

    val unescapedComments = stringFormatter.unescapeStringLiteralSeq(comments)

    val unescapedName: Option[String] = name match {
      case None        => None
      case Some(value) => Some(Iri.fromSparqlEncodedString(value))
    }

    copy(name = unescapedName, labels = unescapedLabels, comments = unescapedComments)
  }

  /**
   * Gets the label in the user's preferred language.
   *
   * @param userLang     the user's preferred language.
   * @param fallbackLang language to use if label is not available in user's preferred language.
   * @return the label in the preferred language.
   */
  def getLabelInPreferredLanguage(userLang: String, fallbackLang: String): Option[String] =
    labels.getPreferredLanguage(userLang, fallbackLang)

  /**
   * Gets the comment in the user's preferred language.
   *
   * @param userLang     the user's preferred language.
   * @param fallbackLang language to use if comment is not available in user's preferred language.
   * @return the comment in the preferred language.
   */
  def getCommentInPreferredLanguage(userLang: String, fallbackLang: String): Option[String] =
    comments.getPreferredLanguage(userLang, fallbackLang)

}
object ListRootNodeInfoADM {
  implicit lazy val codec: JsonCodec[ListRootNodeInfoADM] = DeriveJsonCodec.gen[ListRootNodeInfoADM]
}

final case class ListChildNodeInfoADM(
  id: IRI,
  name: Option[String],
  labels: StringLiteralSequenceV2,
  comments: StringLiteralSequenceV2,
  position: Int,
  hasRootNode: IRI,
) extends ListNodeInfoADM {

  /**
   * Sorts the whole hierarchy.
   *
   * @return a sorted [[ListChildNodeInfoADM]].
   */
  def sorted: ListChildNodeInfoADM =
    ListChildNodeInfoADM(
      id = id,
      name = name,
      labels = labels.sortByLanguage,
      comments = comments.sortByLanguage,
      position = position,
      hasRootNode = hasRootNode,
    )

  /**
   * unescapes the special characters in labels, comments, and name for comparison in tests.
   */
  def unescape: ListChildNodeInfoADM = {
    val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val unescapedLabels = stringFormatter.unescapeStringLiteralSeq(labels)

    val unescapedComments = stringFormatter.unescapeStringLiteralSeq(comments)

    val unescapedName: Option[String] = name match {
      case None        => None
      case Some(value) => Some(Iri.fromSparqlEncodedString(value))
    }

    copy(name = unescapedName, labels = unescapedLabels, comments = unescapedComments)
  }

  /**
   * Gets the label in the user's preferred language.
   *
   * @param userLang     the user's preferred language.
   * @param fallbackLang language to use if label is not available in user's preferred language.
   * @return the label in the preferred language.
   */
  def getLabelInPreferredLanguage(userLang: String, fallbackLang: String): Option[String] =
    labels.getPreferredLanguage(userLang, fallbackLang)

  /**
   * Gets the comment in the user's preferred language.
   *
   * @param userLang     the user's preferred language.
   * @param fallbackLang language to use if comment is not available in user's preferred language.
   * @return the comment in the preferred language.
   */
  def getCommentInPreferredLanguage(userLang: String, fallbackLang: String): Option[String] =
    comments.getPreferredLanguage(userLang, fallbackLang)
}
object ListChildNodeInfoADM {
  implicit lazy val codec: JsonCodec[ListChildNodeInfoADM] = DeriveJsonCodec.gen[ListChildNodeInfoADM]
}

/**
 * Represents a hierarchical list node.
 */
@jsonDiscriminator("type")
sealed trait ListNodeADM {

  /**
   * The IRI of the list node.
   */
  def id: IRI

  /**
   * The name of the list node.
   */
  def name: Option[String]

  /**
   * The label(s) of the list node.
   */
  def labels: StringLiteralSequenceV2

  /**
   * The comment(s) attached to the list in a specific language (if language tags are used).
   */
  def comments: StringLiteralSequenceV2

  /**
   * The list node's child nodes.
   */
  def children: Seq[ListChildNodeADM]

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
  def getLabelInPreferredLanguage(userLang: String, fallbackLang: String): Option[String]

  /**
   * Gets the comment in the user's preferred language.
   *
   * @param userLang     the user's preferred language.
   * @param fallbackLang language to use if comment is not available in user's preferred language.
   * @return the comment in the preferred language.
   */
  def getCommentInPreferredLanguage(userLang: String, fallbackLang: String): Option[String]
}
object ListNodeADM {
  implicit lazy val codec: JsonCodec[ListNodeADM] = DeriveJsonCodec.gen[ListNodeADM]
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
final case class ListRootNodeADM(
  id: IRI,
  projectIri: IRI,
  name: Option[String],
  labels: StringLiteralSequenceV2,
  comments: StringLiteralSequenceV2,
  children: Seq[ListChildNodeADM],
  isRootNode: Boolean = true,
) extends ListNodeADM {

  /**
   * Sorts the whole hierarchy.
   *
   * @return a sorted [[ListNodeADM]].
   */
  override def sorted: ListRootNodeADM =
    this.copy(
      labels = labels.sortByLanguage,
      comments = comments.sortByLanguage,
      children = children.sortBy(_.position).map(_.sorted),
    )

  /**
   * unescapes the special characters in labels, comments, and name for comparison in tests.
   */
  def unescape: ListRootNodeADM = {
    val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val unescapedLabels   = stringFormatter.unescapeStringLiteralSeq(labels)
    val unescapedComments = stringFormatter.unescapeStringLiteralSeq(comments)

    val unescapedName: Option[String] = name match {
      case None        => None
      case Some(value) => Some(Iri.fromSparqlEncodedString(value))
    }

    copy(name = unescapedName, labels = unescapedLabels, comments = unescapedComments)
  }

  /**
   * Gets the label in the user's preferred language.
   *
   * @param userLang     the user's preferred language.
   * @param fallbackLang language to use if label is not available in user's preferred language.
   * @return the label in the preferred language.
   */
  override def getLabelInPreferredLanguage(userLang: String, fallbackLang: String): Option[String] =
    labels.getPreferredLanguage(userLang, fallbackLang)

  /**
   * Gets the comment in the user's preferred language.
   *
   * @param userLang     the user's preferred language.
   * @param fallbackLang language to use if comment is not available in user's preferred language.
   * @return the comment in the preferred language.
   */
  override def getCommentInPreferredLanguage(userLang: String, fallbackLang: String): Option[String] =
    comments.getPreferredLanguage(userLang, fallbackLang)
}
object ListRootNodeADM {
  implicit lazy val codec: JsonCodec[ListRootNodeADM] = DeriveJsonCodec.gen[ListRootNodeADM]
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
final case class ListChildNodeADM(
  id: IRI,
  name: Option[String],
  labels: StringLiteralSequenceV2,
  comments: StringLiteralSequenceV2,
  position: Int,
  hasRootNode: IRI,
  children: Seq[ListChildNodeADM],
) extends ListNodeADM {

  /**
   * Sorts the whole hierarchy.
   *
   * @return a sorted [[ListNodeADM]].
   */
  override def sorted: ListChildNodeADM =
    this.copy(labels = labels.sortByLanguage, children = children.sortBy(_.position).map(_.sorted))

  /**
   * unescapes the special characters in labels, comments, and name for comparison in tests.
   */
  def unescape: ListChildNodeADM = {
    val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val unescapedLabels   = stringFormatter.unescapeStringLiteralSeq(labels)
    val unescapedComments = stringFormatter.unescapeStringLiteralSeq(comments)

    val unescapedName: Option[String] = name match {
      case Some(value) => Some(Iri.fromSparqlEncodedString(value))
      case None        => None
    }

    copy(name = unescapedName, labels = unescapedLabels, comments = unescapedComments)
  }

  /**
   * Gets the label in the user's preferred language.
   *
   * @param userLang     the user's preferred language.
   * @param fallbackLang language to use if label is not available in user's preferred language.
   * @return the label in the preferred language.
   */
  def getLabelInPreferredLanguage(userLang: String, fallbackLang: String): Option[String] =
    labels.getPreferredLanguage(userLang, fallbackLang)

  /**
   * Gets the comment in the user's preferred language.
   *
   * @param userLang     the user's preferred language.
   * @param fallbackLang language to use if comment is not available in user's preferred language.
   * @return the comment in the preferred language.
   */
  override def getCommentInPreferredLanguage(userLang: String, fallbackLang: String): Option[String] =
    comments.getPreferredLanguage(userLang, fallbackLang)
}
object ListChildNodeADM {
  implicit lazy val codec: JsonCodec[ListChildNodeADM] = DeriveJsonCodec.gen[ListChildNodeADM]
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// JSON formatting

/**
 * A spray-json protocol for generating Knora API V2 JSON providing data about lists.
 */
trait ListADMJsonProtocol extends SprayJsonSupport with DefaultJsonProtocol with TriplestoreJsonProtocol {

  implicit object ListRootNodeInfoFormat extends JsonFormat[ListRootNodeInfoADM] {

    def write(node: ListRootNodeInfoADM): JsValue =
      ListNodeInfoFormat.write(node)

    def read(value: JsValue): ListRootNodeInfoADM =
      ListNodeInfoFormat.read(value).asInstanceOf[ListRootNodeInfoADM]

  }

  implicit object ListChildNodeInfoFormat extends JsonFormat[ListChildNodeInfoADM] {

    def write(node: ListChildNodeInfoADM): JsValue =
      ListNodeInfoFormat.write(node)

    def read(value: JsValue): ListChildNodeInfoADM =
      ListNodeInfoFormat.read(value).asInstanceOf[ListChildNodeInfoADM]

  }

  implicit object ListNodeInfoFormat extends JsonFormat[ListNodeInfoADM] {

    /**
     * Converts a [[ListNodeInfoADM]] to a [[JsValue]].
     *
     * @param nodeInfo a [[ListNodeInfoADM]].
     * @return a [[JsValue]].
     */
    def write(nodeInfo: ListNodeInfoADM): JsValue =
      nodeInfo match {
        case root: ListRootNodeInfoADM =>
          if (root.name.nonEmpty) {
            JsObject(
              "id"         -> root.id.toJson,
              "projectIri" -> root.projectIri.toJson,
              "name"       -> root.name.toJson,
              "labels"     -> JsArray(root.labels.stringLiterals.map(_.toJson)),
              "comments"   -> JsArray(root.comments.stringLiterals.map(_.toJson)),
              "isRootNode" -> true.toJson,
            )
          } else {
            JsObject(
              "id"         -> root.id.toJson,
              "projectIri" -> root.projectIri.toJson,
              "labels"     -> JsArray(root.labels.stringLiterals.map(_.toJson)),
              "comments"   -> JsArray(root.comments.stringLiterals.map(_.toJson)),
              "isRootNode" -> true.toJson,
            )
          }

        case child: ListChildNodeInfoADM =>
          if (child.name.nonEmpty) {
            JsObject(
              "id"          -> child.id.toJson,
              "name"        -> child.name.toJson,
              "labels"      -> JsArray(child.labels.stringLiterals.map(_.toJson)),
              "comments"    -> JsArray(child.comments.stringLiterals.map(_.toJson)),
              "position"    -> child.position.toJson,
              "hasRootNode" -> child.hasRootNode.toJson,
            )
          } else {
            JsObject(
              "id"          -> child.id.toJson,
              "labels"      -> JsArray(child.labels.stringLiterals.map(_.toJson)),
              "comments"    -> JsArray(child.comments.stringLiterals.map(_.toJson)),
              "position"    -> child.position.toJson,
              "hasRootNode" -> child.hasRootNode.toJson,
            )
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

      val id =
        fields.getOrElse("id", throw DeserializationException("The expected field 'id' is missing.")).convertTo[String]
      val name = fields.get("name").map(_.convertTo[String])
      val labels = fields.get("labels") match {
        case Some(JsArray(values)) => values.map(_.convertTo[StringLiteralV2])
        case None                  => Seq.empty[StringLiteralV2]
        case _                     => throw DeserializationException("The expected field 'labels' is in the wrong format.")
      }

      val comments = fields.get("comments") match {
        case Some(JsArray(values)) => values.map(_.convertTo[StringLiteralV2])
        case None                  => Seq.empty[StringLiteralV2]
        case _                     => throw DeserializationException("The expected field 'comments' is in the wrong format.")
      }

      val maybePosition: Option[Int] = fields.get("position").map(_.convertTo[Int])

      val maybeHasRootNode: Option[IRI] = fields.get("hasRootNode").map(_.convertTo[String])

      val maybeIsRootNode: Option[Boolean] = fields.get("isRootNode").map(_.convertTo[Boolean])

      val isRootNode = maybeIsRootNode match {
        case Some(boolValue) => boolValue
        case None            => false
      }

      val maybeProjectIri: Option[IRI] = fields.get("projectIri").map(_.convertTo[IRI])

      if (isRootNode) {
        ListRootNodeInfoADM(
          id = id,
          projectIri = maybeProjectIri.getOrElse(throw DeserializationException("The project IRI is not defined.")),
          name = name,
          labels = StringLiteralSequenceV2(labels.toVector),
          comments = StringLiteralSequenceV2(comments.toVector),
        )
      } else {
        ListChildNodeInfoADM(
          id = id,
          name = name,
          labels = StringLiteralSequenceV2(labels.toVector),
          comments = StringLiteralSequenceV2(comments.toVector),
          position = maybePosition.getOrElse(throw DeserializationException("The position is not defined.")),
          hasRootNode = maybeHasRootNode.getOrElse(throw DeserializationException("The root node is not defined.")),
        )
      }
    }
  }

  implicit object ListRootNodeFormat extends JsonFormat[ListRootNodeADM] {
    def write(node: ListRootNodeADM): JsValue = listNodeADMFormat.write(node)
    def read(value: JsValue): ListRootNodeADM = listNodeADMFormat.read(value).asInstanceOf[ListRootNodeADM]
  }
  implicit object ListChildNodeFormat extends JsonFormat[ListChildNodeADM] {
    def write(node: ListChildNodeADM): JsValue = listNodeADMFormat.write(node)
    def read(value: JsValue): ListChildNodeADM = listNodeADMFormat.read(value).asInstanceOf[ListChildNodeADM]
  }

  implicit val listNodeADMFormat: JsonFormat[ListNodeADM] = new RootJsonFormat[ListNodeADM] {

    /**
     * Converts a [[ListNodeADM]] to a [[JsValue]].
     *
     * @param node a [[ListNodeADM]].
     * @return a [[JsValue]].
     */
    override def write(node: ListNodeADM): JsValue =
      node match {
        case root: ListRootNodeADM =>
          JsObject(
            "id"         -> root.id.toJson,
            "projectIri" -> root.projectIri.toJson,
            "name"       -> root.name.toJson,
            "labels"     -> JsArray(root.labels.stringLiterals.map(_.toJson)),
            "comments"   -> JsArray(root.comments.stringLiterals.map(_.toJson)),
            "isRootNode" -> true.toJson,
            "children"   -> JsArray(root.children.map(write).toVector),
          )

        case child: ListChildNodeADM =>
          JsObject(
            "id"          -> child.id.toJson,
            "name"        -> child.name.toJson,
            "labels"      -> JsArray(child.labels.stringLiterals.map(_.toJson)),
            "comments"    -> JsArray(child.comments.stringLiterals.map(_.toJson)),
            "position"    -> child.position.toJson,
            "hasRootNode" -> child.hasRootNode.toJson,
            "children"    -> JsArray(child.children.map(write).toVector),
          )
      }

    /**
     * Converts a [[JsValue]] to a [[ListNodeADM]].
     *
     * @param value a [[JsValue]].
     * @return a [[ListNodeADM]].
     */
    override def read(value: JsValue): ListNodeADM = {

      val fields = value.asJsObject.fields

      val id =
        fields.getOrElse("id", throw DeserializationException("The expected field 'id' is missing.")).convertTo[String]
      val name = fields.get("name").map(_.convertTo[String])
      val labels = fields.get("labels") match {
        case Some(JsArray(values)) => values.map(_.convertTo[StringLiteralV2])
        case None                  => Seq.empty[StringLiteralV2]
        case _                     => throw DeserializationException("The expected field 'labels' is in the wrong format.")
      }

      val comments = fields.get("comments") match {
        case Some(JsArray(values)) => values.map(_.convertTo[StringLiteralV2])
        case None                  => Seq.empty[StringLiteralV2]
        case _                     => throw DeserializationException("The expected field 'comments' is in the wrong format.")
      }

      val children: Seq[ListChildNodeADM] = fields.get("children") match {
        case Some(JsArray(values)) => values.map(read).map(_.asInstanceOf[ListChildNodeADM])
        case None                  => Seq.empty[ListChildNodeADM]
        case _                     => throw DeserializationException("The expected field 'children' is in the wrong format.")
      }

      val maybePosition: Option[Int] = fields.get("position").map(_.convertTo[Int])

      val maybeHasRootNode: Option[IRI] = fields.get("hasRootNode").map(_.convertTo[String])

      val maybeIsRootNode: Option[Boolean] = fields.get("isRootNode").map(_.convertTo[Boolean])

      val isRootNode = maybeIsRootNode match {
        case Some(boolValue) => boolValue
        case None            => false
      }

      val maybeProjectIri: Option[IRI] = fields.get("projectIri").map(_.convertTo[IRI])

      if (isRootNode) {
        ListRootNodeADM(
          id = id,
          projectIri = maybeProjectIri.getOrElse(throw DeserializationException("The project IRI is not defined.")),
          name = name,
          labels = StringLiteralSequenceV2(labels.toVector),
          comments = StringLiteralSequenceV2(comments.toVector),
          children = children,
        )
      } else {
        ListChildNodeADM(
          id = id,
          name = name,
          labels = StringLiteralSequenceV2(labels.toVector),
          comments = StringLiteralSequenceV2(comments.toVector),
          position = maybePosition.getOrElse(throw DeserializationException("The position is not defined.")),
          hasRootNode = maybeHasRootNode.getOrElse(throw DeserializationException("The root node is not defined.")),
          children = children,
        )
      }
    }
  }

  implicit object ListFormat extends JsonFormat[ListADM] {

    /**
     * Converts a [[ListADM]] to a [[JsValue]].
     *
     * @param list a [[ListADM]].
     * @return a [[JsValue]].
     */
    def write(list: ListADM): JsValue =
      JsObject(
        "listinfo" -> list.listinfo.toJson,
        "children" -> JsArray(list.children.map(_.toJson).toVector),
      )

    /**
     * Converts a [[JsValue]] to a [[List]].
     *
     * @param value a [[JsValue]].
     * @return a [[List]].
     */
    def read(value: JsValue): ListADM = {

      val fields = value.asJsObject.fields

      val listinfo: ListRootNodeInfoADM = fields
        .getOrElse("listinfo", throw DeserializationException("The expected field 'listinfo' is missing."))
        .convertTo[ListRootNodeInfoADM]
      val children: Seq[ListChildNodeADM] = fields.get("children") match {
        case Some(JsArray(values)) => values.map(it => ListChildNodeFormat.read(it))
        case None                  => Seq.empty[ListChildNodeADM]
        case _                     => throw DeserializationException("The expected field 'children' is in the wrong format.")
      }

      ListADM(
        listinfo = listinfo,
        children = children,
      )
    }
  }

  implicit object SublistFormat extends JsonFormat[NodeADM] {

    /**
     * Converts a [[NodeADM]] to a [[JsValue]].
     *
     * @param node a [[NodeADM]].
     * @return a [[JsValue]].
     */
    def write(node: NodeADM): JsValue =
      JsObject(
        "nodeinfo" -> node.nodeinfo.toJson,
        "children" -> JsArray(node.children.map(_.toJson).toVector),
      )

    /**
     * Converts a [[JsValue]] to a [[NodeADM]].
     *
     * @param value a [[JsValue]].
     * @return a [[NodeADM]].
     */
    def read(value: JsValue): NodeADM = {

      val fields = value.asJsObject.fields

      val nodeinfo: ListChildNodeInfoADM = fields
        .getOrElse("nodeinfo", throw DeserializationException("The expected field 'nodeinfo' is missing."))
        .convertTo[ListChildNodeInfoADM]
      val children: Seq[ListChildNodeADM] = fields.get("children") match {
        case Some(JsArray(values)) => values.map(ListChildNodeFormat.read(_))
        case None                  => Seq.empty[ListChildNodeADM]
        case _                     => throw DeserializationException("The expected field 'children' is in the wrong format.")
      }

      NodeADM(
        nodeinfo = nodeinfo,
        children = children,
      )
    }
  }

  implicit val listsGetResponseADMFormat: RootJsonFormat[ListsGetResponseADM] =
    jsonFormat(ListsGetResponseADM.apply, "lists")

  implicit val listItemGetResponseADMJsonFormat: RootJsonFormat[ListItemGetResponseADM] =
    new RootJsonFormat[ListItemGetResponseADM] {
      override def write(obj: ListItemGetResponseADM): JsValue = obj match {
        case list: ListGetResponseADM     => listGetResponseADMFormat.write(list)
        case node: ListNodeGetResponseADM => listNodeGetResponseADMFormat.write(node)
      }
      override def read(json: JsValue): ListItemGetResponseADM =
        Try(listGetResponseADMFormat.read(json))
          .getOrElse(listNodeGetResponseADMFormat.read(json))
    }
  implicit val listGetResponseADMFormat: RootJsonFormat[ListGetResponseADM] =
    jsonFormat(ListGetResponseADM.apply, "list")
  implicit val listNodeGetResponseADMFormat: RootJsonFormat[ListNodeGetResponseADM] =
    jsonFormat(ListNodeGetResponseADM.apply, "node")

  implicit val nodeInfoGetResponseADMJsonFormat: RootJsonFormat[NodeInfoGetResponseADM] =
    new RootJsonFormat[NodeInfoGetResponseADM] {
      override def write(obj: NodeInfoGetResponseADM): JsValue = obj match {
        case root: RootNodeInfoGetResponseADM  => listInfoGetResponseADMFormat.write(root)
        case node: ChildNodeInfoGetResponseADM => listNodeInfoGetResponseADMFormat.write(node)
      }
      override def read(json: JsValue): NodeInfoGetResponseADM =
        Try(listInfoGetResponseADMFormat.read(json))
          .getOrElse(listNodeInfoGetResponseADMFormat.read(json))
    }
  implicit val listInfoGetResponseADMFormat: RootJsonFormat[RootNodeInfoGetResponseADM] =
    jsonFormat(RootNodeInfoGetResponseADM.apply, "listinfo")
  implicit val listNodeInfoGetResponseADMFormat: RootJsonFormat[ChildNodeInfoGetResponseADM] =
    jsonFormat(ChildNodeInfoGetResponseADM.apply, "nodeinfo")

  implicit val changeNodePositionApiResponseADMFormat: RootJsonFormat[NodePositionChangeResponseADM] =
    jsonFormat(NodePositionChangeResponseADM.apply, "node")

  implicit val listItemDeleteResponseADMFormat: RootJsonFormat[ListItemDeleteResponseADM] =
    new RootJsonFormat[ListItemDeleteResponseADM] {
      override def write(obj: ListItemDeleteResponseADM): JsValue = obj match {
        case list: ListDeleteResponseADM      => listDeleteResponseADMFormat.write(list)
        case node: ChildNodeDeleteResponseADM => listNodeDeleteResponseADMFormat.write(node)
      }
      override def read(json: JsValue): ListItemDeleteResponseADM =
        Try(listDeleteResponseADMFormat.read(json))
          .getOrElse(listNodeDeleteResponseADMFormat.read(json))
    }
  implicit val listNodeDeleteResponseADMFormat: RootJsonFormat[ChildNodeDeleteResponseADM] =
    jsonFormat(ChildNodeDeleteResponseADM.apply, "node")
  implicit val listDeleteResponseADMFormat: RootJsonFormat[ListDeleteResponseADM] =
    jsonFormat(ListDeleteResponseADM.apply, "iri", "deleted")

  implicit val canDeleteListResponseADMFormat: RootJsonFormat[CanDeleteListResponseADM] =
    jsonFormat(CanDeleteListResponseADM.apply, "listIri", "canDeleteList")
  implicit val ListNodeCommentsDeleteResponseADMFormat: RootJsonFormat[ListNodeCommentsDeleteResponseADM] =
    jsonFormat(ListNodeCommentsDeleteResponseADM.apply, "nodeIri", "commentsDeleted")
}
