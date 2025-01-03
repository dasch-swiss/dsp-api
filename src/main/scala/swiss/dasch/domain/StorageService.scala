/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.apache.commons.io.FileUtils
import swiss.dasch.config.Configuration.StorageConfig
import swiss.dasch.domain.AugmentedPath.Conversions.given_Conversion_AugmentedPath_Path
import swiss.dasch.domain.AugmentedPath.{AssetFolder, AssetsBaseFolder, ProjectFolder, TempFolder}
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
  def createProjectFolder(projectShortcode: ProjectShortcode): IO[IOException, ProjectFolder]
  def getProjectFolder(projectShortcode: ProjectShortcode): UIO[ProjectFolder]
  def getAssetFolder(asset: AssetRef): UIO[AssetFolder]
  def getAssetsBaseFolder(): UIO[AssetsBaseFolder]
  def getTempFolder(): UIO[TempFolder]
  def getImportFolder(shortcode: ProjectShortcode): UIO[Path] =
    getTempFolder().map(_ / "import" / shortcode.value)
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
   * Deletes a regular file.
   *
   * @param file the path to the file to delete
   *
   * @return A task that completes when the file is deleted.
   *        Fails with an [[IOException]] if the file is not a regular file or does not exist.
   */
  def delete(file: Path): IO[IOException, Unit]

  /**
   * Deletes a file tree recursively.
   *
   * @param path the root of the file tree to delete
   * @return the number of files deleted
   */
  def deleteRecursive(path: Path): IO[IOException, Long]

  def deleteDirectoryIfEmpty(directory: Path): IO[IOException, Unit]

  def calculateSizeInBytes(path: Path): Task[FileSize]
}

object StorageService {
  def findInPath(
    path: Path,
    filter: FileFilter,
    maxDepth: Int = Int.MaxValue,
  ): ZStream[Any, IOException, Path] =
    Files.walk(path, maxDepth).filterZIO(filter)
  def maxParallelism(): Int = 10
  def getProjectFolder(projectShortcode: ProjectShortcode): RIO[StorageService, ProjectFolder] =
    ZIO.serviceWithZIO[StorageService](_.getProjectFolder(projectShortcode))
  def getAssetFolder(asset: AssetRef): RIO[StorageService, AssetFolder] =
    ZIO.serviceWithZIO[StorageService](_.getAssetFolder(asset))
  def getAssetsBaseFolder(): RIO[StorageService, AssetsBaseFolder] =
    ZIO.serviceWithZIO[StorageService](_.getAssetsBaseFolder())
  def getTempFolder(): RIO[StorageService, TempFolder] =
    ZIO.serviceWithZIO[StorageService](_.getTempFolder())
  def createDirectories(path: AugmentedFolder, attrs: FileAttribute[_]*): ZIO[StorageService, IOException, Unit] =
    ZIO.serviceWithZIO[StorageService](_.createDirectories(path, attrs: _*))
  def createTempDirectoryScoped(
    directoryName: String,
    prefix: Option[String] = None,
  ): ZIO[Scope with StorageService, IOException, Path] =
    ZIO.serviceWithZIO[StorageService](_.createTempDirectoryScoped(directoryName, prefix))
  def loadJsonFile[A](file: Path)(implicit decoder: JsonDecoder[A]): ZIO[StorageService, Throwable, A] =
    ZIO.serviceWithZIO[StorageService](_.loadJsonFile(file)(decoder))
  def saveJsonFile[A](file: Path, content: A)(implicit encoder: JsonEncoder[A]): ZIO[StorageService, Throwable, Unit] =
    ZIO.serviceWithZIO[StorageService](_.saveJsonFile(file, content)(encoder))
}

final case class StorageServiceLive(config: StorageConfig) extends StorageService {

  override def getTempFolder(): UIO[TempFolder] =
    ZIO.succeed(TempFolder.from(config))

  override def fileExists(path: Path): IO[IOException, Boolean] =
    Files.exists(path)

  override def getAssetsBaseFolder(): UIO[AssetsBaseFolder] =
    ZIO.succeed(AssetsBaseFolder.from(config))

  override def createProjectFolder(shortcode: ProjectShortcode): IO[IOException, ProjectFolder] =
    getProjectFolder(shortcode).tap(f => createDirectories(f.path))

  override def getProjectFolder(shortcode: ProjectShortcode): UIO[ProjectFolder] =
    getAssetsBaseFolder().map(assetDir => ProjectFolder.unsafeFrom(assetDir / shortcode.toString))

  override def getAssetFolder(ref: AssetRef): UIO[AssetFolder] =
    getProjectFolder(ref.belongsToProject).map(_.assetFolder(ref.id))

  override def createTempDirectoryScoped(
    directoryName: String,
    prefix: Option[String],
  ): ZIO[Scope, IOException, Path] = {
    val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss") withZone ZoneId.from(ZoneOffset.UTC)
    Clock.instant.flatMap { now =>
      val basePath      = prefix.map(config.tempPath / _).getOrElse(config.tempPath)
      val directoryPath = basePath / s"${formatter.format(now)}" / directoryName
      ZIO.acquireRelease(Files.createDirectories(directoryPath).as(directoryPath))(path =>
        ZIO.attemptBlockingIO(FileUtils.deleteDirectory(path.toFile)).logError.ignore,
      )
    }
  }

  def loadJsonFile[A](file: Path)(implicit decoder: JsonDecoder[A]): Task[A] =
    Files
      .readAllLines(file)
      .flatMap(lines =>
        ZIO
          .fromEither(lines.mkString.fromJson[A])
          .mapError(e => new ParseException(s"Unable to parse $file, reason: $e", -1)),
      )

  override def saveJsonFile[A](file: Path, content: A)(implicit encoder: JsonEncoder[A]): Task[Unit] = {
    val bytes = Chunk.fromIterable((content.toJsonPretty + "\n").getBytes)
    Files.writeBytes(file, bytes, WRITE, CREATE, TRUNCATE_EXISTING)
  }

  override def copyFile(source: Path, target: Path, copyOption: CopyOption*): IO[IOException, Unit] =
    Files.copy(source, target, copyOption: _*)

  override def createDirectories(dir: Path, attrs: FileAttribute[_]*): IO[IOException, Unit] =
    Files.createDirectories(dir, attrs: _*)

  override def delete(file: Path): IO[IOException, Unit] =
    Files
      .delete(file)
      .whenZIO(Files.isRegularFile(file))
      .someOrFail(new IOException(s"File $file is not a regular file"))
      .unit

  override def deleteRecursive(path: Path): IO[IOException, Long] =
    Files.deleteRecursive(path)

  override def deleteDirectoryIfEmpty(directory: Path): IO[IOException, Unit] =
    Files
      .delete(directory)
      .catchSome { case _: DirectoryNotEmptyException => ZIO.unit }
      .whenZIO(Files.isDirectory(directory))
      .unit

  /**
   * Returns the size of a non hidden regular file or the total size of all non hidden files in a directory in bytes.
   * @param path the path to the file or directory
   * @return the size in bytes, or 0 if the file is neither a non hidden regular file or a directory
   */
  override def calculateSizeInBytes(path: Path): Task[FileSize] =
    Files.isDirectory(path).flatMap {
      case true  => calculateDirectorySize(path)
      case false => calculateFileSize(path)
    }

  private def calculateDirectorySize(path: Path): ZIO[Any, IOException, FileSize] =
    Files
      .walk(path)
      .filterZIO(p => FileFilters.isNonHiddenRegularFile(p))
      .mapZIO(Files.size)
      .map(FileSize.apply)
      .runFold(FileSize(0L))(_ + _)

  private def calculateFileSize(path: Path): ZIO[Any, IOException, FileSize] =
    FileFilters.isNonHiddenRegularFile.apply(path).flatMap {
      case false => ZIO.succeed(FileSize(0))
      case true  => Files.size(path).map(FileSize.apply)
    }
}

object StorageServiceLive {
  val layer: URLayer[StorageConfig, StorageService] = ZLayer.derive[StorageServiceLive]
}
