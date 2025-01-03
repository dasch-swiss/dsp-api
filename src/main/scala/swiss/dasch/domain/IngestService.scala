/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.commons.io.FilenameUtils
import swiss.dasch.domain.Asset.{MovingImageAsset, StillImageAsset}
import swiss.dasch.domain.AugmentedPath.Conversions.given_Conversion_AugmentedPath_Path
import swiss.dasch.domain.AugmentedPath.{AssetFolder, AudioDerivativeFile, OrigFile, OtherDerivativeFile}
import swiss.dasch.domain.PathOps.fileExtension
import swiss.dasch.domain.SupportedFileType.{Audio, MovingImage, OtherFiles, StillImage}
import zio.nio.file.{Files, Path}
import zio.{IO, Task, ZIO, ZLayer}

import java.io.IOException

trait IngestService {
  def ingestFile(fileToIngest: Path, project: ProjectShortcode): Task[Asset]
}

class IngestServiceLive(
  assetInfo: AssetInfoService,
  movingImageService: MovingImageService,
  otherFilesService: OtherFilesService,
  stillImageService: StillImageService,
  projectService: ProjectService,
  storage: StorageService,
) extends IngestService {

  def ingestFile(fileToIngest: Path, project: ProjectShortcode): Task[Asset] =
    for {
      _ <- ZIO.logInfo(s"Ingesting file $fileToIngest")
      _ <- ZIO.unlessZIO(FileFilters.isSupported(fileToIngest))(
             ZIO.fail(new IllegalArgumentException(s"File $fileToIngest is not a supported file.")),
           )
      ref      <- AssetRef.makeNew(project)
      _        <- projectService.findOrCreateProject(ref.belongsToProject)
      assetDir <- ensureAssetDirectoryExists(ref)
      asset    <- ingestAsset(fileToIngest, assetDir).tapError(_ => tryCleanup(assetDir).logError.ignore)
      _        <- ZIO.logInfo(s"Successfully ingesting file $fileToIngest as ${asset.ref}")
    } yield asset

  private def ingestAsset(fileToIngest: Path, assetDir: AssetFolder): ZIO[Any, Throwable, Asset] =
    for {
      original <- createOriginalFileInAssetDir(fileToIngest, assetDir)
      asset <- ZIO
                 .fromOption(SupportedFileType.fromPath(fileToIngest))
                 .orElseFail(new IllegalArgumentException("Unsupported file type."))
                 .flatMap {
                   case StillImage  => handleImageFile(original, assetDir)
                   case MovingImage => handleMovingImageFile(original, assetDir)
                   case Audio       => handleAudioFile(original, assetDir)
                   case OtherFiles  => handleOtherFile(original, assetDir)
                 }
      _ <- assetInfo.createAssetInfo(asset).tap(assetInfo.save).logError
      _ <- storage.delete(fileToIngest)
    } yield asset

  private def ensureAssetDirectoryExists(assetRef: AssetRef): IO[IOException, AssetFolder] =
    storage.getAssetFolder(assetRef).tap(storage.createDirectories(_))

  private def createOriginalFileInAssetDir(file: Path, assetDir: AssetFolder) =
    ZIO.logInfo(s"Creating original for $file, ${assetDir.assetRef}") *> {
      val orig             = OrigFile.unsafeFrom(assetDir / s"${assetDir.assetId}.${file.fileExtension}.orig")
      val originalFileName = NonEmptyString.unsafeFrom(file.filename.toString)
      storage.copyFile(file, orig).as(Original(orig, originalFileName))
    }

  private def handleImageFile(original: Original, assetDir: AssetFolder): Task[StillImageAsset] =
    ZIO.logInfo(s"Creating derivative for image $original, ${assetDir.assetRef}") *> {
      for {
        derivative <- stillImageService.createDerivative(original.file)
        metadata   <- stillImageService.extractMetadata(original, derivative)
      } yield Asset.makeStillImage(assetDir.assetRef, original, derivative, metadata)
    }

  private def handleAudioFile(original: Original, assetDir: AssetFolder) =
    ZIO.logInfo(s"Creating derivative for audio $original, ${assetDir.assetRef}") *> {
      val fileExtension = FilenameUtils.getExtension(original.originalFilename.toString)
      val derivative    = AudioDerivativeFile.unsafeFrom(assetDir / s"${assetDir.assetId}.$fileExtension")
      handleAudioAndOtherFile(original, assetDir, derivative)
    }

  private def handleOtherFile(original: Original, assetDir: AssetFolder) =
    ZIO.logInfo(s"Creating derivative for other $original, ${assetDir.assetRef}") *> {
      val fileExtension = FilenameUtils.getExtension(original.originalFilename.toString)
      val derivative    = OtherDerivativeFile.unsafeFrom(assetDir / s"${assetDir.assetId}.$fileExtension")
      handleAudioAndOtherFile(original, assetDir, derivative)
    }

  private def handleAudioAndOtherFile(
    original: Original,
    assetDir: AssetFolder,
    derivative: AudioDerivativeFile | OtherDerivativeFile,
  ) =
    for {
      _        <- storage.copyFile(original.file, derivative)
      metadata <- otherFilesService.extractMetadata(original, derivative)
    } yield Asset.makeOther(assetDir.assetRef, original, derivative, metadata)

  private def handleMovingImageFile(original: Original, assetDir: AssetFolder): Task[MovingImageAsset] =
    ZIO.logInfo(s"Creating derivative for moving image $original, ${assetDir.assetRef}") *> {
      for {
        derivative <- movingImageService.createDerivative(original, assetDir.assetRef)
        _          <- movingImageService.extractKeyFrames(derivative, assetDir.assetRef)
        meta       <- movingImageService.extractMetadata(original, derivative)
      } yield Asset.makeMovingImageAsset(assetDir.assetRef, original, derivative, meta)
    }

  private def tryCleanup(assetDir: AssetFolder) =
    // remove all files and folders which start with the asset id and remove empty assetDir
    ZIO.logInfo(s"Failed ingest for ${assetDir.assetRef} cleaning up in directory ${assetDir.path}") *>
      StorageService
        .findInPath(assetDir, p => ZIO.succeed(p.filename.toString.startsWith(assetDir.assetId.value)), maxDepth = 1)
        .mapZIO(p => ZIO.ifZIO(Files.isDirectory(p))(storage.deleteRecursive(p).unit, storage.delete(p)))
        .runDrain *> storage.deleteDirectoryIfEmpty(assetDir) *> storage.deleteDirectoryIfEmpty(assetDir.parent.head)
}

object IngestService {
  def ingestFile(fileToIngest: Path, project: ProjectShortcode): ZIO[IngestService, Throwable, Asset] =
    ZIO.serviceWithZIO[IngestService](_.ingestFile(fileToIngest, project))

  def layer = ZLayer.derive[IngestServiceLive]
}
