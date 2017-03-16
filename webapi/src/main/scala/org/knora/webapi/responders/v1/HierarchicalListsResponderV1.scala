/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

package org.knora.webapi.responders.v1

import akka.actor.Status
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1.responder.listmessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectRequest, SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.util.ActorUtil._

import scala.annotation.tailrec
import scala.collection.breakOut
import scala.concurrent.Future

/**
  * A responder that returns information about hierarchical lists.
  */
class HierarchicalListsResponderV1 extends ResponderV1 {

    /**
      * An enumeration whose values correspond to the types of hierarchical list objects that this actor can
      * produce: "hlists" | "selections".
      */
    object PathType extends Enumeration {
        val HList, Selection = Value
    }

    def receive = {
        case HListGetRequestV1(listIri, userProfile) => future2Message(sender(), getListResponseV1(listIri, userProfile, PathType.HList), log)
        case SelectionGetRequestV1(listIri, userProfile) => future2Message(sender(), getListResponseV1(listIri, userProfile, PathType.Selection), log)
        case NodePathGetRequestV1(iri: IRI, userProfile: UserProfileV1) => future2Message(sender(), getNodePathResponseV1(iri, userProfile), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Methods for generating complete API responses.

    /**
      * Retrieves a list from the triplestore and returns it as a [[ListsGetResponseV1]].
      * Due to compatibility with the old, crappy SALSAH-API, "hlists" and "selection" have to be differentiated in the response
      * [[ListsGetResponseV1]] is the abstract super class of [[HListGetResponseV1]] and [[SelectionGetResponseV1]]
      *
      * @param rootNodeIri the Iri if the root node of the list to be queried.
      * @return a [[ListsGetResponseV1]].
      */
    private def getListResponseV1(rootNodeIri: IRI, userProfile: UserProfileV1, pathType: PathType.Value): Future[ListsGetResponseV1] = {

        /**
          * Compares the `position`-values of two nodes
          *
          * @param list1 node in a list
          * @param list2 node in the same list
          * @return true if the `position` of list1 is lower than the one of list2
          */
        def orderNodes(list1: HierarchicalListV1, list2: HierarchicalListV1): Boolean = {
            list1.position < list2.position
        }

        /**
          * This function recursively transforms SPARQL query results representing a hierarchical list into a [[HierarchicalListV1]].
          *
          * @param nodeIri          the IRI of the node to be created.
          * @param groupedByNodeIri a [[Map]] in which each key is the IRI of a node in the hierarchical list, and each value is a [[Seq]]
          *                         of SPARQL query results representing that node's children.
          * @return a [[HierarchicalListV1]].
          */
        def createHierarchicalListV1(nodeIri: IRI, groupedByNodeIri: Map[IRI, Seq[VariableResultsRow]], level: Int): HierarchicalListV1 = {
            val childRows = groupedByNodeIri(nodeIri)

            /*
                childRows has the following structure:

                For each child of the parent node (represented by nodeIri), there is a row that provides the child's IRI.
                The information about the parent node is repeated in each row.
                Therefore, we can just access the first row for all the information about the parent.

                node                                      position	   nodeName   label         child

                http://data.knora.org/lists/10d16738cc    3            4          VOLKSKUNDE    http://data.knora.org/lists/a665b90cd
                http://data.knora.org/lists/10d16738cc    3            4          VOLKSKUNDE    http://data.knora.org/lists/4238eabcc
                http://data.knora.org/lists/10d16738cc    3            4          VOLKSKUNDE    http://data.knora.org/lists/a94bb71cc
                http://data.knora.org/lists/10d16738cc    3            4          VOLKSKUNDE    http://data.knora.org/lists/db6b61e4cc
                http://data.knora.org/lists/10d16738cc    3            4          VOLKSKUNDE    http://data.knora.org/lists/749fb41dcd
                http://data.knora.org/lists/10d16738cc    3            4          VOLKSKUNDE    http://data.knora.org/lists/dd3757cd

                In any case, childRows has at least one element (we know that at least one entry exists for a node without children).

             */

            val firstRowMap = childRows.head.rowMap

            HierarchicalListV1(
                id = nodeIri,
                name = firstRowMap.get("nodeName"),
                label = firstRowMap.get("label"),
                children = if (firstRowMap.get("child").isEmpty) {
                    // If this node has no children, childRows will just contain one row with no value for "child".
                    Nil
                } else {
                    // Recursively get the child nodes.
                    childRows.map(childRow => createHierarchicalListV1(childRow.rowMap("child"), groupedByNodeIri, level + 1)).sortWith(orderNodes)
                },
                position = firstRowMap("position").toInt,
                level = level
            )

        }

        for {
            listQuery <- Future {
                queries.sparql.v1.txt.getList(
                    triplestore = settings.triplestoreType,
                    rootNodeIri = rootNodeIri,
                    preferredLanguage = userProfile.userData.lang,
                    fallbackLanguage = settings.fallbackLanguage
                ).toString()
            }
            listQueryResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(listQuery)).mapTo[SparqlSelectResponse]

            // Group the results to map each node to the SPARQL query results representing its children.
            groupedByNodeIri: Map[IRI, Seq[VariableResultsRow]] = listQueryResponse.results.bindings.groupBy(row => row.rowMap("node"))

            rootNodeChildren = groupedByNodeIri.get(rootNodeIri) match {
                case Some(resultsList) => resultsList
                case _ => throw NotFoundException(s"List not found: $rootNodeIri")
            }

            children: Seq[HierarchicalListV1] = if (rootNodeChildren.head.rowMap.get("child").isEmpty) {
                // The root node has no children.
                Nil
            } else {
                // Process each child of the root node.
                rootNodeChildren.map {
                    childRow => createHierarchicalListV1(childRow.rowMap("child"), groupedByNodeIri, 0)
                }.sortWith(orderNodes)
            }

            // consider routing path here ("hlists" | "selections") and return the correct case class
            list = pathType match {
                case PathType.HList =>
                    HListGetResponseV1(
                        hlist = children
                    )

                case PathType.Selection =>
                    SelectionGetResponseV1(
                        selection = children
                    )
            }

        } yield list
    }

    /**
      * Provides the path to a particular hierarchical list node.
      *
      * @param queryNodeIri the IRI of the node whose path is to be queried.
      * @param userProfile  the profile of the user making the request.
      */
    private def getNodePathResponseV1(queryNodeIri: IRI, userProfile: UserProfileV1): Future[NodePathGetResponseV1] = {
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
        def makePath(node: IRI, nodeMap: Map[IRI, Map[String, String]], parentMap: Map[IRI, IRI], path: Seq[NodePathElementV1]): Seq[NodePathElementV1] = {
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
            nodePathQuery <- Future {
                queries.sparql.v1.txt.getNodePath(
                    triplestore = settings.triplestoreType,
                    queryNodeIri = queryNodeIri,
                    preferredLanguage = userProfile.userData.lang,
                    fallbackLanguage = settings.fallbackLanguage
                ).toString()
            }
            nodePathResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(nodePathQuery)).mapTo[SparqlSelectResponse]

            /*

            If we request the path to the node <http://data.knora.org/lists/c7f07a3fc1> ("Heidi Film"), the response has the following format:

            node                                        nodeName     label                     child
            <http://data.knora.org/lists/c7f07a3fc1>    1            Heidi Film
            <http://data.knora.org/lists/2ebd2706c1>    7            FILM UND FOTO             <http://data.knora.org/lists/c7f07a3fc1>
            <http://data.knora.org/lists/691eee1cbe>    4KUN         ART                       <http://data.knora.org/lists/2ebd2706c1>

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
        } yield NodePathGetResponseV1(
            nodelist = makePath(queryNodeIri, nodeMap, parentMap, Nil)
        )
    }
}
