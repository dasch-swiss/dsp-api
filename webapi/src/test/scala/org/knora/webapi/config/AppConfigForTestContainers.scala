package org.knora.webapi.config

import com.typesafe.config.ConfigFactory
import org.knora.webapi.testcontainers.SipiTestContainer
import zio._
import zio.config._
import zio.config.typesafe.TypesafeConfigSource

import magnolia._
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.testcontainers.containers.GenericContainer

/**
 * Alters the AppConfig with the TestContainer ports for Fuseki and Sipi.
 */
object AppConfigForTestContainers {

  private def alterFusekiAndSipiPort(
    oldConfig: AppConfig,
    fusekiContainer: FusekiTestContainer,
    sipiContainer: SipiTestContainer
  ): UIO[AppConfig] = {

    val newFusekiPort = fusekiContainer.container.getFirstMappedPort()
    val newSipiPort   = sipiContainer.container.getFirstMappedPort()

    val alteredFuseki = oldConfig.triplestore.fuseki.copy(port = newFusekiPort)

    val alteredTriplestore = oldConfig.triplestore.copy(fuseki = alteredFuseki)
    val alteredSipi        = oldConfig.sipi.copy(internalPort = newSipiPort)

    val newConfig: AppConfig = oldConfig.copy(allowReloadOverHttp = true, triplestore = alteredTriplestore, sipi = alteredSipi)

    ZIO.succeed(newConfig)
  }

  private def alterFusekiPort(
    oldConfig: AppConfig,
    fusekiContainer: FusekiTestContainer
  ): UIO[AppConfig] = {

    val newFusekiPort = fusekiContainer.container.getFirstMappedPort()

    val alteredFuseki = oldConfig.triplestore.fuseki.copy(port = newFusekiPort)

    val alteredTriplestore = oldConfig.triplestore.copy(fuseki = alteredFuseki)

    val newConfig: AppConfig = oldConfig.copy(triplestore = alteredTriplestore)

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
  val testcontainers: ZLayer[FusekiTestContainer & SipiTestContainer, Nothing, AppConfig] =
    ZLayer {
      for {
        appConfig       <- config
        fusekiContainer <- ZIO.service[FusekiTestContainer]
        sipiContainer   <- ZIO.service[SipiTestContainer]
        alteredConfig   <- alterFusekiAndSipiPort(appConfig, fusekiContainer, sipiContainer)
      } yield alteredConfig
    }.tap(_ => ZIO.debug(">>> App Config for Fuseki and Sipi Testcontainers Initialized <<<"))

  /**
   * Altered AppConfig with ports from TestContainers for Fuseki and Sipi.
   */
  val fusekiOnlyTestcontainer: ZLayer[FusekiTestContainer, Nothing, AppConfig] =
    ZLayer {
      for {
        appConfig       <- config
        fusekiContainer <- ZIO.service[FusekiTestContainer]
        alteredConfig   <- alterFusekiPort(appConfig, fusekiContainer)
      } yield alteredConfig
    }.tap(_ => ZIO.debug(">>> App Config for Fuseki only Testcontainers Initialized <<<"))
}
