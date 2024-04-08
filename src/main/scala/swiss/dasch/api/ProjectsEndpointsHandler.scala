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
import zio.stream.ZStream
import zio.{ZIO, ZLayer, stream}

import java.io.IOException

final case class ProjectsEndpointsHandler(
  bulkIngestService: BulkIngestService,
  importService: ImportService,
  projectEndpoints: ProjectsEndpoints,
  projectService: ProjectService,
  reportService: ReportService,
  assetInfoService: AssetInfoService,
) extends HandlerFunctions {

  val getProjectsEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.getProjectsEndpoint
    .serverLogic(_ =>
      _ =>
        projectService
          .listAllProjects()
          .mapBoth(
            InternalServerError(_),
            list =>
              (list.map(ProjectResponse.from), ContentRange("items", Some(0, list.size), Some(list.size)).toString),
          ),
    )

  val getProjectByShortcodeEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.getProjectByShortcodeEndpoint
    .serverLogic(_ =>
      shortcode =>
        projectService
          .findProject(shortcode)
          .some
          .mapBoth(
            projectNotFoundOrServerError(_, shortcode),
            _ => ProjectResponse.from(shortcode),
          ),
    )

  private val getProjectChecksumReportEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.getProjectsChecksumReport
    .serverLogic(_ =>
      shortcode =>
        reportService
          .checksumReport(shortcode)
          .some
          .mapBoth(
            projectNotFoundOrServerError(_, shortcode),
            AssetCheckResultResponse.from,
          ),
    )

  private val getProjectsAssetsInfoEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.getProjectsAssetsInfo
    .serverLogic(_ =>
      (shortcode, assetId) =>
        {
          val ref = AssetRef(assetId, shortcode)
          assetInfoService
            .findByAssetRef(ref)
            .some
            .mapBoth(
              assetRefNotFoundOrServerError(_, ref),
              AssetInfoResponse.from,
            )
        },
    )

  private val postBulkIngestEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.postBulkIngest
    .serverLogic(_ =>
      code =>
        bulkIngestService
          .startBulkIngest(code)
          .mapBoth(
            _ => failBulkIngestInProgress(code),
            _ => ProjectResponse.from(code),
          ),
    )

  private val postBulkIngestEndpointFinalize: ZServerEndpoint[Any, Any] = projectEndpoints.postBulkIngestFinalize
    .serverLogic(_ =>
      code =>
        bulkIngestService
          .finalizeBulkIngest(code)
          .mapBoth(
            _ => failBulkIngestInProgress(code),
            _ => ProjectResponse.from(code),
          ),
    )

  private val getBulkIngestMappingCsvEndpoint: ZServerEndpoint[Any, Any] =
    projectEndpoints.getBulkIngestMappingCsv
      .serverLogic(_ =>
        code =>
          {
            bulkIngestService
              .getBulkIngestMappingCsv(code)
              .mapError {
                case None                           => failBulkIngestInProgress(code)
                case Some(ioException: IOException) => InternalServerError(ioException)
              }
              .some
              .mapError(_ => NotFound(code))
          },
      )

  private def failBulkIngestInProgress(code: ProjectShortcode) =
    Conflict(s"A bulk ingest is currently in progress for project ${code.value}.")

  private val postExportEndpoint: ZServerEndpoint[Any, ZioStreams] = projectEndpoints.postExport
    .serverLogic(_ =>
      shortcode =>
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
    .serverLogic(_ =>
      (shortcode, stream) =>
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
