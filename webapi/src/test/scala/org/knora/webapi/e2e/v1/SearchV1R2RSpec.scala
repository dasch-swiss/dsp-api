/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.v1

import akka.actor.ActorSystem
import akka.http.javadsl.model.StatusCodes
import akka.http.scaladsl.testkit.RouteTestTimeout
import org.scalatest.Assertion
import spray.json._

import scala.concurrent.ExecutionContextExecutor

import dsp.errors.InvalidApiJsonException
import org.knora.webapi._
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject
import org.knora.webapi.routing.v1.SearchRouteV1

/**
 * End-to-end test specification for the search endpoint. This specification uses the Spray Testkit as documented
 * here: http://spray.io/documentation/1.2.2/spray-testkit/
 */
class SearchV1R2RSpec extends R2RSpec {

  private val searchPath = new SearchRouteV1(routeData).makeRoute

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(
    appConfig.defaultTimeoutAsDuration
  )

  implicit val ec: ExecutionContextExecutor = system.dispatcher

  override lazy val rdfDataObjects = List(
    RdfDataObject(path = "test_data/all_data/anything-data.ttl", name = "http://www.knora.org/data/0001/anything"),
    RdfDataObject(path = "test_data/demo_data/images-demo-data.ttl", name = "http://www.knora.org/data/00FF/images"),
    RdfDataObject(path = "test_data/all_data/incunabula-data.ttl", name = "http://www.knora.org/data/0803/incunabula")
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
    val nhits                          = response.getOrElse("nhits", throw InvalidApiJsonException(s"No member 'nhits' given"))

    nhits match {
      case JsString(hits) =>
        assert(hits == expectedNumber.toString, s"expected $expectedNumber result, but $hits given")

      case _ => throw InvalidApiJsonException(s"'nhits' is not a number")
    }
  }

  "The Search Endpoint" should {
    "perform an extended search for the pages of a book whose seqnum is lower than or equals 10" in {
      val props =
        "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23partOf&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23seqnum&compop=EQ&compop=LT_EQ&searchval=http%3A%2F%2Frdfh.ch%2F0803%2F5e77e98d2603&searchval=10"
      val filter =
        "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23page"

      Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        checkNumberOfHits(responseAs[String], 10)
      }
    }

    "perform an extended search for books that have been published on the first of March 1497 (Julian Calendar)" in {
      val props =
        "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23pubdate&compop=EQ&searchval=JULIAN:1497-03-01"
      val filter =
        "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23book"

      Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        checkNumberOfHits(responseAs[String], 2)
      }
    }

    "perform an extended search for books that have not been published on the first of March 1497 (Julian Calendar)" in {
      val props =
        "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23pubdate&compop=!EQ&searchval=JULIAN:1497-03-01"
      val filter =
        "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23book"

      Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 2 (number of results from query above)
        checkNumberOfHits(responseAs[String], 17)
      }
    }

    "perform an extended search for books that have been published before 1497 (Julian Calendar)" in {
      val props =
        "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23pubdate&compop=LT&searchval=JULIAN:1497"
      val filter =
        "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23book"

      Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        checkNumberOfHits(responseAs[String], 15)
      }
    }

    "perform an extended search for books that have been published 1497 or later (Julian Calendar)" in {
      val props =
        "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23pubdate&compop=GT_EQ&searchval=JULIAN:1497"
      val filter =
        "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23book"

      Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        checkNumberOfHits(responseAs[String], 4)
      }
    }

    "perform an extended search for books that have been published after 1497 (Julian Calendar)" in {
      val props =
        "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23pubdate&compop=GT&searchval=JULIAN:1497"
      val filter =
        "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23book"

      Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        checkNumberOfHits(responseAs[String], 1)
      }
    }

    "perform an extended search for books that have been published 1497 or before (Julian Calendar)" in {
      val props =
        "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23pubdate&compop=LT_EQ&searchval=JULIAN:1497"
      val filter =
        "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0803%2Fincunabula%23book"

      Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        // this is the negation of the query condition above, hence the size of the result set must be 19 (total of incunabula:book) minus 1 (number of results from query above)
        checkNumberOfHits(responseAs[String], 18)
      }
    }

    "perform an extended search for an anything:Thing that has a Boolean value set to true" in {
      val props =
        "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23hasBoolean&compop=EQ&searchval=true"
      val filter =
        "&show_nrows=25&start_at=0&filter_by_project=http%3A%2F%2Frdfh.ch%2Fprojects%2FLw3FC39BSzCwvmdOaTyLqQ"

      Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        checkNumberOfHits(responseAs[String], 2)
      }
    }

    "perform an extended search for an anything:Thing that has a Boolean value that is not false" in {
      val props =
        "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23hasBoolean&compop=!EQ&searchval=false"
      val filter =
        "&show_nrows=25&start_at=0&filter_by_project=http%3A%2F%2Frdfh.ch%2Fprojects%2FLw3FC39BSzCwvmdOaTyLqQ"

      Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        checkNumberOfHits(responseAs[String], 2)
      }
    }

    "perform an extended search for an anything:Thing that has a Boolean value (EXISTS)" in {
      val props =
        "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23hasBoolean&compop=EXISTS&searchval="
      val filter =
        "&show_nrows=25&start_at=0&filter_by_project=http%3A%2F%2Frdfh.ch%2Fprojects%2FLw3FC39BSzCwvmdOaTyLqQ"

      Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        checkNumberOfHits(responseAs[String], 2)
      }
    }

    val props_two_lists_one =
      "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23hasListItem&compop=EQ&searchval=http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FtreeList01"

    "perform an extended search for an anything:Thing that has two list values on one of the lists (EQ)" in {
      val filter =
        "&show_nrows=25&start_at=0&filter_by_project=http%3A%2F%2Frdfh.ch%2Fprojects%2FLw3FC39BSzCwvmdOaTyLqQ"

      Get("/v1/search/?searchtype=extended" + props_two_lists_one + filter) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        checkNumberOfHits(responseAs[String], 2)
      }
    }

    val props_two_lists_two =
      "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23hasOtherListItem&compop=EQ&searchval=http%3A%2F%2Frdfh.ch%2Flists%2F0001%2FotherTreeList02"

    "perform an extended search for an anything:Thing that has two list values on the other lists (EQ)" in {
      val filter =
        "&show_nrows=25&start_at=0&filter_by_project=http%3A%2F%2Frdfh.ch%2Fprojects%2FLw3FC39BSzCwvmdOaTyLqQ"

      Get("/v1/search/?searchtype=extended" + props_two_lists_two + filter) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        checkNumberOfHits(responseAs[String], 1)
      }
    }

    "perform an extended search for an anything:Thing that has two list values on both lists (EQ)" in {
      val filter =
        "&show_nrows=25&start_at=0&filter_by_project=http%3A%2F%2Frdfh.ch%2Fprojects%2FLw3FC39BSzCwvmdOaTyLqQ"

      Get(
        "/v1/search/?searchtype=extended" + props_two_lists_one + props_two_lists_two + filter
      ) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        checkNumberOfHits(responseAs[String], 1)
      }
    }

    "perform an extended search for an anything:Thing with a timestamp" in {
      val props =
        "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23hasTimeStamp&compop=GT&searchval=2019-08-30T10%3A45%3A26.365863Z"
      val filter =
        "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23Thing"

      Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        checkNumberOfHits(responseAs[String], 1)
      }
    }

    "perform an extended search for an anything:Thing with a Geoname id" in {
      val props =
        "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23hasGeoname&compop=EQ&searchval=2661604"
      val filter =
        "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23Thing"

      Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        checkNumberOfHits(responseAs[String], 1)
      }
    }

    "perform an extended search for an anything:Thing with a URI" in {
      val props =
        "&property_id=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23hasUri&compop=EQ&searchval=http%3A%2F%2Fwww.google.ch"
      val filter =
        "&show_nrows=25&start_at=0&filter_by_restype=http%3A%2F%2Fwww.knora.org%2Fontology%2F0001%2Fanything%23Thing"

      Get("/v1/search/?searchtype=extended" + props + filter) ~> searchPath ~> check {
        assert(status == StatusCodes.OK, response.toString)
        checkNumberOfHits(responseAs[String], 1)
      }
    }
  }
}
