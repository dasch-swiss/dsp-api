/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.config.Configuration.StorageConfig
import zio.*
import zio.json.{ DeriveJsonCodec, DeriveJsonDecoder, DeriveJsonEncoder, JsonCodec, JsonDecoder, JsonEncoder }
import zio.nio.file.Files.{ delete, deleteIfExists, isDirectory, newDirectoryStream }
import zio.nio.file.{ Files, Path }
import zio.stream.{ ZSink, ZStream }

import java.io.IOException

opaque type ProjectShortcode = String Refined MatchesRegex["""^\p{XDigit}{4,4}$"""]
type IiifPrefix              = ProjectShortcode

object ProjectShortcode {
  def make(shortcode: String): Either[String, ProjectShortcode] = refineV(shortcode.toUpperCase)
}

final case class DotInfoFileContent(
    internalFilename: String,
    originalInternalFilename: String,
    originalFilename: String,
    checksumOriginal: String,
    checksumDerivative: String,
  )

object DotInfoFileContent {
  implicit val codec: JsonCodec[DotInfoFileContent] = DeriveJsonCodec.gen[DotInfoFileContent]
}

trait AssetService {
  def listAllProjects(): IO[IOException, Chunk[ProjectShortcode]]
  def findProject(shortcode: ProjectShortcode): IO[IOException, Option[Path]]
  def zipProject(shortcode: ProjectShortcode): Task[Option[Path]]
  def importProject(shortcode: ProjectShortcode, zipFile: Path): IO[Throwable, Unit]
}

object AssetService {
  def listAllProjects(): ZIO[AssetService, IOException, Chunk[ProjectShortcode]] =
    ZIO.serviceWithZIO[AssetService](_.listAllProjects())

  def findProject(shortcode: ProjectShortcode): ZIO[AssetService, IOException, Option[Path]] =
    ZIO.serviceWithZIO[AssetService](_.findProject(shortcode))

  def zipProject(shortcode: ProjectShortcode): ZIO[AssetService, Throwable, Option[Path]] =
    ZIO.serviceWithZIO[AssetService](_.zipProject(shortcode))

  def importProject(shortcode: ProjectShortcode, zipFile: Path): ZIO[AssetService, Throwable, Unit] =
    ZIO.serviceWithZIO[AssetService](_.importProject(shortcode, zipFile))
}

final case class AssetServiceLive(config: StorageConfig) extends AssetService {

  private val existingProjectDirectories               = Files.list(config.assetPath).filterZIO(Files.isDirectory(_))
  private val existingTempFiles                        = Files.list(config.tempPath).filterZIO(Files.isRegularFile(_))
  private def projectPath(shortcode: ProjectShortcode) = config.assetPath / shortcode.toString

  override def listAllProjects(): IO[IOException, Chunk[ProjectShortcode]] =
    existingProjectDirectories
      .filterZIO(directoryTreeContainsNonHiddenRegularFiles)
      .map(_.filename.toString)
      .runCollect
      .map(_.sorted.flatMap(ProjectShortcode.make(_).toOption))

  private def directoryTreeContainsNonHiddenRegularFiles(path: Path) =
    Files.walk(path).findZIO(it => Files.isRegularFile(it) && Files.isHidden(it).map(!_)).runCollect.map(_.nonEmpty)

  override def findProject(shortcode: ProjectShortcode): IO[IOException, Option[Path]] =
    existingProjectDirectories.filter(_.filename.toString == shortcode.toString).runHead

  override def zipProject(shortcode: ProjectShortcode): Task[Option[Path]] =
    ZIO.logInfo(s"Zipping project $shortcode") *>
      findProject(shortcode).flatMap(_.map(zipProjectPath(_, shortcode)).getOrElse(ZIO.none)) <*
      ZIO.logInfo(s"Zipping project $shortcode was successful")

  private def zipProjectPath(projectPath: Path, shortcode: ProjectShortcode) = {
    val targetFolder = config.tempPath / "zipped"
    ZipUtility.zipFolder(projectPath, targetFolder).map(Some(_))
  }

  override def importProject(shortcode: ProjectShortcode, zipFile: Path): IO[Throwable, Unit] = {
    val targetFolder = config.assetPath / shortcode.toString
    ZIO.logInfo(s"Importing project $shortcode") *>
      deleteExistingProjectFiles(shortcode) *>
      ZipUtility.unzipFile(zipFile, targetFolder) *>
      Files.createDirectories(targetFolder) *>
      ZIO.logInfo(s"Importing project $shortcode was successful")
  }

  private def deleteExistingProjectFiles(shortcode: ProjectShortcode): IO[IOException, Unit] =
    deleteRecursive(projectPath(shortcode))
      .whenZIO(Files.exists(projectPath(shortcode)))
      .unit

  // The zio.nio.file.Files.deleteRecursive function has a bug in 2.0.1
  // https://github.com/zio/zio-nio/pull/588/files <- this PR fixes it
  // This is a workaround until the bug is fixed:
  private def deleteRecursive(path: Path)(implicit trace: Trace): ZIO[Any, IOException, Long] =
    newDirectoryStream(path)
      .mapZIO { p =>
        for {
          deletedInSubDirectory <- deleteRecursive(p).whenZIO(isDirectory(p)).map(_.getOrElse(0L))
          deletedFile           <- deleteIfExists(p).map(if (_) 1 else 0)
        } yield deletedInSubDirectory + deletedFile
      }
      .run(ZSink.sum) <* delete(path)
}

object AssetServiceLive {
  val layer: URLayer[StorageConfig, AssetService] = ZLayer.fromFunction(AssetServiceLive.apply _)
}
