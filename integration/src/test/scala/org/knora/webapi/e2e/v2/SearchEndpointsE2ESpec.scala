/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v2

import org.apache.pekko
import org.xmlunit.builder.DiffBuilder
import org.xmlunit.builder.Input
import org.xmlunit.diff.Diff
import zio.ZIO

import java.net.URLEncoder
import java.nio.file.Paths
import scala.concurrent.ExecutionContextExecutor

import dsp.errors.BadRequestException
import dsp.valueobjects.Iri
import org.knora.webapi.*
import org.knora.webapi.e2e.v2.ResponseCheckerV2.*
import org.knora.webapi.messages.IriConversions.*
import org.knora.webapi.messages.OntologyConstants
import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.messages.util.rdf.JsonLDKeywords
import org.knora.webapi.routing.UnsafeZioRun
import org.knora.webapi.sharedtestdata.SharedTestDataADM
import org.knora.webapi.testservices.TestClientService
import org.knora.webapi.util.FileUtil
import org.knora.webapi.util.MutableTestIri

import pekko.http.javadsl.model.StatusCodes
import pekko.http.scaladsl.model.ContentTypes
import pekko.http.scaladsl.model.HttpEntity
import pekko.http.scaladsl.model.Multipart
import pekko.http.scaladsl.model.headers.BasicHttpCredentials

/**
 * End-to-end test specification for the search endpoint. This specification uses the Spray Testkit as documented
 * here: http://spray.io/documentation/1.2.2/spray-testkit/
 */
class SearchEndpointsE2ESpec extends E2ESpec {
  private implicit val stringFormatter: StringFormatter = StringFormatter.getGeneralInstance
  implicit val ec: ExecutionContextExecutor             = system.dispatcher

  private val anythingUser       = SharedTestDataADM.anythingUser1
  private val anythingUserEmail  = anythingUser.email
  private val anythingProjectIri = SharedTestDataADM.anythingProjectIri

  private val incunabulaUser      = SharedTestDataADM.incunabulaMemberUser
  private val incunabulaUserEmail = incunabulaUser.email

  private val password = SharedTestDataADM.testPass

  private val hamletResourceIri  = new MutableTestIri
  private val timeTagResourceIri = new MutableTestIri

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

  private def testData(filename: String): String = readTestData(Paths.get("searchR2RV2", filename))

  "The Search v2 Endpoint" should {
    "perform a fulltext search for 'Narr'" in {
      val actual   = getResponseAsString(Get(s"$baseApiUrl/v2/search/Narr"))
      val expected = testData("NarrFulltextSearch.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a count query for a fulltext search for 'Narr'" in {
      val actual = getResponseAsString(Get(s"$baseApiUrl/v2/search/count/Narr"))
      checkCountResponse(actual, 136)
    }

    "perform a fulltext search for 'Ding'" in {
      val actual = getResponseAsString(Get(s"$baseApiUrl/v2/search/Ding"))
      // the response involves forbidden resource
      val expected = testData("searchResponseWithHiddenResource.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a fulltext search for 'Dinge' (in the complex schema)" in {
      val actual = getResponseAsString(
        Get(s"$baseApiUrl/v2/search/Dinge") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("DingeFulltextSearch.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a fulltext search for 'Dinge' (in the simple schema)" in {
      val actual = getResponseAsString(
        Get(s"$baseApiUrl/v2/search/Dinge").addHeader(SchemaHeader.simple) ~>
          addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("DingeFulltextSearchSimple.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a count query for a fulltext search for 'Dinge'" in {
      val actual = getResponseAsString(
        Get(s"$baseApiUrl/v2/search/count/Dinge") ~>
          addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      checkCountResponse(actual, 1)
    }

    "perform a fulltext query for a search value containing a single character wildcard" in {
      val actual = getResponseAsString(
        Get(s"$baseApiUrl/v2/search/Unif%3Frm") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingUniform.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a fulltext query for a search value containing a multiple character wildcard" in {
      val actual = getResponseAsString(
        Get(s"$baseApiUrl/v2/search/Unif*m") ~>
          addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingUniform.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "return files attached to full-text search results" in {
      val actual   = getResponseAsString(Get(s"$baseApiUrl/v2/search/p7v?returnFiles=true"))
      val expected = testData("FulltextSearchWithImage.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "not accept a fulltext query containing http://api.knora.org" in {
      val invalidSearchString: String =
        URLEncoder.encode("PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>", "UTF-8")

      val actual      = singleAwaitingRequest(Get(s"$baseApiUrl/v2/search/$invalidSearchString"))
      val responseStr = responseToString(actual)

      assert(actual.status == StatusCodes.BAD_REQUEST)
      assert(responseStr.contains("It looks like you are submitting a Gravsearch request to a full-text search route"))
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Queries without type inference

    "perform a Gravsearch query using simple schema which allows to sort the results by external link" in {
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
          |ORDER BY (?exLink)
          |""".stripMargin

      val response = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, query),
        ),
      )
      assert(response.status == StatusCodes.OK)
    }

    "perform a Gravsearch query using complex schema which allows to sort the results by external link" in {
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
          |ORDER BY (?exLink)
          |""".stripMargin

      val response = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, query),
        ),
      )
      assert(response.status == StatusCodes.OK)
    }

    "perform a Gravsearch query for an anything:Thing with an optional date and sort by date" in {
      val gravsearchQuery =
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
          |ORDER BY DESC(?date)
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("thingWithOptionalDateSortedDesc.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch count query for an anything:Thing with an optional date used as a sort criterion" in {
      val gravsearchQuery =
        """PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |  ?thing anything:hasDate ?date .
          |} WHERE {
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
          |ORDER BY DESC(?date)
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended/count",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      checkCountResponse(actual, 44)
    }

    "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer (in the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that have the dcterms:title 'Zeitglöcklein des Lebens' returning the title in the answer (in the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX dcterms: <http://purl.org/dc/terms/>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book dcterms:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book dcterms:title ?title .
          |        dcterms:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }""".stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer (in the simple schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery))
          .addHeader(SchemaHeader.simple),
      )
      val expected = testData("ZeitgloeckleinExtendedSearchWithTitleInAnswerSimple.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that have the dcterms:title 'Zeitglöcklein des Lebens' returning the title in the answer (in the simple schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX dcterms: <http://purl.org/dc/terms/>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book dcterms:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book dcterms:title ?title .
          |        dcterms:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery))
          .addHeader(SchemaHeader.simple),
      )
      val expected = testData("ZeitgloeckleinExtendedSearchWithTitleInAnswerSimple.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch count query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended/count",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      checkCountResponse(actual, 2)
    }

    "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' not returning the title in the answer" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("ZeitgloeckleinExtendedSearchNoTitleInAnswer.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that do not have the title 'Zeitglöcklein des Lebens'" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        FILTER(?title != "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("NotZeitgloeckleinExtendedSearch.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch count query for books that do not have the title 'Zeitglöcklein des Lebens'" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        FILTER(?title != "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended/count",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      // 19 - 2 = 18 :-)
      // there is a total of 19 incunabula books of which two have the title "Zeitglöcklein des Lebens und Leidens Christi" (see test above)
      // however, there are 18 books that have a title that is not "Zeitglöcklein des Lebens und Leidens Christi"
      // this is because there is a book that has two titles, one "Zeitglöcklein des Lebens und Leidens Christi" and the other in Latin "Horologium devotionis circa vitam Christi"
      checkCountResponse(actual, 18)
    }

    "perform a Gravsearch query for the page of a book whose seqnum equals 10, returning the seqnum and the link value" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |        ?page a knora-api:Resource .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
          |
          |        <http://rdfh.ch/0803/b6b5ff1eb703> a knora-api:Resource .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |        incunabula:seqnum knora-api:objectType xsd:integer .
          |
          |        FILTER(?seqnum = 10)
          |
          |        ?seqnum a xsd:integer .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("PageWithSeqnum10WithSeqnumAndLinkValueInAnswer.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for the page of a book whose seqnum equals 10, returning only the seqnum" in {

      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |        ?page a knora-api:Resource .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
          |
          |        <http://rdfh.ch/0803/b6b5ff1eb703> a knora-api:Resource .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |        incunabula:seqnum knora-api:objectType xsd:integer .
          |
          |        FILTER(?seqnum = 10)
          |
          |        ?seqnum a xsd:integer .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("PageWithSeqnum10OnlySeqnuminAnswer.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for the pages of a book whose seqnum is lower than or equals 10" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |        ?page a knora-api:Resource .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
          |
          |        <http://rdfh.ch/0803/b6b5ff1eb703> a knora-api:Resource .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |        incunabula:seqnum knora-api:objectType xsd:integer .
          |
          |        FILTER(?seqnum <= 10)
          |
          |        ?seqnum a xsd:integer .
          |
          |    } ORDER BY ?seqnum
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("pagesOfLatinNarrenschiffWithSeqnumLowerEquals10.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |        ?page a knora-api:Resource .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
          |
          |        <http://rdfh.ch/0803/b6b5ff1eb703> a knora-api:Resource .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |        incunabula:seqnum knora-api:objectType xsd:integer .
          |
          |        ?seqnum a xsd:integer .
          |
          |    } ORDER BY ?seqnum
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("PagesOfNarrenschiffOrderedBySeqnum.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum and get the next OFFSET" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |        ?page a knora-api:Resource .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
          |
          |        <http://rdfh.ch/0803/b6b5ff1eb703> a knora-api:Resource .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |        incunabula:seqnum knora-api:objectType xsd:integer .
          |
          |        ?seqnum a xsd:integer .
          |
          |    } ORDER BY ?seqnum
          |    OFFSET 1
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("PagesOfNarrenschiffOrderedBySeqnumNextOffset.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that have been published on the first of March 1497 (Julian Calendar) (2)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |        incunabula:pubdate knora-api:objectType knora-api:Date .
          |
          |        ?pubdate a knora-api:Date .
          |
          |        FILTER(?pubdate = "JULIAN:1497-03-01"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksPublishedOnDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that have not been published on the first of March 1497 (Julian Calendar)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |        incunabula:pubdate knora-api:objectType knora-api:Date .
          |
          |        ?pubdate a knora-api:Date .
          |
          |        FILTER(?pubdate != "JULIAN:1497-03-01"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin

      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksNotPublishedOnDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
      checkSearchResponseNumberOfResults(actual, 17)
    }

    "perform a Gravsearch query for books that have not been published on the first of March 1497 (Julian Calendar) 2" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |        incunabula:pubdate knora-api:objectType knora-api:Date .
          |
          |        ?pubdate a knora-api:Date .
          |
          |         FILTER(?pubdate < "JULIAN:1497-03-01"^^knora-api:Date || ?pubdate > "JULIAN:1497-03-01"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksNotPublishedOnDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
      checkSearchResponseNumberOfResults(actual, 17)
    }

    "perform a Gravsearch query for books that have been published before 1497 (Julian Calendar)" in {
      val gravsearchQuery =
        """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |        incunabula:pubdate knora-api:objectType knora-api:Date .
          |
          |        ?pubdate a knora-api:Date .
          |        FILTER(?pubdate < "JULIAN:1497"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksPublishedBeforeDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 4 (number of results from query below)
      checkSearchResponseNumberOfResults(actual, 15)
    }

    "perform a Gravsearch query for books that have been published 1497 or later (Julian Calendar)" in {
      val gravsearchQuery =
        """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |        incunabula:pubdate knora-api:objectType knora-api:Date .
          |
          |        ?pubdate a knora-api:Date .
          |        FILTER(?pubdate >= "JULIAN:1497"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksPublishedAfterOrOnDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 15 (number of results from query above)
      checkSearchResponseNumberOfResults(actual, 4)
    }

    "perform a Gravsearch query for books that have been published after 1497 (Julian Calendar)" in {
      val gravsearchQuery =
        """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |        incunabula:pubdate knora-api:objectType knora-api:Date .
          |
          |        ?pubdate a knora-api:Date .
          |        FILTER(?pubdate > "JULIAN:1497"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksPublishedAfterDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 18 (number of results from query above)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "perform a Gravsearch query for books that have been published 1497 or before (Julian Calendar)" in {
      val gravsearchQuery =
        """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |        incunabula:pubdate knora-api:objectType knora-api:Date .
          |
          |        ?pubdate a knora-api:Date .
          |        FILTER(?pubdate <= "JULIAN:1497"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksPublishedBeforeOrOnDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 1 (number of results from query above)
      checkSearchResponseNumberOfResults(actual, 18)
    }

    "perform a Gravsearch query for books that have been published after 1486 and before 1491 (Julian Calendar)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |        incunabula:pubdate knora-api:objectType knora-api:Date .
          |
          |        ?pubdate a knora-api:Date .
          |
          |        FILTER(?pubdate > "JULIAN:1486"^^knora-api:Date && ?pubdate < "JULIAN:1491"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksPublishedBetweenDates.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 5)
    }

    "get the regions belonging to a page" in {
      val gravsearchQuery =
        """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?region knora-api:isMainResource true .
          |
          |        ?region knora-api:isRegionOf <http://rdfh.ch/0803/9d626dc76c03> .
          |
          |        ?region knora-api:hasGeometry ?geom .
          |
          |        ?region knora-api:hasComment ?comment .
          |
          |        ?region knora-api:hasColor ?color .
          |    } WHERE {
          |
          |        ?region a knora-api:Region .
          |        ?region a knora-api:Resource .
          |
          |        ?region knora-api:isRegionOf <http://rdfh.ch/0803/9d626dc76c03> .
          |        knora-api:isRegionOf knora-api:objectType knora-api:Resource .
          |
          |        <http://rdfh.ch/0803/9d626dc76c03> a knora-api:Resource .
          |
          |        ?region knora-api:hasGeometry ?geom .
          |        knora-api:hasGeometry knora-api:objectType knora-api:Geom .
          |
          |        ?geom a knora-api:Geom .
          |
          |        ?region knora-api:hasComment ?comment .
          |        knora-api:hasComment knora-api:objectType xsd:string .
          |
          |        ?comment a xsd:string .
          |
          |        ?region knora-api:hasColor ?color .
          |        knora-api:hasColor knora-api:objectType knora-api:Color .
          |
          |        ?color a knora-api:Color .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("RegionsForPage.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "get a book a page points to and include the page in the results (all properties present in WHERE clause)" in {
      val gravsearchQuery =
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
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:seqnum ?seqnum .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:hasStillImageFile ?file .
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("bookWithIncomingPagesWithAllRequestedProps.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "get a book a page points to and only include the page's partOf link in the results (none of the other properties)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("bookWithIncomingPagesOnlyLink.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "get incoming links pointing to an incunbaula:book, excluding isPartOf and isRegionOf" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("IncomingLinksForBook.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an anything:Thing that has a decimal value of 2.1 2" in {
      val gravsearchQuery =
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
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingEqualsDecimal.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "search for an anything:Thing that has a decimal value bigger than 2.0" in {
      val gravsearchQuery =
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
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingBiggerThanDecimal.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "search for an anything:Thing that has a decimal value smaller than 3.0" in {
      val gravsearchQuery =
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
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingSmallerThanDecimal.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for an anything:Thing that has a specific URI value" in {
      val gravsearchQuery =
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
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("thingWithURI.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "search for an anything:Thing that has a Boolean value that is true" in {
      val gravsearchQuery =
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
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithBoolean.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for an anything:Thing that may have a Boolean value that is true" in {
      val gravsearchQuery =
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
          |} OFFSET 0""".stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithBooleanOptionalOffset0.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the first page of results
      checkSearchResponseNumberOfResults(actual, 25)
    }

    "search for an anything:Thing that may have a Boolean value that is true using an increased offset" in {
      // set OFFSET to 1 to get "Testding for extended search"
      val gravsearchQuery =
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
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithBooleanOptionalOffset1.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the second page of results
      checkSearchResponseNumberOfResults(actual, 19)
    }

    "search for an anything:Thing that either has a Boolean value that is true or a decimal value that equals 2.1 (or both)" in {
      val gravsearchQuery =
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
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithBooleanOrDecimal.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for a book whose title contains 'Zeit' using the regex function" in {
      val gravsearchQuery =
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
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("BooksWithTitleContainingZeit.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for a book whose title contains 'Zeitglöcklein' using the match function" in {
      val gravsearchQuery =
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
          |        ?mainRes <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title> ?propVal0 .
          |        <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title> knora-api:objectType <http://www.w3.org/2001/XMLSchema#string> .
          |        ?propVal0 a <http://www.w3.org/2001/XMLSchema#string> .
          |
          |        FILTER knora-api:matchText(?propVal0, "Zeitglöcklein")
          |
          |     }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("BooksWithTitleContainingZeitgloecklein.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for a book whose title contains 'Zeitglöcklein' and 'Lebens' using the match function" in {
      val gravsearchQuery =
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
          |        ?mainRes <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title> ?propVal0 .
          |        <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#title> knora-api:objectType <http://www.w3.org/2001/XMLSchema#string> .
          |        ?propVal0 a <http://www.w3.org/2001/XMLSchema#string> .
          |
          |        FILTER knora-api:matchText(?propVal0, "Zeitglöcklein AND Lebens")
          |
          |     }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("BooksWithTitleContainingZeitgloecklein.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for 'Zeitglöcklein des Lebens' using dcterms:title" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |    PREFIX dcterms: <http://purl.org/dc/terms/>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book dcterms:title ?title .
          |
          |    } WHERE {
          |        ?book a knora-api:Resource .
          |
          |        ?book dcterms:title ?title .
          |
          |        dcterms:title knora-api:objectType xsd:string .
          |
          |        ?title a xsd:string .
          |
          |        FILTER(?title = 'Zeitglöcklein des Lebens und Leidens Christi')
          |
          |    } OFFSET 0
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("BooksWithTitleContainingZeitgloecklein.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for an anything:Thing with a list value" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?thing knora-api:isMainResource true .
          |
          |        ?thing anything:hasListItem ?listItem .
          |
          |    } WHERE {
          |        ?thing a knora-api:Resource .
          |
          |        ?thing anything:hasListItem ?listItem .
          |
          |        anything:hasListItem knora-api:objectType knora-api:ListNode .
          |
          |        ?listItem a knora-api:ListNode .
          |
          |    } OFFSET 0
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithListValue.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 3)
    }

    "search for a text using the lang function" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasText ?text .
          |} WHERE {
          |     ?thing a knora-api:Resource .
          |
          |     ?thing a anything:Thing .
          |
          |     ?thing anything:hasText ?text .
          |
          |     anything:hasText knora-api:objectType xsd:string .
          |
          |     ?text a xsd:string .
          |
          |     FILTER(lang(?text) = "fr")
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("LanguageFulltextSearch.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "search for a specific text using the lang function" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true .
          |
          |     ?thing anything:hasText ?text .
          |} WHERE {
          |     ?thing a knora-api:Resource .
          |
          |     ?thing a anything:Thing .
          |
          |     ?thing anything:hasText ?text .
          |
          |     anything:hasText knora-api:objectType xsd:string .
          |
          |     ?text a xsd:string .
          |
          |     FILTER(lang(?text) = "fr" && ?text = "Bonjour")
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("LanguageFulltextSearch.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "perform a fulltext search for 'Bonjour'" in {
      val actual = getResponseAsString(
        Get(s"$baseApiUrl/v2/search/Bonjour") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("LanguageFulltextSearch.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a fulltext search for the term 'text' marked up as a paragraph" in {
      val actual = getResponseAsString(
        Get(
          s"$baseApiUrl/v2/search/text?limitToStandoffClass=" + URLEncoder
            .encode("http://api.knora.org/ontology/standoff/v2#StandoffParagraphTag", "UTF-8"),
        ),
      )
      val expected = testData("ThingWithRichtextWithTermTextInParagraph.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a fulltext search count query for the term 'text' marked up as a paragraph" in {
      val actual = getResponseAsString(
        Get(
          s"$baseApiUrl/v2/search/count/text?limitToStandoffClass=" + URLEncoder
            .encode("http://api.knora.org/ontology/standoff/v2#StandoffParagraphTag", "UTF-8"),
        ),
      )
      checkCountResponse(actual, 1)
    }

    "do a fulltext search for the term 'text' marked up as italic" in {
      val actual = getResponseAsString(
        Get(
          s"$baseApiUrl/v2/search/text?limitToStandoffClass=" + URLEncoder
            .encode("http://api.knora.org/ontology/standoff/v2#StandoffItalicTag", "UTF-8"),
        ),
      )
      val expected = testData("ThingWithRichtextWithTermTextInParagraph.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a fulltext search count query for the term 'text' marked up as italic" in {
      val actual = getResponseAsString(
        Get(
          s"$baseApiUrl/v2/search/count/text?limitToStandoffClass=" + URLEncoder
            .encode("http://api.knora.org/ontology/standoff/v2#StandoffItalicTag", "UTF-8"),
        ),
      )
      checkCountResponse(actual, 1)
    }

    "do a fulltext search for the terms 'interesting' and 'text' marked up as italic" in {
      val actual = getResponseAsString(
        Get(
          s"$baseApiUrl/v2/search/interesting%20text?limitToStandoffClass=" + URLEncoder
            .encode("http://api.knora.org/ontology/standoff/v2#StandoffItalicTag", "UTF-8"),
        ),
      )
      val expected = testData("ThingWithRichtextWithTermTextInParagraph.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a fulltext search count query for the terms 'interesting' and 'text' marked up as italic" in {
      val actual = getResponseAsString(
        Get(
          s"$baseApiUrl/v2/search/interesting%20text?limitToStandoffClass=" + URLEncoder
            .encode("http://api.knora.org/ontology/standoff/v2#StandoffItalicTag", "UTF-8"),
        ),
      )
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a fulltext search for the terms 'interesting' and 'boring' marked up as italic" in {
      val actual = getResponseAsString(
        Get(
          s"$baseApiUrl/v2/search/interesting%20boring?limitToStandoffClass=" + URLEncoder
            .encode("http://api.knora.org/ontology/standoff/v2#StandoffItalicTag", "UTF-8"),
        ),
      )
      // there is no single italic element that contains both 'interesting' and 'boring':
      checkSearchResponseNumberOfResults(actual, 0)
    }

    "do a Gravsearch query for link objects that link to an incunabula book" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |
          |CONSTRUCT {
          |     ?linkObj knora-api:isMainResource true .
          |
          |     ?linkObj knora-api:hasLinkTo ?book .
          |
          |} WHERE {
          |     ?linkObj a knora-api:Resource .
          |     ?linkObj a knora-api:LinkObj .
          |
          |     ?linkObj knora-api:hasLinkTo ?book .
          |     knora-api:hasLinkTo knora-api:objectType knora-api:Resource .
          |
          |     ?book a knora-api:Resource .
          |     ?book a incunabula:book .
          |
          |     ?book incunabula:title ?title .
          |
          |     incunabula:title knora-api:objectType xsd:string .
          |
          |     ?title a xsd:string .
          |
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("LinkObjectsToBooks.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 3)
    }

    "do a Gravsearch query for a letter that links to a specific person via two possible properties" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?letter knora-api:isMainResource true .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1  <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> .
          |
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
          |        # testperson2
          |        ?letter ?linkingProp1 <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> .
          |
          |        <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> a knora-api:Resource .
          |
          |        ?linkingProp1 knora-api:objectType knora-api:Resource .
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        beol:hasAuthor knora-api:objectType knora-api:Resource .
          |        beol:hasRecipient knora-api:objectType knora-api:Resource .
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("letterWithAuthor.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch count query for a letter that links to a specific person via two possible properties" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?letter knora-api:isMainResource true .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1  <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> .
          |
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
          |        # testperson2
          |        ?letter ?linkingProp1 <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> .
          |
          |        <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> a knora-api:Resource .
          |
          |        ?linkingProp1 knora-api:objectType knora-api:Resource .
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        beol:hasAuthor knora-api:objectType knora-api:Resource .
          |        beol:hasRecipient knora-api:objectType knora-api:Resource .
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended/count",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      checkCountResponse(actual, 1)
    }

    "do a Gravsearch query for a letter that links to a person with a specified name" in {
      val gravsearchQuery =
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
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        beol:hasAuthor knora-api:objectType knora-api:Resource .
          |        beol:hasRecipient knora-api:objectType knora-api:Resource .
          |
          |        ?person1 beol:hasFamilyName ?name .
          |
          |        beol:hasFamilyName knora-api:objectType xsd:string .
          |        ?name a xsd:string .
          |
          |        FILTER(?name = "Meier")
          |
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("letterWithPersonWithName.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch count query for a letter that links to a person with a specified name" in {
      val gravsearchQuery =
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
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        beol:hasAuthor knora-api:objectType knora-api:Resource .
          |        beol:hasRecipient knora-api:objectType knora-api:Resource .
          |
          |        ?person1 beol:hasFamilyName ?name .
          |
          |        beol:hasFamilyName knora-api:objectType xsd:string .
          |        ?name a xsd:string .
          |
          |        FILTER(?name = "Meier")
          |
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended/count",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      checkCountResponse(actual, 1)
    }

    "do a Gravsearch query for a letter that links to another person with a specified name" in {
      val gravsearchQuery =
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
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        beol:hasAuthor knora-api:objectType knora-api:Resource .
          |        beol:hasRecipient knora-api:objectType knora-api:Resource .
          |
          |        ?person1 beol:hasFamilyName ?name .
          |
          |        beol:hasFamilyName knora-api:objectType xsd:string .
          |        ?name a xsd:string .
          |
          |        FILTER(?name = "Muster")
          |
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("letterWithPersonWithName2.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "run a Gravsearch query that searches for a person using foaf classes and properties" in {
      val gravsearchQuery =
        """
          |      PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |      PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |      PREFIX foaf: <http://xmlns.com/foaf/0.1/>
          |
          |      CONSTRUCT {
          |          ?person knora-api:isMainResource true .
          |
          |          ?person foaf:familyName ?familyName .
          |
          |          ?person foaf:givenName ?givenName .
          |
          |      } WHERE {
          |          ?person a knora-api:Resource .
          |          ?person a foaf:Person .
          |
          |          ?person foaf:familyName ?familyName .
          |          foaf:familyName knora-api:objectType xsd:string .
          |
          |          ?familyName a xsd:string .
          |
          |          ?person foaf:givenName ?givenName .
          |          foaf:givenName knora-api:objectType xsd:string .
          |
          |          ?givenName a xsd:string .
          |
          |          FILTER(?familyName = "Meier")
          |
          |      }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("foafPerson.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "run a Gravsearch query that searches for a single resource specified by its IRI" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |     ?thing knora-api:isMainResource true ;
          |         anything:hasText ?text ;
          |         anything:hasInteger ?integer .
          |
          |} WHERE {
          |     BIND(<http://rdfh.ch/0001/H6gBWUuJSuuO-CilHV8kQw> AS ?thing)
          |
          |     ?thing a knora-api:Resource .
          |     ?thing a anything:Thing .
          |     ?thing anything:hasText ?text .
          |     anything:hasText knora-api:objectType xsd:string .
          |     ?text a xsd:string .
          |     ?thing anything:hasInteger ?integer .
          |     anything:hasInteger knora-api:objectType xsd:integer .
          |     ?integer a xsd:integer.
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingByIriWithRequestedValues.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a Gravsearch query for a letter and get information about the persons associated with it" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?letter knora-api:isMainResource true .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1 ?person1 .
          |
          |        ?person1 beol:hasFamilyName ?familyName .
          |
          |
          |    } WHERE {
          |        BIND(<http://rdfh.ch/0801/_B3lQa6tSymIq7_7SowBsA> AS ?letter)
          |        ?letter a knora-api:Resource .
          |        ?letter a beol:letter .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        beol:creationDate knora-api:objectType knora-api:Date .
          |        ?date a knora-api:Date .
          |
          |        # testperson2
          |        ?letter ?linkingProp1 ?person1 .
          |
          |        ?person1 a knora-api:Resource .
          |
          |        ?linkingProp1 knora-api:objectType knora-api:Resource .
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        beol:hasAuthor knora-api:objectType knora-api:Resource .
          |        beol:hasRecipient knora-api:objectType knora-api:Resource .
          |
          |        ?person1 beol:hasFamilyName ?familyName .
          |        beol:hasFamilyName knora-api:objectType xsd:string .
          |
          |        ?familyName a xsd:string .
          |
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("letterWithAuthorWithInformation.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a Gravsearch query for the pages of a book whose seqnum is lower than or equals 10, with the book as the main resource" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |        ?book incunabula:title ?title .
          |
          |        ?page knora-api:isPartOf ?book ;
          |            incunabula:seqnum ?seqnum .
          |    } WHERE {
          |        BIND(<http://rdfh.ch/0803/b6b5ff1eb703> AS ?book)
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |        ?title a xsd:string .
          |
          |        ?page a incunabula:page .
          |        ?page a knora-api:Resource .
          |
          |        ?page knora-api:isPartOf ?book .
          |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |        incunabula:seqnum knora-api:objectType xsd:integer .
          |
          |        FILTER(?seqnum <= 10)
          |
          |        ?seqnum a xsd:integer .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("incomingPagesForBook.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "reject a Gravsearch query containing a statement whose subject is not the main resource and whose object is used in ORDER BY" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |        ?book incunabula:title ?title .
          |
          |        ?page knora-api:isPartOf ?book ;
          |            incunabula:seqnum ?seqnum .
          |    } WHERE {
          |        BIND(<http://rdfh.ch/0803/b6b5ff1eb703> AS ?book)
          |        ?book a knora-api:Resource .
          |
          |        ?book incunabula:title ?title .
          |        incunabula:title knora-api:objectType xsd:string .
          |        ?title a xsd:string .
          |
          |        ?page a incunabula:page .
          |        ?page a knora-api:Resource .
          |
          |        ?page knora-api:isPartOf ?book .
          |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |        incunabula:seqnum knora-api:objectType xsd:integer .
          |
          |        FILTER(?seqnum <= 10)
          |
          |        ?seqnum a xsd:integer .
          |
          |    } ORDER BY ?seqnum
                """.stripMargin
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
    }

    "do a Gravsearch query for regions that belong to pages that are part of a book with the title 'Zeitglöcklein des Lebens und Leidens Christi'" in {
      val gravsearchQuery =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |    ?region a knora-api:Resource .
          |	?region a knora-api:Region .
          |
          |	?region knora-api:isRegionOf ?page .
          |
          |    knora-api:isRegionOf knora-api:objectType knora-api:Resource .
          |
          |    ?page a knora-api:Resource .
          |    ?page a incunabula:page .
          |
          |    ?page knora-api:isPartOf ?book .
          |
          |    knora-api:isPartOf knora-api:objectType knora-api:Resource .
          |
          |    ?book a knora-api:Resource .
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    incunabula:title knora-api:objectType xsd:string .
          |
          |    ?title a xsd:string .
          |
          |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("regionsOfZeitgloecklein.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a Gravsearch query containing a UNION nested in an OPTIONAL" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX gravsearchtest1: <http://0.0.0.0:3333/ontology/0666/gravsearchtest1/simple/v2#>
          |
          |CONSTRUCT {
          |  ?Project knora-api:isMainResource true .
          |  ?isInProject gravsearchtest1:isInProject ?Project .
          |} WHERE {
          |  ?Project a knora-api:Resource .
          |  ?Project a gravsearchtest1:Project .
          |
          |  OPTIONAL {
          |    ?isInProject gravsearchtest1:isInProject ?Project .
          |    gravsearchtest1:isInProject knora-api:objectType knora-api:Resource .
          |    ?isInProject a knora-api:Resource .
          |    { ?isInProject a gravsearchtest1:BibliographicNotice . } UNION { ?isInProject a gravsearchtest1:Person . }
          |  }
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("ProjectsWithOptionalPersonOrBiblio.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Queries with type inference

    "do a Gravsearch query in which 'rdf:type knora-api:Resource' is inferred from a more specific rdf:type (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?letter beol:creationDate ?date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |} WHERE {
          |    ?letter a beol:letter .
          |    ?letter beol:creationDate ?date .
          |    beol:creationDate knora-api:objectType knora-api:Date .
          |    ?date a knora-api:Date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> a beol:person .
          |    ?linkingProp1 knora-api:objectType knora-api:Resource .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |    beol:hasFamilyName knora-api:objectType xsd:string .
          |    ?name a xsd:string .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |} ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("letterWithPersonWithName2.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch query in which the object types of property IRIs are inferred (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?letter beol:creationDate ?date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |} WHERE {
          |    ?letter a beol:letter .
          |    ?letter beol:creationDate ?date .
          |    ?date a knora-api:Date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> a beol:person .
          |    ?linkingProp1 knora-api:objectType knora-api:Resource .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |    ?name a xsd:string .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |} ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("letterWithPersonWithName2.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch query in which the types of property objects are inferred from the knora-api:objectType of each property (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?letter beol:creationDate ?date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |} WHERE {
          |    ?letter a beol:letter .
          |    ?letter beol:creationDate ?date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> a beol:person .
          |    ?linkingProp1 knora-api:objectType knora-api:Resource .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |} ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("letterWithPersonWithName2.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch query in which a property's knora-api:objectType is inferred from its object (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?letter beol:creationDate ?date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |} WHERE {
          |    ?letter a beol:letter .
          |    ?letter beol:creationDate ?date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> a beol:person .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |} ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("letterWithPersonWithName2.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch query in which the types of property subjects are inferred from the knora-api:subjectType of each property (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?letter beol:creationDate ?date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |} WHERE {
          |    ?letter beol:creationDate ?date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |    <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> beol:hasFamilyName ?name .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |
          |} ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("letterWithPersonWithName2.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch query in which the knora-api:objectType of a property variable is inferred from a FILTER (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?letter beol:creationDate ?date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |} WHERE {
          |    ?letter beol:creationDate ?date .
          |    ?letter ?linkingProp1 <http://rdfh.ch/0801/H7s3FmuWTkaCXa54eFANOA> .
          |
          |    FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |} ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("letterWithPersonWithoutName.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch query that finds all the books that have a page with seqnum 100, inferring types (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |  FILTER(?seqnum = 100)
          |
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("booksWithPage100.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a Gravsearch query that finds all the letters sent by someone called Meier, ordered by date, inferring types (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |  FILTER(?name = "Meier")
          |
          |} ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("lettersByMeier.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer (in the complex schema) (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |    } WHERE {
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that have the dcterms:title 'Zeitglöcklein des Lebens' returning the title in the answer (in the complex schema) (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX dcterms: <http://purl.org/dc/terms/>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book dcterms:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book dcterms:title ?title .
          |
          |        FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer (in the simple schema) (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery))
          .addHeader(SchemaHeader.simple),
      )
      val expected = testData("ZeitgloeckleinExtendedSearchWithTitleInAnswerSimple.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that have the dcterms:title 'Zeitglöcklein des Lebens' returning the title in the answer (in the simple schema) (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX dcterms: <http://purl.org/dc/terms/>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book dcterms:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book dcterms:title ?title .
          |
          |        FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery))
          .addHeader(SchemaHeader.simple),
      )
      val expected = testData("ZeitgloeckleinExtendedSearchWithTitleInAnswerSimple.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch count query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended/count",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      checkCountResponse(actual, 2)
    }

    "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' not returning the title in the answer (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("ZeitgloeckleinExtendedSearchNoTitleInAnswer.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that do not have the title 'Zeitglöcklein des Lebens' (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        FILTER(?title != "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("NotZeitgloeckleinExtendedSearch.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch count query for books that do not have the title 'Zeitglöcklein des Lebens' (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        FILTER(?title != "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended/count",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      // 19 - 2 = 18 :-)
      // there is a total of 19 incunabula books of which two have the title "Zeitglöcklein des Lebens und Leidens Christi" (see test above)
      // however, there are 18 books that have a title that is not "Zeitglöcklein des Lebens und Leidens Christi"
      // this is because there is a book that has two titles, one "Zeitglöcklein des Lebens und Leidens Christi" and the other in Latin "Horologium devotionis circa vitam Christi"
      checkCountResponse(actual, 18)
    }

    "perform a Gravsearch query for the page of a book whose seqnum equals 10, returning the seqnum and the link value (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |        FILTER(?seqnum = 10)
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("PageWithSeqnum10WithSeqnumAndLinkValueInAnswer.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch count query for the page of a book whose seqnum equals 10, returning the seqnum and the link value (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |        FILTER(?seqnum = 10)
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "perform a Gravsearch query for the page of a book whose seqnum equals 10, returning only the seqnum (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |        FILTER(?seqnum = 10)
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("PageWithSeqnum10OnlySeqnuminAnswer.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for the pages of a book whose seqnum is lower than or equals 10 (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |        FILTER(?seqnum <= 10)
          |
          |    } ORDER BY ?seqnum
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("pagesOfLatinNarrenschiffWithSeqnumLowerEquals10.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |    } ORDER BY ?seqnum
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("PagesOfNarrenschiffOrderedBySeqnum.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum and get the next OFFSET (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |    } ORDER BY ?seqnum
          |    OFFSET 1
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("PagesOfNarrenschiffOrderedBySeqnumNextOffset.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that have been published on the first of March 1497 (Julian Calendar) (2) (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(?pubdate = "JULIAN:1497-03-01"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksPublishedOnDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that have not been published on the first of March 1497 (Julian Calendar) (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(?pubdate != "JULIAN:1497-03-01"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksNotPublishedOnDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
      checkSearchResponseNumberOfResults(actual, 17)
    }

    "perform a Gravsearch query for books that have not been published on the first of March 1497 (Julian Calendar) 2 (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(?pubdate < "JULIAN:1497-03-01"^^knora-api:Date || ?pubdate > "JULIAN:1497-03-01"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksNotPublishedOnDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
      checkSearchResponseNumberOfResults(actual, 17)
    }

    "perform a Gravsearch query for books that have been published before 1497 (Julian Calendar) (with type inference)" in {
      val gravsearchQuery =
        """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(?pubdate < "JULIAN:1497"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksPublishedBeforeDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 4 (number of results from query below)
      checkSearchResponseNumberOfResults(actual, 15)
    }

    "perform a Gravsearch query for books that have been published 1497 or later (Julian Calendar) (with type inference)" in {
      val gravsearchQuery =
        """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(?pubdate >= "JULIAN:1497"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksPublishedAfterOrOnDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 15 (number of results from query above)
      checkSearchResponseNumberOfResults(actual, 4)
    }

    "perform a Gravsearch query for books that have been published after 1497 (Julian Calendar) (with type inference)" in {
      val gravsearchQuery =
        """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(?pubdate > "JULIAN:1497"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksPublishedAfterDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 18 (number of results from query above)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "perform a Gravsearch query for books that have been published 1497 or before (Julian Calendar) (with type inference)" in {
      val gravsearchQuery =
        """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(?pubdate <= "JULIAN:1497"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksPublishedBeforeOrOnDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 1 (number of results from query above)
      checkSearchResponseNumberOfResults(actual, 18)
    }

    "perform a Gravsearch query for books that have been published after 1486 and before 1491 (Julian Calendar) (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(?pubdate > "JULIAN:1486"^^knora-api:Date && ?pubdate < "JULIAN:1491"^^knora-api:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("BooksPublishedBetweenDates.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 5)
    }

    "get the regions belonging to a page (with type inference)" in {
      val gravsearchQuery =
        """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?region knora-api:isMainResource true .
          |
          |        ?region knora-api:isRegionOf <http://rdfh.ch/0803/9d626dc76c03> .
          |
          |        ?region knora-api:hasGeometry ?geom .
          |
          |        ?region knora-api:hasComment ?comment .
          |
          |        ?region knora-api:hasColor ?color .
          |    } WHERE {
          |
          |        ?region a knora-api:Region .
          |
          |        ?region knora-api:isRegionOf <http://rdfh.ch/0803/9d626dc76c03> .
          |
          |        ?region knora-api:hasGeometry ?geom .
          |
          |        ?region knora-api:hasComment ?comment .
          |
          |        ?region knora-api:hasColor ?color .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("RegionsForPage.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "get a book a page points to and include the page in the results (all properties present in WHERE clause) (with type inference)" in {
      val gravsearchQuery =
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
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:seqnum ?seqnum .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:hasStillImageFile ?file .
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
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:hasStillImageFile ?file .
          |
          |} OFFSET 0
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("bookWithIncomingPagesWithAllRequestedProps.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "get a book a page points to and only include the page's partOf link in the results (none of the other properties) (with type inference)" in {
      val gravsearchQuery =
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
          |    ?book a incunabula:book .
          |
          |    ?book incunabula:title ?title .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:isPartOf ?book .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:seqnum ?seqnum .
          |
          |    <http://rdfh.ch/0803/50e7460a7203> knora-api:hasStillImageFile ?file .
          |
          |} OFFSET 0
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("bookWithIncomingPagesOnlyLink.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "get incoming links pointing to an incunbaula:book, excluding isPartOf (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
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
          |     ?incomingRes ?incomingProp <http://rdfh.ch/0803/8be1b7cf7103> .
          |
          |     <http://rdfh.ch/0803/8be1b7cf7103> a incunabula:book .
          |
          |
          |     FILTER NOT EXISTS {
          |         ?incomingRes knora-api:isPartOf <http://rdfh.ch/0803/8be1b7cf7103> .
          |     }
          |
          |} OFFSET 0
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("IncomingLinksForBook.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an anything:Thing that has a decimal value of 2.1 2 (with type inference)" in {
      val gravsearchQuery =
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
          |
          |     ?thing anything:hasDecimal ?decimal .
          |
          |     FILTER(?decimal = "2.1"^^xsd:decimal)
          |}
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingEqualsDecimal.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "search for an anything:Thing that has a decimal value bigger than 2.0 (with type inference)" in {
      val gravsearchQuery =
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
          |
          |     ?thing anything:hasDecimal ?decimal .
          |
          |     FILTER(?decimal > "2"^^xsd:decimal)
          |}
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingBiggerThanDecimal.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "search for an anything:Thing that has a decimal value smaller than 3.0 (with type inference)" in {
      val gravsearchQuery =
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
          |
          |     ?thing anything:hasDecimal ?decimal .
          |
          |     FILTER(?decimal < "3"^^xsd:decimal)
          |}
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingSmallerThanDecimal.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for an anything:Thing that has a Boolean value that is true 2 (with type inference)" in {
      val gravsearchQuery =
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
          |
          |     ?thing anything:hasBoolean ?boolean .
          |
          |     FILTER(?boolean = true)
          |
          |}
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithBoolean.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for an anything:Thing that may have a Boolean value that is true (with type inference)" in {
      // set OFFSET to 1 to get "Testding for extended search"
      val gravsearchQuery =
        """PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
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
          |         ?thing anything:hasBoolean ?boolean .
          |         FILTER(?boolean = true)
          |     }
          |
          |     MINUS {
          |         ?thing anything:hasInteger ?intVal .
          |         FILTER(?intVal = 123454321 || ?intVal = 999999999)
          |     }
          |} OFFSET 1""".stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithBooleanOptionalOffset1.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the second page of results
      checkSearchResponseNumberOfResults(actual, 19)
    }

    "search for an anything:Thing that either has a Boolean value that is true or a decimal value that equals 2.1 (or both) (with type inference)" in {
      val gravsearchQuery =
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
          |
          |         FILTER(?boolean = true)
          |     } UNION {
          |         ?thing anything:hasDecimal ?decimal .
          |
          |         FILTER(?decimal = "2.1"^^xsd:decimal)
          |     }
          |
          |} OFFSET 0
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithBooleanOrDecimal.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for a book whose title contains 'Zeit' using the regex function (with type inference)" in {
      val gravsearchQuery =
        """
          |    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |
          |        ?mainRes knora-api:isMainResource true .
          |
          |        ?mainRes incunabula:title ?propVal0 .
          |
          |     } WHERE {
          |
          |        ?mainRes a incunabula:book .
          |
          |        ?mainRes incunabula:title ?propVal0 .
          |
          |        FILTER regex(?propVal0, "Zeit", "i")
          |
          |     }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("BooksWithTitleContainingZeit.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for a book whose title contains 'Zeitglöcklein' using the match function (with type inference)" in {
      val gravsearchQuery =
        """
          |    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |
          |        ?mainRes knora-api:isMainResource true .
          |
          |        ?mainRes incunabula:title ?propVal0 .
          |
          |     } WHERE {
          |
          |        ?mainRes a incunabula:book .
          |
          |        ?mainRes incunabula:title ?propVal0 .
          |
          |        FILTER knora-api:matchText(?propVal0, "Zeitglöcklein")
          |
          |     }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("BooksWithTitleContainingZeitgloecklein.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for a book whose title contains 'Zeitglöcklein' and 'Lebens' using the match function (with type inference)" in {
      val gravsearchQuery =
        """
          |    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |
          |        ?mainRes knora-api:isMainResource true .
          |
          |        ?mainRes incunabula:title ?propVal0 .
          |
          |     } WHERE {
          |
          |        ?mainRes a incunabula:book .
          |
          |        ?mainRes incunabula:title ?propVal0 .
          |
          |        FILTER knora-api:matchText(?propVal0, "Zeitglöcklein AND Lebens")
          |
          |     }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("BooksWithTitleContainingZeitgloecklein.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for 'Zeitglöcklein des Lebens' using dcterms:title (with type inference)" in {
      val gravsearchQuery =
        """
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |    PREFIX dcterms: <http://purl.org/dc/terms/>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book dcterms:title ?title .
          |
          |    } WHERE {
          |        ?book a knora-api:Resource .
          |
          |        ?book dcterms:title ?title .
          |
          |        FILTER(?title = 'Zeitglöcklein des Lebens und Leidens Christi')
          |
          |    } OFFSET 0
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("BooksWithTitleContainingZeitgloecklein.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for an anything:Thing with a list value (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?thing knora-api:isMainResource true .
          |
          |        ?thing anything:hasListItem ?listItem .
          |
          |    } WHERE {
          |        ?thing a anything:Thing .
          |
          |        ?thing anything:hasListItem ?listItem .
          |
          |    } OFFSET 0
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithListValue.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 3)
    }

    "search for a text using the lang function (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
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
          |     FILTER(lang(?text) = "fr")
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("LanguageFulltextSearch.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "search for a specific text using the lang function (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
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
          |     FILTER(lang(?text) = "fr" && ?text = "Bonjour")
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("LanguageFulltextSearch.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch query for link objects that link to an incunabula book (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("LinkObjectsToBooks.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 3)
    }

    "do a Gravsearch query for a letter that links to a specific person via two possible properties (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?letter knora-api:isMainResource true .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1  <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> .
          |
          |
          |    } WHERE {
          |        ?letter a beol:letter .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        # testperson2
          |        ?letter ?linkingProp1 <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> .
          |
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("letterWithAuthor.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch query for a letter that links to a person with a specified name (with type inference)" in {
      val gravsearchQuery =
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
          |        ?letter a beol:letter .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1 ?person1 .
          |
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        ?person1 beol:hasFamilyName ?name .
          |
          |        FILTER(?name = "Meier")
          |
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("letterWithPersonWithName.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch query for a letter that links to another person with a specified name (with type inference)" in {
      val gravsearchQuery =
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
          |        ?letter a beol:letter .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1 ?person1 .
          |
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        ?person1 beol:hasFamilyName ?name .
          |
          |        FILTER(?name = "Muster")
          |
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("letterWithPersonWithName2.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "run a Gravsearch query that searches for a person using foaf classes and propertie (with type inference)" in {
      val gravsearchQuery =
        """
          |      PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |      PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |      PREFIX foaf: <http://xmlns.com/foaf/0.1/>
          |
          |      CONSTRUCT {
          |          ?person knora-api:isMainResource true .
          |
          |          ?person foaf:familyName ?familyName .
          |
          |          ?person foaf:givenName ?givenName .
          |
          |      } WHERE {
          |          ?person a knora-api:Resource .
          |          ?person a foaf:Person .
          |
          |          ?person foaf:familyName ?familyName .
          |
          |          ?familyName a xsd:string .
          |
          |          ?person foaf:givenName ?givenName .
          |
          |          ?givenName a xsd:string .
          |
          |          FILTER(?familyName = "Meier")
          |
          |      }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("foafPerson.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "run a Gravsearch query that searches for a single resource specified by its IRI (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingByIriWithRequestedValues.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a Gravsearch query for a letter and get information about the persons associated with it (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?letter knora-api:isMainResource true .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1 ?person1 .
          |
          |        ?person1 beol:hasFamilyName ?familyName .
          |
          |
          |    } WHERE {
          |        BIND(<http://rdfh.ch/0801/_B3lQa6tSymIq7_7SowBsA> AS ?letter)
          |        ?letter a beol:letter .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        # testperson2
          |        ?letter ?linkingProp1 ?person1 .
          |
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        ?person1 beol:hasFamilyName ?familyName .
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("letterWithAuthorWithInformation.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a Gravsearch query for the pages of a book whose seqnum is lower than or equals 10, with the book as the main resource (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |        ?book incunabula:title ?title .
          |
          |        ?page knora-api:isPartOf ?book ;
          |            incunabula:seqnum ?seqnum .
          |    } WHERE {
          |        BIND(<http://rdfh.ch/0803/b6b5ff1eb703> AS ?book)
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf ?book .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |        FILTER(?seqnum <= 10)
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("incomingPagesForBook.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "reject a Gravsearch query containing a statement whose subject is not the main resource and whose object is used in ORDER BY (with type inference)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |        ?book incunabula:title ?title .
          |
          |        ?page knora-api:isPartOf ?book ;
          |            incunabula:seqnum ?seqnum .
          |    } WHERE {
          |        BIND(<http://rdfh.ch/0803/b6b5ff1eb703> AS ?book)
          |
          |        ?book incunabula:title ?title .
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf ?book .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |        FILTER(?seqnum <= 10)
          |
          |    } ORDER BY ?seqnum
                """.stripMargin
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
    }

    "do a Gravsearch query for regions that belong to pages that are part of a book with the title 'Zeitglöcklein des Lebens und Leidens Christi (with type inference)'" in {
      val gravsearchQuery =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
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
          |    FILTER(?title = "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("regionsOfZeitgloecklein.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a Gravsearch query containing a UNION nested in an OPTIONAL (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX gravsearchtest1: <http://0.0.0.0:3333/ontology/0666/gravsearchtest1/simple/v2#>
          |
          |CONSTRUCT {
          |  ?Project knora-api:isMainResource true .
          |  ?isInProject gravsearchtest1:isInProject ?Project .
          |} WHERE {
          |  ?Project a gravsearchtest1:Project .
          |
          |  OPTIONAL {
          |    ?isInProject gravsearchtest1:isInProject ?Project .
          |    { ?isInProject a gravsearchtest1:BibliographicNotice . } UNION { ?isInProject a gravsearchtest1:Person . }
          |  }
          |}
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ProjectsWithOptionalPersonOrBiblio.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a Gravsearch query that searches for a list node (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |
          |?mainRes knora-api:isMainResource true .
          |
          |?mainRes anything:hasListItem ?propVal0 .
          |
          |} WHERE {
          |
          |?mainRes anything:hasListItem ?propVal0 .
          |
          |FILTER(?propVal0 = "Tree list node 02"^^knora-api:ListNode)
          |
          |}
          |
          |OFFSET 0
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ThingWithListNodeLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a Gravsearch count query that searches for a list node (with type inference)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |
          |CONSTRUCT {
          |
          |?mainRes knora-api:isMainResource true .
          |
          |?mainRes anything:hasListItem ?propVal0 .
          |
          |} WHERE {
          |
          |?mainRes anything:hasListItem ?propVal0 .
          |
          |FILTER(?propVal0 = "Tree list node 02"^^knora-api:ListNode)
          |
          |}
          |
          |OFFSET 0
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended/count",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      checkCountResponse(actual, 1)
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Queries that submit the complex schema

    "perform a Gravsearch query for an anything:Thing with an optional date and sort by date (submitting the complex schema)" in {
      val gravsearchQuery =
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
          |ORDER BY DESC(?date)
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("thingWithOptionalDateSortedDesc.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch count query for an anything:Thing with an optional date used as a sort criterion (submitting the complex schema)" in {
      val gravsearchQuery =
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
          |ORDER BY DESC(?date)
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended/count",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      checkCountResponse(actual, 44)
    }

    "perform a Gravsearch query for an anything:Thing that has an optional decimal value greater than 2 and sort by the decimal value (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ThingsWithOptionalDecimalGreaterThan1.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a Gravsearch query that finds all the books that have a page with seqnum 100 (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("booksWithPage100.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a Gravsearch query that finds all the letters sent by someone called Meier, ordered by date (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("lettersByMeier.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |    } WHERE {
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?title knora-api:valueAsString "Zeitglöcklein des Lebens und Leidens Christi" .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch count query for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?title knora-api:valueAsString "Zeitglöcklein des Lebens und Leidens Christi" .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended/count",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      checkCountResponse(actual, 2)
    }

    "perform a Gravsearch query for books that have the title 'Zeitglöcklein des Lebens' not returning the title in the answer (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?title knora-api:valueAsString "Zeitglöcklein des Lebens und Leidens Christi" .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinExtendedSearchNoTitleInAnswer.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that do not have the title 'Zeitglöcklein des Lebens' (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?title knora-api:valueAsString ?titleStr .
          |
          |        FILTER(?titleStr != "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("NotZeitgloeckleinExtendedSearch.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch count query for books that do not have the title 'Zeitglöcklein des Lebens' (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?title knora-api:valueAsString ?titleStr .
          |
          |        FILTER(?titleStr != "Zeitglöcklein des Lebens und Leidens Christi")
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended/count",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      // 19 - 2 = 18 :-)
      // there is a total of 19 incunabula books of which two have the title "Zeitglöcklein des Lebens und Leidens Christi" (see test above)
      // however, there are 18 books that have a title that is not "Zeitglöcklein des Lebens und Leidens Christi"
      // this is because there is a book that has two titles, one "Zeitglöcklein des Lebens und Leidens Christi" and the other in Latin "Horologium devotionis circa vitam Christi"
      checkCountResponse(actual, 18)
    }

    "perform a Gravsearch query for the page of a book whose seqnum equals 10, returning the seqnum and the link value (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |        ?seqnum knora-api:intValueAsInt 10 .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("PageWithSeqnum10WithSeqnumAndLinkValueInAnswer.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch count query for the page of a book whose seqnum equals 10, returning the seqnum and the link value (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |        ?seqnum knora-api:intValueAsInt 10 .
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "perform a Gravsearch query for the page of a book whose seqnum equals 10, returning only the seqnum (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |        ?seqnum knora-api:intValueAsInt 10 .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("PageWithSeqnum10OnlySeqnuminAnswer.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for the pages of a book whose seqnum is lower than or equals 10 (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |        ?seqnum knora-api:intValueAsInt ?seqnumInt .
          |
          |        FILTER(?seqnumInt <= 10)
          |
          |    } ORDER BY ?seqnum
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("pagesOfLatinNarrenschiffWithSeqnumLowerEquals10.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |    } ORDER BY ?seqnum
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("PagesOfNarrenschiffOrderedBySeqnum.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum and get the next OFFSET (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?page knora-api:isMainResource true .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |    } WHERE {
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf <http://rdfh.ch/0803/b6b5ff1eb703> .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |    } ORDER BY ?seqnum
          |    OFFSET 1
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("PagesOfNarrenschiffOrderedBySeqnumNextOffset.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that have been published on the first of March 1497 (Julian Calendar) (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(knora-api:toSimpleDate(?pubdate) = "JULIAN:1497-03-01"^^knora-api-simple:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("BooksPublishedOnDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a Gravsearch query for books that have not been published on the first of March 1497 (Julian Calendar) (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(knora-api:toSimpleDate(?pubdate) != "JULIAN:1497-03-01"^^knora-api-simple:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("BooksNotPublishedOnDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
      checkSearchResponseNumberOfResults(actual, 17)
    }

    "perform a Gravsearch query for books that have not been published on the first of March 1497 (Julian Calendar) 2 (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(knora-api:toSimpleDate(?pubdate) < "JULIAN:1497-03-01"^^knora-api-simple:Date || knora-api:toSimpleDate(?pubdate) > "JULIAN:1497-03-01"^^knora-api-simple:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("BooksNotPublishedOnDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
      checkSearchResponseNumberOfResults(actual, 17)
    }

    "perform a Gravsearch query for books that have been published before 1497 (Julian Calendar) (submitting the complex schema)" in {
      val gravsearchQuery =
        """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |    PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(knora-api:toSimpleDate(?pubdate) < "JULIAN:1497"^^knora-api-simple:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("BooksPublishedBeforeDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 4 (number of results from query below)
      checkSearchResponseNumberOfResults(actual, 15)
    }

    "perform a Gravsearch query for books that have been published 1497 or later (Julian Calendar) (submitting the complex schema)" in {
      val gravsearchQuery =
        """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |    PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(knora-api:toSimpleDate(?pubdate) >= "JULIAN:1497"^^knora-api-simple:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("BooksPublishedAfterOrOnDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 15 (number of results from query above)
      checkSearchResponseNumberOfResults(actual, 4)
    }

    "perform a Gravsearch query for books that have been published after 1497 (Julian Calendar) (submitting the complex schema)" in {
      val gravsearchQuery =
        """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |    PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(knora-api:toSimpleDate(?pubdate) > "JULIAN:1497"^^knora-api-simple:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("BooksPublishedAfterDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 18 (number of results from query above)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "perform a Gravsearch query for books that have been published 1497 or before (Julian Calendar) (submitting the complex schema)" in {
      val gravsearchQuery =
        """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |    PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(knora-api:toSimpleDate(?pubdate) <= "JULIAN:1497"^^knora-api-simple:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("BooksPublishedBeforeOrOnDate.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 1 (number of results from query above)
      checkSearchResponseNumberOfResults(actual, 18)
    }

    "perform a Gravsearch query for books that have been published after 1486 and before 1491 (Julian Calendar) (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX knora-api-simple: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |    } WHERE {
          |
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?book incunabula:pubdate ?pubdate .
          |
          |        FILTER(knora-api:toSimpleDate(?pubdate) > "JULIAN:1486"^^knora-api-simple:Date && knora-api:toSimpleDate(?pubdate) < "JULIAN:1491"^^knora-api-simple:Date)
          |
          |    } ORDER BY ?pubdate
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("BooksPublishedBetweenDates.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 5)
    }

    "get the regions belonging to a page (submitting the complex schema)" in {
      val gravsearchQuery =
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
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("RegionsForPage.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "get a book a page points to and include the page in the results (all properties present in WHERE clause) (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("bookWithIncomingPagesWithAllRequestedProps.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "get a book a page points to and only include the page's partOf link in the results (none of the other properties) (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("bookWithIncomingPagesOnlyLink.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "get incoming links pointing to an incunbaula:book, excluding isPartOf (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("IncomingLinksForBook.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an anything:Thing that has a decimal value of 2.1 (submitting the complex schema)" in {
      val gravsearchQuery =
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
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingEqualsDecimal.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "search for an anything:Thing that has a decimal value of 2.1 (submitting the complex schema), without inference" in {
      val gravsearchQuery =
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
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingEqualsDecimal.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "search for an anything:Thing that has a decimal value bigger than 2.0 (submitting the complex schema)" in {
      val gravsearchQuery =
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
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingBiggerThanDecimal.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "search for an anything:Thing that has a decimal value smaller than 3.0 (submitting the complex schema)" in {
      val gravsearchQuery =
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
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected: String = testData("ThingSmallerThanDecimal.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for an anything:Thing that has a link to a specified other thing" in {
      val gravsearchQuery =
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
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected: String = testData("ThingWithLinkToStart.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "return a page of anything:Thing resources" in {
      val gravsearchQuery =
        """PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?thing knora-api:isMainResource true .
          |} WHERE {
          |    ?thing a anything:Thing .
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected: String = testData("PageOfThings.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an anything:Thing that has a Boolean value that is true (submitting the complex schema)" in {
      val gravsearchQuery =
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
          |
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithBoolean.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for an anything:Thing that may have a Boolean value that is true (submitting the complex schema)" in {
      // set OFFSET to 1 to get "Testding for extended search"
      val gravsearchQuery =
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
          |} OFFSET 1""".stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithBooleanOptionalOffset1.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      // this is the second page of results
      checkSearchResponseNumberOfResults(actual, 19)
    }

    "search for an anything:Thing that either has a Boolean value that is true or a decimal value that equals 2.1 (or both) (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithBooleanOrDecimal.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for a book whose title contains 'Zeit' using the regex function (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |
          |        ?mainRes knora-api:isMainResource true .
          |
          |        ?mainRes incunabula:title ?title .
          |
          |     } WHERE {
          |
          |        ?mainRes a incunabula:book .
          |
          |        ?mainRes incunabula:title ?title .
          |
          |        ?title knora-api:valueAsString ?titleStr .
          |
          |        FILTER regex(?titleStr, "Zeit", "i")
          |
          |     }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("BooksWithTitleContainingZeit.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for a book whose title contains 'Zeitglöcklein' using the match function (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |
          |        ?mainRes knora-api:isMainResource true .
          |
          |        ?mainRes incunabula:title ?title .
          |
          |     } WHERE {
          |
          |        ?mainRes a incunabula:book .
          |
          |        ?mainRes incunabula:title ?title .
          |
          |        FILTER knora-api:matchText(?title, "Zeitglöcklein")
          |
          |     }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("BooksWithTitleContainingZeitgloecklein.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for a book whose title contains 'Zeitglöcklein' and 'Lebens' using the match function (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |
          |        ?mainRes knora-api:isMainResource true .
          |
          |        ?mainRes incunabula:title ?title .
          |
          |     } WHERE {
          |
          |        ?mainRes a incunabula:book .
          |
          |        ?mainRes incunabula:title ?title .
          |
          |        FILTER knora-api:matchText(?title, "Zeitglöcklein AND Lebens")
          |
          |     }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("BooksWithTitleContainingZeitgloecklein.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 2)
    }

    "search for an anything:Thing with a list value (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |    CONSTRUCT {
          |        ?thing knora-api:isMainResource true .
          |
          |        ?thing anything:hasListItem ?listItem .
          |
          |    } WHERE {
          |        ?thing a anything:Thing .
          |
          |        ?thing anything:hasListItem ?listItem .
          |
          |    } OFFSET 0
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithListValue.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 3)
    }

    "search for a text in a particular language (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("LanguageFulltextSearch.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "search for a specific text using the lang function (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("LanguageFulltextSearch.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch query for link objects that link to an incunabula book (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("LinkObjectsToBooks.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 3)
    }

    "do a Gravsearch query for a letter that links to a specific person via two possible properties (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?letter knora-api:isMainResource true .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1 <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> .
          |
          |
          |    } WHERE {
          |        ?letter a beol:letter .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        # testperson2
          |        ?letter ?linkingProp1 <http://rdfh.ch/0801/VvYVIy-FSbOJBsh2d9ZFJw> .
          |
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("letterWithAuthor.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch query for a letter that links to a person with a specified name (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
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
          |        ?letter a beol:letter .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1 ?person1 .
          |
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        ?person1 beol:hasFamilyName ?name .
          |
          |        ?name knora-api:valueAsString "Meier" .
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("letterWithPersonWithName.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "do a Gravsearch query for a letter that links to another person with a specified name (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
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
          |        ?letter a beol:letter .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1 ?person1 .
          |
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        ?person1 beol:hasFamilyName ?name .
          |
          |        ?name knora-api:valueAsString "Muster" .
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("letterWithPersonWithName2.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
      checkSearchResponseNumberOfResults(actual, 1)
    }

    "run a Gravsearch query that searches for a single resource specified by its IRI (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingByIriWithRequestedValues.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a Gravsearch query for a letter and get information about the persons associated with it (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?letter knora-api:isMainResource true .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        ?letter ?linkingProp1 ?person1 .
          |
          |        ?person1 beol:hasFamilyName ?familyName .
          |
          |
          |    } WHERE {
          |        BIND(<http://rdfh.ch/0801/_B3lQa6tSymIq7_7SowBsA> AS ?letter)
          |        ?letter a beol:letter .
          |
          |        ?letter beol:creationDate ?date .
          |
          |        # testperson2
          |        ?letter ?linkingProp1 ?person1 .
          |
          |        FILTER(?linkingProp1 = beol:hasAuthor || ?linkingProp1 = beol:hasRecipient)
          |
          |        ?person1 beol:hasFamilyName ?familyName .
          |
          |    } ORDER BY ?date
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("letterWithAuthorWithInformation.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "do a Gravsearch query for the pages of a book whose seqnum is lower than or equals 10, with the book as the main resource (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |        ?book incunabula:title ?title .
          |
          |        ?page knora-api:isPartOf ?book ;
          |            incunabula:seqnum ?seqnum .
          |    } WHERE {
          |        BIND(<http://rdfh.ch/0803/b6b5ff1eb703> AS ?book)
          |        ?book a incunabula:book .
          |
          |        ?book incunabula:title ?title .
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf ?book .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |        ?seqnum knora-api:intValueAsInt ?seqnumInt .
          |
          |        FILTER(?seqnumInt <= 10)
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("incomingPagesForBook.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "reject a Gravsearch query containing a statement whose subject is not the main resource and whose object is used in ORDER BY (submitting the complex schema)" in {
      val gravsearchQuery =
        """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |    CONSTRUCT {
          |        ?book knora-api:isMainResource true .
          |        ?book incunabula:title ?title .
          |
          |        ?page knora-api:isPartOf ?book ;
          |            incunabula:seqnum ?seqnum .
          |    } WHERE {
          |        BIND(<http://rdfh.ch/0803/b6b5ff1eb703> AS ?book)
          |
          |        ?book incunabula:title ?title .
          |
          |        ?page a incunabula:page .
          |
          |        ?page knora-api:isPartOf ?book .
          |
          |        ?page incunabula:seqnum ?seqnum .
          |
          |        ?seqnum knora-api:intValueAsInt ?seqnumInt .
          |
          |        FILTER(?seqnumInt <= 10)
          |
          |    } ORDER BY ?seqnum
                """.stripMargin
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
    }

    "do a Gravsearch query for regions that belong to pages that are part of a book with the title 'Zeitglöcklein des Lebens und Leidens Christi' (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("regionsOfZeitgloecklein.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "reject a Gravsearch query in the complex schema that uses knora-api:isMainResource in the simple schema" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
    }

    "reject a Gravsearch query in the complex schema that uses a Knora property in the simple schema" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
    }

    "reject a Gravsearch query that uses a string literal in the CONSTRUCT clause" in {
      val gravsearchQuery =
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
                """.stripMargin

      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
    }

    "reject a Gravsearch query in the complex schema with a variable in the CONSTRUCT clause referring to a non-property entity that isn't a resource or value" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
    }

    "search for a list value that refers to a particular list node (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |    CONSTRUCT {
          |        ?thing knora-api:isMainResource true .
          |
          |        ?thing anything:hasListItem ?listItem .
          |
          |    } WHERE {
          |        ?thing anything:hasListItem ?listItem .
          |
          |        ?listItem knora-api:listValueAsListNode <http://rdfh.ch/lists/0001/treeList02> .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("thingReferringToSpecificListNode.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a list value that does not refer to a particular list node (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |    CONSTRUCT {
          |        ?thing knora-api:isMainResource true .
          |
          |        ?thing anything:hasListItem ?listItem .
          |
          |    } WHERE {
          |        ?thing anything:hasListItem ?listItem .
          |
          |        FILTER NOT EXISTS {
          |
          |         ?listItem knora-api:listValueAsListNode <http://rdfh.ch/lists/0001/treeList02> .
          |
          |        }
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("thingNotReferringToSpecificListNode.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a list value that does not refer to a particular list node, performing a count query (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |    CONSTRUCT {
          |        ?thing knora-api:isMainResource true .
          |
          |        ?thing anything:hasListItem ?listItem .
          |
          |    } WHERE {
          |        ?thing anything:hasListItem ?listItem .
          |
          |        FILTER NOT EXISTS {
          |
          |         ?listItem knora-api:listValueAsListNode <http://rdfh.ch/lists/0001/treeList02> .
          |
          |        }
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended/count",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      checkCountResponse(actual, 2)
    }

    "search for a list value that refers to a particular list node that has subnodes (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
          |
          |    CONSTRUCT {
          |        ?thing knora-api:isMainResource true .
          |
          |        ?thing anything:hasListItem ?listItem .
          |
          |    } WHERE {
          |        ?thing anything:hasListItem ?listItem .
          |
          |        ?listItem knora-api:listValueAsListNode <http://rdfh.ch/lists/0001/treeList> .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("thingReferringToSpecificListNodeWithSubnodes.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a beol:letter with list value that refers to a particular list node (submitting the complex schema)" in {
      val gravsearchQuery =
        """
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |
          |    CONSTRUCT {
          |        ?letter knora-api:isMainResource true .
          |
          |        ?letter beol:hasSubject ?subject .
          |
          |    } WHERE {
          |        ?letter a beol:letter .
          |
          |        ?letter beol:hasSubject ?subject .
          |
          |        ?subject knora-api:listValueAsListNode <http://rdfh.ch/lists/0801/logarithmic_curves> .
          |
          |    }
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      val expected = testData("letterWithSubject.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a standoff link using the knora-api:standoffLink function (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("thingsWithStandoffLinks.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a standoff link using the knora-api:standoffLink function, referring to the target resource in the function call only (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("thingsWithStandoffLinks.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a standoff link using the knora-api:standoffLink function specifying an Iri for the target resource (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("thingsWithStandoffLinksToSpecificThing.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a standoff link using the knora-api:standoffLink function specifying an Iri for the target resource, referring to the target resource in the function call only (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("thingsWithStandoffLinksToSpecificThing.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for matching words in a particular type of standoff tag (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("ThingWithRichtextWithTermTextInParagraph.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for a standoff date tag indicating a date in a particular range (submitting the complex schema)" in {
      // First, create a standoff-to-XML mapping that can handle standoff date tags.
      val mappingFileToSend = Paths.get("..", "test_data/test_route/texts/mappingForHTML.xml")
      val paramsCreateHTMLMappingFromXML =
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
           |}
                """.stripMargin

      val formDataMapping = Multipart.FormData(
        Multipart.FormData.BodyPart(
          "json",
          HttpEntity(ContentTypes.`application/json`, paramsCreateHTMLMappingFromXML),
        ),
        Multipart.FormData.BodyPart(
          "xml",
          HttpEntity.fromPath(ContentTypes.`text/xml(UTF-8)`, mappingFileToSend),
          Map("filename" -> mappingFileToSend.getFileName.toString),
        ),
      )

      // send mapping xml to route
      val _ = getResponseAsString(
        Post(s"$baseApiUrl/v2/mapping", formDataMapping) ~> addCredentials(
          BasicHttpCredentials(anythingUserEmail, password),
        ),
      )

      // Next, create a resource with a text value containing a standoff date tag.
      val xmlForJson = stringFormatter.toJsonEncodedString(
        FileUtil.readTextFile(Paths.get("..", "test_data/test_route/texts/HTML.xml")),
      )
      val requestBody =
        s"""{
           |  "@id" : "http://rdfh.ch/0001/a-thing",
           |  "@type" : "anything:Thing",
           |  "anything:hasText" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:textValueAsXml" : $xmlForJson,
           |    "knora-api:textValueHasMapping" : {
           |      "@id": "$anythingProjectIri/mappings/HTMLMapping"
           |    }
           |  },
           |  "@context" : {
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val _ = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/values",
          HttpEntity(RdfMediaTypes.`application/ld+json`, requestBody),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
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
          |}
                """.stripMargin

      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      assert(actual.contains("we will have a party"))
    }

    "search for a standoff tag using knora-api:standoffTagHasStartAncestor (submitting the complex schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      assert(actual.contains("we will have a party"))
    }

    "reject a link value property in a query in the simple schema" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)),
      )
      assert(actual.status == StatusCodes.NOT_FOUND)
      assert(responseToString(actual).contains("http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#partOfValue"))
    }

    "create a resource with a large text containing a lot of markup (32849 words, 6738 standoff tags)" ignore { // uses too much memory for GitHub CI
      // Create a resource containing the text of Hamlet.
      val hamletXml = FileUtil.readTextFile(Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/hamlet.xml"))
      val jsonLDEntity =
        s"""{
           |  "@type" : "anything:Thing",
           |  "anything:hasRichtext" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:textValueAsXml" : ${stringFormatter.toJsonEncodedString(hamletXml)},
           |    "knora-api:textValueHasMapping" : {
           |      "@id" : "http://rdfh.ch/standoff/mappings/StandardMapping"
           |    }
           |  },
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "rdfs:label" : "test thing",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
      val resourceCreateResponseAsJsonLD = getResponseAsJsonLD(
        Post(
          s"$baseApiUrl/v2/resources",
          HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val validationFun: (String, => Nothing) => String =
        (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val resourceIri: IRI =
        resourceCreateResponseAsJsonLD.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(resourceIri.toSmartIri.isKnoraDataIri)
      hamletResourceIri.set(resourceIri)
    }

    "search for the large text and its markup and receive it as XML, and check that it matches the original XML" ignore { // depends on previous test
      val hamletXml = FileUtil.readTextFile(Paths.get("..", "test_data/generated_test_data/resourcesR2RV2/hamlet.xml"))
      val gravsearchQuery =
        s"""PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
           |PREFIX standoff: <http://api.knora.org/ontology/standoff/v2#>
           |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/v2#>
           |
           |CONSTRUCT {
           |    ?thing knora-api:isMainResource true .
           |    ?thing anything:hasRichtext ?text .
           |} WHERE {
           |    BIND(<${hamletResourceIri.get}> AS ?thing)
           |    ?thing a anything:Thing .
           |    ?thing anything:hasRichtext ?text .
           |}""".stripMargin
      val searchResponseAsJsonLD = getResponseAsJsonLD(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val xmlFromResponse: String = searchResponseAsJsonLD.body
        .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasRichtext")
        .flatMap(_.getRequiredString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml))
        .fold(e => throw BadRequestException(e), identity)

      // Compare it to the original XML.
      val xmlDiff: Diff =
        DiffBuilder.compare(Input.fromString(hamletXml)).withTest(Input.fromString(xmlFromResponse)).build()
      xmlDiff.hasDifferences should be(false)
    }

    "find a resource with two different incoming links" in {
      // Create the target resource.
      val targetResource: String =
        """{
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
          |}""".stripMargin
      val targetResourceIri = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[TestClientService](
          _.getResponseJsonLD(
            Post(
              s"$baseApiUrl/v2/resources",
              HttpEntity(RdfMediaTypes.`application/ld+json`, targetResource),
            ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
          ).map(_.body.getRequiredString(JsonLDKeywords.ID).getOrElse(throw AssertionError("No IRI returned"))),
        ),
      )

      val sourceResource1: String =
        s"""{
           |  "@type" : "anything:BlueThing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |    "anything:hasBlueThingValue" : {
           |    "@type" : "knora-api:LinkValue",
           |        "knora-api:linkValueHasTargetIri" : {
           |        "@id" : "$targetResourceIri"
           |    }
           |  },
           |  "rdfs:label" : "blue thing with link to other blue thing",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
      val _ = checkResponseOK(
        Post(
          s"$baseApiUrl/v2/resources",
          HttpEntity(RdfMediaTypes.`application/ld+json`, sourceResource1),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )

      val sourceResource2: String =
        s"""{
           |  "@type" : "anything:Thing",
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |    "anything:hasOtherThingValue" : {
           |    "@type" : "knora-api:LinkValue",
           |        "knora-api:linkValueHasTargetIri" : {
           |        "@id" : "$targetResourceIri"
           |    }
           |  },
           |  "rdfs:label" : "thing with link to blue thing",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin

      val _ = checkResponseOK(
        Post(
          s"$baseApiUrl/v2/resources",
          HttpEntity(RdfMediaTypes.`application/ld+json`, sourceResource2),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )

      val gravsearchQuery =
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
                """.stripMargin

      val responseJsonDoc = getResponseAsJsonLD(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val validationFun: (String, => Nothing) => String =
        (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val searchResultIri = responseJsonDoc.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(searchResultIri == targetResourceIri)
    }

    "search for an anything:Thing with a time value (using the simple schema)" in {
      val gravsearchQuery =
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
                """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val expected = testData("ThingWithTimeStamp.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "get a resource with a link to another resource that the user doesn't have permission to see" in {
      val gravsearchQuery =
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
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ThingWithHiddenThing.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "not return duplicate results when there are UNION branches with different variables" in {
      val gravsearchQuery =
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

      val expectedCount = 1

      val actualCount = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended/count",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      checkCountResponse(actualCount, expectedCount)

      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      checkSearchResponseNumberOfResults(actual, expectedCount)
      val expected = testData("ThingFromQueryWithUnion.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "reject an ORDER by containing a variable that's not bound at the top level of the WHERE clause" in {
      val gravsearchQuery =
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
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
      assert(
        responseToString(actual).contains(
          "Variable ?int is used in ORDER by, but is not bound at the top level of the WHERE clause",
        ),
      )
    }

    "reject a FILTER in a UNION that uses a variable that's out of scope" in {
      val gravsearchQuery =
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
      val actual = singleAwaitingRequest(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      assert(actual.status == StatusCodes.BAD_REQUEST)
      assert(
        responseToString(actual).contains(
          "One or more variables used in a filter have not been bound in the same UNION block: ?richtext",
        ),
      )
    }

    "search for a resource containing a time value tag" in {
      // Create a resource containing a time value.

      val xmlStr =
        """<?xml version="1.0" encoding="UTF-8"?>
          |<text documentType="html">
          |    <p>The timestamp for this test is <span class="timestamp" data-timestamp="2020-01-27T08:31:51.503187Z">27 January 2020</span>.</p>
          |</text>
          |""".stripMargin

      val jsonLDEntity =
        s"""{
           |  "@type" : "anything:Thing",
           |  "anything:hasText" : {
           |    "@type" : "knora-api:TextValue",
           |    "knora-api:textValueAsXml" : ${stringFormatter.toJsonEncodedString(xmlStr)},
           |    "knora-api:textValueHasMapping" : {
           |      "@id" : "$anythingProjectIri/mappings/HTMLMapping"
           |    }
           |  },
           |  "knora-api:attachedToProject" : {
           |    "@id" : "http://rdfh.ch/projects/0001"
           |  },
           |  "rdfs:label" : "thing with timestamp in markup",
           |  "@context" : {
           |    "rdf" : "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
           |    "knora-api" : "http://api.knora.org/ontology/knora-api/v2#",
           |    "rdfs" : "http://www.w3.org/2000/01/rdf-schema#",
           |    "xsd" : "http://www.w3.org/2001/XMLSchema#",
           |    "anything" : "http://0.0.0.0:3333/ontology/0001/anything/v2#"
           |  }
           |}""".stripMargin
      val resourceIri = UnsafeZioRun.runOrThrow(
        ZIO.serviceWithZIO[TestClientService](
          _.getResponseJsonLD(
            Post(
              s"$baseApiUrl/v2/resources",
              HttpEntity(RdfMediaTypes.`application/ld+json`, jsonLDEntity),
            ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
          ).map(_.body.getRequiredString(JsonLDKeywords.ID).getOrElse(throw AssertionError("No IRI returned"))),
        ),
      )
      timeTagResourceIri.set(resourceIri)

      // Search for the resource.
      val gravsearchQuery =
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
                """.stripMargin

      val actual = getResponseAsJsonLD(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val validationFun: (String, => Nothing) => String =
        (s, e) => Iri.validateAndEscapeIri(s).getOrElse(e)
      val actualResourceIri: IRI = actual.body.requireStringWithValidation(JsonLDKeywords.ID, validationFun)
      assert(actualResourceIri == timeTagResourceIri.get)

      val xmlFromResponse: String = actual.body
        .getRequiredObject("http://0.0.0.0:3333/ontology/0001/anything/v2#hasText")
        .flatMap(_.getRequiredString(OntologyConstants.KnoraApiV2Complex.TextValueAsXml))
        .fold(e => throw BadRequestException(e), identity)

      // Compare it to the original XML.
      val xmlDiff: Diff =
        DiffBuilder.compare(Input.fromString(xmlStr)).withTest(Input.fromString(xmlFromResponse)).build()
      xmlDiff.hasDifferences should be(false)
    }

    "search for an rdfs:label using a literal in the simple schema" in {
      val gravsearchQuery: String =
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
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an rdfs:label using a literal in the complex schema" in {
      val gravsearchQuery: String =
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
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an rdfs:label using a variable in the simple schema" in {
      val gravsearchQuery: String =
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
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an rdfs:label using a variable in the complex schema" in {
      val gravsearchQuery: String =
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
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an rdfs:label using knora-api:matchLabel in the simple schema" in {
      val gravsearchQuery: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    FILTER knora-api:matchLabel(?book, "Zeitglöcklein")
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an rdfs:label using knora-api:matchLabel in the complex schema" in {
      val gravsearchQuery: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    FILTER knora-api:matchLabel(?book, "Zeitglöcklein")
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an rdfs:label using the regex function in the simple schema" in {
      val gravsearchQuery: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    FILTER regex(?bookLabel, "Zeit", "i")
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "search for an rdfs:label using the regex function in the complex schema" in {
      val gravsearchQuery: String =
        """
          |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?book knora-api:isMainResource true .
          |} WHERE {
          |    ?book rdf:type incunabula:book .
          |    ?book rdfs:label ?bookLabel .
          |    FILTER regex(?bookLabel, "Zeit", "i")
          |}""".stripMargin
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("ZeitgloeckleinViaLabel.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a search that compares two variables representing resources (in the simple schema)" in {
      val gravsearchQuery: String =
        """PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |} WHERE {
          |    ?letter a beol:letter .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |    FILTER(?person1 != ?person2) .
          |}
          |OFFSET 0""".stripMargin
      // We should get one result, not including <http://rdfh.ch/0801/XNn6wanrTHWShGTjoULm5g> ("letter to self").
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("LetterNotToSelf.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a search that compares two variables representing resources (in the complex schema)" in {
      val gravsearchQuery: String =
        """PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |} WHERE {
          |    ?letter a beol:letter .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |    FILTER(?person1 != ?person2) .
          |}
          |OFFSET 0""".stripMargin
      // We should get one result, not including <http://rdfh.ch/0801/XNn6wanrTHWShGTjoULm5g> ("letter to self").
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("LetterNotToSelf.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a search that compares a variable with a resource IRI (in the simple schema)" in {
      val gravsearchQuery: String =
        """PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |} WHERE {
          |    ?letter a beol:letter .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |    FILTER(?person1 != <http://rdfh.ch/0801/F4n1xKa3TCiR4llJeElAGA>) .
          |}
          |OFFSET 0""".stripMargin
      // We should get one result, not including <http://rdfh.ch/0801/XNn6wanrTHWShGTjoULm5g> ("letter to self").
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("LetterNotToSelf.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a search that compares a variable with a resource IRI (in the complex schema)" in {
      val gravsearchQuery: String =
        """PREFIX beol: <http://0.0.0.0:3333/ontology/0801/beol/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
          |
          |CONSTRUCT {
          |    ?letter knora-api:isMainResource true .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |} WHERE {
          |    ?letter a beol:letter .
          |    ?letter beol:hasAuthor ?person1 .
          |    ?letter beol:hasRecipient ?person2 .
          |    FILTER(?person1 != <http://rdfh.ch/0801/F4n1xKa3TCiR4llJeElAGA>) .
          |}
          |OFFSET 0""".stripMargin
      // We should get one result, not including <http://rdfh.ch/0801/XNn6wanrTHWShGTjoULm5g> ("letter to self").
      val actual = getResponseAsString(
        Post(s"$baseApiUrl/v2/searchextended", HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery)),
      )
      val expected = testData("LetterNotToSelf.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a searchbylabel search for the label 'Treasure Island' with search string 'Treasure Island'" in {
      val searchValueUriEncoded: String = "Treasure%20Island"
      val limitToResourceClassUriEncoded: String = URLEncoder.encode(
        "http://0.0.0.0:3333/ontology/0001/books/v2#Book",
        "UTF-8",
      )
      val offset: Int = 0

      val request =
        s"$baseApiUrl/v2/searchbylabel/" + searchValueUriEncoded +
          "?limitToResourceClass=" + limitToResourceClassUriEncoded +
          "&offset=" + offset

      val actual   = getResponseAsString(Get(request))
      val expected = testData("SearchbylabelSimple.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a searchbylabel search for the label 'Treasure Island' with search string 'Treasure'" in {

      val searchValueUriEncoded: String = "Treasure"
      val limitToResourceClassUriEncoded: String = URLEncoder.encode(
        "http://0.0.0.0:3333/ontology/0001/books/v2#Book",
        "UTF-8",
      )
      val offset: Int = 0

      val request =
        s"$baseApiUrl/v2/searchbylabel/" + searchValueUriEncoded +
          "?limitToResourceClass=" + limitToResourceClassUriEncoded +
          "&offset=" + offset

      val actual   = getResponseAsString(Get(request))
      val expected = testData("SearchbylabelSimple.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a searchbylabel search for a label with special characters" in {
      // the characters that have a special meaning in the lucene query parser syntax need to be escaped like so:
      // this .,\:; is \+ a \- test & with \\ special \( characters \) in \[\] \{\} | the \|\| label\?\!
      // then, the search value needs to be encoded (a useful tool for this is: https://meyerweb.com/eric/tools/dencoder/)
      val searchValueUriEncoded =
        "this%20.%2C%5C%3A%3B%20is%20%5C%2B%20a%20%5C-%20test%20%26%20with%20%5C%5C%20special%20%5C(%20characters%20%5C)%20in%20%5C%5B%5C%5D%20%5C%7B%5C%7D%20%7C%20the%20%5C%7C%5C%7C%20label%5C%3F%5C!"

      val limitToResourceClassUriEncoded: String = URLEncoder.encode(
        "http://0.0.0.0:3333/ontology/0001/books/v2#Book",
        "UTF-8",
      )
      val offset: Int = 0

      val request =
        s"$baseApiUrl/v2/searchbylabel/" + searchValueUriEncoded +
          "?limitToResourceClass=" + limitToResourceClassUriEncoded +
          "&offset=" + offset

      val actual   = getResponseAsString(Get(request))
      val expected = testData("SearchbylabelSpecialCharacters.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a searchbylabel search for a label that starts with a slash `/`" in {
      val searchValueUriEncoded: String = "%5C%2Fslashes"
      val limitToResourceClassUriEncoded: String = URLEncoder.encode(
        "http://0.0.0.0:3333/ontology/0001/books/v2#Book",
        "UTF-8",
      )
      val offset: Int = 0

      val request =
        s"$baseApiUrl/v2/searchbylabel/" + searchValueUriEncoded +
          "?limitToResourceClass=" + limitToResourceClassUriEncoded +
          "&offset=" + offset

      val actual   = getResponseAsString(Get(request))
      val expected = testData("SearchbylabelSlashes.jsonld")
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "perform a searchbylabel search for the label 'Treasure Island' but providing the wrong class" in {
      val searchValueUriEncoded: String = URLEncoder.encode(
        "Treasure",
        "UTF-8",
      )
      val limitToResourceClassUriEncoded: String = URLEncoder.encode(
        "http://0.0.0.0:3333/ontology/0001/books/v2#Page",
        "UTF-8",
      )
      val offset: Int = 0

      val request =
        s"$baseApiUrl/v2/searchbylabel/" + searchValueUriEncoded +
          "?limitToResourceClass=" + limitToResourceClassUriEncoded +
          "&offset=" + offset

      val actual   = getResponseAsString(Get(request))
      val expected = "{}"
      compareJSONLDForResourcesResponse(expected, actual)
    }

    "count anything:Thing that doesn't have a boolean property (MINUS)" in {
      val gravsearchQuery =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |} WHERE {
          |  ?thing a anything:Thing .
          |  ?thing a knora-api:Resource .
          |  MINUS {
          |    ?thing anything:hasBoolean ?bool .
          |  }
          |}
          |
        """.stripMargin

      val actual = getResponseAsJsonLD(
        Post(
          s"$baseApiUrl/v2/searchextended/count",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val numberOfResults = actual.body
        .getRequiredInt(OntologyConstants.SchemaOrg.NumberOfItems)
        .fold(e => throw AssertionError(e), identity)
      assert(numberOfResults != 0)
    }

    "count anything:Thing that doesn't have a boolean property (FILTER NOT EXISTS)" in {
      val gravsearchQuery =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |} WHERE {
          |  ?thing a anything:Thing .
          |  ?thing a knora-api:Resource .
          |  FILTER NOT EXISTS {
          |    ?thing anything:hasBoolean ?bool .
          |  }
          |}
          |
            """.stripMargin
      val actual = getResponseAsJsonLD(
        Post(
          s"$baseApiUrl/v2/searchextended/count",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      val numberOfResults = actual.body
        .getRequiredInt(OntologyConstants.SchemaOrg.NumberOfItems)
        .fold(e => throw AssertionError(e), identity)
      assert(numberOfResults != 0)
    }

    "search for anything:Thing that doesn't have a boolean property (FILTER NOT EXISTS)" in {
      val gravsearchQuery =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |} WHERE {
          |  ?thing a anything:Thing .
          |  ?thing a knora-api:Resource .
          |  FILTER NOT EXISTS {
          |    ?thing anything:hasBoolean ?bool .
          |  }
          |}
          |
            """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      checkSearchResponseNumberOfResults(actual, 24)
    }

    "search for anything:Thing that doesn't have a link property (FILTER NOT EXISTS)" in {
      val gravsearchQuery =
        """
          |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
          |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
          |
          |CONSTRUCT {
          |  ?thing knora-api:isMainResource true .
          |} WHERE {
          |  ?thing a anything:Thing .
          |  ?thing a knora-api:Resource .
          |  FILTER NOT EXISTS {
          |    ?thing anything:hasOtherThing ?otherThing .
          |  }
          |}
          |
            """.stripMargin
      val actual = getResponseAsString(
        Post(
          s"$baseApiUrl/v2/searchextended",
          HttpEntity(RdfMediaTypes.`application/sparql-query`, gravsearchQuery),
        ) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)),
      )
      checkSearchResponseNumberOfResults(actual, 24)
    }
  }
}
