/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import zio.Scope
import zio.test.*

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.responders.v2.SearchResponderV2.QueryResultType

/**
 * Unit tests for the `gravsearch.query.shape` derivation (Decision 4). The shape must be bounded,
 * human-readable and derived from query *structure* only — never from literal values — so that it is
 * safe to use as a metric label and never encodes user data (REQ-1.3).
 */
object GravsearchQueryShapeSpec extends ZIOSpecDefault {

  // GravsearchParser uses the process-wide StringFormatter; initialise it before parsing.
  private val _ = StringFormatter.getInitializedTestInstance

  private def bookQueryWithTitleFilter(title: String): String =
    s"""PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
       |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
       |
       |CONSTRUCT {
       |    ?book knora-api:isMainResource true .
       |    ?book incunabula:title ?title .
       |} WHERE {
       |    ?book a incunabula:book .
       |    ?book a knora-api:Resource .
       |    ?book incunabula:title ?title .
       |    incunabula:title knora-api:objectType xsd:string .
       |    ?title a xsd:string .
       |    FILTER(?title = "$title")
       |}""".stripMargin

  private def shapeOf(query: String, resultType: QueryResultType) =
    SearchResponderV2.queryShape(GravsearchParser.parseQuery(query), resultType)

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("SearchResponderV2.queryShape")(
      test("is invariant under a change to a FILTER literal (never encodes user data)") {
        val shapeA = shapeOf(
          bookQueryWithTitleFilter("Zeitglöcklein des Lebens und Leidens Christi"),
          QueryResultType.ResourceList,
        )
        val shapeB = shapeOf(bookQueryWithTitleFilter("a completely different value"), QueryResultType.ResourceList)
        assertTrue(shapeA == shapeB)
      },
      test("encodes the result type, structural flags and bucketed counts, but no literal text") {
        val shape = shapeOf(bookQueryWithTitleFilter("anything"), QueryResultType.ResourceList)
        assertTrue(
          shape.label.startsWith("resource-list"),
          shape.flags("has_filter"),
          !shape.flags("has_optional"),
          !shape.flags("has_union"),
          !shape.flags("has_order_by"),
          shape.label.contains("has_filter"),
          shape.label.contains("patterns:"),
          !shape.label.contains("anything"),
          !shape.predicates.exists(_.contains("anything")),
        )
      },
      test("the count variant uses the count result-type token") {
        val shape = shapeOf(bookQueryWithTitleFilter("anything"), QueryResultType.Count)
        assertTrue(shape.label.startsWith("count"))
      },
    )
}
