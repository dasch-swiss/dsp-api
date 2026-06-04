/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.`export`.domain

import zio.*
import zio.nio.file.Files
import zio.nio.file.Path

/** Filesystem helpers shared by the data-task storage services. */
private[domain] object DataTaskStorage {

  def ensureDirExists(path: Path, taskId: DataTaskId): UIO[Unit] =
    Files.createDirectories(path).logError(s"$taskId: Failed to create directory at $path").orDie

  /** Creates `baseDir/temp`, deleting it recursively when the scope closes. */
  def scopedTempDir(taskId: DataTaskId, baseDir: Path): URIO[Scope, Path] = {
    val tempPath = baseDir / "temp"
    ZIO.acquireRelease(Files.createDirectories(tempPath).logError.orDie.as(tempPath)) { (path: Path) =>
      ZIO.logInfo(s"$taskId: Deleting temp directory $path") *>
        Files.deleteRecursive(path).logError(s"$taskId: Failed deleting temp directory $path").orDie
    }
  }
}
