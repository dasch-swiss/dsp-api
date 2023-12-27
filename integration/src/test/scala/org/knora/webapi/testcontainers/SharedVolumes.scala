package org.knora.webapi.testcontainers

import zio.ULayer
import zio.ZLayer
import zio.nio.file.Files

object SharedVolumes {

  final case class Images(hostPath: String) extends AnyVal
  object Images {
    val layer: ULayer[Images] =
      ZLayer
        .scoped(for {
          tmpPath <- Files.createTempDirectoryScoped(None, List.empty)
          absDir  <- tmpPath.toAbsolutePath.map(_.toString())
        } yield Images(absDir))
        .orDie
  }
}
