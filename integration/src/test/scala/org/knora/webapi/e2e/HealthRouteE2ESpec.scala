/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.e2e

import org.apache.pekko
import zio.Unsafe
import zio.ZIO

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.NANOSECONDS

import org.knora.webapi.E2ESpec
import org.knora.webapi.core.State
import org.knora.webapi.core.domain.AppState

import pekko.http.scaladsl.model._
import pekko.http.scaladsl.testkit.RouteTestTimeout

/**
 * End-to-End (E2E) test specification for testing route rejections.
 */
class HealthRouteE2ESpec extends E2ESpec {
  implicit def default: RouteTestTimeout = RouteTestTimeout(
    FiniteDuration(appConfig.defaultTimeout.toNanos, NANOSECONDS),
  )

  "The Health Route" should {

    "return 'OK' for state 'Running'" in {

      val request                = Get(baseApiUrl + s"/health")
      val response: HttpResponse = singleAwaitingRequest(request)

      response.status should be(StatusCodes.OK)
    }

    "return 'ServiceUnavailable' for state 'Stopped'" in {

      Unsafe.unsafe { implicit u =>
        runtime.unsafe
          .run(
            for {
              state <- ZIO.service[State]
              _     <- state.set(AppState.Stopped)
            } yield (),
          )
          .getOrThrow()
      }

      val request                = Get(baseApiUrl + s"/health")
      val response: HttpResponse = singleAwaitingRequest(request)
      val responseStr: String    = responseToString(response)

      response.status should be(StatusCodes.ServiceUnavailable)
    }

    "return 'ServiceUnavailable' for state 'MaintenanceMode'" in {
      Unsafe.unsafe { implicit u =>
        runtime.unsafe
          .run(
            for {
              state <- ZIO.service[State]
              _     <- state.set(AppState.MaintenanceMode)
            } yield (),
          )
          .getOrThrow()
      }

      val request                = Get(baseApiUrl + s"/health")
      val response: HttpResponse = singleAwaitingRequest(request)
      response.status should be(StatusCodes.ServiceUnavailable)
    }
  }
}
