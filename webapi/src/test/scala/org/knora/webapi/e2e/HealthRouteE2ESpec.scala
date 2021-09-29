/*
 * Copyright © 2015-2021 Data and Service Center for the Humanities (DaSCH)
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

object HealthRouteE2ESpec {
  val config: Config = ConfigFactory.parseString("""
          akka.loglevel = "DEBUG"
          akka.stdout-loglevel = "DEBUG"
        """.stripMargin)
}

/**
 * End-to-End (E2E) test specification for testing route rejections.
 */
class HealthRouteE2ESpec extends E2ESpec(HealthRouteE2ESpec.config) {

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(settings.defaultTimeout)

  // Directory path for generated client test data
  private val clientTestDataPath: Seq[String] = Seq("system", "health")

  // Collects client test data
  private val clientTestDataCollector = new ClientTestDataCollector(settings)

  "The Health Route" should {

    "return 'OK' for state 'Running'" in {

      val request = Get(baseApiUrl + s"/health")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseStr: String = responseToString(response)
      val responseHeadersStr: String = response.headers.map(_.toString).mkString("\n")

      response.status should be(StatusCodes.OK)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "running-response",
            fileExtension = "json"
          ),
          text = responseStr
        )
      )

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "response-headers",
            fileExtension = "json"
          ),
          text = responseHeadersStr
        )
      )
    }

    "return 'ServiceUnavailable' for state 'Stopped'" in {

      appActor ! SetAppState(AppStates.Stopped)

      val request = Get(baseApiUrl + s"/health")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseStr: String = responseToString(response)

      logger.debug(response.toString())

      response.status should be(StatusCodes.ServiceUnavailable)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "stopped-response",
            fileExtension = "json"
          ),
          text = responseStr
        )
      )
    }

    "return 'ServiceUnavailable' for state 'MaintenanceMode'" in {
      appActor ! SetAppState(AppStates.MaintenanceMode)

      val request = Get(baseApiUrl + s"/health")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseStr: String = responseToString(response)

      logger.debug(response.toString())

      response.status should be(StatusCodes.ServiceUnavailable)

      clientTestDataCollector.addFile(
        TestDataFileContent(
          filePath = TestDataFilePath(
            directoryPath = clientTestDataPath,
            filename = "maintenance-mode-response",
            fileExtension = "json"
          ),
          text = responseStr
        )
      )
    }

  }
}
