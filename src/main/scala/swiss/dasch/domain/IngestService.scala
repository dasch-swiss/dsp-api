/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.commons.io.FilenameUtils
import swiss.dasch.domain.Asset.{MovingImageAsset, OtherAsset, StillImageAsset}
import swiss.dasch.domain.DerivativeFile.OtherDerivativeFile
import swiss.dasch.domain.PathOps.fileExtension
import zio.nio.file.{Files, Path}
import zio.{IO, Task, ZIO, ZLayer}

import java.io.IOException

final case class IngestService(
  storage: StorageService,
  stillImageService: StillImageService,
  movingImageService: MovingImageService,
  assetInfo: AssetInfoService,
  mimeTypeGuesser: MimeTypeGuesser
) {

  def ingestFile(fileToIngest: Path, project: ProjectShortcode): Task[Asset] =
    for {
      _ <- ZIO.logInfo(s"Ingesting file $fileToIngest")
      _ <- ZIO.unlessZIO(FileFilters.isSupported(fileToIngest))(
             ZIO.fail(new IllegalArgumentException(s"File $fileToIngest is not a supported file."))
           )
      ref      <- AssetRef.makeNew(project)
      assetDir <- ensureAssetDirectoryExists(ref)
      asset    <- ingestAsset(fileToIngest, ref, assetDir).tapError(_ => tryCleanup(ref, assetDir).logError.ignore)
      _        <- ZIO.logInfo(s"Successfully ingesting file $fileToIngest as ${asset.ref}")
    } yield asset

  private def ingestAsset(fileToIngest: Path, assetRef: AssetRef, assetDir: Path) =
    for {
      original <- createOriginalFileInAssetDir(fileToIngest, assetRef, assetDir)
      asset <- ZIO
                 .fromOption(SupportedFileType.fromPath(fileToIngest))
                 .orElseFail(new IllegalArgumentException("Unsupported file type."))
                 .flatMap {
                   case SupportedFileType.StillImage  => handleImageFile(original, assetRef)
                   case SupportedFileType.Other       => handleOtherFile(original, assetRef, assetDir)
                   case SupportedFileType.MovingImage => handleMovingImageFile(original, assetRef)
                 }
      _ <- assetInfo.createAssetInfo(asset).tap(assetInfo.save).logError
      _ <- storage.delete(fileToIngest)
    } yield asset

  private def ensureAssetDirectoryExists(assetRef: AssetRef): IO[IOException, Path] =
    storage.getAssetDirectory(assetRef).tap(storage.createDirectories(_))

  private def createOriginalFileInAssetDir(file: Path, assetRef: AssetRef, assetDir: Path): IO[IOException, Original] =
    ZIO.logInfo(s"Creating original for $file, $assetRef") *> {
      val fileExtension    = s"${file.fileExtension}.orig"
      val originalPath     = assetDir / s"${assetRef.id}.$fileExtension"
      val originalFile     = OriginalFile.unsafeFrom(originalPath)
      val originalFileName = NonEmptyString.unsafeFrom(file.filename.toString)
      storage.copyFile(file, originalPath).as(Original(originalFile, originalFileName))
    }

  private def handleImageFile(original: Original, assetRef: AssetRef): Task[StillImageAsset] =
    ZIO.logInfo(s"Creating derivative for image $original, $assetRef") *> {
      for {
        derivative <- stillImageService.createDerivative(original.file)
        metadata   <- stillImageService.extractMetadata(original, derivative)
      } yield Asset.makeStillImage(assetRef, original, derivative, metadata)
    }

  private def handleOtherFile(original: Original, assetRef: AssetRef, assetDir: Path): Task[OtherAsset] =
    ZIO.logInfo(s"Creating derivative for other $original, $assetRef") *> {
      val fileExtension  = FilenameUtils.getExtension(original.originalFilename.toString)
      val derivativePath = assetDir / s"${assetRef.id}.$fileExtension"
      val derivative     = OtherDerivativeFile.unsafeFrom(derivativePath)
      for {
        _                 <- storage.copyFile(original.file.toPath, derivativePath)
        originalMimeType   = mimeTypeGuesser.guess(Path(original.originalFilename.value))
        derivativeMimeType = mimeTypeGuesser.guess(derivative.file)
      } yield Asset.makeOther(assetRef, original, derivative, originalMimeType, derivativeMimeType)
    }

  private def handleMovingImageFile(original: Original, assetRef: AssetRef): Task[MovingImageAsset] =
    ZIO.logInfo(s"Creating derivative for moving image $original, $assetRef") *> {
      for {
        derivative <- movingImageService.createDerivative(original, assetRef)
        _          <- movingImageService.extractKeyFrames(derivative, assetRef)
        meta       <- movingImageService.extractMetadata(original, derivative, assetRef)
      } yield Asset.makeMovingImageAsset(assetRef, original, derivative, meta)
    }

  private def tryCleanup(assetRef: AssetRef, assetDir: Path): IO[IOException, Unit] =
    // remove all files and folders which start with the asset id and remove empty assetDir
    ZIO.logInfo(s"Failed ingest for $assetRef cleaning up in directory $assetDir") *>
      StorageService
        .findInPath(assetDir, p => ZIO.succeed(p.filename.toString.startsWith(assetRef.id.value)), maxDepth = 1)
        .mapZIO(p => ZIO.ifZIO(Files.isDirectory(p))(storage.deleteRecursive(p).unit, storage.delete(p)))
        .runDrain *> storage.deleteDirectoryIfEmpty(assetDir) *> storage.deleteDirectoryIfEmpty(assetDir.parent.head)
}

object IngestService {

  def ingestFile(fileToIngest: Path, project: ProjectShortcode): ZIO[IngestService, Throwable, Asset] =
    ZIO.serviceWithZIO[IngestService](_.ingestFile(fileToIngest, project))

  def layer = ZLayer.derive[IngestService]
}
