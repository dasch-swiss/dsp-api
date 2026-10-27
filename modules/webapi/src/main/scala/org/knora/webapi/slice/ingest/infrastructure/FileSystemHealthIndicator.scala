/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.infrastructure

import zio.IO
import zio.RIO
import zio.UIO
import zio.URIO
import zio.URLayer
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Files

import org.knora.webapi.slice.ingest.config.StorageConfig

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

  /** A missing ingest mount must not stop dsp-api: it is logged, and `/ingest/health` reports it. */
  def smokeTestOrLogError(): URIO[FileSystemHealthIndicator, Unit] =
    ZIO.serviceWithZIO[FileSystemHealthIndicator](_.smokeTest()).catchAll(e => ZIO.logError(e.getMessage))
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
