/*
 * Copyright © 2015-2021 the contributors (see Contributors.md).
 *
 *  This file is part of Knora.
 *
 *  Knora is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published
 *  by the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Knora is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public
 *  License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
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

    def findLayeredPermutations(layeredOrder: graph.LayeredTopologicalOrder[NodeT]): List[Vector[NodeT]] = {
      val lastLayerNodes: Vector[NodeT] = layeredOrder.last._2.toVector
      val allLowerLayers: Iterable[(Int, Iterable[NodeT])] = layeredOrder.dropRight(1)

      // find all permutations of last layer nodes
      val allPermutationsOfLastLayer: List[Vector[NodeT]] = lastLayerNodes.permutations.toList

      // for nodes of each permutation of last layer, find origin of incoming edges
      val allPermutations: List[Vector[NodeT]] = allPermutationsOfLastLayer.map {
        lastLayerPermutation: Vector[NodeT] =>
          val layerPerms: Vector[NodeT] = allLowerLayers.iterator.foldRight(lastLayerPermutation) { (layer, acc) =>
            val layerNodes: Vector[NodeT] = layer._2.toVector

            val origins: Set[NodeT] = acc.foldRight(Set.empty[NodeT]) { (node, originsAcc) =>
              val foundOriginNodeOption: Option[NodeT] = layerNodes.find(n => graph.edges.contains(DiHyperEdge(n, node)))
              // Is there any edge which has its origin in this layer and target in already visited layers?
              foundOriginNodeOption match {
                // Yes. Add the origin node to the topological order
                case Some(n) => Set(n) ++ originsAcc
                // No. do nothing.
                case None => originsAcc
              }
            }
            // Find all nodes of this layer which are not origin of an edge
            val notOriginNodes = layerNodes.diff(origins.toVector)
            // Prepend the non-origin nodes and origin nodes to those found in previous layers.
            notOriginNodes ++ origins ++ acc
          }
          layerPerms
      }
      allPermutations
    }

    // Accumulates topological orders.
    val allOrders: Set[Vector[NodeT]] = graph.topologicalSort match {
      // Is there any topological order?
      case Right(topOrder) =>
        // Yes. Find all valid permutations.
        findLayeredPermutations(topOrder.toLayered).toSet
      case Left(_) =>
        // No, The graph has a cycle, so don't try to sort it.
        Set.empty[Vector[NodeT]]
    }

    allOrders.filter(_.nonEmpty)
  }
}
