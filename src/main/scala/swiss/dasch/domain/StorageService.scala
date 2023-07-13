/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.types.string.NonEmptyString
import org.apache.commons.io.FileUtils
import swiss.dasch.config.Configuration.StorageConfig
import zio.*
import zio.json.{ DecoderOps, DeriveJsonCodec, JsonCodec, JsonDecoder }
import zio.nio.file.{ Files, Path }
import zio.prelude.Validation

import java.io.IOException
import java.time.format.DateTimeFormatter
import java.time.{ ZoneId, ZoneOffset }
import java.util.UUID

trait StorageService  {
  def getProjectDirectory(projectShortcode: ProjectShortcode): UIO[Path]
  def getAssetDirectory(asset: Asset): UIO[Path]
  def getAssetDirectory(): UIO[Path]
  def getTempDirectory(): UIO[Path]
  def createTempDirectoryScoped(directoryName: String, prefix: Option[String] = None): ZIO[Scope, IOException, Path]
  def loadJsonFile[A](file: Path)(implicit decoder: JsonDecoder[A]): Task[A]
}
object StorageService {
  def maxParallelism(): Int                                                                            = 10
  def getProjectDirectory(projectShortcode: ProjectShortcode): RIO[StorageService, Path]               =
    ZIO.serviceWithZIO[StorageService](_.getProjectDirectory(projectShortcode))
  def getAssetDirectory(asset: Asset): RIO[StorageService, Path]                                       =
    ZIO.serviceWithZIO[StorageService](_.getAssetDirectory(asset))
  def getAssetDirectory(): RIO[StorageService, Path]                                                   =
    ZIO.serviceWithZIO[StorageService](_.getAssetDirectory())
  def getTempDirectory(): RIO[StorageService, Path]                                                    =
    ZIO.serviceWithZIO[StorageService](_.getTempDirectory())
  def createTempDirectoryScoped(directoryName: String, prefix: Option[String] = None)
      : ZIO[Scope with StorageService, IOException, Path] =
    ZIO.serviceWithZIO[StorageService](_.createTempDirectoryScoped(directoryName, prefix))
  def loadJsonFile[A](file: Path)(implicit decoder: JsonDecoder[A]): ZIO[StorageService, Throwable, A] =
    ZIO.serviceWithZIO[StorageService](_.loadJsonFile(file)(decoder))
}

final case class StorageServiceLive(config: StorageConfig) extends StorageService {
  override def getTempDirectory(): UIO[Path]                                      =
    ZIO.succeed(config.tempPath)
  override def getAssetDirectory(): UIO[Path]                                     =
    ZIO.succeed(config.assetPath)
  override def getProjectDirectory(projectShortcode: ProjectShortcode): UIO[Path] =
    getAssetDirectory().map(_ / projectShortcode.toString)
  override def getAssetDirectory(asset: Asset): UIO[Path]                         =
    getProjectDirectory(asset.belongsToProject).map(_ / segments(asset.id))

  private def segments(assetId: AssetId): Path = {
    val assetString = assetId.toString
    val segment1    = assetString.substring(0, 2)
    val segment2    = assetString.substring(2, 4)
    Path(segment1.toLowerCase, segment2.toLowerCase)
  }

  override def createTempDirectoryScoped(directoryName: String, prefix: Option[String])
      : ZIO[Scope, IOException, Path] = {
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
          .mapError(e => new IllegalArgumentException(s"Unable to parse $file, reason: $e"))
      )
}
object StorageServiceLive {
  val layer: URLayer[StorageConfig, StorageService] = ZLayer.fromFunction(StorageServiceLive.apply _)
}
