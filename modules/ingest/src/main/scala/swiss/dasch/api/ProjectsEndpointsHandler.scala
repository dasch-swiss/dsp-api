/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.capabilities.zio.ZioStreams
import sttp.model.headers.ContentRange
import sttp.tapir.ztapir.ZServerEndpoint
import swiss.dasch.FetchAssetPermissions
import swiss.dasch.api.ApiProblem.*
import swiss.dasch.api.HandlerFunctions.guarded
import swiss.dasch.api.ProjectsEndpointsResponses.AssetCheckResultResponse
import swiss.dasch.api.ProjectsEndpointsResponses.AssetInfoResponse
import swiss.dasch.api.ProjectsEndpointsResponses.ProjectResponse
import swiss.dasch.api.ProjectsEndpointsResponses.UploadResponse
import swiss.dasch.config.Configuration.Features
import swiss.dasch.domain.*
import swiss.dasch.domain.BulkIngestError.BulkIngestInProgress
import swiss.dasch.domain.BulkIngestError.ImportFolderDoesNotExist
import zio.*
import zio.nio.file.Files
import zio.stream.ZSink
import zio.stream.ZStream

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
        guarded {
          authorizationHandler.ensureAdminScope(userSession) *>
            projectService
              .listAllProjects()
              .flatMapError(IngestFailure.toApiProblem)
              .map(list =>
                (list.map(ProjectResponse.from), ContentRange("items", Some(0, list.size), Some(list.size)).toString),
              )
        },
    )

  val getProjectByShortcodeEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.getProjectByShortcodeEndpoint
    .serverLogic(userSession =>
      shortcode =>
        guarded {
          authorizationHandler.ensureProjectReadable(userSession, shortcode) *>
            projectService
              .findProject(shortcode)
              .some
              .flatMapError(projectNotFoundOrServerError(_, shortcode))
              .as(ProjectResponse.from(shortcode))
        },
    )

  private val getProjectChecksumReportEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.getProjectsChecksumReport
    .serverLogic(userSession =>
      shortcode =>
        guarded {
          authorizationHandler.ensureProjectReadable(userSession, shortcode) *>
            reportService
              .checksumReport(shortcode)
              .some
              .flatMapError(projectNotFoundOrServerError(_, shortcode))
              .map(AssetCheckResultResponse.from)
        },
    )

  private val deleteProjectsEraseEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.deleteProjectsErase
    .serverLogic(userSession =>
      shortcode =>
        guarded {
          authorizationHandler.ensureAdminScope(userSession) *>
            projectService.findProject(shortcode).some.flatMapError(projectNotFoundOrServerError(_, shortcode)) *> {
              if (features.allowEraseProjects) {
                projectService
                  .deleteProject(shortcode)
                  .flatMapError(IngestFailure.toApiProblem)
                  .as(ProjectResponse.from(shortcode))
              } else {
                ZIO.fail(Forbidden("The feature to erase projects is not enabled."))
              }
            }
        },
    )

  private val getProjectsAssetsInfoEndpoint: ZServerEndpoint[Any, Any] =
    projectEndpoints.getProjectsAssetsInfo.serverLogic { userSession => (shortcode, assetId) =>
      guarded {
        val ref = AssetRef(assetId, shortcode)
        authorizationHandler.ensureProjectReadable(userSession, shortcode) *>
          assetInfoService
            .findByAssetRef(ref)
            .some
            .flatMapError(assetRefNotFoundOrServerError(_, ref))
            .map(AssetInfoResponse.from)
      }
    }

  private val getProjectsAssetsOriginalEndpoint: ZServerEndpoint[Any, ZioStreams] =
    projectEndpoints.getProjectsAssetsOriginal
      .serverLogic(userSession =>
        (shortcode, assetId) =>
          guarded {
            for {
              ref            <- ZIO.succeed(AssetRef(assetId, shortcode))
              assetInfo      <- assetInfoService.findByAssetRef(ref).some.flatMapError(assetRefNotFoundOrServerError(_, ref))
              filenameEncoded = URLEncoder.encode(assetInfo.originalFilename.value, StandardCharsets.UTF_8.toString)
              granted        <- fetchAssetPermissions
                           .isOriginalGranted(userSession.map(_.jwtRaw), assetInfo)
                           .flatMapError(IngestFailure.toApiProblem)
              _ <- ZIO.fail(Forbidden("permission denied")).unless(granted)
            } yield (
              s"attachment; filename*=UTF-8''${filenameEncoded}", // Content-Disposition
              assetInfo.metadata.originalMimeType.map(m => m.stringValue).getOrElse("application/octet-stream"),
              ZStream.fromFile(assetInfo.original.file.toFile),
            )
          },
      )

  private val ChunkSize                                                  = 64 * 1024 // larger chunk size; better for larger files
  private val postProjectAssetEndpoint: ZServerEndpoint[Any, ZioStreams] = projectEndpoints.postProjectAsset
    .serverLogic(principal => { case (shortcode, filename, stream) =>
      guarded {
        authorizationHandler.ensureProjectWritable(principal, shortcode) *>
          ZIO.scoped {
            for {
              assetName <- ZIO.fromEither(AssetFilename.from(filename)).mapError(BadRequest("filename", _))
              prj       <- projectService.findOrCreateProject(shortcode).flatMapError(IngestFailure.toApiProblem)
              tmpDir    <- storageService
                          .createTempDirectoryScoped(s"${prj.shortcode}-ingest")
                          .flatMapError(IngestFailure.toApiProblem)
              tmpFile = tmpDir / assetName.value
              _      <- stream
                     .rechunk(ChunkSize)
                     .run(ZSink.fromFile(tmpFile.toFile))
                     .flatMapError(IngestFailure.toApiProblem)
              size  <- Files.size(tmpFile).flatMapError(IngestFailure.toApiProblem)
              _     <- ZIO.fail(BadRequest.invalidBody("The uploaded file is empty.")).when(size <= 0)
              asset <- ingestService.ingestFile(tmpFile, shortcode).flatMapError(IngestFailure.toApiProblem)
              info  <- assetInfoService
                        .findByAssetRef(asset.ref)
                        .someOrFailException
                        .flatMapError(IngestFailure.toApiProblem)
            } yield AssetInfoResponse.from(info)
          }
      }
    })

  private val postBulkIngestEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.postBulkIngest
    .serverLogic(userSession =>
      code =>
        guarded {
          authorizationHandler.ensureProjectWritable(userSession, code) *>
            bulkIngestService
              .startBulkIngest(code)
              .mapBoth(
                {
                  case BulkIngestInProgress     => failBulkIngestInProgress(code)
                  case ImportFolderDoesNotExist => NotFound(code.value, "Import folder not found.")
                },
                _ => ProjectResponse.from(code),
              )
        },
    )

  private val postBulkIngestEndpointFinalize: ZServerEndpoint[Any, Any] = projectEndpoints.postBulkIngestFinalize
    .serverLogic(userSession =>
      code =>
        guarded {
          authorizationHandler.ensureProjectWritable(userSession, code) *>
            bulkIngestService
              .finalizeBulkIngest(code)
              .mapBoth(
                {
                  case BulkIngestInProgress     => failBulkIngestInProgress(code)
                  case ImportFolderDoesNotExist => NotFound(code.value, "Import folder not found.")
                },
                _ => ProjectResponse.from(code),
              )
        },
    )

  private val getBulkIngestMappingCsvEndpoint: ZServerEndpoint[Any, Any] =
    projectEndpoints.getBulkIngestMappingCsv
      .serverLogic(userSession =>
        code =>
          guarded {
            authorizationHandler.ensureProjectWritable(userSession, code) *>
              bulkIngestService
                .getBulkIngestMappingCsv(code)
                .flatMapError {
                  case None              => ZIO.succeed(failBulkIngestInProgress(code))
                  case Some(ioException) => IngestFailure.toApiProblem(ioException)
                }
                .someOrFail(NotFound(code))
          },
      )

  private val postBulkIngestUploadEndpoint: ZServerEndpoint[Any, ZioStreams] = projectEndpoints.postBulkIngestUpload
    .serverLogic(principal => { case (shortcode, filename, stream) =>
      guarded {
        for {
          _ <- authorizationHandler.ensureProjectWritable(principal, shortcode)
          s <- bulkIngestService.uploadSingleFile(shortcode, filename, stream).mapError {
                 _.getOrElse(failBulkIngestInProgress(shortcode))
               }
        } yield s
      }
    })

  private def failBulkIngestInProgress(code: ProjectShortcode) =
    Conflict(s"A bulk ingest is currently in progress for project ${code.value}.")

  private val postExportEndpoint: ZServerEndpoint[Any, ZioStreams] = projectEndpoints.postExport
    .serverLogic(userSession =>
      shortcode =>
        guarded {
          authorizationHandler.ensureAdminScope(userSession) *>
            projectService
              .zipProject(shortcode)
              .some
              .flatMapError(projectNotFoundOrServerError(_, shortcode))
              .map(path =>
                (
                  s"attachment; filename=export-$shortcode.bagit.zip",
                  "application/zip",
                  ZStream.fromFile(path.toFile).orDie,
                ),
              )
        },
    )

  private val postImportEndpoint: ZServerEndpoint[Any, ZioStreams] = projectEndpoints.postImport
    .serverLogic(userSession =>
      (shortcode, stream) =>
        guarded {
          authorizationHandler.ensureAdminScope(userSession) *>
            importService
              .importZipStream(shortcode, stream.orDie)
              .flatMapError {
                case IoError(e)                 => IngestFailure.toApiProblem(e)
                case BagItValidationFailed(msg) => ZIO.succeed(BadRequest.invalidBody(s"BagIt validation failed: $msg"))
                case ProjectAlreadyExists(sc)   =>
                  ZIO.succeed(Conflict(s"Project ${sc.value} already exists. Delete the project before importing."))
              }
              .as(UploadResponse())
        },
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
      postImportEndpoint,
      postBulkIngestUploadEndpoint,
    )
}

object ProjectsEndpointsHandler {
  val layer = ZLayer.derive[ProjectsEndpointsHandler]
}
