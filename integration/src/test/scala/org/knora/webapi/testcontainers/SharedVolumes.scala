package org.knora.webapi.testcontainers

import zio.ULayer
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Files
import zio.nio.file.Path

import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.StandardCopyOption

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

object SharedVolumes {

  final case class Images(hostPath: String) extends AnyVal
  object Images {
    def copyFileToAssetFolder(shortcode: Shortcode, file: Path): ZIO[Images, IOException, Unit] =
      ZIO.serviceWithZIO[SharedVolumes.Images] { imagesVolume =>
        val filename = file.filename.toString()
        val seg01    = filename.substring(0, 2).toLowerCase()
        val seg02    = filename.substring(2, 4).toLowerCase()
        val target   = Path(s"${imagesVolume.hostPath}/${shortcode.value}/$seg01/$seg02/$filename")
        val source   = Path(this.getClass.getClassLoader.getResource(file.toString()).toURI)
        Files.createDirectories(target.parent.head) *>
          Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
      }

    val layer: ULayer[Images] =
      ZLayer
        .scoped(for {
          tmpPath <- Files.createTempDirectoryScoped(None, List.empty)
          absDir  <- tmpPath.toAbsolutePath.map(_.toString())
        } yield Images(absDir))
        .orDie
  }
}
