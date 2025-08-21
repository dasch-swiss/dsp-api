/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import sttp.client4.Response
import sttp.client4.UriContext
import zio.*
import zio.test.*

import org.knora.webapi.E2EZSpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.RdfModel
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.User
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

  private def verifyQueryResult(query: String, expectedFile: String, f: RequestUpdate[String] = identity) =
    TestApiClient.postSparql(uri"/v2/searchextended", query, f = f).flatMap(compare(_, expectedFile))

  private def verifyQueryResult(query: String, expectedFile: String, user: User) =
    TestApiClient.postSparql(uri"/v2/searchextended", query, Some(user)).flatMap(compare(_, expectedFile))

  private def compare(response: Response[Either[String, String]], expectedFile: String) =
    for {
      resultJsonLd <- response.assert200
      actual       <- ZIO.attempt(RdfModel.fromJsonLD(resultJsonLd))
      expected     <- loadFile(expectedFile).mapAttempt(RdfModel.fromJsonLD)
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
    test("perform a Gravsearch query for books that have been published 1497 or before (Julian Calendar)") {
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
          |    FILTER(?pubdate <= "JULIAN:1497"^^knora-api:Date)
          |
          |} ORDER BY ?pubdate
                """.stripMargin
      verifyQueryResult(query, "BooksPublishedBeforeOrOnDate.jsonld")
    },
    test("perform a Gravsearch query for books that have been published after 1486 and before 1491 (Julian Calendar") {
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
          |    FILTER(?pubdate > "JULIAN:1486"^^knora-api:Date && ?pubdate < "JULIAN:1491"^^knora-api:Date)
          |
          |} ORDER BY ?pubdate
          |""".stripMargin
      verifyQueryResult(query, "BooksPublishedBetweenDates.jsonld")
    },
    test("get the regions belonging to a page") {
      val query =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?region knora-api:isMainResource true .
          |
          |    ?region knora-api:isRegionOf <http://rdfh.ch/0803/9d626dc76c03> .
          |
          |    ?region knora-api:hasGeometry ?geom .
          |
          |    ?region knora-api:hasComment ?comment .
          |
          |    ?region knora-api:hasColor ?color .
          |} WHERE {
          |
          |    ?region a knora-api:Region .
          |    ?region a knora-api:Resource .
          |
          |    ?region knora-api:isRegionOf <http://rdfh.ch/0803/9d626dc76c03> .
          |    knora-api:isRegionOf knora-api:objectType knora-api:Resource .
          |
          |    <http://rdfh.ch/0803/9d626dc76c03> a knora-api:Resource .
          |
          |    ?region knora-api:hasGeometry ?geom .
          |    knora-api:hasGeometry knora-api:objectType knora-api:Geom .
          |
          |    ?geom a knora-api:Geom .
          |
          |    ?region knora-api:hasComment ?comment .
          |    knora-api:hasComment knora-api:objectType xsd:string .
          |
          |    ?comment a xsd:string .
          |
          |    ?region knora-api:hasColor ?color .
          |    knora-api:hasColor knora-api:objectType knora-api:Color .
          |
          |    ?color a knora-api:Color .
          |
          |}
                """.stripMargin
      verifyQueryResult(query, "RegionsForPage.jsonld")
    },
    test(
      "get a book a page points to and only include the page's partOf link in the results (none of the other properties)",
    ) {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
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
          |    ?book a knora-api:Resource .
          |
          |    ?book incunabula:title ?title .
          |
          |    incunabula:title knora-api:objectType xsd:string .
          |
          |    ?title a xsd:string .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:isPartOf ?book .
          |    knora-api:isPartOf knora-api:objectType knora-api:Resource .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> a knora-api:Resource .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:seqnum ?seqnum .
          |    knora-api:seqnum knora-api:objectType xsd:integer .
          |
          |    ?seqnum a xsd:integer .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:hasStillImageFile ?file .
          |    knora-api:hasStillImageFile knora-api:objectType knora-api:File .
          |
          |    ?file a knora-api:File .
          |
          |} OFFSET 0
          |""".stripMargin
      verifyQueryResult(query, "bookWithIncomingPagesOnlyLink.jsonld")
    },
    test("get incoming links pointing to an incunbaula:book, excluding isPartOf and isRegionOf") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |
          |     ?incomingRes knora-api:isMainResource true .
          |
          |     ?incomingRes ?incomingProp <http://rdfh.ch/0803/8be1b7cf7103> .
          |
          |} WHERE {
          |
          |     ?incomingRes a knora-api:Resource .
          |
          |     ?incomingRes ?incomingProp <http://rdfh.ch/0803/8be1b7cf7103> .
          |
          |     <http://rdfh.ch/0803/8be1b7cf7103> a knora-api:Resource .
          |
          |     ?incomingProp knora-api:objectType knora-api:Resource .
          |
          |     knora-api:isRegionOf knora-api:objectType knora-api:Resource .
          |     knora-api:isPartOf knora-api:objectType knora-api:Resource .
          |
          |     FILTER NOT EXISTS {
          |         ?incomingRes  knora-api:isRegionOf <http://rdfh.ch/0803/8be1b7cf7103> .
          |     }
          |
          |     FILTER NOT EXISTS {
          |         ?incomingRes  knora-api:isPartOf <http://rdfh.ch/0803/8be1b7cf7103> .
          |     }
          |
          |} OFFSET 0
          |""".stripMargin
      verifyQueryResult(query, "IncomingLinksForBook.jsonld")
    },
    test("search for an anything:Thing that has a decimal value of 2.1 2") {
      val query =
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
          |     ?thing anything:hasDecimal ?decimal .
          |     anything:hasDecimal knora-api:objectType xsd:decimal .
          |
          |     ?decimal a xsd:decimal .
          |
          |     FILTER(?decimal = "2.1"^^xsd:decimal)
          |}
          |""".stripMargin
      verifyQueryResult(query, "ThingEqualsDecimal.jsonld", anythingUser1)
    },
    test("search for an anything:Thing that has a decimal value bigger than 2.0") {
      val query =
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
          |     ?thing anything:hasDecimal ?decimal .
          |     anything:hasDecimal knora-api:objectType xsd:decimal .
          |
          |     ?decimal a xsd:decimal .
          |
          |     FILTER(?decimal > "2"^^xsd:decimal)
          |}
          |""".stripMargin
      verifyQueryResult(query, "ThingBiggerThanDecimal.jsonld", anythingUser1)
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
    test("search for an anything:Thing that has a decimal value smaller than 3.0") {
      val query =
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
          |     ?thing anything:hasDecimal ?decimal .
          |     anything:hasDecimal knora-api:objectType xsd:decimal .
          |
          |     ?decimal a xsd:decimal .
          |
          |     FILTER(?decimal < "3"^^xsd:decimal)
          |}
          |""".stripMargin
      verifyQueryResult(query, "ThingSmallerThanDecimal.jsonld", anythingUser1)
    },
    test("search for an anything:Thing that has a specific URI value") {
      val query =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasUri ?uri .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |     ?thing a knora-api:Resource .
          |
          |     ?thing anything:hasUri ?uri .
          |
          |     FILTER(?uri = "http://www.google.ch"^^xsd:anyURI)
          |}
          |""".stripMargin
      verifyQueryResult(query, "thingWithURI.jsonld", anythingUser1)
    },
    test("search for an anything:Thing that has a Boolean value that is true") {
      val query =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |     ?thing a knora-api:Resource .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |     anything:hasBoolean knora-api:objectType xsd:boolean .
          |
          |     ?boolean a xsd:boolean .
          |
          |     FILTER(?boolean = true)
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "ThingWithBoolean.jsonld", anythingUser1)
    },
    test("search for an anything:Thing that may have a Boolean value that is true") {
      val query =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |     ?thing a knora-api:Resource .
          |
          |     OPTIONAL {
          |
          |         ?thing anything:hasBoolean ?boolean .
          |         anything:hasBoolean knora-api:objectType xsd:boolean .
          |
          |         ?boolean a xsd:boolean .
          |
          |         FILTER(?boolean = true)
          |     }
          |
          |     MINUS {
          |         ?thing anything:hasInteger ?intVal .
          |         anything:hasInteger knora-api:objectType xsd:integer .
          |         ?intVal a xsd:integer .
          |         FILTER(?intVal = 123454321 || ?intVal = 999999999)
          |     }
          |} OFFSET 0
          |""".stripMargin
      verifyQueryResult(query, "ThingWithBooleanOptionalOffset0.jsonld", anythingUser1)
    },
    test("search for an anything:Thing that may have a Boolean value that is true using an increased offset") {
      // set OFFSET to 1 to get "Testding for extended search"
      val query =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasBoolean ?boolean .
          |} WHERE {
          |
          |     ?thing a anything:Thing .
          |     ?thing a knora-api:Resource .
          |
          |     OPTIONAL {
          |
          |         ?thing anything:hasBoolean ?boolean .
          |         anything:hasBoolean knora-api:objectType xsd:boolean .
          |
          |         ?boolean a xsd:boolean .
          |
          |         FILTER(?boolean = true)
          |     }
          |
          |     MINUS {
          |         ?thing anything:hasInteger ?intVal .
          |         anything:hasInteger knora-api:objectType xsd:integer .
          |         ?intVal a xsd:integer .
          |         FILTER(?intVal = 123454321 || ?intVal = 999999999)
          |     }
          |} OFFSET 1
          |
                """.stripMargin
      verifyQueryResult(query, "ThingWithBooleanOptionalOffset1.jsonld", anythingUser1)
    },
    test(
      "search for an anything:Thing that either has a Boolean value that is true or a decimal value that equals 2.1 (or both)",
    ) {
      val query =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |     ?thing a knora-api:Resource .
          |
          |     {
          |         ?thing anything:hasBoolean ?boolean .
          |         anything:hasBoolean knora-api:objectType xsd:boolean .
          |
          |         ?boolean a xsd:boolean .
          |
          |         FILTER(?boolean = true)
          |     } UNION {
          |         ?thing anything:hasDecimal ?decimal .
          |         anything:hasDecimal knora-api:objectType xsd:decimal .
          |
          |         ?decimal a xsd:decimal .
          |
          |         FILTER(?decimal = "2.1"^^xsd:decimal)
          |     }
          |
          |} OFFSET 0
          |""".stripMargin
      verifyQueryResult(query, "ThingWithBooleanOrDecimal.jsonld", anythingUser1)
    },
    test("search for a book whose title contains 'Zeitglöcklein' using the match function") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |CONSTRUCT {
          |
          |   ?mainRes knora-api:isMainResource true .
          |
          |   ?mainRes <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title> ?propVal0 .
          |
          |} WHERE {
          |
          |   ?mainRes a knora-api:Resource .
          |
          |   ?mainRes a <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book> .
          |
          |   ?mainRes <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title> ?propVal0 .
          |   <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title> knora-api:objectType <http://www.w3.org/2001/XMLSchema#string> .
          |   ?propVal0 a <http://www.w3.org/2001/XMLSchema#string> .
          |
          |   FILTER knora-api:matchText(?propVal0, "Zeitglöcklein")
          |
          |}""".stripMargin
      verifyQueryResult(query, "BooksWithTitleContainingZeitgloecklein.jsonld", anythingUser1)
    },
    test("search for a book whose title contains 'Zeitglöcklein' and 'Lebens' using the match function") {
      val query =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |CONSTRUCT {
          |
          |   ?mainRes knora-api:isMainResource true .
          |
          |   ?mainRes <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title> ?propVal0 .
          |
          |} WHERE {
          |
          |   ?mainRes a knora-api:Resource .
          |
          |   ?mainRes a <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#book> .
          |
          |   ?mainRes <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title> ?propVal0 .
          |   <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title> knora-api:objectType <http://www.w3.org/2001/XMLSchema#string> .
          |   ?propVal0 a <http://www.w3.org/2001/XMLSchema#string> .
          |
          |   FILTER knora-api:matchText(?propVal0, "Zeitglöcklein AND Lebens")
          |
          |}
          |""".stripMargin
      verifyQueryResult(query, "BooksWithTitleContainingZeitgloecklein.jsonld", anythingUser1)
    },
  )
}
