/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.E2ESpec

object RejectingRouteE2ESpec {
  val config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
          app.routes-to-reject = ["v1/testRouteToReject", "v2/testRouteToReject"]
        """.stripMargin)
}

/**
 * End-to-End (E2E) test specification for testing route rejections.
 */
class RejectingRouteE2ESpec extends E2ESpec(RejectingRouteE2ESpec.config) {

  "The Rejecting Route" should {

    "reject the 'v1/test' path" in {
      val request                = Get(baseApiUrl + s"/v1/testRouteToReject")
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.NotFound)
    }

    "reject the 'v2/test' path" in {
      val request                = Get(baseApiUrl + s"/v2/testRouteToReject")
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.NotFound)
    }

    "not reject the 'admin/groups' path" in {
      val request                = Get(baseApiUrl + s"/admin/groups")
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.OK)
    }

    "not reject the 'admin/projects' path, as it is not listed" in {
      val request                = Get(baseApiUrl + s"/admin/projects")
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.OK)
    }
  }
}
