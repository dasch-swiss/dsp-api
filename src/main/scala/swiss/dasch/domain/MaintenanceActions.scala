/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.commons.io.FilenameUtils
import swiss.dasch.api.ActionName
import swiss.dasch.domain
import swiss.dasch.domain.AugmentedPath.*
import swiss.dasch.domain.AugmentedPath.Conversions.given_Conversion_AugmentedPath_Path
import swiss.dasch.domain.FileFilters.isJpeg2000
import swiss.dasch.domain.SipiImageFormat.Tif
import swiss.dasch.domain.SupportedFileType.MovingImage
import zio.json.interop.refined.*
import zio.json.{DeriveJsonCodec, EncoderOps, JsonCodec, JsonEncoder}
import zio.nio.file
import zio.nio.file.{Files, Path}
import zio.stream.{ZSink, ZStream}
import zio.*

import java.io.IOException

trait MaintenanceActions {
  def updateAssetMetadata(projects: Iterable[ProjectFolder]): Task[Unit]
  def createNeedsOriginalsReport(imagesOnly: Boolean): Task[Unit]
  def createNeedsTopLeftCorrectionReport(): Task[Unit]
  def createWasTopLeftCorrectionAppliedReport(): Task[Unit]
  def applyTopLeftCorrections(projectPath: ProjectFolder): Task[Int]
  final def applyTopLeftCorrections(projectPath: Iterable[ProjectFolder]): Task[Int] =
    ZIO
      .foreach(projectPath)(applyTopLeftCorrections)
      .map(_.sum)
      .tap(sum => ZIO.logInfo(s"Finished ${ActionName.ApplyTopLeftCorrection} for $sum files"))
  def createOriginals(projectPath: ProjectFolder, mapping: Map[String, String]): Task[Int]
  def importProjectsToDb(): Task[Unit]
}

final case class MaintenanceActionsLive(
  assetInfoService: AssetInfoService,
  imageService: StillImageService,
  mimeTypeGuesser: MimeTypeGuesser,
  movingImageService: MovingImageService,
  otherFilesService: OtherFilesService,
  projectService: ProjectService,
  sipiClient: SipiClient,
  storageService: StorageService,
) extends MaintenanceActions {

  override def updateAssetMetadata(projects: Iterable[ProjectFolder]): Task[Unit] = {
    def updateSingleAsset(info: AssetInfo): Task[Unit] =
      for {
        assetType <- ZIO
                       .fromOption(SupportedFileType.fromPath(Path(info.originalFilename.value)))
                       .orElseFail(new Exception(s"Could not get asset type from path ${info.originalFilename.value}"))
        newMetadata <- getMetadata(info, assetType)
        _           <- assetInfoService.save(info.copy(metadata = newMetadata))
      } yield ()

    def getMetadata(info: AssetInfo, assetType: SupportedFileType): Task[AssetMetadata] = {
      val original = Original(OrigFile.unsafeFrom(info.original.file), info.originalFilename)
      assetType match {
        case SupportedFileType.StillImage =>
          imageService.extractMetadata(original, JpxDerivativeFile.unsafeFrom(info.derivative.file))

        case SupportedFileType.MovingImage =>
          movingImageService.extractMetadata(original, MovingImageDerivativeFile.unsafeFrom(info.derivative.file))

        case SupportedFileType.OtherFiles =>
          otherFilesService.extractMetadata(original, OtherDerivativeFile.unsafeFrom(info.derivative.file))
      }
    }

    for {
      _ <- ZIO.foreachDiscard(projects) { projectPath =>
             projectService
               .findAssetInfosOfProject(projectPath.shortcode)
               .mapZIOPar(8)(updateSingleAsset(_).logError.ignore)
               .runDrain
           }
      _ <- ZIO.logInfo(s"Finished ${ActionName.UpdateAssetMetadata}")
    } yield ()
  }

  def createNeedsOriginalsReport(imagesOnly: Boolean): Task[Unit] = {
    val reportName = if (imagesOnly) "needsOriginals_images_only" else "needsOriginals"
    for {
      _        <- ZIO.logInfo(s"Checking for originals")
      tmpDir   <- storageService.getTempFolder()
      projects <- projectService.listAllProjects()
      _ <- ZIO
             .foreach(projects)(prj =>
               Files
                 .walk(prj.path)
                 .mapZIOPar(8)(originalNotPresent(imagesOnly))
                 .filter(identity)
                 .as(prj.shortcode)
                 .runHead,
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
    report: A,
  )(implicit encoder: JsonEncoder[A]): Task[Unit] =
    Files.createDirectories(tmpDir / "reports") *>
      Files.deleteIfExists(tmpDir / "reports" / s"$name.json") *>
      Files.createFile(tmpDir / "reports" / s"$name.json") *>
      storageService.saveJsonFile(tmpDir / "reports" / s"$name.json", report)

  override def createNeedsTopLeftCorrectionReport(): Task[Unit] =
    for {
      _        <- ZIO.logInfo(s"Checking for top left correction")
      tmpDir   <- storageService.getTempFolder()
      projects <- projectService.listAllProjects()
      _ <-
        ZIO
          .foreach(projects)(prj =>
            Files
              .walk(prj.path)
              .mapZIOPar(8)(imageService.needsTopLeftCorrection)
              .filter(identity)
              .runHead
              .map(_.map(_ => prj.shortcode)),
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
      _        <- ZIO.logInfo(s"Checking where top left correction was applied")
      tmpDir   <- storageService.getTempFolder()
      projects <- projectService.listAllProjects()
      assetsWithBak <-
        ZIO
          .foreach(projects) { prj =>
            Files
              .walk(prj.path)
              .flatMapPar(8)(hasBeenTopLeftTransformed)
              .runCollect
              .map { assetIdDimensions =>
                ProjectWithBakFiles(
                  prj.shortcode,
                  assetIdDimensions.map { case (id: AssetId, dim: Dimensions) => ReportAsset(id, dim) },
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
      derivative         = JpxDerivativeFile.unsafeFrom(path.parent.head / derivativeFilename)
      _                 <- ZIO.fail(None).whenZIO(FileFilters.isJpeg2000(derivative.file).negate.asSomeError)
      // get the dimensions
      dimensions <- imageService.getDimensions(derivative).asSomeError
    } yield (assetId, dimensions)

    ZStream.fromZIOOption(
      zioTask
        // None.type errors are just a sign that the path should be ignored. Some.type errors are real errors.
        .tapSomeError { case Some(e) => ZIO.logError(s"Error while processing $path: $e") }
        // We have logged real errors above, from here on out ignore all errors so that the stream can continue.
        .orElseFail(None),
    )
  }

  override def applyTopLeftCorrections(projectPath: ProjectFolder): Task[Int] =
    ZIO.logInfo(s"Starting top left corrections in ${projectPath.path}") *>
      findJpeg2000Files(projectPath)
        .mapZIOPar(8)(imageService.applyTopLeftCorrection)
        .map(_.map(_ => 1).getOrElse(0))
        .run(ZSink.sum)
        .tap(sum => ZIO.logInfo(s"Top left corrections applied for $sum files in ${projectPath.path}"))

  private def findJpeg2000Files(projectPath: ProjectFolder) = StorageService.findInPath(projectPath.path, isJpeg2000)

  override def createOriginals(projectPath: ProjectFolder, mapping: Map[String, String]): Task[Int] =
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

  private def findAssetsWithoutOriginal(
    jpxPath: Path,
    mapping: Map[String, String],
  ): ZStream[Any, Throwable, CreateOriginalFor] =
    AssetId.fromPath(jpxPath) match {
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
        case true =>
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
      .someOrElseZIO(ZIO.logWarning(s"Sipi did not create an original for $c").as(0))
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
      checksumDerivative = checksumDerivative,
    )

  override def importProjectsToDb(): Task[Unit] = for {
    prjFolders <- projectService.listAllProjects()
    _          <- ZIO.foreachDiscard(prjFolders.map(_.shortcode))(projectService.addProjectToDb)
  } yield ()

}
object MaintenanceActionsLive {
  val layer = ZLayer.derive[MaintenanceActionsLive]
}
