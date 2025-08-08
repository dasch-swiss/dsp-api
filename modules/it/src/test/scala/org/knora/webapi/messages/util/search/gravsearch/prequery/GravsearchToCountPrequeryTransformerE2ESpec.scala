/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import zio.*
import zio.test.*

import dsp.errors.AssertionException
import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.util.search.gravsearch.GravsearchQueryChecker
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionUtil
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*

object GravsearchToCountPrequeryTransformerE2ESpec extends E2EZSpec {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val queryTraverser   = ZIO.serviceWithZIO[QueryTraverser]
  private val inspectionRunner = ZIO.serviceWithZIO[GravsearchTypeInspectionRunner]

  private def transformQuery(
    query: String,
  ): ZIO[QueryTraverser & GravsearchTypeInspectionRunner, Throwable, SelectQuery] = for {
    query                <- ZIO.attempt(GravsearchParser.parseQuery(query))
    inspectionResult     <- inspectionRunner(_.inspectTypes(query.whereClause, anythingAdminUser))
    _                    <- GravsearchQueryChecker.checkConstructClause(query.constructClause, inspectionResult)
    sanitizedWhereClause <- GravsearchTypeInspectionUtil.removeTypeAnnotations(query.whereClause)
    querySchema          <- ZIO.fromOption(query.querySchema).orElseFail(AssertionException(s"WhereClause has no querySchema"))
    prequery <- queryTraverser(
                  _.transformConstructToSelect(
                    query.copy(whereClause = sanitizedWhereClause, orderBy = Seq.empty),
                    new GravsearchToCountPrequeryTransformer(query.constructClause, inspectionResult, querySchema),
                  ),
                )
  } yield prequery

  val inputQueryWithDecimalOptionalSortCriterionAndFilter: String =
    """
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |     ?thing knora-api:isMainResource true .
      |
      |     ?thing anything:hasDecimal ?decimal .
      |} WHERE {
      |
      |     ?thing a anything:Thing .
      |     ?thing a knora-api:Resource .
      |
      |     OPTIONAL {
      |        ?thing anything:hasDecimal ?decimal .
      |        anything:hasDecimal knora-api:objectType xsd:decimal .
      |
      |        ?decimal a xsd:decimal .
      |
      |        FILTER(?decimal > "2"^^xsd:decimal)
      |     }
      |} ORDER BY ASC(?decimal)
        """.stripMargin

  val transformedQueryWithDecimalOptionalSortCriterionAndFilter: SelectQuery =
    SelectQuery(
      variables = Vector(
        Count(
          inputVariable = QueryVariable(variableName = "thing"),
          distinct = true,
          outputVariableName = "count",
        ),
      ),
      offset = 0,
      groupBy = Nil,
      orderBy = Nil,
      whereClause = WhereClause(
        patterns = Vector(
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
              propertyPathOperator = None,
            ),
            obj = XsdLiteral(
              value = "false",
              datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
            ),
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              propertyPathOperator = None,
            ),
            obj = IriRef(
              iri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
              propertyPathOperator = None,
            ),
          ),
          OptionalPattern(
            patterns = Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasDecimal".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "decimal"),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "decimal"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = XsdLiteral(
                  value = "false",
                  datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
                ),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "decimal"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasDecimal".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "decimal__valueHasDecimal"),
              ),
              FilterPattern(expression =
                CompareExpression(
                  leftArg = QueryVariable(variableName = "decimal__valueHasDecimal"),
                  operator = CompareExpressionOperator.GREATER_THAN,
                  rightArg = XsdLiteral(
                    value = "2",
                    datatype = "http://www.w3.org/2001/XMLSchema#decimal".toSmartIri,
                  ),
                ),
              ),
            ),
          ),
        ).toSeq,
        positiveEntities = Set(),
        querySchema = None,
      ),
      limit = Some(1),
      useDistinct = true,
    )

  val inputQueryWithDecimalOptionalSortCriterionAndFilterComplex: String =
    """
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |
      |CONSTRUCT {
      |     ?thing knora-api:isMainResource true .
      |
      |     ?thing anything:hasDecimal ?decimal .
      |} WHERE {
      |
      |     ?thing a anything:Thing .
      |     ?thing a knora-api:Resource .
      |
      |     OPTIONAL {
      |        ?thing anything:hasDecimal ?decimal .
      |
      |        ?decimal knora-api:decimalValueAsDecimal ?decimalVal .
      |
      |        FILTER(?decimalVal > "2"^^xsd:decimal)
      |     }
      |} ORDER BY ASC(?decimal)
        """.stripMargin

  val expectedQueryWithDecimalOptionalSortCriterionAndFilterComplex: SelectQuery =
    SelectQuery(
      fromClause = None,
      variables = List(
        Count(
          outputVariableName = "count",
          distinct = true,
          inputVariable = QueryVariable(variableName = "thing"),
        ),
      ),
      offset = 0,
      groupBy = Nil,
      orderBy = Nil,
      whereClause = WhereClause(
        patterns = Vector(
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
              propertyPathOperator = None,
            ),
            obj = XsdLiteral(
              value = "false",
              datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
            ),
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              propertyPathOperator = None,
            ),
            obj = IriRef(
              iri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
              propertyPathOperator = None,
            ),
          ),
          OptionalPattern(
            patterns = Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasDecimal".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "decimal"),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "decimal"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = XsdLiteral(
                  value = "false",
                  datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
                ),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "decimal"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasDecimal".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "decimalVal"),
              ),
              FilterPattern(expression =
                CompareExpression(
                  leftArg = QueryVariable(variableName = "decimalVal"),
                  operator = CompareExpressionOperator.GREATER_THAN,
                  rightArg = XsdLiteral(
                    value = "2",
                    datatype = "http://www.w3.org/2001/XMLSchema#decimal".toSmartIri,
                  ),
                ),
              ),
            ),
          ),
        ),
        positiveEntities = Set(),
        querySchema = None,
      ),
      limit = Some(1),
      useDistinct = true,
    )

  override val e2eSpec = suite("The NonTriplestoreSpecificGravsearchToCountPrequeryGenerator object")(
    test("transform an input query with a decimal as an optional sort criterion and a filter") {
      transformQuery(inputQueryWithDecimalOptionalSortCriterionAndFilter)
        .map(actual => assertTrue(actual == transformedQueryWithDecimalOptionalSortCriterionAndFilter))
    },
    test(
      "transform an input query with a decimal as an optional sort criterion and a filter (submitted in complex schema)",
    ) {
      transformQuery(inputQueryWithDecimalOptionalSortCriterionAndFilterComplex)
        .map(actual => assertTrue(actual == expectedQueryWithDecimalOptionalSortCriterionAndFilterComplex))
    },
  )
}
