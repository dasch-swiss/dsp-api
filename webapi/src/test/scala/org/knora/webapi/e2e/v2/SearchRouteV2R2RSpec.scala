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

import akka.actor.{ActorSystem, Props}
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.e2e.v2.ResponseCheckerR2RV2._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.messages.v1.responder.ontologymessages.LoadOntologiesRequest
import org.knora.webapi.responders.{ResponderManager, _}
import org.knora.webapi.routing.v2.SearchRouteV2
import org.knora.webapi.store._
import org.knora.webapi.util.FileUtil

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor}


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

    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val searchPath = SearchRouteV2.knoraApiPath(system, settings, log)

    implicit private val timeout: Timeout = settings.defaultRestoreTimeout

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(15).second)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    private val anythingUser = SharedTestDataV1.anythingUser1
    private val anythingUserEmail = anythingUser.userData.email.get

    private val password = "test"

    private val rdfDataObjects = List(

        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")

    )

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 360.seconds)
        Await.result(responderManager ? LoadOntologiesRequest(SharedTestDataV1.rootUser), 10.seconds)
    }

    "The Search v2 Endpoint" should {
        "perform a fulltext search for 'Narr'" in {

            Get("/v2/search/Narr") ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/NarrFulltextSearch.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a count query for a fulltext search for 'Narr'" in {

            Get("/v2/search/count/Narr") ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkCountQuery(responseAs[String], 210)

            }
        }


        "perform a fulltext search for 'Dinge'" in {
            Get("/v2/search/Dinge") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/DingeFulltextSearch.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }
        }

        "perform a count query for a fulltext search for 'Dinge'" in {
            Get("/v2/search/count/Dinge") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkCountQuery(responseAs[String], 1)

            }
        }

        "perform an extended search for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer" in {
            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
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

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/ZeitgloeckleinExtendedSearchWithTitleInAnswer.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform a count query for an extended search for books that have the title 'Zeitglöcklein des Lebens' returning the title in the answer" in {
            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
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

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/count/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkCountQuery(responseAs[String], 2)

            }

        }


        "perform an extended search for books that have the title 'Zeitglöcklein des Lebens' not returning the title in the answer" in {
            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
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

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/ZeitgloeckleinExtendedSearchNoTitleInAnswer.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform an extended search for books that do not have the title 'Zeitglöcklein des Lebens'" in {
            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
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

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/NotZeitgloeckleinExtendedSearch.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform a count query for an extended search for books that do not have the title 'Zeitglöcklein des Lebens'" in {
            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
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

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/count/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                // 19 - 2 = 18 :-)
                // there is a total of 19 incunabula books of which two have the title "Zeitglöcklein des Lebens und Leidens Christi" (see test above)
                // however, there are 18 books that have a title that is not "Zeitglöcklein des Lebens und Leidens Christi"
                // this is because there is a book that has two titles, one "Zeitglöcklein des Lebens und Leidens Christi" and the other in Latin "Horologium devotionis circa vitam Christi"

                checkCountQuery(responseAs[String], 18)

            }

        }

        "perform an extended search for the page of a book whose seqnum equals 10, returning the seqnum  and the link value" in {

            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://data.knora.org/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |        ?page a knora-api:Resource .
                  |
                  |        ?page knora-api:isPartOf <http://data.knora.org/b6b5ff1eb703> .
                  |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |        <http://data.knora.org/b6b5ff1eb703> a knora-api:Resource .
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

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/PageWithSeqnum10WithSeqnumAndLinkValueInAnswer.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])


            }

        }

        "perform a count query for an extended search for the page of a book whose seqnum equals 10, returning the seqnum  and the link value" in {

            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://data.knora.org/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |        ?page a knora-api:Resource .
                  |
                  |        ?page knora-api:isPartOf <http://data.knora.org/b6b5ff1eb703> .
                  |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |        <http://data.knora.org/b6b5ff1eb703> a knora-api:Resource .
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

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkCountQuery(responseAs[String], 1)


            }

        }

        "perform an extended search for the page of a book whose seqnum equals 10, returning only the seqnum" in {

            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
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
                  |        ?page knora-api:isPartOf <http://data.knora.org/b6b5ff1eb703> .
                  |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |        <http://data.knora.org/b6b5ff1eb703> a knora-api:Resource .
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

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/PageWithSeqnum10OnlySeqnuminAnswer.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])


            }

        }


        "perform an extended search for the pages of a book whose seqnum is lower than or equals 10" in {

            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://data.knora.org/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |        ?page a knora-api:Resource .
                  |
                  |        ?page knora-api:isPartOf <http://data.knora.org/b6b5ff1eb703> .
                  |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |        <http://data.knora.org/b6b5ff1eb703> a knora-api:Resource .
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

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/pagesOfLatinNarrenschiffWithSeqnumLowerEquals10.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform an extended search for the pages of a book and return them ordered by their seqnum" in {

            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://data.knora.org/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |        ?page a knora-api:Resource .
                  |
                  |        ?page knora-api:isPartOf <http://data.knora.org/b6b5ff1eb703> .
                  |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |        <http://data.knora.org/b6b5ff1eb703> a knora-api:Resource .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |        incunabula:seqnum knora-api:objectType xsd:integer .
                  |
                  |        ?seqnum a xsd:integer .
                  |
                  |    } ORDER BY ?seqnum
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/PagesOfNarrenschiffOrderedBySeqnum.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "perform an extended search for the pages of a book and return them ordered by their seqnum and get the next OFFSET" in {

            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page knora-api:isPartOf <http://data.knora.org/b6b5ff1eb703> .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |    } WHERE {
                  |
                  |        ?page a incunabula:page .
                  |        ?page a knora-api:Resource .
                  |
                  |        ?page knora-api:isPartOf <http://data.knora.org/b6b5ff1eb703> .
                  |        knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |        <http://data.knora.org/b6b5ff1eb703> a knora-api:Resource .
                  |
                  |        ?page incunabula:seqnum ?seqnum .
                  |        incunabula:seqnum knora-api:objectType xsd:integer .
                  |
                  |        ?seqnum a xsd:integer .
                  |
                  |    } ORDER BY ?seqnum
                  |    OFFSET 1
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/PagesOfNarrenschiffOrderedBySeqnumNextOffset.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }


        "perform an extended search for books that have been published on the first of March 1497 (Julian Calendar)" ignore { // literals are not supported
            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?book knora-api:isMainResource true .
                  |
                  |        ?book a incunabula:book .
                  |
                  |        ?book incunabula:title ?title .
                  |
                  |        ?book incunabula:pubdate "JULIAN:1497-03-01" .
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
                  |        ?book incunabula:pubdate "JULIAN:1497-03-01" .
                  |        incunabula:pubdate knora-api:objectType knora-api:Date .
                  |
                  |    }
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkCountQuery(responseAs[String], 2)

            }

        }

        "perform an extended search for books that have been published on the first of March 1497 (Julian Calendar) (2)" in {
            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?book knora-api:isMainResource true .
                  |
                  |        ?book a incunabula:book .
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
                  |        FILTER(?pubdate = "JULIAN:1497-03-01")
                  |
                  |    } ORDER BY ?pubdate
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/BooksPublishedOnDate.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }


        "perform an extended search for books that have not been published on the first of March 1497 (Julian Calendar)" in {
            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?book knora-api:isMainResource true .
                  |
                  |        ?book a incunabula:book .
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
                  |        FILTER(?pubdate != "JULIAN:1497-03-01")
                  |
                  |    } ORDER BY ?pubdate
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/BooksNotPublishedOnDate.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
                checkCountQuery(responseAs[String], 17)

            }

        }

        "perform an extended search for books that have not been published on the first of March 1497 (Julian Calendar) 2" in {
            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?book knora-api:isMainResource true .
                  |
                  |        ?book a incunabula:book .
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
                  |         FILTER(?pubdate < "JULIAN:1497-03-01" || ?pubdate > "JULIAN:1497-03-01")
                  |
                  |    } ORDER BY ?pubdate
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/BooksNotPublishedOnDate.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
                checkCountQuery(responseAs[String], 17)

            }

        }

        "perform an extended search for books that have been published before 1497 (Julian Calendar)" in {
            val sparqlSimplified =
                """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
                  |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?book knora-api:isMainResource true .
                  |
                  |        ?book a incunabula:book .
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
                  |        FILTER(?pubdate < "JULIAN:1497")
                  |
                  |    } ORDER BY ?pubdate
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/BooksPublishedBeforeDate.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 4 (number of results from query below)
                checkCountQuery(responseAs[String], 15)

            }

        }

        "perform an extended search for books that have been published 1497 or later (Julian Calendar)" in {
            val sparqlSimplified =
                """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
                  |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?book knora-api:isMainResource true .
                  |
                  |        ?book a incunabula:book .
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
                  |        FILTER(?pubdate >= "JULIAN:1497")
                  |
                  |    } ORDER BY ?pubdate
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/BooksPublishedAfterOrOnDate.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 15 (number of results from query above)
                checkCountQuery(responseAs[String], 4)

            }

        }

        "perform an extended search for books that have been published after 1497 (Julian Calendar)" in {
            val sparqlSimplified =
                """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
                  |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?book knora-api:isMainResource true .
                  |
                  |        ?book a incunabula:book .
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
                  |        FILTER(?pubdate > "JULIAN:1497")
                  |
                  |    } ORDER BY ?pubdate
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/BooksPublishedAfterDate.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 18 (number of results from query above)
                checkCountQuery(responseAs[String], 1)

            }

        }

        "perform an extended search for books that have been published 1497 or before (Julian Calendar)" in {
            val sparqlSimplified =
                """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
                  |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?book knora-api:isMainResource true .
                  |
                  |        ?book a incunabula:book .
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
                  |        FILTER(?pubdate <= "JULIAN:1497")
                  |
                  |    } ORDER BY ?pubdate
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/BooksPublishedBeforeOrOnDate.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 1 (number of results from query above)
                checkCountQuery(responseAs[String], 18)

            }

        }

        "perform an extended search for books that have been published after 1486 and before 1491 (Julian Calendar)" in {
            val sparqlSimplified =
                """PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?book knora-api:isMainResource true .
                  |
                  |        ?book a incunabula:book .
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
                  |        FILTER(?pubdate > "JULIAN:1486" && ?pubdate < "JULIAN:1491")
                  |
                  |    } ORDER BY ?pubdate
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/BooksPublishedBetweenDates.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkCountQuery(responseAs[String], 5)

            }

        }

        "get the regions belonging to a page" in {
            val sparqlSimplified =
                """    PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>
                  |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?region knora-api:isMainResource true .
                  |
                  |        ?region a knora-api:Region .
                  |
                  |        ?region knora-api:isRegionOf <http://data.knora.org/9d626dc76c03> .
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
                  |        ?region knora-api:isRegionOf <http://data.knora.org/9d626dc76c03> .
                  |        knora-api:isRegionOf knora-api:objectType knora-api:Resource .
                  |
                  |        <http://data.knora.org/9d626dc76c03> a knora-api:Resource .
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

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/RegionsForPage.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkCountQuery(responseAs[String], 2)

            }

        }

        "get a book a page points to and include the page in the results (all properties present in WHERE clause)" in {
            val sparqlSimplified =
                """
            PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>

            CONSTRUCT {

                ?book knora-api:isMainResource true .

                ?book incunabula:title ?title .

                <http://data.knora.org/50e7460a7203> knora-api:isPartOf ?book .

                <http://data.knora.org/50e7460a7203> knora-api:seqnum ?seqnum .

                <http://data.knora.org/50e7460a7203> knora-api:hasStillImageFile ?file .

            } WHERE {

                ?book a knora-api:Resource .

                ?book incunabula:title ?title .

                incunabula:title knora-api:objectType xsd:string .

                ?title a xsd:string .

                <http://data.knora.org/50e7460a7203> knora-api:isPartOf ?book .
                knora-api:isPartOf knora-api:objectType knora-api:Resource .

                <http://data.knora.org/50e7460a7203> a knora-api:Resource .

                <http://data.knora.org/50e7460a7203> knora-api:seqnum ?seqnum .
                knora-api:seqnum knora-api:objectType xsd:integer .

                ?seqnum a xsd:integer .

                <http://data.knora.org/50e7460a7203> knora-api:hasStillImageFile ?file .
                knora-api:hasStillImageFile knora-api:objectType knora-api:File .

                ?file a knora-api:File .

            } OFFSET 0
            """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/bookWithIncomingPagesWithAllRequestedProps.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "get a book a page points to and only include the page's partOf link in the results (none of the other properties)" in {
            val sparqlSimplified =
                """
            PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
            PREFIX incunabula: <http://0.0.0.0:3333/ontology/incunabula/simple/v2#>

            CONSTRUCT {

                ?book knora-api:isMainResource true .

                ?book incunabula:title ?title .

                <http://data.knora.org/50e7460a7203> knora-api:isPartOf ?book .

            } WHERE {

                ?book a knora-api:Resource .

                ?book incunabula:title ?title .

                incunabula:title knora-api:objectType xsd:string .

                ?title a xsd:string .

                <http://data.knora.org/50e7460a7203> knora-api:isPartOf ?book .
                knora-api:isPartOf knora-api:objectType knora-api:Resource .

                <http://data.knora.org/50e7460a7203> a knora-api:Resource .

                <http://data.knora.org/50e7460a7203> knora-api:seqnum ?seqnum .
                knora-api:seqnum knora-api:objectType xsd:integer .

                ?seqnum a xsd:integer .

                <http://data.knora.org/50e7460a7203> knora-api:hasStillImageFile ?file .
                knora-api:hasStillImageFile knora-api:objectType knora-api:File .

                ?file a knora-api:File .

            } OFFSET 0
            """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/bookWithIncomingPagesOnlyLink.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "get incoming links pointing to an incunbaula:book, excluding isPartOf and isRegionOf" in {
            var sparqlSimplified =
                """
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |
                  |     ?incomingRes knora-api:isMainResource true .
                  |
                  |     ?incomingRes ?incomingProp <http://data.knora.org/8be1b7cf7103> .
                  |
                  |} WHERE {
                  |
                  |     ?incomingRes a knora-api:Resource .
                  |
                  |     ?incomingRes ?incomingProp <http://data.knora.org/8be1b7cf7103> .
                  |
                  |     <http://data.knora.org/8be1b7cf7103> a knora-api:Resource .
                  |
                  |     ?incomingProp knora-api:objectType knora-api:Resource .
                  |
                  |     knora-api:isRegionOf knora-api:objectType knora-api:Resource .
                  |     knora-api:isPartOf knora-api:objectType knora-api:Resource .
                  |
                  |     FILTER NOT EXISTS {
                  |         ?incomingRes  knora-api:isRegionOf <http://data.knora.org/8be1b7cf7103> .
                  |     }
                  |
                  |     FILTER NOT EXISTS {
                  |         ?incomingRes  knora-api:isPartOf <http://data.knora.org/8be1b7cf7103> .
                  |     }
                  |
                  |} OFFSET 0
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/IncomingLinksForBook.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

            }

        }

        "search for an anything:Thing that has a decimal value of 2.1" ignore { // literals are not supported
            val sparqlSimplified =
                """
                  |PREFIX anything: <http://0.0.0.0:3333/ontology/anything/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |     ?thing knora-api:isMainResource true .
                  |
                  |     ?thing a anything:Thing .
                  |
                  |     ?thing anything:hasDecimal 2.1
                  |} WHERE {
                  |
                  |     ?thing a anything:Thing .
                  |     ?thing a knora-api:Resource .
                  |
                  |     ?thing anything:hasDecimal 2.1 .
                  |     anything:hasDecimal knora-api:objectType xsd:decimal .
                  |
                  |}
                  |
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkCountQuery(responseAs[String], 1)

            }

        }

        "search for an anything:Thing that has a decimal value of 2.1 2" in {
            val sparqlSimplified =
                """
                  |PREFIX anything: <http://0.0.0.0:3333/ontology/anything/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |     ?thing knora-api:isMainResource true .
                  |
                  |     ?thing a anything:Thing .
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
                  |     FILTER(?decimal = 2.1)
                  |}
                  |
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/ThingEqualsDecimal.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkCountQuery(responseAs[String], 1)

            }

        }

        "search for an anything:Thing that has a decimal value bigger than 2.0" in {
            val sparqlSimplified =
                """
                  |PREFIX anything: <http://0.0.0.0:3333/ontology/anything/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |     ?thing knora-api:isMainResource true .
                  |
                  |     ?thing a anything:Thing .
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
                  |     FILTER(?decimal > 2)
                  |}
                  |
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/ThingBiggerThanDecimal.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkCountQuery(responseAs[String], 1)

            }

        }

        "search for an anything:Thing that has a decimal value smaller than 3.0" in {
            val sparqlSimplified =
                """
                  |PREFIX anything: <http://0.0.0.0:3333/ontology/anything/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |     ?thing knora-api:isMainResource true .
                  |
                  |     ?thing a anything:Thing .
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
                  |     FILTER(?decimal < 3)
                  |}
                  |
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/ThingSmallerThanDecimal.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkCountQuery(responseAs[String], 1)

            }

        }

        "search for an anything:Thing that has a Boolean value that is true" ignore { // literals are not supported
            val sparqlSimplified =
                """
                  |PREFIX anything: <http://0.0.0.0:3333/ontology/anything/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |     ?thing knora-api:isMainResource true .
                  |
                  |     ?thing a anything:Thing .
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

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkCountQuery(responseAs[String], 1)

            }

        }

        "search for an anything:Thing that has a Boolean value that is true 2" in {
            val sparqlSimplified =
                """
                  |PREFIX anything: <http://0.0.0.0:3333/ontology/anything/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |     ?thing knora-api:isMainResource true .
                  |
                  |     ?thing a anything:Thing .
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

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/ThingWithBoolean.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkCountQuery(responseAs[String], 1)

            }

        }

        "search for an anything:Thing that may have a Boolean value that is true" in {
            // set OFFSET to 1 to get "Testding for extended search"
            val sparqlSimplified =
                """
                  |PREFIX anything: <http://0.0.0.0:3333/ontology/anything/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |     ?thing knora-api:isMainResource true .
                  |
                  |     ?thing a anything:Thing .
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

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/ThingWithBooleanOptional.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                // this is the second page of results
                checkCountQuery(responseAs[String], 12)

            }

        }

        "search for an anything:Thing that either has a Boolean value that is true or a decimal value that equals 2.1 (or both)" in {

            val sparqlSimplified =
                """
                  |PREFIX anything: <http://0.0.0.0:3333/ontology/anything/simple/v2#>
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |CONSTRUCT {
                  |     ?thing knora-api:isMainResource true .
                  |
                  |     ?thing a anything:Thing .
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
                  |         FILTER(?decimal = 2.1)
                  |     }
                  |
                  |} OFFSET 0
                  |
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/ThingWithBooleanOrDecimal.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkCountQuery(responseAs[String], 1)

            }

        }

        "search for a book whose title contains 'Zeit' using the regex function" in {

            val sparqlSimplified =
                """
                  |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |    CONSTRUCT {
                  |
                  |        ?mainRes knora-api:isMainResource true .
                  |
                  |        ?mainRes <http://0.0.0.0:3333/ontology/incunabula/simple/v2#title> ?propVal0 .
                  |
                  |     } WHERE {
                  |
                  |        ?mainRes a knora-api:Resource .
                  |
                  |        ?mainRes a <http://0.0.0.0:3333/ontology/incunabula/simple/v2#book> .
                  |
                  |
                  |        ?mainRes <http://0.0.0.0:3333/ontology/incunabula/simple/v2#title> ?propVal0 .
                  |        <http://0.0.0.0:3333/ontology/incunabula/simple/v2#title> knora-api:objectType <http://www.w3.org/2001/XMLSchema#string> .
                  |        ?propVal0 a <http://www.w3.org/2001/XMLSchema#string> .
                  |
                  |        FILTER regex(?propVal0, "Zeit", "i")
                  |
                  |     }
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/BooksWithTitleContainingZeit.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkCountQuery(responseAs[String], 2)

            }

        }

        "search for a book whose title contains 'Zeitglöcklein' using the contains function" in {

            val sparqlSimplified =
                """
                  |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |    CONSTRUCT {
                  |
                  |        ?mainRes knora-api:isMainResource true .
                  |
                  |        ?mainRes <http://0.0.0.0:3333/ontology/incunabula/simple/v2#title> ?propVal0 .
                  |
                  |     } WHERE {
                  |
                  |        ?mainRes a knora-api:Resource .
                  |
                  |        ?mainRes a <http://0.0.0.0:3333/ontology/incunabula/simple/v2#book> .
                  |
                  |        ?mainRes <http://0.0.0.0:3333/ontology/incunabula/simple/v2#title> ?propVal0 .
                  |        <http://0.0.0.0:3333/ontology/incunabula/simple/v2#title> knora-api:objectType <http://www.w3.org/2001/XMLSchema#string> .
                  |        ?propVal0 a <http://www.w3.org/2001/XMLSchema#string> .
                  |
                  |        FILTER contains(?propVal0, "Zeitglöcklein")
                  |
                  |     }
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/BooksWithTitleContainingZeitgloecklein.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkCountQuery(responseAs[String], 2)

            }


        }

        "search for a book whose title contains 'Zeitglöcklein' and 'Lebens' using the contains function" in {

            val sparqlSimplified =
                """
                  |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |    CONSTRUCT {
                  |
                  |        ?mainRes knora-api:isMainResource true .
                  |
                  |        ?mainRes <http://0.0.0.0:3333/ontology/incunabula/simple/v2#title> ?propVal0 .
                  |
                  |     } WHERE {
                  |
                  |        ?mainRes a knora-api:Resource .
                  |
                  |        ?mainRes a <http://0.0.0.0:3333/ontology/incunabula/simple/v2#book> .
                  |
                  |        ?mainRes <http://0.0.0.0:3333/ontology/incunabula/simple/v2#title> ?propVal0 .
                  |        <http://0.0.0.0:3333/ontology/incunabula/simple/v2#title> knora-api:objectType <http://www.w3.org/2001/XMLSchema#string> .
                  |        ?propVal0 a <http://www.w3.org/2001/XMLSchema#string> .
                  |
                  |        FILTER contains(?propVal0, "Zeitglöcklein Lebens")
                  |
                  |     }
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/BooksWithTitleContainingZeitgloecklein.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkCountQuery(responseAs[String], 2)

            }


        }

        "search for 'Zeitglöcklein des Lebens' using dcterms:title" in {

            val sparqlSimplified =
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

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/BooksWithTitleContainingZeitgloecklein.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkCountQuery(responseAs[String], 2)

            }

        }

        "search for a anything:Thing with a list value" in {

            val sparqlSimplified =
                """
                  |PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |PREFIX anything: <http://0.0.0.0:3333/ontology/anything/simple/v2#>
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

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparqlSimplified, "UTF-8").replace("+", "%20")) ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                val expectedAnswerJSONLD = FileUtil.readTextFile(new File("src/test/resources/test-data/searchR2RV2/ThingWithListValue.jsonld"))

                compareJSONLD(expectedJSONLD = expectedAnswerJSONLD, receivedJSONLD = responseAs[String])

                checkCountQuery(responseAs[String], 2)

            }

        }

    }
}