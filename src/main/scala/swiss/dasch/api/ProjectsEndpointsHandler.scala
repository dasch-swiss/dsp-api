/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.capabilities.zio.ZioStreams
import sttp.model.headers.ContentRange
import sttp.tapir.ztapir.ZServerEndpoint
import swiss.dasch.api.*
import swiss.dasch.api.ApiProblem.{BadRequest, Conflict, InternalServerError, NotFound}
import swiss.dasch.api.ProjectsEndpointsResponses.{
  AssetCheckResultResponse,
  AssetInfoResponse,
  ProjectResponse,
  UploadResponse,
}
import swiss.dasch.domain.*
import zio.stream.{ZSink, ZStream}
import zio.{ZIO, ZLayer, stream}

import java.io.IOException

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

  private val getProjectsAssetsInfoEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.getProjectsAssetsInfo
    .serverLogic(userSession =>
      (shortcode, assetId) =>
        {
          val ref = AssetRef(assetId, shortcode)
          authorizationHandler.ensureProjectReadable(userSession, shortcode) *>
            assetInfoService
              .findByAssetRef(ref)
              .some
              .mapBoth(
                assetRefNotFoundOrServerError(_, ref),
                AssetInfoResponse.from,
              )
        },
    )

  private val postProjectAssetEndpoint: ZServerEndpoint[Any, ZioStreams] = projectEndpoints.postProjectAsset
    .serverLogic(principal => { case (shortcode, filename, stream) =>
      authorizationHandler.ensureProjectWritable(principal, shortcode) *>
        ZIO.scoped {
          for {
            prj <- projectService.findOrCreateProject(shortcode).mapError(InternalServerError(_))
            tmpDir <-
              storageService.createTempDirectoryScoped(s"${prj.shortcode}-ingest").mapError(InternalServerError(_))
            tmpFile = tmpDir / filename.value
            _      <- stream.run(ZSink.fromFile(tmpFile.toFile)).mapError(InternalServerError(_))
            asset  <- ingestService.ingestFile(tmpFile, shortcode).mapError(InternalServerError(_))
            info   <- assetInfoService.findByAssetRef(asset.ref).someOrFailException.orDie
          } yield AssetInfoResponse.from(info)
        }
    })

  private val postBulkIngestEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.postBulkIngest
    .serverLogic(userSession =>
      code =>
        authorizationHandler.ensureAdminScope(userSession) *>
          bulkIngestService
            .startBulkIngest(code)
            .mapBoth(
              _ => failBulkIngestInProgress(code),
              _ => ProjectResponse.from(code),
            ),
    )

  private val postBulkIngestEndpointFinalize: ZServerEndpoint[Any, Any] = projectEndpoints.postBulkIngestFinalize
    .serverLogic(userSession =>
      code =>
        authorizationHandler.ensureAdminScope(userSession) *>
          bulkIngestService
            .finalizeBulkIngest(code)
            .mapBoth(
              _ => failBulkIngestInProgress(code),
              _ => ProjectResponse.from(code),
            ),
    )

  private val getBulkIngestMappingCsvEndpoint: ZServerEndpoint[Any, Any] =
    projectEndpoints.getBulkIngestMappingCsv
      .serverLogic(userSession =>
        code =>
          authorizationHandler.ensureAdminScope(userSession) *>
            bulkIngestService
              .getBulkIngestMappingCsv(code)
              .mapError {
                case None                           => failBulkIngestInProgress(code)
                case Some(ioException: IOException) => InternalServerError(ioException)
              }
              .some
              .mapError(_ => NotFound(code)),
      )

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
      getProjectsAssetsInfoEndpoint,
      postProjectAssetEndpoint,
      postBulkIngestEndpoint,
      postBulkIngestEndpointFinalize,
      getBulkIngestMappingCsvEndpoint,
      postExportEndpoint,
      getImportEndpoint,
    )
}

object ProjectsEndpointsHandler {
  val layer = ZLayer.derive[ProjectsEndpointsHandler]
}
