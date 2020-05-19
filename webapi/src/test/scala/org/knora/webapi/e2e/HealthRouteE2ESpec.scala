/*
 * Copyright © 2015-2019 the contributors (see Contributors.md).
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
import com.typesafe.config.{Config, ConfigFactory}
import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.app.appmessages.{AppStates, SetAppState}
import org.knora.webapi.testing.tags.E2ETest


object HealthRouteE2ESpec {
    val config: Config = ConfigFactory.parseString(
        """
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
  * End-to-End (E2E) test specification for testing route rejections.
  */
@E2ETest
class HealthRouteE2ESpec extends E2ESpec(HealthRouteE2ESpec.config) {

    implicit def default(implicit system: ActorSystem) = RouteTestTimeout(settings.defaultTimeout)

    "The Health Route" should {

        "return 'OK' for state 'Running'" in {

            val request = Get(baseApiUrl + s"/health")
            val response: HttpResponse = singleAwaitingRequest(request)

            response.status should be(StatusCodes.OK)
        }

        "return 'ServiceUnavailable' for state 'Stopped'" in {

            appActor ! SetAppState(AppStates.Stopped)

            val request = Get(baseApiUrl + s"/health")
            val response: HttpResponse = singleAwaitingRequest(request)

            logger.debug(response.toString())

            response.status should be(StatusCodes.ServiceUnavailable)
        }

        "return 'ServiceUnavailable' for state 'MaintenanceMode'" in {
            appActor ! SetAppState(AppStates.MaintenanceMode)

            val request = Get(baseApiUrl + s"/health")
            val response: HttpResponse = singleAwaitingRequest(request)

            logger.debug(response.toString())

            response.status should be(StatusCodes.ServiceUnavailable)
        }

    }
}
