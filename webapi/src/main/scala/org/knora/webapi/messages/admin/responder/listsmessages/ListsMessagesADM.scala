/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.admin.responder.listsmessages

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import org.knora.webapi._
import org.knora.webapi.exceptions.BadRequestException
import org.knora.webapi.feature.FeatureFactoryConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.KnoraRequestADM
import org.knora.webapi.messages.admin.responder.KnoraResponseADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCreatePayloadADM.ListChildNodeCreatePayloadADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCreatePayloadADM.ListRootNodeCreatePayloadADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListsErrorMessagesADM._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralSequenceV2
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2
import org.knora.webapi.messages.store.triplestoremessages.TriplestoreJsonProtocol
import spray.json._

import java.util.UUID
import org.knora.webapi.messages.admin.responder.valueObjects.Comments

/////////////// API requests

/**
 * Represents an API request payload that asks the Knora API server to create a new list root node.
 * At least one label and comment need to be supplied.
 *
 * @param id            the optional custom IRI of the list node.
 * @param projectIri    the IRI of the project.
 * @param name          the optional name of the list node.
 * @param labels        labels of the list node.
 * @param comments      comments of the list node.
 */
case class ListRootNodeCreateApiRequestADM(
  id: Option[IRI] = None,
  projectIri: IRI,
  name: Option[String] = None,
  labels: Seq[StringLiteralV2],
  comments: Seq[StringLiteralV2]
) extends ListADMJsonProtocol {
  def toJsValue: JsValue = createListRootNodeApiRequestADMFormat.write(this)
}

/**
 * Represents an API request payload that asks the Knora API server to create a new list child node.
 * attached to given parent node as a sublist node.
 * If a specific position is given, insert the child node there. Otherwise, the newly created list node will be appended
 * to the end of the list of children.
 * At least one label needs to be supplied.
 *
 * @param id            the optional custom IRI of the list node.
 * @param parentNodeIri the optional IRI of the parent node.
 * @param projectIri    the IRI of the project.
 * @param name          the optional name of the list node.
 * @param position      the optional position of the node.
 * @param labels        labels of the list node.
 * @param comments      comments of the list node.
 */
case class ListChildNodeCreateApiRequestADM(
  id: Option[IRI] = None,
  parentNodeIri: IRI,
  projectIri: IRI,
  name: Option[String] = None,
  position: Option[Int] = None,
  labels: Seq[StringLiteralV2],
  comments: Option[Seq[StringLiteralV2]]
) extends ListADMJsonProtocol {
  def toJsValue: JsValue = createListChildNodeApiRequestADMFormat.write(this)
}

/**
 * Represents an API request payload that asks the Knora API server to update an existing node's basic information (root or child).
 *
 * @param listIri     the IRI of the node to change.
 * @param projectIri  the IRI of the project the list belongs to.
 * @param hasRootNode the flag to identify a child node.
 * @param position    the position of the node, if not a root node.
 * @param name        the name of the node
 * @param labels      the labels.
 * @param comments    the comments.
 */
case class ListNodeChangeApiRequestADM(
  listIri: IRI,
  projectIri: IRI,
  hasRootNode: Option[IRI] = None,
  position: Option[Int] = None,
  name: Option[String] = None,
  labels: Option[Seq[StringLiteralV2]] = None,
  comments: Option[Seq[StringLiteralV2]] = None
) extends ListADMJsonProtocol {
  def toJsValue: JsValue = changeListInfoApiRequestADMFormat.write(this)
}

/**
 * Represents an API request payload that asks the Knora API server to update an existing node's name (root or child).
 *
 * @param name the new name of the node.
 */
case class ChangeNodeNameApiRequestADM(name: String) extends ListADMJsonProtocol {

  def toJsValue: JsValue = changeNodeNameApiRequestADMFormat.write(this)
}

/**
 * Represents an API request payload that asks the Knora API server to update an existing node's labels (root or child).
 *
 * @param labels the new labels of the node
 */
case class ChangeNodeLabelsApiRequestADM(labels: Seq[StringLiteralV2]) extends ListADMJsonProtocol {

  def toJsValue: JsValue = changeNodeLabelsApiRequestADMFormat.write(this)
}

/**
 * Represents an API request payload that asks the Knora API server to update an existing node's comments (root or child).
 *
 * @param comments the new comments of the node.
 */
case class ChangeNodeCommentsApiRequestADM(comments: Seq[StringLiteralV2]) extends ListADMJsonProtocol {

  def toJsValue: JsValue = changeNodeCommentsApiRequestADMFormat.write(this)
}

/**
 * Represents an API request payload that asks the Knora API server to update the position of child node.
 *
 * @param position  the new position of the node.
 * @param parentIri the parent node Iri.
 */
case class ChangeNodePositionApiRequestADM(position: Int, parentIri: IRI) extends ListADMJsonProtocol {
  private val stringFormatter = StringFormatter.getInstanceForConstantOntologies

  if (parentIri.isEmpty) {
    throw BadRequestException(s"IRI of parent node is missing.")
  }
  if (!stringFormatter.isKnoraListIriStr(parentIri)) {
    throw BadRequestException(s"Invalid IRI is given: $parentIri.")
  }

  if (position < -1) {
    throw BadRequestException(INVALID_POSITION)
  }
  def toJsValue: JsValue = changeNodePositionApiRequestADMFormat.write(this)
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
 * @param projectIri           the IRI of the project.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class ListsGetRequestADM(
  projectIri: Option[IRI] = None,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM
) extends ListsResponderRequestADM

/**
 * Requests a node (root or child). A successful response will be a [[ListItemGetResponseADM]]
 *
 * @param iri                  the IRI of the node (root or child).
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class ListGetRequestADM(iri: IRI, featureFactoryConfig: FeatureFactoryConfig, requestingUser: UserADM)
    extends ListsResponderRequestADM

/**
 * Request basic information about a node (root or child). A successful response will be a [[NodeInfoGetResponseADM]]
 *
 * @param iri                  the IRI of the list node.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class ListNodeInfoGetRequestADM(iri: IRI, featureFactoryConfig: FeatureFactoryConfig, requestingUser: UserADM)
    extends ListsResponderRequestADM

/**
 * Requests the path from the root node of a list to a particular node. A successful response will be
 * a [[NodePathGetResponseADM]].
 *
 * @param iri                  the IRI of the node.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class NodePathGetRequestADM(iri: IRI, featureFactoryConfig: FeatureFactoryConfig, requestingUser: UserADM)
    extends ListsResponderRequestADM

/**
 * Requests the creation of a new list.
 *
 * @param createRootNode       the [[ListRootNodeCreatePayloadADM]] information used for creating the root node of the list.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user creating the new list.
 * @param apiRequestID         the ID of the API request.
 */
case class ListRootNodeCreateRequestADM(
  createRootNode: ListRootNodeCreatePayloadADM,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends ListsResponderRequestADM

/**
 * Request updating basic information of an existing node.
 *
 * @param listIri              the IRI of the node to be updated (root or child ).
 * @param changeNodeRequest    the data which needs to be update.
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user initiating the request.
 * @param apiRequestID         the ID of the API request.
 */
case class NodeInfoChangeRequestADM(
  listIri: IRI,
  changeNodeRequest: ListNodeChangePayloadADM,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends ListsResponderRequestADM

/**
 * Request the creation of a new list node, root or child.
 *
 * @param createChildNodeRequest the new node information.
 * @param featureFactoryConfig   the feature factory configuration.
 * @param requestingUser         the user making the request.
 * @param apiRequestID           the ID of the API request.
 */
case class ListChildNodeCreateRequestADM(
  createChildNodeRequest: ListChildNodeCreatePayloadADM,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends ListsResponderRequestADM

/**
 * Request updating the name of an existing node.
 *
 * @param nodeIri               the IRI of the node whose name should be updated.
 * @param changeNodeNameRequest the payload containing the new name.
 * @param featureFactoryConfig  the feature factory configuration.
 * @param requestingUser        the user initiating the request.
 * @param apiRequestID          the ID of the API request.
 */
case class NodeNameChangeRequestADM(
  nodeIri: IRI,
  changeNodeNameRequest: NodeNameChangePayloadADM,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends ListsResponderRequestADM

/**
 * Request updating the labels of an existing node.
 *
 * @param nodeIri                 the IRI of the node whose name should be updated.
 * @param changeNodeLabelsRequest the payload containing the new labels.
 * @param featureFactoryConfig    the feature factory configuration.
 * @param requestingUser          the user initiating the request.
 * @param apiRequestID            the ID of the API request.
 */
case class NodeLabelsChangeRequestADM(
  nodeIri: IRI,
  changeNodeLabelsRequest: NodeLabelsChangePayloadADM,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends ListsResponderRequestADM

/**
 * Request updating the comments of an existing node.
 *
 * @param nodeIri                   the IRI of the node whose name should be updated.
 * @param changeNodeCommentsRequest the payload containing the new comments.
 * @param featureFactoryConfig      the feature factory configuration.
 * @param requestingUser            the user initiating the request.
 * @param apiRequestID              the ID of the API request.
 */
case class NodeCommentsChangeRequestADM(
  nodeIri: IRI,
  changeNodeCommentsRequest: NodeCommentsChangePayloadADM,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends ListsResponderRequestADM

/**
 * Request updating the position of an existing node.
 *
 * @param nodeIri                   the IRI of the node whose position should be updated.
 * @param changeNodePositionRequest the payload containing the new comments.
 * @param featureFactoryConfig      the feature factory configuration.
 * @param requestingUser            the user initiating the request.
 * @param apiRequestID              the ID of the API request.
 */
case class NodePositionChangeRequestADM(
  nodeIri: IRI,
  changeNodePositionRequest: ChangeNodePositionApiRequestADM,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends ListsResponderRequestADM

/**
 * Requests deletion of a node (root or child). A successful response will be a [[ListDeleteResponseADM]]
 *
 * @param nodeIri              the IRI of the node (root or child).
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class ListItemDeleteRequestADM(
  nodeIri: IRI,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM,
  apiRequestID: UUID
) extends ListsResponderRequestADM

/**
 * Request checks if a list is unused and can be deleted. A successful response will be a [[CanDeleteListResponseADM]]
 *
 * @param iri                  the IRI of the list node (root or child).
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class CanDeleteListRequestADM(iri: IRI, featureFactoryConfig: FeatureFactoryConfig, requestingUser: UserADM)
    extends ListsResponderRequestADM

/**
 * Request deletes a list comments. A successful response will be a [[ListChildNodeCommentsDeleteADM]]
 *
 * @param iri                  the IRI of the list node (root or child).
 * @param featureFactoryConfig the feature factory configuration.
 * @param requestingUser       the user making the request.
 */
case class ListChildNodeCommentsDeleteRequestADM(
  iri: IRI,
  featureFactoryConfig: FeatureFactoryConfig,
  requestingUser: UserADM
) extends ListsResponderRequestADM

///////////////////////// Responses

/**
 * Responds to deletion of a list comments by returning a success message.
 *
 * @param nodeIri the IRI of the list that comments are deleted.
 * @param commentsDeleted boolean message if comments were deleted.
 */
case class ListChildNodeCommentsDeleteResponseADM(nodeIri: IRI, commentsDeleted: Boolean)
    extends KnoraResponseADM
    with ListADMJsonProtocol {
  def toJsValue: JsValue = listChildNodeCommentsDeleteResponseADMFormat.write(this)
}

/**
 * Checks if a list can be deleted (none of its nodes is used in data).
 *
 * @param iri the IRI of the list that is checked.
 * @param canDeleteList boolean message if list can be deleted.
 */
case class CanDeleteListResponseADM(listIri: IRI, canDeleteList: Boolean)
    extends KnoraResponseADM
    with ListADMJsonProtocol {

  def toJsValue: JsValue = canDeleteListResponseADMFormat.write(this)
}

/**
 * Represents a sequence of list info nodes.
 *
 * @param lists a [[ListRootNodeInfoADM]] sequence.
 */
case class ListsGetResponseADM(lists: Seq[ListNodeInfoADM]) extends KnoraResponseADM with ListADMJsonProtocol {
  def toJsValue: JsValue = listsGetResponseADMFormat.write(this)
}

abstract class ListItemGetResponseADM(listItem: ListItemADM) extends KnoraResponseADM with ListADMJsonProtocol

/**
 * Provides completes information about the list. The basic information (rood node) and all the child nodes.
 *
 * @param list the complete list.
 */
case class ListGetResponseADM(list: ListADM) extends ListItemGetResponseADM(list) {

  def toJsValue: JsValue = listGetResponseADMFormat.write(this)
}

/**
 * Provides completes information about the node. The basic information (child node) and all its children.
 *
 * @param node the node.
 */
case class ListNodeGetResponseADM(node: NodeADM) extends ListItemGetResponseADM(node) {

  def toJsValue: JsValue = listNodeGetResponseADMFormat.write(this)
}

/**
 * Provides basic information about any node (root or child) without it's children.
 *
 * @param nodeinfo the basic information about a node.
 */
abstract class NodeInfoGetResponseADM(nodeinfo: ListNodeInfoADM) extends KnoraResponseADM with ListADMJsonProtocol

/**
 * Provides basic information about a root node without it's children.
 *
 * @param listinfo the basic information about a list.
 */
case class RootNodeInfoGetResponseADM(listinfo: ListRootNodeInfoADM) extends NodeInfoGetResponseADM(listinfo) {

  def toJsValue: JsValue = listInfoGetResponseADMFormat.write(this)
}

/**
 * Provides basic information about a child node without it's children.
 *
 * @param nodeinfo the basic information about a list node.
 */
case class ChildNodeInfoGetResponseADM(nodeinfo: ListChildNodeInfoADM) extends NodeInfoGetResponseADM(nodeinfo) {

  def toJsValue: JsValue = listNodeInfoGetResponseADMFormat.write(this)
}

/**
 * Responds to a [[NodePathGetRequestADM]] by providing the path to a particular hierarchical list node.
 *
 * @param elements a list of the nodes composing the path from the list's root node up to and including the specified node.
 */
case class NodePathGetResponseADM(elements: Seq[NodePathElementADM]) extends KnoraResponseADM with ListADMJsonProtocol {

  def toJsValue: JsValue = nodePathGetResponseADMFormat.write(this)
}

abstract class ListItemDeleteResponseADM extends KnoraResponseADM with ListADMJsonProtocol

/**
 * Responds to deletion of a list by returning a success message.
 *
 * @param iri the IRI of the list that is deleted.
 */
case class ListDeleteResponseADM(iri: IRI, deleted: Boolean) extends ListItemDeleteResponseADM {

  def toJsValue: JsValue = listDeleteResponseADMFormat.write(this)
}

/**
 * Responds to deletion of a child node by returning its parent node together with list of its immediate children
 * whose position is updated.
 *
 * @param node the updated parent node.
 */
case class ChildNodeDeleteResponseADM(node: ListNodeADM) extends ListItemDeleteResponseADM {

  def toJsValue: JsValue = listNodeDeleteResponseADMFormat.write(this)
}

/**
 * Responds to change of a child node's position by returning its parent node together with list of its children.
 *
 * @param node the updated parent node.
 */
case class NodePositionChangeResponseADM(node: ListNodeADM) extends KnoraResponseADM with ListADMJsonProtocol {

  def toJsValue: JsValue = changeNodePositionApiResponseADMFormat.write(this)
}

//////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Components of messages
abstract class ListItemADM(info: ListNodeInfoADM, children: Seq[ListChildNodeADM])

case class ListADM(listinfo: ListRootNodeInfoADM, children: Seq[ListChildNodeADM])
    extends ListItemADM(listinfo, children) {

  /**
   * Sorts the whole hierarchy.
   *
   * @return a sorted [[List]].
   */
  def sorted: ListADM =
    ListADM(
      listinfo = listinfo,
      children = children.sortBy(_.position).map(_.sorted)
    )
}

case class NodeADM(nodeinfo: ListChildNodeInfoADM, children: Seq[ListChildNodeADM])
    extends ListItemADM(nodeinfo, children) {

  /**
   * Sorts the whole hierarchy.
   *
   * @return a sorted [[List]].
   */
  def sorted: NodeADM =
    NodeADM(
      nodeinfo = nodeinfo,
      children = children.sortBy(_.position).map(_.sorted)
    )
}

/**
 * Represents basic information about a list node, the information which is found in the list's root or child node.
 *
 * @param id       the IRI of the list.
 * @param name     the name of the list node.
 * @param labels   the labels of the node in all available languages.
 * @param comments the comments attached to the node in all available languages.
 */
abstract class ListNodeInfoADM(
  id: IRI,
  name: Option[String],
  labels: StringLiteralSequenceV2,
  comments: StringLiteralSequenceV2
) {

  /**
   * Sorts the whole hierarchy.
   *
   * @return a sorted [[ListNodeInfoADM]].
   */
  def sorted: ListNodeInfoADM

  def getName: Option[String] = name

  def getLabels: StringLiteralSequenceV2 = labels

  def getComments: StringLiteralSequenceV2 = comments

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

case class ListRootNodeInfoADM(
  id: IRI,
  projectIri: IRI,
  name: Option[String] = None,
  labels: StringLiteralSequenceV2,
  comments: StringLiteralSequenceV2
) extends ListNodeInfoADM(id, name, labels, comments) {

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
      labels = labels.sortByStringValue,
      comments = comments.sortByStringValue
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
      case Some(value) => Some(stringFormatter.fromSparqlEncodedString(value))
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

case class ListChildNodeInfoADM(
  id: IRI,
  name: Option[String],
  labels: StringLiteralSequenceV2,
  comments: StringLiteralSequenceV2,
  position: Int,
  hasRootNode: IRI
) extends ListNodeInfoADM(id, name, labels, comments) {

  /**
   * Sorts the whole hierarchy.
   *
   * @return a sorted [[ListChildNodeInfoADM]].
   */
  def sorted: ListChildNodeInfoADM =
    ListChildNodeInfoADM(
      id = id,
      name = name,
      labels = labels.sortByStringValue,
      comments = comments,
      position = position,
      hasRootNode = hasRootNode
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
      case Some(value) => Some(stringFormatter.fromSparqlEncodedString(value))
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

/**
 * Represents a hierarchical list node.
 *
 * @param id       the IRI of the list node.
 * @param name     the name of the list node.
 * @param labels   the label(s) of the list node.
 * @param comments the comment(s) attached to the list in a specific language (if language tags are used) .
 * @param children the list node's child nodes.
 */
abstract class ListNodeADM(
  id: IRI,
  name: Option[String],
  labels: StringLiteralSequenceV2,
  comments: StringLiteralSequenceV2,
  children: Seq[ListChildNodeADM]
) {

  /**
   * Sorts the whole hierarchy.
   *
   * @return a sorted [[ListNodeADM]].
   */
  def sorted: ListNodeADM

  def getName: Option[String] = name

  def getLabels: StringLiteralSequenceV2 = labels

  def getComments: StringLiteralSequenceV2 = comments

  def getChildren: Seq[ListChildNodeADM] = children

  def getNodeId: IRI = id

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
case class ListRootNodeADM(
  id: IRI,
  projectIri: IRI,
  name: Option[String],
  labels: StringLiteralSequenceV2,
  comments: StringLiteralSequenceV2,
  children: Seq[ListChildNodeADM]
) extends ListNodeADM(id, name, labels, comments, children) {

  /**
   * Sorts the whole hierarchy.
   *
   * @return a sorted [[ListNodeADM]].
   */
  def sorted: ListRootNodeADM =
    ListRootNodeADM(
      id = id,
      projectIri = projectIri,
      name = name,
      labels = labels.sortByStringValue,
      comments = comments.sortByStringValue,
      children = children.sortBy(_.position).map(_.sorted)
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
      case Some(value) => Some(stringFormatter.fromSparqlEncodedString(value))
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
case class ListChildNodeADM(
  id: IRI,
  name: Option[String],
  labels: StringLiteralSequenceV2,
  comments: Option[StringLiteralSequenceV2],
  position: Int,
  hasRootNode: IRI,
  children: Seq[ListChildNodeADM]
) extends ListNodeADM(
      id,
      name,
      labels,
      comments = comments.getOrElse(StringLiteralSequenceV2(Vector.empty[StringLiteralV2])),
      children
    ) {

  /**
   * Sorts the whole hierarchy.
   *
   * @return a sorted [[ListNodeADM]].
   */
  def sorted: ListChildNodeADM =
    ListChildNodeADM(
      id = id,
      name = name,
      labels = labels.sortByStringValue,
      comments = comments,
      position = position,
      hasRootNode = hasRootNode,
      children = children.sortBy(_.position).map(_.sorted)
    )

  /**
   * unescapes the special characters in labels, comments, and name for comparison in tests.
   */
  def unescape: ListChildNodeADM = {
    val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    val unescapedLabels = stringFormatter.unescapeStringLiteralSeq(labels)
    val unescapedComments = comments match {
      case Some(value) => Some(stringFormatter.unescapeStringLiteralSeq(value))
      case None        => None
    }

    val unescapedName: Option[String] = name match {
      case Some(value) => Some(stringFormatter.fromSparqlEncodedString(value))
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
  def getCommentInPreferredLanguage(userLang: String, fallbackLang: String): Option[String] =
    comments match {
      case Some(value) => value.getPreferredLanguage(userLang, fallbackLang)
      case None        => None
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
case class NodePathElementADM(
  id: IRI,
  name: Option[String],
  labels: StringLiteralSequenceV2,
  comments: StringLiteralSequenceV2
)

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
              "isRootNode" -> true.toJson
            )
          } else {
            JsObject(
              "id"         -> root.id.toJson,
              "projectIri" -> root.projectIri.toJson,
              "labels"     -> JsArray(root.labels.stringLiterals.map(_.toJson)),
              "comments"   -> JsArray(root.comments.stringLiterals.map(_.toJson)),
              "isRootNode" -> true.toJson
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
              "hasRootNode" -> child.hasRootNode.toJson
            )
          } else {
            JsObject(
              "id"          -> child.id.toJson,
              "labels"      -> JsArray(child.labels.stringLiterals.map(_.toJson)),
              "comments"    -> JsArray(child.comments.stringLiterals.map(_.toJson)),
              "position"    -> child.position.toJson,
              "hasRootNode" -> child.hasRootNode.toJson
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

    def write(node: ListRootNodeADM): JsValue =
      ListNodeFormat.write(node)

    def read(value: JsValue): ListRootNodeADM =
      ListNodeFormat.read(value).asInstanceOf[ListRootNodeADM]

  }

  implicit object ListChildNodeFormat extends JsonFormat[ListChildNodeADM] {

    def write(node: ListChildNodeADM): JsValue =
      ListNodeFormat.write(node)

    def read(value: JsValue): ListChildNodeADM =
      ListNodeFormat.read(value).asInstanceOf[ListChildNodeADM]

  }

  implicit object ListNodeFormat extends JsonFormat[ListNodeADM] {

    /**
     * Converts a [[ListNodeADM]] to a [[JsValue]].
     *
     * @param node a [[ListNodeADM]].
     * @return a [[JsValue]].
     */
    def write(node: ListNodeADM): JsValue =
      node match {
        case root: ListRootNodeADM =>
          JsObject(
            "id"         -> root.id.toJson,
            "projectIri" -> root.projectIri.toJson,
            "name"       -> root.name.toJson,
            "labels"     -> JsArray(root.labels.stringLiterals.map(_.toJson)),
            "comments"   -> JsArray(root.comments.stringLiterals.map(_.toJson)),
            "isRootNode" -> true.toJson,
            "children"   -> JsArray(root.children.map(write).toVector)
          )

        case child: ListChildNodeADM =>
          JsObject(
            "id"     -> child.id.toJson,
            "name"   -> child.name.toJson,
            "labels" -> JsArray(child.labels.stringLiterals.map(_.toJson)),
            "comments" -> JsArray(
              child.comments
                .getOrElse(StringLiteralSequenceV2(Vector.empty[StringLiteralV2]))
                .stringLiterals
                .map(_.toJson)
            ),
            "position"    -> child.position.toJson,
            "hasRootNode" -> child.hasRootNode.toJson,
            "children"    -> JsArray(child.children.map(write).toVector)
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
          children = children
        )
      } else {
        ListChildNodeADM(
          id = id,
          name = name,
          labels = StringLiteralSequenceV2(labels.toVector),
          comments = Some(StringLiteralSequenceV2(comments.toVector)),
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
     * @param element a [[NodePathElementADM]].
     * @return a [[JsValue]].
     */
    def write(element: NodePathElementADM): JsValue =
      JsObject(
        "id"       -> element.id.toJson,
        "name"     -> element.name.toJson,
        "labels"   -> JsArray(element.labels.stringLiterals.map(_.toJson)),
        "comments" -> JsArray(element.comments.stringLiterals.map(_.toJson))
      )

    /**
     * Converts a [[JsValue]] to a [[ListNodeInfoADM]].
     *
     * @param value a [[JsValue]].
     * @return a [[ListNodeInfoADM]].
     */
    def read(value: JsValue): NodePathElementADM = {

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
    def write(list: ListADM): JsValue =
      JsObject(
        "listinfo" -> list.listinfo.toJson,
        "children" -> JsArray(list.children.map(_.toJson).toVector)
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
        case Some(JsArray(values)) => values.map(_.convertTo[ListNodeADM].asInstanceOf[ListChildNodeADM])
        case None                  => Seq.empty[ListChildNodeADM]
        case _                     => throw DeserializationException("The expected field 'children' is in the wrong format.")
      }

      ListADM(
        listinfo = listinfo,
        children = children
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
        "children" -> JsArray(node.children.map(_.toJson).toVector)
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
        case Some(JsArray(values)) => values.map(_.convertTo[ListNodeADM].asInstanceOf[ListChildNodeADM])
        case None                  => Seq.empty[ListChildNodeADM]
        case _                     => throw DeserializationException("The expected field 'children' is in the wrong format.")
      }

      NodeADM(
        nodeinfo = nodeinfo,
        children = children
      )
    }
  }

  implicit val createListRootNodeApiRequestADMFormat: RootJsonFormat[ListRootNodeCreateApiRequestADM] =
    jsonFormat(
      ListRootNodeCreateApiRequestADM,
      "id",
//      "parentNodeIri",
      "projectIri",
      "name",
//      "position",
      "labels",
      "comments"
    )
  implicit val createListChildNodeApiRequestADMFormat: RootJsonFormat[ListChildNodeCreateApiRequestADM] =
    jsonFormat(
      ListChildNodeCreateApiRequestADM,
      "id",
      "parentNodeIri",
      "projectIri",
      "name",
      "position",
      "labels",
      "comments"
    )
  implicit val changeListInfoApiRequestADMFormat: RootJsonFormat[ListNodeChangeApiRequestADM] = jsonFormat(
    ListNodeChangeApiRequestADM,
    "listIri",
    "projectIri",
    "hasRootNode",
    "position",
    "name",
    "labels",
    "comments"
  )
  implicit val nodePathGetResponseADMFormat: RootJsonFormat[NodePathGetResponseADM] =
    jsonFormat(NodePathGetResponseADM, "elements")
  implicit val listsGetResponseADMFormat: RootJsonFormat[ListsGetResponseADM] = jsonFormat(ListsGetResponseADM, "lists")
  implicit val listGetResponseADMFormat: RootJsonFormat[ListGetResponseADM]   = jsonFormat(ListGetResponseADM, "list")
  implicit val listNodeGetResponseADMFormat: RootJsonFormat[ListNodeGetResponseADM] =
    jsonFormat(ListNodeGetResponseADM, "node")
  implicit val listInfoGetResponseADMFormat: RootJsonFormat[RootNodeInfoGetResponseADM] =
    jsonFormat(RootNodeInfoGetResponseADM, "listinfo")
  implicit val listNodeInfoGetResponseADMFormat: RootJsonFormat[ChildNodeInfoGetResponseADM] =
    jsonFormat(ChildNodeInfoGetResponseADM, "nodeinfo")
  implicit val changeNodeNameApiRequestADMFormat: RootJsonFormat[ChangeNodeNameApiRequestADM] =
    jsonFormat(ChangeNodeNameApiRequestADM, "name")
  implicit val changeNodeLabelsApiRequestADMFormat: RootJsonFormat[ChangeNodeLabelsApiRequestADM] =
    jsonFormat(ChangeNodeLabelsApiRequestADM, "labels")
  implicit val changeNodeCommentsApiRequestADMFormat: RootJsonFormat[ChangeNodeCommentsApiRequestADM] =
    jsonFormat(ChangeNodeCommentsApiRequestADM, "comments")
  implicit val changeNodePositionApiRequestADMFormat: RootJsonFormat[ChangeNodePositionApiRequestADM] =
    jsonFormat(ChangeNodePositionApiRequestADM, "position", "parentNodeIri")
  implicit val changeNodePositionApiResponseADMFormat: RootJsonFormat[NodePositionChangeResponseADM] =
    jsonFormat(NodePositionChangeResponseADM, "node")
  implicit val listNodeDeleteResponseADMFormat: RootJsonFormat[ChildNodeDeleteResponseADM] =
    jsonFormat(ChildNodeDeleteResponseADM, "node")
  implicit val listDeleteResponseADMFormat: RootJsonFormat[ListDeleteResponseADM] =
    jsonFormat(ListDeleteResponseADM, "iri", "deleted")
  implicit val canDeleteListResponseADMFormat: RootJsonFormat[CanDeleteListResponseADM] =
    jsonFormat(CanDeleteListResponseADM, "listIri", "canDeleteList")
  implicit val listChildNodeCommentsDeleteResponseADMFormat: RootJsonFormat[ListChildNodeCommentsDeleteResponseADM] =
    jsonFormat(ListChildNodeCommentsDeleteResponseADM, "nodeIri", "commentsDeleted")
}
