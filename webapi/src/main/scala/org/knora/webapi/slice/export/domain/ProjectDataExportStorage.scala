package org.knora.webapi.slice.`export`.domain

import java.nio.file.Path as JPath
import org.knora.webapi.config.AppConfig
import zio.Scope
import zio.UIO
import zio.ZIO
import zio.nio.file.Files
import zio.nio.file.Path

final class ProjectDataExportStorage(config: AppConfig) {
  private val basePath: Path = zio.nio.file.Path.fromJava(JPath.of(config.tmpDatadir)) / "migration-exports"

  def exportDir(taskId: DataTaskId): UIO[Path] =
    val path = basePath / taskId.value
    Files.createDirectories(path).unlessZIO(Files.exists(path)).logError.orDie.as(path)

  def tempExportScoped(taskId: DataTaskId): ZIO[Scope, Nothing, (Path, Path)] = for {
    exportPath <- exportDir(taskId)
    tempPath    = exportPath / "temp"
    _          <- ZIO.acquireRelease(Files.createDirectories(tempPath).logError.orDie.as(tempPath)) { path =>
           ZIO.logInfo(s"Deleting temp directory $path") *>
             Files.deleteRecursive(path).logError(s"Failed deleting export directory $path").orDie
         }
  } yield (tempPath, exportPath)
}

object ProjectDataExportStorage {
  val layer = zio.ZLayer.derive[ProjectDataExportStorage]
}
