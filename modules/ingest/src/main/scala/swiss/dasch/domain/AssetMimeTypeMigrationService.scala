/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.AugmentedPath.ProjectFolder
import zio.*
import zio.nio.file.Path
import zio.stream.ZStream

/** What [[AssetInfoService.backfillOriginalMimeType]] did to one sidecar. */
enum MimeTypeBackfillOutcome {
  case Updated(mimeType: MimeType)
  case AlreadyPresent
  case UnknownExtension(originalFilename: String)
}

final case class AssetMimeTypeMigrationReport(
  found: Int,
  updated: Int,
  alreadyPresent: Int,
  unknownExtension: Int,
  failed: Int,
) {
  def +(other: AssetMimeTypeMigrationReport): AssetMimeTypeMigrationReport =
    AssetMimeTypeMigrationReport(
      found + other.found,
      updated + other.updated,
      alreadyPresent + other.alreadyPresent,
      unknownExtension + other.unknownExtension,
      failed + other.failed,
    )
}

object AssetMimeTypeMigrationReport {
  val zero: AssetMimeTypeMigrationReport              = AssetMimeTypeMigrationReport(0, 0, 0, 0, 0)
  val oneUpdated: AssetMimeTypeMigrationReport        = AssetMimeTypeMigrationReport(1, 1, 0, 0, 0)
  val oneAlreadyPresent: AssetMimeTypeMigrationReport = AssetMimeTypeMigrationReport(1, 0, 1, 0, 0)
  val oneUnknown: AssetMimeTypeMigrationReport        = AssetMimeTypeMigrationReport(1, 0, 0, 1, 0)
  val oneFailed: AssetMimeTypeMigrationReport         = AssetMimeTypeMigrationReport(1, 0, 0, 0, 1)
}

/**
 * Backfills `originalMimeType` into `.info` sidecars written before that field existed, deriving it
 * from `originalFilename`'s extension via [[MimeTypeGuesser]] — the same mapping the ingest pipeline
 * uses, so a backfilled sidecar is indistinguishable from a freshly written one and no mime type
 * outside `SupportedFileType` can enter the store. Every other field, checksums included, is left
 * alone. Entrypoint: [[swiss.dasch.MigrateMimeTypes]].
 */
final case class AssetMimeTypeMigrationService(
  assetInfoService: AssetInfoService,
  projectService: ProjectService,
) {

  /** A failing sidecar (unreadable or unparsable) is logged and counted, so it cannot strand the rest. */
  def migrateAll(): Task[AssetMimeTypeMigrationReport] =
    for {
      _      <- ZIO.logInfo("Collecting sidecars for mime type migration")
      report <- backfillAll(findAllInfoFiles())
      _      <- logReport(report)
    } yield report

  /** [[migrateAll]] limited to one project. */
  def migrateProject(shortcode: ProjectShortcode): Task[AssetMimeTypeMigrationReport] =
    for {
      project <- projectService
                   .findProject(shortcode)
                   .someOrFail(IllegalArgumentException(s"Project $shortcode not found"))
      _      <- ZIO.logInfo(s"Collecting sidecars of $shortcode for mime type migration")
      report <- backfillAll(findInfoFilesOfProject(project))
      _      <- logReport(report)
    } yield report

  private def logReport(report: AssetMimeTypeMigrationReport): UIO[Unit] =
    ZIO.logInfo(
      s"Mime type migration finished: ${report.updated} updated, ${report.alreadyPresent} already present, " +
        s"${report.unknownExtension} unknown extension, ${report.failed} failed of ${report.found}",
    )

  def findAllInfoFiles(): ZStream[Any, Throwable, Path] =
    ZStream
      .fromIterableZIO(projectService.listAllProjects())
      .flatMap(findInfoFilesOfProject)

  def findInfoFilesOfProject(project: ProjectFolder): ZStream[Any, Throwable, Path] =
    StorageService.findInPath(project.path, FileFilters.isInfoFile)

  /**
   * Streamed rather than collected first: the store holds ~10^5 sidecars, and only the running
   * report needs to be in memory.
   */
  private def backfillAll(infoFiles: ZStream[Any, Throwable, Path]): Task[AssetMimeTypeMigrationReport] =
    infoFiles
      .mapZIOPar(AssetMimeTypeMigrationService.parallelism)(backfillOne)
      .runFold(AssetMimeTypeMigrationReport.zero)(_ + _)

  private def backfillOne(infoFile: Path): UIO[AssetMimeTypeMigrationReport] =
    assetInfoService
      .backfillOriginalMimeType(infoFile)
      .flatMap {
        case MimeTypeBackfillOutcome.Updated(_)                         => ZIO.succeed(AssetMimeTypeMigrationReport.oneUpdated)
        case MimeTypeBackfillOutcome.AlreadyPresent                     => ZIO.succeed(AssetMimeTypeMigrationReport.oneAlreadyPresent)
        case MimeTypeBackfillOutcome.UnknownExtension(originalFilename) =>
          ZIO
            .logWarning(s"No mime type for extension of '$originalFilename' in $infoFile, left absent")
            .as(AssetMimeTypeMigrationReport.oneUnknown)
      }
      .catchAll(e =>
        ZIO
          .logError(s"Failed to backfill sidecar $infoFile: $e")
          .as(AssetMimeTypeMigrationReport.oneFailed),
      )
}

object AssetMimeTypeMigrationService {

  /**
   * Well above `StorageService.maxParallelism` (10): each unit of work is a small read of a ~450 byte
   * sidecar plus, at most, a write of the same, so throughput is bound by storage round-trip latency
   * rather than by bandwidth or CPU.
   */
  val parallelism: Int = 50

  val layer = ZLayer.derive[AssetMimeTypeMigrationService]
}
