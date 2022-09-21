package org.knora.webapi.util.search.gravsearch.prequery

import akka.actor.ActorRef

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

import dsp.errors.AssertionException
import org.knora.webapi.CoreSpec
import org.knora.webapi.config.AppConfig
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser
import org.knora.webapi.messages.util.search.gravsearch.GravsearchQueryChecker
import org.knora.webapi.messages.util.search.gravsearch.prequery.NonTriplestoreSpecificGravsearchToCountPrequeryTransformer
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionRunner
import org.knora.webapi.messages.util.search.gravsearch.types.GravsearchTypeInspectionUtil
import org.knora.webapi.sharedtestdata.SharedTestDataADM

private object CountQueryHandler {

  private val timeout = 10.seconds

  val anythingUser: UserADM = SharedTestDataADM.anythingAdminUser

  def transformQuery(
    query: String,
    appActor: ActorRef,
    responderData: ResponderData,
    appConfig: AppConfig
  )(implicit executionContext: ExecutionContext): SelectQuery = {

    val constructQuery = GravsearchParser.parseQuery(query)

    val typeInspectionRunner =
      new GravsearchTypeInspectionRunner(
        appActor,
        responderData = responderData,
        inferTypes = true
      )

    val typeInspectionResultFuture = typeInspectionRunner.inspectTypes(constructQuery.whereClause, anythingUser)

    val typeInspectionResult = Await.result(typeInspectionResultFuture, timeout)

    val whereClauseWithoutAnnotations: WhereClause =
      GravsearchTypeInspectionUtil.removeTypeAnnotations(constructQuery.whereClause)

    // Validate schemas and predicates in the CONSTRUCT clause.
    GravsearchQueryChecker.checkConstructClause(
      constructClause = constructQuery.constructClause,
      typeInspectionResult = typeInspectionResult
    )

    // Create a Select prequery

    val nonTriplestoreSpecificConstructToSelectTransformer: NonTriplestoreSpecificGravsearchToCountPrequeryTransformer =
      new NonTriplestoreSpecificGravsearchToCountPrequeryTransformer(
        constructClause = constructQuery.constructClause,
        typeInspectionResult = typeInspectionResult,
        querySchema = constructQuery.querySchema.getOrElse(throw AssertionException(s"WhereClause has no querySchema"))
      )

    val nonTriplestoreSpecficPrequery: SelectQuery = QueryTraverser.transformConstructToSelect(
      inputQuery = constructQuery.copy(
        whereClause = whereClauseWithoutAnnotations,
        orderBy = Seq.empty[OrderCriterion] // count queries do not need any sorting criteria
      ),
      transformer = nonTriplestoreSpecificConstructToSelectTransformer
    )

    nonTriplestoreSpecficPrequery
  }

}

class NonTriplestoreSpecificGravsearchToCountPrequeryTransformerSpec extends CoreSpec() {

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

      val transformedQuery =
        CountQueryHandler.transformQuery(
          inputQueryWithDecimalOptionalSortCriterionAndFilter,
          appActor,
          responderData,
          appConfig
        )

      assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterionAndFilter)

    }

    "transform an input query with a decimal as an optional sort criterion and a filter (submitted in complex schema)" in {

      val transformedQuery =
        CountQueryHandler.transformQuery(
          inputQueryWithDecimalOptionalSortCriterionAndFilterComplex,
          appActor,
          responderData,
          appConfig
        )

      assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterionAndFilterComplex)

    }

  }
}
