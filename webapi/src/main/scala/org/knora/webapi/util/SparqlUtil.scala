/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
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

import akka.event.LoggingAdapter
import com.typesafe.scalalogging.Logger
import org.apache.commons.lang3.StringUtils
import org.knora.webapi.messages.v1.store.triplestoremessages.{SparqlSelectResponse, VariableResultsRow}
import org.knora.webapi.util.SparqlResultProtocol._
import org.knora.webapi.{SettingsImpl, TriplestoreResponseException}
import org.slf4j.LoggerFactory
import spray.json._

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Utility functions for parsing responses from triplestores and for filtering results by language.
  */
object SparqlUtil {
    private val logDelimiter = "\n" + StringUtils.repeat('=', 80) + "\n"

    val log = Logger(LoggerFactory.getLogger("org.knora.rapier.util.SparqlUtils"))

    /**
      * Parses a response from the triplestore and converts it to a [[SparqlSelectResponse]].
      *
      * @param sparql    the SPARQL query that was submitted.
      * @param resultStr the triplestore's response.
      * @return a [[SparqlSelectResponse]].
      */
    def parseJsonResponse(sparql: String, resultStr: String, log: LoggingAdapter): Future[SparqlSelectResponse] = {
        val parseTry = Try {
            resultStr.parseJson.convertTo[SparqlSelectResponse]
        }

        parseTry match {
            case Success(parsed) => Future.successful(parsed)
            case Failure(e) =>
                log.error(e, s"Couldn't parse response from triplestore:$logDelimiter$resultStr${logDelimiter}in response to SPARQL query:$logDelimiter$sparql")
                Future.failed(TriplestoreResponseException("Couldn't parse JSON from triplestore", e, log))
        }
    }

    /**
      * Filters SPARQL query result rows by attempting to return results for certain properties in either the user's
      * preferred language or the application's default language. The name of the column containing language codes is
      * assumed to be `lang`.
      *
      * Rows are first grouped by the values of all columns except `langSpecificColumnName` and `lang`. In each group,
      * if there is no value for `lang`, the entire group is included in the results. If there is a value for `lang` in
      * the group, only one row in the group is returned: the row containing the user's preferred language if available,
      * otherwise the row containing the application's default language if available, otherwise the first row in the group.
      *
      * @param response               the SPARQL query result to filter.
      * @param langSpecificColumnName the name of the column that may contain language-specific data.
      * @param preferredLanguage      the user's preferred language.
      * @param settings               the application's configuration settings.
      * @return a filtered list of result rows.
      */
    def filterByLanguage(response: SparqlSelectResponse, langSpecificColumnName: String, preferredLanguage: String, settings: SettingsImpl): Seq[VariableResultsRow] = {
        val defaultLanguage = settings.fallbackLanguage

        // The names of all columns except "lang" and the column containing language-specific data.
        val keyColumnNames = response.head.vars.toSet - langSpecificColumnName - "lang"

        // Group the rows by the values of the key columns.
        val keyGroups: Map[Map[String, String], Seq[VariableResultsRow]] = response.results.bindings.groupBy {
            row => row.rowMap.filterKeys(columnName => keyColumnNames.contains(columnName))
        }

        // Filter the rows in each group.
        keyGroups.foldLeft(Vector.empty[VariableResultsRow]) {
            case (acc, (rowKey, keyGroup)) =>
                // Group the rows in each key group by language tag.
                val languageGroups: Map[Option[String], Seq[VariableResultsRow]] = keyGroup.groupBy {
                    _.rowMap.get("lang") match {
                        case Some("") => None
                        case Some(lang) => Some(lang)
                        case None => None
                    }
                }

                // Does this key group contain any rows with a language tag?
                if (languageGroups.keySet == Set(None)) {
                    // No: take all rows in the key group.
                    acc ++ keyGroup
                } else {
                    // Yes: see if there's a language group for the user's preferred language.
                    languageGroups.get(Some(preferredLanguage)) match {
                        case Some(rowsWithPreferredLanguage) =>
                            rowsWithPreferredLanguage.head +: acc
                        case None =>
                            // If the user's preferred language isn't available, see if there's a
                            // language group for the application's default language.
                            languageGroups.get(Some(defaultLanguage)) match {
                                case Some(rowsWithDefaultLanguage) => rowsWithDefaultLanguage.head +: acc
                                case None =>
                                    // If the application's default language isn't available, take the first
                                    // row in the key group, regardless of its language.
                                    keyGroup.head +: acc
                            }
                    }
                }
        }
    }
}
