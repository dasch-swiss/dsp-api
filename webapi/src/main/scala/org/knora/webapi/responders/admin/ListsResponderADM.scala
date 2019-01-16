/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.responders.admin

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectGetADM}
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.responders.Responder.handleUnexpectedMessage
import org.knora.webapi.responders.admin.ListsResponderADM._
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.util.KnoraIdUtil

import scala.annotation.tailrec
import scala.collection.breakOut
import scala.concurrent.Future

object ListsResponderADM {

    val LIST_IRI_MISSING_ERROR = "List IRI cannot be empty."
    val LIST_IRI_INVALID_ERROR = "List IRI cannot be empty."
    val LIST_NODE_IRI_MISSING_ERROR = "List node IRI cannot be empty."
    val LIST_NODE_IRI_INVALID_ERROR = "List node IRI is invalid."
    val PROJECT_IRI_MISSING_ERROR = "Project IRI cannot be empty."
    val PROJECT_IRI_INVALID_ERROR = "Project IRI is invalid."
    val LABEL_MISSING_ERROR = "At least one label needs to be supplied."
    val LIST_CREATE_PERMISSION_ERROR = "A list can only be created by the project or system administrator."
    val LIST_CHANGE_PERMISSION_ERROR = "A list can only be changed by the project or system administrator."
    val REQUEST_NOT_CHANGING_DATA_ERROR = "No data would be changed."
}

/**
  * A responder that returns information about hierarchical lists.
  */
class ListsResponderADM(system: ActorSystem, applicationStateActor: ActorRef, responderManager: ActorRef, storeManager: ActorRef) extends Responder(system, applicationStateActor, responderManager, storeManager) {


    // Creates IRIs for new Knora user objects.
    private val knoraIdUtil = new KnoraIdUtil

    // The IRI used to lock user creation and update
    private val LISTS_GLOBAL_LOCK_IRI = "http://rdfh.ch/lists"

    /**
      * Receives a message of type [[ListsResponderRequestADM]], and returns an appropriate response message.
      */
    def receive(msg: ListsResponderRequestADM) = msg match {
        case ListsGetRequestADM(projectIri, requestingUser) => listsGetRequestADM(projectIri, requestingUser)
        case ListGetRequestADM(listIri, requestingUser) => listGetRequestADM(listIri, requestingUser)
        case ListInfoGetRequestADM(listIri, requestingUser) => listInfoGetRequestADM(listIri, requestingUser)
        case ListNodeInfoGetRequestADM(listIri, requestingUser) => listNodeInfoGetRequestADM(listIri, requestingUser)
        case NodePathGetRequestADM(iri, requestingUser) => nodePathGetAdminRequest(iri, requestingUser)
        case ListCreateRequestADM(createListRequest, requestingUser, apiRequestID) => listCreateRequestADM(createListRequest, requestingUser, apiRequestID)
        case ListInfoChangeRequestADM(listIri, changeListRequest, requestingUser, apiRequestID) => listInfoChangeRequest(listIri, changeListRequest, requestingUser, apiRequestID)
        case ListChildNodeCreateRequestADM(parentNodeIri, createListNodeRequest, requestingUser, apiRequestID) => listChildNodeCreateRequestADM(parentNodeIri, createListNodeRequest, requestingUser, apiRequestID)
        case other => handleUnexpectedMessage(other, log, this.getClass.getName)
    }


    /**
      * Gets all lists and returns them as a [[ListsGetResponseADM]]. For performance reasons
      * (as lists can be very large), we only return the head of the list, i.e. the root node without
      * any children.
      *
      * @param projectIri  the IRI of the project the list belongs to.
      * @param requestingUser the user making the request.
      * @return a [[ListsGetResponseADM]].
      */
    private def listsGetRequestADM(projectIri: Option[IRI], requestingUser: UserADM): Future[ListsGetResponseADM] = {

        // log.debug("listsGetRequestV2")

        for {
            sparqlQuery <- Future(queries.sparql.admin.txt.getLists(
                triplestore = settings.triplestoreType,
                maybeProjectIri = projectIri
            ).toString())

            listsResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            // _ = log.debug("listsGetAdminRequest - listsResponse: {}", listsResponse )

            // Seq(subjectIri, (objectIri -> Seq(stringWithOptionalLand))
            statements = listsResponse.statements.toList

            lists: Seq[ListNodeInfoADM] = statements.map {
                case (listIri: SubjectV2, propsMap: Map[IRI, Seq[LiteralV2]]) =>

                    val name: Option[String] = propsMap.get(OntologyConstants.KnoraBase.ListNodeName).map(_.head.asInstanceOf[StringLiteralV2].value)
                    val labels: Seq[StringLiteralV2] = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2])
                    val comments: Seq[StringLiteralV2] = propsMap.getOrElse(OntologyConstants.Rdfs.Comment, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2])

                    ListRootNodeInfoADM(
                        id = listIri.toString,
                        projectIri = propsMap.getOrElse(OntologyConstants.KnoraBase.AttachedToProject, throw InconsistentTriplestoreDataException("The required property 'attachedToProject' not found.")).head.asInstanceOf[IriLiteralV2].value,
                        name = name,
                        labels = StringLiteralSequenceV2(labels.toVector.sortBy(_.language)),
                        comments = StringLiteralSequenceV2(comments.toVector.sortBy(_.language))
                    )
            }

            // _ = log.debug("listsGetAdminRequest - items: {}", items)

        } yield ListsGetResponseADM(lists = lists)
    }

    /**
      * Retrieves a complete list (root and all children) from the triplestore and returns it as a optional [[ListADM]].
      *
      * @param rootNodeIri the Iri if the root node of the list to be queried.
      * @param requestingUser the user making the request.
      * @return a optional [[ListADM]].
      */
    private def listGetADM(rootNodeIri: IRI, requestingUser: UserADM): Future[Option[ListADM]] = {

        for {
            // this query will give us only the information about the root node.
            exists <- listRootNodeByIriExists(rootNodeIri)

            // _ = log.debug(s"listGetADM - exists: {}", exists)

            maybeList: Option[ListADM] <- if (exists) {
                for {
                    // here we know that the list exists and it is fine if children is an empty list
                    children: Seq[ListChildNodeADM] <- getChildren(rootNodeIri, shallow = false, KnoraSystemInstances.Users.SystemUser)

                    maybeRootNodeInfo <- listNodeInfoGetADM(rootNodeIri, KnoraSystemInstances.Users.SystemUser)

                    // _ = log.debug(s"listGetADM - maybeRootNodeInfo: {}", maybeRootNodeInfo)

                    rootNodeInfo = maybeRootNodeInfo match {
                        case Some(info: ListRootNodeInfoADM) => info.asInstanceOf[ListRootNodeInfoADM]
                        case Some(info: ListChildNodeInfoADM) => throw InconsistentTriplestoreDataException("A child node info was found, although we are expecting a root node info. Please report this as a possible bug.")
                        case Some(_) | None => throw InconsistentTriplestoreDataException("No info about list node found, although list node should exist. Please report this as a possible bug.")
                    }

                    list = ListADM(listinfo = rootNodeInfo, children = children)
                } yield Some(list)
            } else {
                FastFuture.successful(None)
            }

        } yield maybeList
    }

    /**
      * Retrieves a complete list (root and all children) from the triplestore and returns it as a [[ListGetResponseADM]].
      *
      * @param rootNodeIri the Iri if the root node of the list to be queried.
      * @param requestingUser the user making the request.
      * @return a [[ListGetResponseADM]].
      */
    private def listGetRequestADM(rootNodeIri: IRI, requestingUser: UserADM): Future[ListGetResponseADM] = {

        for {
            maybeListADM <- listGetADM(rootNodeIri, requestingUser)
            result = maybeListADM match {
                case Some(list) => ListGetResponseADM(list = list)
                case None => throw NotFoundException(s"List '$rootNodeIri' not found")
            }
        } yield result
    }

    /**
      * Retrieves information about a list (root) node.
      *
      * @param listIri the Iri if the list (root node) to be queried.
      * @param requestingUser the user making the request.
      * @return a [[ListInfoGetResponseADM]].
      */
    private def listInfoGetRequestADM(listIri: IRI, requestingUser: UserADM): Future[ListInfoGetResponseADM] = {
        for {
            listNodeInfo <- listNodeInfoGetADM(nodeIri = listIri, requestingUser = requestingUser)

            // _ = log.debug(s"listInfoGetRequestADM - listNodeInfo: {}", listNodeInfo)

            listRootNodeInfo = listNodeInfo match {
                case Some(value: ListRootNodeInfoADM) => value
                case Some(value: ListChildNodeInfoADM) => throw BadRequestException(s"The supplied IRI $listIri does not belong to a list but to a list child node.")
                case Some(_) | None => throw NotFoundException(s"List $listIri not found.")
            }

            // _ = log.debug(s"listInfoGetRequestADM - node: {}", MessageUtil.toSource(node))

        } yield ListInfoGetResponseADM(listinfo = listRootNodeInfo)
    }

    /**
      * Retrieves information about a single node (without information about children). The single node can be the
      * lists root node or child node
      *
      * @param nodeIri the Iri if the list node to be queried.
      * @param requestingUser the user making the request.
      * @return a optional [[ListNodeInfoADM]].
      */
    private def listNodeInfoGetADM(nodeIri: IRI, requestingUser: UserADM): Future[Option[ListNodeInfoADM]] = {
        for {
            sparqlQuery <- Future(queries.sparql.admin.txt.getListNode(
                triplestore = settings.triplestoreType,
                nodeIri = nodeIri
            ).toString())

            // _ = log.debug("listNodeInfoGetADM - sparqlQuery: {}", sparqlQuery)

            listNodeResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            statements: Map[SubjectV2, Map[IRI, Seq[LiteralV2]]] = listNodeResponse.statements

            // _ = log.debug(s"listNodeInfoGetADM - statements: {}", statements)

            maybeListNodeInfo = if (statements.nonEmpty) {

                // Map(subjectIri -> (objectIri -> Seq(stringWithOptionalLand))

                val nodeInfo: ListNodeInfoADM = statements.head match {
                    case (nodeIri: SubjectV2, propsMap: Map[IRI, Seq[LiteralV2]]) =>

                        val labels: Seq[StringLiteralV2] = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2])
                        val comments: Seq[StringLiteralV2] = propsMap.getOrElse(OntologyConstants.Rdfs.Comment, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2])

                        val attachedToProjectOption: Option[IRI] = propsMap.get(OntologyConstants.KnoraBase.AttachedToProject) match {
                            case Some(iris: Seq[LiteralV2]) =>
                                iris.headOption match {
                                    case Some(iri: IriLiteralV2) => Some(iri.value)
                                    case other => throw InconsistentTriplestoreDataException(s"Expected attached to project Iri as an IriLiteralV2 for list node $nodeIri, but got $other")
                                }
                            case None => None
                        }

                        val hasRootNodeOption: Option[IRI] = propsMap.get(OntologyConstants.KnoraBase.HasRootNode) match {
                            case Some(iris: Seq[LiteralV2]) =>
                                iris.headOption match {
                                    case Some(iri: IriLiteralV2) => Some(iri.value)
                                    case other => throw InconsistentTriplestoreDataException(s"Expected root node Iri as an IriLiteralV2 for list node $nodeIri, but got $other")
                                }
                            case None => None
                        }

                        val isRootNode: Boolean = propsMap.get(OntologyConstants.KnoraBase.IsRootNode) match {
                            case Some(values: Seq[LiteralV2]) =>
                                values.headOption match {
                                    case Some(value: BooleanLiteralV2) => value.value
                                    case Some(other) => throw InconsistentTriplestoreDataException(s"Expected isRootNode as an BooleanLiteralV2 for list node $nodeIri, but got $other")
                                    case None => false
                                }
                            case None => false
                        }

                        val positionOption: Option[Int] = propsMap.get(OntologyConstants.KnoraBase.ListNodePosition).map(_.head.asInstanceOf[IntLiteralV2].value)

                        if (isRootNode) {
                            ListRootNodeInfoADM(
                                id = nodeIri.toString,
                                projectIri = attachedToProjectOption.getOrElse(throw InconsistentTriplestoreDataException(s"Required attachedToProject property missing for list node $nodeIri.")),
                                name = propsMap.get(OntologyConstants.KnoraBase.ListNodeName).map(_.head.asInstanceOf[StringLiteralV2].value),
                                labels = StringLiteralSequenceV2(labels.toVector.sortBy(_.language)),
                                comments = StringLiteralSequenceV2(comments.toVector.sortBy(_.language))
                            )
                        } else {
                            ListChildNodeInfoADM (
                                id = nodeIri.toString,
                                name = propsMap.get(OntologyConstants.KnoraBase.ListNodeName).map(_.head.asInstanceOf[StringLiteralV2].value),
                                labels = StringLiteralSequenceV2(labels.toVector.sortBy(_.language)),
                                comments = StringLiteralSequenceV2(comments.toVector.sortBy(_.language)),
                                position = positionOption.getOrElse(throw InconsistentTriplestoreDataException(s"Required position property missing for list node $nodeIri.")),
                                hasRootNode = hasRootNodeOption.getOrElse(throw InconsistentTriplestoreDataException(s"Required hasRootNode property missing for list node $nodeIri."))
                            )
                        }
                }
                Some(nodeInfo)
            } else {
                None
            }

            // _ = log.debug(s"listNodeInfoGetADM - maybeListNodeInfo: {}", maybeListNodeInfo)

        } yield maybeListNodeInfo

    }

    /**
      * Retrieves information about a single node (without information about children). The single node can be the
      * lists root node or child node
      *
      * @param nodeIri the IRI of the list node to be queried.
      * @param requestingUser the user making the request.
      * @return a [[ListNodeInfoGetResponseADM]].
      */
    private def listNodeInfoGetRequestADM(nodeIri: IRI, requestingUser: UserADM): Future[ListNodeInfoGetResponseADM] = {
        for {
            maybeListNodeInfoADM: Option[ListNodeInfoADM] <- listNodeInfoGetADM(nodeIri, requestingUser)
            result = maybeListNodeInfoADM match {
                case Some(nodeInfo) => ListNodeInfoGetResponseADM(nodeinfo = nodeInfo)
                case None => throw NotFoundException(s"List node '$nodeIri' not found")
            }
        } yield result
    }


    /**
      * Retrieves a complete node including children. The node can be the lists root node or child node.
      *
      * @param nodeIri the IRI of the list node to be queried.
      * @param shallow  denotes if all children or only the immediate children will be returned.
      * @param requestingUser the user making the request.
      * @return a optional [[ListNodeADM]]
      */
    private def listNodeGetADM(nodeIri: IRI, shallow: Boolean, requestingUser: UserADM): Future[Option[ListNodeADM]] = {
        for {
            // this query will give us only the information about the root node.
            sparqlQuery <- Future(queries.sparql.admin.txt.getListNode(
                triplestore = settings.triplestoreType,
                nodeIri = nodeIri
            ).toString())

            listInfoResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            // _ = log.debug(s"listGetADM - statements: {}", MessageUtil.toSource(listInfoResponse.statements))

            maybeListNode: Option[ListNodeADM] <- if (listInfoResponse.statements.nonEmpty) {
                for {
                    // here we know that the list exists and it is fine if children is an empty list
                    children: Seq[ListChildNodeADM] <- getChildren(nodeIri, shallow, requestingUser)

                    // _ = log.debug(s"listGetADM - children count: {}", children.size)

                    // Map(subjectIri -> (objectIri -> Seq(stringWithOptionalLand))
                    statements = listInfoResponse.statements

                    node: ListNodeADM = statements.head match {
                        case (nodeIri: SubjectV2, propsMap: Map[IRI, Seq[LiteralV2]]) =>

                            val labels: Seq[StringLiteralV2] = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2])
                            val comments: Seq[StringLiteralV2] = propsMap.getOrElse(OntologyConstants.Rdfs.Comment, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2])

                            val attachedToProjectOption: Option[IRI] = propsMap.get(OntologyConstants.KnoraBase.AttachedToProject) match {
                                case Some(iris: Seq[LiteralV2]) =>
                                    iris.headOption match {
                                        case Some(iri: IriLiteralV2) => Some(iri.value)
                                        case other => throw InconsistentTriplestoreDataException(s"Expected attached to project Iri as an IriLiteralV2 for list node $nodeIri, but got $other")
                                    }
                                case None => None
                            }

                            val hasRootNodeOption: Option[IRI] = propsMap.get(OntologyConstants.KnoraBase.HasRootNode) match {
                                case Some(iris: Seq[LiteralV2]) =>
                                    iris.headOption match {
                                        case Some(iri: IriLiteralV2) => Some(iri.value)
                                        case other => throw InconsistentTriplestoreDataException(s"Expected root node Iri as an IriLiteralV2 for list node $nodeIri, but got $other")
                                    }
                                case None => None
                            }

                            val isRootNode: Boolean = propsMap.get(OntologyConstants.KnoraBase.IsRootNode) match {
                                case Some(values: Seq[LiteralV2]) =>
                                    values.headOption match {
                                        case Some(value: BooleanLiteralV2) => value.value
                                        case Some(other) => throw InconsistentTriplestoreDataException(s"Expected isRootNode as an BooleanLiteralV2 for list node $nodeIri, but got $other")
                                        case None => false
                                    }
                                case None => false
                            }

                            val positionOption: Option[Int] = propsMap.get(OntologyConstants.KnoraBase.ListNodePosition).map(_.head.asInstanceOf[IntLiteralV2].value)

                            if (isRootNode) {
                                ListRootNodeADM(
                                    id = nodeIri.toString,
                                    projectIri = attachedToProjectOption.getOrElse(throw InconsistentTriplestoreDataException(s"Required attachedToProject property missing for list node $nodeIri.")),
                                    name = propsMap.get(OntologyConstants.KnoraBase.ListNodeName).map(_.head.asInstanceOf[StringLiteralV2].value),
                                    labels = StringLiteralSequenceV2(labels.toVector.sortBy(_.language)),
                                    comments = StringLiteralSequenceV2(comments.toVector.sortBy(_.language)),
                                    children = children
                                )
                            } else {
                                ListChildNodeADM (
                                    id = nodeIri.toString,
                                    name = propsMap.get(OntologyConstants.KnoraBase.ListNodeName).map(_.head.asInstanceOf[StringLiteralV2].value),
                                    labels = StringLiteralSequenceV2(labels.toVector.sortBy(_.language)),
                                    comments = StringLiteralSequenceV2(comments.toVector.sortBy(_.language)),
                                    position = positionOption.getOrElse(throw InconsistentTriplestoreDataException(s"Required position property missing for list node $nodeIri.")),
                                    hasRootNode = hasRootNodeOption.getOrElse(throw InconsistentTriplestoreDataException(s"Required hasRootNode property missing for list node $nodeIri.")),
                                    children = children
                                )
                            }
                    }

                    // _ = log.debug(s"listGetADM - list: {}", MessageUtil.toSource(list))
                } yield Some(node)
            } else {
                FastFuture.successful(None)
            }

        } yield maybeListNode
    }


    /**
      * Retrieves the child nodes from the triplestore. If shallow is true, then only the immediate children will be
      * returned, otherwise all children and their children's children will be returned.
      *
      * @param nodeIri the IRI of the node for which children are to be returned.
      * @param shallow denotes if all children or only the immediate children will be returned.
      * @param requestingUser the user making the request.
      * @return a sequence of [[ListChildNodeADM]].
      */
    private def getChildren(ofNodeIri: IRI, shallow: Boolean, requestingUser: UserADM): Future[Seq[ListChildNodeADM]] = {

        /**
          * This function recursively transforms SPARQL query results representing a hierarchical list into a [[ListChildNodeADM]].
          *
          * @param nodeIri          the IRI of the node to be created.
          * @param groupedByNodeIri a [[Map]] in which each key is the IRI of a node in the hierarchical list, and each value is a [[Seq]]
          *                         of SPARQL query results representing that node's children.
          * @return a [[ListChildNodeADM]].
          */
        def createChildNode(nodeIri: IRI, statements: Seq[(SubjectV2, Map[IRI, Seq[LiteralV2]])]): ListChildNodeADM = {

            val propsMap: Map[IRI, Seq[LiteralV2]] = statements.filter(_._1 == IriSubjectV2(nodeIri)).head._2

            val hasRootNode: IRI = propsMap.getOrElse(OntologyConstants.KnoraBase.HasRootNode, throw InconsistentTriplestoreDataException(s"Required hasRootNode property missing for list node $nodeIri.")).head.toString

            val nameOption = propsMap.get(OntologyConstants.KnoraBase.ListNodeName).map(_.head.asInstanceOf[StringLiteralV2].value)

            val labels: Seq[StringLiteralV2] = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2])
            val comments: Seq[StringLiteralV2] = propsMap.getOrElse(OntologyConstants.Rdfs.Comment, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2])

            val positionOption: Option[Int] = propsMap.get(OntologyConstants.KnoraBase.ListNodePosition).map(_.head.asInstanceOf[IntLiteralV2].value)
            val position = positionOption.getOrElse(throw InconsistentTriplestoreDataException(s"Required position property missing for list node $nodeIri."))

            val children: Seq[ListChildNodeADM] = propsMap.get(OntologyConstants.KnoraBase.HasSubListNode) match {
                case Some(iris: Seq[LiteralV2]) => {

                    if (!shallow) {
                        // if not shallow then get the children of this node
                        iris.map {
                            iri => createChildNode(iri.toString, statements)
                        }
                    } else {
                        // if shallow, then we don't need the children
                        Seq.empty[ListChildNodeADM]
                    }
                }
                case None => Seq.empty[ListChildNodeADM]
            }

            ListChildNodeADM(
                id = nodeIri,
                name = nameOption,
                labels = StringLiteralSequenceV2(labels.toVector.sortBy(_.language)),
                comments = StringLiteralSequenceV2(comments.toVector.sortBy(_.language)),
                children = children.map(_.sorted),
                position = position,
                hasRootNode = hasRootNode
            )
        }

        for {
            nodeChildrenQuery <- Future {
                queries.sparql.admin.txt.getListNodeWithChildren(
                    triplestore = settings.triplestoreType,
                    startNodeIri = ofNodeIri
                ).toString()
            }
            nodeWithChildrenResponse <- (storeManager ? SparqlExtendedConstructRequest(nodeChildrenQuery)).mapTo[SparqlExtendedConstructResponse]

            statements: Seq[(SubjectV2, Map[IRI, Seq[LiteralV2]])] = nodeWithChildrenResponse.statements.toList

            startNodePropsMap: Map[IRI, Seq[LiteralV2]] = statements.filter(_._1 == IriSubjectV2(ofNodeIri)).head._2

            children: Seq[ListChildNodeADM] = startNodePropsMap.get(OntologyConstants.KnoraBase.HasSubListNode) match {
                case Some(iris: Seq[LiteralV2]) => iris.map {
                    iri => createChildNode(iri.toString, statements)
                }
                case None => Seq.empty[ListChildNodeADM]
            }

            sortedChildren = children.sortBy(_.position) map (_.sorted)

        } yield sortedChildren
    }

    /**
      * Provides the path to a particular hierarchical list node.
      *
      * @param queryNodeIri the IRI of the node whose path is to be queried.
      * @param requestingUser  the user making the request.
      */
    private def nodePathGetAdminRequest(queryNodeIri: IRI, requestingUser: UserADM): Future[NodePathGetResponseADM] = {
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
        def makePath(node: IRI, nodeMap: Map[IRI, Map[String, String]], parentMap: Map[IRI, IRI], path: Seq[NodePathElementADM]): Seq[NodePathElementADM] = {
            // Get the details of the node.
            val nodeData = nodeMap(node)

            // Construct a NodePathElementV2 containing those details.
            val pathElement = NodePathElementADM (
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

        // TODO: Rewrite using a construct sparql query
        for {
            nodePathQuery <- Future {
                queries.sparql.admin.txt.getNodePath(
                    triplestore = settings.triplestoreType,
                    queryNodeIri = queryNodeIri,
                    preferredLanguage = requestingUser.lang,
                    fallbackLanguage = settings.fallbackLanguage
                ).toString()
            }
            nodePathResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(nodePathQuery)).mapTo[SparqlSelectResponse]

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
            nodeMap: Map[IRI, Map[String, String]] = nodePathResponse.results.bindings.map {
                row => row.rowMap("node") -> row.rowMap
            }(breakOut)

            // A Map of child node IRIs to parent node IRIs.
            parentMap: Map[IRI, IRI] = nodePathResponse.results.bindings.foldLeft(Map.empty[IRI, IRI]) {
                case (acc, row) =>
                    row.rowMap.get("child") match {
                        case Some(child) => acc + (child -> row.rowMap("node"))
                        case None => acc
                    }
            }
        } yield NodePathGetResponseADM(elements = makePath(queryNodeIri, nodeMap, parentMap, Nil))
    }


    /**
      * Creates a list.
      *
      * @param createListRequest the new list's information.
      * @param requestingUser the user that is making the request.
      * @param apiRequestID   the unique api request ID.
      * @return a [[ListInfoGetResponseADM]]
      * @throws ForbiddenException in the case that the user is not allowed to perform the operation.
      * @throws BadRequestException in the case when the project IRI or label is missing or invalid.
      */
    private def listCreateRequestADM(createListRequest: CreateListApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[ListGetResponseADM] = {

        /**
          * The actual task run with an IRI lock.
          */
        def listCreateTask(createListRequest: CreateListApiRequestADM, requestingUser: UserADM, apiRequestID: UUID) = for {

            // check if the requesting user is allowed to perform operation
            _ <- Future(
                if (!requestingUser.permissions.isProjectAdmin(createListRequest.projectIri) && !requestingUser.permissions.isSystemAdmin) {
                    // not project or a system admin
                    // log.debug("same user: {}, system admin: {}", userProfile.userData.user_id.contains(userIri), userProfile.permissionData.isSystemAdmin)
                    throw ForbiddenException(LIST_CREATE_PERMISSION_ERROR)
                }
            )

            /* Verify that the project exists. */
            maybeProject <- (responderManager ? ProjectGetADM(maybeIri = Some(createListRequest.projectIri), maybeShortname = None, maybeShortcode = None, KnoraSystemInstances.Users.SystemUser)).mapTo[Option[ProjectADM]]
            project: ProjectADM = maybeProject match {
                case Some(project: ProjectADM) => project
                case None => throw BadRequestException(s"Project '${createListRequest.projectIri}' not found.")
            }

            /* verify that the list node name is unique for the project */
            projectUniqueNodeName <- listNodeNameIsProjectUnique(createListRequest.projectIri, createListRequest.name)
            _ = if (!projectUniqueNodeName) {
                throw BadRequestException(s"The node name ${createListRequest.name.get} is already by a list inside the project ${createListRequest.projectIri}.")
            }

            maybeShortcode = project.shortcode
            dataNamedGraph = stringFormatter.projectDataNamedGraphV2(project)

            listIri = knoraIdUtil.makeRandomListIri(maybeShortcode)

            // Create the new list
            createNewListSparqlString = queries.sparql.admin.txt.createNewList(
                dataNamedGraph = dataNamedGraph,
                triplestore = settings.triplestoreType,
                listIri = listIri,
                projectIri = project.id,
                listClassIri = OntologyConstants.KnoraBase.ListNode,
                maybeName = createListRequest.name,
                maybeLabels = createListRequest.labels,
                maybeComments = createListRequest.comments
            ).toString
            // _ = log.debug("listCreateRequestADM - createNewListSparqlString: {}", createNewListSparqlString)
            createResourceResponse <- (storeManager ? SparqlUpdateRequest(createNewListSparqlString)).mapTo[SparqlUpdateResponse]


            // Verify that the list was created.
            maybeNewListADM <- listGetADM(listIri, KnoraSystemInstances.Users.SystemUser)
            newListADM = maybeNewListADM.getOrElse(throw UpdateNotPerformedException(s"List $listIri was not created. Please report this as a possible bug."))

            // _ = log.debug(s"listCreateRequestADM - newListADM: $newListADM")

        } yield ListGetResponseADM(list = newListADM)

        for {
            // run list creation with an global IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                LISTS_GLOBAL_LOCK_IRI,
                () => listCreateTask(createListRequest, requestingUser, apiRequestID)
            )
        } yield taskResult
    }

    /**
      * Changes basic list information stored in the list's root node.
      *
      * @param listIri the list's IRI.
      * @param changeListRequest the new list information.
      * @param requestingUser the user that is making the request.
      * @param apiRequestID the unique api request ID.
      * @return a [[ListInfoGetResponseADM]]
      * @throws ForbiddenException in the case that the user is not allowed to perform the operation.
      * @throws BadRequestException in the case when the project IRI is missing or invalid.
      * @throws UpdateNotPerformedException in the case something else went wrong, and the change could not be performed.
      */
    private def listInfoChangeRequest(listIri: IRI, changeListRequest: ChangeListInfoApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[ListInfoGetResponseADM] = {

        /**
          * The actual task run with an IRI lock.
          */
        def listInfoChangeTask(listIri: IRI, changeListRequest: ChangeListInfoApiRequestADM, requestingUser: UserADM, apiRequestID: UUID) = for {
            // check if required information is supplied
            _ <- Future(if (changeListRequest.labels.isEmpty && changeListRequest.comments.isEmpty) throw BadRequestException(REQUEST_NOT_CHANGING_DATA_ERROR))
            _ = if (!listIri.equals(changeListRequest.listIri)) throw BadRequestException("List IRI in path and payload don't match.")

            // check if the requesting user is allowed to perform operation
            _ <- Future(
                if (!requestingUser.permissions.isProjectAdmin(changeListRequest.projectIri) && !requestingUser.permissions.isSystemAdmin) {
                    // not project or a system admin
                    // log.debug("same user: {}, system admin: {}", userProfile.userData.user_id.contains(userIri), userProfile.permissionData.isSystemAdmin)
                    throw ForbiddenException(LIST_CHANGE_PERMISSION_ERROR)
                }
            )

            /* Verify that the list exists. */
            maybeList <- listGetADM(rootNodeIri = listIri, requestingUser = KnoraSystemInstances.Users.SystemUser)
            list: ListADM = maybeList match {
                case Some(list: ListADM) => list
                case None => throw BadRequestException(s"List '$listIri' not found.")
            }

            /* Get the project information */
            maybeProject <- (responderManager ? ProjectGetADM(maybeIri = Some(list.listinfo.projectIri), maybeShortname = None, maybeShortcode = None, KnoraSystemInstances.Users.SystemUser)).mapTo[Option[ProjectADM]]
            project: ProjectADM = maybeProject match {
                case Some(project: ProjectADM) => project
                case None => throw BadRequestException(s"Project '${list.listinfo.projectIri}' not found.")
            }

            // get the data graph of the project.
            dataNamedGraph = stringFormatter.projectDataNamedGraphV2(project)

            // Update the list
            changeListInfoSparqlString = queries.sparql.admin.txt.updateListInfo(
                dataNamedGraph = dataNamedGraph,
                triplestore = settings.triplestoreType,
                listIri = listIri,
                projectIri = project.id,
                listClassIri = OntologyConstants.KnoraBase.ListNode,
                maybeLabels = changeListRequest.labels,
                maybeComments = changeListRequest.comments
            ).toString
            // _ = log.debug("listCreateRequestADM - createNewListSparqlString: {}", createNewListSparqlString)
            changeResourceResponse <- (storeManager ? SparqlUpdateRequest(changeListInfoSparqlString)).mapTo[SparqlUpdateResponse]


            /* Verify that the list was updated */
            maybeListADM <- listGetADM(listIri, KnoraSystemInstances.Users.SystemUser)
            updatedList = maybeListADM.getOrElse(throw UpdateNotPerformedException(s"List $listIri was not updated. Please report this as a possible bug."))


            _ = if (changeListRequest.labels.nonEmpty) {
                if (updatedList.listinfo.labels.stringLiterals.sorted != changeListRequest.labels.sorted) throw UpdateNotPerformedException("Lists's 'labels' where not updated. Please report this as a possible bug.")
            }

            _ = if (changeListRequest.comments.nonEmpty) {
                if (updatedList.listinfo.comments.stringLiterals.sorted != changeListRequest.comments.sorted) throw UpdateNotPerformedException("List's 'comments' was not updated. Please report this as a possible bug.")
            }

            // _ = log.debug(s"listInfoChangeRequest - updatedList: {}", updatedList)

        } yield ListInfoGetResponseADM(listinfo = updatedList.listinfo)

        for {
            // run list info update with an local IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                listIri,
                () => listInfoChangeTask(listIri, changeListRequest, requestingUser, apiRequestID)
            )
        } yield taskResult
    }


    /**
      * Creates a new list node and appends it to an existing list node.
      *
      * @param listNodeIri the existing list node to which we want to append.
      * @param createListNodeRequest the new list node's information.
      * @param requestingUser the user making the request.
      * @param apiRequestID the unique api request ID.
      * @return a [[ListNodeInfoGetResponseADM]]
      */
    private def listChildNodeCreateRequestADM(parentNodeIri: IRI, createChildNodeRequest: CreateChildNodeApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[ListNodeInfoGetResponseADM] = {

        /**
          * The actual task run with an IRI lock.
          */
        def listChildNodeCreateTask(createChildNodeRequest: CreateChildNodeApiRequestADM, requestingUser: UserADM, apiRequestID: UUID) = for {

            // check if the requesting user is allowed to perform operation
            _ <- Future(
                if (!requestingUser.permissions.isProjectAdmin(createChildNodeRequest.projectIri) && !requestingUser.permissions.isSystemAdmin) {
                    // not project or a system admin
                    // log.debug("same user: {}, system admin: {}", userProfile.userData.user_id.contains(userIri), userProfile.permissionData.isSystemAdmin)
                    throw ForbiddenException(LIST_CREATE_PERMISSION_ERROR)
                }
            )

            _ = if (!parentNodeIri.equals(createChildNodeRequest.parentNodeIri)) throw BadRequestException("List node IRI in path and payload don't match.")

            /* Verify that the list node exists by retrieving the whole node including children one level deep (need for position calculation) */
            maybeParentListNode <- listNodeGetADM(parentNodeIri, shallow = true, requestingUser = KnoraSystemInstances.Users.SystemUser)
            (parentListNode, children) = maybeParentListNode match {
                case Some(node: ListRootNodeADM) => (node.asInstanceOf[ListRootNodeADM], node.children)
                case Some(node: ListChildNodeADM) => (node.asInstanceOf[ListChildNodeADM], node.children)
                case Some(_) | None => throw BadRequestException(s"List node '$parentNodeIri' not found.")
            }

            // append child to the end
            position: Int = if(children.isEmpty) {
                0
            } else {
                children.size
            }

            /* get the root node, depending on the type of the parent */
            rootNode = parentListNode match {
                case root: ListRootNodeADM => root.id
                case child: ListChildNodeADM => child.hasRootNode
            }

            /* Verify that the project exists by retrieving it. We need the project information so that we can calculate the data graph and IRI for the new node.  */
            maybeProject <- (responderManager ? ProjectGetADM(maybeIri = Some(createChildNodeRequest.projectIri), maybeShortname = None, maybeShortcode = None, KnoraSystemInstances.Users.SystemUser)).mapTo[Option[ProjectADM]]
            project: ProjectADM = maybeProject match {
                case Some(project: ProjectADM) => project
                case None => throw BadRequestException(s"Project '${createChildNodeRequest.projectIri}' not found.")
            }

            /* verify that the list node name is unique for the project */
            projectUniqueNodeName <- listNodeNameIsProjectUnique(createChildNodeRequest.projectIri, createChildNodeRequest.name)
            _ = if (!projectUniqueNodeName) {
                throw BadRequestException(s"The node name ${createChildNodeRequest.name.get} is already by a list inside the project ${createChildNodeRequest.projectIri}.")
            }

            // calculate the data named graph
            dataNamedGraph = stringFormatter.projectDataNamedGraphV2(project)

            // calculate the new node's IRI
            maybeShortcode = project.shortcode
            newListNodeIri = knoraIdUtil.makeRandomListIri(maybeShortcode)

            // Create the new list node
            createNewListSparqlString = queries.sparql.admin.txt.createNewListChildNode(
                dataNamedGraph = dataNamedGraph,
                triplestore = settings.triplestoreType,
                listClassIri = OntologyConstants.KnoraBase.ListNode,
                nodeIri = newListNodeIri,
                parentNodeIri = parentNodeIri,
                rootNodeIri = rootNode,
                position = position,
                maybeName = createChildNodeRequest.name,
                maybeLabels = createChildNodeRequest.labels,
                maybeComments = createChildNodeRequest.comments
            ).toString
            // _ = log.debug("listCreateRequestADM - createNewListSparqlString: {}", createNewListSparqlString)
            createResourceResponse <- (storeManager ? SparqlUpdateRequest(createNewListSparqlString)).mapTo[SparqlUpdateResponse]


            // Verify that the list node was created.
            maybeNewListNode <- listNodeInfoGetADM(newListNodeIri, KnoraSystemInstances.Users.SystemUser)
            newListNode = maybeNewListNode.getOrElse(throw UpdateNotPerformedException(s"List node $newListNodeIri was not created. Please report this as a possible bug."))

            // _ = log.debug(s"listCreateRequestADM - newListADM: $newListADM")

        } yield ListNodeInfoGetResponseADM(nodeinfo = newListNode)


        for {
            // run list node creation with an global IRI lock
            taskResult <- IriLocker.runWithIriLock(
                apiRequestID,
                LISTS_GLOBAL_LOCK_IRI,
                () => listChildNodeCreateTask(createChildNodeRequest, requestingUser, apiRequestID)
            )
        } yield taskResult

    }

    ////////////////////
    // Helper Methods //
    ////////////////////

    /**
      * Helper method for checking if a project identified by IRI exists.
      *
      * @param projectIri the IRI of the project.
      * @return a [[Boolean]].
      */
    private def projectByIriExists(projectIri: IRI): Future[Boolean] = {
        for {
            askString <- Future(queries.sparql.admin.txt.checkProjectExistsByIri(projectIri = projectIri).toString)
            //_ = log.debug("projectByIriExists - query: {}", askString)

            askResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = askResponse.result

        } yield result
    }

    /**
      * Helper method for checking if a list node identified by IRI exists and is a root node.
      *
      * @param listNodeIri the IRI of the project.
      * @return a [[Boolean]].
      */
    private def listRootNodeByIriExists(listNodeIri: IRI): Future[Boolean] = {
        for {
            askString <- Future(queries.sparql.admin.txt.checkListRootNodeExistsByIri(listNodeIri = listNodeIri).toString)
            // _ = log.debug("listRootNodeByIriExists - query: {}", askString)

            askResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = askResponse.result

        } yield result
    }

    /**
      * Helper method for checking if a list node identified by IRI exists.
      *
      * @param listNodeIri the IRI of the project.
      * @return a [[Boolean]].
      */
    private def listNodeByIriExists(listNodeIri: IRI): Future[Boolean] = {
        for {
            askString <- Future(queries.sparql.admin.txt.checkListNodeExistsByIri(listNodeIri = listNodeIri).toString)
            //_ = log.debug("listNodeByIriExists - query: {}", askString)

            askResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = askResponse.result

        } yield result
    }

    /**
      * Helper method for checking if a list node identified by name exists.
      *
      * @param projectIri the IRI of the project.
      * @return a [[Boolean]].
      */
    private def listNodeByNameExists(name: String): Future[Boolean] = {
        for {
            askString <- Future(queries.sparql.admin.txt.checkListNodeExistsByName(listNodeName = name).toString)
            //_ = log.debug("listNodeByNameExists - query: {}", askString)

            askResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = askResponse.result

        } yield result
    }

    /**
      * Helper method for checking if a list node name is not used in any list inside a project. Returns a 'TRUE' if the
      * name is NOT used inside any list of this project.
      *
      * @param rootNodeIri the list's root node.
      * @param listNodeName the list node name.
      * @return a [[Boolean]].
      */
    private def listNodeNameIsProjectUnique(projectIri: IRI, listNodeName: Option[String]): Future[Boolean] = {
        listNodeName match {
            case Some(name) => {
                for {
                    askString <- Future(queries.sparql.admin.txt.checkListNodeNameIsProjectUnique(projectIri = projectIri, listNodeName = name).toString)
                    //_ = log.debug("listNodeNameIsProjectUnique - query: {}", askString)

                    askResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
                    result = askResponse.result

                } yield !result
            }
            case None => FastFuture.successful(true)
        }
    }
}
