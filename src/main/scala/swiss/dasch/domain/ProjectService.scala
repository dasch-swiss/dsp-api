/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import eu.timepit.refined.api.{Refined, RefinedTypeOps}
import eu.timepit.refined.string.MatchesRegex
import swiss.dasch.domain.AugmentedPath.ProjectFolder
import zio.*
import zio.json.JsonCodec
import zio.nio.file.Files.{isDirectory, newDirectoryStream}
import zio.nio.file.{Files, Path}
import zio.prelude.ForEachOps
import zio.schema.Schema
import zio.stream.ZStream

import java.io.IOException

type ProjectShortcode = String Refined MatchesRegex["""^\p{XDigit}{4,4}$"""]

object ProjectShortcode extends RefinedTypeOps[ProjectShortcode, String] {
  override def from(str: String): Either[String, ProjectShortcode] = super.from(str.toUpperCase)
  given schema: Schema[ProjectShortcode]                           = Schema[String].transformOrFail(ProjectShortcode.from, id => Right(id.value))
  given codec: JsonCodec[ProjectShortcode]                         = JsonCodec[String].transformOrFail(ProjectShortcode.from, _.value)
}

final case class ProjectService(
  assetInfos: AssetInfoService,
  storage: StorageService,
  checksum: FileChecksumService
) {

  def listAllProjects(): IO[IOException, Chunk[ProjectFolder]] =
    ZStream
      .fromZIO(storage.getAssetDirectory())
      .flatMap(newDirectoryStream(_))
      .flatMap(dir => ZStream.fromZIOOption(ZIO.fromOption(ProjectFolder.from(dir).toOption)))
      .flatMapPar(StorageService.maxParallelism())(ZStream.succeed(_).filterZIO(projectIsNotEmpty))
      .runCollect

  private def projectIsNotEmpty(prj: ProjectFolder) =
    Files.isDirectory(prj.path) &&
      Files
        .walk(prj.path, maxDepth = 3)
        .filterZIO(FileFilters.isNonHiddenRegularFile)
        .runHead
        .map(_.isDefined)

  def findProjects(shortcodes: Iterable[ProjectShortcode]): IO[IOException, Chunk[ProjectFolder]] =
    ZIO.foreach(shortcodes)(findProject).map(_.toChunk.flatten)

  def findProject(shortcode: ProjectShortcode): IO[IOException, Option[ProjectFolder]] =
    storage
      .getProjectDirectory(shortcode)
      .flatMap(path => ZIO.whenZIO(Files.isDirectory(path.path))(ZIO.succeed(path)))

  def findAssetInfosOfProject(shortcode: ProjectShortcode): ZStream[Any, Throwable, AssetInfo] =
    ZStream
      .fromIterableZIO(findProject(shortcode).map(_.map(_.path).toList))
      .flatMap(assetInfos.findAllInPath(_, shortcode))

  def zipProject(shortcode: ProjectShortcode): Task[Option[Path]] =
    ZIO.logInfo(s"Zipping project $shortcode") *>
      findProject(shortcode).flatMap(_.map(zipProjectPath).getOrElse(ZIO.none)) <*
      ZIO.logInfo(s"Zipping project $shortcode was successful")

  private def zipProjectPath(projectPath: ProjectFolder) =
    storage
      .getTempDirectory()
      .map(_ / "zipped")
      .flatMap(targetFolder => ZipUtility.zipFolder(projectPath.path, targetFolder).map(Some(_)))

  def deleteProject(shortcode: ProjectShortcode): IO[IOException, Unit] =
    findProject(shortcode).tapSome { case Some(prj) => Files.deleteRecursive(prj.path) }.unit
}

object ProjectService {
  def listAllProjects(): ZIO[ProjectService, IOException, Chunk[ProjectFolder]] =
    ZIO.serviceWithZIO[ProjectService](_.listAllProjects())
  def findProject(shortcode: ProjectShortcode): ZIO[ProjectService, IOException, Option[ProjectFolder]] =
    ZIO.serviceWithZIO[ProjectService](_.findProject(shortcode))
  def findAssetInfosOfProject(shortcode: ProjectShortcode): ZStream[ProjectService, Throwable, AssetInfo] =
    ZStream.serviceWithStream[ProjectService](_.findAssetInfosOfProject(shortcode))
  def zipProject(shortcode: ProjectShortcode): ZIO[ProjectService, Throwable, Option[Path]] =
    ZIO.serviceWithZIO[ProjectService](_.zipProject(shortcode))
  def deleteProject(shortcode: ProjectShortcode): ZIO[ProjectService, IOException, Unit] =
    ZIO.serviceWithZIO[ProjectService](_.deleteProject(shortcode))

  val layer = ZLayer.derive[ProjectService]
}
