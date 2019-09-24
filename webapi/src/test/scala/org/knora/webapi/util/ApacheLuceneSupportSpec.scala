/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util

import org.knora.webapi._

class ApacheLuceneSupportSpec extends CoreSpec() {

    "The ApacheLuceneSupport class" should {

        "combine space separated words with a logical AND" in {

            val searchString = "Reise Land"
            val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

            assert(searchExpression == "Reise AND Land")
        }

        "combine space separated words with a logical AND (2)" in {

            val searchString = "Reise ins Land"
            val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

            assert(searchExpression == "Reise AND ins AND Land")
        }

        "combine phrases and terms with a logical AND" in {

            val searchString = "\"Leonhard Euler\" Bernoulli"
            val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

            assert(searchExpression == "\"Leonhard Euler\" AND Bernoulli")

        }

        "combine two phrases and one term with a logical AND" in {

            val searchString = "\"Leonhard Euler\" \"Daniel Bernoulli\" formula"
            val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

            assert(searchExpression == "\"Leonhard Euler\" AND \"Daniel Bernoulli\" AND formula")

        }

        "combine two phrases and two terms with a logical AND" in {

            val searchString = "\"Leonhard Euler\" \"Daniel Bernoulli\" formula geometria"
            val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

            assert(searchExpression == "\"Leonhard Euler\" AND \"Daniel Bernoulli\" AND formula AND geometria")

        }

        "handle one phrase correctly" in {

            val searchString = "\"Leonhard Euler\""
            val searchExpression: String = ApacheLuceneSupport.LuceneQueryString(searchString).getQueryString

            assert(searchExpression == "\"Leonhard Euler\"")

        }

        "combine space separated words with a logical AND and add a wildcard to the last word (non exact sequence)" in {

            val searchString = "Reise ins Heilige Lan"
            val searchExpression = ApacheLuceneSupport.MatchStringWhileTyping(searchString).generateLiteralForLuceneIndexWithoutExactSequence

            assert(searchExpression == "Reise AND ins AND Heilige AND Lan*")

        }

        "add a wildcard to the word if the search string only contains one word (non exact sequence)" in {

            val searchString = "Reis"
            val searchExpression = ApacheLuceneSupport.MatchStringWhileTyping(searchString).generateLiteralForLuceneIndexWithoutExactSequence

            assert(searchExpression == "Reis*")

        }

        "combine all space separated words to a phrase but the last one and add a wildcard to it (exact sequence)" in {

            val searchString = "Reise ins Heilige Lan"
            val searchExpression = ApacheLuceneSupport.MatchStringWhileTyping(searchString).generateLiteralForLuceneIndexWithExactSequence

            assert(searchExpression == """"Reise ins Heilige" AND Lan*""")

        }

        "add a wildcard to the word if the search string only contains one word (exact sequence)" in {

            val searchString = "Reis"
            val searchExpression = ApacheLuceneSupport.MatchStringWhileTyping(searchString).generateLiteralForLuceneIndexWithExactSequence

            assert(searchExpression == "Reis*")

        }

        "create a regex FILTER expression for an exact match" in {

            val searchString = "Reise ins Heilige Lan"
            val searchExpression = ApacheLuceneSupport.MatchStringWhileTyping(searchString).generateRegexFilterStatementForExactSequenceMatch("firstProp")

            assert(searchExpression == "FILTER regex(?firstProp, 'Reise ins Heilige Lan*', 'i')")

        }

        "not create a regex FILTER expression for an exact match when only one word is provided" in {

            val searchString = "Reise"
            val searchExpression = ApacheLuceneSupport.MatchStringWhileTyping(searchString).generateRegexFilterStatementForExactSequenceMatch("firstProp")

            assert(searchExpression == "")

        }


    }
}
