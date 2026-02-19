/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.Scope
import zio.URIO
import zio.ZIO
import zio.nio.file.Files
import zio.nio.file.Path

import java.nio.file.Path as JPath

import org.knora.webapi.config.AppConfig

final class ProjectMigrationStorageService(config: AppConfig) { self =>
  private val basePath: Path = Path.fromJava(JPath.of(config.tmpDatadir)) / "migration"

  val exportsDir: Path = basePath / "exports"

  private def exportDir(taskId: DataTaskId): Path = self.exportsDir / taskId.value

  def bagItZipPath(taskId: DataTaskId): Path = exportDir(taskId) / "bagit.zip"

  /**
   * Creates a temporary directory for the export task, and ensures that it is deleted when the scope is closed.
   *
   * @param taskId the ID of the export task
   * @return a tuple containing the path to the temporary directory and the path to the export directory
   */
  def tempExportScoped(taskId: DataTaskId): URIO[Scope, (Path, Path)] =
    val exportPath = exportDir(taskId)
    for {
      _       <- Files.createDirectories(exportPath).unlessZIO(Files.exists(exportPath)).logError.orDie
      tempPath = exportPath / "temp"
      _       <- ZIO.acquireRelease(Files.createDirectories(tempPath).logError.orDie.as(tempPath)) { path =>
             ZIO.logInfo(s"$taskId: Deleting temp directory $path") *>
               Files.deleteRecursive(path).logError(s"$taskId: Failed deleting export directory $path").orDie
           }
    } yield (tempPath, exportPath)
}

object ProjectMigrationStorageService {
  val layer = zio.ZLayer.derive[ProjectMigrationStorageService]
}
