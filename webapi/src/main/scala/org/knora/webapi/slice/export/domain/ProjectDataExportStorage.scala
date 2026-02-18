package org.knora.webapi.slice.`export`.domain

import java.nio.file.Path as JPath
import org.knora.webapi.config.AppConfig
import zio.Scope
import zio.ZIO
import zio.nio.file.Files
import zio.nio.file.Path

final class ProjectDataExportStorage(config: AppConfig) {
  private val basePath: Path = zio.nio.file.Path.fromJava(JPath.of(config.tmpDatadir)) / "migration-exports"

  private def exportDir(taskId: DataTaskId): Path = basePath / taskId.value

  def bagItZipPath(taskId: DataTaskId): Path = exportDir(taskId) / "bagit.zip"

  def tempExportScoped(taskId: DataTaskId): ZIO[Scope, Nothing, (Path, Path)] =
    val exportPath = exportDir(taskId)
    for {
      _       <- Files.createDirectories(exportPath).unlessZIO(Files.exists(exportPath)).logError.orDie
      tempPath = exportPath / "temp"
      _       <- ZIO.acquireRelease(Files.createDirectories(tempPath).logError.orDie.as(tempPath)) { path =>
             ZIO.logInfo(s"Deleting temp directory $path") *>
               Files.deleteRecursive(path).logError(s"Failed deleting export directory $path").orDie
           }
    } yield (tempPath, exportPath)
}

object ProjectDataExportStorage {
  val layer = zio.ZLayer.derive[ProjectDataExportStorage]
}
