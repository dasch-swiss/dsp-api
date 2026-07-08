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
import org.knora.webapi.messages.util.search.ConstructClause
import org.knora.webapi.messages.util.search.QueryTraverser
import org.knora.webapi.messages.util.search.SelectQuery
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.util.search.gravsearch.GravsearchQueryChecker
import org.knora.webapi.messages.util.search.gravsearch.transformers.OntologyInferencer
import org.knora.webapi.messages.util.search.gravsearch.transformers.SelectTransformer
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionResult
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionUtil

/**
 * Shared by [[GravsearchToPrequeryTransformerE2ESpec]] and [[GravsearchToCountPrequeryTransformerE2ESpec]]:
 * runs the full two-stage prequery pipeline — prequery generation (transformConstructToSelect, where a
 * FILTER like matchFulltext is replaced by its expansion) followed by the inference pass
 * (transformSelectToSelect via SelectTransformer, where the optimizer's moveLuceneToBeginning hoists a
 * GroupPattern expansion and OntologyInferencer expands rdf:type/property statements). This mirrors
 * SearchResponderV2.gravsearchV2's own composition and is what golden-snapshotting an expansion needs:
 * taking the snapshot after only the first stage would miss traps (BIND hoisting, rdf:type-with-variable-object
 * rejection, join-order pessimization) that only manifest once the inference pass runs.
 */
object GravsearchInferencePipelineTestSupport {

  def transformQueryWithInference(
    query: String,
    buildTransformer: (
      ConstructClause,
      GravsearchTypeInspectionResult,
      ApiV2Schema,
      AppConfig,
    ) => AbstractPrequeryGenerator,
    dropOrderBy: Boolean = false,
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
                               limitResultsToProject = None,
                             ),
                           )
  } yield transformedPrequery
}
