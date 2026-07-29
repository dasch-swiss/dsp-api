/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.search

import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

/**
 * Term-shape rules for the fulltext search routes (`/v2/search`, `/v2/search/count`). Both rules operate on the
 * same phrase-aware split ([[LuceneQueryString.termsAndPhrases]]) so the input validation (LITERAL-LENGTH) and the
 * breadth probe cannot disagree about what a term is (DEV-6864).
 */
object FulltextSearchTerms {

  // Lucene boolean operators advertised in dsp-app's Search tips panel. `OR` is two characters, so a per-term
  // length rule would reject it; they carry no wildcard and so are never checked, but exempt them explicitly.
  private val booleanOperators = Set("AND", "OR", "NOT")

  private val wildcardChars = Set('*', '?')

  private def hasWildcard(term: String): Boolean = term.exists(wildcardChars)

  /**
   * The wildcard terms that carry fewer than `minLength` literal (non-wildcard) characters. `de*` strips to `de`
   * (2) and is too short; `Alice*` strips to `Alice` (5) and `Unif?rm` to `Unifrm` (6) are fine. Only terms that
   * actually carry a wildcard are checked — a plain term is governed by the existing whole-string minimum, so this
   * does not newly reject e.g. `is Alice`. Boolean operators and quoted phrases carry no wildcard and pass.
   */
  def tooShortWildcardTerms(query: LuceneQueryString, minLength: Int): Seq[String] =
    query.termsAndPhrases.filter { term =>
      hasWildcard(term) &&
      !booleanOperators.contains(term) &&
      term.count(!wildcardChars.contains(_)) < minLength
    }
}
