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
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.api.v3.Conflict
import org.knora.webapi.slice.api.v3.NotFound
import org.knora.webapi.slice.api.v3.V3Authorizer
import org.knora.webapi.slice.api.v3.V3ErrorCode.Conflicts
import org.knora.webapi.slice.api.v3.V3ErrorCode.NotFounds
import org.knora.webapi.slice.api.v3.V3ErrorCode.export_exists
import org.knora.webapi.slice.api.v3.V3ErrorCode.export_failed
import org.knora.webapi.slice.api.v3.V3ErrorCode.export_in_progress
import org.knora.webapi.slice.api.v3.V3ErrorCode.export_not_found
import org.knora.webapi.slice.api.v3.V3ErrorCode.import_exists
import org.knora.webapi.slice.api.v3.V3ErrorCode.import_in_progress
import org.knora.webapi.slice.api.v3.V3ErrorCode.import_not_found
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
  projectService: KnoraProjectService,
) {

  private def ensureSystemAdminAndProjectExists(user: User, projectIri: ProjectIri) =
    auth.ensureSystemAdmin(user) *>
      projectService.findById(projectIri).orElseFail(NotFound.from(projectIri))

  private def conflict(code: Conflicts, prj: ProjectIri, id: DataTaskId): Conflict =
    Conflict(
      code,
      code.template.replace("{id}", id.value).replace("{projectIri}", prj.value),
      Map("id" -> id.value, "projectIri" -> prj.value),
    )

  private def notFound(code: NotFounds, prj: ProjectIri, id: DataTaskId) =
    NotFound(
      code,
      code.template.replace("{id}", id.value).replace("{projectIri}", prj.value),
      Map("id" -> id.value, "projectIri" -> prj.value),
    )

  def triggerProjectExportCreate(user: User)(projectIri: ProjectIri): IO[V3ErrorInfo, DataTaskStatusResponse] =
    for {
      _     <- ensureSystemAdminAndProjectExists(user, projectIri)
      state <-
        exportService
          .createExport(projectIri, user)
          .mapError { case ExportExistsError(t) => conflict(export_exists, t.projectIri, t.id) }
    } yield DataTaskStatusResponse.from(state)

  def getProjectExportStatus(
    user: User,
  )(projectIri: ProjectIri, exportId: DataTaskId): IO[V3ErrorInfo, DataTaskStatusResponse] = for {
    _     <- ensureSystemAdminAndProjectExists(user, projectIri)
    state <-
      exportService.getExportStatus(exportId).orElseFail(notFound(export_not_found, projectIri, exportId))
  } yield DataTaskStatusResponse.from(state)

  def deleteProjectExport(
    user: User,
  )(projectIri: ProjectIri, exportId: DataTaskId): IO[V3ErrorInfo, Unit] = for {
    _ <- ensureSystemAdminAndProjectExists(user, projectIri)
    _ <- exportService
           .deleteExport(exportId)
           .mapError {
             case Some(ExportInProgressError(t)) => conflict(export_in_progress, t.projectIri, t.id)
             case None                           => notFound(export_not_found, projectIri, exportId)
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
      _                 <- ensureSystemAdminAndProjectExists(user, projectIri)
      filenameAndStream <- exportService.downloadExport(exportId).mapError {
                             case Some(ExportInProgressError(t)) => conflict(export_in_progress, t.projectIri, t.id)
                             case Some(ExportFailedError(t))     => conflict(export_failed, t.projectIri, t.id)
                             case None                           => notFound(export_not_found, projectIri, exportId)
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
          .mapError { case ImportExistsError(t) => conflict(import_exists, t.projectIri, t.id) }
    } yield DataTaskStatusResponse.from(state)

  def getProjectImportStatus(
    user: User,
  )(projectIri: ProjectIri, importId: DataTaskId): IO[V3ErrorInfo, DataTaskStatusResponse] =
    for {
      _     <- auth.ensureSystemAdmin(user)
      state <-
        importService.getImportStatus(importId).orElseFail(notFound(import_not_found, projectIri, importId))
    } yield DataTaskStatusResponse.from(state)

  def deleteProjectImport(user: User)(projectIri: ProjectIri, importId: DataTaskId): IO[V3ErrorInfo, Unit] =
    for {
      _ <- auth.ensureSystemAdmin(user)
      _ <- importService
             .deleteImport(importId)
             .mapError {
               case Some(ImportInProgressError(t)) => conflict(import_in_progress, t.projectIri, t.id)
               case None                           => notFound(import_not_found, projectIri, importId)
             }
    } yield ()
}

object V3ProjectsRestService {
  val layer = ProjectDataExportService.layer >+> ProjectDataImportService.layer >>> ZLayer.derive[V3ProjectsRestService]
}
