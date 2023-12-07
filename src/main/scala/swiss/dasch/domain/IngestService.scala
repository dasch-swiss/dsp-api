/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import org.apache.commons.io.FilenameUtils
import swiss.dasch.domain.Asset.{OtherAsset, StillImageAsset}
import swiss.dasch.domain.DerivativeFile.OtherDerivativeFile
import swiss.dasch.domain.PathOps.fileExtension
import zio.nio.file.Path
import zio.{IO, Task, ZIO, ZLayer}

import java.io.IOException

final case class IngestService(
  storage: StorageService,
  imageService: ImageService,
  sipiClient: SipiClient,
  assetInfo: AssetInfoService
) {

  def ingestFile(fileToIngest: Path, project: ProjectShortcode): Task[Asset] =
    for {
      _ <- ZIO.logInfo(s"Ingesting file $fileToIngest")
      _ <- ZIO.unlessZIO(FileFilters.isSupported(fileToIngest))(
             ZIO.fail(new IllegalArgumentException(s"File $fileToIngest is not a supported file."))
           )
      assetRef <- AssetRef.makeNew(project)
      assetDir <- ensureAssetDirectoryExists(assetRef)
      original <- createOriginalFileInAssetDir(fileToIngest, assetRef, assetDir)
      asset <- ZIO
                 .fromOption(SupportedFileType.fromPath(fileToIngest))
                 .orElseFail(new IllegalArgumentException("Unsupported file type."))
                 .flatMap {
                   case SupportedFileType.StillImage => handleImageFile(original, assetRef)
                   case SupportedFileType.Other      => handleOtherFile(original, assetRef, assetDir)
                   case SupportedFileType.MovingImage =>
                     ZIO.fail(new NotImplementedError("Video files are not supported yet."))
                 }
      _ <- assetInfo.createAssetInfo(asset)
      _ <- storage.delete(fileToIngest)
      _ <- ZIO.logInfo(s"Finished ingesting file $fileToIngest")
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
    ZIO.logInfo(s"Creating derivative for image $original, $assetRef") *>
      imageService
        .createDerivative(original.file)
        .map(derivative => Asset.makeStillImage(assetRef, original, derivative))

  private def handleOtherFile(original: Original, assetRef: AssetRef, assetDir: Path): Task[OtherAsset] =
    ZIO.logInfo(s"Creating derivative for other $original, $assetRef") *> {
      val fileExtension  = FilenameUtils.getExtension(original.originalFilename.toString)
      val derivativePath = assetDir / s"${assetRef.id}.$fileExtension"
      val derivative     = OtherDerivativeFile.unsafeFrom(derivativePath)
      storage.copyFile(original.file.toPath, derivativePath).as(Asset.makeOther(assetRef, original, derivative))
    }
}

object IngestService {

  def ingestFile(fileToIngest: Path, project: ProjectShortcode): ZIO[IngestService, Throwable, Asset] =
    ZIO.serviceWithZIO[IngestService](_.ingestFile(fileToIngest, project))

  def layer = ZLayer.derive[IngestService]
}
