package org.knora.webapi.util.search.gravsearch.prequery

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.util.search.gravsearch.prequery.TopologicalSortUtil
import scalax.collection.Graph
import scalax.collection.GraphEdge._

class TopologicalSortUtilSpec extends CoreSpec() {
  type NodeT = Graph[Int, DiHyperEdge]#NodeT

  private def nodesToValues(orders: Set[Vector[NodeT]]): Set[Vector[Int]] = {
    orders.map { order: Vector[NodeT] =>
      order.map(_.value)
    }
  }

  "TopologicalSortUtilSpec" should {

    "return all topological orders of a graph" in {
      val graph: Graph[Int, DiHyperEdge] =
        Graph[Int, DiHyperEdge](DiHyperEdge[Int](2, 4), DiHyperEdge[Int](2, 7), DiHyperEdge[Int](4, 5))

      val allOrders: Set[Vector[Int]] = nodesToValues(
        TopologicalSortUtil
          .findAllTopologicalOrders(graph))

      val expectedOrders = Set(
        Vector(2, 4, 5, 7),
        Vector(2, 4, 7, 5),
        Vector(2, 7, 4, 5)
      )

      assert(allOrders == expectedOrders)
    }

    "return an empty set of orders for an empty graph" in {
      val graph: Graph[Int, DiHyperEdge] = Graph[Int, DiHyperEdge]()

      val allOrders: Set[Vector[Int]] = nodesToValues(
        TopologicalSortUtil
          .findAllTopologicalOrders(graph))

      assert(allOrders.isEmpty)
    }

    "return an empty set of orders for a cyclic graph" in {
      val graph: Graph[Int, DiHyperEdge] =
        Graph[Int, DiHyperEdge](DiHyperEdge[Int](2, 4), DiHyperEdge[Int](4, 7), DiHyperEdge[Int](7, 2))

      val allOrders: Set[Vector[Int]] = nodesToValues(
        TopologicalSortUtil
          .findAllTopologicalOrders(graph))

      assert(allOrders.isEmpty)
    }
  }
}
