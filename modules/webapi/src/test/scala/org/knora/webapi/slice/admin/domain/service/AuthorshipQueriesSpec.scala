/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import org.apache.jena.query.QueryFactory
import org.junit.runner.RunWith
import zio.test.*

import org.knora.sparqlbuilder.Iri
import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.slice.api.PageAndSize
import org.knora.webapi.slice.api.admin.model.FilterAndOrder
import org.knora.webapi.slice.api.admin.model.Order

/**
 * Pins the rendered SPARQL of [[AuthorshipQueries]] against the output of the string-built
 * predecessor. The `expected` strings below are the verbatim output of the previous
 * implementation, compared after canonicalisation by Jena.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class AuthorshipQueriesSpec extends ZIOSpecDefault {

  private def canonical(query: String): String = {
    val parsed = QueryFactory.create(query)
    parsed.getPrefixMapping.clearNsPrefixMap()
    parsed.toString
  }

  private val graph = Iri.unsafeFrom("http://www.knora.org/data/0001/anything")

  override def spec: Spec[Any, Nothing] = suite("AuthorshipQueries")(
    test("authorships without a filter, ascending, first page") {
      val actual = AuthorshipQueries
        .authorships(graph, PageAndSize(1, 25), FilterAndOrder(None, Order.Asc))
        .sparql
      val expected =
        """|SELECT DISTINCT ?author WHERE {
           |  GRAPH <http://www.knora.org/data/0001/anything> {
           |  ?fileValue <http://www.knora.org/ontology/knora-base#hasAuthorship> ?author .
           |
           |}
           |} ORDER BY ASC(?author) LIMIT 25 OFFSET 0""".stripMargin
      assertTrue(canonical(actual) == canonical(expected))
    },
    test("authorships with a filter, ascending, first page") {
      val actual = AuthorshipQueries
        .authorships(graph, PageAndSize(1, 25), FilterAndOrder(Some("Mc Douglas"), Order.Asc))
        .sparql
      val expected =
        """|SELECT DISTINCT ?author WHERE {
           |  GRAPH <http://www.knora.org/data/0001/anything> {
           |  ?fileValue <http://www.knora.org/ontology/knora-base#hasAuthorship> ?author .
           |  FILTER(CONTAINS(LCASE(STR(?author)), "mc douglas"))
           |}
           |} ORDER BY ASC(?author) LIMIT 25 OFFSET 0""".stripMargin
      assertTrue(canonical(actual) == canonical(expected))
    },
    test("authorships with a filter, descending, third page") {
      val actual = AuthorshipQueries
        .authorships(graph, PageAndSize(3, 10), FilterAndOrder(Some("Mc Douglas"), Order.Desc))
        .sparql
      val expected =
        """|SELECT DISTINCT ?author WHERE {
           |  GRAPH <http://www.knora.org/data/0001/anything> {
           |  ?fileValue <http://www.knora.org/ontology/knora-base#hasAuthorship> ?author .
           |  FILTER(CONTAINS(LCASE(STR(?author)), "mc douglas"))
           |}
           |} ORDER BY DESC(?author) LIMIT 10 OFFSET 20""".stripMargin
      assertTrue(canonical(actual) == canonical(expected))
    },
    test("authorships without a filter, descending, first page") {
      val actual = AuthorshipQueries
        .authorships(graph, PageAndSize(1, 25), FilterAndOrder(None, Order.Desc))
        .sparql
      val expected =
        """|SELECT DISTINCT ?author WHERE {
           |  GRAPH <http://www.knora.org/data/0001/anything> {
           |  ?fileValue <http://www.knora.org/ontology/knora-base#hasAuthorship> ?author .
           |
           |}
           |} ORDER BY DESC(?author) LIMIT 25 OFFSET 0""".stripMargin
      assertTrue(canonical(actual) == canonical(expected))
    },
    test("count without a filter") {
      val actual   = AuthorshipQueries.count(graph, FilterAndOrder(None, Order.Asc)).sparql
      val expected =
        """|SELECT (COUNT(DISTINCT ?author) AS ?count) WHERE {
           |  GRAPH <http://www.knora.org/data/0001/anything> {
           |  ?fileValue <http://www.knora.org/ontology/knora-base#hasAuthorship> ?author .
           |
           |}
           |}""".stripMargin
      assertTrue(canonical(actual) == canonical(expected))
    },
    test("count with a filter") {
      val actual   = AuthorshipQueries.count(graph, FilterAndOrder(Some("Mc Douglas"), Order.Asc)).sparql
      val expected =
        """|SELECT (COUNT(DISTINCT ?author) AS ?count) WHERE {
           |  GRAPH <http://www.knora.org/data/0001/anything> {
           |  ?fileValue <http://www.knora.org/ontology/knora-base#hasAuthorship> ?author .
           |  FILTER(CONTAINS(LCASE(STR(?author)), "mc douglas"))
           |}
           |}""".stripMargin
      assertTrue(canonical(actual) == canonical(expected))
    },
  )
}
