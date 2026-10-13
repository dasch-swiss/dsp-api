/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.fair.api

import org.junit.runner.RunWith
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.config.FairConfig

@RunWith(classOf[DspZTestJUnitRunner])
class FairServerEndpointsSpec extends ZIOSpecDefault {

  // The rest service is only captured by the endpoint logic, never called, so null is enough to construct the wiring.
  private def serverEndpoints(baseUrl: Option[String]) =
    FairServerEndpoints(
      FairConfig(baseUrl, "https://app.example.org/index.html", "https://example.org/oai", ""),
      FairEndpoints(),
      null,
    ).serverEndpoints

  override def spec: Spec[Any, Any] = suite("FairServerEndpointsSpec")(
    test("registers no routes when the external base URL is not configured") {
      assertTrue(serverEndpoints(None).isEmpty)
    },
    test("registers the four routes when the external base URL is configured") {
      assertTrue(serverEndpoints(Some("https://app.example.org")).size == 4)
    },
  )
}
