/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util

import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.queryparser.classic.QueryParser
import org.apache.lucene.queryparser.classic.QueryParserBase
import zio.*

import scala.util.Try
import scala.util.matching.Regex

import org.knora.webapi.slice.common.StringValueCompanion
import org.knora.webapi.slice.common.Value.StringValue
import org.knora.webapi.slice.common.domain.SparqlEncodedString

final case class FusekiLucenceQuery private (override val value: String) extends StringValue {
  def getQueryString: String = SparqlEncodedString.unsafeFrom(value).toString
}
object FusekiLucenceQuery extends StringValueCompanion[FusekiLucenceQuery] {
  def from(str: String): Either[String, FusekiLucenceQuery] =
    for {
      _ <- Try(new QueryParser("field", new StandardAnalyzer()).parse(str)).toEither.left.map(_.getMessage)
      _ <- SparqlEncodedString.from(str)
    } yield FusekiLucenceQuery(str)
}

/**
 * Provides some functionality to pre-process a given search string so it supports the Apache Lucene Query Parser syntax.
 */
object ApacheLuceneSupport {

  // https://stackoverflow.com/questions/43665641/in-scala-how-can-i-split-a-string-on-whitespaces-accounting-for-an-embedded-quot
  val separateTermsAndPhrasesRegex = new Regex("([^\\s]*\".*?\"[^\\s]*)|([^\\s]+)")

  def asLuceneQueryForSearchByLabel(searchString: String): IO[String, FusekiLucenceQuery] =
    val query = searchString.split(' ').map(QueryParserBase.escape).mkString(s" AND ")
    ZIO.from(FusekiLucenceQuery.from(s"$query*"))

  /**
   * Handles Boolean logic for given search terms.
   *
   * @param queryString given search terms.
   */
  case class LuceneQueryString(private val queryString: String) {

    /**
     * Returns the query string.
     *
     * @return query string.
     */
    def getQueryString: String =
      queryString

    /**
     * Returns the terms contained in a Lucene query string.
     *
     * @return the terms contained in a Lucene query.
     */
    def getSingleTerms: Seq[String] =
      queryString.split(' ').toSeq
  }
}
