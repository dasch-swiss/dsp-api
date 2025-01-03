/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.model.StatusCode
import sttp.tapir.ztapir.*

final case class ReportEndpoints(baseEndpoints: BaseEndpoints) {

  private val report      = "report"
  private val maintenance = "maintenance"

  private[api] val postAssetOverviewReport =
    baseEndpoints.secureEndpoint.post
      .in(report / "asset-overview")
      .out(stringBody)
      .out(statusCode(StatusCode.Accepted))
      .tag(maintenance)

  val endpoints = List(postAssetOverviewReport)
}

object ReportEndpoints {
  val layer = zio.ZLayer.derive[ReportEndpoints]
}
