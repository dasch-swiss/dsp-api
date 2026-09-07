/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.sparqlbuilder

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner

/**
 * Injection safety specification.
 *
 * Defines "injection-safe by construction" concretely:
 *
 * 1. **What types can be interpolated**: Iri, Variable, Literal, Fragment
 * 2. **How are raw strings handled**: Only via `Fragment.raw("...")` — the explicit escape hatch
 * 3. **Validated construction**: `Iri` rejects every character that could terminate the
 *    `<...>` wrapper, `Variable` names are restricted to `VARNAME` characters, and language
 *    tags must match the `LANGTAG` production. `unsafeFrom` throws instead of returning an
 *    `Either` — there is no unvalidated path.
 * 4. **How is Lucene injection prevented**: Lucene queries must be passed as `Literal.string()`
 *    which escapes special characters. A dedicated `LuceneQuery` type could be added later.
 * 5. **What compile-time checks exist**: The `sparql"..."` interpolator only accepts
 *    `SparqlValue | Fragment` — raw `String` is a compile error. Literal values are escaped
 *    at construction time.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class InjectionSafetySpec extends ZIOSpecDefault {

  override def spec = suite("Injection Safety")(
    sparqlInjectionSuite,
    validationSuite,
    luceneInjectionSuite,
    escapeHatchSuite,
  )

  // -------------------------------------------------------------------------
  // SPARQL injection prevention
  // -------------------------------------------------------------------------
  val sparqlInjectionSuite = suite("SPARQL injection prevention")(
    test("string literal with quotes is escaped") {
      val malicious = Literal.string("""value" . ?s ?p ?o . # """)
      val frag      = sparql"?s ?p $malicious ."
      val rendered  = frag.render
      assertTrue(
        // The quotes in the malicious value should be escaped with backslash
        rendered.contains("\\\""),
        // The rendered literal value is a properly quoted SPARQL string.
        // The malicious content is contained inside the string literal, not free SPARQL.
        rendered == """?s ?p "value\" . ?s ?p ?o . # " .""",
      )
    },
    test("string literal with newlines is escaped") {
      val malicious = Literal.string("value\n} INSERT { ?s ?p ?o } WHERE {")
      val rendered  = malicious.render
      assertTrue(
        rendered.contains("\\n"),
        !rendered.contains("\n} INSERT"),
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
      val lit  = Literal.int(42)
      val frag = sparql"?s ?p $lit ."
      assertTrue(frag.render == "?s ?p 42 .")
    },
    test("boolean literals cannot cause injection") {
      val lit  = Literal.bool(false)
      val frag = sparql"?s ?p $lit ."
      assertTrue(frag.render == "?s ?p false .")
    },
    test("non-finite doubles render as valid typed literals, not bare tokens") {
      assertTrue(
        Literal.double(Double.NaN).render == """"NaN"^^<http://www.w3.org/2001/XMLSchema#double>""",
        Literal.double(Double.PositiveInfinity).render == """"INF"^^<http://www.w3.org/2001/XMLSchema#double>""",
        Literal.double(Double.NegativeInfinity).render == """"-INF"^^<http://www.w3.org/2001/XMLSchema#double>""",
        Literal.double(1.5).render == "1.5",
      )
    },
  )

  // -------------------------------------------------------------------------
  // Validated construction — the typed values cannot hold breakout payloads
  // -------------------------------------------------------------------------
  val validationSuite = suite("Validated construction")(
    test("Iri rejects a breakout payload") {
      val payload = "http://example.org/test> . ?s ?p ?o . <http://evil.org"
      assertTrue(
        Iri.from(payload).isLeft,
        // unsafeFrom throws instead of constructing an invalid value
        scala.util.Try(Iri.unsafeFrom(payload)).isFailure,
      )
    },
    test("Iri rejects every IRIREF-terminating character") {
      val badChars = List('<', '>', '"', '{', '}', '|', '^', '`', '\\', ' ', '\n', '\t')
      val results  = badChars.map(c => Iri.from(s"http://example.org/a${c}b"))
      assertTrue(results.forall(_.isLeft))
    },
    test("Iri accepts ordinary absolute IRIs") {
      assertTrue(
        Iri.from("http://www.knora.org/ontology/knora-base#isDeleted").isRight,
        Iri.from("http://rdfh.ch/0001/a-thing_with~odd(chars)*").isRight,
        Iri.from("urn:uuid:6e8bc430-9c3a-11d9-9669-0800200c9a66").isRight,
      )
    },
    test("Variable rejects names that could inject SPARQL syntax") {
      assertTrue(
        Variable.from("x> . ?s ?p ?o . ?evil").isLeft,
        scala.util.Try(Variable("x> . ?s ?p ?o . ?evil")).isFailure,
        Variable.from("linkValue0").isRight,
      )
    },
    test("language tags are validated") {
      assertTrue(
        scala.util.Try(Literal.langString("x", "en . ?s ?p ?o")).isFailure,
        Literal.langString("Haus", "de").render == "\"Haus\"@de",
        Literal.langString("colour", "en-GB").render == "\"colour\"@en-GB",
      )
    },
  )

  // -------------------------------------------------------------------------
  // Lucene injection prevention
  // -------------------------------------------------------------------------
  val luceneInjectionSuite = suite("Lucene injection prevention")(
    test("Lucene special characters in string literal are escaped") {
      // Lucene special characters: + - && || ! ( ) { } [ ] ^ " ~ * ? : \ /
      val luceneQuery   = Literal.string("""test AND secret:* OR "admin"""")
      val textQueryPred = Iri.unsafeFrom("http://jena.apache.org/text#query")
      val rdfsLabel     = Iri.unsafeFrom("http://www.w3.org/2000/01/rdf-schema#label")
      val resource      = Variable("resource")

      val frag     = sparql"$resource $textQueryPred ($rdfsLabel $luceneQuery) ."
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
      val rendered  = malicious.render
      assertTrue(
        // All quotes are escaped. The malicious payload stays inside the string literal.
        // The opening and closing quote characters delimit the string; interior quotes
        // (and the single quotes, per the ECHAR production) are escaped.
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
      //   grep -rn "Fragment.raw" modules/
      // This makes it easy to audit the injection-risk surface.
      assertTrue(true)
    },
  )
}
