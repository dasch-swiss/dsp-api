/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.store.triplestore.api
import zio.test._
import zio.test.Assertion.hasSameElements

import org.knora.webapi.messages.util.rdf._
import org.knora.webapi.slice.resourceinfo.domain.IriTestConstants.Biblio
import org.knora.webapi.store.triplestore.TestDatasetBuilder.datasetLayerFromTurtle

object TriplestoreServiceFakeSpec extends ZIOSpecDefault {

  private val testDataSet =
    s"""
       |@prefix rdf:        <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
       |@prefix rdfs:       <http://www.w3.org/2000/01/rdf-schema#> .
       |@prefix owl:        <http://www.w3.org/2002/07/owl#> .
       |@prefix biblio:     <${Biblio.Ontology.value}> .
       |
       |<${Biblio.Class.Publication.value}>     rdf:type          owl:Class .
       |
       |<${Biblio.Class.Article.value}>         rdf:type          owl:Class ;
       |                                        rdfs:subClassOf   <${Biblio.Class.Publication.value}> .
       |
       |<${Biblio.Class.JournalArticle.value}>  rdf:type          owl:Class ;
       |                                        rdfs:subClassOf   <${Biblio.Class.Article.value}> .
       |
       |<http://anArticle>                      a                 <${Biblio.Class.Article.value}> .
       |<http://aJournalArticle>                a                 <${Biblio.Class.JournalArticle.value}> .
       |
       |""".stripMargin

  val spec: Spec[Any, Throwable] =
    suite("TriplestoreServiceFake")(
      suite("sparqlHttpAsk")(
        test("should return true if anArticle exists") {
          val query = s"""
                         |PREFIX rdf:         <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                         |                          
                         |ASK WHERE {
                         |  <http://anArticle> a <${Biblio.Class.Article.value}> .
                         |}
                         |""".stripMargin
          for {
            result <- TriplestoreService.sparqlHttpAsk(query)
          } yield assertTrue(result.result)
        },
        test("should return false if thing does not exist") {
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
          } yield assertTrue(result.results.bindings.isEmpty)
        },
        test("find an existing thing") {
          val query = """
                        |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                        |
                        |SELECT ?p ?o
                        |WHERE {
                        |  <http://aJournalArticle> ?p ?o.
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
                      "o" -> s"${Biblio.Class.JournalArticle.value}"
                    )
                  )
                )
              )
            )
          )
        }
      ),
      suite("subclass relation querying")(
        test("should find direct subclass (rdfs:subClassOf)") {
          val query = s"""
                         |PREFIX rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                         |PREFIX rdfs:   <http://www.w3.org/2000/01/rdf-schema#>
                         |PREFIX owl:    <http://www.w3.org/2002/07/owl#>
                         |
                         |SELECT ?entity
                         |WHERE {
                         |  ?entity    rdf:type          ?type .
                         |  ?type      rdfs:subClassOf  <${Biblio.Class.Publication.value}> .
                         |}
                         |""".stripMargin
          for {
            result <- TriplestoreService.sparqlHttpSelect(query)
          } yield assert(result.results.bindings.flatMap(_.rowMap.get("entity")))(
            hasSameElements(List("http://anArticle"))
          )
        },
        test("should find all subclasses (rdfs:subClassOf*)") {
          val query = s"""
                         |PREFIX rdf:    <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
                         |PREFIX rdfs:   <http://www.w3.org/2000/01/rdf-schema#>
                         |PREFIX owl:    <http://www.w3.org/2002/07/owl#>
                         |
                         |SELECT ?entity
                         |WHERE {
                         |  ?entity    rdf:type          ?type .
                         |  ?type      rdfs:subClassOf*  <${Biblio.Class.Publication.value}> .
                         |}
                         |""".stripMargin
          for {
            result <- TriplestoreService.sparqlHttpSelect(query)
          } yield assert(result.results.bindings.flatMap(_.rowMap.get("entity")))(
            hasSameElements(List("http://anArticle", "http://aJournalArticle"))
          )
        }
      )
    ).provide(TriplestoreServiceFake.layer, datasetLayerFromTurtle(testDataSet))
}
