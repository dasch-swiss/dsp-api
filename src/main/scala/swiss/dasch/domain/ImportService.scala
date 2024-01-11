/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.apache.commons.io.FileUtils
import zio.*
import zio.nio.file.*
import zio.stream.*

import java.util.zip.ZipFile

sealed trait ImportFailed
case class IoError(e: Throwable) extends ImportFailed
case object EmptyFile            extends ImportFailed
case object NoZipFile            extends ImportFailed
case object InvalidChecksums     extends ImportFailed

trait ImportService {
  def importZipStream(shortcode: ProjectShortcode, stream: ZStream[Any, Nothing, Byte]): IO[ImportFailed, Unit]
  def importZipFile(shortcode: ProjectShortcode, tempFile: Path): IO[ImportFailed, Unit]
}
object ImportService {
  def importZipStream(
    shortcode: ProjectShortcode,
    stream: ZStream[Any, Nothing, Byte]
  ): ZIO[ImportService, ImportFailed, Unit] =
    ZIO.serviceWithZIO[ImportService](_.importZipStream(shortcode, stream))
  def importZipFile(shortcode: ProjectShortcode, tempFile: Path): ZIO[ImportService, ImportFailed, Unit] =
    ZIO.serviceWithZIO[ImportService](_.importZipFile(shortcode, tempFile))
}

final case class ImportServiceLive(
  assetService: FileChecksumService,
  assetInfos: AssetInfoService,
  projectService: ProjectService,
  storageService: StorageService
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
          .tap(size => ZIO.logDebug(s"Saved $zipFile with size $size bytes"))
      )
      .logError
      .mapError(IoError(_))

  override def importZipFile(shortcode: ProjectShortcode, zipFile: Path): IO[ImportFailed, Unit] = ZIO.scoped {
    for {
      unzippedFolder <- validateZipFile(shortcode, zipFile)
      _ <- importProject(shortcode, unzippedFolder)
             .logError(s"Error while importing project $shortcode")
             .mapError(IoError(_))
    } yield ()
  }

  private def validateZipFile(shortcode: ProjectShortcode, zipFile: Path): ZIO[Scope, ImportFailed, Path] =
    for {
      _            <- checkIsNotEmptyFile(zipFile)
      _            <- checkIsZipFile(zipFile)
      unzippedPath <- unzipAndVerifyChecksums(shortcode, zipFile)
    } yield unzippedPath
  private def checkIsNotEmptyFile(zipFile: Path): IO[ImportFailed, Unit] =
    ZIO.fail(EmptyFile).whenZIO(Files.size(zipFile).mapBoth(IoError(_), _ == 0)).unit
  private def checkIsZipFile(zipFile: Path): IO[NoZipFile.type, Unit] = ZIO.scoped {
    ZIO.fromAutoCloseable(ZIO.attemptBlockingIO(new ZipFile(zipFile.toFile))).orElseFail(NoZipFile).unit
  }
  private def unzipAndVerifyChecksums(shortcode: ProjectShortcode, zipFile: Path): ZIO[Scope, ImportFailed, Path] =
    for {
      tempDir <- storageService.createTempDirectoryScoped(s"${shortcode}_import").mapError(IoError(_))
      _       <- ZipUtility.unzipFile(zipFile, tempDir).mapError(IoError(_))
      checks <- assetInfos
                  .findAllInPath(tempDir, shortcode)
                  .mapZIOPar(StorageService.maxParallelism())(assetService.verifyChecksum)
                  .runCollect
                  .mapBoth(IoError(_), _.flatten)
      _ <- ZIO.fail(InvalidChecksums).when(checks.exists(!_.checksumMatches))
    } yield tempDir

  private def importProject(shortcode: ProjectShortcode, unzippedFolder: Path): IO[Throwable, Unit] =
    storageService.getProjectDirectory(shortcode).flatMap { projectPath =>
      ZIO.logInfo(s"Importing project $shortcode") *>
        projectService.deleteProject(shortcode) *>
        ZIO.attemptBlockingIO(FileUtils.moveDirectory(unzippedFolder.toFile, projectPath.path.toFile)) *>
        ZIO.logInfo(s"Importing project $shortcode was successful")
    }
}
object ImportServiceLive {
  val layer: ZLayer[
    FileChecksumService with AssetInfoService with ProjectService with StorageService,
    Nothing,
    ImportService
  ] =
    ZLayer.derive[ImportServiceLive]
}
