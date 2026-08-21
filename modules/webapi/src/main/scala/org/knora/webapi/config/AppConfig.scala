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
  allowSparqlPassthrough: Boolean,
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

/**
 * @param baseUrl         internal URL for server-to-server communication with dsp-ingest.
 * @param externalBaseUrl client-facing URL for download links handed to clients (e.g. OAI file links).
 */
final case class DspIngestConfig(baseUrl: String, externalBaseUrl: String, audience: String)

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
  probe: FulltextSearchProbe,
)

/**
 * Tuning knobs for the fulltext breadth probe (DEV-6864, PROBE).
 *
 * `cap` is the Lucene candidate count above which a fulltext search is refused (raced against the real query so
 * an admitted term pays no tax). `cacheCapacity` / `cacheTtl` bound the measured-breadth cache, and `maxConcurrent`
 * bounds how many probes run against Fuseki at once. All live in config so they can be tuned per deployment
 * without a code change; `cap` also has a `KNORA_WEBAPI_FULLTEXT_PROBE_CAP` override.
 */
final case class FulltextSearchProbe(
  cap: Int,
  cacheCapacity: Int,
  cacheTtl: Duration,
  maxConcurrent: Int,
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
  searchTimeout: Duration,
  maintenanceTimeout: Duration,
  fuseki: Fuseki,
  profileQueries: Boolean,
  sparqlPassthrough: SparqlPassthroughConfig,
  isTestEnv: Boolean = false,
)

/**
 * Guardrails for the admin SPARQL passthrough surface (`POST /admin/sparql/query`), which is itself gated by
 * `app.allow-sparql-passthrough`. All four are operational knobs, living in config so an environment can tune them
 * without a code change, release and redeploy, and all four bound an interaction with the triplestore -- which is
 * why they sit under `app.triplestore`.
 *
 * `timeout` is sent to the store as its per-request execution timeout, so an over-time query is cancelled by the
 * store's engine rather than merely abandoned client-side. It also sizes the API-side deadline.
 *
 * `maxRequestBodyBytes` bounds the SPARQL text a caller may submit. It is enforced on that one endpoint, never
 * globally -- a global cap of this size would reject the larger project-import upload.
 *
 * `maxResponseBytes` bounds the bytes read back from the store, counted before any response compression. The read
 * path buffers up to this ceiling and only then responds, so a breach yields a clean error rather than a truncated
 * body; the consequence is that this value sizes per-request heap use. It is one copy, not two: the relay carries the
 * response as the array the HTTP layer needs, collected once at the point it is read.
 *
 * `maxConcurrentCalls` is a surface-wide backstop on calls in flight *against the store*, so a runaway script or agent
 * cannot open store connections without limit. It is runaway protection, not a throughput or fairness control, hence
 * deliberately generous; a call arriving while it is saturated is rejected, never queued.
 *
 * It is deliberately **not** a heap bound, and must not be documented as one. The slot is released when the store's
 * bytes have been collected, which is before the HTTP layer has written them to the client, so a response array
 * outlives its slot for as long as the caller takes to read it. Worst-case response-side heap is `maxResponseBytes`
 * times the simultaneous callers the deployment admits -- the same shape as the request side, where the backstop
 * likewise sits downstream of the buffering it would have to bound. Holding the slot across the write is not
 * available from here: this service hands the bytes back to the framework, whose write happens after every scope
 * opened inside the server logic has already closed.
 */
final case class SparqlPassthroughConfig(
  timeout: Duration,
  maxRequestBodyBytes: Int,
  maxResponseBytes: Int,
  maxConcurrentCalls: Int,
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
    .validate("app.triplestore.search-timeout must be positive")(
      _.triplestore.searchTimeout.compareTo(Duration.ZERO) > 0,
    )
    // The search tier must never exceed Gravsearch, or the DEV-6864 load-shed rationale inverts: the whole
    // point is that the fulltext prequery sheds load *before* the 120s main query would. An env override
    // (KNORA_WEBAPI_TRIPLESTORE_SEARCH_TIMEOUT) makes a bad value injectable at deploy time, so guard it.
    .validate("app.triplestore.search-timeout must be <= app.triplestore.gravsearch-timeout")(c =>
      c.triplestore.searchTimeout.compareTo(c.triplestore.gravsearchTimeout) <= 0,
    )
    .validate("app.file-permission-cache.ttl must be positive")(_.filePermissionCache.ttl.compareTo(Duration.ZERO) > 0)
    .validate("app.file-permission-cache.ttl must be at most 10 minutes (permission-staleness guard)")(
      _.filePermissionCache.ttl.compareTo(Duration.ofMinutes(10)) <= 0,
    )
    .validate("app.file-permission-cache.capacity must be >= 1")(_.filePermissionCache.capacity >= 1)
    // Whole seconds, not merely positive: the value is sent to the store as its per-request execution timeout in
    // seconds, so every use goes through `toSeconds`. A sub-second value is env-injectable
    // (KNORA_WEBAPI_SPARQL_PASSTHROUGH_TIMEOUT=500 millis) and would truncate to `timeout=0` on the wire -- silently
    // removing the store-side cancellation the design leans on, and reporting "0 seconds" to the caller, while the
    // API-side deadline still made the call look bounded.
    .validate("app.triplestore.sparql-passthrough.timeout must be >= 1 second; it is sent to the store in seconds")(
      _.triplestore.sparqlPassthrough.timeout.toSeconds >= 1,
    )
    .validate("app.triplestore.sparql-passthrough.max-request-body-bytes must be >= 1")(
      _.triplestore.sparqlPassthrough.maxRequestBodyBytes >= 1,
    )
    .validate("app.triplestore.sparql-passthrough.max-response-bytes must be >= 1")(
      _.triplestore.sparqlPassthrough.maxResponseBytes >= 1,
    )
    .validate("app.triplestore.sparql-passthrough.max-concurrent-calls must be >= 1")(
      _.triplestore.sparqlPassthrough.maxConcurrentCalls >= 1,
    )
    // The probe knobs feed FulltextBreadthGuard's cap check, Cache.makeWith and Semaphore.make, so guard each
    // against a value that breaks the guard. cap is env-injectable (KNORA_WEBAPI_FULLTEXT_PROBE_CAP): cap < 1
    // refuses every probed search; cache-capacity < 1 is undefined; max-concurrent = 0 is a zero-permit semaphore,
    // so the probe lookup never completes and `guarded` — which awaits it before deciding — hangs every probed
    // search; cache-ttl <= 0 expires each result instantly, defeating the single-flight cache so every request
    // re-probes. Guard all four, as the sibling file-permission-cache guards do (DEV-6864).
    .validate("app.v2.fulltext-search.probe.cap must be >= 1")(_.v2.fulltextSearch.probe.cap >= 1)
    .validate("app.v2.fulltext-search.probe.cache-capacity must be >= 1")(
      _.v2.fulltextSearch.probe.cacheCapacity >= 1,
    )
    .validate("app.v2.fulltext-search.probe.cache-ttl must be positive")(
      _.v2.fulltextSearch.probe.cacheTtl.compareTo(Duration.ZERO) > 0,
    )
    .validate("app.v2.fulltext-search.probe.max-concurrent must be >= 1")(
      _.v2.fulltextSearch.probe.maxConcurrent >= 1,
    )

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
