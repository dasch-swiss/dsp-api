package org.knora.webapi.config

import com.typesafe.config.ConfigFactory
import zio._
import zio.config._
import zio.config.typesafe.TypesafeConfigSource

import org.knora.webapi.testcontainers.SipiTestContainer
import zio.config.magnolia._

/**
 * Alters the AppConfig with the TestContainer ports for Fuseki and Sipi.
 */
object AppConfigForTestContainers {

  private def alterFusekiAndSipiPort(
    oldConfig: AppConfig,
    sipiContainer: SipiTestContainer
  ): UIO[AppConfig] = {
    val newSipiConfig        = oldConfig.sipi.copy(internalPort = sipiContainer.container.getFirstMappedPort())
    val newConfig: AppConfig = oldConfig.copy(allowReloadOverHttp = true, sipi = newSipiConfig)
    ZIO.succeed(newConfig)
  }

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
        appConfig     <- config
        sipiContainer <- ZIO.service[SipiTestContainer]
        alteredConfig <- alterFusekiAndSipiPort(appConfig, sipiContainer)
      } yield alteredConfig
    }.tap(_ => ZIO.logInfo(">>> AppConfig for Fuseki and Sipi Testcontainers Initialized <<<"))
}
