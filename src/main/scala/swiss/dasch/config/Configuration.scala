/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.config

import com.typesafe.config.ConfigFactory
import zio.*
import zio.config.*
import zio.config.ConfigDescriptor.*
import zio.config.typesafe.TypesafeConfigSource
import zio.nio.file.{ Files, Path }

object Configuration {

  final case class JwtConfig(
      secret: String,
      audience: String,
      issuer: String,
      disableAuth: Boolean = false,
    )
  object JwtConfig {
    private val jwtConfigDescription =
      nested("jwt") {
        string("secret") <*>
          string("audience") <*>
          string("issuer") <*>
          boolean("disable-auth")
      }.to[JwtConfig]
    private[Configuration] val layer = ZLayer(
      read(
        jwtConfigDescription.from(
          TypesafeConfigSource.fromTypesafeConfig(
            ZIO.attempt(ConfigFactory.defaultApplication().resolve())
          )
        )
      )
    )
  }

  final case class DspIngestApiConfig(
      host: String,
      port: Int,
    )

  object DspIngestApiConfig {

    private val serverConfigDescription =
      nested("dsp-ingest-api") {
        string("host") <*>
          int("port")
      }.to[DspIngestApiConfig]

    private[Configuration] val layer = ZLayer(
      read(
        serverConfigDescription.from(
          TypesafeConfigSource.fromTypesafeConfig(
            ZIO.attempt(ConfigFactory.defaultApplication().resolve())
          )
        )
      )
    )
  }

  final case class StorageConfig(assetDir: String, tempDir: String) {
    val assetPath: Path  = Path(assetDir)
    val tempPath: Path   = Path(tempDir)
    val exportPath: Path = Path(tempDir) / "export"
    val importPath: Path = Path(tempDir) / "import"
  }
  object StorageConfig                                              {
    private val storageConfigDescription: ConfigDescriptor[StorageConfig] =
      nested("storage") {
        string("asset-dir") <*>
          string("temp-dir")
      }.to[StorageConfig]

    private[Configuration] val layer: Layer[ReadError[String], StorageConfig] = ZLayer(
      read(
        storageConfigDescription.from(
          TypesafeConfigSource.fromTypesafeConfig(
            ZIO.attempt(ConfigFactory.defaultApplication().resolve())
          )
        )
      )
    )

  }

  val layer: Layer[ReadError[String], DspIngestApiConfig with JwtConfig with StorageConfig] =
    DspIngestApiConfig.layer ++ StorageConfig.layer ++ JwtConfig.layer
}
