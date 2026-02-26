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
  val importsDir: UIO[Path] = basePath.map(_ / "imports")

  private def exportDir(taskId: DataTaskId): UIO[Path] =
    self.exportsDir.map(_ / taskId.value).tap(ensureDirExists(_, taskId))

  def importDir(taskId: DataTaskId): UIO[Path] =
    self.importsDir.map(_ / taskId.value).tap(ensureDirExists(_, taskId))

  private def ensureDirExists(path: Path, taskId: DataTaskId): UIO[Unit] =
    Files.createDirectories(path).logError(s"$taskId: Failed to create directory at $path").orDie

  def exportBagItZipPath(taskId: DataTaskId): UIO[Path] = exportDir(taskId).map(_ / "bagit.zip")
  def importBagItZipPath(taskId: DataTaskId): UIO[Path] = importDir(taskId).map(_ / "bagit.zip")

  /**
   * Creates a temporary directory for the export task, and ensures that it is deleted when the scope is closed.
   *
   * @param taskId the ID of the export task
   * @return a tuple containing the path to the temporary directory and the path to the export directory
   */
  def tempExportScoped(taskId: DataTaskId): URIO[Scope, (Path, Path)] =
    exportDir(taskId).flatMap(scopedTempDir(taskId, _))

  /**
   * Creates a temporary directory for the import task, and ensures that it is deleted when the scope is closed.
   *
   * @param taskId the ID of the export task
   * @return a tuple containing the path to the temporary directory and the path to the import bagit zip file
   */
  def tempImportScoped(taskId: DataTaskId): URIO[Scope, (Path, Path)] =
    importDir(taskId)
      .flatMap(scopedTempDir(taskId, _))
      .flatMap { case (tempPath, _) => importBagItZipPath(taskId).map((tempPath, _)) }

  private def scopedTempDir(taskId: DataTaskId, baseDir: Path): URIO[Scope, (Path, Path)] =
    val tempPath = baseDir / "temp"
    for {
      _ <- ensureDirExists(tempPath, taskId)
      _ <- ZIO.acquireRelease(Files.createDirectories(tempPath).logError.orDie.as(tempPath)) { (path: Path) =>
             ZIO.logInfo(s"$taskId: Deleting temp directory $path") *>
               Files.deleteRecursive(path).logError(s"$taskId: Failed deleting temp directory $path").orDie
           }
    } yield (tempPath, baseDir)
}

object ProjectMigrationStorageService {
  val layer: ULayer[ProjectMigrationStorageService] = ZLayer
    .derive[ProjectMigrationStorageService]
    .tap(env =>
      val service = env.get[ProjectMigrationStorageService]
      for {
        importsDir <- service.importsDir
        exportsDir <- service.exportsDir
        _          <- ZIO.logInfo(s"Ensuring project migration storage directories exist at $importsDir and $exportsDir")
        _          <- Files.createDirectories(importsDir).unlessZIO(Files.exists(importsDir)).logError.orDie
        _          <- Files.createDirectories(exportsDir).unlessZIO(Files.exists(exportsDir)).logError.orDie
      } yield (),
    )
}
