/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import sttp.capabilities.zio.ZioStreams
import zio.*

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v3.Conflict
import org.knora.webapi.slice.api.v3.NotFound
import org.knora.webapi.slice.api.v3.V3Authorizer
import org.knora.webapi.slice.api.v3.V3ErrorCode
import org.knora.webapi.slice.api.v3.V3ErrorInfo
import org.knora.webapi.slice.api.v3.projects.domain.DataExportId
import org.knora.webapi.slice.api.v3.projects.domain.ExportFailedError
import org.knora.webapi.slice.api.v3.projects.domain.ExportInProgressError
import org.knora.webapi.slice.api.v3.projects.domain.ProjectDataExportService

final class V3ProjectsRestService(auth: V3Authorizer, exportService: ProjectDataExportService) {

  private def conflict(prj: ProjectIri, id: DataExportId): Conflict =
    val code: V3ErrorCode.Conflicts = V3ErrorCode.export_in_progress
    val exportId                    = id.value
    val projectIri                  = prj.value
    Conflict(
      code,
      code.template.replace("{id}", exportId).replace("{projectIri}", projectIri),
      Map("id" -> exportId, "projectIri" -> projectIri),
    )

  private def notFound(prj: ProjectIri, id: DataExportId): NotFound =
    val code: V3ErrorCode.NotFounds = V3ErrorCode.export_not_found
    val exportId                    = id.value
    val projectIri                  = prj.value
    NotFound(
      code,
      code.template.replace("{id}", exportId).replace("{projectIri}", projectIri),
      Map("id" -> exportId, "projectIri" -> projectIri),
    )

  def triggerProjectExportCreate(user: User)(projectIri: ProjectIri): IO[V3ErrorInfo, ExportAcceptedResponse] =
    for {
      _     <- auth.ensureSystemAdmin(user)
      curEx <- exportService
                 .createExport(projectIri, user)
                 .mapError((er: ExportInProgressError) => conflict(er.value.projectIri, er.value.id))
    } yield ExportAcceptedResponse(curEx.id)

  def getProjectExportStatus(
    user: User,
  )(projectIri: ProjectIri, exportId: DataExportId): IO[V3ErrorInfo, ExportStatusResponse] = for {
    _      <- auth.ensureSystemAdmin(user)
    curExp <- exportService
                .getExportStatus(exportId)
                .orElseFail(notFound(projectIri, exportId))
  } yield ExportStatusResponse(
    curExp.id,
    curExp.projectIri,
    curExp.status,
    curExp.createdBy.userIri,
    curExp.createdAt,
  )

  def deleteProjectExport(
    user: User,
  )(projectIri: ProjectIri, exportId: DataExportId): IO[V3ErrorInfo, Unit] = for {
    _ <- auth.ensureSystemAdmin(user)
    _ <- exportService
           .deleteExport(exportId)
           .mapError {
             case Some(er: ExportInProgressError) => conflict(er.value.projectIri, er.value.id)
             case None                            => notFound(projectIri, exportId)
           }
  } yield ()

  // Download the export as a zip stream. Annotated to match the tapir endpoint (stream + media type + filename).
  def downloadProjectExport(
    user: User,
  )(
    projectIri: ProjectIri,
    exportId: DataExportId,
  ): IO[V3ErrorInfo, (String, ZioStreams.BinaryStream)] =
    for {
      _                 <- auth.ensureSystemAdmin(user)
      filenameAndStream <- exportService.downloadExport(exportId).mapError {
                             case Some(er: ExportInProgressError) =>
                               conflict(er.value.projectIri, er.value.id)
                             case Some(er: ExportFailedError) =>
                               conflict(er.value.projectIri, er.value.id)
                             case None => notFound(projectIri, exportId)
                           }
      (filename, stream)            = filenameAndStream
      contentDispositionHeaderValue = s"""attachment; filename="$filename""""
    } yield (contentDispositionHeaderValue, stream)
}

object V3ProjectsRestService {
  val layer = ProjectDataExportService.layer >>> ZLayer.derive[V3ProjectsRestService]
}
