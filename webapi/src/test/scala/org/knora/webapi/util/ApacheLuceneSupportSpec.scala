/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import org.knora.webapi._

class ApacheLuceneSupportSpec extends CoreSpec() {

  "The ApacheLuceneSupport class" should {

    "leave a Lucene query unchanged" in {

      val searchString = "Reise Land"
      val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

      assert(searchExpression == "Reise Land")
    }

    "leave a Lucene query unchanged (2)" in {

      val searchString = "Reise ins Land"
      val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

      assert(searchExpression == "Reise ins Land")
    }

    "leave a Lucene query containing phrases and terms unchanged" in {

      val searchString = "\"Leonhard Euler\" Bernoulli"
      val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

      assert(searchExpression == "\"Leonhard Euler\" Bernoulli")

    }

    "leave a Lucene query containing two phrases and one term unchanged" in {

      val searchString = "\"Leonhard Euler\" \"Daniel Bernoulli\" formula"
      val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

      assert(searchExpression == "\"Leonhard Euler\" \"Daniel Bernoulli\" formula")

    }

    "leave a Lucene query containing two phrases and two terms unchanged" in {

      val searchString = "\"Leonhard Euler\" \"Daniel Bernoulli\" formula geometria"
      val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

      assert(searchExpression == "\"Leonhard Euler\" \"Daniel Bernoulli\" formula geometria")

    }

    "get terms contained in  a Lucene query" in {

      val searchString = "Reise Land"
      val singleTerms: Seq[String] = ApacheLuceneSupport.LuceneQueryString(searchString).getSingleTerms

      assert(singleTerms.size === 2)

    }

    "handle one phrase correctly" in {

      val searchString = "\"Leonhard Euler\""
      val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

      assert(searchExpression == "\"Leonhard Euler\"")

    }

    "combine space separated words with a logical AND and add a wildcard to the last word (non exact sequence)" in {

      val searchString = "Reise ins Heilige Lan"
      val searchExpression =
        ApacheLuceneSupport.MatchStringWhileTyping(searchString).generateLiteralForLuceneIndexWithoutExactSequence

      assert(searchExpression == "Reise AND ins AND Heilige AND Lan*")

    }

    "add a wildcard to the word if the search string only contains one word (non exact sequence)" in {

      val searchString = "Reis"
      val searchExpression =
        ApacheLuceneSupport.MatchStringWhileTyping(searchString).generateLiteralForLuceneIndexWithoutExactSequence

      assert(searchExpression == "Reis*")

    }

    "combine all space separated words to a phrase but the last one and add a wildcard to it (exact sequence)" in {

      val searchString = "Reise ins Heilige Lan"
      val searchExpression =
        ApacheLuceneSupport.MatchStringWhileTyping(searchString).generateLiteralForLuceneIndexWithExactSequence

      assert(searchExpression == """"Reise ins Heilige" AND Lan*""")

    }

    "add a wildcard to the word if the search string only contains one word (exact sequence)" in {

      val searchString = "Reis"
      val searchExpression =
        ApacheLuceneSupport.MatchStringWhileTyping(searchString).generateLiteralForLuceneIndexWithExactSequence

      assert(searchExpression == "Reis*")

    }

    "create a regex FILTER expression for an exact match" in {

      val searchString = "Reise ins Heilige Lan"
      val searchExpression = ApacheLuceneSupport
        .MatchStringWhileTyping(searchString)
        .generateRegexFilterStatementForExactSequenceMatch("firstProp")

      assert(searchExpression == "FILTER regex(?firstProp, 'Reise ins Heilige Lan*', 'i')")

    }

    "not create a regex FILTER expression for an exact match when only one word is provided" in {

      val searchString = "Reise"
      val searchExpression = ApacheLuceneSupport
        .MatchStringWhileTyping(searchString)
        .generateRegexFilterStatementForExactSequenceMatch("firstProp")

      assert(searchExpression == "")

    }

  }
}
