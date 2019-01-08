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

package org.knora.webapi.e2e.v2

import java.io.File
import java.net.URLEncoder

import akka.actor.ActorSystem
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, Multipart}
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi._
import org.knora.webapi.e2e.v2.ResponseCheckerR2RV2._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.responders.v2.search._
import org.knora.webapi.routing.RouteUtilV2
import org.knora.webapi.routing.v1.ValuesRouteV1
import org.knora.webapi.routing.v2.{SearchRouteV2, StandoffRouteV2}
import org.knora.webapi.util.FileUtil
import spray.json.JsString

import scala.concurrent.ExecutionContextExecutor


/**
  * End-to-end test specification for the search endpoint. This specification uses the Spray Testkit as documented
  * here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class SearchRouteV2R2RSpec extends R2RSpec {

    override def testConfigSource: String =
        """
          |# akka.loglevel = "DEBUG"
          |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    private val searchPath = SearchRouteV2.knoraApiPath(system, settings, log)
    private val standoffPath = StandoffRouteV2.knoraApiPath(system, settings, log)
    private val valuesPath = ValuesRouteV1.knoraApiPath(system, settings, log)

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    private val anythingUser = SharedTestDataADM.anythingUser1
    private val anythingUserEmail = anythingUser.email
    private val anythingProjectIri = SharedTestDataADM.ANYTHING_PROJECT_IRI

    private val incunabulaUser = SharedTestDataADM.incunabulaMemberUser
    private val incunabulaUserEmail = incunabulaUser.email

    private val password = "test"

    // If true, writes all API responses to test data files. If false, compares the API responses to the existing test data files.
    private val writeTestDataFiles = false

    override lazy val rdfDataObjects: List[RdfDataObject] = List(
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula"),
        RdfDataObject(path = "_test_data/all_data/beol-data.ttl", name = "http://www.knora.org/data/0801/beol"),
        RdfDataObject(path = "_test_data/e2e.v2.SearchRouteV2R2RSpec/gravsearchtest1-admin.ttl", name = "http://www.knora.org/data/admin"),
        RdfDataObject(path = "_test_data/e2e.v2.SearchRouteV2R2RSpec/gravsearchtest1-onto.ttl", name = "http://www.knora.org/ontology/0666/gravsearchtest1"),
        RdfDataObject(path = "_test_data/e2e.v2.SearchRouteV2R2RSpec/gravsearchtest1-data.ttl", name = "http://www.knora.org/data/0666/gravsearchtest1")
    )

    "The Search v2 Endpoint" should {
        "perform a fulltext search for 'Narr'" in {

            Get("/v2/search/Narr") ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/NarrFulltextSearch.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a count query for a fulltext search for 'Narr'" in {

            Get("/v2/search/count/Narr") ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkCountResponse(responseAs[String], 210)

            }
        }


        "perform a fulltext search for 'Dinge' (in the complex schema)" in {
            Get("/v2/search/Dinge") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/DingeFulltextSearch.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a fulltext search for 'Dinge' (in the simple schema)" in {
            Get("/v2/search/Dinge").addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/DingeFulltextSearchSimple.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a count query for a fulltext search for 'Dinge'" in {
            Get("/v2/search/count/Dinge") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkCountResponse(responseAs[String], 1)

            }
        }

        "perform a fulltext query for a search value containing a single character wildcard" in {
            Get("/v2/search/Unif%3Frm") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingUniform.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a fulltext query for a search value containing a multiple character wildcard" in {
            Get("/v2/search/Unif*m") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingUniform.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Queries without type inference

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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
                  |    }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)).addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ZeitgloeckleinExtendedSearchWithTitleInAnswerSimple.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)).addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ZeitgloeckleinExtendedSearchWithTitleInAnswerSimple.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended/count", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkCountResponse(responseAs[String], 2)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ZeitgloeckleinExtendedSearchNoTitleInAnswer.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/NotZeitgloeckleinExtendedSearch.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended/count", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                // 19 - 2 = 18 :-)
                // there is a total of 19 incunabula books of which two have the title "Zeitglöcklein des Lebens und Leidens Christi" (see test above)
                // however, there are 18 books that have a title that is not "Zeitglöcklein des Lebens und Leidens Christi"
                // this is because there is a book that has two titles, one "Zeitglöcklein des Lebens und Leidens Christi" and the other in Latin "Horologium devotionis circa vitam Christi"

                checkCountResponse(responseAs[String], 18)

            }

        }

        "perform a Gravsearch query for the page of a book whose seqnum equals 10, returning the seqnum  and the link value" in {

            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |        ?page a knora-api:Resource .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |        <http://rdfh.ch/b6b5ff1eb703> a knora-api:Resource .
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/PageWithSeqnum10WithSeqnumAndLinkValueInAnswer.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])


            }

        }

        "perform a Gravsearch count query for the page of a book whose seqnum equals 10, returning the seqnum  and the link value" in {

            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |        ?page a knora-api:Resource .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |        <http://rdfh.ch/b6b5ff1eb703> a knora-api:Resource .
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkSearchResponseNumberOfResults(responseAs[String], 1)


            }

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
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |        <http://rdfh.ch/b6b5ff1eb703> a knora-api:Resource .
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/PageWithSeqnum10OnlySeqnuminAnswer.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])


            }

        }


        "perform a Gravsearch query for the pages of a book whose seqnum is lower than or equals 10" in {

            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |        ?page a knora-api:Resource .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |        <http://rdfh.ch/b6b5ff1eb703> a knora-api:Resource .
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/pagesOfLatinNarrenschiffWithSeqnumLowerEquals10.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum" in {

            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |        ?page a knora-api:Resource .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |        <http://rdfh.ch/b6b5ff1eb703> a knora-api:Resource .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |        incunabula:seqnum knora-api:objectType xsd:integer .
                  |
                  |        ?seqnum a xsd:integer .
                  |
                  |    } ORDER BY ?seqnum
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/PagesOfNarrenschiffOrderedBySeqnum.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum and get the next OFFSET" in {

            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |        ?page a knora-api:Resource .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |        <http://rdfh.ch/b6b5ff1eb703> a knora-api:Resource .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |        incunabula:seqnum knora-api:objectType xsd:integer .
                  |
                  |        ?seqnum a xsd:integer .
                  |
                  |    } ORDER BY ?seqnum
                  |    OFFSET 1
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/PagesOfNarrenschiffOrderedBySeqnumNextOffset.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }


        "perform a Gravsearch query for books that have been published on the first of March 1497 (Julian Calendar)" ignore { // literals are not supported
            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?book knora-api:isMainResource true .
                  |
                  |        ?book incunabula:title ?title .
                  |
                  |        ?book incunabula:pubdate "JULIAN:1497-03-01"^^knora-api:Date .
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
                  |        ?book incunabula:pubdate "JULIAN:1497-03-01"^^knora-api:Date .
                  |        incunabula:pubdate knora-api:objectType knora-api:Date .
                  |
                  |    }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedOnDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksNotPublishedOnDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
                checkSearchResponseNumberOfResults(responseAs[String], 17)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksNotPublishedOnDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
                checkSearchResponseNumberOfResults(responseAs[String], 17)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedBeforeDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 4 (number of results from query below)
                checkSearchResponseNumberOfResults(responseAs[String], 15)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedAfterOrOnDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 15 (number of results from query above)
                checkSearchResponseNumberOfResults(responseAs[String], 4)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedAfterDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 18 (number of results from query above)
                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedBeforeOrOnDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 1 (number of results from query above)
                checkSearchResponseNumberOfResults(responseAs[String], 18)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedBetweenDates.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 5)

            }

        }

        "get the regions belonging to a page" in {
            val gravsearchQuery =
                """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?region knora-api:isMainResource true .
                  |
                  |        ?region knora-api:isRegionOf <http://rdfh.ch/9d626dc76c03> .
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
                  |        ?region knora-api:isRegionOf <http://rdfh.ch/9d626dc76c03> .
                  |        knora-api:isRegionOf knora-api:objectType knora-api:Resource .
                  |
                  |        <http://rdfh.ch/9d626dc76c03> a knora-api:Resource .
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/RegionsForPage.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

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
                  |    <http://rdfh.ch/50e7460a7203> knora-api:isPartOf ?book .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:seqnum ?seqnum .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:hasStillImageFile ?file .
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
                  |    <http://rdfh.ch/50e7460a7203> knora-api:isPartOf ?book .
                  |    knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |    <http://rdfh.ch/50e7460a7203> a knora-api:Resource .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:seqnum ?seqnum .
                  |    knora-api:seqnum knora-api:objectType xsd:integer .
                  |
                  |    ?seqnum a xsd:integer .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:hasStillImageFile ?file .
                  |    knora-api:hasStillImageFile knora-api:objectType knora-api:File .
                  |
                  |    ?file a knora-api:File .
                  |
                  |} OFFSET 0
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/bookWithIncomingPagesWithAllRequestedProps.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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
                  |    <http://rdfh.ch/50e7460a7203> knora-api:isPartOf ?book .
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
                  |    <http://rdfh.ch/50e7460a7203> knora-api:isPartOf ?book .
                  |    knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |    <http://rdfh.ch/50e7460a7203> a knora-api:Resource .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:seqnum ?seqnum .
                  |    knora-api:seqnum knora-api:objectType xsd:integer .
                  |
                  |    ?seqnum a xsd:integer .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:hasStillImageFile ?file .
                  |    knora-api:hasStillImageFile knora-api:objectType knora-api:File .
                  |
                  |    ?file a knora-api:File .
                  |
                  |} OFFSET 0
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/bookWithIncomingPagesOnlyLink.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "get incoming links pointing to an incunbaula:book, excluding isPartOf and isRegionOf" in {
            var gravsearchQuery =
                """
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |
                  |     ?incomingRes knora-api:isMainResource true .
                  |
                  |     ?incomingRes ?incomingProp <http://rdfh.ch/8be1b7cf7103> .
                  |
                  |} WHERE {
                  |
                  |     ?incomingRes a knora-api:Resource .
                  |
                  |     ?incomingRes ?incomingProp <http://rdfh.ch/8be1b7cf7103> .
                  |
                  |     <http://rdfh.ch/8be1b7cf7103> a knora-api:Resource .
                  |
                  |     ?incomingProp knora-api:objectType knora-api:Resource .
                  |
                  |     knora-api:isRegionOf knora-api:objectType knora-api:Resource .
                  |     knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |     FILTER NOT EXISTS {
                  |         ?incomingRes  knora-api:isRegionOf <http://rdfh.ch/8be1b7cf7103> .
                  |     }
                  |
                  |     FILTER NOT EXISTS {
                  |         ?incomingRes  knora-api:isPartOf <http://rdfh.ch/8be1b7cf7103> .
                  |     }
                  |
                  |} OFFSET 0
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/IncomingLinksForBook.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "search for an anything:Thing that has a decimal value of 2.1" ignore { // literals are not supported
            val gravsearchQuery =
                """
                  |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |     ?thing knora-api:isMainResource true .
                  |
                  |     ?thing anything:hasDecimal "2.1"^^xsd:decimal .
                  |} WHERE {
                  |
                  |     ?thing a anything:Thing .
                  |     ?thing a knora-api:Resource .
                  |
                  |     ?thing anything:hasDecimal "2.1"^^xsd:decimal .
                  |     anything:hasDecimal knora-api:objectType xsd:decimal .
                  |
                  |}
                  |
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingEqualsDecimal.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingBiggerThanDecimal.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingSmallerThanDecimal.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/thingWithURI.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

        }

        "search for an anything:Thing that has a Boolean value that is true" ignore { // literals are not supported
            val gravsearchQuery =
                """
                  |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |     ?thing knora-api:isMainResource true .
                  |
                  |     ?thing anything:hasBoolean true
                  |} WHERE {
                  |
                  |     ?thing a anything:Thing .
                  |     ?thing a knora-api:Resource .
                  |
                  |     ?thing anything:hasBoolean true .
                  |     anything:hasBoolean knora-api:objectType xsd:boolean .
                  |
                  |}
                  |
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

        }

        "search for an anything:Thing that has a Boolean value that is true 2" in {
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithBoolean.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)
            }

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
                  |} OFFSET 0
                  |
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithBooleanOptionalOffset0.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the second page of results
                checkSearchResponseNumberOfResults(responseAs[String], 25)
            }

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
                  |} OFFSET 1
                  |
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)
                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithBooleanOptionalOffset1.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the second page of results
                checkSearchResponseNumberOfResults(responseAs[String], 17)
            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithBooleanOrDecimal.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksWithTitleContainingZeit.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

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
                  |        FILTER knora-api:match(?propVal0, "Zeitglöcklein")
                  |
                  |     }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksWithTitleContainingZeitgloecklein.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }


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
                  |        FILTER knora-api:match(?propVal0, "Zeitglöcklein Lebens")
                  |
                  |     }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksWithTitleContainingZeitgloecklein.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }


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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksWithTitleContainingZeitgloecklein.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

        }

        "search for an anything:Thing with a list value" ignore { // ignored because the list node label returned is not deterministic

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
                  |        anything:hasListItem knora-api:objectType xsd:string .
                  |
                  |        ?listItem a xsd:string .
                  |
                  |    } OFFSET 0
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithListValue.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 3)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/LanguageFulltextSearch.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)
            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/LanguageFulltextSearch.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)
            }
        }

        "perform a fulltext search for 'Bonjour'" in {
            Get("/v2/search/Bonjour") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)
                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/LanguageFulltextSearch.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])
            }

        }

        "do a fulltext search for the term 'text' marked up as a paragraph" in {

            Get("/v2/search/text?limitToStandoffClass=" + URLEncoder.encode("http://api.knora.org/ontology/standoff/v2#StandoffParagraphTag", "UTF-8")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithRichtextWithTermTextInParagraph.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "do a fulltext search count query for the term 'text' marked up as a paragraph" in {

            Get("/v2/search/count/text?limitToStandoffClass=" + URLEncoder.encode("http://api.knora.org/ontology/standoff/v2#StandoffParagraphTag", "UTF-8")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkCountResponse(responseAs[String], 1)

            }

        }

        "do a fulltext search for the term 'text' marked up as italic" in {

            Get("/v2/search/text?limitToStandoffClass=" + URLEncoder.encode("http://api.knora.org/ontology/standoff/v2#StandoffItalicTag", "UTF-8")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithRichtextWithTermTextInParagraph.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "do a fulltext search count query for the term 'text' marked up as italic" in {

            Get("/v2/search/count/text?limitToStandoffClass=" + URLEncoder.encode("http://api.knora.org/ontology/standoff/v2#StandoffItalicTag", "UTF-8")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkCountResponse(responseAs[String], 1)
            }

        }

        "do a fulltext search for the terms 'interesting' and 'text' marked up as italic" in {

            Get("/v2/search/interesting%20text?limitToStandoffClass=" + URLEncoder.encode("http://api.knora.org/ontology/standoff/v2#StandoffItalicTag", "UTF-8")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithRichtextWithTermTextInParagraph.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "do a fulltext search count query for the terms 'interesting' and 'text' marked up as italic" in {

            Get("/v2/search/interesting%20text?limitToStandoffClass=" + URLEncoder.encode("http://api.knora.org/ontology/standoff/v2#StandoffItalicTag", "UTF-8")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkSearchResponseNumberOfResults(responseAs[String], 1)
            }

        }

        "do a fulltext search for the terms 'interesting' and 'boring' marked up as italic" in {

            Get("/v2/search/interesting%20boring?limitToStandoffClass=" + URLEncoder.encode("http://api.knora.org/ontology/standoff/v2#StandoffItalicTag", "UTF-8")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                // there is no single italic element that contains both 'interesting' and 'boring':

                /*
                    <?xml version="1.0" encoding="UTF-8"?>
                    <text>
                     <p>
                         This is a test that contains marked up elements. This is <em>interesting text</em> in italics. This is <em>boring text</em> in italics.
                     </p>
                    </text>
                */

                checkSearchResponseNumberOfResults(responseAs[String], 0)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/LinkObjectsToBooks.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 3)

            }


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


            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithAuthor.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
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


            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithPersonWithName.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
        }

        "do a Gravsearch query for a letter that links to a person with a specified name (optional)" in {

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
                  |        OPTIONAL {
                  |             ?person1 beol:hasFamilyName ?name .
                  |
                  |             beol:hasFamilyName knora-api:objectType xsd:string .
                  |             ?name a xsd:string .
                  |
                  |             FILTER(?name = "Meier")
                  |        }
                  |
                  |    } ORDER BY ?date
                """.stripMargin


            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithPersonWithNameOptional.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
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


            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithPersonWithName2.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/foafPerson.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingByIriWithRequestedValues.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])
            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithAuthorWithInformation.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
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
                  |        BIND(<http://rdfh.ch/b6b5ff1eb703> AS ?book)
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/incomingPagesForBook.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
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
                  |        BIND(<http://rdfh.ch/b6b5ff1eb703> AS ?book)
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.BAD_REQUEST, response.toString)

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/regionsOfZeitgloecklein.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ProjectsWithOptionalPersonOrBiblio.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithPersonWithName2.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithPersonWithName2.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithPersonWithName2.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithPersonWithName2.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithPersonWithName2.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithPersonWithoutName.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
        }

        "do a Gravsearch query that finds all the books that have a page with seqnum 100, inferring types (with type inference)" in {
            val gravsearchQuery =
                """
                  |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |
                  |  ?book knora-api:isMainResource true ;
                  |    incunabula:title ?title .
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/booksWithPage100.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/lettersByMeier.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)).addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ZeitgloeckleinExtendedSearchWithTitleInAnswerSimple.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)).addHeader(new SchemaHeader(RouteUtilV2.SIMPLE_SCHEMA_NAME)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ZeitgloeckleinExtendedSearchWithTitleInAnswerSimple.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended/count", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkCountResponse(responseAs[String], 2)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ZeitgloeckleinExtendedSearchNoTitleInAnswer.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/NotZeitgloeckleinExtendedSearch.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended/count", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                // 19 - 2 = 18 :-)
                // there is a total of 19 incunabula books of which two have the title "Zeitglöcklein des Lebens und Leidens Christi" (see test above)
                // however, there are 18 books that have a title that is not "Zeitglöcklein des Lebens und Leidens Christi"
                // this is because there is a book that has two titles, one "Zeitglöcklein des Lebens und Leidens Christi" and the other in Latin "Horologium devotionis circa vitam Christi"

                checkCountResponse(responseAs[String], 18)

            }

        }

        "perform a Gravsearch query for the page of a book whose seqnum equals 10, returning the seqnum  and the link value (with type inference)" in {

            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |
                  |        FILTER(?seqnum = 10)
                  |
                  |    }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/PageWithSeqnum10WithSeqnumAndLinkValueInAnswer.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])


            }

        }

        "perform a Gravsearch count query for the page of a book whose seqnum equals 10, returning the seqnum  and the link value (with type inference)" in {

            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |
                  |        FILTER(?seqnum = 10)
                  |
                  |    }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkSearchResponseNumberOfResults(responseAs[String], 1)


            }

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
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |
                  |        FILTER(?seqnum = 10)
                  |
                  |    }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/PageWithSeqnum10OnlySeqnuminAnswer.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])


            }

        }


        "perform a Gravsearch query for the pages of a book whose seqnum is lower than or equals 10 (with type inference)" in {

            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |
                  |        FILTER(?seqnum <= 10)
                  |
                  |    } ORDER BY ?seqnum
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/pagesOfLatinNarrenschiffWithSeqnumLowerEquals10.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum (with type inference)" in {

            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |
                  |    } ORDER BY ?seqnum
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/PagesOfNarrenschiffOrderedBySeqnum.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum and get the next OFFSET (with type inference)" in {

            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |
                  |    } ORDER BY ?seqnum
                  |    OFFSET 1
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/PagesOfNarrenschiffOrderedBySeqnumNextOffset.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform a Gravsearch query for books that have been published on the first of March 1497 (Julian Calendar) (with type inference)" ignore { // literals are not supported
            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?book knora-api:isMainResource true .
                  |
                  |        ?book incunabula:title ?title .
                  |
                  |        ?book incunabula:pubdate "JULIAN:1497-03-01"^^knora-api:Date .
                  |    } WHERE {
                  |
                  |        ?book a incunabula:book .
                  |
                  |        ?book incunabula:title ?title .
                  |
                  |        ?book incunabula:pubdate "JULIAN:1497-03-01"^^knora-api:Date .
                  |
                  |    }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedOnDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksNotPublishedOnDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
                checkSearchResponseNumberOfResults(responseAs[String], 17)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksNotPublishedOnDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
                checkSearchResponseNumberOfResults(responseAs[String], 17)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedBeforeDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 4 (number of results from query below)
                checkSearchResponseNumberOfResults(responseAs[String], 15)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedAfterOrOnDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 15 (number of results from query above)
                checkSearchResponseNumberOfResults(responseAs[String], 4)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedAfterDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 18 (number of results from query above)
                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedBeforeOrOnDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 1 (number of results from query above)
                checkSearchResponseNumberOfResults(responseAs[String], 18)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedBetweenDates.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 5)

            }

        }

        "get the regions belonging to a page (with type inference)" in {
            val gravsearchQuery =
                """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?region knora-api:isMainResource true .
                  |
                  |        ?region knora-api:isRegionOf <http://rdfh.ch/9d626dc76c03> .
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
                  |        ?region knora-api:isRegionOf <http://rdfh.ch/9d626dc76c03> .
                  |
                  |        ?region knora-api:hasGeometry ?geom .
                  |
                  |        ?region knora-api:hasComment ?comment .
                  |
                  |        ?region knora-api:hasColor ?color .
                  |
                  |    }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/RegionsForPage.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

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
                  |    <http://rdfh.ch/50e7460a7203> knora-api:isPartOf ?book .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:seqnum ?seqnum .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:hasStillImageFile ?file .
                  |
                  |} WHERE {
                  |
                  |    ?book a incunabula:book .
                  |
                  |    ?book incunabula:title ?title .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:isPartOf ?book .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:seqnum ?seqnum .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:hasStillImageFile ?file .
                  |
                  |} OFFSET 0
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/bookWithIncomingPagesWithAllRequestedProps.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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
                  |    <http://rdfh.ch/50e7460a7203> knora-api:isPartOf ?book .
                  |
                  |} WHERE {
                  |
                  |    ?book a incunabula:book .
                  |
                  |    ?book incunabula:title ?title .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:isPartOf ?book .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:seqnum ?seqnum .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:hasStillImageFile ?file .
                  |
                  |} OFFSET 0
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/bookWithIncomingPagesOnlyLink.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "get incoming links pointing to an incunbaula:book, excluding isPartOf and isRegionOf (with type inference)" in {
            var gravsearchQuery =
                """
                  |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |
                  |     ?incomingRes knora-api:isMainResource true .
                  |
                  |     ?incomingRes ?incomingProp <http://rdfh.ch/8be1b7cf7103> .
                  |
                  |} WHERE {
                  |
                  |     ?incomingRes ?incomingProp <http://rdfh.ch/8be1b7cf7103> .
                  |
                  |     <http://rdfh.ch/8be1b7cf7103> a incunabula:book .
                  |
                  |     FILTER NOT EXISTS {
                  |         ?incomingRes knora-api:isRegionOf <http://rdfh.ch/8be1b7cf7103> .
                  |     }
                  |
                  |     FILTER NOT EXISTS {
                  |         ?incomingRes knora-api:isPartOf <http://rdfh.ch/8be1b7cf7103> .
                  |     }
                  |
                  |} OFFSET 0
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/IncomingLinksForBook.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "search for an anything:Thing that has a decimal value of 2.1 (with type inference)" ignore { // literals are not supported
            val gravsearchQuery =
                """
                  |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |     ?thing knora-api:isMainResource true .
                  |
                  |     ?thing anything:hasDecimal"2.1"^^xsd:decimal .
                  |} WHERE {
                  |
                  |     ?thing a anything:Thing .
                  |
                  |     ?thing anything:hasDecimal "2.1"^^xsd:decimal .
                  |
                  |}
                  |
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingEqualsDecimal.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingBiggerThanDecimal.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingSmallerThanDecimal.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

        }

        "search for an anything:Thing that has a Boolean value that is true (with type inference)" ignore { // literals are not supported
            val gravsearchQuery =
                """
                  |PREFIX anything: <http://0.0.0.0:3333/ontology/0001/anything/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |     ?thing knora-api:isMainResource true .
                  |
                  |     ?thing anything:hasBoolean true
                  |} WHERE {
                  |
                  |     ?thing a anything:Thing .
                  |
                  |     ?thing anything:hasBoolean true .
                  |
                  |}
                  |
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithBoolean.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)
            }

        }

        "search for an anything:Thing that may have a Boolean value that is true (with type inference)" in {
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
                  |
                  |         FILTER(?boolean = true)
                  |     }
                  |} OFFSET 1
                  |
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithBooleanOptionalOffset1.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the second page of results
                checkSearchResponseNumberOfResults(responseAs[String], 17)
            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithBooleanOrDecimal.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksWithTitleContainingZeit.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

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
                  |        FILTER knora-api:match(?propVal0, "Zeitglöcklein")
                  |
                  |     }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksWithTitleContainingZeitgloecklein.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }


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
                  |        FILTER knora-api:match(?propVal0, "Zeitglöcklein Lebens")
                  |
                  |     }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksWithTitleContainingZeitgloecklein.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }


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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksWithTitleContainingZeitgloecklein.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

        }

        "search for an anything:Thing with a list value (with type inference)" ignore { // ignored because the list node label returned is not deterministic

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithListValue.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 3)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/LanguageFulltextSearch.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)
            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/LanguageFulltextSearch.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)
            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/LinkObjectsToBooks.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 3)

            }


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


            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithAuthor.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
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


            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithPersonWithName.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
        }

        "do a Gravsearch query for a letter that links to a person with a specified name (optional) (with type inference)" in {

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
                  |        OPTIONAL {
                  |             ?person1 beol:hasFamilyName ?name .
                  |
                  |             FILTER(?name = "Meier")
                  |        }
                  |
                  |    } ORDER BY ?date
                """.stripMargin


            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithPersonWithNameOptional.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
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


            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithPersonWithName2.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/foafPerson.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingByIriWithRequestedValues.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])
            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithAuthorWithInformation.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
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
                  |        BIND(<http://rdfh.ch/b6b5ff1eb703> AS ?book)
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/incomingPagesForBook.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
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
                  |        BIND(<http://rdfh.ch/b6b5ff1eb703> AS ?book)
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.BAD_REQUEST, response.toString)

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/regionsOfZeitgloecklein.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ProjectsWithOptionalPersonOrBiblio.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // Queries that submit the complex schema

        "do a Gravsearch query that finds all the books that have a page with seqnum 100 (submitting the complex schema)" in {
            val gravsearchQuery =
                """
                  |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
                  |
                  |CONSTRUCT {
                  |
                  |  ?book knora-api:isMainResource true ;
                  |    incunabula:title ?title .
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/booksWithPage100.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/lettersByMeier.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended/count", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkCountResponse(responseAs[String], 2)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ZeitgloeckleinExtendedSearchNoTitleInAnswer.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/NotZeitgloeckleinExtendedSearch.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended/count", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                // 19 - 2 = 18 :-)
                // there is a total of 19 incunabula books of which two have the title "Zeitglöcklein des Lebens und Leidens Christi" (see test above)
                // however, there are 18 books that have a title that is not "Zeitglöcklein des Lebens und Leidens Christi"
                // this is because there is a book that has two titles, one "Zeitglöcklein des Lebens und Leidens Christi" and the other in Latin "Horologium devotionis circa vitam Christi"

                checkCountResponse(responseAs[String], 18)

            }

        }

        "perform a Gravsearch query for the page of a book whose seqnum equals 10, returning the seqnum and the link value (submitting the complex schema)" in {

            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |
                  |        ?seqnum knora-api:intValueAsInt 10 .
                  |
                  |    }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/PageWithSeqnum10WithSeqnumAndLinkValueInAnswer.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])


            }

        }

        "perform a Gravsearch count query for the page of a book whose seqnum equals 10, returning the seqnum and the link value (submitting the complex schema)" in {

            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |
                  |        ?seqnum knora-api:intValueAsInt 10 .
                  |    }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkSearchResponseNumberOfResults(responseAs[String], 1)


            }

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
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |
                  |        ?seqnum knora-api:intValueAsInt 10 .
                  |
                  |    }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/PageWithSeqnum10OnlySeqnuminAnswer.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])


            }

        }


        "perform a Gravsearch query for the pages of a book whose seqnum is lower than or equals 10 (submitting the complex schema)" in {

            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |
                  |        ?seqnum knora-api:intValueAsInt ?seqnumInt .
                  |
                  |        FILTER(?seqnumInt <= 10)
                  |
                  |    } ORDER BY ?seqnum
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/pagesOfLatinNarrenschiffWithSeqnumLowerEquals10.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum (submitting the complex schema)" in {

            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |
                  |    } ORDER BY ?seqnum
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/PagesOfNarrenschiffOrderedBySeqnum.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform a Gravsearch query for the pages of a book and return them ordered by their seqnum and get the next OFFSET (submitting the complex schema)" in {

            val gravsearchQuery =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |
                  |        ?page knora-api:isPartOf <http://rdfh.ch/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |
                  |    } ORDER BY ?seqnum
                  |    OFFSET 1
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/PagesOfNarrenschiffOrderedBySeqnumNextOffset.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedOnDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksNotPublishedOnDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
                checkSearchResponseNumberOfResults(responseAs[String], 17)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksNotPublishedOnDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
                checkSearchResponseNumberOfResults(responseAs[String], 17)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedBeforeDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 4 (number of results from query below)
                checkSearchResponseNumberOfResults(responseAs[String], 15)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedAfterOrOnDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 15 (number of results from query above)
                checkSearchResponseNumberOfResults(responseAs[String], 4)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedAfterDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 18 (number of results from query above)
                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedBeforeOrOnDate.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 1 (number of results from query above)
                checkSearchResponseNumberOfResults(responseAs[String], 18)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksPublishedBetweenDates.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 5)

            }

        }

        "get the regions belonging to a page (submitting the complex schema)" in {
            val gravsearchQuery =
                """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
                  |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?region knora-api:isMainResource true .
                  |
                  |        ?region knora-api:isRegionOf <http://rdfh.ch/9d626dc76c03> .
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
                  |        ?region knora-api:isRegionOf <http://rdfh.ch/9d626dc76c03> .
                  |
                  |        ?region knora-api:hasGeometry ?geom .
                  |
                  |        ?region knora-api:hasComment ?comment .
                  |
                  |        ?region knora-api:hasColor ?color .
                  |
                  |    }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/RegionsForPage.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

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
                  |    <http://rdfh.ch/50e7460a7203> knora-api:isPartOf ?book .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:seqnum ?seqnum .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:hasStillImageFileValue ?file .
                  |
                  |} WHERE {
                  |
                  |    ?book a incunabula:book .
                  |
                  |    ?book incunabula:title ?title .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:isPartOf ?book .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:seqnum ?seqnum .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:hasStillImageFileValue ?file .
                  |
                  |} OFFSET 0
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/bookWithIncomingPagesWithAllRequestedProps.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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
                  |    <http://rdfh.ch/50e7460a7203> knora-api:isPartOf ?book .
                  |
                  |} WHERE {
                  |
                  |    ?book a incunabula:book .
                  |
                  |    ?book incunabula:title ?title .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:isPartOf ?book .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:seqnum ?seqnum .
                  |
                  |    <http://rdfh.ch/50e7460a7203> knora-api:hasStillImageFileValue ?file .
                  |
                  |} OFFSET 0
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/bookWithIncomingPagesOnlyLink.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "get incoming links pointing to an incunbaula:book, excluding isPartOf and isRegionOf (submitting the complex schema)" in {
            var gravsearchQuery =
                """
                  |PREFIX incunabula: <http://0.0.0.0:3333/ontology/0803/incunabula/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/v2#>
                  |
                  |CONSTRUCT {
                  |
                  |     ?incomingRes knora-api:isMainResource true .
                  |
                  |     ?incomingRes ?incomingProp <http://rdfh.ch/8be1b7cf7103> .
                  |
                  |} WHERE {
                  |
                  |     ?incomingRes ?incomingProp <http://rdfh.ch/8be1b7cf7103> .
                  |
                  |     <http://rdfh.ch/8be1b7cf7103> a incunabula:book .
                  |
                  |     FILTER NOT EXISTS {
                  |         ?incomingRes knora-api:isRegionOf <http://rdfh.ch/8be1b7cf7103> .
                  |     }
                  |
                  |     FILTER NOT EXISTS {
                  |         ?incomingRes knora-api:isPartOf <http://rdfh.ch/8be1b7cf7103> .
                  |     }
                  |
                  |} OFFSET 0
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/IncomingLinksForBook.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingEqualsDecimal.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingBiggerThanDecimal.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }

        }

        "search for an anything:Thing that has a decimal value smaller than 3.0 (submitting the complex schema)" in {
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
                  |     FILTER(?decimalDec < "3"^^xsd:decimal)
                  |}
                  |
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingSmallerThanDecimal.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithBoolean.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)
            }

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
                  |
                  |         ?thing anything:hasBoolean ?boolean .
                  |
                  |         ?boolean knora-api:booleanValueAsBoolean true .
                  |     }
                  |} OFFSET 1
                  |
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithBooleanOptionalOffset1.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the second page of results
                checkSearchResponseNumberOfResults(responseAs[String], 17)
            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithBooleanOrDecimal.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksWithTitleContainingZeit.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }

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
                  |        ?title knora-api:valueAsString ?titleStr .
                  |
                  |        FILTER knora-api:match(?titleStr, "Zeitglöcklein")
                  |
                  |     }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksWithTitleContainingZeitgloecklein.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }


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
                  |        ?title knora-api:valueAsString ?titleStr .
                  |
                  |        FILTER knora-api:match(?titleStr, "Zeitglöcklein Lebens")
                  |
                  |     }
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/BooksWithTitleContainingZeitgloecklein.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 2)

            }


        }

        "search for an anything:Thing with a list value (submitting the complex schema)" ignore { // ignored because the list node label returned is not deterministic

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithListValue.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 3)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/LanguageFulltextSearch.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)
            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/LanguageFulltextSearch.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)
            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/LinkObjectsToBooks.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 3)

            }


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


            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithAuthor.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
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


            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithPersonWithName.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
        }

        "do a Gravsearch query for a letter that links to a person with a specified name (optional) (submitting the complex schema)" in {

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
                  |        OPTIONAL {
                  |             ?person1 beol:hasFamilyName ?name .
                  |
                  |             ?name knora-api:valueAsString "Meier" .
                  |        }
                  |
                  |    } ORDER BY ?date
                """.stripMargin


            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithPersonWithNameOptional.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
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


            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithPersonWithName2.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkSearchResponseNumberOfResults(responseAs[String], 1)

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingByIriWithRequestedValues.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])
            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithAuthorWithInformation.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
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
                  |        BIND(<http://rdfh.ch/b6b5ff1eb703> AS ?book)
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/incomingPagesForBook.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
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
                  |        BIND(<http://rdfh.ch/b6b5ff1eb703> AS ?book)
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.BAD_REQUEST, response.toString)

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/regionsOfZeitgloecklein.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.BAD_REQUEST, response.toString)

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.BAD_REQUEST, response.toString)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.BAD_REQUEST, response.toString)

            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.BAD_REQUEST, response.toString)

            }
        }

        "search for a list value that refers to a particular list node (submitting the complex schema)" ignore { // ignored because the list node label returned is not deterministic
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/thingReferringToSpecificListNode.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])
            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/letterWithSubject.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])
            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/thingsWithStandoffLinks.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])
            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/thingsWithStandoffLinks.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])
            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/thingsWithStandoffLinksToSpecificThing.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])
            }
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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/thingsWithStandoffLinksToSpecificThing.jsonld"), writeTestDataFiles)
                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])
            }
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
                  |    ?text knora-api:valueAsString ?textStr .
                  |    ?text knora-api:textValueHasStandoff ?standoffTag .
                  |    ?standoffTag a standoff:StandoffItalicTag .
                  |    FILTER knora-api:matchInStandoff(?textStr, ?standoffTag, "interesting text")
                  |}
                """.stripMargin

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = readOrWriteTextFile(responseAs[String], new File("src/test/resources/test-data/searchR2RV2/ThingWithRichtextWithTermTextInParagraph.jsonld"), writeTestDataFiles)

                compareJSONLDForResourcesResponse(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])
            }

        }

        "search for a standoff date tag indicating a date in a particular range (submitting the complex schema)" in {
            // First, create a standoff-to-XML mapping that can handle standoff date tags.

            val mappingFileToSend = new File("_test_data/test_route/texts/mappingForHTML.xml")

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
                   |        "knora-api": "${OntologyConstants.KnoraApiV2WithValueObjects.KnoraApiV2PrefixExpansion}"
                   |    }
                   |}
                """.stripMargin

            val formDataMapping = Multipart.FormData(
                Multipart.FormData.BodyPart(
                    "json",
                    HttpEntity(ContentTypes.`application/json`, paramsCreateHTMLMappingFromXML)
                ),
                Multipart.FormData.BodyPart(
                    "xml",
                    HttpEntity.fromPath(ContentTypes.`text/xml(UTF-8)`, mappingFileToSend.toPath),
                    Map("filename" -> mappingFileToSend.getName)
                )
            )

            // send mapping xml to route
            Post("/v2/mapping", formDataMapping) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> standoffPath ~> check {

                assert(status == StatusCodes.OK)

            }


            // Next, create a resource with a text value containing a standoff date tag. Use API v1, because v2 doesn't yet have update routes.

            val xmlFileToSend = new File("_test_data/test_route/texts/HTML.xml")

            val newValueParams =
                s"""
                   |{
                   |  "project_id": "http://rdfh.ch/projects/0001",
                   |  "res_id": "http://rdfh.ch/0001/a-thing",
                   |  "prop": "http://www.knora.org/ontology/0001/anything#hasText",
                   |  "richtext_value": {
                   |        "xml": ${JsString(FileUtil.readTextFile(xmlFileToSend))},
                   |        "mapping_id": "$anythingProjectIri/mappings/HTMLMapping"
                   |  }
                   |}
                 """.stripMargin

            Post("/v1/values", HttpEntity(ContentTypes.`application/json`, newValueParams)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> valuesPath ~> check {

                assert(status == StatusCodes.OK)

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                assert(responseAs[String].contains("we will have a party"))

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                assert(responseAs[String].contains("we will have a party"))

            }

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

            Post("/v2/searchextended", HttpEntity(SparqlQueryConstants.`application/sparql-query`, gravsearchQuery)) ~> addCredentials(BasicHttpCredentials(incunabulaUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.NOT_FOUND, response.toString)

            }
        }

    }
}
