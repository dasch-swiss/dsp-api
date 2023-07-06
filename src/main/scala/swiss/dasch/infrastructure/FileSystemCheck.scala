/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.infrastructure

import swiss.dasch.config.Configuration.StorageConfig
import zio.{ UIO, URLayer, ZIO, ZLayer }
import zio.nio.file.{ Files, Path }

import java.io.IOException

trait FileSystemCheck  {
  def checkExpectedFoldersExist(): ZIO[Any, Nothing, Boolean]
  def smokeTest(): ZIO[Any, IOException, Unit]
  def createTempFolders(): ZIO[Any, IOException, Unit]
}
object FileSystemCheck {
  def checkExpectedFoldersExist(): ZIO[FileSystemCheck, Nothing, Boolean] =
    ZIO.serviceWithZIO[FileSystemCheck](_.checkExpectedFoldersExist())
  def smokeTestOrDie(): ZIO[FileSystemCheck, IOException, Unit]           =
    ZIO.serviceWithZIO[FileSystemCheck](_.smokeTest())
  def createTempFolders(): ZIO[FileSystemCheck, IOException, Unit]        =
    ZIO.serviceWithZIO[FileSystemCheck](_.createTempFolders())
}

final case class FileSystemCheckLive(config: StorageConfig) extends FileSystemCheck {
  override def checkExpectedFoldersExist(): ZIO[Any, Nothing, Boolean] =
    Files.isDirectory(config.assetPath) && Files.isDirectory(config.tempPath)

  override def createTempFolders(): ZIO[Any, IOException, Unit] =
    createDirectoryIfNotExist(config.importPath) *> createDirectoryIfNotExist(config.exportPath)

  private def createDirectoryIfNotExist(dir: Path) =
    ZIO.ifZIO(Files.isDirectory(dir))(
      onTrue = ZIO.unit,
      onFalse = ZIO.logInfo(s"Creating $dir") *> Files.createDirectories(dir),
    )

  override def smokeTest(): UIO[Unit] =
    checkExpectedFoldersExist()
      .filterOrDie(identity)(
        new IllegalStateException(
          s"Stopping the start up. Asset ${config.assetPath} and temp ${config.tempPath} directories not found."
        )
      )
      .unit
}

object FileSystemCheckLive {
  val layer: URLayer[StorageConfig, FileSystemCheck] = ZLayer.fromFunction(FileSystemCheckLive.apply _)
}
