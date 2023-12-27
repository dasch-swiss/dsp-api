package org.knora.webapi.testcontainers

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import zio.nio.file.{Files, Path}
import zio.{ULayer, ZIO, ZLayer}

import java.io.{FileNotFoundException, IOException}
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.PosixFilePermissions.asFileAttribute

object SharedVolumes {

  final case class Images(hostPath: String) extends AnyVal
  object Images {
    def copyFileToAssetFolder(shortcode: Shortcode, source: Path): ZIO[Images, IOException, Unit] =
      ZIO.fail(new FileNotFoundException(s"File not found $source")).whenZIO(Files.notExists(source)).logError *>
        ZIO.serviceWithZIO[SharedVolumes.Images] { imagesVolume =>
          val filename  = source.filename.toString()
          val seg01     = filename.substring(0, 2).toLowerCase()
          val seg02     = filename.substring(2, 4).toLowerCase()
          val targetDir = Path(s"${imagesVolume.hostPath}/${shortcode.value}/$seg01/$seg02")
          Files.createDirectories(targetDir, asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx"))).logError *>
            Files.copy(source, targetDir / filename, StandardCopyOption.REPLACE_EXISTING)
        }

    val layer: ULayer[Images] =
      ZLayer
        .scoped(for {
          tmpPath <-
            Files.createTempDirectoryScoped(None, Seq(asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx"))))
          absDir <- tmpPath.toAbsolutePath.map(_.toString())
        } yield Images(absDir))
        .orDie
  }
}
