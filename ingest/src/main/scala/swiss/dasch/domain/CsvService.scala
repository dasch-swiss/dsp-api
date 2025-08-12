/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import com.github.tototoshi.csv.CSVWriter
import swiss.dasch.domain.SupportedFileType.{Audio, MovingImage, OtherFiles, StillImage}
import zio.nio.file.Path
import zio.{Task, ZIO}

object AssetOverviewReportRowBuilder {
  private def getFileSize(key: SupportedFileType, f: SizeInBytesPerType => FileSize): AssetOverviewReport => Number =
    rep => rep.sizesPerType.sizes.get(key).map(f).map(_.sizeInBytes).getOrElse(0)

  private final case class Column(header: String, value: AssetOverviewReport => String | Number)

  private val columns: List[Column] = List(
    Column("Project Shortcode", _.shortcode.value),
    Column("No. Of All Assets Sum", _.totalNrOfAssets),
    Column("No. Of Still Image Assets", _.nrOfAssetsPerType.getOrElse(StillImage, 0)),
    Column("No. Of Moving Image Assets", _.nrOfAssetsPerType.getOrElse(MovingImage, 0)),
    Column("No. Of Audio Assets", _.nrOfAssetsPerType.getOrElse(Audio, 0)),
    Column("No. Of Other Assets", _.nrOfAssetsPerType.getOrElse(OtherFiles, 0)),
    Column("Size Of All Assets Sum", _.sizesPerType.sizes.values.map(_.sum.sizeInBytes).sum),
    Column("Size Of Still Image Sum", getFileSize(StillImage, _.sum)),
    Column("Size Of Still Image Originals", getFileSize(StillImage, _.sizeOrig)),
    Column("Size Of Still Image Derivatives", getFileSize(StillImage, _.sizeDerivative)),
    Column("Size Of Moving Image Sum", getFileSize(MovingImage, _.sum)),
    Column("Size Of Moving Image Originals", getFileSize(MovingImage, _.sizeOrig)),
    Column("Size Of Moving Image Derivatives", getFileSize(MovingImage, _.sizeDerivative)),
    Column("Size Of Moving Image Keyframes", getFileSize(MovingImage, _.sizeKeyframes)),
    Column("Size Of Audio Sum", getFileSize(Audio, _.sum)),
    Column("Size Of Audio Originals", getFileSize(Audio, _.sizeOrig)),
    Column("Size Of Audio Derivatives", getFileSize(Audio, _.sizeDerivative)),
    Column("Size Of Other Sum", getFileSize(OtherFiles, _.sum)),
    Column("Size Of Other Originals", getFileSize(OtherFiles, _.sizeOrig)),
    Column("Size Of Other Derivatives", getFileSize(OtherFiles, _.sizeDerivative)),
  )

  val headerRow: List[String] = columns.map(_.header)

  def valueRow(report: AssetOverviewReport): List[String | Number] =
    columns.map(_.value(report))
}

final case class CsvService() {

  private def createWriter(path: Path) = {
    val acquire = ZIO.succeed(CSVWriter.open(path.toFile))
    val release = (w: CSVWriter) => ZIO.succeed(w.close())
    ZIO.acquireRelease(acquire)(release)
  }

  def writeReportToCsv(report: Seq[AssetOverviewReport], path: Path): Task[Path] =
    ZIO.scoped {
      for {
        w <- createWriter(path)
        _ <- ZIO.succeed(w.writeRow(AssetOverviewReportRowBuilder.headerRow))
        _ <- ZIO.foreachDiscard(report)(rep => ZIO.succeed(w.writeRow(AssetOverviewReportRowBuilder.valueRow(rep))))
      } yield path
    }
}

object CsvService {
  val layer = zio.ZLayer.derive[CsvService]
}
