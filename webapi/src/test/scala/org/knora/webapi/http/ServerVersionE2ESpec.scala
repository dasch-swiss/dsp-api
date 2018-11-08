/*
 * Copyright © 2015-2018 the contributors (see Contributors.md).
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

package org.knora.webapi.http

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import org.knora.webapi.E2ESpec
import org.knora.webapi.e2e.HealthRouteE2ESpec


object ServerVersionE2ESpec {
    val config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing the server response.
  */
class ServerVersionE2ESpec extends E2ESpec(HealthRouteE2ESpec.config) {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(settings.defaultTimeout)

    implicit override lazy val log = akka.event.Logging(system, this.getClass)

    "The Server" should {

        "return the custom 'Server' header with every response" in {
            val request = Get(baseApiUrl + s"/admin/users")
            val response: HttpResponse = singleAwaitingRequest(request)
            // log.debug(s"response: ${response.toString}")
            response.headers should contain (ServerVersion.getServerVersionHeader())
            response.status should be(StatusCodes.OK)
        }
    }
}
