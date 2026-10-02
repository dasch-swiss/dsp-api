/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.resources.service

import org.knora.webapi.IRI
import org.knora.webapi.messages.OntologyConstants.KnoraBase
import org.knora.webapi.messages.v2.responder.standoffmessages.MappingXMLtoStandoff

final case class ValueHasXmlBackfillReport(found: Int, rendered: Int, failed: Int, stoppedEarly: Boolean) {
  def +(other: ValueHasXmlBackfillReport): ValueHasXmlBackfillReport =
    ValueHasXmlBackfillReport(
      found + other.found,
      rendered + other.rendered,
      failed + other.failed,
      stoppedEarly || other.stoppedEarly,
    )
}

object ValueHasXmlBackfillReport {
  val zero: ValueHasXmlBackfillReport = ValueHasXmlBackfillReport(0, 0, 0, false)
}

/** One formatted text value with its standoff nodes as lexical maps (predicate IRI to object string). */
final case class StoredTextValue(
  valueIri: IRI,
  valueHasString: String,
  mappingIri: IRI,
  textValueType: Option[IRI],
  standoffNodes: Map[IRI, Map[IRI, String]],
)

final case class RunState(
  failed: Set[IRI],
  mappings: Map[IRI, MappingXMLtoStandoff],
  report: ValueHasXmlBackfillReport,
  previousBatch: Set[IRI],
  done: Boolean,
)

object RunState {
  val zero: RunState = RunState(Set.empty, Map.empty, ValueHasXmlBackfillReport.zero, Set.empty, false)
}

enum StopReason {
  case NoCandidates, MaxFailures, Stalled

  def stoppedEarly: Boolean = this != NoCandidates
}

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

  /** A linked node absent from the response fails the value: rendering a subset would store incomplete XML. */
  private def standoffNodesOf(
    statements: Map[IRI, Seq[(IRI, String)]],
    own: Seq[(IRI, String)],
  ): Either[String, Map[IRI, Map[IRI, String]]] = {
    val linked = own.collect { case (KnoraBase.ValueHasStandoff, node) => node }
    if (!linked.forall(statements.contains)) Left("standoff node missing from the CONSTRUCT response")
    else Right(linked.map(node => node -> statements(node).toMap).filterNot((_, n) => hasNegativeStartIndex(n)).toMap)
  }

  private def objectOf(own: Seq[(IRI, String)], predicate: IRI): Option[String] =
    own.collectFirst { case (`predicate`, obj) => obj }

  private def hasNegativeStartIndex(node: Map[IRI, String]): Boolean =
    node.get(KnoraBase.StandoffTagHasStartIndex).flatMap(_.toIntOption).exists(_ < 0)

  def nextBatch(candidates: Seq[IRI], failed: Set[IRI], batchSize: Int): Seq[IRI] =
    candidates.filterNot(failed.contains).take(batchSize)

  /** Check order: MaxFailures, then NoCandidates, then Stalled. */
  def stopReason(state: RunState, batch: Seq[IRI], maxFailures: Int): Option[StopReason] =
    if (state.failed.size >= maxFailures) Some(StopReason.MaxFailures)
    else if (batch.isEmpty) Some(StopReason.NoCandidates)
    else if (state.previousBatch.nonEmpty && batch.toSet == state.previousBatch) Some(StopReason.Stalled)
    else None
}
