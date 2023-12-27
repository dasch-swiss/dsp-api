package org.knora.webapi.testcontainers

import zio.{ULayer, ZLayer}
import zio.nio.file.Files

object SharedVolumes {

  private val someTempDir = Files.createTempFileScoped()
  final case class Images(absolutePath: String) extends AnyVal
  object Images {
    val layer: ULayer[Images] = ZLayer.scoped(someTempDir.flatMap(_.toAbsolutePath).map(_.toString).map(Images.apply)).orDie
  }
}
