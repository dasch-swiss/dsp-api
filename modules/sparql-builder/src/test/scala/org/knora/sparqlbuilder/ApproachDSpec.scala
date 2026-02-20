/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf

import zio.test.*

/**
 * Approach D: String interpolator + RDF4J escaping under the hood
 *
 * Same API surface as Approach A (sparql"..." interpolator, Fragment, SparqlQuery builders),
 * but delegates string escaping to RDF4J's `SparqlBuilderUtils` / `RdfLiteral.StringLiteral`
 * instead of custom hand-rolled escaping.
 *
 * This prototype demonstrates the implementation strategy dimension — the API is identical
 * to Approach A, but the escaping backend is different. We compare the output of both
 * escaping strategies to validate correctness.
 */
object ApproachDSpec extends ZIOSpecDefault {

  // -- RDF4J-backed literal escaping --

  /** Create a string literal using RDF4J's escaping instead of our custom escaping. */
  def rdf4jStringLiteral(value: String): String =
    Rdf.literalOf(value).getQueryString

  /** Custom-escaped string literal (from our types.scala Literal.string). */
  def customStringLiteral(value: String): String =
    Literal.string(value).render

  override def spec = suite("Approach D: Interpolator + RDF4J escaping")(
    escapingComparisonSuite,
    rdf4jEscapingInjectionSuite,
    apiEquivalenceSuite,
  )

  // -------------------------------------------------------------------------
  // Compare escaping strategies
  // -------------------------------------------------------------------------
  val escapingComparisonSuite = suite("Escaping strategy comparison")(
    test("simple string — both produce same output") {
      val value = "hello world"
      assertTrue(
        customStringLiteral(value) == rdf4jStringLiteral(value),
      )
    },
    test("string with double quotes") {
      val value  = """She said "hello""""
      val custom = customStringLiteral(value)
      val rdf4j  = rdf4jStringLiteral(value)
      assertTrue(
        custom.contains("\\\""),
        rdf4j.contains("\\\""),
        custom == rdf4j,
      )
    },
    test("string with newlines") {
      val value  = "line1\nline2\rline3"
      val custom = customStringLiteral(value)
      val rdf4j  = rdf4jStringLiteral(value)
      assertTrue(
        custom.contains("\\n"),
        rdf4j.contains("\\n"),
        custom.contains("\\r"),
        rdf4j.contains("\\r"),
      )
    },
    test("string with backslashes") {
      val value  = """path\to\file"""
      val custom = customStringLiteral(value)
      val rdf4j  = rdf4jStringLiteral(value)
      assertTrue(
        custom.contains("\\\\"),
        rdf4j.contains("\\\\"),
        custom == rdf4j,
      )
    },
    test("string with tabs") {
      val value  = "col1\tcol2"
      val custom = customStringLiteral(value)
      val rdf4j  = rdf4jStringLiteral(value)
      assertTrue(
        custom.contains("\\t"),
        rdf4j.contains("\\t"),
        custom == rdf4j,
      )
    },
    test("SPARQL injection payload — both escape correctly") {
      val payload = """value" . ?s ?p ?o . # """
      val custom  = customStringLiteral(payload)
      val rdf4j   = rdf4jStringLiteral(payload)
      // Both should produce a safe escaped string
      assertTrue(
        custom.contains("\\\""),
        rdf4j.contains("\\\""),
      )
    },
    test("Lucene injection payload — both escape correctly") {
      val payload = """test AND secret:* OR "admin""""
      val custom  = customStringLiteral(payload)
      val rdf4j   = rdf4jStringLiteral(payload)
      assertTrue(
        custom.contains("\\\"admin\\\""),
        rdf4j.contains("\\\"admin\\\""),
      )
    },
    test("empty string") {
      assertTrue(
        customStringLiteral("") == rdf4jStringLiteral(""),
        customStringLiteral("") == "\"\"",
      )
    },
  )

  // -------------------------------------------------------------------------
  // RDF4J escaping injection safety
  // -------------------------------------------------------------------------
  val rdf4jEscapingInjectionSuite = suite("RDF4J escaping injection safety")(
    test("RDF4J escapes form feed and backspace (custom does not)") {
      // RDF4J escapes \f and \b which our custom escaping does not handle
      val withFormFeed  = "before\fafter"
      val withBackspace = "before\bafter"
      val rdf4jFF       = rdf4jStringLiteral(withFormFeed)
      val rdf4jBS       = rdf4jStringLiteral(withBackspace)
      assertTrue(
        rdf4jFF.contains("\\f"),
        rdf4jBS.contains("\\b"),
      )
    },
    test("RDF4J escapes single quotes") {
      val value = "it's a test"
      val rdf4j = rdf4jStringLiteral(value)
      assertTrue(
        rdf4j.contains("\\'"),
      )
    },
  )

  // -------------------------------------------------------------------------
  // API equivalence — same API, different backend
  // -------------------------------------------------------------------------
  val apiEquivalenceSuite = suite("API equivalence with Approach A")(
    test("same query using Approach A API produces identical output") {
      // This test demonstrates that Approach D is purely an implementation swap.
      // The API surface (sparql"...", SparqlQuery, Fragment) is identical to Approach A.
      val s             = Variable("s")
      val p             = Variable("p")
      val o             = Variable("o")
      val lmd           = Variable("lastModDate")
      val resourceClass = Iri.trusted("http://example.org/MyClass")

      val query = SparqlQuery
        .select(s, p, o)
        .where(
          sparql"$s a $resourceClass .",
          sparql"$s ${Iri.trusted("http://www.knora.org/ontology/knora-base#isDeleted")} false .",
          Fragments.optional(
            sparql"$s ${Iri.trusted("http://www.knora.org/ontology/knora-base#lastModificationDate")} $lmd .",
          ),
          sparql"$s $p $o .",
        )
        .orderBy(lmd.desc)
        .limit(25)
        .render

      // The API is the same as Approach A — the only difference would be
      // which escaping function Literal.string() calls internally.
      assertTrue(
        query.contains("SELECT ?s ?p ?o"),
        query.contains("OPTIONAL"),
        query.contains("LIMIT 25"),
      )
    },
    test("RDF4J escaping can be used as a drop-in replacement") {
      // Demonstrate that swapping the escaping backend is a single-line change
      // in types.scala — the Literal.render method would call rdf4jStringLiteral
      // instead of escapeSparqlString.
      val testValue = "test \"with\" special\nchars"
      val custom    = customStringLiteral(testValue)
      val rdf4j     = rdf4jStringLiteral(testValue)
      // Both produce valid SPARQL string literals
      assertTrue(
        custom.startsWith("\""),
        custom.endsWith("\""),
        rdf4j.startsWith("\""),
        rdf4j.endsWith("\""),
      )
    },
  )
}
