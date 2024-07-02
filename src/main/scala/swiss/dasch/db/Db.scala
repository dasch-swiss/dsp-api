/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.db

import com.zaxxer.hikari.{HikariConfig, HikariDataSource}
import io.getquill.*
import io.getquill.jdbczio.*
import swiss.dasch.config.Configuration.DbConfig
import zio.{URLayer, ZIO, ZLayer}

import javax.sql.DataSource

object Db {

  private def makeDataSource(dbConfig: DbConfig): HikariDataSource = {
    val config = new HikariConfig()
    config.setJdbcUrl(dbConfig.jdbcUrl)
    config.setConnectionInitSql("PRAGMA foreign_keys = ON")
    new HikariDataSource(config)
  }

  val dataSourceLive: URLayer[DbConfig, DataSource] =
    ZLayer.scoped(ZIO.fromAutoCloseable(ZIO.serviceWith[DbConfig](makeDataSource)))

  val quillLive: URLayer[DataSource, Quill.Sqlite[SnakeCase]] =
    Quill.Sqlite.fromNamingStrategy(SnakeCase)
}
