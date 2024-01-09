/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import zio.*

final case class Report(results: Map[AssetInfo, Chunk[ChecksumResult]], nrOfAssets: Int)

object Report {
  def make(map: Map[AssetInfo, Chunk[ChecksumResult]]): Report = Report(map, map.size)
}

trait ReportService {
  def checksumReport(projectShortcode: ProjectShortcode): Task[Option[Report]]
}
object ReportService {
  def checksumReport(projectShortcode: ProjectShortcode): RIO[ReportService, Option[Report]] =
    ZIO.serviceWithZIO[ReportService](_.checksumReport(projectShortcode))
}

final case class ReportServiceLive(projectService: ProjectService, assetService: FileChecksumService)
    extends ReportService {
  override def checksumReport(projectShortcode: ProjectShortcode): Task[Option[Report]] =
    projectService
      .findProject(projectShortcode)
      .flatMap {
        case Some(_) =>
          projectService
            .findAssetInfosOfProject(projectShortcode)
            .mapZIOPar(StorageService.maxParallelism())(info => assetService.verifyChecksum(info).map((info, _)))
            .runCollect
            .map(_.toMap)
            .map(Report.make)
            .map(Some(_))
        case None => ZIO.none
      }
}
object ReportServiceLive {
  val layer = ZLayer.derive[ReportServiceLive]
}
