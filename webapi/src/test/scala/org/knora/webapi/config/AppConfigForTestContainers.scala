package org.knora.webapi.config

import zio._
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testcontainers.SipiTestContainer
import zio.config.typesafe.TypesafeConfigSource
import com.typesafe.config.ConfigFactory
import zio.config.ReadError
import zio.config.ConfigDescriptor
import zio.config._

import typesafe._
import magnolia._

import zio._
import com.typesafe.config.ConfigFactory
import scala.concurrent.duration._
import java.util.concurrent.TimeUnit

/**
 * Alters the AppConfig with the TestContainer ports for Fuseki and Sipi.
 */
object AppConfigForTestContainers {

  /**
   * Reads in the applicaton configuration using ZIO-Config. ZIO-Config is capable of loading
   * the Typesafe-Config format. Reads the 'app' configuration from 'application.conf'.
   */
  private val source: ConfigSource =
    TypesafeConfigSource.fromTypesafeConfig(ZIO.attempt(ConfigFactory.load().getConfig("app").resolve))

  /**
   * Intantiates our config class hierarchy using the data from the 'app' configuration from 'application.conf'.
   */
  private val config: UIO[AppConfig] = (read(descriptor[AppConfig].mapKey(toKebabCase) from source)).orDie

  /**
   * Altered AppConfig with ports from TestContainers for Fuseki and Sipi.
   */
  val testcontainers: ZLayer[SipiTestContainer, Nothing, AppConfig] =
    ZLayer {
      for {
        // _         <- ZIO.debug("start of app config")
        appConfig         <- config // .tapBoth(ZIO.debug(_), ZIO.debug(_))
        // fusekiContainer   <- ZIO.service[FusekiTestContainer]
        sipiContainer     <- ZIO.service[SipiTestContainer]
        alteredSipiConfig <- ZIO.succeed(appConfig.sipi.copy(internalPort = sipiContainer.container.getFirstMappedPort))
      } yield appConfig.copy(sipi = alteredSipiConfig)
    }.tap(_ => ZIO.debug(">>> AppConfigForTestContainers Initialized <<<"))
}
