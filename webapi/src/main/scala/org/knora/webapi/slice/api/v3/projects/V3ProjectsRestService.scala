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
import org.knora.webapi.slice.api.v3.projects.domain.ExportExistsError
import org.knora.webapi.slice.api.v3.projects.domain.ExportFailedError
import org.knora.webapi.slice.api.v3.projects.domain.ExportInProgressError
import org.knora.webapi.slice.api.v3.projects.domain.ImportExistsError
import org.knora.webapi.slice.api.v3.projects.domain.ImportInProgressError
import org.knora.webapi.slice.api.v3.projects.domain.ProjectDataExportService
import org.knora.webapi.slice.api.v3.projects.domain.ProjectDataImportService

final class V3ProjectsRestService(
  auth: V3Authorizer,
  exportService: ProjectDataExportService,
  importService: ProjectDataImportService,
) {

  private def conflict(code: V3ErrorCode.Conflicts, prj: ProjectIri, id: DataTaskId): Conflict =
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

  def triggerProjectExportCreate(user: User)(projectIri: ProjectIri): IO[V3ErrorInfo, DataTaskStatusResponse] =
    for {
      _     <- auth.ensureSystemAdmin(user)
      state <-
        exportService
          .createExport(projectIri, user)
          .mapError((er: ExportExistsError) => conflict(V3ErrorCode.export_exists, er.value.projectIri, er.value.id))
    } yield DataTaskStatusResponse.from(state)

  def getProjectExportStatus(
    user: User,
  )(projectIri: ProjectIri, exportId: DataTaskId): IO[V3ErrorInfo, DataTaskStatusResponse] = for {
    _     <- auth.ensureSystemAdmin(user)
    state <- exportService.getExportStatus(exportId).orElseFail(notFound(projectIri, exportId))
  } yield DataTaskStatusResponse.from(state)

  def deleteProjectExport(
    user: User,
  )(projectIri: ProjectIri, exportId: DataTaskId): IO[V3ErrorInfo, Unit] = for {
    _ <- auth.ensureSystemAdmin(user)
    _ <- exportService
           .deleteExport(exportId)
           .mapError {
             case Some(er: ExportInProgressError) =>
               conflict(V3ErrorCode.export_in_progress, er.value.projectIri, er.value.id)
             case None => notFound(projectIri, exportId)
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
                               conflict(V3ErrorCode.export_in_progress, er.value.projectIri, er.value.id)
                             case Some(er: ExportFailedError) =>
                               conflict(V3ErrorCode.export_failed, er.value.projectIri, er.value.id)
                             case None => notFound(projectIri, exportId)
                           }
      (filename, stream)            = filenameAndStream
      contentDispositionHeaderValue = s"""attachment; filename="$filename""""
    } yield (contentDispositionHeaderValue, stream)

  def triggerProjectImportCreate(
    user: User,
  )(projectIri: ProjectIri, stream: ZStream[Any, Throwable, Byte]): IO[V3ErrorInfo, DataTaskStatusResponse] =
    for {
      _     <- auth.ensureSystemAdmin(user)
      state <-
        importService
          .importDataExport(projectIri, user, stream)
          .mapError((e: ImportExistsError) => conflict(V3ErrorCode.import_exists, e.value.projectIri, e.value.id))
    } yield DataTaskStatusResponse.from(state)

  def getProjectImportStatus(
    user: User,
  )(projectIri: ProjectIri, importId: DataTaskId): IO[V3ErrorInfo, DataTaskStatusResponse] =
    for {
      _     <- auth.ensureSystemAdmin(user)
      state <- importService.getImportStatus(importId).orElseFail(notFoundImport(projectIri, importId))
    } yield DataTaskStatusResponse.from(state)

  def deleteProjectImport(user: User)(projectIri: ProjectIri, importId: DataTaskId): IO[V3ErrorInfo, Unit] =
    for {
      _ <- auth.ensureSystemAdmin(user)
      _ <- importService
             .deleteImport(importId)
             .mapError {
               case Some(e: ImportInProgressError) =>
                 conflict(V3ErrorCode.import_in_progress, e.value.projectIri, e.value.id)
               case None => notFoundImport(projectIri, importId)
             }
    } yield ()
}

object V3ProjectsRestService {
  val layer = ProjectDataExportService.layer >+> ProjectDataImportService.layer >>> ZLayer.derive[V3ProjectsRestService]
}
