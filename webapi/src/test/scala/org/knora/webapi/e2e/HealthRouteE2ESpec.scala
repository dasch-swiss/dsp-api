/*
 * Copyright © 2021 - 2022 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

import org.knora.webapi.E2ESpec
import org.knora.webapi.messages.app.appmessages.AppStates
import org.knora.webapi.messages.app.appmessages.SetAppState

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

      val request                    = Get(baseApiUrl + s"/health")
      val response: HttpResponse     = singleAwaitingRequest(request)
      val responseStr: String        = responseToString(response)
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

      val request                = Get(baseApiUrl + s"/health")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseStr: String    = responseToString(response)

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

      val request                = Get(baseApiUrl + s"/health")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseStr: String    = responseToString(response)

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
