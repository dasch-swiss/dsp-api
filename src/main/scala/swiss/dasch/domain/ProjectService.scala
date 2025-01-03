/*
 * Copyright © 2021 - 2025 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain
import swiss.dasch.domain.AugmentedPath.Conversions.given_Conversion_AugmentedPath_Path
import swiss.dasch.domain.AugmentedPath.ProjectFolder
import zio.nio.file.Files.newDirectoryStream
import zio.nio.file.{Files, Path}
import zio.prelude.ForEachOps
import zio.stream.ZStream
import zio.{IO, *}

import java.io.IOException
import java.sql.SQLException

final case class ProjectService(
  private val assetInfos: AssetInfoService,
  private val storage: StorageService,
  private val checksum: FileChecksumService,
  private val projectRepo: ProjectRepository,
) {

  def listAllProjects(): IO[IOException, Chunk[ProjectFolder]] =
    ZStream
      .fromZIO(storage.getAssetsBaseFolder())
      .flatMap(newDirectoryStream(_))
      .flatMap(dir => ZStream.fromZIOOption(ZIO.fromOption(ProjectFolder.from(dir).toOption)))
      .flatMapPar(StorageService.maxParallelism())(ZStream.succeed(_).filterZIO(projectIsNotEmpty))
      .runCollect

  private def projectIsNotEmpty(projectDir: ProjectFolder) =
    Files.isDirectory(projectDir) &&
      Files
        .walk(projectDir, maxDepth = 3)
        .filterZIO(FileFilters.isNonHiddenRegularFile)
        .runHead
        .map(_.isDefined)

  def findProjects(shortcodes: Iterable[ProjectShortcode]): IO[IOException, Chunk[ProjectFolder]] =
    ZIO.foreach(shortcodes)(findProject).map(_.toChunk.flatten)

  def findProject(shortcode: ProjectShortcode): IO[IOException, Option[ProjectFolder]] =
    storage
      .getProjectFolder(shortcode)
      .flatMap(path => ZIO.whenZIO(Files.isDirectory(path))(ZIO.succeed(path)))

  def findOrCreateProject(shortcode: ProjectShortcode): IO[IOException | SQLException, ProjectFolder] =
    projectRepo.findByShortcode(shortcode).someOrElseZIO(projectRepo.addProject(shortcode)) *>
      storage.createProjectFolder(shortcode)

  def findAssetInfosOfProject(shortcode: ProjectShortcode): ZStream[Any, Throwable, AssetInfo] =
    ZStream
      .fromZIOOption(findProject(shortcode).some)
      .flatMap(assetInfos.findAllInPath(_, shortcode))

  def zipProject(shortcode: ProjectShortcode): Task[Option[Path]] =
    ZIO.logInfo(s"Zipping project $shortcode") *>
      findProject(shortcode).flatMap(_.map(zipProjectPath).getOrElse(ZIO.none)) <*
      ZIO.logInfo(s"Zipping project $shortcode was successful")

  private def zipProjectPath(projectPath: ProjectFolder) =
    storage
      .getTempFolder()
      .map(_ / "zipped")
      .flatMap(targetFolder => ZipUtility.zipFolder(projectPath, targetFolder).map(Some(_)))

  def deleteProject(shortcode: ProjectShortcode): IO[IOException | SQLException, Unit] =
    findProject(shortcode).tapSome { case Some(folder) =>
      val delete: IO[IOException | SQLException, Unit] =
        Files.deleteRecursive(folder) *> projectRepo.deleteByShortcode(shortcode)
      delete
    }.unit

  def addProjectToDb(shortcode: ProjectShortcode): IO[IOException | SQLException, Unit] = {
    val zipped: IO[IOException | SQLException, (Option[ProjectFolder], Option[Project])] =
      (findProject(shortcode) <&> projectRepo.findByShortcode(shortcode))
    zipped.tapSome { case (Some(folder), None) =>
      projectRepo.addProject(folder.shortcode).flatMap(p => ZIO.logInfo(s"Imported $folder as $p to database."))
    }.unit
  }
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
  def deleteProject(shortcode: ProjectShortcode): ZIO[ProjectService, Throwable, Unit] =
    ZIO.serviceWithZIO[ProjectService](_.deleteProject(shortcode))

  val layer = ZLayer.derive[ProjectService]
}
