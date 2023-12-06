/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.config.Configuration.IngestConfig
import swiss.dasch.domain.Asset.{OtherAsset, StillImageAsset}
import swiss.dasch.domain.DerivativeFile.OtherDerivativeFile
import swiss.dasch.domain.PathOps.fileExtension
import zio.nio.file.{Files, Path}
import zio.{IO, Task, ZIO, ZLayer}

import java.io.{FileNotFoundException, IOException}
import java.nio.file.StandardOpenOption

trait BulkIngestService {

  def startBulkIngest(shortcode: ProjectShortcode): Task[IngestResult]
}

object BulkIngestService {
  def startBulkIngest(shortcode: ProjectShortcode): ZIO[BulkIngestService, Throwable, IngestResult] =
    ZIO.serviceWithZIO[BulkIngestService](_.startBulkIngest(shortcode))
}

case class IngestResult(success: Int = 0, failed: Int = 0) {
  def +(other: IngestResult): IngestResult = IngestResult(success + other.success, failed + other.failed)
}

object IngestResult {
  val success: IngestResult = IngestResult(success = 1)
  val failed: IngestResult  = IngestResult(failed = 1)
}

final case class BulkIngestServiceLive(
  storage: StorageService,
  sipiClient: SipiClient,
  assetInfo: AssetInfoService,
  imageService: ImageService,
  config: IngestConfig
) extends BulkIngestService {

  override def startBulkIngest(project: ProjectShortcode): Task[IngestResult] =
    for {
      _           <- ZIO.logInfo(s"Starting bulk ingest for project $project")
      importDir   <- storage.getBulkIngestImportFolder(project)
      _           <- ZIO.fail(new IOException(s"Import directory '$importDir' does not exist")).unlessZIO(Files.exists(importDir))
      mappingFile <- storage.createBulkIngestMappingFile(project)
      _           <- ZIO.logInfo(s"Import dir: $importDir, mapping file: $mappingFile")
      total       <- StorageService.findInPath(importDir, FileFilters.isNonHiddenRegularFile).runCount
      sum <- StorageService
               .findInPath(importDir, FileFilters.isSupported)
               .mapZIOPar(config.bulkMaxParallel)(file =>
                 ingestSingleFile(file, project, mappingFile).logError
                   .catchNonFatalOrDie(e =>
                     ZIO
                       .logError(s"Error ingesting image $file: ${e.getMessage}")
                       .as(IngestResult.failed)
                   )
               )
               .runFold(IngestResult())(_ + _)
      _ <- {
        val countAssets  = sum.success + sum.failed
        val countSuccess = sum.success
        val countFailed  = sum.failed
        ZIO.logInfo(
          s"Finished bulk ingest for project $project. " +
            s"Found $countAssets assets from $total files. " +
            s"Ingested $countSuccess successfully and failed $countFailed assets (See logs above for more details)."
        )
      }
    } yield sum

  private def ingestSingleFile(
    fileToIngest: Path,
    project: ProjectShortcode,
    csv: Path
  ): Task[IngestResult] =
    for {
      _        <- ZIO.logInfo(s"Ingesting file $fileToIngest")
      assetRef <- AssetRef.makeNew(project)
      _ <- ZIO.whenZIO(FileFilters.isNonHiddenRegularFile(fileToIngest).negate)(
             ZIO.fail(new FileNotFoundException(s"File $fileToIngest is not a regular file"))
           )
      _        <- storage.getAssetDirectory(assetRef).tap(Files.createDirectories(_))
      original <- createOriginalFileInAssetDir(fileToIngest, assetRef)
      asset <- ZIO
                 .fromOption(SupportedFileType.fromPath(fileToIngest))
                 .orElseFail(new IllegalArgumentException("Unsupported file type."))
                 .flatMap { it =>
                   it match {
                     case SupportedFileType.StillImage => handleImageFile(original, assetRef)
                     case SupportedFileType.Other      => handleOtherFile(original, assetRef)
                     case SupportedFileType.MovingImage =>
                       ZIO.fail(new NotImplementedError("Video files are not supported yet."))
                   }
                 }
      _ <- assetInfo.createAssetInfo(asset)
      _ <- updateMappingCsv(csv, fileToIngest, asset)
      _ <- Files.delete(fileToIngest)
      _ <- ZIO.logInfo(s"Finished ingesting file $fileToIngest")
    } yield IngestResult.success

  private def createOriginalFileInAssetDir(file: Path, assetRef: AssetRef): IO[IOException, Original] = for {
    _               <- ZIO.logInfo(s"Creating original for $file, $assetRef")
    assetDir        <- storage.getAssetDirectory(assetRef)
    originalPath     = assetDir / s"${assetRef.id}.${file.fileExtension}.orig"
    _               <- storage.copyFile(file, originalPath)
    originalFile     = OriginalFile.unsafeFrom(originalPath)
    originalFileName = NonEmptyString.unsafeFrom(file.filename.toString)
  } yield Original(originalFile, originalFileName)

  private def handleImageFile(original: Original, assetRef: AssetRef): Task[StillImageAsset] =
    ZIO.logInfo(s"Creating derivative for image $original, $assetRef") *>
      imageService
        .createDerivative(original.file)
        .tapError(_ => Files.delete(original.file.toPath).ignore)
        .map(derivative => Asset.makeStillImage(assetRef, original, derivative))

  private def handleOtherFile(original: Original, assetRef: AssetRef): Task[OtherAsset] = {
    def createDerivative(original: Original) =
      for {
        folder        <- storage.getAssetDirectory(assetRef)
        derivativePath = folder / s"${assetRef.id}.${original.file.toPath.fileExtension}"
        _ <- storage
               .copyFile(original.file.toPath, derivativePath)
               .tapError(_ => Files.delete(original.file.toPath).ignore)
      } yield OtherDerivativeFile.unsafeFrom(derivativePath)
    ZIO.logInfo(s"Creating derivative for other $original, $assetRef") *>
      createDerivative(original).map(derivative => Asset.makeOther(assetRef, original, derivative))
  }

  private def updateMappingCsv(
    mappingFile: Path,
    imageToIngest: Path,
    asset: Asset
  ) =
    ZIO.logInfo(s"Updating mapping file $mappingFile, $asset") *> {
      for {
        importDir                <- storage.getBulkIngestImportFolder(asset.belongsToProject)
        imageToIngestRelativePath = importDir.relativize(imageToIngest)
        _ <- Files.writeLines(
               mappingFile,
               List(s"$imageToIngestRelativePath,${asset.derivative.filename}"),
               openOptions = Set(StandardOpenOption.APPEND)
             )
      } yield ()
    }
}

object BulkIngestServiceLive {
  val layer = ZLayer.derive[BulkIngestServiceLive]
}
