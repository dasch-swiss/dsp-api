/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import scalax.collection.Graph
import scalax.collection.GraphEdge.DiHyperEdge

import scala.collection.mutable

/**
 * A utility for finding all topological orders of a graph.
 * Based on [[https://github.com/scala-graph/scala-graph/issues/129#issuecomment-485398400]].
 */
object TopologicalSortUtil {

  /**
   * Finds all possible topological order permutations of a graph. If the graph is cyclical, returns an empty set.
   *
   * @param graph the graph to be sorted.
   * @tparam T the type of the nodes in the graph.
   */
  def findAllTopologicalOrderPermutations[T](graph: Graph[T, DiHyperEdge]): Set[Vector[Graph[T, DiHyperEdge]#NodeT]] = {
    type NodeT = Graph[T, DiHyperEdge]#NodeT

    /**
     * Finds all possible topological order permutations of a graph using layer information. This method considers all
     * permutations of the topological order regarding the leaf nodes.
     *
     * First, for each permutation of leaf nodes, find the correct order of parent nodes w.r.t incoming edges.
     * For example, consider a graph that its topological order has 3 layers; i.e. layer 0 contains root nodes, and layer 2 contains leaf nodes.
     * Permutations of leaf nodes consist the possible topological orders. Iterate over these set of ordered nodes to
     * add nodes of lower layers to each set by considering the edges. That means for nodes of each layer, e.g. layer 1,
     * find the outgoing edges to layer 2. If there is an edge for node n in layer 1 to node m in layer 2, then add this
     * node to the set of order. After adding these nodes that are origins of edges, add the remaining nodes of layer 1
     * to the set of order. Proceed to layer 0 and add nodes of it to the order in the same manner.
     *
     * @param layeredOrder the topological order of the graph with layer information i.e. (layer number, layer nodes).
     * @return a list of all permutations of topological order.
     */
    def findPermutations(layeredOrder: graph.LayeredTopologicalOrder[NodeT]): List[Vector[NodeT]] = {
      val lastLayerNodes: Vector[NodeT] = layeredOrder.last._2.toVector
      val allLowerLayers: Iterable[(Int, Iterable[NodeT])] = layeredOrder.dropRight(1)

      // Find all permutations of last layer nodes; i.e leaf nodes.
      val permutationsOfLastLayerNodes: List[Vector[NodeT]] = lastLayerNodes.permutations.toList

      // For each permutation of last layer nodes, add nodes of lower layers in correct order.
      val allPermutations: List[Vector[NodeT]] = permutationsOfLastLayerNodes.map {
        lastLayerPermutation: Vector[NodeT] =>
          // Iterate over the previous layers to add the nodes into the order w.r.t. edges.
          val orderedLowerLayerNodes: Vector[NodeT] = allLowerLayers.iterator.foldRight(lastLayerPermutation) {
            (layer, acc) =>
              val layerNodes: Vector[NodeT] = layer._2.toVector
              // Get those nodes within a layer that are origins of outgoing edges to the nodes already in set of ordered nodes.
              val origins: Set[NodeT] = acc.foldRight(Set.empty[NodeT]) { (node, originsAcc) =>
                val maybeOriginNode: Option[NodeT] =
                  layerNodes.find(layerNode => graph.edges.contains(DiHyperEdge(layerNode, node)))
                // Is there any edge which has its origin in this layer and target in already visited layers?
                maybeOriginNode match {
                  // Yes. Add the origin node to the topological order
                  case Some(originNode) => Set(originNode) ++ originsAcc
                  // No. do nothing.
                  case None => originsAcc
                }
              }
              // Find all nodes of this layer which are not origin of an edge
              val notOriginNodes = layerNodes.diff(origins.toVector)
              // Prepend the non-origin nodes and origin nodes to those found in higher layers.
              notOriginNodes ++ origins ++ acc
          }
          orderedLowerLayerNodes
      }
      allPermutations
    }

    // Accumulates topological orders.
    val allOrders: Set[Vector[NodeT]] = graph.topologicalSort match {
      // Is there any topological order?
      case Right(topOrder) =>
        // Yes. Find all valid permutations.
        findPermutations(topOrder.toLayered).toSet
      case Left(_) =>
        // No, The graph has a cycle, so don't try to sort it.
        Set.empty[Vector[NodeT]]
    }

    allOrders.filter(_.nonEmpty)
  }
}
