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
          app.routes-to-reject = ["v1/test", "v2/test", "v1/groups", "v1/users"]
        """.stripMargin)
}

/**
 * End-to-End (E2E) test specification for testing route rejections.
 */
class RejectingRouteE2ESpec extends E2ESpec(RejectingRouteE2ESpec.config) {

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(settings.defaultTimeout)

  "The Rejecting Route" should {

    "reject the 'v1/test' path" in {
      val request = Get(baseApiUrl + s"/v1/test")
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.NotFound)
    }

    "reject the 'v2/test' path" in {
      val request = Get(baseApiUrl + s"/v2/test")
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.NotFound)
    }

    "reject the 'v1/groups' path" in {
      val request = Get(baseApiUrl + s"/v1/groups")
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.NotFound)
    }

    "reject the 'v1/users' path" in {
      val request = Get(baseApiUrl + s"/v1/users")
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.NotFound)
    }

    "not reject the 'v1/projects' path, as it is not listed" in {
      val request = Get(baseApiUrl + s"/v1/projects")
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.OK)
    }
  }
}
