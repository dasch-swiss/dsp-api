/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

@RunWith(classOf[DspZTestJUnitRunner])
class FulltextSearchTermsSpec extends ZIOSpecDefault {

  private val minLength               = 3
  private def tooShort(query: String) = FulltextSearchTerms.tooShortWildcardTerms(LuceneQueryString(query), minLength)

  override def spec: Spec[TestEnvironment, Any] = suite("FulltextSearchTerms.tooShortWildcardTerms")(
    test("rejects a wildcard term with fewer than 3 literal characters") {
      assertTrue(tooShort("de*") == Seq("de*"))
    },
    test("admits a wildcard term with 3 or more literal characters") {
      assertTrue(tooShort("Alice*").isEmpty)
    },
    test("rejects a short wildcard term even when a long plain term follows it (Lucene ORs terms)") {
      assertTrue(tooShort("de* Alice") == Seq("de*"))
    },
    test("admits an advertised boolean OR chain (OR is two characters but carries no wildcard)") {
      assertTrue(tooShort("Alice OR Wonderland").isEmpty)
    },
    test("admits a quoted phrase without splitting it on spaces") {
      assertTrue(tooShort(""""down the rabbit hole"""").isEmpty)
    },
    test("counts a single-character wildcard's literal characters, admitting Unif?rm") {
      assertTrue(tooShort("Unif?rm").isEmpty)
    },
    test("admits a short plain term, which the whole-string minimum governs, not this rule") {
      assertTrue(tooShort("is Alice").isEmpty)
    },
    test("does not probe a single plain term") {
      assertTrue(!FulltextSearchTerms.shouldProbe(LuceneQueryString("der")))
    },
    test("does not probe a single quoted phrase") {
      assertTrue(!FulltextSearchTerms.shouldProbe(LuceneQueryString(""""down the rabbit hole"""")))
    },
    test("probes a wildcard term") {
      assertTrue(FulltextSearchTerms.shouldProbe(LuceneQueryString("der*")))
    },
    test("probes a multi-term query") {
      assertTrue(FulltextSearchTerms.shouldProbe(LuceneQueryString("der und")))
    },
  )
}
