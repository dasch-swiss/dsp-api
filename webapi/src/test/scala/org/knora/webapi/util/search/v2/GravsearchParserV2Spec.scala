/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
 *
 * This file is part of Knora.
 *
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.util.search.v2

import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.search._
import org.knora.webapi.{CoreSpec, GravsearchException}

/**
  * Tests [[GravsearchParserV2]].
  */
class GravsearchParserV2Spec extends CoreSpec() {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    "The GravsearchParserV2 object" should {
        "parse a Gravsearch query" in {
            val parsed: ConstructQuery = GravsearchParserV2.parseQuery(Query)
            parsed should ===(ParsedQuery)
            val reparsed = GravsearchParserV2.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query with a BIND" in {
            val parsed: ConstructQuery = GravsearchParserV2.parseQuery(QueryWithBind)
            parsed should ===(ParsedQueryWithBind)
            val reparsed = GravsearchParserV2.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query with a FILTER containing a Boolean operator" in {
            val parsed: ConstructQuery = GravsearchParserV2.parseQuery(QueryForAThingRelatingToAnotherThing)
            parsed should ===(ParsedQueryForAThingRelatingToAnotherThing)
            val reparsed = GravsearchParserV2.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query with FILTER NOT EXISTS" in {
            val parsed: ConstructQuery = GravsearchParserV2.parseQuery(QueryWithFilterNotExists)
            parsed should ===(ParsedQueryWithFilterNotExists)
            val reparsed = GravsearchParserV2.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query with MINUS" in {
            val parsed: ConstructQuery = GravsearchParserV2.parseQuery(QueryWithMinus)
            parsed should ===(ParsedQueryWithMinus)
            val reparsed = GravsearchParserV2.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query with OFFSET" in {
            val parsed: ConstructQuery = GravsearchParserV2.parseQuery(QueryWithOffset)
            parsed should ===(ParsedQueryWithOffset)
            val reparsed = GravsearchParserV2.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query with a FILTER containing a regex function" in {
            val parsed = GravsearchParserV2.parseQuery(queryWithFilterContainingRegex)
            parsed should ===(ParsedQueryWithFilterContainingRegex)
            val reparsed = GravsearchParserV2.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "accept a custom 'match' function in a FILTER" in {
            val parsed: ConstructQuery = GravsearchParserV2.parseQuery(QueryWithMatchFunction)
            parsed should ===(ParsedQueryWithMatchFunction)
            val reparsed = GravsearchParserV2.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query with a FILTER containing a lang function" in {
            val parsed = GravsearchParserV2.parseQuery(QueryWithFilterContainingLang)
            parsed should ===(ParsedQueryWithLangFunction)
            val reparsed = GravsearchParserV2.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query containing a FILTER in an OPTIONAL" in {
            val parsed = GravsearchParserV2.parseQuery(QueryWithFilterInOptional)
            parsed should ===(ParsedQueryWithFilterInOptional)
            val reparsed = GravsearchParserV2.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query containing a nested OPTIONAL" in {
            val parsed = GravsearchParserV2.parseQuery(QueryStrWithNestedOptional)
            parsed should ===(ParsedQueryWithNestedOptional)
            val reparsed = GravsearchParserV2.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query containing a UNION in an OPTIONAL" in {
            val parsed = GravsearchParserV2.parseQuery(QueryStrWithUnionInOptional)
            parsed should ===(ParsedQueryWithUnionInOptional)
            val reparsed = GravsearchParserV2.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "reject a SELECT query" in {
            assertThrows[GravsearchException] {
                GravsearchParserV2.parseQuery(SparqlSelect)
            }
        }

        "reject a DESCRIBE query" in {
            assertThrows[GravsearchException] {
                GravsearchParserV2.parseQuery(SparqlDescribe)
            }
        }

        "reject an INSERT" in {
            assertThrows[GravsearchException] {
                GravsearchParserV2.parseQuery(SparqlInsert)
            }
        }

        "reject a DELETE" in {
            assertThrows[GravsearchException] {
                GravsearchParserV2.parseQuery(SparqlDelete)
            }
        }

        "reject an internal ontology IRI" in {
            assertThrows[GravsearchException] {
                GravsearchParserV2.parseQuery(QueryWithInternalEntityIri)
            }
        }

        "reject left-nested UNIONs" in {
            assertThrows[GravsearchException] {
                GravsearchParserV2.parseQuery(QueryWithLeftNestedUnion)
            }
        }

        "reject right-nested UNIONs" in {
            assertThrows[GravsearchException] {
                GravsearchParserV2.parseQuery(QueryStrWithRightNestedUnion)
            }
        }

        "reject an unsupported FILTER" in {
            assertThrows[GravsearchException] {
                GravsearchParserV2.parseQuery(QueryWithWrongFilter)
            }
        }
    }

    val Query: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book a ?bookType .
          |    ?book rdfs:label ?bookLabel .
          |    ?book incunabula:publisher ?bookPublisher .
          |    ?book incunabula:publoc ?bookPubLoc .
          |    ?book knora-api:isMainResource "true"^^xsd:boolean .
          |    ?page a ?pageType .
          |    ?page rdfs:label ?pageLabel .
          |    ?page incunabula:isPartOf ?book .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |
          |    OPTIONAL {
          |        ?book incunabula:publisher ?bookPublisher .
          |        ?book incunabula:publoc ?bookPubLoc .
          |    }
          |
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |    ?page a incunabula:page .
          |    ?page rdfs:label ?pageLabel .
          |    ?page incunabula:isPartOf ?book .
          |
          |    {
          |        ?page incunabula:pagenum "a7r"^^xsd:string .
          |        ?page incunabula:seqnum "14"^^xsd:integer.
          |    } UNION {
          |        ?page incunabula:pagenum "a8r"^^xsd:string .
          |        ?page incunabula:seqnum "16"^^xsd:integer.
          |    } UNION {
          |        ?page incunabula:pagenum "a9r"^^xsd:string .
          |        ?page incunabula:seqnum ?seqnum.
          |        FILTER(?seqnum > "17"^^xsd:integer)
          |    }
          |}
        """.stripMargin

    val ParsedQuery = ConstructQuery(
        orderBy = Nil,
        whereClause = WhereClause(patterns = Vector(
            StatementPattern(
                obj = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri),
                pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                subj = QueryVariable("book")
            ),
            StatementPattern(
                obj = QueryVariable("bookLabel"),
                pred = IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                subj = QueryVariable("book")
            ),
            OptionalPattern(patterns = Vector(
                StatementPattern(
                    obj = QueryVariable("bookPublisher"),
                    pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher".toSmartIri),
                    subj = QueryVariable("book")
                ),
                StatementPattern(
                    obj = QueryVariable("bookPubLoc"),
                    pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publoc".toSmartIri),
                    subj = QueryVariable("book")
                )
            )),
            StatementPattern(
                obj = QueryVariable("pubdate"),
                pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate".toSmartIri),
                subj = QueryVariable("book")
            ),
            StatementPattern(
                obj = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri),
                pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                subj = QueryVariable("page")
            ),
            StatementPattern(
                obj = QueryVariable("pageLabel"),
                pred = IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                subj = QueryVariable("page")
            ),
            StatementPattern(
                obj = QueryVariable("book"),
                pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#isPartOf".toSmartIri),
                subj = QueryVariable("page")
            ),
            UnionPattern(blocks = Vector(
                Vector(
                    StatementPattern(
                        obj = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
                            value = "a7r"
                        ),
                        pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pagenum".toSmartIri
                        ),
                        subj = QueryVariable("page")
                    ),
                    StatementPattern(
                        obj = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri,
                            value = "14"
                        ),
                        pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#seqnum".toSmartIri
                        ),
                        subj = QueryVariable("page")
                    )
                ),
                Vector(
                    StatementPattern(
                        obj = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
                            value = "a8r"
                        ),
                        pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pagenum".toSmartIri
                        ),
                        subj = QueryVariable("page")
                    ),
                    StatementPattern(
                        obj = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri,
                            value = "16"
                        ),
                        pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#seqnum".toSmartIri
                        ),
                        subj = QueryVariable("page")
                    )
                ),
                Vector(
                    StatementPattern(
                        obj = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
                            value = "a9r"
                        ),
                        pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pagenum".toSmartIri
                        ),
                        subj = QueryVariable("page")
                    ),
                    StatementPattern(
                        obj = QueryVariable("seqnum"),
                        pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#seqnum".toSmartIri
                        ),
                        subj = QueryVariable("page")
                    ),
                    FilterPattern(expression = CompareExpression(
                        rightArg = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri,
                            value = "17"
                        ),
                        operator = CompareExpressionOperator.GREATER_THAN,
                        leftArg = QueryVariable("seqnum")
                    ))
                )
            )),
            FilterPattern(expression = CompareExpression(
                rightArg = XsdLiteral(
                    datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
                    value = "GREGORIAN:1500"
                ),
                operator = CompareExpressionOperator.LESS_THAN,
                leftArg = QueryVariable("pubdate")
            ))
        ), positiveEntities = Set(
            IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publoc".toSmartIri),
            QueryVariable("page"),
            IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher".toSmartIri),
            IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri),
            QueryVariable("bookPubLoc"),
            IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri),
            QueryVariable("bookLabel"),
            IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri),
            IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#seqnum".toSmartIri),
            IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate".toSmartIri),
            IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#isPartOf".toSmartIri),
            IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
            QueryVariable("bookPublisher"),
            IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
            QueryVariable("book"),
            IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pagenum".toSmartIri),
            QueryVariable("pubdate"),
            QueryVariable("seqnum"),
            QueryVariable("bookType"),
            QueryVariable("pageLabel"),
            QueryVariable("pageType")
        )),
        constructClause = ConstructClause(statements = Vector(
            StatementPattern(
                obj = QueryVariable("bookType"),
                pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                subj = QueryVariable("book")
            ),
            StatementPattern(
                obj = QueryVariable("bookLabel"),
                pred = IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                subj = QueryVariable("book")
            ),
            StatementPattern(
                obj = QueryVariable("bookPublisher"),
                pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher".toSmartIri),
                subj = QueryVariable("book")
            ),
            StatementPattern(
                obj = QueryVariable("bookPubLoc"),
                pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publoc".toSmartIri),
                subj = QueryVariable("book")
            ),
            StatementPattern(
                obj = XsdLiteral(
                    datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
                    value = "true"
                ),
                pred = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri),
                subj = QueryVariable("book")
            ),
            StatementPattern(
                obj = QueryVariable("pageType"),
                pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                subj = QueryVariable("page")
            ),
            StatementPattern(
                obj = QueryVariable("pageLabel"),
                pred = IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                subj = QueryVariable("page")
            ),
            StatementPattern(
                obj = QueryVariable("book"),
                pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#isPartOf".toSmartIri),
                subj = QueryVariable("page")
            )
        ))
    )

    val QueryWithBind: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing a ?thingType .
          |    ?thing rdfs:label ?thingLabel .
          |} WHERE {
          |    BIND(<http://rdfh.ch/a-thing> AS ?thing)
          |    ?thing a knora-api:Resource .
          |    ?thing a anything:Thing .
          |}
        """.stripMargin

    val ParsedQueryWithBind: ConstructQuery = ConstructQuery(
        constructClause = ConstructClause(statements = Vector(
            StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                obj = QueryVariable(variableName = "thingType")
            ),
            StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                obj = QueryVariable(variableName = "thingLabel")
            )
        )),
        whereClause = WhereClause(
            patterns = Vector(
                BindPattern(
                    variable = QueryVariable(variableName = "thing"),
                    iriValue = IriRef("http://rdfh.ch/a-thing".toSmartIri)
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    obj = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri)
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    obj = IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri)
                )
            ),
            positiveEntities = Set(
                QueryVariable(variableName = "thing"),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
                IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                QueryVariable(variableName = "thingLabel"),
                IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                QueryVariable(variableName = "thingType"),
                IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri)
            )
        ),
        orderBy = Nil,
        offset = 0
    )

    val QueryWithFilterNotExists: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing a ?thingType .
          |    ?thing rdfs:label ?thingLabel .
          |} WHERE {
          |    ?thing a anything:Thing .
          |
          |    FILTER NOT EXISTS {
          |        ?thing anything:hasOtherThing ?aThing .
          |    }
          |}
        """.stripMargin

    val ParsedQueryWithFilterNotExists: ConstructQuery = ConstructQuery(
        orderBy = Nil,
        whereClause = WhereClause(patterns = Vector(
            StatementPattern(
                namedGraph = None,
                obj = IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri),
                pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                subj = QueryVariable(variableName = "thing")
            ),
            FilterNotExistsPattern(patterns = Vector(StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "aThing"),
                pred = IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasOtherThing".toSmartIri),
                subj = QueryVariable(variableName = "thing")
            )))
        ),
            positiveEntities = Set( // note that entities from `?thing anything:hasOtherThing ?aThing .` must not be not mentioned here (unless they are also present elsewhere)
                QueryVariable("thingLabel"),
                QueryVariable("thing"),
                IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                QueryVariable("thingType"),
                IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri)
            )
        ),
        constructClause = ConstructClause(statements = Vector(
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "thingType"),
                pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                subj = QueryVariable(variableName = "thing")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "thingLabel"),
                pred = IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                subj = QueryVariable(variableName = "thing")
            )
        ))
    )

    val QueryWithMinus: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing a ?thingType .
          |    ?thing rdfs:label ?thingLabel .
          |} WHERE {
          |    ?thing a anything:Thing .
          |
          |    MINUS {
          |        ?thing anything:hasOtherThing ?aThing .
          |    }
          |}
        """.stripMargin

    val ParsedQueryWithMinus: ConstructQuery = ConstructQuery(
        orderBy = Nil,
        whereClause = WhereClause(patterns = Vector(
            StatementPattern(
                namedGraph = None,
                obj = IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri),
                pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                subj = QueryVariable(variableName = "thing")
            ),
            MinusPattern(patterns = Vector(StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "aThing"),
                pred = IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasOtherThing".toSmartIri),
                subj = QueryVariable(variableName = "thing")
            )))
        ),
            positiveEntities = Set( // note that entities from `?thing anything:hasOtherThing ?aThing .` must not be not mentioned here (unless they are also present elsewhere)
                QueryVariable("thingLabel"),
                QueryVariable("thing"),
                IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                QueryVariable("thingType"),
                IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri)
            )
        ),
        constructClause = ConstructClause(statements = Vector(
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "thingType"),
                pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                subj = QueryVariable(variableName = "thing")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "thingLabel"),
                pred = IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                subj = QueryVariable(variableName = "thing")
            )
        ))
    )

    val QueryWithOffset: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing a ?thingType .
          |    ?thing rdfs:label ?thingLabel .
          |} WHERE {
          |    ?thing a anything:Thing .
          |} OFFSET 10
        """.stripMargin

    val ParsedQueryWithOffset: ConstructQuery = ConstructQuery(
        offset = 10,
        orderBy = Nil,
        whereClause = WhereClause(patterns = Vector(StatementPattern(
            namedGraph = None,
            obj = IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri),
            pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
            subj = QueryVariable(variableName = "thing")
        )),
            positiveEntities = Set(
                QueryVariable("thingLabel"),
                QueryVariable("thing"),
                IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                QueryVariable("thingType"),
                IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri)
            )
        ),
        constructClause = ConstructClause(statements = Vector(
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "thingType"),
                pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                subj = QueryVariable(variableName = "thing")
            ),
            StatementPattern(
                namedGraph = None,
                obj = QueryVariable(variableName = "thingLabel"),
                pred = IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                subj = QueryVariable(variableName = "thing")
            )
        ))
    )

    val QueryStrWithNestedOptional: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book a ?bookType .
          |    ?book rdfs:label ?bookLabel .
          |    ?book knora-api:isMainResource "true"^^xsd:boolean .
          |    ?book incunabula:title ?bookTitle .
          |    ?page a ?pageType .
          |    ?page rdfs:label ?pageLabel .
          |    ?page incunabula:isPartOf ?book .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |
          |    OPTIONAL {
          |        ?book incunabula:publisher "Lienhart Ysenhut"^^xsd:string .
          |
          |        OPTIONAL {
          |            ?book incunabula:title ?bookTitle .
          |        }
          |    }
          |
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |}
        """.stripMargin

    val ParsedQueryWithNestedOptional: ConstructQuery = ConstructQuery(
        constructClause = ConstructClause(statements = Vector(
            StatementPattern(
                subj = QueryVariable(variableName = "book"),
                pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                obj = QueryVariable(variableName = "bookType")
            ),
            StatementPattern(
                subj = QueryVariable(variableName = "book"),
                pred = IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                obj = QueryVariable(variableName = "bookLabel")
            ),
            StatementPattern(
                subj = QueryVariable(variableName = "book"),
                pred = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri),
                obj = XsdLiteral(
                    value = "true",
                    datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                )
            ),
            StatementPattern(
                subj = QueryVariable(variableName = "book"),
                pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri),
                obj = QueryVariable(variableName = "bookTitle")
            ),
            StatementPattern(
                subj = QueryVariable(variableName = "page"),
                pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                obj = QueryVariable(variableName = "pageType")
            ),
            StatementPattern(
                subj = QueryVariable(variableName = "page"),
                pred = IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                obj = QueryVariable(variableName = "pageLabel")
            ),
            StatementPattern(
                subj = QueryVariable(variableName = "page"),
                pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#isPartOf".toSmartIri),
                obj = QueryVariable(variableName = "book")
            )
        )),
        whereClause = WhereClause(
            patterns = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    obj = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri)
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                    obj = QueryVariable(variableName = "bookLabel")
                ),
                OptionalPattern(patterns = Vector(
                    StatementPattern(
                        subj = QueryVariable(variableName = "book"),
                        pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher".toSmartIri),
                        obj = XsdLiteral(
                            value = "Lienhart Ysenhut",
                            datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
                        )
                    ),
                    OptionalPattern(patterns = Vector(StatementPattern(
                        subj = QueryVariable(variableName = "book"),
                        pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri),
                        obj = QueryVariable(variableName = "bookTitle")
                    )))
                )),
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate".toSmartIri),
                    obj = QueryVariable(variableName = "pubdate")
                ),
                FilterPattern(expression = CompareExpression(
                    leftArg = QueryVariable(variableName = "pubdate"),
                    operator = CompareExpressionOperator.LESS_THAN,
                    rightArg = XsdLiteral(
                        value = "GREGORIAN:1500",
                        datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
                    )
                ))
            ),
            positiveEntities = Set(
                IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri),
                QueryVariable(variableName = "bookType"),
                IriRef("http://www.w3.org/2000/01/rdf-schema#label".toSmartIri),
                QueryVariable(variableName = "page"),
                QueryVariable(variableName = "book"),
                IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher".toSmartIri),
                IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri),
                QueryVariable(variableName = "bookTitle"),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri),
                IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate".toSmartIri),
                QueryVariable(variableName = "bookLabel"),
                IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#isPartOf".toSmartIri),
                QueryVariable(variableName = "pubdate"),
                QueryVariable(variableName = "pageType"),
                QueryVariable(variableName = "pageLabel")
            )
        ),
        orderBy = Nil
    )

    val QueryWithWrongFilter: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book a ?bookType .
          |    ?book rdfs:label ?bookLabel .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(BOUND(?pubdate))
          |}
        """.stripMargin

    val QueryWithInternalEntityIri: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://www.knora.org/ontology/0803/incunabula#>
          |
          |CONSTRUCT {
          |    ?book a ?bookType .
          |    ?book rdfs:label ?bookLabel .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |}
        """.stripMargin

    val SparqlSelect: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |
          |SELECT ?subject ?predicate ?object
          |WHERE {
          |    ?subject a incunabula:book .
          |    ?book rdfs:label ?predicate .
          |    ?book incunabula:pubdate ?object .
          |}
        """.stripMargin

    val SparqlDescribe: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |
          |DESCRIBE ?book
          |WHERE {
          |    ?book a incunabula:book .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |}
        """.stripMargin

    val SparqlInsert: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |
          |INSERT DATA {
          |    <http://example.org/12345> a incunabula:book .
          |}
        """.stripMargin

    val SparqlDelete: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |
          |DELETE {
          |    <http://example.org/12345> a incunabula:book .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |}
        """.stripMargin

    val QueryWithLeftNestedUnion: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book a ?bookType .
          |    ?book rdfs:label ?bookLabel .
          |    ?book knora-api:isMainResource "true"^^xsd:boolean .
          |    ?page a ?pageType .
          |    ?page rdfs:label ?pageLabel .
          |    ?page incunabula:isPartOf ?book .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    ?book incunabula:publisher "Lienhart Ysenhut"^^xsd:string .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |    ?page a incunabula:page .
          |    ?page rdfs:label ?pageLabel .
          |    ?page incunabula:isPartOf ?book .
          |
          |    {
          |        ?page incunabula:pagenum "a6r"^^xsd:string .
          |        ?page incunabula:seqnum "12"^^xsd:integer.
          |
          |        {
          |            ?page incunabula:pagenum "a7r"^^xsd:string .
          |            ?page incunabula:seqnum "14"^^xsd:integer.
          |        } UNION {
          |            ?page incunabula:pagenum "a8r"^^xsd:string .
          |            ?page incunabula:seqnum "16"^^xsd:integer.
          |        }
          |    } UNION {
          |        ?page incunabula:pagenum "a9r"^^xsd:string .
          |        ?page incunabula:seqnum "18"^^xsd:integer.
          |    } UNION {
          |        ?page incunabula:pagenum "a10r"^^xsd:string .
          |        ?page incunabula:seqnum "20"^^xsd:integer.
          |    }
          |}
        """.stripMargin

    val QueryStrWithRightNestedUnion: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book a ?bookType .
          |    ?book rdfs:label ?bookLabel .
          |    ?book knora-api:isMainResource "true"^^xsd:boolean .
          |    ?page a ?pageType .
          |    ?page rdfs:label ?pageLabel .
          |    ?page incunabula:isPartOf ?book .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    ?book incunabula:publisher "Lienhart Ysenhut"^^xsd:string .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |    ?page a incunabula:page .
          |    ?page rdfs:label ?pageLabel .
          |    ?page incunabula:isPartOf ?book .
          |
          |    {
          |        ?page incunabula:pagenum "a7r"^^xsd:string .
          |        ?page incunabula:seqnum "14"^^xsd:integer.
          |    } UNION {
          |        ?page incunabula:pagenum "a8r"^^xsd:string .
          |        ?page incunabula:seqnum "16"^^xsd:integer.
          |    } UNION {
          |        ?page incunabula:pagenum "a9r"^^xsd:string .
          |        ?page incunabula:seqnum "18"^^xsd:integer.
          |
          |        {
          |            ?page incunabula:pagenum "a10r"^^xsd:string .
          |            ?page incunabula:seqnum "20"^^xsd:integer.
          |        } UNION {
          |            ?page incunabula:pagenum "a11r"^^xsd:string .
          |            ?page incunabula:seqnum "22"^^xsd:integer.
          |        }
          |    }
          |}
        """.stripMargin

    val QueryStrWithUnionInOptional: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX test: <http://0.0.0.0:3333/ontology/0666/test/simple/v2#>
          |CONSTRUCT {
          |  ?Project knora-api:isMainResource true .
          |  ?isInProject test:isInProject ?Project .
          |} WHERE {
          |  ?Project a knora-api:Resource .
          |  ?Project a test:Project .
          |
          |  OPTIONAL {
          |    ?isInProject test:isInProject ?Project .
          |    test:isInProject knora-api:objectType knora-api:Resource .
          |    ?isInProject a knora-api:Resource .
          |    { ?isInProject a test:BibliographicNotice . } UNION { ?isInProject a test:Person . }
          |  }
          |}
        """.stripMargin

    val ParsedQueryWithUnionInOptional: ConstructQuery = ConstructQuery(
        constructClause = ConstructClause(statements = Vector(
            StatementPattern(
                subj = QueryVariable(variableName = "Project"),
                pred = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri),
                obj = XsdLiteral(
                    value = "true",
                    datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                )
            ),
            StatementPattern(
                subj = QueryVariable(variableName = "isInProject"),
                pred = IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#isInProject".toSmartIri),
                obj = QueryVariable(variableName = "Project")
            )
        )),
        whereClause = WhereClause(
            patterns = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "Project"),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    obj = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri)
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "Project"),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    obj = IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#Project".toSmartIri)
                ),
                OptionalPattern(patterns = Vector(
                    StatementPattern(
                        subj = QueryVariable(variableName = "isInProject"),
                        pred = IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#isInProject".toSmartIri),
                        obj = QueryVariable(variableName = "Project")
                    ),
                    StatementPattern(
                        subj = IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#isInProject".toSmartIri),
                        pred = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri),
                        obj = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri)
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "isInProject"),
                        pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                        obj = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri)
                    ),
                    UnionPattern(blocks = Vector(
                        Vector(StatementPattern(
                            subj = QueryVariable(variableName = "isInProject"),
                            pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                            obj = IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#BibliographicNotice".toSmartIri)
                        )),
                        Vector(StatementPattern(
                            subj = QueryVariable(variableName = "isInProject"),
                            pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                            obj = IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#Person".toSmartIri)
                        ))
                    ))
                ))
            ),
            positiveEntities = Set(
                QueryVariable(variableName = "Project"),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
                IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#BibliographicNotice".toSmartIri),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri),
                IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#isInProject".toSmartIri),
                IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#Project".toSmartIri),
                QueryVariable(variableName = "isInProject"),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri),
                IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#Person".toSmartIri)
            )
        ),
        orderBy = Nil
    )

    val QueryForAThingRelatingToAnotherThing: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?resource knora-api:hasLinkTo <http://rdfh.ch/a-thing> .
          |} WHERE {
          |    ?resource a anything:Thing .
          |
          |    ?resource ?linkingProp <http://rdfh.ch/a-thing> .
          |    FILTER(?linkingProp = anything:isPartOfOtherThing || ?linkingProp = anything:hasOtherThing)
          |
          |}
        """.stripMargin

    val ParsedQueryForAThingRelatingToAnotherThing = ConstructQuery(
        orderBy = Nil,
        whereClause = WhereClause(patterns = Vector(
            StatementPattern(
                obj = IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri),
                pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                subj = QueryVariable(variableName = "resource")
            ),
            StatementPattern(
                obj = IriRef("http://rdfh.ch/a-thing".toSmartIri),
                pred = QueryVariable(variableName = "linkingProp"),
                subj = QueryVariable(variableName = "resource")
            ),
            FilterPattern(expression = OrExpression(
                rightArg = CompareExpression(
                    rightArg = IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasOtherThing".toSmartIri),
                    operator = CompareExpressionOperator.EQUALS,
                    leftArg = QueryVariable(variableName = "linkingProp")
                ),
                leftArg = CompareExpression(
                    rightArg = IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#isPartOfOtherThing".toSmartIri),
                    operator = CompareExpressionOperator.EQUALS,
                    leftArg = QueryVariable(variableName = "linkingProp")
                )
            ))
        ),
            positiveEntities = Set(
                QueryVariable("linkingProp"),
                QueryVariable("resource"),
                IriRef("http://rdfh.ch/a-thing".toSmartIri),
                IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#hasLinkTo".toSmartIri),
                IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri)
            )
        ),
        constructClause = ConstructClause(statements = Vector(StatementPattern(
            obj = IriRef("http://rdfh.ch/a-thing".toSmartIri),
            pred = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#hasLinkTo".toSmartIri),
            subj = QueryVariable(variableName = "resource")
        )))
    )


    val QueryWithExplicitTypeAnnotations: String =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |
          |    ?letter a knora-api:Resource .
          |    ?letter a beol:letter .
          |
          |    ?letter knora-api:hasLinkTo <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
          |    ?letter ?linkingProp1  <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
          |
          |    <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> a knora-api:Resource .
          |
          |    ?letter knora-api:hasLinkTo <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
          |    ?letter ?linkingProp2  <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
          |
          |    <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> a knora-api:Resource .
          |
          |} WHERE {
          |    ?letter a knora-api:Resource .
          |    ?letter a beol:letter .
          |
          |    # Scheuchzer, Johann Jacob 1672-1733
          |    ?letter ?linkingProp1  <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
          |    ?linkingProp1 knora-api:objectType knora-api:Resource .
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient )
          |
          |    <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> a knora-api:Resource .
          |
          |    # Hermann, Jacob 1678-1733
          |    ?letter ?linkingProp2 <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
          |    ?linkingProp2 knora-api:objectType knora-api:Resource .
          |
          |    FILTER(?linkingProp2 = beol:hasAuthor || ?linkingProp2 = beol:hasRecipient )
          |
          |    <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> a knora-api:Resource .
          |}
        """.stripMargin

    val queryWithFilterContainingRegex: String =
        """
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |    CONSTRUCT {
          |
          |        ?mainRes knora-api:isMainResource true .
          |
          |        ?mainRes <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title> ?propVal0 .
          |
          |     } WHERE {
          |
          |        ?mainRes a knora-api:Resource .
          |
          |        ?mainRes a <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book> .
          |
          |
          |        ?mainRes <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title> ?propVal0 .
          |        <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title> knora-api:objectType <http://www.w3.org/2001/XMLSchema#string> .
          |        ?propVal0 a <http://www.w3.org/2001/XMLSchema#string> .
          |
          |        FILTER regex(?propVal0, "Zeit", "i")
          |
          |     }
        """.stripMargin

    val ParsedQueryWithFilterContainingRegex = ConstructQuery(
        ConstructClause(
            statements = Vector(
                StatementPattern(
                    subj = QueryVariable("mainRes"),
                    pred = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri),
                    obj = XsdLiteral("true", "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri)
                ),
                StatementPattern(
                    subj = QueryVariable("mainRes"),
                    pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri),
                    obj = QueryVariable("propVal0")
                )
            )
        ),
        WhereClause(
            patterns = Vector(
                StatementPattern(
                    subj = QueryVariable("mainRes"),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    obj = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri), None),
                StatementPattern(
                    subj = QueryVariable("mainRes"),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    obj = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri)
                ),
                StatementPattern(
                    subj = QueryVariable("mainRes"),
                    pred = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri),
                    obj = QueryVariable("propVal0")
                ),
                StatementPattern(
                    subj = IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri),
                    pred = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri),
                    obj = IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri)
                ),
                StatementPattern(
                    subj = QueryVariable("propVal0"),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    obj = IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri)
                ),
                FilterPattern(RegexFunction(QueryVariable("propVal0"), "Zeit", "i"))
            ),
            positiveEntities = Set(

                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri),
                IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri),
                IriRef("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri),
                IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),

                QueryVariable("propVal0"),
                IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                QueryVariable("mainRes")
            )
        )
    )

    val QueryWithMatchFunction: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?resource a anything:Thing .
          |} WHERE {
          |    ?resource a anything:Thing .
          |    ?resource anything:hasText ?text .
          |    FILTER(knora-api:match(?text, "foo"))
          |}
        """.stripMargin

    val ParsedQueryWithMatchFunction: ConstructQuery = ConstructQuery(
        orderBy = Nil,
        whereClause = WhereClause(
            patterns = Vector(
                StatementPattern(
                    obj = IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    subj = QueryVariable(variableName = "resource")
                ),
                StatementPattern(
                    obj = QueryVariable(variableName = "text"),
                    pred = IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText".toSmartIri),
                    subj = QueryVariable(variableName = "resource")
                ),
                FilterPattern(expression = FunctionCallExpression(
                    functionIri = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#match".toSmartIri),
                    args = Seq(QueryVariable(variableName = "text"), XsdLiteral(value = "foo", datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri))
                ))
            ),
            positiveEntities = Set(
                QueryVariable("resource"),
                QueryVariable("text"),
                IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText".toSmartIri),
                IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri)
            )
        ),
        constructClause = ConstructClause(statements = Vector(StatementPattern(
            obj = IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri),
            pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
            subj = QueryVariable(variableName = "resource")
        )))
    )

    val QueryWithFilterContainingLang: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?resource a anything:Thing .
          |} WHERE {
          |    ?resource a anything:Thing .
          |    ?resource anything:hasText ?text .
          |    FILTER(lang(?text) = "en")
          |}
        """.stripMargin

    val ParsedQueryWithLangFunction = ConstructQuery(
        constructClause = ConstructClause(statements = Vector(StatementPattern(
            subj = QueryVariable(variableName = "resource"),
            pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
            obj = IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri)
        ))),
        whereClause = WhereClause(
            patterns = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "resource"),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    obj = IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri)
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "resource"),
                    pred = IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText".toSmartIri),
                    obj = QueryVariable(variableName = "text")
                ),
                FilterPattern(expression = CompareExpression(
                    leftArg = LangFunction(textValueVar = QueryVariable(variableName = "text")),
                    operator = CompareExpressionOperator.EQUALS,
                    rightArg = XsdLiteral(
                        value = "en",
                        datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
                    )
                ))
            ),
            positiveEntities = Set(
                IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText".toSmartIri),
                IriRef("http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri),
                IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                QueryVariable(variableName = "text"),
                QueryVariable(variableName = "resource")
            )
        )
    )

    val QueryWithFilterInOptional: String =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |    CONSTRUCT {
          |        ?letter knora-api:isMainResource true .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1  ?person1 .
          |
          |        ?person1 beol:hasFamilyName ?name .
          |
          |    } WHERE {
          |        ?letter a knora-api:Resource .
          |        ?letter a beol:letter .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        beol:creationDate knora-api:objectType knora-api:Date .
          |        ?date a knora-api:Date .
          |
          |        ?letter ?linkingProp1 ?person1 .
          |
          |        ?person1 a knora-api:Resource .
          |
          |        ?linkingProp1 knora-api:objectType knora-api:Resource .
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient )
          |
          |      	OPTIONAL {
          |        ?person1 beol:hasFamilyName ?name .
          |
          |        beol:hasFamilyName knora-api:objectType xsd:string .
          |        ?name a xsd:string .
          |
          |        FILTER(?name = "Meier")
          |    }
          |
          |    } ORDER BY ?date
        """.stripMargin

    val ParsedQueryWithFilterInOptional = ConstructQuery(
        constructClause = ConstructClause(statements = Vector(
            StatementPattern(
                subj = QueryVariable(variableName = "letter"),
                pred = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri),
                obj = XsdLiteral(
                    value = "true",
                    datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                )
            ),
            StatementPattern(
                subj = QueryVariable(variableName = "letter"),
                pred = IriRef("http://0.0.0.0:3333/ontology/0801/beol/simple/v2#creationDate".toSmartIri),
                obj = QueryVariable(variableName = "date")
            ),
            StatementPattern(
                subj = QueryVariable(variableName = "letter"),
                pred = QueryVariable(variableName = "linkingProp1"),
                obj = QueryVariable(variableName = "person1")
            ),
            StatementPattern(
                subj = QueryVariable(variableName = "person1"),
                pred = IriRef("http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasFamilyName".toSmartIri),
                obj = QueryVariable(variableName = "name")
            )
        )),
        whereClause = WhereClause(
            patterns = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "letter"),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    obj = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri)
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "letter"),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    obj = IriRef("http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri)
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "letter"),
                    pred = IriRef("http://0.0.0.0:3333/ontology/0801/beol/simple/v2#creationDate".toSmartIri),
                    obj = QueryVariable(variableName = "date")
                ),
                StatementPattern(
                    subj = IriRef("http://0.0.0.0:3333/ontology/0801/beol/simple/v2#creationDate".toSmartIri),
                    pred = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri),
                    obj = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri)
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "date"),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    obj = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri)
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "letter"),
                    pred = QueryVariable(variableName = "linkingProp1"),
                    obj = QueryVariable(variableName = "person1")
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "person1"),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    obj = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri)
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "linkingProp1"),
                    pred = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri),
                    obj = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri)
                ),
                OptionalPattern(patterns = Vector(
                    StatementPattern(
                        subj = QueryVariable(variableName = "person1"),
                        pred = IriRef("http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasFamilyName".toSmartIri),
                        obj = QueryVariable(variableName = "name")
                    ),
                    StatementPattern(
                        subj = IriRef("http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasFamilyName".toSmartIri),
                        pred = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri),
                        obj = IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri)
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "name"),
                        pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                        obj = IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri)
                    ),
                    FilterPattern(expression = CompareExpression(
                        leftArg = QueryVariable(variableName = "name"),
                        operator = CompareExpressionOperator.EQUALS,
                        rightArg = XsdLiteral(
                            value = "Meier",
                            datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
                        )
                    ))
                )),
                FilterPattern(expression = OrExpression(
                    leftArg = CompareExpression(
                        leftArg = QueryVariable(variableName = "linkingProp1"),
                        operator = CompareExpressionOperator.EQUALS,
                        rightArg = IriRef("http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri)
                    ),
                    rightArg = CompareExpression(
                        leftArg = QueryVariable(variableName = "linkingProp1"),
                        operator = CompareExpressionOperator.EQUALS,
                        rightArg = IriRef("http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri)
                    )
                ))
            ),
            positiveEntities = Set(
                QueryVariable(variableName = "name"),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
                IriRef("http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasFamilyName".toSmartIri),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri),
                IriRef("http://0.0.0.0:3333/ontology/0801/beol/simple/v2#creationDate".toSmartIri),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri),
                IriRef("http://www.w3.org/2001/XMLSchema#string".toSmartIri),
                QueryVariable(variableName = "person1"),
                QueryVariable(variableName = "date"),
                QueryVariable(variableName = "letter"),
                IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri),
                QueryVariable(variableName = "linkingProp1"),
                IriRef("http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri)
            )
        ),
        orderBy = Vector(OrderCriterion(
            queryVariable = QueryVariable(variableName = "date"),
            isAscending = true
        ))
    )
}
