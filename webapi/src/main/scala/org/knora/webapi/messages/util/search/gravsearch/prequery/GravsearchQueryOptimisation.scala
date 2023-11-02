/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import scalax.collection.Graph
import scalax.collection.GraphEdge.DiHyperEdge

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.util.search.gravsearch.prequery.RemoveEntitiesInferredFromProperty.removeEntitiesInferredFromProperty
import org.knora.webapi.messages.util.search.gravsearch.prequery.RemoveRedundantKnoraApiResource.removeRedundantKnoraApiResource
import org.knora.webapi.messages.util.search.gravsearch.prequery.ReorderPatternsByDependency.reorderPatternsByDependency
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionResult
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionUtil
import org.knora.webapi.messages.util.search.gravsearch.types.TypeableEntity

/**
 * A feature factory that constructs Gravsearch query optimisation algorithms.
 */
object GravsearchQueryOptimisation {

  def optimiseQueryPatterns(
    patterns: Seq[QueryPattern],
    typeInspectionResult: GravsearchTypeInspectionResult
  ): Seq[QueryPattern] = {
    val removedRedundant = removeRedundantKnoraApiResource(patterns)
    val removedEntities  = removeEntitiesInferredFromProperty(removedRedundant, typeInspectionResult)
    val result           = reorderPatternsByDependency(removedEntities)
    result
  }
}

/**
 * Removes a statement with rdf:type knora-api:Resource if there is another rdf:type statement with the same subject
 * and a different type.
 */
private object RemoveRedundantKnoraApiResource {

  /**
   * If the specified statement has rdf:type with an IRI as object, returns that IRI, otherwise None.
   */
  private def getObjOfRdfType(statementPattern: StatementPattern): Option[SmartIri] =
    statementPattern.pred match {
      case predicateIriRef: IriRef =>
        if (predicateIriRef.iri.toString == OntologyConstants.Rdf.Type) {
          statementPattern.obj match {
            case iriRef: IriRef => Some(iriRef.iri)
            case _              => None
          }
        } else {
          None
        }

      case _ => None
    }

  def removeRedundantKnoraApiResource(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
    // Make a Set of subjects that have rdf:type statements whose objects are not knora-api:Resource.
    val rdfTypesBySubj: Set[Entity] = patterns
      .foldLeft(Set.empty[Entity]) { case (acc, queryPattern: QueryPattern) =>
        queryPattern match {
          case statementPattern: StatementPattern =>
            getObjOfRdfType(statementPattern) match {
              case Some(typeIri) =>
                if (!OntologyConstants.KnoraApi.KnoraApiV2ResourceIris.contains(typeIri.toString)) {
                  acc + statementPattern.subj
                } else {
                  acc
                }

              case None => acc
            }

          case _ => acc
        }
      }

    patterns.filterNot {
      case statementPattern: StatementPattern =>
        // If this statement has rdf:type knora-api:Resource, and we also have another rdf:type statement
        // with the same subject and a different type, remove this statement.
        getObjOfRdfType(statementPattern) match {
          case Some(typeIri) =>
            OntologyConstants.KnoraApi.KnoraApiV2ResourceIris
              .contains(typeIri.toString) && rdfTypesBySubj.contains(statementPattern.subj)

          case None => false
        }

      case _ => false
    }
  }
}

/**
 * Optimises a query by removing `rdf:type` statements that are known to be redundant. A redundant
 * `rdf:type` statement gives the type of a variable whose type is already restricted by its
 * use with a property that can only be used with that type (unless the property
 * statement is in an `OPTIONAL` block).
 */
private object RemoveEntitiesInferredFromProperty {

  def removeEntitiesInferredFromProperty(
    patterns: Seq[QueryPattern],
    typeInspectionResult: GravsearchTypeInspectionResult
  ): Seq[QueryPattern] = {

    // Collect all entities which are used as subject or object of an OptionalPattern.
    val optionalEntities: Seq[TypeableEntity] = patterns.collect { case optionalPattern: OptionalPattern =>
      optionalPattern
    }.flatMap {
      case optionalPattern: OptionalPattern =>
        optionalPattern.patterns.flatMap {
          case pattern: StatementPattern =>
            GravsearchTypeInspectionUtil.maybeTypeableEntity(pattern.subj) ++ GravsearchTypeInspectionUtil
              .maybeTypeableEntity(pattern.obj)

          case _ => None
        }

      case _ => None
    }

    // Remove statements whose predicate is rdf:type, type of subject is inferred from a property,
    // and the subject is not in optionalEntities.
    patterns.filterNot {
      case statementPattern: StatementPattern =>
        // Is the predicate an IRI?
        statementPattern.pred match {
          case predicateIriRef: IriRef =>
            // Yes. Is this an rdf:type statement?
            if (predicateIriRef.iri.toString == OntologyConstants.Rdf.Type) {
              // Yes. Is the subject a typeable entity?
              val subjectAsTypeableEntity: Option[TypeableEntity] =
                GravsearchTypeInspectionUtil.maybeTypeableEntity(statementPattern.subj)

              subjectAsTypeableEntity match {
                case Some(typeableEntity) =>
                  // Yes. Was the type of the subject inferred from another predicate?
                  if (typeInspectionResult.entitiesInferredFromProperties.keySet.contains(typeableEntity)) {
                    // Yes. Is the subject in optional entities?
                    if (optionalEntities.contains(typeableEntity)) {
                      // Yes. Keep the statement.
                      false
                    } else {
                      // Remove the statement.
                      true
                    }
                  } else {
                    // The type of the subject was not inferred from another predicate. Keep the statement.
                    false
                  }

                case _ =>
                  // The subject isn't a typeable entity. Keep the statement.
                  false
              }
            } else {
              // This isn't an rdf:type statement. Keep it.
              false
            }

          case _ =>
            // The predicate isn't an IRI. Keep the statement.
            false
        }

      case _ =>
        // This isn't a statement pattern. Keep it.
        false
    }
  }
}

/**
 * Optimises query patterns by reordering them on the basis of dependencies between subjects and objects.
 */
private object ReorderPatternsByDependency {

  /**
   * Converts a sequence of query patterns into DAG representing dependencies between
   * the subjects and objects used, performs a topological sort of the graph, and reorders
   * the query patterns according to the topological order.
   *
   * @param statementPatterns the query patterns to be reordered.
   * @return the reordered query patterns.
   */
  private def createAndSortGraph(statementPatterns: Seq[StatementPattern]): Seq[QueryPattern] = {
    @scala.annotation.tailrec
    def makeGraphWithoutCycles(graphComponents: Seq[(String, String)]): Graph[String, DiHyperEdge] = {
      val graph: Graph[String, DiHyperEdge] = graphComponents.foldLeft(Graph.empty[String, DiHyperEdge]) {
        (graph, edgeDef) =>
          val edge: DiHyperEdge[String] = DiHyperEdge(edgeDef._1, edgeDef._2)
          graph ++ Vector(edge) // add nodes and edges to graph
      }

      if (graph.isCyclic) {
        // get the cycle
        val cycle: graph.Cycle = graph.findCycle.get

        // the cyclic node is the one that cycle starts and ends with
        val cyclicNode: graph.NodeT        = cycle.endNode
        val cyclicEdge: graph.EdgeT        = cyclicNode.edges.last
        val originNodeOfCyclicEdge: String = cyclicEdge._1.value
        val TargetNodeOfCyclicEdge: String = cyclicEdge._2.value
        val graphComponentsWithOutCycle =
          graphComponents.filterNot(edgeDef => edgeDef.equals((originNodeOfCyclicEdge, TargetNodeOfCyclicEdge)))

        makeGraphWithoutCycles(graphComponentsWithOutCycle)
      } else {
        graph
      }
    }

    def createGraph: Graph[String, DiHyperEdge] = {
      val graphComponents: Seq[(String, String)] = statementPatterns.map { statementPattern =>
        // transform every statementPattern to pair of nodes that will consist an edge.
        val node1 = statementPattern.subj.toSparql
        val node2 = statementPattern.obj.toSparql
        (node1, node2)
      }

      makeGraphWithoutCycles(graphComponents)
    }

    /**
     * Finds topological orders that don't end with an object of rdf:type.
     *
     * @param orders the orders to be filtered.
     * @param statementPatterns the statement patterns that the orders are based on.
     * @return the filtered topological orders.
     */
    def findOrdersNotEndingWithObjectOfRdfType(
      orders: Set[Vector[Graph[String, DiHyperEdge]#NodeT]],
      statementPatterns: Seq[StatementPattern]
    ): Set[Vector[Graph[String, DiHyperEdge]#NodeT]] = {
      type NodeT = Graph[String, DiHyperEdge]#NodeT

      // Find the nodes that are objects of rdf:type in the statement patterns.
      val nodesThatAreObjectsOfRdfType: Set[String] = statementPatterns.filter { statementPattern =>
        statementPattern.pred match {
          case iriRef: IriRef => iriRef.iri.toString == OntologyConstants.Rdf.Type
          case _              => false
        }
      }.map { statementPattern =>
        statementPattern.obj.toSparql
      }.toSet

      // Filter out the topological orders that end with any of those nodes.
      orders.filterNot { order: Vector[NodeT] =>
        nodesThatAreObjectsOfRdfType.contains(order.last.value)
      }
    }

    /**
     * Tries to find the best topological order for the graph, by finding all possible topological orders
     * and eliminating those whose last node is the object of rdf:type.
     *
     * @param graph the graph to be ordered.
     * @param statementPatterns the statement patterns that were used to create the graph.
     * @return a topological order.
     */
    def findBestTopologicalOrder(
      graph: Graph[String, DiHyperEdge],
      statementPatterns: Seq[StatementPattern]
    ): Vector[Graph[String, DiHyperEdge]#NodeT] = {
      type NodeT = Graph[String, DiHyperEdge]#NodeT

      /**
       * An ordering for sorting topological orders.
       */
      object TopologicalOrderOrdering extends Ordering[Vector[NodeT]] {
        private def orderToString(order: Vector[NodeT]) = order.map(_.value).mkString("|")

        override def compare(left: Vector[NodeT], right: Vector[NodeT]): Int =
          orderToString(left).compare(orderToString(right))
      }

      // Get all the possible topological orders for the graph.
      val allTopologicalOrders: Set[Vector[NodeT]] = TopologicalSortUtil.findAllTopologicalOrderPermutations(graph)

      // Did we find any topological orders?
      if (allTopologicalOrders.isEmpty) {
        // No, the graph is cyclical.
        Vector.empty
      } else {
        // Yes. Is there only one possible order?
        if (allTopologicalOrders.size == 1) {
          // Yes. Don't bother filtering.
          allTopologicalOrders.head
        } else {
          // There's more than one possible order. Find orders that don't end with an object of rdf:type.
          val ordersNotEndingWithObjectOfRdfType: Set[Vector[NodeT]] =
            findOrdersNotEndingWithObjectOfRdfType(allTopologicalOrders, statementPatterns)

          // Are there any?
          val preferredOrders = if (ordersNotEndingWithObjectOfRdfType.nonEmpty) {
            // Yes. Use one of those.
            ordersNotEndingWithObjectOfRdfType
          } else {
            // No. Use any order.
            allTopologicalOrders
          }

          // Sort the preferred orders to produce a deterministic result, and return one of them.
          preferredOrders.min(TopologicalOrderOrdering)
        }
      }
    }

    def sortStatementPatterns(
      createdGraph: Graph[String, DiHyperEdge],
      statementPatterns: Seq[StatementPattern]
    ): Seq[QueryPattern] = {
      type NodeT = Graph[String, DiHyperEdge]#NodeT

      // Try to find the best topological order for the graph.
      val topologicalOrder: Vector[NodeT] =
        findBestTopologicalOrder(graph = createdGraph, statementPatterns = statementPatterns)

      // Was a topological order found?
      if (topologicalOrder.nonEmpty) {
        // Yes. Sort the statement patterns according to the reverse topological order.
        topologicalOrder.foldRight(Vector.empty[QueryPattern]) { (node, sortedStatements) =>
          val nextStatements = statementPatterns.filter(_.obj.toSparql.equals(node.value)).toVector
          nextStatements ++ sortedStatements
        }
      } else {
        // No topological order found.
        statementPatterns
      }
    }

    sortStatementPatterns(createGraph, statementPatterns)
  }

  /**
   * Performs the optimisation.
   *
   * @param patterns the query patterns.
   * @return the optimised query patterns.
   */
  def reorderPatternsByDependency(patterns: Seq[QueryPattern]): Seq[QueryPattern] = {
    val (statementPatterns: Seq[StatementPattern], otherPatterns: Seq[QueryPattern]) =
      patterns.partition {
        case _: StatementPattern => true
        case _                   => false
      }

    val sortedStatementPatterns = createAndSortGraph(statementPatterns)

    val sortedOtherPatterns = otherPatterns.map {
      case unionPattern: UnionPattern =>
        UnionPattern(unionPattern.blocks.map(reorderPatternsByDependency))
      case optionalPattern: OptionalPattern =>
        OptionalPattern(reorderPatternsByDependency(optionalPattern.patterns))
      case minusPattern: MinusPattern =>
        MinusPattern(reorderPatternsByDependency(minusPattern.patterns))
      case filterNotExistsPattern: FilterNotExistsPattern =>
        FilterNotExistsPattern(reorderPatternsByDependency(filterNotExistsPattern.patterns))
      case pattern: QueryPattern => pattern
    }

    sortedStatementPatterns ++ sortedOtherPatterns
  }
}
