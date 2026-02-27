/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.domain

import swiss.dasch.domain.AugmentedPath.Conversions.given_Conversion_AugmentedPath_Path
import swiss.dasch.domain.AugmentedPath.ProjectFolder
import zio.*
import zio.nio.file.Files
import zio.nio.file.Files.newDirectoryStream
import zio.nio.file.Path
import zio.prelude.ForEachOps
import zio.stream.ZStream

import java.io.IOException
import java.sql.SQLException
import scala.language.implicitConversions

import org.knora.bagit.BagIt
import org.knora.bagit.domain.BagInfo
import org.knora.bagit.domain.PayloadEntry

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
    ZIO.logInfo(s"Exporting project $shortcode as BagIt") *>
      findProject(shortcode).flatMap(_.map(exportProjectAsBagIt(shortcode, _)).getOrElse(ZIO.none)) <*
      ZIO.logInfo(s"Exporting project $shortcode as BagIt was successful")

  private def exportProjectAsBagIt(shortcode: ProjectShortcode, projectPath: ProjectFolder) =
    for {
      targetFolder <- storage.getTempFolder().map(_ / "zipped")
      _            <- Files.createDirectories(targetFolder).whenZIO(Files.notExists(targetFolder))
      outputPath    = targetFolder / s"${projectPath.path.filename.toString}.zip"
      bagInfo       = BagInfo(
                  sourceOrganization = Some("DaSCH Service Platform"),
                  externalIdentifier = Some(shortcode.value),
                  additionalFields = List("Ingest-Export-Version" -> "1"),
                )
      payloadEntries = List(PayloadEntry.Directory(prefix = "", sourcePath = projectPath.path))
      resultPath    <- BagIt.create(payloadEntries, outputPath, bagInfo = Some(bagInfo))
    } yield Some(resultPath)

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
