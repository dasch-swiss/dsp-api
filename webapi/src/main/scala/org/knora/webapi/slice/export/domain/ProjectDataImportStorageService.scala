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

/**
 * Filesystem storage for project data-graph imports. Mirrors the migration import layout: a dedicated directory
 * under the configured `tmpDatadir` with one subdirectory per task holding the uploaded JSON-LD and the task state.
 */
final class ProjectDataImportStorageService() { self =>

  val dataImportsDir: UIO[Path] =
    AppConfig.config(_.tmpDatadir).map(dir => Path.fromJava(JPath.of(dir)) / "data-imports")

  def dataImportDir(taskId: DataTaskId): UIO[Path] =
    self.dataImportsDir.map(_ / taskId.value).tap(ensureDirExists(_, taskId))

  /** The uploaded knora-api JSON-LD payload of the given task. */
  def dataImportJsonLdPath(taskId: DataTaskId): UIO[Path] = dataImportDir(taskId).map(_ / "data.jsonld")

  /**
   * Creates a temporary directory for the import task, and ensures that it is deleted when the scope is closed.
   *
   * @param taskId the ID of the import task
   * @return the path to the temporary directory
   */
  def tempDataImportScoped(taskId: DataTaskId): URIO[Scope, Path] =
    dataImportDir(taskId).flatMap { baseDir =>
      val tempPath = baseDir / "temp"
      ZIO.acquireRelease(Files.createDirectories(tempPath).logError.orDie.as(tempPath)) { (path: Path) =>
        ZIO.logInfo(s"$taskId: Deleting temp directory $path") *>
          Files.deleteRecursive(path).logError(s"$taskId: Failed deleting temp directory $path").orDie
      }
    }

  private def ensureDirExists(path: Path, taskId: DataTaskId): UIO[Unit] =
    Files.createDirectories(path).logError(s"$taskId: Failed to create directory at $path").orDie
}

object ProjectDataImportStorageService {
  val layer: ULayer[ProjectDataImportStorageService] = ZLayer
    .derive[ProjectDataImportStorageService]
    .tap(env =>
      val service = env.get[ProjectDataImportStorageService]
      for {
        dataImportsDir <- service.dataImportsDir
        _              <- ZIO.logInfo(s"Ensuring project data import storage directory exists at $dataImportsDir")
        _              <- Files.createDirectories(dataImportsDir).unlessZIO(Files.exists(dataImportsDir)).logError.orDie
      } yield (),
    )
}
