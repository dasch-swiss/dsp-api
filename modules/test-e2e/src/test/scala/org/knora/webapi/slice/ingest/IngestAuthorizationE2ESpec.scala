/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest

import org.junit.runner.RunWith
import sttp.model.Method
import sttp.model.StatusCode
import zio.test.*

import org.knora.testrunner.DspZTestJUnitRunner
import org.knora.webapi.E2EZSpec
import org.knora.webapi.sharedtestdata.SharedTestDataADM.*
import org.knora.webapi.slice.admin.domain.model.User

/**
 * The refusals of the role-based ingest routes that AuthorizationMatrixE2ESpec exempts: they start jobs, write or
 * delete, so they get no matrix row. Ingest refuses a token without the needed scope with 401, not 403.
 */
@RunWith(classOf[DspZTestJUnitRunner])
class IngestAuthorizationE2ESpec extends E2EZSpec {

  private val shortcode = anythingShortcode.value

  private final case class Route(method: Method, path: String, outOfScope: User, contentType: String)

  private def systemAdmin(method: Method, path: String, contentType: String = "application/octet-stream") =
    Route(method, path, anythingAdminUser, contentType)

  private def projectWrite(method: Method, path: String) =
    Route(method, s"/ingest/projects/$shortcode$path", incunabulaMemberUser, "application/octet-stream")

  // A ProjectAdmin of the anything project holds only that project's write scope; an incunabula member holds none
  // for it.
  private val routes = List(
    systemAdmin(Method.GET, "/ingest/maintenance/needs-top-left-correction"),
    systemAdmin(Method.GET, "/ingest/maintenance/was-top-left-correction-applied"),
    systemAdmin(Method.POST, "/ingest/maintenance/ApplyTopLeftCorrection"),
    systemAdmin(Method.POST, "/ingest/report/asset-overview"),
    systemAdmin(Method.DELETE, s"/ingest/projects/$shortcode/erase"),
    systemAdmin(Method.POST, s"/ingest/projects/$shortcode/export"),
    systemAdmin(Method.POST, s"/ingest/projects/$shortcode/import", "application/zip"),
    projectWrite(Method.GET, "/bulk-ingest/mapping.csv"),
    projectWrite(Method.POST, "/assets/ingest/matrix.jpg"),
    projectWrite(Method.POST, "/bulk-ingest"),
    projectWrite(Method.POST, "/bulk-ingest/finalize"),
    projectWrite(Method.POST, "/bulk-ingest/ingest/matrix.jpg"),
  )

  private def body(route: Route) = Option.when(route.method != Method.GET)(Array[Byte](1, 2, 3))

  private def refused(route: Route) =
    test(s"${route.method} ${route.path} refuses anonymous and out-of-scope callers with 401") {
      for {
        anonymous  <- IngestRequests.send(route.method, route.path, None, body(route), route.contentType)
        outOfScope <-
          IngestRequests.send(route.method, route.path, Some(route.outOfScope), body(route), route.contentType)
      } yield assertTrue(anonymous.code == StatusCode.Unauthorized, outOfScope.code == StatusCode.Unauthorized)
    }

  override val e2eSpec = suite("Ingest authorization")(routes.map(refused)*)
}
