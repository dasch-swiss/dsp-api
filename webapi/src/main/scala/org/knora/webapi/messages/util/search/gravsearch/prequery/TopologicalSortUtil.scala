/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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
    def findPermutations(listOfLists: List[Vector[NodeT]]): List[Vector[NodeT]] = {
      def makePermutations(next: Vector[NodeT], acc: List[Vector[NodeT]]): List[Vector[NodeT]] = {
        next.permutations.toList.flatMap(i => acc.map(j => j ++ i))
      }

      def makePermutationsRec(next: Vector[NodeT],
                              rest: List[Vector[NodeT]],
                              acc: List[Vector[NodeT]]): List[Vector[NodeT]] = {
        if (rest.isEmpty) {
          makePermutations(next, acc)
        } else {
          makePermutationsRec(rest.head, rest.tail, makePermutations(next, acc))
        }
      }

      listOfLists match {
        case Nil                => Nil
        case one :: Nil         => one.permutations.toList
        case one :: two :: tail => makePermutationsRec(two, tail, one.permutations.toList)
      }
    }

    // Accumulates topological orders.
    val allOrders = graph.topologicalSort match {
      // Is there any topological order?
      case Right(topOrder) =>
        // Yes, find all valid permutations
        val nodesOfLayers: List[Vector[NodeT]] =
          topOrder.toLayered.iterator.foldRight(List.empty[Vector[NodeT]]) { (layer, acc) =>
            val layerNodes: Vector[NodeT] = layer._2.toVector
            layerNodes +: acc
          }
        findPermutations(nodesOfLayers).toSet
      case Left(_) =>
        // No, The graph has a cycle, so don't try to sort it.
        Set.empty[Vector[NodeT]]
    }
    allOrders.filter(_.nonEmpty)
  }
}
