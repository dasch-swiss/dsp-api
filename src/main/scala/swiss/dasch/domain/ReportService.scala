/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain
import swiss.dasch.domain.SizeInBytesPerType.{SizeInBytesMovingImages, SizeInBytesOther}
import zio.*
import zio.json.{DeriveJsonEncoder, JsonEncoder, JsonFieldDecoder, JsonFieldEncoder}
import zio.nio.file.Path

final case class ChecksumReport(results: Map[AssetInfo, Chunk[ChecksumResult]], nrOfAssets: Int)

object ChecksumReport {
  def make(map: Map[AssetInfo, Chunk[ChecksumResult]]): ChecksumReport = ChecksumReport(map, map.size)
}

final case class AssetOverviewReport(
  shortcode: ProjectShortcode,
  totalNrOfAssets: Int,
  nrOfAssetsPerType: Map[SupportedFileType, Int],
  sizesPerType: SizeInBytesReport
) { self =>
  def add(someSize: SizeInBytesPerType): AssetOverviewReport =
    self.copy(sizesPerType = sizesPerType.add(someSize))
}

object AssetOverviewReport {

  import zio.json.*
  import zio.json.interop.refined.encodeRefined

  given JsonFieldEncoder[SupportedFileType] = JsonFieldEncoder[String].contramap(_.toString)

  given JsonEncoder[AssetOverviewReport] = DeriveJsonEncoder.gen[AssetOverviewReport]

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
object SizeInBytesReport {

  given JsonFieldEncoder[SupportedFileType] = JsonFieldEncoder[String].contramap(_.toString)
  given JsonFieldDecoder[SupportedFileType] = JsonFieldDecoder[String].map(s => SupportedFileType.valueOf(s))
  given JsonEncoder[SizeInBytesReport]      = DeriveJsonEncoder.gen[SizeInBytesReport]
}

final case class FileSize(sizeInBytes: BigDecimal) {
  def +(other: FileSize): FileSize = FileSize(sizeInBytes + other.sizeInBytes)

}
object FileSize {
  given JsonEncoder[FileSize] = JsonEncoder[String].contramap(prettyPrint)

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
sealed trait SizeInBytesPerType {
  def fileType: SupportedFileType
  def add(other: SizeInBytesPerType): SizeInBytesPerType
}
object SizeInBytesPerType {
  given JsonEncoder[SizeInBytesPerType] = DeriveJsonEncoder.gen[SizeInBytesPerType]

  final case class SizeInBytesOther(fileType: SupportedFileType, sizeOrig: FileSize, sizeDerivative: FileSize)
      extends SizeInBytesPerType { self =>
    override def add(other: SizeInBytesPerType): SizeInBytesOther =
      other match {
        case otherSize: SizeInBytesOther =>
          self.copy(
            sizeOrig = sizeOrig + otherSize.sizeOrig,
            sizeDerivative = sizeDerivative + otherSize.sizeDerivative
          )
        case _ => self
      }
  }

  final case class SizeInBytesMovingImages(
    sizeOrig: FileSize,
    sizeDerivative: FileSize,
    sizeKeyframes: FileSize
  ) extends SizeInBytesPerType { self =>
    val fileType: SupportedFileType = SupportedFileType.MovingImage
    def add(other: SizeInBytesPerType): SizeInBytesMovingImages =
      other match {
        case otherSize: SizeInBytesMovingImages =>
          self.copy(
            sizeOrig = sizeOrig + otherSize.sizeOrig,
            sizeDerivative = sizeDerivative + otherSize.sizeDerivative,
            sizeKeyframes = sizeKeyframes + otherSize.sizeKeyframes
          )
        case _ => this
      }
  }
}

final case class ReportService(
  projectService: ProjectService,
  assetService: FileChecksumService,
  storageService: StorageService
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

  def saveReport[A](name: String, report: A)(using encoder: JsonEncoder[A]): Task[Path] = for {
    tmpDir    <- storageService.getTempFolder()
    reportDir  = tmpDir / "reports"
    _         <- storageService.createDirectories(reportDir)
    now       <- Clock.instant
    reportFile = reportDir / s"${name}_$now.json"
    _         <- storageService.saveJsonFile(reportFile, report)
  } yield reportFile

  private def updateAssetOverviewReport(
    report: AssetOverviewReport,
    info: AssetInfo
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
    fileType: SupportedFileType
  ): ZIO[Any, Throwable, AssetOverviewReport] =
    ZIO.logInfo(s"Calculating size for asset ${info.assetRef} of type $fileType") *>
      calculateSizeInBytes(fileType, info)
        .map(size =>
          report.copy(
            totalNrOfAssets = report.totalNrOfAssets + 1,
            nrOfAssetsPerType = report.nrOfAssetsPerType.updatedWith(fileType)(_.map(_ + 1).orElse(Some(1))),
            sizesPerType = report.sizesPerType.add(size)
          )
        )

  private def calculateSizeInBytes(fileType: SupportedFileType, info: AssetInfo): Task[SizeInBytesPerType] =
    fileType match {
      case SupportedFileType.MovingImage => calculateSizeInBytesMovingImages(info)
      case _                             => calculateSizeInBytesOther(fileType, info)
    }

  private def calculateSizeInBytesMovingImages(info: AssetInfo): Task[SizeInBytesMovingImages] =
    for {
      assetFolder    <- storageService.getAssetFolder(info.assetRef)
      sizeOrig       <- storageService.calculateSizeInBytes(assetFolder / info.original.file.filename)
      sizeDerivative <- storageService.calculateSizeInBytes(assetFolder / info.derivative.file.filename)
      sizeKeyframes  <- storageService.calculateSizeInBytes(assetFolder / info.assetRef.id.toString)

    } yield SizeInBytesMovingImages(sizeOrig, sizeDerivative, sizeKeyframes)

  private def calculateSizeInBytesOther(fileType: SupportedFileType, info: AssetInfo): Task[SizeInBytesOther] =
    for {
      assetFolder    <- storageService.getAssetFolder(info.assetRef)
      sizeOrig       <- storageService.calculateSizeInBytes(assetFolder / info.original.file.filename)
      sizeDerivative <- storageService.calculateSizeInBytes(assetFolder / info.derivative.file.filename)
    } yield SizeInBytesOther(fileType, sizeOrig, sizeDerivative)
}
object ReportService {
  val layer = ZLayer.derive[ReportService]
}
