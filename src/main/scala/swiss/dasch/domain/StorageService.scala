/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import org.apache.commons.io.{FileUtils, FilenameUtils}
import swiss.dasch.config.Configuration.StorageConfig
import zio.*
import zio.json.{DecoderOps, EncoderOps, JsonDecoder, JsonEncoder}
import zio.nio.file.{Files, Path}
import zio.stream.ZStream

import java.io.{FileNotFoundException, IOException}
import java.nio.file.StandardOpenOption.*
import java.nio.file.{OpenOption, StandardOpenOption}
import java.text.ParseException
import java.time.format.DateTimeFormatter
import java.time.{ZoneId, ZoneOffset}

trait StorageService {
  def getProjectDirectory(projectShortcode: ProjectShortcode): UIO[Path]
  def getAssetDirectory(asset: AssetRef): UIO[Path]
  def getAssetDirectory(): UIO[Path]
  def createOriginalFileInAssetDir(file: Path, asset: AssetRef): IO[IOException, OriginalFile]
  def getTempDirectory(): UIO[Path]
  def getBulkIngestImportFolder(project: ProjectShortcode): UIO[Path]
  def createTempDirectoryScoped(directoryName: String, prefix: Option[String] = None): ZIO[Scope, IOException, Path]
  def loadJsonFile[A](file: Path)(implicit decoder: JsonDecoder[A]): Task[A]
  def saveJsonFile[A](file: Path, content: A)(implicit encoder: JsonEncoder[A]): Task[Unit]
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
  def createOriginalFileInAssetDir(file: Path, asset: AssetRef): ZIO[StorageService, IOException, OriginalFile] =
    ZIO.serviceWithZIO[StorageService](_.createOriginalFileInAssetDir(file, asset))
  def getTempDirectory(): RIO[StorageService, Path] =
    ZIO.serviceWithZIO[StorageService](_.getTempDirectory())
  def getBulkIngestImportFolder(project: ProjectShortcode): RIO[StorageService, Path] =
    ZIO.serviceWithZIO[StorageService](_.getBulkIngestImportFolder(project))
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

  override def createOriginalFileInAssetDir(file: Path, asset: AssetRef): IO[IOException, OriginalFile] = for {
    _ <- ZIO.logInfo(s"Creating original from $file, $asset")
    _ <- ZIO
           .fail(new FileNotFoundException(s"File $file is not a regular file"))
           .whenZIO(FileFilters.isNonHiddenRegularFile(file).negate)
    assetDir    <- getAssetDirectory(asset).tap(Files.createDirectories(_))
    originalPath = assetDir / s"${asset.id}.${FilenameUtils.getExtension(file.filename.toString)}.orig"
    _           <- Files.copy(file, originalPath)
  } yield OriginalFile.unsafeFrom(originalPath)

  override def getTempDirectory(): UIO[Path] =
    ZIO.succeed(config.tempPath)

  override def getBulkIngestImportFolder(project: ProjectShortcode): UIO[Path] =
    getTempDirectory().map(_ / "import" / project.toString)

  override def getAssetDirectory(): UIO[Path] =
    ZIO.succeed(config.assetPath)

  override def getProjectDirectory(projectShortcode: ProjectShortcode): UIO[Path] =
    getAssetDirectory().map(_ / projectShortcode.toString)

  override def getAssetDirectory(asset: AssetRef): UIO[Path] =
    getProjectDirectory(asset.belongsToProject).map(_ / segments(asset.id))

  private def segments(assetId: AssetId): Path = {
    val assetString = assetId.toString
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
    Files.writeBytes(file, bytes, CREATE, WRITE, TRUNCATE_EXISTING)
  }
}
object StorageServiceLive {
  val layer: URLayer[StorageConfig, StorageService] = ZLayer.derive[StorageServiceLive]
}
