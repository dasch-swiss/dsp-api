package org.knora.webapi.config

import com.typesafe.config.ConfigFactory
import zio._
import zio.config._

import java.nio.file.Paths
import scala.concurrent.duration

import org.knora.webapi.messages.StringFormatter
import org.knora.webapi.messages.util.rdf.RdfFeatureFactory
import org.knora.webapi.util.cache.CacheUtil

import typesafe._
import magnolia._

/**
 * Represents (eventually) the complete configuration as defined in application.conf.
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
  knoraApi: KnoraAPI,
  sipi: Sipi,
  ark: Ark,
  salsah1: Salsah1,
  gui: Gui,
  triplestore: Triplestore,
  v2: V2,
  shacl: Shacl,
  fallbackLanguage: String,
  maxResultsPerSearchResultPage: Int,
  standoffPerPage: Int,
  routesToReject: List[String],
  tmpDatadir: String,
  clientTestDataService: ClientTestDataService,
  caches: List[CacheConfig],
  cacheService: CacheService
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

}

final case class KnoraAPI(
  internalHost: String,
  internalPort: Int,
  externalProtocol: String,
  externalHost: String,
  externalPort: Int
) {
  val internalKnoraApiHostPort: String = internalHost + (if (internalPort != 80)
                                                           ":" + internalPort
                                                         else "")
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
  def internalBaseUrl: String = "http://" + internalHost + (if (internalPort != 80)
                                                              ":" + internalPort
                                                            else "")
  def externalBaseUrl: String = "http://" + externalHost + (if (externalPort != 80)
                                                              ":" + externalPort
                                                            else "")
  val timeoutInSeconds: duration.Duration = scala.concurrent.duration.Duration(timeout)

}

final case class CacheConfig(
  cacheName: String,
  maxElementsInMemory: Int,
  overflowToDisk: Boolean,
  eternal: Boolean,
  timeToLiveSeconds: Int,
  timeToIdleSeconds: Int
)

final case class CacheService(
  enabled: Boolean,
  redis: Redis
)

final case class Redis(
  host: String,
  port: Int
)

final case class V2(
  resourcesSequence: ResourcesSequence,
  graphRoute: GraphRoute,
  fulltextSearch: FulltextSearch
)

final case class ResourcesSequence(
  resultsPerPage: Int
)

final case class GraphRoute(
  defaultGraphDepth: Int,
  maxGraphBreadth: Int,
  maxGraphDepth: Int
)

final case class FulltextSearch(
  searchValueMinLength: Int
)

final case class Salsah1(
  baseUrl: String,
  projectIconsBasepath: String
)

final case class Gui(
  defaultIconSize: DefaultIconSize
)

final case class DefaultIconSize(
  dimX: Int,
  dimY: Int
)

final case class Ark(
  resolver: String,
  assignedNumber: Int
)

final case class Triplestore(
  dbtype: String,
  useHttps: Boolean,
  host: String,
  queryTimeout: String,
  gravsearchTimeout: String,
  autoInit: Boolean,
  profileQueries: Boolean,
  fuseki: Fuseki
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

final case class ClientTestDataService(
  collectClientTestData: Boolean
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
  private val config: IO[ReadError[String], AppConfig] = read(descriptor[AppConfig].mapKey(toKebabCase) from source)

  /**
   * Live configuration reading from application.conf and initializing StringFormater for live
   */
  val live: ZLayer[Any, Nothing, AppConfig] =
    ZLayer {
      for {
        c <- config.orDie
        _ <- ZIO.attempt(StringFormatter.init(c)).orDie   // needs early init before first usage
        _ <- ZIO.attempt(RdfFeatureFactory.init(c)).orDie // needs early init before first usage
      } yield c
    }.tap(_ => ZIO.logInfo(">>> AppConfig Live Initialized <<<"))

  /**
   * Test configuration reading from application.conf and initializing StringFormater for tests
   */
  val test: ZLayer[Any, Nothing, AppConfig] =
    ZLayer {
      for {
        c <- config.orDie
        _ <- ZIO.attempt(StringFormatter.initForTest()).orDie // needs early init before first usage
        _ <- ZIO.attempt(RdfFeatureFactory.init(c)).orDie     // needs early init before first usage
      } yield c
    }.tap(_ => ZIO.logInfo(">>> AppConfig Test Initialized <<<"))
}
