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

package org.knora.webapi.e2e.v1

import akka.actor.ActorSystem
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.v1.SearchRouteV1
import org.scalatest.Assertion
import spray.json._

import scala.concurrent.ExecutionContextExecutor

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

    private val searchPath = SearchRouteV1.knoraApiPath(system, settings, log)

    implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

    implicit val ec: ExecutionContextExecutor = system.dispatcher

    override protected val rdfDataObjects = List(
        RdfDataObject(path = "_test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
        RdfDataObject(path = "_test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
        RdfDataObject(path = "_test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula")
    )

    /**
      * Checks for the number of expected results to be returned.
      *
      * @param responseJson the response send back by the search route.
      * @param expectedNumber the expected number of results for the query.
      * @return an assertion that the actual amount of results corresponds with the expected number of results.
      */
    def checkNumberOfHits(responseJson: String, expectedNumber: Int): Assertion = {

        val response: Map[String, JsValue] = responseAs[String].parseJson.asJsObject.fields

        val nhits = response.getOrElse("nhits", throw InvalidApiJsonException(s"No member 'nhits' given"))

        nhits match {
            case JsString(hits) => assert(hits == expectedNumber.toString, s"expected $expectedNumber result, but $hits given")

            case _ => throw InvalidApiJsonException(s"'nhits' is not a number")
        }


    }

    "The Search Endpoint" should {
        "perform an extended search for the pages of a book whose seqnum is lower than or equals 10" in {

            val props = "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23partOf&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23seqnum&compop=EQ&compop=LT_EQ&searchval=http%3A%2F%2Frdfh.ch%2F5e77e98d2603&searchval=10"
            val filter = "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23page"

            Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkNumberOfHits(responseAs[String], 10)

            }

        }

        "perform an extended search for books that have been published on the first of March 1497 (Julian Calendar)" in {

            val props = "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23pubdate&compop=EQ&searchval=JULIAN:1497-03-01"
            val filter = "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23book"

            Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkNumberOfHits(responseAs[String], 2)

            }

        }

        "perform an extended search for books that have not been published on the first of March 1497 (Julian Calendar)" in {

            val props = "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23pubdate&compop=!EQ&searchval=JULIAN:1497-03-01"
            val filter = "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23book"

            Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
                checkNumberOfHits(responseAs[String], 17)

            }

        }

        "perform an extended search for books that have been published before 1497 (Julian Calendar)" in {

            val props = "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23pubdate&compop=LT&searchval=JULIAN:1497"
            val filter = "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23book"

            Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 4 (number of results from query below)
                checkNumberOfHits(responseAs[String], 15)

            }

        }

        "perform an extended search for books that have been published 1497 or later (Julian Calendar)" in {

            val props = "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23pubdate&compop=GT_EQ&searchval=JULIAN:1497"
            val filter = "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23book"

            Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 15 (number of results from query above)
                checkNumberOfHits(responseAs[String], 4)

            }

        }

        "perform an extended search for books that have been published after 1497 (Julian Calendar)" in {

            val props = "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23pubdate&compop=GT&searchval=JULIAN:1497"
            val filter = "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23book"

            Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 18 (number of results from query below)
                checkNumberOfHits(responseAs[String], 1)

            }

        }

        "perform an extended search for books that have been published 1497 or before (Julian Calendar)" in {

            val props = "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23pubdate&compop=LT_EQ&searchval=JULIAN:1497"
            val filter = "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23book"

            Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 1 (number of results from query above)
                checkNumberOfHits(responseAs[String], 18)

            }

        }

        "perform an extended search for an anything:Thing that has a Boolean value set to true" in {

            val props = "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23hasBoolean&compop=EQ&searchval=true"
            val filter = "&show_nrows=25&start_at=0&filter_by_project=http%3A%2F%2Frdfh.ch%2Fprojects%2F0001"

            Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkNumberOfHits(responseAs[String], 2)

            }

        }

        "perform an extended search for an anything:Thing that has a Boolean value that is not false" in {

            val props = "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23hasBoolean&compop=!EQ&searchval=false"
            val filter = "&show_nrows=25&start_at=0&filter_by_project=http%3A%2F%2Frdfh.ch%2Fprojects%2F0001"

            Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkNumberOfHits(responseAs[String], 2)

            }

        }

        "perform an extended search for an anything:Thing that has a Boolean value (EXISTS)" in {

            val props = "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23hasBoolean&compop=EXISTS&searchval="
            val filter = "&show_nrows=25&start_at=0&filter_by_project=http%3A%2F%2Frdfh.ch%2Fprojects%2F0001"

            Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkNumberOfHits(responseAs[String], 2)

            }

        }

        val props_two_lists_one = "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23hasListItem&compop=EQ&searchval=http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FtreeList01"

        "perform an extended search for an anything:Thing that has two list values on one of the lists (EQ)" in {

            val filter = "&show_nrows=25&start_at=0&filter_by_project=http%3A%2F%2Frdfh.ch%2Fprojects%2F0001"

            Get("/v1/search/?searchtype=extended" + props_two_lists_one + filter) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkNumberOfHits(responseAs[String], 2)

            }

        }

        val props_two_lists_two = "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23hasOtherListItem&compop=EQ&searchval=http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FotherTreeList02"

        "perform an extended search for an anything:Thing that has two list values on the other lists (EQ)" in {

            val filter = "&show_nrows=25&start_at=0&filter_by_project=http%3A%2F%2Frdfh.ch%2Fprojects%2F0001"

            Get("/v1/search/?searchtype=extended" + props_two_lists_two + filter) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkNumberOfHits(responseAs[String], 1)

            }

        }

        "perform an extended search for an anything:Thing that has two list values on both lists (EQ)" in {

            val filter = "&show_nrows=25&start_at=0&filter_by_project=http%3A%2F%2Frdfh.ch%2Fprojects%2F0001"

            Get("/v1/search/?searchtype=extended" + props_two_lists_one + props_two_lists_two + filter) ~> searchPath ~> check {

                assert(status == StatusCodes.OK, response.toString)

                checkNumberOfHits(responseAs[String], 1)

            }

        }

    }

}