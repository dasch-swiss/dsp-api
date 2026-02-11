/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import sttp.capabilities.zio.ZioStreams
import zio.*
import zio.stream.ZStream

import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.api.v3.Conflict
import org.knora.webapi.slice.api.v3.NotFound
import org.knora.webapi.slice.api.v3.V3Authorizer
import org.knora.webapi.slice.api.v3.V3ErrorCode
import org.knora.webapi.slice.api.v3.V3ErrorCode.Conflicts
import org.knora.webapi.slice.api.v3.V3ErrorCode.NotFounds
import org.knora.webapi.slice.api.v3.V3ErrorInfo
import org.knora.webapi.slice.api.v3.projects.domain.DataTaskId
import org.knora.webapi.slice.api.v3.projects.domain.ExportFailedError
import org.knora.webapi.slice.api.v3.projects.domain.ExportInProgressError
import org.knora.webapi.slice.api.v3.projects.domain.ImportInProgressError
import org.knora.webapi.slice.api.v3.projects.domain.ProjectDataExportService
import org.knora.webapi.slice.api.v3.projects.domain.ProjectDataImportService

final class V3ProjectsRestService(
  auth: V3Authorizer,
  exportService: ProjectDataExportService,
  importService: ProjectDataImportService,
) {

  private def conflict(prj: ProjectIri, id: DataTaskId): Conflict =
    buildConflict(V3ErrorCode.export_exists, prj, id)

  private def conflictImport(prj: ProjectIri, id: DataTaskId): Conflict =
    buildConflict(V3ErrorCode.import_exists, prj, id)

  private def buildConflict(code: Conflicts, prj: ProjectIri, id: DataTaskId) =
    Conflict(
      code,
      code.template.replace("{id}", id.value).replace("{projectIri}", prj.value),
      Map("id" -> id.value, "projectIri" -> prj.value),
    )

  private def notFound(prj: ProjectIri, id: DataTaskId): NotFound =
    buildNotFound(V3ErrorCode.export_not_found, prj, id)

  private def notFoundImport(prj: ProjectIri, id: DataTaskId): NotFound =
    buildNotFound(V3ErrorCode.import_not_found, prj, id)

  private def buildNotFound(code: NotFounds, prj: ProjectIri, id: DataTaskId) =
    NotFound(
      code,
      code.template.replace("{id}", id.value).replace("{projectIri}", prj.value),
      Map("id" -> id.value, "projectIri" -> prj.value),
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
  )(projectIri: ProjectIri, exportId: DataTaskId): IO[V3ErrorInfo, ExportStatusResponse] = for {
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
  )(projectIri: ProjectIri, exportId: DataTaskId): IO[V3ErrorInfo, Unit] = for {
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
    exportId: DataTaskId,
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

  def triggerProjectImportCreate(
    user: User,
  )(projectIri: ProjectIri, stream: ZStream[Any, Throwable, Byte]): IO[V3ErrorInfo, ImportAcceptedResponse] =
    for {
      _   <- auth.ensureSystemAdmin(user)
      imp <- importService
               .importDataExport(projectIri, user, stream)
               .mapError((e: ImportInProgressError) => conflictImport(e.value.projectIri, e.value.id))
    } yield ImportAcceptedResponse(imp.id)

  def getProjectImportStatus(
    user: User,
  )(projectIri: ProjectIri, importId: DataTaskId): IO[V3ErrorInfo, ExportStatusResponse] =
    for {
      _   <- auth.ensureSystemAdmin(user)
      imp <- importService.getImportStatus(importId).orElseFail(notFoundImport(projectIri, importId))
    } yield ExportStatusResponse(
      imp.id,
      imp.projectIri,
      imp.status,
      imp.createdBy.userIri,
      imp.createdAt,
    )
}

object V3ProjectsRestService {
  val layer = ProjectDataExportService.layer >+> ProjectDataImportService.layer >>> ZLayer.derive[V3ProjectsRestService]
}
