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

package org.knora.webapi.e2e.v1

import akka.actor.{ActorSystem, Props}
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.testkit.RouteTestTimeout
import akka.pattern._
import akka.util.Timeout
import org.knora.webapi.messages.store.triplestoremessages.{RdfDataObject, ResetTriplestoreContent}
import org.knora.webapi.messages.v1.responder.ontologymessages.LoadOntologiesRequest
import org.knora.webapi.responders.{RESPONDER_MANAGER_ACTOR_NAME, ResponderManager}
import org.knora.webapi.routing.v1.SearchRouteV1
import org.knora.webapi.store.{STORE_MANAGER_ACTOR_NAME, StoreManager}
import org.knora.webapi.{InvalidApiJsonException, LiveActorMaker, R2RSpec, SharedAdminTestData}
import spray.json.{JsNumber, JsValue, _}

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContextExecutor}

/**
  * End-to-end test specification for the search endpoint. This specification uses the Spray Testkit as documented
  * here: http://spray.io/documentation/1.2.2/spray-testkit/
  */
class SearchV1R2RSpec extends R2RSpec {

    override def testConfigSource: String =
        """
          |# akka.loglevel = "DEBUG"
          |# akka.stdout-loglevel = "DEBUG"
        """.stripMargin

    private val responderManager = system.actorOf(Props(new ResponderManager with LiveActorMaker), name = RESPONDER_MANAGER_ACTOR_NAME)
    private val storeManager = system.actorOf(Props(new StoreManager with LiveActorMaker), name = STORE_MANAGER_ACTOR_NAME)

    private val searchPath = SearchRouteV1.knoraApiPath(system, settings, log)

    implicit private val timeout: Timeout = settings.defaultRestoreTimeout

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(new DurationInt(15).second)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    private val rdfDataObjects = List(

        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/incunabula"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/images"),
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/anything")

    )

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
    def checkNumberOfHits(responseJson: String, expectedNumber: Int) = {

        val response: Map[String, JsValue] = responseAs[String].parseJson.asJsObject.fields

        val nhits = response.getOrElse("nhits", throw InvalidApiJsonException(s"No member 'nhits' given"))

        nhits match {
            case JsString(hits) => assert(hits == expectedNumber.toString, s"expected $expectedNumber result, but $hits given")

            case _ => throw InvalidApiJsonException(s"'nhits' is not a number")
        }


    }

    "The Search Endpoint" should {
        "perform an extended search for the pages of a book whose seqnum is lower than or equals 10" in {

            val props = "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23partOf&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23seqnum&compop=EQ&compop=LT_EQ&searchval=http%3A%2F%2Fdata.knora.org%2F5e77e98d2603&searchval=10"
            val filter = "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2Fincunabula%23page"

            Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkNumberOfHits(responseAs[String], 10)

            }

        }

    }

}