/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.apache.commons.io.FileUtils
import org.knora.bagit.BagIt
import org.knora.bagit.BagItError
import swiss.dasch.domain.AugmentedPath.Conversions.given_Conversion_AugmentedPath_Path
import scala.language.implicitConversions
import zio.*
import zio.nio.file.*
import zio.stream.*

sealed trait ImportFailed
case class IoError(e: Throwable)                             extends ImportFailed
case class BagItValidationFailed(message: String)            extends ImportFailed
case class ProjectAlreadyExists(shortcode: ProjectShortcode) extends ImportFailed

trait ImportService {
  def importZipStream(shortcode: ProjectShortcode, stream: ZStream[Any, Nothing, Byte]): IO[ImportFailed, Unit]
  def importZipFile(shortcode: ProjectShortcode, tempFile: Path): IO[ImportFailed, Unit]
}
object ImportService {
  def importZipStream(
    shortcode: ProjectShortcode,
    stream: ZStream[Any, Nothing, Byte],
  ): ZIO[ImportService, ImportFailed, Unit] =
    ZIO.serviceWithZIO[ImportService](_.importZipStream(shortcode, stream))
  def importZipFile(shortcode: ProjectShortcode, tempFile: Path): ZIO[ImportService, ImportFailed, Unit] =
    ZIO.serviceWithZIO[ImportService](_.importZipFile(shortcode, tempFile))
}

final case class ImportServiceLive(
  assetService: FileChecksumService,
  assetInfos: AssetInfoService,
  projectService: ProjectService,
  storageService: StorageService,
) extends ImportService {

  def importZipStream(shortcode: ProjectShortcode, stream: ZStream[Any, Nothing, Byte]): IO[ImportFailed, Unit] =
    ZIO.scoped(saveStreamToFile(shortcode, stream).flatMap(importZipFile(shortcode, _)))

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

  override def importZipFile(shortcode: ProjectShortcode, zipFile: Path): IO[ImportFailed, Unit] = ZIO.scoped {
    for {
      result <- BagIt
                  .readAndValidateZip(zipFile)
                  .mapError {
                    case e: java.io.IOException => IoError(e)
                    case e: BagItError          => BagItValidationFailed(e.message)
                  }
      (_, bagRoot) = result
      dataDir      = bagRoot / "data"
      _           <- importProject(shortcode, dataDir)
    } yield ()
  }

  private def importProject(shortcode: ProjectShortcode, dataDir: Path): IO[ImportFailed, Unit] =
    for {
      projectPath <- storageService.getProjectFolder(shortcode)
      exists      <- Files.isDirectory(projectPath)
      _           <- ZIO.when(exists) {
             for {
               hasFiles <- Files
                             .walk(projectPath)
                             .filterZIO(p => Files.isRegularFile(p))
                             .runHead
                             .map(_.isDefined)
                             .mapError(IoError(_))
               _ <- ZIO.when(hasFiles)(ZIO.fail(ProjectAlreadyExists(shortcode)))
               _ <- Files.deleteRecursive(projectPath).mapError(IoError(_))
             } yield ()
           }
      _ <- ZIO.logInfo(s"Importing project $shortcode")
      _ <- ZIO.attemptBlockingIO(FileUtils.moveDirectory(dataDir.toFile, projectPath.toFile)).mapError(IoError(_))
      _ <- ZIO.logInfo(s"Importing project $shortcode was successful")
    } yield ()
}
object ImportServiceLive {
  val layer = ZLayer.derive[ImportServiceLive]
}
