package org.knora.webapi.messages.util.search.gravsearch.prequery

import scala.collection.mutable.ArrayBuffer
import dsp.errors.AssertionException
import zio.ZIO

import org.knora.webapi.CoreSpec
import org.knora.webapi.core.MessageRelay
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.util.search.gravsearch.GravsearchQueryChecker
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionUtil
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM.anythingAdminUser
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

class NonTriplestoreSpecificGravsearchToPrequeryTransformerSpec extends CoreSpec {

  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

  private def transformQuery(query: String): SelectQuery = {
    val inspectionRunner = for {
      qt <- ZIO.service[QueryTraverser]
      mr <- ZIO.service[MessageRelay]
      sf <- ZIO.service[StringFormatter]
    } yield new GravsearchTypeInspectionRunner(inferTypes = true, qt, mr, sf)

    val preQueryZio = for {
      constructQuery       <- ZIO.attempt(GravsearchParser.parseQuery(query))
      constructClause       = constructQuery.constructClause
      whereClause           = constructQuery.whereClause
      querySchemaMaybe      = constructQuery.querySchema
      sanitizedWhereClause <- ZIO.serviceWithZIO[GravsearchTypeInspectionUtil](_.removeTypeAnnotations(whereClause))
      typeInspectionResult <- inspectionRunner.flatMap(_.inspectTypes(whereClause, anythingAdminUser))
      _                    <- ZIO.attempt(GravsearchQueryChecker.checkConstructClause(constructClause, typeInspectionResult))
      querySchema          <- ZIO.fromOption(querySchemaMaybe).orElseFail(AssertionException(s"WhereClause has no querySchema"))
      transformer = new NonTriplestoreSpecificGravsearchToPrequeryTransformer(
                      constructClause = constructClause,
                      typeInspectionResult = typeInspectionResult,
                      querySchema = querySchema,
                      appConfig = appConfig
                    )
      preQuery <-
        ZIO.serviceWithZIO[QueryTraverser](
          _.transformConstructToSelect(constructQuery.copy(whereClause = sanitizedWhereClause), transformer)
        )
    } yield preQuery
    UnsafeZioRun.runOrThrow(preQueryZio)
  }

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
          outputVariableName = "date__Concat"
        )
      ),
      offset = 0,
      groupBy = Vector(
        QueryVariable(variableName = "thing"),
        QueryVariable(variableName = "date__valueHasStartJDN")
      ),
      orderBy = Vector(
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "date__valueHasStartJDN"),
          isAscending = false
        ),
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "thing"),
          isAscending = true
        )
      ),
      whereClause = WhereClause(
        patterns = ArrayBuffer(
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
              propertyPathOperator = None
            ),
            obj = XsdLiteral(
              value = "false",
              datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
            ),
            namedGraph = Some(
              IriRef(
                iri = "http://www.knora.org/explicit".toSmartIri,
                propertyPathOperator = None
              )
            )
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/0001/anything#hasDate".toSmartIri,
              propertyPathOperator = None
            ),
            obj = QueryVariable(variableName = "date"),
            namedGraph = None
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "date"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
              propertyPathOperator = None
            ),
            obj = XsdLiteral(
              value = "false",
              datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
            ),
            namedGraph = Some(
              IriRef(
                iri = "http://www.knora.org/explicit".toSmartIri,
                propertyPathOperator = None
              )
            )
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "date"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
              propertyPathOperator = None
            ),
            obj = QueryVariable(variableName = "date__valueHasStartJDN"),
            namedGraph = Some(
              IriRef(
                iri = "http://www.knora.org/explicit".toSmartIri,
                propertyPathOperator = None
              )
            )
          )
        ).toSeq,
        positiveEntities = Set(),
        querySchema = None
      ),
      limit = Some(25),
      useDistinct = true
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
          outputVariableName = "date__Concat"
        )
      ),
      offset = 0,
      groupBy = Vector(
        QueryVariable(variableName = "thing"),
        QueryVariable(variableName = "date__valueHasStartJDN")
      ),
      orderBy = Vector(
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "date__valueHasStartJDN"),
          isAscending = false
        ),
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "thing"),
          isAscending = true
        )
      ),
      whereClause = WhereClause(
        patterns = ArrayBuffer(
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
              propertyPathOperator = None
            ),
            obj = XsdLiteral(
              value = "false",
              datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
            ),
            namedGraph = Some(
              IriRef(
                iri = "http://www.knora.org/explicit".toSmartIri,
                propertyPathOperator = None
              )
            )
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/0001/anything#hasDate".toSmartIri,
              propertyPathOperator = None
            ),
            obj = QueryVariable(variableName = "date"),
            namedGraph = None
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "date"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
              propertyPathOperator = None
            ),
            obj = XsdLiteral(
              value = "false",
              datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
            ),
            namedGraph = Some(
              IriRef(
                iri = "http://www.knora.org/explicit".toSmartIri,
                propertyPathOperator = None
              )
            )
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "date"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
              propertyPathOperator = None
            ),
            obj = QueryVariable(variableName = "date__valueHasStartJDN"),
            namedGraph = Some(
              IriRef(
                iri = "http://www.knora.org/explicit".toSmartIri,
                propertyPathOperator = None
              )
            )
          ),
          FilterPattern(
            expression = CompareExpression(
              leftArg = QueryVariable(variableName = "date__valueHasStartJDN"),
              operator = CompareExpressionOperator.GREATER_THAN,
              rightArg = XsdLiteral(
                value = "2455928",
                datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
              )
            )
          )
        ).toSeq,
        positiveEntities = Set(),
        querySchema = None
      ),
      limit = Some(25),
      useDistinct = true
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
          outputVariableName = "date__Concat"
        )
      ),
      offset = 0,
      groupBy = Vector(
        QueryVariable(variableName = "thing"),
        QueryVariable(variableName = "date__valueHasStartJDN")
      ),
      orderBy = Vector(
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "date__valueHasStartJDN"),
          isAscending = false
        ),
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "thing"),
          isAscending = true
        )
      ),
      whereClause = WhereClause(
        patterns = ArrayBuffer(
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
              propertyPathOperator = None
            ),
            obj = XsdLiteral(
              value = "false",
              datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
            ),
            namedGraph = Some(
              IriRef(
                iri = "http://www.knora.org/explicit".toSmartIri,
                propertyPathOperator = None
              )
            )
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              propertyPathOperator = None
            ),
            obj = IriRef(
              iri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
              propertyPathOperator = None
            ),
            namedGraph = None
          ),
          OptionalPattern(
            patterns = Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasDate".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "date"),
                namedGraph = None
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "date"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = XsdLiteral(
                  value = "false",
                  datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "date"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "date__valueHasStartJDN"),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              )
            )
          )
        ).toSeq,
        positiveEntities = Set(),
        querySchema = None
      ),
      limit = Some(25),
      useDistinct = true
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
          outputVariableName = "date__Concat"
        )
      ),
      offset = 0,
      groupBy = Vector(
        QueryVariable(variableName = "thing"),
        QueryVariable(variableName = "date__valueHasStartJDN")
      ),
      orderBy = Vector(
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "date__valueHasStartJDN"),
          isAscending = false
        ),
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "thing"),
          isAscending = true
        )
      ),
      whereClause = WhereClause(
        patterns = ArrayBuffer(
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
              propertyPathOperator = None
            ),
            obj = XsdLiteral(
              value = "false",
              datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
            ),
            namedGraph = Some(
              IriRef(
                iri = "http://www.knora.org/explicit".toSmartIri,
                propertyPathOperator = None
              )
            )
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              propertyPathOperator = None
            ),
            obj = IriRef(
              iri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
              propertyPathOperator = None
            ),
            namedGraph = None
          ),
          OptionalPattern(
            patterns = Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasDate".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "date"),
                namedGraph = None
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "date"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = XsdLiteral(
                  value = "false",
                  datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "date"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "date__valueHasStartJDN"),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              FilterPattern(expression =
                CompareExpression(
                  leftArg = QueryVariable(variableName = "date__valueHasStartJDN"),
                  operator = CompareExpressionOperator.GREATER_THAN,
                  rightArg = XsdLiteral(
                    value = "2455928",
                    datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
                  )
                )
              )
            )
          )
        ).toSeq,
        positiveEntities = Set(),
        querySchema = None
      ),
      limit = Some(25),
      useDistinct = true
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
          outputVariableName = "decimal__Concat"
        )
      ),
      offset = 0,
      groupBy = Vector(
        QueryVariable(variableName = "thing"),
        QueryVariable(variableName = "decimal__valueHasDecimal")
      ),
      orderBy = Vector(
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "decimal__valueHasDecimal"),
          isAscending = true
        ),
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "thing"),
          isAscending = true
        )
      ),
      whereClause = WhereClause(
        patterns = ArrayBuffer(
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
              propertyPathOperator = None
            ),
            obj = XsdLiteral(
              value = "false",
              datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
            ),
            namedGraph = Some(
              IriRef(
                iri = "http://www.knora.org/explicit".toSmartIri,
                propertyPathOperator = None
              )
            )
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              propertyPathOperator = None
            ),
            obj = IriRef(
              iri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
              propertyPathOperator = None
            ),
            namedGraph = None
          ),
          OptionalPattern(
            patterns = Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasDecimal".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "decimal"),
                namedGraph = None
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "decimal"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = XsdLiteral(
                  value = "false",
                  datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "decimal"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasDecimal".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "decimal__valueHasDecimal"),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              )
            )
          )
        ).toSeq,
        positiveEntities = Set(),
        querySchema = None
      ),
      limit = Some(25),
      useDistinct = true
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
          outputVariableName = "decimal__Concat"
        )
      ),
      offset = 0,
      groupBy = Vector(
        QueryVariable(variableName = "thing"),
        QueryVariable(variableName = "decimal__valueHasDecimal")
      ),
      orderBy = Vector(
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "decimal__valueHasDecimal"),
          isAscending = true
        ),
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "thing"),
          isAscending = true
        )
      ),
      whereClause = WhereClause(
        patterns = Vector(
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
              propertyPathOperator = None
            ),
            obj = XsdLiteral(
              value = "false",
              datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
            ),
            namedGraph = Some(
              IriRef(
                iri = "http://www.knora.org/explicit".toSmartIri,
                propertyPathOperator = None
              )
            )
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              propertyPathOperator = None
            ),
            obj = IriRef(
              iri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
              propertyPathOperator = None
            ),
            namedGraph = None
          ),
          OptionalPattern(
            patterns = Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasDecimal".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "decimal"),
                namedGraph = None
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "decimal"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = XsdLiteral(
                  value = "false",
                  datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "decimal"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasDecimal".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "decimal__valueHasDecimal"),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              FilterPattern(expression =
                CompareExpression(
                  leftArg = QueryVariable(variableName = "decimal__valueHasDecimal"),
                  operator = CompareExpressionOperator.GREATER_THAN,
                  rightArg = XsdLiteral(
                    value = "2",
                    datatype = "http://www.w3.org/2001/XMLSchema#decimal".toSmartIri
                  )
                )
              )
            )
          )
        ),
        positiveEntities = Set(),
        querySchema = None
      ),
      limit = Some(25),
      useDistinct = true
    )

  val transformedQueryWithDecimalOptionalSortCriterionAndFilterComplex: SelectQuery =
    SelectQuery(
      fromClause = None,
      variables = Vector(
        QueryVariable(variableName = "thing"),
        GroupConcat(
          inputVariable = QueryVariable(variableName = "decimal"),
          separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
          outputVariableName = "decimal__Concat"
        )
      ),
      offset = 0,
      groupBy = Vector(
        QueryVariable(variableName = "thing"),
        QueryVariable(variableName = "decimal__valueHasDecimal")
      ),
      orderBy = Vector(
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "decimal__valueHasDecimal"),
          isAscending = true
        ),
        OrderCriterion(
          queryVariable = QueryVariable(variableName = "thing"),
          isAscending = true
        )
      ),
      whereClause = WhereClause(
        patterns = Vector(
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
              propertyPathOperator = None
            ),
            obj = XsdLiteral(
              value = "false",
              datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
            ),
            namedGraph = Some(
              IriRef(
                iri = "http://www.knora.org/explicit".toSmartIri,
                propertyPathOperator = None
              )
            )
          ),
          StatementPattern(
            subj = QueryVariable(variableName = "thing"),
            pred = IriRef(
              iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              propertyPathOperator = None
            ),
            obj = IriRef(
              iri = "http://www.knora.org/ontology/0001/anything#Thing".toSmartIri,
              propertyPathOperator = None
            ),
            namedGraph = None
          ),
          OptionalPattern(
            patterns = Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "decimal"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasDecimal".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "decimalVal"),
                namedGraph = None
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasDecimal".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "decimal"),
                namedGraph = None
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "decimal"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = XsdLiteral(
                  value = "false",
                  datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "decimal"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasDecimal".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "decimal__valueHasDecimal"),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              FilterPattern(expression =
                CompareExpression(
                  leftArg = QueryVariable(variableName = "decimalVal"),
                  operator = CompareExpressionOperator.GREATER_THAN,
                  rightArg = XsdLiteral(
                    value = "2",
                    datatype = "http://www.w3.org/2001/XMLSchema#decimal".toSmartIri
                  )
                )
              )
            )
          )
        ),
        positiveEntities = Set(),
        querySchema = None
      ),
      limit = Some(25),
      useDistinct = true
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
        isAscending = true
      )
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
            datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
          ),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
            propertyPathOperator = None
          ),
          namedGraph = None
        )
      ),
      positiveEntities = Set(),
      querySchema = None
    ),
    limit = Some(25),
    useDistinct = true
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
        isAscending = true
      )
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "label"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
            propertyPathOperator = None
          ),
          namedGraph = None
        ),
        FilterPattern(
          expression = CompareExpression(
            leftArg = QueryVariable(variableName = "label"),
            operator = CompareExpressionOperator.EQUALS,
            rightArg = XsdLiteral(
              value = "Zeitgl\u00F6cklein des Lebens und Leidens Christi",
              datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
            )
          )
        )
      ),
      positiveEntities = Set(),
      querySchema = None
    ),
    limit = Some(25),
    useDistinct = true
  )

  val TransformedQueryWithRdfsLabelAndRegex: SelectQuery = SelectQuery(
    fromClause = None,
    variables = Vector(QueryVariable(variableName = "book")),
    offset = 0,
    groupBy = Vector(QueryVariable(variableName = "book")),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "book"),
        isAscending = true
      )
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "bookLabel"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/0803/incunabula#book".toSmartIri,
            propertyPathOperator = None
          ),
          namedGraph = None
        ),
        FilterPattern(
          expression = RegexFunction(
            textExpr = QueryVariable(variableName = "bookLabel"),
            pattern = "Zeit",
            modifier = Some("i")
          )
        )
      ),
      positiveEntities = Set(),
      querySchema = None
    ),
    limit = Some(25),
    useDistinct = true
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
    variables = Vector(QueryVariable(variableName = "document")),
    offset = 0,
    groupBy = Vector(QueryVariable(variableName = "document")),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "document"),
        isAscending = true
      )
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "document"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        // This statement must not be removed by AbstractPrequeryGenerator.removeEntitiesInferredFromProperty
        // because the property from which its type can be inferred is in an optional. Without this statement,
        // the type beol:basicLetter (inferred from property beol:hasRecipient) would be considered for ?document.
        StatementPattern(
          subj = QueryVariable(variableName = "document"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/0801/beol#writtenSource".toSmartIri,
            propertyPathOperator = None
          ),
          namedGraph = None
        ),
        OptionalPattern(
          patterns = Vector(
            StatementPattern(
              subj = QueryVariable(variableName = "recipient"),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                propertyPathOperator = None
              ),
              obj = XsdLiteral(
                value = "false",
                datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
              ),
              namedGraph = Some(
                IriRef(
                  iri = "http://www.knora.org/explicit".toSmartIri,
                  propertyPathOperator = None
                )
              )
            ),
            StatementPattern(
              subj = QueryVariable(variableName = "recipient"),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/0801/beol#hasFamilyName".toSmartIri,
                propertyPathOperator = None
              ),
              obj = QueryVariable(variableName = "familyName"),
              namedGraph = None
            ),
            StatementPattern(
              subj = QueryVariable(variableName = "familyName"),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                propertyPathOperator = None
              ),
              obj = XsdLiteral(
                value = "false",
                datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
              ),
              namedGraph = Some(
                IriRef(
                  iri = "http://www.knora.org/explicit".toSmartIri,
                  propertyPathOperator = None
                )
              )
            ),
            StatementPattern(
              subj = QueryVariable(variableName = "document"),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/0801/beol#hasRecipient".toSmartIri,
                propertyPathOperator = None
              ),
              obj = QueryVariable(variableName = "recipient"),
              namedGraph = None
            ),
            StatementPattern(
              subj = QueryVariable(variableName = "document"),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/0801/beol#hasRecipientValue".toSmartIri,
                propertyPathOperator = None
              ),
              obj = QueryVariable(
                variableName = "document__httpwwwknoraorgontology0801beolhasRecipient__recipient__LinkValue"
              ),
              namedGraph = None
            ),
            StatementPattern(
              subj = QueryVariable(
                variableName = "document__httpwwwknoraorgontology0801beolhasRecipient__recipient__LinkValue"
              ),
              pred = IriRef(
                iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                propertyPathOperator = None
              ),
              obj = IriRef(
                iri = "http://www.knora.org/ontology/knora-base#LinkValue".toSmartIri,
                propertyPathOperator = None
              ),
              namedGraph = Some(
                IriRef(
                  iri = "http://www.knora.org/explicit".toSmartIri,
                  propertyPathOperator = None
                )
              )
            ),
            StatementPattern(
              subj = QueryVariable(
                variableName = "document__httpwwwknoraorgontology0801beolhasRecipient__recipient__LinkValue"
              ),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                propertyPathOperator = None
              ),
              obj = XsdLiteral(
                value = "false",
                datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
              ),
              namedGraph = Some(
                IriRef(
                  iri = "http://www.knora.org/explicit".toSmartIri,
                  propertyPathOperator = None
                )
              )
            ),
            StatementPattern(
              subj = QueryVariable(
                variableName = "document__httpwwwknoraorgontology0801beolhasRecipient__recipient__LinkValue"
              ),
              pred = IriRef(
                iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object".toSmartIri,
                propertyPathOperator = None
              ),
              obj = QueryVariable(variableName = "recipient"),
              namedGraph = Some(
                IriRef(
                  iri = "http://www.knora.org/explicit".toSmartIri,
                  propertyPathOperator = None
                )
              )
            ),
            LuceneQueryPattern(
              subj = QueryVariable(variableName = "familyName"),
              obj = QueryVariable(variableName = "familyName__valueHasString"),
              queryString = LuceneQueryString(queryString = "Bernoulli"),
              literalStatement = Some(
                StatementPattern(
                  subj = QueryVariable(variableName = "familyName"),
                  pred = IriRef(
                    iri = "http://www.knora.org/ontology/knora-base#valueHasString".toSmartIri,
                    propertyPathOperator = None
                  ),
                  obj = QueryVariable(variableName = "familyName__valueHasString"),
                  namedGraph = Some(
                    IriRef(
                      iri = "http://www.knora.org/explicit".toSmartIri,
                      propertyPathOperator = None
                    )
                  )
                )
              )
            )
          )
        )
      ),
      positiveEntities = Set(),
      querySchema = None
    ),
    limit = Some(25),
    useDistinct = true
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
        outputVariableName = "text__Concat"
      )
    ),
    offset = 0,
    groupBy = Vector(
      QueryVariable(variableName = "thing"),
      QueryVariable(variableName = "text__valueHasString")
    ),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "text__valueHasString"),
        isAscending = true
      ),
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "thing"),
        isAscending = true
      )
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "text"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "text"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "text"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasString".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "text__valueHasString"),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        UnionPattern(
          blocks = Vector(
            Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "int"),
                namedGraph = None
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "int"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = XsdLiteral(
                  value = "false",
                  datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "int"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasInteger".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "int__valueHasInteger"),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              FilterPattern(expression =
                CompareExpression(
                  leftArg = QueryVariable(variableName = "int__valueHasInteger"),
                  operator = CompareExpressionOperator.EQUALS,
                  rightArg = XsdLiteral(
                    value = "1",
                    datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
                  )
                )
              )
            ),
            Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "text"),
                namedGraph = None
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "text"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = XsdLiteral(
                  value = "false",
                  datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "text"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasString".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "text__valueHasString"),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              FilterPattern(
                expression = RegexFunction(
                  textExpr = QueryVariable(variableName = "text__valueHasString"),
                  pattern = "Abel",
                  modifier = Some("i")
                )
              )
            )
          )
        )
      ),
      positiveEntities = Set(),
      querySchema = None
    ),
    limit = Some(25),
    useDistinct = true
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
        outputVariableName = "person1__Concat"
      ),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "person2"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "person2__Concat"
      ),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "date"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "date__Concat"
      ),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "letter__linkingProp1__person1__LinkValue"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "letter__linkingProp1__person1__LinkValue__Concat"
      ),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "letter__linkingProp2__person2__LinkValue"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "letter__linkingProp2__person2__LinkValue__Concat"
      )
    ),
    offset = 0,
    groupBy = Vector(
      QueryVariable(variableName = "letter"),
      QueryVariable(variableName = "date__valueHasStartJDN")
    ),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "date__valueHasStartJDN"),
        isAscending = true
      ),
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "letter"),
        isAscending = true
      )
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "gnd2"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasString".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "(DE-588)118696149",
            datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
          ),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "gnd1"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasString".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "(DE-588)118531379",
            datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
          ),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "person2"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "person2"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0801/beol#hasIAFIdentifier".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "gnd2"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "gnd2"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "person1"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "person1"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0801/beol#hasIAFIdentifier".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "gnd1"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "gnd1"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = QueryVariable(variableName = "linkingProp2"),
          obj = QueryVariable(variableName = "person2"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = QueryVariable(variableName = "linkingProp2__hasLinkToValue"),
          obj = QueryVariable(variableName = "letter__linkingProp2__person2__LinkValue"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter__linkingProp2__person2__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#LinkValue".toSmartIri,
            propertyPathOperator = None
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter__linkingProp2__person2__LinkValue"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter__linkingProp2__person2__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "person2"),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = QueryVariable(variableName = "linkingProp1"),
          obj = QueryVariable(variableName = "person1"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = QueryVariable(variableName = "linkingProp1__hasLinkToValue"),
          obj = QueryVariable(variableName = "letter__linkingProp1__person1__LinkValue"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter__linkingProp1__person1__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#LinkValue".toSmartIri,
            propertyPathOperator = None
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter__linkingProp1__person1__LinkValue"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter__linkingProp1__person1__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "person1"),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0801/beol#creationDate".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "date"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "date"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "date"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "date__valueHasStartJDN"),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        FilterPattern(
          expression = OrExpression(
            leftArg = CompareExpression(
              leftArg = QueryVariable(variableName = "linkingProp1"),
              operator = CompareExpressionOperator.EQUALS,
              rightArg = IriRef(
                iri = "http://www.knora.org/ontology/0801/beol#hasAuthor".toSmartIri,
                propertyPathOperator = None
              )
            ),
            rightArg = CompareExpression(
              leftArg = QueryVariable(variableName = "linkingProp1"),
              operator = CompareExpressionOperator.EQUALS,
              rightArg = IriRef(
                iri = "http://www.knora.org/ontology/0801/beol#hasRecipient".toSmartIri,
                propertyPathOperator = None
              )
            )
          )
        ),
        FilterPattern(
          expression = OrExpression(
            leftArg = CompareExpression(
              leftArg = QueryVariable(variableName = "linkingProp2"),
              operator = CompareExpressionOperator.EQUALS,
              rightArg = IriRef(
                iri = "http://www.knora.org/ontology/0801/beol#hasAuthor".toSmartIri,
                propertyPathOperator = None
              )
            ),
            rightArg = CompareExpression(
              leftArg = QueryVariable(variableName = "linkingProp2"),
              operator = CompareExpressionOperator.EQUALS,
              rightArg = IriRef(
                iri = "http://www.knora.org/ontology/0801/beol#hasRecipient".toSmartIri,
                propertyPathOperator = None
              )
            )
          )
        )
      ),
      positiveEntities = Set(),
      querySchema = None
    ),
    limit = Some(25),
    useDistinct = true
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
        isAscending = true
      )
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "thing1"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing1"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "thing2"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing1"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#hasOtherThingValue".toSmartIri,
            propertyPathOperator = None
          ),
          obj =
            QueryVariable(variableName = "thing1__httpwwwknoraorgontology0001anythinghasOtherThing__thing2__LinkValue"),
          namedGraph = None
        ),
        StatementPattern(
          subj =
            QueryVariable(variableName = "thing1__httpwwwknoraorgontology0001anythinghasOtherThing__thing2__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#LinkValue".toSmartIri,
            propertyPathOperator = None
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj =
            QueryVariable(variableName = "thing1__httpwwwknoraorgontology0001anythinghasOtherThing__thing2__LinkValue"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj =
            QueryVariable(variableName = "thing1__httpwwwknoraorgontology0001anythinghasOtherThing__thing2__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "thing2"),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing2"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "thing1"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#hasOtherThingValue".toSmartIri,
            propertyPathOperator = None
          ),
          obj =
            QueryVariable(variableName = "thing__httpwwwknoraorgontology0001anythinghasOtherThing__thing1__LinkValue"),
          namedGraph = None
        ),
        StatementPattern(
          subj =
            QueryVariable(variableName = "thing__httpwwwknoraorgontology0001anythinghasOtherThing__thing1__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#LinkValue".toSmartIri,
            propertyPathOperator = None
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj =
            QueryVariable(variableName = "thing__httpwwwknoraorgontology0001anythinghasOtherThing__thing1__LinkValue"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj =
            QueryVariable(variableName = "thing__httpwwwknoraorgontology0001anythinghasOtherThing__thing1__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "thing1"),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing2"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#hasOtherThing".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "thing"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing2"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#hasOtherThingValue".toSmartIri,
            propertyPathOperator = None
          ),
          obj =
            QueryVariable(variableName = "thing2__httpwwwknoraorgontology0001anythinghasOtherThing__thing__LinkValue"),
          namedGraph = None
        ),
        StatementPattern(
          subj =
            QueryVariable(variableName = "thing2__httpwwwknoraorgontology0001anythinghasOtherThing__thing__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#LinkValue".toSmartIri,
            propertyPathOperator = None
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj =
            QueryVariable(variableName = "thing2__httpwwwknoraorgontology0001anythinghasOtherThing__thing__LinkValue"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj =
            QueryVariable(variableName = "thing2__httpwwwknoraorgontology0001anythinghasOtherThing__thing__LinkValue"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "thing"),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        )
      ),
      positiveEntities = Set(),
      querySchema = None
    ),
    limit = Some(25),
    useDistinct = true,
    fromClause = None,
    variables = Vector(QueryVariable(variableName = "thing"))
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
        isAscending = true
      )
    ),
    whereClause = WhereClause(
      patterns = Vector(
        MinusPattern(patterns =
          Vector(
            StatementPattern(
              subj = QueryVariable(variableName = "thing"),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                propertyPathOperator = None
              ),
              obj = XsdLiteral(
                value = "false",
                datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
              ),
              namedGraph = Some(
                IriRef(
                  iri = "http://www.knora.org/explicit".toSmartIri,
                  propertyPathOperator = None
                )
              )
            ),
            StatementPattern(
              subj = QueryVariable(variableName = "thing"),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri,
                propertyPathOperator = None
              ),
              obj = QueryVariable(variableName = "intVal"),
              namedGraph = None
            ),
            StatementPattern(
              subj = QueryVariable(variableName = "intVal"),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                propertyPathOperator = None
              ),
              obj = XsdLiteral(
                value = "false",
                datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
              ),
              namedGraph = Some(
                IriRef(
                  iri = "http://www.knora.org/explicit".toSmartIri,
                  propertyPathOperator = None
                )
              )
            ),
            StatementPattern(
              subj = QueryVariable(variableName = "intVal"),
              pred = IriRef(
                iri = "http://www.knora.org/ontology/knora-base#valueHasInteger".toSmartIri,
                propertyPathOperator = None
              ),
              obj = QueryVariable(variableName = "intVal__valueHasInteger"),
              namedGraph = Some(
                IriRef(
                  iri = "http://www.knora.org/explicit".toSmartIri,
                  propertyPathOperator = None
                )
              )
            ),
            FilterPattern(expression =
              OrExpression(
                leftArg = CompareExpression(
                  leftArg = QueryVariable(variableName = "intVal__valueHasInteger"),
                  operator = CompareExpressionOperator.EQUALS,
                  rightArg = XsdLiteral(
                    value = "123454321",
                    datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
                  )
                ),
                rightArg = CompareExpression(
                  leftArg = QueryVariable(variableName = "intVal__valueHasInteger"),
                  operator = CompareExpressionOperator.EQUALS,
                  rightArg = XsdLiteral(
                    value = "999999999",
                    datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
                  )
                )
              )
            )
          )
        )
      ),
      positiveEntities = Set(),
      querySchema = None
    ),
    limit = Some(25),
    useDistinct = true
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
        outputVariableName = "int__Concat"
      ),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "richtext"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "richtext__Concat"
      ),
      GroupConcat(
        inputVariable = QueryVariable(variableName = "text"),
        separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
        outputVariableName = "text__Concat"
      )
    ),
    offset = 0,
    groupBy = Vector(
      QueryVariable(variableName = "thing"),
      QueryVariable(variableName = "int__valueHasInteger")
    ),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "int__valueHasInteger"),
        isAscending = true
      ),
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "thing"),
        isAscending = true
      )
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "int"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "int"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "int"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasInteger".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "int__valueHasInteger"),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        UnionPattern(
          blocks = Vector(
            Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "int"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasInteger".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = XsdLiteral(
                  value = "1",
                  datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
                ),
                namedGraph = None
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "int"),
                namedGraph = None
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "int"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = XsdLiteral(
                  value = "false",
                  datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "int"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasInteger".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "int__valueHasInteger"),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasRichtext".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "richtext"),
                namedGraph = None
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "richtext"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = XsdLiteral(
                  value = "false",
                  datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              LuceneQueryPattern(
                subj = QueryVariable(variableName = "richtext"),
                obj = QueryVariable(variableName = "richtext__valueHasString"),
                queryString = LuceneQueryString(queryString = "test"),
                literalStatement = Some(
                  StatementPattern(
                    subj = QueryVariable(variableName = "richtext"),
                    pred = IriRef(
                      iri = "http://www.knora.org/ontology/knora-base#valueHasString".toSmartIri,
                      propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "richtext__valueHasString"),
                    namedGraph = Some(
                      IriRef(
                        iri = "http://www.knora.org/explicit".toSmartIri,
                        propertyPathOperator = None
                      )
                    )
                  )
                )
              )
            ),
            Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "int"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasInteger".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = XsdLiteral(
                  value = "3",
                  datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
                ),
                namedGraph = None
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasInteger".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "int"),
                namedGraph = None
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "int"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = XsdLiteral(
                  value = "false",
                  datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "int"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasInteger".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "int__valueHasInteger"),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "text"),
                namedGraph = None
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "text"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = XsdLiteral(
                  value = "false",
                  datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = Some(
                  IriRef(
                    iri = "http://www.knora.org/explicit".toSmartIri,
                    propertyPathOperator = None
                  )
                )
              ),
              LuceneQueryPattern(
                subj = QueryVariable(variableName = "text"),
                obj = QueryVariable(variableName = "text__valueHasString"),
                queryString = LuceneQueryString(queryString = "test"),
                literalStatement = Some(
                  StatementPattern(
                    subj = QueryVariable(variableName = "text"),
                    pred = IriRef(
                      iri = "http://www.knora.org/ontology/knora-base#valueHasString".toSmartIri,
                      propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "text__valueHasString"),
                    namedGraph = Some(
                      IriRef(
                        iri = "http://www.knora.org/explicit".toSmartIri,
                        propertyPathOperator = None
                      )
                    )
                  )
                )
              )
            )
          )
        )
      ),
      positiveEntities = Set(),
      querySchema = None
    ),
    limit = Some(25),
    useDistinct = true
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
        outputVariableName = "text__Concat"
      )
    ),
    offset = 0,
    groupBy = Vector(QueryVariable(variableName = "thing")),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "thing"),
        isAscending = true
      )
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "standoffParagraphTag"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/standoff#StandoffParagraphTag".toSmartIri,
            propertyPathOperator = None
          ),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "standoffDateTag"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#standoffTagHasStartAncestor".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "standoffParagraphTag"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "standoffDateTag"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None
          ),
          obj = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#StandoffDateTag".toSmartIri,
            propertyPathOperator = None
          ),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "text"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasStandoff".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "standoffDateTag"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/0001/anything#hasText".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "text"),
          namedGraph = None
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "text"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#isDeleted".toSmartIri,
            propertyPathOperator = None
          ),
          obj = XsdLiteral(
            value = "false",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
          ),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "standoffDateTag"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "standoffDateTag__valueHasStartJDN"),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "standoffDateTag"),
          pred = IriRef(
            iri = "http://www.knora.org/ontology/knora-base#valueHasEndJDN".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "standoffDateTag__valueHasEndJDN"),
          namedGraph = Some(
            IriRef(
              iri = "http://www.knora.org/explicit".toSmartIri,
              propertyPathOperator = None
            )
          )
        ),
        FilterPattern(
          expression = AndExpression(
            leftArg = CompareExpression(
              leftArg = XsdLiteral(
                value = "2457747",
                datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
              ),
              operator = CompareExpressionOperator.LESS_THAN_OR_EQUAL_TO,
              rightArg = QueryVariable(variableName = "standoffDateTag__valueHasEndJDN")
            ),
            rightArg = CompareExpression(
              leftArg = XsdLiteral(
                value = "2457747",
                datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
              ),
              operator = CompareExpressionOperator.GREATER_THAN_OR_EQUAL_TO,
              rightArg = QueryVariable(variableName = "standoffDateTag__valueHasStartJDN")
            )
          )
        )
      ),
      positiveEntities = Set(),
      querySchema = None
    ),
    limit = Some(25),
    useDistinct = true
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

  "The NonTriplestoreSpecificGravsearchToPrequeryGenerator object" should {

    "transform an input query with an optional property criterion without removing the rdf:type statement" in {
      val transformedQuery = transformQuery(queryWithOptional)
      assert(transformedQuery === TransformedQueryWithOptional)
    }

    "transform an input query with a date as a non optional sort criterion" in {
      val transformedQuery = transformQuery(inputQueryWithDateNonOptionalSortCriterion)
      assert(transformedQuery === transformedQueryWithDateNonOptionalSortCriterion)
    }

    "transform an input query with a date as a non optional sort criterion (submitted in complex schema)" in {
      val transformedQuery = transformQuery(inputQueryWithDateNonOptionalSortCriterionComplex)
      assert(transformedQuery === transformedQueryWithDateNonOptionalSortCriterion)
    }

    "transform an input query with a date as non optional sort criterion and a filter" in {
      val transformedQuery = transformQuery(inputQueryWithDateNonOptionalSortCriterionAndFilter)
      assert(transformedQuery === transformedQueryWithDateNonOptionalSortCriterionAndFilter)
    }

    "transform an input query with a date as non optional sort criterion and a filter (submitted in complex schema)" in {
      val transformedQuery = transformQuery(inputQueryWithDateNonOptionalSortCriterionAndFilterComplex)
      assert(transformedQuery === transformedQueryWithDateNonOptionalSortCriterionAndFilter)
    }

    "transform an input query with a date as an optional sort criterion" in {
      val transformedQuery = transformQuery(inputQueryWithDateOptionalSortCriterion)
      assert(transformedQuery === transformedQueryWithDateOptionalSortCriterion)
    }

    "transform an input query with a date as an optional sort criterion (submitted in complex schema)" in {
      val transformedQuery = transformQuery(inputQueryWithDateOptionalSortCriterionComplex)
      assert(transformedQuery === transformedQueryWithDateOptionalSortCriterion)
    }

    "transform an input query with a date as an optional sort criterion and a filter" in {
      val transformedQuery = transformQuery(inputQueryWithDateOptionalSortCriterionAndFilter)
      assert(transformedQuery === transformedQueryWithDateOptionalSortCriterionAndFilter)
    }

    "transform an input query with a date as an optional sort criterion and a filter (submitted in complex schema)" in {
      val transformedQuery = transformQuery(inputQueryWithDateOptionalSortCriterionAndFilterComplex)
      assert(transformedQuery === transformedQueryWithDateOptionalSortCriterionAndFilter)
    }

    "transform an input query with a decimal as an optional sort criterion" in {
      val transformedQuery = transformQuery(inputQueryWithDecimalOptionalSortCriterion)
      assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterion)
    }

    "transform an input query with a decimal as an optional sort criterion (submitted in complex schema)" in {
      val transformedQuery = transformQuery(inputQueryWithDecimalOptionalSortCriterionComplex)
      assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterion)
    }

    "transform an input query with a decimal as an optional sort criterion and a filter" in {
      val transformedQuery = transformQuery(inputQueryWithDecimalOptionalSortCriterionAndFilter)
      assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterionAndFilter)
    }

    "transform an input query with a decimal as an optional sort criterion and a filter (submitted in complex schema)" in {
      val transformedQuery = transformQuery(inputQueryWithDecimalOptionalSortCriterionAndFilterComplex)
      // TODO: user provided statements and statement generated for sorting should be unified (https://github.com/dhlab-basel/Knora/issues/1195)
      assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterionAndFilterComplex)
    }

    "transform an input query using rdfs:label and a literal in the simple schema" in {
      val transformedQuery = transformQuery(InputQueryWithRdfsLabelAndLiteralInSimpleSchema)
      assert(transformedQuery == TransformedQueryWithRdfsLabelAndLiteral)
    }

    "transform an input query using rdfs:label and a literal in the complex schema" in {
      val transformedQuery = transformQuery(InputQueryWithRdfsLabelAndLiteralInComplexSchema)
      assert(transformedQuery === TransformedQueryWithRdfsLabelAndLiteral)
    }

    "transform an input query using rdfs:label and a variable in the simple schema" in {
      val transformedQuery = transformQuery(InputQueryWithRdfsLabelAndVariableInSimpleSchema)
      assert(transformedQuery === TransformedQueryWithRdfsLabelAndVariable)
    }

    "transform an input query using rdfs:label and a variable in the complex schema" in {
      val transformedQuery = transformQuery(InputQueryWithRdfsLabelAndVariableInComplexSchema)
      assert(transformedQuery === TransformedQueryWithRdfsLabelAndVariable)
    }

    "transform an input query using rdfs:label and a regex in the simple schema" in {
      val transformedQuery = transformQuery(InputQueryWithRdfsLabelAndRegexInSimpleSchema)
      assert(transformedQuery === TransformedQueryWithRdfsLabelAndRegex)
    }

    "transform an input query using rdfs:label and a regex in the complex schema" in {
      val transformedQuery = transformQuery(InputQueryWithRdfsLabelAndRegexInComplexSchema)
      assert(transformedQuery === TransformedQueryWithRdfsLabelAndRegex)
    }

    "transform an input query with UNION scopes in the simple schema" in {
      val transformedQuery = transformQuery(InputQueryWithUnionScopes)
      assert(transformedQuery === TransformedQueryWithUnionScopes)
    }

    "transform an input query with knora-api:standoffTagHasStartAncestor" in {
      val transformedQuery = transformQuery(queryWithStandoffTagHasStartAncestor)
      assert(transformedQuery === transformedQueryWithStandoffTagHasStartAncestor)
    }

    "reorder query patterns in where clause" in {
      val transformedQuery = transformQuery(queryToReorder)
      assert(transformedQuery === transformedQueryToReorder)
    }

    "reorder query patterns in where clause with union" in {
      val transformedQuery = transformQuery(queryToReorderWithUnion)
      assert(transformedQuery === transformedQueryToReorderWithUnion)
    }

    "reorder query patterns in where clause with optional" in {
      val transformedQuery = transformQuery(queryWithOptional)
      assert(transformedQuery === TransformedQueryWithOptional)
    }

    "reorder query patterns with minus scope" in {
      val transformedQuery = transformQuery(queryToReorderWithMinus)
      assert(transformedQuery == transformedQueryToReorderWithMinus)
    }

    "reorder a query with a cycle" in {
      val transformedQuery = transformQuery(queryToReorderWithCycle)
      assert(transformedQuery == transformedQueryToReorderWithCycle)
    }

    "not remove rdf:type knora-api:Resource if it's needed" in {
      val transformedQuery = transformQuery(queryWithKnoraApiResource)

      assert(
        transformedQuery.whereClause.patterns.contains(
          StatementPattern(
            subj = QueryVariable(variableName = "resource"),
            pred = IriRef(
              iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
              propertyPathOperator = None
            ),
            obj = IriRef(
              iri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri,
              propertyPathOperator = None
            ),
            namedGraph = None
          )
        )
      )
    }
  }
}
