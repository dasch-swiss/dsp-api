/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateErrorResult
import zio.{RIO, Task, ZIO, ZLayer}

import javax.sql.DataSource

class DbMigrator(ds: DataSource) {

  def migrate(): Task[Unit] =
    ZIO
      .attempt(
        Flyway
          .configure()
          .dataSource(ds)
          .load()
          .migrate(),
      )
      .flatMap {
        case r: MigrateErrorResult => ZIO.fail(DbMigrationFailed(r.error.message, r.error.cause.stackTrace))
        case _                     => ZIO.unit
      }
      .onError(cause => ZIO.logErrorCause("Database migration has failed", cause))

}

case class DbMigrationFailed(msg: String, stackTrace: String) extends RuntimeException(s"$msg\n$stackTrace")

object DbMigrator {
  def migrateOrDie(): RIO[DbMigrator, Unit] = ZIO.serviceWithZIO[DbMigrator](_.migrate()).logError.orDie
  def layer                                 = ZLayer.derive[DbMigrator]
}
