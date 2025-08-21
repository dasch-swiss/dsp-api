/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import sttp.client4.UriContext
import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.testservices.RequestsUpdates
import org.knora.webapi.testservices.RequestsUpdates.RequestUpdate
import org.knora.webapi.testservices.RequestsUpdates.addSimpleSchemaHeader
import org.knora.webapi.testservices.ResponseOps.assert200
import org.knora.webapi.testservices.TestApiClient
import org.knora.webapi.util.TestDataFileUtil

object SearchEndpointsPostGravsearchE2ESpec extends E2EZSpec {

  override lazy val rdfDataObjects: List[RdfDataObject] = List(
    RdfDataObject(path = "test_data/project_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(
      path = "test_data/project_data/incunabula-data.ttl",
      name = "http://www.knora.org/data/0803/incunabula",
    ),
    RdfDataObject(path = "test_data/project_data/beol-data.ttl", name = "http://www.knora.org/data/0801/beol"),
    RdfDataObject(
      path = "test_data/project_ontologies/books-onto.ttl",
      name = "http://www.knora.org/ontology/0001/books",
    ),
    RdfDataObject(path = "test_data/project_data/books-data.ttl", name = "http://www.knora.org/data/0001/books"),
    RdfDataObject(
      path = "test_data/generated_test_data/e2e.v2.SearchRouteV2R2RSpec/gravsearchtest1-admin.ttl",
      name = "http://www.knora.org/data/admin",
    ),
    RdfDataObject(
      path = "test_data/generated_test_data/e2e.v2.SearchRouteV2R2RSpec/gravsearchtest1-onto.ttl",
      name = "http://www.knora.org/ontology/0666/gravsearchtest1",
    ),
    RdfDataObject(
      path = "test_data/generated_test_data/e2e.v2.SearchRouteV2R2RSpec/gravsearchtest1-data.ttl",
      name = "http://www.knora.org/data/0666/gravsearchtest1",
    ),
  )

  private def loadFile(filename: String) = TestDataFileUtil.readTestData("searchR2RV2", filename)

  private def verifyQueryResult(
    query: String,
    expectedFile: String,
    update: RequestUpdate[String] = identity,
  ) = for {
    actual <- TestApiClient
                .postSparql(uri"/v2/searchextended", query, update)
                .flatMap(_.assert200)
                .mapAttempt(RdfModel.fromJsonLD)
    expected <- loadFile(expectedFile).mapAttempt(RdfModel.fromJsonLD)
  } yield assertTrue(actual == expected)

  override def e2eSpec = suite("SearchEndpoints POST /v2/searchextended")(
    test("perform a Gravsearch query using simple schema which allows to sort the results by external link") {
      val query =
        """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |  ?res knora-api:isMainResource true .
          |  ?res anything:hasUri ?exLink .
          |} WHERE {
          |  ?res a knora-api:Resource .
          |  ?res a anything:Thing .
          |  ?res anything:hasUri ?exLink .
          |}
          |ORDER BY (?exLink)""".stripMargin
      TestApiClient.postSparql(uri"/v2/searchextended", query).flatMap(_.assert200).as(assertCompletes)
    },
    test("perform a Gravsearch query using complex schema which allows to sort the results by external link") {
      val query =
        """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |CONSTRUCT {
          |  ?res knora-api:isMainResource true .
          |  ?res anything:hasUri ?exLink .
          |} WHERE {
          |  ?res a knora-api:Resource .
          |  ?res a anything:Thing .
          |  ?res anything:hasUri ?exLink .
          |}
          |ORDER BY (?exLink)""".stripMargin
      TestApiClient.postSparql(uri"/v2/searchextended", query).flatMap(_.assert200).as(assertCompletes)
    },
    test("perform a Gravsearch query for an anything:Thing with an optional date and sort by date") {
      val query =
        """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
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
          |    anything:hasDate knora-api:objectType knora-api:Date .
          |    ?date a knora-api:Date .
          |  }
          |
          |  MINUS {
          |    ?thing anything:hasInteger ?intVal .
          |    anything:hasInteger knora-api:objectType xsd:integer .
          |    ?intVal a xsd:integer .
          |    FILTER(?intVal = 123454321 || ?intVal = 999999999)
          |  }
          |}
          |ORDER BY DESC(?date)""".stripMargin
      verifyQueryResult(query, "thingWithOptionalDateSortedDesc.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer (in the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |
          |    ?book knora-api:isMainResource true .
          |    ?book incunabula:title ?title .
          |
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |    ?book a knora-api:Resource .
          |
          |    ?book incunabula:title ?title .
          |    incunabula:title knora-api:objectType xsd:string .
          |
          |    ?title a xsd:string .
          |
          |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}""".stripMargin
      verifyQueryResult(query, "ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have the dcterms:title 'Zeitglöcklein des Lebens' returning the title in the answer (in the complex schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX dcterms: <http://purl.org/dc/terms/>
          |
          |CONSTRUCT {
          |
          |    ?book knora-api:isMainResource true .
          |    ?book dcterms:title ?title .
          |
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |    ?book a knora-api:Resource .
          |
          |    ?book dcterms:title ?title .
          |    dcterms:title knora-api:objectType xsd:string .
          |
          |    ?title a xsd:string .
          |
          |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}""".stripMargin
      verifyQueryResult(query, "ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer (in the simple schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |
          |    ?book knora-api:isMainResource true .
          |    ?book incunabula:title ?title .
          |
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |    ?book a knora-api:Resource .
          |
          |    ?book incunabula:title ?title .
          |    incunabula:title knora-api:objectType xsd:string .
          |
          |    ?title a xsd:string .
          |
          |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}""".stripMargin
      verifyQueryResult(query, "ZeitgloeckleinExtendedSearchWithTitleInAnswerSimple.jsonld", addSimpleSchemaHeader)
    },
    test(
      "perform a Gravsearch query for books that have the dcterms:title 'Zeitglöcklein des Lebens' returning the title in the answer (in the simple schema)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX dcterms: <http://purl.org/dc/terms/>
          |
          |CONSTRUCT {
          |
          |    ?book knora-api:isMainResource true .
          |    ?book dcterms:title ?title .
          |
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |    ?book a knora-api:Resource .
          |
          |    ?book dcterms:title ?title .
          |    dcterms:title knora-api:objectType xsd:string .
          |
          |    ?title a xsd:string .
          |
          |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}""".stripMargin
      verifyQueryResult(query, "ZeitgloeckleinExtendedSearchWithTitleInAnswerSimple.jsonld", addSimpleSchemaHeader)
    },
    test(
      "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' not returning the title in the answer",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |    ?book a knora-api:Resource .
          |
          |    ?book incunabula:title ?title .
          |    incunabula:title knora-api:objectType xsd:string .
          |
          |    ?title a xsd:string .
          |
          |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}""".stripMargin
      verifyQueryResult(query, "ZeitgloeckleinExtendedSearchNoTitleInAnswer.jsonld")
    },
    test("perform a Gravsearch query for books that do not have the title 'Zeitglöcklein des Lebens'") {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |    ?book incunabula:title ?title .
          |
          |} WHERE {
          |
          |    ?book a incunabula:book .
          |    ?book a knora-api:Resource .
          |
          |    ?book incunabula:title ?title .
          |    incunabula:title knora-api:objectType xsd:string .
          |
          |    ?title a xsd:string .
          |
          |    FILTER(?title != "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}""".stripMargin
      verifyQueryResult(query, "NotZeitgloeckleinExtendedSearch.jsonld")
    },
    test(
      "perform a Gravsearch query for the page of a book whose seqnum equals 10, returning the seqnum and the link value",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |     ?page knora-api:isMainResource true .
          |
          |     ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |     ?page incunabula:seqnum ?seqnum .
          |} WHERE {
          |
          |     ?page a incunabula:page .
          |     ?page a knora-api:Resource .
          |
          |     ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |     knora-api:isPartOf knora-api:objectType knora-api:Resource .
          |
          |     <http://rdfh.ch/0803/b6b5ff1eb703> a knora-api:Resource .
          |
          |     ?page incunabula:seqnum ?seqnum .
          |     incunabula:seqnum knora-api:objectType xsd:integer .
          |
          |     FILTER(?seqnum = 10)
          |
          |     ?seqnum a xsd:integer .
          |
          |}""".stripMargin
      verifyQueryResult(query, "PageWithSeqnum10WithSeqnumAndLinkValueInAnswer.jsonld")
    },
    test("perform a Gravsearch query for the page of a book whose seqnum equals 10, returning only the seqnum") {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?page knora-api:isMainResource true .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |} WHERE {
          |
          |    ?page a incunabula:page .
          |    ?page a knora-api:Resource .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |    knora-api:isPartOf knora-api:objectType knora-api:Resource .
          |
          |    <http://rdfh.ch/0803/b6b5ff1eb703> a knora-api:Resource .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |    incunabula:seqnum knora-api:objectType xsd:integer .
          |
          |    FILTER(?seqnum = 10)
          |
          |    ?seqnum a xsd:integer .
          |
          |}""".stripMargin
      verifyQueryResult(query, "PageWithSeqnum10OnlySeqnuminAnswer.jsonld")
    },
    test("perform a Gravsearch query for the pages of a book whose seqnum is lower than or equals 10") {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |    ?page a knora-api:Resource .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |    knora-api:isPartOf knora-api:objectType knora-api:Resource .
          |
          |    <http://rdfh.ch/0803/b6b5ff1eb703> a knora-api:Resource .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |    incunabula:seqnum knora-api:objectType xsd:integer .
          |
          |    FILTER(?seqnum <= 10)
          |
          |    ?seqnum a xsd:integer .
          |
          |} ORDER BY ?seqnum
          |""".stripMargin
      verifyQueryResult(query, "PagesOfLatinNarrenschiffWithSeqnumLowerEquals10.jsonld")
    },
    test("perform a Gravsearch query for the pages of a book and return them ordered by their seqnum") {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |    ?page a knora-api:Resource .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |    knora-api:isPartOf knora-api:objectType knora-api:Resource .
          |
          |    <http://rdfh.ch/0803/b6b5ff1eb703> a knora-api:Resource .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |    incunabula:seqnum knora-api:objectType xsd:integer .
          |
          |    ?seqnum a xsd:integer .
          |
          |} ORDER BY ?seqnum
          |""".stripMargin
      verifyQueryResult(query, "PagesOfNarrenschiffOrderedBySeqnum.jsonld")
    },
    test(
      "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum and get the next OFFSET",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |    ?page a knora-api:Resource .
          |
          |    ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |    knora-api:isPartOf knora-api:objectType knora-api:Resource .
          |
          |    <http://rdfh.ch/0803/b6b5ff1eb703> a knora-api:Resource .
          |
          |    ?page incunabula:seqnum ?seqnum .
          |    incunabula:seqnum knora-api:objectType xsd:integer .
          |
          |    ?seqnum a xsd:integer .
          |
          |} ORDER BY ?seqnum
          |OFFSET 1
          |""".stripMargin
      verifyQueryResult(query, "PagesOfNarrenschiffOrderedBySeqnumNextOffset.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have been published on the first of March 1497 (Julian Calendar) (2)",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |    ?book a knora-api:Resource .
          |
          |    ?book incunabula:title ?title .
          |    incunabula:title knora-api:objectType xsd:string .
          |
          |    ?title a xsd:string .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |    incunabula:pubdate knora-api:objectType knora-api:Date .
          |
          |    ?pubdate a knora-api:Date .
          |
          |    FILTER(?pubdate = "JULIAN:1497-03-01"^^knora-api:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksPublishedOnDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have not been published on the first of March 1497 (Julian Calendar)",
    ) {
      // this is the negation of the query condition above, hence the size of the result set
      // must be 19 (total of incunabula:book) minus 2 (number of results from query above)
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |    ?book a knora-api:Resource .
          |
          |    ?book incunabula:title ?title .
          |    incunabula:title knora-api:objectType xsd:string .
          |
          |    ?title a xsd:string .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |    incunabula:pubdate knora-api:objectType knora-api:Date .
          |
          |    ?pubdate a knora-api:Date .
          |
          |    FILTER(?pubdate != "JULIAN:1497-03-01"^^knora-api:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin

      verifyQueryResult(query, "BooksNotPublishedOnDate.jsonld")
    },
    test(
      "perform a Gravsearch query for books that have not been published on the first of March 1497 (Julian Calendar) 2",
    ) {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |    ?book a knora-api:Resource .
          |
          |    ?book incunabula:title ?title .
          |    incunabula:title knora-api:objectType xsd:string .
          |
          |    ?title a xsd:string .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |    incunabula:pubdate knora-api:objectType knora-api:Date .
          |
          |    ?pubdate a knora-api:Date .
          |
          |     FILTER(?pubdate < "JULIAN:1497-03-01"^^knora-api:Date || ?pubdate > "JULIAN:1497-03-01"^^knora-api:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksNotPublishedOnDate.jsonld")
    },
    test("perform a Gravsearch query for books that have been published before 1497 (Julian Calendar)") {
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 4 (number of results from query below)
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |    ?book a knora-api:Resource .
          |
          |    ?book incunabula:title ?title .
          |    incunabula:title knora-api:objectType xsd:string .
          |
          |    ?title a xsd:string .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |    incunabula:pubdate knora-api:objectType knora-api:Date .
          |
          |    ?pubdate a knora-api:Date .
          |    FILTER(?pubdate < "JULIAN:1497"^^knora-api:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksPublishedBeforeDate.jsonld")
    },
    test("perform a Gravsearch query for books that have been published 1497 or later (Julian Calendar)") {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |    ?book a knora-api:Resource .
          |
          |    ?book incunabula:title ?title .
          |    incunabula:title knora-api:objectType xsd:string .
          |
          |    ?title a xsd:string .
          |
          |    ?book incunabula:pubdate ?pubdate .
          |    incunabula:pubdate knora-api:objectType knora-api:Date .
          |
          |    ?pubdate a knora-api:Date .
          |    FILTER(?pubdate >= "JULIAN:1497"^^knora-api:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksPublishedAfterOrOnDate.jsonld")
    },
  )
}
