/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.capabilities.zio.ZioStreams
import sttp.model.headers.ContentRange
import sttp.tapir.ztapir.ZServerEndpoint
import swiss.dasch.FetchAssetPermissions
import swiss.dasch.api.ApiProblem.*
import swiss.dasch.api.ProjectsEndpointsResponses.{
  AssetCheckResultResponse,
  AssetInfoResponse,
  ProjectResponse,
  UploadResponse,
}
import swiss.dasch.config.Configuration.Features
import swiss.dasch.domain.*
import swiss.dasch.domain.BulkIngestError.{BulkIngestInProgress, ImportFolderDoesNotExist}
import zio.*
import zio.nio.file.Files
import zio.stream.{ZSink, ZStream}

import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

final case class ProjectsEndpointsHandler(
  bulkIngestService: BulkIngestService,
  ingestService: IngestService,
  importService: ImportService,
  projectEndpoints: ProjectsEndpoints,
  projectService: ProjectService,
  reportService: ReportService,
  storageService: StorageService,
  assetInfoService: AssetInfoService,
  authorizationHandler: AuthorizationHandler,
  features: Features,
  fetchAssetPermissions: FetchAssetPermissions,
) extends HandlerFunctions {

  val getProjectsEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.getProjectsEndpoint
    .serverLogic(userSession =>
      _ =>
        authorizationHandler.ensureAdminScope(userSession) *>
          projectService
            .listAllProjects()
            .mapBoth(
              InternalServerError(_),
              list =>
                (list.map(ProjectResponse.from), ContentRange("items", Some(0, list.size), Some(list.size)).toString),
            ),
    )

  val getProjectByShortcodeEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.getProjectByShortcodeEndpoint
    .serverLogic(userSession =>
      shortcode =>
        authorizationHandler.ensureProjectReadable(userSession, shortcode) *>
          projectService
            .findProject(shortcode)
            .some
            .mapBoth(
              projectNotFoundOrServerError(_, shortcode),
              _ => ProjectResponse.from(shortcode),
            ),
    )

  private val getProjectChecksumReportEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.getProjectsChecksumReport
    .serverLogic(userSession =>
      shortcode =>
        authorizationHandler.ensureProjectReadable(userSession, shortcode) *>
          reportService
            .checksumReport(shortcode)
            .some
            .mapBoth(
              projectNotFoundOrServerError(_, shortcode),
              AssetCheckResultResponse.from,
            ),
    )

  private val deleteProjectsEraseEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.deleteProjectsErase
    .serverLogic(userSession =>
      shortcode =>
        authorizationHandler.ensureAdminScope(userSession) *>
          projectService.findProject(shortcode).some.mapError(projectNotFoundOrServerError(_, shortcode)) *> {
            if (features.allowEraseProjects) {
              projectService
                .deleteProject(shortcode)
                .mapBoth(
                  InternalServerError(_),
                  _ => ProjectResponse.from(shortcode),
                )
            } else {
              ZIO.fail(Forbidden("The feature to erase projects is not enabled."))
            }
          },
    )

  private val getProjectsAssetsInfoEndpoint: ZServerEndpoint[Any, Any] =
    projectEndpoints.getProjectsAssetsInfo.serverLogic { userSession => (shortcode, assetId) =>
      val ref = AssetRef(assetId, shortcode)
      authorizationHandler.ensureProjectReadable(userSession, shortcode) *>
        assetInfoService
          .findByAssetRef(ref)
          .some
          .mapBoth(
            assetRefNotFoundOrServerError(_, ref),
            AssetInfoResponse.from,
          )
    }

  private val getProjectsAssetsOriginalEndpoint: ZServerEndpoint[Any, ZioStreams] =
    projectEndpoints.getProjectsAssetsOriginal
      .serverLogic(userSession =>
        (shortcode, assetId) =>
          for {
            ref            <- ZIO.succeed(AssetRef(assetId, shortcode))
            assetInfo      <- assetInfoService.findByAssetRef(ref).some.mapError(assetRefNotFoundOrServerError(_, ref))
            filenameEncoded = URLEncoder.encode(assetInfo.originalFilename.value, StandardCharsets.UTF_8.toString)
            permissionCode <- fetchAssetPermissions
                                .getPermissionCode(userSession.map(_.jwtRaw), assetInfo)
                                .orElseFail(InternalServerError("error fetching permissions"))
            _ <- ZIO.fail(Forbidden("permission denied")).unless(permissionCode >= 2)
          } yield (
            s"attachment; filename*=UTF-8''${filenameEncoded}", // Content-Disposition
            assetInfo.metadata.originalMimeType.map(m => m.stringValue).getOrElse("application/octet-stream"),
            ZStream.fromFile(assetInfo.original.file.toFile),
          ),
      )

  private val ChunkSize = 64 * 1024 // larger chunk size; better for larger files
  private val postProjectAssetEndpoint: ZServerEndpoint[Any, ZioStreams] = projectEndpoints.postProjectAsset
    .serverLogic(principal => { case (shortcode, filename, stream) =>
      authorizationHandler.ensureProjectWritable(principal, shortcode) *>
        ZIO.scoped {
          for {
            prj <- projectService.findOrCreateProject(shortcode).mapError(InternalServerError(_))
            tmpDir <-
              storageService.createTempDirectoryScoped(s"${prj.shortcode}-ingest").mapError(InternalServerError(_))
            tmpFile = tmpDir / filename.value
            _      <- stream.rechunk(ChunkSize).run(ZSink.fromFile(tmpFile.toFile)).mapError(InternalServerError(_))
            _      <- Files.size(tmpFile).orDie.filterOrFail(_ > 0)(BadRequest.invalidBody("The uploaded file is empty."))
            asset  <- ingestService.ingestFile(tmpFile, shortcode).mapError(InternalServerError(_))
            info   <- assetInfoService.findByAssetRef(asset.ref).someOrFailException.orDie
          } yield AssetInfoResponse.from(info)
        }
    })

  private val postBulkIngestEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.postBulkIngest
    .serverLogic(userSession =>
      code =>
        authorizationHandler.ensureProjectWritable(userSession, code) *>
          bulkIngestService
            .startBulkIngest(code)
            .mapBoth(
              {
                case BulkIngestInProgress     => failBulkIngestInProgress(code)
                case ImportFolderDoesNotExist => NotFound(code.value, "Import folder not found.")
              },
              _ => ProjectResponse.from(code),
            ),
    )

  private val postBulkIngestEndpointFinalize: ZServerEndpoint[Any, Any] = projectEndpoints.postBulkIngestFinalize
    .serverLogic(userSession =>
      code =>
        authorizationHandler.ensureProjectWritable(userSession, code) *>
          bulkIngestService
            .finalizeBulkIngest(code)
            .mapBoth(
              {
                case BulkIngestInProgress     => failBulkIngestInProgress(code)
                case ImportFolderDoesNotExist => NotFound(code.value, "Import folder not found.")
              },
              _ => ProjectResponse.from(code),
            ),
    )

  private val getBulkIngestMappingCsvEndpoint: ZServerEndpoint[Any, Any] =
    projectEndpoints.getBulkIngestMappingCsv
      .serverLogic(userSession =>
        code =>
          authorizationHandler.ensureProjectWritable(userSession, code) *>
            bulkIngestService
              .getBulkIngestMappingCsv(code)
              .mapError {
                case None                           => failBulkIngestInProgress(code)
                case Some(ioException: IOException) => InternalServerError(ioException)
              }
              .someOrFail(NotFound(code)),
      )

  private val postBulkIngestUploadEndpoint: ZServerEndpoint[Any, ZioStreams] = projectEndpoints.postBulkIngestUpload
    .serverLogic(principal => { case (shortcode, filename, stream) =>
      for {
        _ <- authorizationHandler.ensureProjectWritable(principal, shortcode)
        s <- bulkIngestService.uploadSingleFile(shortcode, filename, stream).mapError {
               _.getOrElse(failBulkIngestInProgress(shortcode))
             }
      } yield s
    })

  private def failBulkIngestInProgress(code: ProjectShortcode) =
    Conflict(s"A bulk ingest is currently in progress for project ${code.value}.")

  private val postExportEndpoint: ZServerEndpoint[Any, ZioStreams] = projectEndpoints.postExport
    .serverLogic(userSession =>
      shortcode =>
        authorizationHandler.ensureAdminScope(userSession) *>
          projectService
            .zipProject(shortcode)
            .some
            .mapBoth(
              projectNotFoundOrServerError(_, shortcode),
              path =>
                (
                  s"attachment; filename=export-$shortcode.zip",
                  "application/zip",
                  ZStream.fromFile(path.toFile).orDie,
                ),
            ),
    )

  private val getImportEndpoint: ZServerEndpoint[Any, ZioStreams] = projectEndpoints.getImport
    .serverLogic(userSession =>
      (shortcode, stream) =>
        authorizationHandler.ensureAdminScope(userSession) *>
          importService
            .importZipStream(shortcode, stream.orDie)
            .mapBoth(
              {
                case IoError(e)       => InternalServerError(s"Import of project ${shortcode.value} failed.", e)
                case EmptyFile        => BadRequest.invalidBody("The uploaded file is empty.")
                case NoZipFile        => BadRequest.invalidBody("The uploaded file is not a zip file.")
                case InvalidChecksums => BadRequest.invalidBody("The uploaded file contains invalid checksums.")
              },
              _ => UploadResponse(),
            ),
    )

  val endpoints: List[ZServerEndpoint[Any, ZioStreams]] =
    List(
      getProjectsEndpoint,
      getProjectByShortcodeEndpoint,
      getProjectChecksumReportEndpoint,
      deleteProjectsEraseEndpoint,
      getProjectsAssetsInfoEndpoint,
      getProjectsAssetsOriginalEndpoint,
      postProjectAssetEndpoint,
      postBulkIngestEndpoint,
      postBulkIngestEndpointFinalize,
      getBulkIngestMappingCsvEndpoint,
      postExportEndpoint,
      getImportEndpoint,
      postBulkIngestUploadEndpoint,
    )
}

object ProjectsEndpointsHandler {
  val layer = ZLayer.derive[ProjectsEndpointsHandler]
}
