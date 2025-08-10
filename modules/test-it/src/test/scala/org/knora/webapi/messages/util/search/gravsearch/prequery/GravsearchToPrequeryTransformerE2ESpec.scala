/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.messages.util.search.gravsearch.prequery

import zio.*
import zio.test.*

import scala.collection.mutable.ArrayBuffer

import dsp.errors.AssertionException
import org.knora.webapi.E2EZSpec
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search.*
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.util.search.gravsearch.GravsearchQueryChecker
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionUtil
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*

object GravsearchToPrequeryTransformerE2ESpec extends E2EZSpec {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val inspectionRunner = ZIO.serviceWithZIO[GravsearchTypeInspectionRunner]
  private val queryTraverser   = ZIO.serviceWithZIO[QueryTraverser]

  private def transformQuery(
    query: String,
  ): ZIO[AppConfig & QueryTraverser & GravsearchTypeInspectionRunner, Throwable, SelectQuery] = for {
    query                <- ZIO.attempt(GravsearchParser.parseQuery(query))
    sanitizedWhereClause <- GravsearchTypeInspectionUtil.removeTypeAnnotations(query.whereClause)
    typeInspectionResult <- inspectionRunner(_.inspectTypes(query.whereClause, anythingAdminUser))
    _                    <- GravsearchQueryChecker.checkConstructClause(query.constructClause, typeInspectionResult)
    querySchema          <- ZIO.fromOption(query.querySchema).orElseFail(AssertionException(s"WhereClause has no querySchema"))
    appConfig            <- ZIO.service[AppConfig]
    transformer <- ZIO.attempt(
                     new GravsearchToPrequeryTransformer(
                       constructClause = query.constructClause,
                       typeInspectionResult = typeInspectionResult,
                       querySchema = querySchema,
                       appConfig = appConfig,
                     ),
                   )
    preQuery <- queryTraverser(
                  _.transformConstructToSelect(query.copy(whereClause = sanitizedWhereClause), transformer),
                )
  } yield preQuery

  val inputQueryWithDateNonOptionalSortCriterion: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  ?thing onto:hasDate ?date .
      |  onto:hasDate knora-api:objectType knora-api:Date .
      |  ?date a knora-api:Date .
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val inputQueryWithDateNonOptionalSortCriterionComplex: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  ?thing onto:hasDate ?date .
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val transformedQueryWithDateNonOptionalSortCriterion: SelectQuery =
    SelectQuery(
      variables = Vector(
        QueryVariable(variableName = "thing"),
        GroupConcat(
          inputVariable = QueryVariable(variableName = "date"),
          separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
          outputVariableName = "date__Concat",
        ),
      ),
      offset = 0,
      groupBy = Vector(
        QueryVariable(variableName = "thing"),
        QueryVariable(variableName = "date__valueHasStartJDN"),
      ),
      orderBy = Vector(
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "date__valueHasStartJDN"),
          isAscending = false,
        ),
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "thing"),
          isAscending = true,
        ),
      ),
      whereClause = WhereClause(
        patterns = ArrayBuffer(
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
              iri = "http://www.knora.org/ontology/0001/anything#hasDate".toSmartIri,
              propertyPathOperator = None,
            ),
            obj = QueryVariable(variableName = "date"),
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "date"),
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
            subj = QueryVariable(variableName = "date"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
              propertyPathOperator = None,
            ),
            obj = QueryVariable(variableName = "date__valueHasStartJDN"),
          ),
        ).toSeq,
        positiveEntities = Set(),
        querySchema = None,
      ),
      limit = Some(25),
      useDistinct = true,
    )

  val inputQueryWithDateNonOptionalSortCriterionAndFilter: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  ?thing onto:hasDate ?date .
      |  onto:hasDate knora-api:objectType knora-api:Date .
      |  ?date a knora-api:Date .
      |
      |  FILTER(?date > "GREGORIAN:2012-01-01"^^knora-api:Date)
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val inputQueryWithDateNonOptionalSortCriterionAndFilterComplex: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  ?thing onto:hasDate ?date .
      |
      |  FILTER(knora-api:toSimpleDate(?date) > "GREGORIAN:2012-01-01"^^knora-api-simple:Date)
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val transformedQueryWithDateNonOptionalSortCriterionAndFilter: SelectQuery =
    SelectQuery(
      variables = Vector(
        QueryVariable(variableName = "thing"),
        GroupConcat(
          inputVariable = QueryVariable(variableName = "date"),
          separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
          outputVariableName = "date__Concat",
        ),
      ),
      offset = 0,
      groupBy = Vector(
        QueryVariable(variableName = "thing"),
        QueryVariable(variableName = "date__valueHasStartJDN"),
      ),
      orderBy = Vector(
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "date__valueHasStartJDN"),
          isAscending = false,
        ),
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "thing"),
          isAscending = true,
        ),
      ),
      whereClause = WhereClause(
        patterns = ArrayBuffer(
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
              iri = "http://www.knora.org/ontology/0001/anything#hasDate".toSmartIri,
              propertyPathOperator = None,
            ),
            obj = QueryVariable(variableName = "date"),
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "date"),
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
            subj = QueryVariable(variableName = "date"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
              propertyPathOperator = None,
            ),
            obj = QueryVariable(variableName = "date__valueHasStartJDN"),
          ),
          FilterPattern(
            expression = CompareExpression(
              leftArg = QueryVariable(variableName = "date__valueHasStartJDN"),
              operator = CompareExpressionOperator.GREATER_THAN,
              rightArg = XsdLiteral(
                value = "2455928",
                datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri,
              ),
            ),
          ),
        ).toSeq,
        positiveEntities = Set(),
        querySchema = None,
      ),
      limit = Some(25),
      useDistinct = true,
    )

  val inputQueryWithDateOptionalSortCriterion: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  OPTIONAL {
      |
      |    ?thing onto:hasDate ?date .
      |    onto:hasDate knora-api:objectType knora-api:Date .
      |    ?date a knora-api:Date .
      |
      |  }
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val inputQueryWithDateOptionalSortCriterionComplex: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  OPTIONAL {
      |
      |    ?thing onto:hasDate ?date .
      |
      |  }
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val transformedQueryWithDateOptionalSortCriterion: SelectQuery =
    SelectQuery(
      variables = Vector(
        QueryVariable(variableName = "thing"),
        GroupConcat(
          inputVariable = QueryVariable(variableName = "date"),
          separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
          outputVariableName = "date__Concat",
        ),
      ),
      offset = 0,
      groupBy = Vector(
        QueryVariable(variableName = "thing"),
        QueryVariable(variableName = "date__valueHasStartJDN"),
      ),
      orderBy = Vector(
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "date__valueHasStartJDN"),
          isAscending = false,
        ),
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "thing"),
          isAscending = true,
        ),
      ),
      whereClause = WhereClause(
        patterns = ArrayBuffer(
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
                  iri = "http://www.knora.org/ontology/0001/anything#hasDate".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "date"),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "date"),
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
                subj = QueryVariable(variableName = "date"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "date__valueHasStartJDN"),
              ),
            ),
          ),
        ).toSeq,
        positiveEntities = Set(),
        querySchema = None,
      ),
      limit = Some(25),
      useDistinct = true,
    )

  val inputQueryWithDateOptionalSortCriterionAndFilter: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  OPTIONAL {
      |
      |    ?thing onto:hasDate ?date .
      |    onto:hasDate knora-api:objectType knora-api:Date .
      |    ?date a knora-api:Date .
      |
      |    FILTER(?date > "GREGORIAN:2012-01-01"^^knora-api:Date)
      |  }
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val inputQueryWithDateOptionalSortCriterionAndFilterComplex: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |  ?thing onto:hasDate ?date .
      |} WHERE {
      |
      |  ?thing a knora-api:Resource .
      |  ?thing a onto:Thing .
      |
      |  OPTIONAL {
      |
      |    ?thing onto:hasDate ?date .
      |
      |    FILTER(knora-api:toSimpleDate(?date) > "GREGORIAN:2012-01-01"^^knora-api-simple:Date)
      |  }
      |
      |}
      |ORDER BY DESC(?date)
        """.stripMargin

  val transformedQueryWithDateOptionalSortCriterionAndFilter: SelectQuery =
    SelectQuery(
      variables = Vector(
        QueryVariable(variableName = "thing"),
        GroupConcat(
          inputVariable = QueryVariable(variableName = "date"),
          separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
          outputVariableName = "date__Concat",
        ),
      ),
      offset = 0,
      groupBy = Vector(
        QueryVariable(variableName = "thing"),
        QueryVariable(variableName = "date__valueHasStartJDN"),
      ),
      orderBy = Vector(
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "date__valueHasStartJDN"),
          isAscending = false,
        ),
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "thing"),
          isAscending = true,
        ),
      ),
      whereClause = WhereClause(
        patterns = ArrayBuffer(
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
                  iri = "http://www.knora.org/ontology/0001/anything#hasDate".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "date"),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "date"),
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
                subj = QueryVariable(variableName = "date"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "date__valueHasStartJDN"),
              ),
              FilterPattern(expression =
                CompareExpression(
                  leftArg = QueryVariable(variableName = "date__valueHasStartJDN"),
                  operator = CompareExpressionOperator.GREATER_THAN,
                  rightArg = XsdLiteral(
                    value = "2455928",
                    datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri,
                  ),
                ),
              ),
            ),
          ),
        ).toSeq,
        positiveEntities = Set(),
        querySchema = None,
      ),
      limit = Some(25),
      useDistinct = true,
    )

  val inputQueryWithDecimalOptionalSortCriterion: String =
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
      |     }
      |} ORDER BY ASC(?decimal)
        """.stripMargin

  val inputQueryWithDecimalOptionalSortCriterionComplex: String =
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
      |     }
      |} ORDER BY ASC(?decimal)
        """.stripMargin

  val transformedQueryWithDecimalOptionalSortCriterion: SelectQuery =
    SelectQuery(
      variables = Vector(
        QueryVariable(variableName = "thing"),
        GroupConcat(
          inputVariable = QueryVariable(variableName = "decimal"),
          separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
          outputVariableName = "decimal__Concat",
        ),
      ),
      offset = 0,
      groupBy = Vector(
        QueryVariable(variableName = "thing"),
        QueryVariable(variableName = "decimal__valueHasDecimal"),
      ),
      orderBy = Vector(
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "decimal__valueHasDecimal"),
          isAscending = true,
        ),
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "thing"),
          isAscending = true,
        ),
      ),
      whereClause = WhereClause(
        patterns = ArrayBuffer(
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
            ),
          ),
        ).toSeq,
        positiveEntities = Set(),
        querySchema = None,
      ),
      limit = Some(25),
      useDistinct = true,
    )

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

  val transformedQueryWithDecimalOptionalSortCriterionAndFilter: SelectQuery =
    SelectQuery(
      variables = Vector(
        QueryVariable(variableName = "thing"),
        GroupConcat(
          inputVariable = QueryVariable(variableName = "decimal"),
          separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
          outputVariableName = "decimal__Concat",
        ),
      ),
      offset = 0,
      groupBy = Vector(
        QueryVariable(variableName = "thing"),
        QueryVariable(variableName = "decimal__valueHasDecimal"),
      ),
      orderBy = Vector(
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "decimal__valueHasDecimal"),
          isAscending = true,
        ),
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "thing"),
          isAscending = true,
        ),
      ),
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
        ),
        positiveEntities = Set(),
        querySchema = None,
      ),
      limit = Some(25),
      useDistinct = true,
    )

  val transformedQueryWithDecimalOptionalSortCriterionAndFilterComplex: SelectQuery =
    SelectQuery(
      fromClause = None,
      variables = List(
        QueryVariable(variableName = "thing"),
        GroupConcat(
          inputVariable = QueryVariable(variableName = "decimal"),
          separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
          outputVariableName = "decimal__Concat",
        ),
      ),
      offset = 0,
      groupBy = Vector(
        QueryVariable(variableName = "thing"),
        QueryVariable(variableName = "decimal__valueHasDecimal"),
      ),
      orderBy = Vector(
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "decimal__valueHasDecimal"),
          isAscending = true,
        ),
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "thing"),
          isAscending = true,
        ),
      ),
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
      limit = Some(25),
      useDistinct = true,
    )

  val InputQueryWithRdfsLabelAndLiteralInSimpleSchema: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true .
      |
      |} WHERE {
      |    ?book rdf:type incunabula:book .
      |    ?book rdfs:label "Zeitglöcklein des Lebens und Leidens Christi" .
      |}
        """.stripMargin

  val InputQueryWithRdfsLabelAndLiteralInComplexSchema: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true .
      |
      |} WHERE {
      |    ?book rdf:type incunabula:book .
      |    ?book rdfs:label "Zeitglöcklein des Lebens und Leidens Christi" .
      |}
        """.stripMargin

  val TransformedQueryWithRdfsLabelAndLiteral: SelectQuery = SelectQuery(
    fromClause = None,
    variables = Vector(QueryVariable(variableName = "book")),
    offset = 0,
    groupBy = Vector(QueryVariable(variableName = "book")),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "book"),
        isAscending = true,
      ),
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
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
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
            datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
          ),
        ),
      ),
      positiveEntities = Set(),
      querySchema = None,
    ),
    limit = Some(25),
    useDistinct = true,
  )

  val InputQueryWithRdfsLabelAndVariableInSimpleSchema: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true .
      |
      |} WHERE {
      |    ?book rdf:type incunabula:book .
      |    ?book rdfs:label ?label .
      |    FILTER(?label = "Zeitglöcklein des Lebens und Leidens Christi")
      |}
        """.stripMargin

  val InputQueryWithRdfsLabelAndVariableInComplexSchema: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true .
      |
      |} WHERE {
      |    ?book rdf:type incunabula:book .
      |    ?book rdfs:label ?label .
      |    FILTER(?label = "Zeitglöcklein des Lebens und Leidens Christi")
      |}
        """.stripMargin

  val InputQueryWithRdfsLabelAndRegexInSimpleSchema: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true .
      |
      |} WHERE {
      |    ?book rdf:type incunabula:book .
      |    ?book rdfs:label ?bookLabel .
      |    FILTER regex(?bookLabel, "Zeit", "i")
      |}""".stripMargin

  val InputQueryWithRdfsLabelAndRegexInComplexSchema: String =
    """
      |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |
      |CONSTRUCT {
      |    ?book knora-api:isMainResource true .
      |
      |} WHERE {
      |    ?book rdf:type incunabula:book .
      |    ?book rdfs:label ?bookLabel .
      |    FILTER regex(?bookLabel, "Zeit", "i")
      |}""".stripMargin

  val TransformedQueryWithRdfsLabelAndVariable: SelectQuery = SelectQuery(
    fromClause = None,
    variables = Vector(QueryVariable(variableName = "book")),
    offset = 0,
    groupBy = Vector(QueryVariable(variableName = "book")),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "book"),
        isAscending = true,
      ),
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
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
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "label"),
        ),
        FilterPattern(
          expression = CompareExpression(
            leftArg = QueryVariable(variableName = "label"),
            operator = CompareExpressionOperator.EQUALS,
            rightArg = XsdLiteral(
              value = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
              datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
            ),
          ),
        ),
      ),
      positiveEntities = Set(),
      querySchema = None,
    ),
    limit = Some(25),
    useDistinct = true,
  )

  val TransformedQueryWithRdfsLabelAndRegex: SelectQuery = SelectQuery(
    fromClause = None,
    variables = Vector(QueryVariable(variableName = "book")),
    offset = 0,
    groupBy = Vector(QueryVariable(variableName = "book")),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "book"),
        isAscending = true,
      ),
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
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
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "bookLabel"),
        ),
        FilterPattern(
          expression = RegexFunction(
            textExpr = QueryVariable(variableName = "bookLabel"),
            pattern = "Zeit",
            modifier = Some("i"),
          ),
        ),
      ),
      positiveEntities = Set(),
      querySchema = None,
    ),
    limit = Some(25),
    useDistinct = true,
  )

  val queryWithOptional: String =
    """
      |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
      |
      |CONSTRUCT {
      |    ?document knora-api:isMainResource true .
      |} WHERE {
      |    ?document rdf:type beol:writtenSource .
      |
      |    OPTIONAL {
      |    ?document beol:hasRecipient ?recipient .
      |
      |    ?recipient beol:hasFamilyName ?familyName .
      |
      |    FILTER knora-api:matchText(?familyName, "Bernoulli")
      |}
      |}
                """.stripMargin

  val TransformedQueryWithOptional: SelectQuery = SelectQuery(
    fromClause = None,
    variables = List(QueryVariable(variableName = "document")),
    offset = 0,
    groupBy = Vector(QueryVariable(variableName = "document")),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "document"),
        isAscending = true,
      ),
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "document"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
          ),
        ),
        // This statement must not be removed by AbstractPrequeryGenerator.removeEntitiesInferredFromProperty
        // because the property from which its type can be inferred is in an optional. Without this statement,
        // the type beol:basicLetter (inferred from property beol:hasRecipient) would be considered for ?document.
        StatementPattern(
          subj = QueryVariable(variableName = "document"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/0801/beol#writtenSource".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        OptionalPattern(
          patterns = Vector(
            StatementPattern(
              subj = QueryVariable(variableName = "document"),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/0801/beol#hasRecipient".toSmartIri,
                propertyPathOperator = None,
              ),
              obj = QueryVariable(variableName = "recipient"),
            ),
            StatementPattern(
              subj = QueryVariable(variableName = "document"),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/0801/beol#hasRecipientValue".toSmartIri,
                propertyPathOperator = None,
              ),
              obj = QueryVariable(
                variableName = "document__httpwwwknoraorgontology0801beolhasRecipient__recipient__LinkValue",
              ),
            ),
            StatementPattern(
              subj = QueryVariable(
                variableName = "document__httpwwwknoraorgontology0801beolhasRecipient__recipient__LinkValue",
              ),
              pred = IriRef(
                iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                propertyPathOperator = None,
              ),
              obj = IriRef(
                iri = "http://www.knora.org/ontology/knora-base#LinkValue".toSmartIri,
                propertyPathOperator = None,
              ),
            ),
            StatementPattern(
              subj = QueryVariable(
                variableName = "document__httpwwwknoraorgontology0801beolhasRecipient__recipient__LinkValue",
              ),
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
              subj = QueryVariable(
                variableName = "document__httpwwwknoraorgontology0801beolhasRecipient__recipient__LinkValue",
              ),
              pred = IriRef(
                iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object".toSmartIri,
                propertyPathOperator = None,
              ),
              obj = QueryVariable(variableName = "recipient"),
            ),
            StatementPattern(
              subj = QueryVariable(variableName = "recipient"),
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
              subj = QueryVariable(variableName = "recipient"),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/0801/beol#hasFamilyName".toSmartIri,
                propertyPathOperator = None,
              ),
              obj = QueryVariable(variableName = "familyName"),
            ),
            StatementPattern(
              subj = QueryVariable(variableName = "familyName"),
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
              subj = QueryVariable(variableName = "familyName"),
              pred = IriRef(OntologyConstants.Fuseki.luceneQueryPredicate.toSmartIri),
              obj = XsdLiteral(
                value = "Bernoulli",
                datatype = OntologyConstants.Xsd.String.toSmartIri,
              ),
            ),
          ),
        ),
      ),
      positiveEntities = Set(),
      querySchema = None,
    ),
    limit = Some(25),
    useDistinct = true,
  )

  val InputQueryWithUnionScopes: String =
    """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX onto: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |
      |CONSTRUCT {
      |    ?thing knora-api:isMainResource true .
      |    ?thing onto:hasText ?text .
      |} WHERE {
      |    ?thing a onto:Thing .
      |    ?thing onto:hasText ?text .
      |
      |    {
      |        ?thing onto:hasInteger ?int .
      |        FILTER(?int = 1)
      |    } UNION {
      |        ?thing onto:hasText ?text .
      |        FILTER regex(?text, "Abel", "i") .
      |    }
      |}
      |ORDER BY ASC(?text)
      |OFFSET 0""".stripMargin

  val TransformedQueryWithUnionScopes: SelectQuery = SelectQuery(
    fromClause = None,
    variables = Vector(
      QueryVariable(variableName = "thing"),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "text"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "text__Concat",
      ),
    ),
    offset = 0,
    groupBy = Vector(
      QueryVariable(variableName = "thing"),
      QueryVariable(variableName = "text__valueHasString"),
    ),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "text__valueHasString"),
        isAscending = true,
      ),
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "thing"),
        isAscending = true,
      ),
    ),
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
            iri = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "text"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "text"),
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
          subj = QueryVariable(variableName = "text"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasString".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "text__valueHasString"),
        ),
        UnionPattern(
          blocks = Vector(
            Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "int"),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "int"),
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
                subj = QueryVariable(variableName = "int"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasInteger".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "int__valueHasInteger"),
              ),
              FilterPattern(expression =
                CompareExpression(
                  leftArg = QueryVariable(variableName = "int__valueHasInteger"),
                  operator = CompareExpressionOperator.EQUALS,
                  rightArg = XsdLiteral(
                    value = "1",
                    datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri,
                  ),
                ),
              ),
            ),
            Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "text"),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "text"),
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
                subj = QueryVariable(variableName = "text"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasString".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "text__valueHasString"),
              ),
              FilterPattern(
                expression = RegexFunction(
                  textExpr = QueryVariable(variableName = "text__valueHasString"),
                  pattern = "Abel",
                  modifier = Some("i"),
                ),
              ),
            ),
          ),
        ),
      ),
      positiveEntities = Set(),
      querySchema = None,
    ),
    limit = Some(25),
    useDistinct = true,
  )

  val queryToReorder: String = """
                                 |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
                                 |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
                                 |
                                 |CONSTRUCT {
                                 |  ?letter knora-api:isMainResource true .
                                 |  ?letter ?linkingProp1  ?person1 .
                                 |  ?letter ?linkingProp2  ?person2 .
                                 |  ?letter beol:creationDate ?date .
                                 |} WHERE {
                                 |  ?letter beol:creationDate ?date .
                                 |
                                 |  ?letter ?linkingProp1 ?person1 .
                                 |  FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient )
                                 |
                                 |  ?letter ?linkingProp2 ?person2 .
                                 |  FILTER(?linkingProp2 = beol:hasAuthor || ?linkingProp2 = beol:hasRecipient )
                                 |
                                 |  ?person1 beol:hasIAFIdentifier ?gnd1 .
                                 |  ?gnd1 knora-api:valueAsString "(DE-588)118531379" .
                                 |
                                 |  ?person2 beol:hasIAFIdentifier ?gnd2 .
                                 |  ?gnd2 knora-api:valueAsString "(DE-588)118696149" .
                                 |} ORDER BY ?date""".stripMargin

  val transformedQueryToReorder: SelectQuery = SelectQuery(
    fromClause = None,
    variables = Vector(
      QueryVariable(variableName = "letter"),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "person1"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "person1__Concat",
      ),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "person2"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "person2__Concat",
      ),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "date"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "date__Concat",
      ),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "letter__linkingProp1__person1__LinkValue"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "letter__linkingProp1__person1__LinkValue__Concat",
      ),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "letter__linkingProp2__person2__LinkValue"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "letter__linkingProp2__person2__LinkValue__Concat",
      ),
    ),
    offset = 0,
    groupBy = Vector(
      QueryVariable(variableName = "letter"),
      QueryVariable(variableName = "date__valueHasStartJDN"),
    ),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "date__valueHasStartJDN"),
        isAscending = true,
      ),
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "letter"),
        isAscending = true,
      ),
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
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
          subj = QueryVariable(variableName = "letter"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0801/beol#creationDate".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "date"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "date"),
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
          subj = QueryVariable(variableName = "date"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "date__valueHasStartJDN"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = QueryVariable(variableName = "linkingProp1"),
          obj = QueryVariable(variableName = "person1"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = QueryVariable(variableName = "linkingProp1__hasLinkToValue"),
          obj = QueryVariable(variableName = "letter__linkingProp1__person1__LinkValue"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter__linkingProp1__person1__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#LinkValue".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter__linkingProp1__person1__LinkValue"),
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
          subj = QueryVariable(variableName = "letter__linkingProp1__person1__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "person1"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "person1"),
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
          subj = QueryVariable(variableName = "letter"),
          pred = QueryVariable(variableName = "linkingProp2"),
          obj = QueryVariable(variableName = "person2"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = QueryVariable(variableName = "linkingProp2__hasLinkToValue"),
          obj = QueryVariable(variableName = "letter__linkingProp2__person2__LinkValue"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter__linkingProp2__person2__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#LinkValue".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter__linkingProp2__person2__LinkValue"),
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
          subj = QueryVariable(variableName = "letter__linkingProp2__person2__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "person2"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "person2"),
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
          subj = QueryVariable(variableName = "person1"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0801/beol#hasIAFIdentifier".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "gnd1"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "gnd1"),
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
          subj = QueryVariable(variableName = "person2"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0801/beol#hasIAFIdentifier".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "gnd2"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "gnd2"),
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
          subj = QueryVariable(variableName = "gnd1"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasString".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "(DE-588)118531379",
            datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "gnd2"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasString".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "(DE-588)118696149",
            datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
          ),
        ),
        FilterPattern(
          expression = OrExpression(
            leftArg = CompareExpression(
              leftArg = QueryVariable(variableName = "linkingProp1"),
              operator = CompareExpressionOperator.EQUALS,
              rightArg = IriRef(
                iri = "http://www.knora.org/ontology/0801/beol#hasAuthor".toSmartIri,
                propertyPathOperator = None,
              ),
            ),
            rightArg = CompareExpression(
              leftArg = QueryVariable(variableName = "linkingProp1"),
              operator = CompareExpressionOperator.EQUALS,
              rightArg = IriRef(
                iri = "http://www.knora.org/ontology/0801/beol#hasRecipient".toSmartIri,
                propertyPathOperator = None,
              ),
            ),
          ),
        ),
        FilterPattern(
          expression = OrExpression(
            leftArg = CompareExpression(
              leftArg = QueryVariable(variableName = "linkingProp2"),
              operator = CompareExpressionOperator.EQUALS,
              rightArg = IriRef(
                iri = "http://www.knora.org/ontology/0801/beol#hasAuthor".toSmartIri,
                propertyPathOperator = None,
              ),
            ),
            rightArg = CompareExpression(
              leftArg = QueryVariable(variableName = "linkingProp2"),
              operator = CompareExpressionOperator.EQUALS,
              rightArg = IriRef(
                iri = "http://www.knora.org/ontology/0801/beol#hasRecipient".toSmartIri,
                propertyPathOperator = None,
              ),
            ),
          ),
        ),
      ),
      positiveEntities = Set(),
      querySchema = None,
    ),
    limit = Some(25),
    useDistinct = true,
  )

  val queryToReorderWithCycle: String = """
                                          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
                                          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                                          |
                                          |CONSTRUCT {
                                          |    ?thing knora-api:isMainResource true .
                                          |} WHERE {
                                          |  ?thing anything:hasOtherThing ?thing1 .
                                          |  ?thing1 anything:hasOtherThing ?thing2 .
                                          |  ?thing2 anything:hasOtherThing ?thing .
                                          |} """.stripMargin

  val transformedQueryToReorderWithCycle: SelectQuery = SelectQuery(
    offset = 0,
    groupBy = Vector(QueryVariable(variableName = "thing")),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "thing"),
        isAscending = true,
      ),
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "thing2"),
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
          subj = QueryVariable(variableName = "thing2"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "thing"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing2"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#hasOtherThingValue".toSmartIri,
            propertyPathOperator = None,
          ),
          obj =
            QueryVariable(variableName = "thing2__httpwwwknoraorgontology0001anythinghasOtherThing__thing__LinkValue"),
        ),
        StatementPattern(
          subj =
            QueryVariable(variableName = "thing2__httpwwwknoraorgontology0001anythinghasOtherThing__thing__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#LinkValue".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj =
            QueryVariable(variableName = "thing2__httpwwwknoraorgontology0001anythinghasOtherThing__thing__LinkValue"),
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
          subj =
            QueryVariable(variableName = "thing2__httpwwwknoraorgontology0001anythinghasOtherThing__thing__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "thing"),
        ),
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
            iri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "thing1"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#hasOtherThingValue".toSmartIri,
            propertyPathOperator = None,
          ),
          obj =
            QueryVariable(variableName = "thing__httpwwwknoraorgontology0001anythinghasOtherThing__thing1__LinkValue"),
        ),
        StatementPattern(
          subj =
            QueryVariable(variableName = "thing__httpwwwknoraorgontology0001anythinghasOtherThing__thing1__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#LinkValue".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj =
            QueryVariable(variableName = "thing__httpwwwknoraorgontology0001anythinghasOtherThing__thing1__LinkValue"),
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
          subj =
            QueryVariable(variableName = "thing__httpwwwknoraorgontology0001anythinghasOtherThing__thing1__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "thing1"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing1"),
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
          subj = QueryVariable(variableName = "thing1"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "thing2"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing1"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#hasOtherThingValue".toSmartIri,
            propertyPathOperator = None,
          ),
          obj =
            QueryVariable(variableName = "thing1__httpwwwknoraorgontology0001anythinghasOtherThing__thing2__LinkValue"),
        ),
        StatementPattern(
          subj =
            QueryVariable(variableName = "thing1__httpwwwknoraorgontology0001anythinghasOtherThing__thing2__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#LinkValue".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj =
            QueryVariable(variableName = "thing1__httpwwwknoraorgontology0001anythinghasOtherThing__thing2__LinkValue"),
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
          subj =
            QueryVariable(variableName = "thing1__httpwwwknoraorgontology0001anythinghasOtherThing__thing2__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "thing2"),
        ),
      ),
      positiveEntities = Set(),
      querySchema = None,
    ),
    limit = Some(25),
    useDistinct = true,
    fromClause = None,
    variables = Vector(QueryVariable(variableName = "thing")),
  )

  val queryToReorderWithMinus: String =
    """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |
      |CONSTRUCT {
      |  ?thing knora-api:isMainResource true .
      |} WHERE {
      |  ?thing a knora-api:Resource .
      |  ?thing a anything:Thing .
      |  MINUS {
      |    ?thing anything:hasInteger ?intVal .
      |    ?intVal a xsd:integer .
      |    FILTER(?intVal = 123454321 || ?intVal = 999999999)
      |  }
      |}""".stripMargin

  val transformedQueryToReorderWithMinus: SelectQuery = SelectQuery(
    fromClause = None,
    variables = Vector(QueryVariable(variableName = "thing")),
    offset = 0,
    groupBy = Vector(QueryVariable(variableName = "thing")),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "thing"),
        isAscending = true,
      ),
    ),
    whereClause = WhereClause(
      patterns = Vector(
        MinusPattern(patterns =
          Vector(
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
                iri = "http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri,
                propertyPathOperator = None,
              ),
              obj = QueryVariable(variableName = "intVal"),
            ),
            StatementPattern(
              subj = QueryVariable(variableName = "intVal"),
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
              subj = QueryVariable(variableName = "intVal"),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/knora-base#valueHasInteger".toSmartIri,
                propertyPathOperator = None,
              ),
              obj = QueryVariable(variableName = "intVal__valueHasInteger"),
            ),
            FilterPattern(expression =
              OrExpression(
                leftArg = CompareExpression(
                  leftArg = QueryVariable(variableName = "intVal__valueHasInteger"),
                  operator = CompareExpressionOperator.EQUALS,
                  rightArg = XsdLiteral(
                    value = "123454321",
                    datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri,
                  ),
                ),
                rightArg = CompareExpression(
                  leftArg = QueryVariable(variableName = "intVal__valueHasInteger"),
                  operator = CompareExpressionOperator.EQUALS,
                  rightArg = XsdLiteral(
                    value = "999999999",
                    datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri,
                  ),
                ),
              ),
            ),
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
      ),
      positiveEntities = Set(),
      querySchema = None,
    ),
    limit = Some(25),
    useDistinct = true,
  )

  val queryToReorderWithUnion: String =
    s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
       |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
       |CONSTRUCT {
       |    ?thing knora-api:isMainResource true .
       |    ?thing anything:hasInteger ?int .
       |    ?thing anything:hasRichtext ?richtext .
       |    ?thing anything:hasText ?text .
       |} WHERE {
       |    ?thing a knora-api:Resource .
       |    ?thing a anything:Thing .
       |    ?thing anything:hasInteger ?int .
       |
       |    {
       |        ?thing anything:hasRichtext ?richtext .
       |        FILTER knora-api:matchText(?richtext, "test")
       |
       |		    ?thing anything:hasInteger ?int .
       |		    ?int knora-api:intValueAsInt 1 .
       |    }
       |    UNION
       |    {
       |        ?thing anything:hasText ?text .
       |        FILTER knora-api:matchText(?text, "test")
       |
       |		    ?thing anything:hasInteger ?int .
       |		    ?int knora-api:intValueAsInt 3 .
       |    }
       |}
       |ORDER BY (?int)""".stripMargin

  val transformedQueryToReorderWithUnion: SelectQuery = SelectQuery(
    fromClause = None,
    variables = Vector(
      QueryVariable(variableName = "thing"),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "int"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "int__Concat",
      ),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "richtext"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "richtext__Concat",
      ),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "text"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "text__Concat",
      ),
    ),
    offset = 0,
    groupBy = Vector(
      QueryVariable(variableName = "thing"),
      QueryVariable(variableName = "int__valueHasInteger"),
    ),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "int__valueHasInteger"),
        isAscending = true,
      ),
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "thing"),
        isAscending = true,
      ),
    ),
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
            iri = "http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "int"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "int"),
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
          subj = QueryVariable(variableName = "int"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasInteger".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "int__valueHasInteger"),
        ),
        UnionPattern(
          blocks = Vector(
            Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasRichtext".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "richtext"),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "richtext"),
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
                  iri = "http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "int"),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "int"),
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
                subj = QueryVariable(variableName = "int"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasInteger".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "int__valueHasInteger"),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "int"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasInteger".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = XsdLiteral(
                  value = "1",
                  datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri,
                ),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "richtext"),
                pred = IriRef(OntologyConstants.Fuseki.luceneQueryPredicate.toSmartIri),
                obj = XsdLiteral(
                  value = "test",
                  datatype = OntologyConstants.Xsd.String.toSmartIri,
                ),
              ),
            ),
            Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "text"),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "text"),
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
                  iri = "http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "int"),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "int"),
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
                subj = QueryVariable(variableName = "int"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasInteger".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "int__valueHasInteger"),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "int"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasInteger".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = XsdLiteral(
                  value = "3",
                  datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri,
                ),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "text"),
                pred = IriRef(OntologyConstants.Fuseki.luceneQueryPredicate.toSmartIri),
                obj = XsdLiteral(
                  value = "test",
                  datatype = OntologyConstants.Xsd.String.toSmartIri,
                ),
              ),
            ),
          ),
        ),
      ),
      positiveEntities = Set(),
      querySchema = None,
    ),
    limit = Some(25),
    useDistinct = true,
  )

  val queryWithStandoffTagHasStartAncestor: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
      |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |
      |CONSTRUCT {
      |    ?thing knora-api:isMainResource true .
      |    ?thing anything:hasText ?text .
      |} WHERE {
      |    ?thing a anything:Thing .
      |    ?thing anything:hasText ?text .
      |    ?text knora-api:textValueHasStandoff ?standoffDateTag .
      |    ?standoffDateTag a knora-api:StandoffDateTag .
      |    FILTER(knora-api:toSimpleDate(?standoffDateTag) = "GREGORIAN:2016-12-24 CE"^^knora-api-simple:Date)
      |    ?standoffDateTag knora-api:standoffTagHasStartAncestor ?standoffParagraphTag .
      |    ?standoffParagraphTag a standoff:StandoffParagraphTag .
      |}""".stripMargin

  val transformedQueryWithStandoffTagHasStartAncestor: SelectQuery = SelectQuery(
    fromClause = None,
    variables = Vector(
      QueryVariable(variableName = "thing"),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "text"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "text__Concat",
      ),
    ),
    offset = 0,
    groupBy = Vector(QueryVariable(variableName = "thing")),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "thing"),
        isAscending = true,
      ),
    ),
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
            iri = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "text"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "text"),
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
          subj = QueryVariable(variableName = "text"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasStandoff".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "standoffDateTag"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "standoffDateTag"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#StandoffDateTag".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "standoffDateTag"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#standoffTagHasStartAncestor".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "standoffParagraphTag"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "standoffParagraphTag"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/standoff#StandoffParagraphTag".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "standoffDateTag"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "standoffDateTag__valueHasStartJDN"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "standoffDateTag"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasEndJDN".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "standoffDateTag__valueHasEndJDN"),
        ),
        FilterPattern(
          expression = AndExpression(
            leftArg = CompareExpression(
              leftArg = XsdLiteral(
                value = "2457747",
                datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri,
              ),
              operator = CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO,
              rightArg = QueryVariable(variableName = "standoffDateTag__valueHasEndJDN"),
            ),
            rightArg = CompareExpression(
              leftArg = XsdLiteral(
                value = "2457747",
                datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri,
              ),
              operator = CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO,
              rightArg = QueryVariable(variableName = "standoffDateTag__valueHasStartJDN"),
            ),
          ),
        ),
      ),
      positiveEntities = Set(),
      querySchema = None,
    ),
    limit = Some(25),
    useDistinct = true,
  )

  val queryWithKnoraApiResource: String =
    """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |CONSTRUCT {
      |    ?resource knora-api:isMainResource true .
      |    ?resource ?p ?text .
      |} WHERE {
      |    ?resource a knora-api:Resource .
      |    ?resource ?p ?text .
      |    ?p knora-api:objectType knora-api:TextValue .
      |    FILTER knora-api:matchText(?text, "der")
      |}""".stripMargin

  override val e2eSpec = suite("The NonTriplestoreSpecificGravsearchToPrequeryGenerator object")(
    test("transform an input query with an optional property criterion without removing the rdf:type statement") {
      transformQuery(queryWithOptional)
        .map(actual => assertTrue(actual == TransformedQueryWithOptional))
    },
    test("transform an input query with a date as a non optional sort criterion") {
      transformQuery(inputQueryWithDateNonOptionalSortCriterion)
        .map(actual => assertTrue(actual == transformedQueryWithDateNonOptionalSortCriterion))
    },
    test("transform an input query with a date as a non optional sort criterion (submitted in complex schema)") {
      transformQuery(inputQueryWithDateNonOptionalSortCriterionComplex)
        .map(actual => assertTrue(actual == transformedQueryWithDateNonOptionalSortCriterion))
    },
    test("transform an input query with a date as non optional sort criterion and a filter") {
      transformQuery(inputQueryWithDateNonOptionalSortCriterionAndFilter)
        .map(actual => assertTrue(actual == transformedQueryWithDateNonOptionalSortCriterionAndFilter))
    },
    test(
      "transform an input query with a date as non optional sort criterion and a filter (submitted in complex schema)",
    ) {
      transformQuery(inputQueryWithDateNonOptionalSortCriterionAndFilterComplex)
        .map(actual => assertTrue(actual == transformedQueryWithDateNonOptionalSortCriterionAndFilter))
    },
    test("transform an input query with a date as an optional sort criterion") {
      transformQuery(inputQueryWithDateOptionalSortCriterion)
        .map(actual => assertTrue(actual == transformedQueryWithDateOptionalSortCriterion))
    },
    test("transform an input query with a date as an optional sort criterion (submitted in complex schema)") {
      transformQuery(inputQueryWithDateOptionalSortCriterionComplex)
        .map(actual => assertTrue(actual == transformedQueryWithDateOptionalSortCriterion))
    },
    test("transform an input query with a date as an optional sort criterion and a filter") {
      transformQuery(inputQueryWithDateOptionalSortCriterionAndFilter)
        .map(actual => assertTrue(actual == transformedQueryWithDateOptionalSortCriterionAndFilter))
    },
    test(
      "transform an input query with a date as an optional sort criterion and a filter (submitted in complex schema)",
    ) {
      transformQuery(inputQueryWithDateOptionalSortCriterionAndFilterComplex)
        .map(actual => assertTrue(actual == transformedQueryWithDateOptionalSortCriterionAndFilter))
    },
    test("transform an input query with a decimal as an optional sort criterion") {
      transformQuery(inputQueryWithDecimalOptionalSortCriterion)
        .map(actual => assertTrue(actual == transformedQueryWithDecimalOptionalSortCriterion))
    },
    test("transform an input query with a decimal as an optional sort criterion (submitted in complex schema)") {
      transformQuery(inputQueryWithDecimalOptionalSortCriterionComplex)
        .map(actual => assertTrue(actual == transformedQueryWithDecimalOptionalSortCriterion))
    },
    test("transform an input query with a decimal as an optional sort criterion and a filter") {
      transformQuery(inputQueryWithDecimalOptionalSortCriterionAndFilter)
        .map(actual => assertTrue(actual == transformedQueryWithDecimalOptionalSortCriterionAndFilter))
    },
    test(
      "transform an input query with a decimal as an optional sort criterion and a filter (submitted in complex schema)",
    ) {
      transformQuery(inputQueryWithDecimalOptionalSortCriterionAndFilterComplex)
        .map(actual => assertTrue(actual == transformedQueryWithDecimalOptionalSortCriterionAndFilterComplex))
    },
    test("transform an input query using rdfs:label and a literal in the simple schema") {
      transformQuery(InputQueryWithRdfsLabelAndLiteralInSimpleSchema)
        .map(actual => assertTrue(actual == TransformedQueryWithRdfsLabelAndLiteral))
    },
    test("transform an input query using rdfs:label and a literal in the complex schema") {
      transformQuery(InputQueryWithRdfsLabelAndLiteralInComplexSchema)
        .map(actual => assertTrue(actual == TransformedQueryWithRdfsLabelAndLiteral))
    },
    test("transform an input query using rdfs:label and a variable in the simple schema") {
      transformQuery(InputQueryWithRdfsLabelAndVariableInSimpleSchema)
        .map(actual => assertTrue(actual == TransformedQueryWithRdfsLabelAndVariable))
    },
    test("transform an input query using rdfs:label and a variable in the complex schema") {
      transformQuery(InputQueryWithRdfsLabelAndVariableInComplexSchema)
        .map(actual => assertTrue(actual == TransformedQueryWithRdfsLabelAndVariable))
    },
    test("transform an input query using rdfs:label and a regex in the simple schema") {
      transformQuery(InputQueryWithRdfsLabelAndRegexInSimpleSchema)
        .map(actual => assertTrue(actual == TransformedQueryWithRdfsLabelAndRegex))
    },
    test("transform an input query using rdfs:label and a regex in the complex schema") {
      transformQuery(InputQueryWithRdfsLabelAndRegexInComplexSchema)
        .map(actual => assertTrue(actual == TransformedQueryWithRdfsLabelAndRegex))
    },
    test("transform an input query with UNION scopes in the simple schema") {
      transformQuery(InputQueryWithUnionScopes)
        .map(actual => assertTrue(actual == TransformedQueryWithUnionScopes))
    },
    test("transform an input query with knora-api:standoffTagHasStartAncestor") {
      transformQuery(queryWithStandoffTagHasStartAncestor)
        .map(actual => assertTrue(actual == transformedQueryWithStandoffTagHasStartAncestor))
    },
    test("reorder query patterns in where clause") {
      transformQuery(queryToReorder)
        .map(actual =>
          assertTrue(
            actual.variables.toSet == transformedQueryToReorder.variables.toSet,
            actual.copy(variables = Vector()) == transformedQueryToReorder.copy(variables = Vector()),
          ),
        )
    },
    test("reorder query patterns in where clause with union") {
      transformQuery(queryToReorderWithUnion)
        .map(actual =>
          assertTrue(
            actual.variables.toSet == transformedQueryToReorderWithUnion.variables.toSet,
            actual.copy(variables = Vector()) == transformedQueryToReorderWithUnion.copy(variables = Vector()),
          ),
        )
    },
    test("reorder query patterns in where clause with optional") {
      transformQuery(queryWithOptional)
        .map(actual => assertTrue(actual == TransformedQueryWithOptional))
    },
    test("reorder query patterns with minus scope") {
      transformQuery(queryToReorderWithMinus)
        .map(actual => assertTrue(actual == transformedQueryToReorderWithMinus))
    },
    test("reorder a query with a cycle") {
      transformQuery(queryToReorderWithCycle)
        .map(actual => assertTrue(actual == transformedQueryToReorderWithCycle))
    },
    test("not remove rdf:type knora-api:Resource if it's needed") {
      transformQuery(queryWithKnoraApiResource)
        .map(actual =>
          assertTrue(
            actual.whereClause.patterns.contains(
              StatementPattern(
                subj = QueryVariable(variableName = "resource"),
                pred = IriRef(
                  iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri,
                  propertyPathOperator = None,
                ),
              ),
            ),
          ),
        )
    },
  )
}
