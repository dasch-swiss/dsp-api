/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api

import org.junit.runner.RunWith
import sttp.model.Method
import zio.*
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.slice.api.admin.StoreEndpoints
import org.knora.webapi.slice.common.api.BaseEndpoints

@RunWith(classOf[DspZTestJUnitRunner])
class EndpointsE2ESpec extends E2EZSpec {

  override def e2eSpec = suite("Endpoints")(
    test("every endpoint declares an HTTP method") {
      // A tapir endpoint without a method matches every HTTP method.
      // Endpoints registered only behind a config flag (e.g. allowReloadOverHttp) are not in this list.
      ZIO.serviceWith[Endpoints] { endpoints =>
        val all           = endpoints.serverEndpoints.map(_.endpoint)
        val withoutMethod = all.filter(_.method.isEmpty).map(_.showShort)
        assertTrue(all.nonEmpty) &&
        (assertTrue(withoutMethod.isEmpty) ?? "declare an HTTP method on these endpoints, e.g. `.get`")
      }
    },
    test("the flag-gated store reset endpoint declares POST") {
      ZIO.serviceWith[BaseEndpoints] { baseEndpoints =>
        assertTrue(StoreEndpoints(baseEndpoints).postStoreResetTriplestoreContent.method.contains(Method.POST))
      }
    },
  )
}
