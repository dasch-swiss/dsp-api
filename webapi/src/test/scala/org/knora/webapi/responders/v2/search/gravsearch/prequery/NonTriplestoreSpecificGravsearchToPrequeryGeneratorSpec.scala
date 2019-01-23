package org.knora.webapi.responders.v2.search.gravsearch.prequery

import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.responders.ResponderData
import org.knora.webapi.responders.v2.search._
import org.knora.webapi.responders.v2.search.gravsearch.types.{GravsearchTypeInspectionRunner, GravsearchTypeInspectionUtil}
import org.knora.webapi.responders.v2.search.gravsearch.{GravsearchParser, GravsearchQueryChecker}
import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.{MessageUtil, StringFormatter}
import org.knora.webapi.{AssertionException, CoreSpec, SettingsImpl, SharedTestDataADM}

import scala.concurrent.Await
import scala.concurrent.duration._


private object QueryHandler {

    private val timeout = 10.seconds

    val anythingUser: UserADM = SharedTestDataADM.anythingAdminUser

    def transformQuery(query: String, responderData: ResponderData, settings: SettingsImpl): SelectQuery = {

        val constructQuery = GravsearchParser.parseQuery(query)

        val typeInspectionRunner = new GravsearchTypeInspectionRunner(responderData = responderData, inferTypes = true)

        val typeInspectionResultFuture = typeInspectionRunner.inspectTypes(constructQuery.whereClause, anythingUser)

        val typeInspectionResult = Await.result(typeInspectionResultFuture, timeout)

        val whereClauseWithoutAnnotations: WhereClause = GravsearchTypeInspectionUtil.removeTypeAnnotations(constructQuery.whereClause)

        // Validate schemas and predicates in the CONSTRUCT clause.
        GravsearchQueryChecker.checkConstructClause(
            constructClause = constructQuery.constructClause,
            typeInspectionResult = typeInspectionResult
        )

        // Create a Select prequery

        val nonTriplestoreSpecificConstructToSelectTransformer: NonTriplestoreSpecificGravsearchToPrequeryGenerator = new NonTriplestoreSpecificGravsearchToPrequeryGenerator(
            typeInspectionResult = typeInspectionResult,
            querySchema = constructQuery.querySchema.getOrElse(throw AssertionException(s"WhereClause has no querySchema")),
            settings = settings
        )

        val nonTriplestoreSpecficPrequery: SelectQuery = QueryTraverser.transformConstructToSelect(
            inputQuery = constructQuery.copy(whereClause = whereClauseWithoutAnnotations),
            transformer = nonTriplestoreSpecificConstructToSelectTransformer
        )

        nonTriplestoreSpecficPrequery
    }

}

class NonTriplestoreSpecificGravsearchToPrequeryGeneratorSpec extends CoreSpec() {

    implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    "The  NonTriplestoreSpecificGravsearchToPrequeryGenerator object" should {

        "transform an input query with a non optional sort criterion" in {

            val transformedQuery = QueryHandler.transformQuery(inputQueryWithNonOptionalSortCriterion, responderData, settings)

            assert(transformedQuery === transformedQueryWithNonOptionalSortCriterion)

        }

        "transform an input query with a non optional sort criterion and a filter" in {

            val transformedQuery = QueryHandler.transformQuery(inputQueryWithNonOptionalSortCriterionAndFilter, responderData, settings)

            assert(transformedQuery === transformedQueryWithNonOptionalSortCriterionAndFilter)

        }



        "transform an input query with an optional sort criterion" in {

            val transformedQuery = QueryHandler.transformQuery(inputQueryWithOptionalSortCriterion, responderData, settings)

            assert(transformedQuery === transformedQueryWithOptionalSortCriterion)

        }

    }

    val inputQueryWithNonOptionalSortCriterion: String =
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

    val transformedQueryWithNonOptionalSortCriterion =
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
                patterns = Vector(
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri,
                            propertyPathOperator = None
                        ),
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
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
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
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "date"),
                        pred = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = QueryVariable(variableName = "date__valueHasStartJDN"),
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
                    )
                ),
                positiveEntities = Set(),
                querySchema = None
            ),
            limit = Some(25),
            useDistinct = true
        )

    val inputQueryWithNonOptionalSortCriterionAndFilter: String =
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

    val transformedQueryWithNonOptionalSortCriterionAndFilter: SelectQuery =
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
                patterns = Vector(
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri,
                            propertyPathOperator = None
                        ),
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
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
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
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "date"),
                        pred = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = QueryVariable(variableName = "date__valueHasStartJDN"),
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
                    ),
                    FilterPattern(expression = CompareExpression(
                        leftArg = QueryVariable(variableName = "date__valueHasStartJDN"),
                        operator = CompareExpressionOperator.GREATER_THAN,
                        rightArg = XsdLiteral(
                            value = "2455928",
                            datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
                        )
                    ))
                ),
                positiveEntities = Set(),
                querySchema = None
            ),
            limit = Some(25),
            useDistinct = true
        )

    val inputQueryWithOptionalSortCriterion: String =
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

    val transformedQueryWithOptionalSortCriterion: SelectQuery =
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
                patterns = Vector(
                    StatementPattern(
                        subj = QueryVariable(variableName = "thing"),
                        pred = IriRef(
                            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = IriRef(
                            iri = "http://www.knora.org/ontology/knora-base#Resource".toSmartIri,
                            propertyPathOperator = None
                        ),
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
                        namedGraph = Some(IriRef(
                            iri = "http://www.knora.org/explicit".toSmartIri,
                            propertyPathOperator = None
                        ))
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
                    OptionalPattern(patterns = Vector(
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
                            namedGraph = Some(IriRef(
                                iri = "http://www.knora.org/explicit".toSmartIri,
                                propertyPathOperator = None
                            ))
                        ),
                        StatementPattern(
                            subj = QueryVariable(variableName = "date"),
                            pred = IriRef(
                                iri = "http://www.knora.org/ontology/knora-base#valueHasStartJDN".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = QueryVariable(variableName = "date__valueHasStartJDN"),
                            namedGraph = Some(IriRef(
                                iri = "http://www.knora.org/explicit".toSmartIri,
                                propertyPathOperator = None
                            ))
                        )
                    ))
                ),
                positiveEntities = Set(),
                querySchema = None
            ),
            limit = Some(25),
            useDistinct = true
        )

}