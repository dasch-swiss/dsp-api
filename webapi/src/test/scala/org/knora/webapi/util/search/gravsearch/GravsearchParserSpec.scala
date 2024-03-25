/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.util.search.gravsearch

import zio.ZIO
import zio.test.Assertion.anything
import zio.test.Assertion.fails
import zio.test.Assertion.isSubtype
import zio.test.Spec
import zio.test.ZIOSpecDefault
import zio.test.assert
import zio.test.assertTrue

import dsp.errors.GravsearchException
import org.knora.webapi.ApiV2Complex
import org.knora.webapi.ApiV2Simple
import org.knora.webapi.messages.IriConversions._
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.search._
import org.knora.webapi.messages.util.search.gravsearch.GravsearchParser

object GravsearchParserSpec extends ZIOSpecDefault {

  private implicit val sf: StringFormatter = StringFormatter.getInitializedTestInstance

  private val Query: String =
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

  private val QueryWithBind: String =
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

  private val QueryWithFilterNotExists: String =
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

  private val QueryWithMinus: String =
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

  private val QueryWithOffset: String =
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

  private val QueryStrWithNestedOptional: String =
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

  private val QueryWithWrongFilter: String =
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

  private val QueryWithInternalEntityIri: String =
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

  private val SparqlSelect: String =
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

  private val SparqlDescribe: String =
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

  private val SparqlInsert: String =
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

  private val SparqlDelete: String =
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

  private val QueryWithLeftNestedUnion: String =
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

  private val QueryStrWithRightNestedUnion: String =
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

  private val QueryStrWithUnionInOptional: String =
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

  private val ParsedQueryWithUnionInOptional = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "Project"),
          pred = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri),
          obj = XsdLiteral(
            value = "true",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "isInProject"),
          pred = IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#isInProject".toSmartIri),
          obj = QueryVariable(variableName = "Project"),
        ),
      ),
      querySchema = Some(ApiV2Simple),
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "Project"),
          pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
          obj = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "Project"),
          pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
          obj = IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#Project".toSmartIri),
        ),
        OptionalPattern(
          patterns = Vector(
            StatementPattern(
              subj = QueryVariable(variableName = "isInProject"),
              pred = IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#isInProject".toSmartIri),
              obj = QueryVariable(variableName = "Project"),
            ),
            StatementPattern(
              subj = IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#isInProject".toSmartIri),
              pred = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri),
              obj = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
            ),
            StatementPattern(
              subj = QueryVariable(variableName = "isInProject"),
              pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
              obj = IriRef("http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri),
            ),
            UnionPattern(blocks =
              Vector(
                Vector(
                  StatementPattern(
                    subj = QueryVariable(variableName = "isInProject"),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    obj = IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#BibliographicNotice".toSmartIri),
                  ),
                ),
                Vector(
                  StatementPattern(
                    subj = QueryVariable(variableName = "isInProject"),
                    pred = IriRef("http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri),
                    obj = IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#Person".toSmartIri),
                  ),
                ),
              ),
            ),
          ),
        ),
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
        IriRef("http://0.0.0.0:3333/ontology/0666/test/simple/v2#Person".toSmartIri),
      ),
      querySchema = Some(ApiV2Simple),
    ),
    orderBy = Nil,
    querySchema = Some(ApiV2Simple),
  )

  private val QueryForAThingRelatingToAnotherThing: String =
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

  private val queryWithFilterContainingRegex: String =
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

  private val QueryWithMatchFunction: String =
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

  private val QueryWithMatchTextFunction: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
      |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
      |
      |CONSTRUCT {
      |    ?thing knora-api:isMainResource true .
      |} WHERE {
      |    ?thing a anything:Thing .
      |    ?thing anything:hasText ?text .
      |    FILTER(knora-api:matchText(?text, "foo"))
      |}
        """.stripMargin

  private val QueryWithFilterContainingLang: String =
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

  private val QueryWithFilterInOptional: String =
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
      |
      |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
      |
      |        beol:hasAuthor knora-api:objectType knora-api:Resource .
      |        beol:hasRecipient knora-api:objectType knora-api:Resource .
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

  private val ParsedQueryWithFilterInOptional = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "true",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#creationDate".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "date"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = QueryVariable(variableName = "linkingProp1"),
          obj = QueryVariable(variableName = "person1"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "person1"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasFamilyName".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "name"),
        ),
      ),
      querySchema = Some(ApiV2Simple),
    ),
    querySchema = Some(ApiV2Simple),
    offset = 0,
    orderBy = Vector(
      OrderCriterion(
        queryVariable = QueryVariable(variableName = "date"),
        isAscending = true,
      ),
    ),
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#creationDate".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "date"),
        ),
        StatementPattern(
          subj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#creationDate".toSmartIri,
            propertyPathOperator = None,
          ),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "date"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = QueryVariable(variableName = "linkingProp1"),
          obj = QueryVariable(variableName = "person1"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "person1"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "linkingProp1"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri,
            propertyPathOperator = None,
          ),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri,
            propertyPathOperator = None,
          ),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        OptionalPattern(
          patterns = Vector(
            StatementPattern(
              subj = QueryVariable(variableName = "person1"),
              pred = IriRef(
                iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasFamilyName".toSmartIri,
                propertyPathOperator = None,
              ),
              obj = QueryVariable(variableName = "name"),
            ),
            StatementPattern(
              subj = IriRef(
                iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasFamilyName".toSmartIri,
                propertyPathOperator = None,
              ),
              pred = IriRef(
                iri = "http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri,
                propertyPathOperator = None,
              ),
              obj = IriRef(
                iri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
                propertyPathOperator = None,
              ),
            ),
            StatementPattern(
              subj = QueryVariable(variableName = "name"),
              pred = IriRef(
                iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
                propertyPathOperator = None,
              ),
              obj = IriRef(
                iri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
                propertyPathOperator = None,
              ),
            ),
            FilterPattern(expression =
              CompareExpression(
                leftArg = QueryVariable(variableName = "name"),
                operator = CompareExpressionOperator.EQUALS,
                rightArg = XsdLiteral(
                  value = "Meier",
                  datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
                ),
              ),
            ),
          ),
        ),
        FilterPattern(
          expression = OrExpression(
            leftArg = CompareExpression(
              leftArg = QueryVariable(variableName = "linkingProp1"),
              operator = CompareExpressionOperator.EQUALS,
              rightArg = IriRef(
                iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri,
                propertyPathOperator = None,
              ),
            ),
            rightArg = CompareExpression(
              leftArg = QueryVariable(variableName = "linkingProp1"),
              operator = CompareExpressionOperator.EQUALS,
              rightArg = IriRef(
                iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri,
                propertyPathOperator = None,
              ),
            ),
          ),
        ),
      ),
      positiveEntities = Set(
        QueryVariable(variableName = "name"),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasFamilyName".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#creationDate".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasRecipient".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "person1"),
        QueryVariable(variableName = "date"),
        QueryVariable(variableName = "letter"),
        IriRef(
          iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#Date".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "linkingProp1"),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#letter".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0801/beol/simple/v2#hasAuthor".toSmartIri,
          propertyPathOperator = None,
        ),
      ),
      querySchema = Some(ApiV2Simple),
    ),
  )

  private val ParsedQuery = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "true",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "bookPublisher"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publoc".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "bookPubLoc"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "page"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#isPartOf".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "book"),
        ),
      ),
      querySchema = Some(ApiV2Simple),
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
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        OptionalPattern(
          patterns = Vector(
            StatementPattern(
              subj = QueryVariable(variableName = "book"),
              pred = IriRef(
                iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher".toSmartIri,
                propertyPathOperator = None,
              ),
              obj = QueryVariable(variableName = "bookPublisher"),
            ),
            StatementPattern(
              subj = QueryVariable(variableName = "book"),
              pred = IriRef(
                iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publoc".toSmartIri,
                propertyPathOperator = None,
              ),
              obj = QueryVariable(variableName = "bookPubLoc"),
            ),
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "pubdate"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "page"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "page"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#isPartOf".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "book"),
        ),
        UnionPattern(
          blocks = Vector(
            Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "page"),
                pred = IriRef(
                  iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pagenum".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = XsdLiteral(
                  value = "a7r",
                  datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
                ),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "page"),
                pred = IriRef(
                  iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#seqnum".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = XsdLiteral(
                  value = "14",
                  datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri,
                ),
              ),
            ),
            Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "page"),
                pred = IriRef(
                  iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pagenum".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = XsdLiteral(
                  value = "a8r",
                  datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
                ),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "page"),
                pred = IriRef(
                  iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#seqnum".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = XsdLiteral(
                  value = "16",
                  datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri,
                ),
              ),
            ),
            Vector(
              StatementPattern(
                subj = QueryVariable(variableName = "page"),
                pred = IriRef(
                  iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pagenum".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = XsdLiteral(
                  value = "a9r",
                  datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
                ),
              ),
              StatementPattern(
                subj = QueryVariable(variableName = "page"),
                pred = IriRef(
                  iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#seqnum".toSmartIri,
                  propertyPathOperator = None,
                ),
                obj = QueryVariable(variableName = "seqnum"),
              ),
              FilterPattern(expression =
                CompareExpression(
                  leftArg = QueryVariable(variableName = "seqnum"),
                  operator = CompareExpressionOperator.GREATER_THAN,
                  rightArg = XsdLiteral(
                    value = "17",
                    datatype = "http://www.w3.org/2001/XMLSchema#integer".toSmartIri,
                  ),
                ),
              ),
            ),
          ),
        ),
        FilterPattern(
          expression = CompareExpression(
            leftArg = QueryVariable(variableName = "pubdate"),
            operator = CompareExpressionOperator.LESS_THAN,
            rightArg = XsdLiteral(
              value = "GREGORIAN:1500",
              datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
            ),
          ),
        ),
      ),
      positiveEntities = Set(
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pagenum".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "page"),
        QueryVariable(variableName = "bookPublisher"),
        QueryVariable(variableName = "bookPubLoc"),
        QueryVariable(variableName = "book"),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publoc".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#page".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#isPartOf".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#seqnum".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "pubdate"),
        QueryVariable(variableName = "seqnum"),
      ),
      querySchema = Some(ApiV2Simple),
    ),
  )

  private val ParsedQueryWithBind = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "true",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
          ),
        ),
      ),
      querySchema = Some(ApiV2Simple),
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
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
      ),
      positiveEntities = Set(
        QueryVariable(variableName = "thing"),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://rdfh.ch/a-thing".toSmartIri,
          propertyPathOperator = None,
        ),
      ),
      querySchema = Some(ApiV2Simple),
    ),
  )

  private val ParsedQueryForAThingRelatingToAnotherThing = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "true",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#hasLinkTo".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://rdfh.ch/a-thing".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
      ),
      querySchema = Some(ApiV2Simple),
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
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = QueryVariable(variableName = "linkingProp"),
          obj = IriRef(
            iri = "http://rdfh.ch/a-thing".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        FilterPattern(
          expression = OrExpression(
            leftArg = CompareExpression(
              leftArg = QueryVariable(variableName = "linkingProp"),
              operator = CompareExpressionOperator.EQUALS,
              rightArg = IriRef(
                iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#isPartOfOtherThing".toSmartIri,
                propertyPathOperator = None,
              ),
            ),
            rightArg = CompareExpression(
              leftArg = QueryVariable(variableName = "linkingProp"),
              operator = CompareExpressionOperator.EQUALS,
              rightArg = IriRef(
                iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasOtherThing".toSmartIri,
                propertyPathOperator = None,
              ),
            ),
          ),
        ),
      ),
      positiveEntities = Set(
        QueryVariable(variableName = "thing"),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#hasLinkTo".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "linkingProp"),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasOtherThing".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#isPartOfOtherThing".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://rdfh.ch/a-thing".toSmartIri,
          propertyPathOperator = None,
        ),
      ),
      querySchema = Some(ApiV2Simple),
    ),
  )

  private val ParsedQueryWithFilterNotExists = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "true",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
          ),
        ),
      ),
      querySchema = Some(ApiV2Simple),
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
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        FilterNotExistsPattern(
          patterns = Vector(
            StatementPattern(
              subj = QueryVariable(variableName = "thing"),
              pred = IriRef(
                iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasOtherThing".toSmartIri,
                propertyPathOperator = None,
              ),
              obj = QueryVariable(variableName = "aThing"),
            ),
          ),
        ),
      ),
      positiveEntities = Set(
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "thing"),
      ),
      querySchema = Some(ApiV2Simple),
    ),
  )

  private val ParsedQueryWithMinus = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "true",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
          ),
        ),
      ),
      querySchema = Some(ApiV2Simple),
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
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        MinusPattern(
          patterns = Vector(
            StatementPattern(
              subj = QueryVariable(variableName = "thing"),
              pred = IriRef(
                iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasOtherThing".toSmartIri,
                propertyPathOperator = None,
              ),
              obj = QueryVariable(variableName = "aThing"),
            ),
          ),
        ),
      ),
      positiveEntities = Set(
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "thing"),
      ),
      querySchema = Some(ApiV2Simple),
    ),
  )

  private val ParsedQueryWithOffset = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "true",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
          ),
        ),
      ),
      querySchema = Some(ApiV2Simple),
    ),
    querySchema = Some(ApiV2Simple),
    offset = 10,
    orderBy = Nil,
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
      ),
      positiveEntities = Set(
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "thing"),
      ),
      querySchema = Some(ApiV2Simple),
    ),
  )

  private val ParsedQueryWithFilterContainingRegex = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "mainRes"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "true",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "mainRes"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "propVal0"),
        ),
      ),
      querySchema = Some(ApiV2Simple),
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
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "mainRes"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "mainRes"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "propVal0"),
        ),
        StatementPattern(
          subj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri,
            propertyPathOperator = None,
          ),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "propVal0"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        FilterPattern(
          expression = RegexFunction(
            textExpr = QueryVariable(variableName = "propVal0"),
            pattern = "Zeit",
            modifier = Some("i"),
          ),
        ),
      ),
      positiveEntities = Set(
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#Resource".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#objectType".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "propVal0"),
        QueryVariable(variableName = "mainRes"),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
          propertyPathOperator = None,
        ),
      ),
      querySchema = Some(ApiV2Simple),
    ),
  )

  private val ParsedQueryWithMatchFunction = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "true",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
          ),
        ),
      ),
      querySchema = Some(ApiV2Simple),
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
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "text"),
        ),
        FilterPattern(
          expression = FunctionCallExpression(
            functionIri = IriRef(
              iri = "http://api.knora.org/ontology/knora-api/simple/v2#match".toSmartIri,
              propertyPathOperator = None,
            ),
            args = Vector(
              QueryVariable(variableName = "text"),
              XsdLiteral(
                value = "foo",
                datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
              ),
            ),
          ),
        ),
      ),
      positiveEntities = Set(
        QueryVariable(variableName = "thing"),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "text"),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
          propertyPathOperator = None,
        ),
      ),
      querySchema = Some(ApiV2Simple),
    ),
  )

  private val ParsedQueryWithMatchTextFunction = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "true",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
          ),
        ),
      ),
      querySchema = Some(ApiV2Simple),
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
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "text"),
        ),
        FilterPattern(
          expression = FunctionCallExpression(
            functionIri = IriRef(
              iri = "http://api.knora.org/ontology/knora-api/simple/v2#matchText".toSmartIri,
              propertyPathOperator = None,
            ),
            args = Vector(
              QueryVariable(variableName = "text"),
              XsdLiteral(
                value = "foo",
                datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
              ),
            ),
          ),
        ),
      ),
      positiveEntities = Set(
        QueryVariable(variableName = "thing"),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "text"),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
          propertyPathOperator = None,
        ),
      ),
      querySchema = Some(ApiV2Simple),
    ),
  )

  private val ParsedQueryWithLangFunction = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "true",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
          ),
        ),
      ),
      querySchema = Some(ApiV2Simple),
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
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "thing"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "text"),
        ),
        FilterPattern(
          expression = CompareExpression(
            leftArg = LangFunction(textValueVar = QueryVariable(variableName = "text")),
            operator = CompareExpressionOperator.EQUALS,
            rightArg = XsdLiteral(
              value = "en",
              datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
            ),
          ),
        ),
      ),
      positiveEntities = Set(
        QueryVariable(variableName = "thing"),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#hasText".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "text"),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0001/anything/simple/v2#Thing".toSmartIri,
          propertyPathOperator = None,
        ),
      ),
      querySchema = Some(ApiV2Simple),
    ),
  )

  private val ParsedQueryWithNestedOptional = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "true",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "bookTitle"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "page"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#isPartOf".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "book"),
        ),
      ),
      querySchema = Some(ApiV2Simple),
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
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        OptionalPattern(
          patterns = Vector(
            StatementPattern(
              subj = QueryVariable(variableName = "book"),
              pred = IriRef(
                iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher".toSmartIri,
                propertyPathOperator = None,
              ),
              obj = QueryVariable(variableName = "publisher"),
            ),
            OptionalPattern(patterns =
              Vector(
                StatementPattern(
                  subj = QueryVariable(variableName = "book"),
                  pred = IriRef(
                    iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri,
                    propertyPathOperator = None,
                  ),
                  obj = QueryVariable(variableName = "bookTitle"),
                ),
              ),
            ),
            FilterPattern(expression =
              CompareExpression(
                leftArg = QueryVariable(variableName = "publisher"),
                operator = CompareExpressionOperator.EQUALS,
                rightArg = XsdLiteral(
                  value = "Lienhart Ysenhut",
                  datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
                ),
              ),
            ),
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "book"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "pubdate"),
        ),
        FilterPattern(
          expression = CompareExpression(
            leftArg = QueryVariable(variableName = "pubdate"),
            operator = CompareExpressionOperator.LESS_THAN,
            rightArg = XsdLiteral(
              value = "GREGORIAN:1500",
              datatype = "http://www.w3.org/2001/XMLSchema#string".toSmartIri,
            ),
          ),
        ),
      ),
      positiveEntities = Set(
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "page"),
        QueryVariable(variableName = "book"),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#publisher".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "bookTitle"),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/simple/v2#isMainResource".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "publisher"),
        IriRef(
          iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#pubdate".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#isPartOf".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "pubdate"),
      ),
      querySchema = Some(ApiV2Simple),
    ),
  )

  private val QueryWithIriArgInFunction: String =
    """
      |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
      |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
      |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
      |
      |CONSTRUCT {
      |    ?letter knora-api:isMainResource true .
      |    ?letter beol:hasText ?text .
      |} WHERE {
      |    ?letter a beol:letter .
      |    ?letter beol:hasText ?text .
      |    ?text knora-api:textValueHasStandoff ?standoffLinkTag .
      |    ?standoffLinkTag a knora-api:StandoffLinkTag .
      |
      |    FILTER knora-api:standoffLink(?letter, ?standoffLinkTag, <http://rdfh.ch/biblio/up0Q0ZzPSLaULC2tlTs1sA>)
      |}
        """.stripMargin

  private val ParsedQueryWithIriArgInFunction = ConstructQuery(
    constructClause = ConstructClause(
      statements = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/v2#isMainResource".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = XsdLiteral(
            value = "true",
            datatype = "http://www.w3.org/2001/XMLSchema#boolean".toSmartIri,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0801/beol/v2#hasText".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "text"),
        ),
      ),
      querySchema = Some(ApiV2Complex),
    ),
    querySchema = Some(ApiV2Complex),
    offset = 0,
    orderBy = Nil,
    whereClause = WhereClause(
      patterns = Vector(
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0801/beol/v2#letter".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "letter"),
          pred = IriRef(
            iri = "http://0.0.0.0:3333/ontology/0801/beol/v2#hasText".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "text"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "text"),
          pred = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/v2#textValueHasStandoff".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = QueryVariable(variableName = "standoffLinkTag"),
        ),
        StatementPattern(
          subj = QueryVariable(variableName = "standoffLinkTag"),
          pred = IriRef(
            iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
            propertyPathOperator = None,
          ),
          obj = IriRef(
            iri = "http://api.knora.org/ontology/knora-api/v2#StandoffLinkTag".toSmartIri,
            propertyPathOperator = None,
          ),
        ),
        FilterPattern(
          expression = FunctionCallExpression(
            functionIri = IriRef(
              iri = "http://api.knora.org/ontology/knora-api/v2#standoffLink".toSmartIri,
              propertyPathOperator = None,
            ),
            args = Vector(
              QueryVariable(variableName = "letter"),
              QueryVariable(variableName = "standoffLinkTag"),
              IriRef(
                iri = "http://rdfh.ch/biblio/up0Q0ZzPSLaULC2tlTs1sA".toSmartIri,
                propertyPathOperator = None,
              ),
            ),
          ),
        ),
      ),
      positiveEntities = Set(
        QueryVariable(variableName = "standoffLinkTag"),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0801/beol/v2#letter".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/v2#isMainResource".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "letter"),
        IriRef(
          iri = "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://0.0.0.0:3333/ontology/0801/beol/v2#hasText".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://rdfh.ch/biblio/up0Q0ZzPSLaULC2tlTs1sA".toSmartIri,
          propertyPathOperator = None,
        ),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/v2#StandoffLinkTag".toSmartIri,
          propertyPathOperator = None,
        ),
        QueryVariable(variableName = "text"),
        IriRef(
          iri = "http://api.knora.org/ontology/knora-api/v2#textValueHasStandoff".toSmartIri,
          propertyPathOperator = None,
        ),
      ),
      querySchema = Some(ApiV2Complex),
    ),
  )

  val spec: Spec[Any, Nothing] = suite("The GravsearchParser object")(
    test("parse a Gravsearch query") {
      val parsed   = GravsearchParser.parseQuery(Query)
      val reparsed = GravsearchParser.parseQuery(parsed.toSparql)

      assertTrue(parsed == ParsedQuery, reparsed == parsed)
    },
    test("parse a Gravsearch query with a BIND") {
      val parsed   = GravsearchParser.parseQuery(QueryWithBind)
      val reparsed = GravsearchParser.parseQuery(parsed.toSparql)

      assertTrue(parsed == ParsedQueryWithBind, reparsed == parsed)
    },
    test("parse a Gravsearch query with a FILTER containing a Boolean operator") {
      val parsed   = GravsearchParser.parseQuery(QueryForAThingRelatingToAnotherThing)
      val reparsed = GravsearchParser.parseQuery(parsed.toSparql)

      assertTrue(parsed == ParsedQueryForAThingRelatingToAnotherThing, reparsed == parsed)
    },
    test("parse a Gravsearch query with FILTER NOT EXISTS") {
      val parsed   = GravsearchParser.parseQuery(QueryWithFilterNotExists)
      val reparsed = GravsearchParser.parseQuery(parsed.toSparql)

      assertTrue(parsed == ParsedQueryWithFilterNotExists, reparsed == parsed)
    },
    test("parse a Gravsearch query with MINUS") {
      val parsed   = GravsearchParser.parseQuery(QueryWithMinus)
      val reparsed = GravsearchParser.parseQuery(parsed.toSparql)

      assertTrue(parsed == ParsedQueryWithMinus, reparsed == parsed)
    },
    test("parse a Gravsearch query with OFFSET") {
      val parsed   = GravsearchParser.parseQuery(QueryWithOffset)
      val reparsed = GravsearchParser.parseQuery(parsed.toSparql)

      assertTrue(parsed == ParsedQueryWithOffset, reparsed == parsed)
    },
    test("parse a Gravsearch query with a FILTER containing a regex function") {
      val parsed   = GravsearchParser.parseQuery(queryWithFilterContainingRegex)
      val reparsed = GravsearchParser.parseQuery(parsed.toSparql)

      assertTrue(parsed == ParsedQueryWithFilterContainingRegex, reparsed == parsed)
    },
    test("accept a custom 'match' function in a FILTER") {
      val parsed   = GravsearchParser.parseQuery(QueryWithMatchFunction)
      val reparsed = GravsearchParser.parseQuery(parsed.toSparql)

      assertTrue(parsed == ParsedQueryWithMatchFunction, reparsed == parsed)
    },
    test("accept a custom 'matchText' function in a FILTER") {
      val parsed   = GravsearchParser.parseQuery(QueryWithMatchTextFunction)
      val reparsed = GravsearchParser.parseQuery(parsed.toSparql)

      assertTrue(parsed == ParsedQueryWithMatchTextFunction, reparsed == parsed)
    },
    test("parse a Gravsearch query with a FILTER containing a lang function") {
      val parsed   = GravsearchParser.parseQuery(QueryWithFilterContainingLang)
      val reparsed = GravsearchParser.parseQuery(parsed.toSparql)

      assertTrue(parsed == ParsedQueryWithLangFunction, reparsed == parsed)
    },
    test("parse a Gravsearch query containing a FILTER in an OPTIONAL") {
      val parsed   = GravsearchParser.parseQuery(QueryWithFilterInOptional)
      val reparsed = GravsearchParser.parseQuery(parsed.toSparql)

      assertTrue(parsed == ParsedQueryWithFilterInOptional, reparsed == parsed)
    },
    test("parse a Gravsearch query containing a nested OPTIONAL") {
      val parsed   = GravsearchParser.parseQuery(QueryStrWithNestedOptional)
      val reparsed = GravsearchParser.parseQuery(parsed.toSparql)

      assertTrue(parsed == ParsedQueryWithNestedOptional, reparsed == parsed)
    },
    test("parse a Gravsearch query containing a UNION in an OPTIONAL") {
      val parsed   = GravsearchParser.parseQuery(QueryStrWithUnionInOptional)
      val reparsed = GravsearchParser.parseQuery(parsed.toSparql)

      assertTrue(parsed == ParsedQueryWithUnionInOptional, reparsed == parsed)
    },
    test("parse a Gravsearch query containing an IRI as a function argument") {
      val parsed   = GravsearchParser.parseQuery(QueryWithIriArgInFunction)
      val reparsed = GravsearchParser.parseQuery(parsed.toSparql)

      assertTrue(parsed == ParsedQueryWithIriArgInFunction, reparsed == parsed)
    },
    test("reject a SELECT query") {
      for {
        actual <- ZIO.attempt(GravsearchParser.parseQuery(SparqlSelect)).exit
      } yield assert(actual)(fails(isSubtype[GravsearchException](anything)))
    },
    test("reject a DESCRIBE query") {
      for {
        actual <- ZIO.attempt(GravsearchParser.parseQuery(SparqlDescribe)).exit
      } yield assert(actual)(fails(isSubtype[GravsearchException](anything)))
    },
    test("reject an INSERT") {
      for {
        actual <- ZIO.attempt(GravsearchParser.parseQuery(SparqlInsert)).exit
      } yield assert(actual)(fails(isSubtype[GravsearchException](anything)))
    },
    test("reject a DELETE") {
      for {
        actual <- ZIO.attempt(GravsearchParser.parseQuery(SparqlDelete)).exit
      } yield assert(actual)(fails(isSubtype[GravsearchException](anything)))
    },
    test("reject an internal ontology IRI") {
      for {
        actual <- ZIO.attempt(GravsearchParser.parseQuery(QueryWithInternalEntityIri)).exit
      } yield assert(actual)(fails(isSubtype[GravsearchException](anything)))
    },
    test("reject left-nested UNIONs") {
      for {
        actual <- ZIO.attempt(GravsearchParser.parseQuery(QueryWithLeftNestedUnion)).exit
      } yield assert(actual)(fails(isSubtype[GravsearchException](anything)))
    },
    test("reject right-nested UNIONs") {
      for {
        actual <- ZIO.attempt(GravsearchParser.parseQuery(QueryStrWithRightNestedUnion)).exit
      } yield assert(actual)(fails(isSubtype[GravsearchException](anything)))
    },
    test("reject an unsupported FILTER") {
      for {
        actual <- ZIO.attempt(GravsearchParser.parseQuery(QueryWithWrongFilter)).exit
      } yield assert(actual)(fails(isSubtype[GravsearchException](anything)))
    },
  )
}
