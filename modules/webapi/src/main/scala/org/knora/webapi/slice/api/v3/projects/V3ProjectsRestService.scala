/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import sttp.capabilities.zio.ZioStreams
import zio.*
import zio.stream.ZStream

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.`export`.domain.DataTaskId
import org.knora.webapi.slice.`export`.domain.ExportExistsError
import org.knora.webapi.slice.`export`.domain.ExportFailedError
import org.knora.webapi.slice.`export`.domain.ExportInProgressError
import org.knora.webapi.slice.`export`.domain.ImportExistsError
import org.knora.webapi.slice.`export`.domain.ImportInProgressError
import org.knora.webapi.slice.`export`.domain.ProjectDataImportService
import org.knora.webapi.slice.`export`.domain.ProjectMigrationExportService
import org.knora.webapi.slice.`export`.domain.ProjectMigrationImportService
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.api.v3.BadRequest
import org.knora.webapi.slice.api.v3.Conflict
import org.knora.webapi.slice.api.v3.NotFound
import org.knora.webapi.slice.api.v3.V3Authorizer
import org.knora.webapi.slice.api.v3.V3ErrorCode.Conflicts
import org.knora.webapi.slice.api.v3.V3ErrorCode.NotFounds
import org.knora.webapi.slice.api.v3.V3ErrorCode.data_graph_exists
import org.knora.webapi.slice.api.v3.V3ErrorCode.export_exists
import org.knora.webapi.slice.api.v3.V3ErrorCode.export_failed
import org.knora.webapi.slice.api.v3.V3ErrorCode.export_in_progress
import org.knora.webapi.slice.api.v3.V3ErrorCode.export_not_found
import org.knora.webapi.slice.api.v3.V3ErrorCode.import_exists
import org.knora.webapi.slice.api.v3.V3ErrorCode.import_in_progress
import org.knora.webapi.slice.api.v3.V3ErrorCode.import_not_found
import org.knora.webapi.slice.api.v3.V3ErrorCode.on_behalf_of_user_ineligible
import org.knora.webapi.slice.api.v3.V3ErrorCode.on_behalf_of_user_not_found
import org.knora.webapi.slice.api.v3.V3ErrorCode.project_ontologies_missing
import org.knora.webapi.slice.api.v3.V3ErrorInfo

final class V3ProjectsRestService(
  auth: V3Authorizer,
  exportService: ProjectMigrationExportService,
  importService: ProjectMigrationImportService,
  dataImportService: ProjectDataImportService,
  projectService: KnoraProjectService,
  userService: UserService,
) {

  private def ensureSystemAdminAndProjectExists(user: User, projectIri: ProjectIri) =
    auth.ensureSystemAdmin(user) *>
      projectService.findById(projectIri).orDie.someOrFail(NotFound.from(projectIri))

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

  def triggerProjectExportCreate(
    user: User,
  )(projectIri: ProjectIri, skipAssets: Boolean): IO[V3ErrorInfo, DataTaskStatusResponse] =
    for {
      project <- ensureSystemAdminAndProjectExists(user, projectIri)
      state   <-
        exportService
          .createExport(project, user, skipAssets)
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

  private val failImportFeatureMissing: IO[V3ErrorInfo, Unit] = ZIO
    .fail(NotFound.featureMissing("allowImportMigrationBagit"))
    .unlessZIO(AppConfig.features(_.allowImportMigrationBagit))
    .unit

  def triggerProjectImportCreate(
    user: User,
  )(projectIri: ProjectIri, stream: ZStream[Any, Throwable, Byte]): IO[V3ErrorInfo, DataTaskStatusResponse] =
    failImportFeatureMissing.zipRight(for {
      _     <- auth.ensureSystemAdmin(user)
      state <-
        importService
          .importDataExport(projectIri, user, stream)
          .mapError { case ImportExistsError(t) => conflict(import_exists, t.projectIri, t.id) }
    } yield DataTaskStatusResponse.from(state))

  def getProjectImportStatus(
    user: User,
  )(projectIri: ProjectIri, importId: DataTaskId): IO[V3ErrorInfo, DataTaskStatusResponse] =
    failImportFeatureMissing.zipRight {
      for {
        _     <- auth.ensureSystemAdmin(user)
        state <-
          importService.getImportStatus(importId).orElseFail(notFound(import_not_found, projectIri, importId))
      } yield DataTaskStatusResponse.from(state)
    }

  def deleteProjectImport(user: User)(projectIri: ProjectIri, importId: DataTaskId): IO[V3ErrorInfo, Unit] =
    failImportFeatureMissing.zipRight {
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

  private val failDataImportFeatureMissing: IO[V3ErrorInfo, Unit] = ZIO
    .fail(NotFound.featureMissing("allowProjectDataImport"))
    .unlessZIO(AppConfig.features(_.allowProjectDataImport))
    .unit

  private def dataGraphExistsConflict(projectIri: ProjectIri): Conflict =
    Conflict(
      data_graph_exists,
      data_graph_exists.template.replace("{projectIri}", projectIri.value),
      Map("projectIri" -> projectIri.value),
    )

  // Keyed on the project only: this rejection is raised before any import task exists, so it cannot use the generic
  // `conflict` helper (which needs a DataTaskId). Mirrors `dataGraphExistsConflict`.
  private def projectOntologiesMissing(projectIri: ProjectIri): Conflict =
    Conflict(
      project_ontologies_missing,
      project_ontologies_missing.template.replace("{projectIri}", projectIri.value),
      Map("projectIri" -> projectIri.value),
    )

  // Request-phase rejections for the on-behalf-of user are raised before any task exists, so they cannot use the
  // generic `conflict`/`notFound` helpers (which need a DataTaskId). They are keyed on the project and the supplied
  // identifier — the only value available for a not-found user (G4).
  // Substitute the caller-supplied `{id}` last: the fixed values (projectIri, reason) carry no `{…}` tokens, so a
  // supplied identifier containing a literal `{reason}`/`{projectIri}` cannot be re-substituted into the message.
  private def onBehalfOfUserNotFound(projectIri: ProjectIri, onBehalfOfUser: String): NotFound =
    NotFound(
      on_behalf_of_user_not_found,
      on_behalf_of_user_not_found.template.replace("{projectIri}", projectIri.value).replace("{id}", onBehalfOfUser),
      Map("id" -> onBehalfOfUser, "projectIri" -> projectIri.value),
    )

  private def onBehalfOfUserIneligible(
    projectIri: ProjectIri,
    onBehalfOfUser: String,
    reason: OnBehalfOfIneligibility,
  ): BadRequest =
    BadRequest(
      on_behalf_of_user_ineligible,
      on_behalf_of_user_ineligible.template
        .replace("{projectIri}", projectIri.value)
        .replace("{reason}", reason.reason)
        .replace("{id}", onBehalfOfUser),
      Map("id" -> onBehalfOfUser, "projectIri" -> projectIri.value, "reason" -> reason.reason),
    )

  // Resolve the supplied identifier (email if it contains `@`, else username) to a User, then assert eligibility.
  // A value that parses as neither is `400 malformed_identifier`; a well-formed identifier that matches no user is
  // `404`. Eligibility and the resolved user are a trigger-time snapshot — not re-checked mid-import (G2/Q1).
  private def resolveOnBehalfOfUser(projectIri: ProjectIri, onBehalfOfUser: String): IO[V3ErrorInfo, User] = {
    val malformed                               = onBehalfOfUserIneligible(projectIri, onBehalfOfUser, OnBehalfOfIneligibility.MalformedIdentifier)
    val resolved: IO[V3ErrorInfo, Option[User]] =
      if (onBehalfOfUser.contains("@"))
        ZIO
          .fromEither(Email.from(onBehalfOfUser))
          .orElseFail(malformed)
          .flatMap(email => userService.findUserByEmail(email).orDie)
      else
        ZIO
          .fromEither(Username.from(onBehalfOfUser))
          .orElseFail(malformed)
          .flatMap(username => userService.findUserByUsername(username).orDie)
    for {
      user <- resolved.someOrFail(onBehalfOfUserNotFound(projectIri, onBehalfOfUser))
      _    <- OnBehalfOfUserEligibility.check(user, projectIri) match {
             case Some(reason) => ZIO.fail(onBehalfOfUserIneligible(projectIri, onBehalfOfUser, reason))
             case None         => ZIO.unit
           }
    } yield user
  }

  def triggerProjectDataImportCreate(
    user: User,
  )(
    projectIri: ProjectIri,
    stream: ZStream[Any, Throwable, Byte],
    onBehalfOfUser: String,
  ): IO[V3ErrorInfo, DataTaskStatusResponse] =
    failDataImportFeatureMissing.zipRight(for {
      project <- ensureSystemAdminAndProjectExists(user, projectIri)
      // On-behalf-of user: malformed identifier (400), then not-found (404), then eligibility (400). Runs after the
      // project-exists check and before the create-only precondition (G1 order).
      onBehalfOf <- resolveOnBehalfOfUser(projectIri, onBehalfOfUser)
      // Ontology-existence precondition (synchronous, so the client receives a real 409). Hoisted from the async
      // import task: an ontology-less project is rejected at trigger time. Ordered before the create-only check
      // (G1 order). `getOntologyGraphsForProject` reads the in-memory ontology cache, so this is cheap.
      _ <- ZIO
             .fail(projectOntologiesMissing(projectIri))
             .whenZIO(projectService.getOntologyGraphsForProject(project).orDie.map(_.isEmpty))
      // Create-only precondition (synchronous, so the client receives a real 409). It is re-verified inside the
      // import task immediately before the upload.
      _ <- ZIO
             .fail(dataGraphExistsConflict(projectIri))
             .whenZIO(dataImportService.dataGraphExists(project).orDie)
      state <-
        dataImportService
          .importDataGraph(project = project, createdBy = user, onBehalfOf = onBehalfOf, stream = stream)
          .mapError { case ImportExistsError(t) => conflict(import_exists, t.projectIri, t.id) }
    } yield DataTaskStatusResponse.from(state))

  def getProjectDataImportStatus(
    user: User,
  )(projectIri: ProjectIri, importId: DataTaskId): IO[V3ErrorInfo, DataTaskStatusResponse] =
    failDataImportFeatureMissing.zipRight {
      for {
        _     <- auth.ensureSystemAdmin(user)
        state <-
          dataImportService.getImportStatus(importId).orElseFail(notFound(import_not_found, projectIri, importId))
      } yield DataTaskStatusResponse.from(state)
    }

  def deleteProjectDataImport(user: User)(projectIri: ProjectIri, importId: DataTaskId): IO[V3ErrorInfo, Unit] =
    failDataImportFeatureMissing.zipRight {
      for {
        _ <- auth.ensureSystemAdmin(user)
        _ <- dataImportService
               .deleteImport(importId)
               .mapError {
                 case Some(ImportInProgressError(t)) => conflict(import_in_progress, t.projectIri, t.id)
                 case None                           => notFound(import_not_found, projectIri, importId)
               }
      } yield ()
    }
}

object V3ProjectsRestService {
  val layer = ZLayer.derive[V3ProjectsRestService]
}
