/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import zio.*

import dsp.errors.AssertionException
import org.knora.webapi.ApiV2Schema
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.BindPattern
import org.knora.webapi.messages.util.search.ConstructClause
import org.knora.webapi.messages.util.search.Entity
import org.knora.webapi.messages.util.search.FilterNotExistsPattern
import org.knora.webapi.messages.util.search.FilterPattern
import org.knora.webapi.messages.util.search.GroupPattern
import org.knora.webapi.messages.util.search.IriRef
import org.knora.webapi.messages.util.search.MinusPattern
import org.knora.webapi.messages.util.search.OptionalPattern
import org.knora.webapi.messages.util.search.QueryPattern
import org.knora.webapi.messages.util.search.QueryTraverser
import org.knora.webapi.messages.util.search.QueryVariable
import org.knora.webapi.messages.util.search.SelectQuery
import org.knora.webapi.messages.util.search.StatementPattern
import org.knora.webapi.messages.util.search.UnionPattern
import org.knora.webapi.messages.util.search.ValuesPattern
import org.knora.webapi.messages.util.search.XsdLiteral
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.util.search.gravsearch.GravsearchQueryChecker
import org.knora.webapi.messages.util.search.gravsearch.transformers.OntologyInferencer
import org.knora.webapi.messages.util.search.gravsearch.transformers.SelectTransformer
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionResult
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionUtil
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri

/**
 * Shared by [[GravsearchToPrequeryTransformerE2ESpec]] and [[GravsearchToCountPrequeryTransformerE2ESpec]]:
 * runs the full two-stage prequery pipeline — prequery generation (transformConstructToSelect, where a
 * FILTER like matchFulltext is replaced by its expansion) followed by the inference pass
 * (transformSelectToSelect via SelectTransformer, where PrequeryPatternOrdering places the Lucene
 * GroupPattern expansion first (tier T1) and OntologyInferencer expands rdf:type/property statements).
 * This mirrors SearchResponderV2.gravsearchV2's own composition and is what golden-snapshotting an
 * expansion needs: taking the snapshot after only the first stage would miss traps (BIND hoisting,
 * rdf:type-with-variable-object rejection, join-order pessimization) that only manifest once the
 * inference pass runs.
 */
object GravsearchInferencePipelineTestSupport {

  /**
   * Shared verbatim by the page and count specs: both transformers emit the same WHERE clause for this
   * query and differ only in SELECT and ORDER BY, so their `listNodeAnchorShape` goldens must stay
   * byte-identical. Keeping one copy of the query text is what makes that comparison meaningful - do not
   * inline it back into either spec.
   */
  val queryListNodeAnchor: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
      |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      |
      |CONSTRUCT {
      |  ?letter knora-api:isMainResource true .
      |  ?letter beol:hasSubject ?subj .
      |} WHERE {
      |  ?letter a beol:letter .
      |  ?letter beol:hasSubject ?subj .
      |  ?subj knora-api:listValueAsListNode <http://rdfh.ch/lists/0801/logarithmic_curves> .
      |}
        """.stripMargin

  def transformQueryWithInference(
    query: String,
    buildTransformer: (
      ConstructClause,
      GravsearchTypeInspectionResult,
      ApiV2Schema,
      AppConfig,
    ) => AbstractPrequeryGenerator,
    dropOrderBy: Boolean = false,
    limitResultsToProject: Option[ProjectIri] = None,
  )(implicit
    sf: StringFormatter,
  ): ZIO[
    AppConfig & QueryTraverser & GravsearchTypeInspectionRunner & OntologyInferencer & InferenceOptimizationService,
    Throwable,
    SelectQuery,
  ] = for {
    parsedQuery          <- ZIO.attempt(GravsearchParser.parseQuery(query))
    sanitizedWhereClause <- GravsearchTypeInspectionUtil.removeTypeAnnotations(parsedQuery.whereClause)
    typeInspectionResult <-
      ZIO.serviceWithZIO[GravsearchTypeInspectionRunner](_.inspectTypes(parsedQuery.whereClause))
    _           <- GravsearchQueryChecker.checkConstructClause(parsedQuery.constructClause, typeInspectionResult)
    querySchema <-
      ZIO.fromOption(parsedQuery.querySchema).orElseFail(AssertionException(s"WhereClause has no querySchema"))
    appConfig           <- ZIO.service[AppConfig]
    prequeryTransformer <-
      ZIO.attempt(buildTransformer(parsedQuery.constructClause, typeInspectionResult, querySchema, appConfig))
    // Count queries don't need sorting (there's only ever one result row), mirroring
    // SearchResponderV2.fulltextSearchCountV2, which drops orderBy the same way.
    queryForPrequery = parsedQuery.copy(
                         whereClause = sanitizedWhereClause,
                         orderBy = if (dropOrderBy) Seq.empty else parsedQuery.orderBy,
                       )
    prequery <- ZIO.serviceWithZIO[QueryTraverser](
                  _.transformConstructToSelect(queryForPrequery, prequeryTransformer),
                )
    ontologyInferencer     <- ZIO.service[OntologyInferencer]
    inferenceOptimization  <- ZIO.service[InferenceOptimizationService]
    ontologiesForInference <- inferenceOptimization.getOntologiesRelevantForInference(parsedQuery.whereClause)
    selectTransformer       = new SelectTransformer(
                          simulateInference = prequeryTransformer.useInference,
                          ontologyInferencer,
                          prequeryTransformer.mainResourceVariable,
                          sf,
                        )
    transformedPrequery <- ZIO.serviceWithZIO[QueryTraverser](
                             _.transformSelectToSelect(
                               inputQuery = prequery,
                               transformer = selectTransformer,
                               limitInferenceToOntologies = ontologiesForInference,
                               limitResultsToProject = limitResultsToProject,
                             ),
                           )
  } yield transformedPrequery

  /**
   * Renders one line per top-level pattern of `query.whereClause.patterns`, in traversal order, so pattern
   * order becomes mechanically diffable in a golden file. This is needed because the rendered SPARQL is flat
   * (nested patterns start at column 0), so a text grep on the query string cannot tell top-level order.
   */
  def shapeSummary(query: SelectQuery): String = {
    def entityKind(entity: Entity): String = entity match {
      case _: QueryVariable => "var"
      case _: IriRef        => "iri"
      case _: XsdLiteral    => "lit"
      case _                => "other"
    }

    def predicateSummary(pred: Entity): String = pred match {
      case _: QueryVariable => "?var"
      case iriRef: IriRef   =>
        val iriStr         = iriRef.iri.toString
        val separatorIndex = if (iriStr.contains("#")) iriStr.lastIndexOf('#') else iriStr.lastIndexOf('/')
        val localName      = iriStr.substring(separatorIndex + 1)
        iriRef.propertyPathOperator.fold(localName)(_ => s"$localName*")
      case _ => "other"
    }

    def patternSummary(pattern: QueryPattern): String = pattern match {
      case StatementPattern(subj, pred, obj) => s"STMT ${entityKind(subj)} ${predicateSummary(pred)} ${entityKind(obj)}"
      case ValuesPattern(_, values)          => s"VALUES (${values.size})"
      case _: BindPattern                    => "BIND"
      case _: FilterPattern                  => "FILTER"
      case _: FilterNotExistsPattern         => "FNE"
      case _: OptionalPattern                => "OPTIONAL"
      case _: UnionPattern                   => "UNION"
      case _: MinusPattern                   => "MINUS"
      case _: GroupPattern                   => "GROUP"
    }

    query.whereClause.patterns.map(patternSummary).mkString("\n")
  }
}
