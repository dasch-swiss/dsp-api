package swiss.dasch.domain

import eu.timepit.refined.api.Refined
import eu.timepit.refined.types.string.NonEmptyString
import swiss.dasch.config.Configuration.StorageConfig
import zio.*
import zio.nio.file.{ Files, Path }

import java.io.IOException

opaque type ProjectShortcode = NonEmptyString
type IiifPrefix              = ProjectShortcode

trait AssetService {
  def listAllProjects(): IO[IOException, Chunk[String]]
}

final case class AssetServiceLive(config: StorageConfig) extends AssetService {

  private val existingProjectDirectories = Files.list(Path(config.assetDir)).filterZIO(Files.isDirectory(_))
  private val existingTempFiles          = Files.list(Path(config.tempDir)).filterZIO(Files.isRegularFile(_))

  override def listAllProjects(): IO[IOException, Chunk[String]] =
    existingProjectDirectories.map(_.filename.toString).runCollect
}

object AssetServiceLive {
  val layer: URLayer[StorageConfig, AssetService] = ZLayer.fromFunction(AssetServiceLive.apply _)
}
