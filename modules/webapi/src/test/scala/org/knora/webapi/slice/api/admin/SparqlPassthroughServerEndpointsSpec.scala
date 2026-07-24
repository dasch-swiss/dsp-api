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
            registered.head.showShort.contains(SparqlPassthroughEndpoints.pathTemplate),
          ),
        )
        .provide(SparqlPassthroughTestEnv.layer())
    },
    test("the published path template matches the endpoint's own, so the server's 401 hook cannot drift") {
      // The server recognises a rejected passthrough request by comparing the rendered route to this constant. If the
      // route were renamed and the constant not, the rejection would silently stop being logged.
      ZIO
        .serviceWith[SparqlPassthroughEndpoints](
          _.postAdminSparqlQuery.endpoint.showPathTemplate(showQueryParam = None, showQueryParamsAs = None),
        )
        .map(rendered => assertTrue(rendered == SparqlPassthroughEndpoints.pathTemplate))
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
