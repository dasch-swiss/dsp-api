/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import zio.Scope
import zio.test.Gen
import zio.test.Spec
import zio.test.TestEnvironment
import zio.test.ZIOSpecDefault
import zio.test.assertTrue
import zio.test.checkAll

object ApacheLuceneSupportSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment & Scope, Any] =
    suite("The ApacheLuceneSupport class")(
      test("leave a Lucene query unchanged") {
        val searchString             = "Reise Land"
        val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

        assertTrue(searchExpression == "Reise Land")
      },
      test("leave a Lucene query unchanged (2)") {
        val searchString             = "Reise ins Land"
        val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

        assertTrue(searchExpression == "Reise ins Land")
      },
      test("leave a Lucene query containing phrases and terms unchanged") {
        val searchString             = "\"Leonhard Euler\" Bernoulli"
        val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

        assertTrue(searchExpression == "\"Leonhard Euler\" Bernoulli")
      },
      test("leave a Lucene query containing two phrases and one term unchanged") {
        val searchString             = "\"Leonhard Euler\" \"Daniel Bernoulli\" formula"
        val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

        assertTrue(searchExpression == "\"Leonhard Euler\" \"Daniel Bernoulli\" formula")
      },
      test("leave a Lucene query containing two phrases and two terms unchanged") {
        val searchString             = "\"Leonhard Euler\" \"Daniel Bernoulli\" formula geometria"
        val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

        assertTrue(searchExpression == "\"Leonhard Euler\" \"Daniel Bernoulli\" formula geometria")
      },
      test("get terms contained in  a Lucene query") {
        val searchString             = "Reise Land"
        val singleTerms: Seq[String] = ApacheLuceneSupport.LuceneQueryString(searchString).getSingleTerms

        assertTrue(singleTerms.size == 2)
      },
      test("handle one phrase correctly") {
        val searchString             = "\"Leonhard Euler\""
        val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

        assertTrue(searchExpression == "\"Leonhard Euler\"")
      },
      test("asLuceneQueryForSearchByLabel - Build query with support special characters in Lucene queries") {
        val checkThese = Gen.fromIterable(
          Seq( // (searchTerm, expected)
            ("""HasBackslash\2010""", """HasBackslash\\2010*"""),
            ("""HasPlus+Sign""", """HasPlus\+Sign*"""),
            ("""HasMinus-Sign""", """HasMinus\-Sign*"""),
            ("""HasExclamation!Sign""", """HasExclamation\!Sign*"""),
            ("""HasParenthesis(Sign)""", """HasParenthesis\(Sign\)*"""),
            ("""HasColon:2010""", """HasColon\:2010*"""),
            ("""HasCaret^Sign""", """HasCaret\^Sign*"""),
            ("""HasBracket[Sign]""", """HasBracket\[Sign\]*"""),
            ("""HasDoubleQuote"2010""", """HasDoubleQuote\"2010*"""),
            ("""HasBrace{Sign}""", """HasBrace\{Sign\}*"""),
            ("""HasTilde~Sign""", """HasTilde\~Sign*"""),
            ("""HasAsterisk*Sign""", """HasAsterisk\*Sign*"""),
            ("""HasQuestion?Mark""", """HasQuestion\?Mark*"""),
            ("""HasOr|Sign""", """HasOr\|Sign*"""),
            ("""HasAnd&Sign""", """HasAnd\&Sign*"""),
            ("""HasSlash/2010""", """HasSlash\/2010*"""),
            /// we do not support + or - lucene operators at the moment, so we escape them
            ("""+HasPlus Sign""", """\+HasPlus AND Sign*"""),
            ("""-HasMinus Sign""", """\-HasMinus AND Sign*"""),
            // Some examples of characters which do not need to be escaped
            ("""HasDot.2010""", """HasDot.2010*"""),
            ("""HasSingleQuote'2010""", """HasSingleQuote'2010*"""),
            ("""HasSemicolon;2010""", """HasSemicolon;2010*"""),
            ("Reis", "Reis*"),
            ("Reise ins Heilige Lan", "Reise AND ins AND Heilige AND Lan*"),
            ("öäüÖÄÜ", "öäüÖÄÜ*"),
            ("Café Bonaparte", "Café AND Bonaparte*"),
          ),
        )
        checkAll(checkThese) { (term, expected) =>
          ApacheLuceneSupport
            .asLuceneQueryForSearchByLabel(term)
            .map(_.value)
            .map(actual => assertTrue(actual == expected))
        }
      },
    )
}
