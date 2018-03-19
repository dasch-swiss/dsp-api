/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

import akka.http.scaladsl.util.FastFuture
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.admin.responder.listsmessages._
import org.knora.webapi.messages.admin.responder.projectsmessages.{ProjectADM, ProjectGetADM}
import org.knora.webapi.messages.admin.responder.usersmessages._
import org.knora.webapi.messages.store.triplestoremessages._
import org.knora.webapi.responders.admin.ListsResponderADM._
import org.knora.webapi.responders.{IriLocker, Responder}
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util.KnoraIdUtil

import scala.annotation.tailrec
import scala.collection.breakOut
import scala.concurrent.Future

object ListsResponderADM {

    val LIST_IRI_MISSING_ERROR = "List IRI cannot be empty."
    val LIST_IRI_INVALID_ERROR = "List IRI is invalid."
    val PROJECT_IRI_MISSING_ERROR = "Project IRI cannot be empty."
    val PROJECT_IRI_INVALID_ERROR = "Project IRI is invalid."
    val LABEL_MISSING_ERROR = "At least one label needs to be supplied."
    val LIST_CREATE_PERMISSION_ERROR = "A list can only be created by the project or system administrator."
    val LIST_CHANGE_PERMISSION_ERROR = "A list can only be changed by the project or system administrator."
    val REQUEST_REMOVING_ALL_LABELS_ERROR = "Request would remove all labels, but at least one label is required."
}

/**
  * A responder that returns information about hierarchical lists.
  */
class ListsResponderADM extends Responder {

    // Creates IRIs for new Knora user objects.
    val knoraIdUtil = new KnoraIdUtil

    // The IRI used to lock user creation and update
    val LISTS_GLOBAL_LOCK_IRI = "http://rdfh.ch/lists"

    def receive: PartialFunction[Any, Unit] = {
        case ListsGetRequestADM(projectIri, requestingUser) => future2Message(sender(), listsGetRequestADM(projectIri, requestingUser), log)
        case ListGetRequestADM(listIri, requestingUser) => future2Message(sender(), listGetRequestADM(listIri, requestingUser), log)
        case ListInfoGetRequestADM(listIri, requestingUser) => future2Message(sender(), listInfoGetAdminRequest(listIri, requestingUser), log)
        case ListNodeInfoGetRequestADM(listIri, requestingUser) => future2Message(sender(), listNodeInfoGetAdminRequest(listIri, requestingUser), log)
        case NodePathGetRequestADM(iri, requestingUser) => future2Message(sender(), nodePathGetAdminRequest(iri, requestingUser), log)
        case ListCreateRequestADM(createListRequest, requestingUser, apiRequestID) => future2Message(sender(), listCreateRequestADM(createListRequest, requestingUser, apiRequestID), log)
        case ListInfoChangeRequestADM(listIri, changeListRequest, requestingUser, apiRequestID) => future2Message(sender(), listInfoChangeRequest(listIri, changeListRequest, requestingUser, apiRequestID), log)
        case other => handleUnexpectedMessage(sender(), other, log, this.getClass.getName)
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
    def listsGetRequestADM(projectIri: Option[IRI], requestingUser: UserADM): Future[ListsGetResponseADM] = {

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

            lists: Seq[ListADM] = statements.map {
                case (listIri: SubjectV2, propsMap: Map[IRI, Seq[LiteralV2]]) =>

                    val info = ListInfoADM(
                        id = listIri.toString,
                        projectIri = propsMap.getOrElse(OntologyConstants.KnoraBase.AttachedToProject, throw InconsistentTriplestoreDataException("The required property 'attachedToProject' not found.")).head.asInstanceOf[IriLiteralV2].value,
                        labels = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2]),
                        comments = propsMap.getOrElse(OntologyConstants.Rdfs.Comment, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2])
                    )

                    ListADM(
                        listinfo = info,
                        children = Seq.empty[ListNodeADM]
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
    def listGetADM(rootNodeIri: IRI, requestingUser: UserADM): Future[Option[ListADM]] = {

        for {
            // this query will give us only the information about the root node.
            sparqlQuery <- Future(queries.sparql.admin.txt.getListNode(
                triplestore = settings.triplestoreType,
                nodeIri = rootNodeIri
            ).toString())

            listInfoResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            // _ = log.debug(s"listGetADM - statements: {}", MessageUtil.toSource(listInfoResponse.statements))

            maybeList: Option[ListADM] <- if (listInfoResponse.statements.nonEmpty) {
                for {
                    // here we know that the list exists and it is fine if children is an empty list
                    children: Seq[ListNodeADM] <- listGetChildren(rootNodeIri, requestingUser)

                    // _ = log.debug(s"listGetADM - children count: {}", children.size)

                    // Map(subjectIri -> (objectIri -> Seq(stringWithOptionalLand))
                    statements = listInfoResponse.statements
                    listinfo = statements.head match {
                        case (nodeIri: SubjectV2, propsMap: Map[IRI, Seq[LiteralV2]]) =>
                            ListInfoADM(
                                id = nodeIri.toString,
                                projectIri = propsMap.getOrElse(OntologyConstants.KnoraBase.AttachedToProject, throw InconsistentTriplestoreDataException("The required property 'attachedToProject' not found.")).head.asInstanceOf[IriLiteralV2].value,
                                labels = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2]),
                                comments = propsMap.getOrElse(OntologyConstants.Rdfs.Comment, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2])
                            )
                    }

                    list = ListADM(listinfo = listinfo, children = children)
                    // _ = log.debug(s"listGetADM - list: {}", MessageUtil.toSource(list))
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
    def listGetRequestADM(rootNodeIri: IRI, requestingUser: UserADM): Future[ListGetResponseADM] = {

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
    def listInfoGetAdminRequest(listIri: IRI, requestingUser: UserADM): Future[ListInfoGetResponseADM] = {
        for {
            sparqlQuery <- Future(queries.sparql.admin.txt.getListNode(
                triplestore = settings.triplestoreType,
                nodeIri = listIri
            ).toString())

            // _ = log.debug("listNodeInfoGetRequestV2 - sparqlQuery: {}", sparqlQuery)

            listNodeResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            _ = if (listNodeResponse.statements.isEmpty) {
                throw NotFoundException(s"List node not found: $listIri")
            }

            // Map(subjectIri -> (objectIri -> Seq(stringWithOptionalLand))
            statements = listNodeResponse.statements

            // _ = log.debug(s"listNodeInfoGetRequestV2 - statements: {}", MessageUtil.toSource(statements))

            listinfo: ListInfoADM = statements.head match {
                case (nodeIri: SubjectV2, propsMap: Map[IRI, Seq[LiteralV2]]) =>
                    ListInfoADM (
                        id = nodeIri.toString,
                        projectIri = propsMap.getOrElse(OntologyConstants.KnoraBase.AttachedToProject, throw InconsistentTriplestoreDataException("The required property 'attachedToProject' not found.")).head.asInstanceOf[IriLiteralV2].value,
                        labels = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2]),
                        comments = propsMap.getOrElse(OntologyConstants.Rdfs.Comment, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2])
                    )
            }

            // _ = log.debug(s"listNodeInfoGetRequestV2 - node: {}", MessageUtil.toSource(node))

        } yield ListInfoGetResponseADM(listinfo = listinfo)
    }

    /**
      * Retrieves information about a single (child) node.
      *
      * @param nodeIri the Iri if the child node to be queried.
      * @param requestingUser the user making the request.
      * @return a [[ListNodeInfoGetResponseADM]].
      */
    def listNodeInfoGetAdminRequest(nodeIri: IRI, requestingUser: UserADM): Future[ListNodeInfoGetResponseADM] = {
        for {
            sparqlQuery <- Future(queries.sparql.admin.txt.getListNode(
                triplestore = settings.triplestoreType,
                nodeIri = nodeIri
            ).toString())

            // _ = log.debug("listNodeInfoGetRequestV2 - sparqlQuery: {}", sparqlQuery)

            listNodeResponse <- (storeManager ? SparqlExtendedConstructRequest(sparqlQuery)).mapTo[SparqlExtendedConstructResponse]

            _ = if (listNodeResponse.statements.isEmpty) {
                throw NotFoundException(s"List node not found: $nodeIri")
            }

            // Map(subjectIri -> (objectIri -> Seq(stringWithOptionalLand))
            statements = listNodeResponse.statements

            // _ = log.debug(s"listNodeInfoGetRequestV2 - statements: {}", MessageUtil.toSource(statements))

            nodeinfo: ListNodeInfoADM = statements.head match {
                case (nodeIri: SubjectV2, propsMap: Map[IRI, Seq[LiteralV2]]) =>
                    ListNodeInfoADM (
                        id = nodeIri.toString,
                        name = propsMap.get(OntologyConstants.KnoraAdmin.ListNodeName).map(_.head.asInstanceOf[StringLiteralV2].value),
                        labels = propsMap.getOrElse(OntologyConstants.Rdfs.Label, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2]),
                        comments = propsMap.getOrElse(OntologyConstants.Rdfs.Comment, Seq.empty[StringLiteralV2]).map(_.asInstanceOf[StringLiteralV2]),
                        position = propsMap.get(OntologyConstants.KnoraAdmin.ListNodePosition).map(_.head.asInstanceOf[IntLiteralV2].value)
                    )
            }

            // _ = log.debug(s"listNodeInfoGetRequestV2 - node: {}", MessageUtil.toSource(node))

        } yield ListNodeInfoGetResponseADM(nodeinfo = nodeinfo)
    }


    /**
      * Retrieves a list from the triplestore and returns it as a sequence of child nodes.
      *
      * @param rootNodeIri the Iri of the root node of the list to be queried.
      * @param requestingUser the user making the request.
      * @return a sequence of [[ListNodeADM]].
      */
    private def listGetChildren(rootNodeIri: IRI, requestingUser: UserADM): Future[Seq[ListNodeADM]] = {

        /**
          * Compares the `position`-values of two nodes
          *
          * @param list1 node in a list
          * @param list2 node in the same list
          * @return true if the `position` of list1 is lower than the one of list2
          */
        def orderNodes(list1: ListNodeADM, list2: ListNodeADM): Boolean = {
            if (list1.position.nonEmpty && list2.position.nonEmpty) {
                list1.position.get < list2.position.get
            } else {
                true
            }
        }

        /**
          * This function recursively transforms SPARQL query results representing a hierarchical list into a [[ListNodeADM]].
          *
          * @param nodeIri          the IRI of the node to be created.
          * @param groupedByNodeIri a [[Map]] in which each key is the IRI of a node in the hierarchical list, and each value is a [[Seq]]
          *                         of SPARQL query results representing that node's children.
          * @return a [[ListNodeADM]].
          */
        def createListChildNode(nodeIri: IRI, groupedByNodeIri: Map[IRI, Seq[VariableResultsRow]], level: Int): ListNodeADM = {

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

            ListNodeADM(
                id = nodeIri,
                name = firstRowMap.get("nodeName"),
                labels = if (firstRowMap.get("label").nonEmpty) {
                    Seq(StringLiteralV2(firstRowMap.get("label").get))
                } else {
                    Seq.empty[StringLiteralV2]
                },
                comments = Seq.empty[StringLiteralV2],
                children = if (firstRowMap.get("child").isEmpty) {
                    // If this node has no children, childRows will just contain one row with no value for "child".
                    Seq.empty[ListNodeADM]
                } else {
                    // Recursively get the child nodes.
                    childRows.map(childRow => createListChildNode(childRow.rowMap("child"), groupedByNodeIri, level + 1)).sortWith(orderNodes)
                },
                position = firstRowMap.get("position").map(_.toInt)
            )
        }

        // TODO: Rewrite using a construct sparql query
        for {
            listQuery <- Future {
                queries.sparql.admin.txt.getList(
                    triplestore = settings.triplestoreType,
                    rootNodeIri = rootNodeIri,
                    preferredLanguage = requestingUser.lang,
                    fallbackLanguage = settings.fallbackLanguage
                ).toString()
            }
            listQueryResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(listQuery)).mapTo[SparqlSelectResponse]

            // Group the results to map each node to the SPARQL query results representing its children.
            groupedByNodeIri: Map[IRI, Seq[VariableResultsRow]] = listQueryResponse.results.bindings.groupBy(row => row.rowMap("node"))

            rootNodeChildren = groupedByNodeIri.getOrElse(rootNodeIri, Seq.empty[VariableResultsRow])

            children: Seq[ListNodeADM] = if (rootNodeChildren.head.rowMap.get("child").isEmpty) {
                // The root node has no children, so we return an empty list.
                Seq.empty[ListNodeADM]
            } else {
                // Process each child of the root node.
                rootNodeChildren.map {
                    childRow => createListChildNode(childRow.rowMap("child"), groupedByNodeIri, 0)
                }.sortWith(orderNodes)
            }

        } yield children
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
        def makePath(node: IRI, nodeMap: Map[IRI, Map[String, String]], parentMap: Map[IRI, IRI], path: Seq[ListNodeADM]): Seq[ListNodeADM] = {
            // Get the details of the node.
            val nodeData = nodeMap(node)

            // Construct a NodePathElementV2 containing those details.
            val pathElement = ListNodeADM(
                id = nodeData("node"),
                name = nodeData.get("nodeName"),
                labels = if (nodeData.contains("label")) {
                    Seq(StringLiteralV2(nodeData("label")))
                } else {
                    Seq.empty[StringLiteralV2]
                },
                comments = Seq.empty[StringLiteralV2],
                children = Seq.empty[ListNodeADM],
                position = None
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
                queries.sparql.v2.txt.getNodePath(
                    triplestore = settings.triplestoreType,
                    queryNodeIri = queryNodeIri,
                    preferredLanguage = requestingUser.lang,
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
        } yield NodePathGetResponseADM(nodelist = makePath(queryNodeIri, nodeMap, parentMap, Nil))
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

            maybeShortcode = project.shortcode
            dataNamedGraph = stringFormatter.projectDataNamedGraphV2(project)

            listIri = knoraIdUtil.makeRandomListIri(maybeShortcode)

            // Create the new list
            createNewListSparqlString = queries.sparql.admin.txt.createNewList(
                dataNamedGraph = dataNamedGraph,
                triplestore = settings.triplestoreType,
                listIri = listIri,
                projectIri = project.id,
                listClassIri = OntologyConstants.KnoraAdmin.ListNode,
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

    private def listInfoChangeRequest(listIri: IRI, changeListRequest: ChangeListInfoApiRequestADM, requestingUser: UserADM, apiRequestID: UUID): Future[ListInfoGetResponseADM] = {

        /**
          * The actual task run with an IRI lock.
          */
        def listInfoChangeTask(listIri: IRI, changeListRequest: ChangeListInfoApiRequestADM, requestingUser: UserADM, apiRequestID: UUID) = for {
            // check if required information is supplied
            _ <- Future(if (changeListRequest.labels.isEmpty) throw BadRequestException(REQUEST_REMOVING_ALL_LABELS_ERROR))

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
                listClassIri = OntologyConstants.KnoraAdmin.ListNode,
                maybeLabels = changeListRequest.labels,
                maybeComments = changeListRequest.comments
            ).toString
            // _ = log.debug("listCreateRequestADM - createNewListSparqlString: {}", createNewListSparqlString)
            changeResourceResponse <- (storeManager ? SparqlUpdateRequest(changeListInfoSparqlString)).mapTo[SparqlUpdateResponse]


            /* Verify that the list was updated */
            maybeListADM <- listGetADM(listIri, KnoraSystemInstances.Users.SystemUser)
            updatedList = maybeListADM.getOrElse(throw UpdateNotPerformedException(s"List $listIri was not updated. Please report this as a possible bug."))


            _ = if (changeListRequest.labels.nonEmpty) {
                if (updatedList.listinfo.labels.sorted != changeListRequest.labels.sorted) throw UpdateNotPerformedException("Lists's 'labels' where not updated. Please report this as a possible bug.")
            }

            _ = if (changeListRequest.comments.nonEmpty) {
                if (updatedList.listinfo.comments.sorted != changeListRequest.comments.sorted) throw UpdateNotPerformedException("List's 'comments' was not updated. Please report this as a possible bug.")
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


    ////////////////////
    // Helper Methods //
    ////////////////////

    /**
      * Helper method for checking if a project identified by IRI exists.
      *
      * @param projectIri the IRI of the project.
      * @return a [[Boolean]].
      */
    def projectByIriExists(projectIri: IRI): Future[Boolean] = {
        for {
            askString <- Future(queries.sparql.admin.txt.checkProjectExistsByIri(projectIri = projectIri).toString)
            //_ = log.debug("projectExists - query: {}", askString)

            checkProjectExistsResponse <- (storeManager ? SparqlAskRequest(askString)).mapTo[SparqlAskResponse]
            result = checkProjectExistsResponse.result

        } yield result
    }
}
