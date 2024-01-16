/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.config

import com.typesafe.config.ConfigFactory
import zio.*
import zio.config.*
import zio.config.typesafe.TypesafeConfigProvider

import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.testcontainers.DspIngestTestContainer
import org.knora.webapi.testcontainers.FusekiTestContainer
import org.knora.webapi.testcontainers.SipiTestContainer

/**
 * Alters the AppConfig with the TestContainer ports for Fuseki and Sipi.
 */
object AppConfigForTestContainers {

  private def alterFusekiAndSipiPort(
    oldConfig: AppConfig,
    fusekiContainer: FusekiTestContainer,
    sipiContainer: SipiTestContainer,
    dspIngestContainer: DspIngestTestContainer
  ): UIO[AppConfig] = {

    val newFusekiPort    = fusekiContainer.getFirstMappedPort
    val newSipiPort      = sipiContainer.getFirstMappedPort
    val newDspIngestPort = dspIngestContainer.getFirstMappedPort

    val alteredFuseki = oldConfig.triplestore.fuseki.copy(port = newFusekiPort)

    val alteredTriplestore = oldConfig.triplestore.copy(fuseki = alteredFuseki)
    val alteredSipi        = oldConfig.sipi.copy(internalPort = newSipiPort)
    val alteredDspIngest   = oldConfig.dspIngest.copy(baseUrl = s"http://localhost:$newDspIngestPort")

    val newConfig: AppConfig =
      oldConfig.copy(
        allowReloadOverHttp = true,
        triplestore = alteredTriplestore,
        sipi = alteredSipi,
        dspIngest = alteredDspIngest
      )

    ZIO.succeed(newConfig)
  }

  private def alterFusekiPort(
    oldConfig: AppConfig,
    fusekiContainer: FusekiTestContainer
  ): UIO[AppConfig] = {

    val newFusekiPort = fusekiContainer.getFirstMappedPort

    val alteredFuseki = oldConfig.triplestore.fuseki.copy(port = newFusekiPort)

    val alteredTriplestore = oldConfig.triplestore.copy(fuseki = alteredFuseki)

    val newConfig: AppConfig = oldConfig.copy(triplestore = alteredTriplestore)

    ZIO.succeed(newConfig)
  }

  /**
   * Reads in the application configuration using ZIO-Config. ZIO-Config is capable of loading
   * the Typesafe-Config format. Reads the 'app' configuration from 'application.conf'.
   */
  private val source: ConfigProvider =
    TypesafeConfigProvider.fromTypesafeConfig(ConfigFactory.load().getConfig("app").resolve)

  /**
   * Instantiates our config class hierarchy using the data from the 'app' configuration from 'application.conf'.
   */
  private val config: UIO[AppConfig] = read(AppConfig.descriptor from source).orDie

  /**
   * Altered AppConfig with ports from TestContainers for DSP-Ingest, Fuseki and Sipi.
   */
  val testcontainers
    : ZLayer[DspIngestTestContainer & FusekiTestContainer & SipiTestContainer, Nothing, AppConfigurations] = {
    val appConfigLayer = ZLayer {
      for {
        appConfig          <- config
        fusekiContainer    <- ZIO.service[FusekiTestContainer]
        sipiContainer      <- ZIO.service[SipiTestContainer]
        dspIngestContainer <- ZIO.service[DspIngestTestContainer]
        alteredConfig      <- alterFusekiAndSipiPort(appConfig, fusekiContainer, sipiContainer, dspIngestContainer)
      } yield alteredConfig
    }
    AppConfig
      .projectAppConfigurations(appConfigLayer)
      .tap(_ => ZIO.logInfo(">>> AppConfig for Fuseki and Sipi Testcontainers Initialized <<<"))
  }

  /**
   * Altered AppConfig with ports from TestContainers for Fuseki.
   */
  val fusekiOnlyTestcontainer: ZLayer[FusekiTestContainer, Nothing, AppConfigurations] = {
    val appConfigLayer = ZLayer {
      for {
        appConfig       <- config
        fusekiContainer <- ZIO.service[FusekiTestContainer]
        alteredConfig   <- alterFusekiPort(appConfig, fusekiContainer)
      } yield alteredConfig
    }
    AppConfig
      .projectAppConfigurations(appConfigLayer)
      .tap(_ => ZIO.logInfo(">>> AppConfig for Fuseki only Testcontainers Initialized <<<"))
  }
}
