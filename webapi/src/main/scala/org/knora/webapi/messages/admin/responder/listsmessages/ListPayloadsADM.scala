package org.knora.webapi.messages.admin.responder.listsmessages

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.valueObjects.{Comments, Labels, Name, Position}

///**
// * List payload
// */
//sealed abstract case class ListCreatePayloadADM private (
//  id: Option[IRI] = None,
//  projectIri: IRI,
//  name: Option[Name] = None,
//  labels: Labels,
//  comments: Comments
//)
//
//object ListCreatePayloadADM {
//  def create(
//    id: Option[IRI],
//    projectIri: IRI,
//    name: Option[Name],
//    labels: Labels,
//    comments: Comments
//  ): ListCreatePayloadADM = new ListCreatePayloadADM(id, projectIri, name, labels, comments) {}
//}
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
sealed abstract case class NodeCreatePayloadADM private (
  id: Option[IRI] = None,
  parentNodeIri: Option[IRI] = None,
  projectIri: IRI,
  name: Option[Name] = None,
  position: Option[Position] = None,
  labels: Labels,
  comments: Comments
)

object NodeCreatePayloadADM {
  def create(
    id: Option[IRI] = None,
    parentNodeIri: Option[IRI] = None,
    projectIri: IRI,
    name: Option[Name] = None,
    position: Option[Position] = None,
    labels: Labels,
    comments: Comments
  ): NodeCreatePayloadADM = new NodeCreatePayloadADM(id, parentNodeIri, projectIri, name, position, labels, comments) {}
}

final case class ChangeNodeInfoPayloadADM(
  listIri: IRI,
  projectIri: IRI,
  hasRootNode: Option[IRI] = None,
  position: Option[Position] = None,
  name: Option[Name] = None,
  labels: Option[Labels] = None,
  comments: Option[Comments] = None
)

final case class ChangeNodeNamePayloadADM(
  name: Name
)

final case class ChangeNodeLabelsPayloadADM(
  labels: Labels
)

final case class ChangeNodeCommentsPayloadADM(
  comments: Comments
)
