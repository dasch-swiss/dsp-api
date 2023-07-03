/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.admin.domain.service

import zio._
import zio.nio.file._

import dsp.valueobjects.Project.ShortCode
import org.knora.webapi.config.AppConfig
import org.knora.webapi.slice.admin.domain.model.KnoraProject
import org.knora.webapi.slice.admin.domain.service.ProjectExportStorageService.exportFileEnding

case class ProjectExportInfo(projectShortname: String, path: Path)

trait ProjectExportStorageService {

  def projectExportDirectory(project: KnoraProject): Path
  def projectExportDirectory(shortCode: ShortCode): Path

  def projectExportFilename(project: KnoraProject): String =
    s"${project.shortcode}-export${ProjectExportStorageService.exportFileEnding}"

  def projectExportFilename(shortCode: ShortCode): String =
    s"${shortCode.value}-export${ProjectExportStorageService.exportFileEnding}"

  def projectExportFullPath(project: KnoraProject): Path =
    projectExportDirectory(project) / projectExportFilename(project)

  def projectExportFullPath(shortCode: ShortCode): Path =
    projectExportDirectory(shortCode) / projectExportFilename(shortCode)

  def listExports(): Task[Chunk[ProjectExportInfo]]

  def trigFilename(project: KnoraProject): String    = trigFilename(project.shortcode)
  def trigFilename(shortCode: ShortCode): String     = trigFilename(shortCode.value)
  def trigFilename(projectShortcode: String): String = s"$projectShortcode.trig"

}

object ProjectExportStorageService {
  val exportFileEnding        = ".zip"
  val assetsDirectoryInExport = "assets"
}

final case class ProjectExportStorageServiceLive(exportDirectory: Path) extends ProjectExportStorageService {
  override def projectExportDirectory(project: KnoraProject): Path = exportDirectory / s"${project.shortcode}"
  override def projectExportDirectory(shortCode: ShortCode): Path  = exportDirectory / shortCode.value
  override def listExports(): Task[Chunk[ProjectExportInfo]] =
    Files
      .list(exportDirectory)
      .filterZIO(Files.isDirectory(_))
      .flatMap(projectDirectory =>
        Files
          .list(projectDirectory)
          .filterZIO(Files.isRegularFile(_))
          .filter(_.filename.toString().endsWith(exportFileEnding))
          .map(file => (projectDirectory, file))
      )
      .map(toProjectExportInfo)
      .runCollect

  private def toProjectExportInfo(projectDirAndFile: (Path, Path)) = {
    val (projectDirectory, exportFile) = projectDirAndFile
    val projectShortname               = projectDirectory.filename.toString()
    ProjectExportInfo(projectShortname, exportFile)
  }
}

object ProjectExportStorageServiceLive {

  val layer: URLayer[AppConfig, ProjectExportStorageServiceLive] = ZLayer.fromZIO(
    for {
      exportDirectory <- ZIO.serviceWith[AppConfig](_.tmpDataDirPath / "project-export")
      _               <- Files.createDirectories(exportDirectory).orDie
    } yield ProjectExportStorageServiceLive(exportDirectory)
  )
}
