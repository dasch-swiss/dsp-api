/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import org.apache.commons.io.FilenameUtils
import swiss.dasch.domain
import swiss.dasch.domain.DerivativeFile.JpxDerivativeFile
import swiss.dasch.domain.FileFilters.isJpeg2000
import swiss.dasch.domain.SipiImageFormat.Tif
import zio.*
import zio.json.interop.refined.*
import zio.json.{DeriveJsonCodec, EncoderOps, JsonCodec, JsonEncoder}
import zio.nio.file
import zio.nio.file.{Files, Path}
import zio.stream.{ZSink, ZStream}

import java.io.IOException

trait MaintenanceActions {
  def extractImageMetadataAndAddToInfoFile(): Task[Unit]
  def createNeedsOriginalsReport(imagesOnly: Boolean): Task[Unit]
  def createNeedsTopLeftCorrectionReport(): Task[Unit]
  def createWasTopLeftCorrectionAppliedReport(): Task[Unit]
  def applyTopLeftCorrections(projectPath: Path): Task[Int]
  def createOriginals(projectPath: Path, mapping: Map[String, String]): Task[Int]
}

final case class MaintenanceActionsLive(
  assetInfoService: AssetInfoService,
  imageService: StillImageService,
  projectService: ProjectService,
  sipiClient: SipiClient,
  storageService: StorageService
) extends MaintenanceActions {

  override def extractImageMetadataAndAddToInfoFile(): Task[Unit] = {
    def updateSingleFile(path: Path, shortcode: ProjectShortcode): Task[Unit] =
      for {
        jpx <- ZIO
                 .fromOption(JpxDerivativeFile.from(path))
                 .orElseFail(new Exception(s"Is not a jpx file: $path"))
        id <- ZIO
                .fromOption(AssetId.fromPath(path))
                .orElseFail(new Exception(s"Could not get asset id from path $path"))
        dim <- imageService.getDimensions(jpx)
        _   <- assetInfoService.updateStillImageMetadata(AssetRef(id, shortcode), dim)
      } yield ()

    for {
      projectShortcodes <- projectService.listAllProjects()
      assetDir          <- storageService.getAssetDirectory()
      _ <- ZIO.foreachDiscard(projectShortcodes) { shortcode =>
             Files
               .walk(assetDir / s"$shortcode")
               .filterZIO(FileFilters.isJpeg2000)
               .mapZIOPar(8)(updateSingleFile(_, shortcode).logError)
               .runDrain
           }
      _ <- ZIO.logInfo(s"Finished extract StillImage metadata")
    } yield ()
  }

  def createNeedsOriginalsReport(imagesOnly: Boolean): Task[Unit] = {
    val reportName = if (imagesOnly) "needsOriginals_images_only" else "needsOriginals"
    for {
      _                 <- ZIO.logInfo(s"Checking for originals")
      assetDir          <- storageService.getAssetDirectory()
      tmpDir            <- storageService.getTempDirectory()
      projectShortcodes <- projectService.listAllProjects()
      _ <- ZIO
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
    lazy val assetId = AssetId.fromPath(path).map(_.toString).getOrElse("unknown-asset-id")

    def checkIsImageIfNeeded(path: file.Path) = {
      val shouldNotCheckImages = ZIO.succeed(!imagesOnly)
      shouldNotCheckImages || FileFilters.isStillImage(path)
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
    report: A
  )(implicit encoder: JsonEncoder[A]): Task[Unit] =
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
      _ <-
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

  case class ReportAsset(id: AssetId, dimensions: Dimensions)
  object ReportAsset {
    given codec: JsonCodec[ReportAsset] = DeriveJsonCodec.gen[ReportAsset]
  }
  case class ProjectWithBakFiles(id: ProjectShortcode, assetIds: Chunk[ReportAsset])
  object ProjectWithBakFiles {
    given codec: JsonCodec[ProjectWithBakFiles] = DeriveJsonCodec.gen[ProjectWithBakFiles]
  }
  case class ProjectsWithBakfilesReport(projects: Chunk[ProjectWithBakFiles])
  object ProjectsWithBakfilesReport {
    given codec: JsonCodec[ProjectsWithBakfilesReport] = DeriveJsonCodec.gen[ProjectsWithBakfilesReport]
  }

  override def createWasTopLeftCorrectionAppliedReport(): Task[Unit] =
    for {
      _                 <- ZIO.logInfo(s"Checking where top left correction was applied")
      assetDir          <- storageService.getAssetDirectory()
      tmpDir            <- storageService.getTempDirectory()
      projectShortcodes <- projectService.listAllProjects()
      assetsWithBak <-
        ZIO
          .foreach(projectShortcodes) { shortcode =>
            Files
              .walk(assetDir / shortcode.toString)
              .flatMapPar(8)(hasBeenTopLeftTransformed)
              .runCollect
              .map { assetIdDimensions =>
                ProjectWithBakFiles(
                  shortcode,
                  assetIdDimensions.map { case (id: AssetId, dim: Dimensions) => ReportAsset(id, dim) }
                )
              }
          }
      report = ProjectsWithBakfilesReport(assetsWithBak.filter(_.assetIds.nonEmpty))
      _     <- saveReport(tmpDir, "wasTopLeftCorrectionApplied", report)
      _     <- ZIO.logInfo(s"Created wasTopLeftCorrectionApplied.json")
    } yield ()

  private def hasBeenTopLeftTransformed(path: Path): ZStream[Any, Throwable, (AssetId, Dimensions)] = {
    val zioTask: ZIO[Any, Option[Throwable], (AssetId, Dimensions)] = for {
      // must be a .bak file
      bakFile <- ZIO.succeed(path).whenZIO(FileFilters.isBakFile(path)).some
      // must have an AssetId
      assetId <- ZIO.fromOption(AssetId.fromPath(bakFile))
      // must have a corresponding Jpeg2000 derivative
      bakFilename        = bakFile.filename.toString
      derivativeFilename = bakFilename.substring(0, bakFilename.length - ".bak".length)
      derivativeFile     = path.parent.map(_ / derivativeFilename).orNull
      _                 <- ZIO.fail(None).whenZIO(FileFilters.isJpeg2000(derivativeFile).negate.asSomeError)
      jpxDerivative      = JpxDerivativeFile.unsafeFrom(derivativeFile)
      // get the dimensions
      dimensions <- imageService.getDimensions(jpxDerivative).asSomeError
    } yield (assetId, dimensions)

    ZStream.fromZIOOption(
      zioTask
        // None.type errors are just a sign that the path should be ignored. Some.type errors are real errors.
        .tapSomeError { case Some(e) => ZIO.logError(s"Error while processing $path: $e") }
        // We have logged real errors above, from here on out ignore all errors so that the stream can continue.
        .orElseFail(None)
    )
  }

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
    originalFilename: String
  ) {
    def originalPath: Path = jpxPath.parent.map(_ / s"$assetId.${targetFormat.extension}.orig").orNull
  }

  private def findAssetsWithoutOriginal(
    jpxPath: Path,
    mapping: Map[String, String]
  ): ZStream[Any, Throwable, CreateOriginalFor] =
    AssetId.fromPath(jpxPath) match {
      case Some(assetId) => filterWithoutOriginal(assetId, jpxPath, mapping)
      case None          => ZStream.logWarning(s"Not an assetId: $jpxPath") *> ZStream.empty
    }

  private def filterWithoutOriginal(
    assetId: AssetId,
    jpxPath: Path,
    mapping: Map[String, String]
  ): ZStream[Any, Throwable, CreateOriginalFor] = {
    val createThis = makeCreateOriginalFor(assetId, jpxPath, mapping)
    ZStream
      .fromZIO(Files.exists(createThis.originalPath))
      .flatMap {
        case true =>
          ZStream.logInfo(s"Original for $jpxPath present, skipping ${createThis.originalPath}") *> ZStream.empty
        case false =>
          ZStream.logDebug(s"Original for $jpxPath not present") *> ZStream.succeed(createThis)
      }
  }

  private def makeCreateOriginalFor(
    assetId: AssetId,
    jpxPath: Path,
    mapping: Map[String, String]
  ) = {
    val fallBackFormat             = Tif
    val originalFilenameMaybe      = mapping.get(jpxPath.filename.toString)
    val originalFileExtensionMaybe = originalFilenameMaybe.map(FilenameUtils.getExtension).filter(_ != null)
    val targetFormat               = originalFileExtensionMaybe.flatMap(SipiImageFormat.fromExtension).getOrElse(fallBackFormat)
    val originalFilename = originalFilenameMaybe.map { fileName =>
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
      internalFilename = NonEmptyString.unsafeFrom(c.jpxPath.filename.toString),
      originalInternalFilename = NonEmptyString.unsafeFrom(c.originalPath.filename.toString),
      originalFilename = NonEmptyString.unsafeFrom(c.originalFilename),
      checksumOriginal = checksumOriginal,
      checksumDerivative = checksumDerivative
    )
}
object MaintenanceActionsLive {
  val layer = ZLayer.derive[MaintenanceActionsLive]
}
