package org.knora.webapi.util.search.gravsearch.prequery

import org.knora.webapi.CoreSpec
import org.knora.webapi.exceptions.AssertionException
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.admin.responder.usersmessages.UserADM
import org.knora.webapi.messages.util.ResponderData
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.util.search.gravsearch.prequery.NonTriplestoreSpecificGravsearchToPrequeryTransformer
import org.knora.webapi.messages.util.search.gravsearch.types.{
  GravsearchTypeInspectionRunner,
  GravsearchTypeInspectionUtil
}
import org.knora.webapi.messages.util.search.gravsearch.{GravsearchParser, GravsearchQueryChecker}
import org.knora.webapi.settings.KnoraSettingsImpl
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.util.ApacheLuceneSupport.LuceneQueryString

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await
import scala.concurrent.duration._
import scalax.collection.Graph
import scalax.collection.GraphEdge.DiHyperEdge

import scala.collection.immutable.ListSet

private object QueryHandler {

  private val timeout = 10.seconds

  val anythingUser: UserADM = SharedTestDataADM.anythingAdminUser
  case class edgeLabel(label: String)
  def createAndSortGraph(statementPatterns: Seq[StatementPattern]): Set[QueryPattern] = {
    def createGraph: Graph[String, DiHyperEdge] = {
      val graphComponents = statementPatterns.map { statementPattern =>
        // transform every statementPattern to LUniDiEdge(Subj,Obj)(edgeLabel(pred))
        val node1 = statementPattern.subj.toSparql
        val node2 = statementPattern.obj.toSparql
//        val edge = statementPattern.pred.toSparql
        DiHyperEdge(node1, node2)
      }
      val graph = graphComponents.foldLeft(Graph.empty[String, DiHyperEdge]) { (graph, edge) =>
        graph + edge // add nodes and edges to graph
      }
      graph
    }
    def sortStatementPatterns(createdGraph: Graph[String, DiHyperEdge],
                              statementPatterns: Seq[StatementPattern]): Set[QueryPattern] = {
      // Try topological sorting of graph
      val topologicalOrder: createdGraph.TopologicalOrder[createdGraph.NodeT] = createdGraph.topologicalSort match {
        // Is there a cycle in the graph?
        case Left(cycleNode) =>
          // TODO: yes. break the cycle
          throw new Error(s"Graph contains a cycle at node: ${cycleNode}, entire cycle is ${createdGraph.findCycle}.")
        case Right(topOrder) => {
          // No. return the topological order
          println(topOrder)
          topOrder
        }
      }
      // take the main resource out.
      val topologicalOrderWithoutRoot: Iterable[createdGraph.NodeT] = topologicalOrder.tail
      val sortedStatements: Set[QueryPattern] =
        topologicalOrderWithoutRoot.foldRight(ListSet.empty[QueryPattern]) { (node, sortedStatements) =>
          val statementsOfNode: Set[QueryPattern] = statementPatterns
            .filter(p => p.obj.toSparql.equals(node.value))
            .toSet[QueryPattern]
          println(sortedStatements ++ statementsOfNode)
          sortedStatements ++ statementsOfNode
        }
      sortedStatements
    }

    val createdGraph = createGraph
    sortStatementPatterns(createdGraph, statementPatterns)

  }
  def reorderPatternsByDependency(patterns: Seq[QueryPattern]): Set[QueryPattern] = {

    val statementPatterns: Seq[StatementPattern] = patterns.collect { case pattern: StatementPattern => pattern }
    val sortedStatementPatterns = createAndSortGraph(statementPatterns)
    val otherPatterns: Set[QueryPattern] = patterns.filterNot(p => p.isInstanceOf[StatementPattern]).toSet
    val sortedOtherPatterns: Set[QueryPattern] = otherPatterns.map {
      // sort statements inside each UnionPattern block
      case unionPattern: UnionPattern => {
        val sortedUnionBlocks: Seq[Seq[QueryPattern]] =
          unionPattern.blocks.map(block => reorderPatternsByDependency(block).toSeq)
        UnionPattern(blocks = sortedUnionBlocks)
      }
      // sort statements inside OptionalPattern
      case optionalPattern: OptionalPattern => {
        val sortedOptionalPatterns: Set[QueryPattern] = reorderPatternsByDependency(optionalPattern.patterns)
        OptionalPattern(patterns = sortedOptionalPatterns.toSeq)
      }
      // sort statements inside MinusPattern
      case minusPattern: MinusPattern => {
        val sortedMinusPatterns: Set[QueryPattern] = reorderPatternsByDependency(minusPattern.patterns)
        MinusPattern(patterns = sortedMinusPatterns.toSeq)
      }
      // sort statements inside FilterNotExistsPattern
      case filterNotExistsPattern: FilterNotExistsPattern => {
        val sortedFilterNotExistsPatterns: Set[QueryPattern] =
          reorderPatternsByDependency(filterNotExistsPattern.patterns)
        FilterNotExistsPattern(patterns = sortedFilterNotExistsPatterns.toSeq)
      }
      // return any other query pattern as it is
      case pattern: QueryPattern => pattern
    }
    val sorted = sortedStatementPatterns ++ sortedOtherPatterns
    sorted
  }

  def transformQuery(query: String, responderData: ResponderData, settings: KnoraSettingsImpl): SelectQuery = {

    val constructQuery = GravsearchParser.parseQuery(query)

    val typeInspectionRunner = new GravsearchTypeInspectionRunner(responderData = responderData, inferTypes = true)

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

    val nonTriplestoreSpecificConstructToSelectTransformer: NonTriplestoreSpecificGravsearchToPrequeryTransformer =
      new NonTriplestoreSpecificGravsearchToPrequeryTransformer(
        constructClause = constructQuery.constructClause,
        typeInspectionResult = typeInspectionResult,
        querySchema = constructQuery.querySchema.getOrElse(throw AssertionException(s"WhereClause has no querySchema")),
        settings = settings
      )

    val nonTriplestoreSpecificPrequery: SelectQuery = QueryTraverser.transformConstructToSelect(
      inputQuery = constructQuery.copy(whereClause = whereClauseWithoutAnnotations),
      transformer = nonTriplestoreSpecificConstructToSelectTransformer
    )

    nonTriplestoreSpecificPrequery
  }

}

class NonTriplestoreSpecificGravsearchToPrequeryTransformerSpec extends CoreSpec() {

  implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

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
              ))
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
              ))
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
              ))
          )
        ),
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
              ))
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
              ))
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
              ))
          ),
          FilterPattern(
            expression = CompareExpression(
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
                  ))
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
                  ))
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
            ))
        ),
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
          outputVariableName = "decimal__Concat",
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
                  ))
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
                  ))
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
                  ))
              ),
              FilterPattern(expression = CompareExpression(
                leftArg = QueryVariable(variableName = "decimal__valueHasDecimal"),
                operator = CompareExpressionOperator.GREATER_THAN,
                rightArg = XsdLiteral(
                  value = "2",
                  datatype = "http://www.w3.org/2001/XMLSchema#decimal".toSmartIri
                )
              ))
            ))
        ),
        positiveEntities = Set(),
        querySchema = None
      ),
      limit = Some(25),
      useDistinct = true
    )

  val transformedQueryWithDecimalOptionalSortCriterionAndFilterComplex: SelectQuery =
    SelectQuery(
      variables = Vector(
        QueryVariable(variableName = "thing"),
        GroupConcat(
          inputVariable = QueryVariable(variableName = "decimal"),
          separator = StringFormatter.INFORMATION_SEPARATOR_ONE,
          outputVariableName = "decimal__Concat",
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
                  ))
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
                  ))
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "decimal"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasDecimal".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "decimalVal"),
                namedGraph = None
              ),
              FilterPattern(expression = CompareExpression(
                leftArg = QueryVariable(variableName = "decimalVal"),
                operator = CompareExpressionOperator.GREATER_THAN,
                rightArg = XsdLiteral(
                  value = "2",
                  datatype = "http://www.w3.org/2001/XMLSchema#decimal".toSmartIri
                )
              ))
            ))
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
    variables = Vector(QueryVariable(variableName = "book")),
    offset = 0,
    groupBy = Vector(QueryVariable(variableName = "book")),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "book"),
        isAscending = true
      )),
    whereClause = WhereClause(
      patterns = ArrayBuffer(
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
            ))
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
    variables = Vector(QueryVariable(variableName = "book")),
    offset = 0,
    groupBy = Vector(QueryVariable(variableName = "book")),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "book"),
        isAscending = true
      )),
    whereClause = WhereClause(
      patterns = ArrayBuffer(
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
            ))
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
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "label"),
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
          ))
      ),
      positiveEntities = Set(),
      querySchema = None
    ),
    limit = Some(25),
    useDistinct = true
  )

  val TransformedQueryWithRdfsLabelAndRegex: SelectQuery = SelectQuery(
    variables = Vector(QueryVariable(variableName = "book")),
    offset = 0,
    groupBy = Vector(QueryVariable(variableName = "book")),
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "book"),
        isAscending = true
      )),
    whereClause = WhereClause(
      patterns = ArrayBuffer(
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
            ))
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
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://www.w3.org/2000/01/rdf-schema#label".toSmartIri,
            propertyPathOperator = None
          ),
          obj = QueryVariable(variableName = "bookLabel"),
          namedGraph = None
        ),
        FilterPattern(
          expression = RegexFunction(
            textExpr = QueryVariable(variableName = "bookLabel"),
            pattern = "Zeit",
            modifier = Some("i")
          ))
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
      )),
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
            ))
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
                variableName = "document__httpwwwknoraorgontology0801beolhasRecipient__recipient__LinkValue"),
              namedGraph = None
            ),
            StatementPattern(
              subj = QueryVariable(
                variableName = "document__httpwwwknoraorgontology0801beolhasRecipient__recipient__LinkValue"),
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
                ))
            ),
            StatementPattern(
              subj = QueryVariable(
                variableName = "document__httpwwwknoraorgontology0801beolhasRecipient__recipient__LinkValue"),
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
                ))
            ),
            StatementPattern(
              subj = QueryVariable(
                variableName = "document__httpwwwknoraorgontology0801beolhasRecipient__recipient__LinkValue"),
              pred = IriRef(
                iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#object".toSmartIri,
                propertyPathOperator = None
              ),
              obj = QueryVariable(variableName = "recipient"),
              namedGraph = Some(
                IriRef(
                  iri = "http://www.knora.org/explicit".toSmartIri,
                  propertyPathOperator = None
                ))
            ),
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
                ))
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
                ))
            ),
            LuceneQueryPattern(
              subj = QueryVariable(variableName = "familyName"),
              obj = QueryVariable(variableName = "familyName__valueHasString"),
              queryString = LuceneQueryString(queryString = "Bernoulli"),
              literalStatement = Some(StatementPattern(
                subj = QueryVariable(variableName = "familyName"),
                pred = IriRef(
                  iri = "http://www.knora.org/ontology/knora-base#valueHasString".toSmartIri,
                  propertyPathOperator = None
                ),
                obj = QueryVariable(variableName = "familyName__valueHasString"),
                namedGraph = Some(IriRef(
                  iri = "http://www.knora.org/explicit".toSmartIri,
                  propertyPathOperator = None
                ))
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
            ))
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
            ))
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
            ))
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
                  ))
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
                  ))
              ),
              FilterPattern(expression = CompareExpression(
                leftArg = QueryVariable(variableName = "int__valueHasInteger"),
                operator = CompareExpressionOperator.EQUALS,
                rightArg = XsdLiteral(
                  value = "1",
                  datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
                )
              ))
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
                  ))
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
                  ))
              ),
              FilterPattern(
                expression = RegexFunction(
                  textExpr = QueryVariable(variableName = "text__valueHasString"),
                  pattern = "Abel",
                  modifier = Some("i")
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

  val queryToReorderWithMinus =
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
      |    anything:hasInteger knora-api:objectType xsd:integer .
      |    ?intVal a xsd:integer .
      |    FILTER(?intVal = 123454321 || ?intVal = 999999999)
      |  }
      |}""".stripMargin

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
       |		?thing anything:hasInteger ?int .
       |		?int knora-api:intValueAsInt 1 .
       |    }
       |}
       |ORDER BY (?int)""".stripMargin

  "The NonTriplestoreSpecificGravsearchToPrequeryGenerator object" should {

    "transform an input query with an optional property criterion without removing the rdf:type statement" in {

      val transformedQuery = QueryHandler.transformQuery(queryWithOptional, responderData, settings)
      assert(transformedQuery === TransformedQueryWithOptional)
    }

    "transform an input query with a date as a non optional sort criterion" in {

      val transformedQuery =
        QueryHandler.transformQuery(inputQueryWithDateNonOptionalSortCriterion, responderData, settings)

      assert(transformedQuery === transformedQueryWithDateNonOptionalSortCriterion)

    }

    "transform an input query with a date as a non optional sort criterion (submitted in complex schema)" in {

      val transformedQuery =
        QueryHandler.transformQuery(inputQueryWithDateNonOptionalSortCriterionComplex, responderData, settings)

      assert(transformedQuery === transformedQueryWithDateNonOptionalSortCriterion)

    }

    "transform an input query with a date as non optional sort criterion and a filter" in {

      val transformedQuery =
        QueryHandler.transformQuery(inputQueryWithDateNonOptionalSortCriterionAndFilter, responderData, settings)

      assert(transformedQuery === transformedQueryWithDateNonOptionalSortCriterionAndFilter)

    }

    "transform an input query with a date as non optional sort criterion and a filter (submitted in complex schema)" in {

      val transformedQuery =
        QueryHandler.transformQuery(inputQueryWithDateNonOptionalSortCriterionAndFilterComplex, responderData, settings)

      assert(transformedQuery === transformedQueryWithDateNonOptionalSortCriterionAndFilter)

    }

    "transform an input query with a date as an optional sort criterion" in {

      val transformedQuery =
        QueryHandler.transformQuery(inputQueryWithDateOptionalSortCriterion, responderData, settings)

      assert(transformedQuery === transformedQueryWithDateOptionalSortCriterion)

    }

    "transform an input query with a date as an optional sort criterion (submitted in complex schema)" in {

      val transformedQuery =
        QueryHandler.transformQuery(inputQueryWithDateOptionalSortCriterionComplex, responderData, settings)

      assert(transformedQuery === transformedQueryWithDateOptionalSortCriterion)

    }

    "transform an input query with a date as an optional sort criterion and a filter" in {

      val transformedQuery =
        QueryHandler.transformQuery(inputQueryWithDateOptionalSortCriterionAndFilter, responderData, settings)

      assert(transformedQuery === transformedQueryWithDateOptionalSortCriterionAndFilter)

    }

    "transform an input query with a date as an optional sort criterion and a filter (submitted in complex schema)" in {

      val transformedQuery =
        QueryHandler.transformQuery(inputQueryWithDateOptionalSortCriterionAndFilterComplex, responderData, settings)

      assert(transformedQuery === transformedQueryWithDateOptionalSortCriterionAndFilter)

    }

    "transform an input query with a decimal as an optional sort criterion" in {

      val transformedQuery =
        QueryHandler.transformQuery(inputQueryWithDecimalOptionalSortCriterion, responderData, settings)

      assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterion)
    }

    "transform an input query with a decimal as an optional sort criterion (submitted in complex schema)" in {

      val transformedQuery =
        QueryHandler.transformQuery(inputQueryWithDecimalOptionalSortCriterionComplex, responderData, settings)

      assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterion)
    }

    "transform an input query with a decimal as an optional sort criterion and a filter" in {

      val transformedQuery =
        QueryHandler.transformQuery(inputQueryWithDecimalOptionalSortCriterionAndFilter, responderData, settings)

      assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterionAndFilter)
    }

    "transform an input query with a decimal as an optional sort criterion and a filter (submitted in complex schema)" in {

      val transformedQuery =
        QueryHandler.transformQuery(inputQueryWithDecimalOptionalSortCriterionAndFilterComplex, responderData, settings)

      // TODO: user provided statements and statement generated for sorting should be unified (https://github.com/dhlab-basel/Knora/issues/1195)
      assert(transformedQuery === transformedQueryWithDecimalOptionalSortCriterionAndFilterComplex)
    }

    "transform an input query using rdfs:label and a literal in the simple schema" in {
      val transformedQuery =
        QueryHandler.transformQuery(InputQueryWithRdfsLabelAndLiteralInSimpleSchema, responderData, settings)

      assert(transformedQuery === TransformedQueryWithRdfsLabelAndLiteral)
    }

    "transform an input query using rdfs:label and a literal in the complex schema" in {
      val transformedQuery =
        QueryHandler.transformQuery(InputQueryWithRdfsLabelAndLiteralInComplexSchema, responderData, settings)

      assert(transformedQuery === TransformedQueryWithRdfsLabelAndLiteral)
    }

    "transform an input query using rdfs:label and a variable in the simple schema" in {
      val transformedQuery =
        QueryHandler.transformQuery(InputQueryWithRdfsLabelAndVariableInSimpleSchema, responderData, settings)

      assert(transformedQuery === TransformedQueryWithRdfsLabelAndVariable)
    }

    "transform an input query using rdfs:label and a variable in the complex schema" in {
      val transformedQuery =
        QueryHandler.transformQuery(InputQueryWithRdfsLabelAndVariableInComplexSchema, responderData, settings)

      assert(transformedQuery === TransformedQueryWithRdfsLabelAndVariable)
    }

    "transform an input query using rdfs:label and a regex in the simple schema" in {
      val transformedQuery =
        QueryHandler.transformQuery(InputQueryWithRdfsLabelAndRegexInSimpleSchema, responderData, settings)

      assert(transformedQuery === TransformedQueryWithRdfsLabelAndRegex)
    }

    "transform an input query using rdfs:label and a regex in the complex schema" in {
      val transformedQuery =
        QueryHandler.transformQuery(InputQueryWithRdfsLabelAndRegexInComplexSchema, responderData, settings)

      assert(transformedQuery === TransformedQueryWithRdfsLabelAndRegex)
    }

    "transform an input query with UNION scopes in the simple schema" in {
      val transformedQuery =
        QueryHandler.transformQuery(InputQueryWithUnionScopes, responderData, settings)

      assert(transformedQuery === TransformedQueryWithUnionScopes)
    }

    "reorder query patterns in where clause" in {
      val constructQuery = GravsearchParser.parseQuery(queryToReorder)
      val sortedPatterns = QueryHandler.reorderPatternsByDependency(constructQuery.whereClause.patterns)
      val statements = sortedPatterns.filter(p => p.isInstanceOf[StatementPattern])
      // check that statements are brought to top
      val topElements = sortedPatterns.slice(0, statements.size)
      assert(topElements.equals(statements))
      // check the order of statements
      val mainResourceStatements = statements.slice(statements.size - 3, statements.size)
      assert(!mainResourceStatements.exists(p => p.asInstanceOf[StatementPattern].subj.toSparql !== "?letter"))
    }
    "reorder query patterns in where clause with union" in {
      val constructQuery = GravsearchParser.parseQuery(queryToReorderWithUnion)
      val sortedPatterns = QueryHandler.reorderPatternsByDependency(constructQuery.whereClause.patterns)
      val statements = sortedPatterns.filter(p => p.isInstanceOf[StatementPattern])
      // check that statements are brought to top
      val topElements = sortedPatterns.slice(0, statements.size)
      assert(topElements.equals(statements))
      //check order of statements in each block
      val unionPattern: Set[UnionPattern] = sortedPatterns.collect { case pattern: UnionPattern => pattern }
      val firstBlock = unionPattern.head.blocks.head
      val firstBlockStatements = firstBlock.collect {
        case pattern: StatementPattern => pattern
      }
      assert(firstBlockStatements.head.subj.toSparql == "?int")
      assert(firstBlockStatements.last.subj.toSparql == "?thing")
      assert(firstBlockStatements.last.obj.toSparql == "?richtext")
      val secondBlock = unionPattern.head.blocks.last
      val secondBlockStatements = secondBlock.collect {
        case pattern: StatementPattern => pattern
      }
      assert(secondBlockStatements.head.subj.toSparql == "?int")
      assert(secondBlockStatements.last.subj.toSparql == "?thing")
      assert(secondBlockStatements.last.obj.toSparql == "?text")
    }

    "reorder query patterns in where clause with optional" in {
      val constructQuery = GravsearchParser.parseQuery(queryWithOptional)
      val sortedPatterns = QueryHandler.reorderPatternsByDependency(constructQuery.whereClause.patterns)
      val statements = sortedPatterns.filter(p => p.isInstanceOf[StatementPattern])
      // check that statements are brought to top
      val topElements = sortedPatterns.slice(0, statements.size)
      assert(topElements.equals(statements))
      // check statements inside optional pattern
      val optionalPattern: Set[OptionalPattern] = sortedPatterns.collect { case pattern: OptionalPattern => pattern }
      val optionalPatternStatements = optionalPattern.head.patterns.collect {
        case pattern: StatementPattern => pattern
      }
      assert(optionalPatternStatements.last.subj.toSparql == "?document")
      assert(optionalPatternStatements.last.obj.toSparql == "?recipient")
    }

    "reorder query patterns with minus scope" in {
      val constructQuery = GravsearchParser.parseQuery(queryToReorderWithMinus)
      val sortedPatterns = QueryHandler.reorderPatternsByDependency(constructQuery.whereClause.patterns)
      val statements = sortedPatterns.filter(p => p.isInstanceOf[StatementPattern])
      // check that statements are brought to top
      val topElements = sortedPatterns.slice(0, statements.size)
      assert(topElements.equals(statements))
      // check statements inside minus pattern
      val minusPattern: Set[MinusPattern] = sortedPatterns.collect { case pattern: MinusPattern        => pattern }
      val minusPatternStatements = minusPattern.head.patterns.collect { case pattern: StatementPattern => pattern }
      assert(minusPatternStatements.last.subj.toSparql == "?thing")
      assert(minusPatternStatements.last.obj.toSparql == "?intVal")
    }
  }
}
