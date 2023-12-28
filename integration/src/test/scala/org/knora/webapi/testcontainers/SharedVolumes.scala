package org.knora.webapi.testcontainers

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import zio.nio.file.{Files, Path}
import zio.{ULayer, ZIO, ZLayer}

import java.io.{FileNotFoundException, IOException}
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.nio.file.attribute.PosixFilePermissions.asFileAttribute

object SharedVolumes {

  final case class Images private (hostPath: String) extends AnyVal

  object Images {

    val shortCode001: Shortcode = Shortcode.unsafeFrom("0001")

    private val rwPermissions = asFileAttribute(PosixFilePermissions.fromString("rwxrwxrwx"))

    def copyFileToAssetFolder0001(source: Path): ZIO[Images, IOException, Unit] =
      copyFileToAssetFolder(source, shortCode001)

    def copyFileToAssetFolder(source: Path, shortcode: Shortcode): ZIO[Images, IOException, Unit] =
      ZIO.fail(new FileNotFoundException(s"File not found $source")).whenZIO(Files.notExists(source)).logError *>
        ZIO.serviceWithZIO[SharedVolumes.Images] { imagesVolume =>
          val filename  = source.filename.toString()
          val seg01     = filename.substring(0, 2).toLowerCase()
          val seg02     = filename.substring(2, 4).toLowerCase()
          val targetDir = Path(s"${imagesVolume.hostPath}/${shortcode.value}/$seg01/$seg02")
          Files.createDirectories(targetDir, rwPermissions).logError *>
            Files.copy(source, targetDir / filename, StandardCopyOption.REPLACE_EXISTING)
        }

    val layer: ULayer[Images] =
      ZLayer scoped {
        val tmp = Path(Option(System.getenv("RUNNER_TEMP")).getOrElse(System.getProperty("java.io.tmpdir")))
        for {
          tmpPath <- Files.createTempDirectoryScoped(tmp, None, Seq(rwPermissions))
          _       <- Files.createDirectories(tmp / shortCode001.value, rwPermissions)
          absDir  <- tmpPath.toAbsolutePath.map(_.toString())
        } yield Images(absDir)
      }.orDie
  }
}
