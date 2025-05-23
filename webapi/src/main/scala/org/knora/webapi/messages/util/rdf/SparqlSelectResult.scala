/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.rdf

import cats.syntax.traverse.*

import dsp.errors.InconsistentRepositoryDataException

/**
 * Represents the result of a SPARQL SELECT query.
 *
 * @param head    the header of the response, containing the variable names.
 * @param results the body of the response, containing rows of query results.
 */
case class SparqlSelectResult(head: SparqlSelectResultHeader, results: SparqlSelectResultBody) { self =>

  def getFirstRow: Option[VariableResultsRow] =
    results.bindings.headOption

  def getFirst(v: String): Option[String] =
    results.bindings.headOption.flatMap(_.rowMap.get(v))

  def getFirstOrThrow(v: String): String =
    results.bindings.head.rowMap(v)

  def getCol(v: String): Seq[String] =
    self.flatMap(_.rowMap.get(v))

  def getColOrThrow(v: String): Seq[String] =
    self.map(_.rowMap(v))

  def map[B](f: VariableResultsRow => B): Seq[B] =
    results.bindings.map(f)

  def flatMap[B](f: VariableResultsRow => Iterable[B]): Seq[B] =
    results.bindings.flatMap(f)

  def isEmpty: Boolean =
    results.bindings.isEmpty

  def nonEmpty: Boolean =
    results.bindings.nonEmpty

  def size: Int =
    results.bindings.size

  /**
   * Returns the contents of the first row of results.
   *
   * @return a [[Map]] representing the contents of the first row of results.
   */
  def getFirstRowOrThrow: VariableResultsRow =
    getFirstRow match {
      case Some(row: VariableResultsRow) => row
      case None                          => throw InconsistentRepositoryDataException(s"A SPARQL query unexpectedly returned an empty result")
    }
}

/**
 * Represents the header of the result of a SPARQL SELECT query.
 *
 * @param vars the names of the variables that were used in the SPARQL SELECT statement.
 */
case class SparqlSelectResultHeader(vars: Seq[String])

/**
 * Represents the body of the result of a SPARQL SELECT query.
 *
 * @param bindings the bindings of values to the variables used in the SPARQL SELECT statement.
 */
case class SparqlSelectResultBody(bindings: Seq[VariableResultsRow])

/**
 * Represents a row of results in the result of a SPARQL SELECT query.
 *
 * @param rowMap a map of variable names to values in the row.
 */
case class VariableResultsRow(rowMap: Map[String, String]) {
  def get(v: String): Option[String] = rowMap.get(v)
  def getRequired(v: String): String =
    get(v).getOrElse(throw InconsistentRepositoryDataException(s"Variable '$v' not found"))
  def get[A](v: String, mapper: String => Either[String, A]): Option[A] = rowMap
    .get(v)
    .traverse(mapper)
    .fold(err => throw InconsistentRepositoryDataException(s"Failed mapping variable '$v': $err"), identity)
  def getRequired[A](v: String, mapper: String => Either[String, A]): A =
    get(v, mapper).getOrElse(throw InconsistentRepositoryDataException(s"Variable '$v' not found"))
}
