/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.api.monitoring

import swiss.dasch.config.Configuration.StorageConfig
import zio.nio.file.Files
import zio.{ UIO, ULayer, URIO, URLayer, ZIO, ZLayer }

trait HealthCheckService  {
  def check: UIO[Health]
}
object HealthCheckService {
  def check: URIO[HealthCheckService, Health] = ZIO.serviceWithZIO(_.check)
}

final class HealthCheckServiceLive(config: StorageConfig) extends HealthCheckService {
  override def check: UIO[Health] =
    (Files.isDirectory(config.assetPath) && Files.isDirectory(config.tempPath)).map {
      case true  => Health.up()
      case false => Health.down()
    }
}

object HealthCheckServiceLive {
  val layer: URLayer[StorageConfig, HealthCheckService] = ZLayer.fromFunction(HealthCheckServiceLive(_))
}
