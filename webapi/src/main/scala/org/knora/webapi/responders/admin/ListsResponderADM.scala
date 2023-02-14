/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio._
import java.util.UUID
import scala.annotation.tailrec
import dsp.errors._
import dsp.valueobjects.Iri._
import dsp.valueobjects.List.ListName
import dsp.valueobjects.ListErrorMessages

import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCreatePayloadADM.ListChildNodeCreatePayloadADM
import org.knora.webapi.messages.admin.responder.listsmessages.ListNodeCreatePayloadADM.ListRootNodeCreatePayloadADM
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectGetADM
import org.knora.webapi.messages.admin.responder.projectsmessages.ProjectIdentifierADM._
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.util.KnoraSystemInstances
import org.knora.webapi.responders.EntityAndClassIriService
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.Responder
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.util.ZioHelper
import org.knora.webapi.util.ZioHelper.sequence

/**
 * A responder that returns information about lists.
 */
trait ListsResponderADM {

  /**
   * Gets all lists and returns them as a [[ListsGetResponseADM]]. For performance reasons
   * (as lists can be very large), we only return the head of the list, i.e. the root node without
   * any children.
   *
   * @param projectIri           the IRI of the project the list belongs to.
   * @param requestingUser       the user making the request.
   * @return a [[ListsGetResponseADM]].
   */
  def listsGetRequestADM(projectIri: Option[IRI], requestingUser: UserADM): Task[ListsGetResponseADM]

  /**
   * Retrieves a complete node (root or child) with all children from the triplestore and returns it as a [[ListItemGetResponseADM]].
   * If an IRI of a root node is given, the response is a list with root node info and all chilren of the list.
   * If an IRI of a child node is given, the response is a node with its information and all children of the sublist.
   *
   * @param nodeIri        the Iri if the required node.
   * @param requestingUser the user making the request.
   * @return a [[ListItemGetResponseADM]].
   */
  def listGetRequestADM(nodeIri: IRI, requestingUser: UserADM): Task[ListItemGetResponseADM]

  /**
   * Retrieves information about a single node (without information about children). The single node can be a
   * root node or child node
   *
   * @param nodeIri              the IRI of the list node to be queried.
   * @param requestingUser       the user making the request.
   * @return a [[ChildNodeInfoGetResponseADM]].
   */
  def listNodeInfoGetRequestADM(nodeIri: IRI, requestingUser: UserADM): Task[NodeInfoGetResponseADM]

  /**
   * Provides the path to a particular hierarchical list node.
   *
   * @param queryNodeIri   the IRI of the node whose path is to be queried.
   * @param requestingUser the user making the request.
   */
  def nodePathGetAdminRequest(queryNodeIri: IRI, requestingUser: UserADM): Task[NodePathGetResponseADM]

  /**
   * Creates a list.
   *
   * @param request    the new list's information.
   * @param apiRequestID         the unique api request ID.
   * @return a [[RootNodeInfoGetResponseADM]]
   */
  def listCreateRequestADM(request: ListRootNodeCreatePayloadADM, apiRequestID: UUID): Task[ListGetResponseADM]

  /**
   * Creates a new child node and appends it to an existing list node.
   *
   * @param request the new list node's information.
   * @param apiRequestID           the unique api request ID.
   * @return a [[ChildNodeInfoGetResponseADM]]
   */
  def listChildNodeCreateRequestADM(
    request: ListChildNodeCreatePayloadADM,
    apiRequestID: UUID
  ): Task[ChildNodeInfoGetResponseADM]

  /**
   * Changes basic node information stored (root or child)
   *
   * @param nodeIri              the list's IRI.
   * @param changeNodeRequest    the new node information.
   * @param apiRequestID         the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   *
   *         [[ForbiddenException]]          in the case that the user is not allowed to perform the operation.
   *
   *         [[BadRequestException]]         in the case when the list IRI given in the path does not match with the one given in the payload.
   *
   *         [[UpdateNotPerformedException]] in the case something else went wrong, and the change could not be performed.
   */
  def nodeInfoChangeRequest(
    nodeIri: IRI,
    changeNodeRequest: ListNodeChangePayloadADM,
    apiRequestID: UUID
  ): Task[NodeInfoGetResponseADM]

  /**
   * Changes name of the node (root or child)
   *
   * @param nodeIri               the node's IRI.
   * @param changeNodeNameRequest the new node name.
   * @param apiRequestID          the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   *
   *         [[ForbiddenException]]          in the case that the user is not allowed to perform the operation.
   *
   *         [[UpdateNotPerformedException]] in the case something else went wrong, and the change could not be performed.
   */
  def nodeNameChangeRequest(
    nodeIri: IRI,
    changeNodeNameRequest: NodeNameChangePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[NodeInfoGetResponseADM]

  /**
   * Changes labels of the node (root or child)
   *
   * @param nodeIri                 the node's IRI.
   * @param changeNodeLabelsRequest the new node labels.
   * @param requestingUser          the requesting user.
   * @param apiRequestID            the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   *
   *        [[ForbiddenException]]          in the case that the user is not allowed to perform the operation.
   *
   *        [[UpdateNotPerformedException]] in the case something else went wrong, and the change could not be performed.
   */
  def nodeLabelsChangeRequest(
    nodeIri: IRI,
    changeNodeLabelsRequest: NodeLabelsChangePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[NodeInfoGetResponseADM]

  /**
   * Changes comments of the node (root or child)
   *
   * @param nodeIri                   the node's IRI.
   * @param changeNodeCommentsRequest the new node comments.
   * @param requestingUser            the requesting user.
   * @param apiRequestID              the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   *
   *         [[ForbiddenException]]          in the case that the user is not allowed to perform the operation.
   *
   *         [[UpdateNotPerformedException]] in the case something else went wrong, and the change could not be performed.
   */
  def nodeCommentsChangeRequest(
    nodeIri: IRI,
    changeNodeCommentsRequest: NodeCommentsChangePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[NodeInfoGetResponseADM]

  /**
   * Changes position of the node
   *
   * @param nodeIri                   the node's IRI.
   * @param changeNodePositionRequest the new node comments.
   * @param requestingUser            the requesting user.
   * @param apiRequestID              the unique api request ID.
   * @return a [[NodePositionChangeResponseADM]]
   *
   *         [[ForbiddenException]]          in the case that the user is not allowed to perform the operation.
   *
   *         [[UpdateNotPerformedException]] in the case something else went wrong, and the change could not be performed.
   */
  def nodePositionChangeRequest(
    nodeIri: IRI,
    changeNodePositionRequest: ChangeNodePositionApiRequestADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[NodePositionChangeResponseADM]

  /**
   * Delete a node (root or child). If a root node is given, check for its usage in data and ontology. If not used,
   * delete the list and return a confirmation message.
   *
   * @param nodeIri              the node's IRI.
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   *
   *         [[ForbiddenException]]          in the case that the user is not allowed to perform the operation.
   *
   *         [[UpdateNotPerformedException]] in the case the node is in use and cannot be deleted.
   */
  def deleteListItemRequestADM(
    nodeIri: IRI,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[ListItemDeleteResponseADM]
}

case class ListsResponderADMLive(
  appConfig: AppConfig,
  iriService: EntityAndClassIriService,
  messageRelay: MessageRelay,
  triplestoreService: TriplestoreService,
  implicit val stringFormatter: StringFormatter
) extends MessageHandler
    with ListsResponderADM {

  override def handle(message: ResponderRequest): Task[Any] =
    this.receive(message.asInstanceOf[ListsResponderRequestADM])

  override def isResponsibleFor(message: ResponderRequest): Boolean = message match {
    case _: ListsResponderRequestADM => true
    case _                           => false
  }

  // The IRI used to lock user creation and update
  private val LISTS_GLOBAL_LOCK_IRI = "http://rdfh.ch/lists"

  /**
   * Receives a message of type [[ListsResponderRequestADM]], and returns an appropriate response message.
   */
  def receive(msg: ListsResponderRequestADM): Task[Any] = msg match {
    case ListsGetRequestADM(projectIri, requestingUser)     => listsGetRequestADM(projectIri, requestingUser)
    case ListGetRequestADM(listIri, requestingUser)         => listGetRequestADM(listIri, requestingUser)
    case ListNodeInfoGetRequestADM(listIri, requestingUser) => listNodeInfoGetRequestADM(listIri, requestingUser)
    case NodePathGetRequestADM(iri, requestingUser)         => nodePathGetAdminRequest(iri, requestingUser)
    case ListRootNodeCreateRequestADM(createRootNode, _, apiRequestID) =>
      listCreateRequestADM(createRootNode, apiRequestID)
    case ListChildNodeCreateRequestADM(createChildNodeRequest, _, apiRequestID) =>
      listChildNodeCreateRequestADM(createChildNodeRequest, apiRequestID)
    case NodeInfoChangeRequestADM(nodeIri, changeNodeRequest, _, apiRequestID) =>
      nodeInfoChangeRequest(nodeIri, changeNodeRequest, apiRequestID)
    case NodeNameChangeRequestADM(nodeIri, changeNodeNameRequest, requestingUser, apiRequestID) =>
      nodeNameChangeRequest(nodeIri, changeNodeNameRequest, requestingUser, apiRequestID)
    case NodeLabelsChangeRequestADM(nodeIri, changeNodeLabelsRequest, requestingUser, apiRequestID) =>
      nodeLabelsChangeRequest(nodeIri, changeNodeLabelsRequest, requestingUser, apiRequestID)
    case NodeCommentsChangeRequestADM(nodeIri, changeNodeCommentsRequest, requestingUser, apiRequestID) =>
      nodeCommentsChangeRequest(nodeIri, changeNodeCommentsRequest, requestingUser, apiRequestID)
    case NodePositionChangeRequestADM(nodeIri, changeNodePositionRequest, requestingUser, apiRequestID) =>
      nodePositionChangeRequest(nodeIri, changeNodePositionRequest, requestingUser, apiRequestID)
    case ListItemDeleteRequestADM(nodeIri, requestingUser, apiRequestID) =>
      deleteListItemRequestADM(nodeIri, requestingUser, apiRequestID)
    case CanDeleteListRequestADM(iri, _)          => canDeleteListRequestADM(iri)
    case ListNodeCommentsDeleteRequestADM(iri, _) => deleteListNodeCommentsADM(iri)
    case other                                    => Responder.handleUnexpectedMessageTask(other, this.getClass.getName)
  }

  /**
   * Gets all lists and returns them as a [[ListsGetResponseADM]]. For performance reasons
   * (as lists can be very large), we only return the head of the list, i.e. the root node without
   * any children.
   *
   * @param projectIri           the IRI of the project the list belongs to.
   * @param requestingUser       the user making the request.
   * @return a [[ListsGetResponseADM]].
   */
  override def listsGetRequestADM(projectIri: Option[IRI], requestingUser: UserADM): Task[ListsGetResponseADM] = {
    val query = org.knora.webapi.messages.twirl.queries.sparql.admin.txt
      .getLists(projectIri)
      .toString()
    for {
      listsResponse <- triplestoreService.sparqlHttpExtendedConstruct(query)
      statements     = listsResponse.statements.toList
      lists: Seq[ListNodeInfoADM] =
        statements.map { case (listIri: SubjectV2, propsMap: Map[SmartIri, Seq[LiteralV2]]) =>
          val name: Option[String] = propsMap
            .get(OntologyConstants.KnoraBase.ListNodeName.toSmartIri)
            .map(_.head.asInstanceOf[StringLiteralV2].value)
          val labels: Seq[StringLiteralV2] = propsMap
            .getOrElse(
              OntologyConstants.Rdfs.Label.toSmartIri,
              Seq.empty[StringLiteralV2]
            )
            .map(_.asInstanceOf[StringLiteralV2])
          val comments: Seq[StringLiteralV2] = propsMap
            .getOrElse(
              OntologyConstants.Rdfs.Comment.toSmartIri,
              Seq.empty[StringLiteralV2]
            )
            .map(_.asInstanceOf[StringLiteralV2])

          ListRootNodeInfoADM(
            id = listIri.toString,
            projectIri = propsMap
              .getOrElse(
                OntologyConstants.KnoraBase.AttachedToProject.toSmartIri,
                throw InconsistentRepositoryDataException(
                  "The required property 'attachedToProject' not found."
                )
              )
              .head
              .asInstanceOf[IriLiteralV2]
              .value,
            name = name,
            labels = StringLiteralSequenceV2(labels.toVector),
            comments = StringLiteralSequenceV2(comments.toVector)
          ).unescape
        }
    } yield ListsGetResponseADM(lists = lists)
  }

  /**
   * Retrieves a complete list (root and all children) from the triplestore and returns it as a optional [[ListADM]].
   *
   * @param rootNodeIri          the Iri if the root node of the list to be queried.
   * @param requestingUser       the user making the request.
   * @return a optional [[ListADM]].
   */
  private def listGetADM(
    rootNodeIri: IRI,
    requestingUser: UserADM
  ): Task[Option[ListADM]] =
    for {
      // this query will give us only the information about the root node.
      exists <- rootNodeByIriExists(rootNodeIri)

      maybeList <-
        if (exists) {
          for {
            // here we know that the list exists and it is fine if children is an empty list
            children <- getChildren(
                          ofNodeIri = rootNodeIri,
                          shallow = false,
                          KnoraSystemInstances.Users.SystemUser
                        )

            maybeRootNodeInfo <-
              listNodeInfoGetADM(
                nodeIri = rootNodeIri,
                requestingUser = KnoraSystemInstances.Users.SystemUser
              )

            rootNodeInfo = maybeRootNodeInfo match {
                             case Some(info: ListRootNodeInfoADM) => info
                             case Some(_: ListChildNodeInfoADM) =>
                               throw InconsistentRepositoryDataException(
                                 "A child node info was found, although we are expecting a root node info. Please report this as a possible bug."
                               )
                             case Some(_) | None =>
                               throw InconsistentRepositoryDataException(
                                 "No info about list node found, although list node should exist. Please report this as a possible bug."
                               )
                           }

            list = ListADM(listinfo = rootNodeInfo, children = children)
          } yield Some(list)
        } else {
          ZIO.succeed(None)
        }

    } yield maybeList

  /**
   * Retrieves a complete node (root or child) with all children from the triplestore and returns it as a [[ListItemGetResponseADM]].
   * If an IRI of a root node is given, the response is a list with root node info and all chilren of the list.
   * If an IRI of a child node is given, the response is a node with its information and all children of the sublist.
   *
   * @param nodeIri        the Iri if the required node.
   * @param requestingUser the user making the request.
   * @return a [[ListItemGetResponseADM]].
   */
  override def listGetRequestADM(nodeIri: IRI, requestingUser: UserADM): Task[ListItemGetResponseADM] = {
    def getNodeADM(
      childNode: ListChildNodeADM
    ): Task[ListNodeGetResponseADM] =
      for {
        maybeNodeInfo <- listNodeInfoGetADM(
                           nodeIri = nodeIri,
                           requestingUser = requestingUser
                         )

        nodeinfo = maybeNodeInfo match {
                     case Some(childNodeInfo: ListChildNodeInfoADM) => childNodeInfo
                     case _                                         => throw NotFoundException(s"Information not found for node '$nodeIri'")
                   }

        // make a NodeADM instance
        entirenode = ListNodeGetResponseADM(
                       node = NodeADM(
                         nodeinfo = nodeinfo,
                         children = childNode.children
                       )
                     )
      } yield entirenode

    for {
      exists <- rootNodeByIriExists(nodeIri)
      // Is root node IRI given?
      result <-
        if (exists) {
          for {
            // Yes. Get the entire list
            maybeList <- listGetADM(
                           rootNodeIri = nodeIri,
                           requestingUser = requestingUser
                         )

            entireList = maybeList match {
                           case Some(list) => ListGetResponseADM(list = list)
                           case None       => throw NotFoundException(s"List '$nodeIri' not found")
                         }
          } yield entireList
        } else {
          for {
            // No. Get the node and all its sublist children.
            // First, get node itself and all children.
            maybeNode <- listNodeGetADM(
                           nodeIri = nodeIri,
                           shallow = true,
                           requestingUser = requestingUser
                         )

            entireNode <- maybeNode match {
                            // make sure that it is a child node
                            case Some(childNode: ListChildNodeADM) =>
                              // get the info of the child node
                              getNodeADM(childNode)

                            case _ => throw NotFoundException(s"Node '$nodeIri' not found")
                          }
          } yield entireNode
        }
    } yield result
  }

  /**
   * Retrieves information about a single node (without information about children). The single node can be the
   * lists root node or child node
   *
   * @param nodeIri              the Iri if the list node to be queried.
   * @param requestingUser       the user making the request.
   * @return a optional [[ListNodeInfoADM]].
   */
  private def listNodeInfoGetADM(
    nodeIri: IRI,
    requestingUser: UserADM
  ): Task[Option[ListNodeInfoADM]] = {
    val query = org.knora.webapi.messages.twirl.queries.sparql.admin.txt
      .getListNode(nodeIri)
      .toString()
    for {
      listNodeResponse                                         <- triplestoreService.sparqlHttpExtendedConstruct(query)
      statements: Map[SubjectV2, Map[SmartIri, Seq[LiteralV2]]] = listNodeResponse.statements

      maybeListNodeInfo =
        if (statements.nonEmpty) {

          val nodeInfo: ListNodeInfoADM = statements.head match {
            case (nodeIri: SubjectV2, propsMap: Map[SmartIri, Seq[LiteralV2]]) =>
              val labels: Seq[StringLiteralV2] = propsMap
                .getOrElse(OntologyConstants.Rdfs.Label.toSmartIri, Seq.empty[StringLiteralV2])
                .map(_.asInstanceOf[StringLiteralV2])
              val comments: Seq[StringLiteralV2] = propsMap
                .getOrElse(OntologyConstants.Rdfs.Comment.toSmartIri, Seq.empty[StringLiteralV2])
                .map(_.asInstanceOf[StringLiteralV2])

              val attachedToProjectOption: Option[IRI] =
                propsMap.get(OntologyConstants.KnoraBase.AttachedToProject.toSmartIri) match {
                  case Some(iris: Seq[LiteralV2]) =>
                    iris.headOption match {
                      case Some(iri: IriLiteralV2) => Some(iri.value)
                      case other =>
                        throw InconsistentRepositoryDataException(
                          s"Expected attached to project Iri as an IriLiteralV2 for list node $nodeIri, but got $other"
                        )
                    }

                  case None => None
                }

              val hasRootNodeOption: Option[IRI] =
                propsMap.get(OntologyConstants.KnoraBase.HasRootNode.toSmartIri) match {
                  case Some(iris: Seq[LiteralV2]) =>
                    iris.headOption match {
                      case Some(iri: IriLiteralV2) => Some(iri.value)
                      case other =>
                        throw InconsistentRepositoryDataException(
                          s"Expected root node Iri as an IriLiteralV2 for list node $nodeIri, but got $other"
                        )
                    }

                  case None => None
                }

              val isRootNode: Boolean = propsMap.get(OntologyConstants.KnoraBase.IsRootNode.toSmartIri) match {
                case Some(values: Seq[LiteralV2]) =>
                  values.headOption match {
                    case Some(value: BooleanLiteralV2) => value.value
                    case Some(other) =>
                      throw InconsistentRepositoryDataException(
                        s"Expected isRootNode as an BooleanLiteralV2 for list node $nodeIri, but got $other"
                      )
                    case None => false
                  }

                case None => false
              }

              val positionOption: Option[Int] = propsMap
                .get(OntologyConstants.KnoraBase.ListNodePosition.toSmartIri)
                .map(_.head.asInstanceOf[IntLiteralV2].value)

              if (isRootNode) {
                ListRootNodeInfoADM(
                  id = nodeIri.toString,
                  projectIri = attachedToProjectOption.getOrElse(
                    throw InconsistentRepositoryDataException(
                      s"Required attachedToProject property missing for list node $nodeIri."
                    )
                  ),
                  name = propsMap
                    .get(OntologyConstants.KnoraBase.ListNodeName.toSmartIri)
                    .map(_.head.asInstanceOf[StringLiteralV2].value),
                  labels = StringLiteralSequenceV2(labels.toVector),
                  comments = StringLiteralSequenceV2(comments.toVector)
                ).unescape
              } else {
                ListChildNodeInfoADM(
                  id = nodeIri.toString,
                  name = propsMap
                    .get(OntologyConstants.KnoraBase.ListNodeName.toSmartIri)
                    .map(_.head.asInstanceOf[StringLiteralV2].value),
                  labels = StringLiteralSequenceV2(labels.toVector),
                  comments = StringLiteralSequenceV2(comments.toVector),
                  position = positionOption.getOrElse(
                    throw InconsistentRepositoryDataException(
                      s"Required position property missing for list node $nodeIri."
                    )
                  ),
                  hasRootNode = hasRootNodeOption.getOrElse(
                    throw InconsistentRepositoryDataException(
                      s"Required hasRootNode property missing for list node $nodeIri."
                    )
                  )
                ).unescape
              }
          }
          Some(nodeInfo)
        } else {
          None
        }

    } yield maybeListNodeInfo

  }

  /**
   * Retrieves information about a single node (without information about children). The single node can be a
   * root node or child node
   *
   * @param nodeIri              the IRI of the list node to be queried.
   * @param requestingUser       the user making the request.
   * @return a [[ChildNodeInfoGetResponseADM]].
   */
  override def listNodeInfoGetRequestADM(nodeIri: IRI, requestingUser: UserADM): Task[NodeInfoGetResponseADM] =
    for {
      maybeListNodeInfoADM <- listNodeInfoGetADM(nodeIri, requestingUser)
      result = maybeListNodeInfoADM match {
                 case Some(childInfo: ListChildNodeInfoADM) => ChildNodeInfoGetResponseADM(childInfo)
                 case Some(rootInfo: ListRootNodeInfoADM)   => RootNodeInfoGetResponseADM(rootInfo)
                 case _                                     => throw NotFoundException(s"List node '$nodeIri' not found")
               }
    } yield result

  /**
   * Retrieves a complete node including children. The node can be the lists root node or child node.
   *
   * @param nodeIri              the IRI of the list node to be queried.
   * @param shallow              denotes if all children or only the immediate children will be returned.
   * @param requestingUser       the user making the request.
   * @return a optional [[ListNodeADM]]
   */
  private def listNodeGetADM(
    nodeIri: IRI,
    shallow: Boolean,
    requestingUser: UserADM
  ): Task[Option[ListNodeADM]] = {
    // this query will give us only the information about the root node.
    val query = org.knora.webapi.messages.twirl.queries.sparql.admin.txt
      .getListNode(nodeIri)
      .toString()
    for {
      listInfoResponse <- triplestoreService.sparqlHttpExtendedConstruct(query)
      maybeListNode <-
        if (listInfoResponse.statements.nonEmpty) {
          for {
            // here we know that the list exists and it is fine if children is an empty list
            children <-
              getChildren(
                ofNodeIri = nodeIri,
                shallow = shallow,
                requestingUser = requestingUser
              )

            statements = listInfoResponse.statements

            node: ListNodeADM = statements.head match {
                                  case (nodeIri: SubjectV2, propsMap: Map[SmartIri, Seq[LiteralV2]]) =>
                                    val labels: Seq[StringLiteralV2] = propsMap
                                      .getOrElse(OntologyConstants.Rdfs.Label.toSmartIri, Seq.empty[StringLiteralV2])
                                      .map(_.asInstanceOf[StringLiteralV2])
                                    val comments: Seq[StringLiteralV2] = propsMap
                                      .getOrElse(OntologyConstants.Rdfs.Comment.toSmartIri, Seq.empty[StringLiteralV2])
                                      .map(_.asInstanceOf[StringLiteralV2])

                                    val attachedToProjectOption: Option[IRI] =
                                      propsMap.get(OntologyConstants.KnoraBase.AttachedToProject.toSmartIri) match {
                                        case Some(iris: Seq[LiteralV2]) =>
                                          iris.headOption match {
                                            case Some(iri: IriLiteralV2) => Some(iri.value)
                                            case other =>
                                              throw InconsistentRepositoryDataException(
                                                s"Expected attached to project Iri as an IriLiteralV2 for list node $nodeIri, but got $other"
                                              )
                                          }

                                        case None => None
                                      }

                                    val hasRootNodeOption: Option[IRI] =
                                      propsMap.get(OntologyConstants.KnoraBase.HasRootNode.toSmartIri) match {
                                        case Some(iris: Seq[LiteralV2]) =>
                                          iris.headOption match {
                                            case Some(iri: IriLiteralV2) => Some(iri.value)
                                            case other =>
                                              throw InconsistentRepositoryDataException(
                                                s"Expected root node Iri as an IriLiteralV2 for list node $nodeIri, but got $other"
                                              )
                                          }

                                        case None => None
                                      }

                                    val isRootNode: Boolean =
                                      propsMap.get(OntologyConstants.KnoraBase.IsRootNode.toSmartIri) match {
                                        case Some(values: Seq[LiteralV2]) =>
                                          values.headOption match {
                                            case Some(value: BooleanLiteralV2) => value.value
                                            case Some(other) =>
                                              throw InconsistentRepositoryDataException(
                                                s"Expected isRootNode as an BooleanLiteralV2 for list node $nodeIri, but got $other"
                                              )
                                            case None => false
                                          }

                                        case None => false
                                      }

                                    val positionOption: Option[Int] = propsMap
                                      .get(OntologyConstants.KnoraBase.ListNodePosition.toSmartIri)
                                      .map(_.head.asInstanceOf[IntLiteralV2].value)

                                    if (isRootNode) {
                                      ListRootNodeADM(
                                        id = nodeIri.toString,
                                        projectIri = attachedToProjectOption.getOrElse(
                                          throw InconsistentRepositoryDataException(
                                            s"Required attachedToProject property missing for list node $nodeIri."
                                          )
                                        ),
                                        name = propsMap
                                          .get(OntologyConstants.KnoraBase.ListNodeName.toSmartIri)
                                          .map(_.head.asInstanceOf[StringLiteralV2].value),
                                        labels = StringLiteralSequenceV2(labels.toVector),
                                        comments = StringLiteralSequenceV2(comments.toVector),
                                        children = children
                                      )
                                    } else {
                                      ListChildNodeADM(
                                        id = nodeIri.toString,
                                        name = propsMap
                                          .get(OntologyConstants.KnoraBase.ListNodeName.toSmartIri)
                                          .map(_.head.asInstanceOf[StringLiteralV2].value),
                                        labels = StringLiteralSequenceV2(labels.toVector),
                                        comments = Some(StringLiteralSequenceV2(comments.toVector)),
                                        position = positionOption.getOrElse(
                                          throw InconsistentRepositoryDataException(
                                            s"Required position property missing for list node $nodeIri."
                                          )
                                        ),
                                        hasRootNode = hasRootNodeOption.getOrElse(
                                          throw InconsistentRepositoryDataException(
                                            s"Required hasRootNode property missing for list node $nodeIri."
                                          )
                                        ),
                                        children = children
                                      )
                                    }
                                }

          } yield Some(node)
        } else {
          ZIO.succeed(None)
        }

    } yield maybeListNode
  }

  /**
   * Retrieves the child nodes from the triplestore. If shallow is true, then only the immediate children will be
   * returned, otherwise all children and their children's children will be returned.
   *
   * @param ofNodeIri            the IRI of the node for which children are to be returned.
   * @param shallow              denotes if all children or only the immediate children will be returned.
   * @param requestingUser       the user making the request.
   * @return a sequence of [[ListChildNodeADM]].
   */
  private def getChildren(
    ofNodeIri: IRI,
    shallow: Boolean,
    requestingUser: UserADM
  ): Task[Seq[ListChildNodeADM]] = {

    /**
     * This function recursively transforms SPARQL query results representing a hierarchical list into a [[ListChildNodeADM]].
     *
     * @param nodeIri    the IRI of the node to be created.
     * @param statements a [[Map]] in which each key is the IRI of a node in the hierarchical list, and each value is a [[Seq]]
     *                   of SPARQL query results representing that node's children.
     * @return a [[ListChildNodeADM]].
     */
    def createChildNode(nodeIri: IRI, statements: Seq[(SubjectV2, Map[SmartIri, Seq[LiteralV2]])]): ListChildNodeADM = {
      val propsMap: Map[SmartIri, Seq[LiteralV2]] = statements.filter(_._1 == IriSubjectV2(nodeIri)).head._2

      val hasRootNode: IRI = propsMap
        .getOrElse(
          OntologyConstants.KnoraBase.HasRootNode.toSmartIri,
          throw InconsistentRepositoryDataException(s"Required hasRootNode property missing for list node $nodeIri.")
        )
        .head
        .toString

      val nameOption = propsMap
        .get(OntologyConstants.KnoraBase.ListNodeName.toSmartIri)
        .map(_.head.asInstanceOf[StringLiteralV2].value)

      val labels: Seq[StringLiteralV2] = propsMap
        .getOrElse(OntologyConstants.Rdfs.Label.toSmartIri, Seq.empty[StringLiteralV2])
        .map(_.asInstanceOf[StringLiteralV2])
      val comments: Seq[StringLiteralV2] = propsMap
        .getOrElse(OntologyConstants.Rdfs.Comment.toSmartIri, Seq.empty[StringLiteralV2])
        .map(_.asInstanceOf[StringLiteralV2])

      val positionOption: Option[Int] = propsMap
        .get(OntologyConstants.KnoraBase.ListNodePosition.toSmartIri)
        .map(_.head.asInstanceOf[IntLiteralV2].value)
      val position = positionOption.getOrElse(
        throw InconsistentRepositoryDataException(s"Required position property missing for list node $nodeIri.")
      )

      val children: Seq[ListChildNodeADM] = propsMap.get(OntologyConstants.KnoraBase.HasSubListNode.toSmartIri) match {
        case Some(iris: Seq[LiteralV2]) =>
          if (!shallow) {
            // if not shallow then get the children of this node
            iris.map { iri =>
              createChildNode(iri.toString, statements)
            }
          } else {
            // if shallow, then we don't need the children
            Seq.empty[ListChildNodeADM]
          }

        case None => Seq.empty[ListChildNodeADM]
      }

      ListChildNodeADM(
        id = nodeIri,
        name = nameOption,
        labels = StringLiteralSequenceV2(labels.toVector),
        comments = Some(StringLiteralSequenceV2(comments.toVector)),
        children = children.map(_.sorted),
        position = position,
        hasRootNode = hasRootNode
      ).unescape
    }
    val query = org.knora.webapi.messages.twirl.queries.sparql.admin.txt
      .getListNodeWithChildren(ofNodeIri)
      .toString()
    for {
      nodeWithChildrenResponse                                   <- triplestoreService.sparqlHttpExtendedConstruct(query)
      statements: Seq[(SubjectV2, Map[SmartIri, Seq[LiteralV2]])] = nodeWithChildrenResponse.statements.toList

      startNodePropsMap: Map[SmartIri, Seq[LiteralV2]] = statements.filter(_._1 == IriSubjectV2(ofNodeIri)).head._2

      children: Seq[ListChildNodeADM] = startNodePropsMap.get(
                                          OntologyConstants.KnoraBase.HasSubListNode.toSmartIri
                                        ) match {
                                          case Some(iris: Seq[LiteralV2]) =>
                                            iris.map { iri =>
                                              createChildNode(iri.toString, statements)
                                            }

                                          case None => Seq.empty[ListChildNodeADM]
                                        }

      sortedChildren = children.sortBy(_.position) map (_.sorted)

    } yield sortedChildren
  }

  /**
   * Provides the path to a particular hierarchical list node.
   *
   * @param queryNodeIri   the IRI of the node whose path is to be queried.
   * @param requestingUser the user making the request.
   */
  override def nodePathGetAdminRequest(queryNodeIri: IRI, requestingUser: UserADM): Task[NodePathGetResponseADM] = {

    /**
     * Recursively constructs the path to a node.
     *
     * @param node      the IRI of the node whose path is to be constructed.
     * @param nodeMap   a [[Map]] of node IRIs to query result row data, in the format described below.
     * @param parentMap a [[Map]] of child node IRIs to parent node IRIs.
     * @param path      the path constructed so far.
     * @return the complete path to `node`.
     */
    @tailrec
    def makePath(
      node: IRI,
      nodeMap: Map[IRI, Map[String, String]],
      parentMap: Map[IRI, IRI],
      path: Seq[NodePathElementADM]
    ): Seq[NodePathElementADM] = {
      // Get the details of the node.
      val nodeData = nodeMap(node)

      // Construct a NodePathElementV2 containing those details.
      val pathElement = NodePathElementADM(
        id = nodeData("node"),
        name = nodeData.get("nodeName"),
        labels = if (nodeData.contains("label")) {
          StringLiteralSequenceV2(Vector(StringLiteralV2(nodeData("label"))))
        } else {
          StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
        },
        comments = StringLiteralSequenceV2(Vector.empty[StringLiteralV2])
      )

      // Add it to the path.
      val newPath = pathElement +: path

      // Does this node have a parent?
      parentMap.get(pathElement.id) match {
        case Some(parentIri) =>
          // Yes: recurse.
          makePath(parentIri, nodeMap, parentMap, newPath)

        case None =>
          // No: the path is complete.
          newPath
      }
    }

    val query =
      org.knora.webapi.messages.twirl.queries.sparql.admin.txt
        .getNodePath(
          queryNodeIri = queryNodeIri,
          preferredLanguage = requestingUser.lang,
          fallbackLanguage = appConfig.fallbackLanguage
        )
        .toString()
    for {
      nodePathResponse <- triplestoreService.sparqlHttpSelect(query)
      /*

            If we request the path to the node <http://rdfh.ch/lists/c7f07a3fc1> ("Heidi Film"), the response has the following format:

            node                                        nodeName     label                     child
            <http://rdfh.ch/lists/c7f07a3fc1>    1            Heidi Film
            <http://rdfh.ch/lists/2ebd2706c1>    7            FILM UND FOTO             <http://rdfh.ch/lists/c7f07a3fc1>
            <http://rdfh.ch/lists/691eee1cbe>    4KUN         ART                       <http://rdfh.ch/lists/2ebd2706c1>

            The order of the rows is arbitrary. Now we need to reconstruct the path based on the parent-child relationships between
            nodes.

       */

      // A Map of node IRIs to query result rows.
      nodeMap: Map[IRI, Map[String, String]] = nodePathResponse.results.bindings.map { row =>
                                                 row.rowMap("node") -> row.rowMap
                                               }.toMap

      // A Map of child node IRIs to parent node IRIs.
      parentMap: Map[IRI, IRI] = nodePathResponse.results.bindings.foldLeft(Map.empty[IRI, IRI]) { case (acc, row) =>
                                   row.rowMap.get("child") match {
                                     case Some(child) => acc + (child -> row.rowMap("node"))
                                     case None        => acc
                                   }
                                 }
    } yield NodePathGetResponseADM(elements = makePath(queryNodeIri, nodeMap, parentMap, Nil))
  }

  /**
   * Creates a node (root or child).
   *
   * @param createNodeRequest    the new node's information.
   * @return a [newListNodeIri]
   */
  private def createNode(
    createNodeRequest: ListNodeCreatePayloadADM
  ): Task[IRI] = {
    val parentNode: Option[ListIri] = createNodeRequest match {
      case ListRootNodeCreatePayloadADM(_, _, _, _, _)                    => None
      case ListChildNodeCreatePayloadADM(_, parentNodeIri, _, _, _, _, _) => Some(parentNodeIri)
    }

    val (id, projectIri, name, position) = createNodeRequest match {
      case root: ListNodeCreatePayloadADM.ListRootNodeCreatePayloadADM =>
        (root.id, root.projectIri, root.name, None)
      case child: ListNodeCreatePayloadADM.ListChildNodeCreatePayloadADM =>
        (child.id, child.projectIri, child.name, child.position)
    }

    def getPositionOfNewChild(children: Seq[ListChildNodeADM]): Int = {
      if (position.exists(_.value > children.size)) {
        val givenPosition = position.map(_.value)
        throw BadRequestException(
          s"Invalid position given $givenPosition, maximum allowed position is = ${children.size}."
        )
      }

      val newPosition = if (position.isEmpty || position.exists(_.value.equals(-1))) {
        children.size
      } else {
        position.get.value
      }
      newPosition
    }

    def getRootNodeIri(parentListNode: ListNodeADM): IRI =
      parentListNode match {
        case root: ListRootNodeADM   => root.id
        case child: ListChildNodeADM => child.hasRootNode
      }

    def getRootNodeAndPositionOfNewChild(
      parentNodeIri: IRI,
      dataNamedGraph: IRI
    ): Task[(Some[Int], Some[IRI])] =
      for {
        /* Verify that the list node exists by retrieving the whole node including children one level deep (need for position calculation) */
        maybeParentListNode <- listNodeGetADM(
                                 nodeIri = parentNodeIri,
                                 shallow = true,
                                 requestingUser = KnoraSystemInstances.Users.SystemUser
                               )

        (parentListNode: ListNodeADM, children: Seq[ListChildNodeADM]) = maybeParentListNode match {
                                                                           case Some(node: ListRootNodeADM) =>
                                                                             (
                                                                               node,
                                                                               node.children
                                                                             )
                                                                           case Some(node: ListChildNodeADM) =>
                                                                             (
                                                                               node,
                                                                               node.children
                                                                             )
                                                                           case Some(_) | None =>
                                                                             throw BadRequestException(
                                                                               s"List node '$parentNodeIri' not found."
                                                                             )
                                                                         }

        // get position of the new child
        position = getPositionOfNewChild(children)

        // Is the node supposed to be inserted in a specific position in array of children?
        _ <-
          if (position != children.size) {
            // Yes. Shift the siblings after the given position to right in order to free the position.
            for {
              // shift siblings that are after given position to right
              updatedSiblings <- shiftNodes(
                                   startPos = position,
                                   endPos = children.size - 1,
                                   nodes = children,
                                   shiftToLeft = false,
                                   dataNamedGraph = dataNamedGraph
                                 )
            } yield updatedSiblings
          } else {
            // No. new node will be appended to the end, no shifting is necessary.
            ZIO.succeed(children)
          }

        /* get the root node, depending on the type of the parent */
        rootNodeIri = getRootNodeIri(parentListNode)

      } yield (Some(position), Some(rootNodeIri))

    for {
      /* Verify that the project exists by retrieving it. We need the project information so that we can calculate the data graph and IRI for the new node.  */
      maybeProject <-
        messageRelay
          .ask(
            ProjectGetADM(
              identifier = IriIdentifier
                .fromString(projectIri.value)
                .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
            )
          )
          .map(any => any.asInstanceOf[Option[ProjectADM]])

      project: ProjectADM = maybeProject match {
                              case Some(project: ProjectADM) => project
                              case None                      => throw BadRequestException(s"Project '$projectIri' not found.")
                            }

      /* verify that the list node name is unique for the project */
      projectUniqueNodeName <- listNodeNameIsProjectUnique(
                                 projectIri.value,
                                 name
                               )
      _ = if (!projectUniqueNodeName) {
            val escapedName   = name.get.value
            val unescapedName = stringFormatter.fromSparqlEncodedString(escapedName)
            throw BadRequestException(
              s"The node name $unescapedName is already used by a list inside the project ${projectIri.value}."
            )
          }

      // calculate the data named graph
      dataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(project)

      // if parent node is known, find the root node of the list and the position of the new child node
      t <-
        if (parentNode.nonEmpty) {
          getRootNodeAndPositionOfNewChild(
            parentNodeIri = parentNode.get.value,
            dataNamedGraph = dataNamedGraph
          )
        } else {
          ZIO.succeed(None, None)
        }
      newPosition: Option[Int] = t._1
      rootNodeIri: Option[IRI] = t._2

      // check the custom IRI; if not given, create an unused IRI
      customListIri: Option[SmartIri] = id.map(_.value).map(_.toSmartIri)
      maybeShortcode: String          = project.shortcode
      newListNodeIri <-
        iriService.checkOrCreateEntityIriTask(customListIri, stringFormatter.makeRandomListIri(maybeShortcode))

      // Create the new list node depending on type
      createNewListSparqlString: String = createNodeRequest match {
                                            case ListNodeCreatePayloadADM.ListRootNodeCreatePayloadADM(
                                                  _,
                                                  projectIri,
                                                  name,
                                                  labels,
                                                  comments
                                                ) =>
                                              org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                                                .createNewListNode(
                                                  dataNamedGraph = dataNamedGraph,
                                                  listClassIri = OntologyConstants.KnoraBase.ListNode,
                                                  projectIri = projectIri.value,
                                                  nodeIri = newListNodeIri,
                                                  parentNodeIri = None,
                                                  rootNodeIri = rootNodeIri,
                                                  position = None,
                                                  maybeName = name.map(_.value),
                                                  maybeLabels = labels.value,
                                                  maybeComments = Some(comments.value)
                                                )
                                                .toString
                                            case ListNodeCreatePayloadADM.ListChildNodeCreatePayloadADM(
                                                  _,
                                                  parentNodeIri,
                                                  projectIri,
                                                  name,
                                                  _,
                                                  labels,
                                                  comments
                                                ) =>
                                              org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                                                .createNewListNode(
                                                  dataNamedGraph = dataNamedGraph,
                                                  listClassIri = OntologyConstants.KnoraBase.ListNode,
                                                  projectIri = projectIri.value,
                                                  nodeIri = newListNodeIri,
                                                  parentNodeIri = Some(parentNodeIri.value),
                                                  rootNodeIri = rootNodeIri,
                                                  position = newPosition,
                                                  maybeName = name.map(_.value),
                                                  maybeLabels = labels.value,
                                                  maybeComments = comments.map(_.value)
                                                )
                                                .toString
                                          }

      _ <- triplestoreService.sparqlHttpUpdate(createNewListSparqlString)
    } yield newListNodeIri
  }

  /**
   * Creates a list.
   *
   * @param createRootRequest    the new list's information.
   * @param apiRequestID         the unique api request ID.
   * @return a [[RootNodeInfoGetResponseADM]]
   */
  override def listCreateRequestADM(
    createRootRequest: ListRootNodeCreatePayloadADM,
    apiRequestID: UUID
  ): Task[ListGetResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def listCreateTask(
      createRootRequest: ListRootNodeCreatePayloadADM
    ): Task[ListGetResponseADM] =
      for {
        listRootIri <- createNode(createRootRequest)

        // Verify that the list was created.
        maybeNewListADM <- listGetADM(
                             rootNodeIri = listRootIri,
                             requestingUser = KnoraSystemInstances.Users.SystemUser
                           )

        newListADM = maybeNewListADM.getOrElse(
                       throw UpdateNotPerformedException(
                         s"List $listRootIri was not created. Please report this as a possible bug."
                       )
                     )

      } yield ListGetResponseADM(newListADM)

    IriLocker.runWithIriLockZio(
      apiRequestID,
      LISTS_GLOBAL_LOCK_IRI,
      listCreateTask(createRootRequest)
    )
  }

  /**
   * Changes basic node information stored (root or child)
   *
   * @param nodeIri              the list's IRI.
   * @param changeNodeRequest    the new node information.
   * @param apiRequestID         the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   *         ForbiddenException          in the case that the user is not allowed to perform the operation.
   *         BadRequestException         in the case when the list IRI given in the path does not match with the one given in the payload.
   *         UpdateNotPerformedException in the case something else went wrong, and the change could not be performed.
   */
  override def nodeInfoChangeRequest(
    nodeIri: IRI,
    changeNodeRequest: ListNodeChangePayloadADM,
    apiRequestID: UUID
  ): Task[NodeInfoGetResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def nodeInfoChangeTask(
      nodeIri: IRI,
      changeNodeRequest: ListNodeChangePayloadADM
    ): Task[NodeInfoGetResponseADM] =
      for {
        // check if nodeIRI in path and payload match
        _ <- ZIO.attempt(
               if (!nodeIri.equals(changeNodeRequest.listIri.value))
                 throw BadRequestException("IRI in path and payload don't match.")
             )

        changeNodeInfoSparqlString <- getUpdateNodeInfoSparqlStatement(changeNodeRequest)
        _                          <- triplestoreService.sparqlHttpUpdate(changeNodeInfoSparqlString)

        /* Verify that the node info was updated */
        maybeNodeADM <- listNodeInfoGetADM(
                          nodeIri = nodeIri,
                          requestingUser = KnoraSystemInstances.Users.SystemUser
                        )

        response = maybeNodeADM match {
                     case Some(rootNode: ListRootNodeInfoADM) => RootNodeInfoGetResponseADM(listinfo = rootNode)

                     case Some(childNode: ListChildNodeInfoADM) => ChildNodeInfoGetResponseADM(nodeinfo = childNode)

                     case _ =>
                       throw UpdateNotPerformedException(
                         s"Node $nodeIri was not updated. Please report this as a possible bug."
                       )
                   }

      } yield response

    IriLocker.runWithIriLockZio(
      apiRequestID,
      nodeIri,
      nodeInfoChangeTask(nodeIri, changeNodeRequest)
    )
  }

  /**
   * Creates a new child node and appends it to an existing list node.
   *
   * @param createChildNodeRequest the new list node's information.
   * @param apiRequestID           the unique api request ID.
   * @return a [[ChildNodeInfoGetResponseADM]]
   */
  override def listChildNodeCreateRequestADM(
    createChildNodeRequest: ListChildNodeCreatePayloadADM,
    apiRequestID: UUID
  ): Task[ChildNodeInfoGetResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def listChildNodeCreateTask(
      createChildNodeRequest: ListChildNodeCreatePayloadADM
    ): Task[ChildNodeInfoGetResponseADM] =
      for {
        newListNodeIri <- createNode(createChildNodeRequest)
        // Verify that the list node was created.
        maybeNewListNode <- listNodeInfoGetADM(
                              nodeIri = newListNodeIri,
                              KnoraSystemInstances.Users.SystemUser
                            )
        newListNode = maybeNewListNode match {
                        case Some(childNode: ListChildNodeInfoADM) => childNode
                        case Some(_: ListRootNodeInfoADM) =>
                          throw UpdateNotPerformedException(
                            s"Child node ${createChildNodeRequest.name} could not be created. Probably parent node Iri is missing in payload."
                          )
                        case _ =>
                          throw UpdateNotPerformedException(
                            s"List node $newListNodeIri was not created. Please report this as a possible bug."
                          )
                      }

      } yield ChildNodeInfoGetResponseADM(nodeinfo = newListNode)

    IriLocker.runWithIriLockZio(
      apiRequestID,
      LISTS_GLOBAL_LOCK_IRI,
      listChildNodeCreateTask(createChildNodeRequest)
    )
  }

  /**
   * Changes name of the node (root or child)
   *
   * @param nodeIri               the node's IRI.
   * @param changeNodeNameRequest the new node name.
   * @param apiRequestID          the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   *
   *         [[ForbiddenException]]          in the case that the user is not allowed to perform the operation.
   *
   *         [[UpdateNotPerformedException]] in the case something else went wrong, and the change could not be performed.
   */
  override def nodeNameChangeRequest(
    nodeIri: IRI,
    changeNodeNameRequest: NodeNameChangePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[NodeInfoGetResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def nodeNameChangeTask(
      nodeIri: IRI,
      changeNodeNameRequest: NodeNameChangePayloadADM,
      requestingUser: UserADM
    ): Task[NodeInfoGetResponseADM] =
      for {
        projectIri <- getProjectIriFromNode(nodeIri)
        // check if the requesting user is allowed to perform operation
        _ = if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
              // not project or a system admin
              throw ForbiddenException(ListErrorMessages.ListChangePermission)
            }

        changeNodeNameSparqlString <-
          getUpdateNodeInfoSparqlStatement(
            changeNodeInfoRequest = ListNodeChangePayloadADM(
              listIri = ListIri.make(nodeIri).fold(e => throw e.head, v => v),
              projectIri = ProjectIri.make(projectIri).fold(e => throw e.head, v => v),
              name = Some(changeNodeNameRequest.name)
            )
          )
        _ <- triplestoreService.sparqlHttpUpdate(changeNodeNameSparqlString)

        /* Verify that the node info was updated */
        maybeNodeADM <- listNodeInfoGetADM(
                          nodeIri = nodeIri,
                          requestingUser = KnoraSystemInstances.Users.SystemUser
                        )

        response = maybeNodeADM match {
                     case Some(rootNode: ListRootNodeInfoADM)   => RootNodeInfoGetResponseADM(listinfo = rootNode)
                     case Some(childNode: ListChildNodeInfoADM) => ChildNodeInfoGetResponseADM(nodeinfo = childNode)
                     case _ =>
                       throw UpdateNotPerformedException(
                         s"Node $nodeIri was not updated. Please report this as a possible bug."
                       )
                   }
      } yield response

    IriLocker.runWithIriLockZio(
      apiRequestID,
      nodeIri,
      nodeNameChangeTask(nodeIri, changeNodeNameRequest, requestingUser)
    )
  }

  /**
   * Changes labels of the node (root or child)
   *
   * @param nodeIri                 the node's IRI.
   * @param changeNodeLabelsRequest the new node labels.
   * @param requestingUser          the requesting user.
   * @param apiRequestID            the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   *
   *        [[ForbiddenException]]          in the case that the user is not allowed to perform the operation.
   *
   *        [[UpdateNotPerformedException]] in the case something else went wrong, and the change could not be performed.
   */
  override def nodeLabelsChangeRequest(
    nodeIri: IRI,
    changeNodeLabelsRequest: NodeLabelsChangePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[NodeInfoGetResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def nodeLabelsChangeTask(
      nodeIri: IRI,
      changeNodeLabelsRequest: NodeLabelsChangePayloadADM,
      requestingUser: UserADM
    ): Task[NodeInfoGetResponseADM] =
      for {
        projectIri <- getProjectIriFromNode(nodeIri)

        // check if the requesting user is allowed to perform operation
        _ = if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
              // not project or a system admin
              throw ForbiddenException(ListErrorMessages.ListChangePermission)
            }
        changeNodeLabelsSparqlString <- getUpdateNodeInfoSparqlStatement(
                                          changeNodeInfoRequest = ListNodeChangePayloadADM(
                                            listIri = ListIri.make(nodeIri).fold(e => throw e.head, v => v),
                                            projectIri = ProjectIri.make(projectIri).fold(e => throw e.head, v => v),
                                            labels = Some(changeNodeLabelsRequest.labels)
                                          )
                                        )
        _ <- triplestoreService.sparqlHttpUpdate(changeNodeLabelsSparqlString)

        /* Verify that the node info was updated */
        maybeNodeADM <- listNodeInfoGetADM(
                          nodeIri = nodeIri,
                          requestingUser = KnoraSystemInstances.Users.SystemUser
                        )

        response = maybeNodeADM match {
                     case Some(rootNode: ListRootNodeInfoADM)   => RootNodeInfoGetResponseADM(listinfo = rootNode)
                     case Some(childNode: ListChildNodeInfoADM) => ChildNodeInfoGetResponseADM(nodeinfo = childNode)
                     case _ =>
                       throw UpdateNotPerformedException(
                         s"Node $nodeIri was not updated. Please report this as a possible bug."
                       )
                   }
      } yield response

    IriLocker.runWithIriLockZio(
      apiRequestID,
      nodeIri,
      nodeLabelsChangeTask(nodeIri, changeNodeLabelsRequest, requestingUser)
    )
  }

  /**
   * Changes comments of the node (root or child)
   *
   * @param nodeIri                   the node's IRI.
   * @param changeNodeCommentsRequest the new node comments.
   * @param requestingUser            the requesting user.
   * @param apiRequestID              the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   *
   *         [[ForbiddenException]]          in the case that the user is not allowed to perform the operation.
   *
   *         [[UpdateNotPerformedException]] in the case something else went wrong, and the change could not be performed.
   */
  override def nodeCommentsChangeRequest(
    nodeIri: IRI,
    changeNodeCommentsRequest: NodeCommentsChangePayloadADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[NodeInfoGetResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def nodeCommentsChangeTask(
      nodeIri: IRI,
      changeNodeCommentsRequest: NodeCommentsChangePayloadADM,
      requestingUser: UserADM
    ): Task[NodeInfoGetResponseADM] =
      for {
        projectIri <- getProjectIriFromNode(nodeIri)

        // check if the requesting user is allowed to perform operation
        _ = if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
              // not project or a system admin
              throw ForbiddenException(ListErrorMessages.ListChangePermission)
            }

        changeNodeCommentsSparqlString <- getUpdateNodeInfoSparqlStatement(
                                            changeNodeInfoRequest = ListNodeChangePayloadADM(
                                              listIri = ListIri.make(nodeIri).fold(e => throw e.head, v => v),
                                              projectIri = ProjectIri.make(projectIri).fold(e => throw e.head, v => v),
                                              comments = Some(changeNodeCommentsRequest.comments)
                                            )
                                          )
        _ <- triplestoreService.sparqlHttpUpdate(changeNodeCommentsSparqlString)

        /* Verify that the node info was updated */
        maybeNodeADM <- listNodeInfoGetADM(
                          nodeIri = nodeIri,
                          requestingUser = KnoraSystemInstances.Users.SystemUser
                        )

        response = maybeNodeADM match {
                     case Some(rootNode: ListRootNodeInfoADM)   => RootNodeInfoGetResponseADM(listinfo = rootNode)
                     case Some(childNode: ListChildNodeInfoADM) => ChildNodeInfoGetResponseADM(nodeinfo = childNode)
                     case _ =>
                       throw UpdateNotPerformedException(
                         s"Node $nodeIri was not updated. Please report this as a possible bug."
                       )
                   }
      } yield response

    IriLocker.runWithIriLockZio(
      apiRequestID,
      nodeIri,
      nodeCommentsChangeTask(nodeIri, changeNodeCommentsRequest, requestingUser)
    )
  }

  /**
   * Changes position of the node
   *
   * @param nodeIri                   the node's IRI.
   * @param changeNodePositionRequest the new node comments.
   * @param requestingUser            the requesting user.
   * @param apiRequestID              the unique api request ID.
   * @return a [[NodePositionChangeResponseADM]]
   *
   *         [[ForbiddenException]]          in the case that the user is not allowed to perform the operation.
   *
   *         [[UpdateNotPerformedException]] in the case something else went wrong, and the change could not be performed.
   */
  override def nodePositionChangeRequest(
    nodeIri: IRI,
    changeNodePositionRequest: ChangeNodePositionApiRequestADM,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[NodePositionChangeResponseADM] = {

    /**
     * Checks if the given position is in range.
     * The highest position a node can be placed is to the end of the parents children; that means length of existing
     * children + 1
     *
     * @param parentNode  the parent to which the node should belong.
     * @param isNewParent identifier that node is added to another parent or not.
     */
    @throws[BadRequestException]("if given position is out of range.")
    def isNewPositionValid(parentNode: ListNodeADM, isNewParent: Boolean): Unit = {
      val numberOfChildren = parentNode.getChildren.size
      // If the node must be added to a new parent, highest valid position is numberOfChildre.
      // For example, if the new parent already has 3 children, the highest occupied position is 2, node can be
      // placed in position 3. That means the furthest a node can be positioned is being appended to the end of
      // children of the new parent.
      if (isNewParent && changeNodePositionRequest.position > numberOfChildren) {
        throw BadRequestException(s"Invalid position given, maximum allowed position is = $numberOfChildren.")
      }

      // If node remains in its current parent, the highest valid position is numberOfChildren -1
      // That means if the parent node has 4 children, the highest position is 3.
      // Nodes are only reorganized within the same parent.
      if (!isNewParent && changeNodePositionRequest.position > numberOfChildren - 1) {
        throw BadRequestException(s"Invalid position given, maximum allowed position is = ${numberOfChildren - 1}.")
      }

      // The lowest position a node gets is 0. If -1 is given, node will be appended to the end of children list.
      // Values less than -1 are not allowed.
      if (changeNodePositionRequest.position < -1) {
        throw BadRequestException(s"Invalid position given, minimum allowed is -1.")
      }
    }

    /**
     * Checks that the position of the node is updated and node is sublist of specified parent.
     * It also checks the sibling nodes are shifted accordingly.
     *
     * @param newPosition the new position of the node.
     * @return the updated parent node with all its children as [[ListNodeADM]]
     *         UpdateNotPerformedException if some thing has gone wrong during the update.
     */
    def verifyParentChildrenUpdate(newPosition: Int): Task[ListNodeADM] =
      for {
        maybeParentNode <- listNodeGetADM(
                             nodeIri = changeNodePositionRequest.parentIri,
                             shallow = false,
                             requestingUser = KnoraSystemInstances.Users.SystemUser
                           )
        updatedParent                          = maybeParentNode.get
        updatedChildren: Seq[ListChildNodeADM] = updatedParent.getChildren
        (siblingsPositionedBefore: Seq[ListChildNodeADM], rest: Seq[ListChildNodeADM]) =
          updatedChildren.partition(node => node.position < newPosition)

        // verify that node is among children of specified parent in correct position
        updatedNode = rest.head
        _ = if (updatedNode.id != nodeIri || updatedNode.position != newPosition) {
              throw UpdateNotPerformedException(
                s"Node is not repositioned correctly in specified parent node. Please report this as a bug."
              )
            }
        leftPositions: Seq[Int] = siblingsPositionedBefore.map(child => child.position)
        _ = if (leftPositions != leftPositions.sorted) {
              throw UpdateNotPerformedException(
                s"Something has gone wrong with shifting nodes. Please report this as a bug."
              )
            }
        siblingsPositionedAfter = rest.slice(1, rest.length)
        rightSiblings: Seq[Int] = siblingsPositionedAfter.map(child => child.position)
        _ = if (rightSiblings != rightSiblings.sorted) {
              throw UpdateNotPerformedException(
                s"Something has gone wrong with shifting nodes. Please report this as a bug."
              )
            }

      } yield updatedParent

    /**
     * Changes position of the node within its original parent.
     *
     * @param node           the node whose position should be updated.
     * @param parentIri      the IRI of the parent node.
     * @param givenPosition  the new node position.
     * @param dataNamedGraph the new node position.
     * @return the new position of the node [[Int]]
     *         UpdateNotPerformedException in the case the given new position is the same as current position.
     */
    def updatePositionWithinSameParent(
      node: ListChildNodeADM,
      parentIri: IRI,
      givenPosition: Int,
      dataNamedGraph: IRI
    ): Task[Int] =
      for {
        // get parent node with its immediate children
        maybeParentNode <- listNodeGetADM(
                             nodeIri = parentIri,
                             shallow = true,
                             requestingUser = KnoraSystemInstances.Users.SystemUser
                           )
        parentNode =
          maybeParentNode.getOrElse(
            throw BadRequestException(s"The parent node $parentIri could node be found, report this as a bug.")
          )
        _              = isNewPositionValid(parentNode, isNewParent = false)
        parentChildren = parentNode.getChildren
        currPosition   = node.position

        // if givenPosition is -1, append the child to the end of the list of children
        newPosition =
          if (givenPosition == -1) {
            parentChildren.size - 1
          } else givenPosition

        // update the position of the node itself
        _ <- updatePositionOfNode(
               nodeIri = node.id,
               newPosition = newPosition,
               dataNamedGraph = dataNamedGraph
             )

        // update position of siblings
        _ <-
          if (currPosition < newPosition) {
            for {
              // shift siblings to left
              updatedSiblings <- shiftNodes(
                                   startPos = currPosition + 1,
                                   endPos = newPosition,
                                   nodes = parentChildren,
                                   shiftToLeft = true,
                                   dataNamedGraph = dataNamedGraph
                                 )
            } yield updatedSiblings
          } else if (currPosition > newPosition) {
            for {
              // shift siblings to right
              updatedSiblings <- shiftNodes(
                                   startPos = newPosition,
                                   endPos = currPosition - 1,
                                   nodes = parentChildren,
                                   shiftToLeft = false,
                                   dataNamedGraph = dataNamedGraph
                                 )
            } yield updatedSiblings
          } else {
            throw UpdateNotPerformedException(s"The given position is the same as node's current position.")
          }
      } yield newPosition

    /**
     * Changes position of the node, remove from current parent and add to the specified parent.
     * It shifts the new siblings and old siblings.
     *
     * @param node           the node whose position should be updated.
     * @param newParentIri   the IRI of the new parent node.
     * @param currParentIri  the IRI of the current parent node.
     * @param givenPosition  the new node position.
     * @param dataNamedGraph the new node position.
     * @return the new position of the node [[Int]]
     *         UpdateNotPerformedException in the case the given new position is the same as current position.
     */
    def updateParentAndPosition(
      node: ListChildNodeADM,
      newParentIri: IRI,
      currParentIri: IRI,
      givenPosition: Int,
      dataNamedGraph: IRI
    ): Task[Int] =
      for {
        // get current parent node with its immediate children
        maybeCurrentParentNode <- listNodeGetADM(
                                    nodeIri = currParentIri,
                                    shallow = true,
                                    requestingUser = KnoraSystemInstances.Users.SystemUser
                                  )
        currentSiblings = maybeCurrentParentNode.get.getChildren
        // get new parent node with its immediate children
        maybeNewParentNode <- listNodeGetADM(
                                nodeIri = newParentIri,
                                shallow = true,
                                requestingUser = KnoraSystemInstances.Users.SystemUser
                              )
        newParent   = maybeNewParentNode.get
        _           = isNewPositionValid(newParent, isNewParent = true)
        newSiblings = newParent.getChildren

        currentNodePosition = node.position

        // if givenPosition is -1, append the child to the end of the list of children
        newPosition =
          if (givenPosition == -1) {
            newSiblings.size
          } else givenPosition

        // update the position of the node itself
        _ <- updatePositionOfNode(
               nodeIri = node.id,
               newPosition = newPosition,
               dataNamedGraph = dataNamedGraph
             )

        // shift current siblings with a higher position to left as if the node is deleted
        _ <- shiftNodes(
               startPos = currentNodePosition + 1,
               endPos = currentSiblings.last.position,
               nodes = currentSiblings,
               shiftToLeft = true,
               dataNamedGraph = dataNamedGraph
             )

        // Is node supposed to be added to the end of new parent's children list?
        _ <-
          if (givenPosition == -1 || givenPosition == newSiblings.size) {
            // Yes. New siblings should not be shifted
            ZIO.attempt(newSiblings)
          } else {
            // No. Shift new siblings with the same and higher position
            // to right, as if the node is inserted in the given position
            for {
              updatedSiblings <- shiftNodes(
                                   startPos = newPosition,
                                   endPos = newSiblings.last.position,
                                   nodes = newSiblings,
                                   shiftToLeft = false,
                                   dataNamedGraph = dataNamedGraph
                                 )
            } yield updatedSiblings
          }

        /* update the sublists of parent nodes */
        _ <- changeParentNode(
               nodeIri = node.id,
               oldParentIri = currParentIri,
               newParentIri = newParentIri,
               dataNamedGraph = dataNamedGraph
             )

      } yield newPosition

    /**
     * The actual task run with an IRI lock.
     */
    def nodePositionChangeTask(
      nodeIri: IRI,
      changeNodePositionRequest: ChangeNodePositionApiRequestADM,
      requestingUser: UserADM
    ): Task[NodePositionChangeResponseADM] =
      for {
        projectIri <- getProjectIriFromNode(nodeIri)

        // get data names graph of the project
        dataNamedGraph <- getDataNamedGraph(projectIri)

        // check if the requesting user is allowed to perform operation
        _ = if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
              // not project or a system admin
              throw ForbiddenException(ListErrorMessages.ListChangePermission)
            }

        // get node in its current position
        maybeNode <- listNodeGetADM(
                       nodeIri = nodeIri,
                       shallow = true,
                       requestingUser = KnoraSystemInstances.Users.SystemUser
                     )
        node = maybeNode match {
                 case Some(node: ListChildNodeADM) => node
                 case _ =>
                   throw BadRequestException(s"Update of position is only allowed for child nodes!")
               }

        // get node's current parent
        currentParentNodeIri <- getParentNodeIRI(nodeIri)
        newPosition <-
          if (currentParentNodeIri == changeNodePositionRequest.parentIri) {
            updatePositionWithinSameParent(
              node = node,
              parentIri = currentParentNodeIri,
              givenPosition = changeNodePositionRequest.position,
              dataNamedGraph = dataNamedGraph
            )
          } else {
            updateParentAndPosition(
              node = node,
              newParentIri = changeNodePositionRequest.parentIri,
              currParentIri = currentParentNodeIri,
              givenPosition = changeNodePositionRequest.position,
              dataNamedGraph = dataNamedGraph
            )
          }
        /* Verify that the node position and parent children position were updated */
        parentNode <- verifyParentChildrenUpdate(newPosition)
      } yield NodePositionChangeResponseADM(node = parentNode)

    IriLocker.runWithIriLockZio(
      apiRequestID,
      nodeIri,
      nodePositionChangeTask(nodeIri, changeNodePositionRequest, requestingUser)
    )
  }

  /**
   * Checks if a list can be deleted (none of its nodes is used in data).
   */
  private def canDeleteListRequestADM(
    iri: IRI
  ): Task[CanDeleteListResponseADM] =
    for {
      sparqlQuery <- ZIO.attempt(
                       org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                         .canDeleteList(iri)
                         .toString()
                     )
      response <- triplestoreService.sparqlHttpSelect(sparqlQuery)

      canDelete =
        if (response.results.bindings.isEmpty) true
        else false

    } yield CanDeleteListResponseADM(iri, canDelete)

  /**
   * Deletes all comments from requested list node (only child).
   */
  private def deleteListNodeCommentsADM(
    iri: IRI
  ): Task[ListNodeCommentsDeleteResponseADM] =
    for {
      node <- listNodeInfoGetADM(
                nodeIri = iri,
                requestingUser = KnoraSystemInstances.Users.SystemUser
              )

      doesNodeHaveComments = node.get.getComments.stringLiterals.nonEmpty

      _ = if (!doesNodeHaveComments) {
            throw BadRequestException(s"Nothing to delete. Node $iri does not have comments.")
          }

      isRootNode =
        node match {
          case Some(_: ListRootNodeInfoADM)  => true
          case Some(_: ListChildNodeInfoADM) => false
          case _                             => throw InconsistentRepositoryDataException("Bad data. List node expected.")
        }

      _ = if (isRootNode) {
            throw BadRequestException("Root node comments cannot be deleted.")
          }

      projectIri <- getProjectIriFromNode(iri)
      namedGraph <- getDataNamedGraph(projectIri)

      sparqlQuery <-
        ZIO.attempt(
          org.knora.webapi.messages.twirl.queries.sparql.admin.txt
            .deleteListNodeComments(
              namedGraph = namedGraph,
              nodeIri = iri,
              isRootNode = isRootNode
            )
            .toString()
        )
      _ <- triplestoreService.sparqlHttpUpdate(sparqlQuery)
    } yield ListNodeCommentsDeleteResponseADM(iri, !isRootNode)

  /**
   * Delete a node (root or child). If a root node is given, check for its usage in data and ontology. If not used,
   * delete the list and return a confirmation message.
   *
   * @param nodeIri              the node's IRI.
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   *
   *         [[ForbiddenException]]          in the case that the user is not allowed to perform the operation.
   *
   *         [[UpdateNotPerformedException]] in the case the node is in use and cannot be deleted.
   */
  override def deleteListItemRequestADM(
    nodeIri: IRI,
    requestingUser: UserADM,
    apiRequestID: UUID
  ): Task[ListItemDeleteResponseADM] = {

    /**
     * Checks if node itself or any of its children is in use.
     *
     * @param nodeIri      the node's IRI.
     * @param nodeChildren the children of the node.
     *
     * @return [[Unit]] if it is not in use.
     *
     *          [[BadRequestException]] in case a node or one of its children is in use.
     */
    def isNodeOrItsChildrenUsed(nodeIri: IRI, nodeChildren: Seq[ListChildNodeADM]): Task[Unit] =
      for {
        // Is node itself in use?
        _ <- isNodeUsed(
               nodeIri = nodeIri,
               errorFun = throw BadRequestException(s"Node $nodeIri cannot be deleted, because it is in use.")
             )

        errorCheckFutures: Seq[Task[Unit]] = nodeChildren.map { child =>
                                               isNodeUsed(
                                                 nodeIri = child.id,
                                                 errorFun = throw BadRequestException(
                                                   s"Node $nodeIri cannot be deleted, because its child ${child.id} is in use."
                                                 )
                                               )
                                             }

        _ <- sequence(errorCheckFutures)

      } yield ()

    /**
     * Delete a list (root node) or a child node after verifying that neither the node itself nor any of its children
     * are used. If not used, delete the children of the node first, then delete the node itself.
     *
     * @param nodeIri    the node's IRI.
     * @param projectIri the feature factory configuration.
     * @param children   the children of the node.
     * @param isRootNode the flag to determine the type of the node, root or child.
     * @return a [[IRI]]
     *         UpdateNotPerformedException in case a node is in use.
     */
    def deleteListItem(
      nodeIri: IRI,
      projectIri: IRI,
      children: Seq[ListChildNodeADM],
      isRootNode: Boolean
    ): Task[IRI] =
      for {
        // get the data graph of the project.
        dataNamedGraph <- getDataNamedGraph(projectIri)

        // delete the children
        errorCheckFutures: Seq[Task[Unit]] =
          children.map(child => deleteNode(dataNamedGraph, child.id, isRootNode = false))
        _ <- sequence(errorCheckFutures)

        // delete the node itself
        _ <- deleteNode(dataNamedGraph, nodeIri, isRootNode)

      } yield dataNamedGraph

    /**
     * Update the parent node of the deleted node by updating its remaining children.
     * Shift the remaining children of the parent node with respect to the position of the deleted node.
     *
     * @param deletedNodeIri        the IRI of the deleted node.
     * @param positionOfDeletedNode the position of the deleted node.
     * @param parentNodeIri         the IRI of the deleted node's parent.
     * @param dataNamedGraph        the data named graph.
     * @return a [[ListNodeADM]]
     *         UpdateNotPerformedException if the node that had to be deleted is still in the list of parent's children.
     */
    def updateParentNode(
      deletedNodeIri: IRI,
      positionOfDeletedNode: Int,
      parentNodeIri: IRI,
      dataNamedGraph: IRI
    ): Task[ListNodeADM] =
      for {
        maybeNode <- listNodeGetADM(
                       nodeIri = parentNodeIri,
                       shallow = false,
                       requestingUser = KnoraSystemInstances.Users.SystemUser
                     )

        parentNode: ListNodeADM =
          maybeNode.getOrElse(
            throw BadRequestException(s"The parent node of $deletedNodeIri not found, report this as a bug.")
          )

        remainingChildren = parentNode.getChildren

        _ = if (remainingChildren.exists(child => child.id == deletedNodeIri)) {
              throw UpdateNotPerformedException(s"Node $deletedNodeIri is not deleted properly, report this as a bug.")
            }

        // shift the siblings that were positioned after the deleted node, one place to left.
        updatedChildren <-
          if (remainingChildren.nonEmpty) {
            for {
              shiftedChildren <- shiftNodes(
                                   startPos = positionOfDeletedNode + 1,
                                   endPos = remainingChildren.last.position,
                                   nodes = remainingChildren,
                                   shiftToLeft = true,
                                   dataNamedGraph = dataNamedGraph
                                 )
            } yield shiftedChildren
          } else {
            ZIO.succeed(remainingChildren)
          }

        // return updated parent node with shifted children.
        updatedParentNode = parentNode match {
                              case rootNode: ListRootNodeADM =>
                                ListRootNodeADM(
                                  id = rootNode.id,
                                  projectIri = rootNode.projectIri,
                                  name = rootNode.name,
                                  labels = rootNode.labels,
                                  comments = rootNode.comments,
                                  children = updatedChildren
                                )

                              case childNode: ListChildNodeADM =>
                                ListChildNodeADM(
                                  id = childNode.id,
                                  name = childNode.name,
                                  labels = childNode.labels,
                                  comments = childNode.comments,
                                  position = childNode.position,
                                  hasRootNode = childNode.hasRootNode,
                                  children = updatedChildren
                                )
                            }
      } yield updatedParentNode

    /**
     * The actual task run with an IRI lock.
     */
    def nodeDeleteTask(
      nodeIri: IRI,
      requestingUser: UserADM
    ): Task[ListItemDeleteResponseADM] =
      for {
        projectIri <- getProjectIriFromNode(nodeIri)

        // check if the requesting user is allowed to perform operation
        _ = if (!requestingUser.permissions.isProjectAdmin(projectIri) && !requestingUser.permissions.isSystemAdmin) {
              // not project or a system admin
              throw ForbiddenException(ListErrorMessages.ListChangePermission)
            }

        maybeNode <-
          listNodeGetADM(nodeIri = nodeIri, shallow = false, requestingUser = KnoraSystemInstances.Users.SystemUser)

        response <- maybeNode match {
                      case Some(rootNode: ListRootNodeADM) =>
                        for {
                          _ <- isNodeOrItsChildrenUsed(rootNode.id, rootNode.children)

                          _ <- deleteListItem(
                                 nodeIri = rootNode.id,
                                 projectIri = projectIri,
                                 children = rootNode.children,
                                 isRootNode = true
                               )
                        } yield ListDeleteResponseADM(rootNode.id, deleted = true)

                      case Some(childNode: ListChildNodeADM) =>
                        for {
                          _ <- isNodeOrItsChildrenUsed(childNode.id, childNode.children)

                          // get parent node IRI before deleting the node
                          parentNodeIri <- getParentNodeIRI(nodeIri)

                          // delete the node
                          dataNamedGraph <- deleteListItem(
                                              nodeIri = childNode.id,
                                              projectIri = projectIri,
                                              children = childNode.children,
                                              isRootNode = false
                                            )

                          // update the parent node
                          updatedParentNode <- updateParentNode(
                                                 deletedNodeIri = nodeIri,
                                                 positionOfDeletedNode = childNode.position,
                                                 parentNodeIri = parentNodeIri,
                                                 dataNamedGraph = dataNamedGraph
                                               )

                        } yield ChildNodeDeleteResponseADM(node = updatedParentNode)

                      case _ =>
                        throw BadRequestException(
                          s"Node $nodeIri was not found. Please verify the given IRI."
                        )
                    }
      } yield response

    IriLocker.runWithIriLockZio(
      apiRequestID,
      nodeIri,
      nodeDeleteTask(nodeIri, requestingUser)
    )
  }

  ////////////////////
  // Helper Methods //
  ////////////////////

  /**
   * Helper method for checking if a list node identified by IRI exists and is a root node.
   *
   * @param rootNodeIri the IRI of the project.
   * @return a [[Boolean]].
   */
  private def rootNodeByIriExists(rootNodeIri: IRI): UIO[Boolean] = {
    val query =
      org.knora.webapi.messages.twirl.queries.sparql.admin.txt.checkListRootNodeExistsByIri(rootNodeIri).toString
    triplestoreService.sparqlHttpAsk(query).map(_.result)
  }

  /**
   * Helper method for checking if a node identified by IRI exists.
   *
   * @param nodeIri the IRI of the project.
   * @return a [[Boolean]].
   */
  private def nodeByIriExists(nodeIri: IRI): Task[Boolean] = {
    val query = org.knora.webapi.messages.twirl.queries.sparql.admin.txt.checkListNodeExistsByIri(nodeIri).toString
    triplestoreService.sparqlHttpAsk(query).map(_.result)
  }

  /**
   * Helper method for checking if a list node name is not used in any list inside a project. Returns a 'TRUE' if the
   * name is NOT used inside any list of this project.
   *
   * @param projectIri   the IRI of the project.
   * @param listNodeName the list node name.
   * @return a [[Boolean]].
   */
  private def listNodeNameIsProjectUnique(projectIri: IRI, listNodeName: Option[ListName]): Task[Boolean] =
    listNodeName.map { name =>
      val query = org.knora.webapi.messages.twirl.queries.sparql.admin.txt
        .checkListNodeNameIsProjectUnique(projectIri, name.value)
        .toString
      triplestoreService.sparqlHttpAsk(query).map(_.result)
    }.getOrElse(ZIO.succeed(true))

  /**
   * Helper method to generate a sparql statement for updating node information.
   *
   * @param changeNodeInfoRequest the node information to change.
   * @return a [[String]].
   */
  private def getUpdateNodeInfoSparqlStatement(
    changeNodeInfoRequest: ListNodeChangePayloadADM
  ): Task[String] =
    for {
      // get the data graph of the project.
      dataNamedGraph <- getDataNamedGraph(changeNodeInfoRequest.projectIri.value)

      /* verify that the list name is unique for the project */
      nodeNameUnique <- listNodeNameIsProjectUnique(
                          changeNodeInfoRequest.projectIri.value,
                          changeNodeInfoRequest.name
                        )
      _ = if (!nodeNameUnique) {
            throw DuplicateValueException(
              s"The name ${changeNodeInfoRequest.name.get} is already used by a list inside the project ${changeNodeInfoRequest.projectIri.value}."
            )
          }

      /* Verify that the node with Iri exists. */
      maybeNode <- listNodeGetADM(
                     nodeIri = changeNodeInfoRequest.listIri.value,
                     shallow = true,
                     requestingUser = KnoraSystemInstances.Users.SystemUser
                   )

      node = maybeNode.getOrElse(
               throw BadRequestException(s"List item with '${changeNodeInfoRequest.listIri}' not found.")
             )

      isRootNode = maybeNode match {
                     case Some(_: ListRootNodeADM)  => true
                     case Some(_: ListChildNodeADM) => false
                     case _                         => false
                   }

      hasOldName: Boolean = node.getName.nonEmpty

      // Update the list
      changeNodeInfoSparqlString: String = org.knora.webapi.messages.twirl.queries.sparql.admin.txt
                                             .updateListInfo(
                                               dataNamedGraph = dataNamedGraph,
                                               nodeIri = changeNodeInfoRequest.listIri.value,
                                               hasOldName = hasOldName,
                                               isRootNode = isRootNode,
                                               maybeName = changeNodeInfoRequest.name.map(_.value),
                                               projectIri = changeNodeInfoRequest.projectIri.value,
                                               listClassIri = OntologyConstants.KnoraBase.ListNode,
                                               maybeLabels = changeNodeInfoRequest.labels.map(_.value),
                                               maybeComments = changeNodeInfoRequest.comments.map(_.value)
                                             )
                                             .toString
    } yield changeNodeInfoSparqlString

  /**
   * Helper method to get projectIri of a node.
   *
   * @param nodeIri              the IRI of the node.
   * @return a [[IRI]].
   */
  private def getProjectIriFromNode(nodeIri: IRI): Task[IRI] =
    for {
      maybeNode <- listNodeGetADM(
                     nodeIri = nodeIri,
                     shallow = true,
                     requestingUser = KnoraSystemInstances.Users.SystemUser
                   )

      projectIri <- maybeNode match {
                      case Some(rootNode: ListRootNodeADM) => ZIO.attempt(rootNode.projectIri)

                      case Some(childNode: ListChildNodeADM) =>
                        for {
                          maybeRoot <- listNodeGetADM(
                                         nodeIri = childNode.hasRootNode,
                                         shallow = true,
                                         requestingUser = KnoraSystemInstances.Users.SystemUser
                                       )
                          rootProjectIri = maybeRoot match {
                                             case Some(rootNode: ListRootNodeADM) => rootNode.projectIri
                                             case _ =>
                                               throw BadRequestException(
                                                 s"Root node of $nodeIri was not found. Please verify the given IRI."
                                               )
                                           }
                        } yield rootProjectIri

                      case _ => throw BadRequestException(s"Node $nodeIri was not found. Please verify the given IRI.")
                    }
    } yield projectIri

  /**
   * Helper method to check if a node is in use.
   *
   * @param nodeIri  the IRI of the node.
   * @param errorFun a function that throws an exception. It will be called if the node is used.
   * @return a [[Boolean]].
   */
  private def isNodeUsed(nodeIri: IRI, errorFun: => Nothing): Task[Unit] = {
    val query =
      org.knora.webapi.messages.twirl.queries.sparql.admin.txt
        .isNodeUsed(nodeIri)
        .toString()
    for {
      isNodeUsedResponse <- triplestoreService.sparqlHttpSelect(query)
      _                   = if (isNodeUsedResponse.results.bindings.nonEmpty) { errorFun }
    } yield ()
  }

  /**
   * Helper method to get the data named graph of a project.
   *
   * @param projectIri           the IRI of the project.
   * @return an [[IRI]].
   */
  private def getDataNamedGraph(projectIri: IRI): Task[IRI] =
    for {
      /* Get the project information */
      maybeProject <-
        messageRelay
          .ask(
            ProjectGetADM(
              IriIdentifier
                .fromString(projectIri)
                .getOrElseWith(e => throw BadRequestException(e.head.getMessage))
            )
          )
          .map(any => any.asInstanceOf[Option[ProjectADM]])

      project: ProjectADM = maybeProject match {
                              case Some(project: ProjectADM) => project
                              case None                      => throw BadRequestException(s"Project '$projectIri' not found.")
                            }

      // Get the IRI of the named graph from which the resource will be erased.
      dataNamedGraph: IRI = stringFormatter.projectDataNamedGraphV2(project)
    } yield dataNamedGraph

  /**
   * Helper method to get parent of a node.
   *
   * @param nodeIri              the IRI of the node.
   * @return a [[ListNodeADM]].
   */
  private def getParentNodeIRI(nodeIri: IRI): Task[IRI] = {
    val query =
      org.knora.webapi.messages.twirl.queries.sparql.admin.txt
        .getParentNode(nodeIri)
        .toString
    for {
      parentNodeResponse <- triplestoreService.sparqlHttpExtendedConstruct(query)
      parentStatements = parentNodeResponse.statements.headOption.getOrElse(
                           throw BadRequestException(s"The parent node for $nodeIri not found, report this as a bug.")
                         )
      parentNodeIri = parentStatements._1.toString
    } yield parentNodeIri
  }

  /**
   * Helper method to delete a node.
   *
   * @param dataNamedGraph the data named graph of the project.
   * @param nodeIri        the IRI of the node.
   * @param isRootNode     is the node to be deleted a root node?
   *         UpdateNotPerformedException if the node could not be deleted.
   * @return a [[ListNodeADM]].
   */
  private def deleteNode(dataNamedGraph: IRI, nodeIri: IRI, isRootNode: Boolean): Task[Unit] = {
    val query =
      org.knora.webapi.messages.twirl.queries.sparql.admin.txt
        .deleteNode(
          dataNamedGraph = dataNamedGraph,
          nodeIri = nodeIri,
          isRootNode = isRootNode
        )
        .toString()
    for {
      // Do the update.
      _ <- triplestoreService.sparqlHttpUpdate(query)

      // Verify that the node was deleted correctly.
      nodeStillExists <- nodeByIriExists(nodeIri)

      _ = if (nodeStillExists) {
            throw UpdateNotPerformedException(s"Node <$nodeIri> was not erased. Please report this as a possible bug.")
          }
    } yield ()
  }

  /**
   * Helper method to update position of a node without changing its parent.
   *
   * @param nodeIri              the IRI of the node that must be shifted.
   * @param newPosition          the new position of the child node.
   * @param dataNamedGraph       the data named graph of the project.
   *         UpdateNotPerformedException if the position of the node could not be updated.
   * @return a [[ListChildNodeADM]].
   */
  private def updatePositionOfNode(
    nodeIri: IRI,
    newPosition: Int,
    dataNamedGraph: IRI
  ): Task[ListChildNodeADM] = {
    val query =
      org.knora.webapi.messages.twirl.queries.sparql.admin.txt
        .updateNodePosition(
          dataNamedGraph = dataNamedGraph,
          nodeIri = nodeIri,
          newPosition = newPosition
        )
        .toString()
    for {
      _ <- triplestoreService.sparqlHttpUpdate(query)

      /* Verify that the node info was updated */
      maybeNode <- listNodeGetADM(
                     nodeIri = nodeIri,
                     shallow = false,
                     requestingUser = KnoraSystemInstances.Users.SystemUser
                   )

      childNode: ListChildNodeADM =
        maybeNode
          .getOrElse(throw BadRequestException(s"Node with $nodeIri could not be found to update its position."))
          .asInstanceOf[ListChildNodeADM]

      _ = if (!childNode.position.equals(newPosition)) {
            throw UpdateNotPerformedException(
              s"The position of the node $nodeIri could not be updated, report this as a possible bug."
            )
          }

    } yield childNode
  }

  /**
   * Helper method to shift nodes between positions startPos and endPos to the left if 'shiftToLeft' is true,
   * otherwise shift them one position to the right.
   *
   * @param startPos             the position of first node in range that must be shifted.
   * @param endPos               the position of last node in range that must be shifted.
   * @param nodes                the list of all nodes.
   * @param shiftToLeft          shift nodes to left if true, otherwise to right.
   * @param dataNamedGraph       the data named graph of the project.
   *         UpdateNotPerformedException if the position of a node could not be updated.
   * @return a sequence of [[ListChildNodeADM]].
   */
  private def shiftNodes(
    startPos: Int,
    endPos: Int,
    nodes: Seq[ListChildNodeADM],
    shiftToLeft: Boolean,
    dataNamedGraph: IRI
  ): Task[Seq[ListChildNodeADM]] = {
    val nodesTobeUpdated = nodes.filter(node => node.position >= startPos && node.position <= endPos)
    val staticStartNodes = nodes.filter(node => node.position < startPos)
    val staticEndNotes   = nodes.filter(node => node.position > endPos)
    val updatePositionZios = nodesTobeUpdated.map { child =>
      val currPos = child.position
      val newPos = if (shiftToLeft) {
        currPos - 1
      } else currPos + 1

      updatePositionOfNode(
        nodeIri = child.id,
        newPosition = newPos,
        dataNamedGraph = dataNamedGraph
      )
    }
    for {
      updatedNodes <- ZioHelper.sequence(updatePositionZios)
    } yield staticStartNodes ++ updatedNodes ++ staticEndNotes
  }

  /**
   * Helper method to change parent node of a node.
   *
   * @param nodeIri              the IRI of the node.
   * @param oldParentIri         the IRI of the current parent node.
   * @param newParentIri         the IRI of the new parent node.
   * @param dataNamedGraph       the data named graph of the project.
   *         UpdateNotPerformedException if the parent of a node could not be updated.
   */
  private def changeParentNode(
    nodeIri: IRI,
    oldParentIri: IRI,
    newParentIri: IRI,
    dataNamedGraph: IRI
  ): Task[Unit] = {
    val query =
      org.knora.webapi.messages.twirl.queries.sparql.admin.txt
        .changeParentNode(
          dataNamedGraph = dataNamedGraph,
          nodeIri = nodeIri,
          currentParentIri = oldParentIri,
          newParentIri = newParentIri
        )
        .toString()
    for {
      _ <- triplestoreService.sparqlHttpUpdate(query)

      /* verify that parents were updated */
      // get old parent node with its immediate children
      maybeOldParent <- listNodeGetADM(
                          nodeIri = oldParentIri,
                          shallow = true,
                          requestingUser = KnoraSystemInstances.Users.SystemUser
                        )
      childrenOfOldParent = maybeOldParent.get.getChildren
      _ = if (childrenOfOldParent.exists(node => node.id == nodeIri)) {
            throw UpdateNotPerformedException(
              s"Node $nodeIri is still a child of $oldParentIri. Report this as a bug."
            )
          }
      // get new parent node with its immediate children
      maybeNewParentNode <- listNodeGetADM(
                              nodeIri = newParentIri,
                              shallow = true,
                              requestingUser = KnoraSystemInstances.Users.SystemUser
                            )
      childrenOfNewParent = maybeNewParentNode.get.getChildren
      _ = if (!childrenOfNewParent.exists(node => node.id == nodeIri)) {
            throw UpdateNotPerformedException(s"Node $nodeIri is not added to parent node $newParentIri. ")
          }

    } yield ()
  }
}
object ListsResponderADMLive {
  val layer: URLayer[
    EntityAndClassIriService with AppConfig with MessageRelay with StringFormatter with TriplestoreService,
    ListsResponderADMLive
  ] = ZLayer.fromZIO {
    for {
      ts         <- ZIO.service[TriplestoreService]
      sf         <- ZIO.service[StringFormatter]
      mr         <- ZIO.service[MessageRelay]
      appConfig  <- ZIO.service[AppConfig]
      iriService <- ZIO.service[EntityAndClassIriService]
      responder   = ListsResponderADMLive(appConfig, iriService, mr, ts, sf)
      _          <- mr.subscribe(responder)
    } yield responder
  }
}
