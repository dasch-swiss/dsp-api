/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.refineV
import eu.timepit.refined.string.MatchesRegex
import eu.timepit.refined.types.string.NonEmptyString
import org.apache.commons.io.FileUtils
import swiss.dasch.config.Configuration.StorageConfig
import zio.*
import zio.json.{ DeriveJsonCodec, DeriveJsonDecoder, DeriveJsonEncoder, JsonCodec, JsonDecoder, JsonEncoder }
import zio.nio.file.Files.{ delete, deleteIfExists, isDirectory, newDirectoryStream }
import zio.nio.file.{ Files, Path }
import zio.stream.{ ZSink, ZStream }

import java.io.{ FileNotFoundException, IOException }

opaque type ProjectShortcode = String Refined MatchesRegex["""^\p{XDigit}{4,4}$"""]
type IiifPrefix              = ProjectShortcode

object ProjectShortcode {
  def make(shortcode: String): Either[String, ProjectShortcode] = refineV(shortcode.toUpperCase)
}

trait ProjectService {
  def listAllProjects(): IO[IOException, Chunk[ProjectShortcode]]
  def findProject(shortcode: ProjectShortcode): IO[IOException, Option[Path]]
  def deleteProject(shortcode: ProjectShortcode): IO[IOException, Unit]
  def findAssetInfosOfProject(shortcode: ProjectShortcode): ZStream[Any, Throwable, AssetInfo]
  def zipProject(shortcode: ProjectShortcode): Task[Option[Path]]
}

object ProjectService {
  def listAllProjects(): ZIO[ProjectService, IOException, Chunk[ProjectShortcode]]                        =
    ZIO.serviceWithZIO[ProjectService](_.listAllProjects())
  def findProject(shortcode: ProjectShortcode): ZIO[ProjectService, IOException, Option[Path]]            =
    ZIO.serviceWithZIO[ProjectService](_.findProject(shortcode))
  def findAssetInfosOfProject(shortcode: ProjectShortcode): ZStream[ProjectService, Throwable, AssetInfo] =
    ZStream.serviceWithStream[ProjectService](_.findAssetInfosOfProject(shortcode))
  def zipProject(shortcode: ProjectShortcode): ZIO[ProjectService, Throwable, Option[Path]]               =
    ZIO.serviceWithZIO[ProjectService](_.zipProject(shortcode))
  def deleteProject(shortcode: ProjectShortcode): ZIO[ProjectService, IOException, Unit]                  =
    ZIO.serviceWithZIO[ProjectService](_.deleteProject(shortcode))
}

final case class ProjectServiceLive(
    assetInfos: AssetInfoService,
    storage: StorageService,
    checksum: FileChecksumService,
  ) extends ProjectService {

  override def listAllProjects(): IO[IOException, Chunk[ProjectShortcode]] =
    ZStream
      .fromZIO(storage.getAssetDirectory())
      .flatMap(newDirectoryStream(_))
      .filterZIO(directoryContainsNonHiddenRegularFile)
      .runCollect
      .map(toProjectShortcodes)

  private def directoryContainsNonHiddenRegularFile(path: Path) =
    Files.isDirectory(path) &&
    Files
      .walk(path)
      .filterZIO(it => Files.isRegularFile(it) && Files.isHidden(it).negate)
      .runHead
      .map(_.isDefined)

  private val toProjectShortcodes: Chunk[Path] => Chunk[ProjectShortcode] =
    _.map(_.filename.toString).sorted.flatMap(ProjectShortcode.make(_).toOption)

  override def findProject(shortcode: ProjectShortcode): IO[IOException, Option[Path]] =
    storage.getProjectDirectory(shortcode).flatMap(path => ZIO.whenZIO(Files.isDirectory(path))(ZIO.succeed(path)))

  override def findAssetInfosOfProject(shortcode: ProjectShortcode): ZStream[Any, Throwable, AssetInfo] =
    ZStream.fromIterableZIO(findProject(shortcode).map(_.toList)).flatMap(assetInfos.findAllInPath(_, shortcode))

  override def zipProject(shortcode: ProjectShortcode): Task[Option[Path]] =
    ZIO.logInfo(s"Zipping project $shortcode") *>
      findProject(shortcode).flatMap(_.map(zipProjectPath).getOrElse(ZIO.none)) <*
      ZIO.logInfo(s"Zipping project $shortcode was successful")

  private def zipProjectPath(projectPath: Path) =
    storage
      .getTempDirectory()
      .map(_ / "zipped")
      .flatMap(targetFolder => ZipUtility.zipFolder(projectPath, targetFolder).map(Some(_)))

  override def deleteProject(shortcode: ProjectShortcode): IO[IOException, Unit] =
    storage
      .getProjectDirectory(shortcode)
      .flatMap { projectDir =>
        ZIO.whenZIO(Files.isDirectory(projectDir))(ZIO.attemptBlockingIO(FileUtils.deleteDirectory(projectDir.toFile)))
      }
      .unit
}

object ProjectServiceLive {
  val layer: ZLayer[AssetInfoService with StorageService with FileChecksumService, Nothing, ProjectService] =
    ZLayer.fromFunction(ProjectServiceLive.apply _)
}
