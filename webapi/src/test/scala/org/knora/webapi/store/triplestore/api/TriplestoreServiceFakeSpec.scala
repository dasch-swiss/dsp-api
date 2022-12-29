/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.api
import zio.test._

import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.store.triplestore.TestDatasetBuilder.asLayer
import org.knora.webapi.store.triplestore.TestDatasetBuilder.dataSetFromTurtle

object TriplestoreServiceFakeSpec extends ZIOSpecDefault {

  private val testDataSet =
    dataSetFromTurtle("""
                        |@prefix rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                        |@prefix anything:    <http://www.knora.org/ontology/0001/anything#> .
                        |
                        |<http://rdfh.ch/0001/knownThing> a anything:Thing .
                        |
                        |""".stripMargin)

  val spec = suite("TriplestoreServiceFake")(
    suite("sparqlHttpAsk")(
      test("should return true if thing exists") {
        val query = """
                      |PREFIX rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                      |PREFIX anything:    <http://www.knora.org/ontology/0001/anything#>
                      |
                      |ASK WHERE {
                      | <http://rdfh.ch/0001/knownThing> a anything:Thing .
                      |}
                      |""".stripMargin
        for {
          result <- TriplestoreService.sparqlHttpAsk(query)
        } yield assertTrue(result.result)
      },
      test("should return false if thing dose not exist") {
        val query = """
                      |PREFIX rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                      |PREFIX anything:    <http://www.knora.org/ontology/0001/anything#>
                      |
                      |ASK WHERE {
                      | <http://rdfh.ch/0001/nonexisting> a anything:Thing .
                      |}
                      |""".stripMargin
        for {
          result <- TriplestoreService.sparqlHttpAsk(query)
        } yield assertTrue(!result.result)
      }
    ),
    suite("sparqlHttpSelect")(
      test("not find non-existing thing") {
        val query = """
                      |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                      |
                      |SELECT ?p ?o
                      |WHERE {
                      |  <http://rdfh.ch/0001/nonexisting> ?p ?o.
                      |}
                      |""".stripMargin
        for {
          result <- TriplestoreService.sparqlHttpSelect(query)
        } yield assertTrue(
          result == SparqlSelectResult(
            SparqlSelectResultHeader(List("p", "o")),
            SparqlSelectResultBody(List())
          )
        )
      },
      test("find an existing thing") {
        val query = """
                      |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                      |
                      |SELECT ?p ?o
                      |WHERE {
                      |  <http://rdfh.ch/0001/knownThing> ?p ?o.
                      |}
                      |""".stripMargin
        for {
          result <- TriplestoreService.sparqlHttpSelect(query)
        } yield assertTrue(
          result == SparqlSelectResult(
            SparqlSelectResultHeader(List("p", "o")),
            SparqlSelectResultBody(
              List(
                VariableResultsRow(
                  Map(
                    "p" -> "http://www.w3.org/1999/02/22-rdf-syntax-ns#type",
                    "o" -> "http://www.knora.org/ontology/0001/anything#Thing"
                  )
                )
              )
            )
          )
        )
      }
    )
  ).provide(TriplestoreServiceFake.layer, asLayer(testDataSet))
}
