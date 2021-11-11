package org.knora.webapi.messages.admin.responder.listsmessages

import org.knora.webapi.messages.admin.responder.valueObjects._

/**
 * List root node and child node creation payloads
 */
sealed trait ListNodeCreatePayloadADM
object ListNodeCreatePayloadADM {
  final case class ListRootNodeCreatePayloadADM(
    id: Option[ListIRI] = None,
    projectIri: ProjectIRI,
    name: Option[ListName] = None,
    labels: Labels,
    comments: Comments
  ) extends ListNodeCreatePayloadADM
  final case class ListChildNodeCreatePayloadADM(
    id: Option[ListIRI] = None,
//    TODO-mpro: lack of consistency between parentNodeIri and hasRootNode, should be renamed to hasParent
//    TODO-mpro: making parentNodeIri required didn't bring much, consider adding separate sparql model for child creation
    parentNodeIri: ListIRI,
    projectIri: ProjectIRI,
    name: Option[ListName] = None,
    position: Option[Position] = None,
    labels: Labels,
    comments: Option[Comments] = None
  ) extends ListNodeCreatePayloadADM
}

/**
 * List node update payload
 */
final case class ListNodeChangePayloadADM(
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
