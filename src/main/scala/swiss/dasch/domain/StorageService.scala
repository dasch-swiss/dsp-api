/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.apache.commons.io.FileUtils
import swiss.dasch.config.Configuration.StorageConfig
import zio.*
import zio.json.{DecoderOps, EncoderOps, JsonDecoder, JsonEncoder}
import zio.nio.file.{Files, Path}
import zio.stream.ZStream

import java.io.IOException
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.FileAttribute
import java.nio.file.{CopyOption, DirectoryNotEmptyException, OpenOption, StandardOpenOption}
import java.text.ParseException
import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZoneOffset}

trait StorageService {
  def getProjectDirectory(projectShortcode: ProjectShortcode): UIO[Path]
  def getAssetDirectory(asset: AssetRef): UIO[Path]
  def getAssetDirectory(): UIO[Path]
  def getTempDirectory(): UIO[Path]
  def fileExists(path: Path): IO[IOException, Boolean]
  def createTempDirectoryScoped(directoryName: String, prefix: Option[String] = None): ZIO[Scope, IOException, Path]
  def loadJsonFile[A](file: Path)(implicit decoder: JsonDecoder[A]): Task[A]

  /**
   * Saves an A as json in a file.
   * The file is created if it does not exist, otherwise it is overwritten.
   *
   * @param file the path to the file to save
   * @param content the content to save
   * @param encoder the encoder to use to encode the content
   * @tparam A the type of the content, must have a corresponding [[JsonDecoder]]
   * @return a task that completes when the file is saved
   */
  def saveJsonFile[A](file: Path, content: A)(implicit encoder: JsonEncoder[A]): Task[Unit]
  def copyFile(source: Path, target: Path, copyOption: CopyOption*): IO[IOException, Unit]
  def createDirectories(path: Path, attrs: FileAttribute[_]*): IO[IOException, Unit]

  /**
   * Deletes a file.
   *
   * @param file the path to the file to delete
   */
  def delete(file: Path): IO[IOException, Unit]

  /**
   * Deletes a file tree recursively.
   *
   * @param directory the root of the file tree to delete
   * @return the number of files deleted
   */
  def deleteRecursive(directory: Path): IO[IOException, Long]

  def deleteDirectoryIfEmpty(directory: Path): IO[IOException, Unit]
}

object StorageService {
  def findInPath(
    path: Path,
    filter: FileFilter,
    maxDepth: Int = Int.MaxValue
  ): ZStream[Any, IOException, Path] =
    Files.walk(path, maxDepth).filterZIO(filter)
  def maxParallelism(): Int = 10
  def getProjectDirectory(projectShortcode: ProjectShortcode): RIO[StorageService, Path] =
    ZIO.serviceWithZIO[StorageService](_.getProjectDirectory(projectShortcode))
  def getAssetDirectory(asset: AssetRef): RIO[StorageService, Path] =
    ZIO.serviceWithZIO[StorageService](_.getAssetDirectory(asset))
  def getAssetDirectory(): RIO[StorageService, Path] =
    ZIO.serviceWithZIO[StorageService](_.getAssetDirectory())
  def getTempDirectory(): RIO[StorageService, Path] =
    ZIO.serviceWithZIO[StorageService](_.getTempDirectory())
  def createTempDirectoryScoped(
    directoryName: String,
    prefix: Option[String] = None
  ): ZIO[Scope with StorageService, IOException, Path] =
    ZIO.serviceWithZIO[StorageService](_.createTempDirectoryScoped(directoryName, prefix))
  def loadJsonFile[A](file: Path)(implicit decoder: JsonDecoder[A]): ZIO[StorageService, Throwable, A] =
    ZIO.serviceWithZIO[StorageService](_.loadJsonFile(file)(decoder))
  def saveJsonFile[A](file: Path, content: A)(implicit encoder: JsonEncoder[A]): ZIO[StorageService, Throwable, Unit] =
    ZIO.serviceWithZIO[StorageService](_.saveJsonFile(file, content)(encoder))
}

final case class StorageServiceLive(config: StorageConfig) extends StorageService {

  override def getTempDirectory(): UIO[Path] =
    ZIO.succeed(config.tempPath)

  override def fileExists(path: Path): IO[IOException, Boolean] =
    Files.exists(path)

  override def getAssetDirectory(): UIO[Path] =
    ZIO.succeed(config.assetPath)

  override def getProjectDirectory(projectShortcode: ProjectShortcode): UIO[Path] =
    getAssetDirectory().map(_ / projectShortcode.toString)

  override def getAssetDirectory(asset: AssetRef): UIO[Path] =
    getProjectDirectory(asset.belongsToProject).map(_ / segments(asset.id))

  private def segments(assetId: AssetId): Path = {
    val assetString = assetId.value
    val segment1    = assetString.substring(0, 2)
    val segment2    = assetString.substring(2, 4)
    Path(segment1.toLowerCase, segment2.toLowerCase)
  }

  override def createTempDirectoryScoped(
    directoryName: String,
    prefix: Option[String]
  ): ZIO[Scope, IOException, Path] = {
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss") withZone ZoneId.from(ZoneOffset.UTC)
    Clock.instant.flatMap { now =>
      val basePath      = prefix.map(config.tempPath / _).getOrElse(config.tempPath)
      val directoryPath = basePath / s"${formatter.format(now)}" / directoryName
      ZIO.acquireRelease(Files.createDirectories(directoryPath).as(directoryPath))(path =>
        ZIO.attemptBlockingIO(FileUtils.deleteDirectory(path.toFile)).logError.ignore
      )
    }
  }

  def loadJsonFile[A](file: Path)(implicit decoder: JsonDecoder[A]): Task[A] =
    Files
      .readAllLines(file)
      .flatMap(lines =>
        ZIO
          .fromEither(lines.mkString.fromJson[A])
          .mapError(e => new ParseException(s"Unable to parse $file, reason: $e", -1))
      )

  override def saveJsonFile[A](file: Path, content: A)(implicit encoder: JsonEncoder[A]): Task[Unit] = {
    val bytes = Chunk.fromIterable((content.toJsonPretty + "\n").getBytes)
    Files.writeBytes(file, bytes, WRITE, CREATE, TRUNCATE_EXISTING)
  }

  override def copyFile(source: Path, target: Path, copyOption: CopyOption*): IO[IOException, Unit] =
    Files.copy(source, target, copyOption: _*)

  override def createDirectories(path: Path, attrs: FileAttribute[_]*): IO[IOException, Unit] =
    Files.createDirectories(path, attrs: _*)

  override def delete(path: Path): IO[IOException, Unit] =
    Files.delete(path)

  override def deleteRecursive(path: Path): IO[IOException, Long] =
    Files.deleteRecursive(path)

  override def deleteDirectoryIfEmpty(directory: Path): IO[IOException, Unit] =
    Files
      .delete(directory)
      .catchSome { case _: DirectoryNotEmptyException => ZIO.unit }
      .whenZIO(Files.isDirectory(directory))
      .unit
}

object StorageServiceLive {
  val layer: URLayer[StorageConfig, StorageService] = ZLayer.derive[StorageServiceLive]
}
