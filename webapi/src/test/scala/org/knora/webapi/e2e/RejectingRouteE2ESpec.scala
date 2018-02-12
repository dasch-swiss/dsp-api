/*
 * Copyright © 2015 Lukas Rosenthaler, Benjamin Geer, Ivan Subotic,
 * Tobias Schweizer, André Kilchenmann, and André Fatton.
 * This file is part of Knora.
 * Knora is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * Knora is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public
 * License along with Knora.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.knora.webapi.e2e

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.E2ESpec

import scala.concurrent.duration._


object RejectingRouteE2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
          app.routes-to-reject = ["v1/test", "v2/test", "v1/groups", "v1/users"]
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing route rejections.
  */
class RejectingRouteE2ESpec extends E2ESpec(RejectingRouteE2ESpec.config) {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(5.seconds)

    implicit override lazy val log = akka.event.Logging(system, this.getClass())

    private val rdfDataObjects = List()


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
