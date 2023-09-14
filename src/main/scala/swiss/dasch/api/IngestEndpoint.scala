/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import org.apache.commons.io.FilenameUtils
import swiss.dasch.api.ApiPathCodecSegments.{ projects, shortcodePathVar }
import swiss.dasch.api.ListProjectsEndpoint.ProjectResponse
import swiss.dasch.config.Configuration.IngestConfig
import swiss.dasch.domain.*
import swiss.dasch.domain.SipiImageFormat.Jpx
import zio.*
import zio.http.Status
import zio.http.codec.HttpCodec
import zio.http.endpoint.Endpoint
import zio.nio.file.{ Files, Path }

import java.nio.file.StandardOpenOption

object IngestEndpoint {

  private val endpoint = Endpoint
    .post(projects / shortcodePathVar / "bulk-ingest")
    .out[ProjectResponse]
    .outErrors(
      HttpCodec.error[ProjectNotFound](Status.NotFound),
      HttpCodec.error[IllegalArguments](Status.BadRequest),
      HttpCodec.error[InternalProblem](Status.InternalServerError),
    )

  private val route = endpoint.implement(shortcode =>
    ApiStringConverters.fromPathVarToProjectShortcode(shortcode).flatMap { code =>
      BulkIngestService.startBulkIngest(code).logError.forkDaemon *>
        ZIO.succeed(ProjectResponse(code))
    }
  )

  val app = route.toApp
}

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
    config: IngestConfig,
  ) extends BulkIngestService {

  override def startBulkIngest(project: ProjectShortcode): Task[IngestResult] =
    for {
      _          <- ZIO.logInfo(s"Starting bulk ingest for project $project")
      importDir  <- storage.getTempDirectory().map(_ / "import" / project.value)
      mappingFile = importDir.parent.head / s"mapping-$project.csv"
      _          <- (Files.createFile(mappingFile) *> Files.writeLines(mappingFile, List("original,derivative")))
                      .whenZIO(Files.exists(mappingFile).negate)
      total      <- StorageService.findInPath(importDir, FileFilters.isNonHiddenRegularFile).runCount
      sum        <- StorageService
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
      _          <- {
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
      file: Path,
      project: ProjectShortcode,
      csv: Path,
    ): Task[IngestResult] =
    for {
      _               <- ZIO.logInfo(s"Ingesting image $file")
      asset           <- Asset.makeNew(project)
      assetDir        <- ensureAssetDirExists(asset)
      originalFile    <- copyFileToAssetDir(file, assetDir, asset)
      derivativeFile  <- transcode(assetDir, originalFile, asset)
      originalFilename = file.filename.toString
      _               <- assetInfo.createAssetInfo(asset, originalFilename)
      _               <- updateMappingCsv(csv, derivativeFile, originalFilename, asset)
      _               <- Files.delete(file)
      _               <- ZIO.logInfo(s"Finished ingesting image $file")
    } yield IngestResult.success

  private def updateMappingCsv(
      mappingFile: Path,
      derivativeFile: Path,
      originalFilename: String,
      asset: Asset,
    ) =
    ZIO.logInfo(s"Updating mapping file $mappingFile, $asset") *>
      Files.writeLines(
        mappingFile,
        List(s"$originalFilename,${derivativeFile.filename}"),
        openOptions = Set(StandardOpenOption.APPEND),
      )

  private def ensureAssetDirExists(asset: Asset) =
    for {
      _        <- ZIO.logInfo(s"Ensuring asset dir exists, $asset")
      assetDir <- storage.getAssetDirectory(asset)
      _        <- Files.createDirectories(assetDir)
    } yield assetDir

  private def copyFileToAssetDir(
      file: Path,
      assetDir: Path,
      asset: Asset,
    ) = {
    val originalFile = assetDir / s"${asset.id}.${FilenameUtils.getExtension(file.filename.toString)}.orig"
    ZIO.logInfo(s"Copying file $file to $assetDir, $asset") *>
      Files.copy(file, originalFile).as(originalFile)
  }

  private def transcode(
      assetDir: Path,
      originalFile: Path,
      asset: Asset,
    ) = {
    val derivativeFile = assetDir / s"${asset.id}.${Jpx.extension}"
    ZIO.logInfo(s"Transcoding $originalFile to $derivativeFile, $asset") *>
      sipiClient.transcodeImageFile(originalFile, derivativeFile, Jpx) *>
      ZIO
        .whenZIO(Files.exists(derivativeFile).negate)(
          Files.delete(originalFile) *>
            ZIO.fail(IllegalStateException(s"Sipi failed transcoding $originalFile to $derivativeFile"))
        )
        .as(derivativeFile)
  }
}

object BulkIngestServiceLive {
  val layer = ZLayer.fromFunction(BulkIngestServiceLive.apply _)
}
