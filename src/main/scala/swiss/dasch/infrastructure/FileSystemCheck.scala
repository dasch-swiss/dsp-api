/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.config.Configuration.StorageConfig
import zio.nio.file.{ Files, Path }
import zio.{ UIO, URLayer, ZIO, ZLayer }

import java.io.IOException

trait FileSystemCheck  {
  def checkExpectedFoldersExist(): ZIO[Any, Nothing, Boolean]
  def smokeTest(): ZIO[Any, IOException, Unit]
}
object FileSystemCheck {
  def checkExpectedFoldersExist(): ZIO[FileSystemCheck, Nothing, Boolean] =
    ZIO.serviceWithZIO[FileSystemCheck](_.checkExpectedFoldersExist())
  def smokeTestOrDie(): ZIO[FileSystemCheck, IOException, Unit]           =
    ZIO.serviceWithZIO[FileSystemCheck](_.smokeTest())
}

final case class FileSystemCheckLive(config: StorageConfig) extends FileSystemCheck {
  override def checkExpectedFoldersExist(): ZIO[Any, Nothing, Boolean] =
    Files.isDirectory(config.assetPath) && Files.isDirectory(config.tempPath)

  override def smokeTest(): UIO[Unit] =
    checkExpectedFoldersExist()
      .filterOrDie(identity)(
        new IllegalStateException(
          s"Stopping the start up. Asset ${config.assetPath} and temp ${config.tempPath} directories not found."
        )
      )
      .unit *> ZIO.logInfo(s"Serving from ${config.assetPath} and ${config.tempPath} directories.")
}

object FileSystemCheckLive {
  val layer: URLayer[StorageConfig, FileSystemCheck] = ZLayer.fromFunction(FileSystemCheckLive.apply _)
}
