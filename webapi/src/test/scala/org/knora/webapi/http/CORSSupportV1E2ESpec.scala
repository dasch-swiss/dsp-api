/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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

package org.knora.webapi.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Methods`, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.{E2ESpec, R2RSpec}
import org.knora.webapi.http.CORSSupport.CORS
import org.knora.webapi.http.ServerVersion.addServerHeader
import org.knora.webapi.routing.v1.ResourcesRouteV1

object CORSSupportV1E2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-end test specification for testing [[CORSSupport]].
  */
class CORSSupportV1E2ESpec extends E2ESpec(CORSSupportV1E2ESpec.config) {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(settings.defaultTimeout)

    val exampleOrigin = HttpOrigin("http://example.com")
    val corsSettings = CORSSupport.corsSettings

    "A Route with enabled CORS support" should {

        "accept valid pre-flight requests" in {
            val request = Options(baseApiUrl + s"/admin/projects")
            val response: HttpResponse = singleAwaitingRequest(request)
            // logger.debug(s"response: ${response.toString}")
            response.status shouldBe StatusCodes.OK
            response.entity.toString shouldBe empty
            response.headers should contain allElementsOf Seq(
                `Access-Control-Allow-Origin`(exampleOrigin),
                `Access-Control-Allow-Methods`(CORSSupport.allowedMethods),
                // `Access-Control-Allow-Headers`(CORSSupport.exposedHeaders),
                `Access-Control-Max-Age`(1800),
                `Access-Control-Allow-Credentials`(true)
            )
        }

        "reject pre-flight requests with invalid method" in {
            val request = Patch(baseApiUrl + s"/admin/projects")
            val response: HttpResponse = singleAwaitingRequest(request)
            // logger.debug(s"response: ${response.toString}")
            responseToString(response) shouldEqual "HTTP method not allowed, supported methods: GET, POST"
            response.status shouldBe StatusCodes.BadRequest
        }

    }
}
