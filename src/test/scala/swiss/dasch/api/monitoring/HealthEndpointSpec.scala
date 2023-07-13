/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.monitoring

import swiss.dasch.api.monitoring.{ HealthEndpoint, MockHealthCheckService }
import zio.http.*
import zio.json.DecoderOps
import zio.json.ast.Json
import zio.test.*
import zio.test.Assertion.*

object HealthEndpointSpec extends ZIOSpecDefault {

  // Our API Health Response follows the IETF draft paragraph 3.1:
  // https://datatracker.ietf.org/doc/html/draft-inadarei-api-health-check-02#section-3.1
  override def spec =
    suite("HealthEndpoint")(
      test("when healthy should return status UP") {
        for {
          _        <- MockHealthCheckService.setHealthUp()
          response <- HealthEndpoint.app.runZIO(Request.get(URL(Root / "health")))
          bodyJson <- response.body.asString
        } yield assertTrue(
          response.status == Status.Ok,
          bodyJson.fromJson[Json] == "{\"status\":\"UP\"}".fromJson[Json],
        )
      },
      test("when unhealthy should return status DOWN") {
        for {
          _        <- MockHealthCheckService.setHealthDown()
          response <- HealthEndpoint.app.runZIO(Request.get(URL(Root / "health")))
          bodyJson <- response.body.asString
        } yield assertTrue(
          response.status == Status.ServiceUnavailable,
          bodyJson.fromJson[Json] == "{\"status\":\"DOWN\"}".fromJson[Json],
        )
      },
    ).provide(MockHealthCheckService.layer)
}
