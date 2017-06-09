/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and Sepideh Alassi.
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

import org.knora.webapi.SparqlSearchException
import org.scalatest.{Matchers, WordSpec}

/**
  * Tests [[SearchParserV2]].
  */
class SearchParserV2Spec extends WordSpec with Matchers {

    import SearchParserV2Spec._

    "The SearchParserV2 object" should {
        "parse a simple CONSTRUCT query for an extended search" in {
            SearchParserV2.parseSearchQuery(SimpleSparqlConstructQueryStr) should ===(SimpleParsedSparqlConstructQuery)
        }

        "reject a SELECT query" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SimpleSparqlSelectQueryStr)
            }
        }

        "reject a DESCRIBE query" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SimpleSparqlDescribeQueryStr)
            }
        }

        "reject an INSERT" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SimpleSparqlInsertStr)
            }
        }

        "reject a DELETE" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SimpleSparqlDeleteStr)
            }
        }

        "reject an internal ontology IRI" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SimpleSparqlConstructQueryStrWithInternalEntityIri)
            }
        }

        "reject left-nested UNIONs" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SimpleSparqlConstructQueryStrWithLeftNestedUnion)
            }
        }

        "reject right-nested UNIONs" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SimpleSparqlConstructQueryStrWithRightNestedUnion)
            }
        }

        "reject an OPTIONAL" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SimpleSparqlConstructQueryStrWithOptional)
            }
        }

        "reject a poorly formatted FILTER" in {
            assertThrows[SparqlSearchException] {
                SearchParserV2.parseSearchQuery(SimpleSparqlConstructQueryStrWithWrongFilter)
            }
        }
    }
}

object SearchParserV2Spec {
    val SimpleSparqlConstructQueryStr: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
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
          |        ?page incunabula:seqnum ?seqnum.
          |        FILTER(?seqnum > "17"^^xsd:integer)
          |    }
          |}
        """.stripMargin

    val SimpleParsedSparqlConstructQuery = SimpleConstructQuery(
        whereClause = SimpleWhereClause(statements = Vector(
            StatementPattern(
                obj = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#book"),
                pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                obj = QueryVariable(variableName = "bookLabel"),
                pred = IriRef(iri = "http://www.w3.org/2000/01/rdf-schema#label"),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                obj = XsdLiteral(
                    datatype = "http://www.w3.org/2001/XMLSchema#string",
                    value = "Lienhart Ysenhut"
                ),
                pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#publisher"),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                obj = QueryVariable(variableName = "pubdate"),
                pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#pubdate"),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                obj = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#page"),
                pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                subj = QueryVariable(variableName = "page")
            ),
            StatementPattern(
                obj = QueryVariable(variableName = "pageLabel"),
                pred = IriRef(iri = "http://www.w3.org/2000/01/rdf-schema#label"),
                subj = QueryVariable(variableName = "page")
            ),
            StatementPattern(
                obj = QueryVariable(variableName = "book"),
                pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#isPartOf"),
                subj = QueryVariable(variableName = "page")
            ),
            UnionPattern(blocks = Vector(
                Vector(
                    StatementPattern(
                        obj = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#string",
                            value = "a7r"
                        ),
                        pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#pagenum"),
                        subj = QueryVariable(variableName = "page")
                    ),
                    StatementPattern(
                        obj = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#integer",
                            value = "14"
                        ),
                        pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#seqnum"),
                        subj = QueryVariable(variableName = "page")
                    )
                ),
                Vector(
                    StatementPattern(
                        obj = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#string",
                            value = "a8r"
                        ),
                        pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#pagenum"),
                        subj = QueryVariable(variableName = "page")
                    ),
                    StatementPattern(
                        obj = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#integer",
                            value = "16"
                        ),
                        pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#seqnum"),
                        subj = QueryVariable(variableName = "page")
                    )
                ),
                Vector(
                    StatementPattern(
                        obj = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#string",
                            value = "a9r"
                        ),
                        pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#pagenum"),
                        subj = QueryVariable(variableName = "page")
                    ),
                    StatementPattern(
                        obj = QueryVariable(variableName = "seqnum"),
                        pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#seqnum"),
                        subj = QueryVariable(variableName = "page")
                    ),
                    FilterPattern(
                        rightArgLiteral = XsdLiteral(
                            datatype = "http://www.w3.org/2001/XMLSchema#integer",
                            value = "17"
                        ),
                        operator = ">",
                        leftArgVariableName = "seqnum"
                    )
                )
            )),
            FilterPattern(
                rightArgLiteral = XsdLiteral(
                    datatype = "http://www.w3.org/2001/XMLSchema#string",
                    value = "GREGORIAN:1500"
                ),
                operator = "<",
                leftArgVariableName = "pubdate"
            )
        )),
        constructClause = SimpleConstructClause(statements = Vector(
            StatementPattern(
                obj = QueryVariable(variableName = "bookType"),
                pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                obj = QueryVariable(variableName = "bookLabel"),
                pred = IriRef(iri = "http://www.w3.org/2000/01/rdf-schema#label"),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                obj = XsdLiteral(
                    datatype = "http://www.w3.org/2001/XMLSchema#boolean",
                    value = "true"
                ),
                pred = IriRef(iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource"),
                subj = QueryVariable(variableName = "book")
            ),
            StatementPattern(
                obj = QueryVariable(variableName = "pageType"),
                pred = IriRef(iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
                subj = QueryVariable(variableName = "page")
            ),
            StatementPattern(
                obj = QueryVariable(variableName = "pageLabel"),
                pred = IriRef(iri = "http://www.w3.org/2000/01/rdf-schema#label"),
                subj = QueryVariable(variableName = "page")
            ),
            StatementPattern(
                obj = QueryVariable(variableName = "book"),
                pred = IriRef(iri = "http://api.knora.org/ontology/incunabula/simple/v2#isPartOf"),
                subj = QueryVariable(variableName = "page")
            )
        ))
    )

    val SimpleSparqlConstructQueryStrWithWrongFilter: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book a ?bookType .
          |    ?book rdfs:label ?bookLabel .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER("GREGORIAN:1500"^^xsd:string > ?pubdate)
          |}
        """.stripMargin

    val SimpleSparqlConstructQueryStrWithInternalEntityIri: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://www.knora.org/ontology/incunabula#>
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

    val SimpleSparqlSelectQueryStr: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
          |
          |SELECT ?book ?bookLabel
          |WHERE {
          |    ?book a incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |}
        """.stripMargin

    val SimpleSparqlDescribeQueryStr: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
          |
          |DESCRIBE ?book
          |WHERE {
          |    ?book a incunabula:book .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |}
        """.stripMargin

    val SimpleSparqlInsertStr: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
          |
          |INSERT DATA {
          |    <http://example.org/12345> a incunabula:book .
          |}
        """.stripMargin

    val SimpleSparqlDeleteStr: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
          |
          |DELETE {
          |    <http://example.org/12345> a incunabula:book .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |}
        """.stripMargin

    val SimpleSparqlConstructQueryStrWithLeftNestedUnion: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
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

    val SimpleSparqlConstructQueryStrWithRightNestedUnion: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
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

    val SimpleSparqlConstructQueryStrWithOptional: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
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
          |
          |    OPTIONAL {
          |        ?book incunabula:publisher "Lienhart Ysenhut"^^xsd:string .
          |    }
          |
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |}
        """.stripMargin
}
