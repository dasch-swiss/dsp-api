/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import sttp.client4.*
import sttp.model.StatusCode
import zio.*
import zio.json.ast.Json
import zio.test.*

import java.nio.file.Paths

import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import org.xmlunit.diff.Diff

import org.knora.webapi.E2EZSpec
import org.knora.webapi.e2e.v2.ResponseCheckerV2.checkSearchResponseNumberOfResults
import org.knora.webapi.e2e.v2.SearchEndpointE2ESpecHelper.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.messages.util.rdf.JsonLDUtil
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.ResponseOps.assert400
import org.knora.webapi.testservices.ResponseOps.assert404
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.FileUtil

object SearchEndpointsPostGravsearchWithTypeInferenceComplexSchemaE2ESpec extends E2EZSpec {

  override val rdfDataObjects: List[RdfDataObject] = SearchEndpointE2ESpecHelper.rdfDataObjects

  override def e2eSpec = suite("SearchEndpoints POST /v2/searchextended (with type inference, complex schema)")(
    test(
      "perform a Gravsearch query for an anything:Thing with an optional date and sort by date (submitting the complex schema)",
    ) {
      val query =
        """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |  ?thing anything:hasDate ?date .
          |} WHERE {
          |
          |  ?thing a knora-api:Resource .
          |  ?thing a anything:Thing .
          |
          |  OPTIONAL {
          |    ?thing anything:hasDate ?date .
          |  }
          |
          |  MINUS {
          |    ?thing anything:hasInteger ?intVal .
          |    ?intVal knora-api:intValueAsInt 123454321 .
          |  }
          |
          |  MINUS {
          |    ?thing anything:hasInteger ?intVal .
          |    ?intVal knora-api:intValueAsInt 999999999 .
          |  }
          |}
          |ORDER BY DESC(?date)""".stripMargin
      verifyQueryResult(query, "thingWithOptionalDateSortedDesc.jsonld")
    },
    test(
      "perform a Gravsearch query for an anything:Thing that has an optional decimal value greater than 2 and sort by the decimal value (submitting the complex schema)",
    ) {
      val query =
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
          |        ?decimal knora-api:decimalValueAsDecimal ?decimalVal .
          |        FILTER(?decimalVal > "1"^^xsd:decimal)
          |     }
          |
          |     MINUS {
          |        ?thing anything:hasInteger ?intVal .
          |        ?intVal knora-api:intValueAsInt 123454321 .
          |     }
          |
          |     MINUS {
          |       ?thing anything:hasInteger ?intVal .
          |       ?intVal knora-api:intValueAsInt 999999999 .
          |     }
          |} ORDER BY DESC(?decimal)
          |""".stripMargin
      verifyQueryResult(query, "ThingsWithOptionalDecimalGreaterThan1.jsonld")
    },
    test(
      "do a Gravsearch query that finds all the books that have a page with seqnum 100 (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |
          |  ?book knora-api:isMainResource true .
          |
          |  ?page incunabula:partOf ?book ;
          |    incunabula:seqnum ?seqnum .
          |
          |} WHERE {
          |
          |  ?page incunabula:partOf ?book ;
          |    incunabula:seqnum ?seqnum .
          |
          |  ?seqnum knora-api:intValueAsInt 100 .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "booksWithPage100.jsonld")
    },
    test(
      "do a Gravsearch query that finds all the letters sent by someone called Meier, ordered by date (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |CONSTRUCT {
          |
          |  ?letter knora-api:isMainResource true ;
          |    beol:creationDate ?date ;
          |    beol:hasAuthor ?author .
          |
          |  ?author beol:hasFamilyName ?name .
          |
          |} WHERE {
          |
          |  ?letter beol:hasAuthor ?author ;
          |    beol:creationDate ?date .
          |
          |  ?author beol:hasFamilyName ?name .
          |
          |  ?name knora-api:valueAsString "Meier" .
          |
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "lettersByMeier.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |} WHERE {
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString "Zeitglöcklein des Lebens und Leidens Christi" .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' not returning the title in the answer (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString "Zeitglöcklein des Lebens und Leidens Christi" .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "ZeitgloeckleinExtendedSearchNoTitleInAnswer.jsonld")
    },
    test(
      "perform a Gravsearch query for books that do not have the title 'Zeitglöcklein des Lebens' (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString ?titleStr .
          |
          |    FILTER(?titleStr != "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "NotZeitgloeckleinExtendedSearch.jsonld")
    },
    test(
      "perform a Gravsearch query for the page of a book whose seqnum equals 10, returning the seqnum and the link value (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?page knora-api:isMainResource true .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |} WHERE {
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |
          |    ?seqnum knora-api:intValueAsInt 10 .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "PageWithSeqnum10WithSeqnumAndLinkValueInAnswer.jsonld")
    },
    test(
      "perform a Gravsearch count query for the page of a book whose seqnum equals 10, returning the seqnum and the link value (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?page knora-api:isMainResource true .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |} WHERE {
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |
          |    ?seqnum knora-api:intValueAsInt 10 .
          |}
          |""".stripMargin
      for {
        response <- postGravsearchQuery(query)
        jsonLd   <- response.assert200
        _        <- ZIO.attempt(checkSearchResponseNumberOfResults(jsonLd, 1))
      } yield assertCompletes
    },
    test(
      "perform a Gravsearch query for the page of a book whose seqnum equals 10, returning only the seqnum (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?page knora-api:isMainResource true .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |} WHERE {
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |
          |    ?seqnum knora-api:intValueAsInt 10 .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "PageWithSeqnum10OnlySeqnuminAnswer.jsonld")
    },
    test(
      "perform a Gravsearch query for the pages of a book whose seqnum is lower than or equals 10 (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?page knora-api:isMainResource true .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |} WHERE {
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |
          |    ?seqnum knora-api:intValueAsInt ?seqnumInt .
          |
          |    FILTER(?seqnumInt <= 10)
          |
          |} ORDER BY ?seqnum
          |""".stripMargin
      verifyQueryResult(query, "PagesOfLatinNarrenschiffWithSeqnumLowerEquals10.jsonld")
    },
    test(
      "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?page knora-api:isMainResource true .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |} WHERE {
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |
          |} ORDER BY ?seqnum
          |""".stripMargin
      verifyQueryResult(query, "PagesOfNarrenschiffOrderedBySeqnum.jsonld")
    },
    test(
      "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum and get the next OFFSET (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?page knora-api:isMainResource true .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |} WHERE {
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |
          |} ORDER BY ?seqnum
          |OFFSET 1
          |""".stripMargin
      verifyQueryResult(query, "PagesOfNarrenschiffOrderedBySeqnumNextOffset.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have been published on the first of March 1497 (Julian Calendar) (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |
          |    FILTER(knora-api:toSimpleDate(?pubdate) = "JULIAN:1497-03-01"^^knora-api-simple:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksPublishedOnDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have not been published on the first of March 1497 (Julian Calendar) (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |
          |    FILTER(knora-api:toSimpleDate(?pubdate) != "JULIAN:1497-03-01"^^knora-api-simple:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksNotPublishedOnDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have not been published on the first of March 1497 (Julian Calendar) 2 (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |
          |    FILTER(knora-api:toSimpleDate(?pubdate) < "JULIAN:1497-03-01"^^knora-api-simple:Date || knora-api:toSimpleDate(?pubdate) > "JULIAN:1497-03-01"^^knora-api-simple:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksNotPublishedOnDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have been published before 1497 (Julian Calendar) (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |
          |    FILTER(knora-api:toSimpleDate(?pubdate) < "JULIAN:1497"^^knora-api-simple:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksPublishedBeforeDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have been published 1497 or later (Julian Calendar) (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |
          |    FILTER(knora-api:toSimpleDate(?pubdate) >= "JULIAN:1497"^^knora-api-simple:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksPublishedAfterOrOnDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have been published after 1497 (Julian Calendar) (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |
          |    FILTER(knora-api:toSimpleDate(?pubdate) > "JULIAN:1497"^^knora-api-simple:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksPublishedAfterDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have been published 1497 or before (Julian Calendar) (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |
          |    FILTER(knora-api:toSimpleDate(?pubdate) <= "JULIAN:1497"^^knora-api-simple:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksPublishedBeforeOrOnDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have been published after 1486 and before 1491 (Julian Calendar) (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |
          |    FILTER(knora-api:toSimpleDate(?pubdate) > "JULIAN:1486"^^knora-api-simple:Date && knora-api:toSimpleDate(?pubdate) < "JULIAN:1491"^^knora-api-simple:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksPublishedBetweenDates.jsonld")
    },
    test("get the regions belonging to a page (submitting the complex schema)") {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?region knora-api:isMainResource true .
          |    ?region knora-api:isRegionOf <http://rdfh.ch/0803/9d626dc76c03> .
          |    ?region knora-api:hasGeometry ?geom .
          |    ?region knora-api:hasComment ?comment .
          |    ?region knora-api:hasColor ?color .
          |} WHERE {
          |    ?region a knora-api:Region .
          |    ?region knora-api:isRegionOf <http://rdfh.ch/0803/9d626dc76c03> .
          |    ?region knora-api:hasGeometry ?geom .
          |    ?region knora-api:hasComment ?comment .
          |    ?region knora-api:hasColor ?color .
          |}""".stripMargin
      verifyQueryResult(query, "RegionsForPage.jsonld")
    },
    test(
      "get a book a page points to and include the page in the results (all properties present in WHERE clause) (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:isPartOf ?book .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:seqnum ?seqnum .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:hasStillImageFileValue ?file .
          |
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:isPartOf ?book .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:seqnum ?seqnum .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:hasStillImageFileValue ?file .
          |
          |} OFFSET 0
          |""".stripMargin
      verifyQueryResult(query, "bookWithIncomingPagesWithAllRequestedProps.jsonld")
    },
    test(
      "get a book a page points to and only include the page's partOf link in the results (none of the other properties) (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:isPartOf ?book .
          |
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:isPartOf ?book .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:seqnum ?seqnum .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:hasStillImageFileValue ?file .
          |
          |} OFFSET 0
          |""".stripMargin
      verifyQueryResult(query, "bookWithIncomingPagesOnlyLink.jsonld")
    },
    test("get incoming links pointing to an incunbaula:book, excluding isPartOf (submitting the complex schema)") {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |
          |     ?incomingRes knora-api:isMainResource true .
          |
          |     ?incomingRes ?incomingProp <http://rdfh.ch/0803/8be1b7cf7103> .
          |
          |} WHERE {
          |
          |     ?incomingRes ?incomingProp <http://rdfh.ch/0803/8be1b7cf7103> .
          |
          |     <http://rdfh.ch/0803/8be1b7cf7103> a incunabula:book .
          |
          |     FILTER NOT EXISTS {
          |         ?incomingRes knora-api:isPartOf <http://rdfh.ch/0803/8be1b7cf7103> .
          |     }
          |
          |} OFFSET 0
          |""".stripMargin
      verifyQueryResult(query, "IncomingLinksForBook.jsonld")
    },
    test("search for an anything:Thing that has a decimal value of 2.1 (submitting the complex schema)") {
      val query =
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
          |
          |     ?thing anything:hasDecimal ?decimal .
          |
          |     ?decimal knora-api:decimalValueAsDecimal "2.1"^^xsd:decimal .
          |}
          |""".stripMargin
      verifyQueryResult(query, "ThingEqualsDecimal.jsonld", anythingUser1)
    },
    test(
      "search for an anything:Thing that has a decimal value of 2.1 (submitting the complex schema), without inference",
    ) {
      val query =
        """PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasDecimal ?decimal .
          |} WHERE {
          |    knora-api:GravsearchOptions knora-api:useInference false .
          |    ?thing a anything:Thing .
          |    ?thing anything:hasDecimal ?decimal .
          |    ?decimal knora-api:decimalValueAsDecimal "2.1"^^xsd:decimal .
          |}""".stripMargin
      verifyQueryResult(query, "ThingEqualsDecimal.jsonld", anythingUser1)
    },
    test("search for an anything:Thing that has a decimal value bigger than 2.0 (submitting the complex schema)") {
      val query =
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
          |
          |     ?thing anything:hasDecimal ?decimal .
          |
          |     ?decimal knora-api:decimalValueAsDecimal ?decimalDec .
          |
          |     FILTER(?decimalDec > "2"^^xsd:decimal)
          |}
          |""".stripMargin
      verifyQueryResult(query, "ThingBiggerThanDecimal.jsonld", anythingUser1)
    },
    test("search for an anything:Thing that has a decimal value smaller than 3.0 (submitting the complex schema)") {
      val query =
        """PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |     ?thing anything:hasDecimal ?decimal .
          |} WHERE {
          |     ?thing a anything:Thing .
          |     ?thing anything:hasDecimal ?decimal .
          |     ?decimal knora-api:decimalValueAsDecimal ?decimalDec .
          |     FILTER(?decimalDec < "3"^^xsd:decimal)
          |}""".stripMargin
      verifyQueryResult(query, "ThingSmallerThanDecimal.jsonld", anythingUser1)
    },
    test("search for an anything:Thing that has a link to a specified other thing") {
      val query =
        """PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasOtherThing <http://rdfh.ch/0001/start> .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasOtherThing <http://rdfh.ch/0001/start> .
          |}""".stripMargin
      verifyQueryResult(query, "ThingWithLinkToStart.jsonld", anythingUser1)
    },
    test("return a page of anything:Thing resources") {
      val query =
        """PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |} WHERE {
          |    ?thing a anything:Thing .
          |}""".stripMargin
      verifyQueryResult(query, "PageOfThings.jsonld", anythingUser1)
    },
    test("search for an anything:Thing that has a Boolean value that is true (submitting the complex schema)") {
      val query =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |
          |     ?boolean knora-api:booleanValueAsBoolean true .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "ThingWithBoolean.jsonld", anythingUser1)
    },
    test("search for an anything:Thing that may have a Boolean value that is true (submitting the complex schema)") {
      // set OFFSET to 1 to get "Testding for extended search"
      val query =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |
          |     OPTIONAL {
          |         ?thing anything:hasBoolean ?boolean .
          |         ?boolean knora-api:booleanValueAsBoolean true .
          |     }
          |
          |     MINUS {
          |         ?thing anything:hasInteger ?intVal .
          |         ?intVal knora-api:intValueAsInt 123454321 .
          |     }
          |
          |     MINUS {
          |         ?thing anything:hasInteger ?intVal .
          |         ?intVal knora-api:intValueAsInt 999999999 .
          |     }
          |
          |} OFFSET 1
          |""".stripMargin
      verifyQueryResult(query, "ThingWithBooleanOptionalOffset1.jsonld", anythingUser1)
    },
    test(
      "search for an anything:Thing that either has a Boolean value that is true or a decimal value that equals 2.1 (or both) (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |
          |     ?thing anything:hasDecimal ?decimal .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |
          |     {
          |         ?thing anything:hasBoolean ?boolean .
          |
          |         ?boolean knora-api:booleanValueAsBoolean ?booleanBool .
          |
          |         FILTER(?booleanBool = true)
          |     } UNION {
          |         ?thing anything:hasDecimal ?decimal .
          |
          |         ?decimal knora-api:decimalValueAsDecimal "2.1"^^xsd:decimal .
          |     }
          |
          |} OFFSET 0
          |
          |""".stripMargin
      verifyQueryResult(query, "ThingWithBooleanOrDecimal.jsonld", anythingUser1)
    },
    test("search for a book whose title contains 'Zeit' using the regex function (submitting the complex schema)") {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |
          |    ?mainRes knora-api:isMainResource true .
          |
          |    ?mainRes incunabula:title ?title .
          |
          |} WHERE {
          |
          |    ?mainRes a incunabula:book .
          |
          |    ?mainRes incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString ?titleStr .
          |
          |    FILTER regex(?titleStr, "Zeit", "i")
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "BooksWithTitleContainingZeit.jsonld", anythingUser1)
    },
    test(
      "search for a book whose title contains 'Zeitglöcklein' using the match function (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |
          |   ?mainRes knora-api:isMainResource true .
          |
          |   ?mainRes incunabula:title ?title .
          |
          |} WHERE {
          |
          |   ?mainRes a incunabula:book .
          |
          |   ?mainRes incunabula:title ?title .
          |
          |   FILTER knora-api:matchText(?title, "Zeitglöcklein")
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "BooksWithTitleContainingZeitgloecklein.jsonld", anythingUser1)
    },
    test(
      "search for a book whose title contains 'Zeitglöcklein' and 'Lebens' using the match function (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |
          |   ?mainRes knora-api:isMainResource true .
          |
          |   ?mainRes incunabula:title ?title .
          |
          |} WHERE {
          |
          |   ?mainRes a incunabula:book .
          |
          |   ?mainRes incunabula:title ?title .
          |
          |   FILTER knora-api:matchText(?title, "Zeitglöcklein AND Lebens")
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "BooksWithTitleContainingZeitgloecklein.jsonld", anythingUser1)
    },
    test("search for an anything:Thing with a list value (submitting the complex schema)") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |
          |    ?thing anything:hasListItem ?listItem .
          |
          |} WHERE {
          |    ?thing a anything:Thing .
          |
          |    ?thing anything:hasListItem ?listItem .
          |
          |} OFFSET 0
          |""".stripMargin
      verifyQueryResult(query, "ThingWithListValue.jsonld", anythingUser1)
    },
    test("search for a text in a particular language (submitting the complex schema)") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasText ?text .
          |} WHERE {
          |     ?thing a anything:Thing .
          |
          |     ?thing anything:hasText ?text .
          |
          |     ?text knora-api:textValueHasLanguage "fr" .
          |}
          |""".stripMargin
      verifyQueryResult(query, "LanguageFulltextSearch.jsonld", anythingUser1)
    },
    test("search for a specific text using the lang function (submitting the complex schema)") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasText ?text .
          |} WHERE {
          |     ?thing a anything:Thing .
          |
          |     ?thing anything:hasText ?text .
          |
          |     ?text knora-api:valueAsString "Bonjour" .
          |
          |     ?text knora-api:textValueHasLanguage "fr" .
          |}
          |""".stripMargin
      verifyQueryResult(query, "LanguageFulltextSearch.jsonld", anythingUser1)
    },
    test("do a Gravsearch query for link objects that link to an incunabula book (submitting the complex schema)") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |
          |CONSTRUCT {
          |     ?linkObj knora-api:isMainResource true .
          |
          |     ?linkObj knora-api:hasLinkTo ?book .
          |
          |} WHERE {
          |     ?linkObj a knora-api:LinkObj .
          |
          |     ?linkObj knora-api:hasLinkTo ?book .
          |
          |     ?book a incunabula:book .
          |
          |     ?book incunabula:title ?title .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "LinkObjectsToBooks.jsonld", anythingUser1)
    },
    test(
      "do a Gravsearch query for a letter that links to a specific person via two possible properties (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |
          |    ?letter beol:creationDate ?date .
          |
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> .
          |
          |
          |} WHERE {
          |    ?letter a beol:letter .
          |
          |    ?letter beol:creationDate ?date .
          |
          |    # testperson2
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "letterWithAuthor.jsonld", anythingUser1)
    },
    test(
      "do a Gravsearch query for a letter that links to a person with a specified name (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |
          |    ?letter beol:creationDate ?date .
          |
          |    ?letter ?linkingProp1  ?person1 .
          |
          |    ?person1 beol:hasFamilyName ?name .
          |
          |} WHERE {
          |    ?letter a beol:letter .
          |
          |    ?letter beol:creationDate ?date .
          |
          |    ?letter ?linkingProp1 ?person1 .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |    ?person1 beol:hasFamilyName ?name .
          |
          |    ?name knora-api:valueAsString "Meier" .
          |
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "letterWithPersonWithName.jsonld", anythingUser1)
    },
    test(
      "do a Gravsearch query for a letter that links to another person with a specified name (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |
          |    ?letter beol:creationDate ?date .
          |
          |    ?letter ?linkingProp1  ?person1 .
          |
          |    ?person1 beol:hasFamilyName ?name .
          |
          |} WHERE {
          |    ?letter a beol:letter .
          |
          |    ?letter beol:creationDate ?date .
          |
          |    ?letter ?linkingProp1 ?person1 .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |    ?person1 beol:hasFamilyName ?name .
          |
          |    ?name knora-api:valueAsString "Muster" .
          |
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "letterWithPersonWithName2.jsonld")
    },
    test(
      "run a Gravsearch query that searches for a single resource specified by its IRI (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true ;
          |         anything:hasText ?text ;
          |         anything:hasInteger ?integer .
          |
          |} WHERE {
          |     BIND(<http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw> AS ?thing)
          |
          |     ?thing a anything:Thing .
          |     ?thing anything:hasText ?text .
          |     ?thing anything:hasInteger ?integer .
          |}
          |""".stripMargin
      verifyQueryResult(query, "ThingByIriWithRequestedValues.jsonld", anythingUser1)
    },
    test(
      "do a Gravsearch query for a letter and get information about the persons associated with it (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |
          |    ?letter beol:creationDate ?date .
          |
          |    ?letter ?linkingProp1 ?person1 .
          |
          |    ?person1 beol:hasFamilyName ?familyName .
          |
          |
          |} WHERE {
          |    BIND(<http://rdfh.ch/0801/_B3lQa6tSymIq7_7SowBsA> AS ?letter)
          |    ?letter a beol:letter .
          |
          |    ?letter beol:creationDate ?date .
          |
          |    # testperson2
          |    ?letter ?linkingProp1 ?person1 .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |    ?person1 beol:hasFamilyName ?familyName .
          |
          |} ORDER BY ?date
          |""".stripMargin
      verifyQueryResult(query, "letterWithAuthorWithInformation.jsonld")
    },
    test(
      "do a Gravsearch query for the pages of a book whose seqnum is lower than or equals 10, with the book as the main resource (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |    ?book incunabula:title ?title .
          |
          |    ?page knora-api:isPartOf ?book ;
          |        incunabula:seqnum ?seqnum .
          |} WHERE {
          |    BIND(<http://rdfh.ch/0803/b6b5ff1eb703> AS ?book)
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |
          |    ?seqnum knora-api:intValueAsInt ?seqnumInt .
          |
          |    FILTER(?seqnumInt <= 10)
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "incomingPagesForBook.jsonld", incunabulaMemberUser)
    },
    test(
      "reject a Gravsearch query containing a statement whose subject is not the main resource and whose object is used in ORDER BY (submitting the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |    ?book incunabula:title ?title .
          |
          |    ?page knora-api:isPartOf ?book ;
          |        incunabula:seqnum ?seqnum .
          |} WHERE {
          |    BIND(<http://rdfh.ch/0803/b6b5ff1eb703> AS ?book)
          |
          |    ?book incunabula:title ?title .
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |
          |    ?seqnum knora-api:intValueAsInt ?seqnumInt .
          |
          |    FILTER(?seqnumInt <= 10)
          |
          |} ORDER BY ?seqnum
          |""".stripMargin
      postGravsearchQuery(query, Some(incunabulaMemberUser))
        .map(response => assertTrue(response.code == StatusCode.BadRequest))
    },
    test(
      "do a Gravsearch query for regions that belong to pages that are part of a book with the title 'Zeitglöcklein des Lebens und Leidens Christi' (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?region knora-api:isMainResource true .
          |
          |    ?region knora-api:isRegionOf ?page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?book incunabula:title ?title .
          |
          |} WHERE {
          |	   ?region a knora-api:Region .
          |
          |	   ?region knora-api:isRegionOf ?page .
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString "Zeitglöcklein des Lebens und Leidens Christi" .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "regionsOfZeitgloecklein.jsonld", incunabulaMemberUser)
    },
    test("reject a Gravsearch query in the complex schema that uses knora-api:isMainResource in the simple schema") {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?region knora-api-simple:isMainResource true .
          |
          |    ?region knora-api:isRegionOf ?page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?book incunabula:title ?title .
          |
          |} WHERE {
          |	   ?region a knora-api:Region .
          |
          |	   ?region knora-api:isRegionOf ?page .
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString "Zeitglöcklein des Lebens und Leidens Christi" .
          |
          |}
          |""".stripMargin
      postGravsearchQuery(query, Some(incunabulaMemberUser))
        .map(response => assertTrue(response.code == StatusCode.BadRequest))
    },
    test("reject a Gravsearch query in the complex schema that uses a Knora property in the simple schema") {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX incunabula-simple: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?region knora-api:isMainResource true .
          |
          |    ?region knora-api:isRegionOf ?page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?book incunabula:title ?title .
          |
          |} WHERE {
          |	   ?region a knora-api:Region .
          |
          |	   ?region knora-api:isRegionOf ?page .
          |
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    ?book a incunabula:book .
          |
          |    ?book incunabula-simple:title ?title .
          |
          |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}
          |""".stripMargin
      postGravsearchQuery(query, Some(incunabulaMemberUser))
        .map(response => assertTrue(response.code == StatusCode.BadRequest))
    },
    test("reject a Gravsearch query that uses a string literal in the CONSTRUCT clause") {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString "Zeitglöcklein des Lebens und Leidens Christi" .
          |
          |
          |} WHERE {
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString "Zeitglöcklein des Lebens und Leidens Christi" .
          |
          |}
          |""".stripMargin
      postGravsearchQuery(query, Some(incunabulaMemberUser))
        .map(response => assertTrue(response.code == StatusCode.BadRequest))
    },
    test(
      "reject a Gravsearch query in the complex schema with a variable in the CONSTRUCT clause referring to a non-property entity that isn't a resource or value",
    ) {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString ?titleStr .
          |
          |
          |} WHERE {
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    ?title knora-api:valueAsString ?titleStr .
          |
          |    FILTER(?titleStr = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}
          |""".stripMargin
      postGravsearchQuery(query, Some(incunabulaMemberUser))
        .map(response => assertTrue(response.code == StatusCode.BadRequest))
    },
    test("search for a list value that refers to a particular list node (submitting the complex schema)") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |
          |    ?thing anything:hasListItem ?listItem .
          |
          |} WHERE {
          |    ?thing anything:hasListItem ?listItem .
          |
          |    ?listItem knora-api:listValueAsListNode <http://rdfh.ch/lists/0001/treeList02> .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "thingReferringToSpecificListNode.jsonld", incunabulaMemberUser)
    },
    test("search for a list value that does not refer to a particular list node (submitting the complex schema)") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |
          |    ?thing anything:hasListItem ?listItem .
          |
          |} WHERE {
          |    ?thing anything:hasListItem ?listItem .
          |
          |    FILTER NOT EXISTS {
          |       ?listItem knora-api:listValueAsListNode <http://rdfh.ch/lists/0001/treeList02> .
          |    }
          |}
          |""".stripMargin
      verifyQueryResult(query, "thingNotReferringToSpecificListNode.jsonld", incunabulaMemberUser)
    },
    test(
      "search for a list value that refers to a particular list node that has subnodes (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |
          |    ?thing anything:hasListItem ?listItem .
          |
          |} WHERE {
          |    ?thing anything:hasListItem ?listItem .
          |
          |    ?listItem knora-api:listValueAsListNode <http://rdfh.ch/lists/0001/treeList> .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "thingReferringToSpecificListNodeWithSubnodes.jsonld", incunabulaMemberUser)
    },
    test(
      "search for a beol:letter with list value that refers to a particular list node (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |
          |    ?letter beol:hasSubject ?subject .
          |
          |} WHERE {
          |    ?letter a beol:letter .
          |
          |    ?letter beol:hasSubject ?subject .
          |
          |    ?subject knora-api:listValueAsListNode <http://rdfh.ch/lists/0801/logarithmic_curves> .
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "letterWithSubject.jsonld", incunabulaMemberUser)
    },
    test("search for a standoff link using the knora-api:standoffLink function (submitting the complex schema)") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasText ?text .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasText ?text .
          |    ?text knora-api:textValueHasStandoff ?standoffTag .
          |    ?standoffTag a knora-api:StandoffLinkTag .
          |    FILTER knora-api:standoffLink(?thing, ?standoffTag, ?otherThing)
          |    ?otherThing a anything:Thing .
          |}
          |""".stripMargin
      verifyQueryResult(query, "thingsWithStandoffLinks.jsonld", anythingUser1)
    },
    test(
      "search for a standoff link using the knora-api:standoffLink function, referring to the target resource in the function call only (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasText ?text .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasText ?text .
          |    ?text knora-api:textValueHasStandoff ?standoffTag .
          |    ?standoffTag a knora-api:StandoffLinkTag .
          |    FILTER knora-api:standoffLink(?thing, ?standoffTag, ?otherThing)
          |
          |    # Note that ?otherThing is only used as a argument in the function, not in any other statement
          |}
          |""".stripMargin
      verifyQueryResult(query, "thingsWithStandoffLinks.jsonld", anythingUser1)
    },
    test(
      "search for a standoff link using the knora-api:standoffLink function specifying an Iri for the target resource (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasText ?text .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasText ?text .
          |    ?text knora-api:textValueHasStandoff ?standoffTag .
          |    ?standoffTag a knora-api:StandoffLinkTag .
          |    FILTER knora-api:standoffLink(?thing, ?standoffTag, <http://rdfh.ch/0001/a-thing>)
          |    <http://rdfh.ch/0001/a-thing> a anything:Thing .
          |}
          |""".stripMargin
      verifyQueryResult(query, "thingsWithStandoffLinksToSpecificThing.jsonld", anythingUser1)
    },
    test(
      "search for a standoff link using the knora-api:standoffLink function specifying an Iri for the target resource, referring to the target resource in the function call only (submitting the complex schema)",
    ) {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasText ?text .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasText ?text .
          |    ?text knora-api:textValueHasStandoff ?standoffTag .
          |    ?standoffTag a knora-api:StandoffLinkTag .
          |    FILTER knora-api:standoffLink(?thing, ?standoffTag, <http://rdfh.ch/0001/a-thing>)
          |
          |    # Note that <http://rdfh.ch/0001/a-thing> is only used as a argument in the function, not in any other statement
          |}
          |""".stripMargin
      verifyQueryResult(query, "thingsWithStandoffLinksToSpecificThing.jsonld", anythingUser1)
    },
    test("search for matching words in a particular type of standoff tag (submitting the complex schema)") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasRichtext ?text .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasRichtext ?text .
          |    ?text knora-api:textValueHasStandoff ?standoffTag .
          |    ?standoffTag a standoff:StandoffItalicTag .
          |    FILTER knora-api:matchTextInStandoff(?text, ?standoffTag, "interesting text")
          |}
          |""".stripMargin
      verifyQueryResult(query, "ThingWithRichtextWithTermTextInParagraph.jsonld")
    },
    test("search for a standoff date tag indicating a date in a particular range (submitting the complex schema)") {
      // First, we will create a standoff-to-XML mapping that can handle standoff date tags.
      val jsonPart =
        s"""
           |{
           |    "knora-api:mappingHasName": "HTMLMapping",
           |    "knora-api:attachedToProject": {
           |      "@id": "$anythingProjectIri"
           |    },
           |    "rdfs:label": "mapping for HTML",
           |    "@context": {
           |        "rdfs": "${OntologyConstants.Rdfs.RdfsPrefixExpansion}",
           |        "knora-api": "${OntologyConstants.KnoraApiV2Complex.KnoraApiV2PrefixExpansion}"
           |    }
           |}""".stripMargin
      val xmlPart = Paths.get("test_data/test_route/texts/mappingForHTML.xml")
      val createStandoffMappingBody = Seq(
        multipart("json", jsonPart).contentType("application/json"),
        multipartFile("xml", xmlPart).contentType("text/xml(UTF-8)"),
      )

      // Next, create a resource with a text value containing a standoff date tag.
      val xmlForJson = FileUtil.readTextFile(Paths.get("test_data/test_route/texts/HTML.xml"))
      val createValueBody = Json.Obj(
        ("@id", Json.Str("http://rdfh.ch/0001/a-thing")),
        ("@type", Json.Str("anything:Thing")),
        (
          "anything:hasText",
          Json.Obj(
            ("@type", Json.Str("knora-api:TextValue")),
            ("knora-api:textValueAsXml", Json.Str(xmlForJson)),
            (
              "knora-api:textValueHasMapping",
              Json.Obj(
                ("@id", Json.Str(s"$anythingProjectIri/mappings/HTMLMapping")),
              ),
            ),
          ),
        ),
        (
          "@context",
          Json.Obj(
            ("knora-api", Json.Str("http://api.knora.org/ontology/knora-api/v2#")),
            ("anything", Json.Str("http://0.0.0.0:3333/ontology/0001/anything/v2#")),
          ),
        ),
      )

      // Finally, do a Gravsearch query that finds the date tag.
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |    ?thing anything:hasText ?text .
          |} WHERE {
          |    ?thing a anything:Thing .
          |    ?thing anything:hasText ?text .
          |    ?text knora-api:textValueHasStandoff ?standoffEventTag .
          |    ?standoffEventTag a anything:StandoffEventTag .
          |    FILTER(knora-api:toSimpleDate(?standoffEventTag) = "GREGORIAN:2016-12 CE"^^knora-api-simple:Date)
          |}""".stripMargin

      for {
        _ <- TestApiClient // create the new mapping
               .postMultiPart[Json](uri"/v2/mapping", createStandoffMappingBody, anythingUser1)
               .flatMap(_.assert200)
        _ <- TestApiClient // create the new value
               .postJsonLd(uri"/v2/values", createValueBody.toString, anythingUser1)
               .flatMap(_.assert200)
        actual <- postGravsearchQuery(gravsearchQuery, Some(anythingUser1)).flatMap(_.assert200)
      } yield assertTrue(actual.contains("we will have a party"))
    },
    test("search for a standoff tag using knora-api:standoffTagHasStartAncestor (submitting the complex schema)") {
      val query =
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
          |}
          |""".stripMargin
      for {
        actual <- postGravsearchQuery(query, Some(anythingUser1)).flatMap(_.assert200)
      } yield assertTrue(actual.contains("we will have a party"))
    },
    test("reject a link value property in a query in the simple schema") {
      val query =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |    ?book incunabula:title ?title .
          |    ?page incunabula:partOfValue ?book .
          |} WHERE {
          |    ?book a incunabula:book .
          |    ?book incunabula:title ?title .
          |    ?page a incunabula:page .
          |    ?page incunabula:partOfValue ?book .
          |}
          |""".stripMargin
      for {
        response <- postGravsearchQuery(query, Some(incunabulaMemberUser))
        actual   <- response.assert404
      } yield assertTrue(
        actual.contains("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#partOfValue"),
      )
    },
    test("find a resource with two different incoming links") {
      val createTargetResource =
        val jsonLd = """{
                       |  "@type" : "anything:BlueThing",
                       |  "knora-api:attachedToProject" : {
                       |    "@id" : "http://rdfh.ch/projects/0001"
                       |  },
                       |  "rdfs:label" : "blue thing with incoming links",
                       |  "@context" : {
                       |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                       |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                       |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                       |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                       |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                       |  }
                       |
                       |}""".stripMargin
        TestApiClient
          .postJsonLdDocument(uri"/v2/resources", jsonLd, anythingUser1)
          .flatMap(_.assert200)
          .flatMap(doc => ZIO.fromEither(doc.body.getRequiredString(JsonLDKeywords.ID)))

      def createLinkedResource(label: String, targetIri: String) =
        val jsonLd = s"""{
                        |  "@type" : "anything:BlueThing",
                        |  "knora-api:attachedToProject" : {
                        |    "@id" : "http://rdfh.ch/projects/0001"
                        |  },
                        |    "anything:hasBlueThingValue" : {
                        |    "@type" : "knora-api:LinkValue",
                        |        "knora-api:linkValueHasTargetIri" : {
                        |        "@id" : "$targetIri"
                        |    }
                        |  },
                        |  "rdfs:label" : "$label",
                        |  "@context" : {
                        |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
                        |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
                        |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
                        |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
                        |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
                        |  }
                        |}
                        |""".stripMargin
        TestApiClient.postJsonLdDocument(uri"/v2/resources", jsonLd, anythingUser1).flatMap(_.assert200)

      for {
        targetResourceIri <- createTargetResource
        _                 <- createLinkedResource("blue thing with link to other blue thing", targetResourceIri)
        _                 <- createLinkedResource("thing with link to blue thing", targetResourceIri)

        gravsearchQuery =
          s"""
             |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
             |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
             |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
             |
             |CONSTRUCT {
             |    ?targetThing knora-api:isMainResource true .
             |    ?firstIncoming anything:hasBlueThing ?targetThing .
             |    ?secondIncoming anything:hasOtherThing ?targetThing .
             |} WHERE {
             |    ?targetThing a anything:BlueThing .
             |    ?firstIncoming anything:hasBlueThing ?targetThing .
             |    ?secondIncoming anything:hasOtherThing ?targetThing .
             |}
             |""".stripMargin
        queryResult <- postGravsearchQuery(gravsearchQuery, Some(anythingUser1))
                         .flatMap(_.assert200)
                         .mapAttempt(JsonLDUtil.parseJsonLD)
        searchResultIri <- ZIO.fromEither(queryResult.body.getRequiredString(JsonLDKeywords.ID))
      } yield assertTrue(searchResultIri == targetResourceIri)
    },
    test("search for an anything:Thing with a time value (using the simple schema)") {
      val query =
        s"""
           |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
           |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
           |
           |CONSTRUCT {
           |    ?thing knora-api:isMainResource true .
           |    ?thing anything:hasTimeStamp ?timeStamp .
           |} WHERE {
           |    ?thing a anything:Thing .
           |    ?thing anything:hasTimeStamp ?timeStamp .
           |    FILTER(?timeStamp > "2019-08-30T10:45:26.365863Z"^^xsd:dateTimeStamp)
           |}
           |""".stripMargin
      verifyQueryResult(query, "ThingWithTimeStamp.jsonld", anythingUser1)
    },
    test("get a resource with a link to another resource that the user doesn't have permission to see") {
      val query =
        s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
           |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
           |
           |CONSTRUCT {
           |    ?mainThing knora-api:isMainResource true .
           |    ?mainThing anything:hasOtherThing ?hiddenThing .
           |    ?hiddenThing anything:hasInteger ?intValInHiddenThing .
           |    ?mainThing anything:hasOtherThing ?visibleThing .
           |    ?visibleThing anything:hasInteger ?intValInVisibleThing .
           |} WHERE {
           |    ?mainThing a anything:Thing .
           |    ?mainThing anything:hasOtherThing ?hiddenThing .
           |    ?hiddenThing anything:hasInteger ?intValInHiddenThing .
           |    ?intValInHiddenThing knora-api:intValueAsInt 123454321 .
           |    ?mainThing anything:hasOtherThing ?visibleThing .
           |    ?visibleThing anything:hasInteger ?intValInVisibleThing .
           |    ?intValInVisibleThing knora-api:intValueAsInt 543212345 .
           |}""".stripMargin
      verifyQueryResult(query, "ThingWithHiddenThing.jsonld")
    },
    test("not return duplicate results when there are UNION branches with different variables") {
      val query =
        s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
           |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
           |
           |CONSTRUCT {
           |    ?thing knora-api:isMainResource true .
           |    ?thing anything:hasInteger ?int .
           |    ?thing anything:hasRichtext ?richtext .
           |    ?thing anything:hasText ?text .
           |} WHERE {
           |    ?thing a knora-api:Resource .
           |    ?thing a anything:Thing .
           |    ?thing anything:hasInteger ?int .
           |    ?int knora-api:intValueAsInt 1 .
           |
           |    {
           |        ?thing anything:hasRichtext ?richtext .
           |        FILTER knora-api:matchText(?richtext, "test")
           |    }
           |    UNION
           |    {
           |        ?thing anything:hasText ?text .
           |        FILTER knora-api:matchText(?text, "test")
           |    }
           |}
           |ORDER BY (?int)""".stripMargin
      verifyQueryResult(query, "ThingFromQueryWithUnion.jsonld")
    },
    test("reject an ORDER by containing a variable that's not bound at the top level of the WHERE clause") {
      val query =
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
           |		?thing anything:hasInteger ?int .
           |		?int knora-api:intValueAsInt 1 .
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
      for {
        response <- postGravsearchQuery(query)
        actual   <- response.assert400
      } yield assertTrue(
        actual.contains("Variable ?int is used in ORDER by, but is not bound at the top level of the WHERE clause"),
      )
    },
    test("reject a FILTER in a UNION that uses a variable that's out of scope") {
      val query =
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
           |    ?thing anything:hasRichtext ?richtext .
           |    ?thing anything:hasInteger ?int .
           |    ?int knora-api:intValueAsInt 1 .
           |
           |    {
           |        FILTER knora-api:matchText(?richtext, "test")
           |    }
           |    UNION
           |    {
           |        ?thing anything:hasText ?text .
           |        FILTER knora-api:matchText(?text, "test")
           |    }
           |}
           |ORDER BY (?int)""".stripMargin
      for {
        response <- postGravsearchQuery(query)
        actual   <- response.assert400
      } yield assertTrue(
        actual.contains(
          "One or more variables used in a filter have not been bound in the same UNION block: ?richtext",
        ),
      )
    },
    test("search for a resource containing a time value tag") {
      // Create a resource containing a time value.
      val xmlStr =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<text documentType="html">
          |    <p>The timestamp for this test is <span class="timestamp" data-timestamp="2020-01-27T08:31:51.503187Z">27 January 2020</span>.</p>
          |</text>
          |""".stripMargin

      val createResourceJsonLd = Json.Obj(
        ("@type", Json.Str("anything:Thing")),
        (
          "anything:hasText",
          Json.Obj(
            ("@type", Json.Str("knora-api:TextValue")),
            ("knora-api:textValueAsXml", Json.Str(xmlStr)),
            (
              "knora-api:textValueHasMapping",
              Json.Obj(
                ("@id", Json.Str(s"$anythingProjectIri/mappings/HTMLMapping")),
              ),
            ),
          ),
        ),
        (
          "knora-api:attachedToProject",
          Json.Obj(
            ("@id", Json.Str("http://rdfh.ch/projects/0001")),
          ),
        ),
        ("rdfs:label", Json.Str("thing with timestamp in markup")),
        (
          "@context",
          Json.Obj(
            ("rdf", Json.Str("http://www.w3.org/1999/02/22-rdf-syntax-ns#")),
            ("knora-api", Json.Str("http://api.knora.org/ontology/knora-api/v2#")),
            ("rdfs", Json.Str("http://www.w3.org/2000/01/rdf-schema#")),
            ("xsd", Json.Str("http://www.w3.org/2001/XMLSchema#")),
            ("anything", Json.Str("http://0.0.0.0:3333/ontology/0001/anything/v2#")),
          ),
        ),
      )
      for {
        resourceIri <- TestApiClient
                         .postJsonLdDocument(uri"/v2/resources", createResourceJsonLd.toString, anythingUser1)
                         .flatMap(_.assert200)
                         .flatMap(doc => ZIO.fromEither(doc.body.getRequiredString(JsonLDKeywords.ID)))

        // Search for the resource.
        gravsearchQuery =
          """
            |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
            |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
            |
            |CONSTRUCT {
            |    ?thing knora-api:isMainResource true .
            |    ?thing anything:hasText ?text .
            |} WHERE {
            |    ?thing a anything:Thing .
            |    ?thing anything:hasText ?text .
            |    ?text knora-api:textValueHasStandoff ?standoffTag .
            |    ?standoffTag a knora-api:StandoffTimeTag .
            |    ?standoffTag knora-api:timeValueAsTimeStamp ?timeStamp .
            |    FILTER(?timeStamp > "2020-01-27T08:31:51Z"^^xsd:dateTimeStamp && ?timeStamp < "2020-01-27T08:31:52Z"^^xsd:dateTimeStamp)
            |}
            |""".stripMargin
        actualResponse <- postGravsearchQuery(gravsearchQuery, Some(anythingUser1))
                            .flatMap(_.assert200)
                            .mapAttempt(JsonLDUtil.parseJsonLD)
        actualResourceIri <- ZIO.fromEither(actualResponse.body.getRequiredString(JsonLDKeywords.ID))
        xmlFromResponse <- ZIO.fromEither(
                             actualResponse.body
                               .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasText")
                               .flatMap(_.getRequiredString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml)),
                           )
        xmlDiff = DiffBuilder.compare(Input.fromString(xmlStr)).withTest(Input.fromString(xmlFromResponse)).build()
      } yield assertTrue(actualResourceIri == resourceIri, !xmlDiff.hasDifferences)
    },
    test("search for an rdfs:label using a literal in the simple schema") {
      val query =
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
          |""".stripMargin
      verifyQueryResult(query, "ZeitgloeckleinViaLabel.jsonld")
    },
    test("search for an rdfs:label using a literal in the complex schema") {
      val query =
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
          |}""".stripMargin
      verifyQueryResult(query, "ZeitgloeckleinViaLabel.jsonld")
    },
  )
}
