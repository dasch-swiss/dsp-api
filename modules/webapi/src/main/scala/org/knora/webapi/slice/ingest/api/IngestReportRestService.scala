/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import zio.*
import zio.stream.ZStream

import org.knora.webapi.slice.ingest.domain.*

final class IngestReportRestService(reportService: ReportService, projectService: ProjectService) {

  private val createReports: Task[Unit] =
    for {
      reports <- ZStream
                   .fromIterableZIO(projectService.listAllProjects().map(_.map(_.shortcode)))
                   .mapZIO(reportService.assetsOverviewReport)
                   .runCollect
                   .map(_.flatten)
      report <- reportService.saveReports(reports).flatMap(_.toAbsolutePath)
      _      <- ZIO.logInfo(s"Created $report asset overview reports")
    } yield ()

  /** Any authenticated caller may start it: the standalone service never checked the scope it documents. */
  val createAssetOverviewReport: UIO[String] =
    createReports.logError.forkDaemon.as("work in progress")
}

object IngestReportRestService {
  val layer = ZLayer.derive[IngestReportRestService]
}
