/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.RouteTestTimeout

import org.knora.webapi.E2ESpec
import org.knora.webapi.http.version.ServerVersion

/**
 * End-to-End (E2E) test specification for testing the server response.
 */
class ServerVersionE2ESpec extends E2ESpec {
  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(
    appConfig.defaultTimeoutAsDuration
  )

  "The Server" should {
    "return the custom 'Server' header with every response" in {
      val request                = Get(baseApiUrl + s"/admin/projects")
      val response: HttpResponse = singleAwaitingRequest(request)
      response.headers should contain(ServerVersion.serverVersionHeader)
      response.headers.find(_.name == "Server") match {
        case Some(serverHeader: HttpHeader) =>
          serverHeader.value() should include("webapi/")
          serverHeader.value() should include("akka-http/")
        case None => fail("no server header found")
      }
      response.status should be(StatusCodes.OK)
    }
  }
}
