/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.monitoring

import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.infrastructure.FileSystemCheck
import zio.nio.file.Files
import zio.{ UIO, ULayer, URIO, URLayer, ZIO, ZLayer }

import java.io.IOException

trait HealthCheckService  {
  def check: UIO[Health]
}
object HealthCheckService {
  def check: URIO[HealthCheckService, Health] = ZIO.serviceWithZIO(_.check)
}

final class HealthCheckServiceLive(filesystemCheck: FileSystemCheck) extends HealthCheckService {
  override def check: UIO[Health] =
    filesystemCheck.checkExpectedFoldersExist().map {
      case true  => Health.up()
      case false => Health.down()
    }
}

object HealthCheckServiceLive {
  val layer: URLayer[FileSystemCheck, HealthCheckServiceLive] = ZLayer.fromFunction(HealthCheckServiceLive(_))
}
