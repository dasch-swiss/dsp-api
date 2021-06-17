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

package org.knora.webapi.util.search.gravsearch.prequery

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.util.search.gravsearch.prequery.TopologicalSortUtil
import scalax.collection.Graph
import scalax.collection.GraphEdge._

/**
  * Tests [[TopologicalSortUtil]].
  */
class TopologicalSortUtilSpec extends CoreSpec() {
  type NodeT = Graph[Int, DiHyperEdge]#NodeT

  private def nodesToValues(orders: Set[Vector[NodeT]]): Set[Vector[Int]] = {
    orders.map { order: Vector[NodeT] =>
      order.map(_.value)
    }
  }

  "TopologicalSortUtilSpec" should {

    "return all topological orders of a graph with one leaf" in {
      val graph: Graph[Int, DiHyperEdge] =
        Graph[Int, DiHyperEdge](DiHyperEdge[Int](2, 4), DiHyperEdge[Int](2, 7), DiHyperEdge[Int](4, 5))

      val allOrders: Set[Vector[Int]] = nodesToValues(
        TopologicalSortUtil
          .findAllTopologicalOrderPermutations(graph))

      val expectedOrders = Set(
        Vector(2, 7, 4, 5)
      )

      assert(allOrders == expectedOrders)
    }

    "return all topological orders of a graph with multiple leaves" in {
      val graph: Graph[Int, DiHyperEdge] =
        Graph[Int, DiHyperEdge](DiHyperEdge[Int](2, 4), DiHyperEdge[Int](2, 7), DiHyperEdge[Int](2, 8), DiHyperEdge[Int](4, 5), DiHyperEdge[Int](7, 3))

      val allOrders: Set[Vector[Int]] = nodesToValues(
        TopologicalSortUtil
          .findAllTopologicalOrderPermutations(graph))

      val expectedOrders = Set(
        Vector(2, 8, 4, 7, 5, 3),
        Vector(2, 8, 7, 4, 3, 5)
      )

      assert(allOrders == expectedOrders)
    }

    "return an empty set of orders for an empty graph" in {
      val graph: Graph[Int, DiHyperEdge] = Graph[Int, DiHyperEdge]()

      val allOrders: Set[Vector[Int]] = nodesToValues(
        TopologicalSortUtil
          .findAllTopologicalOrderPermutations(graph))

      assert(allOrders.isEmpty)
    }

    "return an empty set of orders for a cyclic graph" in {
      val graph: Graph[Int, DiHyperEdge] =
        Graph[Int, DiHyperEdge](DiHyperEdge[Int](2, 4), DiHyperEdge[Int](4, 7), DiHyperEdge[Int](7, 2))

      val allOrders: Set[Vector[Int]] = nodesToValues(
        TopologicalSortUtil
          .findAllTopologicalOrderPermutations(graph))

      assert(allOrders.isEmpty)
    }
  }
}
