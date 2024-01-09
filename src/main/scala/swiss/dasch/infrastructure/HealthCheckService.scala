/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import zio.{UIO, URIO, ZIO, ZLayer}

trait HealthCheckService {
  def check: UIO[Health]
}
object HealthCheckService {
  def check: URIO[HealthCheckService, Health] = ZIO.serviceWithZIO(_.check)
}

final case class HealthCheckServiceLive(filesystemCheck: FileSystemCheck) extends HealthCheckService {
  override def check: UIO[Health] =
    filesystemCheck.checkExpectedFoldersExist().map {
      case true  => Health.up()
      case false => Health.down()
    }
}

object HealthCheckServiceLive {
  val layer = ZLayer.derive[HealthCheckServiceLive]
}
