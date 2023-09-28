/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.http

import org.apache.pekko

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.NANOSECONDS

import org.knora.webapi.E2ESpec
import org.knora.webapi.http.version.ServerVersion

import pekko.actor.ActorSystem
import pekko.http.scaladsl.model._
import pekko.http.scaladsl.testkit.RouteTestTimeout

/**
 * End-to-End (E2E) test specification for testing the server response.
 */
class ServerVersionE2ESpec extends E2ESpec {
  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(
    FiniteDuration(appConfig.defaultTimeout.toNanos, NANOSECONDS)
  )

  "The Server" should {
    "return the custom 'Server' header with every response" in {
      val request                = Get(baseApiUrl + s"/admin/projects")
      val response: HttpResponse = singleAwaitingRequest(request)
      response.headers should contain(ServerVersion.serverVersionHeader)
      response.headers.find(_.name == "Server") match {
        case Some(serverHeader: HttpHeader) =>
          serverHeader.value() should include("webapi/")
          serverHeader.value() should include("pekko-http/")
        case None => fail("no server header found")
      }
      response.status should be(StatusCodes.OK)
    }
  }
}
