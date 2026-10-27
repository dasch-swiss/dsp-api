/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import sttp.model.headers.ContentRange
import zio.*
import zio.nio.file.Files
import zio.stream.ZSink
import zio.stream.ZStream

import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

import org.knora.webapi.slice.ingest.api.ApiProblem.*
import org.knora.webapi.slice.ingest.api.IngestAuthorization.*
import org.knora.webapi.slice.ingest.api.IngestProjectsEndpointsResponses.*
import org.knora.webapi.slice.ingest.config.IngestFeatures
import org.knora.webapi.slice.ingest.domain.*
import org.knora.webapi.slice.ingest.domain.BulkIngestError.BulkIngestInProgress
import org.knora.webapi.slice.ingest.domain.BulkIngestError.ImportFolderDoesNotExist
import org.knora.webapi.slice.ingest.ports.OriginalAccess
import org.knora.webapi.slice.ingest.repo.*

final class IngestProjectsRestService(
  assetInfoService: AssetInfoService,
  bulkIngestService: BulkIngestService,
  features: IngestFeatures,
  importService: ImportService,
  ingestService: IngestService,
  originalAccess: OriginalAccess,
  projectService: ProjectService,
  reportService: ReportService,
  storageService: StorageService,
) {

  private type FileDownload = (String, String, ZStream[Any, Throwable, Byte])

  def listProjects(principal: IngestPrincipal): IO[ApiProblem, (Chunk[ProjectResponse], String)] =
    ensureAdminScope(principal) *>
      projectService
        .listAllProjects()
        .mapBoth(
          InternalServerError(_),
          list => (list.map(ProjectResponse.from), ContentRange("items", Some(0, list.size), Some(list.size)).toString),
        )

  def getProject(principal: IngestPrincipal)(shortcode: ProjectShortcode): IO[ApiProblem, ProjectResponse] =
    ensureProjectReadable(principal, shortcode) *>
      projectService
        .findProject(shortcode)
        .some
        .mapBoth(projectNotFoundOrServerError(_, shortcode), _ => ProjectResponse.from(shortcode))

  def getChecksumReport(principal: IngestPrincipal)(
    shortcode: ProjectShortcode,
  ): IO[ApiProblem, AssetCheckResultResponse] =
    ensureProjectReadable(principal, shortcode) *>
      reportService
        .checksumReport(shortcode)
        .some
        .mapBoth(projectNotFoundOrServerError(_, shortcode), AssetCheckResultResponse.from)

  def eraseProject(principal: IngestPrincipal)(shortcode: ProjectShortcode): IO[ApiProblem, ProjectResponse] =
    ensureAdminScope(principal) *>
      projectService.findProject(shortcode).some.mapError(projectNotFoundOrServerError(_, shortcode)) *> {
        if (features.allowEraseProjects)
          projectService.deleteProject(shortcode).mapBoth(InternalServerError(_), _ => ProjectResponse.from(shortcode))
        else ZIO.fail(Forbidden("The feature to erase projects is not enabled."))
      }

  def getAssetInfo(principal: IngestPrincipal)(
    shortcode: ProjectShortcode,
    assetId: AssetId,
  ): IO[ApiProblem, AssetInfoResponse] = {
    val ref = AssetRef(assetId, shortcode)
    ensureProjectReadable(principal, shortcode) *>
      assetInfoService.findByAssetRef(ref).some.mapBoth(assetRefNotFoundOrServerError(_, ref), AssetInfoResponse.from)
  }

  def getAssetOriginal(principal: Option[IngestPrincipal])(
    shortcode: ProjectShortcode,
    assetId: AssetId,
  ): IO[ApiProblem, FileDownload] = {
    val ref = AssetRef(assetId, shortcode)
    for {
      assetInfo <- assetInfoService.findByAssetRef(ref).some.mapError(assetRefNotFoundOrServerError(_, ref))
      granted   <- originalAccess
                   .isOriginalGranted(principal.map(_.jwtRaw), shortcode, assetInfo.derivative.filename.value)
                   .tapError(e => ZIO.logError(s"Original access check failed: $e"))
                   .orElseFail(InternalServerError("error fetching permissions"))
      _              <- ZIO.fail(Forbidden("permission denied")).unless(granted)
      filenameEncoded = URLEncoder.encode(assetInfo.originalFilename.value, StandardCharsets.UTF_8.toString)
    } yield (
      s"attachment; filename*=UTF-8''$filenameEncoded",
      assetInfo.metadata.originalMimeType.fold("application/octet-stream")(_.stringValue),
      ZStream.fromFile(assetInfo.original.file.toFile),
    )
  }

  def ingestAsset(principal: IngestPrincipal)(
    shortcode: ProjectShortcode,
    filename: AssetFilename,
    stream: ZStream[Any, Throwable, Byte],
  ): IO[ApiProblem, AssetInfoResponse] =
    ensureProjectWritable(principal, shortcode) *>
      ZIO.scoped {
        for {
          prj    <- projectService.findOrCreateProject(shortcode).mapError(InternalServerError(_))
          tmpDir <-
            storageService.createTempDirectoryScoped(s"${prj.shortcode}-ingest").mapError(InternalServerError(_))
          tmpFile = tmpDir / filename.value
          _      <- stream.rechunk(ChunkSize).run(ZSink.fromFile(tmpFile.toFile)).mapError(InternalServerError(_))
          _      <- Files.size(tmpFile).orDie.filterOrFail(_ > 0)(BadRequest.invalidBody("The uploaded file is empty."))
          asset  <- ingestService.ingestFile(tmpFile, shortcode).mapError(InternalServerError(_))
          info   <- assetInfoService.findByAssetRef(asset.ref).someOrFailException.orDie
        } yield AssetInfoResponse.from(info)
      }

  def startBulkIngest(principal: IngestPrincipal)(shortcode: ProjectShortcode): IO[ApiProblem, ProjectResponse] =
    ensureProjectWritable(principal, shortcode) *>
      bulkIngestService
        .startBulkIngest(shortcode)
        .mapBoth(bulkIngestFailure(shortcode), _ => ProjectResponse.from(shortcode))

  def finalizeBulkIngest(principal: IngestPrincipal)(shortcode: ProjectShortcode): IO[ApiProblem, ProjectResponse] =
    ensureProjectWritable(principal, shortcode) *>
      bulkIngestService
        .finalizeBulkIngest(shortcode)
        .mapBoth(bulkIngestFailure(shortcode), _ => ProjectResponse.from(shortcode))

  def getBulkIngestMappingCsv(principal: IngestPrincipal)(shortcode: ProjectShortcode): IO[ApiProblem, String] =
    ensureProjectWritable(principal, shortcode) *>
      bulkIngestService
        .getBulkIngestMappingCsv(shortcode)
        .mapError {
          case None                           => bulkIngestInProgress(shortcode)
          case Some(ioException: IOException) => InternalServerError(ioException)
        }
        .someOrFail(NotFound(shortcode))

  def uploadBulkIngestFile(principal: IngestPrincipal)(
    shortcode: ProjectShortcode,
    filename: String,
    stream: ZStream[Any, Throwable, Byte],
  ): IO[ApiProblem, UploadResponse] =
    ensureProjectWritable(principal, shortcode) *>
      bulkIngestService
        .uploadSingleFile(shortcode, filename, stream)
        .mapError(_.getOrElse(bulkIngestInProgress(shortcode)))

  def exportProject(principal: IngestPrincipal)(shortcode: ProjectShortcode): IO[ApiProblem, FileDownload] =
    ensureAdminScope(principal) *>
      projectService
        .zipProject(shortcode)
        .some
        .mapBoth(
          projectNotFoundOrServerError(_, shortcode),
          path =>
            (
              s"attachment; filename=export-$shortcode.bagit.zip",
              "application/zip",
              ZStream.fromFile(path.toFile).orDie,
            ),
        )

  def importProject(principal: IngestPrincipal)(
    shortcode: ProjectShortcode,
    stream: ZStream[Any, Throwable, Byte],
  ): IO[ApiProblem, UploadResponse] =
    ensureAdminScope(principal) *>
      importService
        .importZipStream(shortcode, stream.orDie)
        .mapBoth(
          {
            case IoError(e)                 => InternalServerError(s"Import of project ${shortcode.value} failed.", e)
            case BagItValidationFailed(msg) => BadRequest.invalidBody(s"BagIt validation failed: $msg")
            case ProjectAlreadyExists(sc)   =>
              Conflict(s"Project ${sc.value} already exists. Delete the project before importing.")
          },
          _ => UploadResponse(),
        )

  private val ChunkSize = 64 * 1024

  private def bulkIngestFailure(shortcode: ProjectShortcode)(
    error: ImportFolderDoesNotExist.type | BulkIngestInProgress.type,
  ): ApiProblem = error match {
    case BulkIngestInProgress     => bulkIngestInProgress(shortcode)
    case ImportFolderDoesNotExist => NotFound(shortcode.value, "Import folder not found.")
  }

  private def bulkIngestInProgress(shortcode: ProjectShortcode): ApiProblem =
    Conflict(s"A bulk ingest is currently in progress for project ${shortcode.value}.")

  private def projectNotFoundOrServerError(error: Option[Throwable], shortcode: ProjectShortcode): ApiProblem =
    error.fold[ApiProblem](NotFound(shortcode))(InternalServerError(_))

  private def assetRefNotFoundOrServerError(error: Option[Throwable], ref: AssetRef): ApiProblem =
    error.fold[ApiProblem](NotFound(s"${ref.belongsToProject}/${ref.id}", "AssetRef"))(InternalServerError(_))
}

object IngestProjectsRestService {
  val layer = ZLayer.derive[IngestProjectsRestService]
}
