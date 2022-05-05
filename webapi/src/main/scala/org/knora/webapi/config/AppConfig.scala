package org.knora.webapi.config

import com.typesafe.config.ConfigFactory
import zio._
import zio.config._

import scala.concurrent.duration

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
  skipAuthentication: Boolean,
  bcryptPasswordStrength: Int,
  jwtSecretKey: String,
  jwtLongevity: String,
  cookieDomain: String,
  allowReloadOverHttp: Boolean,
  knoraApi: KnoraAPI,
  sipi: Sipi,
  triplestore: Triplestore
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
  def internalBaseUrl: String = "http://" + internalHost + (if (internalPort != 80)
                                                              ":" + internalPort
                                                            else "")
  def externalBaseUrl: String = "http://" + externalHost + (if (externalPort != 80)
                                                              ":" + externalPort
                                                            else "")
  val timeoutInSeconds: duration.Duration = scala.concurrent.duration.Duration(timeout)
}

final case class V2(
  fileMetadataRoute: String,
  moveFileRoute: String,
  deleteTempFileRoute: String
)

final case class Triplestore(
  dbtype: String,
  useHttps: Boolean,
  host: String,
  queryTimeout: String,
  updateTimeout: String,
  autoInit: Boolean
) {
  val updateTimeoutAsDuration = zio.Duration.fromScala(scala.concurrent.duration.Duration(updateTimeout))
}

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
   * Live configuration reading from application.conf.
   */
  val live: ZLayer[Any, Nothing, AppConfig] =
    ZLayer {
      config.orDie
    }.tap(_ => ZIO.debug(">>> AppConfig Initialized <<<"))
}
