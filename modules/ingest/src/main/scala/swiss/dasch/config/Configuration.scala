/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package swiss.dasch.config

import com.typesafe.config.ConfigFactory
import swiss.dasch.version.BuildInfo
import zio.*
import zio.config.*
import zio.config.magnolia.deriveConfig
import zio.config.typesafe.*

import scala.jdk.CollectionConverters.*

import org.knora.webapi.slice.ingest.config.BulkIngestConfig
import org.knora.webapi.slice.ingest.config.SipiConfig
import org.knora.webapi.slice.ingest.config.StorageConfig

object Configuration {
  final private case class ApplicationConf(
    jwt: JwtConfig,
    service: ServiceConfig,
    storage: StorageConfig,
    sipi: SipiConfig,
    ingest: BulkIngestConfig,
    features: Features,
    dspApi: DspApiConfig,
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
    logLevel: String,
  )

  final case class Features(allowEraseProjects: Boolean)

  final case class DspApiConfig(
    url: String,
  )

  private val configDescriptor = deriveConfig[ApplicationConf].mapKey(toKebabCase)

  private type AllConfigs =
    ServiceConfig & JwtConfig & StorageConfig & SipiConfig & BulkIngestConfig & Features & DspApiConfig

  // Without SIPI_LOCAL_DEV_IMAGE, local-dev mode runs the Sipi image built from the same git version.
  private val buildDefaults = ConfigFactory.parseMap(
    Map("sipi.local-dev-image" -> s"daschswiss/knora-sipi:${BuildInfo.knoraSipiVersion}").asJava,
  )

  val layer: ZLayer[Any, Config.Error, AllConfigs] = {
    // defaultApplication() merges every application.conf on the classpath, including webapi's, whose
    // keys all sit under `app`; the top-level keys of ApplicationConf must stay disjoint from it.
    val config          = ConfigFactory.defaultApplication().withFallback(buildDefaults).resolve()
    val applicationConf = ZLayer.fromZIO(
      read(configDescriptor from ConfigProvider.fromTypesafeConfig(config))
        .tap(c => ZIO.logInfo("Feature: ALLOW_ERASE_PROJECTS enabled").when(c.features.allowEraseProjects)),
    )
    applicationConf.project(_.service) ++
      applicationConf.project(_.storage) ++
      applicationConf.project(_.jwt) ++
      applicationConf.project(_.sipi) ++
      applicationConf.project(_.ingest) ++
      applicationConf.project(_.features) ++
      applicationConf.project(_.dspApi)
  }
}
