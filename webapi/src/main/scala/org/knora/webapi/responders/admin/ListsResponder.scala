/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.IO
import zio.Task
import zio.ZIO
import zio.ZLayer

import java.util.UUID

import dsp.errors.*
import dsp.valueobjects.Iri.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages.*
import org.knora.webapi.messages.store.triplestoremessages.*
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.ListsResponder.Queries
import org.knora.webapi.slice.admin.api.Requests.*
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListName
import org.knora.webapi.slice.admin.domain.model.ListProperties.Position
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.slice.resources.repo.AskListNameInProjectExistsQuery
import org.knora.webapi.slice.resources.repo.ChangeParentNodeQuery
import org.knora.webapi.slice.resources.repo.CreateListNodeQuery
import org.knora.webapi.slice.resources.repo.IsListInUseQuery
import org.knora.webapi.slice.resources.repo.ListNodeExistsQuery
import org.knora.webapi.slice.resources.repo.UpdateListInfoQuery
import org.knora.webapi.slice.resources.repo.UpdateNodePositionQuery
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.util.ZioHelper

final case class ListsResponder(
  auth: AuthorizationRestService,
  iriService: IriService,
  knoraProjectService: KnoraProjectService,
  mapper: PredicateObjectMapper,
  triplestore: TriplestoreService,
)(implicit val stringFormatter: StringFormatter) {

  // The IRI used to lock user creation and update
  private val LISTS_GLOBAL_LOCK_IRI = "http://rdfh.ch/lists"

  /**
   * Gets all lists or list belonging to a project and returns them as a [[ListsGetResponseADM]]. For performance reasons
   * (as lists can be very large), we only return the head of the list, i.e. the root node without
   * any children.
   *
   * @param iriShortcode [[Some(ProjectIri|Shortcode)]] if the project for which lists are to be queried.
   *                     [[None]] if all lists are to be queried.
   * @return a [[ListsGetResponseADM]].
   */
  def getLists(iriShortcode: Option[Either[ProjectIri, Shortcode]]): Task[ListsGetResponseADM] =
    for {
      project <- iriShortcode match {
                   case Some(Left(iri)) =>
                     knoraProjectService
                       .findById(iri)
                       .someOrFail(NotFoundException(s"Project with IRI '$iri' not found"))
                       .asSome
                   case Some(Right(code)) =>
                     knoraProjectService
                       .findByShortcode(code)
                       .someOrFail(NotFoundException(s"Project with shortcode '$code' not found"))
                       .asSome
                   case None => ZIO.none
                 }
      statements <-
        triplestore.query(Construct(Queries.getListsQuery(project.map(_.id)))).flatMap(_.asExtended).map(_.statements)
      lists <-
        ZIO.foreach(statements.toList) { case (listIri: SubjectV2, objs: ConstructPredicateObjects) =>
          for {
            name   <- mapper.getSingleOption[StringLiteralV2](KnoraBase.ListNodeName, objs).map(_.map(_.value))
            labels <-
              mapper.getList[StringLiteralV2](Rdfs.Label, objs).map(_.toVector).map(StringLiteralSequenceV2.apply)
            comments <-
              mapper.getList[StringLiteralV2](Rdfs.Comment, objs).map(_.toVector).map(StringLiteralSequenceV2.apply)
            projectIri <- mapper.getSingleOrFail[IriLiteralV2](KnoraBase.AttachedToProject, objs).map(_.value)
          } yield ListRootNodeInfoADM(listIri.toString, projectIri, name, labels, comments)
        }
    } yield ListsGetResponseADM(lists)

  /**
   * Retrieves a complete list (root and all children) from the triplestore and returns it as a optional [[ListADM]].
   *
   * @param rootNodeIri          the Iri if the root node of the list to be queried.
   * @return a optional [[ListADM]].
   */
  private def listGetADM(rootNodeIri: IRI) =
    for {
      // this query will give us only the information about the root node.
      exists <- rootNodeExists(ListIri.unsafeFrom(rootNodeIri))

      maybeList <-
        if (exists) {
          for {
            // here we know that the list exists and it is fine if children is an empty list
            children <-
              getChildren(ofNodeIri = rootNodeIri, shallow = false)

            maybeRootNodeInfo <-
              listNodeInfoGetADM(nodeIri = rootNodeIri)

            rootNodeInfo = maybeRootNodeInfo match {
                             case Some(info: ListRootNodeInfoADM) => info
                             case Some(_: ListChildNodeInfoADM)   =>
                               throw InconsistentRepositoryDataException(
                                 "A child node info was found, although we are expecting a root node info. Please report this as a possible bug.",
                               )
                             case Some(_) | None =>
                               throw InconsistentRepositoryDataException(
                                 "No info about list node found, although list node should exist. Please report this as a possible bug.",
                               )
                           }

            list = ListADM(listinfo = rootNodeInfo, children = children)
          } yield Some(list)
        } else {
          ZIO.none
        }

    } yield maybeList

  /**
   * Retrieves a complete node (root or child) with all children from the triplestore and returns it as a [[ListItemGetResponseADM]].
   * If an IRI of a root node is given, the response is a list with root node info and all children of the list.
   * If an IRI of a child node is given, the response is a node with its information and all children of the sublist.
   *
   * @param nodeIri        the Iri if the required node.
   * @return a [[ListItemGetResponseADM]].
   */
  def listGetRequestADM(nodeIri: ListIri): Task[ListItemGetResponseADM] = {

    def getNodeADM(childNode: ListChildNodeADM): Task[ListNodeGetResponseADM] =
      for {
        maybeNodeInfo <- listNodeInfoGetADM(nodeIri.value)
        nodeInfo      <- maybeNodeInfo match {
                      case Some(childNodeInfo: ListChildNodeInfoADM) => ZIO.succeed(childNodeInfo)
                      case _                                         => ZIO.fail(NotFoundException(s"Information not found for node '$nodeIri'"))
                    }
      } yield ListNodeGetResponseADM(NodeADM(nodeInfo, childNode.children))

    ZIO.ifZIO(rootNodeExists(nodeIri))(
      listGetADM(nodeIri.value)
        .someOrFail(NotFoundException(s"List '$nodeIri' not found"))
        .map(ListGetResponseADM.apply),
      for {
        maybeNode <- listNodeGetADM(nodeIri.value, shallow = true)

        entireNode <- maybeNode match {
                        // make sure that it is a child node
                        case Some(childNode: ListChildNodeADM) => getNodeADM(childNode)
                        case _                                 => ZIO.fail(NotFoundException(s"Node '$nodeIri' not found"))
                      }
      } yield entireNode,
    )
  }

  /**
   * Retrieves information about a single node (without information about children). The single node can be the
   * lists root node or child node
   *
   * @param nodeIri              the Iri if the list node to be queried.
   * @return a optional [[ListNodeInfoADM]].
   */
  private def listNodeInfoGetADM(nodeIri: IRI) = {
    for {
      statements <- triplestore
                      .query(Construct(sparql.admin.txt.getListNode(nodeIri)))
                      .flatMap(_.asExtended)
                      .map(_.statements)

      maybeListNodeInfo =
        if (statements.nonEmpty) {

          val nodeInfo: ListNodeInfoADM = statements.head match {
            case (nodeIri: SubjectV2, propsMap: Map[SmartIri, Seq[LiteralV2]]) =>
              val labels: Seq[StringLiteralV2] = propsMap
                .getOrElse(Rdfs.Label.toSmartIri, Seq.empty[StringLiteralV2])
                .map(_.asInstanceOf[StringLiteralV2])
              val comments: Seq[StringLiteralV2] = propsMap
                .getOrElse(Rdfs.Comment.toSmartIri, Seq.empty[StringLiteralV2])
                .map(_.asInstanceOf[StringLiteralV2])

              val attachedToProjectOption: Option[IRI] =
                propsMap.get(KnoraBase.AttachedToProject.toSmartIri) match {
                  case Some(iris: Seq[LiteralV2]) =>
                    iris.headOption match {
                      case Some(iri: IriLiteralV2) => Some(iri.value)
                      case other                   =>
                        throw InconsistentRepositoryDataException(
                          s"Expected attached to project Iri as an IriLiteralV2 for list node $nodeIri, but got $other",
                        )
                    }

                  case None => None
                }

              val hasRootNodeOption: Option[IRI] =
                propsMap.get(KnoraBase.HasRootNode.toSmartIri) match {
                  case Some(iris: Seq[LiteralV2]) =>
                    iris.headOption match {
                      case Some(iri: IriLiteralV2) => Some(iri.value)
                      case other                   =>
                        throw InconsistentRepositoryDataException(
                          s"Expected root node Iri as an IriLiteralV2 for list node $nodeIri, but got $other",
                        )
                    }

                  case None => None
                }

              val isRootNode: Boolean = propsMap.get(KnoraBase.IsRootNode.toSmartIri) match {
                case Some(values: Seq[LiteralV2]) =>
                  values.headOption match {
                    case Some(value: BooleanLiteralV2) => value.value
                    case Some(other)                   =>
                      throw InconsistentRepositoryDataException(
                        s"Expected isRootNode as an BooleanLiteralV2 for list node $nodeIri, but got $other",
                      )
                    case None => false
                  }

                case None => false
              }

              val positionOption: Option[Int] = propsMap
                .get(KnoraBase.ListNodePosition.toSmartIri)
                .map(_.head.asInstanceOf[IntLiteralV2].value)

              if (isRootNode) {
                ListRootNodeInfoADM(
                  id = nodeIri.toString,
                  projectIri = attachedToProjectOption.getOrElse(
                    throw InconsistentRepositoryDataException(
                      s"Required attachedToProject property missing for list node $nodeIri.",
                    ),
                  ),
                  name = propsMap
                    .get(KnoraBase.ListNodeName.toSmartIri)
                    .map(_.head.asInstanceOf[StringLiteralV2].value),
                  labels = StringLiteralSequenceV2(labels.toVector.sorted),
                  comments = StringLiteralSequenceV2(comments.toVector.sorted),
                )
              } else {
                ListChildNodeInfoADM(
                  id = nodeIri.toString,
                  name = propsMap
                    .get(KnoraBase.ListNodeName.toSmartIri)
                    .map(_.head.asInstanceOf[StringLiteralV2].value),
                  labels = StringLiteralSequenceV2(labels.toVector),
                  comments = StringLiteralSequenceV2(comments.toVector),
                  position = positionOption.getOrElse(
                    throw InconsistentRepositoryDataException(
                      s"Required position property missing for list node $nodeIri.",
                    ),
                  ),
                  hasRootNode = hasRootNodeOption.getOrElse(
                    throw InconsistentRepositoryDataException(
                      s"Required hasRootNode property missing for list node $nodeIri.",
                    ),
                  ),
                )
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
   * @return a [[ChildNodeInfoGetResponseADM]].
   */
  def listNodeInfoGetRequestADM(nodeIri: IRI): Task[NodeInfoGetResponseADM] =
    listNodeInfoGetADM(nodeIri = nodeIri).flatMap {
      case Some(childInfo: ListChildNodeInfoADM) => ZIO.succeed(ChildNodeInfoGetResponseADM(childInfo))
      case Some(rootInfo: ListRootNodeInfoADM)   => ZIO.succeed(RootNodeInfoGetResponseADM(rootInfo))
      case _                                     => ZIO.fail(NotFoundException(s"List node '$nodeIri' not found"))
    }

  /**
   * Find a node including its immediate children. The node can be a root node or a child node.
   *
   * @param nodeIri              the IRI of the list node to be queried.
   * @return a optional [[ListNodeADM]]
   */
  private def findNode(nodeIri: ListIri): IO[Option[Throwable], ListNodeADM] =
    listNodeGetADM(nodeIri.value, true).some

  /**
   * Retrieves a complete node including children. The node can be the lists root node or child node.
   *
   * @param nodeIri              the IRI of the list node to be queried.
   * @param shallow              denotes if all children or only the immediate children will be returned.
   * @return a optional [[ListNodeADM]]
   */
  private def listNodeGetADM(nodeIri: IRI, shallow: Boolean): Task[Option[ListNodeADM]] = {
    for {
      // this query will give us only the information about the root node.
      statements <- triplestore
                      .query(Construct(sparql.admin.txt.getListNode(nodeIri)))
                      .flatMap(_.asExtended)
                      .map(_.statements)

      maybeListNode <-
        if (statements.nonEmpty) {
          for {
            // here we know that the list exists and it is fine if children is an empty list
            children <-
              getChildren(ofNodeIri = nodeIri, shallow = shallow)

            node: ListNodeADM = statements.head match {
                                  case (nodeIri: SubjectV2, propsMap: Map[SmartIri, Seq[LiteralV2]]) =>
                                    val labels: Seq[StringLiteralV2] = propsMap
                                      .getOrElse(Rdfs.Label.toSmartIri, Seq.empty[StringLiteralV2])
                                      .map(_.asInstanceOf[StringLiteralV2])
                                    val comments: Seq[StringLiteralV2] = propsMap
                                      .getOrElse(Rdfs.Comment.toSmartIri, Seq.empty[StringLiteralV2])
                                      .map(_.asInstanceOf[StringLiteralV2])

                                    val attachedToProjectOption: Option[IRI] =
                                      propsMap.get(KnoraBase.AttachedToProject.toSmartIri) match {
                                        case Some(iris: Seq[LiteralV2]) =>
                                          iris.headOption match {
                                            case Some(iri: IriLiteralV2) => Some(iri.value)
                                            case other                   =>
                                              throw InconsistentRepositoryDataException(
                                                s"Expected attached to project Iri as an IriLiteralV2 for list node $nodeIri, but got $other",
                                              )
                                          }

                                        case None => None
                                      }

                                    val hasRootNodeOption: Option[IRI] =
                                      propsMap.get(KnoraBase.HasRootNode.toSmartIri) match {
                                        case Some(iris: Seq[LiteralV2]) =>
                                          iris.headOption match {
                                            case Some(iri: IriLiteralV2) => Some(iri.value)
                                            case other                   =>
                                              throw InconsistentRepositoryDataException(
                                                s"Expected root node Iri as an IriLiteralV2 for list node $nodeIri, but got $other",
                                              )
                                          }

                                        case None => None
                                      }

                                    val isRootNode: Boolean =
                                      propsMap.get(KnoraBase.IsRootNode.toSmartIri) match {
                                        case Some(values: Seq[LiteralV2]) =>
                                          values.headOption match {
                                            case Some(value: BooleanLiteralV2) => value.value
                                            case Some(other)                   =>
                                              throw InconsistentRepositoryDataException(
                                                s"Expected isRootNode as an BooleanLiteralV2 for list node $nodeIri, but got $other",
                                              )
                                            case None => false
                                          }

                                        case None => false
                                      }

                                    val positionOption: Option[Int] = propsMap
                                      .get(KnoraBase.ListNodePosition.toSmartIri)
                                      .map(_.head.asInstanceOf[IntLiteralV2].value)

                                    if (isRootNode) {
                                      ListRootNodeADM(
                                        id = nodeIri.toString,
                                        projectIri = attachedToProjectOption.getOrElse(
                                          throw InconsistentRepositoryDataException(
                                            s"Required attachedToProject property missing for list node $nodeIri.",
                                          ),
                                        ),
                                        name = propsMap
                                          .get(KnoraBase.ListNodeName.toSmartIri)
                                          .map(_.head.asInstanceOf[StringLiteralV2].value),
                                        labels = StringLiteralSequenceV2(labels.toVector),
                                        comments = StringLiteralSequenceV2(comments.toVector),
                                        children = children,
                                      )
                                    } else {
                                      ListChildNodeADM(
                                        id = nodeIri.toString,
                                        name = propsMap
                                          .get(KnoraBase.ListNodeName.toSmartIri)
                                          .map(_.head.asInstanceOf[StringLiteralV2].value),
                                        labels = StringLiteralSequenceV2(labels.toVector),
                                        comments = StringLiteralSequenceV2(comments.toVector),
                                        position = positionOption.getOrElse(
                                          throw InconsistentRepositoryDataException(
                                            s"Required position property missing for list node $nodeIri.",
                                          ),
                                        ),
                                        hasRootNode = hasRootNodeOption.getOrElse(
                                          throw InconsistentRepositoryDataException(
                                            s"Required hasRootNode property missing for list node $nodeIri.",
                                          ),
                                        ),
                                        children = children,
                                      )
                                    }
                                }

          } yield Some(node)
        } else {
          ZIO.none
        }

    } yield maybeListNode
  }

  /**
   * Retrieves the child nodes from the triplestore. If shallow is true, then only the immediate children will be
   * returned, otherwise all children and their children's children will be returned.
   *
   * @param ofNodeIri            the IRI of the node for which children are to be returned.
   * @param shallow              denotes if all children or only the immediate children will be returned.
   * @return a sequence of [[ListChildNodeADM]].
   */
  private def getChildren(ofNodeIri: IRI, shallow: Boolean) = {

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
          KnoraBase.HasRootNode.toSmartIri,
          throw InconsistentRepositoryDataException(s"Required hasRootNode property missing for list node $nodeIri."),
        )
        .head
        .toString

      val nameOption = propsMap
        .get(KnoraBase.ListNodeName.toSmartIri)
        .map(_.head.asInstanceOf[StringLiteralV2].value)

      val labels: Seq[StringLiteralV2] = propsMap
        .getOrElse(Rdfs.Label.toSmartIri, Seq.empty[StringLiteralV2])
        .map(_.asInstanceOf[StringLiteralV2])
      val comments: Seq[StringLiteralV2] = propsMap
        .getOrElse(Rdfs.Comment.toSmartIri, Seq.empty[StringLiteralV2])
        .map(_.asInstanceOf[StringLiteralV2])

      val positionOption: Option[Int] = propsMap
        .get(KnoraBase.ListNodePosition.toSmartIri)
        .map(_.head.asInstanceOf[IntLiteralV2].value)
      val position = positionOption.getOrElse(
        throw InconsistentRepositoryDataException(s"Required position property missing for list node $nodeIri."),
      )

      val children: Seq[ListChildNodeADM] = propsMap.get(KnoraBase.HasSubListNode.toSmartIri) match {
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
        comments = StringLiteralSequenceV2(comments.toVector),
        children = children.map(_.sorted),
        position = position,
        hasRootNode = hasRootNode,
      )
    }

    for {
      statements <- triplestore
                      .query(Construct(sparql.admin.txt.getListNodeWithChildren(ofNodeIri)))
                      .flatMap(_.asExtended)
                      .map(_.statements.toList)

      startNodePropsMap = statements.filter(_._1 == IriSubjectV2(ofNodeIri)).head._2

      children = startNodePropsMap.get(KnoraBase.HasSubListNode.toSmartIri) match {
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
   * Creates a node (root or child).
   *
   * @param createNodeRequest    the new node's information.
   * @return a [newListNodeIri]
   */
  private def createNode(
    createNodeRequest: ListCreateRequest,
  ): Task[IRI] = {
    val parentNode: Option[ListIri] = createNodeRequest match {
      case _: ListCreateRootNodeRequest      => None
      case child: ListCreateChildNodeRequest => Some(child.parentNodeIri)
    }

    val (id, projectIri, name, position) = createNodeRequest match {
      case root: ListCreateRootNodeRequest   => (root.id, root.projectIri, root.name, None)
      case child: ListCreateChildNodeRequest => (child.id, child.projectIri, child.name, child.position)
    }

    def getRootNodeIri(parentListNode: ListNodeADM): IRI =
      parentListNode match {
        case root: ListRootNodeADM   => root.id
        case child: ListChildNodeADM => child.hasRootNode
      }

    def getRootNodeAndPositionOfNewChild(
      parentNodeIri: IRI,
      project: KnoraProject,
    ): Task[(Some[Int], Some[IRI])] =
      for {
        /* Verify that the list node exists by retrieving the whole node including children one level deep (need for position calculation) */
        parentListNode <- listNodeGetADM(parentNodeIri, shallow = true)
                            .someOrFail(BadRequestException(s"List node '$parentNodeIri' not found."))
        children  = parentListNode.children
        size      = children.size
        position <- {
          position.map(_.value) match {
            case Some(pos) if pos > size =>
              ZIO.fail(BadRequestException(s"Invalid position given $pos, maximum allowed position is = $size."))
            case Some(pos) if pos >= 0 => ZIO.succeed(pos)
            case _                     => ZIO.succeed(size)
          }
        }
        _ <- shiftNodes(position, size - 1, children, project, ShiftRight).when(position != size)
      } yield (Some(position), Some(getRootNodeIri(parentListNode)))

    for {
      /* Verify that the project exists by retrieving it. We need the project information so that we can calculate the data graph and IRI for the new node.  */
      project <- knoraProjectService
                   .findById(projectIri)
                   .someOrFail(BadRequestException(s"Project '$projectIri' not found."))

      /* verify that the list node name is unique for the project */
      _ <- name.map(ensureListNameInProjectDoesNotExist(_, project)).getOrElse(ZIO.unit)

      // if parent node is known, find the root node of the list and the position of the new child node
      positionAndNode <- if parentNode.nonEmpty then getRootNodeAndPositionOfNewChild(parentNode.get.value, project)
                         else ZIO.succeed((None, None))
      (newPosition, rootNodeIri) = positionAndNode

      // check the custom IRI; if not given, create an unused IRI
      customListIri   = id.map(_.value).map(_.toSmartIri)
      newListNodeIri <- iriService.checkOrCreateEntityIri(customListIri, ListIri.makeNew(project).value)

      // Create the new list node depending on type
      query = createNodeRequest match {
                case r: ListCreateRootNodeRequest =>
                  CreateListNodeQuery.createRootNode(
                    project,
                    ListIri.unsafeFrom(newListNodeIri),
                    r.name,
                    r.labels,
                    r.comments,
                  )
                case c: ListCreateChildNodeRequest =>
                  CreateListNodeQuery.createChildNode(
                    project,
                    ListIri.unsafeFrom(newListNodeIri),
                    (c.parentNodeIri, ListIri.unsafeFrom(rootNodeIri.get), Position.unsafeFrom(newPosition.get)),
                    c.name,
                    c.labels,
                    c.comments,
                  )
              }
      _ <- triplestore.query(Update(query))
    } yield newListNodeIri
  }

  /**
   * Creates a list.
   *
   * @param req    the new list's information.
   * @param apiRequestID         the unique api request ID.
   * @return a [[ListGetResponseADM]]
   */
  def listCreateRootNode(req: ListCreateRootNodeRequest, apiRequestID: UUID): Task[ListGetResponseADM] = {
    val createTask = createNode(req).flatMap { createdIri =>
      val errMsg = s"List $createdIri was not created. Please report this as a possible bug."
      listGetADM(createdIri)
        .someOrFail(UpdateNotPerformedException(errMsg))
        .map(ListGetResponseADM.apply)
    }
    IriLocker.runWithIriLock(apiRequestID, LISTS_GLOBAL_LOCK_IRI)(createTask)
  }

  /**
   * Changes basic node information stored (root or child)
   *
   * @param request    the new node information.
   * @param apiRequestID         the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   * fails with a ForbiddenException          in the case that the user is not allowed to perform the operation.
   * fails with a UpdateNotPerformedException in the case something else went wrong, and the change could not be performed.
   */
  def nodeInfoChangeRequest(
    request: ListChangeRequest,
    apiRequestID: UUID,
  ): Task[NodeInfoGetResponseADM] = IriLocker.runWithIriLock(apiRequestID, request.listIri)(for {
    /* Verify that the node with Iri exists. */
    _ <- listNodeGetADM(request.listIri.value, shallow = true)
           .someOrFail(BadRequestException(s"List item with '${request.listIri}' not found."))
    project <- knoraProjectService
                 .findById(request.projectIri)
                 .someOrFail(NotFoundException.notFound(request.projectIri))
    _ <- request.name.map(ensureListNameInProjectDoesNotExist(_, project)).getOrElse(ZIO.unit)

    // Update the list
    update = Update(
               UpdateListInfoQuery.build(project, request.listIri, request.name, request.labels, request.comments),
             )
    _            <- triplestore.query(update)
    maybeNodeADM <- listNodeInfoGetADM(request.listIri.value)
    updated      <-
      maybeNodeADM match {
        case Some(rootNode: ListRootNodeInfoADM)   => ZIO.succeed(RootNodeInfoGetResponseADM(rootNode))
        case Some(childNode: ListChildNodeInfoADM) => ZIO.succeed(ChildNodeInfoGetResponseADM(childNode))
        case _                                     =>
          ZIO.fail(
            UpdateNotPerformedException(
              s"Node ${request.listIri} was not updated. Please report this as a possible bug.",
            ),
          )
      }
  } yield updated)

  /**
   * Creates a new child node and appends it to an existing list node.
   *
   * @param req the new list node's information.
   * @param apiRequestID           the unique api request ID.
   * @return a [[ChildNodeInfoGetResponseADM]]
   */
  def listCreateChildNode(
    req: ListCreateChildNodeRequest,
    apiRequestID: UUID,
  ): Task[ChildNodeInfoGetResponseADM] = {

    /**
     * The actual task run with an IRI lock.
     */
    def listChildNodeCreateTask(
      createChildNodeRequest: ListCreateChildNodeRequest,
    ): Task[ChildNodeInfoGetResponseADM] =
      for {
        newListNodeIri <- createNode(createChildNodeRequest)
        // Verify that the list node was created.
        maybeNewListNode <- listNodeInfoGetADM(nodeIri = newListNodeIri)
        newListNode      <- maybeNewListNode match {
                         case Some(childNode: ListChildNodeInfoADM) => ZIO.succeed(childNode)
                         case Some(_: ListRootNodeInfoADM)          =>
                           ZIO.fail(
                             UpdateNotPerformedException(
                               s"Child node ${createChildNodeRequest.name} could not be created. Probably parent node Iri is missing in payload.",
                             ),
                           )
                         case _ =>
                           ZIO.fail(
                             UpdateNotPerformedException(
                               s"List node $newListNodeIri was not created. Please report this as a possible bug.",
                             ),
                           )
                       }

      } yield ChildNodeInfoGetResponseADM(nodeinfo = newListNode)

    IriLocker.runWithIriLock(apiRequestID, LISTS_GLOBAL_LOCK_IRI)(
      listChildNodeCreateTask(req),
    )
  }

  private def ensureUserIsAdminOrProjectOwner(listIri: ListIri, user: User): Task[KnoraProject] =
    getProjectIriFromNode(listIri.value)
      .flatMap(knoraProjectService.findById)
      .someOrFail(BadRequestException(s"Project not found for node $listIri"))
      .tap(auth.ensureSystemAdminOrProjectAdmin(user, _))

  /**
   * Changes name of the node (root or child)
   *
   * @param listIri               the node's IRI.
   * @param changeNameReq         the new node name.
   * @param apiRequestID          the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   *        Fails with a [[ForbiddenException]] in the case that the user is not allowed to perform the operation.
   *        Fails with a [[UpdateNotPerformedException]] in the case something else went wrong, and the change could not be performed.
   */
  def nodeNameChangeRequest(
    listIri: ListIri,
    changeNameReq: ListChangeNameRequest,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[NodeInfoGetResponseADM] =
    IriLocker.runWithIriLock(apiRequestID, listIri.value)(for {
      project <- ensureUserIsAdminOrProjectOwner(listIri, requestingUser)
      updated <- nodeInfoChangeRequest(
                   ListChangeRequest(listIri, project.id, name = Some(changeNameReq.name)),
                   apiRequestID,
                 )
    } yield updated)

  /**
   * Changes labels of the node (root or child)
   *
   * @param listIri                 the node's IRI.
   * @param changeNodeLabelsRequest the new node labels.
   * @param requestingUser          the requesting user.
   * @param apiRequestID            the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   *         fails with a [[ForbiddenException]] in the case that the user is not allowed to perform the operation.
   *         fails with a [[UpdateNotPerformedException]] in the case something else went wrong, and the change could not be performed.
   */
  def nodeLabelsChangeRequest(
    listIri: ListIri,
    changeNodeLabelsRequest: ListChangeLabelsRequest,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[NodeInfoGetResponseADM] =
    IriLocker.runWithIriLock(apiRequestID, listIri)(for {
      project <- ensureUserIsAdminOrProjectOwner(listIri, requestingUser)
      updated <- nodeInfoChangeRequest(
                   ListChangeRequest(listIri, project.id, labels = Some(changeNodeLabelsRequest.labels)),
                   apiRequestID,
                 )
    } yield updated)

  /**
   * Changes comments of the node (root or child)
   *
   * @param listIri                   the node's IRI.
   * @param changeNodeCommentsRequest the new node comments.
   * @param requestingUser            the requesting user.
   * @param apiRequestID              the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   *           fails with a [[ForbiddenException]] in the case that the user is not allowed to perform the operation.
   *           fails with a [[UpdateNotPerformedException]] in the case something else went wrong, and the change could not be performed.
   */
  def nodeCommentsChangeRequest(
    listIri: ListIri,
    changeNodeCommentsRequest: ListChangeCommentsRequest,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[NodeInfoGetResponseADM] =
    IriLocker.runWithIriLock(apiRequestID, listIri)(for {
      project <- ensureUserIsAdminOrProjectOwner(listIri, requestingUser)
      updated <- nodeInfoChangeRequest(
                   ListChangeRequest(listIri, project.id, comments = Some(changeNodeCommentsRequest.comments)),
                   apiRequestID,
                 )
    } yield updated)

  /**
   * Changes position of the node
   *
   * @param nodeIri                   the node's IRI.
   * @param changeNodePositionRequest the new node comments.
   * @param requestingUser            the requesting user.
   * @param apiRequestID              the unique api request ID.
   * @return a [[NodePositionChangeResponseADM]]
   *         Fails with a [[ForbiddenException]] in the case that the user is not allowed to perform the operation.
   *         Fails with a [[UpdateNotPerformedException]] in the case something else went wrong, and the change could not be performed.
   */
  def nodePositionChangeRequest(
    nodeIri: ListIri,
    changeNodePositionRequest: ListChangePositionRequest,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[NodePositionChangeResponseADM] = {

    /**
     * Checks if the given position is in range.
     * The highest position a node can be placed is to the end of the parents children; that means length of existing
     * children + 1
     *
     * If the node must be added to a new parent, highest valid position is numberOfChildren.
     * For example, if the new parent already has 3 children, the highest occupied position is 2, node can be
     * placed in position 3. That means the furthest a node can be positioned is being appended to the end of
     * children of the new parent.
     *
     * If node remains in its current parent, the highest valid position is numberOfChildren -1
     * That means if the parent node has 4 children, the highest position is 3.
     * Nodes are only reorganized within the same parent.
     *
     * The lowest position a node gets is 0. If -1 is given, node will be appended to the end of children list.
     * Values less than -1 are not allowed.
     *
     * @param parentNode  the parent to which the node should belong.
     * @param isNewParent identifier that node is added to another parent or not.
     * @return [[Unit]]
     *         fails with a [[BadRequestException]] if given position is out of range.
     */
    def isNewPositionValid(parentNode: ListNodeADM, isNewParent: Boolean): Task[Unit] = {
      val numberOfChildren = parentNode.children.size
      ZIO
        .fail(s"Invalid position given, maximum allowed position is = $numberOfChildren.")
        .when(isNewParent && changeNodePositionRequest.position > numberOfChildren) *>
        ZIO
          .fail(s"Invalid position given, maximum allowed position is = ${numberOfChildren - 1}.")
          .when(!isNewParent && changeNodePositionRequest.position > numberOfChildren - 1) *>
        ZIO
          .fail(s"Invalid position given, minimum allowed is -1.")
          .when(changeNodePositionRequest.position < -1)
    }.unit.mapError(BadRequestException.apply)

    /**
     * Checks that the position of the node is updated and node is sublist of specified parent.
     * It also checks the sibling nodes are shifted accordingly.
     *
     * @param newPosition the new position of the node.
     * @return the updated parent node with all its children as [[ListNodeADM]]
     *         fails with a [[UpdateNotPerformedException]] if some thing has gone wrong during the update.
     */
    def verifyParentChildrenUpdate(newPosition: Int): Task[ListNodeADM] =
      for {
        maybeParentNode                 <- listNodeGetADM(changeNodePositionRequest.parentNodeIri.value, shallow = false)
        updatedParent                    = maybeParentNode.get
        updatedChildren                  = updatedParent.children
        (siblingsPositionedBefore, rest) = updatedChildren.partition(_.position < newPosition)

        // verify that node is among children of specified parent in correct position
        updatedNode = rest.head
        _          <- ZIO.when(updatedNode.id != nodeIri.value || updatedNode.position != newPosition) {
               ZIO.fail(
                 UpdateNotPerformedException(
                   s"Node is not repositioned correctly in specified parent node. Please report this as a bug.",
                 ),
               )
             }
        leftPositions = siblingsPositionedBefore.map(child => child.position)
        _            <- ZIO.when(leftPositions != leftPositions.sorted) {
               ZIO.fail(
                 UpdateNotPerformedException(
                   s"Something has gone wrong with shifting nodes. Please report this as a bug.",
                 ),
               )
             }
        siblingsPositionedAfter = rest.slice(1, rest.length)
        rightSiblings           = siblingsPositionedAfter.map(child => child.position)
        _                      <- ZIO.when(rightSiblings != rightSiblings.sorted) {
               ZIO.fail(
                 UpdateNotPerformedException(
                   s"Something has gone wrong with shifting nodes. Please report this as a bug.",
                 ),
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
     *         fails with a [[UpdateNotPerformedException]] in the case the given new position is the same as current position.
     */
    def updatePositionWithinSameParent(
      node: ListChildNodeADM,
      parentIri: IRI,
      givenPosition: Int,
      project: KnoraProject,
    ): Task[Int] =
      for {
        // get parent node with its immediate children
        parentNode <-
          listNodeGetADM(nodeIri = parentIri, shallow = true)
            .someOrFail(BadRequestException(s"The parent node $parentIri could node be found, report this as a bug."))
        _             <- isNewPositionValid(parentNode, isNewParent = false)
        parentChildren = parentNode.children
        currPosition   = node.position

        // if givenPosition is -1, append the child to the end of the list of children
        newPosition = if givenPosition == -1 then parentChildren.size - 1
                      else givenPosition

        // update the position of the node itself
        _ <- updatePositionOfNode(node.listIri, Position.unsafeFrom(newPosition), project)

        _ <- // update position of siblings
          if (currPosition < newPosition) {
            shiftNodes(currPosition + 1, newPosition, parentChildren, project, ShiftLeft)
          } else if (currPosition > newPosition) {
            shiftNodes(newPosition, currPosition - 1, parentChildren, project, ShiftRight)
          } else {
            ZIO.fail(UpdateNotPerformedException(s"The given position is the same as node's current position."))
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
     * @param project        the project to which the list belongs.
     * @return the new position of the node [[Int]]
     * @throws UpdateNotPerformedException in the case the given new position is the same as current position.
     */
    def updateParentAndPosition(
      node: ListChildNodeADM,
      newParentIri: IRI,
      currParentIri: IRI,
      givenPosition: Int,
      project: KnoraProject,
    ): Task[Int] =
      for {
        // get current parent node with its immediate children
        maybeCurrentParentNode <- listNodeGetADM(nodeIri = currParentIri, shallow = true)
        currentSiblings         = maybeCurrentParentNode.get.children
        // get new parent node with its immediate children
        maybeNewParentNode <- listNodeGetADM(nodeIri = newParentIri, shallow = true)
        newParent           = maybeNewParentNode.get
        _                  <- isNewPositionValid(newParent, isNewParent = true)
        newSiblings         = newParent.children

        currentNodePosition = node.position

        // if givenPosition is -1, append the child to the end of the list of children
        newPosition = if (givenPosition == -1) { newSiblings.size }
                      else givenPosition
        // update the position of the node itself
        _ <- updatePositionOfNode(node.listIri, Position.unsafeFrom(newPosition), project)

        // shift current siblings with a higher position to left as if the node is deleted
        _ <- shiftNodes(currentNodePosition + 1, currentSiblings.last.position, currentSiblings, project, ShiftLeft)

        // Is node supposed to be added to the end of new parent's children list?
        _ <-
          if (givenPosition == -1 || givenPosition == newSiblings.size) {
            // Yes. New siblings should not be shifted
            ZIO.succeed(newSiblings)
          } else {
            // No. Shift new siblings with the same and higher position
            // to right, as if the node is inserted in the given position
            shiftNodes(newPosition, newSiblings.last.position, newSiblings, project, ShiftRight)
          }

        /* update the sublists of parent nodes */
        _ <-
          changeParentNode(project, node.listIri, ListIri.unsafeFrom(currParentIri), ListIri.unsafeFrom(newParentIri))
      } yield newPosition

    val updateTask =
      for {
        project <- ensureUserIsAdminOrProjectOwner(nodeIri, requestingUser)

        // get node in its current position
        node <- listNodeGetADM(nodeIri.value, shallow = true)
                  .map(_.collect { case child: ListChildNodeADM => child })
                  .someOrFail(BadRequestException(s"Update of position is only allowed for child nodes!"))

        // get node's current parent
        currentParentNodeIri <- getParentNodeIRI(nodeIri.value)
        newPosition          <-
          if (currentParentNodeIri == changeNodePositionRequest.parentNodeIri.value) {
            updatePositionWithinSameParent(
              node,
              currentParentNodeIri,
              changeNodePositionRequest.position.value,
              project,
            )
          } else {
            updateParentAndPosition(
              node = node,
              newParentIri = changeNodePositionRequest.parentNodeIri.value,
              currParentIri = currentParentNodeIri,
              givenPosition = changeNodePositionRequest.position.value,
              project,
            )
          }
        /* Verify that the node position and parent children position were updated */
        parentNode <- verifyParentChildrenUpdate(newPosition)
      } yield NodePositionChangeResponseADM(node = parentNode)

    IriLocker.runWithIriLock(apiRequestID, nodeIri.value)(updateTask)
  }

  /**
   * Checks if a list can be deleted (none of its nodes is used in data).
   */
  def canDeleteList(iri: ListIri): Task[CanDeleteListResponseADM] =
    triplestore.query(IsListInUseQuery.build(iri)).negate.map(CanDeleteListResponseADM(iri, _))

  /**
   * Deletes all comments from requested list node (only child).
   */
  def deleteListNodeCommentsADM(nodeIri: ListIri): Task[ListNodeCommentsDeleteResponseADM] =
    for {
      node <- listNodeInfoGetADM(nodeIri.value).someOrFail(NotFoundException(s"Node ${nodeIri.value} not found."))
      _    <- ZIO
             .fail(BadRequestException("Root node comments cannot be deleted."))
             .when(!node.isInstanceOf[ListChildNodeInfoADM])
      _ <- ZIO
             .fail(BadRequestException(s"Nothing to delete. Node ${nodeIri.value} does not have comments."))
             .when(!node.hasComments)
      projectIri <- getProjectIriFromNode(nodeIri.value)
      namedGraph <- getDataNamedGraph(projectIri)
      _          <- triplestore.query(Update(sparql.admin.txt.deleteListNodeComments(namedGraph, nodeIri.value)))
    } yield ListNodeCommentsDeleteResponseADM(nodeIri.value, commentsDeleted = true)

  /**
   * Delete a node (root or child). If a root node is given, check for its usage in data and ontology. If not used,
   * delete the list and return a confirmation message.
   *
   * @param nodeIri              the node's IRI.
   * @param requestingUser       the requesting user.
   * @param apiRequestID         the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   * @throws ForbiddenException          in the case that the user is not allowed to perform the operation.
   * @throws UpdateNotPerformedException in the case the node is in use and cannot be deleted.
   */
  def deleteListItemRequestADM(
    nodeIri: ListIri,
    requestingUser: User,
    apiRequestID: UUID,
  ): Task[ListItemDeleteResponseADM] = {

    /**
     * Checks if node itself or any of its children is in use.
     *
     * @param nodeIri      the node's IRI.
     * @param children the children of the node.
     */
    def isNodeOrItsChildrenUsed(nodeIri: IRI, children: Seq[ListChildNodeADM]): Task[Unit] = {
      def failIfInUse(nodeIri: IRI, failReason: String) = ZIO
        .fail(BadRequestException(failReason))
        .whenZIO(triplestore.query(Select(sparql.admin.txt.isNodeUsed(nodeIri))).map(_.nonEmpty))
      failIfInUse(nodeIri, s"Node $nodeIri cannot be deleted, because it is in use.") *> {
        ZIO.foreachDiscard(children) { child =>
          failIfInUse(child.id, s"Node $nodeIri cannot be deleted, because its child ${child.id} is in use.")
        }
      }
    }

    /**
     * Delete a list (root node) or a child node after verifying that neither the node itself nor any of its children
     * are used. If not used, delete the children of the node first, then delete the node itself.
     *
     * @param nodeIri    the node's IRI.
     * @param projectIri the feature factory configuration.
     * @param children   the children of the node.
     * @param isRootNode the flag to determine the type of the node, root or child.
     * @return a [[IRI]]
     * @throws UpdateNotPerformedException in case a node is in use.
     */
    def deleteListItem(
      nodeIri: IRI,
      projectIri: ProjectIri,
      children: Seq[ListChildNodeADM],
      isRootNode: Boolean,
    ): Task[IRI] =
      for {
        // get the data graph of the project.
        dataNamedGraph <- getDataNamedGraph(projectIri)

        // delete the children
        errorCheckFutures: Seq[Task[Unit]] =
          children.map(child => deleteNode(dataNamedGraph, child.id, isRootNode = false))
        _ <- ZioHelper.sequence(errorCheckFutures)

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
     * @param project               the project to which the list belongs.
     * @return a [[ListNodeADM]]
     * @throws UpdateNotPerformedException if the node that had to be deleted is still in the list of parent's children.
     */
    def updateParentNode(
      deletedNodeIri: IRI,
      positionOfDeletedNode: Int,
      parentNodeIri: IRI,
      project: KnoraProject,
    ): Task[ListNodeADM] =
      for {
        parentNode <-
          listNodeGetADM(parentNodeIri, shallow = false)
            .someOrFail(BadRequestException(s"The parent node of $deletedNodeIri not found, report this as a bug."))

        remainingChildren = parentNode.children

        _ <- ZIO.when(remainingChildren.exists(child => child.id == deletedNodeIri)) {
               ZIO.fail(
                 UpdateNotPerformedException(
                   s"Node $deletedNodeIri is not deleted properly, report this as a bug.",
                 ),
               )
             }

        // shift the siblings that were positioned after the deleted node, one place to left.
        updatedChildren <-
          if (remainingChildren.nonEmpty) {
            for {
              shiftedChildren <- shiftNodes(
                                   positionOfDeletedNode + 1,
                                   remainingChildren.last.position,
                                   remainingChildren,
                                   project,
                                   ShiftLeft,
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
                                  children = updatedChildren,
                                )

                              case childNode: ListChildNodeADM =>
                                ListChildNodeADM(
                                  id = childNode.id,
                                  name = childNode.name,
                                  labels = childNode.labels,
                                  comments = childNode.comments,
                                  position = childNode.position,
                                  hasRootNode = childNode.hasRootNode,
                                  children = updatedChildren,
                                )
                            }
      } yield updatedParentNode

    val nodeDeleteTask = for {
      project   <- ensureUserIsAdminOrProjectOwner(nodeIri, requestingUser)
      projectIri = project.id
      maybeNode <- listNodeGetADM(nodeIri.value, shallow = false)

      response <- maybeNode match {
                    case Some(rootNode: ListRootNodeADM) =>
                      for {
                        _ <- isNodeOrItsChildrenUsed(rootNode.id, rootNode.children)
                        _ <- deleteListItem(rootNode.id, projectIri, rootNode.children, isRootNode = true)
                      } yield ListDeleteResponseADM(rootNode.id, deleted = true)

                    case Some(childNode: ListChildNodeADM) =>
                      for {
                        _                 <- isNodeOrItsChildrenUsed(childNode.id, childNode.children)
                        parentNodeIri     <- getParentNodeIRI(nodeIri.value)
                        _                 <- deleteListItem(childNode.id, projectIri, childNode.children, isRootNode = false)
                        updatedParentNode <- updateParentNode(nodeIri.value, childNode.position, parentNodeIri, project)
                      } yield ChildNodeDeleteResponseADM(updatedParentNode)

                    case _ =>
                      ZIO.fail {
                        val msg = s"Node ${nodeIri.value} was not found. Please verify the given IRI."
                        BadRequestException(msg)
                      }
                  }
    } yield response

    IriLocker.runWithIriLock(apiRequestID, nodeIri.value)(nodeDeleteTask)
  }

  ////////////////////
  // Helper Methods //
  ////////////////////

  /**
   * Helper method for checking if a root list node identified by IRI exists.
   *
   * @param iri The [[ListIri]] of the node.
   * @return a [[Boolean]].
   */
  private def rootNodeExists(iri: ListIri): Task[Boolean] = triplestore.query(ListNodeExistsQuery.rootNodeExists(iri))

  /**
   * Helper method for checking if a node exists.
   *
   * @param iri The [[ListIri]] of the node.
   * @return a [[Boolean]].
   */
  private def nodeExists(iri: ListIri): Task[Boolean] = triplestore.query(ListNodeExistsQuery.anyNodeExists(iri))

  /**
   * Helper method for checking if a list node name is not used in any list inside a project. Returns a 'TRUE' if the
   * name is NOT used inside any list of this project.
   *
   * @param project      the project.
   * @param name         the list node name.
   * @return [[Unit]] or fails with a [[DuplicateValueException]] if name is already in use.
   */
  private def ensureListNameInProjectDoesNotExist(
    name: ListName,
    project: KnoraProject,
  ): IO[DuplicateValueException, Unit] =
    triplestore
      .query(AskListNameInProjectExistsQuery.build(name, project.id))
      .orDie
      .filterOrFail(exists => !exists)(
        DuplicateValueException(s"The name '$name' is already used by a list in project ${project.id}."),
      )
      .unit

  /**
   * Helper method to get projectIri of a node.
   *
   * @param nodeIri              the IRI of the node.
   * @return a [[ProjectIri]].
   */
  private def getProjectIriFromNode(nodeIri: IRI): Task[ProjectIri] =
    for {
      maybeNode <- listNodeGetADM(nodeIri = nodeIri, shallow = true)

      projectIriStr <- maybeNode match {
                         case Some(rootNode: ListRootNodeADM) => ZIO.succeed(rootNode.projectIri)

                         case Some(childNode: ListChildNodeADM) =>
                           for {
                             maybeRoot <- listNodeGetADM(childNode.hasRootNode, shallow = true)
                             iriStr    <- maybeRoot.collect { case it: ListRootNodeADM => it }
                                         .map(rootNode => ZIO.succeed(rootNode.projectIri))
                                         .getOrElse(ZIO.fail {
                                           val msg =
                                             s"Root node of $nodeIri was not found. Please verify the given IRI."
                                           BadRequestException(msg)
                                         })
                           } yield iriStr

                         case _ =>
                           ZIO.fail(BadRequestException(s"Node $nodeIri was not found. Please verify the given IRI."))
                       }
      projectIri <- ZIO.fromEither(ProjectIri.from(projectIriStr)).mapError(BadRequestException.apply)
    } yield projectIri

  /**
   * Helper method to get the data named graph of a project.
   *
   * @param projectIri           the IRI of the project.
   * @return an [[IRI]].
   */
  private def getDataNamedGraph(projectIri: ProjectIri): Task[IRI] =
    knoraProjectService
      .findById(projectIri)
      .someOrFail(BadRequestException(s"Project '$projectIri' not found."))
      .map(ProjectService.projectDataNamedGraphV2(_).value)

  /**
   * Helper method to get parent of a node.
   *
   * @param nodeIri              the IRI of the node.
   * @return a [[ListNodeADM]].
   */
  private def getParentNodeIRI(nodeIri: IRI): Task[IRI] =
    triplestore
      .query(Construct(sparql.admin.txt.getParentNode(nodeIri)))
      .map(_.statements.keys.headOption)
      .some
      .orElseFail(BadRequestException(s"The parent node for $nodeIri not found, report this as a bug."))

  /**
   * Helper method to delete a node.
   *
   * @param dataNamedGraph the data named graph of the project.
   * @param nodeIri        the IRI of the node.
   * @param isRootNode     is the node to be deleted a root node?
   * @return A [[ListNodeADM]].
   *
   *         Fails with a [[UpdateNotPerformedException]] if the node could not be deleted.
   */
  private def deleteNode(dataNamedGraph: IRI, nodeIri: IRI, isRootNode: Boolean): Task[Unit] =
    for {
      _ <- triplestore.query(Update(sparql.admin.txt.deleteNode(dataNamedGraph, nodeIri, isRootNode)))
      _ <- // Verify that the node was deleted correctly.
        ZIO
          .fail(UpdateNotPerformedException(s"Node <$nodeIri> was not erased. Please report this as a possible bug."))
          .whenZIO(nodeExists(ListIri.unsafeFrom(nodeIri)))
    } yield ()

  /**
   * Helper method to update position of a node without changing its parent.
   *
   * @param nodeIri              the IRI of the node that must be shifted.
   * @param newPosition          the new position of the child node.
   * @param project              the project to which the list belongs.
   * @return a [[ListChildNodeADM]].
   */
  private def updatePositionOfNode(
    nodeIri: ListIri,
    newPosition: Position,
    project: KnoraProject,
  ): Task[ListChildNodeADM] =
    for {
      _ <- triplestore.query(UpdateNodePositionQuery.build(project, nodeIri, newPosition))
      /* Verify that the node info was updated */
      childNode <- listNodeGetADM(nodeIri = nodeIri.value, shallow = false)
                     .someOrFail(BadRequestException(s"Node with $nodeIri could not be found to update its position."))
                     .map(_.asInstanceOf[ListChildNodeADM])
      _ <- ZIO
             .fail(
               UpdateNotPerformedException(
                 s"The position of the node $nodeIri could not be updated, report this as a possible bug.",
               ),
             )
             .unless(childNode.position.equals(newPosition))
    } yield childNode

  sealed trait ShiftDir { def apply(num: Int): Int }
  private case object ShiftLeft  extends ShiftDir { def apply(num: Int): Int = num - 1 }
  private case object ShiftRight extends ShiftDir { def apply(num: Int): Int = num + 1 }

  /**
   * Helper method to shift nodes between positions startPos and endPos.
   *
   * @param startPos             the position of first node in range that must be shifted.
   * @param endPos               the position of last node in range that must be shifted.
   * @param nodes                the list of all nodes.
   * @param shift                the [[ShiftDir]] direction to shift
   * @param namedGraph           the data named graph of the project.
   * @return a sequence of [[ListChildNodeADM]].
   */
  private def shiftNodes(
    startPos: Int,
    endPos: Int,
    nodes: Seq[ListChildNodeADM],
    project: KnoraProject,
    shift: ShiftDir,
  ) = {
    val (start, rest)     = nodes.partition(_.position < startPos)
    val (needUpdate, end) = rest.partition(_.position <= endPos)
    ZIO
      .foreach(needUpdate)(node =>
        updatePositionOfNode(node.listIri, Position.unsafeFrom(shift(node.position)), project),
      )
      .map(start ++ _ ++ end)
  }

  /**
   * Helper method to change parent node of a node.
   *
   * @param project              the project to which the list belongs.
   * @param nodeIri              the IRI of the node.
   * @param oldParentIri         the IRI of the current parent node.
   * @param newParentIri         the IRI of the new parent node.
   * @throws UpdateNotPerformedException if the parent of a node could not be updated.
   */
  private def changeParentNode(
    project: KnoraProject,
    nodeIri: ListIri,
    oldParentIri: ListIri,
    newParentIri: ListIri,
  ): Task[Unit] =
    for {
      _ <- triplestore.query(ChangeParentNodeQuery.build(project, nodeIri, oldParentIri, newParentIri))

      /* verify that parents were updated */
      // get old parent node with its immediate children
      childrenOfOldParent <-
        findNode(oldParentIri).mapBoth(
          {
            case None      => UpdateNotPerformedException(s"Old parent $oldParentIri not found, report this as a bug.")
            case Some(err) => err
          },
          _.children,
        )
      _ <- ZIO.when(childrenOfOldParent.exists(_.listIri == nodeIri)) {
             ZIO.fail(
               UpdateNotPerformedException(
                 s"Node $nodeIri is still a child of $oldParentIri. Report this as a bug.",
               ),
             )
           }
      // get new parent node with its immediate children
      childrenOfNewParent <-
        findNode(newParentIri).mapBoth(
          {
            case None      => UpdateNotPerformedException(s"New parent $newParentIri not found, report this as a bug.")
            case Some(err) => err
          },
          _.children,
        )
      _ <- ZIO.when(!childrenOfNewParent.exists(_.listIri == nodeIri)) {
             ZIO.fail(UpdateNotPerformedException(s"Node $nodeIri is not added to parent node $newParentIri. "))
           }
    } yield ()
}

object ListsResponder {

  private object Queries {
    def getListsQuery(projectIri: Option[ProjectIri]): String =
      s"""
         |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
         |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
         |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
         |PREFIX knora-base: <http://www.knora.org/ontology/knora-base#>
         |
         |CONSTRUCT { ?s ?p ?o . }
         |WHERE {
         |    ?s rdf:type knora-base:ListNode .
         |    ?s knora-base:isRootNode "true"^^xsd:boolean .
         |    ${projectIri.map(_.value).map(iri => s"?s knora-base:attachedToProject <$iri> .").getOrElse("")}
         |    ?s ?p ?o .
         |}""".stripMargin
  }

  val layer = ZLayer.derive[ListsResponder]
}
