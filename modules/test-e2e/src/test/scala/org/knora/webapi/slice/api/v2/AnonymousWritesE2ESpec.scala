/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v2

import org.junit.runner.RunWith
import sttp.client4.UriContext
import sttp.model.Method
import sttp.model.StatusCode
import sttp.model.Uri
import zio.ZIO
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.testservices.TestApiClient

@RunWith(classOf[DspZTestJUnitRunner])
class AnonymousWritesE2ESpec extends E2EZSpec {

  private val writes: List[(Method, Uri)] = List(
    Method.POST -> uri"/v2/resources",
    Method.POST -> uri"/v2/resources/delete",
    Method.PUT  -> uri"/v2/resources/authorship",
    Method.POST -> uri"/v2/values",
    Method.PUT  -> uri"/v2/values",
    Method.PUT  -> uri"/v2/values/order",
    Method.POST -> uri"/v2/values/delete",
  )

  val e2eSpec: Spec[env, Any] = suite("Object-access writes refuse an anonymous caller")(
    writes.map { case (method, uri) =>
      test(s"$method $uri answers 401 before the body is read") {
        ZIO
          .serviceWithZIO[TestApiClient](client =>
            if (method == Method.POST) client.postJsonLdDocument(uri, "not json", None, identity)
            else client.putJsonLdDocument(uri, "not json", None, identity),
          )
          .map(response => assertTrue(response.code == StatusCode.Unauthorized))
      }
    },
  )
}
