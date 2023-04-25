/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v1

import zio._

import scala.annotation.tailrec

import dsp.errors.NotFoundException
import org.knora.webapi._
import org.knora.webapi.config.AppConfig
import org.knora.webapi.core.MessageHandler
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.ResponderRequest
import org.knora.webapi.messages.util.rdf.VariableResultsRow
import org.knora.webapi.messages.v1.responder.listmessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.responders.Responder
import org.knora.webapi.store.triplestore.api.TriplestoreService

/**
 * A responder that returns information about hierarchical lists.
 */
trait ListsResponderV1 {

  /**
   * Retrieves a list from the triplestore and returns it as a [[ListGetResponseV1]].
   * Due to compatibility with the old, crappy SALSAH-API, "hlists" and "selection" have to be differentiated in the response
   * [[ListGetResponseV1]] is the abstract super class of [[HListGetResponseV1]] and [[SelectionGetResponseV1]]
   *
   * @param rootNodeIri the Iri if the root node of the list to be queried.
   * @param userProfile the profile of the user making the request.
   * @param pathType    the type of the list (HList or Selection).
   * @return a [[ListGetResponseV1]].
   */
  def listGetRequestV1(rootNodeIri: IRI, userProfile: UserProfileV1, pathType: PathType.Value): Task[ListGetResponseV1]

  /**
   * Provides the path to a particular hierarchical list node.
   *
   * @param queryNodeIri the IRI of the node whose path is to be queried.
   * @param userProfile  the profile of the user making the request.
   */
  def getNodePathResponseV1(queryNodeIri: IRI, userProfile: UserProfileV1): Task[NodePathGetResponseV1]
}

final case class ListsResponderV1Live(
  appConfig: AppConfig,
  triplestoreService: TriplestoreService
) extends ListsResponderV1
    with MessageHandler {

  override def isResponsibleFor(message: ResponderRequest): Boolean =
    message.isInstanceOf[ListsResponderRequestV1]

  override def handle(msg: ResponderRequest): Task[Any] = msg match {
    case HListGetRequestV1(listIri, userProfile)                    => listGetRequestV1(listIri, userProfile, PathType.HList)
    case SelectionGetRequestV1(listIri, userProfile)                => listGetRequestV1(listIri, userProfile, PathType.Selection)
    case NodePathGetRequestV1(iri: IRI, userProfile: UserProfileV1) => getNodePathResponseV1(iri, userProfile)
    case other                                                      => Responder.handleUnexpectedMessage(other, this.getClass.getName)
  }

  /**
   * Retrieves a list from the triplestore and returns it as a [[ListGetResponseV1]].
   * Due to compatibility with the old, crappy SALSAH-API, "hlists" and "selection" have to be differentiated in the response
   * [[ListGetResponseV1]] is the abstract super class of [[HListGetResponseV1]] and [[SelectionGetResponseV1]]
   *
   * @param rootNodeIri the Iri if the root node of the list to be queried.
   * @param userProfile the profile of the user making the request.
   * @param pathType    the type of the list (HList or Selection).
   * @return a [[ListGetResponseV1]].
   */
  override def listGetRequestV1(
    rootNodeIri: IRI,
    userProfile: UserProfileV1,
    pathType: PathType.Value
  ): Task[ListGetResponseV1] =
    for {
      maybeChildren <- listGetV1(rootNodeIri, userProfile)
      children <-
        maybeChildren match {
          case children: Seq[ListNodeV1] if children.nonEmpty => ZIO.succeed(children)
          case _                                              => ZIO.fail(NotFoundException(s"List not found: $rootNodeIri"))
        }

      // consider routing path here ("hlists" | "selections") and return the correct case class
      result = pathType match {
                 case PathType.HList     => HListGetResponseV1(hlist = children)
                 case PathType.Selection => SelectionGetResponseV1(selection = children)
               }

    } yield result

  /**
   * Retrieves a list from the triplestore and returns it as a sequence of child nodes.
   *
   * @param rootNodeIri the Iri of the root node of the list to be queried.
   * @param userProfile the profile of the user making the request.
   * @return a sequence of [[ListNodeV1]].
   */
  private def listGetV1(rootNodeIri: IRI, userProfile: UserProfileV1): Task[Seq[ListNodeV1]] = {

    /**
     * Compares the `position`-values of two nodes
     *
     * @param list1 node in a list
     * @param list2 node in the same list
     * @return true if the `position` of list1 is lower than the one of list2
     */
    def orderNodes(list1: ListNodeV1, list2: ListNodeV1): Boolean =
      list1.position < list2.position

    /**
     * This function recursively transforms SPARQL query results representing a hierarchical list into a [[ListNodeV1]].
     *
     * @param nodeIri          the IRI of the node to be created.
     * @param groupedByNodeIri a [[Map]] in which each key is the IRI of a node in the hierarchical list, and each value is a [[Seq]]
     *                         of SPARQL query results representing that node's children.
     * @return a [[ListNodeV1]].
     */
    def createHierarchicalListV1(
      nodeIri: IRI,
      groupedByNodeIri: Map[IRI, Seq[VariableResultsRow]],
      level: Int
    ): ListNodeV1 = {
      val childRows = groupedByNodeIri(nodeIri)

      /*
                childRows has the following structure:

                For each child of the parent node (represented by nodeIri), there is a row that provides the child's IRI.
                The information about the parent node is repeated in each row.
                Therefore, we can just access the first row for all the information about the parent.

                node                               position	   nodeName   label         child

                http://rdfh.ch/lists/10d16738cc    3            4          VOLKSKUNDE    http://rdfh.ch/lists/a665b90cd
                http://rdfh.ch/lists/10d16738cc    3            4          VOLKSKUNDE    http://rdfh.ch/lists/4238eabcc
                http://rdfh.ch/lists/10d16738cc    3            4          VOLKSKUNDE    http://rdfh.ch/lists/a94bb71cc
                http://rdfh.ch/lists/10d16738cc    3            4          VOLKSKUNDE    http://rdfh.ch/lists/db6b61e4cc
                http://rdfh.ch/lists/10d16738cc    3            4          VOLKSKUNDE    http://rdfh.ch/lists/749fb41dcd
                http://rdfh.ch/lists/10d16738cc    3            4          VOLKSKUNDE    http://rdfh.ch/lists/dd3757cd

                In any case, childRows has at least one element (we know that at least one entry exists for a node without children).

       */

      val firstRowMap = childRows.head.rowMap

      ListNodeV1(
        id = nodeIri,
        name = firstRowMap.get("nodeName"),
        label = firstRowMap.get("label"),
        children = if (!firstRowMap.contains("child")) {
          // If this node has no children, childRows will just contain one row with no value for "child".
          Nil
        } else {
          // Recursively get the child nodes.
          childRows
            .map(childRow => createHierarchicalListV1(childRow.rowMap("child"), groupedByNodeIri, level + 1))
            .sortWith(orderNodes)
        },
        position = firstRowMap("position").toInt,
        level = level
      )
    }

    for {
      listQuery <- ZIO.attempt {
                     org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                       .getList(
                         rootNodeIri = rootNodeIri,
                         preferredLanguage = userProfile.userData.lang,
                         fallbackLanguage = appConfig.fallbackLanguage
                       )
                       .toString()
                   }
      listQueryResponse <- triplestoreService.sparqlHttpSelect(listQuery)

      // Group the results to map each node to the SPARQL query results representing its children.
      groupedByNodeIri: Map[IRI, Seq[VariableResultsRow]] =
        listQueryResponse.results.bindings.groupBy(row => row.rowMap("node"))

      rootNodeChildren = groupedByNodeIri.getOrElse(rootNodeIri, Seq.empty[VariableResultsRow])

      children: Seq[ListNodeV1] =
        if (!rootNodeChildren.head.rowMap.contains("child")) {
          // The root node has no children, so we return an empty list.
          Seq.empty[ListNodeV1]
        } else {
          // Process each child of the root node.
          rootNodeChildren.map { childRow =>
            createHierarchicalListV1(childRow.rowMap("child"), groupedByNodeIri, 0)
          }
            .sortWith(orderNodes)
        }

    } yield children
  }

  /**
   * Provides the path to a particular hierarchical list node.
   *
   * @param queryNodeIri the IRI of the node whose path is to be queried.
   * @param userProfile  the profile of the user making the request.
   */
  override def getNodePathResponseV1(queryNodeIri: IRI, userProfile: UserProfileV1): Task[NodePathGetResponseV1] = {

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
      path: Seq[NodePathElementV1]
    ): Seq[NodePathElementV1] = {
      // Get the details of the node.
      val nodeData = nodeMap(node)

      // Construct a NodePathElementV1 containing those details.
      val pathElement = NodePathElementV1(
        id = nodeData("node"),
        name = nodeData.get("nodeName"),
        label = nodeData.get("label")
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

    for {
      nodePathQuery <- ZIO.attempt {
                         org.knora.webapi.messages.twirl.queries.sparql.v1.txt
                           .getNodePath(
                             queryNodeIri = queryNodeIri,
                             preferredLanguage = userProfile.userData.lang,
                             fallbackLanguage = appConfig.fallbackLanguage
                           )
                           .toString()
                       }
      nodePathResponse <- triplestoreService.sparqlHttpSelect(nodePathQuery)

      /*

            If we request the path to the node <http://rdfh.ch/lists/c7f07a3fc1> ("Heidi Film"), the response has the following format:

            node                                 nodeName     label                     child
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
    } yield NodePathGetResponseV1(
      nodelist = makePath(queryNodeIri, nodeMap, parentMap, Nil)
    )
  }
}

object ListsResponderV1Live {
  val layer: URLayer[TriplestoreService with MessageRelay with AppConfig, ListsResponderV1] = ZLayer.fromZIO {
    for {
      config  <- ZIO.service[AppConfig]
      mr      <- ZIO.service[MessageRelay]
      ts      <- ZIO.service[TriplestoreService]
      handler <- mr.subscribe(ListsResponderV1Live(config, ts))
    } yield handler
  }
}
