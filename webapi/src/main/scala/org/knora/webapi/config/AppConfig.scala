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

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import scala.concurrent.duration
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import dsp.errors.FileWriteException
import org.knora.webapi.messages.util.rdf.RdfFeatureFactory
import org.knora.webapi.util.cache.CacheUtil

/**
 * Represents the configuration as defined in application.conf.
 */
final case class AppConfig(
  testing: Boolean = false,
  printExtendedConfig: Boolean,
  defaultTimeout: String,
  dumpMessages: Boolean,
  showInternalErrors: Boolean,
  bcryptPasswordStrength: Int,
  jwtSecretKey: String,
  jwtLongevity: String,
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
  shacl: Shacl,
  cacheService: CacheService,
  clientTestDataService: ClientTestDataService,
  instrumentationServerConfig: InstrumentationServerConfig,
  zioHttp: ZioHttp
) {
  val jwtLongevityAsDuration = scala.concurrent.duration.Duration(jwtLongevity)
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

  // create the directories
  val tmpDataDirCreation: Try[Path] = Try {
    Files.createDirectories(Paths.get(tmpDatadir))
  }
  tmpDataDirCreation match {
    case Success(_) => ZIO.logInfo(s"Created tmp directory $tmpDatadir")
    case Failure(e) => throw FileWriteException(s"Tmp data directory $tmpDatadir could not be created: ${e.getMessage}")
  }

  val dataDirCreation: Try[Path] = Try {
    Files.createDirectories(Paths.get(datadir))
  }
  dataDirCreation match {
    case Success(_) => ZIO.logInfo(s"Created directory $datadir")
    case Failure(e) => throw FileWriteException(s"Data directory $datadir could not be created: ${e.getMessage}")
  }

}

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

final case class Shacl(
  shapesDir: String
) {
  val shapesDirPath = Paths.get(shapesDir)
}

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

final case class ZioHttp(
  corsAllowedOrigins: Set[String]
)

/**
 * Loads the applicaton configuration using ZIO-Config. ZIO-Config is capable of loading
 * the Typesafe-Config format.
 */
object AppConfig {

  /**
   * Reads in the applicaton configuration using ZIO-Config. ZIO-Config is capable of loading
   * the Typesafe-Config format. Reads the 'app' configuration from 'application.conf'.
   */
  private val source: ConfigSource =
    TypesafeConfigSource.fromTypesafeConfig(ZIO.attempt(ConfigFactory.load().getConfig("app").resolve))

  /**
   * Instantiates our config class hierarchy using the data from the 'app' configuration from 'application.conf'.
   */
  private val configFromSource: IO[ReadError[String], AppConfig] = read(
    descriptor[AppConfig].mapKey(toKebabCase) from source
  )

  /**
   * Application configuration from application.conf
   */
  val live: ZLayer[Any, Nothing, AppConfig] =
    ZLayer {
      for {
        c <- configFromSource.orDie
        _ <- ZIO.attempt(RdfFeatureFactory.init(c)).orDie // needs early init before first usage
      } yield c
    }.tap(_ => ZIO.logInfo(">>> AppConfig Live Initialized <<<"))

  /**
   * Application configuration from test.conf for testing
   */
  val test: ZLayer[Any, Nothing, AppConfig] =
    ZLayer {
      for {
        c <- configFromSource.orDie
        _ <- ZIO.attempt(RdfFeatureFactory.init(c)).orDie // needs early init before first usage
      } yield c
    }.tap(_ => ZIO.logInfo(">>> AppConfig Test Initialized <<<"))

}
