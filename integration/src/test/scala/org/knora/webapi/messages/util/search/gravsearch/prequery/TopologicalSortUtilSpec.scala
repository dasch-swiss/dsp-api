/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.search.gravsearch.prequery

import scalax.collection.hyperedges.DiHyperEdge
import scalax.collection.immutable.Graph

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.util.search.gravsearch.prequery.TopologicalSortUtil

/**
 * Tests [[TopologicalSortUtil]].
 */
class TopologicalSortUtilSpec extends CoreSpec {
  type GraphT = Graph[Int, DiHyperEdge[Int]]
  type NodeT  = GraphT#NodeT

  private def nodesToValues(orders: Set[Vector[NodeT]]): Set[Vector[Int]] = orders.map(_.map(_.outer))

  "TopologicalSortUtilSpec" should {

    "return all topological orders of a graph with one leaf" in {
      val graph: GraphT =
        Graph.from(List(DiHyperEdge[Int](2)(4), DiHyperEdge[Int](2)(7), DiHyperEdge[Int](4)(5)))

      val allOrders: Set[Vector[Int]] = nodesToValues(
        TopologicalSortUtil
          .findAllTopologicalOrderPermutations(graph)
      )

      val expectedOrders = Set(
        Vector(2, 7, 4, 5)
      )

      assert(allOrders == expectedOrders)
    }

    "return all topological orders of a graph with multiple leaves" in {
      val graph: GraphT =
        Graph.from(
          List(
            DiHyperEdge[Int](2)(4),
            DiHyperEdge[Int](2)(7),
            DiHyperEdge[Int](2)(8),
            DiHyperEdge[Int](4)(5),
            DiHyperEdge[Int](7)(3)
          )
        )

      val allOrders: Set[Vector[Int]] = nodesToValues(
        TopologicalSortUtil
          .findAllTopologicalOrderPermutations(graph)
      )

      val expectedOrders = Set(
        Vector(2, 8, 4, 7, 5, 3),
        Vector(2, 8, 7, 4, 3, 5)
      )

      assert(allOrders == expectedOrders)
    }

    "return an empty set of orders for an empty graph" in {
      val graph: GraphT = Graph.empty

      val allOrders: Set[Vector[Int]] = nodesToValues(
        TopologicalSortUtil
          .findAllTopologicalOrderPermutations(graph)
      )

      assert(allOrders.isEmpty)
    }

    "return an empty set of orders for a cyclic graph" in {
      val graph: GraphT =
        Graph.from(List(DiHyperEdge[Int](2)(4), DiHyperEdge[Int](4)(7), DiHyperEdge[Int](7)(2)))

      val allOrders: Set[Vector[Int]] = nodesToValues(
        TopologicalSortUtil
          .findAllTopologicalOrderPermutations(graph)
      )

      assert(allOrders.isEmpty)
    }
  }
}
