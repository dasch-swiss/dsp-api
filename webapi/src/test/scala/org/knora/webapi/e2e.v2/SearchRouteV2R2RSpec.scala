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

package org.knora.webapi.e2e.v2

import java.net.URLEncoder
import java.util

import akka.actor.{ActorSystem, Props}
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.model.headers.BasicHttpCredentials
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.util.Timeout
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.messages.v1.responder.ontologymessages.LoadOntologiesRequest
import org.knora.webapi.responders.{ResponderManager, _}
import org.knora.webapi.routing.v2.SearchRouteV2
import org.knora.webapi.store._
import akka.pattern._
import org.scalatest.Assertion
import spray.json._

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor}
import com.github.jsonldjava.core._
import com.github.jsonldjava.utils._

import scala.collection.JavaConverters._
import scala.collection.mutable

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

    private val anythingUser = SharedAdminTestData.anythingUser1
    private val anythingUserEmail = anythingUser.userData.email.get

    private val password = "test"

    private val rdfDataObjects = List(

        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")

    )

    private val numberOfItemsMember = "http://schema.org/numberOfItems"

    private val itemListElementMember = "http://schema.org/itemListElement"

    "Load test data" in {
        Await.result(storeManager ? ResetTriplestoreContent(rdfDataObjects), 360.seconds)
        Await.result(responderManager ? LoadOntologiesRequest(SharedAdminTestData.rootUser), 10.seconds)
    }

    /**
      * Checks for the number of expected results to be returned.
      *
      * @param responseJson the response send back by the search route.
      * @param expectedNumber the expected number of results for the query.
      * @return an assertion that the actual amount of results corresponds with the expected number of results.
      */
    def checkNumberOfItems(responseJson: String, expectedNumber: Int): Assertion = {

        val res = JsonUtils.fromString(responseJson)

        val compacted: Map[IRI, Any] = JsonLdProcessor.compact(res, new util.HashMap[String, String](), new JsonLdOptions()).asScala.toMap

        val numberOfItems: Any = compacted.getOrElse(numberOfItemsMember, throw InvalidApiJsonException(s"member '$numberOfItemsMember' not given for search response."))

        assert(numberOfItems.isInstanceOf[Int] && numberOfItems == expectedNumber)

    }

    "The Search Endpoint" should {
        "perform a fulltext search for 'Narr'" in {

            Get("/v2/search/Narr") ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkNumberOfItems(responseAs[String], 210)

            }
        }


        "perform a fulltext search for 'Dinge'" in {
            Get("/v2/search/Dinge") ~> addCredentials(BasicHttpCredentials(anythingUserEmail, password)) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkNumberOfItems(responseAs[String], 1)

            }
        }

        "perform an extended search for the pages of a book whose seqnum is lower than or equals 10" in {

            val sparql_simplified =
                """PREFIX incunabula: <http://api.knora.org/ontology/incunabula/simple/v2#>
                  |    PREFIX knora-api: <http://api.knora.org/ontology/knora-api/simple/v2#>
                  |
                  |    CONSTRUCT {
                  |        ?page knora-api:isMainResource true .
                  |
                  |        ?page a incunabula:page .
                  |
                  |        ?page knora-api:partOf <http://data.knora.org/b6b5ff1eb703> .
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
                  |    }
                """.stripMargin

            // TODO: find a better way to submit spaces as %20
            Get("/v2/searchextended/" + URLEncoder.encode(sparql_simplified, "UTF-8").replace("+", "%20")) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkNumberOfItems(responseAs[String], 10)

            }

        }
    }
}