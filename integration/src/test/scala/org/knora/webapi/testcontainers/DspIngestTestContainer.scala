package org.knora.webapi.testcontainers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.MountableFile
import zio.Task
import zio.ULayer
import zio.ZIO
import zio.nio.file.Path

import org.knora.webapi.slice.admin.domain.model.KnoraProject.Shortcode
import org.knora.webapi.testcontainers.TestContainerOps.StartableOps

final class DspIngestTestContainer extends GenericContainer[DspIngestTestContainer](s"daschswiss/dsp-ingest:latest") {

  def copyFileFromClassPathToAssetDirInContainer(source: Path, shortcode: Shortcode): Task[Unit] = {
    val filename = source.filename.toString
    val seg01    = filename.substring(0, 2).toLowerCase()
    val seg02    = filename.substring(2, 4).toLowerCase()
    val target   = Path(s"${DspIngestTestContainer.assetDir}/$shortcode/$seg01/$seg02/$filename")
    copyFileFromClassPathToContainer(source, target)
  }

  def copyFileFromClassPathToContainer(source: Path, target: Path): Task[Unit] = {
    val mountableFile = MountableFile.forClasspathResource(source.toString(), 777)
    ZIO.attemptBlockingIO(copyFileToContainer(mountableFile, target.toFile.toString)) <*
      ZIO.logInfo(s"Copied $source to $target")
  }
}

object DspIngestTestContainer {

  private val assetDir: Path = Path("/opt/images")

  private val tempDir: Path = Path("/opt/temp")

  def make: DspIngestTestContainer = {
    val port = 3340
    new DspIngestTestContainer()
      .withExposedPorts(port)
      .withEnv("SERVICE_PORT", s"$port")
      .withEnv("SERVICE_LOG_FORMAT", "text")
      .withEnv("JWT_AUDIENCE", s"http://localhost:$port")
      .withEnv("JWT_ISSUER", "0.0.0.0:3333")
      .withEnv("STORAGE_ASSET_DIR", s"$assetDir")
      .withEnv("STORAGE_TEMP_DIR", s"$tempDir")
      .withEnv("JWT_SECRET", "UP 4888, nice 4-8-4 steam engine")
      .withEnv("SIPI_USE_LOCAL_DEV", "false")
  }

  val layer: ULayer[DspIngestTestContainer] = make.toLayer
}
