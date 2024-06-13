/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.config

import com.typesafe.config
import com.typesafe.config.ConfigFactory
import zio.*
import zio.config.*
import zio.config.magnolia.deriveConfig
import zio.config.typesafe.*
import zio.nio.file.Path

object Configuration {
  final private case class ApplicationConf(
    jwt: JwtConfig,
    service: ServiceConfig,
    storage: StorageConfig,
    sipi: SipiConfig,
    ingest: IngestConfig,
    features: Features,
  )

  final case class JwtConfig(
    secret: String,
    audience: String,
    issuer: String,
    disableAuth: Boolean = false,
  )

  final case class ServiceConfig(
    host: String,
    port: Int,
    logFormat: String,
  )

  final case class SipiConfig(useLocalDev: Boolean)

  final case class StorageConfig(assetDir: String, tempDir: String) {
    val assetPath: Path = Path(assetDir)
    val tempPath: Path  = Path(tempDir)
  }

  final case class IngestConfig(bulkMaxParallel: Int)

  final case class Features(allowEraseProjects: Boolean)

  private val configDescriptor = deriveConfig[ApplicationConf].mapKey(toKebabCase)

  private type AllConfigs = ServiceConfig
    with JwtConfig
    with StorageConfig
    with SipiConfig
    with IngestConfig
    with Features

  val layer: ZLayer[Any, Config.Error, AllConfigs] = {
    val applicationConf = ZLayer.fromZIO(
      read(configDescriptor from ConfigProvider.fromTypesafeConfig(ConfigFactory.defaultApplication().resolve()))
        .tap(c => ZIO.logInfo("Feature: ALLOW_ERASE_PROJECTS enabled").when(c.features.allowEraseProjects)),
    )
    applicationConf.project(_.service) ++
      applicationConf.project(_.storage) ++
      applicationConf.project(_.jwt) ++
      applicationConf.project(_.sipi) ++
      applicationConf.project(_.ingest) ++
      applicationConf.project(_.features)
  }
}
