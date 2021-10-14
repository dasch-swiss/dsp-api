package org.knora.webapi.messages.admin.responder.listsmessages

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.valueObjects.{Comments, Labels, Name, Position}

//sealed trait NodeCreatePayloadADM
//object NodeCreatePayloadADM {
//  final case class CreateRootNodePayloadADM(
//    id: Option[IRI] = None,
//    parentNodeIri: Option[IRI] = None,
//    projectIri: IRI,
//    name: Option[Name] = None,
//    position: Option[Position] = None,
//    labels: Labels,
//    comments: Comments
//  ) extends NodeCreatePayloadADM
//  final case class CreateChildNodePayloadADM(
//    id: Option[IRI] = None,
//    parentNodeIri: Option[IRI] = None,
//    projectIri: IRI,
//    name: Option[Name] = None,
//    position: Option[Position] = None,
//    labels: Labels,
//    comments: Option[Comments] = None
//  ) extends NodeCreatePayloadADM
//}

/**
 * Node creation payload
 */
final case class NodeCreatePayloadADM(
  id: Option[IRI] = None,
  parentNodeIri: Option[IRI] = None,
  projectIri: IRI,
  name: Option[Name] = None,
  position: Option[Position] = None,
  labels: Labels,
  comments: Comments
)

/**
 * Node Info update payload
 */
final case class NodeInfoChangePayloadADM(
  listIri: IRI,
  projectIri: IRI,
  hasRootNode: Option[IRI] = None,
  position: Option[Position] = None,
  name: Option[Name] = None,
  labels: Option[Labels] = None,
  comments: Option[Comments] = None
)

/**
 * Node Name update payload
 */
final case class NodeNameChangePayloadADM(
  name: Name
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
  comments: Comments
)
