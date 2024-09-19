/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.testcontainers

import zio.ULayer
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Files
import zio.nio.file.Path

import java.io.FileNotFoundException
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.PosixFilePermissions.asFileAttribute

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

object SharedVolumes {

  type Volumes = Images & Temp

  final case class Images private (hostPath: String) extends AnyVal

  object Images {

    private val rwPermissions = asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx"))

    val layer: ULayer[Images] =
      ZLayer scoped {
        val tmp = Path(Option(System.getenv("RUNNER_TEMP")).getOrElse(System.getProperty("java.io.tmpdir")))
        for {
          tmpPath <- Files.createTempDirectoryScoped(tmp, None, Seq(rwPermissions))
          _       <- createAssets(tmpPath).tap(n => ZIO.logInfo(s"Created $n assets")).logError
          absDir  <- tmpPath.toAbsolutePath.map(_.toString())
        } yield Images(absDir)
      }.orDie

    private def createAssets(assetDir: Path) = {
      val testfilesDir = Path(getClass.getResource("/sipi/testfiles").toURI)
      val shortcode    = Shortcode.unsafeFrom("0001")
      Files
        .walk(testfilesDir, 1)
        .filterZIO(p => Files.isRegularFile(p) && Files.isHidden(p).negate)
        .mapZIO(p => copyFileToAssetFolder(assetDir, p, shortcode).as(1))
        .runSum
    }

    private def copyFileToAssetFolder(
      assetDir: Path,
      source: Path,
      shortcode: Shortcode,
    ) =
      ZIO.fail(new FileNotFoundException(s"File not found $source")).whenZIO(Files.notExists(source)).logError *> {
        val filename  = source.filename.toString()
        val seg01     = filename.substring(0, 2).toLowerCase()
        val seg02     = filename.substring(2, 4).toLowerCase()
        val targetDir = assetDir / shortcode.value / seg01 / seg02
        Files.createDirectories(targetDir, rwPermissions).logError *>
          Files.copy(source, targetDir / filename, StandardCopyOption.REPLACE_EXISTING)
      }
  }

  final case class Temp private (hostPath: String) extends AnyVal
  object Temp {
    val layer: ULayer[Temp] = ZLayer.succeed(Temp(System.getProperty("java.io.tmpdir")))
  }

  val layer: ULayer[Images & Temp] = Images.layer ++ Temp.layer
}
