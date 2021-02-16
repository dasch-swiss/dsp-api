package org.knora.webapi.util.search.gravsearch.prequery

import org.knora.webapi.CoreSpec
import org.knora.webapi.messages.util.search.gravsearch.prequery.TopologicalSortUtil
import scalax.collection.Graph
import scalax.collection.GraphEdge._

class TopologicalSortUtilSpec extends CoreSpec() {
  "TopologicalSortUtilSpec" should {

    "return all topological orders of a graph" in {
      val graph: Graph[Int, DiHyperEdge] =
        Graph[Int, DiHyperEdge](DiHyperEdge[Int](2, 4), DiHyperEdge[Int](2, 7), DiHyperEdge[Int](4, 5))

      val allOrders: Set[List[Int]] = TopologicalSortUtil
        .allTopologicalOrders(graph)
        .map { order: List[Graph[Int, DiHyperEdge]#NodeT] =>
          order.map(_.value)
        }

      val expectedOrders = Set(
        List(2, 4, 5, 7),
        List(2, 4, 7, 5),
        List(2, 7, 4, 5)
      )

      assert(allOrders == expectedOrders)
    }
  }
}
