package org.knora.webapi.responders.v2.search.sparql


/**
  * Provides some functionality to pre-process a given search string so it supports the Apache Lucene Query Parser syntax.
  */
object ApacheLuceneSupport {

    private val wildcard = "*"

    private val logicalAnd = "AND"

    // separates single terms
    private val space = " "

    /**
      * Searches for a resource by its rdfs:label as the user is typing.
      *
      * @param terms the terms to search for.
      * @param lastTerm the last term the user is entering at the moment.
      */
    case class MatchStringWhileTyping(terms: Seq[String], lastTerm: String) {

        //
        // Search logic for Lucene: combine a phrase enclosed by double quotes (exact match) with a single search term with a wildcard at the end (matches the beginning of the given term).
        // Example: searchString "Reise ins Heili" results in: '"Reise ins" AND Heili*' that matches "Reise ins Heilige Land".
        // This is necessary because wildcards cannot be used inside a phrase. And we need phrases because we cannot just search for a combination of single terms as their order matters.
        //
        // Use Lucene Query Parser Syntax: https://lucene.apache.org/core/3_6_2/queryparsersyntax.html
        //


        /**
          * Generates a string literal to be used as an object in a statement with the Apache Lucene predicate.
          * It combines all terms with a logical AND and adds the last term with a wildcard at the end, not taking into account the given sequence of the elements.
          *
          * This matches all the strings that contain all of the terms in any order and the term with the wildcard.
          *
          * @return a string literal.
          */
        def generateLiteralForLuceneIndexWithoutExactSequence: String = {

            // Combine all terms with a logical AND and add a wildcard to the last term.
            // Finds all the strings that contain all of the terms in any order and the last term with the wildcard at the end.

            if (terms.nonEmpty) {
                s"'${this.terms.mkString(s" $logicalAnd ")} $logicalAnd $lastTerm$wildcard'"
            } else {
                s"'$lastTerm$wildcard'"
            }
        }

        /**
          * Generates a string literal to be used as an object in a statement with the Apache Lucene predicate.
          * It generates one string from the given terms to be enclosed with double quotes and appends the last term to it with a wildcard at the end.
          *
          * This matches all the strings that contain the exact phrase and the term with the wildcard.
          * Example: "Reise ins" "Heili" -> "Reise ins" AND Heili*

          * @return a string literal.
          */
        def generateLiteralForLuceneIndexWithExactSequence: String = {

            // Combine all the terms to a phrase (to be enclosed by double quotes) and add a wildcard to the last term.
            // This finds all the strings that contain the exact phrase and the term with the wildcard at the end.
            // Example: "Reise ins" "Heili" -> "Reise ins" AND Heili*

            if (terms.nonEmpty) {
                s"""'"${this.terms.mkString(" ")}" $logicalAnd $lastTerm$wildcard'"""
            } else {
                s"'$lastTerm$wildcard'"
            }
        }

        /**
          * Generates a Filter regex that makes sure that phrase and the last term occur exactly in the given order.
          *
          * @param labelVarName the name of the variable representing the string to check.
          * @return a FILTER regex statement, if phrase is given.
          */
        def generateRegexFilterStatementForExactSequenceMatch(labelVarName: String): String = {

            // Apply filter statement containing a regex to make sure that lastTerm directly follows the phrase
            // (only necessary if we have several terms to search for).

            if (terms.nonEmpty) {
                s"FILTER regex(?$labelVarName, '${this.terms.mkString(" ")} $lastTerm$wildcard', 'i')"
            } else {
                ""
            }

        }

    }

    /**
      * Companion object providing constructor.
      */
    object MatchStringWhileTyping {

        def apply(searchString: String) = {

            // split search string by a space
            val searchStringSegments: Seq[String] = searchString.split(space).toList

            if (searchStringSegments.size > 1) {

                new MatchStringWhileTyping(searchStringSegments.init, searchStringSegments.last)
            } else {
                new MatchStringWhileTyping(List.empty[String], searchStringSegments.head)
            }



        }

    }

    /**
      * Handles Boolean logic for given search terms.
      *
      * @param terms given search terms.
      */
    case class CombineSearchTerms(terms: Seq[String]) {

        /**
          * Combines given search terms with a logical AND.
          * Lucene's default behaviour is a logical OR.
          *
          * @return a string combining the given search terms with a logical AND.
          */
        def combineSearchTermsWithLogicalAnd: String = {

            terms.mkString(s" $logicalAnd ")

        }

    }

    /**
      * Companion object providing constructor.
      */
    object CombineSearchTerms {

        def apply(searchString: String): CombineSearchTerms = {

            // split search string by a space
            val searchTerms = searchString.split(space)

            new CombineSearchTerms(terms = searchTerms.toSeq)

        }


    }

}
