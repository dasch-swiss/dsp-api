package org.knora.webapi.messages.util.search.gravsearch.prequery

import scala.collection.mutable.ArrayBuffer
import dsp.errors.AssertionException

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
import zio._

import org.knora.webapi.sharedtestdata.SharedTestDataADM.anythingAdminUser

class GravsearchToCountPrequeryTransformerSpec extends CoreSpec {

  def transformQuery(query: String): SelectQuery = {

    val inspectionRunner = for {
      qt <- ZIO.service[QueryTraverser]
      mr <- ZIO.service[MessageRelay]
      sf <- ZIO.service[StringFormatter]
    } yield GravsearchTypeInspectionRunner(qt, mr, sf)

    val countQueryZio = for {
      constructQuery       <- ZIO.attempt(GravsearchParser.parseQuery(query))
      whereClause           = constructQuery.whereClause
      constructClause       = constructQuery.constructClause
      querySchemaMaybe      = constructQuery.querySchema
      inspectionResult     <- inspectionRunner.flatMap(_.inspectTypes(whereClause, anythingAdminUser))
      _                    <- GravsearchQueryChecker.checkConstructClause(constructClause, inspectionResult)
      sanitizedWhereClause <- GravsearchTypeInspectionUtil.removeTypeAnnotations(whereClause)
      querySchema <-
        ZIO.fromOption(querySchemaMaybe).orElseFail(AssertionException(s"WhereClause has no querySchema"))
      transformer =
        new GravsearchToCountPrequeryTransformer(
          constructClause = constructClause,
          typeInspectionResult = inspectionResult,
          querySchema = querySchema
        )
      prequery <- ZIO.serviceWithZIO[QueryTraverser](
                    _.transformConstructToSelect(
                      inputQuery = constructQuery.copy(
                        whereClause = sanitizedWhereClause,
                        orderBy = Seq.empty[OrderCriterion] // count queries do not need any sorting criteria
                      ),
                      transformer
                    )
                  )

    } yield prequery
    UnsafeZioRun.runOrThrow(countQueryZio)
  }

  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

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
          outputVariableName = "count"
        )
      ),
      offset = 0,
      groupBy = Nil,
      orderBy = Nil,
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
            )
          ),
          OptionalPattern(
            patterns = Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasDecimal".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "decimal")
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
                )
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "decimal"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasDecimal".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "decimal__valueHasDecimal")
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
        ).toSeq,
        positiveEntities = Set(),
        querySchema = None
      ),
      limit = Some(1),
      useDistinct = true
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

  val transformedQueryWithDecimalOptionalSortCriterionAndFilterComplex: SelectQuery =
    SelectQuery(
      fromClause = None,
      variables = Vector(
        Count(
          outputVariableName = "count",
          distinct = true,
          inputVariable = QueryVariable(variableName = "thing")
        )
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
              propertyPathOperator = None
            ),
            obj = XsdLiteral(
              value = "false",
              datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
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
            )
          ),
          OptionalPattern(
            patterns = Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "decimal"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasDecimal".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "decimalVal")
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/0001/anything#hasDecimal".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "decimal")
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
      limit = Some(1),
      useDistinct = true
    )

  "The NonTriplestoreSpecificGravsearchToCountPrequeryGenerator object" should {

    "transform an input query with a decimal as an optional sort criterion and a filter" in {
      val transformedQuery = transformQuery(inputQueryWithDecimalOptionalSortCriterionAndFilter)
      assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterionAndFilter)
    }

    "transform an input query with a decimal as an optional sort criterion and a filter (submitted in complex schema)" in {
      val transformedQuery = transformQuery(inputQueryWithDecimalOptionalSortCriterionAndFilterComplex)
      assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterionAndFilterComplex)
    }
  }
}
