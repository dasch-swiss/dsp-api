/*
 * Copyright © 2021 - 2024 Swiss National Data and Service Center for the Humanities and/or DaSCH Service Platform contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.knora.webapi.config

import com.typesafe.config.ConfigFactory
import zio._
import zio.config._
import zio.config.typesafe.TypesafeConfigProvider

import org.knora.webapi.config.AppConfig.AppConfigurations
import org.knora.webapi.testcontainers.FusekiTestContainer

/**
 * Alters the AppConfig with the TestContainer ports for Fuseki and Sipi.
 */
object AppConfigForTestContainers {

  private def alterFusekiPort(
    oldConfig: AppConfig,
    fusekiContainer: FusekiTestContainer,
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
