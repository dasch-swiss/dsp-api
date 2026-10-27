/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.api

import zio.*

import org.knora.webapi.slice.ingest.api.IngestAuthorization.ensureAdminScope
import org.knora.webapi.slice.ingest.domain.*
import org.knora.webapi.slice.ingest.domain.ActionName.*
import org.knora.webapi.slice.ingest.infrastructure.IngestExecutor

/**
 * Every action is forked: the response only acknowledges it, and its outcome, failure included, is logged. The forked
 * fiber inherits the ingest executor from `run`.
 */
final class IngestMaintenanceRestService(
  maintenanceActions: MaintenanceActions,
  mimeTypeMigration: AssetMimeTypeMigrationService,
  projectService: ProjectService,
  sizeMigration: AssetSizeMigrationService,
  ingestExecutor: IngestExecutor,
) {

  def runAction(principal: IngestPrincipal)(
    action: ActionName,
    shortcodes: List[ProjectShortcode],
  ): IO[ApiProblem, String] = ingestExecutor.run {
    for {
      _     <- ensureAdminScope(principal)
      paths <- (if (shortcodes.isEmpty) projectService.listAllProjects() else projectService.findProjects(shortcodes))
                 .mapError(ApiProblem.InternalServerError(_))
      _     <- ZIO.logDebug(s"Maintenance endpoint called $action, $shortcodes, $paths")
      target = if (shortcodes.isEmpty) "all projects" else s"projects ${shortcodes.mkString(", ")}"
      _     <- (action match {
             case UpdateAssetMetadata    => maintenanceActions.updateAssetMetadata(paths)
             case ApplyTopLeftCorrection => maintenanceActions.applyTopLeftCorrections(paths)
             case MigrateSizes           =>
               (if (shortcodes.isEmpty) sizeMigration.migrateAll()
                else
                  ZIO
                    .foreach(shortcodes)(sizeMigration.migrateProject)
                    .map(_.fold(AssetSizeMigrationReport.zero)(_ + _)))
                 .tap(r => ZIO.logInfo(s"$action for $target finished with ${r.failed} failed: $r"))
             case MigrateMimeTypes =>
               (if (shortcodes.isEmpty) mimeTypeMigration.migrateAll()
                else
                  ZIO
                    .foreach(shortcodes)(mimeTypeMigration.migrateProject)
                    .map(_.fold(AssetMimeTypeMigrationReport.zero)(_ + _)))
                 .tap(r => ZIO.logInfo(s"$action for $target finished with ${r.failed} failed: $r"))
           }).logError.forkDaemon
    } yield s"work in progress for projects ${paths.map(_.shortcode).mkString(", ")} (for details see logs)"
  }

  def needsTopLeftCorrection(principal: IngestPrincipal): IO[ApiProblem, String] = ingestExecutor.run {
    ensureAdminScope(principal) *>
      maintenanceActions.createNeedsTopLeftCorrectionReport().logError.forkDaemon.as("work in progress")
  }

  def wasTopLeftCorrectionApplied(principal: IngestPrincipal): IO[ApiProblem, String] = ingestExecutor.run {
    ensureAdminScope(principal) *>
      maintenanceActions.createWasTopLeftCorrectionAppliedReport().logError.forkDaemon.as("work in progress")
  }

}

object IngestMaintenanceRestService {
  val layer = ZLayer.derive[IngestMaintenanceRestService]
}
