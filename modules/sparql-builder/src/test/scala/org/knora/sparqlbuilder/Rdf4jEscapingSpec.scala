/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf
import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

/**
 * Escaping parity with RDF4J.
 *
 * RDF4J's SparqlBuilder is the incumbent query-generation library in dsp-api, so its string
 * escaping (`Rdf.literalOf(v).getQueryString`) is the oracle this library's escaping must
 * match **byte for byte**. That parity is what makes the migration verifiable: a query
 * ported from RDF4J SparqlBuilder to `sparql"..."` templates must render identical literals.
 *
 * RDF4J is a test-only dependency of this module — the main sources implement the same
 * escape set (the full SPARQL `ECHAR` production) without depending on it.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class Rdf4jEscapingSpec extends ZIOSpecDefault {

  /** The oracle: a string literal escaped and rendered by RDF4J. */
  def rdf4jStringLiteral(value: String): String =
    Rdf.literalOf(value).getQueryString

  /** The implementation under test: a string literal escaped and rendered by this library. */
  def customStringLiteral(value: String): String =
    Literal.string(value).render

  private def parity(name: String, value: String) =
    test(name) {
      assertTrue(customStringLiteral(value) == rdf4jStringLiteral(value))
    }

  override def spec = suite("Escaping parity with RDF4J")(
    parity("simple string", "hello world"),
    parity("empty string", ""),
    parity("double quotes", """She said "hello""""),
    parity("single quotes", "it's a test"),
    parity("backslashes", """path\to\file"""),
    parity("newlines and carriage returns", "line1\nline2\rline3"),
    parity("tabs", "col1\tcol2"),
    parity("form feed", "before\fafter"),
    parity("backspace", "before\bafter"),
    parity("SPARQL injection payload", """value" . ?s ?p ?o . # """),
    parity("Lucene injection payload", """test AND secret:* OR "admin""""),
    parity("all ECHAR characters at once", "\\ \" ' \t \b \n \r \f"),
    parity("unicode outside the ECHAR set is untouched", "Zürich — çöğüşi ✓"),
    test("escaped output round-trips the ECHAR set") {
      val rendered = customStringLiteral("\\ \" ' \t \b \n \r \f")
      assertTrue(
        rendered.contains("\\\\"),
        rendered.contains("\\\""),
        rendered.contains("\\'"),
        rendered.contains("\\t"),
        rendered.contains("\\b"),
        rendered.contains("\\n"),
        rendered.contains("\\r"),
        rendered.contains("\\f"),
      )
    },
  )
}
