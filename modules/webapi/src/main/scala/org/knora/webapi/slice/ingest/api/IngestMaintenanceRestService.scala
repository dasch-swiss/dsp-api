/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import zio.*

import org.knora.webapi.slice.ingest.api.IngestAuthorization.ensureAdminScope
import org.knora.webapi.slice.ingest.domain.*
import org.knora.webapi.slice.ingest.domain.ActionName.*

/** Every action is forked: the response only acknowledges it, and its outcome is logged. */
final class IngestMaintenanceRestService(
  maintenanceActions: MaintenanceActions,
  mimeTypeMigration: AssetMimeTypeMigrationService,
  projectService: ProjectService,
  sizeMigration: AssetSizeMigrationService,
) {

  def runAction(principal: IngestPrincipal)(
    action: ActionName,
    shortcodes: List[ProjectShortcode],
  ): IO[ApiProblem, String] =
    for {
      _     <- ensureAdminScope(principal)
      paths <- (if (shortcodes.isEmpty) projectService.listAllProjects() else projectService.findProjects(shortcodes))
                 .mapError(ApiProblem.InternalServerError(_))
      _ <- ZIO.logDebug(s"Maintenance endpoint called $action, $shortcodes, $paths")
      _ <- (action match {
             case UpdateAssetMetadata    => maintenanceActions.updateAssetMetadata(paths)
             case ApplyTopLeftCorrection => maintenanceActions.applyTopLeftCorrections(paths)
             case MigrateSizes           =>
               migrate(action, shortcodes)(sizeMigration.migrateAll(), sizeMigration.migrateProject)(
                 AssetSizeMigrationReport.zero,
               )(_ + _)(_.failed)
             case MigrateMimeTypes =>
               migrate(action, shortcodes)(mimeTypeMigration.migrateAll(), mimeTypeMigration.migrateProject)(
                 AssetMimeTypeMigrationReport.zero,
               )(_ + _)(_.failed)
           }).forkDaemon.logError
    } yield s"work in progress for projects ${paths.map(_.shortcode).mkString(", ")} (for details see logs)"

  def needsTopLeftCorrection(principal: IngestPrincipal): IO[ApiProblem, String] =
    ensureAdminScope(principal) *>
      maintenanceActions.createNeedsTopLeftCorrectionReport().forkDaemon.logError.as("work in progress")

  def wasTopLeftCorrectionApplied(principal: IngestPrincipal): IO[ApiProblem, String] =
    ensureAdminScope(principal) *>
      maintenanceActions.createWasTopLeftCorrectionAppliedReport().forkDaemon.logError.as("work in progress")

  /** Runs `all` without a restriction, else `one` per listed project, and logs the summed report. */
  private def migrate[Report](action: ActionName, shortcodes: List[ProjectShortcode])(
    all: Task[Report],
    one: ProjectShortcode => Task[Report],
  )(zero: Report)(add: (Report, Report) => Report)(failed: Report => Int): Task[Unit] = {
    val target = if (shortcodes.isEmpty) "all projects" else s"projects ${shortcodes.mkString(", ")}"
    (if (shortcodes.isEmpty) all else ZIO.foldLeft(shortcodes)(zero)((acc, sc) => one(sc).map(add(acc, _))))
      .flatMap(report => ZIO.logInfo(s"$action for $target finished with ${failed(report)} failed: $report"))
  }
}

object IngestMaintenanceRestService {
  val layer = ZLayer.derive[IngestMaintenanceRestService]
}
