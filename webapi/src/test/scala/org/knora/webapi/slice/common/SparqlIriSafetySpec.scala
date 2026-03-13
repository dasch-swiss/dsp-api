/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.common

import zio.test.*

object SparqlIriSafetySpec extends ZIOSpecDefault {

  override def spec: Spec[TestEnvironment, Any] = suite("SparqlIriSafety")(
    suite("isSparqlIriRefUnsafe")(
      test("safe IRI returns false") {
        assertTrue(!SparqlIriSafety.isSparqlIriRefUnsafe("http://schema.org/Thing"))
      },
      test("IRI with '|' returns true (C1 fix)") {
        assertTrue(SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/Thing|x"))
      },
      test("IRI with '{' returns true") {
        assertTrue(SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/{injection}"))
      },
      test("IRI with '}' returns true") {
        assertTrue(SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/x}y"))
      },
      test("IRI with '\"' returns true") {
        assertTrue(SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/x\"y"))
      },
      test("IRI with '<' returns true") {
        assertTrue(SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/x<y"))
      },
      test("IRI with '>' returns true") {
        assertTrue(SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/x>y"))
      },
      test("IRI with '\\' returns true") {
        assertTrue(SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/x\\y"))
      },
      test("IRI with '^' returns true") {
        assertTrue(SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/x^y"))
      },
      test("IRI with '`' returns true") {
        assertTrue(SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/x`y"))
      },
      test("IRI with space (U+0020) returns true — control char boundary (W1 fix)") {
        assertTrue(SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/x y"))
      },
      test("IRI with NUL (U+0000) returns true (W1 fix)") {
        assertTrue(SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/\u0000x"))
      },
      test("IRI with SOH (U+0001) returns true (W1 fix)") {
        assertTrue(SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/\u0001x"))
      },
      test("IRI with TAB (U+0009) returns true (W1 fix)") {
        assertTrue(SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/\tx"))
      },
      test("IRI with LF (U+000A) returns true (W1 fix)") {
        assertTrue(SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/\nx"))
      },
      test("IRI with CR (U+000D) returns true (W1 fix)") {
        assertTrue(SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/\rx"))
      },
      test("percent-encoded '|' (%7C) is safe — encoding is not a forbidden char") {
        assertTrue(!SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/Thing%7Cx"))
      },
      test("percent-encoded '}' (%7D) is safe") {
        assertTrue(!SparqlIriSafety.isSparqlIriRefUnsafe("http://example.org/Thing%7Dx"))
      },
    ),
    suite("findForbiddenChar")(
      test("returns None for a safe IRI") {
        assertTrue(SparqlIriSafety.findForbiddenChar("http://schema.org/Thing").isEmpty)
      },
      test("returns the '|' character") {
        assertTrue(SparqlIriSafety.findForbiddenChar("http://example.org/x|y").contains('|'))
      },
      test("returns the control character") {
        assertTrue(SparqlIriSafety.findForbiddenChar("http://example.org/\u0001x").contains('\u0001'))
      },
      test("returns space as the forbidden char") {
        assertTrue(SparqlIriSafety.findForbiddenChar("http://example.org/x y").contains(' '))
      },
    ),
  )
}
