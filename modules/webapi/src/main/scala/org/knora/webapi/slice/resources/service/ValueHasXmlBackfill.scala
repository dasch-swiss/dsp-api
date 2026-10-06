/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants.KnoraBase

/** Cumulative counts of a backfill run: values processed, values written, values that could not be rendered. */
final case class ValueHasXmlBackfillCounts(found: Int, rendered: Int, failed: Int) {
  def +(other: ValueHasXmlBackfillCounts): ValueHasXmlBackfillCounts =
    ValueHasXmlBackfillCounts(found + other.found, rendered + other.rendered, failed + other.failed)

  def describe: String = s"found=$found rendered=$rendered failed=$failed"
}

object ValueHasXmlBackfillCounts {
  val zero: ValueHasXmlBackfillCounts = ValueHasXmlBackfillCounts(0, 0, 0)
}

/** Totals of a run over several projects: projects processed, projects whose run failed, summed value counts. */
final case class ValueHasXmlBackfillSummary(projects: Int, projectsFailed: Int, counts: ValueHasXmlBackfillCounts) {
  def +(other: ValueHasXmlBackfillSummary): ValueHasXmlBackfillSummary =
    ValueHasXmlBackfillSummary(projects + other.projects, projectsFailed + other.projectsFailed, counts + other.counts)

  def hasFailures: Boolean = projectsFailed > 0 || counts.failed > 0

  def describe: String = s"projects=$projects projectsFailed=$projectsFailed ${counts.describe}"
}

object ValueHasXmlBackfillSummary {
  val zero: ValueHasXmlBackfillSummary = ValueHasXmlBackfillSummary(0, 0, ValueHasXmlBackfillCounts.zero)

  def succeeded(counts: ValueHasXmlBackfillCounts): ValueHasXmlBackfillSummary =
    ValueHasXmlBackfillSummary(1, 0, counts)

  val failed: ValueHasXmlBackfillSummary = ValueHasXmlBackfillSummary(1, 1, ValueHasXmlBackfillCounts.zero)
}

/** One formatted text value with its standoff nodes as lexical maps (predicate IRI to object string). */
final case class StoredTextValue(
  valueIri: IRI,
  valueHasString: String,
  mappingIri: IRI,
  textValueType: Option[IRI],
  standoffNodes: Map[IRI, Map[IRI, String]],
)

object ValueHasXmlBackfill {

  def groupByValue(
    statements: Map[IRI, Seq[(IRI, String)]],
    valueIris: Seq[IRI],
  ): Map[IRI, Either[String, StoredTextValue]] =
    valueIris.map(iri => iri -> toStoredTextValue(statements, iri)).toMap

  private def toStoredTextValue(
    statements: Map[IRI, Seq[(IRI, String)]],
    valueIri: IRI,
  ): Either[String, StoredTextValue] =
    for {
      own     <- statements.get(valueIri).toRight("value missing from the CONSTRUCT response")
      string  <- objectOf(own, KnoraBase.ValueHasString).toRight("value has no valueHasString")
      mapping <- objectOf(own, KnoraBase.ValueHasMapping).toRight("value has no valueHasMapping")
      nodes   <- standoffNodesOf(statements, own)
    } yield StoredTextValue(
      valueIri = valueIri,
      valueHasString = string,
      mappingIri = mapping,
      textValueType = objectOf(own, KnoraBase.HasTextValueType),
      standoffNodes = nodes,
    )

  /**
   * A linked node absent from the response fails the value: rendering a subset would store incomplete XML.
   * [[org.knora.webapi.slice.resources.repo.ValueHasXmlBackfillQuery.constructStandoff]] emits a link only together
   * with its node.
   */
  private def standoffNodesOf(
    statements: Map[IRI, Seq[(IRI, String)]],
    own: Seq[(IRI, String)],
  ): Either[String, Map[IRI, Map[IRI, String]]] = {
    val linked = own.collect { case (KnoraBase.ValueHasStandoff, node) => node }
    if (!linked.forall(statements.contains)) Left("standoff node missing from the CONSTRUCT response")
    else {
      val renderable = linked.map(node => node -> statements(node).toMap).filterNot((_, n) => hasNegativeStartIndex(n))
      // An empty node set renders no XML, so the value would stay a candidate on every batch.
      Either.cond(renderable.nonEmpty, renderable.toMap, "value has no standoff node with a start index >= 0")
    }
  }

  private def objectOf(own: Seq[(IRI, String)], predicate: IRI): Option[String] =
    own.collectFirst { case (`predicate`, obj) => obj }

  private def hasNegativeStartIndex(node: Map[IRI, String]): Boolean =
    node.get(KnoraBase.StandoffTagHasStartIndex).flatMap(_.toIntOption).exists(_ < 0)
}
