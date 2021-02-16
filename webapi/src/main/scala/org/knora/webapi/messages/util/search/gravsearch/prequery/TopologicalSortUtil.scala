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

import org.knora.webapi.exceptions.AssertionException
import scalax.collection.Graph
import scalax.collection.GraphEdge.DiHyperEdge

import scala.collection.mutable

/**
  * A utility for finding all topological orders of a graph.
  * Based on [[https://github.com/scala-graph/scala-graph/issues/129#issuecomment-485398400]].
  */
object TopologicalSortUtil {

  /**
	 * Finds all possible topological orders of a graph.
	 *
	 * @param graph the graph to be sorted.
	 * @tparam T the type of the nodes in the graph.
	 */
  def allTopologicalOrders[T](graph: Graph[T, DiHyperEdge]): Set[List[graph.NodeT]] = {

    /**
		 * Represents arguments to be put on the simulated call stack.
		 */
    case class StackItem(sources: Set[graph.NodeT],
                         inDegrees: Map[graph.NodeT, Int],
                         topOrder: List[graph.NodeT],
                         count: Int)

    val inDegree: Map[graph.NodeT, Int] = graph.nodes.map(node => (node, node.inDegree)).toMap

    def isSource(node: graph.NodeT): Boolean = inDegree(node) == 0
    def getSources: Set[graph.NodeT] = graph.nodes.filter(node => isSource(node)).toSet

    // Replaces the program stack by our own.
    val stack: mutable.ArrayStack[StackItem] = new mutable.ArrayStack()

    // Accumulates topological orders.
    var allOrders = Set[List[graph.NodeT]]()

    // Push arguments onto the stack.
    stack.push(StackItem(getSources, inDegree, List[graph.NodeT](), 0))

    while (stack.nonEmpty) {
      // Fetch arguments
      val stackItem = stack.pop()

      if (stackItem.sources.nonEmpty) {
        // `sources` contains all the nodes we can pick. Generate all possibilities.
        for (source <- stackItem.sources) {
          val newTopOrder = source :: stackItem.topOrder
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
        throw AssertionException("Expected an an acyclic graph, but there is a cycle")
      } else {
        allOrders += stackItem.topOrder.reverse
      }
    }

    allOrders
  }
}
