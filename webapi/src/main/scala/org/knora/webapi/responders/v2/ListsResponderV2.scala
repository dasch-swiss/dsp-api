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

package org.knora.webapi.responders.v2

import akka.pattern._
import org.apache.jena.sparql.procedure.library.debug
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.messages.v1.responder.usermessages.UserProfileV1
import org.knora.webapi.messages.v2.responder.listmessages._
import org.knora.webapi.responders.Responder
import org.knora.webapi.util.ActorUtil._

import scala.annotation.tailrec
import scala.collection.breakOut
import scala.concurrent.Future

/**
  * A responder that returns information about hierarchical lists.
  */
class ListsResponderV2 extends Responder {

    def receive = {
        case ListsGetRequestV2(projectIri, userProfile) => future2Message(sender(), listsGetRequestV2(projectIri, userProfile), log)
        case ListExtendedGetRequestV2(listIri, userProfile) => future2Message(sender(), listExtendedGetRequestV2(listIri, userProfile), log)
        case ListNodeInfoGetRequestV2(listIri, userProfile) => future2Message(sender(), listNodeInfoGetRequestV2(listIri, userProfile), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
    }


    /**
      * Gets all lists and returns them as a sequence of [[ListInfoV2]].
      *
      * @param projectIri  the IRI of the project the list belongs to.
      * @param userProfile the profile of the user making the request.
      * @return
      */
    def listsGetRequestV2(projectIri: Option[IRI], userProfile: UserProfileV1): Future[ListsGetResponseV2] = {

        log.debug("listsGetRequestV2")

        for {
            sparqlQuery <- Future(queries.sparql.v2.txt.getLists(
                triplestore = settings.triplestoreType,
                maybeProjectIri = projectIri
            ).toString())

            listsResponse <- (storeManager ? SparqlConstructRequest(sparqlQuery)).mapTo[SparqlConstructResponse]

            _ = log.debug("listsGetRequestV2 - listsResponse: {}", listsResponse )

            lists = Seq.empty[ListInfoV2]

            /*
            listsResponseRows: Seq[VariableResultsRow] = listsResponse.results.bindings

            listsWithProperties: Map[String, Map[String, Seq[String]]] = listsResponseRows.groupBy(_.rowMap("s")).map {
                case (listIri: String, rows: Seq[VariableResultsRow]) => listIri -> rows.groupBy(_.rowMap("p")).map {
                    case (predicate, rows: Seq[VariableResultsRow]) => predicate -> rows.map(_.rowMap("o"))
                }
            }

            lists = listsWithProperties.map {
                case (listIri: IRI, propsMap: Map[String, Seq[String]]) =>

                    ListInfoV2(
                        id = listIri,
                        projectIri = propsMap.get(OntologyConstants.KnoraBase.AttachedToProject).map(_.head),
                        labels = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[String]),
                        comment = propsMap.get(OntologyConstants.Rdfs.Comment).map(_.head)
                    )
            }.toVector
            */
        } yield ListsGetResponseV2(
            lists = lists
        )
    }

    /**
      * Retrieves a list from the triplestore and returns it as a [[org.knora.webapi.messages.v2.responder.listmessages.ListExtendedGetResponseV2]].
      *
      * @param rootNodeIri the Iri if the root node of the list to be queried.
      * @param userProfile the profile of the user making the request.
      * @return a [[ListExtendedGetResponseV2]].
      */
    def listExtendedGetRequestV2(rootNodeIri: IRI, userProfile: UserProfileV1): Future[ListExtendedGetResponseV2] = {

        for {
            sparqlQuery <- Future(queries.sparql.v2.txt.getListNode(
                triplestore = settings.triplestoreType,
                nodeIri = rootNodeIri,
                isRootNode = true
            ).toString())

            listInfoResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            _ = if (listInfoResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"List not found: $rootNodeIri")
            }

            groupedListProperties: Map[String, Seq[String]] = listInfoResponse.results.bindings.groupBy(_.rowMap("p")).map {
                case (predicate: String, rows: Seq[VariableResultsRow]) => predicate -> rows.map(_.rowMap("o"))
            }

            // _ = log.debug(s"listExtendedGetRequestV2 - groupedListProperties: ${MessageUtil.toSource(groupedListProperties)}")

            listInfo: ListInfoV2 = ListInfoV2(
                id = rootNodeIri,
                projectIri = groupedListProperties.get(OntologyConstants.KnoraBase.AttachedToProject).map(_.head),
                labels = groupedListProperties.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[String]),
                comment = groupedListProperties.get(OntologyConstants.Rdfs.Comment).map(_.head)

                // status = groupedListProperties.get(OntologyConstants.KnoraBase.Status).map(_.head.toBoolean)
            )

            // here we know that the list exists and it is fine if children is an empty list
            children: Seq[ListNodeV2] <- listGetV2(rootNodeIri, userProfile)

            // _ = log.debug(s"listExtendedGetRequestV2 - children: {}", MessageUtil.toSource(children)")

        } yield ListExtendedGetResponseV2(info = listInfo, nodes = children)
    }


    def listNodeInfoGetRequestV2(nodeIri: IRI, userProfile: UserProfileV1): Future[ListNodeInfoGetResponseV2] = {
        for {
            sparqlQuery <- Future(queries.sparql.v2.txt.getListNode(
                triplestore = settings.triplestoreType,
                nodeIri = nodeIri,
                isRootNode = false
            ).toString())

            // _ = log.debug("listNodeInfoGetRequestV2 - sparqlQuery: {}", sparqlQuery)

            listNodeResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            _ = if (listNodeResponse.results.bindings.isEmpty) {
                throw NotFoundException(s"List node not found: $nodeIri")
            }

            groupedListProperties: Map[String, Seq[String]] = listNodeResponse.results.bindings.groupBy(_.rowMap("p")).map {
                case (predicate: String, rows: Seq[VariableResultsRow]) => predicate -> rows.map(_.rowMap("o"))
            }

            // _ = log.debug(s"listNodeInfoGetRequestV2 - groupedListProperties: {}", MessageUtil.toSource(groupedListProperties))

            listNodeInfo: ListNodeInfoV2 = ListNodeInfoV2(
                id = nodeIri,
                labels = groupedListProperties.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[String]),
                comment = groupedListProperties.get(OntologyConstants.Rdfs.Comment).map(_.head)

                // status = groupedListProperties.get(OntologyConstants.KnoraBase.Status).map(_.head.toBoolean)
            )

            // _ = log.debug(s"listNodeInfoGetRequestV2 - listNodeInfo: {}", MessageUtil.toSource(listNodeInfo))

        } yield ListNodeInfoGetResponseV2(id = listNodeInfo.id, labels = listNodeInfo.labels, comment = listNodeInfo.comment)
    }

    /**
      * Retrieves a list from the triplestore and returns it as a [[org.knora.webapi.messages.v2.responder.listmessages.ListGetResponseV2]].
      * Due to compatibility with the old, crappy SALSAH-API, "hlists" and "selection" have to be differentiated in the response
      * [[org.knora.webapi.messages.v2.responder.listmessages.ListGetResponseV2]] is the abstract super class of [[HListGetResponseV2]] and [[SelectionGetResponseV2]]
      *
      * @param rootNodeIri the Iri if the root node of the list to be queried.
      * @param userProfile the profile of the user making the request.
      * @param pathType    the type of the list (HList or Selection).
      * @return a [[ListGetResponseV2]].
      */
    def listGetRequestV2(rootNodeIri: IRI, userProfile: UserProfileV1, pathType: PathType.Value): Future[ListGetResponseV2] = {

        for {
            maybeChildren <- listGetV2(rootNodeIri, userProfile)

            children = maybeChildren match {
                case children: Seq[ListNodeV2] if children.nonEmpty => children
                case _ => throw NotFoundException(s"List not found: $rootNodeIri")
            }

            // consider routing path here ("hlists" | "selections") and return the correct case class
            result = pathType match {
                case PathType.HList => HListGetResponseV2(hlist = children)
                case PathType.Selection => SelectionGetResponseV2(selection = children)
            }

        } yield result
    }

    /**
      * Retrieves a list from the triplestore and returns it as a sequence of child nodes.
      *
      * @param rootNodeIri the Iri of the root node of the list to be queried.
      * @param userProfile the profile of the user making the request.
      * @return a sequence of [[ListNodeV2]].
      */
    private def listGetV2(rootNodeIri: IRI, userProfile: UserProfileV1): Future[Seq[ListNodeV2]] = {

        /**
          * Compares the `position`-values of two nodes
          *
          * @param list1 node in a list
          * @param list2 node in the same list
          * @return true if the `position` of list1 is lower than the one of list2
          */
        def orderNodes(list1: ListNodeV2, list2: ListNodeV2): Boolean = {
            list1.position < list2.position
        }

        /**
          * This function recursively transforms SPARQL query results representing a hierarchical list into a [[ListNodeV2]].
          *
          * @param nodeIri          the IRI of the node to be created.
          * @param groupedByNodeIri a [[Map]] in which each key is the IRI of a node in the hierarchical list, and each value is a [[Seq]]
          *                         of SPARQL query results representing that node's children.
          * @return a [[ListNodeV2]].
          */
        def createHierarchicalListV2(nodeIri: IRI, groupedByNodeIri: Map[IRI, Seq[VariableResultsRow]], level: Int): ListNodeV2 = {
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

            ListNodeV2(
                id = nodeIri,
                name = firstRowMap.get("nodeName"),
                label = firstRowMap.get("label"),
                children = if (firstRowMap.get("child").isEmpty) {
                    // If this node has no children, childRows will just contain one row with no value for "child".
                    Nil
                } else {
                    // Recursively get the child nodes.
                    childRows.map(childRow => createHierarchicalListV2(childRow.rowMap("child"), groupedByNodeIri, level + 1)).sortWith(orderNodes)
                },
                position = firstRowMap("position").toInt,
                level = level
            )
        }

        for {
            listQuery <- Future {
                queries.sparql.v2.txt.getList(
                    triplestore = settings.triplestoreType,
                    rootNodeIri = rootNodeIri,
                    preferredLanguage = userProfile.userData.lang,
                    fallbackLanguage = settings.fallbackLanguage
                ).toString()
            }
            listQueryResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(listQuery)).mapTo[SparqlSelectResponse]

            // Group the results to map each node to the SPARQL query results representing its children.
            groupedByNodeIri: Map[IRI, Seq[VariableResultsRow]] = listQueryResponse.results.bindings.groupBy(row => row.rowMap("node"))

            rootNodeChildren = groupedByNodeIri.getOrElse(rootNodeIri, Seq.empty[VariableResultsRow])

            children: Seq[ListNodeV2] = if (rootNodeChildren.head.rowMap.get("child").isEmpty) {
                // The root node has no children, so we return an empty list.
                Seq.empty[ListNodeV2]
            } else {
                // Process each child of the root node.
                rootNodeChildren.map {
                    childRow => createHierarchicalListV2(childRow.rowMap("child"), groupedByNodeIri, 0)
                }.sortWith(orderNodes)
            }

        } yield children
    }

    /**
      * Provides the path to a particular hierarchical list node.
      *
      * @param queryNodeIri the IRI of the node whose path is to be queried.
      * @param userProfile  the profile of the user making the request.
      */
    private def getNodePathResponseV2(queryNodeIri: IRI, userProfile: UserProfileV1): Future[NodePathGetResponseV2] = {
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
        def makePath(node: IRI, nodeMap: Map[IRI, Map[String, String]], parentMap: Map[IRI, IRI], path: Seq[NodePathElementV2]): Seq[NodePathElementV2] = {
            // Get the details of the node.
            val nodeData = nodeMap(node)

            // Construct a NodePathElementV2 containing those details.
            val pathElement = NodePathElementV2(
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
                queries.sparql.v2.txt.getNodePath(
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
        } yield NodePathGetResponseV2(
            nodelist = makePath(queryNodeIri, nodeMap, parentMap, Nil)
        )
    }
}
