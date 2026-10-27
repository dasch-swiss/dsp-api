/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.slice.ingest.config

import com.typesafe.config.Config as TypesafeConfig
import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigResolveOptions
import zio.*
import zio.config.*
import zio.config.magnolia.deriveConfig
import zio.config.typesafe.*
import zio.nio.file.Path

final case class StorageConfig(assetDir: String, tempDir: String) {
  val assetPath: Path = Path(assetDir)
  val tempPath: Path  = Path(tempDir)
}

final case class SipiConfig(useLocalDev: Boolean)

final case class BulkIngestConfig(bulkMaxParallel: Int)

final case class IngestFeatures(allowEraseProjects: Boolean)

final case class ExecutorConfig(cpuShare: Double)

/**
 * The ingest slice's settings, read from `ingest.conf` and `INGEST_*` environment variables only.
 * No key or environment variable here is also read by webapi's `AppConfig`.
 */
final case class IngestConfig(
  storage: StorageConfig,
  sipi: SipiConfig,
  bulkMaxParallel: Int,
  features: IngestFeatures,
  executor: ExecutorConfig,
)

object IngestConfig {

  type IngestConfigs = StorageConfig & SipiConfig & BulkIngestConfig & IngestFeatures & ExecutorConfig

  private val descriptor: Config[IngestConfig] =
    deriveConfig[IngestConfig]
      .mapKey(toKebabCase)
      .validate("ingest.executor.cpu-share must be in (0, 1]") { c =>
        c.executor.cpuShare > 0 && c.executor.cpuShare <= 1
      }

  /**
   * Reads `ingest.conf`, resolving its `${?INGEST_*}` substitutions against `env` only, so a test can
   * supply the environment without touching the process environment.
   */
  def load(env: TypesafeConfig = ConfigFactory.systemEnvironment()): IO[Config.Error, IngestConfig] =
    ZIO
      .attempt(
        env
          .withFallback(ConfigFactory.parseResources("ingest.conf"))
          .resolve(ConfigResolveOptions.defaults().setUseSystemEnvironment(false))
          .getConfig("ingest"),
      )
      .mapError(e => Config.Error.InvalidData(message = e.getMessage))
      .flatMap(c => read(descriptor from TypesafeConfigProvider.fromTypesafeConfig(c)))

  val layer: Layer[Config.Error, IngestConfigs] = {
    val config = ZLayer.fromZIO(load())
    config.project(_.storage) ++
      config.project(_.sipi) ++
      config.project(c => BulkIngestConfig(c.bulkMaxParallel)) ++
      config.project(_.features) ++
      config.project(_.executor)
  }
}
