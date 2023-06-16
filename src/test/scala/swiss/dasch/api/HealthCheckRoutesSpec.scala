/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import swiss.dasch.api.healthcheck.HealthCheckServiceTest
import zio.http.*
import zio.test.*
import zio.test.Assertion.*

object HealthCheckRoutesSpec extends ZIOSpecDefault {

  override def spec: Spec[Any, Option[Nothing]] =
    suite("http")(
      suite("health check")(
        test("ok status") {
          val actual =
            HealthCheckRoutes.app.runZIO(Request.get(URL(Root / "health")))
          assertZIO(actual)(equalTo(Response(Status.Ok, Headers.empty, Body.empty)))
        }
      )
    ).provide(HealthCheckServiceTest.layer)
}
