/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.*
import zio.nio.file.Files
import zio.nio.file.Path

import java.nio.file.Path as JPath
import org.knora.webapi.config.AppConfig

final class ProjectMigrationStorageService() { self =>
  private val basePath: UIO[Path] =
    AppConfig.config(_.tmpDatadir).map(dir => Path.fromJava(JPath.of(dir)) / "migration")

  val exportsDir: UIO[Path] = basePath.map(_ / "exports")

  private def exportDir(taskId: DataTaskId): UIO[Path] = self.exportsDir.map(_ / taskId.value)

  def bagItZipPath(taskId: DataTaskId): UIO[Path] = exportDir(taskId).map(_ / "bagit.zip")

  /**
   * Creates a temporary directory for the export task, and ensures that it is deleted when the scope is closed.
   *
   * @param taskId the ID of the export task
   * @return a tuple containing the path to the temporary directory and the path to the export directory
   */
  def tempExportScoped(taskId: DataTaskId): URIO[Scope, (Path, Path)] =
    for {
      exportPath <- exportDir(taskId)
      _          <- Files.createDirectories(exportPath).unlessZIO(Files.exists(exportPath)).logError.orDie
      tempPath    = exportPath / "temp"
      _          <- ZIO.acquireRelease(Files.createDirectories(tempPath).logError.orDie.as(tempPath)) { path =>
             ZIO.logInfo(s"$taskId: Deleting temp directory $path") *>
               Files.deleteRecursive(path).logError(s"$taskId: Failed deleting export directory $path").orDie
           }
    } yield (tempPath, exportPath)
}

object ProjectMigrationStorageService {
  val layer = zio.ZLayer.derive[ProjectMigrationStorageService]
}
