/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e

import org.apache.pekko

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.NANOSECONDS

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.store.triplestoremessages.RdfDataObject

import pekko.http.scaladsl.model.HttpMethods.*
import pekko.http.scaladsl.model.HttpResponse
import pekko.http.scaladsl.model.StatusCodes
import pekko.http.scaladsl.model.headers.*
import pekko.http.scaladsl.testkit.RouteTestTimeout

/**
 * End-to-end test specification for testing [[CORSSupport]].
 */
class CORSSupportE2ESpec extends E2ESpec {

  implicit def default: RouteTestTimeout = RouteTestTimeout(
    FiniteDuration(appConfig.defaultTimeout.toNanos, NANOSECONDS),
  )

  override lazy val rdfDataObjects = List(
    RdfDataObject(
      path = "test_data/project_data/anything-data.ttl",
      name = "http://www.knora.org/data/0001/anything",
    ),
  )

  val exampleOrigin = HttpOrigin("http://example.com")

  "A Route with enabled CORS support" should {

    "accept valid pre-flight requests" in {
      val request =
        Options(baseApiUrl + s"/admin/projects") ~> Origin(exampleOrigin) ~> `Access-Control-Request-Method`(GET)
      val response: HttpResponse = singleAwaitingRequest(request)
      response.status shouldBe StatusCodes.OK
      response.headers should contain allElementsOf Seq(
        `Access-Control-Allow-Origin`(exampleOrigin),
        `Access-Control-Allow-Methods`(List(GET, PUT, POST, DELETE, PATCH, HEAD, OPTIONS)),
        `Access-Control-Max-Age`(1800),
        `Access-Control-Allow-Credentials`(true),
      )
    }

    "send `Access-Control-Allow-Origin` header when the Knora resource is found " in {
      val request = Get(
        baseApiUrl + "/v2/resources/" + java.net.URLEncoder
          .encode("http://rdfh.ch/0001/55UrkgTKR2SEQgnsLWI9mg", "utf-8"),
      ) ~> Origin(exampleOrigin)
      val response = singleAwaitingRequest(request)
      response.status should equal(StatusCodes.OK)
      response.headers should contain allElementsOf Seq(
        `Access-Control-Allow-Origin`(exampleOrigin),
      )
    }

    "send `Access-Control-Allow-Origin` header when the Knora resource is NOT found " in {
      val request = Get(
        baseApiUrl + "/v2/resources/" + java.net.URLEncoder.encode("http://rdfh.ch/0803/nonexistent", "utf-8"),
      ) ~> Origin(exampleOrigin)
      val response = singleAwaitingRequest(request)
      response.status should equal(StatusCodes.NotFound)
      response.headers should contain allElementsOf Seq(
        `Access-Control-Allow-Origin`(exampleOrigin),
      )
    }
  }
}
