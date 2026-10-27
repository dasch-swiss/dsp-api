/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import zio.*

import org.knora.webapi.slice.ingest.api.IngestAuthorization.ensureAdminScope
import org.knora.webapi.slice.ingest.domain.*
import org.knora.webapi.slice.ingest.domain.ActionName.*

/** Every action is forked: the response only acknowledges it, and its outcome, failure included, is logged. */
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
               (if (shortcodes.isEmpty) sizeMigration.migrateAll()
                else
                  ZIO.foldLeft(shortcodes)(AssetSizeMigrationReport.zero)((sum, sc) =>
                    sizeMigration.migrateProject(sc).map(sum + _),
                  ))
                 .flatMap(report => logReport(action, shortcodes, report.failed, report))
             case MigrateMimeTypes =>
               (if (shortcodes.isEmpty) mimeTypeMigration.migrateAll()
                else
                  ZIO.foldLeft(shortcodes)(AssetMimeTypeMigrationReport.zero)((sum, sc) =>
                    mimeTypeMigration.migrateProject(sc).map(sum + _),
                  ))
                 .flatMap(report => logReport(action, shortcodes, report.failed, report))
           }).logError.forkDaemon
    } yield s"work in progress for projects ${paths.map(_.shortcode).mkString(", ")} (for details see logs)"

  def needsTopLeftCorrection(principal: IngestPrincipal): IO[ApiProblem, String] =
    ensureAdminScope(principal) *>
      maintenanceActions.createNeedsTopLeftCorrectionReport().logError.forkDaemon.as("work in progress")

  def wasTopLeftCorrectionApplied(principal: IngestPrincipal): IO[ApiProblem, String] =
    ensureAdminScope(principal) *>
      maintenanceActions.createWasTopLeftCorrectionAppliedReport().logError.forkDaemon.as("work in progress")

  private def logReport[R](
    action: ActionName,
    shortcodes: List[ProjectShortcode],
    failed: Int,
    report: R,
  ): UIO[Unit] = {
    val target = if (shortcodes.isEmpty) "all projects" else s"projects ${shortcodes.mkString(", ")}"
    ZIO.logInfo(s"$action for $target finished with $failed failed: $report")
  }
}

object IngestMaintenanceRestService {
  val layer = ZLayer.derive[IngestMaintenanceRestService]
}
