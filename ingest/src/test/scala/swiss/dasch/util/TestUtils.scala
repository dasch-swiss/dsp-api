/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.util

import io.getquill.SnakeCase
import io.getquill.jdbczio.Quill
import swiss.dasch.config.Configuration.DbConfig
import swiss.dasch.db.Db
import swiss.dasch.db.DbMigrator
import zio.RIO
import zio.Random
import zio.UIO
import zio.ULayer
import zio.ZIO
import zio.ZLayer

import java.nio.file.Files
import java.nio.file.Paths
import javax.sql.DataSource

object TestUtils {
  type TestDbLayer = DbConfig & DataSource & DbMigrator & Quill.Sqlite[SnakeCase]

  private val initializeDb: RIO[DbMigrator, Unit] = DbMigrator.migrateOrDie()

  private val createTestDbConfig: ZIO[Any, Nothing, DbConfig] = for {
    uuid   <- Random.RandomLive.nextUUID
    tmpDir <- zio.System.SystemLive.propertyOrElse("java.io.tmpdir", ".").orDie
  } yield DbConfig(s"jdbc:sqlite:$tmpDir/ingest-$uuid.sqlite")

  private def clearDb(cfg: DbConfig): UIO[Unit] = for {
    dbPath <- ZIO.succeed(Paths.get(cfg.jdbcUrl.replace("jdbc:sqlite:", "")))
    _      <- ZIO.attemptBlocking(Files.deleteIfExists(dbPath)).orDie
  } yield ()

  private val testDbConfigLive: ULayer[DbConfig] =
    ZLayer.scoped(ZIO.acquireRelease(createTestDbConfig)(config => clearDb(config)))

  val testDbLayer: ULayer[TestDbLayer] =
    testDbConfigLive >+> Db.dataSourceLive >+> Db.quillLive >+> DbMigrator.layer

  val testDbLayerWithEmptyDb: ULayer[TestDbLayer] =
    testDbLayer >+> ZLayer.fromZIO(initializeDb.orDie)
}
