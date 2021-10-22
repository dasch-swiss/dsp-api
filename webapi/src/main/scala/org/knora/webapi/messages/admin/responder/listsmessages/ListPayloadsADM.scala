package org.knora.webapi.messages.admin.responder.listsmessages

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.valueObjects.{
  Comments,
  CustomID,
  Labels,
  ListName,
  Position,
  ProjectIRI
}

/**
 * List (parent node, former root node) and Node (former child node) creation payloads
 */
sealed trait NodeCreatePayloadADM
object NodeCreatePayloadADM {
  final case class ListCreatePayloadADM(
    id: Option[CustomID] = None,
    projectIri: ProjectIRI,
    name: Option[ListName] = None,
    labels: Labels,
    comments: Comments
  ) extends NodeCreatePayloadADM
  final case class ChildNodeCreatePayloadADM(
    id: Option[CustomID] = None,
    parentNodeIri: Option[IRI] = None,
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
  listIri: IRI,
  projectIri: ProjectIRI,
  hasRootNode: Option[IRI] = None,
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
//  TODO: remove Option here
  comments: Option[Comments] = None
)
