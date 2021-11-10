package org.knora.webapi.messages.admin.responder.listsmessages

import org.knora.webapi.messages.admin.responder.valueObjects._

/**
 * List (parent node, former root node) and Node (former child node) creation payloads
 */
sealed trait NodeCreatePayloadADM
object NodeCreatePayloadADM {
//  ListRootNode
  final case class ListCreatePayloadADM(
    id: Option[ListIRI] = None,
    projectIri: ProjectIRI,
    name: Option[ListName] = None,
    labels: Labels,
    comments: Comments
  ) extends NodeCreatePayloadADM
//  ListChildNode
  final case class ChildNodeCreatePayloadADM(
    id: Option[ListIRI] = None,
//    TODO-mpro: lack of consistency between parentNodeIri and hasRootNode, should be renamed to hasParent
//      make arentNodeIri required
    parentNodeIri: Option[ListIRI] = None,
    projectIri: ProjectIRI,
    name: Option[ListName] = None,
    position: Option[Position] = None,
    labels: Labels,
    comments: Option[Comments] = None
  ) extends NodeCreatePayloadADM
}

/**
 * Node Info update payload
 */
final case class NodeInfoChangePayloadADM(
//  TODO-mpro: listIri can be probably removed
  listIri: ListIRI,
  projectIri: ProjectIRI,
  hasRootNode: Option[ListIRI] = None,
  position: Option[Position] = None,
  name: Option[ListName] = None,
  labels: Option[Labels] = None,
  comments: Option[Comments] = None
)

/**
 * Node Name update payload
 */
final case class NodeNameChangePayloadADM(
  name: ListName
)

/**
 * Node Labels update payload
 */
final case class NodeLabelsChangePayloadADM(
  labels: Labels
)

/**
 * Node Comments update payload
 */
final case class NodeCommentsChangePayloadADM(
//  TODO-mpro: remove Option here
  comments: Option[Comments] = None
)
