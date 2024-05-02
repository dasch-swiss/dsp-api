/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.admin

import zio.IO
import zio.Task
import zio.ZIO
import zio.ZLayer

import java.util.UUID

import dsp.errors._
import dsp.valueobjects.Iri
import dsp.valueobjects.Iri._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.OntologyConstants.Rdfs
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.store.triplestoremessages.SparqlExtendedConstructResponse.ConstructPredicateObjects
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.twirl.queries.sparql
import org.knora.webapi.responders.IriLocker
import org.knora.webapi.responders.IriService
import org.knora.webapi.responders.admin.ListsResponder.Queries
import org.knora.webapi.slice.admin.api.Requests._
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListIri
import org.knora.webapi.slice.admin.domain.model.ListProperties.ListName
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.ProjectService
import org.knora.webapi.slice.common.api.AuthorizationRestService
import org.knora.webapi.slice.common.repo.service.PredicateObjectMapper
import org.knora.webapi.store.triplestore.api.TriplestoreService
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Ask
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Construct
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Select
import org.knora.webapi.store.triplestore.api.TriplestoreService.Queries.Update
import org.knora.webapi.util.ZioHelper

final case class ListsResponder(
  appConfig: AppConfig,
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
   * @param projectIri [[Some(ProjectIri)]] if the project for which lists are to be queried.
   *                   [[None]] if all lists are to be queried.
   * @return a [[ListsGetResponseADM]].
   */
  def getLists(projectIri: Option[ProjectIri]): Task[ListsGetResponseADM] =
    for {
      statements <-
        triplestore.query(Construct(Queries.getListsQuery(projectIri))).flatMap(_.asExtended).map(_.statements)
      lists <-
        ZIO.foreach(statements.toList) { case (listIri: SubjectV2, objs: ConstructPredicateObjects) =>
          for {
            name <- mapper.getSingleOption[StringLiteralV2](KnoraBase.ListNodeName, objs).map(_.map(_.value))
            labels <-
              mapper.getList[StringLiteralV2](Rdfs.Label, objs).map(_.toVector).map(StringLiteralSequenceV2.apply)
            comments <-
              mapper.getList[StringLiteralV2](Rdfs.Comment, objs).map(_.toVector).map(StringLiteralSequenceV2.apply)
            projectIri <- mapper.getSingleOrFail[IriLiteralV2](KnoraBase.AttachedToProject, objs).map(_.value)
          } yield ListRootNodeInfoADM(listIri.toString, projectIri, name, labels, comments).unescape
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
      exists <- rootNodeByIriExists(rootNodeIri)

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
                             case Some(_: ListChildNodeInfoADM) =>
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
  def listGetRequestADM(nodeIri: IRI): Task[ListItemGetResponseADM] = {

    def getNodeADM(childNode: ListChildNodeADM): Task[ListNodeGetResponseADM] =
      for {
        maybeNodeInfo <- listNodeInfoGetADM(nodeIri = nodeIri)
        nodeInfo <- maybeNodeInfo match {
                      case Some(childNodeInfo: ListChildNodeInfoADM) => ZIO.succeed(childNodeInfo)
                      case _                                         => ZIO.fail(NotFoundException(s"Information not found for node '$nodeIri'"))
                    }
      } yield ListNodeGetResponseADM(NodeADM(nodeInfo, childNode.children))

    ZIO.ifZIO(rootNodeByIriExists(nodeIri))(
      listGetADM(nodeIri).someOrFail(NotFoundException(s"List '$nodeIri' not found")).map(ListGetResponseADM.apply),
      for {
        maybeNode <- listNodeGetADM(nodeIri, shallow = true)

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
                      case other =>
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
                      case other =>
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
                    case Some(other) =>
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
                ).unescape
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
   * @return a [[ChildNodeInfoGetResponseADM]].
   */
  def listNodeInfoGetRequestADM(nodeIri: IRI): Task[NodeInfoGetResponseADM] =
    listNodeInfoGetADM(nodeIri = nodeIri).flatMap {
      case Some(childInfo: ListChildNodeInfoADM) => ZIO.succeed(ChildNodeInfoGetResponseADM(childInfo))
      case Some(rootInfo: ListRootNodeInfoADM)   => ZIO.succeed(RootNodeInfoGetResponseADM(rootInfo))
      case _                                     => ZIO.fail(NotFoundException(s"List node '$nodeIri' not found"))
    }

  /**
   * Retrieves a complete node including children. The node can be the lists root node or child node.
   *
   * @param nodeIri              the IRI of the list node to be queried.
   * @param shallow              denotes if all children or only the immediate children will be returned.
   * @return a optional [[ListNodeADM]]
   */
  private def listNodeGetADM(nodeIri: IRI, shallow: Boolean) = {
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
                                            case other =>
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
                                            case other =>
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
                                            case Some(other) =>
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
      ).unescape
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

    def getPositionOfNewChild(children: Seq[ListChildNodeADM]): IO[BadRequestException, Int] = {
      val size = children.size
      position.map(_.value) match {
        case Some(pos) if pos > size =>
          ZIO.fail(BadRequestException(s"Invalid position given $pos, maximum allowed position is = $size."))
        case Some(pos) if pos >= 0 => ZIO.succeed(pos)
        case _                     => ZIO.succeed(size)
      }
    }

    def getRootNodeIri(parentListNode: ListNodeADM): IRI =
      parentListNode match {
        case root: ListRootNodeADM   => root.id
        case child: ListChildNodeADM => child.hasRootNode
      }

    def getRootNodeAndPositionOfNewChild(
      parentNodeIri: IRI,
      dataNamedGraph: IRI,
    ): Task[(Some[Int], Some[IRI])] =
      for {
        /* Verify that the list node exists by retrieving the whole node including children one level deep (need for position calculation) */
        parentListNode <- listNodeGetADM(parentNodeIri, shallow = true)
                            .someOrFail(BadRequestException(s"List node '$parentNodeIri' not found."))
        children  = parentListNode.children
        position <- getPositionOfNewChild(children)

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
                                   dataNamedGraph = dataNamedGraph,
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
      project <- knoraProjectService
                   .findById(projectIri)
                   .someOrFail(BadRequestException(s"Project '$projectIri' not found."))

      /* verify that the list node name is unique for the project */
      _ <- ZIO.fail {
             val unescapedName = Iri.fromSparqlEncodedString(name.map(_.value).getOrElse(""))
             BadRequestException(
               s"The node name $unescapedName is already used by a list inside the project ${projectIri.value}.",
             )
           }.whenZIO(listNodeNameIsProjectUnique(projectIri.value, name).negate)

      // calculate the data named graph
      dataNamedGraph: IRI = ProjectService.projectDataNamedGraphV2(project).value

      // if parent node is known, find the root node of the list and the position of the new child node
      positionAndNode <-
        if (parentNode.nonEmpty) {
          getRootNodeAndPositionOfNewChild(
            parentNodeIri = parentNode.get.value,
            dataNamedGraph = dataNamedGraph,
          )
        } else {
          ZIO.attempt((None, None))
        }
      newPosition: Option[Int] = positionAndNode._1
      rootNodeIri: Option[IRI] = positionAndNode._2

      // check the custom IRI; if not given, create an unused IRI
      customListIri   = id.map(_.value).map(_.toSmartIri)
      newListNodeIri <- iriService.checkOrCreateEntityIri(customListIri, ListIri.makeNew(project).value)

      // Create the new list node depending on type
      createNewListSparqlString = createNodeRequest match {
                                    case r: ListCreateRootNodeRequest =>
                                      sparql.admin.txt
                                        .createNewListNode(
                                          dataNamedGraph = dataNamedGraph,
                                          listClassIri = KnoraBase.ListNode,
                                          projectIri = r.projectIri.value,
                                          nodeIri = newListNodeIri,
                                          parentNodeIri = None,
                                          rootNodeIri = rootNodeIri,
                                          position = None,
                                          maybeName = r.name.map(_.value),
                                          maybeLabels = r.labels.value,
                                          maybeComments = Some(r.comments.value),
                                        )
                                    case c: ListCreateChildNodeRequest =>
                                      sparql.admin.txt
                                        .createNewListNode(
                                          dataNamedGraph = dataNamedGraph,
                                          listClassIri = KnoraBase.ListNode,
                                          projectIri = c.projectIri.value,
                                          nodeIri = newListNodeIri,
                                          parentNodeIri = Some(c.parentNodeIri.value),
                                          rootNodeIri = rootNodeIri,
                                          position = newPosition,
                                          maybeName = c.name.map(_.value),
                                          maybeLabels = c.labels.value,
                                          maybeComments = c.comments.map(_.value),
                                        )
                                  }

      _ <- triplestore.query(Update(createNewListSparqlString))
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
    IriLocker.runWithIriLock(apiRequestID, LISTS_GLOBAL_LOCK_IRI, createTask)
  }

  /**
   * Changes basic node information stored (root or child)
   *
   * @param changeNodeRequest    the new node information.
   * @param apiRequestID         the unique api request ID.
   * @return a [[NodeInfoGetResponseADM]]
   * fails with a ForbiddenException          in the case that the user is not allowed to perform the operation.
   * fails with a UpdateNotPerformedException in the case something else went wrong, and the change could not be performed.
   */
  def nodeInfoChangeRequest(
    changeNodeRequest: ListChangeRequest,
    apiRequestID: UUID,
  ): Task[NodeInfoGetResponseADM] = {
    val nodeIri = changeNodeRequest.listIri.value
    val nodeInfoChangeTask =
      for {
        changeNodeInfoSparql <- getUpdateNodeInfoSparqlStatement(changeNodeRequest)
        _                    <- triplestore.query(Update(changeNodeInfoSparql))
        maybeNodeADM         <- listNodeInfoGetADM(changeNodeRequest.listIri.value)
        updated <-
          maybeNodeADM match {
            case Some(rootNode: ListRootNodeInfoADM)   => ZIO.succeed(RootNodeInfoGetResponseADM(rootNode))
            case Some(childNode: ListChildNodeInfoADM) => ZIO.succeed(ChildNodeInfoGetResponseADM(childNode))
            case _ =>
              ZIO.fail(
                UpdateNotPerformedException(
                  s"Node $nodeIri was not updated. Please report this as a possible bug.",
                ),
              )
          }
      } yield updated
    IriLocker.runWithIriLock(apiRequestID, nodeIri, nodeInfoChangeTask)
  }

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
        newListNode = maybeNewListNode match {
                        case Some(childNode: ListChildNodeInfoADM) => childNode
                        case Some(_: ListRootNodeInfoADM) =>
                          throw UpdateNotPerformedException(
                            s"Child node ${createChildNodeRequest.name} could not be created. Probably parent node Iri is missing in payload.",
                          )
                        case _ =>
                          throw UpdateNotPerformedException(
                            s"List node $newListNodeIri was not created. Please report this as a possible bug.",
                          )
                      }

      } yield ChildNodeInfoGetResponseADM(nodeinfo = newListNode)

    IriLocker.runWithIriLock(
      apiRequestID,
      LISTS_GLOBAL_LOCK_IRI,
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
  ): Task[NodeInfoGetResponseADM] = {
    val updateTask =
      for {
        project <- ensureUserIsAdminOrProjectOwner(listIri, requestingUser)

        updateQuery <-
          getUpdateNodeInfoSparqlStatement(
            changeNodeInfoRequest = ListChangeRequest(
              listIri = listIri,
              projectIri = project.id,
              name = Some(changeNameReq.name),
            ),
          )
        _       <- triplestore.query(Update(updateQuery))
        updated <- loadUpdatedListFromTriplestore(listIri)
      } yield updated

    IriLocker.runWithIriLock(apiRequestID, listIri.value, updateTask)
  }

  private def loadUpdatedListFromTriplestore(listIri: ListIri) =
    listNodeInfoGetADM(listIri.value).flatMap {
      case Some(rootNode: ListRootNodeInfoADM) =>
        ZIO.succeed(RootNodeInfoGetResponseADM(rootNode))
      case Some(childNode: ListChildNodeInfoADM) =>
        ZIO.succeed(ChildNodeInfoGetResponseADM(childNode))
      case _ =>
        ZIO.fail {
          val msg = s"Node ${listIri.value} was not updated. Please report this as a possible bug."
          UpdateNotPerformedException(msg)
        }
    }

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
  ): Task[NodeInfoGetResponseADM] = {
    val updateTask =
      for {
        project <- ensureUserIsAdminOrProjectOwner(listIri, requestingUser)

        updateQuery <- getUpdateNodeInfoSparqlStatement(
                         changeNodeInfoRequest = ListChangeRequest(
                           listIri = listIri,
                           projectIri = project.id,
                           labels = Some(changeNodeLabelsRequest.labels),
                         ),
                       )
        _ <- triplestore.query(Update(updateQuery))

        updated <- loadUpdatedListFromTriplestore(listIri)
      } yield updated
    IriLocker.runWithIriLock(apiRequestID, listIri.value, updateTask)
  }

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
  ): Task[NodeInfoGetResponseADM] = {
    val updateTask =
      for {
        project <- ensureUserIsAdminOrProjectOwner(listIri, requestingUser)

        changeNodeCommentsSparql <- getUpdateNodeInfoSparqlStatement(
                                      ListChangeRequest(
                                        listIri = listIri,
                                        projectIri = project.id,
                                        comments = Some(changeNodeCommentsRequest.comments),
                                      ),
                                    )
        _ <- triplestore.query(Update(changeNodeCommentsSparql))

        updated <- loadUpdatedListFromTriplestore(listIri)
      } yield updated

    IriLocker.runWithIriLock(apiRequestID, listIri.value, updateTask)
  }

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
        _ = if (updatedNode.id != nodeIri.value || updatedNode.position != newPosition) {
              throw UpdateNotPerformedException(
                s"Node is not repositioned correctly in specified parent node. Please report this as a bug.",
              )
            }
        leftPositions = siblingsPositionedBefore.map(child => child.position)
        _ = if (leftPositions != leftPositions.sorted) {
              throw UpdateNotPerformedException(
                s"Something has gone wrong with shifting nodes. Please report this as a bug.",
              )
            }
        siblingsPositionedAfter = rest.slice(1, rest.length)
        rightSiblings           = siblingsPositionedAfter.map(child => child.position)
        _ = if (rightSiblings != rightSiblings.sorted) {
              throw UpdateNotPerformedException(
                s"Something has gone wrong with shifting nodes. Please report this as a bug.",
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
      dataNamedGraph: IRI,
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
        newPosition =
          if (givenPosition == -1) {
            parentChildren.size - 1
          } else givenPosition

        // update the position of the node itself
        _ <- updatePositionOfNode(
               nodeIri = node.id,
               newPosition = newPosition,
               dataNamedGraph = dataNamedGraph,
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
                                   dataNamedGraph = dataNamedGraph,
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
                                   dataNamedGraph = dataNamedGraph,
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
     * @throws UpdateNotPerformedException in the case the given new position is the same as current position.
     */
    def updateParentAndPosition(
      node: ListChildNodeADM,
      newParentIri: IRI,
      currParentIri: IRI,
      givenPosition: Int,
      dataNamedGraph: IRI,
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
        newPosition =
          if (givenPosition == -1) {
            newSiblings.size
          } else givenPosition

        // update the position of the node itself
        _ <- updatePositionOfNode(
               nodeIri = node.id,
               newPosition = newPosition,
               dataNamedGraph = dataNamedGraph,
             )

        // shift current siblings with a higher position to left as if the node is deleted
        _ <- shiftNodes(
               startPos = currentNodePosition + 1,
               endPos = currentSiblings.last.position,
               nodes = currentSiblings,
               shiftToLeft = true,
               dataNamedGraph = dataNamedGraph,
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
                                   dataNamedGraph = dataNamedGraph,
                                 )
            } yield updatedSiblings
          }

        /* update the sublists of parent nodes */
        _ <- changeParentNode(
               nodeIri = node.id,
               oldParentIri = currParentIri,
               newParentIri = newParentIri,
               dataNamedGraph = dataNamedGraph,
             )

      } yield newPosition

    val updateTask =
      for {
        project <- ensureUserIsAdminOrProjectOwner(nodeIri, requestingUser)

        // get data names graph of the project
        dataNamedGraph <- getDataNamedGraph(project.id)
        // get node in its current position
        node <- listNodeGetADM(nodeIri.value, shallow = true)
                  .map(_.collect { case child: ListChildNodeADM => child })
                  .someOrFail(BadRequestException(s"Update of position is only allowed for child nodes!"))

        // get node's current parent
        currentParentNodeIri <- getParentNodeIRI(nodeIri.value)
        newPosition <-
          if (currentParentNodeIri == changeNodePositionRequest.parentNodeIri.value) {
            updatePositionWithinSameParent(
              node = node,
              parentIri = currentParentNodeIri,
              givenPosition = changeNodePositionRequest.position.value,
              dataNamedGraph = dataNamedGraph,
            )
          } else {
            updateParentAndPosition(
              node = node,
              newParentIri = changeNodePositionRequest.parentNodeIri.value,
              currParentIri = currentParentNodeIri,
              givenPosition = changeNodePositionRequest.position.value,
              dataNamedGraph = dataNamedGraph,
            )
          }
        /* Verify that the node position and parent children position were updated */
        parentNode <- verifyParentChildrenUpdate(newPosition)
      } yield NodePositionChangeResponseADM(node = parentNode)

    IriLocker.runWithIriLock(apiRequestID, nodeIri.value, updateTask)
  }

  /**
   * Checks if a list can be deleted (none of its nodes is used in data).
   */
  def canDeleteListRequestADM(iri: ListIri): Task[CanDeleteListResponseADM] =
    triplestore
      .query(Select(sparql.admin.txt.canDeleteList(iri.value)))
      .map(_.results.bindings.isEmpty)
      .map(CanDeleteListResponseADM(iri.value, _))

  /**
   * Deletes all comments from requested list node (only child).
   */
  def deleteListNodeCommentsADM(nodeIri: ListIri): Task[ListNodeCommentsDeleteResponseADM] =
    for {
      node <- listNodeInfoGetADM(nodeIri.value).someOrFail(NotFoundException(s"Node ${nodeIri.value} not found."))
      _ <- ZIO
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
        .whenZIO(triplestore.query(Select(sparql.admin.txt.isNodeUsed(nodeIri))).map(_.results.bindings.nonEmpty))
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
     * @param dataNamedGraph        the data named graph.
     * @return a [[ListNodeADM]]
     * @throws UpdateNotPerformedException if the node that had to be deleted is still in the list of parent's children.
     */
    def updateParentNode(
      deletedNodeIri: IRI,
      positionOfDeletedNode: Int,
      parentNodeIri: IRI,
      dataNamedGraph: IRI,
    ): Task[ListNodeADM] =
      for {
        parentNode <-
          listNodeGetADM(parentNodeIri, shallow = false)
            .someOrFail(BadRequestException(s"The parent node of $deletedNodeIri not found, report this as a bug."))

        remainingChildren = parentNode.children

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
                                   dataNamedGraph = dataNamedGraph,
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
                        _             <- isNodeOrItsChildrenUsed(childNode.id, childNode.children)
                        parentNodeIri <- getParentNodeIRI(nodeIri.value)
                        dataNamedGraph <-
                          deleteListItem(childNode.id, projectIri, childNode.children, isRootNode = false)
                        updatedParentNode <-
                          updateParentNode(nodeIri.value, childNode.position, parentNodeIri, dataNamedGraph)
                      } yield ChildNodeDeleteResponseADM(updatedParentNode)

                    case _ =>
                      ZIO.fail {
                        val msg = s"Node ${nodeIri.value} was not found. Please verify the given IRI."
                        BadRequestException(msg)
                      }
                  }
    } yield response

    IriLocker.runWithIriLock(apiRequestID, nodeIri.value, nodeDeleteTask)
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
  private def rootNodeByIriExists(rootNodeIri: IRI): Task[Boolean] =
    triplestore.query(Ask(sparql.admin.txt.checkListRootNodeExistsByIri(rootNodeIri)))

  /**
   * Helper method for checking if a node identified by IRI exists.
   *
   * @param nodeIri the IRI of the project.
   * @return a [[Boolean]].
   */
  private def nodeByIriExists(nodeIri: IRI): Task[Boolean] =
    triplestore.query(Ask(sparql.admin.txt.checkListNodeExistsByIri(nodeIri)))

  /**
   * Helper method for checking if a list node name is not used in any list inside a project. Returns a 'TRUE' if the
   * name is NOT used inside any list of this project.
   *
   * @param projectIri   the IRI of the project.
   * @param listNodeName the list node name.
   * @return a [[Boolean]].
   */
  private def listNodeNameIsProjectUnique(projectIri: IRI, listNodeName: Option[ListName]): Task[Boolean] =
    listNodeName match {
      case Some(name) =>
        triplestore.query(Ask(sparql.admin.txt.checkListNodeNameIsProjectUnique(projectIri, name.value))).negate

      case None => ZIO.succeed(true)
    }

  /**
   * Helper method to generate a sparql statement for updating node information.
   *
   * @param changeNodeInfoRequest the node information to change.
   * @return a [[String]].
   */
  private def getUpdateNodeInfoSparqlStatement(
    changeNodeInfoRequest: ListChangeRequest,
  ): Task[String] =
    for {
      // get the data graph of the project.
      dataNamedGraph <- getDataNamedGraph(changeNodeInfoRequest.projectIri)

      /* verify that the list name is unique for the project */
      _ <- ZIO.fail {
             val msg =
               s"The name ${changeNodeInfoRequest.name.get} is already used by a list inside the project ${changeNodeInfoRequest.projectIri.value}."
             DuplicateValueException(msg)
           }.whenZIO(
             listNodeNameIsProjectUnique(changeNodeInfoRequest.projectIri.value, changeNodeInfoRequest.name).negate,
           )

      /* Verify that the node with Iri exists. */
      node <- listNodeGetADM(changeNodeInfoRequest.listIri.value, shallow = true)
                .someOrFail(BadRequestException(s"List item with '${changeNodeInfoRequest.listIri}' not found."))

      // Update the list
      changeNodeInfoSparqlString: String = sparql.admin.txt
                                             .updateListInfo(
                                               dataNamedGraph = dataNamedGraph,
                                               nodeIri = changeNodeInfoRequest.listIri.value,
                                               hasOldName = node.name.nonEmpty,
                                               isRootNode = node.isInstanceOf[ListRootNodeADM],
                                               maybeName = changeNodeInfoRequest.name.map(_.value),
                                               projectIri = changeNodeInfoRequest.projectIri.value,
                                               listClassIri = KnoraBase.ListNode,
                                               maybeLabels = changeNodeInfoRequest.labels.map(_.value),
                                               maybeComments = changeNodeInfoRequest.comments.map(_.value),
                                             )
                                             .toString
    } yield changeNodeInfoSparqlString

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
                             iriStr <- maybeRoot.collect { case it: ListRootNodeADM => it }
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
      // Verify that the node was deleted correctly.
      nodeStillExists <- nodeByIriExists(nodeIri)

      _ = if (nodeStillExists) {
            throw UpdateNotPerformedException(s"Node <$nodeIri> was not erased. Please report this as a possible bug.")
          }
    } yield ()

  /**
   * Helper method to update position of a node without changing its parent.
   *
   * @param nodeIri              the IRI of the node that must be shifted.
   * @param newPosition          the new position of the child node.
   * @param dataNamedGraph       the data named graph of the project.
   * @return a [[ListChildNodeADM]].
   */
  private def updatePositionOfNode(
    nodeIri: IRI,
    newPosition: Int,
    dataNamedGraph: IRI,
  ): Task[ListChildNodeADM] =
    for {
      _ <- triplestore.query(Update(sparql.admin.txt.updateNodePosition(dataNamedGraph, nodeIri, newPosition)))
      /* Verify that the node info was updated */
      childNode <- listNodeGetADM(nodeIri = nodeIri, shallow = false)
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

  /**
   * Helper method to shift nodes between positions startPos and endPos to the left if 'shiftToLeft' is true,
   * otherwise shift them one position to the right.
   *
   * @param startPos             the position of first node in range that must be shifted.
   * @param endPos               the position of last node in range that must be shifted.
   * @param nodes                the list of all nodes.
   * @param shiftToLeft          shift nodes to left if true, otherwise to right.
   * @param dataNamedGraph       the data named graph of the project.
   * @throws UpdateNotPerformedException if the position of a node could not be updated.
   * @return a sequence of [[ListChildNodeADM]].
   */
  private def shiftNodes(
    startPos: Int,
    endPos: Int,
    nodes: Seq[ListChildNodeADM],
    shiftToLeft: Boolean,
    dataNamedGraph: IRI,
  ): Task[Seq[ListChildNodeADM]] =
    for {
      nodesTobeUpdated <- ZIO.attempt(
                            nodes.filter(node => node.position >= startPos && node.position <= endPos),
                          )
      staticStartNodes = nodes.filter(node => node.position < startPos)
      staticEndNotes   = nodes.filter(node => node.position > endPos)
      updatePositionFutures = nodesTobeUpdated.map { child =>
                                val currPos = child.position
                                val newPos = if (shiftToLeft) {
                                  currPos - 1
                                } else currPos + 1

                                updatePositionOfNode(
                                  nodeIri = child.id,
                                  newPosition = newPos,
                                  dataNamedGraph = dataNamedGraph,
                                )
                              }
      updatedNodes <- ZioHelper.sequence(updatePositionFutures)
    } yield staticStartNodes ++ updatedNodes ++ staticEndNotes

  /**
   * Helper method to change parent node of a node.
   *
   * @param nodeIri              the IRI of the node.
   * @param oldParentIri         the IRI of the current parent node.
   * @param newParentIri         the IRI of the new parent node.
   * @param dataNamedGraph       the data named graph of the project.
   * @throws UpdateNotPerformedException if the parent of a node could not be updated.
   */
  private def changeParentNode(
    nodeIri: IRI,
    oldParentIri: IRI,
    newParentIri: IRI,
    dataNamedGraph: IRI,
  ): Task[Unit] = for {
    _ <-
      triplestore.query(Update(sparql.admin.txt.changeParentNode(dataNamedGraph, nodeIri, oldParentIri, newParentIri)))

    /* verify that parents were updated */
    // get old parent node with its immediate children
    maybeOldParent     <- listNodeGetADM(nodeIri = oldParentIri, shallow = true)
    childrenOfOldParent = maybeOldParent.get.children
    _ = if (childrenOfOldParent.exists(node => node.id == nodeIri)) {
          throw UpdateNotPerformedException(
            s"Node $nodeIri is still a child of $oldParentIri. Report this as a bug.",
          )
        }
    // get new parent node with its immediate children
    maybeNewParentNode <- listNodeGetADM(nodeIri = newParentIri, shallow = true)
    childrenOfNewParent = maybeNewParentNode.get.children
    _ = if (!childrenOfNewParent.exists(node => node.id == nodeIri)) {
          throw UpdateNotPerformedException(s"Node $nodeIri is not added to parent node $newParentIri. ")
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

  def getLists(projectIri: Option[ProjectIri]): ZIO[ListsResponder, Throwable, ListsGetResponseADM] =
    ZIO.serviceWithZIO[ListsResponder](_.getLists(projectIri))

  def listGetRequestADM(nodeIri: IRI): ZIO[ListsResponder, Throwable, ListItemGetResponseADM] =
    ZIO.serviceWithZIO[ListsResponder](_.listGetRequestADM(nodeIri))

  def listNodeInfoGetRequestADM(nodeIri: String): ZIO[ListsResponder, Throwable, NodeInfoGetResponseADM] =
    ZIO.serviceWithZIO[ListsResponder](_.listNodeInfoGetRequestADM(nodeIri))

  def listCreateRootNode(
    req: ListCreateRootNodeRequest,
    apiRequestID: UUID,
  ): ZIO[ListsResponder, Throwable, ListGetResponseADM] =
    ZIO.serviceWithZIO[ListsResponder](_.listCreateRootNode(req, apiRequestID))

  def listCreateChildNode(
    req: ListCreateChildNodeRequest,
    apiRequestID: UUID,
  ): ZIO[ListsResponder, Throwable, ChildNodeInfoGetResponseADM] =
    ZIO.serviceWithZIO[ListsResponder](_.listCreateChildNode(req, apiRequestID))

  def nodePositionChangeRequestADM(
    nodeIri: ListIri,
    changeNodePositionRequest: ListChangePositionRequest,
    requestingUser: User,
    apiRequestID: UUID,
  ): ZIO[ListsResponder, Throwable, NodePositionChangeResponseADM] =
    ZIO.serviceWithZIO[ListsResponder](
      _.nodePositionChangeRequest(nodeIri, changeNodePositionRequest, requestingUser, apiRequestID),
    )

  def nodeInfoChangeRequest(
    req: ListChangeRequest,
    apiRequestId: UUID,
  ): ZIO[ListsResponder, Throwable, NodeInfoGetResponseADM] =
    ZIO.serviceWithZIO[ListsResponder](_.nodeInfoChangeRequest(req, apiRequestId))

  def deleteListItemRequestADM(
    iri: ListIri,
    user: User,
    uuid: UUID,
  ): ZIO[ListsResponder, Throwable, ListItemDeleteResponseADM] =
    ZIO.serviceWithZIO[ListsResponder](_.deleteListItemRequestADM(iri, user, uuid))

  def deleteListNodeCommentsADM(iri: ListIri): ZIO[ListsResponder, Throwable, ListNodeCommentsDeleteResponseADM] =
    ZIO.serviceWithZIO[ListsResponder](_.deleteListNodeCommentsADM(iri))

  def canDeleteListRequestADM(iri: ListIri): ZIO[ListsResponder, Throwable, CanDeleteListResponseADM] =
    ZIO.serviceWithZIO[ListsResponder](_.canDeleteListRequestADM(iri))

  val layer = ZLayer.derive[ListsResponder]
}
