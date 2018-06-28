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

package org.knora.webapi.util.search.gravsearch

import org.knora.webapi.util.IriConversions._
import org.knora.webapi.util.StringFormatter
import org.knora.webapi.util.search._
import org.knora.webapi.{ApiV2Simple, CoreSpec, GravsearchException}

/**
  * Tests [[GravsearchParser]].
  */
class GravsearchParserSpec extends CoreSpec() {
    private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance

    "The GravsearchParser object" should {
        "parse a Gravsearch query" in {
            val parsed: ConstructQuery = GravsearchParser.parseQuery(Query)
            parsed should ===(ParsedQuery)
            val reparsed = GravsearchParser.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query with a BIND" in {
            val parsed: ConstructQuery = GravsearchParser.parseQuery(QueryWithBind)
            parsed should ===(ParsedQueryWithBind)
            val reparsed = GravsearchParser.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query with a FILTER containing a Boolean operator" in {
            val parsed: ConstructQuery = GravsearchParser.parseQuery(QueryForAThingRelatingToAnotherThing)
            parsed should ===(ParsedQueryForAThingRelatingToAnotherThing)
            val reparsed = GravsearchParser.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query with FILTER NOT EXISTS" in {
            val parsed: ConstructQuery = GravsearchParser.parseQuery(QueryWithFilterNotExists)
            parsed should ===(ParsedQueryWithFilterNotExists)
            val reparsed = GravsearchParser.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query with MINUS" in {
            val parsed: ConstructQuery = GravsearchParser.parseQuery(QueryWithMinus)
            parsed should ===(ParsedQueryWithMinus)
            val reparsed = GravsearchParser.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query with OFFSET" in {
            val parsed: ConstructQuery = GravsearchParser.parseQuery(QueryWithOffset)
            parsed should ===(ParsedQueryWithOffset)
            val reparsed = GravsearchParser.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query with a FILTER containing a regex function" in {
            val parsed = GravsearchParser.parseQuery(queryWithFilterContainingRegex)
            parsed should ===(ParsedQueryWithFilterContainingRegex)
            val reparsed = GravsearchParser.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "accept a custom 'match' function in a FILTER" in {
            val parsed: ConstructQuery = GravsearchParser.parseQuery(QueryWithMatchFunction)
            parsed should ===(ParsedQueryWithMatchFunction)
            val reparsed = GravsearchParser.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query with a FILTER containing a lang function" in {
            val parsed = GravsearchParser.parseQuery(QueryWithFilterContainingLang)
            parsed should ===(ParsedQueryWithLangFunction)
            val reparsed = GravsearchParser.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query containing a FILTER in an OPTIONAL" in {
            val parsed = GravsearchParser.parseQuery(QueryWithFilterInOptional)
            parsed should ===(ParsedQueryWithFilterInOptional)
            val reparsed = GravsearchParser.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query containing a nested OPTIONAL" in {
            val parsed = GravsearchParser.parseQuery(QueryStrWithNestedOptional)
            parsed should ===(ParsedQueryWithNestedOptional)
            val reparsed = GravsearchParser.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "parse a Gravsearch query containing a UNION in an OPTIONAL" in {
            val parsed = GravsearchParser.parseQuery(QueryStrWithUnionInOptional)
            parsed should ===(ParsedQueryWithUnionInOptional)
            val reparsed = GravsearchParser.parseQuery(parsed.toSparql)
            reparsed should ===(parsed)
        }

        "reject a SELECT query" in {
            assertThrows[GravsearchException] {
                GravsearchParser.parseQuery(SparqlSelect)
            }
        }

        "reject a DESCRIBE query" in {
            assertThrows[GravsearchException] {
                GravsearchParser.parseQuery(SparqlDescribe)
            }
        }

        "reject an INSERT" in {
            assertThrows[GravsearchException] {
                GravsearchParser.parseQuery(SparqlInsert)
            }
        }

        "reject a DELETE" in {
            assertThrows[GravsearchException] {
                GravsearchParser.parseQuery(SparqlDelete)
            }
        }

        "reject an internal ontology IRI" in {
            assertThrows[GravsearchException] {
                GravsearchParser.parseQuery(QueryWithInternalEntityIri)
            }
        }

        "reject left-nested UNIONs" in {
            assertThrows[GravsearchException] {
                GravsearchParser.parseQuery(QueryWithLeftNestedUnion)
            }
        }

        "reject right-nested UNIONs" in {
            assertThrows[GravsearchException] {
                GravsearchParser.parseQuery(QueryStrWithRightNestedUnion)
            }
        }

        "reject an unsupported FILTER" in {
            assertThrows[GravsearchException] {
                GravsearchParser.parseQuery(QueryWithWrongFilter)
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
          |    ?book knora-api:isMainResource true .
          |    ?book incunabula:publisher ?bookPublisher .
          |    ?book incunabula:publoc ?bookPubLoc .
          |    ?page incunabula:isPartOf ?book .
          |} WHERE {
          |    ?book a incunabula:book .
          |
          |    OPTIONAL {
          |        ?book incunabula:publisher ?bookPublisher .
          |        ?book incunabula:publoc ?bookPubLoc .
          |    }
          |
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |    ?page a incunabula:page .
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

    // val ParsedQuery = ???

    val QueryWithBind: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |} WHERE {
          |    BIND(<http://rdfh.ch/a-thing> AS ?thing)
          |    ?thing a knora-api:Resource .
          |    ?thing a anything:Thing .
          |}
        """.stripMargin


    val QueryWithFilterNotExists: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |} WHERE {
          |    ?thing a anything:Thing .
          |
          |    FILTER NOT EXISTS {
          |        ?thing anything:hasOtherThing ?aThing .
          |    }
          |}
        """.stripMargin


    val QueryWithMinus: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |} WHERE {
          |    ?thing a anything:Thing .
          |
          |    MINUS {
          |        ?thing anything:hasOtherThing ?aThing .
          |    }
          |}
        """.stripMargin


    val QueryWithOffset: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true.
          |} WHERE {
          |    ?thing a anything:Thing .
          |} OFFSET 10
        """.stripMargin


    val QueryStrWithNestedOptional: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |    ?book incunabula:title ?bookTitle .
          |    ?page incunabula:isPartOf ?book .
          |} WHERE {
          |    ?book a incunabula:book .
          |
          |    OPTIONAL {
          |        ?book incunabula:publisher ?publisher .
          |
          |        FILTER(?publisher = "Lienhart Ysenhut"^^xsd:string)
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

    val QueryWithWrongFilter: String =
        """
          |PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
          |PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |} WHERE {
          |    ?book a incunabula:book .
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
          |    ?book knora-api:isMainResource true .
          |} WHERE {
          |    ?book a incunabula:book .
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
          |SELECT ?subject ?object
          |WHERE {
          |    ?subject a incunabula:book .
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
          |    ?book knora-api:isMainResource true .
          |    ?page incunabula:isPartOf ?book .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book incunabula:publisher "Lienhart Ysenhut"^^xsd:string .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |    ?page a incunabula:page .
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
          |    ?book knora-api:isMainResource true .
          |    ?page a ?pageType .
          |    ?page incunabula:isPartOf ?book .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book incunabula:publisher "Lienhart Ysenhut"^^xsd:string .
          |    ?book incunabula:pubdate ?pubdate .
          |    FILTER(?pubdate < "GREGORIAN:1500"^^xsd:string)
          |    ?page a incunabula:page .
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
        constructClause = ConstructClause(
            statements = Vector(
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
            ),
            querySchema = Some(ApiV2Simple)
        ),
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
            ),
            querySchema = Some(ApiV2Simple)
        ),
        orderBy = Nil,
        querySchema = Some(ApiV2Simple)
    )

    val QueryForAThingRelatingToAnotherThing: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing knora-api:hasLinkTo <http://rdfh.ch/a-thing> .
          |} WHERE {
          |    ?thing a anything:Thing .
          |
          |    ?thing ?linkingProp <http://rdfh.ch/a-thing> .
          |    FILTER(?linkingProp = anything:isPartOfOtherThing || ?linkingProp = anything:hasOtherThing)
          |
          |}
        """.stripMargin

    // val ParsedQueryForAThingRelatingToAnotherThing = ???

    val QueryWithExplicitTypeAnnotations: String =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |
          |    ?letter knora-api:hasLinkTo <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
          |    ?letter ?linkingProp1  <http://rdfh.ch/beol/oU8fMNDJQ9SGblfBl5JamA> .
          |
          |    ?letter knora-api:hasLinkTo <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
          |    ?letter ?linkingProp2  <http://rdfh.ch/beol/6edJwtTSR8yjAWnYmt6AtA> .
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

    val QueryWithMatchFunction: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasText ?text .
          |    FILTER(knora-api:match(?text, "foo"))
          |}
        """.stripMargin


    val QueryWithFilterContainingLang: String =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasText ?text .
          |    FILTER(lang(?text) = "en")
          |}
        """.stripMargin

    // val ParsedQueryWithLangFunction = ???

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
          |        ?letter ?linkingProp1 ?person1 .
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
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        OPTIONAL {
          |            ?person1 beol:hasFamilyName ?name .
          |
          |            beol:hasFamilyName knora-api:objectType xsd:string .
          |            ?name a xsd:string .
          |
          |            FILTER(?name = "Meier")
          |        }
          |
          |    } ORDER BY ?date
        """.stripMargin

    val ParsedQueryWithFilterInOptional = ConstructQuery(
        constructClause = ConstructClause(
            statements = Vector(
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
            ),
            querySchema = Some(ApiV2Simple)
        ),
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
            ),
            querySchema = Some(ApiV2Simple)
        ),
        orderBy = Vector(OrderCriterion(
            queryVariable = QueryVariable(variableName = "date"),
            isAscending = true
        )),
        querySchema = Some(ApiV2Simple)
    )

    val ParsedQuery = ConstructQuery(
        constructClause = ConstructClause(
            statements = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = XsdLiteral(
                        value = "true",
                        datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                    ),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "bookPublisher"),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publoc".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "bookPubLoc"),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "page"),
                    pred = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#isPartOf".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "book"),
                    namedGraph = None
                )
            ),
            querySchema = Some(ApiV2Simple)
        ),
        querySchema = Some(ApiV2Simple),
        offset = 0,
        orderBy = Nil,
        whereClause = WhereClause(
            patterns = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                OptionalPattern(patterns = Vector(
                    StatementPattern(
                        subj = QueryVariable(variableName = "book"),
                        pred = IriRef(
                            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = QueryVariable(variableName = "bookPublisher"),
                        namedGraph = None
                    ),
                    StatementPattern(
                        subj = QueryVariable(variableName = "book"),
                        pred = IriRef(
                            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publoc".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = QueryVariable(variableName = "bookPubLoc"),
                        namedGraph = None
                    )
                )),
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "pubdate"),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "page"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "page"),
                    pred = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#isPartOf".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "book"),
                    namedGraph = None
                ),
                UnionPattern(blocks = Vector(
                    Vector(
                        StatementPattern(
                            subj = QueryVariable(variableName = "page"),
                            pred = IriRef(
                                iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pagenum".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = XsdLiteral(
                                value = "a7r",
                                datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
                            ),
                            namedGraph = None
                        ),
                        StatementPattern(
                            subj = QueryVariable(variableName = "page"),
                            pred = IriRef(
                                iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#seqnum".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = XsdLiteral(
                                value = "14",
                                datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
                            ),
                            namedGraph = None
                        )
                    ),
                    Vector(
                        StatementPattern(
                            subj = QueryVariable(variableName = "page"),
                            pred = IriRef(
                                iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pagenum".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = XsdLiteral(
                                value = "a8r",
                                datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
                            ),
                            namedGraph = None
                        ),
                        StatementPattern(
                            subj = QueryVariable(variableName = "page"),
                            pred = IriRef(
                                iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#seqnum".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = XsdLiteral(
                                value = "16",
                                datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
                            ),
                            namedGraph = None
                        )
                    ),
                    Vector(
                        StatementPattern(
                            subj = QueryVariable(variableName = "page"),
                            pred = IriRef(
                                iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pagenum".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = XsdLiteral(
                                value = "a9r",
                                datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
                            ),
                            namedGraph = None
                        ),
                        StatementPattern(
                            subj = QueryVariable(variableName = "page"),
                            pred = IriRef(
                                iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#seqnum".toSmartIri,
                                propertyPathOperator = None
                            ),
                            obj = QueryVariable(variableName = "seqnum"),
                            namedGraph = None
                        ),
                        FilterPattern(expression = CompareExpression(
                            leftArg = QueryVariable(variableName = "seqnum"),
                            operator = CompareExpressionOperator.GREATER_THAN,
                            rightArg = XsdLiteral(
                                value = "17",
                                datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri
                            )
                        ))
                    )
                )),
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
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pagenum".toSmartIri,
                    propertyPathOperator = None
                ),
                QueryVariable(variableName = "page"),
                QueryVariable(variableName = "bookPublisher"),
                QueryVariable(variableName = "bookPubLoc"),
                QueryVariable(variableName = "book"),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publoc".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#isPartOf".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#seqnum".toSmartIri,
                    propertyPathOperator = None
                ),
                QueryVariable(variableName = "pubdate"),
                QueryVariable(variableName = "seqnum")
            ),
            querySchema = Some(ApiV2Simple)
        )
    )

    val ParsedQueryWithBind = ConstructQuery(
        constructClause = ConstructClause(
            statements = Vector(StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                obj = XsdLiteral(
                    value = "true",
                    datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = None
            )),
            querySchema = Some(ApiV2Simple)
        ),
        querySchema = Some(ApiV2Simple),
        offset = 0,
        orderBy = Nil,
        whereClause = WhereClause(
            patterns = Vector(
                BindPattern(
                    variable = QueryVariable(variableName = "thing"),
                    expression = IriRef(
                        iri = "http://rdfh.ch/a-thing".toSmartIri,
                        propertyPathOperator = None
                    )
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                )
            ),
            positiveEntities = Set(
                QueryVariable(variableName = "thing"),
                IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                    propertyPathOperator = None
                )
            ),
            querySchema = Some(ApiV2Simple)
        )
    )

    val ParsedQueryForAThingRelatingToAnotherThing = ConstructQuery(
        constructClause = ConstructClause(
            statements = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = IriRef(
                        iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = XsdLiteral(
                        value = "true",
                        datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                    ),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = IriRef(
                        iri = "http://api.knora.org/ontology/knora-api/simple/v2#hasLinkTo".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://rdfh.ch/a-thing".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                )
            ),
            querySchema = Some(ApiV2Simple)
        ),
        querySchema = Some(ApiV2Simple),
        offset = 0,
        orderBy = Nil,
        whereClause = WhereClause(
            patterns = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = QueryVariable(variableName = "linkingProp"),
                    obj = IriRef(
                        iri = "http://rdfh.ch/a-thing".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                FilterPattern(expression = OrExpression(
                    leftArg = CompareExpression(
                        leftArg = QueryVariable(variableName = "linkingProp"),
                        operator = CompareExpressionOperator.EQUALS,
                        rightArg = IriRef(
                            iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#isPartOfOtherThing".toSmartIri,
                            propertyPathOperator = None
                        )
                    ),
                    rightArg = CompareExpression(
                        leftArg = QueryVariable(variableName = "linkingProp"),
                        operator = CompareExpressionOperator.EQUALS,
                        rightArg = IriRef(
                            iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasOtherThing".toSmartIri,
                            propertyPathOperator = None
                        )
                    )
                ))
            ),
            positiveEntities = Set(
                QueryVariable(variableName = "thing"),
                IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#hasLinkTo".toSmartIri,
                    propertyPathOperator = None
                ),
                QueryVariable(variableName = "linkingProp"),
                IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://rdfh.ch/a-thing".toSmartIri,
                    propertyPathOperator = None
                )
            ),
            querySchema = Some(ApiV2Simple)
        )
    )

    val ParsedQueryWithFilterNotExists = ConstructQuery(
        constructClause = ConstructClause(
            statements = Vector(StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                obj = XsdLiteral(
                    value = "true",
                    datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = None
            )),
            querySchema = Some(ApiV2Simple)
        ),
        querySchema = Some(ApiV2Simple),
        offset = 0,
        orderBy = Nil,
        whereClause = WhereClause(
            patterns = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                FilterNotExistsPattern(patterns = Vector(StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasOtherThing".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "aThing"),
                    namedGraph = None
                )))
            ),
            positiveEntities = Set(
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                QueryVariable(variableName = "thing")
            ),
            querySchema = Some(ApiV2Simple)
        )
    )

    val ParsedQueryWithMinus = ConstructQuery(
        constructClause = ConstructClause(
            statements = Vector(StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                obj = XsdLiteral(
                    value = "true",
                    datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = None
            )),
            querySchema = Some(ApiV2Simple)
        ),
        querySchema = Some(ApiV2Simple),
        offset = 0,
        orderBy = Nil,
        whereClause = WhereClause(
            patterns = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                MinusPattern(patterns = Vector(StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasOtherThing".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "aThing"),
                    namedGraph = None
                )))
            ),
            positiveEntities = Set(
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                QueryVariable(variableName = "thing")
            ),
            querySchema = Some(ApiV2Simple)
        )
    )

    val ParsedQueryWithOffset = ConstructQuery(
        constructClause = ConstructClause(
            statements = Vector(StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                obj = XsdLiteral(
                    value = "true",
                    datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = None
            )),
            querySchema = Some(ApiV2Simple)
        ),
        querySchema = Some(ApiV2Simple),
        offset = 10,
        orderBy = Nil,
        whereClause = WhereClause(
            patterns = Vector(StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    propertyPathOperator = None
                ),
                obj = IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                    propertyPathOperator = None
                ),
                namedGraph = None
            )),
            positiveEntities = Set(
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                QueryVariable(variableName = "thing")
            ),
            querySchema = Some(ApiV2Simple)
        )
    )

    val ParsedQueryWithFilterContainingRegex = ConstructQuery(
        constructClause = ConstructClause(
            statements = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "mainRes"),
                    pred = IriRef(
                        iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = XsdLiteral(
                        value = "true",
                        datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                    ),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "mainRes"),
                    pred = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "propVal0"),
                    namedGraph = None
                )
            ),
            querySchema = Some(ApiV2Simple)
        ),
        querySchema = Some(ApiV2Simple),
        offset = 0,
        orderBy = Nil,
        whereClause = WhereClause(
            patterns = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "mainRes"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "mainRes"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "mainRes"),
                    pred = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "propVal0"),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri,
                        propertyPathOperator = None
                    ),
                    pred = IriRef(
                        iri = "http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "propVal0"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                FilterPattern(expression = RegexFunction(
                    textVar = QueryVariable(variableName = "propVal0"),
                    pattern = "Zeit",
                    modifier = Some("i")
                ))
            ),
            positiveEntities = Set(
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri,
                    propertyPathOperator = None
                ),
                QueryVariable(variableName = "propVal0"),
                QueryVariable(variableName = "mainRes"),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    propertyPathOperator = None
                )
            ),
            querySchema = Some(ApiV2Simple)
        )
    )

    val ParsedQueryWithMatchFunction = ConstructQuery(
        constructClause = ConstructClause(
            statements = Vector(StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                obj = XsdLiteral(
                    value = "true",
                    datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = None
            )),
            querySchema = Some(ApiV2Simple)
        ),
        querySchema = Some(ApiV2Simple),
        offset = 0,
        orderBy = Nil,
        whereClause = WhereClause(
            patterns = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "text"),
                    namedGraph = None
                ),
                FilterPattern(expression = FunctionCallExpression(
                    functionIri = IriRef(
                        iri = "http://api.knora.org/ontology/knora-api/simple/v2#match".toSmartIri,
                        propertyPathOperator = None
                    ),
                    args = Vector(
                        QueryVariable(variableName = "text"),
                        XsdLiteral(
                            value = "foo",
                            datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
                        )
                    )
                ))
            ),
            positiveEntities = Set(
                QueryVariable(variableName = "thing"),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    propertyPathOperator = None
                ),
                QueryVariable(variableName = "text"),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                    propertyPathOperator = None
                )
            ),
            querySchema = Some(ApiV2Simple)
        )
    )

    val ParsedQueryWithLangFunction = ConstructQuery(
        constructClause = ConstructClause(
            statements = Vector(StatementPattern(
                subj = QueryVariable(variableName = "thing"),
                pred = IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                obj = XsdLiteral(
                    value = "true",
                    datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                ),
                namedGraph = None
            )),
            querySchema = Some(ApiV2Simple)
        ),
        querySchema = Some(ApiV2Simple),
        offset = 0,
        orderBy = Nil,
        whereClause = WhereClause(
            patterns = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "thing"),
                    pred = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "text"),
                    namedGraph = None
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
                QueryVariable(variableName = "thing"),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    propertyPathOperator = None
                ),
                QueryVariable(variableName = "text"),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
                    propertyPathOperator = None
                )
            ),
            querySchema = Some(ApiV2Simple)
        )
    )

    val ParsedQueryWithNestedOptional = ConstructQuery(
        constructClause = ConstructClause(
            statements = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = XsdLiteral(
                        value = "true",
                        datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri
                    ),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "bookTitle"),
                    namedGraph = None
                ),
                StatementPattern(
                    subj = QueryVariable(variableName = "page"),
                    pred = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#isPartOf".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "book"),
                    namedGraph = None
                )
            ),
            querySchema = Some(ApiV2Simple)
        ),
        querySchema = Some(ApiV2Simple),
        offset = 0,
        orderBy = Nil,
        whereClause = WhereClause(
            patterns = Vector(
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
                        propertyPathOperator = None
                    ),
                    namedGraph = None
                ),
                OptionalPattern(patterns = Vector(
                    StatementPattern(
                        subj = QueryVariable(variableName = "book"),
                        pred = IriRef(
                            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = QueryVariable(variableName = "publisher"),
                        namedGraph = None
                    ),
                    OptionalPattern(patterns = Vector(StatementPattern(
                        subj = QueryVariable(variableName = "book"),
                        pred = IriRef(
                            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri,
                            propertyPathOperator = None
                        ),
                        obj = QueryVariable(variableName = "bookTitle"),
                        namedGraph = None
                    ))),
                    FilterPattern(expression = CompareExpression(
                        leftArg = QueryVariable(variableName = "publisher"),
                        operator = CompareExpressionOperator.EQUALS,
                        rightArg = XsdLiteral(
                            value = "Lienhart Ysenhut",
                            datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri
                        )
                    ))
                )),
                StatementPattern(
                    subj = QueryVariable(variableName = "book"),
                    pred = IriRef(
                        iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate".toSmartIri,
                        propertyPathOperator = None
                    ),
                    obj = QueryVariable(variableName = "pubdate"),
                    namedGraph = None
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
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
                    propertyPathOperator = None
                ),
                QueryVariable(variableName = "page"),
                QueryVariable(variableName = "book"),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri,
                    propertyPathOperator = None
                ),
                QueryVariable(variableName = "bookTitle"),
                IriRef(
                    iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
                    propertyPathOperator = None
                ),
                QueryVariable(variableName = "publisher"),
                IriRef(
                    iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate".toSmartIri,
                    propertyPathOperator = None
                ),
                IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#isPartOf".toSmartIri,
                    propertyPathOperator = None
                ),
                QueryVariable(variableName = "pubdate")
            ),
            querySchema = Some(ApiV2Simple)
        )
    )
}
