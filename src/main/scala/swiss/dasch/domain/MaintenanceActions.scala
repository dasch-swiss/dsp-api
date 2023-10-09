/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.apache.commons.io.FilenameUtils
import swiss.dasch.domain
import swiss.dasch.domain.FileFilters.isJpeg2000
import swiss.dasch.domain.SipiImageFormat.Tif
import zio.*
import zio.json.{ EncoderOps, JsonEncoder }
import zio.nio.file
import zio.nio.file.{ Files, Path }
import zio.stream.{ ZSink, ZStream }

import java.io.IOException

trait MaintenanceActions {
  def createNeedsOriginalsReport(imagesOnly: Boolean): Task[Unit]
  def createNeedsTopLeftCorrectionReport(): Task[Unit]
  def applyTopLeftCorrections(projectPath: Path): Task[Int]
  def createOriginals(projectPath: Path, mapping: Map[String, String]): Task[Int]
}

object MaintenanceActions     {
  def createNeedsOriginalsReport(imagesOnly: Boolean): ZIO[MaintenanceActions, Throwable, Unit]                 =
    ZIO.serviceWithZIO[MaintenanceActions](_.createNeedsOriginalsReport(imagesOnly))
  def createNeedsTopLeftCorrectionReport(): ZIO[MaintenanceActions, Throwable, Unit]                            =
    ZIO.serviceWithZIO[MaintenanceActions](_.createNeedsTopLeftCorrectionReport())
  def applyTopLeftCorrections(projectPath: Path): ZIO[MaintenanceActions, Throwable, Int]                       =
    ZIO.serviceWithZIO[MaintenanceActions](_.applyTopLeftCorrections(projectPath))
  def createOriginals(projectPath: Path, mapping: Map[String, String]): ZIO[MaintenanceActions, Throwable, Int] =
    ZIO.serviceWithZIO[MaintenanceActions](_.createOriginals(projectPath, mapping))
}

final case class MaintenanceActionsLive(
    imageService: ImageService,
    projectService: ProjectService,
    sipiClient: SipiClient,
    storageService: StorageService,
  ) extends MaintenanceActions {

  def createNeedsOriginalsReport(imagesOnly: Boolean): Task[Unit] = {
    val reportName = if (imagesOnly) "needsOriginals_images_only" else "needsOriginals"
    for {
      _                 <- ZIO.logInfo(s"Checking for originals")
      assetDir          <- storageService.getAssetDirectory()
      tmpDir            <- storageService.getTempDirectory()
      projectShortcodes <- projectService.listAllProjects()
      _                 <- ZIO
                             .foreach(projectShortcodes)(shortcode =>
                               Files
                                 .walk(assetDir / shortcode.toString)
                                 .mapZIOPar(8)(originalNotPresent(imagesOnly))
                                 .filter(identity)
                                 .as(shortcode)
                                 .runHead
                             )
                             .map(_.flatten.map(_.toString))
                             .flatMap(saveReport(tmpDir, reportName, _))
                             .zipLeft(ZIO.logInfo(s"Created $reportName.json"))

    } yield ()
  }

  private def originalNotPresent(imagesOnly: Boolean)(path: file.Path): IO[IOException, Boolean] = {
    lazy val assetId = AssetId.makeFromPath(path).map(_.toString).getOrElse("unknown-asset-id")

    def checkIsImageIfNeeded(path: file.Path) = {
      val shouldNotCheckImages = ZIO.succeed(!imagesOnly)
      shouldNotCheckImages || FileFilters.isImage(path)
    }

    FileFilters.isNonHiddenRegularFile(path) &&
    checkIsImageIfNeeded(path) &&
    Files
      .list(path.parent.orNull)
      .map(_.filename.toString)
      .filter(name => name.endsWith(".orig") && name.startsWith(assetId))
      .runHead
      .map(_.isEmpty)
  }

  private def saveReport[A](
      tmpDir: Path,
      name: String,
      report: A,
    )(implicit encoder: JsonEncoder[A]
    ): Task[Unit] =
    Files.createDirectories(tmpDir / "reports") *>
      Files.deleteIfExists(tmpDir / "reports" / s"$name.json") *>
      Files.createFile(tmpDir / "reports" / s"$name.json") *>
      storageService.saveJsonFile(tmpDir / "reports" / s"$name.json", report)

  override def createNeedsTopLeftCorrectionReport(): Task[Unit] =
    for {
      _                 <- ZIO.logInfo(s"Checking for top left correction")
      assetDir          <- storageService.getAssetDirectory()
      tmpDir            <- storageService.getTempDirectory()
      projectShortcodes <- projectService.listAllProjects()
      _                 <-
        ZIO
          .foreach(projectShortcodes)(shortcode =>
            Files
              .walk(assetDir / shortcode.toString)
              .mapZIOPar(8)(imageService.needsTopLeftCorrection)
              .filter(identity)
              .runHead
              .map(_.map(_ => shortcode))
          )
          .map(_.flatten)
          .map(_.map(_.toString))
          .flatMap(saveReport(tmpDir, "needsTopLeftCorrection", _))
          .zipLeft(ZIO.logInfo(s"Created needsTopLeftCorrection.json"))
    } yield ()

  override def applyTopLeftCorrections(projectPath: Path): Task[Int] =
    ZIO.logInfo(s"Starting top left corrections in $projectPath") *>
      findJpeg2000Files(projectPath)
        .mapZIOPar(8)(imageService.applyTopLeftCorrection)
        .map(_.map(_ => 1).getOrElse(0))
        .run(ZSink.sum)
        .tap(sum => ZIO.logInfo(s"Top left corrections applied for $sum files in $projectPath"))

  private def findJpeg2000Files(projectPath: Path) = StorageService.findInPath(projectPath, isJpeg2000)

  override def createOriginals(projectPath: Path, mapping: Map[String, String]): Task[Int] =
    findJpeg2000Files(projectPath)
      .flatMap(findAssetsWithoutOriginal(_, mapping))
      .mapZIOPar(8)(createOriginalAndUpdateInfoFile)
      .run(ZSink.sum)

  final private case class CreateOriginalFor(
      assetId: AssetId,
      jpxPath: Path,
      targetFormat: SipiImageFormat,
      originalFilename: String,
    ) {
    def originalPath: Path = jpxPath.parent.map(_ / s"$assetId.${targetFormat.extension}.orig").orNull
  }

  private def findAssetsWithoutOriginal(jpxPath: Path, mapping: Map[String, String])
      : ZStream[Any, Throwable, CreateOriginalFor] =
    AssetId.makeFromPath(jpxPath) match {
      case Some(assetId) => filterWithoutOriginal(assetId, jpxPath, mapping)
      case None          => ZStream.logWarning(s"Not an assetId: $jpxPath") *> ZStream.empty
    }

  private def filterWithoutOriginal(
      assetId: AssetId,
      jpxPath: Path,
      mapping: Map[String, String],
    ): ZStream[Any, Throwable, CreateOriginalFor] = {
    val createThis = makeCreateOriginalFor(assetId, jpxPath, mapping)
    ZStream
      .fromZIO(Files.exists(createThis.originalPath))
      .flatMap {
        case true  =>
          ZStream.logInfo(s"Original for $jpxPath present, skipping ${createThis.originalPath}") *> ZStream.empty
        case false =>
          ZStream.logDebug(s"Original for $jpxPath not present") *> ZStream.succeed(createThis)
      }
  }

  private def makeCreateOriginalFor(
      assetId: AssetId,
      jpxPath: Path,
      mapping: Map[String, String],
    ) = {
    val fallBackFormat             = Tif
    val originalFilenameMaybe      = mapping.get(jpxPath.filename.toString)
    val originalFileExtensionMaybe = originalFilenameMaybe.map(FilenameUtils.getExtension).filter(_ != null)
    val targetFormat               = originalFileExtensionMaybe.flatMap(SipiImageFormat.fromExtension).getOrElse(fallBackFormat)
    val originalFilename           = originalFilenameMaybe
      .map { fileName =>
        if (fileName.endsWith(targetFormat.extension)) fileName
        else fileName.replace(FilenameUtils.getExtension(fileName), targetFormat.extension)
      }
      .getOrElse(s"$assetId.${targetFormat.extension}")

    CreateOriginalFor(assetId, jpxPath, targetFormat, originalFilename)
  }

  private def createOriginalAndUpdateInfoFile =
    (c: CreateOriginalFor) => createOriginal(c) *> updateAssetInfo(c)

  private def createOriginal(c: CreateOriginalFor) =
    ZIO.logInfo(s"Creating ${c.originalPath}/${c.targetFormat} for ${c.jpxPath}") *>
      sipiClient
        .transcodeImageFile(fileIn = c.jpxPath, fileOut = c.originalPath, outputFormat = c.targetFormat)
        .tap(sipiOut => ZIO.logDebug(s"Sipi response for $c: $sipiOut"))

  private def updateAssetInfo(c: CreateOriginalFor): Task[Int] = {
    val infoFilePath = c.jpxPath.parent.orNull / s"${c.assetId}.info"
    ZIO
      .whenZIO(Files.exists(c.originalPath))(for {
        _    <- ZIO.logInfo(s"Updating ${c.assetId} info file $infoFilePath")
        info <- createNewAssetInfoFileContent(c)
        _    <- Files.deleteIfExists(infoFilePath) *> Files.createFile(infoFilePath)
        _    <- Files.writeBytes(infoFilePath, Chunk.fromArray(info.toJsonPretty.getBytes))
      } yield 1)
      .someOrElseZIO(ZIO.logWarning(s"Sipi did not create an original for $c") *> ZIO.succeed(0))
  }

  private def createNewAssetInfoFileContent(c: CreateOriginalFor): IO[Throwable, AssetInfoFileContent] =
    for {
      checksumOriginal   <- FileChecksumService.createSha256Hash(c.originalPath)
      checksumDerivative <- FileChecksumService.createSha256Hash(c.jpxPath)
    } yield AssetInfoFileContent(
      internalFilename = c.jpxPath.filename.toString,
      originalInternalFilename = c.originalPath.filename.toString,
      originalFilename = c.originalFilename,
      checksumOriginal = checksumOriginal.toString,
      checksumDerivative = checksumDerivative.toString,
    )
}
object MaintenanceActionsLive {
  val layer = ZLayer.derive[MaintenanceActionsLive]
}
