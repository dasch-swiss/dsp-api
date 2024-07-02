/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.config.Configuration.StorageConfig
import zio.nio.file.Files
import zio.{IO, RIO, UIO, URLayer, ZIO, ZLayer}

trait FileSystemHealthIndicator extends HealthIndicator {
  def checkExpectedFoldersExist(): UIO[Boolean]
  def smokeTest(): IO[IllegalStateException, Unit]
  final def health: UIO[(String, Health)] = checkExpectedFoldersExist()
    .map(if (_) Health.up else Health.down)
    .catchAllDefect(_ => ZIO.succeed(Health.down))
    .map(("folders", _))
}
object FileSystemHealthIndicator {
  def checkExpectedFoldersExist(): RIO[FileSystemHealthIndicator, Boolean] =
    ZIO.serviceWithZIO[FileSystemHealthIndicator](_.checkExpectedFoldersExist())
  def smokeTestOrDie(): RIO[FileSystemHealthIndicator, Unit] =
    ZIO.serviceWithZIO[FileSystemHealthIndicator](_.smokeTest()).orDie
}

final case class FileSystemHealthIndicatorLive(config: StorageConfig) extends FileSystemHealthIndicator {
  override def checkExpectedFoldersExist(): UIO[Boolean] =
    Files.isDirectory(config.assetPath) && Files.isDirectory(config.tempPath)

  override def smokeTest(): IO[IllegalStateException, Unit] = {
    val msg =
      s"Stopping the start up. Asset ${config.assetPath} and temp ${config.tempPath} directories not found."
    ZIO
      .fail(new IllegalStateException(msg))
      .whenZIO(checkExpectedFoldersExist().negate) *>
      ZIO.logInfo(s"Serving from ${config.assetPath} and ${config.tempPath} directories.")
  }
}

object FileSystemHealthIndicatorLive {
  val layer: URLayer[StorageConfig, FileSystemHealthIndicator] = ZLayer.derive[FileSystemHealthIndicatorLive]
}
