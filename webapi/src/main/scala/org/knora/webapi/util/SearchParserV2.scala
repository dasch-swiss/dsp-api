package org.knora.webapi.util

import org.eclipse.rdf4j.query.algebra._
import org.eclipse.rdf4j.query.parser.ParsedQuery
import org.eclipse.rdf4j.query.parser.sparql._

/**
  * Parses the client's query for an extended search in API v2.
  */
class SearchParserV2 {
    private val sparqlParserFactory = new SPARQLParserFactory()
    private val sparqlParser = sparqlParserFactory.getParser
    private val visitor = new TestQueryModelVisitor

    def parseSearchQuery(query: String): ParsedQuery = {
        sparqlParser.parseQuery(query, "http://api.knora.org/ontology/knora-api/simple/v2#")
    }

    def test: Unit = {
        val parsedQuery: ParsedQuery = parseSearchQuery(SearchParserV2.TestQuery)
        parsedQuery.getTupleExpr.visit(visitor)
    }
}

class TestQueryModelVisitor extends QueryModelVisitor[Exception] {
    private def sparqlVarToString(sparqlVar: Var): String = {
        if (sparqlVar.isAnonymous) {
            sparqlVar.getValue.stringValue
        } else {
            "?" + sparqlVar.getName
        }
    }

    override def meet(node: Slice): Unit = {
        println(s"Got Slice")
        node.visitChildren(this)
    }

    override def meet(node: StatementPattern): Unit = {
        val sub = sparqlVarToString(node.getSubjectVar)
        val pred = sparqlVarToString(node.getPredicateVar)
        val obj = sparqlVarToString(node.getObjectVar)
        println(s"Got StatementPattern: $sub $pred $obj .")
    }

    override def meet(node: Str): Unit = {
        println(s"Got Str")
        node.visitChildren(this)
    }

    override def meet(node: Sum): Unit = {
        println(s"Got Sum")
        node.visitChildren(this)
    }

    override def meet(node: Union): Unit = {
        println(s"Got Union")
        node.visitChildren(this)
    }

    override def meet(node: ValueConstant): Unit = {
        println(s"Got ValueConstant")
        node.visitChildren(this)
    }

    override def meet(node: ListMemberOperator): Unit = {
        println(s"Got ListMemberOperator")
        node.visitChildren(this)
    }

    override def meet(node: Var): Unit = {
        println(s"Got Var")
        node.visitChildren(this)
    }

    override def meet(node: ZeroLengthPath): Unit = {
        println(s"Got ZeroLengthPath")
        node.visitChildren(this)
    }

    override def meet(node: Regex): Unit = {
        println(s"Got Regex")
        node.visitChildren(this)
    }

    override def meet(node: Reduced): Unit = {
        println(s"Got Reduced")
        node.visitChildren(this)
    }

    override def meet(node: ProjectionElemList): Unit = {
        println(s"Got ProjectionElemList")
        node.visitChildren(this)
    }

    override def meet(node: ProjectionElem): Unit = {
        println(s"Got ProjectionElem")
        node.visitChildren(this)
    }

    override def meet(node: Projection): Unit = {
        println(s"Got Projection")
        node.visitChildren(this)
    }

    override def meet(node: OrderElem): Unit = {
        println(s"Got OrderElem")
        node.visitChildren(this)
    }

    override def meet(node: Order): Unit = {
        println(s"Got Order")
        node.visitChildren(this)
    }

    override def meet(node: Or): Unit = {
        println(s"Got Or")
        node.visitChildren(this)
    }

    override def meet(node: Not): Unit = {
        println(s"Got Not")
        node.visitChildren(this)
    }

    override def meet(node: Namespace): Unit = {
        println(s"Got Namespace")
        node.visitChildren(this)
    }

    override def meet(node: MultiProjection): Unit = {
        println(s"Got MultiProjection")
        node.visitChildren(this)
    }

    override def meet(move: Move): Unit = {
        println(s"Got Move $move")
        move.visitChildren(this)
    }

    override def meet(node: Coalesce): Unit = {
        println(s"Got Coalesce")
        node.visitChildren(this)
    }

    override def meet(node: Compare): Unit = {
        println(s"Got Compare")
        node.visitChildren(this)
    }

    override def meet(node: CompareAll): Unit = {
        println(s"Got CompareAll")
        node.visitChildren(this)
    }

    override def meet(node: IsLiteral): Unit = {
        println(s"Got IsLiteral")
        node.visitChildren(this)
    }

    override def meet(node: IsNumeric): Unit = {
        println(s"Got IsNumeric")
        node.visitChildren(this)
    }

    override def meet(node: IsResource): Unit = {
        println(s"Got IsResource")
        node.visitChildren(this)
    }

    override def meet(node: IsURI): Unit = {
        println(s"Got IsURI")
        node.visitChildren(this)
    }

    override def meet(node: SameTerm): Unit = {
        println(s"Got SameTerm")
        node.visitChildren(this)
    }

    override def meet(modify: Modify): Unit = {
        println(s"Got Modify $modify")
        modify.visitChildren(this)
    }

    override def meet(node: Min): Unit = {
        println(s"Got Min")
        node.visitChildren(this)
    }

    override def meet(node: Max): Unit = {
        println(s"Got Max")
        node.visitChildren(this)
    }

    override def meet(node: ExtensionElem): Unit = {
        println(s"Got ExtensionElem")
        node.visitChildren(this)
    }

    override def meet(node: Extension): Unit = {
        println(s"Got Extension")
        node.visitChildren(this)
    }

    override def meet(node: Exists): Unit = {
        println(s"Got Exists")
        node.visitChildren(this)
    }

    override def meet(node: EmptySet): Unit = {
        println(s"Got EmptySet")
        node.visitChildren(this)
    }

    override def meet(node: Distinct): Unit = {
        println(s"Got Distinct")
        node.visitChildren(this)
    }

    override def meet(node: Difference): Unit = {
        println(s"Got Difference")
        node.visitChildren(this)
    }

    override def meet(deleteData: DeleteData): Unit = {
        println(s"Got DeleteData $deleteData")
        deleteData.visitChildren(this)
    }

    override def meet(node: Datatype): Unit = {
        println(s"Got Datatype")
        node.visitChildren(this)
    }

    override def meet(clear: Clear): Unit = {
        println(s"Got Clear $clear")
        clear.visitChildren(this)
    }

    override def meet(node: Bound): Unit = {
        println(s"Got Bound")
        node.visitChildren(this)
    }

    override def meet(node: BNodeGenerator): Unit = {
        println(s"Got BNodeGenerator")
        node.visitChildren(this)
    }

    override def meet(node: BindingSetAssignment): Unit = {
        println(s"Got BindingSetAssignment")
        node.visitChildren(this)
    }

    override def meet(node: Avg): Unit = {
        println(s"Got Avg")
        node.visitChildren(this)
    }

    override def meet(node: ArbitraryLengthPath): Unit = {
        println(s"Got ArbitraryLengthPath")
        node.visitChildren(this)
    }

    override def meet(node: And): Unit = {
        println(s"Got And")
        node.visitChildren(this)
    }

    override def meet(add: Add): Unit = {
        println(s"Got Add $add")
        add.visitChildren(this)
    }

    override def meet(node: QueryRoot): Unit = {
        println(s"Got QueryRoot")
        node.visitChildren(this)
    }

    override def meet(node: DescribeOperator): Unit = {
        println(s"Got Str")
        node.visitChildren(this)
    }

    override def meet(copy: Copy): Unit = {
        println(s"Got Copy $copy")
        copy.visitChildren(this)
    }

    override def meet(node: Count): Unit = {
        println(s"Got Str")
        node.visitChildren(this)
    }

    override def meet(create: Create): Unit = {
        println(s"Got Create $create")
        create.visitChildren(this)
    }

    override def meet(node: Sample): Unit = {
        println(s"Got Sample")
        node.visitChildren(this)
    }

    override def meet(node: Service): Unit = {
        println(s"Got Service")
        node.visitChildren(this)
    }

    override def meet(node: SingletonSet): Unit = {
        println(s"Got SingletonSet")
        node.visitChildren(this)
    }

    override def meet(node: CompareAny): Unit = {
        println(s"Got CompareAny")
        node.visitChildren(this)
    }

    override def meet(node: Filter): Unit = {
        println(s"Got Filter")
        node.visitChildren(this)
    }

    override def meet(node: FunctionCall): Unit = {
        println(s"Got FunctionCall")
        node.visitChildren(this)
    }

    override def meet(node: Group): Unit = {
        println(s"Got Group")
        node.visitChildren(this)
    }

    override def meet(node: GroupConcat): Unit = {
        println(s"Got GroupConcat")
        node.visitChildren(this)
    }

    override def meet(node: GroupElem): Unit = {
        println(s"Got GroupElem")
        node.visitChildren(this)
    }

    override def meet(node: If): Unit = {
        println(s"Got If")
        node.visitChildren(this)
    }

    override def meet(node: In): Unit = {
        println(s"Got In")
        node.visitChildren(this)
    }

    override def meet(insertData: InsertData): Unit = {
        println(s"Got InsertData $insertData")
        insertData.visitChildren(this)
    }

    override def meet(node: Intersection): Unit = {
        println(s"Got Intersection")
        node.visitChildren(this)
    }

    override def meet(node: IRIFunction): Unit = {
        println(s"Got IRIFunction")
        node.visitChildren(this)
    }

    override def meet(node: IsBNode): Unit = {
        println(s"Got IsBNode")
        node.visitChildren(this)
    }

    override def meet(node: MathExpr): Unit = {
        println(s"Got MathExpr")
        node.visitChildren(this)
    }

    override def meet(node: LocalName): Unit = {
        println(s"Got LocalName")
        node.visitChildren(this)
    }

    override def meet(load: Load): Unit = {
        println(s"Got Load $load")
        load.visitChildren(this)
    }

    override def meet(node: Like): Unit = {
        println(s"Got Like")
        node.visitChildren(this)
    }

    override def meet(node: LeftJoin): Unit = {
        println(s"Got LeftJoin")
        node.visitChildren(this)
    }

    override def meet(node: LangMatches): Unit = {
        println(s"Got LangMatches")
        node.visitChildren(this)
    }

    override def meet(node: Lang): Unit = {
        println(s"Got Lang")
        node.visitChildren(this)
    }

    override def meet(node: Label): Unit = {
        println(s"Got Label")
        node.visitChildren(this)
    }

    override def meet(node: Join): Unit = {
        println(s"Got Join")
        node.visitChildren(this)
    }

    override def meetOther(node: QueryModelNode): Unit = {
        println(s"Got QueryModelNode")
        node.visitChildren(this)
    }
}


object SearchParserV2 {
var TestQuery: String =
    """
      |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
      |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
      |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
      |
      |CONSTRUCT {
      |    ?book a ?bookType .
      |    ?book rdfs:label ?bookLabel .
      |    ?page a ?pageType .
      |    ?page rdfs:label ?pageLabel .
      |    ?page incunabula:isPartOf ?book .
      |} WHERE {
      |    ?book a incunabula:book .
      |    ?book rdfs:label ?bookLabel .
      |    ?book incunabula:publisher "Lienhart Ysenhut" .
      |    ?book incunabula:pubdate ?pubdate .
      |    FILTER(?pubdate < "GREGORIAN:1500")
      |    ?page a incunabula:page .
      |    ?page rdfs:label ?pageLabel .
      |    ?page incunabula:partOf ?book .
      |    ?page incunabula:pagenum ?pagenum .
      |    ?page incunabula:seqnum ?seqnum .
      |    FILTER(?seqnum < 20)
      |
      |    {
      |        ?page incunabula:pagenum "a7r" .
      |    } UNION {
      |        ?page incunabula:pagenum "a8r" .
      |    }
      |}
    """.stripMargin
}