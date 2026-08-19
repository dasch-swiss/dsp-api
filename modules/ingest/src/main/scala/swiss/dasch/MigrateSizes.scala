/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch

import swiss.dasch.config.Configuration
import swiss.dasch.config.Configuration.JwtConfig
import swiss.dasch.config.Configuration.ServiceConfig
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.db.Db
import swiss.dasch.domain.*
import swiss.dasch.infrastructure.*
import zio.*

/**
 * One-off migration backfilling `sizeOriginal`/`sizeDerivative` into every `.info` sidecar in the store.
 * Reads `STORAGE_ASSET_DIR` like the service does, so point it at the same volume.
 *
 * Locally, via Bazel:
 *
 * {{{
 *   bazel run //modules/ingest:migrate-sizes            # whole asset store
 *   bazel run //modules/ingest:migrate-sizes -- 0801    # a single project
 * }}}
 *
 * Against a deployed image, swapping only the main class out of the image's own entrypoint:
 */
// docker compose run --rm --no-deps --entrypoint java ingest -cp '/sipi/lib/*' swiss.dasch.MigrateSizes
object MigrateSizes extends ZIOAppDefault {

  override val bootstrap: Layer[Config.Error, ServiceConfig & JwtConfig & StorageConfig] =
    Configuration.layer >+> Logger.layer

  private def migrate(shortcodes: Chunk[String]) =
    for {
      migration <- ZIO.service[AssetSizeMigrationService]
      report    <- shortcodes match {
                  case Chunk() => migration.migrateAll()
                  case codes   =>
                    ZIO
                      .foreach(codes) { code =>
                        ZIO
                          .fromEither(ProjectShortcode.from(code))
                          .mapError(e => IllegalArgumentException(s"Invalid project shortcode '$code': $e"))
                          .flatMap(migration.migrateProject)
                      }
                      .map(_.fold(AssetSizeMigrationReport.zero)(_ + _))
                }
    } yield report

  override val run: ZIO[ZIOAppArgs, Any, ExitCode] =
    (for {
      args   <- ZIOAppArgs.getArgs
      report <- migrate(args)
      // Non-zero exit so partial failures are visible to the caller.
      exitCode = if (report.failed > 0) ExitCode.failure else ExitCode.success
    } yield exitCode)
      .provideSome[ZIOAppArgs](
        AssetInfoServiceLive.layer,
        AssetSizeMigrationService.layer,
        Configuration.layer,
        Db.dataSourceLive,
        FileChecksumServiceLive.layer,
        ProjectRepositoryLive.layer,
        ProjectService.layer,
        StorageServiceLive.layer,
      )
}
