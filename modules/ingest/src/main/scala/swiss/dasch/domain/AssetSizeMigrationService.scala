/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.AugmentedPath.ProjectFolder
import zio.*
import zio.nio.file.Path
import zio.stream.ZStream

/** The `.info` sidecar plus the original and derivative paths it names — which may not exist on disk. */
final case class AssetFileGroup(
  assetRef: AssetRef,
  infoFile: Path,
  original: Path,
  derivative: Path,
)

final case class AssetSizeMigrationReport(found: Int, updated: Int, failed: Int) {
  def +(other: AssetSizeMigrationReport): AssetSizeMigrationReport =
    AssetSizeMigrationReport(found + other.found, updated + other.updated, failed + other.failed)
}

object AssetSizeMigrationReport {
  val zero: AssetSizeMigrationReport       = AssetSizeMigrationReport(0, 0, 0)
  val oneUpdated: AssetSizeMigrationReport = AssetSizeMigrationReport(1, 1, 0)
  val oneFailed: AssetSizeMigrationReport  = AssetSizeMigrationReport(1, 0, 1)
}

/**
 * Backfills `sizeOriginal`/`sizeDerivative` into `.info` sidecars written before those fields existed.
 * Checksums are left alone, so a corrupted asset stays detectable. Entrypoint: [[swiss.dasch.MigrateSizes]].
 */
final case class AssetSizeMigrationService(
  assetInfoService: AssetInfoService,
  projectService: ProjectService,
) {

  /** A failing sidecar (unreadable or unparsable) is logged and counted, so it cannot strand the rest. */
  def migrateAll(): Task[AssetSizeMigrationReport] =
    for {
      _      <- ZIO.logInfo("Collecting asset groups for size migration")
      groups <- findAllGroups().runCollect
      _      <- ZIO.logInfo(s"Found ${groups.size} asset groups, refreshing sidecars")
      report <- refreshAll(groups)
      _      <- ZIO.logInfo(
             s"Size migration finished: ${report.updated} updated, ${report.failed} failed of ${report.found}",
           )
    } yield report

  /** [[migrateAll]] limited to one project. */
  def migrateProject(shortcode: ProjectShortcode): Task[AssetSizeMigrationReport] =
    for {
      project <- projectService
                   .findProject(shortcode)
                   .someOrFail(IllegalArgumentException(s"Project $shortcode not found"))
      groups <- findGroupsOfProject(project).runCollect
      _      <- ZIO.logInfo(s"Found ${groups.size} asset groups in $shortcode, refreshing sidecars")
      report <- refreshAll(groups)
    } yield report

  def findAllGroups(): ZStream[Any, Throwable, AssetFileGroup] =
    ZStream
      .fromIterableZIO(projectService.listAllProjects())
      .flatMap(findGroupsOfProject)

  /** One group per `.info` file beneath the project folder. */
  def findGroupsOfProject(project: ProjectFolder): ZStream[Any, Throwable, AssetFileGroup] =
    StorageService
      .findInPath(project.path, FileFilters.isInfoFile)
      .mapZIOPar(StorageService.maxParallelism())(toGroup(_, project.shortcode))

  private def toGroup(infoFile: Path, shortcode: ProjectShortcode): Task[AssetFileGroup] =
    assetInfoService
      .loadFromFilesystem(infoFile, shortcode)
      .map(info => AssetFileGroup(info.assetRef, infoFile, info.original.file, info.derivative.file))

  private def refreshAll(groups: Chunk[AssetFileGroup]): Task[AssetSizeMigrationReport] =
    ZStream
      .fromChunk(groups)
      .mapZIOPar(StorageService.maxParallelism())(group =>
        refreshGroup(group)
          .as(AssetSizeMigrationReport.oneUpdated)
          .catchAll(e =>
            ZIO
              .logError(s"Failed to refresh sidecar ${group.infoFile}: $e")
              .as(AssetSizeMigrationReport.oneFailed),
          ),
      )
      .runFold(AssetSizeMigrationReport.zero)(_ + _)

  def refreshGroup(group: AssetFileGroup): Task[Unit] =
    assetInfoService.updateSizes(group.infoFile, group.original, group.derivative)
}

object AssetSizeMigrationService {
  val layer = ZLayer.derive[AssetSizeMigrationService]
}
