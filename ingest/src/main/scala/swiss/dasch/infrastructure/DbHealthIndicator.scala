/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import zio.{Duration, UIO, ZIO, ZLayer}

import javax.sql.DataSource

case class DbHealthIndicator(dataSource: DataSource) extends HealthIndicator {

  def health: UIO[(String, Health)] = queryHealth.fold(_ => Health.down, _ => Health.up).map(("db", _))

  private def queryHealth = ZIO.blocking {
    ZIO.scoped {
      ZIO
        .acquireRelease(ZIO.attempt(dataSource.getConnection))(con => ZIO.succeed(con.close))
        .flatMap(con => ZIO.attempt(con.createStatement.executeQuery("SELECT 1")))
    }
  }.disconnect.timeout(Duration.fromMillis(100)).some
}

object DbHealthIndicator {
  val layer = ZLayer.derive[DbHealthIndicator]
}
