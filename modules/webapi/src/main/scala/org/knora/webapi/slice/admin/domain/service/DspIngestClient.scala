/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import swiss.dasch.api.ProjectsEndpointsResponses.AssetInfoResponse as IngestAssetInfoResponse
import swiss.dasch.config.Configuration.Features
import swiss.dasch.domain.AssetInfoService
import swiss.dasch.domain.AssetRef
import swiss.dasch.domain.ImportService
import swiss.dasch.domain.ProjectService as IngestProjectService
import swiss.dasch.domain.ProjectShortcode
import swiss.dasch.infrastructure.IngestExecutor
import zio.Task
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Files
import zio.nio.file.Path
import zio.stream.ZStream

import java.nio.file.StandardCopyOption

import dsp.errors.NotFoundException
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.api.admin.model.MaintenanceRequests.AssetId

final case class AssetInfoResponse(
  internalFilename: String,
  originalInternalFilename: String,
  originalFilename: String,
  checksumOriginal: String,
  checksumDerivative: String,
  sizeOriginal: Option[Long] = None,
  sizeDerivative: Option[Long] = None,
  width: Option[Int] = None,
  height: Option[Int] = None,
  duration: Option[Double] = None,
  fps: Option[Double] = None,
  internalMimeType: Option[String] = None,
  originalMimeType: Option[String] = None,
)

trait DspIngestClient {
  def getAssetInfo(shortcode: Shortcode, assetId: AssetId): Task[AssetInfoResponse]
  def exportProject(shortcode: Shortcode, outputFile: Path): Task[Option[Path]]
  def eraseProject(shortcode: Shortcode): Task[Unit]
  def importProject(shortcode: Shortcode, fileToImport: Path): Task[Path]
}

/**
 * Calls ingest in-process. A failed import is logged, not raised; erasing is gated by ingest's allow-erase-projects.
 * Export, import and erase run on the ingest executor.
 */
final class DspIngestClientLive(
  assetInfos: AssetInfoService,
  projects: IngestProjectService,
  imports: ImportService,
  features: Features,
  executor: IngestExecutor,
) extends DspIngestClient {

  private def ingestShortcode(shortcode: Shortcode): Task[ProjectShortcode] =
    ZIO.fromEither(ProjectShortcode.from(shortcode.value)).mapError(IllegalArgumentException(_))

  def getAssetInfo(shortcode: Shortcode, assetId: AssetId): Task[AssetInfoResponse] =
    for {
      code <- ingestShortcode(shortcode)
      info <- assetInfos
                .findByAssetRef(AssetRef(assetId, code))
                .someOrFail(NotFoundException(s"Asset $assetId not found in project $shortcode"))
      r = IngestAssetInfoResponse.from(info)
    } yield AssetInfoResponse(
      r.internalFilename,
      r.originalInternalFilename,
      r.originalFilename,
      r.checksumOriginal,
      r.checksumDerivative,
      r.sizeOriginal,
      r.sizeDerivative,
      r.width,
      r.height,
      r.duration,
      r.fps,
      r.internalMimeType,
      r.originalMimeType,
    )

  def exportProject(shortcode: Shortcode, outputFile: Path): Task[Option[Path]] = executor.run {
    for {
      code   <- ingestShortcode(shortcode)
      zipped <- projects.zipProject(code)
      result <- ZIO.foreach(zipped)(Files.move(_, outputFile, StandardCopyOption.REPLACE_EXISTING).as(outputFile))
    } yield result
  }

  def eraseProject(shortcode: Shortcode): Task[Unit] = executor.run {
    if (features.allowEraseProjects) ingestShortcode(shortcode).flatMap(projects.deleteProject)
    else ZIO.logWarning(s"Not erasing the assets of project $shortcode: allow-erase-projects is off in ingest")
  }

  def importProject(shortcode: Shortcode, fileToImport: Path): Task[Path] = executor.run {
    ingestShortcode(shortcode)
      .flatMap(imports.importZipStream(_, ZStream.fromFile(fileToImport.toFile).orDie))
      .foldZIO(
        e => ZIO.logError(s"Importing the assets of project $shortcode failed: $e"),
        _ => ZIO.logInfo(s"Imported the assets of project $shortcode"),
      )
      .as(fileToImport)
  }
}

object DspIngestClientLive {
  val layer = ZLayer.derive[DspIngestClientLive]
}
