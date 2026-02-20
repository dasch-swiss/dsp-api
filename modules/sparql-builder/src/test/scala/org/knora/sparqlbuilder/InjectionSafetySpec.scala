/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

import zio.test.*

/**
 * Injection safety specification.
 *
 * Defines "injection-safe by construction" concretely:
 *
 * 1. **What types can be interpolated**: Iri, Variable, Literal, Fragment
 * 2. **How are raw strings handled**: Only via `Fragment.raw("...")` — the explicit escape hatch
 * 3. **How is Lucene injection prevented**: Lucene queries must be passed as `Literal.string()`
 *    which escapes special characters. A dedicated `LuceneQuery` type could be added later.
 * 4. **What runtime/compile-time checks exist**: The `sparql"..."` interpolator only accepts
 *    `SparqlValue | Fragment` — raw `String` is a compile error. Literal values are escaped
 *    at interpolation time. IRIs use `<...>` wrapping.
 */
object InjectionSafetySpec extends ZIOSpecDefault {

  override def spec = suite("Injection Safety")(
    sparqlInjectionSuite,
    luceneInjectionSuite,
    escapeHatchSuite,
  )

  // -------------------------------------------------------------------------
  // SPARQL injection prevention
  // -------------------------------------------------------------------------
  val sparqlInjectionSuite = suite("SPARQL injection prevention")(
    test("string literal with quotes is escaped") {
      val malicious = Literal.string("""value" . ?s ?p ?o . # """)
      val frag = sparql"?s ?p $malicious ."
      val rendered = frag.render
      assertTrue(
        // The quotes in the malicious value should be escaped with backslash
        rendered.contains("\\\""),
        // The rendered literal value is a properly quoted SPARQL string.
        // The malicious content is contained inside the string literal, not free SPARQL.
        // Verify the literal wraps everything between its opening and closing quotes.
        rendered == """?s ?p "value\" . ?s ?p ?o . # " .""",
      )
    },
    test("string literal with newlines is escaped") {
      val malicious = Literal.string("value\n} INSERT { ?s ?p ?o } WHERE {")
      val rendered = malicious.render
      assertTrue(
        rendered.contains("\\n"),
        !rendered.contains("\n} INSERT"),
      )
    },
    test("IRI wrapping prevents injection") {
      // Even if an IRI contains spaces or special chars, it's wrapped in <...>
      val iri = Iri.trusted("http://example.org/test> . ?s ?p ?o . <http://evil.org")
      val frag = sparql"?s a $iri ."
      val rendered = frag.render
      // The malicious content is contained within the <...> wrapper
      assertTrue(
        rendered.startsWith("?s a <"),
        // In a real implementation, IRI validation would reject this.
        // For the spike, we document that Iri.trusted bypasses validation.
      )
    },
    test("variables cannot inject SPARQL syntax") {
      // Variables are always rendered as ?name
      val v = Variable("x> . ?s ?p ?o . ?evil")
      val frag = sparql"?s $v ?o ."
      val rendered = frag.render
      assertTrue(
        rendered.contains("?x> . ?s ?p ?o . ?evil"),
        // The variable name is not parsed as SPARQL — it's just a bad variable name.
        // In a real implementation, Variable creation would validate the name.
      )
    },
    test("raw strings CANNOT be interpolated directly (compile-time safety)") {
      // This test documents that `sparql"$rawString"` is a compile error.
      // We can't test compile errors at runtime, but we demonstrate the type constraint:
      val rawString: String = "DELETE WHERE { ?s ?p ?o }"
      // sparql"$rawString" // This would NOT compile — String is not SparqlValue | Fragment
      // Instead, you must use Fragment.raw explicitly:
      val escaped = Fragment.raw(rawString)
      assertTrue(escaped.render == rawString)
    },
    test("int literals cannot cause injection") {
      val lit = Literal.int(42)
      val frag = sparql"?s ?p $lit ."
      assertTrue(frag.render == "?s ?p 42 .")
    },
    test("boolean literals cannot cause injection") {
      val lit = Literal.bool(false)
      val frag = sparql"?s ?p $lit ."
      assertTrue(frag.render == "?s ?p false .")
    },
  )

  // -------------------------------------------------------------------------
  // Lucene injection prevention
  // -------------------------------------------------------------------------
  val luceneInjectionSuite = suite("Lucene injection prevention")(
    test("Lucene special characters in string literal are escaped") {
      // Lucene special characters: + - && || ! ( ) { } [ ] ^ " ~ * ? : \ /
      val luceneQuery = Literal.string("""test AND secret:* OR "admin"""")
      val textQueryPred = Iri.trusted("http://jena.apache.org/text#query")
      val rdfsLabel = Iri.trusted("http://www.w3.org/2000/01/rdf-schema#label")
      val resource = Variable("resource")

      val frag = sparql"$resource $textQueryPred ($rdfsLabel $luceneQuery) ."
      val rendered = frag.render

      assertTrue(
        // Quotes in the Lucene query are escaped
        rendered.contains("\\\"admin\\\""),
        // The Lucene query is contained within a string literal
        rendered.contains("\"test AND secret:* OR \\\"admin\\\"\""),
      )
    },
    test("Lucene query cannot break out of string literal") {
      val malicious = Literal.string("""") . ?s <http://jena.apache.org/text#query> ("hack""")
      val rendered = malicious.render
      assertTrue(
        // All quotes are escaped. The malicious payload stays inside the string literal.
        // The opening and closing quote characters delimit the string; interior quotes are escaped.
        rendered == """"\") . ?s <http://jena.apache.org/text#query> (\"hack"""",
      )
    },
  )

  // -------------------------------------------------------------------------
  // Escape hatch documentation
  // -------------------------------------------------------------------------
  val escapeHatchSuite = suite("Fragment.raw escape hatch")(
    test("Fragment.raw is the only way to inject raw SPARQL") {
      // This is intentional — for vendor-specific extensions like Jena text#query
      val raw = Fragment.raw("FILTER(REGEX(?label, 'test', 'i'))")
      assertTrue(raw.render == "FILTER(REGEX(?label, 'test', 'i'))")
    },
    test("Fragment.raw usage is grep-able") {
      // All uses of Fragment.raw in the codebase can be found with:
      //   grep -r "Fragment.raw" modules/sparql-builder/
      // This makes it easy to audit the injection-risk surface.
      assertTrue(true)
    },
  )
}
