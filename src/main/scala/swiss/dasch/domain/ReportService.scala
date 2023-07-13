/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.*

final case class Report(results: Map[AssetInfo, Chunk[ChecksumResult]])
object Report {
  def make(map: Map[AssetInfo, Chunk[ChecksumResult]]): Report = Report(map)
}

trait ReportService  {
  def checksumReport(projectShortcode: ProjectShortcode): Task[Report]
}
object ReportService {
  def checksumReport(projectShortcode: ProjectShortcode): RIO[ReportService, Report] =
    ZIO.serviceWithZIO[ReportService](_.checksumReport(projectShortcode))
}

final case class ReportServiceLive(projectService: ProjectService, assetService: FileChecksumService)
    extends ReportService {
  override def checksumReport(projectShortcode: ProjectShortcode): Task[Report] =
    projectService
      .findAssetInfosOfProject(projectShortcode)
      .mapZIO(info => assetService.verifyChecksum(info).map((info, _)))
      .runCollect
      .map(_.toMap)
      .map(Report.make)
}
object ReportServiceLive  {
  val layer = ZLayer.fromFunction(ReportServiceLive.apply _)
}
