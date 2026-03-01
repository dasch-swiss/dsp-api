/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.AugmentedPath.ProjectFolder
import zio.*
import zio.nio.file.*
import zio.stream.*

import java.nio.file.CopyOption
import scala.language.implicitConversions

import org.knora.bagit.BagIt
import org.knora.bagit.BagItError

sealed trait ImportFailed
case class IoError(e: Throwable)                             extends ImportFailed
case class BagItValidationFailed(message: String)            extends ImportFailed
case class ProjectAlreadyExists(shortcode: ProjectShortcode) extends ImportFailed

trait ImportService {
  def importZipStream(shortcode: ProjectShortcode, stream: ZStream[Any, Nothing, Byte]): IO[ImportFailed, Unit]
}
object ImportService {
  def importZipStream(
    shortcode: ProjectShortcode,
    stream: ZStream[Any, Nothing, Byte],
  ): ZIO[ImportService, ImportFailed, Unit] =
    ZIO.serviceWithZIO[ImportService](_.importZipStream(shortcode, stream))
}

final class ImportServiceLive(
  storageService: StorageService,
  projectService: ProjectService,
) extends ImportService {

  def importZipStream(shortcode: ProjectShortcode, stream: ZStream[Any, Nothing, Byte]): IO[ImportFailed, Unit] =
    for {
      projectPath <- storageService.getProjectFolder(shortcode)
      _           <-
        ZIO
          .ifZIO(projectService.exists(shortcode).mapError(IoError(_)))(
            ZIO.fail(ProjectAlreadyExists(shortcode)),
            ZIO.scoped(saveStreamToFile(shortcode, stream).flatMap(importZipFile(projectPath, _))),
          )
    } yield ()

  private def saveStreamToFile(shortcode: ProjectShortcode, stream: ZStream[Any, Nothing, Byte]) =
    storageService
      .createTempDirectoryScoped(s"import-$shortcode", Some("upload"))
      .map(_ / s"import-$shortcode.zip")
      .tap(Files.createFile(_))
      .tap(zipFile =>
        stream
          .run(ZSink.fromFile(zipFile.toFile))
          .tap(size => ZIO.logDebug(s"Saved $zipFile with size $size bytes")),
      )
      .logError
      .mapError(IoError.apply)

  def importZipFile(projectPath: ProjectFolder, zipFile: Path): IO[ImportFailed, Unit] = ZIO.scoped {
    val importPath = projectPath.path
    for {
      result <- BagIt
                  .readAndValidateZip(zipFile)
                  .mapError {
                    case e: java.io.IOException => IoError(e)
                    case e: BagItError          => BagItValidationFailed(e.message)
                  }
      (_, bagRoot) = result
      _           <- Files.deleteRecursive(importPath).whenZIO(Files.exists(importPath)).mapError(IoError(_))
      _           <- Files.move(bagRoot / "data", importPath).mapError(IoError(_))
      _           <- ZIO.logInfo(s"Imported project ${projectPath.shortcode} was successful")
    } yield ()
  }

}

object ImportServiceLive {
  val layer = ZLayer.derive[ImportServiceLive]
}
