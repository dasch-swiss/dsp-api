/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

import java.util.UUID

import akka.actor.Status
import akka.pattern._
import org.knora.webapi._
import org.knora.webapi.messages.v1respondermessages.graphdatamessages._
import org.knora.webapi.messages.v1respondermessages.ontologymessages._
import org.knora.webapi.messages.v1respondermessages.projectmessages.{ProjectInfoByIRIGetRequest, ProjectInfoResponseV1, ProjectInfoType}
import org.knora.webapi.messages.v1respondermessages.resourcemessages._
import org.knora.webapi.messages.v1respondermessages.sipimessages._
import org.knora.webapi.messages.v1respondermessages.triplestoremessages._
import org.knora.webapi.messages.v1respondermessages.usermessages.{UserDataV1, UserProfileV1}
import org.knora.webapi.messages.v1respondermessages.valuemessages._
import org.knora.webapi.responders.ResourceLocker
import org.knora.webapi.responders.v1.GroupedProps._
import org.knora.webapi.util.ActorUtil._
import org.knora.webapi.util._

import scala.concurrent.{Future, Promise}
import scala.util.Try

/**
  * Responds to requests for information about resources, and returns responses in Knora API v1 format.
  */
class ResourcesResponderV1 extends ResponderV1 {

    // Converts SPARQL query results to ApiValueV1 objects.
    val valueUtilV1 = new ValueUtilV1(settings)

    // Creates IRIs for new Knora value objects.
    val knoraIriUtil = new KnoraIriUtil

    /**
      * Receives a message extending [[ResourcesResponderRequestV1]], and returns an appropriate response message, or
      * [[Status.Failure]]. If a serious error occurs (i.e. an error that isn't the client's fault), this
      * method first returns `Failure` to the sender, then throws an exception.
      */
    def receive = {
        case ResourceInfoGetRequestV1(resourceIri, userProfile) => future2Message(sender(), getResourceInfoResponseV1(resourceIri, userProfile), log)
        case ResourceFullGetRequestV1(resourceIri, userProfile, getIncoming) => future2Message(sender(), getFullResponseV1(resourceIri, userProfile, getIncoming), log)
        case ResourceContextGetRequestV1(resourceIri, userProfile, resinfo) => future2Message(sender(), getContextResponseV1(resourceIri, userProfile, resinfo), log)
        case ResourceRightsGetRequestV1(resourceIri, userProfile) => future2Message(sender(), getRightsResponseV1(resourceIri, userProfile), log)
        case GraphDataGetRequestV1(iri: IRI, userProfile: UserProfileV1, level: Int) => future2Message(sender(), getGraphDataResponseV1(iri, userProfile, level), log)
        case ResourceSearchGetRequestV1(searchString: String, resourceIri: Option[IRI], numberOfProps: Int, limitOfResults: Int, userProfile: UserProfileV1) => future2Message(sender(), getResourceSearchResponseV1(searchString, resourceIri, numberOfProps, limitOfResults, userProfile), log)
        case ResourceCreateRequestV1(resourceTypeIri, label, values, convertRequest, projectIri, userProfile, apiRequestID) => future2Message(sender(), createNewResource(resourceTypeIri, label, values, convertRequest, projectIri, userProfile, apiRequestID), log)
        case ResourceCheckClassRequestV1(resourceIri: IRI, owlClass: IRI, userProfile: UserProfileV1) => future2Message(sender(), checkResourceClass(resourceIri, owlClass, userProfile), log)
        case other => sender ! Status.Failure(UnexpectedMessageException(s"Unexpected message $other of type ${other.getClass.getCanonicalName}"))
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Methods for generating complete API responses.
    /**
      * Returns an instance of [[GraphDataGetResponseV1]] describing a graph representation, in Knora API v1 format.
      *
      * param resourceIri the IRI of the resource to be queried.
      * param userProfile the profile of the user making the request.
      * param level the degree of nested levels the request is asking for
      *
      * @return a [[GraphDataGetResponseV1]] containing a representation of the graph.
      */
    /**
      * TODO: implement build_graph recursion
      * this is just a stub
      * private def getGraphDataResponseV1(resourceIri: IRI, userProfile: UserProfileV1, level: Int): Future[GraphDataGetResponseV1] = {
      * for {
      * (nodes, edges) <- build_graph(resourceIri, level, List[GraphNodeV1](),
      * List[GraphDataEdgeV1](), userProfile)
      * } yield nodes match {
      * case nodes: Seq[GraphNodeV1] =>
      * GraphDataGetResponseV1(
      * status = ApiStatusCodesV1.OK,
      * graph = GraphV1(
      * id = resourceIri,
      * nodes = nodes,
      * edges = edges),
      * userdata = userProfile.userData)
      * case _ =>
      * GraphDataGetResponseV1(
      * status = ApiStatusCodesV1.UNSPECIFIED_ERROR,
      * graph = GraphV1(
      * id = resourceIri,
      * nodes = List[GraphNodeV1](),
      * edges = List[GraphDataEdgeV1]()),
      * userdata = userProfile.userData)
      * }
      * }
      */


    /**
      * Returns an instance of [[GraphDataGetResponseV1]] describing a graph representation, in Knora API v1 format.
      *
      * param resourceIri the IRI of the resource to be queried.
      * param userProfile the profile of the user making the request.
      * param level the degree of nested levels the request is asking for
      *
      * @return a [[GraphDataGetResponseV1]] containing a representation of the graph.
      */


    private def getGraphDataResponseV1(resourceIri: IRI, userProfile: UserProfileV1, depth: Int = 5): Future[GraphDataGetResponseV1] = {

        /**
          *
          * Example: asking for the graph for resource http://data.knora.org/c9824353ae06 will get the following 2 levels of links:
          *
          * To create a GraphV1 for the GraphDataGetResponseV1, we need a Seq of GraphNodesV1 and a Seq of GraphDataEdgeV1
          *
          * {{{
          * outgoingProp1                incomingProp1         resource1      outgoingProp2         incomingProp2      resource2
          * -------------------------------------------------------------------------------------------------------------------------
          * knora-base#isRegionOf                              e1c441dfc103
          * knora-base#isStandoffLinkTo                        047db418ae06   knora-base#isRegionOf                    883be8542e03
          *                              knora-base#hasLinkTo  8e88d28dae06   knora-base#hasLinkTo                     047db418ae06
          *                              knora-base#hasLinkTo  faa4d435a9f7   knora-base#hasLinkTo                     047db418ae06
          * }}}
          */

        def makeGraphNodeV1(resIri: IRI, userProfile: UserProfileV1): Future[GraphNodeV1] = {
            log.debug(s"...Making GraphNode for Iri: $resIri")

            val resPropsFuture: Future[Seq[PropertyV1]] = getResourceProperties(resIri, None, userProfile)

            for {
                (userPermissions, resInfo) <- getResourceInfoV1(resIri, userProfile, queryOntology = true)
                properties <- resPropsFuture
            } yield userPermissions match {
                case Some(permissions) =>
                    GraphNodeV1(id = resIri,
                        resinfo = Some(resInfo),
                        properties = Some(PropsV1(properties = properties)))
                case None =>
                    GraphNodeV1(id = resIri,
                        resinfo = None,
                        properties = None)
            }
        }

        // TODO: filter resinfo and properties for literal V1 frontend compatibility (100% necessary?)
        // TODO: switch to twirl template

        def makeGraphNodeAndEdge(resIri: IRI, from: IRI, userProfile: UserProfileV1, incoming: Boolean): Future[(GraphNodeV1, Option[GraphDataEdgeV1])] = {
            // log.debug(s"...Making GraphNode for Iri: $resIri")

            val resPropsFuture: Future[Seq[PropertyV1]] = getResourceProperties(resIri, None, userProfile)

            for {
                (userPermissions, resInfo) <- getResourceInfoV1(resIri, userProfile, queryOntology = true)
                properties <- resPropsFuture
            } yield userPermissions match {
                case Some(permissions) =>
                    val graphnode = GraphNodeV1(id = resIri,
                        resinfo = Some(resInfo),
                        properties = Some(PropsV1(properties = properties)))
                    val edge =
                        if (incoming) {
                            Some(GraphDataEdgeV1(
                                label = graphnode.resinfo.get.restype_label,
                                from = resIri,
                                to = from))
                        } else {
                            Some(GraphDataEdgeV1(
                                label = graphnode.resinfo.get.restype_label,
                                from = from,
                                to = resIri))
                        }
                    (graphnode, edge)
                case None =>
                    (GraphNodeV1(id = resIri,
                        resinfo = None,
                        properties = None), None)
            }
        }

        def makeGraphEdgeV1(label: Option[String], from: IRI, to: IRI): GraphDataEdgeV1 = {
            GraphDataEdgeV1(
                label = label,
                from = from,
                to = to)
        }

        /*  def createEdges(resourceIri: IRI, rows: Seq[VariableResultsRow]): Seq[GraphDataEdgeV1] = {
              // this is just the most brute force approach, to see what the edges are
              val groupByInc1: Map[IRI, Seq[VariableResultsRow]] = rows.filter(a => a.rowMap.contains("incomingProp1")).groupBy(_.rowMap("incomingProp1"))
              val groupByOut1: Map[IRI, Seq[VariableResultsRow]] = rows.filter(a => a.rowMap.contains("outgoingProp1")).groupBy(_.rowMap("outgoingProp1"))
              val groupByInc2: Map[IRI, Seq[VariableResultsRow]] = rows.filter(a => a.rowMap.contains("incomingProp2")).groupBy(_.rowMap("incomingProp2"))
              val groupByOut2: Map[IRI, Seq[VariableResultsRow]] = rows.filter(a => a.rowMap.contains("outgoingProp2")).groupBy(_.rowMap("outgoingProp2"))


              groupByInc1.map{case (x, lis) => (x, lis.map{ case m => makeGraphEdgeV1(Some("test"),
                  m.rowMap("resource1"), resourceIri)})}.values.toList.flatten ++
                groupByOut1.map{case (x, lis) => (x, lis.map{ case m => makeGraphEdgeV1(Some("test"), resourceIri, m.rowMap("resource1"))})}.values.toList.flatten ++
                groupByInc2.map{case (x, lis) => (x, lis.map{ case m => makeGraphEdgeV1(Some("test"),
                    m.rowMap("resource2"), m.rowMap("resource1"))})}.values.toList.flatten ++
                groupByOut2.map{case (x, lis) => (x, lis.map{ case m => makeGraphEdgeV1(Some("test"), m.rowMap("resource1"), m.rowMap("resource2"))})}.values.toList.flatten
          }*/

        def createGraph(resourceIri: IRI, rrows: Seq[VariableResultsRow], level: Int, userProfile: UserProfileV1):
        Seq[Future[(Seq[GraphNodeV1], Seq[GraphDataEdgeV1])]] = {
            rrows map { row =>
                processRow(resourceIri, row, level, userProfile)
            }
        }

        def processRow(resourceIri: IRI, row: VariableResultsRow, level: Int, userProfile: UserProfileV1):
        Future[(Seq[GraphNodeV1], Seq[GraphDataEdgeV1])] = {
            // example: if level is 4, then we have to look for res1, res2, res3, res4
            // on the same value of the counter, we also look at incomingProp1, outgoingProp1 etc
            var nodes: Seq[GraphNodeV1] = Vector.empty
            var edges: Seq[GraphDataEdgeV1] = Vector.empty
            val rowPromise = Promise[(Seq[GraphNodeV1], Seq[GraphDataEdgeV1])]()
            val counter: Int = 1
            def loop(row: VariableResultsRow, counter: Int, lastIri: IRI) {
                if (counter > level) {
                    // add the resourceIri (rootNode) to the graph as a last step
                    val rootNode: Future[GraphNodeV1] = makeGraphNodeV1(resourceIri, userProfile)
                    rootNode map { case node =>
                        nodes = node +: nodes
                        rowPromise.success((nodes, edges))
                    }
                } else {
                    if (row.rowMap.isDefinedAt("resource" ++ counter.toString)) {
                        val resIri = row.rowMap("resource" ++ counter.toString)
                        val incoming = row.rowMap.isDefinedAt("incomingProp" ++ counter.toString)
                        makeGraphNodeAndEdge(resIri, lastIri, userProfile, incoming) map { case (node, edge) =>
                            nodes = node +: nodes
                            edges = edge.get +: edges
                            loop(row, counter + 1, resIri)
                        }
                    } else {
                        // graph is interrupted
                        // add the resourceIri (rootNode) to the graph as a last step
                        // TODO: Don't do this for every row, just once
                        // println("graph is interrupted at level: " + counter)
                        val rootNode: Future[GraphNodeV1] = makeGraphNodeV1(resourceIri, userProfile)
                        rootNode map { case node =>
                            nodes = node +: nodes
                            rowPromise.success((nodes, edges))
                        }
                    }
                }
            }
            loop(row, counter, resourceIri)
            rowPromise.future
        }

        // execute the graph query template
        // the template can be of depth n, currently it's hardcoded at level 2

        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getGraph(resourceIri).toString())
            graphResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            graphResponseRows: Seq[VariableResultsRow] = graphResponse.results.bindings

            // _ = println (graphResponseRows.map{x => println(x + " Row ")})

            // groupedByResource1: Map[IRI, Seq[VariableResultsRow]] = graphResponseRows.groupBy(_.rowMap("resource1"))
            // groupedByResource2: Map[IRI, Seq[VariableResultsRow]] = graphResponseRows.filter(x => x.rowMap.contains("resource2")).groupBy(_.rowMap("resource2"))

            graph = createGraph(resourceIri, graphResponseRows, depth, userProfile)

            /* graphNodesFutures: Seq[(Future[(GraphNodeV1, Option[GraphDataEdgeV1])],
               scala.collection.immutable.Iterable[Future[(GraphNodeV1, Option[GraphDataEdgeV1])]])] = groupedByResource1.map {
                 case (resIri1: IRI, rest: Seq[VariableResultsRow]) =>
                     (makeGraphNodeAndEdge(resIri1, resourceIri, userProfile, true), groupedByResource2.map {
                         case (resIri2: IRI, rest1: Seq[VariableResultsRow]) =>
                             for {
                                 graphNode2 <- makeGraphNodeAndEdge(resIri2, resIri1, userProfile, true)
                             } yield graphNode2
                     })
             }.toList*/
            //  rootNode = makeGraphNodeV1(resourceIri, userProfile)
            // graphWithRootNode = graph.++:(Vector(rootNode, Vector.empty))
            graphNodes <- Future.sequence(graph)

            nodes = graphNodes.flatMap(x => x._1)
            edges = graphNodes.flatMap(x => x._2)
            // TODO: error handling / handling of other ApiStatusCodes
            graphDataGetResponse = GraphDataGetResponseV1(
                graph = GraphV1(id = resourceIri, nodes = nodes, edges = edges),
                //  createEdges(resourceIri, graphResponseRows )),
                userdata = UserDataV1(lang = userProfile.userData.lang)
            )
        } yield graphDataGetResponse
    }


    /**
      * Returns an instance of [[ResourceInfoResponseV1]] describing a resource, in Knora API v1 format.
      *
      * @param resourceIri the IRI of the resource to be queried.
      * @param userProfile the profile of the user making the request.
      * @return a [[ResourceInfoResponseV1]] containing a representation of the resource.
      */
    private def getResourceInfoResponseV1(resourceIri: IRI, userProfile: UserProfileV1): Future[ResourceInfoResponseV1] = {
        for {
            (userPermissions, resInfo) <- getResourceInfoV1(
                resourceIri = resourceIri,
                userProfile = userProfile,
                queryOntology = true
            )
        } yield userPermissions match {
            case Some(permissions) =>
                ResourceInfoResponseV1(
                    resource_info = Some(resInfo),
                    rights = userPermissions,
                    userdata = userProfile.userData
                )
            case None =>
                val userID = userProfile.userData.user_id.getOrElse(OntologyConstants.KnoraBase.UnknownUser)
                throw ForbiddenException(s"User $userID does not have permission to view resource $resourceIri")
        }
    }

    /**
      * Returns an instance of [[ResourceFullResponseV1]] representing a Knora resource
      * with its properties and their values, in Knora API v1 format.
      *
      * @param resourceIri the IRI of the resource to be queried.
      * @param userProfile the profile of the user making the request.
      * @param getIncoming if `true` (the default), queries the resource's inconing references.
      * @return a [[ResourceFullResponseV1]].
      */
    private def getFullResponseV1(resourceIri: IRI, userProfile: UserProfileV1, getIncoming: Boolean = true): Future[ResourceFullResponseV1] = {
        // TODO: error handling if a required value does not exist

        // Query resource info, resource properties, and incoming references in parallel.
        // See http://buransky.com/scala/scala-for-comprehension-with-concurrently-running-futures/

        // Get information about the properties that have values for this resource.
        val groupedPropsByTypeFuture: Future[GroupedPropertiesByType] = getGroupedProperties(resourceIri)

        // Get a resource info containing basic information about the resource. Do not query the ontology here, because we will query it below.
        val resourceInfoFuture = getResourceInfoV1(
            resourceIri = resourceIri,
            userProfile = userProfile,
            queryOntology = false
        )

        // Get information about the references pointing from other resources to this resource.
        val maybeIncomingRefsFuture: Future[Option[SparqlSelectResponse]] = if (getIncoming) {
            for {
            // Run the template function in a Future to handle exceptions (see http://git.iml.unibas.ch/salsah-suite/knora/wikis/futures-with-akka#handling-errors-with-futures)
                incomingRefsSparql <- Future(queries.sparql.v1.txt.getIncomingReferences(resourceIri).toString())
                response <- (storeManager ? SparqlSelectRequest(incomingRefsSparql)).mapTo[SparqlSelectResponse]
            } yield Some(response)
        } else {
            Future(None)
        }

        for {
            groupedPropsByType: GroupedPropertiesByType <- groupedPropsByTypeFuture

            // Get the types of all the resources that this resource links to.
            linkedResourceTypes = groupedPropsByType.groupedLinkProperties.groupedProperties.foldLeft(Set.empty[IRI]) {
                case (acc, (prop, propMap)) =>
                    val targetResourceTypes = propMap.valueObjects.foldLeft(Set.empty[IRI]) {
                        case (resTypeAcc, (obj: IRI, objMap: ValueProps)) =>

                            val resType = objMap.literalData.get(OntologyConstants.Rdf.Type) match {
                                case Some(value: ValueLiterals) => value.literals
                                case None => throw InconsistentTriplestoreDataException(s"$obj has no rdf:type")
                            }

                            resTypeAcc ++ resType
                    }


                    acc ++ targetResourceTypes
            }

            // Group incoming reference rows by the IRI of the referring resource, and construct an IncomingV1 for each one.

            maybeIncomingRefsResponse: Option[SparqlSelectResponse] <- maybeIncomingRefsFuture

            incomingRefFutures: Seq[Future[Option[IncomingV1]]] = maybeIncomingRefsResponse match {
                case Some(incomingRefsResponse) =>
                    val incomingRefsResponseRows = incomingRefsResponse.results.bindings
                    val groupedByIncomingIri: Map[IRI, Seq[VariableResultsRow]] = incomingRefsResponseRows.groupBy(_.rowMap("referringResource"))
                    groupedByIncomingIri.map {
                        case (incomingIri: IRI, rows: Seq[VariableResultsRow]) =>
                            for {
                                (incomingResPermission, incomingResInfo) <- makeResourceInfoV1(incomingIri, rows, userProfile, queryOntology = false)

                                incomingV1Option = incomingResPermission match {
                                    case Some(_) =>
                                        Some(IncomingV1(
                                            ext_res_id = ExternalResourceIDV1(incomingIri, rows.head.rowMap("linkingProp")),
                                            resinfo = incomingResInfo,
                                            value = incomingResInfo.firstproperty
                                        ))

                                    case None => None
                                }
                            } yield incomingV1Option
                    }.toVector

                case None => Vector.empty[Future[Option[IncomingV1]]]
            }

            // Turn the list of incoming reference futures into a list of Option[IncomingV1] objects, each of which will be None if the user
            // doesn't have permission to see that resource.
            incomingRefOptions: Seq[Option[IncomingV1]] <- Future.sequence(incomingRefFutures)

            // Filter out incoming references from resources that the user doesn't have permission to see.
            incomingRefsWithoutQueryingOntology = incomingRefOptions.filter(_.nonEmpty).map(_.get)

            // Get the resource types of the incoming resources.
            incomingTypes: Set[IRI] = incomingRefsWithoutQueryingOntology.map(_.resinfo.restype_id).toSet

            // Get the resource info (minus ontology-based information) and the user's permissions on it.
            (permissions, resInfoWithoutQueryingOntology: ResourceInfoV1) <- resourceInfoFuture

            // Make a set of the IRIs of ontology entities that we need information about.
            entityIris: Set[String] = groupedPropsByType.groupedOrdinaryValueProperties.groupedProperties.keySet ++ groupedPropsByType.groupedLinkProperties.groupedProperties.keySet ++ incomingTypes ++ linkedResourceTypes + resInfoWithoutQueryingOntology.restype_id // use Set to eliminate redundancy

            // Ask the ontology responder for information about those entities.
            entityInfoResponse: EntityInfoGetResponseV1 <- (responderManager ? EntityInfoGetRequestV1(
                resourceClassIris = incomingTypes ++ linkedResourceTypes + resInfoWithoutQueryingOntology.restype_id,
                propertyIris = groupedPropsByType.groupedOrdinaryValueProperties.groupedProperties.keySet ++ groupedPropsByType.groupedLinkProperties.groupedProperties.keySet,
                userProfile = userProfile)
                ).mapTo[EntityInfoGetResponseV1]

            // Add ontology-based information to the resource info.
            resourceTypeIri = resInfoWithoutQueryingOntology.restype_id
            resourceTypeEntityInfo = entityInfoResponse.resourceEntityInfoMap(resourceTypeIri)

            resInfo: ResourceInfoV1 = resInfoWithoutQueryingOntology.copy(
                restype_label = resourceTypeEntityInfo.getPredicateObject(OntologyConstants.Rdfs.Label),
                restype_description = resourceTypeEntityInfo.getPredicateObject(OntologyConstants.Rdfs.Comment),
                restype_iconsrc = resourceTypeEntityInfo.getPredicateObject(OntologyConstants.KnoraBase.ResourceIcon)
            )

            // Construct a ResourceDataV1.
            resData = ResourceDataV1(
                rights = permissions,
                restype_label = resourceTypeEntityInfo.getPredicateObject(OntologyConstants.Rdfs.Label),
                restype_name = resInfo.restype_id,
                res_id = resourceIri,
                iconsrc = resourceTypeEntityInfo.getPredicateObject(OntologyConstants.KnoraBase.ResourceIcon)
            )

            // Add ontology-based information to incoming references.
            incomingRefs = incomingRefsWithoutQueryingOntology.map {
                incoming =>
                    val incomingResourceTypeEntityInfo = entityInfoResponse.resourceEntityInfoMap(incoming.resinfo.restype_id)

                    incoming.copy(
                        resinfo = incoming.resinfo.copy(
                            restype_label = incomingResourceTypeEntityInfo.getPredicateObject(OntologyConstants.Rdfs.Label),
                            restype_description = incomingResourceTypeEntityInfo.getPredicateObject(OntologyConstants.Rdfs.Comment),
                            restype_iconsrc = incomingResourceTypeEntityInfo.getPredicateObject(OntologyConstants.KnoraBase.ResourceIcon)
                        )
                    )
            }

            // Collect all property IRIs and their cardinalities for the queried resource's type, except the ones that point to LinkValue objects or FileValue objects,
            // which are not relevant in this API operation.
            propsAndCardinalities: Map[IRI, Cardinality.Value] = resourceTypeEntityInfo.cardinalities.filterNot {
                case (propertyIri, cardinality) =>
                    resourceTypeEntityInfo.linkValueProperties(propertyIri) || resourceTypeEntityInfo.fileValueProperties(propertyIri)
            }

            // Construct PropertyV1 objects for the properties that have data for this resource.
            propertiesWithData = queryResults2PropertyV1s(
                containingResourceIri = resourceIri,
                groupedPropertiesByType = groupedPropsByType,
                propertyEntityInfoMap = entityInfoResponse.propertyEntityInfoMap,
                resourceEntityInfoMap = entityInfoResponse.resourceEntityInfoMap,
                propsAndCardinalities = propsAndCardinalities,
                userProfile = userProfile
            )

            // Construct PropertyV1 objects representing properties that have no data for this resource, but are possible properties for the resource type.

            // To find out which properties are possible but have no data for this resource, subtract the set of properties with data from the set of possible properties.
            emptyPropsIris = propsAndCardinalities.keySet -- (groupedPropsByType.groupedOrdinaryValueProperties.groupedProperties.keySet ++ groupedPropsByType.groupedLinkProperties.groupedProperties.keySet)

            // Get information from the ontology about the properties that have no data for this resource.
            emptyPropsInfoResponse: EntityInfoGetResponseV1 <- (responderManager ? EntityInfoGetRequestV1(propertyIris = emptyPropsIris, userProfile = userProfile)).mapTo[EntityInfoGetResponseV1]

            // Create a PropertyV1 for each of those properties.
            emptyProps: Set[PropertyV1] = emptyPropsIris.map {
                case propertyIri =>
                    val propertyEntityInfo: PropertyEntityInfoV1 = emptyPropsInfoResponse.propertyEntityInfoMap(propertyIri)

                    if (propertyEntityInfo.isLinkProp) {
                        // It is a linking prop: its valuetype_id is knora-base:LinkValue.
                        // It is restricted to the resource class that is given for knora-base:objectClassConstraint
                        // for the given property which goes in the attributes that will be read by the GUI.

                        PropertyV1(
                            pid = propertyIri,
                            valuetype_id = Some(OntologyConstants.KnoraBase.LinkValue),
                            guiorder = propertyEntityInfo.getPredicateObject(OntologyConstants.SalsahGui.GuiOrder).map(_.toInt),
                            guielement = propertyEntityInfo.getPredicateObject(OntologyConstants.SalsahGui.GuiElement).map(guiElementIri => SalsahGuiConversions.iri2SalsahGuiElement(guiElementIri)),
                            label = propertyEntityInfo.getPredicateObject(OntologyConstants.Rdfs.Label),
                            occurrence = Some(propsAndCardinalities(propertyIri).toString),
                            attributes = (propertyEntityInfo.getPredicateObjects(OntologyConstants.SalsahGui.GuiAttribute) + valueUtilV1.makeAttributeRestype(propertyEntityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse(throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")))).mkString(";"),
                            value_rights = Nil
                        )

                    } else {


                        PropertyV1(
                            pid = propertyIri,
                            valuetype_id = propertyEntityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint),
                            guiorder = propertyEntityInfo.getPredicateObject(OntologyConstants.SalsahGui.GuiOrder).map(_.toInt),
                            guielement = propertyEntityInfo.getPredicateObject(OntologyConstants.SalsahGui.GuiElement).map(guiElementIri => SalsahGuiConversions.iri2SalsahGuiElement(guiElementIri)),
                            label = propertyEntityInfo.getPredicateObject(OntologyConstants.Rdfs.Label),
                            occurrence = Some(propsAndCardinalities(propertyIri).toString),
                            attributes = propertyEntityInfo.getPredicateObjects(OntologyConstants.SalsahGui.GuiAttribute).mkString(";"),
                            value_rights = Nil
                        )
                    }
            }

            // Add a fake property `__location__` if the resource has FileValues.
            properties: Seq[PropertyV1] = if (resInfo.locations.nonEmpty) {
                PropertyV1(
                    pid = "__location__",
                    valuetype_id = Some("-1"),
                    guiorder = Some(Int.MaxValue),
                    guielement = Some("fileupload"),
                    values = Vector(IntegerValueV1(0)),
                    value_ids = Vector("0"),
                    comments = Vector("0"),
                    locations = resInfo.locations match {
                        case Some(locations: Seq[LocationV1]) => locations
                        case None => Nil
                    },
                    value_rights = Nil
                ) +: (propertiesWithData ++ emptyProps)
            } else {
                propertiesWithData ++ emptyProps
            }

            // Construct the API response. Return no data if the user has no view permissions on the queried resource.
            resFullResponse = if (resData.rights.nonEmpty) {
                ResourceFullResponseV1(
                    resinfo = Some(resInfo),
                    resdata = Some(resData),
                    props = Some(PropsV1(properties)),
                    incoming = incomingRefs,
                    access = "OK",
                    userdata = userProfile.userData
                )
            } else {
                val userID = userProfile.userData.user_id.getOrElse(OntologyConstants.KnoraBase.UnknownUser)
                throw ForbiddenException(s"User $userID does not have permission to query resource $resourceIri")
            }
        } yield resFullResponse
    }

    /**
      * Returns an instance of [[ResourceContextResponseV1]] describing the context of a resource, in Knora API v1 format.
      *
      * @param resourceIri the IRI of the resource to be queried.
      * @param userProfile the profile of the user making the request.
      * @param resinfo     a flag if resinfo should be retrieved or not.
      * @return a [[ResourceContextResponseV1]] describing the context of the resource.
      */
    private def getContextResponseV1(resourceIri: IRI, userProfile: UserProfileV1, resinfo: Boolean): Future[ResourceContextResponseV1] = {

        /**
          * Represents a source object (e.g. a page of a book).
          *
          * @param id IRI of the source Object.
          * @param firstprop first property of the source object.
          * @param seqnum sequence number of the source object.
          * @param permissions the current user's permissions on the source object.
          * @param fileValues the file values belonging to the source object.
          */
        case class SourceObject(id: IRI,
                                firstprop: Option[String],
                                seqnum: Int,
                                permissions: Option[Int],
                                fileValues: Vector[StillImageFileValue] = Vector.empty[StillImageFileValue])

        /**
          * Represents a still image file value belonging to a source object (e.g., an image representation of a page).
          *
          * @param id the file value IRI
          * @param permissions the current user's permissions on the file value.
          * @param image a [[StillImageFileValueV1]]
          */
        case class StillImageFileValue(id: IRI,
                             permissions: Option[Int],
                             image: StillImageFileValueV1)


        /**
          * Creates a [[StillImageFileValue]] from a [[VariableResultsRow]].
          *
          * @param row a [[VariableResultsRow]] representing a [[StillImageFileValueV1]]
          * @return a [[StillImageFileValue]].
          */
        def createStillImageFileValueFromResultRow(row: VariableResultsRow): Vector[StillImageFileValue] = {
            // if the file value has no project, get the project from the source object
            val fileValueProject = row.rowMap.get("fileValueAttachedToProject") match {
                case Some(fileValueProjectIri) => fileValueProjectIri
                case None => row.rowMap("sourceObjectAttachedToProject")
            }

            val fileValueIri = row.rowMap("fileValue")
            val authorizationAssertionsForFileValue = PermissionUtilV1.parsePermissions(row.rowMap("fileValuePermissionAssertions"), row.rowMap("fileValueAttachedToUser"), fileValueProject)
            val fileValuePermissions = PermissionUtilV1.getUserPermissionV1(fileValueIri, authorizationAssertionsForFileValue, userProfile)

            row.rowMap.get("fileValue") match {

                case Some(fileValueIri) => Vector(StillImageFileValue(
                    id = fileValueIri,
                    permissions = fileValuePermissions,
                    image = StillImageFileValueV1(
                        internalMimeType = row.rowMap("internalMimeType"),
                        internalFilename = row.rowMap("internalFilename"),
                        originalFilename = row.rowMap("originalFilename"),
                        dimX = row.rowMap("dimX").toInt,
                        dimY = row.rowMap("dimY").toInt,
                        qualityLevel = row.rowMap("qualityLevel").toInt,
                        isPreview = InputValidation.optionStringToBoolean(row.rowMap.get("isPreview"))
                    ))
                )
                case None => Vector.empty[StillImageFileValue]
            }
        }

        /**
          * Creates a [[SourceObject]] from a [[VariableResultsRow]].
          *
          * @param acc the accumalatur used in the fold left construct.
          * @param row a [[VariableResultsRow]] representing a [[SourceObject]].
          * @return a [[SourceObject]].
          */
        def createSourceObjectFromResultRow(acc: Vector[SourceObject], row: VariableResultsRow) = {
            val sourceObjectIri = row.rowMap("sourceObject")
            val authorizationAssertionsForsourceObject = PermissionUtilV1.parsePermissions(row.rowMap("sourceObjectPermissionAssertions"), row.rowMap("sourceObjectAttachedToUser"), row.rowMap("sourceObjectAttachedToProject"))
            val sourceObjectPermissions = PermissionUtilV1.getUserPermissionV1(sourceObjectIri, authorizationAssertionsForsourceObject, userProfile)

            acc :+ SourceObject(id = row.rowMap("sourceObject"),
                firstprop = row.rowMap.get("firstprop"),
                seqnum = row.rowMap("seqnum").toInt,
                permissions = sourceObjectPermissions,
                fileValues = createStillImageFileValueFromResultRow(row)
            )
        }

        // If the API request asked for a ResourceInfoV1, query for that.
        val resInfoV1Future = if (resinfo) {
            for {
                (userPermission, resInfoV1) <- getResourceInfoV1(
                    resourceIri = resourceIri,
                    userProfile = userProfile,
                    queryOntology = true
                )
            } yield userPermission match {
                case Some(permission) => Some(resInfoV1)
                case None => None
            }
        } else {
            Future(None)
        }

        for {
        // If this resource is part of another resource, get its parent resource.
            isPartOfSparqlQuery <- Future(queries.sparql.v1.txt.isPartOf(resourceIri).toString())
            isPartOfResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(isPartOfSparqlQuery)).mapTo[SparqlSelectResponse]

            (parentResourceIriOption, parentResInfoV1Option) <- isPartOfResponse.results.bindings match {
                case rows if rows.nonEmpty =>
                    val parentResourceIri = rows.head.rowMap("containingResource")

                    for {
                        (userPermission, resInfoV1) <- getResourceInfoV1(
                            resourceIri = rows.head.rowMap("containingResource"),
                            userProfile = userProfile,
                            queryOntology = true
                        )
                    } yield userPermission match {
                        case Some(permission) => (Some(parentResourceIri), Some(resInfoV1))
                        case None => (None, None)
                    }

                case _ => Future((None, None))
            }

            resourceContexts: Seq[ResourceContextItemV1] <- if (parentResInfoV1Option.isEmpty) {
                for {
                // Otherwise, see if this resource has parts.

                // Do a SPARQL query that returns binary representations of resources that are part of this resource (as
                // indicated by knora-base:isPartOf).
                    contextSparqlQuery <- Future(queries.sparql.v1.txt.getContext(resourceIri).toString())
                    contextQueryResponse: SparqlSelectResponse <- (storeManager ? SparqlSelectRequest(contextSparqlQuery)).mapTo[SparqlSelectResponse]
                    rows = contextQueryResponse.results.bindings

                    //
                    // Use fold left to iterate over the results
                    //
                    // Every source object may have one or more file values:
                    // All the file values belonging to the same source object have to be assigned to the same source object case class
                    //
                    sourceObjects: Vector[SourceObject] = rows.foldLeft(Vector.empty[SourceObject]) {
                        case (acc: Vector[SourceObject], row) =>
                            if (acc.isEmpty) {
                                // first run, create a source object containing a still image file value
                                createSourceObjectFromResultRow(acc, row)
                            } else {
                                // get the reference to the last source object
                                val lastSourceObj = acc.last

                                if (lastSourceObj.seqnum == row.rowMap("seqnum").toInt) {
                                    // processing at least the second still image file value belonging to the same source object
                                    // add the still image file value to the file values list of the source object
                                    acc.dropRight(1) :+ lastSourceObj.copy(fileValues = lastSourceObj.fileValues ++ createStillImageFileValueFromResultRow(row))
                                } else {
                                    // dealing with a new source object, create a source object containing a still image file value (like in the first run of this fold left)
                                    createSourceObjectFromResultRow(acc, row)
                                }
                            }
                    }

                    // Filter the source objects by eliminating the ones that the user doesn't have permission to see.
                    sourceObjectsWithPermissions = sourceObjects.filter(sourceObj => sourceObj.permissions.nonEmpty)

                    //_ = println(ScalaPrettyPrinter.prettyPrint(sourceObjectsWithPermissions))

                    contextItems = sourceObjectsWithPermissions.map {
                        (sourceObj: SourceObject) =>

                            val preview: Option[LocationV1] = sourceObj.fileValues.filter(fileVal => fileVal.permissions.nonEmpty && fileVal.image.isPreview).headOption match {
                                case Some(preview: StillImageFileValue) =>
                                    Some(valueUtilV1.fileValueV12LocationV1(preview.image))
                                case None => None
                            }

                            val locations: Option[Seq[LocationV1]] = sourceObj.fileValues.filter(fileVal => fileVal.permissions.nonEmpty && !fileVal.image.isPreview).headOption match {
                                case Some(full: StillImageFileValue) =>
                                    val fileVals = createMultipleImageResolutions(full.image)
                                    Some(preview.toVector ++ fileVals.map(valueUtilV1.fileValueV12LocationV1(_)))

                                case None => None
                            }

                            ResourceContextItemV1(
                                res_id = sourceObj.id,
                                preview = preview,
                                locations = locations,
                                firstprop = sourceObj.firstprop
                            )
                    }

                    //_ = println(ScalaPrettyPrinter.prettyPrint(contextItems))


                } yield contextItems
            } else {
                Future(Nil)
            }

            resinfoV1Option: Option[ResourceInfoV1] <- resInfoV1Future

            resourceContextV1 = parentResourceIriOption match {
                case Some(_) =>
                    // This resource is part of another resource, so return the resource info of the parent.
                    ResourceContextV1(
                        resinfo = resinfoV1Option,
                        parent_res_id = parentResourceIriOption,
                        parent_resinfo = parentResInfoV1Option,
                        context = ResourceContextCodeV1.RESOURCE_CONTEXT_IS_PARTOF,
                        canonical_res_id = resourceIri
                    )

                case None => if (resourceContexts.nonEmpty) {
                    // This resource has parts, so return information about the parts.
                    ResourceContextV1(
                        res_id = Some(resourceContexts.map(_.res_id)),
                        preview = Some(resourceContexts.map(_.preview)),
                        locations = Some(resourceContexts.map(_.locations)),
                        firstprop = Some(resourceContexts.map(_.firstprop)),
                        region = Some(resourceContexts.map(_ => None)),
                        canonical_res_id = resourceIri,
                        resinfo = resinfoV1Option,
                        resclass_name = Some("image"),
                        context = ResourceContextCodeV1.RESOURCE_CONTEXT_IS_COMPOUND
                    )
                } else {
                    // Indicate that neither of the above is true.
                    ResourceContextV1(
                        resinfo = resinfoV1Option,
                        canonical_res_id = resourceIri,
                        context = ResourceContextCodeV1.RESOURCE_CONTEXT_NONE
                    )
                }
            }
        } yield ResourceContextResponseV1(
            resource_context = resourceContextV1,
            userdata = userProfile.userData)

    }

    /**
      * Returns an instance of [[ResourceRightsResponseV1]] describing the permissions on a resource for the current user, in Knora API v1 format.
      *
      * @param resourceIri the IRI of the resource to be queried.
      * @param userProfile the profile of the user making the request.
      * @return a [[ResourceRightsResponseV1]] describing the permissions on the resource.
      */
    private def getRightsResponseV1(resourceIri: IRI, userProfile: UserProfileV1): Future[ResourceRightsResponseV1] = {
        for {
            (userPermission, _) <- getResourceInfoV1(resourceIri, userProfile, queryOntology = false)

            // Construct an API response.
            rightsResponse = ResourceRightsResponseV1(
                rights = userPermission,
                userdata = userProfile.userData
            )
        } yield rightsResponse
    }

    /**
      * Searches for resources matching the given criteria.
      *
      * @param searchString    the string to search for.
      * @param resourceTypeIri if set, restrict search to this resource type.
      * @param numberOfProps   the amount of describing properties to be returned for each found resource (e.g if set to two, for an incunabula book its title and creator would be returned).
      * @param limitOfResults  limits number of resources to be returned.
      * @param userProfile     the profile of the user making the request.
      * @return the resources matching the given criteria.
      */
    private def getResourceSearchResponseV1(searchString: String, resourceTypeIri: Option[IRI], numberOfProps: Int, limitOfResults: Int, userProfile: UserProfileV1): Future[ResourceSearchResponseV1] = {

        //
        // Search logic for Lucene: combine a phrase enclosed by double quotes (exact match) with a single search term with a wildcard at the end (matches the beginning of the given term).
        // Example: searchString "Reise ins Heili" results in: '"Reise ins" AND Heili*' that matches "Reise ins Heilige Land".
        // This is necessary because wildcards cannot be used inside a phrase. And we need phrases because we cannot just search for a combination of single terms as their order matters.
        //

        // split search string by a space
        val searchStringSpaceSeparated: Array[String] = searchString.split(" ")

        // take all the elements except the last one and make a String again (separated by space).
        // if the String would be empty, return a None (occurs when the the Array contains nly one element).
        val phrase: Option[String] = searchStringSpaceSeparated.dropRight(1).mkString(" ") match {
            case "" => None
            case (searchPhrase: String) => Some(searchPhrase)
        }

        // get the las element of the Array
        val lastTerm = searchStringSpaceSeparated.last


        for {

            searchResourcesSparql <- Future(queries.sparql.v1.txt.getResourceSearchResult(phrase, lastTerm, resourceTypeIri, numberOfProps, limitOfResults, settings.triplestoreType, FormatConstants.INFORMATION_SEPARATOR_ONE).toString())
            //_ = println(searchResourcesSparql)
            searchResponse <- (storeManager ? SparqlSelectRequest(searchResourcesSparql)).mapTo[SparqlSelectResponse]

            resources: Seq[ResourceSearchResultRowV1] = searchResponse.results.bindings.map {
                case (row: VariableResultsRow) =>

                    val firstProp = row.rowMap("firstProp")

                    val attachedToUser = row.rowMap("attachedToUser")
                    val attachedToProject = row.rowMap("attachedToProject")
                    val permissionAssertions = row.rowMap("permissionAssertions")
                    val assertions = PermissionUtilV1.parsePermissions(permissionAssertions, attachedToUser, attachedToProject)
                    val permission = PermissionUtilV1.getUserPermissionV1(row.rowMap("resourceIri"), assertions, userProfile)

                    if (numberOfProps > 1) {
                        // The client requested more than one property per resource that was found.

                        val valueStrings = row.rowMap("values").split(FormatConstants.INFORMATION_SEPARATOR_ONE)
                        val guiOrders = row.rowMap("guiOrders").split(";")
                        val valueOrders = row.rowMap("valueOrders").split(";")

                        // create a list of three tuples, sort it by guiOrder and valueOrder and return only string values
                        val values: Seq[String] = (valueStrings, guiOrders, valueOrders).zipped.toVector.sortBy(row => (row._2.toInt, row._3.toInt)).map(_._1)

                        // ?values is given: it is one string to be split by separator
                        val propValues = values.foldLeft(Vector(firstProp)) {
                            case (acc, prop: String) =>
                                if (prop == firstProp || prop == acc.last) {
                                    // in the SPAQRL results, all values are returned four times because of inclusion of permissions. If already existent, ignore prop.
                                    acc
                                } else {
                                    acc :+ prop // append prop to List
                                }
                        }

                        ResourceSearchResultRowV1(
                            id = row.rowMap("resourceIri"),
                            value = propValues.slice(0, numberOfProps), // take only as many elements as were requested by the client.
                            rights = permission

                        )
                    } else {
                        // ?firstProp is sufficient: the client requested just one property per resource that was found
                        ResourceSearchResultRowV1(
                            id = row.rowMap("resourceIri"),
                            value = Vector(firstProp),
                            rights = permission
                        )
                    }
            }.filter(_.rights.nonEmpty) // user must have permissions to see resource (must not be None)

        } yield ResourceSearchResponseV1(
            resources = resources,
            userdata = userProfile.userData)
    }

    /**
      * Creates a new resource and attaches the given values to it.
      *
      * @param resourceClassIri      the resource type of the resource to be created.
      * @param values                the values to be attached to the resource.
      * @param sipiConversionRequest a file (binary representation) to be attached to the resource (GUI and non GUI-case)
      * @param projectIri            the project the resource belongs to.
      * @param userProfile           the user that is creating the resource
      * @param apiRequestID          the ID of this API request.
      * @return a [[ResourceCreateResponseV1]] informing the client about the new resource.
      */
    private def createNewResource(resourceClassIri: IRI, label: String, values: Map[IRI, Seq[CreateValueV1WithComment]], sipiConversionRequest: Option[SipiResponderConversionRequestV1] = None, projectIri: IRI, userProfile: UserProfileV1, apiRequestID: UUID): Future[ResourceCreateResponseV1] = {

        /**
          * Implements a pre-update check to ensure that an [[UpdateValueV1]] has the correct type for the `knora-base:objectClassConstraint` of
          * the property that is supposed to point to it.
          *
          * @param propertyIri the IRI of the property.
          * @param propertyObjectClassConstraint the IRI of the `knora-base:objectClassConstraint` of the property.
          * @param updateValueV1 the value to be updated.
          * @param userProfile   the profile of the user making the request.
          * @return an empty [[Future]] on success, or a failed [[Future]] if the value has the wrong type.
          */
        def checkPropertyObjectClassConstraintForValue(propertyIri: IRI, propertyObjectClassConstraint: IRI, updateValueV1: UpdateValueV1, userProfile: UserProfileV1): Future[Unit] = {
            for {
                result <- updateValueV1 match {
                    case linkUpdate: LinkUpdateV1 =>
                        // We're creating a link. Check the OWL class of the target resource.
                        for {
                            checkTargetClassResponse <- checkResourceClass(
                                resourceIri = linkUpdate.targetResourceIri,
                                owlClass = propertyObjectClassConstraint,
                                userProfile = userProfile
                            ).mapTo[ResourceCheckClassResponseV1]

                            _ = if (!checkTargetClassResponse.isInClass) {
                                throw OntologyConstraintException(s"Resource ${linkUpdate.targetResourceIri} cannot be the target of property $propertyIri, because it is not a member of OWL class $propertyObjectClassConstraint")
                            }
                        } yield ()

                    case otherValue =>
                        // We're creating an ordinary value. Check that its type is valid for the property's knora-base:objectClassConstraint.
                        valueUtilV1.checkValueTypeForPropertyObjectClassConstraint(
                            propertyIri = propertyIri,
                            propertyObjectClassConstraint = propertyObjectClassConstraint,
                            valueType = otherValue.valueTypeIri,
                            responderManager = responderManager)
                }
            } yield result
        }

        /**
          * Does pre-update checks, creates an empty resource, and asks the values responder to create the resource's
          * values. This function is called by [[ResourceLocker]] once it has acquired an update lock on the resource.
          *
          * @param resourceIri  the Iri of the resource to be created.
          * @param values       the values to be attached to the resource.
          * @param permissions  the permissions to be attached.
          * @param ownerIri     the owner of the resource to be created.
          * @param namedGraph   the named graph the resource belongs to.
          * @param apiRequestID the ID used for the locking.
          * @return a [[ResourceCreateResponseV1]] containing information about the created resource.
          */
        def createResourceAndCheck(resourceIri: IRI,
                                   values: Map[IRI, Seq[CreateValueV1WithComment]],
                                   sipiConversionRequest: Option[SipiResponderConversionRequestV1],
                                   permissions: Seq[(IRI, IRI)],
                                   ownerIri: IRI,
                                   namedGraph: IRI,
                                   apiRequestID: UUID): Future[ResourceCreateResponseV1] = {
            val propertyIris = values.keySet

            for {
            // Get ontology information about the resource class's cardinalities and about each property's knora-base:objectClassConstraint.

                entityInfoResponse: EntityInfoGetResponseV1 <- (responderManager ? EntityInfoGetRequestV1(
                    resourceClassIris = Set(resourceClassIri),
                    propertyIris = propertyIris,
                    userProfile = userProfile
                )).mapTo[EntityInfoGetResponseV1]

                // Check that each submitted value is consistent with the knora-base:objectClassConstraint of the property that is supposed to
                // point to it.

                propertyObjectClassConstraintChecks: Seq[Unit] <- Future.sequence {
                    values.foldLeft(Vector.empty[Future[Unit]]) {
                        case (acc, (propertyIri, valuesWithComments)) =>
                            val propertyInfo = entityInfoResponse.propertyEntityInfoMap(propertyIri)
                            val propertyObjectClassConstraint = propertyInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse {
                                throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")
                            }

                            acc ++ valuesWithComments.map {
                                valueV1WithComment: CreateValueV1WithComment => checkPropertyObjectClassConstraintForValue(
                                    propertyIri = propertyIri,
                                    propertyObjectClassConstraint = propertyObjectClassConstraint,
                                    updateValueV1 = valueV1WithComment.updateValueV1,
                                    userProfile = userProfile
                                )
                            }
                    }
                }

                // Check that the resource class has a suitable cardinality for each submitted value.
                resourceClassInfo = entityInfoResponse.resourceEntityInfoMap(resourceClassIri)

                _ = values.foreach {
                    case (propertyIri, valuesForProperty) =>
                        val cardinality = resourceClassInfo.cardinalities.getOrElse(propertyIri, throw OntologyConstraintException(s"Resource class $resourceClassIri has no cardinality for property $propertyIri"))

                        if ((cardinality == Cardinality.MayHaveOne || cardinality == Cardinality.MustHaveOne) && valuesForProperty.size > 1) {
                            throw OntologyConstraintException(s"Resource class $resourceClassIri does not allow more than one value for property $propertyIri")
                        }
                }

                // maximally one file value can be handled here
                _ = if (resourceClassInfo.fileValueProperties.size > 1) throw BadRequestException(s"The given resource type $resourceClassIri requires more than on file value. This is not supported for API V1")

                // Check that no required values are missing.
                requiredProps: Set[IRI] = resourceClassInfo.cardinalities.filter {
                    case (propIri, cardinality) => cardinality == Cardinality.MustHaveOne || cardinality == Cardinality.MustHaveSome
                }.keySet -- resourceClassInfo.linkValueProperties -- resourceClassInfo.fileValueProperties // exclude link value and file value properties from checking

                _ = if (!requiredProps.subsetOf(propertyIris)) {
                    val missingProps = (requiredProps -- propertyIris).mkString(", ")
                    throw OntologyConstraintException(s"Values were not submitted for the following property or properties, which are required by resource class $resourceClassIri: $missingProps")
                }

                // check if a file value is required by the ontology
                fileValuesV1: Option[(IRI, Vector[CreateValueV1WithComment])] <- if (resourceClassInfo.fileValueProperties.nonEmpty) {
                    // call sipi responder
                    for {
                        sipiResponse: SipiResponderConversionResponseV1 <- (responderManager ? sipiConversionRequest.getOrElse(throw OntologyConstraintException(s"No file (required) given for resource type $resourceClassIri"))).mapTo[SipiResponderConversionResponseV1]

                        // check if the file type returned by Sipi corresponds to the expected fileValue property in resourceClassInfo.fileValueProperties.head
                        _ = if (SipiConstants.fileType2FileValueProperty(sipiResponse.file_type) != resourceClassInfo.fileValueProperties.head) {
                            // TODO: remove the file from SIPI (delete request)
                            throw BadRequestException(s"Type of submitted file (${sipiResponse.file_type}) does not correspond to expected property type ${resourceClassInfo.fileValueProperties.head}")
                        }

                    // in case we deal with a SipiResponderConversionPathRequestV1 (non GUI-case), the tmp file created by resources route
                    // has already been deleted by the SipiResponder

                    } yield Some(resourceClassInfo.fileValueProperties.head -> sipiResponse.fileValuesV1.map(fileValue => CreateValueV1WithComment(fileValue)))
                } else {
                    // resource class requires no binary representation
                    // check if there was no file sent
                    // TODO: in all cases of an error, the tmp file has to be deleted
                    sipiConversionRequest match {
                        case None => Future(None) // expected behaviour
                        case Some(sipiConversionFileRequest: SipiResponderConversionFileRequestV1) =>
                            throw BadRequestException(s"File params (GUI-case) are given but resource class $resourceClassIri does not allow any representation")
                        case Some(sipiConversionPathRequest: SipiResponderConversionPathRequestV1) =>
                            throw BadRequestException(s"A binary file was provided (non GUI-case) but resource class $resourceClassIri does not have any binary representation")
                    }
                }

                // Everything looks OK, so we can create an empty resource and add the values to it. This must
                // happen in an update transaction managed by the store package, to ensure that triplestore
                // consistency checks are performed on the result of the whole set of updates. We use
                // TransactionUtil.runInUpdateTransaction to handle the store module's transaction management.
                // Its first argument is a lambda function that sends all the updates to the store manager,
                // using a transaction ID provided by TransactionUtil.runInUpdateTransaction.
                createMultipleValuesResponse <- TransactionUtil.runInUpdateTransaction({
                    transactionID =>
                        for {
                        // Create an empty resource.
                            createNewResourceSparql <- Future(queries.sparql.v1.txt.createNewResource(
                                dataNamedGraph = namedGraph,
                                triplestore = settings.triplestoreType,
                                resourceIri = resourceIri,
                                label = label,
                                resourceClassIri = resourceClassIri,
                                ownerIri = ownerIri,
                                projectIri = projectIri,
                                permissions = permissions).toString())
                            createResourceResponse <- (storeManager ? SparqlUpdateRequest(transactionID, createNewResourceSparql)).mapTo[SparqlUpdateResponse]

                            // Ask the values responder to create the values.
                            createValuesRequest = CreateMultipleValuesRequestV1(
                                transactionID = transactionID,
                                projectIri = projectIri,
                                resourceIri = resourceIri,
                                resourceClassIri = resourceClassIri,
                                values = values ++ fileValuesV1,
                                userProfile = userProfile,
                                apiRequestID = apiRequestID
                            )
                            createValuesResponse: CreateMultipleValuesResponseV1 <- (responderManager ? createValuesRequest).mapTo[CreateMultipleValuesResponseV1]
                        } yield createValuesResponse
                }, storeManager)

                // Verify that the resource was created.

                createdResourcesSparql <- Future(queries.sparql.v1.txt.getCreatedResource(resourceIri = resourceIri).toString())
                createdResourceResponse <- (storeManager ? SparqlSelectRequest(createdResourcesSparql)).mapTo[SparqlSelectResponse]

                _ = if (createdResourceResponse.results.bindings.isEmpty) {
                    throw UpdateNotPerformedException(s"Resource $resourceIri was not created. Please report this as a possible bug.")
                }

                // Verify that all the requested values were created.

                verifyCreateValuesRequest = VerifyMultipleValueCreationRequestV1(
                    resourceIri = resourceIri,
                    unverifiedValues = createMultipleValuesResponse.unverifiedValues,
                    userProfile = userProfile
                )

                verifyMultipleValueCreationResponse: VerifyMultipleValueCreationResponseV1 <- (responderManager ? verifyCreateValuesRequest).mapTo[VerifyMultipleValueCreationResponseV1]

                // Convert CreateValueResponseV1 objects to ResourceCreateValueResponseV1 objects.

                resourceCreateValueResponses: Map[IRI, Seq[ResourceCreateValueResponseV1]] = verifyMultipleValueCreationResponse.verifiedValues.map {
                    case (propIri: IRI, values: Seq[CreateValueResponseV1]) => (propIri, values.map {
                        valueResponse: CreateValueResponseV1 =>
                            MessageUtil.convertCreateValueResponseV1ToResourceCreateValueResponseV1(ownerIri = ownerIri,
                                propertyIri = propIri,
                                resourceIri = resourceIri,
                                valueResponse = valueResponse)
                    })
                }

                apiResponse: ResourceCreateResponseV1 = ResourceCreateResponseV1(results = resourceCreateValueResponses, res_id = resourceIri, userdata = userProfile.userData)
            } yield apiResponse
        }

        val resultFuture = for {
        // Don't allow anonymous users to create resources.
            userIri: IRI <- Future {
                userProfile.userData.user_id match {
                    case Some(iri) => iri
                    case None => throw ForbiddenException("Anonymous users aren't allowed to create resources")
                }
            }

            namedGraph = settings.projectNamedGraphs(projectIri).data
            resourceIri: IRI = knoraIriUtil.makeRandomResourceIri

            // check if the user has the permissions to create a new resource in the given project
            // get project info that includes the permissions the current user has on the project
            projectInfo: ProjectInfoResponseV1 <- (responderManager ? ProjectInfoByIRIGetRequest(
                iri = projectIri,
                requestType = ProjectInfoType.SHORT,
                Some(userProfile)
            )).mapTo[ProjectInfoResponseV1]

            // TODO: projects rights can not be queried yet because the permissions are missing in the data
            // if the rights returned by projects responder are set to None
            // or if they are below the level of modify permissions, the user's request is refused.
            /*_ = if (!projectInfo.project_info.rights.exists(permissionCode =>
                PermissionUtil.impliesV1(userHasPermissionCode = permissionCode, userNeedsPermissionIri = OntologyConstants.KnoraBase.HasModifyPermission))) {
                throw ForbiddenException(s"User $userIri does not have permissions to create a resource in project $projectIri")
            }*/

            // get default permissions for given resource type
            entityInfoResponse <- {
                responderManager ? EntityInfoGetRequestV1(
                    resourceClassIris = Set(resourceClassIri),
                    userProfile = userProfile
                )
            }.mapTo[EntityInfoGetResponseV1]

            // represents the permissions as a List of 2-tuples:
            // e.g. (http://www.knora.org/ontology/knora-base#hasViewPermission,http://www.knora.org/ontology/knora-base#KnownUser)
            permissions: Seq[(IRI, IRI)] = PermissionUtilV1.makePermissionsFromEntityDefaults(entityInfoResponse.resourceEntityInfoMap(resourceClassIri))

            result: ResourceCreateResponseV1 <- ResourceLocker.runWithResourceLock(
                apiRequestID,
                resourceIri,
                () => createResourceAndCheck(
                    resourceIri = resourceIri,
                    values = values,
                    sipiConversionRequest = sipiConversionRequest,
                    permissions = permissions,
                    namedGraph = namedGraph,
                    ownerIri = userIri,
                    apiRequestID = apiRequestID
                )
            )
        } yield result

        // If a temporary file was created, ensure that it's deleted, regardless of whether the request succeeded or failed.
        resultFuture.andThen {
            case _ =>
                sipiConversionRequest match {
                    case Some(conversionRequest) =>
                        conversionRequest match {
                            case (conversionPathRequest: SipiResponderConversionPathRequestV1) =>
                                // a tmp file has been created by the resources route (non GUI-case), delete it
                                InputValidation.deleteFileFromTmpLocation(conversionPathRequest.source, log)
                            case _ => ()
                        }
                    case None => ()
                }
        }
    }

    /**
      * Checks whether a resource belongs to a certain OWL class or to a subclass of that class.
      *
      * @param resourceIri the IRI of the resource to be checked.
      * @param owlClass    the IRI of the OWL class to compare the resource's class to.
      * @param userProfile the profile of the user making the request.
      * @return a [[ResourceCheckClassResponseV1]].
      */
    private def checkResourceClass(resourceIri: IRI, owlClass: IRI, userProfile: UserProfileV1): Future[ResourceCheckClassResponseV1] = {
        for {
        // Check that the user has permission to view the resource.
            (permissionCode, resourceInfo) <- getResourceInfoV1(resourceIri = resourceIri, userProfile = userProfile, queryOntology = false)

            _ = if (!PermissionUtilV1.impliesV1(userHasPermissionCode = permissionCode, userNeedsPermissionIri = OntologyConstants.KnoraBase.HasRestrictedViewPermission)) {
                val userIri = userProfile.userData.user_id.getOrElse(OntologyConstants.KnoraBase.UnknownUser)
                throw ForbiddenException(s"User $userIri does not have permission to view resource $resourceIri")
            }

            sparqlQuery = queries.sparql.v1.txt.checkSubClass(superClassIri = owlClass, subClassIri = resourceInfo.restype_id).toString()
            queryResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
        } yield ResourceCheckClassResponseV1(isInClass = queryResponse.results.bindings.nonEmpty)
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Helper methods.

    /**
      * Returns a [[ResourceInfoV1]] describing a resource.
      *
      * @param resourceIri   the IRI of the resource to be queried.
      * @param userProfile   the user that is making the request.
      * @param queryOntology if `true`, the ontology will be queried for information about the resource type, and the [[ResourceInfoV1]]
      *                      will include `restype_label`, `restype_description`, and `restype_iconsrc`. Otherwise, those member variables
      *                      will be empty.
      * @return a tuple (permission, [[ResourceInfoV1]]) describing the resource.
      */
    private def getResourceInfoV1(resourceIri: IRI, userProfile: UserProfileV1, queryOntology: Boolean): Future[(Option[Int], ResourceInfoV1)] = {
        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getResourceInfo(resourceIri).toString())
            resInfoResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]
            resInfoResponseRows = resInfoResponse.results.bindings
            resInfo <- makeResourceInfoV1(resourceIri, resInfoResponseRows, userProfile, queryOntology)
        } yield resInfo
    }

    /**
      * Queries the properties that have values for a given resource, and returns a [[Seq]] of [[PropertyV1]] objects representing
      * those properties and their values.
      *
      * @param resourceIri          the IRI of the resource to be queried.
      * @param maybeResourceTypeIri an optional IRI representing the resource's class. If provided, an additional query will be done
      *                             to get ontology-based information, such as labels and cardinalities, which will be included in
      *                             the returned [[PropertyV1]] objects.
      * @param userProfile          the profile of the user making the request.
      * @return a [[Seq]] of [[PropertyV1]] objects representing the properties that have values for the resource.
      */
    private def getResourceProperties(resourceIri: IRI, maybeResourceTypeIri: Option[IRI], userProfile: UserProfileV1): Future[Seq[PropertyV1]] = {
        for {

            groupedPropsByType: GroupedPropertiesByType <- getGroupedProperties(resourceIri)

            // TODO: Should we get rid of the tuple and replace it by a case class?
            (propertyEntityInfoMap: Map[IRI, PropertyEntityInfoV1], resourceEntityInfoMap: Map[IRI, ResourceEntityInfoV1], propsAndCardinalities: Map[IRI, Cardinality.Value]) <- maybeResourceTypeIri match {
                case Some(resourceTypeIri) =>
                    val propertyEntityIris: Set[IRI] = groupedPropsByType.groupedOrdinaryValueProperties.groupedProperties.keySet ++ groupedPropsByType.groupedLinkProperties.groupedProperties.keySet
                    val resourceEntityIris: Set[IRI] = Set(resourceTypeIri)

                    for {
                        entityInfoResponse <- (responderManager ? EntityInfoGetRequestV1(resourceClassIris = resourceEntityIris, propertyIris = propertyEntityIris, userProfile = userProfile)).mapTo[EntityInfoGetResponseV1]
                        resourceEntityInfoMap: Map[IRI, ResourceEntityInfoV1] = entityInfoResponse.resourceEntityInfoMap
                        propertyEntityInfoMap: Map[IRI, PropertyEntityInfoV1] = entityInfoResponse.propertyEntityInfoMap

                        resourceTypeEntityInfo = resourceEntityInfoMap(resourceTypeIri)

                        // all properties and their cardinalities for the queried resource's type, except the ones that point to LinkValue objects
                        propsAndCardinalities: Map[IRI, Cardinality.Value] = resourceTypeEntityInfo.cardinalities.filterNot {
                            case (propertyIri, cardinality) =>
                                resourceTypeEntityInfo.linkValueProperties(propertyIri)
                        }
                    } yield (propertyEntityInfoMap, resourceEntityInfoMap, propsAndCardinalities)

                case None =>
                    Future((Map.empty[IRI, PropertyEntityInfoV1], Map.empty[IRI, ResourceEntityInfoV1], Map.empty[IRI, Cardinality.Value]))
            }
        } yield queryResults2PropertyV1s(
            containingResourceIri = resourceIri,
            groupedPropertiesByType = groupedPropsByType,
            propertyEntityInfoMap = propertyEntityInfoMap,
            resourceEntityInfoMap = resourceEntityInfoMap,
            propsAndCardinalities = propsAndCardinalities,
            userProfile = userProfile
        )
    }

    /**
      * Converts a SPARQL query result into a [[ResourceInfoV1]]. Expects the query result to contain columns called `p` (predicate),
      * `o` (object), `objPred` (file value predicate, if `o` is a file value), and `objObj` (file value object).
      *
      * @param resourceIri         the IRI of the resource.
      * @param resInfoResponseRows the SPARQL query result.
      * @param userProfile         the user that is making the request.
      * @param queryOntology       if `true`, the ontology will be queried for information about the resource type, and the [[ResourceInfoV1]]
      *                            will include `restype_label`, `restype_description`, and `restype_iconsrc`. Otherwise, those member variables
      *                            will be empty.
      * @return a tuple (permission, [[ResourceInfoV1]]) describing the resource.
      */
    private def makeResourceInfoV1(resourceIri: IRI, resInfoResponseRows: Seq[VariableResultsRow], userProfile: UserProfileV1, queryOntology: Boolean): Future[(Option[Int], ResourceInfoV1)] = {
        if (resInfoResponseRows.isEmpty) {
            Future.failed(NotFoundException(s"Resource $resourceIri was not found (it may have been deleted)."))
        } else {
            // Get the rows describing file values from the query results, grouped by file value IRI.
            val fileValueGroupedRows: Seq[(IRI, Seq[VariableResultsRow])] = resInfoResponseRows.filter(row => InputValidation.optionStringToBoolean(row.rowMap.get("isFileValue"))).groupBy(row => row.rowMap("o")).toVector

            // Convert the file value rows to ValueProps objects, and filter out the ones that the user doesn't have permission to see.
            val valuePropsForFileValues: Seq[(IRI, ValueProps)] = fileValueGroupedRows.map {
                case (fileValueIri, fileValueRows) => (fileValueIri, valueUtilV1.createValueProps(fileValueIri, fileValueRows))
            }.filter {
                case (fileValueIri, fileValueProps) =>
                    val permissionCode = PermissionUtilV1.getUserPermissionV1WithValueProps(fileValueIri, fileValueProps, userProfile)
                    PermissionUtilV1.impliesV1(userHasPermissionCode = permissionCode, userNeedsPermissionIri = OntologyConstants.KnoraBase.HasRestrictedViewPermission)
            }

            // Convert the ValueProps objects into FileValueV1 objects
            val fileValues: Seq[FileValueV1] = valuePropsForFileValues.map {
                case (fileValueIri, fileValueProps) =>
                    valueUtilV1.makeValueV1(fileValueProps) match {
                        case fileValueV1: FileValueV1 => fileValueV1
                        case otherValueV1 => throw InconsistentTriplestoreDataException(s"Value $fileValueIri is not a knora-base:FileValue, it is an instance of ${otherValueV1.valueTypeIri}")
                    }
            }

            val (previewFileValues, fullFileValues) = fileValues.partition {
                case fileValue: StillImageFileValueV1 => fileValue.isPreview
                case _ => false
            }

            // Convert the preview file value into a LocationV1 as required by Knora API v1.
            val preview: Option[LocationV1] = previewFileValues.headOption.map(fileValueV1 => valueUtilV1.fileValueV12LocationV1(fileValueV1))

            // Convert the full-resolution file values into LocationV1 objects as required by Knora API v1.
            val locations: Seq[LocationV1] = preview.toVector ++ fullFileValues.flatMap {
                fileValueV1 => createMultipleImageResolutions(fileValueV1).map(oneResolution => valueUtilV1.fileValueV12LocationV1(oneResolution))
            }

            // Extract the permission-relevant assertions from the query results.
            val permissionRelevantAssertions = PermissionUtilV1.filterPermissionRelevantAssertions(resInfoResponseRows.map(row => (row.rowMap("p"), row.rowMap("o"))))

            // Get the user's permission on the resource.
            val userPermission = PermissionUtilV1.getUserPermissionV1(
                subjectIri = resourceIri,
                assertions = permissionRelevantAssertions,
                userProfile = userProfile
            )

            // group the SPARQL results by the predicate "p" and map each row to a Seq of objects "o", etc. (getting rid of VariableResultsRow).
            val groupedByPredicate: Map[IRI, Seq[Map[String, String]]] = resInfoResponseRows.groupBy(row => row.rowMap("p")).map {
                case (predicate: IRI, rows: Seq[VariableResultsRow]) => (predicate, rows.map(_.rowMap - "p"))
            }

            for {
            // Query the ontology about the resource's OWL class.
                (restype_label, restype_description, restype_iconsrc) <- if (queryOntology) {
                    val resTypeIri = groupedByPredicate(OntologyConstants.Rdf.Type).head("o")
                    for {
                        entityInfoResponse <- (responderManager ? EntityInfoGetRequestV1(resourceClassIris = Set(resTypeIri), userProfile = userProfile)).mapTo[EntityInfoGetResponseV1]
                        entityInfo = entityInfoResponse.resourceEntityInfoMap(resTypeIri)
                        label = entityInfo.getPredicateObject(OntologyConstants.Rdfs.Label)
                        description = entityInfo.getPredicateObject(OntologyConstants.Rdfs.Comment)
                        iconsrc = entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ResourceIcon)
                    } yield (label, description, iconsrc)
                } else {
                    Future(None, None, None)
                }

                resourceInfo = ResourceInfoV1(
                    restype_id = groupedByPredicate(OntologyConstants.Rdf.Type).head("o"),
                    firstproperty = Some(groupedByPredicate(OntologyConstants.Rdfs.Label).head("o")),
                    preview = preview, // The first element of the list, or None if the list is empty
                    locations = if (locations.nonEmpty) Some(locations) else None,
                    locdata = locations.lastOption,
                    person_id = groupedByPredicate(OntologyConstants.KnoraBase.AttachedToUser).head("o"),
                    project_id = groupedByPredicate(OntologyConstants.KnoraBase.AttachedToProject).head("o"),
                    permissions = PermissionUtilV1.filterPermissions(permissionRelevantAssertions),
                    restype_label = restype_label,
                    restype_name = Some(groupedByPredicate(OntologyConstants.Rdf.Type).head("o")),
                    restype_description = restype_description,
                    restype_iconsrc = restype_iconsrc,
                    resclass_has_location = locations.nonEmpty
                )
            } yield (userPermission, resourceInfo)
        }
    }

    /**
      * Queries the properties that have values for a given resource, and partitions the results into two categories: (1) results for properties that point
      * to ordinary Knora values ([[GroupedPropertiesByType.groupedOrdinaryValueProperties]]), (2) properties that point to link value objects (reifications of links to resources),
      * and (3) properties that point to other resources ([[GroupedPropertiesByType.groupedLinkProperties]]).
      * Then groups the results in each category first by property, then by property object, and finally by Knora object predicate, each level represented by a case class defined in [[GroupedProps]].
      *
      * @param resourceIri the IRI of the resource to be queried.
      * @return a [[GroupedPropertiesByType]] containing properties that point to ordinary value properties, link value properties, and link properties.
      */
    private def getGroupedProperties(resourceIri: IRI): Future[GroupedPropertiesByType] = {

        for {
            sparqlQuery <- Future(queries.sparql.v1.txt.getResourcePropertiesAndValues(resourceIri).toString())
            // _ = println(sparqlQuery)
            resPropsResponse <- (storeManager ? SparqlSelectRequest(sparqlQuery)).mapTo[SparqlSelectResponse]

            // Partition the property result rows into rows with value properties and rows with link properties.
            (rowsWithLinks: Seq[VariableResultsRow], rowsWithValues: Seq[VariableResultsRow]) = resPropsResponse.results.bindings.partition(_.rowMap.get("isLinkProp").exists(_.toBoolean))

            // Partition the rows with values into rows with ordinary values and rows with link values (reifications).
            (rowsWithLinkValues: Seq[VariableResultsRow], rowsWithOrdinaryValues: Seq[VariableResultsRow]) = rowsWithValues.partition(_.rowMap.get("isLinkValueProp").exists(_.toBoolean))

        } yield valueUtilV1.createGroupedPropsByType(rowsWithOrdinaryValues = rowsWithOrdinaryValues, rowsWithLinkValues = rowsWithLinkValues, rowsWithLinks = rowsWithLinks)
    }

    /**
      * Converts grouped property query results returned by the `getGroupedProperties` method to a [[Seq]] of [[PropertyV1]] objects, optionally
      * using ontology-based data if provided.
      *
      * @param groupedPropertiesByType The [[GroupedPropertiesByType]] returned by `getGroupedProperties` containing the resuls of the SPARQL query.
      * @param propertyEntityInfoMap   a [[Map]] of entity IRIs to [[PropertyEntityInfoV1]] objects. If this [[Map]] is not empty, it will be used to include
      *                                ontology-based information in the returned [[PropertyV1]] objects.
      * @param resourceEntityInfoMap   a [[Map]] of entity IRIs to [[ResourceEntityInfoV1]] objects. If this [[Map]] is not empty, it will be used to include
      *                                ontology-based information for linking properties in the returned [[PropertyV1]] objects.
      * @param propsAndCardinalities   a [[Map]] of property IRIs to their cardinalities in the class of the queried resource. If this [[Map]] is not
      *                                empty, it will be used to include cardinalities in the returned [[PropertyV1]] objects.
      * @param userProfile             the profile of the user making the request.
      * @return a [[Seq]] of [[PropertyV1]] objects.
      */
    private def queryResults2PropertyV1s(containingResourceIri: IRI,
                                         groupedPropertiesByType: GroupedPropertiesByType,
                                         propertyEntityInfoMap: Map[IRI, PropertyEntityInfoV1],
                                         resourceEntityInfoMap: Map[IRI, ResourceEntityInfoV1],
                                         propsAndCardinalities: Map[IRI, Cardinality.Value],
                                         userProfile: UserProfileV1): Seq[PropertyV1] = {
        /**
          * Constructs a [[PropertyV1]].
          *
          * @param propertyIri         the IRI of the property.
          * @param propertyCardinality an optional cardinality that the queried resource's class assigns to the property.
          * @param propertyEntityInfo  an optional [[PropertyEntityInfoV1]] describing the property.
          * @param valueObjects        a list of [[ValueObjectV1]] instances representing the `knora-base:Value` objects associated with the property in the queried resource.
          * @return a [[PropertyV1]].
          */
        def makePropertyV1(propertyIri: IRI, propertyCardinality: Option[Cardinality.Value], propertyEntityInfo: Option[PropertyEntityInfoV1], valueObjects: Seq[ValueObjectV1]): PropertyV1 = {
            PropertyV1(
                pid = propertyIri,
                valuetype_id = propertyEntityInfo.flatMap{
                    row =>
                        if (row.isLinkProp) {
                            // it is a linking property
                            Some(OntologyConstants.KnoraBase.LinkValue)
                        } else {
                            row.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint)
                        }
                },
                guiorder = propertyEntityInfo.flatMap(_.getPredicateObject(OntologyConstants.SalsahGui.GuiOrder).map(_.toInt)),
                guielement = propertyEntityInfo.flatMap(_.getPredicateObject(OntologyConstants.SalsahGui.GuiElement).map(guiElementIri => SalsahGuiConversions.iri2SalsahGuiElement(guiElementIri))),
                label = propertyEntityInfo.flatMap(_.getPredicateObject(OntologyConstants.Rdfs.Label)),
                occurrence = propertyCardinality.map(_.toString),
                attributes = propertyEntityInfo match {
                    case Some(entityInfo) =>
                        if (entityInfo.isLinkProp) {
                            (entityInfo.getPredicateObjects(OntologyConstants.SalsahGui.GuiAttribute) + valueUtilV1.makeAttributeRestype(entityInfo.getPredicateObject(OntologyConstants.KnoraBase.ObjectClassConstraint).getOrElse(throw InconsistentTriplestoreDataException(s"Property $propertyIri has no knora-base:objectClassConstraint")))).mkString(";")
                        } else {
                            entityInfo.getPredicateObjects(OntologyConstants.SalsahGui.GuiAttribute).mkString(";")
                        }
                    case None => ""
                },
                value_rights = valueObjects.map(_.valuePermission),
                value_restype = valueObjects.map {
                    _.valueV1 match {
                        case link: LinkV1 => link.valueResourceClassLabel
                        case other => None
                    }
                },
                value_iconsrcs = valueObjects.map {
                    _.valueV1 match {
                        case link: LinkV1 => link.valueResourceClassIcon
                        case other => None
                    }
                },
                value_firstprops = valueObjects.map {
                    _.valueV1 match {
                        case link: LinkV1 => link.valueLabel
                        case other => None
                    }
                },
                values = valueObjects.map(_.valueV1),
                value_ids = valueObjects.map(_.valueObjectIri),
                comments = valueObjects.map(_.comment match {
                    case Some(comment: String) => comment
                    case None => ""
                })
            )
        }

        // Make a PropertyV1 for each value property that has data.
        val valuePropertiesWithData: Seq[PropertyV1] = groupedPropertiesByType.groupedOrdinaryValueProperties.groupedProperties.map {
            case (propertyIri: IRI, valueObject: ValueObjects) =>

                val valueObjectsV1: Seq[ValueObjectV1] = valueObject.valueObjects.map {
                    case (valObjIri: IRI, valueProps: ValueProps) =>
                        // Make sure the value object has an rdf:type.
                        valueProps.literalData.getOrElse(OntologyConstants.Rdf.Type, throw InconsistentTriplestoreDataException(s"$valObjIri has no rdf:type"))

                        // Convert the SPARQL query results to a ValueV1.
                        val valueV1 = valueUtilV1.makeValueV1(valueProps)

                        val valPermission = PermissionUtilV1.getUserPermissionV1WithValueProps(valObjIri, valueProps, userProfile)
                        val predicates = valueProps.literalData

                        ValueObjectV1(
                            valueObjectIri = valObjIri,
                            valueV1 = valueV1,
                            valuePermission = valPermission,
                            comment = predicates.get(OntologyConstants.KnoraBase.ValueHasComment).map(_.literals.head),
                            order = predicates.get(OntologyConstants.KnoraBase.ValueHasOrder) match {
                                // this should not be necessary as an order should always be given (also if there is only one value)
                                case Some(ValueLiterals(literals)) => literals.head.toInt
                                case _ => 0 // order statement is missing, set it to zero
                            }
                        )
                }.toVector.sortBy(_.order) // sort the values by their order given in the triplestore [[OntologyConstants.KnoraBase.ValueHasOrder]]

                // get all the values the user has at least viewing permissions on
                val valueObjectListFiltered = valueObjectsV1.filter(_.valuePermission.nonEmpty)

                // Get the ontology information about the property.
                val propertyEntityInfo = propertyEntityInfoMap.get(propertyIri)

                // Make a PropertyV1 for the property.
                val propertyV1 = makePropertyV1(propertyIri = propertyIri,
                    propertyCardinality = propsAndCardinalities.get(propertyIri),
                    propertyEntityInfo = propertyEntityInfo,
                    valueObjects = valueObjectListFiltered)

                // If the property has a value that the user isn't allowed to see, and its cardinality
                // is MustHaveOne or MayHaveOne, don't return any information about the property.
                propsAndCardinalities.get(propertyIri) match {
                    case Some(cardinality) if (cardinality == Cardinality.MustHaveOne || cardinality == Cardinality.MayHaveOne) && valueObjectsV1.nonEmpty && valueObjectListFiltered.isEmpty => None
                    case _ => Some(propertyV1)
                }
        }.toVector.flatten

        // Make a PropertyV1 for each link property with data. We have to treat links as a special case because they're not really values (they don't have IRIs),
        // and because Knora API v1 needs some information about the target resource.
        val linkPropertiesWithData: Seq[PropertyV1] = groupedPropertiesByType.groupedLinkProperties.groupedProperties.map {
            case (propertyIri: IRI, targetResource: ValueObjects) =>
                val valueObjectsV1: Seq[ValueObjectV1] = targetResource.valueObjects.map {
                    case (targetResourceIri: IRI, valueProps: ValueProps) =>

                        val predicates = valueProps.literalData

                        // Get the IRI of the resource class of the referenced resource.
                        val referencedResType = predicates(OntologyConstants.Rdf.Type).literals.head

                        // Get info about that resource class, if available.
                        // Use resource entity infos to do so.
                        val (maybeResourceClassLabel: Option[String], maybeResourceClassIcon: Option[String]) = resourceEntityInfoMap.get(referencedResType) match {
                            case Some(referencedResTypeEntityInfo) =>

                                val labelOption: Option[String] = referencedResTypeEntityInfo.getPredicateObjects(OntologyConstants.Rdfs.Label).headOption
                                val resIconOption: Option[String] = referencedResTypeEntityInfo.getPredicateObjects(OntologyConstants.KnoraBase.ResourceIcon).headOption

                                (labelOption, resIconOption)

                            case None => (None, None)
                        }

                        val valueV1 = LinkV1(
                            targetResourceIri = targetResourceIri,
                            valueLabel = predicates.get(OntologyConstants.Rdfs.Label).map(_.literals.head),
                            valueResourceClass = predicates(OntologyConstants.Rdf.Type).literals.headOption,
                            valueResourceClassLabel = maybeResourceClassLabel,
                            valueResourceClassIcon = maybeResourceClassIcon
                        )

                        // A direct link between resources has a corresponding LinkValue reification. We use its IRI as the
                        // value object IRI, since links don't have IRIs of their own.

                        // Convert the link property IRI to a link value property IRI.
                        val linkValuePropertyIri = knoraIriUtil.linkPropertyIriToLinkValuePropertyIri(propertyIri)

                        // Get the details of the link value that's pointed to by that link value property, and that has the target resource as its rdf:object.
                        val (linkValueIri, linkValueProps) = groupedPropertiesByType.groupedLinkValueProperties.groupedProperties.getOrElse(linkValuePropertyIri,
                            throw InconsistentTriplestoreDataException(s"Resource $containingResourceIri has link property $propertyIri but does not have a corresponding link value property")).valueObjects.find {
                            case (someLinkValueIri, someLinkValueProps) =>
                                someLinkValueProps.literalData.getOrElse(OntologyConstants.Rdf.Object, throw InconsistentTriplestoreDataException(s"Link value $someLinkValueIri has no rdf:object")).literals.head == targetResourceIri
                        }.getOrElse(throw InconsistentTriplestoreDataException(s"Link property $propertyIri of resource $containingResourceIri points to resource $targetResourceIri, but there is no corresponding link value with the target resource as its rdf:object"))

                        val linkValueOrder = linkValueProps.literalData.get(OntologyConstants.KnoraBase.ValueHasOrder) match {
                            // this should not be necessary as an order should always be given (also if there is only one value)
                            case Some(ValueLiterals(literals)) => literals.head.toInt
                            case _ => 0 // order statement is missing, set it to zero
                        }

                        // We only allow the user to see information about the link if they have at least view permission on both the link value
                        // and on the target resource.

                        val targetResourcePermission = PermissionUtilV1.getUserPermissionV1WithValueProps(targetResourceIri, valueProps, userProfile)
                        val linkValuePermission = PermissionUtilV1.getUserPermissionV1WithValueProps(linkValueIri, linkValueProps, userProfile)

                        val linkPermission = (targetResourcePermission, linkValuePermission) match {
                            case (Some(targetResourcePermissionCode), Some(linkValuePermissionCode)) => Some(scala.math.min(targetResourcePermissionCode, linkValuePermissionCode))
                            case _ => None
                        }

                        ValueObjectV1(
                            valueObjectIri = linkValueIri,
                            valueV1 = valueV1,
                            valuePermission = linkPermission,
                            order = linkValueOrder
                        )
                }.toVector

                // get all the values the user has at least viewing permissions on
                val valueObjectListFiltered = valueObjectsV1.filter(_.valuePermission.nonEmpty)

                // Get the ontology information about the property, if available.
                val propertyEntityInfo = propertyEntityInfoMap.get(propertyIri)

                // Make a PropertyV1 for the property.
                val propertyV1 = makePropertyV1(propertyIri = propertyIri,
                    propertyCardinality = propsAndCardinalities.get(propertyIri),
                    propertyEntityInfo = propertyEntityInfo,
                    valueObjects = valueObjectListFiltered)

                // If the property has a value that the user isn't allowed to see, and its cardinality
                // is MustHaveOne or MayHaveOne, don't return any information about the property.
                propsAndCardinalities.get(propertyIri) match {
                    case Some(cardinality) if (cardinality == Cardinality.MustHaveOne || cardinality == Cardinality.MayHaveOne) && valueObjectsV1.nonEmpty && valueObjectListFiltered.isEmpty => None
                    case _ => Some(propertyV1)
                }
        }.toVector.flatten

        valuePropertiesWithData ++ linkPropertiesWithData
    }

    /**
      * Given a [[FileValueV1]], checks whether it represents a JPEG 2000 image. If so, returns a sequence of [[StillImageFileValueV1]]
      * objects representing different possible resolutions of the same image. Otherwise, returns a sequence containing the
      * same [[FileValueV1]] unchanged.
      *
      * @param fileValueV1 the file's metadata.
      * @return a sequence of [[FileValueV1]] objects.
      */
    private def createMultipleImageResolutions(fileValueV1: FileValueV1): Seq[FileValueV1] = {
        def scaleImageSize(dimension: Int, power: Int): Int = {
            (dimension.toDouble / math.pow(2, power)).round.toInt
        }

        fileValueV1 match {
            case stillImageFileValueV1: StillImageFileValueV1 =>
                // For JPEG 2000 images, create six LocationV1 objects representing different available resolutions.
                if (stillImageFileValueV1.internalMimeType == "image/jp2") {
                    // Create descriptions of 6 different possible resolutions of the image.
                    (5 to 0 by -1).map {
                        // order them from smallest to largest
                        powerOf2 =>
                            stillImageFileValueV1.copy(
                                dimX = scaleImageSize(stillImageFileValueV1.dimX, powerOf2),
                                dimY = scaleImageSize(stillImageFileValueV1.dimY, powerOf2)
                            )
                    }
                } else {
                    // For other MIME types, just leave the image as is.
                    Vector(stillImageFileValueV1)
                }

            case otherFileValueV1 => Vector(otherFileValueV1)
        }
    }
}
