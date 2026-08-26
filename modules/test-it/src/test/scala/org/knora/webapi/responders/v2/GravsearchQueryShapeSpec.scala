/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.responders.v2

import org.junit.runner.RunWith
import zio.Scope
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.responders.v2.SearchResponderV2.QueryResultType

@RunWith(classOf[DspZTestJUnitRunner])
class GravsearchQueryShapeSpec extends ZIOSpecDefault {

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

  private def shortcodesOf(query: String) =
    SearchResponderV2.projectShortcodes(GravsearchParser.parseQuery(query))

  override def spec: Spec[TestEnvironment & Scope, Any] =
    suite("SearchResponderV2 query derivations")(queryShapeSuite, projectShortcodesSuite)

  private def queryShapeSuite =
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
      test("schema_predicates contains ontology predicate names only, never an instance IRI in predicate position") {
        // A parseable query whose predicate position holds an instance (data) IRI — shape is derived right
        // after parse, before type inspection would reject it, so the derivation itself must exclude it.
        val query =
          """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
            |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |CONSTRUCT {
            |    ?book knora-api:isMainResource true .
            |} WHERE {
            |    ?book a incunabula:book .
            |    ?book a knora-api:Resource .
            |    ?book incunabula:title ?title .
            |    ?book <http://rdfh.ch/0803/SECRETINSTANCEID> ?x .
            |}""".stripMargin
        val shape = shapeOf(query, QueryResultType.ResourceList)
        assertTrue(
          shape.predicates.contains("title"),
          !shape.predicates.exists(_.contains("SECRETINSTANCEID")),
        )
      },
    )

  private def projectShortcodesSuite =
    suite("SearchResponderV2.projectShortcodes")(
      test("a single-project query reports that project's shortcode") {
        assertTrue(shortcodesOf(bookQueryWithTitleFilter("anything")) == Seq("0803"))
      },
      test("a cross-project query reports every project it references, sorted and de-duplicated") {
        // Gravsearch can span projects, so the honest shape is a set — not one "primary" project.
        val query =
          """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
            |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
            |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |CONSTRUCT {
            |    ?book knora-api:isMainResource true .
            |    ?book incunabula:title ?title .
            |} WHERE {
            |    ?book a incunabula:book .
            |    ?book a knora-api:Resource .
            |    ?book incunabula:title ?title .
            |    ?thing a anything:Thing .
            |    ?thing anything:hasText ?text .
            |}""".stripMargin
        assertTrue(shortcodesOf(query) == Seq("0001", "0803"))
      },
      test("a query over built-in ontologies only reports no project") {
        // knora-api / salsah-gui carry no project code, so they drop out and the attribute is empty.
        // Empty is a real answer here ("no project identifiable"), distinct from the attribute being absent.
        val query =
          """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |CONSTRUCT {
            |    ?region knora-api:isMainResource true .
            |} WHERE {
            |    ?region a knora-api:Region .
            |    ?region a knora-api:Resource .
            |    ?region knora-api:hasGeometry ?geom .
            |}""".stripMargin
        assertTrue(shortcodesOf(query).isEmpty)
      },
      test("an internally generated search reports the project of the interpolated resource IRI") {
        // The shape of `searchIncomingLinksV2` and friends: built-in ontologies only, with the target
        // resource IRI interpolated in. Reading ontology IRIs alone would leave every such span blank,
        // which is why the derivation reads data IRIs too.
        val query =
          """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |CONSTRUCT {
            |    ?incomingRes knora-api:isMainResource true .
            |    ?incomingRes ?incomingProp <http://rdfh.ch/0803/c5058f3a> .
            |} WHERE {
            |    ?incomingRes a knora-api:Resource .
            |    ?incomingRes ?incomingProp <http://rdfh.ch/0803/c5058f3a> .
            |    <http://rdfh.ch/0803/c5058f3a> a knora-api:Resource .
            |    ?incomingProp knora-api:objectType knora-api:Resource .
            |}""".stripMargin
        assertTrue(shortcodesOf(query) == Seq("0803"))
      },
      test("one project referenced in mixed case is reported once, normalised") {
        // The project-ID pattern is `\p{XDigit}{4}`, so either case parses. An ontology IRI's code has
        // already been through `Shortcode` (upper-cased) but a data IRI's is the raw capture, so reading
        // the raw project code would report this single project as `00FF,00ff`.
        val query =
          """PREFIX images: <http://0.0.0.0:3333/ontology/00ff/images/simple/v2#>
            |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |CONSTRUCT {
            |    ?img knora-api:isMainResource true .
            |} WHERE {
            |    ?img a images:bild .
            |    ?img a knora-api:Resource .
            |    ?img knora-api:isPartOf <http://rdfh.ch/00ff/abc123> .
            |}""".stripMargin
        assertTrue(shortcodesOf(query) == Seq("00FF"))
      },
      test("only the shortcode is reported, never the instance IRI it was taken from") {
        val query =
          """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
            |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            |CONSTRUCT {
            |    ?book knora-api:isMainResource true .
            |} WHERE {
            |    ?book a incunabula:book .
            |    ?book a knora-api:Resource .
            |    ?book knora-api:isPartOf <http://rdfh.ch/0803/SECRETINSTANCEID> .
            |}""".stripMargin
        assertTrue(
          shortcodesOf(query) == Seq("0803"),
          !shortcodesOf(query).exists(_.contains("SECRETINSTANCEID")),
        )
      },
    )
}
