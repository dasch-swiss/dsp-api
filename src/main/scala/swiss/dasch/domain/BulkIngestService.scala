/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.config.Configuration.IngestConfig
import zio.{Task, ZIO, ZLayer}
import zio.nio.file.{Files, Path}

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
      _          <- ZIO.logInfo(s"Starting bulk ingest for project $project")
      importDir  <- storage.getBulkIngestImportFolder(project)
      mappingFile = importDir.parent.head / s"mapping-$project.csv"
      _ <- (Files.createFile(mappingFile) *> Files.writeLines(mappingFile, List("original,derivative")))
             .whenZIO(Files.exists(mappingFile).negate)
      total <- StorageService.findInPath(importDir, FileFilters.isNonHiddenRegularFile).runCount
      sum <- StorageService
               .findInPath(importDir, FileFilters.isImage)
               .mapZIOPar(config.bulkMaxParallel)(image =>
                 ingestSingleImage(image, project, mappingFile)
                   .catchNonFatalOrDie(e =>
                     ZIO
                       .logError(s"Error ingesting image $image: ${e.getMessage}")
                       .as(IngestResult.failed)
                   )
               )
               .runFold(IngestResult())(_ + _)
      _ <- {
        val countImages  = sum.success + sum.failed
        val countSuccess = sum.success
        val countFailed  = sum.failed
        ZIO.logInfo(
          s"Finished bulk ingest for project $project. " +
            s"Found $countImages images from $total files. " +
            s"Ingested $countSuccess successfully and failed $countFailed images (See logs above for more details)."
        )
      }
    } yield sum

  private def ingestSingleImage(
    imageToIngest: Path,
    project: ProjectShortcode,
    csv: Path
  ): Task[IngestResult] =
    for {
      _          <- ZIO.logInfo(s"Ingesting image $imageToIngest")
      asset      <- Asset.makeNew(project)
      original   <- storage.createOriginalFileInAssetDir(imageToIngest, asset)
      derivative <- imageService.createDerivative(original).tapError(_ => Files.delete(original.toPath).ignore)
      imageToIngestFilename <- ZIO
                                 .fromEither(NonEmptyString.from(imageToIngest.filename.toString))
                                 .orElseFail(new IllegalArgumentException("Image filename must not be empty"))
      imageAsset = asset.makeImageAsset(imageToIngestFilename, original, derivative)
      _         <- assetInfo.createAssetInfo(imageAsset)
      _         <- updateMappingCsv(csv, imageToIngest, imageAsset)
      _         <- Files.delete(imageToIngest)
      _         <- ZIO.logInfo(s"Finished ingesting image $imageToIngest")
    } yield IngestResult.success

  private def updateMappingCsv(
    mappingFile: Path,
    imageToIngest: Path,
    asset: ImageAsset
  ) =
    ZIO.logInfo(s"Updating mapping file $mappingFile, $asset") *> {
      for {
        importDir                <- storage.getBulkIngestImportFolder(asset.belongsToProject)
        imageToIngestRelativePath = importDir.relativize(imageToIngest)
        _ <- Files.writeLines(
               mappingFile,
               List(s"$imageToIngestRelativePath,${asset.derivativeFilename}"),
               openOptions = Set(StandardOpenOption.APPEND)
             )
      } yield ()
    }
}

object BulkIngestServiceLive {
  val layer = ZLayer.derive[BulkIngestServiceLive]
}
