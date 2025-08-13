/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain
import swiss.dasch.domain.SupportedFileType.MovingImage
import zio.*
import zio.nio.file.Path

final case class ChecksumReport(results: Map[AssetInfo, Chunk[ChecksumResult]], nrOfAssets: Int)

object ChecksumReport {
  def make(map: Map[AssetInfo, Chunk[ChecksumResult]]): ChecksumReport = ChecksumReport(map, map.size)
}

final case class AssetOverviewReport(
  shortcode: ProjectShortcode,
  totalNrOfAssets: Int,
  nrOfAssetsPerType: Map[SupportedFileType, Int],
  sizesPerType: SizeInBytesReport,
) { self =>
  def add(someSize: SizeInBytesPerType): AssetOverviewReport =
    self.copy(sizesPerType = sizesPerType.add(someSize))
}

object AssetOverviewReport {
  def make(shortcode: ProjectShortcode) =
    AssetOverviewReport(shortcode, 0, Map.empty, SizeInBytesReport(Map.empty[SupportedFileType, SizeInBytesPerType]))
}

final case class SizeInBytesReport(sizes: Map[SupportedFileType, SizeInBytesPerType]) { self =>
  def add(size: SizeInBytesPerType): SizeInBytesReport =
    self.copy(sizes.updatedWith(size.fileType) {
      case None               => Some(size)
      case Some(existingSize) => Some(existingSize.add(size))
    })
}

final case class FileSize(sizeInBytes: BigDecimal) extends AnyVal {
  def +(other: FileSize): FileSize = FileSize(sizeInBytes + other.sizeInBytes)
}

object FileSize {

  val zero: FileSize = FileSize(0)

  def apply(sizeInBytes: Long): FileSize = FileSize(BigDecimal.exact(sizeInBytes))

  private val units = List("B", "KiB", "MiB", "GiB", "TiB")

  def prettyPrint(fileSize: FileSize): String = {
    @annotation.tailrec
    def helper(size: BigDecimal, units: List[String]): String = units match {
      case unit :: _ if size < 1024 => f"$size%.2f $unit"
      case _ :: rest                => helper(size / 1024, rest)
      case Nil                      => f"$size%.2f B"
    }

    helper(fileSize.sizeInBytes, units)
  }

}

final case class SizeInBytesPerType(
  fileType: SupportedFileType,
  sizeOrig: FileSize,
  sizeDerivative: FileSize,
  sizeKeyframes: FileSize,
) {
  self =>
  def add(other: SizeInBytesPerType): SizeInBytesPerType = {
    if (fileType != other.fileType) throw new IllegalArgumentException("Cannot add sizes of different file types")
    self.copy(
      sizeOrig = self.sizeOrig + other.sizeOrig,
      sizeDerivative = self.sizeDerivative + other.sizeDerivative,
      sizeKeyframes = self.sizeKeyframes + other.sizeKeyframes,
    )
  }

  val sum: FileSize = self.sizeOrig + self.sizeDerivative + self.sizeKeyframes
}

final case class ReportService(
  projectService: ProjectService,
  assetService: FileChecksumService,
  storageService: StorageService,
  csvService: CsvService,
) {

  def checksumReport(projectShortcode: ProjectShortcode): Task[Option[ChecksumReport]] =
    projectService
      .findProject(projectShortcode)
      .flatMap {
        case Some(_) =>
          projectService
            .findAssetInfosOfProject(projectShortcode)
            .mapZIOPar(StorageService.maxParallelism())(info => assetService.verifyChecksum(info).map((info, _)))
            .runCollect
            .map(_.toMap)
            .map(ChecksumReport.make)
            .map(Some(_))
        case None => ZIO.none
      }

  def assetsOverviewReport(projectShortcode: ProjectShortcode): Task[Option[AssetOverviewReport]] =
    ZIO.logInfo(s"Calculating asset overview report for project $projectShortcode") *>
      projectService
        .findProject(projectShortcode)
        .flatMap {
          case Some(_) =>
            ZIO.logInfo(s"Project $projectShortcode exists, calculating asset overview report") *>
              projectService
                .findAssetInfosOfProject(projectShortcode)
                .runFoldZIO(AssetOverviewReport.make(projectShortcode))(updateAssetOverviewReport)
                .map(Some(_))
          case None => ZIO.none
        } <* ZIO.logInfo(s"Finished overview report for project $projectShortcode")

  def saveReports(reports: Seq[AssetOverviewReport]): Task[Path] =
    for {
      reportsPath    <- getReportsPath
      reportFilename <- timedReportName("asset_overview_report", "csv")
      created        <- csvService.writeReportToCsv(reports, reportsPath / reportFilename)
    } yield created

  private def getReportsPath: Task[Path] = for {
    tmpDir   <- storageService.getTempFolder()
    reportDir = tmpDir / "reports"
    _        <- storageService.createDirectories(reportDir)
  } yield reportDir

  private def timedReportName(filename: String, fileExtension: String): UIO[String] =
    Clock.instant.map(now => s"${filename}_$now.$fileExtension")

  private def updateAssetOverviewReport(
    report: AssetOverviewReport,
    info: AssetInfo,
  ): Task[AssetOverviewReport] =
    ZIO
      .fromOption(SupportedFileType.fromPath(Path(info.originalFilename.value)))
      .tapSomeError { case None => ZIO.logWarning(s"Could not determine file type for asset ${info.assetRef}") }
      .flatMap(updateAssetOverviewReport(report, info, _).asSomeError)
      .unsome
      .map(_.getOrElse(report))

  private def updateAssetOverviewReport(
    report: AssetOverviewReport,
    info: AssetInfo,
    fileType: SupportedFileType,
  ): ZIO[Any, Throwable, AssetOverviewReport] =
    ZIO.logInfo(s"Calculating size for asset ${info.assetRef} of type $fileType") *>
      calculateSizeInBytes(fileType, info)
        .map(size =>
          report.copy(
            totalNrOfAssets = report.totalNrOfAssets + 1,
            nrOfAssetsPerType = report.nrOfAssetsPerType.updatedWith(fileType)(_.map(_ + 1).orElse(Some(1))),
            sizesPerType = report.sizesPerType.add(size),
          ),
        )

  private def calculateSizeInBytes(fileType: SupportedFileType, info: AssetInfo): Task[SizeInBytesPerType] =
    for {
      assetFolder    <- storageService.getAssetFolder(info.assetRef)
      sizeOrig       <- storageService.calculateSizeInBytes(assetFolder / info.original.file.filename)
      sizeDerivative <- storageService.calculateSizeInBytes(assetFolder / info.derivative.file.filename)
      sizeKeyframes <-
        fileType match {
          case MovingImage => storageService.calculateSizeInBytes(assetFolder / info.assetRef.id.toString)
          case _           => ZIO.succeed(FileSize.zero)
        }
    } yield SizeInBytesPerType(fileType, sizeOrig, sizeDerivative, sizeKeyframes)

}
object ReportService {
  val layer = ZLayer.derive[ReportService]
}
