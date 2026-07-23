/*
 * Copyright © 2021 - 2026 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.config

import com.typesafe.config.ConfigFactory
import zio.*
import zio.config.*
import zio.config.magnolia.*
import zio.config.typesafe.*

import java.time.Duration

/**
 * Represents the configuration as defined in application.conf.
 */
final case class AppConfig(
  bcryptPasswordStrength: Int,
  cookieDomain: String,
  allowReloadOverHttp: Boolean,
  fallbackLanguage: String,
  knoraApi: KnoraApi,
  sipi: Sipi,
  ark: Ark,
  tmpDatadir: String,
  v2: V2,
  triplestore: Triplestore,
  instrumentationServerConfig: InstrumentationServerConfig,
  jwt: JwtConfig,
  dspIngest: DspIngestConfig,
  features: Features,
  `export`: ExportConfig,
  filePermissionCache: FilePermissionCacheConfig,
) {
  val tmpDataDirPath: zio.nio.file.Path = zio.nio.file.Path(this.tmpDatadir)
}

/**
 * Tuning knobs for the streaming CSV export (`POST /v3/export/resources`).
 *
 * `batchSize` is the number of resources fetched and encoded per triplestore round-trip; `parallelism` is how many
 * batches are fetched concurrently. These are operational knobs — they live in config so the values can be changed per
 * deployment (e.g. when prod moves to different hardware) without a code change, release and redeploy.
 */
final case class ExportConfig(
  batchSize: Int,
  parallelism: Int,
)

/**
 * Tuning knobs for the short-lived asset-permission cache on the IIIF tile-serving path (DEV-6806).
 *
 * `ttl` is how long a computed decision is retained before it is re-resolved (so staleness lags by at most one `ttl`);
 * `capacity` is the maximum number of cached decisions before eviction. Both live in config so they can be tuned per
 * deployment without a code change. Validated at load: `ttl` must be positive and at most 10 minutes (a longer window
 * would silently widen permission staleness — this is a short-lived burst cache, not a general one), and `capacity`
 * must be at least 1 (a `capacity` below 1 is undefined for `zio.cache.Cache.makeWith`).
 */
final case class FilePermissionCacheConfig(
  ttl: Duration,
  capacity: Int,
)

final case class JwtConfig(secret: String, expiration: Duration, issuer: Option[String]) {
  def issuerAsString(): String = issuer.getOrElse(
    throw new IllegalStateException(
      "This should never happen, the issuer may be left blank in application.conf but the default is taken from external host and port.",
    ),
  )
}

final case class DspIngestConfig(baseUrl: String, audience: String)

final case class KnoraApi(
  internalHost: String,
  internalPort: Int,
  externalProtocol: String,
  externalHost: String,
  externalPort: Int,
  externalZioPort: Int,
) {
  val internalKnoraApiBaseUrl: String = "http://" + internalHost + (if (internalPort != 80)
                                                                      ":" + internalPort
                                                                    else "")
  val externalKnoraApiHostPort: String = externalHost + (if (externalPort != 80)
                                                           ":" + externalPort
                                                         else "")
  val externalKnoraApiBaseUrl: String = externalProtocol + "://" + externalHost + (if (externalPort != 80)
                                                                                     ":" + externalPort
                                                                                   else "")

  /**
   * If the external hostname is localhost or 0.0.0.0, include the configured
   * external port number in ontology IRIs for manual testing.
   */
  val externalOntologyIriHostAndPort: String =
    if (externalHost == "0.0.0.0" || externalHost == "localhost") {
      externalKnoraApiHostPort
    } else {
      // Otherwise, don't include any port number in IRIs, so the IRIs will work both with http
      // and with https.
      externalHost
    }
}

final case class Sipi(
  internalProtocol: String,
  internalHost: String,
  internalPort: Int,
  timeout: Duration,
  externalProtocol: String,
  externalHost: String,
  externalPort: Int,
  fileServerPath: String,
  moveFileRoute: String,
  deleteTempFileRoute: String,
  imageMimeTypes: List[String],
  documentMimeTypes: List[String],
  textMimeTypes: List[String],
  videoMimeTypes: List[String],
  audioMimeTypes: List[String],
  archiveMimeTypes: List[String],
) {
  val internalBaseUrl: String =
    internalProtocol + "://" + internalHost + (if (internalPort != 80) ":" + internalPort else "")
  val externalBaseUrl: String =
    externalProtocol + "://" + externalHost + (if (externalPort != 80) ":" + externalPort else "")
}

final case class Ark(
  resolver: String,
  assignedNumber: Int,
)

final case class V2(
  resourcesSequence: ResourcesSequence,
  fulltextSearch: FulltextSearch,
  graphRoute: GraphRoute,
  resources: Resources,
)

final case class ResourcesSequence(
  resultsPerPage: Int,
)

final case class Resources(
  maxBatchSize: Int,
)

final case class FulltextSearch(
  searchValueMinLength: Int,
)

final case class GraphRoute(
  defaultGraphDepth: Int,
  maxGraphDepth: Int,
  maxGraphBreadth: Int,
)

final case class Triplestore(
  dbtype: String,
  useHttps: Boolean,
  host: String,
  queryTimeout: Duration,
  gravsearchTimeout: Duration,
  maintenanceTimeout: Duration,
  fuseki: Fuseki,
  profileQueries: Boolean,
  isTestEnv: Boolean = false,
)

final case class Fuseki(
  port: Int,
  username: String,
  password: String,
  queryLoggingThreshold: Duration = Duration.ofMillis(1000),
  allowCompaction: Boolean = false,
  repositoryName: String = "dsp-repo",
)

final case class InstrumentationServerConfig(
  port: Int,
  interval: Duration,
)

final case class Features(
  allowEraseProjects: Boolean,
  disableLastModificationDateCheck: Boolean,
  triggerCompactionAfterProjectErasure: Boolean,
  allowImportMigrationBagit: Boolean,
  allowPlaceholder: Boolean,
  allowProjectDataImport: Boolean,
)

object AppConfig {
  type AppConfigurations = AppConfig & DspIngestConfig & InstrumentationServerConfig & KnoraApi & Sipi & Triplestore &
    GraphRoute & Resources & JwtConfig

  val config: Config[AppConfig] = deriveConfig[AppConfig]
    .mapKey(toKebabCase)
    .map(c => // provide a default value for the JWT issuer if not set explicitly in application.conf
      c.copy(jwt = c.jwt.copy(issuer = c.jwt.issuer.orElse(Some(c.knoraApi.externalKnoraApiHostPort)))),
    )
    .validate("app.v2.resources.max-batch-size must be >= 1")(_.v2.resources.maxBatchSize >= 1)
    .validate("app.file-permission-cache.ttl must be positive")(_.filePermissionCache.ttl.compareTo(Duration.ZERO) > 0)
    .validate("app.file-permission-cache.ttl must be at most 10 minutes (permission-staleness guard)")(
      _.filePermissionCache.ttl.compareTo(Duration.ofMinutes(10)) <= 0,
    )
    .validate("app.file-permission-cache.capacity must be >= 1")(_.filePermissionCache.capacity >= 1)

  def config[A](f: AppConfig => A): UIO[A]  = ZIO.config(config).map(f).orDie
  def features[A](f: Features => A): UIO[A] = ZIO.config(config.map(_.features)).map(f).orDie
  def knoraApi[A](f: KnoraApi => A): UIO[A] = ZIO.config(config.map(_.knoraApi)).map(f).orDie

  private val provider: ConfigProvider =
    TypesafeConfigProvider.fromTypesafeConfig(ConfigFactory.load().getConfig("app").resolve)

  lazy val parseConfig: UIO[AppConfig] = read(config from provider).tap(logFeaturesEnabled).orDie

  val layer: ULayer[AppConfigurations] =
    Runtime.setConfigProvider(provider) >>>
      projectAppConfigurations(ZLayer.fromZIO(parseConfig))
        .tap(_ => ZIO.logInfo(">>> AppConfig Initialized <<<"))

  private def logFeaturesEnabled(c: AppConfig) =
    ZIO.logInfo(
      s"""Features:
         |* ALLOW_ERASE_PROJECTS: ${c.features.allowEraseProjects}
         |* DISABLE_LAST_MODIFICATION_DATE_CHECK: ${c.features.disableLastModificationDateCheck}
         |* TRIGGER_COMPACTION_AFTER_PROJECT_ERASURE: ${c.features.triggerCompactionAfterProjectErasure}
         |* ALLOW_IMPORT_MIGRATION_BAGIT : ${c.features.allowImportMigrationBagit}
         |* ALLOW_PLACEHOLDER: ${c.features.allowPlaceholder}
         |* ALLOW_PROJECT_DATA_IMPORT: ${c.features.allowProjectDataImport}
         |""".stripMargin,
    )

  def projectAppConfigurations[R](appConfigLayer: URLayer[R, AppConfig]): URLayer[R, AppConfigurations] =
    appConfigLayer ++
      appConfigLayer.project(_.knoraApi) ++
      appConfigLayer.project(_.sipi) ++
      appConfigLayer.project(_.dspIngest) ++
      appConfigLayer.project(_.triplestore) ++
      appConfigLayer.project(_.instrumentationServerConfig) ++
      appConfigLayer.project(_.jwt) ++
      appConfigLayer.project(_.v2.graphRoute) ++
      appConfigLayer.project(_.v2.resources)
}
