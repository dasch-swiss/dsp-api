/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.api.v3.projects

import sttp.capabilities.zio.ZioStreams
import zio.*
import zio.stream.ZStream
import zio.telemetry.opentelemetry.tracing.Tracing

import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.`export`.domain.CurrentDataTask
import org.knora.webapi.slice.`export`.domain.DataTaskId
import org.knora.webapi.slice.`export`.domain.ExportExistsError
import org.knora.webapi.slice.`export`.domain.ExportFailedError
import org.knora.webapi.slice.`export`.domain.ExportInProgressError
import org.knora.webapi.slice.`export`.domain.ImportExistsError
import org.knora.webapi.slice.`export`.domain.ImportInProgressError
import org.knora.webapi.slice.`export`.domain.ImportSlotBusyError
import org.knora.webapi.slice.`export`.domain.ProjectDataImportService
import org.knora.webapi.slice.`export`.domain.ProjectMigrationExportService
import org.knora.webapi.slice.`export`.domain.ProjectMigrationImportService
import org.knora.webapi.slice.`export`.domain.TaskCreator
import org.knora.webapi.slice.admin.domain.model.Email
import org.knora.webapi.slice.admin.domain.model.KnoraProject.ProjectIri
import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.slice.admin.domain.model.User
import org.knora.webapi.slice.admin.domain.model.Username
import org.knora.webapi.slice.admin.domain.service.KnoraProjectService
import org.knora.webapi.slice.admin.domain.service.UserService
import org.knora.webapi.slice.api.v3.BadRequest
import org.knora.webapi.slice.api.v3.Conflict
import org.knora.webapi.slice.api.v3.NotFound
import org.knora.webapi.slice.api.v3.PayloadTooLarge
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
import org.knora.webapi.slice.api.v3.V3ErrorCode.import_running
import org.knora.webapi.slice.api.v3.V3ErrorCode.on_behalf_of_user_ineligible
import org.knora.webapi.slice.api.v3.V3ErrorCode.on_behalf_of_user_not_found
import org.knora.webapi.slice.api.v3.V3ErrorCode.project_ontologies_missing
import org.knora.webapi.slice.api.v3.V3ErrorInfo
import org.knora.webapi.slice.security.ExportCaller
import org.knora.webapi.slice.security.ServicePrincipal

final class V3ProjectsRestService(
  exportService: ProjectMigrationExportService,
  importService: ProjectMigrationImportService,
  dataImportService: ProjectDataImportService,
  projectService: KnoraProjectService,
  userService: UserService,
  tracing: Tracing,
) {

  import V3ProjectsRestService.ExportCallFacts
  import V3ProjectsRestService.ExportLookupError
  import V3ProjectsRestService.conflict
  import V3ProjectsRestService.creatorOf
  import V3ProjectsRestService.importConflict
  import V3ProjectsRestService.mayAccess
  import V3ProjectsRestService.outcomeOf

  private def ensureProjectExists(projectIri: ProjectIri) =
    projectService.findById(projectIri).orDie.someOrFail(NotFound.from(projectIri))

  private def notFound(code: NotFounds, prj: ProjectIri, id: DataTaskId) =
    NotFound(
      code,
      code.template.replace("{id}", id.value).replace("{projectIri}", prj.value),
      Map("id" -> id.value, "projectIri" -> prj.value),
    )

  /**
   * Runs one export handler. For a service caller it sets the service's span attributes and writes the call's single
   * audit line from `onExit`, so a failed, crashed or interrupted call is recorded too; a download is recorded when
   * its stream is handed over, not when the stream ends. A SystemAdmin call gets neither. The handler records in
   * `facts` what it learns about the export, which is all the audit line knows beyond the call's arguments.
   */
  private def exportCall[A](
    caller: ExportCaller,
    action: ExportAction,
    projectIri: ProjectIri,
    exportId: Option[DataTaskId],
  )(
    handler: Ref[ExportCallFacts] => IO[V3ErrorInfo, A],
  ): IO[V3ErrorInfo, A] =
    Ref.make(ExportCallFacts()).flatMap { facts =>
      caller match
        case ExportCaller.Admin(_)           => handler(facts)
        case ExportCaller.Service(principal) =>
          serviceSpanAttributes(principal, action) *>
            handler(facts).onExit(exit =>
              facts.get.flatMap(f =>
                ExportServiceAudit(
                  principal,
                  action,
                  outcomeOf(exit, f),
                  f.task.fold(projectIri)(_.projectIri),
                  projectIri,
                  f.task.map(_.id).orElse(exportId),
                ).log,
              ),
            )
    }

  // Only bounded values go on the span: ids, IRIs and the jti would become metric labels; they are in the audit line.
  private def serviceSpanAttributes(principal: ServicePrincipal, action: ExportAction): UIO[Unit] =
    tracing.setAttribute(ExportServiceAudit.ServiceNameKey, principal.name.value) *>
      tracing.setAttribute(ExportServiceAudit.ServiceKidKey, principal.kid) *>
      tracing.setAttribute(ExportServiceAudit.ActionKey, action.value).unit

  private def serviceShortcode(caller: ExportCaller, shortcode: Shortcode): UIO[Unit] = caller match
    case ExportCaller.Admin(_)   => ZIO.unit
    case ExportCaller.Service(_) => tracing.setAttribute(ExportServiceAudit.ShortcodeKey, shortcode.value).unit

  // Looks up the found export's project for its shortcode only; it never gates the call. A project that is gone, or
  // a lookup that fails, leaves the attribute out.
  private def serviceShortcodeOf(caller: ExportCaller, task: CurrentDataTask): UIO[Unit] = caller match
    case ExportCaller.Admin(_)   => ZIO.unit
    case ExportCaller.Service(_) =>
      projectService
        .findById(task.projectIri)
        .orElseSucceed(None)
        .flatMap(ZIO.foreachDiscard(_)(project => serviceShortcode(caller, project.shortcode)))

  def triggerProjectExportCreate(
    caller: ExportCaller,
  )(projectIri: ProjectIri, skipAssets: Boolean): IO[V3ErrorInfo, DataTaskStatusResponse] =
    exportCall(caller, ExportAction.Create, projectIri, None) { facts =>
      for {
        project <- ensureProjectExists(projectIri)
        _       <- serviceShortcode(caller, project.shortcode)
        state   <-
          exportService
            .createExport(project, creatorOf(caller), skipAssets)
            .flatMapError { case ExportExistsError(t) =>
              facts.update(_.found(t)).as(conflict(export_exists, t.projectIri, t.id))
            }
        _ <- facts.update(_.found(state))
      } yield DataTaskStatusResponse.from(state)
    }

  // The three handlers below - status, delete and download - resolve the export by id alone from the server-wide
  // task slot. Their `projectIri` is unvalidated path input, echoed into the error payload and never checked
  // against the export, so it must stay unresolved here: re-adding an existence gate reproduces DEV-6766, where
  // an erased project's export becomes unreachable and wedges the one global slot for every project.
  // Each checks ownership in `findExport` before any state check or deletion, so a service cannot read, download
  // or delete another caller's export, nor learn its state, not even that it is in progress.
  private def findExport(
    caller: ExportCaller,
    exportId: DataTaskId,
    facts: Ref[ExportCallFacts],
  ): IO[ExportLookupError, CurrentDataTask] =
    exportService
      .getExportStatus(exportId)
      .orElseFail(ExportLookupError.NotFound)
      .tap(task => facts.update(_.found(task)))
      .filterOrFail(mayAccess(caller, _))(ExportLookupError.NotOwner)
      .tapError(refusal => facts.update(_.copy(refusal = Some(refusal))))
      .tap(serviceShortcodeOf(caller, _))

  // A refused owner and an unknown id get the same answer.
  private def exportNotFound(projectIri: ProjectIri, exportId: DataTaskId)(e: ExportLookupError): NotFound = e match
    case ExportLookupError.NotFound | ExportLookupError.NotOwner => notFound(export_not_found, projectIri, exportId)

  def getProjectExportStatus(
    caller: ExportCaller,
  )(projectIri: ProjectIri, exportId: DataTaskId): IO[V3ErrorInfo, DataTaskStatusResponse] =
    exportCall(caller, ExportAction.Status, projectIri, Some(exportId)) { facts =>
      findExport(caller, exportId, facts).mapBoth(exportNotFound(projectIri, exportId), DataTaskStatusResponse.from)
    }

  // `projectIri` is unvalidated path input here - see findExport above. The `None` below is not dead: the slot can
  // change between `findExport` and the deletion, and the deletion then finds no export with this id.
  def deleteProjectExport(caller: ExportCaller)(projectIri: ProjectIri, exportId: DataTaskId): IO[V3ErrorInfo, Unit] =
    exportCall(caller, ExportAction.Delete, projectIri, Some(exportId)) { facts =>
      for {
        _ <- findExport(caller, exportId, facts).mapError(exportNotFound(projectIri, exportId))
        _ <- exportService
               .deleteExport(exportId)
               .mapError {
                 case Some(ExportInProgressError(t)) => conflict(export_in_progress, t.projectIri, t.id)
                 case None                           => notFound(export_not_found, projectIri, exportId)
               }
      } yield ()
    }

  // Download the export as a zip stream. Annotated to match the tapir endpoint (stream + media type + filename).
  // `projectIri` is unvalidated path input here - see findExport above.
  def downloadProjectExport(
    caller: ExportCaller,
  )(projectIri: ProjectIri, exportId: DataTaskId): IO[V3ErrorInfo, (String, ZioStreams.BinaryStream)] =
    exportCall(caller, ExportAction.Download, projectIri, Some(exportId)) { facts =>
      for {
        _                 <- findExport(caller, exportId, facts).mapError(exportNotFound(projectIri, exportId))
        filenameAndStream <- exportService.downloadExport(exportId).mapError {
                               case Some(ExportInProgressError(t)) => conflict(export_in_progress, t.projectIri, t.id)
                               case Some(ExportFailedError(t))     => conflict(export_failed, t.projectIri, t.id)
                               case None                           => notFound(export_not_found, projectIri, exportId)
                             }
        (filename, stream)            = filenameAndStream
        contentDispositionHeaderValue = s"""attachment; filename="$filename""""
      } yield (contentDispositionHeaderValue, stream)
    }

  private val failImportFeatureMissing: IO[V3ErrorInfo, Unit] = ZIO
    .fail(NotFound.featureMissing("allowImportMigrationBagit"))
    .unlessZIO(AppConfig.features(_.allowImportMigrationBagit))
    .unit

  def triggerProjectImportCreate(
    user: User,
  )(projectIri: ProjectIri, stream: ZStream[Any, Throwable, Byte]): IO[V3ErrorInfo, DataTaskStatusResponse] =
    failImportFeatureMissing.zipRight(for {
      state <-
        importService
          .importDataExport(projectIri, user, stream)
          .mapError(importConflict)
    } yield DataTaskStatusResponse.from(state))

  def getProjectImportStatus(projectIri: ProjectIri, importId: DataTaskId): IO[V3ErrorInfo, DataTaskStatusResponse] =
    failImportFeatureMissing.zipRight {
      for {
        state <-
          importService.getImportStatus(importId).orElseFail(notFound(import_not_found, projectIri, importId))
      } yield DataTaskStatusResponse.from(state)
    }

  def deleteProjectImport(projectIri: ProjectIri, importId: DataTaskId): IO[V3ErrorInfo, Unit] =
    failImportFeatureMissing.zipRight {
      for {
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

  // Keyed on the project only: these rejections are raised before any import task exists, so they cannot use the
  // generic `conflict` helper (which needs a DataTaskId).
  private def conflictOnProject(code: Conflicts, projectIri: ProjectIri): Conflict =
    Conflict(code, code.template.replace("{projectIri}", projectIri.value), Map("projectIri" -> projectIri.value))

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
    declaredLength: Option[Long],
    onBehalfOfUser: String,
  ): IO[V3ErrorInfo, DataTaskStatusResponse] =
    failDataImportFeatureMissing.zipRight(for {
      // Rejected before the project lookup and before a task exists, so an oversized request leaves no task.
      _ <- ZIO
             .fromEither(
               ProjectDataImportService.checkDeclaredLength(declaredLength, ProjectDataImportService.maxPayloadBytes),
             )
             .mapError { case ProjectDataImportService.PayloadTooLargeError(d, l) => PayloadTooLarge.of(d, l) }
      project <- ensureProjectExists(projectIri)
      // On-behalf-of user: malformed identifier (400), then not-found (404), then eligibility (400). Runs after the
      // project-exists check and before the create-only precondition (G1 order).
      onBehalfOf <- resolveOnBehalfOfUser(projectIri, onBehalfOfUser)
      // Ontology-existence precondition (synchronous, so the client receives a real 409). Hoisted from the async
      // import task: an ontology-less project is rejected at trigger time. Ordered before the create-only check
      // (G1 order). `getOntologyGraphsForProject` reads the in-memory ontology cache, so this is cheap.
      _ <- ZIO
             .fail(conflictOnProject(project_ontologies_missing, projectIri))
             .whenZIO(
               projectService
                 .getOntologyGraphsForProject(project)
                 .orDieWith(e =>
                   new IllegalStateException(s"Ontology-existence check failed for project '${projectIri.value}'.", e),
                 )
                 .map(_.isEmpty),
             )
      // Create-only precondition (synchronous, so the client receives a real 409). It is re-verified inside the
      // import task immediately before the upload.
      _ <- ZIO
             .fail(conflictOnProject(data_graph_exists, projectIri))
             .whenZIO(
               dataImportService
                 .dataGraphExists(project)
                 .orDieWith(e =>
                   new IllegalStateException(s"Create-only check failed for project '${projectIri.value}'.", e),
                 ),
             )
      state <-
        dataImportService
          .importDataGraph(project = project, createdBy = user, onBehalfOf = onBehalfOf, stream = stream)
          .mapError(importConflict)
    } yield DataTaskStatusResponse.from(state))

  def getProjectDataImportStatus(
    projectIri: ProjectIri,
    importId: DataTaskId,
  ): IO[V3ErrorInfo, DataTaskStatusResponse] =
    failDataImportFeatureMissing.zipRight {
      for {
        state <-
          dataImportService.getImportStatus(importId).orElseFail(notFound(import_not_found, projectIri, importId))
      } yield DataTaskStatusResponse.from(state)
    }

  def deleteProjectDataImport(projectIri: ProjectIri, importId: DataTaskId): IO[V3ErrorInfo, Unit] =
    failDataImportFeatureMissing.zipRight {
      for {
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

  /** Why an export lookup failed. Both are answered with the same 404; only the server tells them apart. */
  private[projects] enum ExportLookupError {
    case NotFound
    case NotOwner
  }

  /** What one export call learned about the export it acted on or collided with, for its audit line. */
  private[projects] final case class ExportCallFacts(
    task: Option[CurrentDataTask] = None,
    refusal: Option[ExportLookupError] = None,
  ) {
    def found(t: CurrentDataTask): ExportCallFacts = copy(task = Some(t))
  }

  /** The outcome is taken from the lookup's refusal, if any, before it became the shared 404. */
  private[projects] def outcomeOf(exit: Exit[V3ErrorInfo, Any], facts: ExportCallFacts): ExportOutcome =
    exit match
      case Exit.Success(_)     => ExportOutcome.Ok
      case Exit.Failure(cause) =>
        (facts.refusal, cause.failureOption) match
          case (Some(ExportLookupError.NotOwner), _) => ExportOutcome.NotOwner
          case (Some(ExportLookupError.NotFound), _) => ExportOutcome.NotFound
          case (None, Some(_: NotFound))             => ExportOutcome.NotFound
          case (None, Some(_: Conflict))             => ExportOutcome.Conflict
          case (None, _)                             => ExportOutcome.Error

  private[projects] def creatorOf(caller: ExportCaller): TaskCreator = caller match
    case ExportCaller.Admin(user)        => TaskCreator.ByUser(user.userIri)
    case ExportCaller.Service(principal) => TaskCreator.ByService(principal.name)

  /**
   * A SystemAdmin may access every export, a service only those it created. Ownership is keyed on the service name, so
   * an export stays its service's across a key rotation.
   */
  private[projects] def mayAccess(caller: ExportCaller, task: CurrentDataTask): Boolean = caller match
    case ExportCaller.Admin(_)   => true
    case ExportCaller.Service(_) => task.createdBy == creatorOf(caller)

  private[projects] def conflict(code: Conflicts, prj: ProjectIri, id: DataTaskId): Conflict =
    Conflict(
      code,
      code.template.replace("{id}", id.value).replace("{projectIri}", prj.value),
      Map("id" -> id.value, "projectIri" -> prj.value),
    )

  def importConflict(e: ImportExistsError | ImportSlotBusyError): Conflict = e match
    case ImportExistsError(t)  => conflict(import_exists, t.projectIri, t.id)
    case ImportSlotBusyError() =>
      Conflict(import_running, import_running.template, Map.empty)
}
