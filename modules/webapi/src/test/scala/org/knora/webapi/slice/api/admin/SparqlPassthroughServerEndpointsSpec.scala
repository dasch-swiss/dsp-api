/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.admin

import org.junit.runner.RunWith
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.api.admin.service.SparqlPassthroughRestService
import org.knora.webapi.slice.api.admin.service.SparqlPassthroughTestEnv

@RunWith(classOf[DspZTestJUnitRunner])
class SparqlPassthroughServerEndpointsSpec extends ZIOSpecDefault {

  private val serverEndpoints = for {
    appConfig   <- ZIO.service[AppConfig]
    endpoints   <- ZIO.service[SparqlPassthroughEndpoints]
    restService <- ZIO.service[SparqlPassthroughRestService]
  } yield new SparqlPassthroughServerEndpoints(appConfig, endpoints, restService).serverEndpoints

  val spec: Spec[Any, Any] = suite("SparqlPassthroughServerEndpoints")(
    test("registers the query route when the passthrough is enabled") {
      serverEndpoints
        .map(registered =>
          assertTrue(
            registered.size == 1,
            registered.head.showShort.contains("/admin/sparql/query"),
          ),
        )
        .provide(SparqlPassthroughTestEnv.layer())
    },
    test("the registered route carries the marker the server's interceptors recognise it by") {
      // The server exempts this route from Accept negotiation and attributes requests its security or decode logic
      // rejected, both keyed off this attribute. If it were dropped, the exemption and the log entries would silently
      // stop applying -- with no compile error, since both sides would still be well-typed. Asserted on the endpoint
      // as *registered*, which is the value the interceptors are handed.
      serverEndpoints
        .map(registered =>
          assertTrue(registered.forall(se => SparqlPassthroughEndpoints.isPassthroughRoute(se.endpoint))),
        )
        .provide(SparqlPassthroughTestEnv.layer())
    },
    test("registers nothing when the passthrough is disabled, so the route answers 404") {
      // This, not the service-level flag re-check, is what REQ-3.1 rests on: with no server endpoint registered the
      // path is unrouted, so it 404s and is absent from the generated API documentation.
      serverEndpoints
        .map(registered => assertTrue(registered.isEmpty))
        .provide(SparqlPassthroughTestEnv.layer("app.allow-sparql-passthrough" -> false))
    },
  )
}
