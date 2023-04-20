/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import zio.Task
import zio.ZIO

import org.knora.webapi.ApiV2Schema
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionResult

/**
 * Transforms a preprocessed CONSTRUCT query into a SELECT query that returns only the IRIs and sort order of the main resources that matched
 * the search criteria. This query will be used to get resource IRIs for a single page of results. These IRIs will be included in a CONSTRUCT
 * query to get the actual results for the page.
 *
 * @param constructClause      the CONSTRUCT clause from the input query.
 * @param typeInspectionResult the result of type inspection of the input query.
 * @param querySchema          the ontology schema used in the input query.
 */
class GravsearchToCountPrequeryTransformer(
  constructClause: ConstructClause,
  typeInspectionResult: GravsearchTypeInspectionResult,
  querySchema: ApiV2Schema
) extends AbstractPrequeryGenerator(
      constructClause = constructClause,
      typeInspectionResult = typeInspectionResult,
      querySchema = querySchema
    ) {

  override def getSelectColumns: Task[Seq[SelectQueryColumn]] =
    // return count aggregation function for main variable
    ZIO.succeed(Seq(Count(inputVariable = mainResourceVariable, distinct = true, outputVariableName = "count")))

  override def getGroupBy(orderByCriteria: TransformedOrderBy): Task[Seq[QueryVariable]] =
    ZIO.succeed(Seq.empty[QueryVariable])

  override def getOrderBy(inputOrderBy: Seq[OrderCriterion]): Task[TransformedOrderBy] =
    // empty by default
    ZIO.succeed(TransformedOrderBy())

  override def getLimit: Task[Int] = ZIO.succeed(1) // one row expected for count query

  override def getOffset(inputQueryOffset: Long, limit: Int): Task[Long] =
    // count queries do not consider offsets since there is only one result row
    ZIO.succeed(0L)
}
