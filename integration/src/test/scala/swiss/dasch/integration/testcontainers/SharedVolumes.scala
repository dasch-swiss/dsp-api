/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.integration.testcontainers

import zio.nio.file.{Files, Path}
import zio.{ULayer, ZLayer}

import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.PosixFilePermissions.asFileAttribute

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
          absDir  <- tmpPath.toAbsolutePath.map(_.toString())
        } yield Images(absDir)
      }.orDie
  }

  final case class Temp private (hostPath: String) extends AnyVal {
    def asPath: Path = Path.apply(hostPath)
  }
  object Temp {
    val layer: ULayer[Temp] = ZLayer.succeed(Temp(System.getProperty("java.io.tmpdir")))
  }

  val layer: ULayer[Images & Temp] = Images.layer ++ Temp.layer
}
