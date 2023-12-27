package org.knora.webapi.testcontainers

import org.testcontainers.containers.BindMode
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.MountableFile
import zio.Task
import zio.URLayer
import zio.ZIO
import zio.ZLayer
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

  private val assetDir = "/opt/images"
  private val tempDir  = "/opt/temp"

  def make(imagesVolume: SharedVolumes.Images): DspIngestTestContainer = {
    val port = 3340
    val container = new DspIngestTestContainer()
      .withExposedPorts(port)
      .withEnv("SERVICE_PORT", s"$port")
      .withEnv("SERVICE_LOG_FORMAT", "text")
      .withEnv("JWT_AUDIENCE", s"http://localhost:$port")
      .withEnv("JWT_ISSUER", "0.0.0.0:3333")
      .withEnv("STORAGE_ASSET_DIR", assetDir)
      .withEnv("STORAGE_TEMP_DIR", tempDir)
      .withEnv("JWT_SECRET", "UP 4888, nice 4-8-4 steam engine")
      .withEnv("SIPI_USE_LOCAL_DEV", "false")
      .withEnv("JWT_DISABLE_AUTH", "true")
      .withFileSystemBind(imagesVolume.hostPath, assetDir, BindMode.READ_WRITE)
    container.setPortBindings(java.util.List.of(s"$port:$port"))
    container
  }

  private val initDspIngest = ZLayer.fromZIO(
    ZIO.serviceWithZIO[DspIngestTestContainer] { it =>
      ZIO.attemptBlocking(it.execInContainer("mkdir", s"$tempDir")).orDie
    }
  )

  val layer: URLayer[SharedVolumes.Images, DspIngestTestContainer] =
    ZLayer.scoped(ZIO.service[SharedVolumes.Images].flatMap(make(_).toZio)) >+> initDspIngest
}
