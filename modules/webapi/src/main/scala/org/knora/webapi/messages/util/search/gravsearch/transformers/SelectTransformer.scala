/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.transformers

import zio.*

import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.SmartIri
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*

import SparqlTransformer.*

class SelectTransformer(
  simulateInference: Boolean,
  inferencer: OntologyInferencer,
  mainRes: QueryVariable,
  implicit val stringFormatter: StringFormatter,
) extends WhereTransformer {

  override def enteringUnionBlock(): Task[Unit] = ZIO.unit

  override def leavingUnionBlock(): Task[Unit] = ZIO.unit

  override def transformFilter(filterPattern: FilterPattern): Task[Seq[QueryPattern]] = ZIO.succeed(Seq(filterPattern))

  // Gives each statement a unique suffix for the VALUES variables the inference pass may introduce
  // (e.g. `?resTypes0`, `?resTypes1`, ...), instead of the random one `OntologyInferencer` falls back
  // to. Determinism lets prequery golden-snapshot tests assert on the rendered SPARQL; a *constant*
  // suffix would be wrong here, since it would give unrelated statements (e.g. `?mainRes a Resource`
  // and `?val a Value`) the same VALUES variable, intersecting their blocks into empty results.
  private var statementCounter = 0

  override def transformStatementInWhere(
    statementPattern: StatementPattern,
    inputOrderBy: Seq[OrderCriterion],
    limitInferenceToOntologies: Option[Set[SmartIri]] = None,
  ): Task[Seq[QueryPattern]] = {
    statementCounter += 1
    inferencer.transformStatementInWhere(
      statementPattern = statementPattern,
      simulateInference = simulateInference,
      limitInferenceToOntologies = limitInferenceToOntologies,
      queryVariableSuffix = Some(statementCounter.toString),
    )
  }
  override def optimiseQueryPatterns(patterns: Seq[QueryPattern]): Task[Seq[QueryPattern]] = ZIO.attempt {
    moveBindToBeginning(optimiseIsDeletedWithFilter(moveLuceneToBeginning(patterns)))
  }

  def limitToProjectPattern(projectIri: SmartIri) = StatementPattern(
    subj = mainRes,
    pred = IriRef(stringFormatter.toSmartIri(OntologyConstants.KnoraBase.AttachedToProject)),
    obj = IriRef(projectIri),
  )
}
