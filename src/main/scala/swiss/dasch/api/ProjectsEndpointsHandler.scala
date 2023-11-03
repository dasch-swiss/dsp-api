/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api

import sttp.capabilities.zio.ZioStreams
import sttp.model.headers.ContentRange
import sttp.tapir.ztapir.ZServerEndpoint
import swiss.dasch.api.*
import swiss.dasch.api.ApiProblem.{BadRequest, InternalServerError}
import swiss.dasch.api.ProjectsEndpointsResponses.{AssetCheckResultResponse, ProjectResponse, UploadResponse}
import swiss.dasch.domain.*
import zio.stream.ZStream
import zio.{ZIO, ZLayer, stream}

final case class ProjectsEndpointsHandler(
  bulkIngestService: BulkIngestService,
  importService: ImportService,
  projectEndpoints: ProjectsEndpoints,
  projectService: ProjectService,
  reportService: ReportService
) extends HandlerFunctions {

  val getProjectsEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.getProjectsEndpoint
    .serverLogic(_ =>
      _ =>
        projectService
          .listAllProjects()
          .mapBoth(
            InternalServerError(_),
            list =>
              (list.map(ProjectResponse.make), ContentRange("items", Some(0, list.size), Some(list.size)).toString),
          )
    )

  val getProjectByShortcodeEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.getProjectByShortcodeEndpoint
    .serverLogic(_ =>
      shortcode =>
        projectService
          .findProject(shortcode)
          .some
          .mapBoth(
            projectNotFoundOrServerError(_, shortcode),
            _ => ProjectResponse.make(shortcode)
          )
    )

  val getProjectChecksumReportEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.getProjectsChecksumReport
    .serverLogic(_ =>
      shortcode =>
        reportService
          .checksumReport(shortcode)
          .some
          .mapBoth(
            projectNotFoundOrServerError(_, shortcode),
            AssetCheckResultResponse.make
          )
    )

  val postBulkIngestEndpoint: ZServerEndpoint[Any, Any] = projectEndpoints.postBulkIngest
    .serverLogic(_ =>
      code => bulkIngestService.startBulkIngest(code).logError.forkDaemon.as(ProjectResponse.make(code))
    )

  val postExportEndpoint: ZServerEndpoint[Any, ZioStreams] = projectEndpoints.postExport
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
                ZStream.fromFile(path.toFile).orDie
              ),
          )
    )

  val getImportEndpoint: ZServerEndpoint[Any, ZioStreams] = projectEndpoints.getImport
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
            _ => UploadResponse()
          )
    )

  val endpoints: List[ZServerEndpoint[Any, ZioStreams]] =
    List(
      getProjectsEndpoint,
      getProjectByShortcodeEndpoint,
      getProjectChecksumReportEndpoint,
      postBulkIngestEndpoint,
      postExportEndpoint,
      getImportEndpoint
    )
}

object ProjectsEndpointsHandler {
  val layer = ZLayer.derive[ProjectsEndpointsHandler]
}
