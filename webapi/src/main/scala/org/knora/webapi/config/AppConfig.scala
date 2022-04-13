package org.knora.webapi.config

import org.knora.webapi.store.cacheservice.config.CacheServiceConfig
import zio.config.ConfigDescriptor
import zio.config._

import typesafe._
import magnolia._

import zio._
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration.FiniteDuration

/**
 * Represents the complete configuration as defined in application.conf.
 */
final case class AppConfig(
  printExtendedConfig: Boolean,
  defaultTimeout: String,
  dumpMessages: Boolean,
  showInternalErrors: Boolean,
  skipAuthentication: Boolean,
  bcryptPasswordStrength: Int,
  jwtSecretKey: String,
  jwtLongevity: String,
  cookieDomain: String,
  allowReloadOverHttp: Boolean,
  knoraApi: KnoraAPI,
  sipi: Sipi
)

final case class KnoraAPI(
  internalHost: String,
  internalPort: Int,
  externalProtocol: String,
  externalHost: String,
  externalPort: Int
) {
  def internalKnoraApiBaseUrl: String = internalHost + (if (internalPort != 80)
                                                          ":" + internalPort
                                                        else "")
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
  v2: V2,
  imageMimeTypes: List[String],
  documentMimeTypes: List[String],
  textMimeTypes: List[String],
  videoMimeTypes: List[String],
  audioMimeTypes: List[String],
  archiveMimeTypes: List[String]
)

final case class V2(
  fileMetadataRoute: String,
  moveFileRoute: String,
  deleteTempFileRoute: String
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
   * Intantiates our config class hierarchy using the data from the 'app' configuration from 'application.conf'.
   */
  private val config: IO[ReadError[String], AppConfig] = read(descriptor[AppConfig].mapKey(toKebabCase) from source)

  /**
   * Live configuration reading from application.conf.
   */
  val live: ZLayer[Any, ReadError[String], AppConfig] =
    ZLayer {
      config
    }.tap(_ => ZIO.debug(">>> AppConfig Initialized <<<"))
}
