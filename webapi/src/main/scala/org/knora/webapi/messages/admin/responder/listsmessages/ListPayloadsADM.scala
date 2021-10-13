package org.knora.webapi.messages.admin.responder.listsmessages

import org.knora.webapi.IRI
import org.knora.webapi.messages.admin.responder.valueObjects.{Comments, Labels, Name, Position}
import org.knora.webapi.messages.store.triplestoremessages.StringLiteralV2

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
    id: Option[IRI],
    parentNodeIri: Option[IRI],
    projectIri: IRI,
    name: Option[Name],
    position: Option[Position],
    labels: Labels,
    comments: Comments
  ): NodeCreatePayloadADM = new NodeCreatePayloadADM(id, parentNodeIri, projectIri, name, position, labels, comments) {}
}

sealed abstract case class NodeChangePayloadADM private (
  listIri: IRI,
  projectIri: IRI,
  hasRootNode: Option[IRI] = None,
  position: Option[Position] = None,
  name: Option[Name] = None,
  labels: Option[Labels] = None,
  comments: Option[Comments] = None
)

object NodeChangePayloadADM {
  def create(
    listIri: IRI,
    projectIri: IRI,
    hasRootNode: Option[IRI],
    position: Option[Position],
    name: Option[Name],
    labels: Option[Labels],
    comments: Option[Comments]
  ): NodeChangePayloadADM =
    new NodeChangePayloadADM(listIri, projectIri, hasRootNode, position, name, labels, comments) {}
}
