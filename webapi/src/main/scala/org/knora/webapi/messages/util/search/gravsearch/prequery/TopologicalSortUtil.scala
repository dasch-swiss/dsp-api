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
	 * Finds all possible topological orders of a graph. If the graph is cyclical, returns an empty set.
	 *
	 * @param graph the graph to be sorted.
	 * @tparam T the type of the nodes in the graph.
	 */
  def findAllTopologicalOrders[T](graph: Graph[T, DiHyperEdge]): Set[Vector[Graph[T, DiHyperEdge]#NodeT]] = {
    type NodeT = Graph[T, DiHyperEdge]#NodeT

    /**
		 * Represents arguments to be put on the simulated call stack.
		 */
    case class StackItem(sources: Set[NodeT], inDegrees: Map[NodeT, Int], topOrder: Vector[NodeT], count: Int)

    val inDegrees: Map[NodeT, Int] = graph.nodes.map(node => (node, node.inDegree)).toMap

    def isSource(node: NodeT): Boolean = inDegrees(node) == 0
    def getSources: Set[NodeT] = graph.nodes.filter(node => isSource(node)).toSet

    // Replaces the program stack by our own.
    val stack: mutable.ArrayStack[StackItem] = new mutable.ArrayStack()

    // Accumulates topological orders.
    var allTopologicalOrders = Set[Vector[NodeT]]()

    // Push arguments onto the stack.
    stack.push(StackItem(sources = getSources, inDegrees = inDegrees, topOrder = Vector[NodeT](), count = 0))

    while (stack.nonEmpty) {
      // Fetch arguments
      val stackItem = stack.pop()

      if (stackItem.sources.nonEmpty) {
        // `sources` contains all the nodes we can pick. Generate all possibilities.
        for (source <- stackItem.sources) {
          val newTopOrder = source +: stackItem.topOrder
          var newSources = stackItem.sources - source

          // Decrease the in-degree of all adjacent nodes.
          var newInDegrees = stackItem.inDegrees

          for (adjacent <- source.diSuccessors) {
            val newInDegree = newInDegrees(adjacent) - 1
            newInDegrees = newInDegrees.updated(adjacent, newInDegree)

            // If in-degree becomes zero, add to sources.
            if (newInDegree == 0) {
              newSources = newSources + adjacent
            }
          }

          stack.push(StackItem(newSources, newInDegrees, newTopOrder, stackItem.count + 1))
        }
      } else if (stackItem.count != graph.nodes.size) {
        // The graph has a cycle, so don't try to sort it.
        ()
      } else {
        allTopologicalOrders += stackItem.topOrder.reverse
      }
    }

    allTopologicalOrders.filter(_.nonEmpty)
  }
}
