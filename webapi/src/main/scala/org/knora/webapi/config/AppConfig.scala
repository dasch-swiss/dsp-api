/*
 * Copyright © 2021 - 2023 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.config

import com.typesafe.config.ConfigFactory
import zio._
import zio.config._
import zio.config.magnolia._
import zio.config.typesafe._

import java.time.Duration
import scala.concurrent.duration

import org.knora.webapi.util.cache.CacheUtil

/**
 * Represents the configuration as defined in application.conf.
 */
final case class AppConfig(
  printExtendedConfig: Boolean,
  defaultTimeout: String,
  dumpMessages: Boolean,
  showInternalErrors: Boolean,
  bcryptPasswordStrength: Int,
  cookieDomain: String,
  allowReloadOverHttp: Boolean,
  fallbackLanguage: String,
  knoraApi: KnoraApi,
  sipi: Sipi,
  ark: Ark,
  salsah1: Salsah1,
  caches: List[CacheConfig],
  tmpDatadir: String,
  datadir: String,
  maxResultsPerSearchResultPage: Int,
  standoffPerPage: Int,
  v2: V2,
  gui: Gui,
  routesToReject: List[String],
  triplestore: Triplestore,
  cacheService: CacheService,
  clientTestDataService: ClientTestDataService,
  instrumentationServerConfig: InstrumentationServerConfig,
  jwt: JwtConfig,
  dspIngest: DspIngestConfig
) {
  val tmpDataDirPath: zio.nio.file.Path = zio.nio.file.Path(this.tmpDatadir)
  val defaultTimeoutAsDuration =
    scala.concurrent.duration.Duration.apply(defaultTimeout).asInstanceOf[duration.FiniteDuration]
  val cacheConfigs: Seq[org.knora.webapi.util.cache.CacheUtil.KnoraCacheConfig] = caches.map { c =>
    CacheUtil.KnoraCacheConfig(
      c.cacheName,
      c.maxElementsInMemory,
      c.overflowToDisk,
      c.eternal,
      c.timeToLiveSeconds,
      c.timeToIdleSeconds
    )
  }
}
final case class JwtConfig(secret: String, expiration: Duration, issuer: Option[String]) {
  def issuerAsString(): String = issuer.getOrElse(
    throw new IllegalStateException(
      "This should never happen, the issuer may be left blank in application.conf but the default is taken from external host and port."
    )
  )
}
final case class DspIngestConfig(baseUrl: String, audience: String)

final case class KnoraApi(
  internalHost: String,
  internalPort: Int,
  externalProtocol: String,
  externalHost: String,
  externalPort: Int,
  externalZioPort: Int
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
  timeout: String,
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
  archiveMimeTypes: List[String]
) {
  val internalBaseUrl: String =
    internalProtocol + "://" + internalHost + (if (internalPort != 80) ":" + internalPort else "")
  val externalBaseUrl: String =
    externalProtocol + "://" + externalHost + (if (externalPort != 80) ":" + externalPort else "")
  val timeoutInSeconds: duration.Duration = scala.concurrent.duration.Duration(timeout)

}

final case class Ark(
  resolver: String,
  assignedNumber: Int
)

final case class Salsah1(
  baseUrl: String,
  projectIconsBasepath: String
)

final case class CacheConfig(
  cacheName: String,
  maxElementsInMemory: Int,
  overflowToDisk: Boolean,
  eternal: Boolean,
  timeToLiveSeconds: Int,
  timeToIdleSeconds: Int
)

final case class V2(
  resourcesSequence: ResourcesSequence,
  fulltextSearch: FulltextSearch,
  graphRoute: GraphRoute
)

final case class ResourcesSequence(
  resultsPerPage: Int
)

final case class FulltextSearch(
  searchValueMinLength: Int
)

final case class GraphRoute(
  defaultGraphDepth: Int,
  maxGraphDepth: Int,
  maxGraphBreadth: Int
)

final case class Gui(
  defaultIconSize: DefaultIconSize
)

final case class DefaultIconSize(
  dimX: Int,
  dimY: Int
)

final case class Triplestore(
  dbtype: String,
  useHttps: Boolean,
  host: String,
  queryTimeout: String,
  gravsearchTimeout: String,
  autoInit: Boolean,
  fuseki: Fuseki,
  profileQueries: Boolean
) {
  val queryTimeoutAsDuration      = zio.Duration.fromScala(scala.concurrent.duration.Duration(queryTimeout))
  val gravsearchTimeoutAsDuration = zio.Duration.fromScala(scala.concurrent.duration.Duration(gravsearchTimeout))
}

final case class Fuseki(
  port: Int,
  repositoryName: String,
  username: String,
  password: String
)

final case class CacheService(
  enabled: Boolean
)

final case class ClientTestDataService(
  collectClientTestData: Boolean
)

final case class InstrumentationServerConfig(
  port: Int,
  interval: Duration
)

object AppConfig {
  type AppConfigurations = AppConfig with JwtConfig with DspIngestConfig with Triplestore

  val layer: ULayer[AppConfigurations] = {
    val appConfigLayer = ZLayer {
      val source = TypesafeConfigSource.fromTypesafeConfig(ZIO.attempt(ConfigFactory.load().getConfig("app").resolve))
      read(descriptor[AppConfig].mapKey(toKebabCase) from source).orDie
    }
    projectAppConfigurations(appConfigLayer).tap(_ => ZIO.logInfo(">>> AppConfig Initialized <<<"))
  }

  def projectAppConfigurations[R](appConfigLayer: URLayer[R, AppConfig]): URLayer[R, AppConfigurations] =
    appConfigLayer ++ appConfigLayer.project(_.dspIngest) ++ appConfigLayer.project(_.triplestore) ++
      appConfigLayer.project { appConfig =>
        val jwtConfig = appConfig.jwt
        val issuerFromConfigOrDefault: Option[String] =
          jwtConfig.issuer.orElse(Some(appConfig.knoraApi.externalKnoraApiHostPort))
        jwtConfig.copy(issuer = issuerFromConfigOrDefault)
      }
}
