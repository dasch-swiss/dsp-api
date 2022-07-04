/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import org.knora.webapi._
import zio.test.ZIOSpecDefault
import zio._
import zio.test._

object ApacheLuceneSupportZSpec extends ZIOSpecDefault {

  def spec: Spec[TestEnvironment with Scope, Any] =
    suite("The ApacheLuceneSupport class")(
      test("leave a Lucene query unchanged") {

        val searchString             = "Reise Land"
        val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

        assertTrue(searchExpression == "Reise Land")
      } +

        test("leave a Lucene query unchanged (2)") {

          val searchString             = "Reise ins Land"
          val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

          assertTrue(searchExpression == "Reise ins Land")
        } +

        test("leave a Lucene query containing phrases and terms unchanged") {

          val searchString             = "\"Leonhard Euler\" Bernoulli"
          val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

          assertTrue(searchExpression == "\"Leonhard Euler\" Bernoulli")

        } +

        test("leave a Lucene query containing two phrases and one term unchanged") {

          val searchString             = "\"Leonhard Euler\" \"Daniel Bernoulli\" formula"
          val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

          assertTrue(searchExpression == "\"Leonhard Euler\" \"Daniel Bernoulli\" formula")

        } +

        test("leave a Lucene query containing two phrases and two terms unchanged") {

          val searchString             = "\"Leonhard Euler\" \"Daniel Bernoulli\" formula geometria"
          val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

          assertTrue(searchExpression == "\"Leonhard Euler\" \"Daniel Bernoulli\" formula geometria")

        } +

        test("get terms contained in  a Lucene query") {

          val searchString             = "Reise Land"
          val singleTerms: Seq[String] = ApacheLuceneSupport.LuceneQueryString(searchString).getSingleTerms

          assertTrue(singleTerms.size == 2)

        } +

        test("handle one phrase correctly") {

          val searchString             = "\"Leonhard Euler\""
          val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

          assertTrue(searchExpression == "\"Leonhard Euler\"")

        } +

        test(
          "combine space separated words with a logical AND and add a wildcard to the last word (non exact sequence)"
        ) {

          val searchString = "Reise ins Heilige Lan"
          val searchExpression =
            ApacheLuceneSupport.MatchStringWhileTyping(searchString).generateLiteralForLuceneIndexWithoutExactSequence

          assertTrue(searchExpression == "Reise AND ins AND Heilige AND Lan*")

        } +

        test("add a wildcard to the word if the search string only contains one word (non exact sequence)") {

          val searchString = "Reis"
          val searchExpression =
            ApacheLuceneSupport.MatchStringWhileTyping(searchString).generateLiteralForLuceneIndexWithoutExactSequence

          assertTrue(searchExpression == "Reis*")

        } +

        test(
          "combine all space separated words to a phrase but the last one and add a wildcard to it (exact sequence)"
        ) {

          val searchString = "Reise ins Heilige Lan"
          val searchExpression =
            ApacheLuceneSupport.MatchStringWhileTyping(searchString).generateLiteralForLuceneIndexWithExactSequence

          assertTrue(searchExpression == """"Reise ins Heilige" AND Lan*""")

        } +

        test("add a wildcard to the word if the search string only contains one word (exact sequence)") {

          val searchString = "Reis"
          val searchExpression =
            ApacheLuceneSupport.MatchStringWhileTyping(searchString).generateLiteralForLuceneIndexWithExactSequence

          assertTrue(searchExpression == "Reis*")

        } +

        test("create a regex FILTER expression for an exact match") {

          val searchString = "Reise ins Heilige Lan"
          val searchExpression = ApacheLuceneSupport
            .MatchStringWhileTyping(searchString)
            .generateRegexFilterStatementForExactSequenceMatch("firstProp")

          assertTrue(searchExpression == "FILTER regex(?firstProp, 'Reise ins Heilige Lan*', 'i')")

        } +

        test("not create a regex FILTER expression for an exact match when only one word is provided") {

          val searchString = "Reise"
          val searchExpression = ApacheLuceneSupport
            .MatchStringWhileTyping(searchString)
            .generateRegexFilterStatementForExactSequenceMatch("firstProp")

          assertTrue(searchExpression == "")

        }
    )
}
