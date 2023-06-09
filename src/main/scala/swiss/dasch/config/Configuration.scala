package swiss.dasch.config

import com.typesafe.config.ConfigFactory
import zio.*
import zio.config.*
import zio.config.ConfigDescriptor.*
import zio.config.typesafe.TypesafeConfigSource
import zio.nio.file.{ Files, Path }

object Configuration {

  final case class ApiConfig(host: String, port: Int)

  object ApiConfig {

    private val serverConfigDescription =
      nested("api") {
        string("host") <*>
          int("port")
      }.to[ApiConfig]

    private[Configuration] val layer = ZLayer(
      read(
        serverConfigDescription.from(
          TypesafeConfigSource.fromTypesafeConfig(
            ZIO.attempt(ConfigFactory.defaultApplication())
          )
        )
      )
    )
  }

  final case class StorageConfig(assetDir: String, tempDir: String) {
    val assetPath: Path = Path(assetDir)
    val tempPath: Path  = Path(tempDir)
  }
  object StorageConfig                                              {
    private val storageConfigDescription: ConfigDescriptor[StorageConfig] =
      nested("storage") {
        string("asset-dir") <*>
          string("temp-dir")
      }.to[StorageConfig]

    private[Configuration] val layer: ZLayer[Any, ReadError[String], StorageConfig] = ZLayer(
      read(
        storageConfigDescription.from(
          TypesafeConfigSource.fromTypesafeConfig(
            ZIO.attempt(ConfigFactory.defaultApplication())
          )
        )
      )
    )
  }

  val layer: Layer[ReadError[String], ApiConfig with StorageConfig] = ApiConfig.layer ++ StorageConfig.layer
}
