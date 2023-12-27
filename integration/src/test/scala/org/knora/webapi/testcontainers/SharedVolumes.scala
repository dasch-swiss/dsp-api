package org.knora.webapi.testcontainers

import zio.ULayer
import zio.ZIO
import zio.ZLayer
import zio.nio.file.Files
import zio.nio.file.Path

import java.io.IOException
import java.nio.file.StandardCopyOption

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode

object SharedVolumes {

  final case class Images(hostPath: String) extends AnyVal
  object Images {
    def copyFileToAssetFolder(shortcode: Shortcode, filename: String): ZIO[Images, IOException, Unit] =
      ZIO.serviceWithZIO[SharedVolumes.Images] { img =>
        val seg01  = filename.substring(0, 2).toLowerCase()
        val seg02  = filename.substring(2, 4).toLowerCase()
        val target = Path(s"${img.hostPath}/${shortcode.value}/$seg01/$seg02/$filename")
        val source = Path(s"src/test/resources/sipi/testfiles/$filename")
        Files.createDirectories(target.parent.head) *>
          Files
            .copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            .logError(s"Error copying file $Shortcode, $source")
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
