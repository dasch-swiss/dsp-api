/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.config

import com.typesafe.config.ConfigFactory
import zio.*
import zio.config.*
import zio.config.magnolia.deriveConfig
import zio.config.typesafe.*
import zio.nio.file.Path

object Configuration {
  final private case class IngestConf(
    storage: StorageConfig,
    sipi: SipiConfig,
    bulkMaxParallel: Int,
    features: Features,
    executor: ExecutorConfig,
  )

  /** Not loaded from `ingest.conf`: the embedding application provides it from its own token settings. */
  final case class JwtConfig(
    secret: String,
    audience: String,
    issuer: String,
    disableAuth: Boolean = false,
  )

  final case class SipiConfig(useLocalDev: Boolean, localDevImage: String)

  final case class StorageConfig(assetDir: String, tempDir: String) {
    val assetPath: Path = Path(assetDir)
    val tempPath: Path  = Path(tempDir)
  }

  final case class IngestConfig(bulkMaxParallel: Int)

  final case class Features(allowEraseProjects: Boolean)

  final case class ExecutorConfig(cpuShare: Double)

  private val configDescriptor = deriveConfig[IngestConf]
    .mapKey(toKebabCase)
    .validate("executor.cpu-share must be in (0, 1]")(c => c.executor.cpuShare > 0 && c.executor.cpuShare <= 1)

  type IngestConfigs = StorageConfig & SipiConfig & IngestConfig & Features & ExecutorConfig

  /** Reads `ingest.conf` only, so no key is shared with the embedding application's `application.conf`. */
  val layer: ZLayer[Any, Config.Error, IngestConfigs] =
    fromTypesafeConfig(ConfigFactory.parseResources("ingest.conf").resolve())

  private[config] def fromTypesafeConfig(
    resolved: => com.typesafe.config.Config,
  ): ZLayer[Any, Config.Error, IngestConfigs] = {
    val ingestConf = ZLayer.fromZIO(
      ZIO
        .attempt(resolved.getConfig("ingest"))
        .mapError(e => Config.Error.InvalidData(message = e.getMessage))
        .flatMap(conf => read(configDescriptor from ConfigProvider.fromTypesafeConfig(conf)))
        .tap(c => ZIO.logInfo("Feature: INGEST_ALLOW_ERASE_PROJECTS enabled").when(c.features.allowEraseProjects)),
    )
    ingestConf.project(_.storage) ++
      ingestConf.project(_.sipi) ++
      ingestConf.project(c => IngestConfig(c.bulkMaxParallel)) ++
      ingestConf.project(_.features) ++
      ingestConf.project(_.executor)
  }
}
