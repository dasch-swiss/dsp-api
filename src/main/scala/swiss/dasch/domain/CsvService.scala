/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import com.github.tototoshi.csv.{CSVWriter, defaultCSVFormat}
import swiss.dasch.domain.FileSize.prettyPrint
import swiss.dasch.domain.SizeInBytesPerType.{SizeInBytesMovingImages, SizeInBytesOther}
import swiss.dasch.domain.SupportedFileType.{MovingImage, OtherFiles, StillImage}
import zio.nio.file.Path
import zio.{Task, ZIO}

final case class AssetOverviewReportCsvRow(
  shortcode: ProjectShortcode,
  totalNrOfAssets: Int,
  nrOfStillImageAssets: Int,
  nrOfMovingImageAssets: Int,
  nrOrOtherAssets: Int,
  sizeOfStillImageOriginals: FileSize,
  sizeOfStillImageDerivatives: FileSize,
  sizeOfMovingImageOriginals: FileSize,
  sizeOfMovingImageDerivatives: FileSize,
  sizeOfMovingImageKeyframes: FileSize,
  sizeOfOtherOriginals: FileSize,
  sizeOfOtherDerivatives: FileSize,
) {

  def toList: List[String | Int] = List(
    shortcode.value,
    totalNrOfAssets,
    nrOfStillImageAssets,
    nrOfMovingImageAssets,
    nrOrOtherAssets,
    prettyPrint(sizeOfStillImageOriginals),
    prettyPrint(sizeOfStillImageDerivatives),
    prettyPrint(sizeOfMovingImageOriginals),
    prettyPrint(sizeOfMovingImageDerivatives),
    prettyPrint(sizeOfMovingImageKeyframes),
    prettyPrint(sizeOfOtherOriginals),
    prettyPrint(sizeOfOtherDerivatives),
  )
}

object AssetOverviewReportCsvRow {
  val headerRow: List[String] = List(
    "shortcode",
    "totalNrOfAssets",
    "nrOfStillImageAssets",
    "nrOfMovingImageAssets",
    "nrOfOtherAssets",
    "sizeOfStillImageOriginals",
    "sizeOfStillImageDerivatives",
    "sizeOfMovingImageOriginals",
    "sizeOfMovingImageDerivatives",
    "sizeOfMovingImageKeyframes",
    "sizeOfOtherOriginals",
    "sizeOfOtherDerivatives",
  )
  def fromReport(rep: AssetOverviewReport): AssetOverviewReportCsvRow = {
    def getFileSize(key: SupportedFileType, typ: String) =
      rep.sizesPerType.sizes.get(key) match {
        case None => FileSize(0)
        case Some(other: SizeInBytesOther) =>
          typ match {
            case "orig"       => other.sizeOrig
            case "derivative" => other.sizeDerivative
            case _            => throw new IllegalArgumentException(s"Unknown type: $typ")
          }
        case Some(still: SizeInBytesMovingImages) =>
          typ match {
            case "orig"       => still.sizeOrig
            case "derivative" => still.sizeDerivative
            case "keyframes"  => still.sizeKeyframes
            case _            => throw new IllegalArgumentException(s"Unknown type: $typ")
          }
      }

    AssetOverviewReportCsvRow(
      shortcode = rep.shortcode,
      totalNrOfAssets = rep.totalNrOfAssets,
      nrOfStillImageAssets = rep.nrOfAssetsPerType.getOrElse(StillImage, 0),
      nrOfMovingImageAssets = rep.nrOfAssetsPerType.getOrElse(MovingImage, 0),
      nrOrOtherAssets = rep.nrOfAssetsPerType.getOrElse(OtherFiles, 0),
      getFileSize(StillImage, "orig"),
      getFileSize(StillImage, "derivative"),
      getFileSize(MovingImage, "orig"),
      getFileSize(MovingImage, "derivative"),
      getFileSize(MovingImage, "keyframes"),
      getFileSize(OtherFiles, "orig"),
      getFileSize(OtherFiles, "derivative"),
    )
  }
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
        writer <- createWriter(path)
        _      <- ZIO.succeed(writer.writeRow(AssetOverviewReportCsvRow.headerRow))
        _ <- ZIO.foreachDiscard(report)(rep =>
               ZIO.succeed(writer.writeRow(AssetOverviewReportCsvRow.fromReport(rep).toList)),
             )
      } yield path
    }
}

object CsvService {
  val layer = zio.ZLayer.derive[CsvService]
}
