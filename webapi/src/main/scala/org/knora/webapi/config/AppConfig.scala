package org.knora.webapi.config

import org.knora.webapi.store.cacheservice.config.CacheServiceConfig
import zio.config.ConfigDescriptor
import zio.config._

import typesafe._
import magnolia._

import zio._
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

/**
 * Represents the complete configuration as defined in application.conf.
 */
final case class AppConfig(
  testing: Boolean = false,
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
) {
  val jwtLongevityAsDuration = scala.concurrent.duration.Duration(jwtLongevity)
}

final case class KnoraAPI(
  internalHost: String,
  internalPort: Int,
  externalProtocol: String,
  externalHost: String,
  externalPort: Int
) {
  def internalKnoraApiHostPort: String = internalHost + (if (internalPort != 80)
                                                           ":" + internalPort
                                                         else "")
  def internalKnoraApiBaseUrl: String = "http://" + internalHost + (if (internalPort != 80)
                                                                      ":" + internalPort
                                                                    else "")
  def externalKnoraApiHostPort: String = externalHost + (if (externalPort != 80)
                                                           ":" + externalPort
                                                         else "")
  def externalKnoraApiBaseUrl: String = externalProtocol + "://" + externalHost + (if (externalPort != 80)
                                                                                     ":" + externalPort
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
) {
  def internalBaseUrl = "http://" + internalHost + (if (internalPort != 80)
                                                      ":" + internalPort
                                                    else "")
  def externalBaseUrl = "http://" + externalHost + (if (externalPort != 80)
                                                      ":" + externalPort
                                                    else "")
  val timeoutInSeconds = scala.concurrent.duration.Duration(timeout)
}

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
  val live: ZLayer[Any, Nothing, AppConfig] =
    ZLayer {
      config.orDie
    }.tap(_ => ZIO.debug(">>> AppConfig Initialized <<<"))
}
